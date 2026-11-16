package com.codewall
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

class CodeWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = E()

    inner class E : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val h = Handler(Looper.getMainLooper())
        private val prefs = Prefs(this@CodeWallpaperService)
        private val renderer = Renderer(this@CodeWallpaperService, prefs)
        private val t0 = SystemClock.uptimeMillis()
        private var visible = false
        private val tick = object : Runnable {
            override fun run() { draw(); if (visible) h.postDelayed(this, 1000L / prefs.fps.coerceIn(10, 144)) }
        }

        override fun onCreate(sh: SurfaceHolder) {
            super.onCreate(sh)
            prefs.sp.registerOnSharedPreferenceChangeListener(this)
            renderer.reload()
        }
        override fun onDestroy() {
            prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
            h.removeCallbacks(tick)
            super.onDestroy()
        }
        override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
            if (key == "invert" || key == "stamp" || key == "hasLogo" || key == "shape") renderer.reload()
        }
        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            h.removeCallbacks(tick)
            if (v) tick.run()
        }
        override fun onSurfaceChanged(sh: SurfaceHolder?, f: Int, w: Int, hh: Int) {
            super.onSurfaceChanged(sh, f, w, hh); draw()
        }

        private fun draw() {
            val holder = surfaceHolder
            val c = try { holder.lockHardwareCanvas() } catch (e: Exception) {
                try { holder.lockCanvas() } catch (e2: Exception) { null }
            } ?: return
            try {
                renderer.render(c, c.width.toFloat(), c.height.toFloat(), (SystemClock.uptimeMillis() - t0) / 1000f, 0.44f)
            } finally {
                holder.unlockCanvasAndPost(c)
            }
        }
    }
}
