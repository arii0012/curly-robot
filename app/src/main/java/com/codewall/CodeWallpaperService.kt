package com.codewall
import android.content.SharedPreferences
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max

class CodeWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = E()

    inner class E : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val h = Handler(Looper.getMainLooper())
        private val prefs = Prefs(this@CodeWallpaperService)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
        private val t0 = SystemClock.uptimeMillis()
        private var visible = false
        private var builtIn: Mask? = null
        private var custom: Mask? = null
        private val tick = object : Runnable {
            override fun run() { draw(); if (visible) h.postDelayed(this, 1000L / prefs.fps.coerceIn(10, 144)) }
        }
        private val text2 = TEXT + TEXT

        override fun onCreate(sh: SurfaceHolder) {
            super.onCreate(sh); prefs.sp.registerOnSharedPreferenceChangeListener(this); reload()
        }
        override fun onDestroy() {
            prefs.sp.unregisterOnSharedPreferenceChangeListener(this); h.removeCallbacks(tick); super.onDestroy()
        }
        override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) { if (key == "invert" || key == "stamp" || key == "hasLogo") reload() }
        override fun onVisibilityChanged(v: Boolean) { visible = v; h.removeCallbacks(tick); if (v) tick.run() }
        override fun onSurfaceChanged(sh: SurfaceHolder?, f: Int, w: Int, hh: Int) { super.onSurfaceChanged(sh, f, w, hh); draw() }

        private fun reload() {
            if (builtIn == null) builtIn = Shapes.builtIn()
            custom = null
            val f = File(filesDir, "logo.png")
            if (prefs.hasLogo && f.exists()) BitmapFactory.decodeFile(f.path)?.let { custom = Shapes.fromBitmap(it, prefs.invert) }
        }

        private fun draw() {
            val c = try { surfaceHolder.lockCanvas() } catch (e: Exception) { null } ?: return
            try { render(c) } finally { surfaceHolder.unlockCanvasAndPost(c) }
        }

        private fun render(c: Canvas) {
            c.drawColor(Color.BLACK)
            val w = c.width.toFloat(); val hh = c.height.toFloat()
            val t = (SystemClock.uptimeMillis() - t0) / 1000f
            val list = when (prefs.scene) {
                0 -> listOfNotNull(builtIn)
                1 -> listOfNotNull(custom ?: builtIn)
                else -> listOfNotNull(builtIn, custom)
            }
            if (list.size == 1) shape(c, list[0], w / 2, hh * 0.42f, w * 0.8f * prefs.scale, t, 0f)
            else list.forEachIndexed { i, m -> shape(c, m, if (i == 0) w * 0.27f else w * 0.73f, hh * 0.42f, w * 0.46f * prefs.scale, t, i * 1.7f) }
        }

        private fun shape(c: Canvas, m: Mask, cx: Float, cy: Float, width: Float, t: Float, phase: Float) {
            val ts = width / (m.cols * 0.6f); val cw = ts * 0.6f; val rh = ts * 0.8f
            paint.textSize = ts; paint.color = Prefs.COLORS[prefs.color.coerceIn(0, 2)]
            paint.style = Paint.Style.FILL_AND_STROKE; paint.strokeWidth = (prefs.lineW - 0.5f) * ts * 0.03f
            val cs = max(abs(cos(t * prefs.speed + phase)), 0.05f)   // вращение вокруг вертикальной оси
            val shift = (t * 10f * prefs.speed).toInt()
            val top = cy - m.rows * rh / 2
            for (r in 0 until m.rows) {
                val y = top + r * rh; val runs = m.runs[r]; var i = 0
                while (i < runs.size) {
                    val s = runs[i]; val e = runs[i + 1]; i += 2
                    val off = (r * 31 + s + shift) % TEXT.length
                    val sl = text2.substring(off, off + (e - s))
                    c.save(); c.translate(cx, y); c.scale(cs, 1f)
                    c.drawText(sl, 0, sl.length, (s - m.cols / 2f) * cw, 0f, paint)
                    c.restore()
                }
            }
        }
    }

    companion object {
        val TEXT = ("fastboot devices fastboot flash vendor_boot vendor_boot.img fastboot reboot bootloader " +
            "fastboot --disable-verity --disable-verification flash vbmeta vbmeta.img fastboot set_active a " +
            "fastboot flashing unlock fastboot erase userdata fastboot reboot ").repeat(3)
    }
}
