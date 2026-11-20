package com.codewall
import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.*

class Ui(private val a: Activity) {
    val G: Int get() = Skin.accent
    val DIM: Int get() = Skin.cur.dim
    val LINE: Int get() = Skin.cur.line
    val DARK: Int get() = Skin.cur.panel2
    val PANEL: Int get() = Skin.cur.panel
    val TXT: Int get() = Skin.cur.text
    val BG: Int get() = Skin.cur.bg
    val ON: Int get() = Skin.onAccent()

    fun dp(v: Int) = (v * a.resources.displayMetrics.density).toInt()
    fun toast(m: String) { Toast.makeText(a, m, Toast.LENGTH_SHORT).show() }
    fun hap(v: View) { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }

    fun bg(fill: Int, stroke: Int, rad: Int = 16): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill); g.setStroke(dp(1), stroke); g.cornerRadius = dp(rad).toFloat() * Skin.radius
        return g
    }

    fun card(root: LinearLayout, tag: String, title: String): LinearLayout {
        val c = LinearLayout(a)
        c.orientation = LinearLayout.VERTICAL
        c.background = bg(PANEL, LINE, 24)
        c.setPadding(dp(18), dp(14), dp(18), dp(16))
        if (tag.isNotEmpty()) {
            val t1 = TextView(a)
            t1.text = tag; t1.setTextColor(G); t1.textSize = 12f; t1.typeface = Typeface.MONOSPACE
            c.addView(t1)
        }
        val t2 = TextView(a)
        t2.text = title; t2.setTextColor(TXT); t2.textSize = 19f
        t2.setPadding(0, dp(3), 0, dp(10))
        c.addView(t2)
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.bottomMargin = dp(10)
        root.addView(c, lp)
        return c
    }

    fun note(c: LinearLayout, t: String): TextView {
        val tv = TextView(a)
        tv.text = t; tv.setTextColor(DIM); tv.textSize = 13f
        tv.setPadding(0, dp(8), 0, 0)
        c.addView(tv)
        return tv
    }

    fun btn(c: LinearLayout, t: String, accent: Boolean, click: () -> Unit) {
        val b = Button(a)
        b.text = t
        b.setTextColor(if (accent) ON else TXT)
        b.background = bg(if (accent) G else DARK, if (accent) G else LINE, 20)
        b.setOnClickListener { hap(b); click() }
        val lp = LinearLayout.LayoutParams(-1, dp(50))
        lp.bottomMargin = dp(8)
        c.addView(b, lp)
    }

    fun small(row: LinearLayout, t: String, click: () -> Unit) {
        val tv = TextView(a)
        tv.text = t; tv.setTextColor(G); tv.textSize = 12f; tv.gravity = Gravity.CENTER
        tv.setPadding(dp(10), dp(6), dp(10), dp(6))
        tv.setOnClickListener { hap(tv); click() }
        row.addView(tv)
    }

    /** Плитка-кнопка с глифом сверху. */
    fun tile(row: LinearLayout, glyph: String, label: String, primary: Boolean, click: () -> Unit) {
        val t = LinearLayout(a)
        t.orientation = LinearLayout.VERTICAL; t.gravity = Gravity.CENTER
        t.background = bg(if (primary) G else PANEL, if (primary) G else LINE, 20)
        val g = TextView(a)
        g.text = glyph; g.textSize = 24f; g.setTextColor(if (primary) ON else G); g.gravity = Gravity.CENTER
        val l = TextView(a)
        l.text = label; l.textSize = 12f; l.typeface = Typeface.DEFAULT_BOLD; l.setTextColor(if (primary) ON else TXT); l.gravity = Gravity.CENTER
        t.addView(g); t.addView(l)
        t.setOnClickListener { hap(t); click() }
        row.addView(t, LinearLayout.LayoutParams(0, dp(84), 1f).apply { marginEnd = dp(8) })
    }

    fun chips(c: LinearLayout, labels: List<String>, sel: Int, onSel: (Int) -> Unit) {
        val row = LinearLayout(a)
        val tvs = ArrayList<TextView>()
        fun paintAll(s: Int) {
            for (i in tvs.indices) {
                val on = i == s
                tvs[i].background = bg(if (on) Skin.chipOn() else DARK, if (on) G else LINE, 14)
                tvs[i].setTextColor(if (on) TXT else DIM)
            }
        }
        for (i in labels.indices) {
            val tv = TextView(a)
            tv.text = labels[i]
            tv.gravity = Gravity.CENTER
            tv.textSize = if (labels.size >= 5) 12f else if (labels.size == 4) 13f else 15f
            tv.setOnClickListener { hap(tv); paintAll(i); onSel(i) }
            tvs.add(tv)
            val lp = LinearLayout.LayoutParams(0, dp(46), 1f)
            lp.marginEnd = dp(6)
            row.addView(tv, lp)
        }
        paintAll(sel)
        val lp2 = LinearLayout.LayoutParams(-1, -2)
        lp2.bottomMargin = dp(6)
        c.addView(row, lp2)
    }

    /** Прокручиваемое меню разделов. */
    fun seg(c: LinearLayout, labels: List<String>, sel: Int, onSel: (Int) -> Unit) {
        val hs = HorizontalScrollView(a)
        hs.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(a)
        hs.addView(row)
        val tvs = ArrayList<TextView>()
        fun paintAll(s: Int) {
            for (i in tvs.indices) {
                val on = i == s
                tvs[i].background = bg(if (on) G else PANEL, if (on) G else LINE, 20)
                tvs[i].setTextColor(if (on) ON else TXT)
            }
        }
        for (i in labels.indices) {
            val tv = TextView(a)
            tv.text = labels[i]; tv.textSize = 14f; tv.gravity = Gravity.CENTER
            tv.setPadding(dp(16), 0, dp(16), 0)
            tv.setOnClickListener { hap(tv); paintAll(i); onSel(i) }
            tvs.add(tv)
            row.addView(tv, LinearLayout.LayoutParams(-2, dp(40)).apply { marginEnd = dp(6) })
        }
        paintAll(sel)
        c.addView(hs, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }

    /** Маленький переключатель-«таблетка» для полосы быстрых настроек. */
    fun pill(row: LinearLayout, label: String, v0: Boolean, onChange: (Boolean) -> Unit) {
        val tv = TextView(a)
        var on = v0
        fun paintIt() {
            tv.background = bg(if (on) Skin.chipOn() else PANEL, if (on) G else LINE, 20)
            tv.setTextColor(if (on) TXT else DIM)
        }
        tv.text = label; tv.textSize = 13f; tv.gravity = Gravity.CENTER
        tv.setPadding(dp(14), 0, dp(14), 0)
        paintIt()
        tv.setOnClickListener { hap(tv); on = !on; paintIt(); onChange(on) }
        row.addView(tv, LinearLayout.LayoutParams(-2, dp(36)).apply { marginEnd = dp(6) })
    }

    fun swatches(c: LinearLayout, colors: IntArray, sel: Int, onSel: (Int) -> Unit) {
        val row = LinearLayout(a)
        val views = ArrayList<View>()
        fun paintAll(s: Int) {
            for (i in views.indices) {
                val g = GradientDrawable()
                g.setShape(GradientDrawable.OVAL)
                g.setColor(colors[i])
                g.setStroke(dp(if (i == s) 3 else 1), if (i == s) TXT else LINE)
                views[i].background = g
            }
        }
        for (i in colors.indices) {
            val v = View(a)
            v.setOnClickListener { hap(v); paintAll(i); onSel(i) }
            views.add(v)
            val lp = LinearLayout.LayoutParams(0, dp(36), 1f)
            lp.marginEnd = dp(8)
            row.addView(v, lp)
        }
        paintAll(sel)
        val lp2 = LinearLayout.LayoutParams(-1, -2)
        lp2.bottomMargin = dp(10)
        c.addView(row, lp2)
    }

    fun slider(c: LinearLayout, label: String, lo: Float, hi: Float, v0: Float,
               fmt: (Float) -> String = { "%.1f×".format(it) }, onV: (Float) -> Unit) {
        val head = TextView(a)
        head.setTextColor(DIM)
        fun show(v: Float) { head.text = label + "    " + fmt(v) }
        show(v0)
        val sb = SeekBar(a)
        sb.max = 100
        sb.progress = ((v0 - lo) / (hi - lo) * 100).toInt()
        sb.progressTintList = ColorStateList.valueOf(G)
        sb.thumbTintList = ColorStateList.valueOf(G)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, pr: Int, u: Boolean) {
                val v = lo + (hi - lo) * pr / 100f
                show(v)
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

    fun toggle(c: LinearLayout, label: String, v0: Boolean, onChange: (Boolean) -> Unit) {
        val row = LinearLayout(a)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(4), 0, dp(4))
        val tv = TextView(a)
        tv.text = label; tv.setTextColor(TXT); tv.textSize = 15f
        val pill = TextView(a)
        pill.gravity = Gravity.CENTER; pill.textSize = 12f
        var on = v0
        fun paintPill() {
            pill.text = if (on) "ВКЛ" else "ВЫКЛ"
            pill.setTextColor(if (on) ON else DIM)
            pill.background = bg(if (on) G else DARK, if (on) G else LINE, 14)
        }
        paintPill()
        row.setOnClickListener { hap(row); on = !on; paintPill(); onChange(on) }
        row.addView(tv, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(pill, LinearLayout.LayoutParams(dp(60), dp(30)))
        c.addView(row)
    }

    fun field(c: LinearLayout, hint: String, initial: String, onChange: (String) -> Unit) {
        val et = EditText(a)
        et.hint = hint; et.setText(initial); et.setTextColor(TXT); et.setHintTextColor(DIM)
        et.setSingleLine(true); et.background = bg(DARK, LINE, 14); et.setPadding(dp(14), 0, dp(14), 0)
        et.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { onChange(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c1: Int, a1: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c1: Int) {}
        })
        val lp = LinearLayout.LayoutParams(-1, dp(48))
        lp.bottomMargin = dp(8)
        c.addView(et, lp)
    }

    fun prompt(title: String, hint: String, initial: String, ok: (String) -> Unit) {
        val et = EditText(a)
        et.hint = hint; et.setText(initial); et.setSingleLine(true)
        AlertDialog.Builder(a).setTitle(title).setView(et)
            .setPositiveButton("ОК") { _, _ -> ok(et.text.toString().trim()) }
            .setNegativeButton("Отмена", null).show()
    }

    fun showPoa(poa: Formats.Poa, p: Prefs, done: () -> Unit) {
        val items = Formats.items(poa)
        val labels = items.map { it.second }.toTypedArray()
        val checked = BooleanArray(items.size) { true }
        AlertDialog.Builder(a)
            .setTitle(poa.name + "\n" + poa.author + " · " + poa.category + (if (poa.signed) " · ✓" else " · без подписи"))
            .setMultiChoiceItems(labels, checked) { _, which, v -> checked[which] = v }
            .setPositiveButton("ПРИМЕНИТЬ") { _, _ ->
                val keys = HashSet<String>()
                for (k in items.indices) if (checked[k]) keys.add(items[k].first)
                p.saveBackup()
                Formats.applyPoa(p, a, poa, keys)
                toast("Применено")
                done()
            }
            .setNegativeButton("Отмена", null).show()
    }
}
