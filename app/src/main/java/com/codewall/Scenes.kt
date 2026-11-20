package com.codewall
import android.content.Context
import android.graphics.*
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Частичные сцены: 1 — чёрная дыра, 2 — подарок → фигура, 3 — сердце из слов. Всё рисуется пачками (drawPoints). */
class Scenes(private val ctx: Context, private val prefs: Prefs) {
    private val built = BooleanArray(8)
    private val rnd = Random(11)
    private val pp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val gp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.BLACK }
    private var glowSh: RadialGradient? = null
    private var glowKey = 0

    fun reload() { built.fill(false) }

    private fun density(): Float {
        val q = when (prefs.quality) { 0 -> 0.6f; 2 -> 1.3f; else -> 1f }
        return prefs.density * q
    }

    fun draw(type: Int, c: Canvas, w: Float, h: Float, t: Float, cyFrac: Float) {
        if (!built[type]) {
            when (type) { 1 -> buildHole(); 2 -> buildGift(); 3 -> buildHeart(); 4 -> buildPlanet(); 5 -> buildGalaxy(); else -> buildMatrix() }
            built[type] = true
        }
        c.drawColor(Color.BLACK)
        when (type) {
            1 -> drawHole(c, w, h, t, cyFrac); 2 -> drawGift(c, w, h, t, cyFrac); 3 -> drawHeart(c, w, h, t, cyFrac)
            4 -> drawPlanet(c, w, h, t, cyFrac); 5 -> drawGalaxy(c, w, h, t, cyFrac); else -> drawMatrix(c, w, h, t, cyFrac)
        }
    }

    // ---------- общие помощники ----------
    private fun lerpC(a: Int, b: Int, k: Float): Int {
        val r = (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt()
        val g = (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt()
        val bl = (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt()
        return Color.rgb(r, g, bl)
    }
    private fun shade(c: Int, k: Float) = if (k >= 0f) lerpC(c, Color.WHITE, k) else lerpC(c, Color.BLACK, -k)
    private fun easeIO(x: Float): Float { val v = x.coerceIn(0f, 1f); return v * v * (3f - 2f * v) }
    private fun easeOut(x: Float): Float { val v = 1f - x.coerceIn(0f, 1f); return 1f - v * v * v }

    private fun glow(c: Canvas, cx: Float, cy: Float, r: Float, col: Int, alpha: Int) {
        val key = ((cx.toInt() * 31 + cy.toInt()) * 31 + r.toInt()) * 31 + col
        if (glowSh == null || key != glowKey) {
            glowSh = RadialGradient(cx, cy, r, (col and 0x00FFFFFF) or 0x55000000, col and 0x00FFFFFF, Shader.TileMode.CLAMP)
            glowKey = key
        }
        gp.shader = glowSh
        gp.alpha = alpha.coerceIn(0, 255)
        c.drawCircle(cx, cy, r, gp)
    }

    // ---------- 1. ЧЁРНАЯ ДЫРА ----------
    private lateinit var hR: FloatArray
    private lateinit var hT: FloatArray
    private lateinit var hW: FloatArray
    private lateinit var hZ: FloatArray
    private var hOff = IntArray(7)
    private var hBack = Array(6) { FloatArray(2) }
    private var hFront = Array(6) { FloatArray(2) }
    private val hBn = IntArray(6)
    private val hFn = IntArray(6)
    private lateinit var stars: FloatArray
    private val starPts = FloatArray(300)
    private val ringP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private fun buildHole() {
        val per = ((1400 * density()).toInt().coerceIn(300, 2400)) / 6
        val n = per * 6
        hOff = IntArray(7) { it * per }
        hR = FloatArray(n); hT = FloatArray(n); hW = FloatArray(n); hZ = FloatArray(n)
        val rings = prefs.holeRings.coerceIn(6, 30)
        var k = 0
        for (b in 0 until 6) {
            val j0 = (5 - b) * rings / 6
            val j1 = (6 - b) * rings / 6
            for (m in 0 until per) {
                val j = j0 + rnd.nextInt(max(1, j1 - j0))
                val rr = 0.26f + 0.74f * (j.toFloat() / (rings - 1)).pow(1.15f) + (rnd.nextGaussian() * 0.004).toFloat()
                hR[k] = rr; hT[k] = rnd.nextFloat() * 6.2831853f
                hW[k] = 0.32f / rr.pow(1.5f); hZ[k] = (rnd.nextGaussian() * 0.006).toFloat(); k++
            }
        }
        hBack = Array(6) { FloatArray(per * 2) }
        hFront = Array(6) { FloatArray(per * 2) }
        stars = FloatArray(300) { rnd.nextFloat() }
    }

    private fun drawHole(c: Canvas, w: Float, h: Float, t: Float, cyFrac: Float) {
        val col = prefs.colorInt
        val s = min(w, h * 0.95f) * 0.46f
        val cx = w / 2; val cy = h * cyFrac
        val ang = t * prefs.speed
        // звёзды
        for (i in 0 until 150) { starPts[2 * i] = stars[2 * i] * w; starPts[2 * i + 1] = stars[2 * i + 1] * h }
        pp.color = Color.WHITE; pp.strokeWidth = max(1.5f, s * 0.006f)
        pp.alpha = (110 + 80 * sin(t * 1.7f)).toInt(); c.drawPoints(starPts, 0, 150, pp)
        pp.alpha = (110 + 80 * cos(t * 1.3f)).toInt(); c.drawPoints(starPts, 150, 150, pp)
        glow(c, cx, cy, s * 1.15f, col, 110)

        val tilt = prefs.holeTilt; val st = sin(tilt); val ct = cos(tilt)
        val roll = prefs.holeRoll; val cr = cos(roll); val sr = sin(roll)
        for (b in 0 until 6) {
            var nb = 0; var nf = 0
            val bp = hBack[b]; val fp = hFront[b]
            for (i in hOff[b] until hOff[b + 1]) {
                val th = hT[i] + hW[i] * ang
                val x0 = hR[i] * cos(th); val z0 = hR[i] * sin(th); val y0 = hZ[i]
                val yp = z0 * st + y0 * ct
                val xx = x0 * cr - yp * sr; val yy = x0 * sr + yp * cr
                val dz = z0 * ct - y0 * st
                val f = 1f / (1f - dz * 0.2f)
                val sx = cx + xx * s * f; val sy = cy + yy * s * f
                if (dz < 0f) { bp[2 * nb] = sx; bp[2 * nb + 1] = sy; nb++ } else { fp[2 * nf] = sx; fp[2 * nf + 1] = sy; nf++ }
            }
            hBn[b] = nb; hFn[b] = nf
        }
        for (pass in 0..1) {
            if (pass == 1) {
                fillP.color = Color.BLACK; c.drawCircle(cx, cy, s * 0.2f, fillP)
                ringP.color = shade(col, 0.7f); ringP.alpha = 210; ringP.strokeWidth = s * 0.012f
                c.drawCircle(cx, cy, s * 0.207f, ringP)
            }
            for (b in 0 until 6) {
                val k = b / 5f
                pp.color = shade(col, (k - 0.45f) * 1.6f)
                pp.strokeWidth = max(1.5f, s * 0.0065f * (0.8f + 0.5f * k))
                if (pass == 0) c.drawPoints(hBack[b], 0, hBn[b] * 2, pp) else c.drawPoints(hFront[b], 0, hFn[b] * 2, pp)
            }
        }
    }

    // ---------- 2. ПОДАРОК → ФИГУРА ----------
    private val pal = IntArray(12)
    private val fixedIdx = intArrayOf(0, 4, 5, 6, 7, 8, 9, 10, 11)
    private var gN = 0
    private lateinit var bx: FloatArray
    private lateinit var by: FloatArray
    private lateinit var tx: FloatArray
    private lateinit var ty: FloatArray
    private lateinit var dxv: FloatArray
    private lateinit var dyv: FloatArray
    private lateinit var er: FloatArray
    private lateinit var sw: FloatArray
    private lateinit var bz: FloatArray
    private lateinit var tzv: FloatArray
    private lateinit var ez: FloatArray
    private lateinit var lid: BooleanArray
    private lateinit var ordA: IntArray
    private lateinit var ordB: IntArray
    private var offA = IntArray(13)
    private var offB = IntArray(13)
    private lateinit var gpts: FloatArray

    private fun nearest(px: Int, idxs: IntArray): Int {
        var best = idxs[0]; var bd = Int.MAX_VALUE
        for (i in idxs) {
            val q = pal[i]
            val dr = Color.red(px) - Color.red(q); val dg = Color.green(px) - Color.green(q); val db = Color.blue(px) - Color.blue(q)
            val d = dr * dr + dg * dg + db * db
            if (d < bd) { bd = d; best = i }
        }
        return best
    }

    private fun boxBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(200, 230, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint()
        fun poly(col: Int, vararg v: Float) {
            p.style = Paint.Style.FILL; p.color = col
            val path = Path(); path.moveTo(v[0], v[1])
            var k = 2
            while (k < v.size) { path.lineTo(v[k], v[k + 1]); k += 2 }
            path.close(); c.drawPath(path, p)
        }
        poly(pal[5], 40f, 95f, 100f, 120f, 100f, 192f, 40f, 167f)
        poly(pal[6], 100f, 120f, 160f, 95f, 160f, 167f, 100f, 192f)
        poly(pal[0], 62f, 103f, 78f, 110f, 78f, 182f, 62f, 175f)
        poly(pal[0], 122f, 110f, 138f, 103f, 138f, 175f, 122f, 182f)
        poly(pal[4], 100f, 70f, 160f, 95f, 100f, 120f, 40f, 95f)
        poly(pal[0], 92f, 74f, 108f, 74f, 108f, 118f, 92f, 118f)
        p.style = Paint.Style.STROKE; p.strokeWidth = 7f; p.color = pal[0]
        c.save(); c.rotate(-28f, 82f, 62f); c.drawOval(RectF(62f, 50f, 102f, 74f), p); c.restore()
        c.save(); c.rotate(28f, 118f, 62f); c.drawOval(RectF(98f, 50f, 138f, 74f), p); c.restore()
        p.style = Paint.Style.FILL; c.drawCircle(100f, 72f, 8f, p)
        return bmp
    }

    private fun bearBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(200, 230, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint()
        p.style = Paint.Style.FILL
        fun oval(col: Int, l: Float, t: Float, r: Float, b: Float) { p.color = col; c.drawOval(RectF(l, t, r, b), p) }
        oval(pal[9], 40f, 190f, 90f, 222f); oval(pal[9], 110f, 190f, 160f, 222f)
        oval(pal[4], 52f, 200f, 78f, 218f); oval(pal[4], 122f, 200f, 148f, 218f)
        oval(pal[9], 46f, 112f, 154f, 212f)
        oval(pal[7], 66f, 130f, 134f, 204f)
        oval(pal[9], 26f, 120f, 62f, 180f); oval(pal[9], 138f, 120f, 174f, 180f)
        oval(pal[8], 36f, 160f, 64f, 184f); oval(pal[8], 136f, 160f, 164f, 184f)
        p.color = pal[6]
        c.drawCircle(86f, 148f, 20f, p); c.drawCircle(114f, 148f, 20f, p)
        val hp = Path(); hp.moveTo(66f, 156f); hp.lineTo(134f, 156f); hp.lineTo(100f, 196f); hp.close(); c.drawPath(hp, p)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG); tp.color = pal[0]; tp.typeface = Typeface.DEFAULT_BOLD; tp.textAlign = Paint.Align.CENTER
        val lab = prefs.label.take(14).ifEmpty { "I LOVE YOU" }
        tp.textSize = 16f
        val wd = tp.measureText(lab)
        if (wd > 56f) tp.textSize = 16f * 56f / wd
        c.drawText(lab, 100f, 160f, tp)
        oval(pal[9], 44f, 30f, 156f, 126f)
        oval(pal[9], 40f, 22f, 82f, 62f); oval(pal[9], 118f, 22f, 160f, 62f)
        oval(pal[4], 50f, 32f, 72f, 54f); oval(pal[4], 128f, 32f, 150f, 54f)
        oval(pal[7], 76f, 82f, 124f, 118f)
        oval(pal[8], 93f, 84f, 107f, 94f)
        oval(pal[0], 74f, 62f, 90f, 78f); oval(pal[0], 110f, 62f, 126f, 78f)
        oval(pal[8], 79f, 66f, 87f, 76f); oval(pal[8], 113f, 66f, 121f, 76f)
        return bmp
    }

    private fun buildGift() {
        val acc = prefs.colorInt
        pal[0] = Color.WHITE; pal[1] = shade(acc, 0.45f); pal[2] = acc; pal[3] = shade(acc, -0.45f)
        pal[4] = 0xFFFF9FC4.toInt(); pal[5] = 0xFFFF4D88.toInt(); pal[6] = 0xFFE0203C.toInt(); pal[7] = 0xFFB9A6FF.toInt()
        pal[8] = 0xFF7D5CE0.toInt(); pal[9] = 0xFF4A6FE3.toInt(); pal[10] = 0xFFE8C79A.toInt(); pal[11] = 0xFFFFD84D.toInt()
        val n = (3000 * density()).toInt().coerceIn(600, 4800)
        gN = n
        bx = FloatArray(n); by = FloatArray(n); tx = FloatArray(n); ty = FloatArray(n)
        dxv = FloatArray(n); dyv = FloatArray(n); er = FloatArray(n); sw = FloatArray(n)
        lid = BooleanArray(n)
        bz = FloatArray(n) { (rnd.nextFloat() - 0.5f) * 0.5f }
        tzv = FloatArray(n) { (rnd.nextFloat() - 0.5f) * 0.4f }
        ez = FloatArray(n) { (rnd.nextFloat() - 0.5f) * 1.6f }
        val colA = IntArray(n); val colB = IntArray(n)

        // коробка
        run {
            val bmp = boxBitmap(); val W = bmp.width
            val px = IntArray(W * bmp.height); bmp.getPixels(px, 0, W, 0, 0, W, bmp.height)
            val pool = ArrayList<Int>()
            for (i in px.indices) { if (Color.alpha(px[i]) < 128) continue; val ci = nearest(px[i], fixedIdx); repeat(if (ci == 0) 2 else 1) { pool.add(i) } }
            for (i in 0 until n) {
                val pk = pool[rnd.nextInt(pool.size)]
                val x = pk % W + rnd.nextFloat(); val y = pk / W + rnd.nextFloat()
                bx[i] = (x - 100f) / 100f; by[i] = (y - 115f) / 100f
                colA[i] = nearest(px[pk], fixedIdx); lid[i] = (pk / W) < 118
            }
        }
        // цель: мишка или своя фигура
        var done = false
        if (prefs.target == 1) {
            val m = Shapes.resolve(ctx, prefs.giftShape)
            if (m != null) {
                val pool = ArrayList<Int>()
                for (i in m.on.indices) if (m.on[i]) pool.add(i)
                if (pool.isNotEmpty()) {
                    for (i in 0 until n) {
                        val pk = pool[rnd.nextInt(pool.size)]
                        val gx = pk % m.cols + rnd.nextFloat(); val gy = pk / m.cols + rnd.nextFloat()
                        val half = m.cols / 2f
                        tx[i] = (gx - half) / half; ty[i] = (gy - m.rows / 2f) / half * 1.333f
                        val d = m.depth[pk]
                        tzv[i] = (if (rnd.nextBoolean()) 1f else -1f) * d * 0.35f
                        colB[i] = if (d > 0.85f) 0 else if (d > 0.55f) 1 else if (d > 0.25f) 2 else 3
                    }
                    done = true
                }
            }
        }
        if (!done) {
            val bmp = bearBitmap(); val W = bmp.width
            val px = IntArray(W * bmp.height); bmp.getPixels(px, 0, W, 0, 0, W, bmp.height)
            val pool = ArrayList<Int>()
            for (i in px.indices) { if (Color.alpha(px[i]) < 128) continue; val ci = nearest(px[i], fixedIdx); repeat(if (ci == 0 || ci == 6) 3 else 1) { pool.add(i) } }
            for (i in 0 until n) {
                val pk = pool[rnd.nextInt(pool.size)]
                tx[i] = ((pk % W + rnd.nextFloat()) - 100f) / 100f; ty[i] = ((pk / W + rnd.nextFloat()) - 115f) / 100f
                colB[i] = nearest(px[pk], fixedIdx)
            }
        }
        for (i in 0 until n) {
            val a = rnd.nextFloat() * 6.2831853f
            dxv[i] = cos(a); dyv[i] = sin(a); er[i] = 0.7f + rnd.nextFloat(); sw[i] = if (rnd.nextBoolean()) 1f else -1f
        }
        ordA = IntArray(n); ordB = IntArray(n); offA = IntArray(13); offB = IntArray(13)
        sortBy(colA, ordA, offA); sortBy(colB, ordB, offB)
        gpts = FloatArray(n * 2)
    }

    private fun sortBy(col: IntArray, ord: IntArray, off: IntArray) {
        val cnt = IntArray(12)
        for (c in col) cnt[c]++
        for (b in 0 until 12) off[b + 1] = off[b] + cnt[b]
        val pos = IntArray(12) { off[it] }
        for (i in col.indices) { ord[pos[col[i]]++] = i }
    }

    private fun liftAmt(tt: Float): Float = when {
        tt < 3f -> 0f
        tt < 4.2f -> easeOut((tt - 3f) / 1.2f)
        tt < 12.2f -> 1f
        else -> 1f - easeIO((tt - 12.2f) / 1.8f)
    }

    private fun drawGift(c: Canvas, w: Float, h: Float, t: Float, cyFrac: Float) {
        val s = min(w * 0.40f, h * 0.30f)
        val cx = w / 2; val cy = h * cyFrac
        val tt = (((t * prefs.speed) % prefs.giftLoop) / prefs.giftLoop) * 14f
        val lift = liftAmt(tt) * 0.55f
        val mixZ = when { tt < 4.2f -> 0f; tt < 8.5f -> easeIO((tt - 4.2f) / 4.3f); tt < 12.2f -> 1f; else -> 1f - easeIO((tt - 12.2f) / 1.8f) }
        val chaosZ = if (tt in 4.2f..8.5f) sin((tt - 4.2f) / 4.3f * 3.14159f) else if (tt > 12.2f) sin((tt - 12.2f) / 1.8f * 3.14159f) else 0f
        val gAng = sin(t * 0.5f) * 0.6f
        val gca = cos(gAng); val gsa = sin(gAng)
        val useBear = tt > 6.3f && tt < 13.1f
        val ord = if (useBear) ordB else ordA
        val off = if (useBear) offB else offA
        glow(c, cx, cy, s * 1.5f, pal[5], if (tt in 4.2f..8.5f) 150 else 90)
        pp.strokeWidth = max(2f, s * 0.012f)
        val pi = PI.toFloat()
        for (b in 0 until 12) {
            val s0 = off[b]; val s1 = off[b + 1]
            if (s1 == s0) continue
            for (j in s0 until s1) {
                val i = ord[j]
                val bxx = bx[i]; val byy = by[i] - (if (lid[i]) lift else 0f)
                var x: Float; var y: Float
                if (tt < 4.2f) {
                    x = bxx; y = byy
                    if (tt > 3f) x += sin(tt * 40f + i) * 0.01f
                } else if (tt < 8.5f) {
                    val mm = (tt - 4.2f) / 4.3f
                    val exx = bxx + dxv[i] * er[i]; val eyy = byy + dyv[i] * er[i]
                    if (mm < 0.3f) {
                        val e = easeOut(mm / 0.3f); x = bxx + (exx - bxx) * e; y = byy + (eyy - byy) * e
                    } else {
                        val e = easeIO((mm - 0.3f) / 0.7f); val sp = sin(e * pi) * 0.25f * sw[i]
                        x = exx + (tx[i] - exx) * e - dyv[i] * sp; y = eyy + (ty[i] - eyy) * e + dxv[i] * sp
                    }
                } else if (tt < 12.2f) {
                    val br = 1f + 0.012f * sin(tt * 2.2f)
                    x = tx[i] * br; y = ty[i] * br
                } else {
                    val e = easeIO((tt - 12.2f) / 1.8f); val sp = sin(e * pi) * 0.35f
                    x = tx[i] + (bxx - tx[i]) * e + dxv[i] * sp; y = ty[i] + (byy - ty[i]) * e + dyv[i] * sp
                }
                val zz = bz[i] + (tzv[i] - bz[i]) * mixZ + ez[i] * chaosZ
                val x1 = x * gca + zz * gsa
                val z1 = -x * gsa + zz * gca
                val pf = 1f / (1f - z1 * 0.35f).coerceAtLeast(0.4f)
                gpts[2 * j] = cx + x1 * s * pf; gpts[2 * j + 1] = cy + y * s * pf
            }
            pp.color = pal[b]
            c.drawPoints(gpts, 2 * s0, 2 * (s1 - s0), pp)
        }
    }

    // ---------- 3. СЕРДЦЕ ИЗ СЛОВ ----------
    private var hwN = 0
    private var hwTxt: Array<String> = emptyArray()
    private lateinit var hx: FloatArray
    private lateinit var hy: FloatArray
    private lateinit var hz: FloatArray
    private lateinit var ux: FloatArray
    private lateinit var uy: FloatArray
    private lateinit var uz: FloatArray
    private lateinit var hsz: FloatArray
    private lateinit var hcol: IntArray
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textAlign = Paint.Align.CENTER }
    private val cp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER }
    private var capLines: List<String> = emptyList()
    private var capW = -1f
    private var capMsg = ""

    private fun buildHeart() {
        val acc = prefs.colorInt
        val words = prefs.words.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(14).ifEmpty { listOf("I love you") }
        val n = (68 * density()).toInt().coerceIn(24, 150)
        hwN = n
        hx = FloatArray(n); hy = FloatArray(n); hz = FloatArray(n)
        ux = FloatArray(n); uy = FloatArray(n); uz = FloatArray(n)
        hsz = FloatArray(n); hcol = IntArray(n)
        hwTxt = Array(n) { i -> if (rnd.nextFloat() < 0.1f) "♥" else words[(i + rnd.nextInt(3)) % words.size] }
        for (i in 0 until n) {
            heartPoint(i)
            var gx = rnd.nextGaussian().toFloat(); var gy = rnd.nextGaussian().toFloat(); var gz = rnd.nextGaussian().toFloat()
            val len = max(0.2f, sqrt(gx * gx + gy * gy + gz * gz)); val rad = 1.8f + rnd.nextFloat() * 0.8f
            ux[i] = gx / len * rad; uy[i] = gy / len * rad; uz[i] = gz / len * rad
            hsz[i] = if (hwTxt[i] == "♥") 1.0f else 0.7f + rnd.nextFloat() * 0.7f
            hcol[i] = shade(acc, rnd.nextFloat() * 0.85f - 0.35f)
        }
    }

    private fun heartPoint(i: Int) {
        when (prefs.heartShape) {
            1 -> {
                val gx = rnd.nextGaussian().toFloat(); val gy = rnd.nextGaussian().toFloat(); val gz = rnd.nextGaussian().toFloat()
                val len = max(0.2f, sqrt(gx * gx + gy * gy + gz * gz)); val r = 1.1f * Math.cbrt(rnd.nextDouble()).toFloat()
                hx[i] = gx / len * r; hy[i] = gy / len * r; hz[i] = gz / len * r
            }
            2 -> {
                val th = rnd.nextFloat() * 6.2831853f; val ph = rnd.nextFloat() * 6.2831853f
                val big = 0.8f; val sm = 0.38f
                hx[i] = (big + sm * cos(ph)) * cos(th); hz[i] = (big + sm * cos(ph)) * sin(th); hy[i] = sm * sin(ph) * 1.2f
            }
            3 -> {
                val u = rnd.nextFloat(); val strand = if (rnd.nextBoolean()) 0f else 3.14159f
                val a = u * 18.85f + strand
                hx[i] = 0.7f * cos(a) + (rnd.nextFloat() - 0.5f) * 0.12f
                hz[i] = 0.7f * sin(a) + (rnd.nextFloat() - 0.5f) * 0.12f
                hy[i] = -1.2f + 2.4f * u
            }
            4 -> {
                var x: Float; var y: Float
                do {
                    x = rnd.nextFloat() * 2.4f - 1.2f; y = rnd.nextFloat() * 2.4f - 1.2f
                } while (sqrt(x * x + y * y) > 0.35f + 0.85f * abs(cos(2.5f * atan2(y, x))))
                hx[i] = x; hy[i] = y; hz[i] = (rnd.nextFloat() * 2f - 1f) * 0.3f
            }
            else -> {
                var x: Float; var y: Float
                do {
                    x = rnd.nextFloat() * 2.5f - 1.25f; y = rnd.nextFloat() * 2.5f - 1.25f
                    val a = x * x + y * y - 1f
                } while (a * a * a - x * x * y * y * y > 0f)
                val zr = 0.55f * sqrt(max(0.02f, 1f - (x * x + y * y) / 1.7f))
                hx[i] = x; hy[i] = y - 0.1f; hz[i] = (rnd.nextFloat() * 2f - 1f) * zr
            }
        }
    }

    private fun beatAt(t: Float): Float {
        val ph = (t % 1.1f) / 1.1f
        val a = (ph - 0.08f) * 14f; val b = (ph - 0.30f) * 14f
        return min(1f, exp(-a * a) + 0.65f * exp(-b * b))
    }

    private fun wrap(msg: String, maxW: Float) {
        val out = ArrayList<String>(); var line = ""
        for (word in msg.split(' ')) {
            val test = if (line.isEmpty()) word else "$line $word"
            if (cp.measureText(test) > maxW && line.isNotEmpty()) { out.add(line); line = word } else line = test
        }
        if (line.isNotEmpty()) out.add(line)
        capLines = out.take(4); capW = maxW; capMsg = msg
    }

    private fun drawHeart(c: Canvas, w: Float, h: Float, t: Float, cyFrac: Float) {
        val acc = prefs.colorInt
        val s = min(w * 0.34f, h * 0.26f)
        val cx = w / 2; val cy = h * cyFrac * 0.92f
        val tt = (t * prefs.speed) % 24f
        val g = when { tt < 4f -> easeIO(tt / 4f); tt < 20f -> 1f; else -> 1f - easeIO((tt - 20f) / 4f) }
        val beat = beatAt(t * prefs.beat)
        val pulse = 1f + 0.07f * beat * g
        glow(c, cx, cy, s * 1.7f * (1f + 0.08f * beat), acc, (60 + 110 * beat * g).toInt())
        val ang = t * 0.45f * prefs.speed * prefs.heartSpin
        val ca = cos(ang); val sa = sin(ang)
        for (i in 0 until hwN) {
            val px = (ux[i] + (hx[i] * pulse - ux[i]) * g)
            val py = (uy[i] + (hy[i] * pulse - uy[i]) * g)
            val pz = (uz[i] + (hz[i] * pulse - uz[i]) * g)
            val x1 = px * ca + pz * sa; val z1 = -px * sa + pz * ca
            val f = min(2.5f, 4f / (4f - z1))
            tp.textSize = s * 0.12f * hsz[i] * f
            val dep = ((z1 + 1.4f) / 2.8f).coerceIn(0.12f, 1f)
            tp.color = hcol[i]
            tp.alpha = (dep * 255f * (0.4f + 0.6f * g)).toInt()
            c.drawText(hwTxt[i], cx + x1 * s * f, cy - py * s * f, tp)
        }
        // письмо с печатающимся текстом
        val msg = prefs.message
        if (msg.isNotEmpty()) {
            cp.textSize = max(12f, h * 0.022f)
            if (msg != capMsg || capW != w * 0.8f) wrap(msg, w * 0.8f)
            var left = ((tt - 2f).coerceAtLeast(0f) * 14f).toInt()
            cp.color = shade(acc, 0.5f)
            cp.alpha = if (tt > 21f) (255 * (24f - tt) / 3f).toInt().coerceIn(0, 255) else 255
            var y = h * 0.80f
            for (ln in capLines) {
                if (left <= 0) break
                c.drawText(if (left >= ln.length) ln else ln.substring(0, left), w / 2, y, cp)
                left -= ln.length + 1; y += cp.textSize * 1.5f
            }
        }
    }

    private fun initStars() { stars = FloatArray(300) { rnd.nextFloat() } }

    private fun drawStarsBg(c: Canvas, w: Float, h: Float, t: Float, sz: Float) {
        for (i in 0 until 150) { starPts[2 * i] = stars[2 * i] * w; starPts[2 * i + 1] = stars[2 * i + 1] * h }
        pp.color = Color.WHITE; pp.strokeWidth = max(1.5f, sz * 0.006f)
        pp.alpha = (110 + 80 * sin(t * 1.7f)).toInt(); c.drawPoints(starPts, 0, 150, pp)
        pp.alpha = (110 + 80 * cos(t * 1.3f)).toInt(); c.drawPoints(starPts, 150, 150, pp)
    }

    // ---------- 4. ПЛАНЕТА ----------
    private val PB = 7
    private var plN = 0
    private lateinit var plX: FloatArray
    private lateinit var plY: FloatArray
    private lateinit var plZ: FloatArray
    private var plOff = IntArray(PB + 1)
    private var plGrp = Array(PB * 4) { FloatArray(2) }
    private val plGn = IntArray(PB * 4)
    private var plPal = IntArray(PB)
    private var rgN = 0
    private lateinit var rgR: FloatArray
    private lateinit var rgT: FloatArray
    private var rgBack = FloatArray(2)
    private var rgFront = FloatArray(2)

    private fun planetColors(style: Int, acc: Int): IntArray = when (style) {
        0 -> intArrayOf(0xFF0B2A6B.toInt(), 0xFF123F94.toInt(), 0xFF1D63C9.toInt(), 0xFF2E86E8.toInt(), 0xFF3FB07B.toInt(), 0xFF8BCB5A.toInt(), 0xFFEAF6FF.toInt())
        1 -> intArrayOf(0xFF6B3A1F.toInt(), 0xFF9C5A2B.toInt(), 0xFFC98A4A.toInt(), 0xFFE6B877.toInt(), 0xFFF2D6A5.toInt(), 0xFFB8764A.toInt(), 0xFF8A4F2E.toInt())
        2 -> intArrayOf(0xFF1A0505.toInt(), 0xFF4A0E0A.toInt(), 0xFF8C1C0E.toInt(), 0xFFD13A12.toInt(), 0xFFFF6A1A.toInt(), 0xFFFFA63A.toInt(), 0xFFFFE08A.toInt())
        3 -> intArrayOf(0xFF3A5A7A.toInt(), 0xFF5C86AA.toInt(), 0xFF86B4D6.toInt(), 0xFFB5DAF0.toInt(), 0xFFDDF1FA.toInt(), 0xFFFFFFFF.toInt(), 0xFF9CC4E4.toInt())
        4 -> intArrayOf(0xFF5A4520.toInt(), 0xFF8A6A30.toInt(), 0xFFB8924A.toInt(), 0xFFD9B870.toInt(), 0xFFEAD49A.toInt(), 0xFFC9A55C.toInt(), 0xFF9C7A3C.toInt())
        5 -> intArrayOf(0xFF3A140A.toInt(), 0xFF6B2A14.toInt(), 0xFF9C4020.toInt(), 0xFFC05A2A.toInt(), 0xFFD97A3E.toInt(), 0xFFB04A28.toInt(), 0xFF7A3018.toInt())
        6 -> intArrayOf(0xFF2A2C30.toInt(), 0xFF45484E.toInt(), 0xFF61656C.toInt(), 0xFF7E838B.toInt(), 0xFF9AA0A8.toInt(), 0xFFB8BDC4.toInt(), 0xFFD4D8DD.toInt())
        else -> IntArray(PB) { shade(acc, -0.6f + it * 0.22f) }
    }

    private fun buildPlanet() {
        plPal = planetColors(prefs.planetStyle, prefs.colorInt)
        val n = (2600 * density()).toInt().coerceIn(600, 4200)
        plN = n
        val px = FloatArray(n); val py = FloatArray(n); val pz = FloatArray(n); val bk = IntArray(n)
        val ga = (PI * (3.0 - sqrt(5.0))).toFloat()
        for (i in 0 until n) {
            val y = 1f - 2f * (i + 0.5f) / n
            val rad = sqrt(max(0f, 1f - y * y))
            val th = ga * i
            val x = cos(th) * rad; val z = sin(th) * rad
            px[i] = x; py[i] = y; pz[i] = z
            val lat = asin(y); val lon = atan2(z, x)
            val v = sin(lat * 6f + 1.8f * sin(lon * 3f)) * 0.5f + sin(lon * 5f + lat * 2f) * 0.3f + (rnd.nextFloat() - 0.5f) * 0.25f
            bk[i] = ((v + 1f) / 2f * PB).toInt().coerceIn(0, PB - 1)
        }
        val cnt = IntArray(PB)
        for (b in bk) cnt[b]++
        plOff = IntArray(PB + 1)
        for (b in 0 until PB) plOff[b + 1] = plOff[b] + cnt[b]
        val pos = IntArray(PB) { plOff[it] }
        plX = FloatArray(n); plY = FloatArray(n); plZ = FloatArray(n)
        for (i in 0 until n) { val j = pos[bk[i]]++; plX[j] = px[i]; plY[j] = py[i]; plZ[j] = pz[i] }
        plGrp = Array(PB * 4) { FloatArray(cnt[it / 4] * 2 + 2) }
        initStars()
        rgN = (700 * density()).toInt().coerceIn(200, 1400)
        rgR = FloatArray(rgN); rgT = FloatArray(rgN)
        for (i in 0 until rgN) {
            var r: Float
            do { r = 1.35f + rnd.nextFloat() * 0.8f } while ((r * 13f) % 1f < 0.18f)
            rgR[i] = r; rgT[i] = rnd.nextFloat() * 6.2831853f
        }
        rgBack = FloatArray(rgN * 2); rgFront = FloatArray(rgN * 2)
    }

    private fun drawMoon(c: Canvas, x: Float, y: Float, r: Float) {
        fillP.color = 0xFF6F747C.toInt(); c.drawCircle(x, y, r, fillP)
        fillP.color = 0xFFB8BDC6.toInt(); c.drawCircle(x - r * 0.18f, y - r * 0.18f, r * 0.72f, fillP)
        fillP.color = Color.BLACK
    }

    private fun drawPlanet(c: Canvas, w: Float, h: Float, t: Float, cyFrac: Float) {
        val s = min(w, h * 0.95f) * 0.30f
        val cx = w / 2; val cy = h * cyFrac
        drawStarsBg(c, w, h, t, s)
        glow(c, cx, cy, s * 2.0f, plPal[4], 110)
        val ang = t * prefs.speed * 0.35f
        val ca = cos(ang); val sa = sin(ang)
        val sV = sin(prefs.holeTilt); val cV = cos(prefs.holeTilt)
        val roll = prefs.holeRoll * 0.5f; val cR = cos(roll); val sR = sin(roll)
        val ringOn = prefs.ring
        val ma = t * prefs.speed * 0.6f
        val moonFront = sin(ma) > 0f
        val mxp = cx + cos(ma) * s * 2.3f
        val myp = cy + sin(ma) * s * 0.6f - s * 0.25f
        if (prefs.moon && !moonFront) drawMoon(c, mxp, myp, s * 0.17f)
        var rb = 0; var rf = 0
        if (ringOn) {
            for (i in 0 until rgN) {
                val th = rgT[i] + 0.25f / rgR[i] * t * prefs.speed
                val x0 = rgR[i] * cos(th); val z0 = rgR[i] * sin(th)
                val yp = z0 * sV
                val zp = z0 * cV
                val xx = x0 * cR - yp * sR; val yy = x0 * sR + yp * cR
                if (zp < 0f) { rgBack[2 * rb] = cx + xx * s; rgBack[2 * rb + 1] = cy + yy * s; rb++ }
                else { rgFront[2 * rf] = cx + xx * s; rgFront[2 * rf + 1] = cy + yy * s; rf++ }
            }
            pp.color = shade(plPal[5], 0.1f); pp.alpha = 200; pp.strokeWidth = max(1.5f, s * 0.011f)
            c.drawPoints(rgBack, 0, rb * 2, pp)
        }
        fillP.color = plPal[0]
        c.drawCircle(cx, cy, s, fillP)
        fillP.color = Color.BLACK
        for (g in 0 until PB * 4) plGn[g] = 0
        for (b in 0 until PB) for (i in plOff[b] until plOff[b + 1]) {
            val x = plX[i]; val y = plY[i]; val z = plZ[i]
            val x1 = x * ca + z * sa; val z1 = -x * sa + z * ca
            val yp = z1 * sV + y * cV; val zp = z1 * cV - y * sV
            if (zp <= 0f) continue
            val xx = x1 * cR - yp * sR; val yy = x1 * sR + yp * cR
            val li = xx * -0.45f + yy * -0.35f + zp * 0.82f
            val lv = ((li + 0.2f) / 1.2f * 4f).toInt().coerceIn(0, 3)
            val g = b * 4 + lv
            val arr = plGrp[g]; val k = plGn[g]
            arr[2 * k] = cx + xx * s; arr[2 * k + 1] = cy + yy * s
            plGn[g] = k + 1
        }
        pp.strokeWidth = max(2f, s * 0.026f)
        for (g in 0 until PB * 4) {
            if (plGn[g] == 0) continue
            val sh = when (g % 4) { 0 -> -0.6f; 1 -> -0.3f; 2 -> 0f; else -> 0.18f }
            pp.color = shade(plPal[g / 4], sh)
            c.drawPoints(plGrp[g], 0, plGn[g] * 2, pp)
        }
        ringP.color = shade(plPal[4], 0.5f); ringP.alpha = 90; ringP.strokeWidth = s * 0.035f
        c.drawCircle(cx, cy, s * 1.01f, ringP)
        if (ringOn) {
            pp.color = shade(plPal[5], 0.1f); pp.alpha = 220; pp.strokeWidth = max(1.5f, s * 0.011f)
            c.drawPoints(rgFront, 0, rf * 2, pp)
        }
        if (prefs.moon && moonFront) drawMoon(c, mxp, myp, s * 0.17f)
    }

    // ---------- 5. ГАЛАКТИКА ----------
    private val GB = 6
    private lateinit var gaR: FloatArray
    private lateinit var gaT: FloatArray
    private lateinit var gaY: FloatArray
    private var gaOff = IntArray(GB + 1)
    private var gaPts = Array(GB) { FloatArray(2) }

    private fun buildGalaxy() {
        val per = ((3000 * density()).toInt().coerceIn(600, 5400)) / GB
        val n = per * GB
        gaR = FloatArray(n); gaT = FloatArray(n); gaY = FloatArray(n)
        gaOff = IntArray(GB + 1) { it * per }
        gaPts = Array(GB) { FloatArray(per * 2) }
        initStars()
        val v = prefs.galVar.coerceIn(0, 4)
        val arms = when (v) { 1 -> 4; 4 -> 3; else -> 2 }
        val twist = when (v) { 4 -> 9f; 1 -> 5f; else -> 6.5f }
        var k = 0
        for (b in 0 until GB) for (m in 0 until per) {
            val u = (b + rnd.nextFloat()) / GB
            var r = u.pow(1.6f)
            var th: Float
            var y = rnd.nextGaussian().toFloat() * 0.05f * (1f - r * 0.6f)
            when (v) {
                2 -> { th = rnd.nextFloat() * 6.2831853f; y *= 2.5f }
                3 -> { r = 0.55f + 0.45f * u; th = rnd.nextFloat() * 6.2831853f }
                else -> th = rnd.nextInt(arms) * 6.2831853f / arms + r * twist + rnd.nextGaussian().toFloat() * 0.28f * (1.1f - r)
            }
            gaR[k] = r; gaT[k] = th; gaY[k] = y; k++
        }
    }

    private fun drawGalaxy(c: Canvas, w: Float, h: Float, t: Float, cyFrac: Float) {
        val col = prefs.colorInt
        val s = min(w, h * 0.95f) * 0.46f
        val cx = w / 2; val cy = h * cyFrac
        drawStarsBg(c, w, h, t, s)
        glow(c, cx, cy, s * 0.9f, col, 100)
        val tilt = prefs.holeTilt * 2f; val st = sin(tilt); val ct = cos(tilt)
        val roll = prefs.holeRoll; val cr = cos(roll); val sr = sin(roll)
        val ang = t * prefs.speed * 0.25f
        for (b in 0 until GB) {
            var nb = 0
            val arr = gaPts[b]
            for (i in gaOff[b] until gaOff[b + 1]) {
                val th = gaT[i] + ang / (gaR[i] + 0.25f)
                val x0 = gaR[i] * cos(th); val z0 = gaR[i] * sin(th); val y0 = gaY[i]
                val yp = z0 * st + y0 * ct
                arr[2 * nb] = cx + (x0 * cr - yp * sr) * s; arr[2 * nb + 1] = cy + (x0 * sr + yp * cr) * s
                nb++
            }
            val kk = b / (GB - 1f)
            pp.color = shade(col, 0.9f - kk * 1.25f)
            pp.strokeWidth = max(1.5f, s * 0.0055f * (1.2f - kk * 0.4f))
            c.drawPoints(arr, 0, nb * 2, pp)
        }
    }

    // ---------- 6. МАТРИЦА ----------
    private var mxN = 0
    private lateinit var mxX: FloatArray
    private lateinit var mxS: FloatArray
    private lateinit var mxO: FloatArray
    private lateinit var mxD: FloatArray
    private lateinit var mxL: IntArray
    private var mxGlyphs: Array<String> = emptyArray()
    private val mxP = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER }

    private fun buildMatrix() {
        val set = when (prefs.mxVar.coerceIn(0, 4)) {
            0 -> "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎ"
            1 -> "01"
            2 -> "ABCDEFGHJKLMNPRSTUVWXYZ0123456789"
            3 -> "♥♡❤✦✧★"
            else -> "01{}<>/;=+*#"
        }
        mxGlyphs = Array(set.length) { set.substring(it, it + 1) }
        val n = (30 * density()).toInt().coerceIn(10, 60)
        mxN = n
        mxD = FloatArray(n) { 0.45f + rnd.nextFloat() * 0.55f }.sortedArray()
        mxX = (0 until n).map { (it + rnd.nextFloat() * 0.8f) / n }.shuffled(rnd).toFloatArray()
        mxS = FloatArray(n) { 5f + mxD[it] * 9f }
        mxO = FloatArray(n) { rnd.nextFloat() }
        mxL = IntArray(n) { 8 + rnd.nextInt(14) }
    }

    private fun drawMatrix(c: Canvas, w: Float, h: Float, t: Float, cyFrac: Float) {
        val col = prefs.colorInt
        val headC = lerpC(col, Color.WHITE, 0.85f)
        for (i in 0 until mxN) {
            val d = mxD[i]
            val cs = h * 0.03f * d
            mxP.textSize = cs
            val rows = (h / cs).toInt() + 2
            val len = mxL[i]
            val cycle = rows + len
            val headRow = ((t * mxS[i] * prefs.speed + mxO[i] * cycle) % cycle).toInt()
            val x = mxX[i] * w
            for (k in 0 until len) {
                val row = headRow - k
                if (row < 0 || row >= rows) continue
                val a = 1f - k / len.toFloat()
                mxP.color = if (k == 0) headC else col
                mxP.alpha = (a * a * 255f * (0.35f + 0.65f * d)).toInt().coerceIn(0, 255)
                val gi = (i * 31 + row * 17 + (if (k == 0) (t * 12f).toInt() else 0)) % mxGlyphs.size
                c.drawText(mxGlyphs[gi], x, row * cs + cs, mxP)
            }
        }
    }
}
