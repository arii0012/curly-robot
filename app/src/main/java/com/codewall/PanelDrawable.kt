package com.codewall
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.max

/** Панель интерфейса: скруглённая, со срезанными углами, глянцевая, неоморфная и т. д. — по стилю темы. */
class PanelDrawable(private val fill: Int, private val stroke: Int, private val radius: Float, private val sw: Float,
                    private val kind: Int, private val accent2: Int) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rf = RectF()

    private fun shape(l: Float, t: Float, r: Float, b: Float, grow: Float) {
        path.reset()
        if (kind == 2) {
            val cut = max(4f, radius * 0.9f + grow)
            path.moveTo(l + cut, t); path.lineTo(r, t); path.lineTo(r, b - cut); path.lineTo(r - cut, b); path.lineTo(l, b); path.lineTo(l, t + cut)
            path.close()
        } else {
            val rad = max(0f, radius + grow)
            rf.set(l, t, r, b)
            path.addRoundRect(rf, rad, rad, Path.Direction.CW)
        }
    }

    override fun draw(c: Canvas) {
        val bd = bounds
        val h = sw / 2f
        var ml = bd.left + h; var mt = bd.top + h; var mr = bd.right - h; var mb = bd.bottom - h
        if (kind == 4) {
            val d = sw * 1.4f
            shape(ml + d, mt + d, mr, mb, 0f)
            p.style = Paint.Style.FILL; p.color = Color.BLACK
            c.drawPath(path, p)
            mr -= d; mb -= d
        }
        if (kind == 5) {
            val d = sw * 2.2f
            ml += d; mt += d; mr -= d; mb -= d
            shape(ml + d * 0.8f, mt + d * 0.8f, mr + d * 0.8f, mb + d * 0.8f, 0f)
            p.style = Paint.Style.FILL; p.color = 0x22000000
            c.drawPath(path, p)
            shape(ml - d * 0.8f, mt - d * 0.8f, mr - d * 0.8f, mb - d * 0.8f, 0f)
            p.color = 0x66FFFFFF
            c.drawPath(path, p)
        }
        shape(ml, mt, mr, mb, 0f)
        p.style = Paint.Style.FILL; p.color = fill
        c.drawPath(path, p)
        if (kind == 3 || kind == 1) {
            c.save()
            c.clipPath(path)
            val hh = (mb - mt) * (if (kind == 3) 0.5f else 0.35f)
            p.shader = LinearGradient(0f, mt, 0f, mt + hh, if (kind == 3) 0x66FFFFFF else 0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            c.drawRect(ml, mt, mr, mt + hh, p)
            p.shader = null
            c.restore()
        }
        p.style = Paint.Style.STROKE; p.strokeWidth = sw; p.color = stroke
        c.drawPath(path, p)
        if (kind == 6) {
            val d = sw * 2.4f
            shape(ml + d, mt + d, mr - d, mb - d, -d)
            p.color = accent2; p.strokeWidth = max(1f, sw * 0.7f)
            c.drawPath(path, p)
        }
        if (kind == 2) {
            // неоновый «уголок» в срезанных вершинах
            p.color = accent2; p.strokeWidth = sw * 1.6f
            val cut = max(4f, radius * 0.9f)
            c.drawLine(ml, mt + cut, ml + cut, mt, p)
            c.drawLine(mr, mb - cut, mr - cut, mb, p)
        }
    }

    override fun getOutline(outline: Outline) { outline.setRoundRect(bounds, radius) }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
