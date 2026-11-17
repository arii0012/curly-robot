package com.codewall
import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.*

class PreviewView(ctx: Context, private val r: Renderer) : View(ctx) {
    private val t0 = SystemClock.uptimeMillis()
    override fun onDraw(c: Canvas) {
        r.render(c, width.toFloat(), height.toFloat(), (SystemClock.uptimeMillis() - t0) / 1000f, 0.5f)
        postInvalidateOnAnimation()
    }
}

class MainActivity : Activity() {
    private lateinit var p: Prefs
    private lateinit var r: Renderer
    private lateinit var ui: Ui
    private lateinit var preview: PreviewView
    private lateinit var content: LinearLayout
    private lateinit var sv: ScrollView
    private var tab = 0
    private var target = 0
    private var pendingText = ""
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
        if (k == "shapeA" || k == "shapeB" || k == "stamp" || k == "quality") r.reload()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Prefs(this); r = Renderer(this, p); ui = Ui(this)
        val bgc = Color.parseColor("#070B09")
        window.statusBarColor = bgc; window.navigationBarColor = bgc

        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(bgc)
        page.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(12))

        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        val av = ImageView(this)
        av.setImageResource(R.drawable.ic_avatar)
        head.addView(av, LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(ui.dp(12), 0, 0, 0)
        val t1 = TextView(this)
        t1.text = "PoiPaper"; t1.setTextColor(Color.WHITE); t1.textSize = 20f; t1.typeface = Typeface.DEFAULT_BOLD
        val t2 = TextView(this)
        t2.text = "живые 3D-обои из кода"; t2.setTextColor(ui.DIM); t2.textSize = 12f
        col.addView(t1); col.addView(t2)
        head.addView(col)
        page.addView(head, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(8) })

        preview = PreviewView(this, r)
        preview.background = ui.bg(Color.BLACK, ui.LINE, 24)
        preview.clipToOutline = true
        preview.outlineProvider = ViewOutlineProvider.BACKGROUND
        val ph = (resources.displayMetrics.heightPixels * 0.28f).toInt()
        page.addView(preview, LinearLayout.LayoutParams(-1, ph).apply { bottomMargin = ui.dp(8) })

        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.VERTICAL
        ui.chips(tabs, listOf("Обои", "Фигуры", "Темы", "Виджеты", "Ещё"), 0) { i -> showTab(i) }
        page.addView(tabs)

        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(content)
        page.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))

        val install = Button(this)
        install.text = "УСТАНОВИТЬ КАК ОБОИ"
        install.setTextColor(Color.BLACK)
        install.background = ui.bg(ui.G, ui.G, 20)
        install.setOnClickListener {
            val i = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this@MainActivity, CodeWallpaperService::class.java))
            try { startActivity(i) } catch (e: Exception) { startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
        }
        page.addView(install, LinearLayout.LayoutParams(-1, ui.dp(56)).apply { topMargin = ui.dp(8) })
        setContentView(page)
        showTab(0)
    }

    override fun onResume() {
        super.onResume()
        p.sp.registerOnSharedPreferenceChangeListener(listener)
        r.reload()
        preview.invalidate()
    }

    override fun onPause() {
        p.sp.unregisterOnSharedPreferenceChangeListener(listener)
        super.onPause()
    }

    private fun showTab(i: Int) {
        tab = i
        content.removeAllViews()
        sv.scrollTo(0, 0)
        when (i) { 0 -> tabLook(); 1 -> tabShapes(); 2 -> tabThemes(); 3 -> tabWidgets(); else -> tabMore() }
    }

    // ---------- Вкладка «Обои» ----------
    private fun tabLook() {
        ui.card(content, "01 / ДВИЖЕНИЕ", "Вращение").let {
            ui.chips(it, listOf("Вращение", "Покачивание"), p.mode) { i -> p.mode = i }
            ui.slider(it, "скорость", 0.2f, 3f, p.speed) { v -> p.speed = v }
        }
        ui.card(content, "02 / ОБЪЁМ", "3D, линии и размер").let {
            ui.slider(it, "глубина 3D", 0f, 2f, p.depth) { v -> p.depth = v }
            ui.slider(it, "масштаб фигур", 0.4f, 1.5f, p.scale) { v -> p.scale = v }
            ui.slider(it, "толщина линий", 0.5f, 3f, p.lineW) { v -> p.lineW = v }
        }
        ui.card(content, "03 / ЦВЕТ", "Цвет и фон").let {
            ui.swatches(it, Prefs.PALETTE, Prefs.PALETTE.indexOf(p.colorInt)) { i -> p.colorInt = Prefs.PALETTE[i]; p.rainbow = false }
            ui.chips(it, listOf("Один цвет", "Радуга"), if (p.rainbow) 1 else 0) { i -> p.rainbow = (i == 1) }
            ui.chips(it, listOf("Свечение вкл", "Свечение выкл"), if (p.glow) 0 else 1) { i -> p.glow = (i == 0) }
            ui.chips(it, listOf("Чёрный", "Градиент", "Сетка"), p.bg) { i -> p.bg = i }
        }
        ui.card(content, "04 / ПЛАВНОСТЬ", "Частота кадров").let {
            val f = listOf(30, 45, 60, 90, 120)
            ui.chips(it, f.map { x -> "$x" }, f.indexOf(p.fps).coerceAtLeast(2)) { i -> p.fps = f[i] }
            ui.note(it, "Выше частота — плавнее, но больше расход батареи. 60 FPS — баланс.")
        }
    }

    // ---------- Вкладка «Фигуры» ----------
    private fun curId() = if (target == 1) p.shapeB else p.shapeA

    private fun setTarget(id: String) {
        if (target == 1) p.shapeB = id else p.shapeA = id
        p.stamp = System.currentTimeMillis()
    }

    private fun tabShapes() {
        ui.card(content, "01 / ВЫБОР", "Какую фигуру менять").let {
            ui.chips(it, listOf("Фигура 1", "Фигура 2"), target) { i -> target = i; showTab(1) }
            if (target == 1) ui.btn(it, "УБРАТЬ ФИГУРУ 2", false) { p.shapeB = ""; showTab(1) }
            ui.note(it, if (target == 1 && p.shapeB.isEmpty()) "Фигура 2 выключена. Выбери любую ниже, чтобы показать две фигуры." else "Текущая: " + Shapes.nameOf(curId()))
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
            val mine = Shapes.list(this)
            if (mine.isEmpty()) ui.note(it, "Пока пусто. Создай фигуру из фото или импортируй .poi.")
            for (f in mine) {
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
        }
        ui.card(content, "04 / СОЗДАТЬ", "Новая фигура").let {
            ui.btn(it, "ИЗ ФОТО (РЕДАКТОР)", true) {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                i.addCategory(Intent.CATEGORY_OPENABLE); i.type = "image/*"
                startActivityForResult(i, 1)
            }
            ui.btn(it, "ИЗ ТЕКСТА", false) {
                ui.prompt("Текст фигуры", "до 14 символов", "POI") { t ->
                    if (t.isNotEmpty()) { setTarget("t:" + t.take(14)); showTab(1) }
                }
            }
            ui.btn(it, "ИМПОРТ .poi / .poa", false) { pickFile() }
        }
    }

    // ---------- Вкладка «Темы» ----------
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
                row.setOnClickListener { Themes.apply(p, t); ui.toast("Тема «" + t.name + "» применена") }
                c.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(8) })
            }
        }
    }

    // ---------- Вкладка «Виджеты» ----------
    private fun tabWidgets() {
        ui.card(content, "ВИДЖЕТЫ", "Что показывать на обоях").let {
            ui.toggle(it, "Часы", p.wClock) { v -> p.wClock = v }
            ui.toggle(it, "Дата", p.wDate) { v -> p.wDate = v }
            ui.toggle(it, "Заряд батареи", p.wBattery) { v -> p.wBattery = v }
            val sp = View(this); it.addView(sp, LinearLayout.LayoutParams(-1, ui.dp(8)))
            ui.field(it, "Свой текст (до 24 символов)", p.wText) { s -> p.wText = s.take(24) }
            val sp2 = View(this); it.addView(sp2, LinearLayout.LayoutParams(-1, ui.dp(10)))
            ui.chips(it, listOf("Сверху", "Снизу"), p.wPos) { i -> p.wPos = i }
            ui.note(it, "Если виджеты перекрывают фигуру, уменьши масштаб на вкладке «Обои».")
        }
    }

    // ---------- Вкладка «Ещё» ----------
    private fun tabMore() {
        ui.card(content, "01 / ПРОИЗВОДИТЕЛЬНОСТЬ", "Чтобы не лагало").let {
            ui.chips(it, listOf("Лёгкое", "Баланс", "Красивое"), p.quality) { i -> p.quality = i }
            ui.toggle(it, "Экономия при низком заряде", p.saver) { v -> p.saver = v }
            ui.note(it, "«Лёгкое» рисует меньше символов. В режиме экономии системы частота кадров снижается до 30.")
        }
        ui.card(content, "02 / ПАКИ", "Файлы .poa").let {
            ui.field(it, "Твой ник автора", p.author) { s -> p.author = s.take(24).ifEmpty { "аноним" } }
            val sp = View(this); it.addView(sp, LinearLayout.LayoutParams(-1, ui.dp(10)))
            ui.btn(it, "ИМПОРТ .poa / .poi", true) { pickFile() }
            ui.btn(it, "ЭКСПОРТ ТЕКУЩИХ ОБОЕВ В .poa", false) { exportPoa() }
            ui.note(it, "Пак хранит фигуру, цвета, движение и виджеты. Перед применением ты видишь, что внутри, и можешь снять галочки с лишнего.")
        }
        ui.card(content, "03 / БЕЗОПАСНОСТЬ", "Проверка файлов").let {
            ui.note(it, "Файлы .poi и .poa — только данные, кода в них нет. Приложение принимает лишь известные поля, отклоняет ссылки, слишком большие файлы и значения вне диапазона. Разрешений у приложения нет: оно не просит ни доступ к файлам, ни интернет.")
        }
        ui.card(content, "04 / WORKSHOP", "Общая галерея").let {
            ui.note(it, "Скоро: каталог паков с категориями, вход через Google или почту, загрузка своих работ с проверкой перед публикацией.")
        }
        ui.card(content, "О ПРИЛОЖЕНИИ", "PoiPaper 3.0").let {
            ui.note(it, "Живые 3D-обои из строк кода. Фигуры, темы, виджеты, редактор фото и свои форматы .poi и .poa.")
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
        ui.prompt("Название пака", "например: Неоновый череп", "") { name ->
            if (name.isNotEmpty()) {
                AlertDialog.Builder(this).setTitle("Категория")
                    .setItems(Formats.CATEGORIES.toTypedArray()) { _, w ->
                        val m = Shapes.resolve(this, p.shapeA)
                        pendingText = Formats.buildPoa(p, Shapes.nameOf(p.shapeA), m, name, p.author, Formats.CATEGORIES[w], "")
                        createDoc("$name.poa")
                    }.show()
            }
        }
    }

    private fun importFile(uri: Uri) {
        try {
            val text = Formats.readText(contentResolver, uri)
            when (Formats.kind(text)) {
                "poa" -> showPoa(Formats.parsePoa(text))
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

    private fun showPoa(poa: Formats.Poa) {
        val items = Formats.items(poa)
        val labels = items.map { it.second }.toTypedArray()
        val checked = BooleanArray(items.size) { true }
        AlertDialog.Builder(this)
            .setTitle(poa.name + "\n" + poa.author + " · " + poa.category)
            .setMultiChoiceItems(labels, checked) { _, which, v -> checked[which] = v }
            .setPositiveButton("ПРИМЕНИТЬ") { _, _ ->
                val keys = HashSet<String>()
                for (k in items.indices) if (checked[k]) keys.add(items[k].first)
                Formats.applyPoa(p, this, poa, keys)
                r.reload()
                ui.toast("Применено")
                showTab(tab)
            }
            .setNegativeButton("Отмена", null).show()
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
