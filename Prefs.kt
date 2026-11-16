package com.codewall
import android.content.Context

class Prefs(ctx: Context) {
    val sp = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)
    var scene: Int get() = sp.getInt("scene", 0); set(v) { sp.edit().putInt("scene", v).apply() }
    var shape: Int get() = sp.getInt("shape", 0); set(v) { sp.edit().putInt("shape", v).apply() }
    var mode: Int get() = sp.getInt("mode", 0); set(v) { sp.edit().putInt("mode", v).apply() }
    var fps: Int get() = sp.getInt("fps", 60); set(v) { sp.edit().putInt("fps", v).apply() }
    var speed: Float get() = sp.getFloat("speed", 1f); set(v) { sp.edit().putFloat("speed", v).apply() }
    var depth: Float get() = sp.getFloat("depth", 1f); set(v) { sp.edit().putFloat("depth", v).apply() }
    var lineW: Float get() = sp.getFloat("lineW", 1f); set(v) { sp.edit().putFloat("lineW", v).apply() }
    var scale: Float get() = sp.getFloat("scale", 1f); set(v) { sp.edit().putFloat("scale", v).apply() }
    var color: Int get() = sp.getInt("color", 1); set(v) { sp.edit().putInt("color", v).apply() }
    var glow: Boolean get() = sp.getBoolean("glow", true); set(v) { sp.edit().putBoolean("glow", v).apply() }
    var invert: Boolean get() = sp.getBoolean("invert", false); set(v) { sp.edit().putBoolean("invert", v).apply() }
    var hasLogo: Boolean get() = sp.getBoolean("hasLogo", false); set(v) { sp.edit().putBoolean("hasLogo", v).apply() }
    var stamp: Long get() = sp.getLong("stamp", 0); set(v) { sp.edit().putLong("stamp", v).apply() }
    companion object {
        val COLORS = intArrayOf(0xFFFFFFFF.toInt(), 0xFF4DFF88.toInt(), 0xFFFFB000.toInt(), 0xFF3DD6FF.toInt(), 0xFFFF5DA8.toInt())
    }
}
