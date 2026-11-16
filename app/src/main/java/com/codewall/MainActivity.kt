package com.codewall

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import java.io.File

class MainActivity : Activity() {
    private lateinit var p: Prefs
    private lateinit var status: TextView
    private val G = Color.parseColor("#4DFF88")
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun bg(fill: Int, stroke: Int, r: Int = 16) = GradientDrawable().apply {
        setColor(fill); setStroke(dp(1), stroke); cornerRadius = dp(r).toFloat()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Prefs(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(40), dp(16), dp(24))
        }
        val sv = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#070B09"))
            addView(root)
        }
        setContentView(sv)

        card(root, "01 / СЦЕНА", "Какая фигура").let {
            chips(it, listOf("Встроенная", "Свой логотип", "Обе"), p.scene) { i -> p.scene = i }
        }
        card(root, "02 / ВРАЩЕНИЕ", "Скорость").let {
            slider(it, "скорость", 0.2f, 3f, p.speed) { v -> p.speed = v }
        }
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
                val pick = Intent(Intent.ACTION_OPEN_DOCUMENT)
                pick.addCategory(Intent.CATEGORY_OPENABLE)
                pick.type = "image/*"
                startActivityForResult(pick, 1)
            }
            btn(it, "ВЕРНУТЬ ВСТРОЕННЫЙ", false) {
                File(filesDir, "logo.png").delete()
                p.hasLogo = false
                p.scene = 0
                p.stamp = System.currentTimeMillis()
                refreshStatus()
            }
            btn(it, "ИНВЕРТИРОВАТЬ ФОРМУ", false) {
                p.invert = !p.invert
                refreshStatus()
            }
            status = note(it, "")
            refreshStatus()
        }
        card(root, "06 / ЦВЕТ", "Цвет символов").let {
            chips(it, listOf("Белый", "Зелёный", "Янтарный"), p.color) { i -> p.color = i }
        }

        val install = Button(this)
        install.text = "УСТАНОВИТЬ КАК ОБОИ"
        install.setTextColor(Color.BLACK)
        install.background = bg(G, G, 20)
        install.setOnClickListener {
            val i = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            i.putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this@MainActivity, CodeWallpaperService::class.java)
            )
            try {
                startActivity(i)
            } catch (e: Exception) {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            }
        }
        root.addView(install, LinearLayout.LayoutParams(-1, dp(60)))
    }

    override fun onActivityResult(rc: Int, res: Int, d: Intent?) {
        super.onActivityResult(rc, res, d)
        val uri = d?.data ?: return
        if (rc != 1 || res != RESULT_OK) return
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1
        while (o.outWidth / s > 1024 || o.outHeight / s > 1024) s *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = s }
        val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (bmp == null) {
            Toast.makeText(this, "Не удалось открыть изображение", Toast.LENGTH_SHORT).show()
            return
        }
        File(filesDir, "logo.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        p.hasLogo = true
        p.scene = 1
        p.stamp = System.currentTimeMillis()
        refreshStatus()
    }

    private fun refreshStatus() {
        val f = File(filesDir, "logo.png")
        if (p.hasLogo && f.exists()) {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, o)
            status.text = "Логотип загружен: ${Shapes.COLS}×${Shapes.rowsFor(o.outWidth, o.outHeight)} точек.\n" +
                "Подойдёт PNG с прозрачным фоном или знак на однотонном фоне."
        } else {
            status.text = "Используется встроенная фигура."
        }
    }

    private fun card(root: LinearLayout, tag: String, title: String): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = bg(Color.parseColor("#0E1511"), Color.parseColor("#1F2B24"), 24)
        c.setPadding(dp(18), dp(16), dp(18), dp(16))
        val t1 = TextView(this)
        t1.text = tag
        t1.setTextColor(G)
        t1.textSize = 12f
        c.addView(t1)
        val t2 = TextView(this)
        t2.text = title
        t2.setTextColor(Color.WHITE)
        t2.textSize = 20f
        t2.setPadding(0, dp(4), 0, dp(10))
        c.addView(t2)
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.bottomMargin = dp(12)
        root.addView(c, lp)
        return c
    }

    private fun note(c: LinearLayout, t: String): TextView {
        val tv = TextView(this)
        tv.text = t
        tv.setTextColor(Color.parseColor("#8A9A90"))
        tv.textSize = 13f
        tv.setPadding(0, dp(8), 0, 0)
        c.addView(tv)
        return tv
    }

    private fun btn(c: LinearLayout, t: String, accent: Boolean, click: () -> Unit) {
        val b = Button(this)
        b.text = t
        b.setTextColor(if (accent) G else Color.parseColor("#8A9A90"))
        b.background = bg(Color.parseColor("#0A100D"), if (accent) G else Color.parseColor("#1F2B24"), 20)
        b.setOnClickListener { click() }
        val lp = LinearLayout.LayoutParams(-1, dp(52))
        lp.bottomMargin = dp(8)
        c.addView(b, lp)
    }

    private fun chips(c: LinearLayout, labels: List<String>, sel: Int, onSel: (Int) -> Unit) {
        val row = LinearLayout(this)
        val tvs = ArrayList<TextView>()
        fun paintAll(s: Int) {
            for (i in tvs.indices) {
                val on = i == s
                tvs[i].background = bg(
                    if (on) Color.parseColor("#12301F") else Color.parseColor("#0A100D"),
                    if (on) G else Color.parseColor("#1F2B24"), 14
                )
                tvs[i].setTextColor(if (on) Color.WHITE else Color.parseColor("#8A9A90"))
            }
        }
        for (i in labels.indices) {
            val tv = TextView(this)
            tv.text = labels[i]
            tv.gravity = Gravity.CENTER
            tv.textSize = 15f
            tv.setOnClickListener { paintAll(i); onSel(i) }
            tvs.add(tv)
            val lp = LinearLayout.LayoutParams(0, dp(48), 1f)
            lp.marginEnd = dp(6)
            row.addView(tv, lp)
        }
        paintAll(sel)
        c.addView(row)
    }

    private fun slider(c: LinearLayout, label: String, lo: Float, hi: Float, v0: Float, onV: (Float) -> Unit) {
        val head = TextView(this)
        head.setTextColor(Color.parseColor("#8A9A90"))
        fun fmt(v: Float) { head.text = "$label    ${"%.1f".format(v)}×" }
        fmt(v0)
        val sb = SeekBar(this)
        sb.max = 100
        sb.progress = ((v0 - lo) / (hi - lo) * 100).toInt()
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, pr: Int, u: Boolean) {
                val v = lo + (hi - lo) * pr / 100f
                fmt(v)
                if (u) onV(v)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        c.addView(head)
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.bottomMargin = dp(8)
        c.addView(sb, lp)
    }
}
