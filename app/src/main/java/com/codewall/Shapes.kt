package com.codewall
import android.graphics.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class Mask(val cols: Int, val rows: Int, val runs: Array<IntArray>)

object Shapes {
    const val COLS = 88
    fun rowsFor(w: Int, h: Int) = max(8, (COLS * h.toFloat() / w * 0.75f).roundToInt())

    fun fromBitmap(src: Bitmap, invert: Boolean): Mask {
        val rows = rowsFor(src.width, src.height)
        val b = Bitmap.createScaledBitmap(src, COLS, rows, true)
        val px = IntArray(COLS * rows); b.getPixels(px, 0, COLS, 0, 0, COLS, rows)
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

    // Встроенная фигура: силуэт в капюшоне (приблизительный, не копия из видео)
    fun builtIn(): Mask {
        val bmp = Bitmap.createBitmap(200, 230, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        c.drawOval(RectF(15f, 125f, 185f, 330f), p)
        c.drawOval(RectF(48f, 18f, 152f, 150f), p)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        c.drawOval(RectF(76f, 62f, 124f, 126f), p)
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
        return Mask(COLS, rows, runs)
    }
}
