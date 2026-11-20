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
        "Кошка", "Призрак", "Глаз", "Огонь", "Капля", "Облако", "Планета", "Курсор",
        "Ракета", "Прицел", "Граната", "Геймпад", "Щит", "Наушники")

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


    /** Умное превращение фото в фигуру: 3 способа выделения, сглаживание, заливка дыр, удаление мусора. */
    fun analyze(src: Bitmap, method: Int, thr: Int, invert: Boolean, smooth: Int, fill: Boolean, despeckle: Int): Pair<BooleanArray, Int> {
        val rows = rowsFor(src.width, src.height)
        val b = Bitmap.createScaledBitmap(src, COLS, rows, true)
        val px = IntArray(COLS * rows)
        b.getPixels(px, 0, COLS, 0, 0, COLS, rows)
        val n = px.size
        val hasAlpha = px.any { Color.alpha(it) < 128 }
        var on = BooleanArray(n)
        if (hasAlpha) {
            for (i in 0 until n) on[i] = Color.alpha(px[i]) >= 128
        } else if (method == 0) {
            var sr = 0; var sg = 0; var sb = 0; var cnt = 0
            for (x in 0 until COLS) for (y in intArrayOf(0, rows - 1)) { val q = px[y * COLS + x]; sr += Color.red(q); sg += Color.green(q); sb += Color.blue(q); cnt++ }
            for (y in 0 until rows) for (x in intArrayOf(0, COLS - 1)) { val q = px[y * COLS + x]; sr += Color.red(q); sg += Color.green(q); sb += Color.blue(q); cnt++ }
            val br = sr / cnt; val bgc = sg / cnt; val bb = sb / cnt
            for (i in 0 until n) { val q = px[i]; on[i] = abs(Color.red(q) - br) + abs(Color.green(q) - bgc) + abs(Color.blue(q) - bb) > thr }
        } else {
            val lum = IntArray(n) { (Color.red(px[it]) * 30 + Color.green(px[it]) * 59 + Color.blue(px[it]) * 11) / 100 }
            if (method == 1) {
                for (i in 0 until n) on[i] = lum[i] > thr
            } else {
                for (y in 1 until rows - 1) for (x in 1 until COLS - 1) {
                    val i = y * COLS + x
                    on[i] = abs(lum[i + 1] - lum[i - 1]) + abs(lum[i + COLS] - lum[i - COLS]) > thr / 2
                }
            }
        }
        if (invert) for (i in 0 until n) on[i] = !on[i]
        repeat(smooth) { on = majority(on, rows) }
        if (fill) on = fillHoles(on, rows)
        if (despeckle > 0) on = despeckle(on, rows, despeckle * 3)
        return Pair(on, rows)
    }

    private fun majority(on: BooleanArray, rows: Int): BooleanArray {
        val out = BooleanArray(on.size)
        for (y in 0 until rows) for (x in 0 until COLS) {
            var c = 0
            for (dy in -1..1) for (dx in -1..1) {
                val xx = x + dx; val yy = y + dy
                if (xx in 0 until COLS && yy in 0 until rows && on[yy * COLS + xx]) c++
            }
            out[y * COLS + x] = c >= 5
        }
        return out
    }

    private fun fillHoles(on: BooleanArray, rows: Int): BooleanArray {
        val seen = BooleanArray(on.size); val q = IntArray(on.size)
        var h = 0; var t = 0
        fun push(i: Int) { if (!on[i] && !seen[i]) { seen[i] = true; q[t++] = i } }
        for (x in 0 until COLS) { push(x); push((rows - 1) * COLS + x) }
        for (y in 0 until rows) { push(y * COLS); push(y * COLS + COLS - 1) }
        while (h < t) {
            val i = q[h++]; val x = i % COLS; val y = i / COLS
            if (x > 0) push(i - 1)
            if (x < COLS - 1) push(i + 1)
            if (y > 0) push(i - COLS)
            if (y < rows - 1) push(i + COLS)
        }
        return BooleanArray(on.size) { on[it] || !seen[it] }
    }

    private fun despeckle(on: BooleanArray, rows: Int, minSize: Int): BooleanArray {
        val out = on.copyOf(); val seen = BooleanArray(on.size); val q = IntArray(on.size)
        for (s in on.indices) {
            if (!on[s] || seen[s]) continue
            var h = 0; var t = 0
            q[t++] = s; seen[s] = true
            while (h < t) {
                val i = q[h++]; val x = i % COLS; val y = i / COLS
                if (x > 0 && on[i - 1] && !seen[i - 1]) { seen[i - 1] = true; q[t++] = i - 1 }
                if (x < COLS - 1 && on[i + 1] && !seen[i + 1]) { seen[i + 1] = true; q[t++] = i + 1 }
                if (y > 0 && on[i - COLS] && !seen[i - COLS]) { seen[i - COLS] = true; q[t++] = i - COLS }
                if (y < rows - 1 && on[i + COLS] && !seen[i + COLS]) { seen[i + COLS] = true; q[t++] = i + COLS }
            }
            if (t < minSize) for (k in 0 until t) out[q[k]] = false
        }
        return out
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
            17 -> {
                val body = Path(); body.moveTo(100f, 8f); body.cubicTo(152f, 50f, 152f, 120f, 142f, 160f); body.lineTo(58f, 160f)
                body.cubicTo(48f, 120f, 48f, 50f, 100f, 8f); body.close(); c.drawPath(body, p)
                poly(p, 60f, 118f, 22f, 172f, 60f, 160f); poly(p, 140f, 118f, 178f, 172f, 140f, 160f)
                c.drawRect(80f, 160f, 120f, 176f, p); poly(p, 84f, 180f, 116f, 180f, 100f, 214f)
                c.drawCircle(100f, 82f, 18f, clear); c.drawCircle(100f, 82f, 11f, p)
            }
            18 -> {
                val rp = Paint(p); rp.style = Paint.Style.STROKE; rp.strokeWidth = 12f
                c.drawCircle(100f, 110f, 68f, rp)
                c.drawLine(100f, 8f, 100f, 62f, rp); c.drawLine(100f, 158f, 100f, 212f, rp)
                c.drawLine(8f, 110f, 62f, 110f, rp); c.drawLine(138f, 110f, 192f, 110f, rp)
                c.drawCircle(100f, 110f, 7f, p)
            }
            19 -> {
                c.drawCircle(100f, 130f, 70f, p); c.drawRect(82f, 38f, 118f, 76f, p)
                poly(p, 116f, 44f, 162f, 34f, 168f, 50f, 124f, 62f)
                val rp = Paint(p); rp.style = Paint.Style.STROKE; rp.strokeWidth = 8f; c.drawCircle(66f, 40f, 18f, rp)
                c.drawRect(30f, 104f, 170f, 110f, clear); c.drawRect(30f, 130f, 170f, 136f, clear); c.drawRect(30f, 156f, 170f, 162f, clear)
                c.drawRect(97f, 80f, 103f, 200f, clear)
            }
            20 -> {
                c.drawRoundRect(RectF(14f, 64f, 186f, 150f), 44f, 44f, p)
                c.drawCircle(52f, 148f, 34f, p); c.drawCircle(148f, 148f, 34f, p)
                c.drawRect(38f, 104f, 66f, 114f, clear); c.drawRect(47f, 95f, 57f, 123f, clear)
                c.drawCircle(148f, 94f, 8f, clear); c.drawCircle(166f, 108f, 8f, clear)
                c.drawCircle(148f, 122f, 8f, clear); c.drawCircle(130f, 108f, 8f, clear)
            }
            21 -> {
                val sh = Path(); sh.moveTo(100f, 10f); sh.lineTo(182f, 40f); sh.lineTo(174f, 122f)
                sh.cubicTo(166f, 162f, 132f, 192f, 100f, 212f); sh.cubicTo(68f, 192f, 34f, 162f, 26f, 122f)
                sh.lineTo(18f, 40f); sh.close(); c.drawPath(sh, p)
                c.drawRect(92f, 56f, 108f, 160f, clear); c.drawRect(58f, 92f, 142f, 108f, clear)
            }
            22 -> {
                val rp = Paint(p); rp.style = Paint.Style.STROKE; rp.strokeWidth = 16f
                c.drawArc(RectF(28f, 20f, 172f, 170f), 180f, 180f, false, rp)
                c.drawRoundRect(RectF(14f, 100f, 58f, 176f), 18f, 18f, p); c.drawRoundRect(RectF(142f, 100f, 186f, 176f), 18f, 18f, p)
                c.drawRect(22f, 112f, 30f, 164f, clear)
            }
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

    val tmp = HashMap<String, Mask>()

    fun resolve(ctx: Context, id: String): Mask? {
        return try {
            when {
                id.startsWith("b:") -> builtIn(id.substring(2).toIntOrNull()?.coerceIn(0, NAMES.size - 1) ?: 0)
                id.startsWith("m:") -> tmp[id]
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
