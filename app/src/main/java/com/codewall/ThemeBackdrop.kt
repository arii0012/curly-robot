package com.codewall
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import java.util.Random
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Живой фон интерфейса под тему: блики стекла, неоновая сетка, закат синтвейва, сияние, дождь, конфетти, VHS и т. д.
 * Рисуется в одном View примерно 30 раз в секунду и только пока приложение открыто (running).
 */
class ThemeBackdrop(ctx: Context) : View(ctx) {
    var running = true
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val m = Matrix()
    private val rf = RectF()
    private val rnd = Random(3)
    private val sx = FloatArray(60) { rnd.nextFloat() }
    private val sy = FloatArray(60) { rnd.nextFloat() }
    private val ss = FloatArray(60) { 0.3f + rnd.nextFloat() * 0.9f }
    private val t0 = SystemClock.uptimeMillis()
    private var blobs: Array<RadialGradient>? = null
    private var vignette: RadialGradient? = null
    private var sun: LinearGradient? = null
    private var fog: LinearGradient? = null
    private var aur: Array<LinearGradient>? = null
    private var paper: Bitmap? = null
    private var scan = FloatArray(0)
    private val crack = Path()
    private val confetti = intArrayOf(0xFFFF4D8D.toInt(), 0xFF00E5FF.toInt(), 0xFFA3FF3D.toInt(), 0xFFFFE600.toInt(), 0xFFB07CFF.toInt())
    private val dst = RectF()

    private fun a(col: Int, alpha: Int) = (col and 0x00FFFFFF) or (alpha shl 24)

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val sk = Skin.cur
        val fw = w.toFloat(); val fh = h.toFloat()
        val rr = fw * 0.7f
        blobs = arrayOf(
            RadialGradient(0f, 0f, rr, a(Skin.accent, 0x55), a(Skin.accent, 0), Shader.TileMode.CLAMP),
            RadialGradient(0f, 0f, rr, a(sk.accent2, 0x50), a(sk.accent2, 0), Shader.TileMode.CLAMP),
            RadialGradient(0f, 0f, rr, a(0xFFFFFFFF.toInt(), 0x30), a(0xFFFFFFFF.toInt(), 0), Shader.TileMode.CLAMP)
        )
        vignette = RadialGradient(fw / 2, fh / 2, hypot(fw, fh) * 0.62f, 0x00000000, 0xDD000000.toInt(), Shader.TileMode.CLAMP)
        val hz = fh * 0.8f; val sr = fw * 0.32f
        sun = LinearGradient(0f, hz - sr * 1.35f, 0f, hz, 0xFFFFE066.toInt(), Skin.accent, Shader.TileMode.CLAMP)
        fog = LinearGradient(0f, 0f, 0f, fh * 0.45f, a(Skin.accent, 0x30), a(Skin.accent, 0), Shader.TileMode.CLAMP)
        val ac = intArrayOf(Skin.accent, sk.accent2, 0xFF7CFFB2.toInt())
        aur = Array(3) { i ->
            LinearGradient(0f, 0f, 0f, fh * 0.55f, intArrayOf(a(ac[i], 0), a(ac[i], 0x90), a(ac[i], 0)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        }
        // строки развёртки VHS
        val rows = (fh / 6f).toInt() + 1
        scan = FloatArray(rows * 4)
        for (i in 0 until rows) { scan[4 * i] = 0f; scan[4 * i + 1] = i * 6f; scan[4 * i + 2] = fw; scan[4 * i + 3] = i * 6f }
        // трещины
        crack.reset()
        var cx = fw; var cy = 0f
        crack.moveTo(cx, cy)
        val r2 = Random(11)
        for (i in 0 until 9) { cx -= fw * (0.04f + r2.nextFloat() * 0.07f); cy += fh * (0.03f + r2.nextFloat() * 0.05f); crack.lineTo(cx, cy) }
        // бумага манги: мелкие точки в уменьшенной картинке
        paper?.recycle()
        paper = null
        if (sk.backdrop == 5 && w > 0 && h > 0) {
            val bw = max(1, w / 4); val bh = max(1, h / 4)
            val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            val pp = Paint(Paint.ANTI_ALIAS_FLAG); pp.color = 0x22000000
            var y = 0f
            while (y < bh) {
                var x = 0f
                while (x < bw) {
                    val k = 1f - (x / bw) * 0.6f - (1f - y / bh) * 0.4f
                    if (k > 0.1f) c.drawCircle(x, y, 1.1f * k, pp)
                    x += 3f
                }
                y += 3f
            }
            paper = b
        }
    }

    override fun onDraw(c: Canvas) {
        val sk = Skin.cur
        val w = width.toFloat(); val h = height.toFloat()
        c.drawColor(sk.bg)
        val t = (SystemClock.uptimeMillis() - t0) / 1000f
        when (sk.backdrop) {
            1 -> blobsFx(c, w, h, t)
            2 -> cyber(c, w, h, t)
            3 -> synth(c, w, h, t)
            4 -> bubbles(c, w, h, t)
            5 -> paper?.let { dst.set(0f, 0f, w, h); p.shader = null; c.drawBitmap(it, null, dst, null) }
            6 -> aurora(c, w, h, t)
            7 -> dead(c, w, h, t)
            8 -> love(c, w, h, t)
            9 -> rain(c, w, h, t)
            10 -> stars(c, w, h, t)
            11 -> party(c, w, h, t)
            12 -> horror(c, w, h, t)
        }
        if (running && sk.backdrop != 0 && sk.backdrop != 5) postInvalidateDelayed(33)
    }

    private fun blobsFx(c: Canvas, w: Float, h: Float, t: Float) {
        val sh = blobs ?: return
        for (i in 0..2) {
            m.setTranslate(w * (0.5f + 0.38f * sin(t * 0.25f + i * 2.1f)), h * (0.22f + 0.28f * i + 0.08f * cos(t * 0.33f + i)))
            sh[i].setLocalMatrix(m)
            p.shader = sh[i]
            c.drawRect(0f, 0f, w, h, p)
        }
        p.shader = null
    }

    private fun cyber(c: Canvas, w: Float, h: Float, t: Float) {
        val sk = Skin.cur
        p.style = Paint.Style.STROKE; p.strokeWidth = 1f; p.color = a(sk.accent2, 0x1A)
        val step = w / 8f
        var x = 0f
        while (x <= w) { c.drawLine(x, 0f, x, h, p); x += step }
        var y = (t * 20f) % step
        while (y <= h) { c.drawLine(0f, y, w, y, p); y += step }
        if ((t * 2.5f).toInt() % 9 == 0) {
            p.style = Paint.Style.FILL
            val gy = (sin(t * 91f) * 0.5f + 0.5f) * h
            p.color = a(sk.accent2, 0x40); c.drawRect(0f, gy, w, gy + h * 0.012f, p)
            p.color = a(Skin.accent, 0x30); c.drawRect(w * 0.1f, gy + h * 0.02f, w * 0.9f, gy + h * 0.03f, p)
        }
    }

    private fun synth(c: Canvas, w: Float, h: Float, t: Float) {
        val sk = Skin.cur
        val hz = h * 0.8f; val r = w * 0.32f
        p.style = Paint.Style.FILL
        c.save()
        c.clipRect(0f, 0f, w, hz)
        p.shader = sun
        c.drawCircle(w / 2, hz - r * 0.35f, r, p)
        p.shader = null
        p.color = sk.bg
        for (i in 1..5) { val yy = hz - i * r * 0.17f; c.drawRect(w / 2 - r, yy, w / 2 + r, yy + i * 1.2f, p) }
        c.restore()
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f; p.color = a(sk.accent2, 0x88)
        for (k in -8..8) c.drawLine(w / 2 + k * w * 0.03f, hz, w / 2 + k * w * 0.18f, h, p)
        for (j in 0..7) {
            val f = (j + (t * 0.6f) % 1f) / 8f
            val yy = hz + (h - hz) * f * f
            c.drawLine(0f, yy, w, yy, p)
        }
    }

    private fun bubbles(c: Canvas, w: Float, h: Float, t: Float) {
        for (i in 0 until 14) {
            val y = h - ((t * 0.05f * ss[i] + sy[i]) % 1.2f) * h * 1.1f
            val x = sx[i] * w + sin(t * 0.5f + i) * w * 0.03f
            val rad = w * (0.025f + 0.04f * ss[i])
            p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = 0x66FFFFFF
            c.drawCircle(x, y, rad, p)
            p.style = Paint.Style.FILL; p.color = 0x22FFFFFF
            c.drawCircle(x, y, rad, p)
            p.color = 0x66FFFFFF
            c.drawCircle(x - rad * 0.3f, y - rad * 0.3f, rad * 0.22f, p)
        }
    }

    private fun aurora(c: Canvas, w: Float, h: Float, t: Float) {
        val sh = aur ?: return
        p.style = Paint.Style.FILL
        for (b in 0..2) {
            val base = h * (0.12f + 0.1f * b)
            val th = h * 0.16f
            path.reset()
            val n = 24
            for (i in 0..n) {
                val x = w * i / n
                val y = base + h * 0.05f * sin(x * 0.008f + t * (0.4f + 0.15f * b) + b)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            for (i in n downTo 0) {
                val x = w * i / n
                path.lineTo(x, base + th + h * 0.06f * sin(x * 0.006f + t * 0.3f + b * 2f))
            }
            path.close()
            p.shader = sh[b]
            c.drawPath(path, p)
        }
        p.shader = null
    }

    private fun dead(c: Canvas, w: Float, h: Float, t: Float) {
        p.style = Paint.Style.FILL
        val cell = w / 10f
        p.color = 0x14FFFFFF
        for (r in 0..2) for (k in 0..9) if ((r + k) % 2 == 0) c.drawRect(k * cell, h - (r + 1) * cell, (k + 1) * cell, h - r * cell, p)
        if ((t * 3f).toInt() % 7 == 0) {
            val gy = (sin(t * 57f) * 0.5f + 0.5f) * h
            p.color = a(Skin.accent, 0x55); c.drawRect(0f, gy, w, gy + h * 0.01f, p)
            p.color = 0x33FFFFFF; c.drawRect(w * 0.2f, gy + h * 0.02f, w, gy + h * 0.028f, p)
        }
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = 0x55FFFFFF
        c.drawPath(crack, p)
    }

    private fun heart(c: Canvas, x: Float, y: Float, s: Float) {
        path.reset()
        path.moveTo(x, y + s * 0.35f)
        path.cubicTo(x - s * 1.1f, y - s * 0.3f, x - s * 0.5f, y - s * 1.1f, x, y - s * 0.45f)
        path.cubicTo(x + s * 0.5f, y - s * 1.1f, x + s * 1.1f, y - s * 0.3f, x, y + s * 0.35f)
        path.close()
        c.drawPath(path, p)
    }

    private fun love(c: Canvas, w: Float, h: Float, t: Float) {
        p.style = Paint.Style.FILL
        for (i in 12 until 24) {
            p.color = a(Skin.cur.accent2, 0x1E)
            c.drawCircle(((sx[i] + 0.02f * sin(t * 0.3f + i)) % 1f) * w, sy[i] * h, w * (0.06f + 0.06f * ss[i]), p)
        }
        for (i in 0 until 12) {
            val y = h - ((t * 0.04f * (0.5f + ss[i]) + sy[i]) % 1.15f) * h * 1.1f
            val x = sx[i] * w + sin(t * 0.7f + i) * w * 0.04f
            p.color = a(Skin.accent, 0x70)
            heart(c, x, y, w * (0.03f + 0.04f * ss[i]))
        }
    }

    private fun rain(c: Canvas, w: Float, h: Float, t: Float) {
        p.style = Paint.Style.FILL
        p.shader = fog
        c.drawRect(0f, 0f, w, h * 0.45f, p)
        p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = a(Skin.cur.dim, 0x55)
        for (i in 0 until 40) {
            val y = ((t * (0.35f + 0.4f * ss[i]) + sy[i] * 1.3f) % 1.3f - 0.15f) * h
            val x = sx[i] * w
            c.drawLine(x, y, x, y + h * (0.03f + 0.05f * ss[i]), p)
        }
    }

    private fun stars(c: Canvas, w: Float, h: Float, t: Float) {
        p.style = Paint.Style.FILL
        for (i in 0 until 50) {
            p.color = a(0xFFFFFFFF.toInt(), (110 + 110 * sin(t * (0.8f + ss[i]) + i)).toInt().coerceIn(0, 255))
            c.drawCircle(sx[i] * w, sy[i] * h * 0.8f, 1.5f + ss[i], p)
        }
        val k = (t % 5f) / 1.2f
        if (k < 1f) {
            p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = a(Skin.accent, ((1f - k) * 220f).toInt())
            val x0 = w * 0.85f - k * w * 0.5f; val y0 = h * 0.05f + k * h * 0.25f
            c.drawLine(x0, y0, x0 + w * 0.12f, y0 - h * 0.04f, p)
        }
        p.style = Paint.Style.FILL; p.color = a(Skin.accent, 0x28)
        for (i in 0..2) { rf.set(w * (0.1f + 0.3f * i), h * 0.9f, w * (0.35f + 0.3f * i), h * 0.94f); c.drawOval(rf, p) }
    }

    private fun party(c: Canvas, w: Float, h: Float, t: Float) {
        p.style = Paint.Style.FILL
        for (i in 0 until 40) {
            p.color = confetti[i % confetti.size]
            val x = sx[i] * w + sin(t + i) * 20f
            val y = ((t * 0.15f * (0.5f + ss[i]) + sy[i]) % 1.1f) * h
            c.save()
            c.rotate(t * 180f * ss[i], x, y)
            c.drawRect(x - 6f, y - 3f, x + 6f, y + 3f, p)
            c.restore()
        }
    }

    private fun horror(c: Canvas, w: Float, h: Float, t: Float) {
        p.shader = vignette
        p.style = Paint.Style.FILL
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = 1f; p.color = 0x14000000
        c.drawLines(scan, p)
        p.style = Paint.Style.FILL; p.color = 0x14FFFFFF
        val by = ((t * 0.25f) % 1.2f - 0.1f) * h
        c.drawRect(0f, by, w, by + h * 0.015f, p)
        if (sin(t * 23f) * sin(t * 7f) > 0.93f) c.drawColor(0x66000000)
    }
}
