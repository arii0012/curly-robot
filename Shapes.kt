package com.codewall
import android.graphics.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class Mask(val cols: Int, val rows: Int, val runs: Array<IntArray>, val depth: FloatArray)

object Shapes {
    const val COLS = 88
    fun rowsFor(w: Int, h: Int) = max(8, (COLS * h.toFloat() / w * 0.75f).roundToInt())

    fun fromBitmap(src: Bitmap, invert: Boolean): Mask {
        val rows = rowsFor(src.width, src.height)
        val b = Bitmap.createScaledBitmap(src, COLS, rows, true)
        val px = IntArray(COLS * rows)
        b.getPixels(px, 0, COLS, 0, 0, COLS, rows)
        val hasAlpha = px.any { Color.alpha(it) < 128 }
        val bg = px[0]
        val on = BooleanArray(px.size) { i ->
            val p = px[i]
            val f = if (hasAlpha) Color.alpha(p) >= 128
            else abs(Color.red(p) - Color.red(bg)) + abs(Color.green(p) - Color.green(bg)) + abs(Color.blue(p) - Color.blue(bg)) > 90
            f != invert
        }
        return toMask(on, rows)
    }

    // 0 капюшон, 1 шар, 2 сердце, 3 звезда
    fun builtIn(kind: Int): Mask {
        val bmp = Bitmap.createBitmap(200, 230, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.WHITE
        when (kind) {
            1 -> c.drawCircle(100f, 115f, 98f, p)
            2 -> {
                c.drawCircle(68f, 78f, 55f, p); c.drawCircle(132f, 78f, 55f, p)
                val path = Path(); path.moveTo(16f, 104f); path.lineTo(184f, 104f); path.lineTo(100f, 218f); path.close()
                c.drawPath(path, p)
            }
            3 -> {
                val path = Path()
                for (i in 0 until 10) {
                    val ang = -PI / 2 + i * PI / 5
                    val r = if (i % 2 == 0) 108.0 else 46.0
                    val x = (100 + r * cos(ang)).toFloat(); val y = (122 + r * sin(ang)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close(); c.drawPath(path, p)
            }
            else -> {
                c.drawOval(RectF(15f, 125f, 185f, 330f), p)
                c.drawOval(RectF(48f, 18f, 152f, 150f), p)
                p.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                c.drawOval(RectF(76f, 62f, 124f, 126f), p)
            }
        }
        return fromBitmap(bmp, false)
    }

    private fun toMask(on: BooleanArray, rows: Int): Mask {
        val runs = Array(rows) { r ->
            val l = ArrayList<Int>(); var x = 0
            while (x < COLS) {
                if (on[r * COLS + x]) { val s = x; while (x < COLS && on[r * COLS + x]) x++; l.add(s); l.add(x) } else x++
            }
            l.toIntArray()
        }
        // карта глубины: расстояние до края -> "надутая" форма
        val n = COLS * rows
        val d = FloatArray(n) { if (on[it]) 1e6f else 0f }
        for (y in 0 until rows) for (x in 0 until COLS) {
            val i = y * COLS + x
            if (!on[i]) continue
            var v = d[i]
            if (x == 0 || y == 0 || x == COLS - 1 || y == rows - 1) v = min(v, 1f)
            if (x > 0) v = min(v, d[i - 1] + 1f)
            if (y > 0) {
                v = min(v, d[i - COLS] + 1f)
                if (x > 0) v = min(v, d[i - COLS - 1] + 1.41f)
                if (x < COLS - 1) v = min(v, d[i - COLS + 1] + 1.41f)
            }
            d[i] = v
        }
        for (y in rows - 1 downTo 0) for (x in COLS - 1 downTo 0) {
            val i = y * COLS + x
            if (!on[i]) continue
            var v = d[i]
            if (x < COLS - 1) v = min(v, d[i + 1] + 1f)
            if (y < rows - 1) {
                v = min(v, d[i + COLS] + 1f)
                if (x < COLS - 1) v = min(v, d[i + COLS + 1] + 1.41f)
                if (x > 0) v = min(v, d[i + COLS - 1] + 1.41f)
            }
            d[i] = v
        }
        var mx = 1f
        for (v in d) if (v < 1e5f && v > mx) mx = v
        val depth = FloatArray(n) { if (on[it]) sqrt(min(d[it], mx) / mx) else 0f }
        return Mask(COLS, rows, runs, depth)
    }
}
