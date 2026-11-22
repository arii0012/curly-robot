package com.codewall
import android.graphics.Color
import android.graphics.Typeface
import kotlin.math.max

/**
 * Оформление приложения.
 * kind (форма панелей): 0 обычная, 1 матовое стекло, 2 кибер-срез углов, 3 глянец, 4 манга (жёсткая тень), 5 неоморфизм, 6 двойная обводка.
 * backdrop (живой фон): 0 нет, 1 блики, 2 кибер-сетка, 3 синтвейв, 4 пузыри, 5 бумага, 6 сияние, 7 дед-инсайд,
 * 8 сердечки, 9 дождь, 10 звёзды, 11 конфетти, 12 хоррор.
 * font: 0 системный, 1 моно, 2 узкий, 3 с засечками, 4 моно-курсив, 5 плотный.
 */
class SkinDef(val name: String, val accent: Int, val bg: Int, val panel: Int, val panel2: Int, val line: Int, val dim: Int, val text: Int,
              val kind: Int = 0, val backdrop: Int = 0, val panelAlpha: Float = 1f, val strokeDp: Float = 1f,
              val radiusMul: Float = 1f, val font: Int = 0, val accent2: Int = accent, val mood: Boolean = false)

object Skin {
    private fun c(v: Long) = v.toInt()
    const val CHAMELEON = 22
    const val CUSTOM = 23
    const val COUNT = 24

    private fun classic(n: String, a: Long, bg: Long, pn: Long, p2: Long, ln: Long, dm: Long, tx: Long) =
        SkinDef(n, c(a), c(bg), c(pn), c(p2), c(ln), c(dm), c(tx))

    private val BASE = listOf(
        SkinDef("Liquid Glass", c(0xFF7FD6FF), c(0xFF0B1220), c(0xFF1B2A44), c(0xFF16233A), c(0x66FFFFFF), c(0xFF9FB4D0), c(0xFFFFFFFF),
            kind = 1, backdrop = 1, panelAlpha = 0.55f, radiusMul = 1.5f, accent2 = c(0xFFB48CFF)),
        SkinDef("Cyberpunk 2099", c(0xFFFCEE09), c(0xFF050A1F), c(0xFF0B1235), c(0xFF081028), c(0xFF2B3A8F), c(0xFF7F8CC9), c(0xFFFFFFFF),
            kind = 2, backdrop = 2, strokeDp = 1.5f, radiusMul = 0.5f, font = 2, accent2 = c(0xFFFF2A6D)),
        SkinDef("AMOLED Obsidian", c(0xFFD4AF37), c(0xFF000000), c(0xFF0A0A0A), c(0xFF050505), c(0xFFD4AF37), c(0xFF8A8466), c(0xFFF5E9C8),
            kind = 6, accent2 = c(0xFF2ECC71)),
        SkinDef("Synthwave 80s", c(0xFFFF3CAC), c(0xFF1A0B2E), c(0xFF2A1250), c(0xFF210E40), c(0xFF7A2CBF), c(0xFFB79BE0), c(0xFFFFF0FF),
            backdrop = 3, font = 4, accent2 = c(0xFF2DE2E6)),
        SkinDef("Frutiger Aero", c(0xFF7CFC6A), c(0xFF0B5A86), c(0xFF1E86B8), c(0xFF176FA0), c(0xFF9FE6FF), c(0xFFCDEBFA), c(0xFFFFFFFF),
            kind = 3, backdrop = 4, radiusMul = 1.6f, accent2 = c(0xFFFFFFFF)),
        SkinDef("Manga / Ink", c(0xFF111111), c(0xFFF4F1EA), c(0xFFFFFFFF), c(0xFFEDEAE2), c(0xFF111111), c(0xFF555555), c(0xFF111111),
            kind = 4, backdrop = 5, strokeDp = 2.5f, radiusMul = 0.3f, font = 5, accent2 = c(0xFFD32F2F)),
        SkinDef("Aurora Borealis", c(0xFF5CFFC8), c(0xFF04121F), c(0xFF0B2A3A), c(0xFF081E2C), c(0xFF2B6F73), c(0xFF8FB8C0), c(0xFFEFFFFA),
            kind = 1, backdrop = 6, panelAlpha = 0.6f, radiusMul = 1.3f, accent2 = c(0xFFB07CFF)),
        SkinDef("Minimalist Zen", c(0xFF7FA38B), c(0xFFE9E4DC), c(0xFFEFEAE2), c(0xFFE2DCD2), c(0xFFD2CBC0), c(0xFF7A746A), c(0xFF3B3833),
            kind = 5, radiusMul = 1.8f, accent2 = c(0xFFD9A5A0)),
        SkinDef("Dead Inside", c(0xFFE10600), c(0xFF050505), c(0xFF111111), c(0xFF0A0A0A), c(0xFF3A3A3A), c(0xFF8A8A8A), c(0xFFFFFFFF),
            kind = 2, backdrop = 7, strokeDp = 1.5f, radiusMul = 0.6f, font = 1, accent2 = c(0xFFFFFFFF), mood = true),
        SkinDef("Love & Romance", c(0xFFFF6FA8), c(0xFF2A0A1E), c(0xFF45102F), c(0xFF360C25), c(0xFF8C2B63), c(0xFFE0A0C0), c(0xFFFFF0F6),
            kind = 1, backdrop = 8, panelAlpha = 0.7f, radiusMul = 1.8f, accent2 = c(0xFFFFC2DA), mood = true),
        SkinDef("Depressive", c(0xFF7C93AD), c(0xFF0E1218), c(0xFF1B222C), c(0xFF141A22), c(0xFF2E3846), c(0xFF7A8794), c(0xFFC9D2DC),
            backdrop = 9, panelAlpha = 0.9f, accent2 = c(0xFF5F7288), mood = true),
        SkinDef("Sad / Rainy Night", c(0xFFFFB86B), c(0xFF0A0E1F), c(0xFF141B33), c(0xFF0F1428), c(0xFF27325A), c(0xFF8B94B8), c(0xFFE6E9F7),
            kind = 1, backdrop = 10, panelAlpha = 0.75f, accent2 = c(0xFF6B8CFF), mood = true),
        SkinDef("Happy / Party", c(0xFFFFE600), c(0xFF1B1038), c(0xFF2D1B5E), c(0xFF241449), c(0xFFFF9F1C), c(0xFFD6C2FF), c(0xFFFFFFFF),
            kind = 3, backdrop = 11, radiusMul = 1.8f, font = 5, accent2 = c(0xFF00E5FF), mood = true),
        SkinDef("Horror / Creepy", c(0xFFB0121B), c(0xFF0A0003), c(0xFF1A0508), c(0xFF120305), c(0xFF5C0A12), c(0xFF8A5A5E), c(0xFFEBD5D5),
            backdrop = 12, radiusMul = 0.6f, font = 3, accent2 = c(0xFFFF3B30), mood = true),
        // классика (прежние оформления)
        classic("Матрица", 0xFF4DFF88, 0xFF070B09, 0xFF0E1511, 0xFF0A100D, 0xFF1F2B24, 0xFF8A9A90, 0xFFFFFFFF),
        classic("Неон", 0xFFFF5DA8, 0xFF0B0710, 0xFF150E1C, 0xFF0F0A15, 0xFF2A1B38, 0xFF9A8AA6, 0xFFFFFFFF),
        classic("Лёд", 0xFF3DD6FF, 0xFF060B10, 0xFF0C1620, 0xFF08111A, 0xFF1A2B3A, 0xFF8AA0AE, 0xFFFFFFFF),
        classic("Закат", 0xFFFF8A3D, 0xFF0E0806, 0xFF1A1009, 0xFF130B07, 0xFF33200F, 0xFFA8947F, 0xFFFFFFFF),
        classic("Аметист", 0xFFB07CFF, 0xFF0A0711, 0xFF140E20, 0xFF0E0918, 0xFF2B1F42, 0xFF9A8DB0, 0xFFFFFFFF),
        classic("Моно", 0xFFFFFFFF, 0xFF000000, 0xFF111111, 0xFF0A0A0A, 0xFF262626, 0xFF8C8C8C, 0xFFFFFFFF),
        classic("Золото", 0xFFFFD84D, 0xFF0B0903, 0xFF17130A, 0xFF100D06, 0xFF302811, 0xFFA89C78, 0xFFFFFFFF),
        classic("Светлая", 0xFF16A34A, 0xFFF2F4F3, 0xFFFFFFFF, 0xFFEAEDEB, 0xFFD3D8D5, 0xFF5F6B65, 0xFF0E1411)
    )

    var cur: SkinDef = BASE[0]
    var accent: Int = BASE[0].accent
    var radius = 1f
    var tf: Typeface = Typeface.DEFAULT

    private fun hsv(h: Float, s: Float, v: Float): Int = Color.HSVToColor(floatArrayOf(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))

    /** «Хамелеон»: цвета интерфейса подстраиваются под цвет обоев. */
    private fun chameleon(p: Prefs): SkinDef {
        val hv = FloatArray(3)
        Color.colorToHSV(p.colorInt, hv)
        val h = hv[0]; val s = max(hv[1], 0.25f)
        return SkinDef("Хамелеон", hsv(h, s, max(hv[2], 0.9f)), hsv(h, s * 0.5f, 0.06f), hsv(h, s * 0.45f, 0.13f), hsv(h, s * 0.45f, 0.09f),
            hsv(h, s * 0.4f, 0.28f), hsv(h, 0.18f, 0.7f), Color.WHITE, kind = 1, backdrop = 1, panelAlpha = 0.8f, radiusMul = 1.4f,
            accent2 = hsv((h + 40f) % 360f, s, 0.95f))
    }

    /** «Своя»: оттенок, насыщенность, яркость и прозрачность панелей задаёт пользователь. */
    private fun custom(p: Prefs): SkinDef {
        val h = p.customH; val s = p.customS; val v = p.customV
        return SkinDef("Своя", hsv(h, s, v), hsv(h, s * 0.5f, 0.05f), hsv(h, s * 0.4f, 0.11f), hsv(h, s * 0.4f, 0.08f),
            hsv(h, s * 0.35f, 0.26f), hsv(h, 0.15f, 0.7f), Color.WHITE, kind = if (p.customA < 0.95f) 1 else 0,
            panelAlpha = p.customA, radiusMul = 1.2f, accent2 = hsv((h + 60f) % 360f, s, v))
    }

    fun def(i: Int, p: Prefs): SkinDef = when {
        i in BASE.indices -> BASE[i]
        i == CHAMELEON -> chameleon(p)
        else -> custom(p)
    }

    fun load(p: Prefs) {
        // миграция: старые 8 оформлений теперь идут после новых
        if (!p.sp.contains("skinV2")) {
            p.skin = if (p.sp.contains("skin")) 14 + p.skin.coerceIn(0, 7) else 0
            p.sp.edit().putBoolean("skinV2", true).apply()
        }
        cur = def(p.skin.coerceIn(0, COUNT - 1), p)
        accent = if (p.accentCustom != 0) p.accentCustom else cur.accent
        radius = when (p.corners) { 0 -> 0.35f; 2 -> 1.7f; else -> 1f } * cur.radiusMul
        tf = when (cur.font) {
            1 -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            2 -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
            3 -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
            4 -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD_ITALIC)
            5 -> Typeface.create("sans-serif-black", Typeface.NORMAL)
            else -> Typeface.DEFAULT
        }
    }

    private fun lum(c: Int) = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f
    fun isLight(): Boolean = lum(cur.bg) > 0.6f
    fun onAccent(): Int = if (lum(accent) > 0.55f) Color.BLACK else Color.WHITE

    fun chipOn(): Int {
        val k = 0.2f; val a = cur.panel
        return Color.rgb((Color.red(a) + (Color.red(accent) - Color.red(a)) * k).toInt(),
            (Color.green(a) + (Color.green(accent) - Color.green(a)) * k).toInt(),
            (Color.blue(a) + (Color.blue(accent) - Color.blue(a)) * k).toInt())
    }
}
