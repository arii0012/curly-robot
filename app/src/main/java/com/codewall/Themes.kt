package com.codewall

class Theme(val name: String, val desc: String, val color: Int, val rainbow: Boolean, val speed: Float,
            val depth: Float, val glow: Boolean, val bg: Int, val shape: String, val scene: Int = 0, val skin: Int = -1,
            val pair: Boolean = false, val lockScene: Int = -1, val lockScale: Float = 0.45f, val homeScale: Float = 1.3f, val variant: Int = 0)

object Themes {
    // skin: 0 Матрица, 1 Неон, 2 Лёд, 3 Закат, 4 Аметист, 5 Моно, 6 Золото, 7 Светлая
    val ALL = listOf(
        Theme("Планета: приближение", "Блокировка → стол", 0xFF3DD6FF.toInt(), false, 1f, 1f, true, 3, "b:0", 4, 2, true, -1, 0.42f, 1.45f),
        Theme("Дыра: падение", "Дыра приближается", 0xFF3DD6FF.toInt(), false, 1f, 1f, true, 3, "b:0", 1, 2, true, -1, 0.5f, 1.7f),
        Theme("Сюрприз", "Подарок → сердце", 0xFFFF2D6F.toInt(), false, 1f, 1f, true, 0, "b:0", 3, 1, true, 2, 1f, 1f),
        Theme("Сатурн", "Кольца и луна", 0xFFFFD84D.toInt(), false, 0.8f, 1f, true, 3, "b:0", 4, 6, variant = 4),
        Theme("Марс", "Красная планета", 0xFFFF6A00.toInt(), false, 0.8f, 1f, true, 3, "b:0", 4, 3, variant = 5),
        Theme("Галактика", "Спираль из частиц", 0xFFB07CFF.toInt(), false, 0.8f, 1f, true, 3, "b:0", 5, 4),
        Theme("Вихрь", "Тугая галактика", 0xFF3DD6FF.toInt(), false, 1f, 1f, true, 3, "b:0", 5, 2, variant = 4),
        Theme("Дождь кода", "Падающие символы", 0xFF4DFF88.toInt(), false, 1f, 1f, true, 0, "b:0", 6, 0),
        Theme("Сердечный дождь", "Падают сердечки", 0xFFFF5DA8.toInt(), false, 0.8f, 1f, true, 0, "b:0", 6, 1, variant = 3),
        Theme("Кольцо слов", "Слова по кругу", 0xFF3DD6FF.toInt(), false, 1f, 1f, true, 0, "b:0", 3, 2, variant = 2),
        Theme("Планета", "Частицы и кольцо", 0xFF3DD6FF.toInt(), false, 1f, 1f, true, 3, "b:0", 4, 2),
        Theme("Чёрная дыра", "Диск из частиц", 0xFF3DD6FF.toInt(), false, 1f, 1f, true, 3, "b:0", 1, 2),
        Theme("Подарок", "Сюрприз с мишкой", 0xFFFF5DA8.toInt(), false, 1f, 1f, true, 0, "b:0", 2, 1),
        Theme("Сердце из слов", "Слова любви", 0xFFFF2D6F.toInt(), false, 1f, 1f, true, 0, "b:0", 3, 1),
        Theme("Матрица", "Классика: зелёный код", 0xFF4DFF88.toInt(), false, 0.9f, 1f, true, 4, "b:0", 0, 0),
        Theme("Киберпанк", "Розовый череп и сетка", 0xFFFF5DA8.toInt(), false, 1.1f, 1.2f, true, 2, "b:4", 0, 1),
        Theme("Радуга", "Переливающийся шар", 0xFFFFFFFF.toInt(), true, 1f, 1.3f, true, 3, "b:1"),
        Theme("Терминал", "Янтарный курсор", 0xFFFFB000.toInt(), false, 0.7f, 0.8f, false, 1, "b:16", 0, 3),
        Theme("Лёд", "Холодный алмаз", 0xFF3DD6FF.toInt(), false, 0.8f, 1.4f, true, 3, "b:8", 0, 2),
        Theme("Сакура", "Нежное сердце", 0xFFFF8FC7.toInt(), false, 0.9f, 1.1f, true, 0, "b:2", 0, 1),
        Theme("Огонь", "Быстрое пламя", 0xFFFF6A00.toInt(), false, 1.4f, 1f, true, 1, "b:12", 0, 3),
        Theme("Призрак", "Белый призрак", 0xFFFFFFFF.toInt(), false, 0.7f, 0.7f, false, 3, "b:10", 0, 5),
        Theme("Космос", "Фиолетовая планета", 0xFFB07CFF.toInt(), false, 0.6f, 1.2f, true, 3, "b:15", 0, 4),
        Theme("Корона", "Золотая корона", 0xFFFFD84D.toInt(), false, 0.8f, 1f, true, 1, "b:5", 0, 6)
    )

    /** Возвращает true, если поменялось оформление приложения (нужно перезапустить экран). */
    fun apply(p: Prefs, t: Theme): Boolean {
        p.colorInt = t.color; p.rainbow = t.rainbow; p.speed = t.speed; p.depth = t.depth
        p.glow = t.glow; p.bg = t.bg; p.shapeA = t.shape; p.scene = t.scene
        when (t.scene) { 3 -> p.heartShape = t.variant; 4 -> p.planetStyle = t.variant; 5 -> p.galVar = t.variant; 6 -> p.mxVar = t.variant }
        val wasPair = p.pair
        p.pair = t.pair
        if (t.pair) {
            p.lockScene = t.lockScene; p.lockScale = t.lockScale; p.scale = t.homeScale
            p.lockX = 0.5f; p.posX = 0.5f; p.lockY = 0.34f; p.posY = 0.52f
            p.lockSpeed = 1.6f; p.pairDur = 1.4f; p.pairEase = 1; p.lockBright = 1f; p.lockWidgets = true
        } else if (wasPair) { p.scale = 1f; p.posX = 0.5f; p.posY = 0.46f }
        p.stamp = System.currentTimeMillis()
        if (p.skinWithTheme && t.skin >= 0 && (p.skin != t.skin || p.accentCustom != 0)) {
            p.skin = t.skin; p.accentCustom = 0
            return true
        }
        return false
    }
}
