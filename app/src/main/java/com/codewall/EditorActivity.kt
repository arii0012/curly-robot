package com.codewall
import android.app.Activity
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.media.ExifInterface
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.*
import kotlin.math.max
import kotlin.math.min

class EditorView(ctx: Context) : View(ctx) {
    val cols = Shapes.COLS
    var rows = 0
    var on = BooleanArray(0)
    var photo: Bitmap? = null
    var tool = 0
    var brush = 3
    private val hist = ArrayList<BooleanArray>()
    private var maskBmp: Bitmap? = null
    private val dst = RectF()
    private val paint = Paint()
    private val selPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.WHITE }
    private var sx = 0; private var sy = 0; private var ex = 0; private var ey = 0
    private var selecting = false
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var multi = false
    private var lastMx = 0f
    private var lastMy = 0f
    private val sd = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            zoom = (zoom * d.scaleFactor).coerceIn(1f, 6f)
            invalidate()
            return true
        }
    })

    /** Вернуть масштаб и положение холста. */
    fun resetView() { zoom = 1f; panX = 0f; panY = 0f; invalidate() }

    fun setData(newOn: BooleanArray, newRows: Int, ph: Bitmap) {
        on = newOn; rows = newRows; photo = ph; hist.clear(); rebuild(); invalidate()
    }

    fun mask(): Mask? = if (rows > 0 && on.any { it }) Mask(rows, on.copyOf()) else null

    private fun rebuild() {
        if (rows == 0) return
        val old = maskBmp
        val b = if (old != null && old.height == rows) old else Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
        maskBmp = b
        val px = IntArray(cols * rows) { if (on[it]) 0xFF4DFF88.toInt() else 0 }
        b.setPixels(px, 0, cols, 0, 0, cols, rows)
    }

    private fun push() { hist.add(on.copyOf()); if (hist.size > 20) hist.removeAt(0) }
    fun undo() { if (hist.isNotEmpty()) { on = hist.removeAt(hist.size - 1); rebuild(); invalidate() } }
    fun fillAll(v: Boolean) { push(); on.fill(v); rebuild(); invalidate() }

    private fun dab(cx: Int, cy: Int, v: Boolean) {
        val rr = brush * 0.8f
        val reach = brush * 2
        for (dy in -reach..reach) for (dx in -reach..reach) {
            val d = (dx * 0.6f) * (dx * 0.6f) + (dy * 0.8f) * (dy * 0.8f)
            if (d > rr * rr) continue
            val x = cx + dx; val y = cy + dy
            if (x in 0 until cols && y in 0 until rows) on[y * cols + x] = v
        }
        rebuild()
    }

    private fun fillRect(v: Boolean) {
        for (y in min(sy, ey)..max(sy, ey)) for (x in min(sx, ex)..max(sx, ex)) on[y * cols + x] = v
        rebuild()
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.parseColor("#050807"))
        if (rows == 0) return
        val ar = (cols * 0.6f) / (rows * 0.8f)
        var w = width.toFloat(); var h = w / ar
        if (h > height) { h = height.toFloat(); w = h * ar }
        val zc = width / 2f + panX; val zy = height / 2f + panY
        dst.set(zc - w * zoom / 2, zy - h * zoom / 2, zc + w * zoom / 2, zy + h * zoom / 2)
        paint.isFilterBitmap = false
        photo?.let { paint.alpha = 90; c.drawBitmap(it, null, dst, paint) }
        maskBmp?.let { paint.alpha = 210; c.drawBitmap(it, null, dst, paint) }
        if (selecting) {
            val cwid = dst.width() / cols; val chei = dst.height() / rows
            c.drawRect(dst.left + min(sx, ex) * cwid, dst.top + min(sy, ey) * chei,
                dst.left + (max(sx, ex) + 1) * cwid, dst.top + (max(sy, ey) + 1) * chei, selPaint)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (rows == 0) return true
        sd.onTouchEvent(e)
        if (e.pointerCount >= 2 || sd.isInProgress) {
            // два пальца: масштаб и сдвиг холста, рисование в это время не идёт
            multi = true; selecting = false
            val mx = if (e.pointerCount >= 2) (e.getX(0) + e.getX(1)) / 2f else e.x
            val my = if (e.pointerCount >= 2) (e.getY(0) + e.getY(1)) / 2f else e.y
            if (e.actionMasked == MotionEvent.ACTION_MOVE) {
                panX = (panX + mx - lastMx).coerceIn(-width * zoom, width * zoom)
                panY = (panY + my - lastMy).coerceIn(-height * zoom, height * zoom)
            }
            lastMx = mx; lastMy = my
            invalidate()
            return true
        }
        if (multi) {
            if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) multi = false
            return true
        }
        val cx = ((e.x - dst.left) / dst.width() * cols).toInt().coerceIn(0, cols - 1)
        val cy = ((e.y - dst.top) / dst.height() * rows).toInt().coerceIn(0, rows - 1)
        val value = tool == 0 || tool == 2
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                push()
                if (tool < 2) dab(cx, cy, value) else { sx = cx; sy = cy; ex = cx; ey = cy; selecting = true }
            }
            MotionEvent.ACTION_MOVE -> { if (tool < 2) dab(cx, cy, value) else { ex = cx; ey = cy } }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (tool >= 2 && selecting) { fillRect(value); selecting = false }
            }
        }
        invalidate()
        return true
    }
}

class EditorActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var p: Prefs
    private lateinit var ev: EditorView
    private lateinit var list: LinearLayout
    private var src: Bitmap? = null
    private var method = 0
    private var thr = 90
    private var smooth = 1
    private var despeckle = 2
    private var fill = false
    private var inv = false

    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (o.outWidth / s > 1024 || o.outHeight / s > 1024) s *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = s }
            var bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            // поворот по EXIF: фото с телефона больше не «лежат на боку»
            val orient = try {
                contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } ?: 1
            } catch (e: Exception) { 1 }
            val deg = when (orient) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
            if (deg != 0f) {
                val m = Matrix(); m.postRotate(deg)
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            bmp
        } catch (e: Throwable) { null }
    }

    private fun regen() {
        val s = src ?: return
        val r = Shapes.analyze(s, method, thr, inv, smooth, fill, despeckle)
        ev.setData(r.first, r.second, Bitmap.createScaledBitmap(s, Shapes.COLS, r.second, true))
    }

    private fun controls() {
        list.removeAllViews()
        ui.card(list, "", "Силуэт из фото").let {
            ui.chips(it, listOf("Фон по краю", "Яркость", "Контур"), method) { i ->
                method = i; thr = when (i) { 0 -> 90; 1 -> 128; else -> 60 }; controls(); regen()
            }
            ui.slider(it, "чувствительность", 5f, 255f, thr.toFloat(), { v -> "" + v.toInt() }) { v -> thr = v.toInt(); regen() }
            ui.slider(it, "сглаживание", 0f, 3f, smooth.toFloat(), { v -> "" + v.toInt() }) { v -> smooth = v.toInt(); regen() }
            ui.slider(it, "убрать мусор", 0f, 10f, despeckle.toFloat(), { v -> "" + v.toInt() }) { v -> despeckle = v.toInt(); regen() }
            ui.toggle(it, "Заполнить дыры внутри", fill) { v -> fill = v; regen() }
            ui.toggle(it, "Инвертировать", inv) { v -> inv = v; regen() }
        }
        ui.card(list, "", "Рисование").let {
            ui.chips(it, listOf("Кисть +", "Кисть −", "Область +", "Область −"), ev.tool) { i -> ev.tool = i }
            ui.slider(it, "размер кисти", 1f, 10f, ev.brush.toFloat(), { v -> "" + v.toInt() }) { v -> ev.brush = v.toInt().coerceAtLeast(1) }
        }
        ui.card(list, "", "Правки").let {
            ui.btn(it, "СБРОСИТЬ ВИД (МАСШТАБ)", false) { ev.resetView() }
            ui.btn(it, "ОТМЕНИТЬ ШАГ", false) { ev.undo() }
            ui.btn(it, "ВСЁ УБРАТЬ", false) { ev.fillAll(false) }
            ui.btn(it, "ВСЁ ЗАЛИТЬ", false) { ev.fillAll(true) }
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Prefs(this); Skin.load(p); ui = Ui(this)
        val uri = intent.data
        val bmp = if (uri != null) loadBitmap(uri) else null
        if (bmp == null) { Toast.makeText(this, "Не удалось открыть изображение", Toast.LENGTH_SHORT).show(); finish(); return }
        src = bmp
        val bgc = ui.BG
        window.statusBarColor = bgc; window.navigationBarColor = bgc

        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(bgc)
        page.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(12))

        val t1 = TextView(this)
        t1.text = "РЕДАКТОР ФИГУРЫ"; t1.setTextColor(ui.G); t1.textSize = 12f; t1.typeface = Typeface.MONOSPACE
        page.addView(t1)
        val t2 = TextView(this)
        t2.text = "Подбери способ и чувствительность, потом дорисуй (+) или сотри (−)."
        t2.setTextColor(ui.DIM); t2.textSize = 13f; t2.setPadding(0, ui.dp(2), 0, ui.dp(8))
        page.addView(t2)

        ev = EditorView(this)
        val g = GradientDrawable(); g.setColor(Color.BLACK); g.setStroke(ui.dp(1), ui.LINE); g.cornerRadius = ui.dp(20).toFloat()
        ev.background = g; ev.clipToOutline = true; ev.outlineProvider = ViewOutlineProvider.BACKGROUND
        val eh = (resources.displayMetrics.heightPixels * 0.40f).toInt()
        page.addView(ev, LinearLayout.LayoutParams(-1, eh).apply { bottomMargin = ui.dp(10) })

        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(list)
        page.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))

        val save = Button(this)
        save.text = "СОХРАНИТЬ ФИГУРУ"; save.setTextColor(ui.ON); save.background = ui.bg(ui.G, ui.G, 20)
        save.setOnClickListener {
            val m = ev.mask()
            if (m == null) ui.toast("Фигура пустая")
            else ui.prompt("Название фигуры", "например: Мой логотип", "Моя фигура") { name ->
                val id = Shapes.save(this, name.ifEmpty { "Фигура" }.take(24), m)
                when (intent.getIntExtra("dest", 0)) {
                    2 -> { p.giftShape = id; p.target = 1 }
                    1 -> p.shapeB = id
                    else -> p.shapeA = id
                }
                p.stamp = System.currentTimeMillis()
                setResult(RESULT_OK)
                finish()
            }
        }
        page.addView(save, LinearLayout.LayoutParams(-1, ui.dp(58)).apply { topMargin = ui.dp(10) })
        setContentView(page)
        controls()
        regen()
    }
}
