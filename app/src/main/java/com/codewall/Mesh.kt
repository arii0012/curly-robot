package com.codewall
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class FloatList {
    var a = FloatArray(1024)
    var n = 0
    fun add(v: Float) { if (n == a.size) a = a.copyOf(n * 2); a[n++] = v }
    fun add3(x: Float, y: Float, z: Float) { add(x); add(y); add(z) }
    fun toArray(): FloatArray = a.copyOf(n)
}

class IntList {
    var a = IntArray(1024)
    var n = 0
    fun add(v: Int) { if (n == a.size) a = a.copyOf(n * 2); a[n++] = v }
    fun add3(x: Int, y: Int, z: Int) { add(x); add(y); add(z) }
    fun toArray(): IntArray = a.copyOf(n)
}

/** Подготовленная для рисования модель: сглаженные нормали треугольников и список уникальных рёбер. */
class Prepared(val mesh: Mesh, val tn: FloatArray, val edges: IntArray)

/** Треугольная сетка: v — x,y,z вершин подряд, t — тройки индексов вершин. */
class Mesh(val v: FloatArray, val t: IntArray) {
    val vertCount: Int get() = v.size / 3
    val triCount: Int get() = t.size / 3

    /** Центр в начало координат, радиус 1, слияние совпадающих вершин, удаление вырожденных граней, наружу смотрящие грани. */
    fun cleaned(): Mesh {
        val vc = vertCount
        if (vc < 3 || t.size < 3) throw IllegalArgumentException("В модели нет граней")
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (i in 0 until vc) {
            val x = v[3 * i]; val y = v[3 * i + 1]; val z = v[3 * i + 2]
            if (!x.isFinite() || !y.isFinite() || !z.isFinite()) continue
            minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y); minZ = min(minZ, z); maxZ = max(maxZ, z)
        }
        if (minX == Float.MAX_VALUE) throw IllegalArgumentException("В модели нет корректных вершин")
        val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2; val cz = (minZ + maxZ) / 2
        var r = 0f
        for (i in 0 until vc) {
            val x = v[3 * i]; val y = v[3 * i + 1]; val z = v[3 * i + 2]
            if (!x.isFinite() || !y.isFinite() || !z.isFinite()) continue
            val dx = x - cx; val dy = y - cy; val dz = z - cz
            r = max(r, sqrt(dx * dx + dy * dy + dz * dz))
        }
        if (r < 1e-9f) throw IllegalArgumentException("Модель пустая")
        val inv = 1f / r
        val map = HashMap<Long, Int>(vc * 2)
        val nv = FloatList()
        val remap = IntArray(vc)
        for (i in 0 until vc) {
            var x = v[3 * i]; var y = v[3 * i + 1]; var z = v[3 * i + 2]
            if (!x.isFinite()) x = cx
            if (!y.isFinite()) y = cy
            if (!z.isFinite()) z = cz
            val nx = (x - cx) * inv; val ny = (y - cy) * inv; val nz = (z - cz) * inv
            val qx = (Math.round(nx * 4096f) + 8192).coerceIn(0, 16383)
            val qy = (Math.round(ny * 4096f) + 8192).coerceIn(0, 16383)
            val qz = (Math.round(nz * 4096f) + 8192).coerceIn(0, 16383)
            val key = (qx.toLong() shl 28) or (qy.toLong() shl 14) or qz.toLong()
            val found = map[key]
            val idx = if (found != null) found else { val ni = nv.n / 3; nv.add3(nx, ny, nz); map[key] = ni; ni }
            remap[i] = idx
        }
        val nt = IntList()
        var k = 0
        while (k + 2 < t.size) {
            val a0 = t[k]; val b0 = t[k + 1]; val c0 = t[k + 2]
            k += 3
            if (a0 < 0 || b0 < 0 || c0 < 0 || a0 >= vc || b0 >= vc || c0 >= vc) continue
            val a = remap[a0]; val b = remap[b0]; val c = remap[c0]
            if (a == b || b == c || a == c) continue
            nt.add3(a, b, c)
        }
        if (nt.n == 0) throw IllegalArgumentException("В модели нет граней")
        return Mesh(nv.toArray(), nt.toArray()).orientedOutward()
    }

    /** Замкнутая ли сетка: каждое ребро принадлежит ровно двум треугольникам. */
    fun isClosed(): Boolean {
        val keys = LongArray(t.size)
        var n = 0
        for (k in 0 until triCount) {
            val a = t[3 * k]; val b = t[3 * k + 1]; val c = t[3 * k + 2]
            keys[n++] = edgeKey(a, b); keys[n++] = edgeKey(b, c); keys[n++] = edgeKey(c, a)
        }
        java.util.Arrays.sort(keys, 0, n)
        var i = 0
        while (i < n) {
            var j = i
            while (j < n && keys[j] == keys[i]) j++
            if (j - i != 2) return false
            i = j
        }
        return true
    }

    private fun edgeKey(a: Int, b: Int): Long = (min(a, b).toLong() shl 32) or max(a, b).toLong()

    /** Если сетка замкнута и объём отрицательный — грани вывернуты, разворачиваем. */
    fun orientedOutward(): Mesh {
        if (!isClosed()) return this
        var vol = 0.0
        for (k in 0 until triCount) {
            val a = t[3 * k] * 3; val b = t[3 * k + 1] * 3; val c = t[3 * k + 2] * 3
            val ax = v[a].toDouble(); val ay = v[a + 1].toDouble(); val az = v[a + 2].toDouble()
            val bx = v[b].toDouble(); val by = v[b + 1].toDouble(); val bz = v[b + 2].toDouble()
            val cx = v[c].toDouble(); val cy = v[c + 1].toDouble(); val cz = v[c + 2].toDouble()
            vol += ax * (by * cz - bz * cy) - ay * (bx * cz - bz * cx) + az * (bx * cy - by * cx)
        }
        if (vol >= 0.0) return this
        val nt = t.copyOf()
        for (k in 0 until triCount) { val s = nt[3 * k + 1]; nt[3 * k + 1] = nt[3 * k + 2]; nt[3 * k + 2] = s }
        return Mesh(v, nt)
    }

    /** Упрощение сетки кластеризацией вершин по решётке: подбираем размер ячейки, пока треугольников не станет достаточно мало. */
    fun decimated(maxTris: Int): Mesh {
        if (triCount <= maxTris) return this
        var n = 128
        var best: Mesh = this
        while (n >= 6) {
            val r = cluster(n)
            best = r
            if (r.triCount <= maxTris) break
            n = (n * 0.8f).toInt()
        }
        return best
    }

    private fun cluster(n: Int): Mesh {
        val cell = 2f / n
        val vc = vertCount
        val map = HashMap<Long, Int>(vc)
        val sums = FloatList()
        val cnt = IntList()
        val remap = IntArray(vc)
        for (i in 0 until vc) {
            val ix = ((v[3 * i] + 1f) / cell).toInt().coerceIn(0, n - 1)
            val iy = ((v[3 * i + 1] + 1f) / cell).toInt().coerceIn(0, n - 1)
            val iz = ((v[3 * i + 2] + 1f) / cell).toInt().coerceIn(0, n - 1)
            val key = (ix.toLong() shl 40) or (iy.toLong() shl 20) or iz.toLong()
            val found = map[key]
            val idx: Int
            if (found != null) idx = found
            else { idx = cnt.n; map[key] = idx; sums.add3(0f, 0f, 0f); cnt.add(0) }
            sums.a[3 * idx] += v[3 * i]; sums.a[3 * idx + 1] += v[3 * i + 1]; sums.a[3 * idx + 2] += v[3 * i + 2]
            cnt.a[idx]++
            remap[i] = idx
        }
        val nv = FloatArray(cnt.n * 3)
        for (i in 0 until cnt.n) { val c = cnt.a[i].toFloat(); nv[3 * i] = sums.a[3 * i] / c; nv[3 * i + 1] = sums.a[3 * i + 1] / c; nv[3 * i + 2] = sums.a[3 * i + 2] / c }
        val nt = IntList()
        for (k in 0 until triCount) {
            val a = remap[t[3 * k]]; val b = remap[t[3 * k + 1]]; val c = remap[t[3 * k + 2]]
            if (a == b || b == c || a == c) continue
            nt.add3(a, b, c)
        }
        if (nt.n == 0) return this
        return Mesh(nv, nt.toArray())
    }

    /** Смена осей: 1 — Z вверх → Y вверх (CAD/STL), 2 — вверх ногами. Повороты собственные, ориентация граней сохраняется. */
    fun axisFixed(mode: Int): Mesh {
        if (mode == 0) return this
        val nv = FloatArray(v.size)
        for (i in 0 until vertCount) {
            val x = v[3 * i]; val y = v[3 * i + 1]; val z = v[3 * i + 2]
            if (mode == 1) { nv[3 * i] = x; nv[3 * i + 1] = z; nv[3 * i + 2] = -y }
            else { nv[3 * i] = x; nv[3 * i + 1] = -y; nv[3 * i + 2] = -z }
        }
        return Mesh(nv, t)
    }

    /** Нормали граней со сглаживанием по углу (до ≈37°) и список уникальных рёбер для каркаса. */
    fun prepare(): Prepared {
        val tc = triCount; val vc = vertCount
        val fn = FloatArray(3 * tc)
        for (k in 0 until tc) {
            val a = t[3 * k] * 3; val b = t[3 * k + 1] * 3; val c = t[3 * k + 2] * 3
            val ux = v[b] - v[a]; val uy = v[b + 1] - v[a + 1]; val uz = v[b + 2] - v[a + 2]
            val wx = v[c] - v[a]; val wy = v[c + 1] - v[a + 1]; val wz = v[c + 2] - v[a + 2]
            var nx = uy * wz - uz * wy; var ny = uz * wx - ux * wz; var nz = ux * wy - uy * wx
            val l = sqrt(nx * nx + ny * ny + nz * nz)
            if (l < 1e-12f) { nx = 0f; ny = 1f; nz = 0f } else { nx /= l; ny /= l; nz /= l }
            fn[3 * k] = nx; fn[3 * k + 1] = ny; fn[3 * k + 2] = nz
        }
        val start = IntArray(vc + 1)
        for (i in t.indices) start[t[i] + 1]++
        for (i in 0 until vc) start[i + 1] += start[i]
        val fill = start.copyOf(vc)
        val list = IntArray(t.size)
        for (k in 0 until tc) for (c in 0..2) { val vi = t[3 * k + c]; list[fill[vi]++] = k }
        val tn = FloatArray(3 * tc)
        val cosC = 0.80f
        for (k in 0 until tc) {
            val kx = fn[3 * k]; val ky = fn[3 * k + 1]; val kz = fn[3 * k + 2]
            var sx = 0f; var sy = 0f; var sz = 0f
            for (c in 0..2) {
                val vi = t[3 * k + c]
                for (q in start[vi] until start[vi + 1]) {
                    val f = list[q]
                    if (fn[3 * f] * kx + fn[3 * f + 1] * ky + fn[3 * f + 2] * kz >= cosC) { sx += fn[3 * f]; sy += fn[3 * f + 1]; sz += fn[3 * f + 2] }
                }
            }
            val l = sqrt(sx * sx + sy * sy + sz * sz)
            if (l < 1e-9f) { sx = kx; sy = ky; sz = kz } else { sx /= l; sy /= l; sz /= l }
            tn[3 * k] = sx; tn[3 * k + 1] = sy; tn[3 * k + 2] = sz
        }
        val keys = LongArray(t.size)
        var n = 0
        for (k in 0 until tc) {
            val a = t[3 * k]; val b = t[3 * k + 1]; val c = t[3 * k + 2]
            keys[n++] = edgeKey(a, b); keys[n++] = edgeKey(b, c); keys[n++] = edgeKey(c, a)
        }
        java.util.Arrays.sort(keys, 0, n)
        val e = IntList()
        var i = 0
        while (i < n) {
            val cur = keys[i]
            e.add((cur shr 32).toInt()); e.add((cur and 0xFFFFFFFFL).toInt())
            while (i < n && keys[i] == cur) i++
        }
        return Prepared(this, tn, e.toArray())
    }

    companion object {
        val BUILTIN_NAMES = listOf("Кристалл", "Бублик", "Икосаэдр", "Сердце")
        private const val TAU = 6.2831855f

        fun builtin(i: Int): Mesh = when (i) { 0 -> crystal(); 1 -> torus(); 2 -> icosahedron(); else -> heart() }

        private fun crystal(): Mesh {
            val n = 8
            val v = FloatList(); val t = IntList()
            v.add3(0f, 1.25f, 0f); v.add3(0f, -1.25f, 0f)
            for (i in 0 until n) { val a = TAU * i / n; v.add3(0.72f * cos(a), 0.35f, 0.72f * sin(a)) }
            for (i in 0 until n) { val a = TAU * (i + 0.5f) / n; v.add3(0.72f * cos(a), -0.35f, 0.72f * sin(a)) }
            for (i in 0 until n) {
                val u0 = 2 + i; val u1 = 2 + (i + 1) % n
                val l0 = 2 + n + i; val lm = 2 + n + (i + n - 1) % n; val l1 = 2 + n + (i + 1) % n
                t.add3(0, u1, u0)
                t.add3(u0, u1, l0)
                t.add3(lm, u0, l0)
                t.add3(1, l0, l1)
            }
            return Mesh(v.toArray(), t.toArray()).cleaned()
        }

        private fun torus(): Mesh {
            val nn = 28; val mm = 14
            val big = 0.7f; val small = 0.3f
            val v = FloatList(); val t = IntList()
            for (i in 0 until nn) {
                val a = TAU * i / nn
                for (j in 0 until mm) {
                    val b = TAU * j / mm
                    v.add3((big + small * cos(b)) * cos(a), small * sin(b), (big + small * cos(b)) * sin(a))
                }
            }
            for (i in 0 until nn) for (j in 0 until mm) {
                val a = i * mm + j; val b = ((i + 1) % nn) * mm + j
                val c = ((i + 1) % nn) * mm + (j + 1) % mm; val d = i * mm + (j + 1) % mm
                t.add3(a, b, c); t.add3(a, c, d)
            }
            return Mesh(v.toArray(), t.toArray()).cleaned()
        }

        private fun icosahedron(): Mesh {
            val p = (1f + sqrt(5f)) / 2f
            val vs = floatArrayOf(-1f, p, 0f, 1f, p, 0f, -1f, -p, 0f, 1f, -p, 0f, 0f, -1f, p, 0f, 1f, p, 0f, -1f, -p, 0f, 1f, -p,
                p, 0f, -1f, p, 0f, 1f, -p, 0f, -1f, -p, 0f, 1f)
            val ts = intArrayOf(0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11, 1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8,
                3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9, 4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1)
            return Mesh(vs, ts).cleaned()
        }

        /** «Подушка»-сердце: контур сердца, который надувается кольцами к двум полюсам. */
        private fun heart(): Mesh {
            val nn = 48; val kk = 10
            val ox = FloatArray(nn); val oy = FloatArray(nn)
            for (j in 0 until nn) {
                val tt = TAU * j / nn
                val s = sin(tt)
                ox[j] = 16f * s * s * s / 17f
                oy[j] = (13f * cos(tt) - 5f * cos(2f * tt) - 2f * cos(3f * tt) - cos(4f * tt)) / 17f + 0.15f
            }
            val v = FloatList(); val t = IntList()
            for (k in 1 until kk) {
                val phi = -1.5707964f + 3.1415927f * k / kk
                val sc = cos(phi); val z = sin(phi) * 0.5f
                for (j in 0 until nn) v.add3(ox[j] * sc, oy[j] * sc, z)
            }
            val bot = v.n / 3; v.add3(0f, 0f, -0.5f)
            val top = v.n / 3; v.add3(0f, 0f, 0.5f)
            fun ring(k: Int, j: Int) = (k - 1) * nn + (j % nn)
            for (k in 1 until kk - 1) for (j in 0 until nn) {
                val a = ring(k, j); val b = ring(k, j + 1); val c = ring(k + 1, j + 1); val d = ring(k + 1, j)
                t.add3(a, b, c); t.add3(a, c, d)
            }
            for (j in 0 until nn) {
                t.add3(bot, ring(1, j + 1), ring(1, j))
                t.add3(top, ring(kk - 1, j), ring(kk - 1, j + 1))
            }
            return Mesh(v.toArray(), t.toArray()).cleaned()
        }
    }
}
