package com.codewall
import android.content.Context
import org.json.JSONObject

class Prefs(ctx: Context, name: String = "cfg") {
    val sp = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
    private fun si(k: String, d: Int) = sp.getInt(k, d)
    private fun sb(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun sf(k: String, d: Float) = sp.getFloat(k, d)
    private fun ss(k: String, d: String) = sp.getString(k, d) ?: d
    private fun pi(k: String, v: Int) { sp.edit().putInt(k, v).apply() }
    private fun pb(k: String, v: Boolean) { sp.edit().putBoolean(k, v).apply() }
    private fun pf(k: String, v: Float) { sp.edit().putFloat(k, v).apply() }
    private fun ps(k: String, v: String) { sp.edit().putString(k, v).apply() }

    var scene: Int get() = si("scene", 0); set(v) = pi("scene", v)
    var shapeA: String get() = ss("shapeA", "b:0"); set(v) = ps("shapeA", v)
    var shapeB: String get() = ss("shapeB", ""); set(v) = ps("shapeB", v)
    var mode: Int get() = si("mode", 0); set(v) = pi("mode", v)
    var fps: Int get() = si("fps", 30); set(v) = pi("fps", v)
    var eco: Int get() = si("eco", 60); set(v) = pi("eco", v)
    var speed: Float get() = sf("speed", 1f); set(v) = pf("speed", v)
    var depth: Float get() = sf("depth", 1f); set(v) = pf("depth", v)
    var lineW: Float get() = sf("lineW", 1f); set(v) = pf("lineW", v)
    var scale: Float get() = sf("scale", 1f); set(v) = pf("scale", v)
    var density: Float get() = sf("density", 1f); set(v) = pf("density", v)
    var colorInt: Int get() = si("colorInt", 0xFF4DFF88.toInt()); set(v) = pi("colorInt", v)
    var rainbow: Boolean get() = sb("rainbow", false); set(v) = pb("rainbow", v)
    var glow: Boolean get() = sb("glow", true); set(v) = pb("glow", v)
    var bg: Int get() = si("bg", 0); set(v) = pi("bg", v)
    var bright: Float get() = sf("bright", 1f); set(v) = pf("bright", v)
    var night: Boolean get() = sb("night", false); set(v) = pb("night", v)
    var touch: Boolean get() = sb("touch", true); set(v) = pb("touch", v)
    var parallax: Boolean get() = sb("parallax", true); set(v) = pb("parallax", v)
    var target: Int get() = si("target", 0); set(v) = pi("target", v)
    var words: String get() = ss("words", "I love you,Sweetheart,Je t'aime,Forever,Always,Love,Te amo,Me & you,Mi vida"); set(v) = ps("words", v)
    var message: String get() = ss("message", "Каждый удар моего сердца носит твоё имя."); set(v) = ps("message", v)
    var label: String get() = ss("label", "I LOVE YOU"); set(v) = ps("label", v)
    var wJson: String get() = ss("wJson", "[]"); set(v) = ps("wJson", v)
    var posX: Float get() = sf("posX", 0.5f); set(v) = pf("posX", v)
    var posY: Float get() = sf("posY", 0.46f); set(v) = pf("posY", v)
    var quality: Int get() = si("quality", 1); set(v) = pi("quality", v)
    var saver: Boolean get() = sb("saver", true); set(v) = pb("saver", v)
    var author: String get() = ss("author", "аноним"); set(v) = ps("author", v)
    var stamp: Long get() = sp.getLong("stamp", 0); set(v) { sp.edit().putLong("stamp", v).apply() }
    // оформление приложения
    var skin: Int get() = si("skin", 0); set(v) = pi("skin", v)
    var accentCustom: Int get() = si("accentCustom", 0); set(v) = pi("accentCustom", v)
    var corners: Int get() = si("corners", 1); set(v) = pi("corners", v)
    var skinWithTheme: Boolean get() = sb("skinWithTheme", true); set(v) = pb("skinWithTheme", v)
    // автосмена сборок
    var shuffle: Int get() = si("shuffle", 0); set(v) = pi("shuffle", v)
    var shuffleAt: Long get() = sp.getLong("shuffleAt", 0); set(v) { sp.edit().putLong("shuffleAt", v).apply() }
    var shuffleIdx: Int get() = si("shuffleIdx", 0); set(v) = pi("shuffleIdx", v)

    fun snapshot(skip: Set<String>): String {
        val o = JSONObject()
        for ((k, v) in sp.all) {
            if (k in skip) continue
            when (v) {
                is Int -> o.put(k, "i:$v"); is Long -> o.put(k, "l:$v"); is Float -> o.put(k, "f:$v")
                is Boolean -> o.put(k, "b:$v"); is String -> o.put(k, "s:$v")
            }
        }
        return o.toString()
    }

    fun restore(txt: String): Boolean {
        return try {
            val o = JSONObject(txt); val e = sp.edit(); val it = o.keys()
            while (it.hasNext()) {
                val k = it.next(); val s = o.getString(k); val v = s.substring(2)
                when (s[0]) {
                    'i' -> e.putInt(k, v.toInt()); 'l' -> e.putLong(k, v.toLong()); 'f' -> e.putFloat(k, v.toFloat())
                    'b' -> e.putBoolean(k, v.toBoolean()); 's' -> e.putString(k, v)
                }
            }
            e.putLong("stamp", System.currentTimeMillis()).apply(); true
        } catch (ex: Exception) { false }
    }

    fun saveBackup() { ps("backup", snapshot(SKIP)) }
    fun restoreBackup(): Boolean { val t = sp.getString("backup", null) ?: return false; return restore(t) }

    companion object {
        val PALETTE = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFF4DFF88.toInt(), 0xFFFFB000.toInt(), 0xFF3DD6FF.toInt(),
            0xFFFF5DA8.toInt(), 0xFFFF4D4D.toInt(), 0xFFB07CFF.toInt(), 0xFFFF6A00.toInt()
        )
        val PALETTE2 = intArrayOf(
            0xFFFFD84D.toInt(), 0xFFA3FF3D.toInt(), 0xFF00E5B0.toInt(), 0xFF4D7CFF.toInt(),
            0xFFE040FB.toInt(), 0xFFFF8FC7.toInt(), 0xFFB0B8C0.toInt(), 0xFF8B5E3C.toInt()
        )
        val SKIP = setOf("backup", "token", "refresh", "uid", "email", "author", "shuffleAt", "shuffleIdx")
        val PRESET_SKIP = SKIP + setOf("skin", "accentCustom", "corners", "skinWithTheme", "shuffle")
        val RELOAD_KEYS = setOf("scene", "shapeA", "shapeB", "stamp", "quality", "target", "words", "message", "label", "density", "colorInt")
    }
}
