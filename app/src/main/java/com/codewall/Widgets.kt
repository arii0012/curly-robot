package com.codewall
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min

class Wdg(var type: Int, var style: Int, var color: Int, var size: Float, var x: Float, var y: Float, var anim: Int, var text: String)

object Widgets {
    val TYPES = listOf("Часы", "Дата", "Батарея", "Текст", "Эквалайзер", "Пульс")
    val STYLES = listOf(
        listOf("Моно", "Тонкие", "Неон", "Бинарные"), listOf("Короткая", "Полная", "Цифры"),
        listOf("Текст", "Кольцо", "Полоса"), listOf("Обычный", "Рамка", "Скобки"),
        listOf("Столбики", "Точки"), listOf("ЭКГ")
    )
    val ANIMS = listOf("Нет", "Пульс", "Глитч", "Печать", "Радуга")
    const val MAX = 8

    fun make(type: Int, color: Int, slot: Int): Wdg =
        Wdg(type, 0, color, 1f, 0.5f, (0.1f + 0.1f * slot).coerceAtMost(0.9f), 0, if (type == 3) "PoiPaper" else "")

    fun fromJson(o: JSONObject): Wdg {
        val t = o.optInt("t", 0).coerceIn(0, TYPES.size - 1)
        return Wdg(t, o.optInt("s", 0).coerceIn(0, STYLES[t].size - 1), o.optInt("c", 0xFFFFFFFF.toInt()) or 0xFF000000.toInt(),
            o.optDouble("z", 1.0).toFloat().coerceIn(0.4f, 3f), o.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f),
            o.optDouble("y", 0.12).toFloat().coerceIn(0f, 1f), o.optInt("a", 0).coerceIn(0, ANIMS.size - 1),
            o.optString("tx", "").filter { it >= ' ' }.take(24))
    }

    fun toJson(w: Wdg): JSONObject {
        val o = JSONObject()
        o.put("t", w.type); o.put("s", w.style); o.put("c", w.color); o.put("z", w.size.toDouble())
        o.put("x", w.x.toDouble()); o.put("y", w.y.toDouble()); o.put("a", w.anim); o.put("tx", w.text)
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
