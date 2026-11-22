package com.codewall
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Круг с галочкой: рисуется по шагам (сначала окружность, потом сама галочка). */
class CheckView(ctx: Context, private val col: Int) : View(ctx) {
    private val pa = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = col }
    private val rect = RectF()
    private val chk = Path()
    private val seg = Path()
    private var pm: PathMeasure? = null
    private var progress = 0f

    fun start() {
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 800
        a.addUpdateListener { progress = it.animatedValue as Float; invalidate() }
        a.start()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        chk.reset()
        chk.moveTo(w * 0.28f, h * 0.52f); chk.lineTo(w * 0.44f, h * 0.68f); chk.lineTo(w * 0.74f, h * 0.34f)
        pm = PathMeasure(chk, false)
    }

    override fun onDraw(c: Canvas) {
        pa.strokeWidth = width * 0.07f
        val s = pa.strokeWidth
        rect.set(s, s, width - s, height - s)
        c.drawArc(rect, -90f, 360f * (progress / 0.55f).coerceIn(0f, 1f), false, pa)
        val k = ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f)
        val m = pm
        if (k > 0f && m != null) {
            seg.reset()
            m.getSegment(0f, m.length * k, seg, true)
            c.drawPath(seg, pa)
        }
    }
}

object Banner {
    /** Анимированный баннер «Успешно!» поверх экрана. Исчезает сам. */
    fun show(root: FrameLayout, ui: Ui, msg: String) {
        val box = LinearLayout(root.context)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        box.setPadding(ui.dp(28), ui.dp(24), ui.dp(28), ui.dp(24))
        box.background = ui.bg(ui.PANEL, ui.G, 28)
        val chk = CheckView(root.context, ui.G)
        box.addView(chk, LinearLayout.LayoutParams(ui.dp(84), ui.dp(84)))
        val tv = TextView(root.context)
        tv.text = msg; tv.setTextColor(ui.TXT); tv.textSize = 16f; tv.gravity = Gravity.CENTER; tv.typeface = Skin.tf
        tv.setPadding(0, ui.dp(12), 0, 0)
        box.addView(tv, LinearLayout.LayoutParams(ui.dp(230), -2))
        box.alpha = 0f; box.scaleX = 0.6f; box.scaleY = 0.6f
        root.addView(box, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(OvershootInterpolator(1.6f)).withEndAction { chk.start() }.start()
        box.postDelayed({
            box.animate().alpha(0f).setDuration(300).withEndAction { root.removeView(box) }.start()
        }, 2400)
    }
}
