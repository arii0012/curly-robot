package com.codewall
import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*

class Ui(private val a: Activity) {
    val G = Color.parseColor("#4DFF88")
    val DIM = Color.parseColor("#8A9A90")
    val LINE = Color.parseColor("#1F2B24")
    val DARK = Color.parseColor("#0A100D")

    fun dp(v: Int) = (v * a.resources.displayMetrics.density).toInt()
    fun toast(m: String) { Toast.makeText(a, m, Toast.LENGTH_SHORT).show() }

    fun bg(fill: Int, stroke: Int, rad: Int = 16): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill); g.setStroke(dp(1), stroke); g.cornerRadius = dp(rad).toFloat()
        return g
    }

    fun card(root: LinearLayout, tag: String, title: String): LinearLayout {
        val c = LinearLayout(a)
        c.orientation = LinearLayout.VERTICAL
        c.background = bg(Color.parseColor("#0E1511"), LINE, 24)
        c.setPadding(dp(18), dp(14), dp(18), dp(16))
        val t1 = TextView(a)
        t1.text = tag; t1.setTextColor(G); t1.textSize = 12f; t1.typeface = Typeface.MONOSPACE
        c.addView(t1)
        val t2 = TextView(a)
        t2.text = title; t2.setTextColor(Color.WHITE); t2.textSize = 19f
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
        b.setTextColor(if (accent) G else DIM)
        b.background = bg(DARK, if (accent) G else LINE, 20)
        b.setOnClickListener { click() }
        val lp = LinearLayout.LayoutParams(-1, dp(50))
        lp.bottomMargin = dp(8)
        c.addView(b, lp)
    }

    fun small(row: LinearLayout, t: String, click: () -> Unit) {
        val tv = TextView(a)
        tv.text = t; tv.setTextColor(G); tv.textSize = 12f; tv.gravity = Gravity.CENTER
        tv.setPadding(dp(10), dp(6), dp(10), dp(6))
        tv.setOnClickListener { click() }
        row.addView(tv)
    }

    fun chips(c: LinearLayout, labels: List<String>, sel: Int, onSel: (Int) -> Unit) {
        val row = LinearLayout(a)
        val tvs = ArrayList<TextView>()
        fun paintAll(s: Int) {
            for (i in tvs.indices) {
                val on = i == s
                tvs[i].background = bg(if (on) Color.parseColor("#12301F") else DARK, if (on) G else LINE, 14)
                tvs[i].setTextColor(if (on) Color.WHITE else DIM)
            }
        }
        for (i in labels.indices) {
            val tv = TextView(a)
            tv.text = labels[i]
            tv.gravity = Gravity.CENTER
            tv.textSize = if (labels.size >= 5) 12f else if (labels.size == 4) 13f else 15f
            tv.setOnClickListener { paintAll(i); onSel(i) }
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

    fun swatches(c: LinearLayout, colors: IntArray, sel: Int, onSel: (Int) -> Unit) {
        val row = LinearLayout(a)
        val views = ArrayList<View>()
        fun paintAll(s: Int) {
            for (i in views.indices) {
                val g = GradientDrawable()
                g.setShape(GradientDrawable.OVAL)
                g.setColor(colors[i])
                g.setStroke(dp(if (i == s) 3 else 1), if (i == s) Color.WHITE else LINE)
                views[i].background = g
            }
        }
        for (i in colors.indices) {
            val v = View(a)
            v.setOnClickListener { paintAll(i); onSel(i) }
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
        tv.text = label; tv.setTextColor(Color.WHITE); tv.textSize = 15f
        val pill = TextView(a)
        pill.gravity = Gravity.CENTER; pill.textSize = 12f
        var on = v0
        fun paintPill() {
            pill.text = if (on) "ВКЛ" else "ВЫКЛ"
            pill.setTextColor(if (on) Color.BLACK else DIM)
            pill.background = bg(if (on) G else DARK, if (on) G else LINE, 14)
        }
        paintPill()
        row.setOnClickListener { on = !on; paintPill(); onChange(on) }
        row.addView(tv, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(pill, LinearLayout.LayoutParams(dp(60), dp(30)))
        c.addView(row)
    }

    fun field(c: LinearLayout, hint: String, initial: String, onChange: (String) -> Unit) {
        val et = EditText(a)
        et.hint = hint; et.setText(initial); et.setTextColor(Color.WHITE); et.setHintTextColor(DIM)
        et.setSingleLine(true); et.background = bg(DARK, LINE, 14); et.setPadding(dp(14), 0, dp(14), 0)
        et.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { onChange(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c1: Int, a1: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c1: Int) {}
        })
        c.addView(et, LinearLayout.LayoutParams(-1, dp(48)))
    }

    fun prompt(title: String, hint: String, initial: String, ok: (String) -> Unit) {
        val et = EditText(a)
        et.hint = hint; et.setText(initial); et.setSingleLine(true)
        AlertDialog.Builder(a).setTitle(title).setView(et)
            .setPositiveButton("ОК") { _, _ -> ok(et.text.toString().trim()) }
            .setNegativeButton("Отмена", null).show()
    }
}
