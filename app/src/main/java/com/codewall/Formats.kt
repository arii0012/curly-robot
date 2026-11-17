package com.codewall
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * .poi — фигура (только текст JSON: размер + строки из 0 и 1).
 * .poa — пак (JSON): фигура + настройки + виджеты.
 * Файлы содержат ТОЛЬКО данные из белого списка полей. Кода, ссылок и картинок в них быть не может:
 * любое лишнее поле, ссылка или значение вне диапазона отклоняет файл целиком.
 */
object Formats {
    const val MAX = 262144
    val CATEGORIES = listOf("Минимализм", "Киберпанк", "Игры", "Аниме", "Животные", "Природа", "Космос", "Разное")

    class Invalid(m: String) : Exception(m)
    class Poa(val name: String, val author: String, val category: String, val desc: String,
              val shapeName: String?, val shape: Mask?, val settings: JSONObject?, val widgets: JSONObject?)

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
        if (shape != null) o.put("shape", shapeJson(shapeName, shape))
        val s = JSONObject()
        s.put("color", p.colorInt); s.put("rainbow", p.rainbow); s.put("mode", p.mode)
        s.put("speed", p.speed.toDouble()); s.put("depth", p.depth.toDouble()); s.put("scale", p.scale.toDouble())
        s.put("lineW", p.lineW.toDouble()); s.put("glow", p.glow); s.put("bg", p.bg)
        o.put("settings", s)
        val w = JSONObject()
        w.put("clock", p.wClock); w.put("date", p.wDate); w.put("battery", p.wBattery); w.put("text", p.wText.take(24)); w.put("pos", p.wPos)
        o.put("widgets", w)
        return o.toString()
    }

    fun parsePoa(text: String): Poa {
        if (text.length > MAX) throw Invalid("файл слишком большой")
        val t = text.trim()
        if (!t.startsWith("{")) throw Invalid("это не файл .poa")
        val o = try { JSONObject(t) } catch (e: Exception) { throw Invalid("файл повреждён") }
        onlyKeys(o, setOf("format", "v", "name", "author", "category", "description", "shape", "settings", "widgets"))
        if (o.optString("format") != "poa" || o.optInt("v", 0) != 1) throw Invalid("неверный формат или версия .poa")
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
            onlyKeys(s, setOf("color", "rainbow", "mode", "speed", "depth", "scale", "lineW", "glow", "bg"))
            num(s, "color", Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble())
            num(s, "mode", 0.0, 1.0); num(s, "speed", 0.2, 3.0); num(s, "depth", 0.0, 2.0)
            num(s, "scale", 0.4, 1.5); num(s, "lineW", 0.5, 3.0); num(s, "bg", 0.0, 2.0)
            bool(s, "rainbow"); bool(s, "glow")
            settings = s
        }
        var widgets: JSONObject? = null
        if (o.has("widgets")) {
            val w = o.optJSONObject("widgets") ?: throw Invalid("поле «widgets» повреждено")
            onlyKeys(w, setOf("clock", "date", "battery", "text", "pos"))
            bool(w, "clock"); bool(w, "date"); bool(w, "battery"); num(w, "pos", 0.0, 1.0)
            if (w.has("text")) { if (w.opt("text") !is String) throw Invalid("текст виджета повреждён"); clean(w.getString("text"), 24) }
            widgets = w
        }
        if (shape == null && settings == null && widgets == null) throw Invalid("пак пустой")
        return Poa(name, author, cat, desc, shapeName, shape, settings, widgets)
    }

    fun items(p: Poa): List<Pair<String, String>> {
        val l = ArrayList<Pair<String, String>>()
        if (p.shape != null) l.add(Pair("shape", "Фигура «" + p.shapeName + "»"))
        val s = p.settings
        if (s != null) {
            if (s.has("color") || s.has("rainbow")) l.add(Pair("color", "Цвет" + (if (s.optBoolean("rainbow")) " (радуга)" else "")))
            if (s.has("mode") || s.has("speed")) l.add(Pair("motion", "Движение и скорость"))
            if (s.has("depth") || s.has("scale") || s.has("lineW") || s.has("glow") || s.has("bg")) l.add(Pair("look", "Объём, линии, свечение, фон"))
        }
        val w = p.widgets
        if (w != null) {
            val n = ArrayList<String>()
            if (w.optBoolean("clock")) n.add("часы"); if (w.optBoolean("date")) n.add("дата"); if (w.optBoolean("battery")) n.add("батарея")
            if (w.optString("text", "").isNotEmpty()) n.add("текст")
            l.add(Pair("widgets", "Виджеты: " + (if (n.isEmpty()) "выключены" else n.joinToString(", "))))
        }
        return l
    }

    fun applyPoa(p: Prefs, ctx: Context, poa: Poa, keys: Set<String>) {
        val s = poa.settings; val w = poa.widgets
        val sh = poa.shape
        if ("shape" in keys && sh != null) p.shapeA = Shapes.save(ctx, poa.shapeName ?: poa.name, sh)
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
                if (s.has("scale")) p.scale = s.getDouble("scale").toFloat()
                if (s.has("lineW")) p.lineW = s.getDouble("lineW").toFloat()
                if (s.has("glow")) p.glow = s.getBoolean("glow")
                if (s.has("bg")) p.bg = s.getInt("bg")
            }
        }
        if (w != null && "widgets" in keys) {
            p.wClock = w.optBoolean("clock"); p.wDate = w.optBoolean("date"); p.wBattery = w.optBoolean("battery")
            p.wText = clean(w.optString("text", ""), 24); p.wPos = w.optInt("pos", 0).coerceIn(0, 1)
        }
        p.stamp = System.currentTimeMillis()
    }
}
