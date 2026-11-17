package com.codewall
import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.*

class MainActivity : Activity() {
    private lateinit var p: Prefs
    private lateinit var r: Renderer
    private lateinit var ui: Ui
    private lateinit var acc: Account
    private lateinit var preview: PreviewView
    private lateinit var content: LinearLayout
    private lateinit var sv: ScrollView
    private var tab = 0
    private var target = 0
    private var editing = -1
    private var pendingText = ""
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k -> if (k in Prefs.RELOAD_KEYS) r.reload() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Prefs(this); r = Renderer(this, p); ui = Ui(this); acc = Account(this, p, ui)
        val bgc = Color.parseColor("#070B09")
        window.statusBarColor = bgc; window.navigationBarColor = bgc

        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(bgc)
        page.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8))

        // верх: мини-телефон с живым предпросмотром + главные кнопки
        val dm = resources.displayMetrics
        val ph = (dm.heightPixels * 0.30f).toInt()
        val pw = (ph * dm.widthPixels.toFloat() / dm.heightPixels).toInt()
        val top = LinearLayout(this)
        preview = PreviewView(this, r, p)
        preview.background = ui.bg(Color.BLACK, ui.LINE, 22)
        preview.clipToOutline = true
        preview.outlineProvider = ViewOutlineProvider.BACKGROUND
        top.addView(preview, LinearLayout.LayoutParams(pw, ph))
        val side = LinearLayout(this)
        side.orientation = LinearLayout.VERTICAL
        side.gravity = Gravity.CENTER_VERTICAL
        side.setPadding(ui.dp(12), 0, 0, 0)
        val av = ImageView(this)
        av.setImageResource(R.drawable.ic_avatar)
        av.setOnClickListener { editing = -1; showTab(4) }
        side.addView(av, LinearLayout.LayoutParams(ui.dp(46), ui.dp(46)).apply { bottomMargin = ui.dp(6) })
        val t1 = TextView(this)
        t1.text = "PoiPaper"; t1.setTextColor(Color.WHITE); t1.textSize = 20f; t1.typeface = Typeface.DEFAULT_BOLD
        side.addView(t1, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = ui.dp(8) })
        ui.btn(side, "РАЗМЕТКА", false) { startActivity(Intent(this, LayoutActivity::class.java)) }
        ui.btn(side, "WORKSHOP", false) { startActivity(Intent(this, WorkshopActivity::class.java)) }
        ui.btn(side, "УСТАНОВИТЬ", true) { installWallpaper() }
        top.addView(side, LinearLayout.LayoutParams(0, ph, 1f))
        page.addView(top, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(8) })

        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.VERTICAL
        ui.chips(tabs, listOf("Обои", "Фигуры", "Темы", "Виджеты", "Профиль"), 0) { i -> editing = -1; showTab(i) }
        page.addView(tabs)

        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(content)
        page.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(page)
        showTab(0)
    }

    override fun onResume() {
        super.onResume()
        p.sp.registerOnSharedPreferenceChangeListener(listener)
        r.reload()
        preview.invalidate()
        if (tab == 4) showTab(4)
    }

    override fun onPause() {
        p.sp.unregisterOnSharedPreferenceChangeListener(listener)
        super.onPause()
    }

    private fun installWallpaper() {
        val i = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this, CodeWallpaperService::class.java))
        try { startActivity(i) } catch (e: Exception) { startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
    }

    private fun showTab(i: Int) {
        tab = i
        content.removeAllViews()
        sv.scrollTo(0, 0)
        when (i) { 0 -> tabLook(); 1 -> tabShapes(); 2 -> tabThemes(); 3 -> tabWidgets(); else -> tabProfile() }
    }

    private fun pct(v: Float) = "" + (v * 100).toInt() + "%"

    // ---------- ОБОИ ----------
    private fun tabLook() {
        ui.card(content, "01 / СЦЕНА", "Что показывать").let {
            ui.chips(it, listOf("Код-фигура", "Чёрная дыра", "Подарок", "Сердце"), p.scene) { i -> p.scene = i; showTab(0) }
        }
        if (p.scene == 0) {
            ui.card(content, "02 / ДВИЖЕНИЕ", "Вращение").let {
                ui.chips(it, listOf("Вращение", "Покачивание"), p.mode) { i -> p.mode = i }
                ui.slider(it, "скорость", 0.2f, 3f, p.speed) { v -> p.speed = v }
                ui.slider(it, "глубина 3D", 0f, 2f, p.depth) { v -> p.depth = v }
                ui.slider(it, "толщина линий", 0.5f, 3f, p.lineW) { v -> p.lineW = v }
            }
            ui.card(content, "03 / ЦВЕТ", "Цвет и фон").let {
                ui.swatches(it, Prefs.PALETTE, Prefs.PALETTE.indexOf(p.colorInt)) { i -> p.colorInt = Prefs.PALETTE[i]; p.rainbow = false }
                ui.chips(it, listOf("Один цвет", "Радуга"), if (p.rainbow) 1 else 0) { i -> p.rainbow = (i == 1) }
                ui.chips(it, listOf("Свечение вкл", "Свечение выкл"), if (p.glow) 0 else 1) { i -> p.glow = (i == 0) }
                ui.chips(it, listOf("Чёрный", "Градиент", "Сетка"), p.bg) { i -> p.bg = i }
            }
        } else {
            ui.card(content, "02 / СЦЕНА", Formats.SCENES[p.scene]).let {
                ui.slider(it, "скорость", 0.3f, 2.5f, p.speed) { v -> p.speed = v }
                ui.slider(it, "плотность частиц", 0.4f, 1.5f, p.density) { v -> p.density = v }
                if (p.scene == 2) {
                    ui.chips(it, listOf("Мишка", "Моя фигура"), p.target) { i -> p.target = i }
                    ui.field(it, "Надпись на сердце", p.label) { s -> p.label = s.take(14) }
                }
                if (p.scene == 3) {
                    ui.field(it, "Слова через запятую", p.words) { s -> p.words = s.take(200) }
                    val sp = View(this); it.addView(sp, LinearLayout.LayoutParams(-1, ui.dp(8)))
                    ui.field(it, "Письмо (печатается внизу)", p.message) { s -> p.message = s.take(120) }
                }
            }
            ui.card(content, "03 / ЦВЕТ", "Цвет сцены").let {
                ui.swatches(it, Prefs.PALETTE, Prefs.PALETTE.indexOf(p.colorInt)) { i -> p.colorInt = Prefs.PALETTE[i]; p.rainbow = false }
            }
        }
        ui.card(content, "04 / РАЗМЕР И МЕСТО", "Положение на экране").let {
            ui.slider(it, "размер", 0.4f, 2f, p.scale) { v -> p.scale = v }
            ui.slider(it, "по горизонтали", 0f, 1f, p.posX, { v -> pct(v) }) { v -> p.posX = v }
            ui.slider(it, "по вертикали", 0f, 1f, p.posY, { v -> pct(v) }) { v -> p.posY = v }
            ui.btn(it, "ДВИГАТЬ ПАЛЬЦЕМ НА ЭКРАНЕ", true) { startActivity(Intent(this, LayoutActivity::class.java)) }
            ui.btn(it, "СБРОСИТЬ ПОЛОЖЕНИЕ", false) { p.posX = 0.5f; p.posY = 0.46f; p.scale = 1f; showTab(0) }
        }
        ui.card(content, "05 / БАТАРЕЯ", "Плавность и экономия").let {
            val f = listOf(30, 45, 60, 90, 120)
            ui.chips(it, f.map { x -> "$x" }, f.indexOf(p.fps).coerceAtLeast(0)) { i -> p.fps = f[i] }
            val ecos = listOf(0, 30, 60, 120)
            ui.chips(it, listOf("Не замирать", "30 с", "1 мин", "2 мин"), ecos.indexOf(p.eco).coerceAtLeast(0)) { i -> p.eco = ecos[i] }
            ui.chips(it, listOf("Лёгкое", "Баланс", "Красивое"), p.quality) { i -> p.quality = i }
            ui.toggle(it, "Экономить при низком заряде", p.saver) { v -> p.saver = v }
        }
    }

    // ---------- ФИГУРЫ ----------
    private fun curId() = if (target == 1) p.shapeB else p.shapeA

    private fun setTarget(id: String) {
        if (target == 1) p.shapeB = id else p.shapeA = id
        p.stamp = System.currentTimeMillis()
    }

    private fun tabShapes() {
        ui.card(content, "01 / ВЫБОР", "Какую фигуру менять").let {
            ui.chips(it, listOf("Фигура 1", "Фигура 2"), target) { i -> target = i; showTab(1) }
            if (target == 1 && p.shapeB.isNotEmpty()) ui.btn(it, "УБРАТЬ ФИГУРУ 2", false) { p.shapeB = ""; showTab(1) }
        }
        ui.card(content, "02 / КАТАЛОГ", "Встроенные фигуры").let {
            val names = Shapes.NAMES
            var s = 0
            while (s < names.size) {
                val part = names.subList(s, minOf(s + 4, names.size))
                val cur = curId()
                var sel = -1
                for (k in part.indices) if (cur == "b:" + (s + k)) sel = k
                val base = s
                ui.chips(it, part, sel) { k -> setTarget("b:" + (base + k)); showTab(1) }
                s += 4
            }
        }
        ui.card(content, "03 / МОИ ФИГУРЫ", "Из фото и файлов").let {
            for (f in Shapes.list(this)) {
                val row = LinearLayout(this)
                row.gravity = Gravity.CENTER_VERTICAL
                val tv = TextView(this)
                tv.text = (if (curId() == f.first) "● " else "○ ") + f.second
                tv.setTextColor(if (curId() == f.first) Color.WHITE else ui.DIM); tv.textSize = 15f
                tv.setOnClickListener { setTarget(f.first); showTab(1) }
                row.addView(tv, LinearLayout.LayoutParams(0, -2, 1f))
                ui.small(row, "ЭКСПОРТ") { exportPoi(f.first, f.second) }
                ui.small(row, "УДАЛИТЬ") {
                    Shapes.delete(this, f.first)
                    if (p.shapeA == f.first) p.shapeA = "b:0"
                    if (p.shapeB == f.first) p.shapeB = ""
                    showTab(1)
                }
                it.addView(row, LinearLayout.LayoutParams(-1, ui.dp(44)))
            }
            ui.btn(it, "ИЗ ФОТО (РЕДАКТОР)", true) {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                i.addCategory(Intent.CATEGORY_OPENABLE); i.type = "image/*"
                startActivityForResult(i, 1)
            }
            ui.btn(it, "ИЗ ТЕКСТА", false) {
                ui.prompt("Текст фигуры", "до 14 символов", "POI") { t -> if (t.isNotEmpty()) { setTarget("t:" + t.take(14)); showTab(1) } }
            }
            ui.btn(it, "ИМПОРТ .poi / .poa", false) { pickFile() }
        }
    }

    // ---------- ТЕМЫ ----------
    private fun tabThemes() {
        ui.card(content, "ТЕМЫ", "Готовые стили в один клик").let { c ->
            for (t in Themes.ALL) {
                val row = LinearLayout(this)
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8))
                row.background = ui.bg(ui.DARK, ui.LINE, 16)
                val dot = View(this)
                val g = GradientDrawable()
                g.setShape(GradientDrawable.OVAL); g.setColor(t.color)
                dot.background = g
                row.addView(dot, LinearLayout.LayoutParams(ui.dp(26), ui.dp(26)))
                val col = LinearLayout(this)
                col.orientation = LinearLayout.VERTICAL
                col.setPadding(ui.dp(12), 0, 0, 0)
                val n = TextView(this)
                n.text = t.name; n.setTextColor(Color.WHITE); n.textSize = 16f
                val d = TextView(this)
                d.text = t.desc; d.setTextColor(ui.DIM); d.textSize = 12f
                col.addView(n); col.addView(d)
                row.addView(col)
                row.setOnClickListener { Themes.apply(p, t); ui.toast(t.name) }
                c.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(8) })
            }
        }
    }

    // ---------- ВИДЖЕТЫ ----------
    private fun tabWidgets() {
        val wl = Widgets.load(p)
        if (editing in wl.indices) { editWidget(wl, editing); return }
        ui.card(content, "ВИДЖЕТЫ", "На экране: " + wl.size).let { c ->
            for (i in wl.indices) {
                val w = wl[i]
                val row = LinearLayout(this)
                row.gravity = Gravity.CENTER_VERTICAL
                val tv = TextView(this)
                tv.text = Widgets.TYPES[w.type] + " · " + Widgets.STYLES[w.type][w.style]
                tv.setTextColor(Color.WHITE); tv.textSize = 15f
                tv.setOnClickListener { editing = i; showTab(3) }
                row.addView(tv, LinearLayout.LayoutParams(0, -2, 1f))
                ui.small(row, "ИЗМЕНИТЬ") { editing = i; showTab(3) }
                ui.small(row, "УДАЛИТЬ") { wl.removeAt(i); Widgets.save(p, wl); showTab(3) }
                c.addView(row, LinearLayout.LayoutParams(-1, ui.dp(44)))
            }
            if (wl.size < Widgets.MAX) ui.btn(c, "+ ДОБАВИТЬ ВИДЖЕТ", true) {
                AlertDialog.Builder(this).setTitle("Тип виджета").setItems(Widgets.TYPES.toTypedArray()) { _, k ->
                    wl.add(Widgets.make(k, p.colorInt, wl.size)); Widgets.save(p, wl); editing = wl.size - 1; showTab(3)
                }.show()
            }
            if (wl.isNotEmpty()) ui.btn(c, "ПЕРЕМЕСТИТЬ ПАЛЬЦЕМ", false) { startActivity(Intent(this, LayoutActivity::class.java)) }
        }
    }

    private fun editWidget(wl: MutableList<Wdg>, idx: Int) {
        val w = wl[idx]
        fun save() { Widgets.save(p, wl) }
        ui.card(content, "ВИДЖЕТ " + (idx + 1), Widgets.TYPES[w.type]).let { c ->
            ui.chips(c, Widgets.STYLES[w.type], w.style) { i -> w.style = i; save() }
            ui.chips(c, Widgets.ANIMS, w.anim) { i -> w.anim = i; save() }
            ui.swatches(c, Prefs.PALETTE, Prefs.PALETTE.indexOf(w.color)) { i -> w.color = Prefs.PALETTE[i]; save() }
            ui.slider(c, "размер", 0.4f, 3f, w.size) { v -> w.size = v; save() }
            ui.slider(c, "по горизонтали", 0f, 1f, w.x, { v -> pct(v) }) { v -> w.x = v; save() }
            ui.slider(c, "по вертикали", 0f, 1f, w.y, { v -> pct(v) }) { v -> w.y = v; save() }
            if (w.type == 3) ui.field(c, "Текст (до 24 символов)", w.text) { s -> w.text = s.take(24); save() }
            val sp = View(this); c.addView(sp, LinearLayout.LayoutParams(-1, ui.dp(10)))
            ui.btn(c, "ПЕРЕМЕСТИТЬ ПАЛЬЦЕМ", true) { startActivity(Intent(this, LayoutActivity::class.java).putExtra("sel", idx)) }
            ui.btn(c, "ГОТОВО", false) { editing = -1; showTab(3) }
        }
    }

    // ---------- ПРОФИЛЬ ----------
    private fun tabProfile() {
        ui.card(content, "ПРОФИЛЬ", if (acc.loggedIn()) p.email else "Гость").let { c ->
            val row = LinearLayout(this)
            row.gravity = Gravity.CENTER_VERTICAL
            val av = ImageView(this)
            av.setImageResource(R.drawable.ic_avatar)
            row.addView(av, LinearLayout.LayoutParams(ui.dp(64), ui.dp(64)))
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.setPadding(ui.dp(12), 0, 0, 0)
            ui.field(col, "Твой ник", p.author) { s -> p.author = s.take(24).ifEmpty { "аноним" } }
            row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            c.addView(row)
            val sp = View(this); c.addView(sp, LinearLayout.LayoutParams(-1, ui.dp(12)))
            if (acc.loggedIn()) {
                ui.btn(c, "ОТКРЫТЬ WORKSHOP", true) { startActivity(Intent(this, WorkshopActivity::class.java)) }
                ui.btn(c, "ВЫЙТИ", false) { acc.logout(); showTab(4) }
            } else {
                ui.btn(c, "ВОЙТИ ЧЕРЕЗ GOOGLE", true) { acc.google() }
                ui.btn(c, "ВОЙТИ ПО ПОЧТЕ", false) { acc.loginDialog { showTab(4) } }
            }
        }
        ui.card(content, "ФАЙЛЫ", ".poa и .poi").let {
            ui.btn(it, "ИМПОРТ .poa / .poi", true) { pickFile() }
            ui.btn(it, "ЭКСПОРТ ТЕКУЩИХ ОБОЕВ В .poa", false) { exportPoa() }
            ui.btn(it, "ОТКАТИТЬ ПОСЛЕДНИЙ ПАК", false) {
                if (p.restoreBackup()) { r.reload(); ui.toast("Откат выполнен"); showTab(4) } else ui.toast("Нечего откатывать")
            }
        }
        ui.card(content, "ЗАЩИТА", "Как проверяются работы").let {
            ui.note(it, "Работы — только данные без кода. Лишние поля, ссылки и неверные значения отклоняются, контрольная сумма ловит подмену, вход шифруется ключом Android Keystore.")
        }
    }

    // ---------- Файлы ----------
    private fun pickFile() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE); i.type = "*/*"
        startActivityForResult(i, 2)
    }

    private fun createDoc(fileName: String) {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE); i.type = "application/octet-stream"
        i.putExtra(Intent.EXTRA_TITLE, fileName)
        startActivityForResult(i, 3)
    }

    private fun exportPoi(id: String, name: String) {
        val m = Shapes.resolve(this, id)
        if (m == null) { ui.toast("Не удалось прочитать фигуру"); return }
        pendingText = Formats.poiToText(name, m)
        createDoc("$name.poi")
    }

    private fun exportPoa() {
        ui.prompt("Название пака", "например: Неоновая чёрная дыра", "") { name ->
            if (name.isNotEmpty()) {
                AlertDialog.Builder(this).setTitle("Категория").setItems(Formats.CATEGORIES.toTypedArray()) { _, w ->
                    pendingText = Formats.buildPoa(p, Shapes.nameOf(p.shapeA), Shapes.resolve(this, p.shapeA), name, p.author, Formats.CATEGORIES[w], "")
                    createDoc("$name.poa")
                }.show()
            }
        }
    }

    private fun importFile(uri: Uri) {
        try {
            val text = Formats.readText(contentResolver, uri)
            when (Formats.kind(text)) {
                "poa" -> ui.showPoa(Formats.parsePoa(text), p) { r.reload(); showTab(tab) }
                "poi" -> {
                    val sh = Formats.parsePoi(text)
                    setTarget(Shapes.save(this, sh.first, sh.second))
                    ui.toast("Фигура «" + sh.first + "» добавлена")
                    showTab(tab)
                }
                else -> throw Formats.Invalid("неизвестный формат файла")
            }
        } catch (e: Formats.Invalid) {
            AlertDialog.Builder(this).setTitle("Файл отклонён").setMessage(e.message).setPositiveButton("ОК", null).show()
        }
    }

    override fun onActivityResult(rc: Int, res: Int, d: Intent?) {
        super.onActivityResult(rc, res, d)
        if (rc == 5) { showTab(tab); return }
        if (res != RESULT_OK) return
        val uri = d?.data ?: return
        when (rc) {
            1 -> {
                val i = Intent(this, EditorActivity::class.java)
                i.data = uri
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                i.putExtra("target", target)
                startActivityForResult(i, 5)
            }
            2 -> importFile(uri)
            3 -> try {
                contentResolver.openOutputStream(uri)?.use { it.write(pendingText.toByteArray(Charsets.UTF_8)) }
                ui.toast("Файл сохранён")
            } catch (e: Exception) { ui.toast("Не удалось сохранить файл") }
        }
    }
}
