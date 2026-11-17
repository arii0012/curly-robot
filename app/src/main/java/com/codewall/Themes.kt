package com.codewall

class Theme(val name: String, val desc: String, val color: Int, val rainbow: Boolean, val speed: Float,
            val depth: Float, val glow: Boolean, val bg: Int, val shape: String, val scene: Int = 0)

object Themes {
    val ALL = listOf(
        Theme("Чёрная дыра", "Диск из частиц", 0xFF3DD6FF.toInt(), false, 1f, 1f, true, 0, "b:0", 1),
        Theme("Подарок", "Сюрприз с мишкой", 0xFFFF5DA8.toInt(), false, 1f, 1f, true, 0, "b:0", 2),
        Theme("Сердце из слов", "Слова любви", 0xFFFF2D6F.toInt(), false, 1f, 1f, true, 0, "b:0", 3),
        Theme("Матрица", "Классика: зелёный код", 0xFF4DFF88.toInt(), false, 0.9f, 1f, true, 0, "b:0"),
        Theme("Киберпанк", "Розовый череп и сетка", 0xFFFF5DA8.toInt(), false, 1.1f, 1.2f, true, 2, "b:4"),
        Theme("Радуга", "Переливающийся шар", 0xFFFFFFFF.toInt(), true, 1f, 1.3f, true, 0, "b:1"),
        Theme("Терминал", "Янтарный курсор", 0xFFFFB000.toInt(), false, 0.7f, 0.8f, false, 1, "b:16"),
        Theme("Лёд", "Холодный алмаз", 0xFF3DD6FF.toInt(), false, 0.8f, 1.4f, true, 1, "b:8"),
        Theme("Сакура", "Нежное сердце", 0xFFFF8FC7.toInt(), false, 0.9f, 1.1f, true, 0, "b:2"),
        Theme("Огонь", "Быстрое пламя", 0xFFFF6A00.toInt(), false, 1.4f, 1f, true, 1, "b:12"),
        Theme("Призрак", "Белый призрак", 0xFFFFFFFF.toInt(), false, 0.7f, 0.7f, false, 1, "b:10"),
        Theme("Космос", "Фиолетовая планета", 0xFFB07CFF.toInt(), false, 0.6f, 1.2f, true, 2, "b:15"),
        Theme("Корона", "Золотая корона", 0xFFFFD84D.toInt(), false, 0.8f, 1f, true, 1, "b:5")
    )

    fun apply(p: Prefs, t: Theme) {
        p.colorInt = t.color; p.rainbow = t.rainbow; p.speed = t.speed; p.depth = t.depth
        p.glow = t.glow; p.bg = t.bg; p.shapeA = t.shape; p.scene = t.scene
        p.stamp = System.currentTimeMillis()
    }
}
