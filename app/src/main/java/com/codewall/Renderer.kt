package com.codewall
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

class Renderer(private val ctx: Context, private val prefs: Prefs) {
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

    private val scenes = Scenes(ctx, prefs)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint()
    private val gridPaint = Paint().apply { strokeWidth = 1f }
    private val glowKey = IntArray(2)
    private val glowShader = arrayOfNulls<RadialGradient>(2)
    private var bgKey = 0
    private var bgShader: LinearGradient? = null
    private var slotA: Slot? = null
    private var slotB: Slot? = null
    private val text2 = TEXT + TEXT
    private val hsv = FloatArray(3)
    // адаптивная нагрузка: если кадр рисуется долго, символы укрупняются
    private var lod = 0
    private var ema = 0f
    private var frames = 0

    fun reload() {
        rebuildSlots()
        scenes.reload()
    }

    private fun rebuildSlots() {
        val base = when (prefs.quality) { 0 -> 6; 2 -> 3; else -> 4 }
        val n = base + lod * 2
        slotA = Slot(Shapes.resolve(ctx, prefs.shapeA) ?: Shapes.builtIn(0), n)
        val b = prefs.shapeB
        slotB = if (b.isEmpty()) null else Shapes.resolve(ctx, b)?.let { Slot(it, n) }
    }

    private fun adapt(ms: Float) {
        ema = ema * 0.92f + ms * 0.08f
        frames++
        if (frames % 40 != 0 || prefs.scene != 0) return
        val budget = 1000f / prefs.fps.coerceIn(10, 144)
        if (ema > budget * 0.55f && lod < 4) { lod++; rebuildSlots() }
        else if (ema < budget * 0.2f && lod > 0) { lod--; rebuildSlots() }
    }

    fun render(c: Canvas, w: Float, h: Float, t: Float) {
        val st = System.nanoTime()
        if (slotA == null) rebuildSlots()
        val a = slotA ?: return
        val col = prefs.colorInt
        val cx = w * prefs.posX
        val cy = h * prefs.posY
        val sc = prefs.scene
        if (sc in 1..3) {
            c.save()
            c.translate(cx - w / 2, cy - h * 0.46f)
            c.scale(prefs.scale, prefs.scale, cx, cy)
            scenes.draw(sc, c, w, h, t, 0.46f)
            c.restore()
        } else {
            background(c, w, h, col)
            val s = prefs.scale.coerceAtMost(2f)
            val b = slotB
            if (b == null) shape(c, a, cx, cy, w * 0.84f * s, h * 0.8f * s, t, 0, col)
            else {
                shape(c, a, cx - w * 0.23f, cy, w * 0.46f * s, h * 0.8f * s, t, 0, col)
                shape(c, b, cx + w * 0.23f, cy, w * 0.46f * s, h * 0.8f * s, t, 1, col)
            }
        }
        widgets(c, w, h, t)
        adapt((System.nanoTime() - st) / 1e6f)
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
                val fq = (f * 10f).roundToInt() / 10f
                var light = (0.62f + 0.38f * z2 / hw).coerceIn(0.2f, 1f) * (0.7f + 0.3f * abs(ca))
                light = (light * wt * 8f).toInt() / 8f
                if (light < 0.1f) continue
                paint.alpha = (light * 255f).toInt()
                paint.textSize = ts * fq
                val off = (r * 31 + s.start[i] + shift) % TEXT.length
                c.drawText(text2, off, off + s.len[i], cx + x1 * f, cy + y2 * f + ts * fq * 0.3f, paint)
            }
        }
    }

    // ---------- Виджеты ----------
    private val wp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val sp2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val wmono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    private val wthin = Typeface.create("sans-serif-thin", Typeface.NORMAL)
    private val path = Path()
    private val rect = RectF()
    private var wList: List<Wdg> = emptyList()
    private var wSrc = ""
    private val tf = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val df1 = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
    private val df2 = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
    private val df3 = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private var lastSec = -1L
    private var timeStr = ""; private var d1 = ""; private var d2 = ""; private var d3 = ""
    private var hh = 0; private var mm = 0; private var bat = 100

    private fun battery(): Int {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 100
        val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (l < 0) 100 else l * 100 / max(sc, 1)
    }

    private fun tickTime() {
        val now = System.currentTimeMillis()
        val sec = now / 1000
        if (sec == lastSec) return
        if (lastSec < 0 || sec % 30 == 0L) bat = battery()
        lastSec = sec
        val d = Date(now)
        timeStr = tf.format(d); d1 = df1.format(d); d2 = df2.format(d); d3 = df3.format(d)
        val cal = Calendar.getInstance(); hh = cal.get(Calendar.HOUR_OF_DAY); mm = cal.get(Calendar.MINUTE)
    }

    private fun widgets(c: Canvas, w: Float, h: Float, t: Float) {
        val json = prefs.wJson
        if (json != wSrc) { wList = Widgets.parse(json); wSrc = json }
        if (wList.isEmpty()) return
        tickTime()
        for (wd in wList) one(c, w, h, t, wd)
    }

    private fun one(c: Canvas, w: Float, h: Float, t: Float, wd: Wdg) {
        val u = min(w, h) * 0.06f * wd.size
        var col = wd.color
        if (wd.anim == 4) { hsv[0] = (t * 60f) % 360f; hsv[1] = 0.7f; hsv[2] = 1f; col = Color.HSVToColor(hsv) }
        c.save()
        c.translate(w * wd.x, h * wd.y)
        if (wd.anim == 1) { val k = 1f + 0.06f * sin(t * 3f); c.scale(k, k) }
        val gx = if (wd.anim == 2 && ((t * 8f).toInt() % 6 == 0)) sin(t * 97f) * u * 0.25f else 0f
        if (gx != 0f) {
            c.save(); c.translate(-gx, 0f); paintWidget(c, t, u, wd, 0xFF00E5FF.toInt()); c.restore()
            c.translate(gx * 0.3f, 0f)
        }
        paintWidget(c, t, u, wd, col)
        c.restore()
    }

    private fun typed(s: String, wd: Wdg, t: Float): String =
        if (wd.anim != 3) s else s.take(((t % 7f) * 6f).toInt().coerceAtMost(s.length))

    private fun ecg(p: Float): Float {
        val q = (p % 0.5f) / 0.5f
        return exp(-((q - 0.30f) * 40f).pow(2)) - 0.25f * exp(-((q - 0.26f) * 60f).pow(2)) -
            0.3f * exp(-((q - 0.34f) * 60f).pow(2)) + 0.15f * exp(-((q - 0.55f) * 20f).pow(2))
    }

    private fun paintWidget(c: Canvas, t: Float, u: Float, wd: Wdg, col: Int) {
        wp.style = Paint.Style.FILL; wp.color = col; wp.alpha = 255; wp.typeface = wmono
        sp2.color = col; sp2.alpha = 255
        when (wd.type) {
            0 -> when (wd.style) {
                0 -> { wp.textSize = u * 1.8f; c.drawText(typed(timeStr, wd, t), 0f, u * 0.6f, wp) }
                1 -> { wp.typeface = wthin; wp.textSize = u * 2.6f; c.drawText(typed(timeStr, wd, t), 0f, u * 0.9f, wp) }
                2 -> {
                    wp.textSize = u * 1.8f
                    val s = typed(timeStr, wd, t)
                    wp.style = Paint.Style.FILL_AND_STROKE
                    for (k in 3 downTo 1) { wp.strokeWidth = u * 0.12f * k; wp.alpha = 28; c.drawText(s, 0f, u * 0.6f, wp) }
                    wp.style = Paint.Style.FILL; wp.color = mix(col, 0.8f); wp.alpha = 255
                    c.drawText(s, 0f, u * 0.6f, wp)
                }
                else -> {
                    val ds = intArrayOf(hh / 10, hh % 10, mm / 10, mm % 10)
                    val gap = u * 0.55f
                    for (cc in 0..3) for (rr in 0..3) {
                        val on = (ds[cc] shr (3 - rr)) and 1
                        val x = (cc - 1.5f) * gap + (if (cc >= 2) gap * 0.4f else -gap * 0.4f)
                        val y = (rr - 1.5f) * gap
                        wp.alpha = if (on == 1) 255 else 55
                        c.drawCircle(x, y, if (on == 1) u * 0.2f else u * 0.12f, wp)
                    }
                }
            }
            1 -> {
                val s = when (wd.style) { 0 -> d1; 1 -> d2; else -> d3 }
                wp.textSize = u * (if (wd.style == 1) 0.6f else 0.8f)
                c.drawText(typed(s, wd, t), 0f, u * 0.3f, wp)
            }
            2 -> when (wd.style) {
                0 -> { wp.textSize = u * 0.8f; c.drawText(typed("BAT $bat%", wd, t), 0f, u * 0.3f, wp) }
                1 -> {
                    sp2.strokeWidth = u * 0.22f; sp2.alpha = 60
                    c.drawCircle(0f, 0f, u, sp2)
                    sp2.alpha = 255; rect.set(-u, -u, u, u)
                    c.drawArc(rect, -90f, bat * 3.6f, false, sp2)
                    wp.textSize = u * 0.7f; c.drawText("$bat", 0f, u * 0.25f, wp)
                }
                else -> {
                    sp2.strokeWidth = u * 0.08f
                    rect.set(-u * 2f, -u * 0.25f, u * 2f, u * 0.25f)
                    c.drawRoundRect(rect, u * 0.25f, u * 0.25f, sp2)
                    rect.set(-u * 2f + u * 0.08f, -u * 0.17f, -u * 2f + u * 4f * bat / 100f - u * 0.08f, u * 0.17f)
                    if (rect.right > rect.left) c.drawRoundRect(rect, u * 0.15f, u * 0.15f, wp)
                    wp.textSize = u * 0.5f; c.drawText("$bat%", 0f, -u * 0.5f, wp)
                }
            }
            3 -> {
                wp.textSize = u * 0.9f
                val raw = wd.text.ifEmpty { "PoiPaper" }
                val s = typed(raw, wd, t)
                if (wd.style == 1) {
                    val tw = wp.measureText(raw) + u * 0.8f
                    sp2.strokeWidth = u * 0.07f; rect.set(-tw / 2, -u * 0.7f, tw / 2, u * 0.55f)
                    c.drawRoundRect(rect, u * 0.3f, u * 0.3f, sp2)
                }
                c.drawText(if (wd.style == 2) "[ $s ]" else s, 0f, u * 0.3f, wp)
            }
            4 -> {
                sp2.strokeWidth = if (wd.style == 0) u * 0.18f else u * 0.16f
                for (i in 0 until 12) {
                    val x = (i - 5.5f) * u * 0.34f
                    val hg = u * (0.25f + 0.9f * abs(sin(t * 3f + i * 0.8f)) * (0.6f + 0.4f * sin(t * 1.3f + i)))
                    if (wd.style == 0) c.drawLine(x, 0f, x, -hg, sp2)
                    else { var j = 0f; while (j < hg) { c.drawPoint(x, -j, sp2); j += u * 0.26f } }
                }
            }
            else -> {
                sp2.strokeWidth = u * 0.08f
                path.reset()
                val n = 48
                for (i in 0..n) {
                    val f = i / n.toFloat()
                    val x = (f - 0.5f) * u * 6f
                    val y = -ecg(f + t * 0.8f) * u * 0.9f
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                c.drawPath(path, sp2)
            }
        }
    }

    companion object {
        val TEXT = ("fastboot devices fastboot flash vendor_boot vendor_boot.img fastboot reboot bootloader " +
            "fastboot --disable-verity --disable-verification flash vbmeta vbmeta.img fastboot set_active a " +
            "fastboot flashing unlock fastboot erase userdata fastboot reboot ").repeat(3)
    }
}
