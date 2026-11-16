package com.codewall
import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.io.File

class MainActivity : Activity() {
    private lateinit var p: Prefs
    private lateinit var status: TextView
    private val G = Color.parseColor("#4DFF88")
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun bg(fill: Int, stroke: Int, r: Int = 16) = GradientDrawable().apply { setColor(fill); setStroke(dp(1), stroke); cornerRadius = dp(r).toFloat() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b); p = Prefs(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(40), dp(16), dp(24)) }
        val sv = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#070B09")); addView(root) }
        setContentView(sv)

        card(root, "01 / СЦЕНА", "Какая фигура").let {
            chips(it, listOf("Встроенная", "Свой логотип", "Обе"), p.scene) { i -> p.scene = i }
        }
        card(root, "02 / ВРАЩЕНИЕ", "Скорость").let { slider(it, "скорость", 0.2f, 3f, p.speed) { v -> p.speed = v } }
        card(root, "03 / ПЛАВНОСТЬ", "Частота кадров").let {
            val f = listOf(30, 45, 60, 90, 120)
            chips(it, f.map { x -> "$x" }, f.indexOf(p.fps).coerceAtLeast(2)) { i -> p.fps = f[i] }
            note(it, "Выше частота — плавнее, но больше расход батареи. 60 FPS — баланс.")
        }
        card(root, "04 / ЛИНИИ", "Толщина и масштаб").let {
            slider(it, "толщина линий", 0.5f, 3f, p.lineW) { v -> p.lineW = v }
            slider(it, "масштаб фигур", 0.4f, 1.5f, p.scale) { v -> p.scale = v }
        }
        card(root, "05 / СВОЁ ФОТО", "Форма для сцены «Свой логотип»").let {
            btn(it, "ВЫБРАТЬ ФОТО / ЛОГОТИП", true) {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*" }, 1)
            }
            btn(it, "ВЕРНУТЬ ВСТРОЕННЫЙ", false) { File(filesDir, "logo.png").delete(); p.hasLogo = false; p.scene = 0; p.stamp = System.currentTimeMillis(); refreshStatus() }
            btn(it, "ИНВЕРТИРОВАТЬ ФОРМУ", false) { p.invert = !p.invert; refreshStatus() }
            status = note(it, ""); refreshStatus()
        }
        card(root, "06 / ЦВЕТ", "Цвет символов").let {
            chips(it, listOf("Белый", "Зелёный", "Янтарный"), p.color) { i -> p.color = i }
        }
        Button(this).apply {
            text = "УСТАНОВИТЬ КАК ОБОИ"; setTextColor(Color.BLACK); background = bg(G, G, 20)
            setOnClickListener {
                val i = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this@MainActivity, CodeWallpaperService::class.java))
                try { startActivity(i) } catch (e: Exception) { startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
            }
            root.addView(this, LinearLayout.LayoutParams(-1, dp(60)))
        }
    }

    override fun onActivityResult(rc: Int, res: Int, d: Intent?) {
        super.onActivityResult(rc, res, d)
        val uri = d?.data ?: return
        if (rc != 1 || res != RESULT_OK) return
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1; while (o.outWidth / s > 1024 || o.outHeight / s > 1024) s *= 2
        val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
        if (bmp == null) { Toast.makeText(this, "Не удалось открыть изображение", Toast.LENGTH_SHORT).show(); return }
        File(filesDir, "logo.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        p.hasLogo = true; p.scene = 1; p.stamp = System.currentTimeMillis(); refreshStatus()
    }

    private fun refreshStatus() {
        val f = File(filesDir, "logo.png")
        status.text = if (p.hasLogo && f.exists()) {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(f.path, o)
            "Логотип загружен: ${Shapes.COLS}×${Shapes.rowsFor(o.outWidth, o.outHeight)} точек.\nПодойдёт PNG с прозрачным фоном или знак на однотонном фоне."
        } else "Используется встроенная фигура."
    }

    private fun card(root: LinearLayout, tag: String, title: String): LinearLayout {
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = bg(Color.parseColor("#0E1511"), Color.parseColor("#1F2B24"), 24); setPadding(dp(18), dp(16), dp(18), dp(16)) }
        c.addView(TextView(this).apply { text = tag; setTextColor(G); textSize = 12f })
        c.addView(TextView(this).apply { text = title; setTextColor(Color.WHITE); textSize = 20f; setPadding(0, dp(4), 0, dp(10)) })
        root.addView(c, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        return c
    }
    private fun note(c: LinearLayout, t: String) = TextView(this).apply { text = t; setTextColor(Color.parseColor("#8A9A90")); textSize = 13f; setPadding(0, dp(8), 0, 0); c.addView(this) }
    private fun btn(c: LinearLayout, t: String, accent: Boolean, click: () -> Unit) = Button(this).apply {
        text = t; setTextColor(if (accent) G else Color.parseColor("#8A9A90")); background = bg(Color.parseColor("#0A100D"), if (accent) G else Color.parseColor("#1F2B24"), 20)
        setOnClickListener { click() }; c.addView(this, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(8) })
    }
    private fun chips(c: LinearLayout, labels: List<String>, sel: Int, onSel: (Int) -> Unit) {
        val row = LinearLayout(this); val tvs = ArrayList<TextView>()
        fun paintAll(s: Int) = tvs.forEachIndexed { i, tv ->
            tv.background = bg(if (i == s) Color.parseColor("#12301F") else Color.parseColor("#0A100D"), if (i == s) G else Color.parseColor("#1F2B24"), 14)
            tv.setTextColor(if (i == s) Color.WHITE else Color.parseColor("#8A9A90"))
        }
        labels.forEachIndexed { i, l ->
            val tv = TextView(this).apply { text = l; gravity = Gravity.CENTER; textSize = 15f; setOnClickListener { paintAll(i); onSel(i) } }
            tvs.add(tv); row.addView(tv, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        }
        paintAll(sel); c.addView(row)
    }
    private fun slider(c: LinearLayout, label: String, min: Float, max: Float, v0: Float, onV: (Float) -> Unit) {
        val head = TextView(this).apply { setTextColor(Color.parseColor("#8A9A90")) }
        fun fmt(v: Float) { head.text = "$label    ${"%.1f".format(v)}×" }
        fmt(v0)
        val sb = SeekBar(this).apply {
            max = 100; progress = ((v0 - min) / (max - min) * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, pr: Int, u: Boolean) { val v = min + (max - min) * pr / 100f; fmt(v); if (u) onV(v) }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        c.addView(head); c.addView(sb, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }
}
