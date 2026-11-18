package com.codewall
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
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
import java.io.ByteArrayOutputStream
import java.time.LocalDate

class MainActivity : Activity() {
    private lateinit var p: Prefs
    private lateinit var r: Renderer
    private lateinit var ui: Ui
    private lateinit var frame: FrameLayout
    private lateinit var nav: LinearLayout
    private lateinit var content: LinearLayout
    private val navViews = ArrayList<TextView>()
    private var pv: PreviewView? = null
    private var tab = 0
    private var sub = 0
    private var tsub = 0
    private var wsub = 0
    private var editing = -1
    private var target = 0
    private var pendingBytes: ByteArray? = null
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k -> if (k in Prefs.RELOAD_KEYS) r.reload() }

    @Suppress("DEPRECATION")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Prefs(this); Skin.load(p); r = Renderer(this, p); ui = Ui(this)
        if (b != null) { tab = b.getInt("tab"); sub = b.getInt("sub"); tsub = b.getInt("tsub") }
        window.statusBarColor = ui.BG; window.navigationBarColor = ui.BG
        if (Skin.cur.name == "Светлая") window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(ui.BG)
        frame = FrameLayout(this)
        root.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
        nav = LinearLayout(this)
        nav.setBackgroundColor(ui.PANEL)
        root.addView(nav, LinearLayout.LayoutParams(-1, ui.dp(60)))
        val items = listOf("⌂" to "Главная", "✎" to "Дизайн", "▣" to "Виджеты", "◐" to "Темы", "☺" to "Профиль")
        for (i in items.indices) {
            val tv = TextView(this)
            tv.text = items[i].first + "\n" + items[i].second
            tv.gravity = Gravity.CENTER; tv.textSize = 12f
            tv.setOnClickListener { ui.hap(tv); editing = -1; showTab(i) }
            navViews.add(tv)
            nav.addView(tv, LinearLayout.LayoutParams(0, -1, 1f))
        }
        setContentView(root)
        showTab(tab)
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putInt("tab", tab); out.putInt("sub", sub); out.putInt("tsub", tsub)
    }

    override fun onResume() {
        super.onResume()
        p.sp.registerOnSharedPreferenceChangeListener(listener)
        r.reload()
        pv?.invalidate()
        if (tab == 4) showTab(4)
    }

    override fun onPause() {
        p.sp.unregisterOnSharedPreferenceChangeListener(listener)
        super.onPause()
    }

    private fun showTab(i: Int) {
        tab = i
        for (k in navViews.indices) navViews[k].setTextColor(if (k == i) ui.G else ui.DIM)
        frame.removeAllViews()
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(16))
        sv.addView(content)
        frame.addView(sv)
        pv = null
        when (i) { 0 -> pageHome(); 1 -> pageDesign(); 2 -> pageWidgets(); 3 -> pageThemes(); else -> pageProfile() }
        content.alpha = 0f
        content.animate().alpha(1f).setDuration(160).start()
    }

    private fun previewCard(frac: Float) {
        val dm = resources.displayMetrics
        val ph = (dm.heightPixels * frac).toInt()
        val pw = (ph * dm.widthPixels.toFloat() / dm.heightPixels).toInt()
        val v = PreviewView(this, r, p)
        v.background = ui.bg(Color.BLACK, ui.LINE, 22)
        v.clipToOutline = true
        v.outlineProvider = ViewOutlineProvider.BACKGROUND
        pv = v
        val holder = LinearLayout(this)
        holder.gravity = Gravity.CENTER
        holder.addView(v, LinearLayout.LayoutParams(pw, ph))
        content.addView(holder, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(10) })
    }

    private fun vbox(): LinearLayout { val a = LinearLayout(this); a.orientation = LinearLayout.VERTICAL; return a }
    private fun pct(v: Float) = "" + (v * 100).toInt() + "%"
    private fun spacer(c: LinearLayout, h: Int) { c.addView(View(this), LinearLayout.LayoutParams(-1, ui.dp(h))) }

    private fun installWallpaper() {
        val i = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this, CodeWallpaperService::class.java))
        try { startActivity(i) } catch (e: Exception) { startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
    }

    // ================= ГЛАВНАЯ =================
    private fun pageHome() {
        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        val av = ImageView(this)
        av.setImageResource(R.drawable.ic_avatar)
        av.setOnClickListener { showTab(4) }
        head.addView(av, LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)))
        val col = vbox()
        col.setPadding(ui.dp(12), 0, 0, 0)
        val t1 = TextView(this)
        t1.text = "PoiPaper"; t1.setTextColor(ui.TXT); t1.textSize = 22f; t1.typeface = Typeface.DEFAULT_BOLD
        val t2 = TextView(this)
        t2.text = Formats.SCENES[p.scene]; t2.setTextColor(ui.DIM); t2.textSize = 12f
        col.addView(t1); col.addView(t2)
        head.addView(col)
        content.addView(head, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(10) })
        previewCard(0.44f)
        val r1 = LinearLayout(this)
        ui.tile(r1, "✦", "УСТАНОВИТЬ", true) { installWallpaper() }
        ui.tile(r1, "⤢", "РАЗМЕТКА", false) { startActivity(Intent(this, LayoutActivity::class.java)) }
        content.addView(r1, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(8) })
        val r2 = LinearLayout(this)
        ui.tile(r2, "🎲", "УДИВИ МЕНЯ", false) { surprise() }
        ui.tile(r2, "⇪", "ПОДЕЛИТЬСЯ", false) { exportPoa() }
        content.addView(r2, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(8) })
        val r3 = LinearLayout(this)
        ui.tile(r3, "⬇", "СОХРАНИТЬ КАДР", false) { saveFrame() }
        ui.tile(r3, "★", "МОИ СБОРКИ", false) { tsub = 1; showTab(3) }
        content.addView(r3, LinearLayout.LayoutParams(-1, -2))
    }

    private fun surprise() {
        val t = Themes.ALL.random()
        val changed = Themes.apply(p, t)
        if (Math.random() < 0.5) p.colorInt = (Prefs.PALETTE + Prefs.PALETTE2).random()
        p.speed = 0.7f + Math.random().toFloat()
        ui.toast("✦ " + t.name)
        if (changed) recreate() else showTab(tab)
    }

    private fun saveFrame() {
        ui.toast("Рисую кадр…")
        Thread {
            val dm = resources.displayMetrics
            val bmp = Bitmap.createBitmap(dm.widthPixels, dm.heightPixels, Bitmap.Config.ARGB_8888)
            val rr = Renderer(this, p)
            rr.reload()
            rr.render(Canvas(bmp), bmp.width.toFloat(), bmp.height.toFloat(), 9.5f)
            val bo = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bo)
            pendingBytes = bo.toByteArray()
            runOnUiThread { createDoc("poipaper.png", "image/png") }
        }.start()
    }

    // ================= ДИЗАЙН =================
    private fun pageDesign() {
        previewCard(0.26f)
        val a = vbox()
        ui.seg(content, listOf("Сцена", "Фигуры", "Цвет", "Движение", "Место", "Эффекты", "Батарея"), sub) { i -> sub = i; fillDesign(a) }
        content.addView(a)
        fillDesign(a)
    }

    private fun fillDesign(a: LinearLayout) {
        a.removeAllViews()
        when (sub) { 0 -> dsScene(a); 1 -> dsShapes(a); 2 -> dsColor(a); 3 -> dsMotion(a); 4 -> dsPlace(a); 5 -> dsFx(a); else -> dsBattery(a) }
    }

    private fun dsScene(a: LinearLayout) {
        Gallery.scenes(this, ui, a, p) { fillDesign(a) }
        if (p.scene == 0) return
        ui.card(a, "", Formats.SCENES[p.scene]).let {
            ui.slider(it, "скорость", 0.3f, 2.5f, p.speed) { v -> p.speed = v }
            ui.slider(it, "плотность частиц", 0.4f, 1.5f, p.density) { v -> p.density = v }
            if (p.scene == 2) {
                ui.chips(it, listOf("Мишка", "Моя фигура"), p.target) { i -> p.target = i }
                ui.field(it, "Надпись на сердце", p.label) { s -> p.label = s.take(14) }
            }
            if (p.scene == 3) {
                ui.field(it, "Слова через запятую", p.words) { s -> p.words = s.take(200) }
                ui.field(it, "Письмо (печатается внизу)", p.message) { s -> p.message = s.take(120) }
            }
        }
    }

    private fun curId() = if (target == 1) p.shapeB else p.shapeA

    private fun setTarget(id: String) {
        if (target == 1) p.shapeB = id else p.shapeA = id
        p.stamp = System.currentTimeMillis()
    }

    private fun dsShapes(a: LinearLayout) {
        ui.card(a, "", "Фигура").let {
            ui.chips(it, listOf("Фигура 1", "Фигура 2"), target) { i -> target = i; fillDesign(a) }
            if (target == 1 && p.shapeB.isNotEmpty()) ui.btn(it, "УБРАТЬ ФИГУРУ 2", false) { p.shapeB = ""; fillDesign(a) }
            val names = Shapes.NAMES
            var s = 0
            while (s < names.size) {
                val part = names.subList(s, minOf(s + 4, names.size))
                val cur = curId()
                var sel = -1
                for (k in part.indices) if (cur == "b:" + (s + k)) sel = k
                val base = s
                ui.chips(it, part, sel) { k -> setTarget("b:" + (base + k)); fillDesign(a) }
                s += 4
            }
        }
        ui.card(a, "", "Мои фигуры").let {
            for (f in Shapes.list(this)) {
                val row = LinearLayout(this)
                row.gravity = Gravity.CENTER_VERTICAL
                val tv = TextView(this)
                tv.text = (if (curId() == f.first) "● " else "○ ") + f.second
                tv.setTextColor(if (curId() == f.first) ui.TXT else ui.DIM); tv.textSize = 15f
                tv.setOnClickListener { setTarget(f.first); fillDesign(a) }
                row.addView(tv, LinearLayout.LayoutParams(0, -2, 1f))
                ui.small(row, "ЭКСПОРТ") { exportPoi(f.first, f.second) }
                ui.small(row, "УДАЛИТЬ") {
                    Shapes.delete(this, f.first)
                    if (p.shapeA == f.first) p.shapeA = "b:0"
                    if (p.shapeB == f.first) p.shapeB = ""
                    fillDesign(a)
                }
                it.addView(row, LinearLayout.LayoutParams(-1, ui.dp(44)))
            }
            ui.btn(it, "ИЗ ФОТО (РЕДАКТОР)", true) {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                i.addCategory(Intent.CATEGORY_OPENABLE); i.type = "image/*"
                startActivityForResult(i, 1)
            }
            ui.btn(it, "ИЗ ТЕКСТА", false) {
                ui.prompt("Текст фигуры", "до 14 символов", "POI") { t -> if (t.isNotEmpty()) { setTarget("t:" + t.take(14)); fillDesign(a) } }
            }
        }
    }

    private fun dsColor(a: LinearLayout) {
        ui.card(a, "", "Цвет").let {
            ui.swatches(it, Prefs.PALETTE, Prefs.PALETTE.indexOf(p.colorInt)) { i -> p.colorInt = Prefs.PALETTE[i]; p.rainbow = false; fillDesign(a) }
            ui.swatches(it, Prefs.PALETTE2, Prefs.PALETTE2.indexOf(p.colorInt)) { i -> p.colorInt = Prefs.PALETTE2[i]; p.rainbow = false; fillDesign(a) }
            ui.field(it, "Свой цвет, например #FF8800", String.format("#%06X", p.colorInt and 0xFFFFFF)) { s ->
                val m = Regex("^#?([0-9A-Fa-f]{6})$").find(s.trim())
                if (m != null) { p.colorInt = 0xFF000000.toInt() or m.groupValues[1].toInt(16); p.rainbow = false }
            }
            if (p.scene == 0) ui.chips(it, listOf("Один цвет", "Радуга"), if (p.rainbow) 1 else 0) { i -> p.rainbow = (i == 1) }
        }
    }

    private fun dsMotion(a: LinearLayout) {
        ui.card(a, "", "Движение").let {
            if (p.scene == 0) ui.chips(it, listOf("Вращение", "Покачивание"), p.mode) { i -> p.mode = i }
            ui.slider(it, "скорость", 0.2f, 3f, p.speed) { v -> p.speed = v }
            if (p.scene == 0) {
                ui.slider(it, "глубина 3D", 0f, 2f, p.depth) { v -> p.depth = v }
                ui.slider(it, "толщина линий", 0.5f, 3f, p.lineW) { v -> p.lineW = v }
            }
            ui.toggle(it, "Рябь при касании", p.touch) { v -> p.touch = v }
            ui.toggle(it, "Параллакс при листании", p.parallax) { v -> p.parallax = v }
        }
    }

    private fun dsPlace(a: LinearLayout) {
        ui.card(a, "", "Размер и место").let {
            ui.slider(it, "размер", 0.4f, 2f, p.scale) { v -> p.scale = v }
            ui.slider(it, "по горизонтали", 0f, 1f, p.posX, { v -> pct(v) }) { v -> p.posX = v }
            ui.slider(it, "по вертикали", 0f, 1f, p.posY, { v -> pct(v) }) { v -> p.posY = v }
            ui.slider(it, "яркость", 0.2f, 1f, p.bright, { v -> pct(v) }) { v -> p.bright = v }
            ui.toggle(it, "Ночью приглушать (22:00–07:00)", p.night) { v -> p.night = v }
            spacer(it, 8)
            ui.btn(it, "ДВИГАТЬ ПАЛЬЦЕМ НА ЭКРАНЕ", true) { startActivity(Intent(this, LayoutActivity::class.java)) }
            ui.btn(it, "СБРОСИТЬ ПОЛОЖЕНИЕ", false) { p.posX = 0.5f; p.posY = 0.46f; p.scale = 1f; fillDesign(a) }
        }
    }

    private fun dsFx(a: LinearLayout) {
        ui.card(a, "", "Фон и эффекты").let {
            ui.chips(it, listOf("Чёрный", "Градиент", "Сетка", "Звёзды", "Дождь"), p.bg) { i -> p.bg = i }
            if (p.scene == 0) ui.chips(it, listOf("Свечение вкл", "Свечение выкл"), if (p.glow) 0 else 1) { i -> p.glow = (i == 0) }
        }
    }

    private fun dsBattery(a: LinearLayout) {
        ui.card(a, "", "Плавность и экономия").let {
            val f = listOf(30, 45, 60, 90, 120)
            ui.chips(it, f.map { x -> "$x" }, f.indexOf(p.fps).coerceAtLeast(0)) { i -> p.fps = f[i] }
            val ecos = listOf(0, 30, 60, 120)
            ui.chips(it, listOf("Не замирать", "30 с", "1 мин", "2 мин"), ecos.indexOf(p.eco).coerceAtLeast(0)) { i -> p.eco = ecos[i] }
            ui.chips(it, listOf("Лёгкое", "Баланс", "Красивое"), p.quality) { i -> p.quality = i }
            ui.toggle(it, "Экономить при низком заряде", p.saver) { v -> p.saver = v }
        }
    }

    // ================= ВИДЖЕТЫ =================
    private fun pageWidgets() {
        previewCard(0.26f)
        val wl = Widgets.load(p)
        if (editing in wl.indices) { widgetEditor(wl, editing); return }
        ui.card(content, "", "Виджеты на экране: " + wl.size).let { c ->
            for (i in wl.indices) {
                val w = wl[i]
                val row = LinearLayout(this)
                row.gravity = Gravity.CENTER_VERTICAL
                val dot = View(this)
                val g = GradientDrawable()
                g.setShape(GradientDrawable.OVAL); g.setColor(w.color)
                dot.background = g
                row.addView(dot, LinearLayout.LayoutParams(ui.dp(14), ui.dp(14)).apply { marginEnd = ui.dp(10) })
                val tv = TextView(this)
                tv.text = Widgets.TYPES[w.type] + " · " + Widgets.STYLES[w.type][w.style]
                tv.setTextColor(ui.TXT); tv.textSize = 15f
                tv.setOnClickListener { editing = i; wsub = 0; showTab(2) }
                row.addView(tv, LinearLayout.LayoutParams(0, -2, 1f))
                ui.small(row, "ИЗМЕНИТЬ") { editing = i; wsub = 0; showTab(2) }
                c.addView(row, LinearLayout.LayoutParams(-1, ui.dp(44)))
            }
            if (wl.size < Widgets.MAX) ui.btn(c, "+ ДОБАВИТЬ ВИДЖЕТ", true) {
                AlertDialog.Builder(this).setTitle("Тип виджета").setItems(Widgets.TYPES.toTypedArray()) { _, k ->
                    wl.add(Widgets.make(k, p.colorInt, wl.size)); Widgets.save(p, wl); editing = wl.size - 1; wsub = 0; showTab(2)
                }.show()
            }
            if (wl.isNotEmpty()) ui.btn(c, "ПЕРЕМЕСТИТЬ ПАЛЬЦЕМ", false) { startActivity(Intent(this, LayoutActivity::class.java)) }
        }
    }

    private fun widgetEditor(wl: MutableList<Wdg>, idx: Int) {
        val w = wl[idx]
        val a = vbox()
        ui.card(content, "", Widgets.TYPES[w.type]).let { c ->
            ui.seg(c, listOf("Вид", "Цвет", "Место"), wsub) { i -> wsub = i; fillWidget(a, wl, idx) }
            c.addView(a)
            fillWidget(a, wl, idx)
            spacer(c, 6)
            ui.btn(c, "УДАЛИТЬ ВИДЖЕТ", false) { wl.removeAt(idx); Widgets.save(p, wl); editing = -1; showTab(2) }
            ui.btn(c, "ГОТОВО", true) { editing = -1; showTab(2) }
        }
    }

    private fun fillWidget(a: LinearLayout, wl: MutableList<Wdg>, idx: Int) {
        a.removeAllViews()
        val w = wl[idx]
        fun save() { Widgets.save(p, wl) }
        when (wsub) {
            0 -> {
                if (Widgets.STYLES[w.type].size > 1) ui.chips(a, Widgets.STYLES[w.type], w.style) { i -> w.style = i; save() }
                ui.chips(a, Widgets.ANIMS, w.anim) { i -> w.anim = i; save() }
                ui.chips(a, Widgets.FONTS, w.font) { i -> w.font = i; save() }
            }
            1 -> {
                ui.swatches(a, Prefs.PALETTE, Prefs.PALETTE.indexOf(w.color)) { i -> w.color = Prefs.PALETTE[i]; save(); fillWidget(a, wl, idx) }
                ui.swatches(a, Prefs.PALETTE2, Prefs.PALETTE2.indexOf(w.color)) { i -> w.color = Prefs.PALETTE2[i]; save(); fillWidget(a, wl, idx) }
                ui.slider(a, "прозрачность", 0.2f, 1f, w.op, { v -> pct(v) }) { v -> w.op = v; save() }
                ui.toggle(a, "Свечение", w.glow) { v -> w.glow = v; save() }
                ui.chips(a, Widgets.PLATES, w.plate) { i -> w.plate = i; save() }
            }
            else -> {
                ui.slider(a, "размер", 0.4f, 3f, w.size) { v -> w.size = v; save() }
                ui.slider(a, "поворот", -45f, 45f, w.rot, { v -> "" + v.toInt() + "°" }) { v -> w.rot = v; save() }
                ui.slider(a, "по горизонтали", 0f, 1f, w.x, { v -> pct(v) }) { v -> w.x = v; save() }
                ui.slider(a, "по вертикали", 0f, 1f, w.y, { v -> pct(v) }) { v -> w.y = v; save() }
                if (w.type == 3) ui.field(a, "Текст", w.text) { s -> w.text = s.take(40); save() }
                if (w.type == 7) {
                    val parts = w.text.split(';')
                    val date = parts[0]
                    ui.field(a, "Название события", parts.getOrElse(1) { "" }) { s -> w.text = w.text.split(';')[0] + ";" + s.take(20); save() }
                    ui.btn(a, "ДАТА: $date", false) {
                        val d = try { LocalDate.parse(date) } catch (e: Exception) { LocalDate.now() }
                        DatePickerDialog(this, { _, y, m, dd ->
                            w.text = String.format("%04d-%02d-%02d", y, m + 1, dd) + ";" + w.text.split(';').getOrElse(1) { "" }
                            save(); fillWidget(a, wl, idx)
                        }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                    }
                }
                if (w.type == 9) {
                    val parts = w.text.split(';')
                    ui.field(a, "Название города", parts[0]) { s -> w.text = s.take(16) + ";" + w.text.split(';').getOrElse(1) { "0" }; save() }
                    val off = parts.getOrElse(1) { "0" }.trim().toIntOrNull() ?: 0
                    ui.slider(a, "часовой пояс", -12f, 14f, off.toFloat(), { v -> "UTC" + (if (v >= 0) "+" else "") + v.toInt() }) { v ->
                        w.text = w.text.split(';')[0] + ";" + v.toInt(); save()
                    }
                }
                spacer(a, 6)
                ui.btn(a, "ПЕРЕМЕСТИТЬ ПАЛЬЦЕМ", true) { startActivity(Intent(this, LayoutActivity::class.java).putExtra("sel", idx)) }
            }
        }
    }

    // ================= ТЕМЫ =================
    private fun pageThemes() {
        previewCard(0.22f)
        val a = vbox()
        ui.seg(content, listOf("Обои", "Сборки", "Приложение"), tsub) { i -> tsub = i; fillThemes(a) }
        content.addView(a)
        fillThemes(a)
    }

    private fun fillThemes(a: LinearLayout) {
        a.removeAllViews()
        when (tsub) {
            0 -> Gallery.themes(this, ui, a, p) { changed -> if (changed) recreate() else pv?.invalidate() }
            1 -> {
                val list = Presets.list(this)
                ui.card(a, "", "Мои сборки").let { c ->
                    for (f in list) {
                        val row = LinearLayout(this)
                        row.gravity = Gravity.CENTER_VERTICAL
                        val tv = TextView(this)
                        tv.text = f.name; tv.setTextColor(ui.TXT); tv.textSize = 15f
                        tv.setOnClickListener { Presets.apply(this, p, f.file); r.reload(); ui.toast(f.name) }
                        row.addView(tv, LinearLayout.LayoutParams(0, -2, 1f))
                        ui.small(row, "ПРИМЕНИТЬ") { Presets.apply(this, p, f.file); r.reload(); ui.toast(f.name) }
                        ui.small(row, "УДАЛИТЬ") { Presets.delete(this, f.file); fillThemes(a) }
                        c.addView(row, LinearLayout.LayoutParams(-1, ui.dp(44)))
                    }
                    ui.btn(c, "+ СОХРАНИТЬ ТЕКУЩИЕ ОБОИ", true) {
                        ui.prompt("Название сборки", "например: Вечерняя", "") { n -> if (n.isNotEmpty()) { Presets.save(this, p, n); fillThemes(a) } }
                    }
                }
                ui.card(a, "", "Автосмена").let { c ->
                    ui.chips(c, listOf("Выкл", "Каждый раз", "Каждый час", "Каждый день"), p.shuffle) { i ->
                        p.shuffle = i
                        if (i > 0 && list.size < 2) ui.toast("Сохрани минимум 2 сборки")
                    }
                }
            }
            else -> {
                ui.card(a, "", "Оформление").let { c ->
                    for (i in Skin.ALL.indices) {
                        val sd = Skin.ALL[i]
                        val row = LinearLayout(this)
                        row.gravity = Gravity.CENTER_VERTICAL
                        row.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8))
                        row.background = ui.bg(sd.panel2, if (p.skin == i) ui.G else sd.line, 16)
                        val dot = View(this)
                        val g = GradientDrawable()
                        g.setShape(GradientDrawable.OVAL); g.setColor(sd.accent)
                        dot.background = g
                        row.addView(dot, LinearLayout.LayoutParams(ui.dp(22), ui.dp(22)))
                        val tv = TextView(this)
                        tv.text = sd.name; tv.setTextColor(sd.text); tv.textSize = 16f; tv.setPadding(ui.dp(12), 0, 0, 0)
                        row.addView(tv)
                        row.setOnClickListener { ui.hap(row); p.skin = i; p.accentCustom = 0; recreate() }
                        c.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(8) })
                    }
                }
                ui.card(a, "", "Свой акцент").let { c ->
                    ui.swatches(c, Prefs.PALETTE, Prefs.PALETTE.indexOf(p.accentCustom)) { i -> p.accentCustom = Prefs.PALETTE[i]; recreate() }
                    ui.swatches(c, Prefs.PALETTE2, Prefs.PALETTE2.indexOf(p.accentCustom)) { i -> p.accentCustom = Prefs.PALETTE2[i]; recreate() }
                    ui.btn(c, "АКЦЕНТ ИЗ ТЕМЫ", false) { p.accentCustom = 0; recreate() }
                }
                ui.card(a, "", "Углы").let { c ->
                    ui.chips(c, listOf("Острые", "Мягкие", "Круглые"), p.corners) { i -> p.corners = i; recreate() }
                    ui.toggle(c, "Менять оформление вместе с темой обоев", p.skinWithTheme) { v -> p.skinWithTheme = v }
                }
            }
        }
    }

    // ================= ПРОФИЛЬ =================
    private fun pageProfile() {
        ui.card(content, "", "Профиль").let { c ->
            val row = LinearLayout(this)
            row.gravity = Gravity.CENTER_VERTICAL
            val av = ImageView(this)
            av.setImageResource(R.drawable.ic_avatar)
            row.addView(av, LinearLayout.LayoutParams(ui.dp(64), ui.dp(64)))
            val col = vbox()
            col.setPadding(ui.dp(12), 0, 0, 0)
            ui.field(col, "Твой ник", p.author) { s -> p.author = s.take(24).ifEmpty { "аноним" } }
            row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            c.addView(row)
        }
        ui.card(content, "", "Файлы и обмен").let {
            ui.btn(it, "ИМПОРТ .poa / .poi", true) { pickFile() }
            ui.btn(it, "ЭКСПОРТ ТЕКУЩИХ ОБОЕВ В .poa", false) { exportPoa() }
            ui.btn(it, "ОТКАТИТЬ ПОСЛЕДНИЙ ПАК", false) {
                if (p.restoreBackup()) { r.reload(); ui.toast("Откат выполнен"); showTab(4) } else ui.toast("Нечего откатывать")
            }
        }
    }

    // ================= Файлы =================
    private fun pickFile() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE); i.type = "*/*"
        startActivityForResult(i, 2)
    }

    private fun createDoc(fileName: String, mime: String = "application/octet-stream") {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE); i.type = mime
        i.putExtra(Intent.EXTRA_TITLE, fileName)
        startActivityForResult(i, 3)
    }

    private fun exportPoi(id: String, name: String) {
        val m = Shapes.resolve(this, id)
        if (m == null) { ui.toast("Не удалось прочитать фигуру"); return }
        pendingBytes = Formats.poiToText(name, m).toByteArray(Charsets.UTF_8)
        createDoc("$name.poi")
    }

    private fun exportPoa() {
        ui.prompt("Название пака", "например: Неоновая чёрная дыра", "") { name ->
            if (name.isNotEmpty()) {
                AlertDialog.Builder(this).setTitle("Категория").setItems(Formats.CATEGORIES.toTypedArray()) { _, w ->
                    pendingBytes = Formats.buildPoa(p, Shapes.nameOf(p.shapeA), Shapes.resolve(this, p.shapeA), name, p.author, Formats.CATEGORIES[w], "").toByteArray(Charsets.UTF_8)
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
                contentResolver.openOutputStream(uri)?.use { it.write(pendingBytes ?: ByteArray(0)) }
                pendingBytes = null
                ui.toast("Файл сохранён")
            } catch (e: Exception) { ui.toast("Не удалось сохранить файл") }
        }
    }
}
