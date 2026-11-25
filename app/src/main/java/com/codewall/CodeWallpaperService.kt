package com.codewall
import android.app.KeyguardManager
import android.content.BroadcastReceiver
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
        private val km = this@CodeWallpaperService.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        private val t0 = SystemClock.uptimeMillis()
        private var visible = false
        private var shownAt = 0L
        private var lowBat = false
        private var lastBat = 0L
        private var frozenT = -1f
        private var lastErr = 0L
        private var tx = 0f
        private var ty = 0f
        private var lastMoveWake = 0L
        private val tilt = TiltSensor(this@CodeWallpaperService) { x, y -> renderer.setTilt(x, y) }

        private fun wants3D() = prefs.scene == 7 || (prefs.pair && prefs.lockScene == 7)

        /** Датчик наклона включён только когда виден экран и выбрана 3D-модель с наклоном. */
        private fun syncSensor() {
            if (visible && prefs.modelGyro && wants3D() && !prefs.static) tilt.start() else tilt.stop()
        }

        // журнал сбоев без спама: не чаще раза в 10 секунд
        private fun logErr(what: String, e: Throwable) {
            val now = SystemClock.uptimeMillis()
            if (now - lastErr > 10000) { lastErr = now; CrashLog.add(this@CodeWallpaperService, what, e) }
        }

        private val rcv = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                when (i?.action) {
                    Intent.ACTION_SCREEN_OFF -> renderer.setLocked(true, false)
                    Intent.ACTION_USER_PRESENT -> {
                        renderer.setLocked(false, true)
                        if (prefs.touch) { val f = surfaceHolder.surfaceFrame; renderer.poke(f.width() / 2f, f.height() * 0.5f) }
                        wake()
                    }
                }
            }
        }

        private val tick = object : Runnable {
            override fun run() {
                val now = SystemClock.uptimeMillis()
                if (now - lastBat > 15000) { lastBat = now; lowBat = lowBattery() }
                val real = (now - t0) / 1000f
                val saving = prefs.saver && (pm.isPowerSaveMode || lowBat)
                var freeze = prefs.eco
                if (saving && (freeze == 0 || freeze > 20)) freeze = 20
                val expired = freeze > 0 && now - shownAt > freeze * 1000L
                val frozen = prefs.static || expired
                if (frozen) tilt.stop() else syncSensor()
                val tUse = if (prefs.static) prefs.staticT
                    else if (expired) { if (frozenT < 0f) frozenT = real; frozenT }
                    else { frozenT = -1f; real }
                draw(tUse)
                if (!visible) return
                if (frozen && !renderer.busy()) {
                    // кадр замер, но часы на виджетах продолжают идти
                    if (renderer.needsClock()) h.postDelayed(this, renderer.clockStep())
                    return
                }
                var fps = prefs.fps
                if (saving) fps = min(fps, 24)
                h.postDelayed(this, 1000L / fps.coerceIn(10, 144))
            }
        }

        private fun wake() {
            shownAt = SystemClock.uptimeMillis()
            syncSensor()
            if (visible) { h.removeCallbacks(tick); tick.run() }
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
            val f = IntentFilter()
            f.addAction(Intent.ACTION_SCREEN_OFF); f.addAction(Intent.ACTION_USER_PRESENT)
            try { this@CodeWallpaperService.registerReceiver(rcv, f) } catch (e: Exception) { }
            try { renderer.setLocked(km.isKeyguardLocked, false); renderer.reload() } catch (e: Throwable) { logErr("Старт обоев", e) }
        }
        override fun onDestroy() {
            prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
            try { this@CodeWallpaperService.unregisterReceiver(rcv) } catch (e: Exception) { }
            h.removeCallbacks(tick)
            tilt.stop()
            super.onDestroy()
        }
        override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
            if (key != null && key in Prefs.RELOAD_KEYS) { try { renderer.reload() } catch (e: Throwable) { logErr("Перезагрузка сцены", e) } }
            if (visible) wake()
        }
        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            h.removeCallbacks(tick)
            if (v) {
                try { Presets.maybeSwitch(this@CodeWallpaperService, prefs) } catch (e: Exception) { }
                if (km.isKeyguardLocked) renderer.setLocked(true, false) else renderer.setLocked(false, true)
                shownAt = SystemClock.uptimeMillis()
                tick.run()
            } else {
                // экран скрыт: рендер полностью спит, ни одного отложенного кадра
                h.removeCallbacksAndMessages(null)
                renderer.onHidden()
                tilt.stop()
            }
        }
        override fun onOffsetsChanged(x: Float, y: Float, xs: Float, ys: Float, xp: Int, yp: Int) {
            renderer.parX = x
        }
        override fun onTouchEvent(e: MotionEvent) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    tx = e.x; ty = e.y
                    if (prefs.touch) renderer.poke(e.x, e.y)
                    wake()
                }
                MotionEvent.ACTION_MOVE -> if (wants3D()) {
                    // вращение 3D-модели свайпом, с инерцией
                    renderer.swipe(e.x - tx, e.y - ty)
                    tx = e.x; ty = e.y
                    val now = SystemClock.uptimeMillis()
                    if (now - lastMoveWake > 60) { lastMoveWake = now; wake() }
                }
            }
        }
        override fun onSurfaceChanged(sh: SurfaceHolder?, f: Int, w: Int, hh: Int) {
            super.onSurfaceChanged(sh, f, w, hh)
            draw(if (prefs.static) prefs.staticT else (SystemClock.uptimeMillis() - t0) / 1000f)
        }

        private fun draw(t: Float) {
            val holder = surfaceHolder
            val c = try { holder.lockHardwareCanvas() } catch (e: Exception) {
                try { holder.lockCanvas() } catch (e2: Exception) { null }
            } ?: return
            try {
                renderer.render(c, c.width.toFloat(), c.height.toFloat(), t)
            } catch (e: Throwable) {
                // одна неудачная отрисовка (в том числе нехватка памяти) не должна ронять обои
                logErr("Ошибка отрисовки", e)
            } finally {
                holder.unlockCanvasAndPost(c)
            }
        }
    }
}
