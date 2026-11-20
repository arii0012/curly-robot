package com.codewall
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.min

class Wdg(var type: Int, var style: Int, var color: Int, var size: Float, var x: Float, var y: Float, var anim: Int, var text: String,
          var font: Int = 0, var op: Float = 1f, var plate: Int = 0, var rot: Float = 0f, var glow: Boolean = false,
          var fx: Int = 0, var color2: Int = 0xFFFF5DA8.toInt())

object Widgets {
    val TYPES = listOf("Часы", "Дата", "Батарея", "Текст", "Эквалайзер", "Пульс", "Приветствие", "Дни до даты", "Год",
        "Мировое время", "Цитата", "Неделя", "Бегущая строка")
    val STYLES = listOf(
        listOf("Крупные", "Неон", "Бинарные", "Аналоговые", "Секунды"), listOf("Короткая", "Полная", "Цифры", "Календарь"),
        listOf("Текст", "Кольцо", "Полоса"), listOf("Обычный", "Рамка", "Скобки"), listOf("Столбики", "Точки"), listOf("ЭКГ"),
        listOf("В строку", "Крупно"), listOf("Число", "Полоса"), listOf("Полоса", "Кольцо"), listOf("Компакт"), listOf("По центру"),
        listOf("Точки", "Буквы"), listOf("Обычная")
    )
    val ANIMS = listOf("Нет", "Пульс", "Глитч", "Печать", "Радуга", "Дыхание", "Покачивание", "Мерцание", "Парение")
    val FONTS = listOf("Моно", "Тонкий", "Жирный", "Serif", "Рукописный")
    val PLATES = listOf("Нет", "Заливка", "Рамка")
    val FX = listOf("Нет", "Контур", "Тень", "Градиент", "Эхо")
    val QUOTES = listOf("Делай, что можешь.", "Всё впереди.", "Меньше слов — больше кода.", "Будь проще.", "Не сдавайся.",
        "Stay curious.", "Keep it simple.", "Just ship it.", "Каждый день — новый коммит.", "Дыши. Всё получится.",
        "Сделано лучше, чем идеально.", "Тише едешь — дальше будешь.")
    const val MAX = 10

    fun make(type: Int, color: Int, slot: Int): Wdg {
        val w = Wdg(type, 0, color, 1f, 0.5f, (0.1f + 0.1f * slot).coerceAtMost(0.9f), 0, "")
        w.text = when (type) {
            3 -> "PoiPaper"
            7 -> LocalDate.now().plusDays(30).toString() + ";Событие"
            9 -> "Токио;9"
            12 -> "PoiPaper — живые обои из кода   "
            else -> ""
        }
        return w
    }

    fun fromJson(o: JSONObject): Wdg {
        val t = o.optInt("t", 0).coerceIn(0, TYPES.size - 1)
        return Wdg(t, o.optInt("s", 0).coerceIn(0, STYLES[t].size - 1), o.optInt("c", 0xFFFFFFFF.toInt()) or 0xFF000000.toInt(),
            o.optDouble("z", 1.0).toFloat().coerceIn(0.4f, 3f), o.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f),
            o.optDouble("y", 0.12).toFloat().coerceIn(0f, 1f), o.optInt("a", 0).coerceIn(0, ANIMS.size - 1),
            o.optString("tx", "").filter { it >= ' ' }.take(80),
            o.optInt("f", 0).coerceIn(0, FONTS.size - 1), o.optDouble("o", 1.0).toFloat().coerceIn(0.2f, 1f),
            o.optInt("pl", 0).coerceIn(0, 2), o.optDouble("r", 0.0).toFloat().coerceIn(-90f, 90f), o.optBoolean("g", false),
            o.optInt("fx", 0).coerceIn(0, FX.size - 1), o.optInt("c2", 0xFFFF5DA8.toInt()) or 0xFF000000.toInt())
    }

    fun toJson(w: Wdg): JSONObject {
        val o = JSONObject()
        o.put("t", w.type); o.put("s", w.style); o.put("c", w.color); o.put("z", w.size.toDouble())
        o.put("x", w.x.toDouble()); o.put("y", w.y.toDouble()); o.put("a", w.anim); o.put("tx", w.text)
        o.put("f", w.font); o.put("o", w.op.toDouble()); o.put("pl", w.plate); o.put("r", w.rot.toDouble()); o.put("g", w.glow)
        o.put("fx", w.fx); o.put("c2", w.color2)
        return o
    }

    fun parse(s: String): MutableList<Wdg> {
        val l = ArrayList<Wdg>()
        try {
            val a = JSONArray(s)
            for (i in 0 until min(a.length(), MAX)) l.add(fromJson(a.getJSONObject(i)))
        } catch (e: Exception) { }
        return l
    }

    fun load(p: Prefs): MutableList<Wdg> = parse(p.wJson)

    fun save(p: Prefs, l: List<Wdg>) {
        val a = JSONArray()
        for (w in l.take(MAX)) a.put(toJson(w))
        p.wJson = a.toString()
    }
}
