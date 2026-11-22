package com.codewall
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.max
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.*

/** Живой предпросмотр. В режиме interactive: палец двигает выбранный элемент, щипок меняет размер. */
class PreviewView(ctx: Context, private val r: Renderer, private val p: Prefs) : View(ctx) {
    var interactive = false
    var selected = -1
    var onScale: (() -> Unit)? = null
    private val t0 = SystemClock.uptimeMillis()
    private var lx = 0f
    private var ly = 0f
    private val sd = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean { scaleSel(d.scaleFactor); return true }
    })

    private fun scaleSel(f: Float) {
        if (selected == -1) p.scale = (p.scale * f).coerceIn(0.4f, 2f)
        else {
            val l = Widgets.load(p)
            if (selected < l.size) { l[selected].size = (l[selected].size * f).coerceIn(0.4f, 3f); Widgets.save(p, l) }
        }
        onScale?.invoke()
    }

    private fun move(dx: Float, dy: Float) {
        if (selected == -1) { p.posX = (p.posX + dx).coerceIn(0f, 1f); p.posY = (p.posY + dy).coerceIn(0f, 1f) }
        else {
            val l = Widgets.load(p)
            if (selected < l.size) { val w = l[selected]; w.x = (w.x + dx).coerceIn(0f, 1f); w.y = (w.y + dy).coerceIn(0f, 1f); Widgets.save(p, l) }
        }
    }

    private val hudP = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; setShadowLayer(4f, 0f, 0f, Color.BLACK) }
    private var fpsCount = 0
    private var fpsT = 0L
    private var fpsShown = 0

    override fun onDraw(c: Canvas) {
        r.render(c, width.toFloat(), height.toFloat(), (SystemClock.uptimeMillis() - t0) / 1000f)
        if (p.hud) {
            val now = SystemClock.uptimeMillis()
            fpsCount++
            if (now - fpsT >= 1000L) { fpsShown = fpsCount; fpsCount = 0; fpsT = now }
            val budget = 1000f / p.fps.coerceIn(10, 144)
            hudP.textSize = max(18f, width * 0.06f)
            hudP.color = if (r.frameMs < budget * 0.5f) 0xFF7CFF8A.toInt() else if (r.frameMs < budget * 0.8f) 0xFFFFD84D.toInt() else 0xFFFF5D5D.toInt()
            c.drawText(String.format("%d FPS · %.1f мс", fpsShown, r.frameMs), 8f, hudP.textSize + 4f, hudP)
        }
        postInvalidateOnAnimation()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!interactive) return false
        sd.onTouchEvent(e)
        if (sd.isInProgress || e.pointerCount > 1) {
            if (e.actionMasked == MotionEvent.ACTION_POINTER_UP) { val k = if (e.actionIndex == 0) 1 else 0; lx = e.getX(k); ly = e.getY(k) }
            else { lx = e.x; ly = e.y }
            return true
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lx = e.x; ly = e.y }
            MotionEvent.ACTION_MOVE -> { move((e.x - lx) / width, (e.y - ly) / height); lx = e.x; ly = e.y }
        }
        return true
    }
}

class LayoutActivity : Activity() {
    private lateinit var p: Prefs
    private lateinit var ui: Ui
    private lateinit var pv: PreviewView
    private lateinit var seek: SeekBar
    private lateinit var targets: LinearLayout
    private var syncing = false

    @Suppress("DEPRECATION")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Prefs(this); Skin.load(p); ui = Ui(this)
        val r = Renderer(this, p)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        window.setBackgroundDrawable(null)

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        pv = PreviewView(this, r, p)
        pv.interactive = true
        pv.selected = intent.getIntExtra("sel", -1)
        pv.onScale = { syncSeek() }
        root.addView(pv, FrameLayout.LayoutParams(-1, -1))

        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setBackgroundColor(Color.parseColor("#CC070B09"))
        panel.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(12))

        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        targets = LinearLayout(this)
        hs.addView(targets)
        panel.addView(hs)

        seek = SeekBar(this)
        seek.max = 100
        seek.progressTintList = android.content.res.ColorStateList.valueOf(ui.G)
        seek.thumbTintList = android.content.res.ColorStateList.valueOf(ui.G)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, pr: Int, user: Boolean) { if (user && !syncing) setSize(pr / 100f) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        panel.addView(seek, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(6) })

        val row = LinearLayout(this)
        ui.small(row, "СБРОС") { reset() }
        ui.small(row, "ГОТОВО") { finish() }
        panel.addView(row)

        root.addView(panel, FrameLayout.LayoutParams(-1, -2, android.view.Gravity.BOTTOM))
        setContentView(root)
        buildTargets()
        syncSeek()
    }

    private fun buildTargets() {
        targets.removeAllViews()
        val names = ArrayList<String>()
        names.add("Обои")
        for ((i, w) in Widgets.load(p).withIndex()) names.add((i + 1).toString() + ". " + Widgets.TYPES[w.type])
        for (i in names.indices) {
            val tv = TextView(this)
            tv.text = names[i]; tv.textSize = 14f; tv.gravity = android.view.Gravity.CENTER
            val on = (i - 1) == pv.selected
            tv.setTextColor(if (on) ui.ON else Color.WHITE)
            tv.background = ui.bg(if (on) ui.G else ui.DARK, if (on) ui.G else ui.LINE, 14)
            tv.setPadding(ui.dp(14), 0, ui.dp(14), 0)
            tv.setOnClickListener { pv.selected = i - 1; buildTargets(); syncSeek() }
            targets.addView(tv, LinearLayout.LayoutParams(-2, ui.dp(40)).apply { marginEnd = ui.dp(6) })
        }
    }

    private fun syncSeek() {
        syncing = true
        if (pv.selected == -1) seek.progress = ((p.scale - 0.4f) / 1.6f * 100).toInt()
        else { val l = Widgets.load(p); if (pv.selected < l.size) seek.progress = ((l[pv.selected].size - 0.4f) / 2.6f * 100).toInt() }
        syncing = false
    }

    private fun setSize(k: Float) {
        if (pv.selected == -1) p.scale = 0.4f + 1.6f * k
        else { val l = Widgets.load(p); if (pv.selected < l.size) { l[pv.selected].size = 0.4f + 2.6f * k; Widgets.save(p, l) } }
    }

    private fun reset() {
        if (pv.selected == -1) { p.posX = 0.5f; p.posY = 0.46f; p.scale = 1f }
        else { val l = Widgets.load(p); if (pv.selected < l.size) { l[pv.selected].x = 0.5f; l[pv.selected].y = 0.12f + 0.08f * pv.selected; l[pv.selected].size = 1f; Widgets.save(p, l) } }
        syncSeek()
    }
}
