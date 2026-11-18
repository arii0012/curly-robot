package com.codewall
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * .poi — фигура, .poa — пак (фигура + настройки + сцена + виджеты). Только данные из белого списка полей.
 * Любое лишнее поле, ссылка или значение вне диапазона отклоняет файл. Контрольная сумма SHA-256 ("sum")
 * ловит порчу и правку файла после создания.
 */
object Formats {
    const val MAX = 262144
    val CATEGORIES = listOf("Минимализм", "Киберпанк", "Игры", "Аниме", "Животные", "Природа", "Космос", "Разное")
    val SCENES = listOf("Код-фигура", "Чёрная дыра", "Подарок", "Сердце из слов")

    class Invalid(m: String) : Exception(m)
    class Poa(val name: String, val author: String, val category: String, val desc: String, val shapeName: String?,
              val shape: Mask?, val settings: JSONObject?, val widgets: JSONObject?, val scene: JSONObject?, val signed: Boolean)

    private fun clean(s: String, max: Int): String {
        val t = s.filter { it >= ' ' && it != '\u007f' }.trim()
        if (t.contains("://") || t.contains("www.", ignoreCase = true)) throw Invalid("ссылки в файлах запрещены")
        return t.take(max)
    }

    private fun onlyKeys(o: JSONObject, allowed: Set<String>) {
        val it = o.keys()
        while (it.hasNext()) { val k = it.next(); if (k !in allowed) throw Invalid("неизвестное поле «$k»") }
    }

    private fun num(o: JSONObject, k: String, lo: Double, hi: Double) {
        if (!o.has(k)) return
        val v = o.opt(k)
        if (v !is Number) throw Invalid("поле «$k» должно быть числом")
        val d = v.toDouble()
        if (d.isNaN() || d < lo || d > hi) throw Invalid("значение «$k» вне допустимого диапазона")
    }

    private fun bool(o: JSONObject, k: String) { if (o.has(k) && o.opt(k) !is Boolean) throw Invalid("поле «$k» должно быть да/нет") }

    private fun canon(v: Any?): String = when (v) {
        is JSONObject -> {
            val ks = ArrayList<String>(); val it = v.keys()
            while (it.hasNext()) ks.add(it.next())
            ks.sort()
            ks.joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canon(v.get(it)) }
        }
        is JSONArray -> (0 until v.length()).joinToString(",", "[", "]") { canon(v.get(it)) }
        is String -> JSONObject.quote(v)
        else -> v.toString()
    }

    fun readText(cr: ContentResolver, uri: Uri): String {
        val inp = cr.openInputStream(uri) ?: throw Invalid("не удалось открыть файл")
        inp.use {
            val buf = ByteArrayOutputStream(); val tmp = ByteArray(8192); var total = 0
            while (true) {
                val n = it.read(tmp); if (n < 0) break
                total += n; if (total > MAX) throw Invalid("файл слишком большой (максимум 256 КБ)")
                buf.write(tmp, 0, n)
            }
            return String(buf.toByteArray(), Charsets.UTF_8)
        }
    }

    fun kind(text: String): String {
        val t = text.trim()
        if (!t.startsWith("{")) throw Invalid("это не файл PoiPaper")
        val o = try { JSONObject(t) } catch (e: Exception) { throw Invalid("файл повреждён") }
        return o.optString("format")
    }

    fun shapeJson(name: String, m: Mask): JSONObject {
        val o = JSONObject(); o.put("name", name.take(24)); o.put("rows", m.rows)
        val arr = JSONArray()
        for (r in 0 until m.rows) {
            val sb = StringBuilder()
            for (x in 0 until m.cols) sb.append(if (m.on[r * m.cols + x]) '1' else '0')
            arr.put(sb.toString())
        }
        o.put("data", arr)
        return o
    }

    fun poiToText(name: String, m: Mask): String {
        val o = shapeJson(name, m); o.put("format", "poi"); o.put("v", 1); return o.toString()
    }

    private fun parseShape(o: JSONObject): Pair<String, Mask> {
        val name = clean(o.optString("name", "Фигура"), 24).ifEmpty { "Фигура" }
        val rows = o.optInt("rows", -1)
        if (rows < 8 || rows > 160) throw Invalid("неверная высота фигуры")
        val arr = o.optJSONArray("data") ?: throw Invalid("в файле нет данных фигуры")
        if (arr.length() != rows) throw Invalid("число строк не совпадает")
        val on = BooleanArray(rows * Shapes.COLS)
        for (r in 0 until rows) {
            val s = arr.optString(r, "")
            if (s.length != Shapes.COLS) throw Invalid("неверная ширина строки")
            for (x in s.indices) {
                when (s[x]) { '1' -> on[r * Shapes.COLS + x] = true; '0' -> {}; else -> throw Invalid("недопустимый символ в данных") }
            }
        }
        return Pair(name, Mask(rows, on))
    }

    fun parsePoi(text: String): Pair<String, Mask> {
        if (text.length > MAX) throw Invalid("файл слишком большой")
        val t = text.trim()
        if (!t.startsWith("{")) throw Invalid("это не файл .poi")
        val o = try { JSONObject(t) } catch (e: Exception) { throw Invalid("файл повреждён") }
        onlyKeys(o, setOf("format", "v", "name", "rows", "data"))
        if (o.optString("format") != "poi" || o.optInt("v", 0) != 1) throw Invalid("неверный формат или версия .poi")
        return parseShape(o)
    }

    fun buildPoa(p: Prefs, shapeName: String, shape: Mask?, name: String, author: String, category: String, desc: String): String {
        val o = JSONObject(); o.put("format", "poa"); o.put("v", 1)
        o.put("name", name.take(32)); o.put("author", author.take(24)); o.put("category", category); o.put("description", desc.take(120))
        if (shape != null && (p.scene == 0 || (p.scene == 2 && p.target == 1))) o.put("shape", shapeJson(shapeName, shape))
        val s = JSONObject()
        s.put("color", p.colorInt); s.put("rainbow", p.rainbow); s.put("mode", p.mode)
        s.put("speed", p.speed.toDouble()); s.put("depth", p.depth.toDouble()); s.put("scale", p.scale.toDouble())
        s.put("lineW", p.lineW.toDouble()); s.put("glow", p.glow); s.put("bg", p.bg)
        s.put("x", p.posX.toDouble()); s.put("y", p.posY.toDouble())
        o.put("settings", s)
        val wl = JSONObject(); wl.put("list", JSONArray(p.wJson)); o.put("widgets", wl)
        val sc = JSONObject()
        sc.put("type", p.scene); sc.put("target", p.target); sc.put("density", p.density.toDouble())
        val wa = JSONArray(); for (w in p.words.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(14)) wa.put(w.take(18))
        sc.put("words", wa); sc.put("message", p.message.take(120)); sc.put("label", p.label.take(14))
        o.put("scene", sc)
        val plain = JSONObject(o.toString())
        o.put("sum", Crypto.sha256(canon(plain)))
        return o.toString()
    }

    fun parsePoa(text: String): Poa {
        if (text.length > MAX) throw Invalid("файл слишком большой")
        val t = text.trim()
        if (!t.startsWith("{")) throw Invalid("это не файл .poa")
        val o = try { JSONObject(t) } catch (e: Exception) { throw Invalid("файл повреждён") }
        onlyKeys(o, setOf("format", "v", "name", "author", "category", "description", "shape", "settings", "widgets", "scene", "sum"))
        if (o.optString("format") != "poa" || o.optInt("v", 0) != 1) throw Invalid("неверный формат или версия .poa")
        val sum = o.optString("sum", "")
        if (sum.isNotEmpty()) {
            val copy = JSONObject(t); copy.remove("sum")
            if (Crypto.sha256(canon(copy)) != sum) throw Invalid("файл изменён после создания (не совпала контрольная сумма)")
        }
        val name = clean(o.optString("name", ""), 32).ifEmpty { "Без названия" }
        val author = clean(o.optString("author", "аноним"), 24).ifEmpty { "аноним" }
        val cat = clean(o.optString("category", "Разное"), 24).let { if (it in CATEGORIES) it else "Разное" }
        val desc = clean(o.optString("description", ""), 120)

        var shapeName: String? = null; var shape: Mask? = null
        if (o.has("shape")) {
            val so = o.optJSONObject("shape") ?: throw Invalid("поле «shape» повреждено")
            onlyKeys(so, setOf("name", "rows", "data"))
            val r = parseShape(so); shapeName = r.first; shape = r.second
        }
        var settings: JSONObject? = null
        if (o.has("settings")) {
            val s = o.optJSONObject("settings") ?: throw Invalid("поле «settings» повреждено")
            onlyKeys(s, setOf("color", "rainbow", "mode", "speed", "depth", "scale", "lineW", "glow", "bg", "x", "y"))
            num(s, "color", Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble())
            num(s, "mode", 0.0, 1.0); num(s, "speed", 0.2, 3.0); num(s, "depth", 0.0, 2.0)
            num(s, "scale", 0.4, 2.0); num(s, "lineW", 0.5, 3.0); num(s, "bg", 0.0, 2.0); num(s, "x", 0.0, 1.0); num(s, "y", 0.0, 1.0)
            bool(s, "rainbow"); bool(s, "glow")
            settings = s
        }
        var widgets: JSONObject? = null
        if (o.has("widgets")) {
            val w = o.optJSONObject("widgets") ?: throw Invalid("поле «widgets» повреждено")
            onlyKeys(w, setOf("list"))
            val la = w.optJSONArray("list") ?: throw Invalid("список виджетов повреждён")
            if (la.length() > Widgets.MAX) throw Invalid("слишком много виджетов")
            for (k in 0 until la.length()) {
                val it = la.optJSONObject(k) ?: throw Invalid("виджет повреждён")
                onlyKeys(it, setOf("t", "s", "c", "z", "x", "y", "a", "tx", "f", "o", "pl", "r", "g"))
                num(it, "t", 0.0, 10.0); num(it, "s", 0.0, 4.0); num(it, "c", Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble())
                num(it, "z", 0.4, 3.0); num(it, "x", 0.0, 1.0); num(it, "y", 0.0, 1.0); num(it, "a", 0.0, 4.0)
                num(it, "f", 0.0, 4.0); num(it, "o", 0.2, 1.0); num(it, "pl", 0.0, 2.0); num(it, "r", -90.0, 90.0); bool(it, "g")
                if (it.has("tx")) { if (it.opt("tx") !is String) throw Invalid("текст виджета повреждён"); clean(it.getString("tx"), 40) }
            }
            widgets = w
        }
        var scene: JSONObject? = null
        if (o.has("scene")) {
            val sc = o.optJSONObject("scene") ?: throw Invalid("поле «scene» повреждено")
            onlyKeys(sc, setOf("type", "target", "density", "words", "message", "label"))
            num(sc, "type", 0.0, 3.0); num(sc, "target", 0.0, 1.0); num(sc, "density", 0.4, 1.5)
            if (sc.has("words")) {
                val wa = sc.optJSONArray("words") ?: throw Invalid("слова повреждены")
                if (wa.length() > 14) throw Invalid("слишком много слов")
                for (k in 0 until wa.length()) { val s = wa.opt(k); if (s !is String) throw Invalid("слово повреждено"); clean(s, 18) }
            }
            for (key in listOf("message", "label")) if (sc.has(key)) {
                if (sc.opt(key) !is String) throw Invalid("поле «$key» повреждено")
                clean(sc.getString(key), 120)
            }
            scene = sc
        }
        if (shape == null && settings == null && widgets == null && scene == null) throw Invalid("пак пустой")
        return Poa(name, author, cat, desc, shapeName, shape, settings, widgets, scene, sum.isNotEmpty())
    }

    fun items(p: Poa): List<Pair<String, String>> {
        val l = ArrayList<Pair<String, String>>()
        val sc = p.scene
        if (sc != null) l.add(Pair("scene", "Сцена: " + SCENES[sc.optInt("type", 0).coerceIn(0, 3)]))
        if (p.shape != null) l.add(Pair("shape", "Фигура «" + p.shapeName + "»"))
        val s = p.settings
        if (s != null) {
            if (s.has("color") || s.has("rainbow")) l.add(Pair("color", "Цвет" + (if (s.optBoolean("rainbow")) " (радуга)" else "")))
            if (s.has("mode") || s.has("speed")) l.add(Pair("motion", "Движение и скорость"))
            if (s.has("depth") || s.has("lineW") || s.has("glow") || s.has("bg")) l.add(Pair("look", "Объём, линии, свечение, фон"))
            if (s.has("scale") || s.has("x") || s.has("y")) l.add(Pair("layout", "Размер и положение"))
        }
        val w = p.widgets
        if (w != null) l.add(Pair("widgets", "Виджеты: " + (w.optJSONArray("list")?.length() ?: 0) + " шт."))
        return l
    }

    fun applyPoa(p: Prefs, ctx: Context, poa: Poa, keys: Set<String>, persist: Boolean = true) {
        val s = poa.settings; val w = poa.widgets; val sc = poa.scene; val sh = poa.shape
        if (sc != null && "scene" in keys) {
            p.scene = sc.optInt("type", 0).coerceIn(0, 3); p.target = sc.optInt("target", 0).coerceIn(0, 1)
            p.density = sc.optDouble("density", 1.0).toFloat().coerceIn(0.4f, 1.5f)
            val wa = sc.optJSONArray("words")
            if (wa != null) { val l = ArrayList<String>(); for (k in 0 until wa.length()) l.add(clean(wa.getString(k), 18)); p.words = l.filter { it.isNotEmpty() }.joinToString(",") }
            if (sc.has("message")) p.message = clean(sc.getString("message"), 120)
            if (sc.has("label")) p.label = clean(sc.getString("label"), 14)
        }
        if ("shape" in keys && sh != null) {
            if (persist) p.shapeA = Shapes.save(ctx, poa.shapeName ?: poa.name, sh)
            else { Shapes.tmp["m:preview"] = sh; p.shapeA = "m:preview" }
        }
        if (s != null) {
            if ("color" in keys) {
                if (s.has("color")) p.colorInt = s.getInt("color") or 0xFF000000.toInt()
                if (s.has("rainbow")) p.rainbow = s.getBoolean("rainbow")
            }
            if ("motion" in keys) {
                if (s.has("mode")) p.mode = s.getInt("mode")
                if (s.has("speed")) p.speed = s.getDouble("speed").toFloat()
            }
            if ("look" in keys) {
                if (s.has("depth")) p.depth = s.getDouble("depth").toFloat()
                if (s.has("lineW")) p.lineW = s.getDouble("lineW").toFloat()
                if (s.has("glow")) p.glow = s.getBoolean("glow")
                if (s.has("bg")) p.bg = s.getInt("bg")
            }
            if ("layout" in keys) {
                if (s.has("scale")) p.scale = s.getDouble("scale").toFloat()
                if (s.has("x")) p.posX = s.getDouble("x").toFloat()
                if (s.has("y")) p.posY = s.getDouble("y").toFloat()
            }
        }
        if (w != null && "widgets" in keys) {
            val la = w.getJSONArray("list"); val out = JSONArray()
            for (k in 0 until la.length()) {
                val wd = Widgets.fromJson(la.getJSONObject(k))
                wd.text = clean(wd.text, 40)
                out.put(Widgets.toJson(wd))
            }
            p.wJson = out.toString()
        }
        p.stamp = System.currentTimeMillis()
    }
}
