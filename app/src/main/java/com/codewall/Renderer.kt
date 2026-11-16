package com.codewall
import android.content.Context
import android.graphics.*
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class Renderer(private val ctx: Context, private val prefs: Prefs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowKey = IntArray(2)
    private val glowShader = arrayOfNulls<RadialGradient>(2)
    private var builtIn: Mask? = null
    private var custom: Mask? = null
    private val text2 = TEXT + TEXT

    fun reload() {
        builtIn = Shapes.builtIn(prefs.shape)
        custom = null
        val f = File(ctx.filesDir, "logo.png")
        if (prefs.hasLogo && f.exists()) BitmapFactory.decodeFile(f.path)?.let { custom = Shapes.fromBitmap(it, prefs.invert) }
    }

    fun render(c: Canvas, w: Float, h: Float, t: Float, cyFrac: Float) {
        c.drawColor(Color.BLACK)
        if (builtIn == null) reload()
        val list = when (prefs.scene) {
            0 -> listOfNotNull(builtIn)
            1 -> listOfNotNull(custom ?: builtIn)
            else -> listOfNotNull(builtIn, custom)
        }
        val col = Prefs.COLORS[prefs.color.coerceIn(0, Prefs.COLORS.size - 1)]
        val cy = h * cyFrac
        val sc = prefs.scale
        if (list.size == 1) shape(c, list[0], w / 2, cy, w * 0.84f * sc, h * 0.8f * sc, t, 0, col)
        else list.forEachIndexed { i, m -> shape(c, m, if (i == 0) w * 0.27f else w * 0.73f, cy, w * 0.46f * sc, h * 0.8f * sc, t, i, col) }
    }

    private fun mix(a: Int, k: Float): Int {
        val r = (Color.red(a) + (255 - Color.red(a)) * k).toInt()
        val g = (Color.green(a) + (255 - Color.green(a)) * k).toInt()
        val b = (Color.blue(a) + (255 - Color.blue(a)) * k).toInt()
        return Color.rgb(r, g, b)
    }

    private fun shape(c: Canvas, m: Mask, cx: Float, cy: Float, boxW: Float, boxH: Float, t: Float, idx: Int, col: Int) {
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
        val dir = if (idx == 1) -1f else 1f
        val a = if (prefs.mode == 0) (t * sp + idx * 1.7f) * dir else sin(t * sp * 0.8f + idx * 1.7f) * 0.95f
        val ca = cos(a); val sa = sin(a)
        val tilt = 0.1f + sin(t * 0.5f) * 0.1f
        val cb = cos(tilt); val sb = sin(tilt)
        val dd = hw * 0.45f * prefs.depth
        val cam = hw * 5.2f
        val shift = (t * 8f * sp).toInt()
        val phase = (t * 0.35f + idx * 0.4f) % 1.6f
        val sweepRow = (phase - 0.3f) * m.rows
        val half = m.cols / 2f
        val rhalf = m.rows / 2f

        paint.style = Paint.Style.FILL_AND_STROKE
        paint.strokeWidth = (prefs.lineW - 0.5f) * ts * 0.03f
        paint.textScaleX = max(abs(ca), 0.15f)

        for (k in 0..1) {
            val side = if (k == 0) 1f else -1f
            val wt = (0.5f + side * ca / 0.6f).coerceIn(0f, 1f)
            if (wt < 0.03f) continue
            for (r in 0 until m.rows) {
                val hl = max(0f, 1f - abs(r - sweepRow) / 5f)
                paint.color = if (hl > 0f) mix(col, hl * 0.85f) else col
                val runs = m.runs[r]
                val yy = (r - rhalf + 0.5f) * rh
                var i = 0
                while (i < runs.size) {
                    val st = runs[i]; val en = runs[i + 1]; i += 2
                    var x0 = st
                    while (x0 < en) {
                        val n = min(3, en - x0)
                        val d = m.depth[r * m.cols + min(x0 + n / 2, en - 1)]
                        val z = side * (0.2f + 0.8f * d) * dd
                        val xx = (x0 + n / 2f - half) * cw
                        val x1 = xx * ca + z * sa
                        val z1 = -xx * sa + z * ca
                        val y2 = yy * cb - z1 * sb
                        val z2 = yy * sb + z1 * cb
                        val f = cam / (cam - z2)
                        val fq = (f * 20f).roundToInt() / 20f
                        var light = (0.62f + 0.38f * z2 / hw).coerceIn(0.2f, 1f) * (0.7f + 0.3f * abs(ca))
                        light = (light * wt * 10f).toInt() / 10f
                        if (light >= 0.1f) {
                            paint.alpha = (light * 255f).toInt()
                            paint.textSize = ts * fq
                            val off = (r * 31 + x0 + shift) % TEXT.length
                            c.drawText(text2, off, off + n, cx + x1 * f, cy + y2 * f + ts * fq * 0.3f, paint)
                        }
                        x0 += n
                    }
                }
            }
        }
    }

    companion object {
        val TEXT = ("fastboot devices fastboot flash vendor_boot vendor_boot.img fastboot reboot bootloader " +
            "fastboot --disable-verity --disable-verification flash vbmeta vbmeta.img fastboot set_active a " +
            "fastboot flashing unlock fastboot erase userdata fastboot reboot ").repeat(3)
    }
}
