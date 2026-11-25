package com.codewall
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.StringTokenizer
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Понятная пользователю ошибка импорта. */
class ModelException(msg: String) : Exception(msg)

const val MAX_TRIS_INPUT = 700000

object ObjParser {
    fun parse(data: ByteArray): Mesh {
        val v = FloatList(); val t = IntList()
        val br = BufferedReader(InputStreamReader(ByteArrayInputStream(data), Charsets.UTF_8))
        val poly = IntArray(64)
        var line = br.readLine()
        while (line != null) {
            val s = line.trim()
            if (s.startsWith("v ")) {
                val st = StringTokenizer(s.substring(2))
                if (st.countTokens() >= 3) {
                    val x = st.nextToken().toFloatOrNull() ?: 0f
                    val y = st.nextToken().toFloatOrNull() ?: 0f
                    val z = st.nextToken().toFloatOrNull() ?: 0f
                    v.add3(x, y, z)
                }
            } else if (s.startsWith("f ")) {
                val st = StringTokenizer(s.substring(2))
                val vcNow = v.n / 3
                var n = 0
                while (st.hasMoreTokens() && n < 64) {
                    val tok = st.nextToken()
                    val slash = tok.indexOf('/')
                    val raw = (if (slash >= 0) tok.substring(0, slash) else tok).toIntOrNull() ?: continue
                    poly[n++] = if (raw < 0) vcNow + raw else raw - 1
                }
                for (i in 1 until n - 1) t.add3(poly[0], poly[i], poly[i + 1])
                if (t.n / 3 > MAX_TRIS_INPUT) throw ModelException("Слишком тяжёлая модель (больше 700 тысяч треугольников). Упрости её в Blender модификатором Decimate.")
            }
            line = br.readLine()
        }
        if (v.n < 9 || t.n == 0) throw ModelException("В .obj не нашлось вершин и граней.")
        return Mesh(v.toArray(), t.toArray())
    }
}

object StlParser {
    fun parse(data: ByteArray): Mesh {
        if (data.size > 84) {
            val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val count = bb.getInt(80)
            if (count > 0 && 84L + 50L * count == data.size.toLong()) return binary(bb, count)
        }
        return ascii(String(data, Charsets.UTF_8))
    }

    private fun binary(bb: ByteBuffer, count: Int): Mesh {
        if (count > MAX_TRIS_INPUT) throw ModelException("Слишком тяжёлая модель (больше 700 тысяч треугольников).")
        val v = FloatArray(count * 9)
        val t = IntArray(count * 3)
        var p = 84
        for (i in 0 until count) {
            p += 12
            for (k in 0 until 9) { v[i * 9 + k] = bb.getFloat(p); p += 4 }
            p += 2
            t[3 * i] = 3 * i; t[3 * i + 1] = 3 * i + 1; t[3 * i + 2] = 3 * i + 2
        }
        return Mesh(v, t)
    }

    private fun ascii(text: String): Mesh {
        val v = FloatList()
        for (raw in text.lineSequence()) {
            val s = raw.trim()
            if (s.startsWith("vertex", ignoreCase = true)) {
                val st = StringTokenizer(s)
                st.nextToken()
                if (st.countTokens() >= 3) {
                    v.add3(st.nextToken().toFloatOrNull() ?: 0f, st.nextToken().toFloatOrNull() ?: 0f, st.nextToken().toFloatOrNull() ?: 0f)
                }
            }
        }
        if (v.n == 0 || v.n % 9 != 0) throw ModelException("В .stl не нашлось граней.")
        val n = v.n / 3
        if (n / 3 > MAX_TRIS_INPUT) throw ModelException("Слишком тяжёлая модель (больше 700 тысяч треугольников).")
        return Mesh(v.toArray(), IntArray(n) { it })
    }
}

/** Бинарный glTF (.glb): позиции и индексы всех мешей с учётом преобразований узлов. */
object GlbParser {
    private class Ctx(val bin: ByteBuffer, val accessors: JSONArray, val views: JSONArray)

    private fun mul(a: FloatArray, b: FloatArray): FloatArray {
        val o = FloatArray(16)
        for (c in 0..3) for (r in 0..3) {
            var s = 0f
            for (k in 0..3) s += a[k * 4 + r] * b[c * 4 + k]
            o[c * 4 + r] = s
        }
        return o
    }

    private fun local(n: JSONObject): FloatArray {
        val m = n.optJSONArray("matrix")
        if (m != null && m.length() == 16) return FloatArray(16) { m.getDouble(it).toFloat() }
        val tr = n.optJSONArray("translation"); val ro = n.optJSONArray("rotation"); val sc = n.optJSONArray("scale")
        val tx = tr?.optDouble(0, 0.0)?.toFloat() ?: 0f; val ty = tr?.optDouble(1, 0.0)?.toFloat() ?: 0f; val tz = tr?.optDouble(2, 0.0)?.toFloat() ?: 0f
        val qx = ro?.optDouble(0, 0.0)?.toFloat() ?: 0f; val qy = ro?.optDouble(1, 0.0)?.toFloat() ?: 0f
        val qz = ro?.optDouble(2, 0.0)?.toFloat() ?: 0f; val qw = ro?.optDouble(3, 1.0)?.toFloat() ?: 1f
        val sx = sc?.optDouble(0, 1.0)?.toFloat() ?: 1f; val sy = sc?.optDouble(1, 1.0)?.toFloat() ?: 1f; val sz = sc?.optDouble(2, 1.0)?.toFloat() ?: 1f
        val xx = qx * qx; val yy = qy * qy; val zz = qz * qz
        val xy = qx * qy; val xz = qx * qz; val yz = qy * qz
        val wx = qw * qx; val wy = qw * qy; val wz = qw * qz
        val r00 = 1 - 2 * (yy + zz); val r01 = 2 * (xy - wz); val r02 = 2 * (xz + wy)
        val r10 = 2 * (xy + wz); val r11 = 1 - 2 * (xx + zz); val r12 = 2 * (yz - wx)
        val r20 = 2 * (xz - wy); val r21 = 2 * (yz + wx); val r22 = 1 - 2 * (xx + yy)
        return floatArrayOf(r00 * sx, r10 * sx, r20 * sx, 0f, r01 * sy, r11 * sy, r21 * sy, 0f, r02 * sz, r12 * sz, r22 * sz, 0f, tx, ty, tz, 1f)
    }

    private fun viewInfo(cx: Ctx, acc: JSONObject, elem: Int): IntArray {
        val bv = acc.optInt("bufferView", -1)
        if (bv < 0) throw ModelException("В .glb нет данных буфера.")
        val view = cx.views.getJSONObject(bv)
        val off = view.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
        var stride = view.optInt("byteStride", 0)
        if (stride == 0) stride = elem
        return intArrayOf(off, stride)
    }

    private fun readPositions(cx: Ctx, acc: JSONObject): FloatArray {
        if (acc.optString("type") != "VEC3" || acc.optInt("componentType") != 5126)
            throw ModelException("Вершины в .glb сжаты или квантованы. Экспортируй модель без сжатия.")
        val count = acc.getInt("count")
        val vi = viewInfo(cx, acc, 12)
        val out = FloatArray(count * 3)
        for (i in 0 until count) {
            val p = vi[0] + i * vi[1]
            out[3 * i] = cx.bin.getFloat(p); out[3 * i + 1] = cx.bin.getFloat(p + 4); out[3 * i + 2] = cx.bin.getFloat(p + 8)
        }
        return out
    }

    private fun readIndices(cx: Ctx, acc: JSONObject): IntArray {
        val ct = acc.getInt("componentType")
        val size = when (ct) { 5121 -> 1; 5123 -> 2; 5125 -> 4; else -> throw ModelException("Неподдерживаемый тип индексов в .glb.") }
        val count = acc.getInt("count")
        val vi = viewInfo(cx, acc, size)
        val out = IntArray(count)
        for (i in 0 until count) {
            val p = vi[0] + i * vi[1]
            out[i] = when (ct) { 5121 -> cx.bin.get(p).toInt() and 0xFF; 5123 -> cx.bin.getShort(p).toInt() and 0xFFFF; else -> cx.bin.getInt(p) }
        }
        return out
    }

    fun parse(data: ByteArray): Mesh {
        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        if (data.size < 20 || bb.getInt(0) != 0x46546C67) throw ModelException("Это не файл .glb.")
        val total = minOf(bb.getInt(8), data.size)
        var off = 12
        var json: String? = null
        var binOff = -1; var binLen = 0
        while (off + 8 <= total) {
            val len = bb.getInt(off); val type = bb.getInt(off + 4); val start = off + 8
            if (len < 0 || start + len > data.size) break
            if (type == 0x4E4F534A) json = String(data, start, len, Charsets.UTF_8)
            else if (type == 0x004E4942 && binOff < 0) { binOff = start; binLen = len }
            off = start + ((len + 3) and 3.inv())
        }
        if (json == null) throw ModelException("В .glb нет описания сцены.")
        val root = JSONObject(json)
        val req = root.optJSONArray("extensionsRequired")
        if (req != null) for (i in 0 until req.length()) {
            val e = req.getString(i)
            if (e.contains("draco", true) || e.contains("meshopt", true)) throw ModelException("Модель сжата (Draco/Meshopt). Экспортируй .glb из Blender без сжатия геометрии.")
        }
        if (binOff < 0) throw ModelException("В .glb нет бинарных данных (модель ссылается на внешние файлы).")
        val bin = ByteBuffer.wrap(data, binOff, binLen).slice().order(ByteOrder.LITTLE_ENDIAN)
        val accessors = root.optJSONArray("accessors") ?: throw ModelException("В .glb нет данных о вершинах.")
        val views = root.optJSONArray("bufferViews") ?: throw ModelException("В .glb нет данных буфера.")
        val meshes = root.optJSONArray("meshes") ?: throw ModelException("В .glb нет мешей.")
        val cx = Ctx(bin, accessors, views)
        val nodes = root.optJSONArray("nodes")
        val outV = FloatList(); val outT = IntList()

        fun addMesh(mi: Int, m: FloatArray) {
            val prims = meshes.getJSONObject(mi).optJSONArray("primitives") ?: return
            for (pidx in 0 until prims.length()) {
                val pr = prims.getJSONObject(pidx)
                if (pr.optInt("mode", 4) != 4) continue
                val pa = pr.getJSONObject("attributes").optInt("POSITION", -1)
                if (pa < 0) continue
                val pos = readPositions(cx, accessors.getJSONObject(pa))
                val base = outV.n / 3
                for (i in 0 until pos.size / 3) {
                    val x = pos[3 * i]; val y = pos[3 * i + 1]; val z = pos[3 * i + 2]
                    outV.add3(m[0] * x + m[4] * y + m[8] * z + m[12], m[1] * x + m[5] * y + m[9] * z + m[13], m[2] * x + m[6] * y + m[10] * z + m[14])
                }
                val ia = pr.optInt("indices", -1)
                if (ia >= 0) {
                    val idx = readIndices(cx, accessors.getJSONObject(ia))
                    var k = 0
                    while (k + 2 < idx.size) { outT.add3(base + idx[k], base + idx[k + 1], base + idx[k + 2]); k += 3 }
                } else {
                    var k = 0
                    while (k + 2 < pos.size / 3) { outT.add3(base + k, base + k + 1, base + k + 2); k += 3 }
                }
                if (outT.n / 3 > MAX_TRIS_INPUT) throw ModelException("Слишком тяжёлая модель (больше 700 тысяч треугольников).")
            }
        }

        val ident = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        fun walk(ni: Int, parent: FloatArray, depth: Int) {
            if (depth > 64 || nodes == null) return
            val node = nodes.getJSONObject(ni)
            val m = mul(parent, local(node))
            if (node.has("mesh")) addMesh(node.getInt("mesh"), m)
            val ch = node.optJSONArray("children")
            if (ch != null) for (i in 0 until ch.length()) walk(ch.getInt(i), m, depth + 1)
        }

        if (nodes == null) {
            for (i in 0 until meshes.length()) addMesh(i, ident)
        } else {
            val scenes = root.optJSONArray("scenes")
            val roots = scenes?.optJSONObject(root.optInt("scene", 0))?.optJSONArray("nodes")
            if (roots != null) {
                for (i in 0 until roots.length()) walk(roots.getInt(i), ident, 0)
            } else {
                val hasParent = BooleanArray(nodes.length())
                for (i in 0 until nodes.length()) {
                    val ch = nodes.getJSONObject(i).optJSONArray("children")
                    if (ch != null) for (j in 0 until ch.length()) hasParent[ch.getInt(j)] = true
                }
                for (i in 0 until nodes.length()) if (!hasParent[i]) walk(i, ident, 0)
            }
        }
        if (outT.n == 0) throw ModelException("В .glb нет треугольных граней.")
        return Mesh(outV.toArray(), outT.toArray())
    }
}

/** Векторный SVG → силуэт-фигура (маска PoiPaper). Поддержка: path (все команды, включая дуги), rect, circle, ellipse, polygon, polyline, line, transform, fill/stroke. */
object SvgParser {
    private class Scan(val s: String) {
        var i = 0
        fun skipSep() { while (i < s.length && (s[i] == ' ' || s[i] == ',' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++ }
        fun num(): Float {
            skipSep()
            val st = i
            if (i < s.length && (s[i] == '-' || s[i] == '+')) i++
            var dot = false
            while (i < s.length) {
                val c = s[i]
                if (c.isDigit()) i++
                else if (c == '.' && !dot) { dot = true; i++ }
                else if ((c == 'e' || c == 'E') && i + 1 < s.length && (s[i + 1].isDigit() || s[i + 1] == '-' || s[i + 1] == '+')) i += 2
                else break
            }
            return s.substring(st, i).toFloatOrNull() ?: throw ModelException("Ошибка в данных SVG-пути.")
        }
        fun flag(): Boolean { skipSep(); if (i >= s.length) throw ModelException("Ошибка в данных SVG-пути."); val c = s[i]; i++; return c == '1' }
        fun more(): Boolean { skipSep(); return i < s.length }
    }

    private fun arc(p: Path, x0: Float, y0: Float, rxIn: Float, ryIn: Float, rotDeg: Float, large: Boolean, sweep: Boolean, x: Float, y: Float) {
        var rx = abs(rxIn).toDouble(); var ry = abs(ryIn).toDouble()
        if (rx == 0.0 || ry == 0.0 || (x0 == x && y0 == y)) { p.lineTo(x, y); return }
        val phi = Math.toRadians(rotDeg.toDouble()); val cp = cos(phi); val sp = sin(phi)
        val dx2 = (x0 - x) / 2.0; val dy2 = (y0 - y) / 2.0
        val x1p = cp * dx2 + sp * dy2; val y1p = -sp * dx2 + cp * dy2
        val lam = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry)
        if (lam > 1.0) { val s = sqrt(lam); rx *= s; ry *= s }
        val rx2 = rx * rx; val ry2 = ry * ry
        val num = rx2 * ry2 - rx2 * y1p * y1p - ry2 * x1p * x1p
        val den = rx2 * y1p * y1p + ry2 * x1p * x1p
        var coef = sqrt(max(0.0, num / den))
        if (large == sweep) coef = -coef
        val cxp = coef * rx * y1p / ry; val cyp = -coef * ry * x1p / rx
        val cxc = cp * cxp - sp * cyp + (x0 + x) / 2.0; val cyc = sp * cxp + cp * cyp + (y0 + y) / 2.0
        val ux = (x1p - cxp) / rx; val uy = (y1p - cyp) / ry
        val vx = (-x1p - cxp) / rx; val vy = (-y1p - cyp) / ry
        val th1 = atan2(uy, ux)
        var dth = atan2(ux * vy - uy * vx, ux * vx + uy * vy)
        if (!sweep && dth > 0) dth -= 2 * Math.PI else if (sweep && dth < 0) dth += 2 * Math.PI
        val segs = ceil(abs(dth) / (Math.PI / 2)).toInt().coerceAtLeast(1)
        val delta = dth / segs
        val k = 4.0 / 3.0 * tan(delta / 4)
        var a = th1
        for (i in 0 until segs) {
            val c1 = cos(a); val s1 = sin(a)
            val a2 = a + delta
            val c2 = cos(a2); val s2 = sin(a2)
            val p1x = c1 - k * s1; val p1y = s1 + k * c1
            val p2x = c2 + k * s2; val p2y = s2 - k * c2
            p.cubicTo(
                (cxc + cp * rx * p1x - sp * ry * p1y).toFloat(), (cyc + sp * rx * p1x + cp * ry * p1y).toFloat(),
                (cxc + cp * rx * p2x - sp * ry * p2y).toFloat(), (cyc + sp * rx * p2x + cp * ry * p2y).toFloat(),
                (cxc + cp * rx * c2 - sp * ry * s2).toFloat(), (cyc + sp * rx * c2 + cp * ry * s2).toFloat())
            a = a2
        }
    }

    fun pathFromData(d: String): Path {
        val p = Path()
        val sc = Scan(d)
        var cmd = ' '
        var cx = 0f; var cy = 0f; var sx = 0f; var sy = 0f
        var lcx = 0f; var lcy = 0f
        var last = ' '
        var guard = 0
        while (sc.more()) {
            if (++guard > 400000) throw ModelException("SVG-путь слишком сложный.")
            val ch = d[sc.i]
            if (ch.isLetter()) {
                cmd = ch; sc.i++
                if (cmd == 'z' || cmd == 'Z') { p.close(); cx = sx; cy = sy; last = 'Z'; continue }
            } else if (cmd == ' ' || cmd == 'z' || cmd == 'Z') throw ModelException("Ошибка в данных SVG-пути.")
            val rel = cmd.isLowerCase()
            when (cmd.uppercaseChar()) {
                'M' -> {
                    var x = sc.num(); var y = sc.num()
                    if (rel) { x += cx; y += cy }
                    p.moveTo(x, y); cx = x; cy = y; sx = x; sy = y
                    cmd = if (rel) 'l' else 'L'; last = 'M'
                }
                'L' -> { var x = sc.num(); var y = sc.num(); if (rel) { x += cx; y += cy }; p.lineTo(x, y); cx = x; cy = y; last = 'L' }
                'H' -> { var x = sc.num(); if (rel) x += cx; p.lineTo(x, cy); cx = x; last = 'H' }
                'V' -> { var y = sc.num(); if (rel) y += cy; p.lineTo(cx, y); cy = y; last = 'V' }
                'C' -> {
                    var x1 = sc.num(); var y1 = sc.num(); var x2 = sc.num(); var y2 = sc.num(); var x = sc.num(); var y = sc.num()
                    if (rel) { x1 += cx; y1 += cy; x2 += cx; y2 += cy; x += cx; y += cy }
                    p.cubicTo(x1, y1, x2, y2, x, y); lcx = x2; lcy = y2; cx = x; cy = y; last = 'C'
                }
                'S' -> {
                    var x2 = sc.num(); var y2 = sc.num(); var x = sc.num(); var y = sc.num()
                    if (rel) { x2 += cx; y2 += cy; x += cx; y += cy }
                    val x1 = if (last == 'C' || last == 'S') 2 * cx - lcx else cx
                    val y1 = if (last == 'C' || last == 'S') 2 * cy - lcy else cy
                    p.cubicTo(x1, y1, x2, y2, x, y); lcx = x2; lcy = y2; cx = x; cy = y; last = 'S'
                }
                'Q' -> {
                    var x1 = sc.num(); var y1 = sc.num(); var x = sc.num(); var y = sc.num()
                    if (rel) { x1 += cx; y1 += cy; x += cx; y += cy }
                    p.quadTo(x1, y1, x, y); lcx = x1; lcy = y1; cx = x; cy = y; last = 'Q'
                }
                'T' -> {
                    var x = sc.num(); var y = sc.num()
                    if (rel) { x += cx; y += cy }
                    val x1 = if (last == 'Q' || last == 'T') 2 * cx - lcx else cx
                    val y1 = if (last == 'Q' || last == 'T') 2 * cy - lcy else cy
                    p.quadTo(x1, y1, x, y); lcx = x1; lcy = y1; cx = x; cy = y; last = 'T'
                }
                'A' -> {
                    val rx = sc.num(); val ry = sc.num(); val rot = sc.num()
                    val la = sc.flag(); val sw = sc.flag()
                    var x = sc.num(); var y = sc.num()
                    if (rel) { x += cx; y += cy }
                    arc(p, cx, cy, rx, ry, rot, la, sw, x, y); cx = x; cy = y; last = 'A'
                }
                else -> throw ModelException("Неизвестная команда SVG-пути: $cmd")
            }
        }
        return p
    }

    private fun numAttr(s: String?, def: Float): Float {
        if (s == null) return def
        val m = Regex("^\\s*[-+]?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?").find(s) ?: return def
        return m.value.trim().toFloatOrNull() ?: def
    }

    private fun parseTransform(s: String, m: Matrix) {
        for (mt in Regex("(\\w+)\\s*\\(([^)]*)\\)").findAll(s)) {
            val name = mt.groupValues[1]
            val a = mt.groupValues[2].trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }.mapNotNull { it.toFloatOrNull() }
            val t = Matrix()
            when (name) {
                "translate" -> t.setTranslate(a.getOrElse(0) { 0f }, a.getOrElse(1) { 0f })
                "scale" -> t.setScale(a.getOrElse(0) { 1f }, a.getOrElse(1) { a.getOrElse(0) { 1f } })
                "rotate" -> t.setRotate(a.getOrElse(0) { 0f }, a.getOrElse(1) { 0f }, a.getOrElse(2) { 0f })
                "skewX" -> t.setSkew(tan(Math.toRadians(a.getOrElse(0) { 0f }.toDouble())).toFloat(), 0f)
                "skewY" -> t.setSkew(0f, tan(Math.toRadians(a.getOrElse(0) { 0f }.toDouble())).toFloat())
                "matrix" -> if (a.size >= 6) t.setValues(floatArrayOf(a[0], a[2], a[4], a[1], a[3], a[5], 0f, 0f, 1f))
            }
            m.preConcat(t)
        }
    }

    private class St(var fill: String?, var stroke: String?, var sw: Float, var evenOdd: Boolean, val m: Matrix) {
        fun copy() = St(fill, stroke, sw, evenOdd, Matrix(m))
    }

    private class El(val path: Path, val fill: Boolean, val stroke: Boolean, val sw: Float)

    private val SKIP = setOf("defs", "clipPath", "mask", "symbol", "pattern", "linearGradient", "radialGradient", "style", "title", "desc", "metadata", "filter", "marker")

    fun parse(data: ByteArray): Mask {
        val els = ArrayList<El>()
        var vbX = 0f; var vbY = 0f; var vbW = 0f; var vbH = 0f
        var wAttr = 0f; var hAttr = 0f
        val parser = Xml.newPullParser()
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(ByteArrayInputStream(data), null)
        } catch (e: Exception) { throw ModelException("Не удалось прочитать SVG.") }
        val stack = ArrayList<St>()
        stack.add(St("black", null, 1f, false, Matrix()))
        var depth = 0
        var skipDepth = -1
        var ev = parser.eventType
        try {
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    depth++
                    val name = parser.name.substringAfter(':')
                    val st = stack[stack.size - 1].copy()
                    stack.add(st)
                    if (skipDepth < 0 && name in SKIP) skipDepth = depth
                    if (skipDepth < 0) {
                        var display: String? = null
                        val styleMap = HashMap<String, String>()
                        for (i in 0 until parser.attributeCount) styleMap[parser.getAttributeName(i)] = parser.getAttributeValue(i)
                        styleMap["style"]?.split(';')?.forEach { kv ->
                            val k = kv.substringBefore(':', "").trim(); val v = kv.substringAfter(':', "").trim()
                            if (k.isNotEmpty()) styleMap[k] = v
                        }
                        display = styleMap["display"]
                        styleMap["fill"]?.let { st.fill = it }
                        styleMap["stroke"]?.let { st.stroke = it }
                        styleMap["stroke-width"]?.let { st.sw = numAttr(it, st.sw) }
                        styleMap["fill-rule"]?.let { st.evenOdd = it == "evenodd" }
                        styleMap["transform"]?.let { parseTransform(it, st.m) }
                        if (display == "none") skipDepth = depth
                        else if (name == "svg" && depth == 1) {
                            val vb = styleMap["viewBox"]?.trim()?.split(Regex("[\\s,]+"))?.mapNotNull { it.toFloatOrNull() }
                            if (vb != null && vb.size == 4) { vbX = vb[0]; vbY = vb[1]; vbW = vb[2]; vbH = vb[3] }
                            if (styleMap["width"]?.endsWith("%") != true) wAttr = numAttr(styleMap["width"], 0f)
                            if (styleMap["height"]?.endsWith("%") != true) hAttr = numAttr(styleMap["height"], 0f)
                        } else {
                            val p = Path()
                            var closedShape = true
                            var ok = true
                            when (name) {
                                "path" -> { val d = styleMap["d"]; if (d != null) p.addPath(pathFromData(d)) else ok = false }
                                "rect" -> {
                                    val x = numAttr(styleMap["x"], 0f); val y = numAttr(styleMap["y"], 0f)
                                    val w = numAttr(styleMap["width"], 0f); val h = numAttr(styleMap["height"], 0f)
                                    var rx = numAttr(styleMap["rx"], -1f); var ry = numAttr(styleMap["ry"], -1f)
                                    if (rx < 0f && ry >= 0f) rx = ry
                                    if (ry < 0f && rx >= 0f) ry = rx
                                    if (w <= 0f || h <= 0f) ok = false
                                    else if (rx > 0f) p.addRoundRect(RectF(x, y, x + w, y + h), rx, ry, Path.Direction.CW)
                                    else p.addRect(x, y, x + w, y + h, Path.Direction.CW)
                                }
                                "circle" -> { val r = numAttr(styleMap["r"], 0f); if (r <= 0f) ok = false else p.addCircle(numAttr(styleMap["cx"], 0f), numAttr(styleMap["cy"], 0f), r, Path.Direction.CW) }
                                "ellipse" -> {
                                    val rx = numAttr(styleMap["rx"], 0f); val ry = numAttr(styleMap["ry"], 0f)
                                    val cx = numAttr(styleMap["cx"], 0f); val cy = numAttr(styleMap["cy"], 0f)
                                    if (rx <= 0f || ry <= 0f) ok = false else p.addOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), Path.Direction.CW)
                                }
                                "polygon", "polyline" -> {
                                    val sc = Scan(styleMap["points"] ?: "")
                                    var first = true
                                    while (sc.more()) { val x = sc.num(); val y = sc.num(); if (first) { p.moveTo(x, y); first = false } else p.lineTo(x, y) }
                                    if (first) ok = false else if (name == "polygon") p.close() else closedShape = false
                                }
                                "line" -> { p.moveTo(numAttr(styleMap["x1"], 0f), numAttr(styleMap["y1"], 0f)); p.lineTo(numAttr(styleMap["x2"], 0f), numAttr(styleMap["y2"], 0f)); closedShape = false }
                                else -> ok = false
                            }
                            if (ok) {
                                val tp = Path(p)
                                tp.transform(st.m)
                                tp.fillType = if (st.evenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
                                val mv = FloatArray(9)
                                st.m.getValues(mv)
                                val scl = sqrt(abs(mv[0] * mv[4] - mv[1] * mv[3]))
                                val doFill = st.fill != "none" && closedShape || (st.fill != "none" && name == "polyline")
                                val doStroke = st.stroke != null && st.stroke != "none"
                                if (doFill || doStroke) els.add(El(tp, doFill, doStroke, st.sw * scl))
                            }
                        }
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    if (skipDepth == depth) skipDepth = -1
                    depth--
                    if (stack.size > 1) stack.removeAt(stack.size - 1)
                }
                ev = parser.next()
            }
        } catch (e: ModelException) { throw e }
        catch (e: Exception) { throw ModelException("SVG повреждён или не поддерживается.") }
        if (els.isEmpty()) throw ModelException("В SVG не нашлось фигур для превращения в силуэт.")
        if (vbW <= 0f || vbH <= 0f) {
            if (wAttr > 0f && hAttr > 0f) { vbX = 0f; vbY = 0f; vbW = wAttr; vbH = hAttr }
            else {
                val b = RectF(); val tmp = RectF()
                var first = true
                for (e in els) { e.path.computeBounds(tmp, true); if (first) { b.set(tmp); first = false } else b.union(tmp) }
                vbX = b.left; vbY = b.top; vbW = b.width(); vbH = b.height()
            }
        }
        if (vbW <= 0f || vbH <= 0f) throw ModelException("Не удалось определить размер SVG.")
        val scale = 480f / max(vbW, vbH)
        val bw = ceil(vbW * scale).toInt().coerceIn(8, 640); val bh = ceil(vbH * scale).toInt().coerceIn(8, 640)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        c.translate(-vbX, -vbY)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        for (e in els) {
            if (e.fill) { paint.style = Paint.Style.FILL; c.drawPath(e.path, paint) }
            if (e.stroke) { paint.style = Paint.Style.STROKE; paint.strokeWidth = max(e.sw, 0.5f); c.drawPath(e.path, paint) }
        }
        val res = Shapes.analyze(bmp, 0, 90, false, 1, false, 1)
        bmp.recycle()
        if (!res.first.any { it }) throw ModelException("В SVG не нашлось закрашенных фигур.")
        return Mask(res.second, res.first)
    }
}
