package com.codewall
import android.content.Context
import android.graphics.*
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class Mask(val rows: Int, val on: BooleanArray) {
    val cols = Shapes.COLS
    val depth: FloatArray = Shapes.depthMap(on, rows)
}

object Shapes {
    const val COLS = 88
    val NAMES = listOf("Капюшон", "Шар", "Сердце", "Звезда", "Череп", "Корона", "Молния", "Луна", "Алмаз",
        "Кошка", "Призрак", "Глаз", "Огонь", "Капля", "Облако", "Планета", "Курсор")

    fun rowsFor(w: Int, h: Int) = max(8, (COLS * h.toFloat() / w * 0.75f).roundToInt()).coerceAtMost(150)

    fun bitmapToOn(src: Bitmap, invert: Boolean, thr: Int): Pair<BooleanArray, Int> {
        val rows = rowsFor(src.width, src.height)
        val b = Bitmap.createScaledBitmap(src, COLS, rows, true)
        val px = IntArray(COLS * rows)
        b.getPixels(px, 0, COLS, 0, 0, COLS, rows)
        val hasAlpha = px.any { Color.alpha(it) < 128 }
        val bg = px[0]
        val on = BooleanArray(px.size) { i ->
            val p = px[i]
            val f = if (hasAlpha) Color.alpha(p) >= 128
            else abs(Color.red(p) - Color.red(bg)) + abs(Color.green(p) - Color.green(bg)) + abs(Color.blue(p) - Color.blue(bg)) > thr
            f != invert
        }
        return Pair(on, rows)
    }

    fun depthMap(on: BooleanArray, rows: Int): FloatArray {
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
        return FloatArray(n) { if (on[it]) sqrt(min(d[it], mx) / mx) else 0f }
    }

    fun builtIn(i: Int): Mask {
        val bmp = Bitmap.createBitmap(200, 220, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.WHITE
        val clear = Paint(Paint.ANTI_ALIAS_FLAG)
        clear.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        fun poly(pt: Paint, vararg v: Float) {
            val path = Path(); path.moveTo(v[0], v[1])
            var k = 2
            while (k < v.size) { path.lineTo(v[k], v[k + 1]); k += 2 }
            path.close(); c.drawPath(path, pt)
        }
        when (i) {
            1 -> c.drawCircle(100f, 110f, 98f, p)
            2 -> { c.drawCircle(68f, 78f, 55f, p); c.drawCircle(132f, 78f, 55f, p); poly(p, 16f, 104f, 184f, 104f, 100f, 212f) }
            3 -> {
                val v = FloatArray(20)
                for (k in 0 until 10) {
                    val a = -PI / 2 + k * PI / 5
                    val r = if (k % 2 == 0) 106.0 else 44.0
                    v[2 * k] = (100 + r * cos(a)).toFloat(); v[2 * k + 1] = (116 + r * sin(a)).toFloat()
                }
                poly(p, *v)
            }
            4 -> {
                c.drawCircle(100f, 86f, 76f, p); c.drawRoundRect(RectF(52f, 120f, 148f, 206f), 18f, 18f, p)
                c.drawCircle(72f, 92f, 20f, clear); c.drawCircle(128f, 92f, 20f, clear)
                poly(clear, 100f, 110f, 90f, 136f, 110f, 136f)
                for (x in floatArrayOf(80f, 98f, 116f)) c.drawRect(x, 172f, x + 4f, 210f, clear)
            }
            5 -> poly(p, 22f, 190f, 22f, 66f, 68f, 118f, 100f, 34f, 132f, 118f, 178f, 66f, 178f, 190f)
            6 -> poly(p, 118f, 6f, 40f, 124f, 92f, 124f, 70f, 214f, 162f, 92f, 110f, 92f, 150f, 6f)
            7 -> { c.drawCircle(100f, 110f, 96f, p); c.drawCircle(142f, 88f, 80f, clear) }
            8 -> poly(p, 50f, 20f, 150f, 20f, 192f, 82f, 100f, 206f, 8f, 82f)
            9 -> {
                c.drawCircle(100f, 128f, 74f, p)
                poly(p, 28f, 28f, 34f, 112f, 86f, 72f); poly(p, 172f, 28f, 166f, 112f, 114f, 72f)
                c.drawOval(RectF(60f, 112f, 86f, 146f), clear); c.drawOval(RectF(114f, 112f, 140f, 146f), clear)
            }
            10 -> {
                c.drawCircle(100f, 84f, 70f, p); c.drawRect(30f, 84f, 170f, 206f, p)
                for (x in floatArrayOf(48f, 100f, 152f)) c.drawCircle(x, 212f, 19f, clear)
                c.drawOval(RectF(64f, 70f, 88f, 108f), clear); c.drawOval(RectF(112f, 70f, 136f, 108f), clear)
            }
            11 -> {
                val path = Path(); path.moveTo(8f, 110f); path.quadTo(100f, 8f, 192f, 110f); path.quadTo(100f, 212f, 8f, 110f); path.close()
                c.drawPath(path, p); c.drawCircle(100f, 110f, 46f, clear); c.drawCircle(100f, 110f, 22f, p)
            }
            12 -> {
                val path = Path(); path.moveTo(104f, 8f)
                path.cubicTo(120f, 60f, 178f, 92f, 172f, 150f); path.cubicTo(168f, 192f, 134f, 214f, 100f, 214f)
                path.cubicTo(62f, 214f, 28f, 188f, 30f, 146f); path.cubicTo(31f, 112f, 56f, 92f, 70f, 60f)
                path.cubicTo(80f, 84f, 92f, 92f, 96f, 86f); path.cubicTo(104f, 66f, 98f, 34f, 104f, 8f); path.close()
                c.drawPath(path, p)
            }
            13 -> {
                val path = Path(); path.moveTo(100f, 8f)
                path.cubicTo(100f, 8f, 30f, 100f, 30f, 142f); path.cubicTo(30f, 182f, 62f, 212f, 100f, 212f)
                path.cubicTo(138f, 212f, 170f, 182f, 170f, 142f); path.cubicTo(170f, 100f, 100f, 8f, 100f, 8f); path.close()
                c.drawPath(path, p)
            }
            14 -> {
                c.drawCircle(62f, 132f, 46f, p); c.drawCircle(112f, 96f, 58f, p); c.drawCircle(156f, 136f, 42f, p)
                c.drawRoundRect(RectF(30f, 130f, 186f, 184f), 27f, 27f, p)
            }
            15 -> {
                c.drawCircle(100f, 110f, 64f, p); c.save(); c.rotate(-22f, 100f, 110f)
                val rp = Paint(p); rp.style = Paint.Style.STROKE; rp.strokeWidth = 14f
                c.drawOval(RectF(8f, 84f, 192f, 136f), rp); c.restore()
            }
            16 -> poly(p, 40f, 10f, 40f, 190f, 85f, 148f, 118f, 212f, 148f, 198f, 115f, 136f, 172f, 136f)
            else -> {
                c.drawOval(RectF(15f, 135f, 185f, 340f), p); c.drawOval(RectF(48f, 16f, 152f, 160f), p)
                c.drawOval(RectF(76f, 70f, 124f, 136f), clear)
            }
        }
        val (on, rows) = bitmapToOn(bmp, false, 90)
        return Mask(rows, on)
    }

    fun fromText(text: String): Mask {
        val t = text.take(14).ifEmpty { "POI" }
        val tp = Paint(Paint.ANTI_ALIAS_FLAG)
        tp.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); tp.textSize = 120f; tp.color = Color.WHITE
        val w = max(60, tp.measureText(t).toInt() + 24)
        val bmp = Bitmap.createBitmap(w, 170, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(t, 12f, 125f, tp)
        val (on, rows) = bitmapToOn(bmp, false, 90)
        return Mask(rows, on)
    }

    fun dir(ctx: Context): File { val d = File(ctx.filesDir, "shapes"); d.mkdirs(); return d }

    fun resolve(ctx: Context, id: String): Mask? {
        return try {
            when {
                id.startsWith("b:") -> builtIn(id.substring(2).toIntOrNull()?.coerceIn(0, NAMES.size - 1) ?: 0)
                id.startsWith("t:") -> fromText(id.substring(2))
                id.startsWith("f:") -> {
                    val f = File(dir(ctx), File(id.substring(2)).name)
                    if (f.exists()) Formats.parsePoi(f.readText()).second else null
                }
                else -> null
            }
        } catch (e: Exception) { null }
    }

    fun nameOf(id: String): String = when {
        id.startsWith("b:") -> NAMES.getOrElse(id.substring(2).toIntOrNull() ?: 0) { "Фигура" }
        id.startsWith("t:") -> id.substring(2)
        else -> "Моя фигура"
    }

    fun save(ctx: Context, name: String, m: Mask): String {
        val fn = "p" + System.currentTimeMillis() + ".poi"
        File(dir(ctx), fn).writeText(Formats.poiToText(name, m))
        return "f:$fn"
    }

    fun delete(ctx: Context, id: String) { if (id.startsWith("f:")) File(dir(ctx), File(id.substring(2)).name).delete() }

    fun list(ctx: Context): List<Pair<String, String>> =
        (dir(ctx).listFiles() ?: emptyArray<File>()).filter { it.name.endsWith(".poi") }
            .sortedByDescending { it.lastModified() }
            .mapNotNull { f -> try { Pair("f:" + f.name, Formats.parsePoi(f.readText()).first) } catch (e: Exception) { null } }
}
