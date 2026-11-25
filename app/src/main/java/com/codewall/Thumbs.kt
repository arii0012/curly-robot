package com.codewall
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Миниатюры: один кадр сцены, нарисованный в картинку. Кэшируются. */
object Thumbs {
    private val cache = HashMap<String, Bitmap>()

    @Synchronized
    fun render(ctx: Context, key: String, w: Int, h: Int, t: Float, setup: (Prefs) -> Unit): Bitmap {
        cache[key]?.let { return it }
        val tp = Prefs(ctx, "thumb")
        tp.sp.edit().clear().commit()
        setup(tp)
        val r = Renderer(ctx, tp)
        r.reload()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        r.render(Canvas(bmp), w.toFloat(), h.toFloat(), t)
        cache[key] = bmp
        return bmp
    }

    fun timeFor(scene: Int) = when (scene) { 2 -> 9.5f; 3 -> 8f; else -> 3f }

    @Synchronized
    fun clear() { cache.clear() }
}

object Gallery {
    private fun cell(a: Activity, ui: Ui, name: String, sub: String, selected: Boolean): Triple<LinearLayout, ImageView, TextView> {
        val box = LinearLayout(a)
        box.orientation = LinearLayout.VERTICAL
        box.background = ui.bg(ui.PANEL, if (selected) ui.G else ui.LINE, 20)
        box.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
        val iv = ImageView(a)
        iv.scaleType = ImageView.ScaleType.CENTER_CROP
        iv.setBackgroundColor(Color.BLACK)
        iv.clipToOutline = true
        iv.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        iv.background = ui.bg(Color.BLACK, ui.LINE, 14)
        box.addView(iv, LinearLayout.LayoutParams(-1, ui.dp(190)))
        val t1 = TextView(a)
        t1.text = name; t1.setTextColor(ui.TXT); t1.textSize = 15f; t1.gravity = Gravity.CENTER; t1.setPadding(0, ui.dp(6), 0, 0)
        val t2 = TextView(a)
        t2.text = sub; t2.setTextColor(ui.DIM); t2.textSize = 11f; t2.gravity = Gravity.CENTER
        box.addView(t1); box.addView(t2)
        return Triple(box, iv, t1)
    }

    /** Сетка тем обоев 2 в ряд. onApplied(true) — сменилось оформление приложения. */
    fun themes(a: Activity, ui: Ui, parent: LinearLayout, p: Prefs, onApplied: (Boolean) -> Unit) {
        val views = HashMap<String, ImageView>()
        var i = 0
        while (i < Themes.ALL.size) {
            val row = LinearLayout(a)
            for (k in 0..1) {
                val idx = i + k
                if (idx >= Themes.ALL.size) { row.addView(android.view.View(a), LinearLayout.LayoutParams(0, 1, 1f)); continue }
                val t = Themes.ALL[idx]
                val (box, iv, _) = cell(a, ui, t.name, t.desc, false)
                views[t.name] = iv
                box.setOnClickListener { ui.hap(box); val changed = Themes.apply(p, t); ui.toast(t.name); onApplied(changed) }
                row.addView(box, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = ui.dp(8); bottomMargin = ui.dp(8) })
            }
            parent.addView(row)
            i += 2
        }
        Thread {
            for (t in Themes.ALL) {
                if (a.isFinishing) return@Thread
                try {
                    val bmp = Thumbs.render(a, "t:" + t.name, 200, 360, Thumbs.timeFor(t.scene)) { tp -> Themes.apply(tp, t) }
                    a.runOnUiThread { views[t.name]?.setImageBitmap(bmp) }
                } catch (e: Exception) { }
            }
        }.start()
    }

    /** Сцены с миниатюрами, по две в ряд. */
    fun scenes(a: Activity, ui: Ui, parent: LinearLayout, p: Prefs, onPick: () -> Unit) {
        val views = HashMap<Int, ImageView>()
        val n = Formats.SCENES.size
        var i = 0
        while (i < n) {
            val row = LinearLayout(a)
            for (k in 0..1) {
                val s = i + k
                if (s >= n) { row.addView(android.view.View(a), LinearLayout.LayoutParams(0, 1, 1f)); continue }
                val (box, iv, _) = cell(a, ui, Formats.SCENES[s], "", p.scene == s)
                views[s] = iv
                box.setOnClickListener { ui.hap(box); p.scene = s; onPick() }
                row.addView(box, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = ui.dp(8); bottomMargin = ui.dp(8) })
            }
            parent.addView(row)
            i += 2
        }
        Thread {
            for (s in 0 until n) {
                if (a.isFinishing) return@Thread
                try {
                    val bmp = Thumbs.render(a, "s:$s", 200, 360, Thumbs.timeFor(s)) { tp ->
                        tp.scene = s
                        tp.colorInt = when (s) { 1 -> 0xFF3DD6FF.toInt(); 2 -> 0xFFFF5DA8.toInt(); 3 -> 0xFFFF2D6F.toInt(); 4 -> 0xFF3DD6FF.toInt(); 5 -> 0xFFB07CFF.toInt(); 7 -> 0xFF7FD6FF.toInt(); else -> 0xFF4DFF88.toInt() }
                    }
                    a.runOnUiThread { views[s]?.setImageBitmap(bmp) }
                } catch (e: Exception) { }
            }
        }.start()
    }
}
