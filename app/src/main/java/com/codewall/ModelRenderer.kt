package com.codewall
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Рендер 3D-модели на Canvas без OpenGL: проекция вершин, отсечение невидимых граней, сортировка по глубине,
 * освещение по сглаженным нормалям. Стили: 0 объём, 1 каркас, 2 голограмма, 3 X-Ray, 4 стекло, 5 глина.
 * Все буферы создаются при сборке модели, в кадре новых объектов нет.
 */
class ModelRenderer(private val ctx: Context, private val prefs: Prefs) {
    private var prep: Prepared? = null
    private var yaw = 0.6f
    private var pitch = 0.3f
    private var vYaw = 0f
    private var vPitch = 0f
    private var tiltX = 0f
    private var tiltY = 0f
    private var tgtX = 0f
    private var tgtY = 0f
    private var lastT = -1f
    private var rx = FloatArray(0)
    private var ry = FloatArray(0)
    private var pz = FloatArray(0)
    private var px = FloatArray(0)
    private var py = FloatArray(0)
    private var rnx = FloatArray(0)
    private var rny = FloatArray(0)
    private var rnz = FloatArray(0)
    private var keys = LongArray(0)
    private var edgeBuf = FloatArray(0)
    private val scanBuf = FloatArray(160)
    private val path = Path()
    private val rect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val glowP = Paint(Paint.ANTI_ALIAS_FLAG)
    private var glowSh: RadialGradient? = null
    private var glowKey = 0

    /** Собирает модель под текущее качество: упрощает до бюджета треугольников и готовит нормали и рёбра. */
    fun build() {
        val full = ModelStore.resolve(ctx, prefs.modelId) ?: Mesh.builtin(0)
        val q = when (prefs.quality) { 0 -> 0.5f; 2 -> 1.6f; else -> 1f }
        val budget = (2500f * q * PerformanceGuard.scale).toInt().coerceAtLeast(300)
        var m = full.axisFixed(prefs.modelAxis)
        if (m.triCount > budget) m = m.decimated(budget)
        val p = m.prepare()
        prep = p
        val vc = m.vertCount; val tc = m.triCount
        if (rx.size != vc) { rx = FloatArray(vc); ry = FloatArray(vc); pz = FloatArray(vc); px = FloatArray(vc); py = FloatArray(vc) }
        if (rnx.size != tc) { rnx = FloatArray(tc); rny = FloatArray(tc); rnz = FloatArray(tc); keys = LongArray(tc) }
        edgeBuf = FloatArray(p.edges.size * 2)
    }

    /** Движение пальца по экрану: dx, dy в пикселях. Даёт поворот с инерцией. */
    fun swipe(dx: Float, dy: Float) {
        yaw += dx * 0.012f
        pitch = (pitch + dy * 0.012f).coerceIn(-1.3f, 1.3f)
        vYaw = dx * 0.4f
        vPitch = dy * 0.4f
    }

    /** Наклон телефона, -1..1 по каждой оси. */
    fun setTilt(x: Float, y: Float) { tgtX = x; tgtY = y }

    private fun mixc(a: Int, b: Int, k: Float): Int {
        val r = (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt()
        val g = (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt()
        val bl = (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt()
        return Color.rgb(r, g, bl)
    }

    fun draw(c: Canvas, w: Float, h: Float, t: Float, cyFrac: Float) {
        val pr = prep ?: return
        val m = pr.mesh
        val style = prefs.modelStyle.coerceIn(0, 5)
        val dt = if (lastT < 0f) 0f else (t - lastT).coerceIn(0f, 0.1f)
        lastT = t
        yaw += (prefs.modelSpin * 0.8f + vYaw) * dt
        vYaw *= max(0f, 1f - 3f * dt)
        pitch = (pitch + vPitch * dt).coerceIn(-1.3f, 1.3f)
        vPitch *= max(0f, 1f - 3f * dt)
        val kk = min(1f, dt * 8f)
        tiltX += (tgtX - tiltX) * kk
        tiltY += (tgtY - tiltY) * kk
        val yw = yaw + tiltX * 0.8f
        val cyw = cos(yw); val syw = sin(yw)
        val pt = pitch + tiltY * 0.6f
        val cp = cos(pt); val sp = sin(pt)
        val cx = w / 2f; val cy = h * cyFrac
        val s = min(w, h * 0.95f) * 0.30f
        val cam = 3.6f
        val vc = m.vertCount; val tc = m.triCount
        val v = m.v
        for (i in 0 until vc) {
            val x = v[3 * i]; val y = v[3 * i + 1]; val z = v[3 * i + 2]
            val x1 = x * cyw + z * syw
            val z1 = -x * syw + z * cyw
            val y2 = y * cp - z1 * sp
            val z2 = y * sp + z1 * cp
            val f = cam / (cam - z2)
            rx[i] = x1; ry[i] = y2; pz[i] = z2
            px[i] = cx + x1 * s * f; py[i] = cy - y2 * s * f
        }
        // свет в пространстве камеры; сдвигается при наклоне телефона
        val la = prefs.modelLight + tiltX * 0.9f
        var lx = cos(la) * 0.55f; var ly = 0.65f; var lz = sin(la) * 0.4f + 0.75f
        val ll = sqrt(lx * lx + ly * ly + lz * lz)
        lx /= ll; ly /= ll; lz /= ll
        var hx = lx; var hy = ly; var hz = lz + 1f
        val hl = sqrt(hx * hx + hy * hy + hz * hz)
        hx /= hl; hy /= hl; hz /= hl
        val base = prefs.colorInt
        val br = Color.red(base).toFloat(); val bg = Color.green(base).toFloat(); val bb = Color.blue(base).toFloat()
        val shine = prefs.modelShine
        val opaque = style == 0 || style == 5
        val wire = style == 1
        val tn = pr.tn
        val tri = m.t

        var nk = 0
        if (!wire) {
            for (ti in 0 until tc) {
                val a = tri[3 * ti]; val b = tri[3 * ti + 1]; val cc = tri[3 * ti + 2]
                val nx0 = tn[3 * ti]; val ny0 = tn[3 * ti + 1]; val nz0 = tn[3 * ti + 2]
                val nx1 = nx0 * cyw + nz0 * syw
                val nz1 = -nx0 * syw + nz0 * cyw
                val ny2 = ny0 * cp - nz1 * sp
                val nz2 = ny0 * sp + nz1 * cp
                rnx[ti] = nx1; rny[ti] = ny2; rnz[ti] = nz2
                val gx = (rx[a] + rx[b] + rx[cc]) / 3f
                val gy = (ry[a] + ry[b] + ry[cc]) / 3f
                val gz = (pz[a] + pz[b] + pz[cc]) / 3f
                if (opaque && nx1 * (-gx) + ny2 * (-gy) + nz2 * (cam - gz) <= 0f) continue
                keys[nk++] = (((gz + 4f) * 100000f).toLong().coerceAtLeast(0L) shl 24) or ti.toLong()
            }
            java.util.Arrays.sort(keys, 0, nk)
        }

        // подложка: тень под моделью или мягкое свечение
        if (opaque) {
            val sy0 = cy + s * 1.25f
            glowP.shader = null
            glowP.style = Paint.Style.FILL
            for (i in 0..2) {
                glowP.color = Color.argb(34, 0, 0, 0)
                rect.set(cx - s * (0.95f - 0.17f * i), sy0 - s * (0.22f - 0.06f * i), cx + s * (0.95f - 0.17f * i), sy0 + s * (0.22f - 0.06f * i))
                c.drawOval(rect, glowP)
            }
        } else {
            val key = ((cx.toInt() * 31 + cy.toInt()) * 31 + s.toInt()) * 31 + base
            if (glowSh == null || key != glowKey) {
                glowSh = RadialGradient(cx, cy, s * 1.7f, (base and 0x00FFFFFF) or 0x44000000, base and 0x00FFFFFF, Shader.TileMode.CLAMP)
                glowKey = key
            }
            glowP.style = Paint.Style.FILL
            glowP.shader = glowSh
            c.drawCircle(cx, cy, s * 1.7f, glowP)
            glowP.shader = null
        }

        if (!wire) {
            paint.style = if (opaque) Paint.Style.FILL_AND_STROKE else Paint.Style.FILL
            paint.strokeWidth = max(1f, s * 0.006f)
            for (q in 0 until nk) {
                val ti = (keys[q] and 0xFFFFFFL).toInt()
                val a = tri[3 * ti]; val b = tri[3 * ti + 1]; val cc = tri[3 * ti + 2]
                val nx = rnx[ti]; val ny = rny[ti]; val nz = rnz[ti]
                val d = max(0f, nx * lx + ny * ly + nz * lz)
                val nh = max(0f, nx * hx + ny * hy + nz * hz)
                var s16 = nh * nh
                s16 *= s16; s16 *= s16; s16 *= s16
                val fr = 1f - min(1f, abs(nz))
                val fr2 = fr * fr
                val r: Float; val g: Float; val b2: Float; val al: Int
                when (style) {
                    0 -> {
                        val dif = 0.22f + 0.85f * d
                        val spec = s16 * shine * 0.7f * 255f
                        val rim = fr2 * fr * 0.35f
                        r = br * dif + spec + 120f * rim; g = bg * dif + spec + 130f * rim; b2 = bb * dif + spec + 160f * rim; al = 255
                    }
                    5 -> {
                        val dif = 0.35f + 0.65f * d
                        r = (br * 0.45f + 88f) * dif + 30f * fr2; g = (bg * 0.45f + 88f) * dif + 30f * fr2; b2 = (bb * 0.45f + 88f) * dif + 30f * fr2; al = 255
                    }
                    4 -> {
                        val k = 0.35f + 0.5f * s16
                        r = br + (255f - br) * k; g = bg + (255f - bg) * k; b2 = bb + (255f - bb) * k
                        al = (40f + 140f * fr2 + 60f * s16).toInt()
                    }
                    2 -> {
                        val gz = (pz[a] + pz[b] + pz[cc]) / 3f
                        r = br + (255f - br) * 0.2f; g = bg + (255f - bg) * 0.2f; b2 = bb + (255f - bb) * 0.2f
                        al = ((0.10f + 0.14f * (0.5f + 0.5f * sin(t * 6f + gz * 8f)) + 0.25f * fr2) * 255f).toInt()
                    }
                    else -> {
                        r = 255f + (br - 255f) * 0.4f; g = 255f + (bg - 255f) * 0.4f; b2 = 255f + (bb - 255f) * 0.4f
                        al = ((0.04f + 0.45f * fr2) * 255f).toInt()
                    }
                }
                paint.color = Color.argb(al.coerceIn(0, 255), r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b2.toInt().coerceIn(0, 255))
                path.reset()
                path.moveTo(px[a], py[a]); path.lineTo(px[b], py[b]); path.lineTo(px[cc], py[cc]); path.close()
                c.drawPath(path, paint)
            }
        }

        if (style in 1..4) {
            val e = pr.edges
            var ne = 0
            var i = 0
            while (i < e.size) {
                val a = e[i]; val b = e[i + 1]
                edgeBuf[4 * ne] = px[a]; edgeBuf[4 * ne + 1] = py[a]; edgeBuf[4 * ne + 2] = px[b]; edgeBuf[4 * ne + 3] = py[b]
                ne++; i += 2
            }
            edgeP.strokeWidth = max(1f, s * (if (style == 1) 0.008f else 0.005f))
            val flicker = if (style == 2 && sin(t * 47f) * sin(t * 13f) > 0.97f) 0.5f else 1f
            val ec = when (style) { 1 -> base; 2 -> mixc(base, Color.WHITE, 0.3f); 3 -> mixc(base, Color.WHITE, 0.5f); else -> Color.WHITE }
            val ea = when (style) { 1 -> 230; 2 -> 200; 3 -> 110; else -> 90 }
            edgeP.color = (ec and 0x00FFFFFF) or ((ea * flicker).toInt() shl 24)
            c.drawLines(edgeBuf, 0, ne * 4, edgeP)
        }

        if (style == 2) {
            // голограмма: строки развёртки и платформа
            var n = 0
            var yy = cy - s * 1.3f
            while (yy < cy + s * 1.3f && n + 4 <= scanBuf.size) {
                scanBuf[n] = cx - s * 1.1f; scanBuf[n + 1] = yy; scanBuf[n + 2] = cx + s * 1.1f; scanBuf[n + 3] = yy
                n += 4; yy += s * 0.07f
            }
            edgeP.strokeWidth = 1f
            edgeP.color = (base and 0x00FFFFFF) or 0x30000000
            c.drawLines(scanBuf, 0, n, edgeP)
            edgeP.strokeWidth = max(1.5f, s * 0.01f)
            edgeP.color = (base and 0x00FFFFFF) or 0xA0000000.toInt()
            rect.set(cx - s * 1.0f, cy + s * 1.15f, cx + s * 1.0f, cy + s * 1.45f)
            c.drawOval(rect, edgeP)
        }
    }
}
