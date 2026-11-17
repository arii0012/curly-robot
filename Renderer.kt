package com.codewall
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class Renderer(private val ctx: Context, private val prefs: Prefs) {
    // Заранее посчитанные «кусочки» текста: в кадре остаётся один плоский цикл
    private class Slot(val m: Mask, n: Int) {
        val row: IntArray; val start: IntArray; val len: IntArray; val dep: FloatArray; val xc: FloatArray
        init {
            val ra = ArrayList<Int>(); val sa = ArrayList<Int>(); val la = ArrayList<Int>()
            val da = ArrayList<Float>(); val xa = ArrayList<Float>()
            for (r in 0 until m.rows) {
                var x = 0
                while (x < m.cols) {
                    if (!m.on[r * m.cols + x]) { x++; continue }
                    var e = x
                    while (e < m.cols && m.on[r * m.cols + e]) e++
                    var s = x
                    while (s < e) {
                        val k = min(n, e - s)
                        ra.add(r); sa.add(s); la.add(k); da.add(m.depth[r * m.cols + s + k / 2]); xa.add(s + k / 2f)
                        s += k
                    }
                    x = e
                }
            }
            row = ra.toIntArray(); start = sa.toIntArray(); len = la.toIntArray(); dep = da.toFloatArray(); xc = xa.toFloatArray()
        }
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint()
    private val gridPaint = Paint().apply { strokeWidth = 1f }
    private val wp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textAlign = Paint.Align.CENTER }
    private val glowKey = IntArray(2)
    private val glowShader = arrayOfNulls<RadialGradient>(2)
    private var bgKey = 0
    private var bgShader: LinearGradient? = null
    private var slotA: Slot? = null
    private var slotB: Slot? = null
    private val text2 = TEXT + TEXT
    private val hsv = FloatArray(3)
    private val tf = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val df = SimpleDateFormat("EEE, d MMMM", Locale.getDefault())
    private var lastSec = -1L
    private var timeStr = ""
    private var dateStr = ""
    private var batStr = ""

    fun reload() {
        val n = when (prefs.quality) { 0 -> 6; 2 -> 3; else -> 4 }
        slotA = Slot(Shapes.resolve(ctx, prefs.shapeA) ?: Shapes.builtIn(0), n)
        val b = prefs.shapeB
        slotB = if (b.isEmpty()) null else Shapes.resolve(ctx, b)?.let { Slot(it, n) }
    }

    fun render(c: Canvas, w: Float, h: Float, t: Float, cyFrac: Float) {
        if (slotA == null) reload()
        val a = slotA ?: return
        val col = prefs.colorInt
        background(c, w, h, col)
        val sc = prefs.scale
        val cy = h * cyFrac
        val b = slotB
        if (b == null) shape(c, a, w / 2, cy, w * 0.84f * sc, h * 0.8f * sc, t, 0, col)
        else {
            shape(c, a, w * 0.27f, cy, w * 0.46f * sc, h * 0.8f * sc, t, 0, col)
            shape(c, b, w * 0.73f, cy, w * 0.46f * sc, h * 0.8f * sc, t, 1, col)
        }
        widgets(c, w, h, col)
    }

    private fun background(c: Canvas, w: Float, h: Float, col: Int) {
        c.drawColor(Color.BLACK)
        when (prefs.bg) {
            1 -> {
                val key = h.toInt() * 31 + col
                if (bgShader == null || bgKey != key) {
                    bgShader = LinearGradient(0f, 0f, 0f, h, 0x00000000, (col and 0x00FFFFFF) or 0x30000000, Shader.TileMode.CLAMP)
                    bgKey = key
                }
                bgPaint.shader = bgShader
                c.drawRect(0f, 0f, w, h, bgPaint)
            }
            2 -> {
                gridPaint.color = (col and 0x00FFFFFF) or 0x16000000
                val step = max(24f, w / 14f)
                var x = 0f
                while (x < w) { c.drawLine(x, 0f, x, h, gridPaint); x += step }
                var y = 0f
                while (y < h) { c.drawLine(0f, y, w, y, gridPaint); y += step }
            }
        }
    }

    private fun mix(a: Int, k: Float): Int {
        val r = (Color.red(a) + (255 - Color.red(a)) * k).toInt()
        val g = (Color.green(a) + (255 - Color.green(a)) * k).toInt()
        val b = (Color.blue(a) + (255 - Color.blue(a)) * k).toInt()
        return Color.rgb(r, g, b)
    }

    private fun shape(c: Canvas, s: Slot, cx: Float, cy: Float, boxW: Float, boxH: Float, t: Float, idx: Int, col: Int) {
        val m = s.m
        val ts = min(boxW / (m.cols * 0.6f), boxH / (m.rows * 0.8f))
        val cw = ts * 0.6f
        val rh = ts * 0.8f
        val hw = m.cols * cw * 0.5f

        if (prefs.glow) {
            val gr = hw * 1.5f
            val key = ((cx.toInt() * 31 + cy.toInt()) * 31 + gr.toInt()) * 31 + col
            if (glowShader[idx] == null || glowKey[idx] != key) {
                glowShader[idx] = RadialGradient(cx, cy, gr, (col and 0x00FFFFFF) or 0x40000000, col and 0x00FFFFFF, Shader.TileMode.CLAMP)
                glowKey[idx] = key
            }
            glowPaint.shader = glowShader[idx]
            glowPaint.alpha = (190 + 65 * sin(t * 1.3f)).toInt()
            c.drawCircle(cx, cy, gr, glowPaint)
        }

        val sp = prefs.speed
        val rainbow = prefs.rainbow
        val dir = if (idx == 1) -1f else 1f
        val a = if (prefs.mode == 0) (t * sp + idx * 1.7f) * dir else sin(t * sp * 0.8f + idx * 1.7f) * 0.95f
        val ca = cos(a); val sa = sin(a)
        val tilt = 0.1f + sin(t * 0.5f) * 0.1f
        val cb = cos(tilt); val sb = sin(tilt)
        val dd = hw * 0.45f * prefs.depth
        val cam = hw * 5.2f
        val shift = (t * 8f * sp).toInt()
        val sweepRow = ((t * 0.35f + idx * 0.4f) % 1.6f - 0.3f) * m.rows
        val half = m.cols / 2f
        val rhalf = m.rows / 2f

        paint.style = Paint.Style.FILL_AND_STROKE
        paint.strokeWidth = (prefs.lineW - 0.5f) * ts * 0.03f
        paint.textScaleX = max(abs(ca), 0.15f)

        for (k in 0..1) {
            val side = if (k == 0) 1f else -1f
            val wt = (0.5f + side * ca / 0.6f).coerceIn(0f, 1f)
            if (wt < 0.03f) continue
            var lastRow = -1
            for (i in s.row.indices) {
                val r = s.row[i]
                if (r != lastRow) {
                    lastRow = r
                    var base = col
                    if (rainbow) { hsv[0] = (r * 4f + t * 50f) % 360f; hsv[1] = 0.65f; hsv[2] = 1f; base = Color.HSVToColor(hsv) }
                    val hl = max(0f, 1f - abs(r - sweepRow) / 5f)
                    paint.color = if (hl > 0f) mix(base, hl * 0.85f) else base
                }
                val z = side * (0.2f + 0.8f * s.dep[i]) * dd
                val xx = (s.xc[i] - half) * cw
                val yy = (r - rhalf + 0.5f) * rh
                val x1 = xx * ca + z * sa
                val z1 = -xx * sa + z * ca
                val y2 = yy * cb - z1 * sb
                val z2 = yy * sb + z1 * cb
                val f = cam / (cam - z2)
                val fq = (f * 20f).roundToInt() / 20f
                var light = (0.62f + 0.38f * z2 / hw).coerceIn(0.2f, 1f) * (0.7f + 0.3f * abs(ca))
                light = (light * wt * 10f).toInt() / 10f
                if (light < 0.1f) continue
                paint.alpha = (light * 255f).toInt()
                paint.textSize = ts * fq
                val off = (r * 31 + s.start[i] + shift) % TEXT.length
                c.drawText(text2, off, off + s.len[i], cx + x1 * f, cy + y2 * f + ts * fq * 0.3f, paint)
            }
        }
    }

    private fun battery(): Int {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return -1
        val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (l < 0) -1 else l * 100 / max(sc, 1)
    }

    private fun widgets(c: Canvas, w: Float, h: Float, col: Int) {
        val text = prefs.wText
        val clock = prefs.wClock; val date = prefs.wDate; val bat = prefs.wBattery
        if (!clock && !date && !bat && text.isEmpty()) return
        val now = System.currentTimeMillis()
        val sec = now / 1000
        if (sec != lastSec) {
            if (lastSec < 0 || sec % 30 == 0L) { val b = battery(); batStr = if (b >= 0) "[ BAT $b% ]" else "" }
            lastSec = sec
            val d = Date(now)
            timeStr = tf.format(d); dateStr = df.format(d)
        }
        wp.color = col; wp.alpha = 235
        var y = if (prefs.wPos == 0) h * 0.07f else h * 0.76f
        if (clock) { wp.textSize = h * 0.07f; y += wp.textSize; c.drawText(timeStr, w / 2, y, wp); y += h * 0.012f }
        if (date) { wp.textSize = h * 0.022f; y += wp.textSize; c.drawText(dateStr, w / 2, y, wp); y += h * 0.012f }
        if (bat && batStr.isNotEmpty()) { wp.textSize = h * 0.02f; y += wp.textSize; c.drawText(batStr, w / 2, y, wp); y += h * 0.012f }
        if (text.isNotEmpty()) { wp.textSize = h * 0.024f; y += wp.textSize; c.drawText(text, w / 2, y, wp) }
    }

    companion object {
        val TEXT = ("fastboot devices fastboot flash vendor_boot vendor_boot.img fastboot reboot bootloader " +
            "fastboot --disable-verity --disable-verification flash vbmeta vbmeta.img fastboot set_active a " +
            "fastboot flashing unlock fastboot erase userdata fastboot reboot ").repeat(3)
    }
}
