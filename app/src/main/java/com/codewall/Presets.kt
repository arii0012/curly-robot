package com.codewall
import android.content.Context
import org.json.JSONObject
import java.io.File

/** «Мои сборки»: снимки всех настроек обоев. Умеют автосменяться по расписанию. */
object Presets {
    class Item(val file: String, val name: String)

    private fun dir(ctx: Context): File { val d = File(ctx.filesDir, "presets"); d.mkdirs(); return d }

    fun list(ctx: Context): List<Item> =
        (dir(ctx).listFiles() ?: emptyArray<File>()).filter { it.name.endsWith(".json") }.sortedBy { it.lastModified() }
            .mapNotNull { f -> try { Item(f.name, JSONObject(f.readText()).getString("name")) } catch (e: Exception) { null } }

    fun save(ctx: Context, p: Prefs, name: String) {
        val o = JSONObject(); o.put("name", name.take(24)); o.put("snap", p.snapshot(Prefs.PRESET_SKIP))
        File(dir(ctx), "s" + System.currentTimeMillis() + ".json").writeText(o.toString())
    }

    fun snap(ctx: Context, file: String): String? =
        try { JSONObject(File(dir(ctx), File(file).name).readText()).getString("snap") } catch (e: Exception) { null }

    fun apply(ctx: Context, p: Prefs, file: String): Boolean { val s = snap(ctx, file) ?: return false; return p.restore(s) }

    fun delete(ctx: Context, file: String) { File(dir(ctx), File(file).name).delete() }

    fun maybeSwitch(ctx: Context, p: Prefs) {
        if (p.shuffle == 0) return
        val now = System.currentTimeMillis()
        val due = when (p.shuffle) { 1 -> true; 2 -> now - p.shuffleAt > 3600000L; else -> now - p.shuffleAt > 86400000L }
        if (!due) return
        val l = list(ctx)
        if (l.size < 2) return
        val idx = (p.shuffleIdx + 1) % l.size
        p.shuffleIdx = idx; p.shuffleAt = now
        apply(ctx, p, l[idx].file)
    }
}
