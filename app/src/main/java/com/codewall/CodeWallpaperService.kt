package com.codewall
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import kotlin.math.min

class CodeWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = E()

    inner class E : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val h = Handler(Looper.getMainLooper())
        private val prefs = Prefs(this@CodeWallpaperService)
        private val renderer = Renderer(this@CodeWallpaperService, prefs)
        private val pm = this@CodeWallpaperService.getSystemService(Context.POWER_SERVICE) as PowerManager
        private val t0 = SystemClock.uptimeMillis()
        private var visible = false
        private var shownAt = 0L
        private var lowBat = false
        private var lastBat = 0L

        private val tick = object : Runnable {
            override fun run() {
                draw()
                if (!visible) return
                val now = SystemClock.uptimeMillis()
                if (now - lastBat > 15000) { lastBat = now; lowBat = lowBattery() }
                val saving = prefs.saver && (pm.isPowerSaveMode || lowBat)
                var fps = prefs.fps
                if (saving) fps = min(fps, 24)
                var freeze = prefs.eco
                if (saving && (freeze == 0 || freeze > 20)) freeze = 20
                // экономия: через N секунд кадр замирает; касание будит
                if (freeze > 0 && now - shownAt > freeze * 1000L) return
                h.postDelayed(this, 1000L / fps.coerceIn(10, 144))
            }
        }

        private fun lowBattery(): Boolean {
            val i = this@CodeWallpaperService.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
            val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
            val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            return l in 0..20 && !charging
        }

        override fun onCreate(sh: SurfaceHolder) {
            super.onCreate(sh)
            setTouchEventsEnabled(true)
            prefs.sp.registerOnSharedPreferenceChangeListener(this)
            renderer.reload()
        }
        override fun onDestroy() {
            prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
            h.removeCallbacks(tick)
            super.onDestroy()
        }
        override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
            if (key != null && key in Prefs.RELOAD_KEYS) renderer.reload()
        }
        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            h.removeCallbacks(tick)
            if (v) {
                Presets.maybeSwitch(this@CodeWallpaperService, prefs)
                shownAt = SystemClock.uptimeMillis()
                tick.run()
            }
        }
        override fun onOffsetsChanged(x: Float, y: Float, xs: Float, ys: Float, xp: Int, yp: Int) {
            renderer.parX = x
        }
        override fun onTouchEvent(e: MotionEvent) {
            if (e.actionMasked != MotionEvent.ACTION_DOWN || !prefs.touch) return
            renderer.poke(e.x, e.y)
            shownAt = SystemClock.uptimeMillis()
            if (visible) { h.removeCallbacks(tick); tick.run() }
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
                renderer.render(c, c.width.toFloat(), c.height.toFloat(), (SystemClock.uptimeMillis() - t0) / 1000f)
            } finally {
                holder.unlockCanvasAndPost(c)
            }
        }
    }
}
