package com.codewall
import android.graphics.Color

class SkinDef(val name: String, val accent: Int, val bg: Int, val panel: Int, val panel2: Int, val line: Int, val dim: Int, val text: Int)

/** Оформление самого приложения. Выбирается в «Темы → Приложение», может меняться вместе с темой обоев. */
object Skin {
    private fun c(v: Long) = v.toInt()
    val ALL = listOf(
        SkinDef("Матрица", c(0xFF4DFF88), c(0xFF070B09), c(0xFF0E1511), c(0xFF0A100D), c(0xFF1F2B24), c(0xFF8A9A90), c(0xFFFFFFFF)),
        SkinDef("Неон", c(0xFFFF5DA8), c(0xFF0B0710), c(0xFF150E1C), c(0xFF0F0A15), c(0xFF2A1B38), c(0xFF9A8AA6), c(0xFFFFFFFF)),
        SkinDef("Лёд", c(0xFF3DD6FF), c(0xFF060B10), c(0xFF0C1620), c(0xFF08111A), c(0xFF1A2B3A), c(0xFF8AA0AE), c(0xFFFFFFFF)),
        SkinDef("Закат", c(0xFFFF8A3D), c(0xFF0E0806), c(0xFF1A1009), c(0xFF130B07), c(0xFF33200F), c(0xFFA8947F), c(0xFFFFFFFF)),
        SkinDef("Аметист", c(0xFFB07CFF), c(0xFF0A0711), c(0xFF140E20), c(0xFF0E0918), c(0xFF2B1F42), c(0xFF9A8DB0), c(0xFFFFFFFF)),
        SkinDef("Моно", c(0xFFFFFFFF), c(0xFF000000), c(0xFF111111), c(0xFF0A0A0A), c(0xFF262626), c(0xFF8C8C8C), c(0xFFFFFFFF)),
        SkinDef("Золото", c(0xFFFFD84D), c(0xFF0B0903), c(0xFF17130A), c(0xFF100D06), c(0xFF302811), c(0xFFA89C78), c(0xFFFFFFFF)),
        SkinDef("Светлая", c(0xFF16A34A), c(0xFFF2F4F3), c(0xFFFFFFFF), c(0xFFEAEDEB), c(0xFFD3D8D5), c(0xFF5F6B65), c(0xFF0E1411))
    )
    var cur: SkinDef = ALL[0]
    var accent: Int = ALL[0].accent
    var radius = 1f

    fun load(p: Prefs) {
        cur = ALL[p.skin.coerceIn(0, ALL.size - 1)]
        accent = if (p.accentCustom != 0) p.accentCustom else cur.accent
        radius = when (p.corners) { 0 -> 0.35f; 2 -> 1.7f; else -> 1f }
    }

    private fun lum(c: Int) = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f
    fun onAccent(): Int = if (lum(accent) > 0.55f) Color.BLACK else Color.WHITE

    fun chipOn(): Int {
        val k = 0.2f; val a = cur.panel
        return Color.rgb((Color.red(a) + (Color.red(accent) - Color.red(a)) * k).toInt(),
            (Color.green(a) + (Color.green(accent) - Color.green(a)) * k).toInt(),
            (Color.blue(a) + (Color.blue(accent) - Color.blue(a)) * k).toInt())
    }
}
