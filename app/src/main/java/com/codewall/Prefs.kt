package com.codewall
import android.content.Context

class Prefs(ctx: Context) {
    val sp = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)
    private fun si(k: String, d: Int) = sp.getInt(k, d)
    private fun sb(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun sf(k: String, d: Float) = sp.getFloat(k, d)
    private fun ss(k: String, d: String) = sp.getString(k, d) ?: d
    private fun pi(k: String, v: Int) { sp.edit().putInt(k, v).apply() }
    private fun pb(k: String, v: Boolean) { sp.edit().putBoolean(k, v).apply() }
    private fun pf(k: String, v: Float) { sp.edit().putFloat(k, v).apply() }
    private fun ps(k: String, v: String) { sp.edit().putString(k, v).apply() }

    var shapeA: String get() = ss("shapeA", "b:0"); set(v) = ps("shapeA", v)
    var shapeB: String get() = ss("shapeB", ""); set(v) = ps("shapeB", v)
    var mode: Int get() = si("mode", 0); set(v) = pi("mode", v)
    var fps: Int get() = si("fps", 60); set(v) = pi("fps", v)
    var speed: Float get() = sf("speed", 1f); set(v) = pf("speed", v)
    var depth: Float get() = sf("depth", 1f); set(v) = pf("depth", v)
    var lineW: Float get() = sf("lineW", 1f); set(v) = pf("lineW", v)
    var scale: Float get() = sf("scale", 1f); set(v) = pf("scale", v)
    var colorInt: Int get() = si("colorInt", 0xFF4DFF88.toInt()); set(v) = pi("colorInt", v)
    var rainbow: Boolean get() = sb("rainbow", false); set(v) = pb("rainbow", v)
    var glow: Boolean get() = sb("glow", true); set(v) = pb("glow", v)
    var bg: Int get() = si("bg", 0); set(v) = pi("bg", v)
    var wClock: Boolean get() = sb("wClock", false); set(v) = pb("wClock", v)
    var wDate: Boolean get() = sb("wDate", false); set(v) = pb("wDate", v)
    var wBattery: Boolean get() = sb("wBattery", false); set(v) = pb("wBattery", v)
    var wText: String get() = ss("wText", ""); set(v) = ps("wText", v)
    var wPos: Int get() = si("wPos", 0); set(v) = pi("wPos", v)
    var quality: Int get() = si("quality", 1); set(v) = pi("quality", v)
    var saver: Boolean get() = sb("saver", true); set(v) = pb("saver", v)
    var author: String get() = ss("author", "аноним"); set(v) = ps("author", v)
    var stamp: Long get() = sp.getLong("stamp", 0); set(v) { sp.edit().putLong("stamp", v).apply() }

    companion object {
        val PALETTE = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFF4DFF88.toInt(), 0xFFFFB000.toInt(), 0xFF3DD6FF.toInt(),
            0xFFFF5DA8.toInt(), 0xFFFF4D4D.toInt(), 0xFFB07CFF.toInt(), 0xFFFF6A00.toInt()
        )
    }
}
