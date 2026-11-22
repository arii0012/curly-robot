package com.codewall
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.BatteryManager
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Random
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

class Renderer(private val ctx: Context, private val prefs: Prefs) {
    private class Slot(val m: Mask, n: Int) {
        val row: IntArray; val start: IntArray; val len: IntArray; val dep: FloatArray; val xc: FloatArray
        init {
            val ra = ArrayList<Int>(); val sa = ArrayList<Int>(); val la = ArrayList<Int>()
            val da = ArrayList<Float>(); val xa = ArrayList<Float>()
            for (r in 0 until m.rows) {
                var x = 0
                while (x < m.cols) {
                    if (!m.on[r * m.cols + x]) { x++; continue }
                    var e = x
                    while (e < m.cols && m.on[r * m.cols + e]) e++
                    var s = x
                    while (s < e) {
                        val k = min(n, e - s)
                        ra.add(r); sa.add(s); la.add(k); da.add(m.depth[r * m.cols + s + k / 2]); xa.add(s + k / 2f)
                        s += k
                    }
                    x = e
                }
            }
            row = ra.toIntArray(); start = sa.toIntArray(); len = la.toIntArray(); dep = da.toFloatArray(); xc = xa.toFloatArray()
        }
    }

    var parX = 0.5f
    private var lastScale = 1f
    /** Среднее время подготовки кадра, мс (для индикатора нагрузки). */
    val frameMs: Float get() = ema
    val lodLevel: Int get() = lod
    private val scenes = Scenes(ctx, prefs)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint()
    private val gridPaint = Paint().apply { strokeWidth = 1f }
    private val starP = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; color = Color.WHITE }
    private val rainP = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val ripP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowKey = IntArray(2)
    private val glowShader = arrayOfNulls<RadialGradient>(2)
    private var bgKey = 0
    private var bgShader: LinearGradient? = null
    private var slotA: Slot? = null
    private var slotB: Slot? = null
    private var curText = TEXT
    private var text2 = TEXT + TEXT
    private val hsv = FloatArray(3)
    private var lod = 0
    private var ema = 0f
    private var frames = 0
    private val rnd0 = Random(5)
    private val bgStars = FloatArray(180) { rnd0.nextFloat() }
    private val starPts = FloatArray(180)
    private val rainX = FloatArray(40) { rnd0.nextFloat() }
    private val rainS = FloatArray(40) { 0.25f + rnd0.nextFloat() * 0.5f }
    private val rainO = FloatArray(40) { rnd0.nextFloat() * 1.3f }
    private val rainL = FloatArray(40) { 0.03f + rnd0.nextFloat() * 0.07f }
    private val rainPts = FloatArray(160)
    private val rpX = FloatArray(3)
    private val rpY = FloatArray(3)
    private val rpT = LongArray(3)
    private var rpI = 0
    // парные обои
    private var pairProg = 1f
    private var pairFrom = 1f
    private var pairTo = 1f
    private var pairStart = 0L
    private var vt = 0f
    private var lastT = -1f

    fun poke(x: Float, y: Float) {
        val i = rpI++ % 3
        rpX[i] = x; rpY[i] = y; rpT[i] = System.nanoTime()
    }

    /** Экран скрыт: гасим эффекты касаний. Рендер при этом уже остановлен сервисом. */
    fun onHidden() { for (i in 0..2) rpT[i] = 0L }

    fun reload() {
        applyText()
        rebuildSlots()
        scenes.reload()
    }

    private fun applyText() {
        val raw = prefs.codeText.replace('\n', ' ').trim()
        var s = if (raw.isEmpty()) TEXT else "$raw  "
        while (s.length < 400) s += s
        curText = s
        text2 = s + s
    }

    private fun rebuildSlots() {
        val base = when (prefs.quality) { 0 -> 6; 2 -> 3; else -> 4 }
        val n = base + lod * 2
        slotA = Slot(Shapes.resolve(ctx, prefs.shapeA) ?: Shapes.builtIn(0), n)
        val b = prefs.shapeB
        slotB = if (b.isEmpty()) null else Shapes.resolve(ctx, b)?.let { Slot(it, n) }
    }

    private fun adapt(ms: Float) {
        ema = ema * 0.92f + ms * 0.08f
        frames++
        if (frames % 30 != 0) return
        val budget = 1000f / prefs.fps.coerceIn(10, 144)
        // PerformanceGuard сам подбирает плотность частиц; при смене — пересобираем сцену
        PerformanceGuard.report(ema, budget)
        if (PerformanceGuard.scale != lastScale) { lastScale = PerformanceGuard.scale; scenes.reload() }
        if (prefs.scene != 0) return
        if (ema > budget * 0.55f && lod < 4) { lod++; rebuildSlots() }
        else if (ema < budget * 0.2f && lod > 0) { lod--; rebuildSlots() }
    }

    // ---------- парные обои ----------
    private fun lerp(a: Float, b: Float, k: Float) = a + (b - a) * k

    private fun ease(k: Float): Float = when (prefs.pairEase) {
        0 -> k * k * (3f - 2f * k)
        1 -> 1f - (1f - k) * (1f - k) * (1f - k)
        2 -> { val c1 = 1.70158f; val c3 = c1 + 1f; val q = k - 1f; 1f + c3 * q * q * q + c1 * q * q }
        else -> if (k > 0.001f) 1f else 0f
    }

    /** locked=true — состояние «блокировка» (сразу); locked=false — переход на рабочий стол. */
    fun setLocked(locked: Boolean, animate: Boolean) {
        val target = if (locked) 0f else 1f
        if (pairProg == target && pairTo == target) return
        if (locked || !animate || !prefs.pair) { pairProg = target; pairFrom = target; pairTo = target; return }
        pairFrom = pairProg; pairTo = target; pairStart = System.nanoTime()
    }

    private fun updatePair(): Float {
        if (!prefs.pair) return 1f
        if (pairProg != pairTo) {
            val dur = prefs.pairDur.coerceIn(0.2f, 4f)
            val k = ((System.nanoTime() - pairStart) / 1e9f / dur).coerceIn(0f, 1f)
            pairProg = if (k >= 1f) pairTo else pairFrom + (pairTo - pairFrom) * k
        }
        return pairProg
    }

    fun busy(): Boolean {
        if (prefs.pair && pairProg != pairTo) return true
        val now = System.nanoTime()
        for (i in 0..2) if (rpT[i] != 0L && (now - rpT[i]) / 1e9f < 1.1f) return true
        return false
    }

    private fun widgetList(): List<Wdg> {
        val j = prefs.wJson
        if (j != wSrc) { wList = Widgets.parse(j); wSrc = j }
        return wList
    }

    fun needsClock(): Boolean = widgetList().any { it.type == 0 || it.type == 1 || it.type == 6 || it.type == 7 || it.type == 8 || it.type == 9 || it.type == 11 }

    fun clockStep(): Long =
        if (widgetList().any { it.type == 0 && (it.style == 3 || it.style == 4) }) 1000L
        else 60000L - System.currentTimeMillis() % 60000L + 150L

    fun render(c: Canvas, w: Float, h: Float, t: Float) {
        val st = System.nanoTime()
        tickTime()
        if (slotA == null) rebuildSlots()
        val col = prefs.colorInt
        val pair = prefs.pair
        val prog = updatePair()
        val k = if (pair) ease(prog) else 1f
        // виртуальное время: скорость меняется плавно, без скачков фазы
        if (lastT < 0f) { vt = t; lastT = t }
        val dt = (t - lastT).coerceIn(0f, 0.1f)
        lastT = t
        vt += dt * (if (pair) lerp(prefs.lockSpeed, 1f, k) else 1f)
        val sc = if (pair) lerp(prefs.lockScale, prefs.scale, k) else prefs.scale
        val px = if (pair) lerp(prefs.lockX, prefs.posX, k) else prefs.posX
        val py = if (pair) lerp(prefs.lockY, prefs.posY, k) else prefs.posY
        val br = if (pair) lerp(prefs.lockBright, prefs.bright, k.coerceIn(0f, 1f)) else prefs.bright
        val par = if (prefs.parallax) (parX - 0.5f) * w * 0.1f else 0f
        val cx = w * px + par
        val cy = h * py
        val home = prefs.scene
        val lockSc = if (pair && prefs.lockScene >= 0) prefs.lockScene else home
        if (lockSc != home && prog < 0.999f) {
            c.drawColor(Color.BLACK)
            if (prog <= 0.001f) layer(c, w, h, vt, lockSc, sc, cx, cy, col, 1f)
            else { layer(c, w, h, vt, lockSc, sc, cx, cy, col, 1f - prog); layer(c, w, h, vt, home, sc, cx, cy, col, prog) }
        } else layer(c, w, h, vt, home, sc, cx, cy, col, 1f)
        val dim = (((1f - br) * 255f).toInt() + (if (prefs.night && isNight) 110 else 0)).coerceIn(0, 235)
        if (dim > 0) c.drawColor(dim shl 24)
        val wa = if (pair && !prefs.lockWidgets) k.coerceIn(0f, 1f) else 1f
        if (wa >= 0.99f) widgets(c, w, h, t)
        else if (wa > 0.01f) { c.saveLayerAlpha(null, (wa * 255f).toInt()); widgets(c, w, h, t); c.restore() }
        ripples(c, w, h, col)
        adapt((System.nanoTime() - st) / 1e6f)
    }

    private fun layer(c: Canvas, w: Float, h: Float, t: Float, scene: Int, sc: Float, cx: Float, cy: Float, col: Int, alpha: Float) {
        val a = (alpha * 255f).toInt()
        if (a < 255) c.saveLayerAlpha(null, a)
        if (scene in 1..6) {
            c.save()
            c.translate(cx - w / 2, cy - h * 0.46f)
            c.scale(sc, sc, cx, cy)
            scenes.draw(scene, c, w, h, t, 0.46f)
            c.restore()
            if (prefs.bg > 0) background(c, w, h, col, t, false)
        } else {
            val sl = slotA
            if (sl != null) {
                background(c, w, h, col, t, true)
                val s = sc.coerceAtMost(2f)
                val b = slotB
                if (b == null) shape(c, sl, cx, cy, w * 0.84f * s, h * 0.8f * s, t, 0, col)
                else {
                    shape(c, sl, cx - w * 0.23f, cy, w * 0.46f * s, h * 0.8f * s, t, 0, col)
                    shape(c, b, cx + w * 0.23f, cy, w * 0.46f * s, h * 0.8f * s, t, 1, col)
                }
            }
        }
        if (a < 255) c.restore()
    }

    private fun ripples(c: Canvas, w: Float, h: Float, col: Int) {
        if (!prefs.touch) return
        val now = System.nanoTime()
        for (i in 0..2) {
            if (rpT[i] == 0L) continue
            val el = (now - rpT[i]) / 1e9f
            if (el > 1.1f) { rpT[i] = 0L; continue }
            val k = 1f - (1f - el / 1.1f).pow(3)
            ripP.color = col; ripP.alpha = ((1f - k) * 200f).toInt()
            ripP.strokeWidth = min(w, h) * 0.012f * (1f - k) + 2f
            c.drawCircle(rpX[i], rpY[i], min(w, h) * 0.35f * k, ripP)
        }
    }

    private fun background(c: Canvas, w: Float, h: Float, col: Int, t: Float, clear: Boolean) {
        if (clear) c.drawColor(Color.BLACK)
        when (prefs.bg) {
            1 -> {
                val key = h.toInt() * 31 + col
                if (bgShader == null || bgKey != key) {
                    bgShader = LinearGradient(0f, 0f, 0f, h, 0x00000000, (col and 0x00FFFFFF) or 0x30000000, Shader.TileMode.CLAMP)
                    bgKey = key
                }
                bgPaint.shader = bgShader
                c.drawRect(0f, 0f, w, h, bgPaint)
            }
            2 -> {
                gridPaint.color = (col and 0x00FFFFFF) or 0x16000000
                val step = max(24f, w / 14f)
                var x = 0f
                while (x < w) { c.drawLine(x, 0f, x, h, gridPaint); x += step }
                var y = 0f
                while (y < h) { c.drawLine(0f, y, w, y, gridPaint); y += step }
            }
            3 -> {
                for (i in 0 until 90) { starPts[2 * i] = bgStars[2 * i] * w; starPts[2 * i + 1] = bgStars[2 * i + 1] * h }
                starP.strokeWidth = max(1.5f, w * 0.004f)
                starP.alpha = (120 + 90 * sin(t * 1.6f)).toInt(); c.drawPoints(starPts, 0, 90, starP)
                starP.alpha = (120 + 90 * cos(t * 1.1f)).toInt(); c.drawPoints(starPts, 90, 90, starP)
            }
            4 -> {
                for (i in 0 until 40) {
                    val y = ((t * rainS[i] + rainO[i]) % 1.3f - 0.15f) * h
                    rainPts[4 * i] = rainX[i] * w; rainPts[4 * i + 1] = y
                    rainPts[4 * i + 2] = rainX[i] * w; rainPts[4 * i + 3] = y + rainL[i] * h
                }
                rainP.color = (col and 0x00FFFFFF) or 0x66000000
                rainP.strokeWidth = max(1.5f, w * 0.003f)
                c.drawLines(rainPts, 0, 160, rainP)
            }
        }
    }

    private fun mix(a: Int, k: Float): Int {
        val r = (Color.red(a) + (255 - Color.red(a)) * k).toInt()
        val g = (Color.green(a) + (255 - Color.green(a)) * k).toInt()
        val b = (Color.blue(a) + (255 - Color.blue(a)) * k).toInt()
        return Color.rgb(r, g, b)
    }

    private fun shape(c: Canvas, s: Slot, cx: Float, cy: Float, boxW: Float, boxH: Float, t: Float, idx: Int, col: Int) {
        val m = s.m
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
        val rainbow = prefs.rainbow
        val dir = if (idx == 1) -1f else 1f
        val a = if (prefs.mode == 0) (t * sp + idx * 1.7f) * dir else sin(t * sp * 0.8f + idx * 1.7f) * 0.95f
        val ca = cos(a); val sa = sin(a)
        val tilt = 0.1f + sin(t * 0.5f) * 0.1f
        val cb = cos(tilt); val sb = sin(tilt)
        val dd = hw * 0.45f * prefs.depth
        val cam = hw * 5.2f
        val shift = (t * 8f * sp).toInt()
        val sweepRow = ((t * 0.35f + idx * 0.4f) % 1.6f - 0.3f) * m.rows
        val half = m.cols / 2f
        val rhalf = m.rows / 2f
        val tl = curText.length

        paint.style = Paint.Style.FILL_AND_STROKE
        paint.strokeWidth = (prefs.lineW - 0.5f) * ts * 0.03f
        paint.textScaleX = max(abs(ca), 0.15f)

        for (k in 0..1) {
            val side = if (k == 0) 1f else -1f
            val wt = (0.5f + side * ca / 0.6f).coerceIn(0f, 1f)
            if (wt < 0.03f) continue
            var lastRow = -1
            for (i in s.row.indices) {
                val r = s.row[i]
                if (r != lastRow) {
                    lastRow = r
                    var base = col
                    if (rainbow) { hsv[0] = (r * 4f + t * 50f) % 360f; hsv[1] = 0.65f; hsv[2] = 1f; base = Color.HSVToColor(hsv) }
                    val hl = max(0f, 1f - abs(r - sweepRow) / 5f)
                    paint.color = if (hl > 0f) mix(base, hl * 0.85f) else base
                }
                val z = side * (0.2f + 0.8f * s.dep[i]) * dd
                val xx = (s.xc[i] - half) * cw
                val yy = (r - rhalf + 0.5f) * rh
                val x1 = xx * ca + z * sa
                val z1 = -xx * sa + z * ca
                val y2 = yy * cb - z1 * sb
                val z2 = yy * sb + z1 * cb
                val f = cam / (cam - z2)
                val fq = (f * 10f).roundToInt() / 10f
                var light = (0.62f + 0.38f * z2 / hw).coerceIn(0.2f, 1f) * (0.7f + 0.3f * abs(ca))
                light = (light * wt * 8f).toInt() / 8f
                if (light < 0.1f) continue
                paint.alpha = (light * 255f).toInt()
                paint.textSize = ts * fq
                val off = (r * 31 + s.start[i] + shift) % tl
                c.drawText(text2, off, off + s.len[i], cx + x1 * f, cy + y2 * f + ts * fq * 0.3f, paint)
            }
        }
    }

    // ---------- Виджеты ----------
    private val wp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val sp2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val fonts = arrayOf(
        Typeface.create(Typeface.MONOSPACE, Typeface.BOLD), Typeface.create("sans-serif-thin", Typeface.NORMAL),
        Typeface.create("sans-serif-black", Typeface.NORMAL), Typeface.create(Typeface.SERIF, Typeface.BOLD),
        Typeface.create("cursive", Typeface.NORMAL)
    )
    private val path = Path()
    private val rect = RectF()
    private val pz = FloatArray(2)
    private var wList: List<Wdg> = emptyList()
    private var wSrc = ""
    private var tf = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var tfs = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var df1 = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
    private var df2 = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
    private var df3 = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private var dfd = SimpleDateFormat("d", Locale.getDefault())
    private var dfm = SimpleDateFormat("LLLL", Locale.getDefault())
    private var dfw = SimpleDateFormat("EEE", Locale.getDefault())
    private var lastSec = -1L
    private var last24 = -1
    private var lastTz = ""
    private var nowMs = 0L
    private var timeStr = ""; private var secStr = ""; private var d1 = ""; private var d2 = ""; private var d3 = ""
    private var dayNum = ""; private var monName = ""; private var wdName = ""
    private var hh = 0; private var mm = 0; private var bat = 100; private var dayIdx = 0; private var dowIdx = 0
    private var isNight = false; private var todayEpoch = 0L; private var yearPct = 0f
    private val dateCache = HashMap<String, Long>()
    private val dsBuf = IntArray(4)
    private var batText = ""; private var batNum = ""; private var batPct = ""; private var yearTxt1 = ""; private var yearTxt2 = ""
    private var gradSh: LinearGradient? = null
    private var gradKey = 0L
    private var curU = 1f; private var curW = 1f; private var curCol = 0; private var curWd: Wdg? = null

    private fun battery(): Int {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 100
        val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (l < 0) 100 else l * 100 / max(sc, 1)
    }

    /** Формат часов (12/24) и часовой пояс берутся из системы и обновляются на лету. */
    private fun syncFormats() {
        val is24 = if (DateFormat.is24HourFormat(ctx)) 1 else 0
        val tz = TimeZone.getDefault()
        if (is24 == last24 && tz.id == lastTz) return
        last24 = is24; lastTz = tz.id
        val loc = Locale.getDefault()
        fun f(p: String): SimpleDateFormat { val x = SimpleDateFormat(p, loc); x.timeZone = tz; return x }
        tf = f(if (is24 == 1) "HH:mm" else "h:mm"); tfs = f(if (is24 == 1) "HH:mm:ss" else "h:mm:ss")
        df1 = f("EEE, d MMM"); df2 = f("EEEE, d MMMM yyyy"); df3 = f("dd.MM.yyyy"); dfd = f("d"); dfm = f("LLLL"); dfw = f("EEE")
    }

    private fun tickTime() {
        val now = System.currentTimeMillis()
        nowMs = now
        val sec = now / 1000
        if (sec == lastSec) return
        syncFormats()
        if (lastSec < 0 || sec % 30 == 0L) bat = battery()
        lastSec = sec
        val d = Date(now)
        timeStr = tf.format(d); secStr = tfs.format(d); d1 = df1.format(d); d2 = df2.format(d); d3 = df3.format(d)
        dayNum = dfd.format(d); monName = dfm.format(d); wdName = dfw.format(d).uppercase()
        val cal = Calendar.getInstance()
        hh = cal.get(Calendar.HOUR_OF_DAY); mm = cal.get(Calendar.MINUTE); dayIdx = cal.get(Calendar.DAY_OF_YEAR)
        dowIdx = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        isNight = hh >= 22 || hh < 7
        val ld = LocalDate.now()
        todayEpoch = ld.toEpochDay(); yearPct = ld.dayOfYear / ld.lengthOfYear().toFloat()
        // строки виджетов готовим раз в секунду, чтобы не создавать мусор в onDraw
        batText = "BAT $bat%"; batNum = "$bat"; batPct = "$bat%"
        val pcy = (yearPct * 100f).toInt(); yearTxt1 = "ГОД $pcy%"; yearTxt2 = "$pcy%"
    }

    private fun widgets(c: Canvas, w: Float, h: Float, t: Float) {
        val l = widgetList()
        if (l.isEmpty()) return
        for (wd in l) one(c, w, h, t, wd)
    }

    private fun plateSize(wd: Wdg, u: Float) {
        var hw = u * 2.4f; var hh2 = u * 0.9f
        when (wd.type) {
            0 -> when (wd.style) {
                2 -> { hw = u * 1.5f; hh2 = u * 1.5f }
                3 -> { hw = u * 1.7f; hh2 = u * 1.7f }
                4 -> { hw = u * 4.3f; hh2 = u * 1.1f }
                else -> { hw = u * 3.3f; hh2 = u * 1.2f }
            }
            1 -> if (wd.style == 3) { hw = u * 1.2f; hh2 = u * 1.5f } else { hw = u * 3.4f; hh2 = u * 0.65f }
            2 -> if (wd.style == 1) { hw = u * 1.3f; hh2 = u * 1.3f } else { hw = u * 2.3f; hh2 = u * 0.8f }
            3 -> { hw = u * (0.3f * max(4, wd.text.length) + 0.8f); hh2 = u * 0.75f }
            4 -> { hw = u * 2.4f; hh2 = u * 1.3f }
            5 -> { hw = u * 3.3f; hh2 = u * 1.1f }
            6 -> { hw = u * 4f; hh2 = u * 0.8f }
            7 -> { hw = u * 2.4f; hh2 = u * 1.4f }
            8 -> { hw = u * 2.4f; hh2 = u * 1.0f }
            9 -> { hw = u * 2f; hh2 = u * 1.2f }
            11 -> { hw = u * 3.2f; hh2 = u * 0.6f }
            12 -> { hw = u * 4.5f; hh2 = u * 0.7f }
            else -> { hw = u * 3.6f; hh2 = u * 1.1f }
        }
        pz[0] = hw; pz[1] = hh2
    }

    private fun one(c: Canvas, w: Float, h: Float, t: Float, wd: Wdg) {
        val u = min(w, h) * 0.06f * wd.size
        var col = wd.color
        if (wd.anim == 4) { hsv[0] = (t * 60f) % 360f; hsv[1] = 0.7f; hsv[2] = 1f; col = Color.HSVToColor(hsv) }
        curW = w; curWd = wd; curU = u; curCol = col
        var op = wd.op
        if (wd.anim == 5) op *= 0.65f + 0.35f * sin(t * 2.2f)
        if (wd.anim == 7 && ((t * 11f).toInt() * 7919) % 5 == 0) op *= 0.25f
        val layer = op < 0.99f
        if (layer) c.saveLayerAlpha(null, (op.coerceIn(0f, 1f) * 255f).toInt())
        c.save()
        c.translate(w * wd.x, h * wd.y + (if (wd.anim == 8) sin(t * 1.5f) * u * 0.25f else 0f))
        val rot = wd.rot + (if (wd.anim == 6) sin(t * 2f) * 6f else 0f)
        if (rot != 0f) c.rotate(rot)
        if (wd.anim == 1) { val k = 1f + 0.06f * sin(t * 3f); c.scale(k, k) }
        if (wd.plate > 0) {
            plateSize(wd, u)
            rect.set(-pz[0], -pz[1], pz[0], pz[1])
            if (wd.plate == 1) {
                wp.style = Paint.Style.FILL; wp.clearShadowLayer(); wp.shader = null; wp.color = (col and 0x00FFFFFF) or 0x33000000
                c.drawRoundRect(rect, u * 0.35f, u * 0.35f, wp)
            } else {
                sp2.color = col; sp2.alpha = 200; sp2.strokeWidth = u * 0.07f
                c.drawRoundRect(rect, u * 0.35f, u * 0.35f, sp2)
            }
        }
        val gx = if (wd.anim == 2 && ((t * 8f).toInt() % 6 == 0)) sin(t * 97f) * u * 0.25f else 0f
        if (gx != 0f) {
            c.save(); c.translate(-gx, 0f); paintWidget(c, t, u, wd, 0xFF00E5FF.toInt()); c.restore()
            c.translate(gx * 0.3f, 0f)
        }
        paintWidget(c, t, u, wd, col)
        c.restore()
        if (layer) c.restore()
    }

    private fun typed(s: String, wd: Wdg, t: Float): String =
        if (wd.anim != 3) s else s.take(((t % 7f) * 6f).toInt().coerceAtMost(s.length))

    /** Текст с эффектом виджета: контур, тень, градиент, эхо. */
    private fun txt(c: Canvas, s: String, x: Float, y: Float) {
        val fx = curWd?.fx ?: 0
        val u = curU
        when (fx) {
            1 -> {
                val keepC = wp.color; val keepA = wp.alpha
                wp.style = Paint.Style.STROKE; wp.strokeWidth = u * 0.16f; wp.color = Color.BLACK
                c.drawText(s, x, y, wp)
                wp.style = Paint.Style.FILL; wp.color = keepC; wp.alpha = keepA
            }
            2 -> {
                val keepC = wp.color
                wp.color = Color.BLACK; wp.alpha = 150
                c.drawText(s, x + u * 0.09f, y + u * 0.09f, wp)
                wp.color = keepC
            }
            3 -> {
                val c2 = curWd?.color2 ?: curCol
                val key = (curCol.toLong() shl 32) xor (c2.toLong() and 0xFFFFFFFFL) xor (u.toLong() shl 8)
                if (gradSh == null || gradKey != key) { gradSh = LinearGradient(-u * 2f, 0f, u * 2f, 0f, curCol, c2, Shader.TileMode.CLAMP); gradKey = key }
                c.save(); c.translate(x, y)
                wp.shader = gradSh
                c.drawText(s, 0f, 0f, wp)
                wp.shader = null
                c.restore()
                return
            }
            4 -> {
                val keepC = wp.color
                for (k in 2 downTo 1) { wp.alpha = 70; c.drawText(s, x + k * u * 0.07f, y + k * u * 0.07f, wp) }
                wp.color = keepC
            }
        }
        c.drawText(s, x, y, wp)
    }

    private fun ecg(p: Float): Float {
        val q = (p % 0.5f) / 0.5f
        return exp(-((q - 0.30f) * 40f).pow(2)) - 0.25f * exp(-((q - 0.26f) * 60f).pow(2)) -
            0.3f * exp(-((q - 0.34f) * 60f).pow(2)) + 0.15f * exp(-((q - 0.55f) * 20f).pow(2))
    }

    private fun daysTo(s: String): Long {
        val e = dateCache.getOrPut(s) { try { LocalDate.parse(s).toEpochDay() } catch (ex: Exception) { todayEpoch } }
        return e - todayEpoch
    }

    private fun bar(c: Canvas, u: Float, frac: Float, wide: Float) {
        sp2.strokeWidth = u * 0.08f
        rect.set(-wide, -u * 0.25f, wide, u * 0.25f)
        c.drawRoundRect(rect, u * 0.25f, u * 0.25f, sp2)
        rect.set(-wide + u * 0.08f, -u * 0.17f, -wide + 2f * wide * frac.coerceIn(0f, 1f) - u * 0.08f, u * 0.17f)
        if (rect.right > rect.left) c.drawRoundRect(rect, u * 0.15f, u * 0.15f, wp)
    }

    private fun ring(c: Canvas, u: Float, frac: Float) {
        sp2.strokeWidth = u * 0.22f; sp2.alpha = 60
        c.drawCircle(0f, 0f, u, sp2)
        sp2.alpha = 255; rect.set(-u, -u, u, u)
        c.drawArc(rect, -90f, 360f * frac.coerceIn(0f, 1f), false, sp2)
    }

    private fun paintWidget(c: Canvas, t: Float, u: Float, wd: Wdg, col: Int) {
        curCol = col
        wp.style = Paint.Style.FILL; wp.shader = null; wp.color = col; wp.alpha = 255; wp.typeface = fonts[wd.font.coerceIn(0, fonts.size - 1)]
        if (wd.glow) wp.setShadowLayer(u * 0.5f, 0f, 0f, col) else wp.clearShadowLayer()
        sp2.color = col; sp2.alpha = 255
        when (wd.type) {
            0 -> clock(c, t, u, wd, col)
            1 -> date(c, t, u, wd)
            2 -> when (wd.style) {
                0 -> { wp.textSize = u * 0.8f; txt(c, typed(batText, wd, t), 0f, u * 0.3f) }
                1 -> { ring(c, u, bat / 100f); wp.textSize = u * 0.7f; txt(c, batNum, 0f, u * 0.25f) }
                else -> { bar(c, u, bat / 100f, u * 2f); wp.textSize = u * 0.5f; txt(c, batPct, 0f, -u * 0.5f) }
            }
            3 -> {
                wp.textSize = u * 0.9f
                val raw = wd.text.ifEmpty { "PoiPaper" }
                val s = typed(raw, wd, t)
                if (wd.style == 1) {
                    val tw = wp.measureText(raw) + u * 0.8f
                    sp2.strokeWidth = u * 0.07f; rect.set(-tw / 2, -u * 0.7f, tw / 2, u * 0.55f)
                    c.drawRoundRect(rect, u * 0.3f, u * 0.3f, sp2)
                }
                txt(c, if (wd.style == 2) "[ $s ]" else s, 0f, u * 0.3f)
            }
            4 -> {
                sp2.strokeWidth = if (wd.style == 0) u * 0.18f else u * 0.16f
                for (i in 0 until 12) {
                    val x = (i - 5.5f) * u * 0.34f
                    val hg = u * (0.25f + 0.9f * abs(sin(t * 3f + i * 0.8f)) * (0.6f + 0.4f * sin(t * 1.3f + i)))
                    if (wd.style == 0) c.drawLine(x, 0f, x, -hg, sp2)
                    else { var j = 0f; while (j < hg) { c.drawPoint(x, -j, sp2); j += u * 0.26f } }
                }
            }
            5 -> {
                sp2.strokeWidth = u * 0.08f
                path.reset()
                val n = 48
                for (i in 0..n) {
                    val f = i / n.toFloat()
                    val x = (f - 0.5f) * u * 6f
                    val y = -ecg(f + t * 0.8f) * u * 0.9f
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                c.drawPath(path, sp2)
            }
            6 -> {
                val g = when { hh < 5 -> "Доброй ночи"; hh < 12 -> "Доброе утро"; hh < 18 -> "Добрый день"; else -> "Добрый вечер" }
                if (wd.style == 0) { wp.textSize = u * 0.8f; txt(c, typed(g + ", " + prefs.author, wd, t), 0f, u * 0.3f) }
                else { wp.textSize = u * 0.6f; txt(c, "$g,", 0f, -u * 0.25f); wp.textSize = u * 1.2f; txt(c, typed(prefs.author, wd, t), 0f, u * 0.85f) }
            }
            7 -> {
                val parts = wd.text.split(';')
                val days = daysTo(parts[0]); val label = parts.getOrElse(1) { "" }
                if (wd.style == 0) {
                    wp.textSize = u * 2f; txt(c, "" + abs(days), 0f, u * 0.5f)
                    wp.textSize = u * 0.5f; txt(c, (if (days >= 0) "дн. до: " else "дн. назад: ") + label, 0f, u * 1.2f)
                } else {
                    bar(c, u, 1f - days / 100f, u * 2f)
                    wp.textSize = u * 0.6f; txt(c, "" + abs(days) + " дн. · " + label, 0f, -u * 0.55f)
                }
            }
            8 -> {
                if (wd.style == 0) { bar(c, u, yearPct, u * 2f); wp.textSize = u * 0.55f; txt(c, yearTxt1, 0f, -u * 0.55f) }
                else { ring(c, u, yearPct); wp.textSize = u * 0.6f; txt(c, yearTxt2, 0f, u * 0.2f) }
            }
            9 -> {
                val parts = wd.text.split(';')
                val off = parts.getOrElse(1) { "0" }.trim().toIntOrNull() ?: 0
                val tm = Math.floorMod(nowMs / 60000L + off * 60L, 1440L)
                wp.textSize = u * 0.5f; txt(c, parts[0].ifEmpty { "UTC" + (if (off >= 0) "+" else "") + off }, 0f, -u * 0.55f)
                wp.textSize = u * 1.4f; txt(c, String.format("%02d:%02d", tm / 60, tm % 60), 0f, u * 0.65f)
            }
            11 -> {
                val gap = u * 0.75f
                val letters = "ПВСЧПСВ"
                for (i in 0 until 7) {
                    val x = (i - 3f) * gap
                    val today = i == dowIdx
                    if (wd.style == 0) { wp.alpha = if (today) 255 else 70; c.drawCircle(x, 0f, if (today) u * 0.28f else u * 0.16f, wp) }
                    else { wp.alpha = if (today) 255 else 90; wp.textSize = if (today) u * 0.8f else u * 0.55f; c.drawText(letters.substring(i, i + 1), x, u * 0.25f, wp) }
                }
                wp.alpha = 255
            }
            12 -> {
                wp.textSize = u * 0.9f
                val s = wd.text.ifEmpty { "PoiPaper" }
                val tw = wp.measureText(s)
                val x = (curW + tw / 2) - ((t * u * 3f) % (curW + tw)) - curW * wd.x
                txt(c, s, x, u * 0.3f)
            }
            else -> {
                val q = Widgets.QUOTES[dayIdx % Widgets.QUOTES.size]
                var cut = q.indexOf(' ', q.length / 2)
                if (cut < 0) cut = q.length
                wp.textSize = u * 0.55f
                txt(c, typed(q.substring(0, cut), wd, t), 0f, -u * 0.1f)
                if (cut < q.length) txt(c, typed(q.substring(cut + 1), wd, t), 0f, u * 0.6f)
            }
        }
    }

    private fun clock(c: Canvas, t: Float, u: Float, wd: Wdg, col: Int) {
        when (wd.style) {
            0 -> { wp.textSize = u * 1.8f; txt(c, typed(timeStr, wd, t), 0f, u * 0.6f) }
            1 -> {
                wp.textSize = u * 1.8f
                val s = typed(timeStr, wd, t)
                wp.clearShadowLayer(); wp.style = Paint.Style.FILL_AND_STROKE
                for (k in 3 downTo 1) { wp.strokeWidth = u * 0.12f * k; wp.alpha = 28; c.drawText(s, 0f, u * 0.6f, wp) }
                wp.style = Paint.Style.FILL; wp.color = mix(col, 0.8f); wp.alpha = 255
                c.drawText(s, 0f, u * 0.6f, wp)
            }
            2 -> {
                val ds = dsBuf; ds[0] = hh / 10; ds[1] = hh % 10; ds[2] = mm / 10; ds[3] = mm % 10
                val gap = u * 0.55f
                for (cc in 0..3) for (rr in 0..3) {
                    val on = (ds[cc] shr (3 - rr)) and 1
                    val x = (cc - 1.5f) * gap + (if (cc >= 2) gap * 0.4f else -gap * 0.4f)
                    val y = (rr - 1.5f) * gap
                    wp.alpha = if (on == 1) 255 else 55
                    c.drawCircle(x, y, if (on == 1) u * 0.2f else u * 0.12f, wp)
                }
            }
            3 -> {
                val r = u * 1.5f
                sp2.strokeWidth = u * 0.07f
                c.drawCircle(0f, 0f, r, sp2)
                for (k in 0 until 12) {
                    val a = k * 0.5235988f
                    val inner = if (k % 3 == 0) 0.8f else 0.9f
                    c.drawLine(sin(a) * r * inner, -cos(a) * r * inner, sin(a) * r * 0.97f, -cos(a) * r * 0.97f, sp2)
                }
                val secF = (nowMs % 60000L) / 1000f
                val mA = (mm + secF / 60f) * 0.10471976f
                val hA = ((hh % 12) + mm / 60f) * 0.5235988f
                val sA = secF * 0.10471976f
                sp2.strokeWidth = u * 0.11f; c.drawLine(0f, 0f, sin(hA) * r * 0.5f, -cos(hA) * r * 0.5f, sp2)
                sp2.strokeWidth = u * 0.07f; c.drawLine(0f, 0f, sin(mA) * r * 0.78f, -cos(mA) * r * 0.78f, sp2)
                sp2.strokeWidth = u * 0.03f; sp2.color = mix(col, 0.6f); c.drawLine(0f, 0f, sin(sA) * r * 0.88f, -cos(sA) * r * 0.88f, sp2)
                c.drawCircle(0f, 0f, u * 0.08f, wp)
            }
            else -> { wp.textSize = u * 1.3f; txt(c, typed(secStr, wd, t), 0f, u * 0.45f) }
        }
    }

    private fun date(c: Canvas, t: Float, u: Float, wd: Wdg) {
        when (wd.style) {
            0 -> { wp.textSize = u * 0.8f; txt(c, typed(d1, wd, t), 0f, u * 0.3f) }
            1 -> { wp.textSize = u * 0.6f; txt(c, typed(d2, wd, t), 0f, u * 0.25f) }
            2 -> { wp.textSize = u * 0.8f; txt(c, typed(d3, wd, t), 0f, u * 0.3f) }
            else -> {
                sp2.strokeWidth = u * 0.07f
                rect.set(-u * 1.1f, -u * 1.4f, u * 1.1f, u * 1.4f)
                c.drawRoundRect(rect, u * 0.25f, u * 0.25f, sp2)
                rect.set(-u * 1.1f, -u * 1.4f, u * 1.1f, -u * 0.75f)
                c.drawRoundRect(rect, u * 0.25f, u * 0.25f, wp)
                val keep = wp.color
                wp.color = Color.BLACK; wp.clearShadowLayer(); wp.textSize = u * 0.42f
                c.drawText(wdName, 0f, -u * 0.95f, wp)
                wp.color = keep; wp.textSize = u * 1.5f; txt(c, dayNum, 0f, u * 0.6f)
                wp.textSize = u * 0.38f; txt(c, monName, 0f, u * 1.15f)
            }
        }
    }

    companion object {
        val TEXT = ("fastboot devices fastboot flash vendor_boot vendor_boot.img fastboot reboot bootloader " +
            "fastboot --disable-verity --disable-verification flash vbmeta vbmeta.img fastboot set_active a " +
            "fastboot flashing unlock fastboot erase userdata fastboot reboot ").repeat(3)
    }
}
