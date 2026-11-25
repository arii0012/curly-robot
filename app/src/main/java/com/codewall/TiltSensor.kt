package com.codewall
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/** Наклон телефона по акселерометру (экономный режим опроса). Работает только пока включён и экран виден. */
class TiltSensor(ctx: Context, private val onTilt: (Float, Float) -> Unit) : SensorEventListener {
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val acc: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var on = false

    fun start() {
        if (on || acc == null) return
        sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_UI)
        on = true
    }

    fun stop() {
        if (!on) return
        sm.unregisterListener(this)
        on = false
        onTilt(0f, 0f)
    }

    override fun onSensorChanged(e: SensorEvent) {
        val g = 9.81f
        onTilt((-e.values[0] / g).coerceIn(-1f, 1f), (e.values[2] / g).coerceIn(-1f, 1f))
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}
