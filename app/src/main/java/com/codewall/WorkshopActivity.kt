package com.codewall
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.*

class WorkshopActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var p: Prefs
    private lateinit var acc: Account
    private lateinit var filters: LinearLayout
    private lateinit var list: LinearLayout
    private lateinit var accBtn: TextView
    private var cat = ""
    private var sort = 0
    private var query = ""
    private val cats = listOf("Все") + Formats.CATEGORIES

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        ui = Ui(this); p = Prefs(this); acc = Account(this, p, ui)
        val bgc = Color.parseColor("#070B09")
        window.statusBarColor = bgc; window.navigationBarColor = bgc

        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(bgc)
        page.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(12))

        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        val back = TextView(this)
        back.text = "←"; back.setTextColor(Color.WHITE); back.textSize = 26f
        back.setPadding(0, 0, ui.dp(14), 0)
        back.setOnClickListener { finish() }
        head.addView(back)
        val title = TextView(this)
        title.text = "WORKSHOP"; title.setTextColor(ui.G); title.textSize = 18f; title.typeface = Typeface.MONOSPACE
        head.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        accBtn = TextView(this)
        accBtn.textSize = 13f; accBtn.setTextColor(ui.G); accBtn.setPadding(ui.dp(10), ui.dp(6), ui.dp(10), ui.dp(6))
        accBtn.setOnClickListener { if (acc.loggedIn()) confirmLogout() else acc.loginDialog { refreshAccount(); load() } }
        head.addView(accBtn)
        page.addView(head, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(8) })

        if (!Config.ok) {
            val c = LinearLayout(this)
            c.orientation = LinearLayout.VERTICAL
            ui.card(c, "СЕРВЕР НЕ ПОДКЛЮЧЁН", "Нужна настройка Supabase").let {
                ui.note(it, "Добавь в GitHub → Settings → Secrets → Actions два секрета: SUPABASE_URL и SUPABASE_KEY, затем пересобери приложение.")
            }
            page.addView(c)
            setContentView(page)
            return
        }

        filters = LinearLayout(this)
        filters.orientation = LinearLayout.VERTICAL
        page.addView(filters)
        buildFilters()

        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(list)
        page.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))

        val pub = Button(this)
        pub.text = "ОПУБЛИКОВАТЬ МОИ ОБОИ"; pub.setTextColor(Color.BLACK); pub.background = ui.bg(ui.G, ui.G, 20)
        pub.setOnClickListener { publish() }
        page.addView(pub, LinearLayout.LayoutParams(-1, ui.dp(54)).apply { topMargin = ui.dp(8) })
        setContentView(page)

        acc.handleLink(intent)
        refreshAccount()
        load()
    }

    override fun onNewIntent(i: Intent?) {
        super.onNewIntent(i)
        setIntent(i)
        if (acc.handleLink(i)) { ui.toast("Вы вошли через Google"); refreshAccount(); if (Config.ok) load() }
    }

    private fun refreshAccount() {
        accBtn.text = if (acc.loggedIn()) "● " + p.email.substringBefore('@').take(14) else "ВОЙТИ"
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this).setTitle(p.email).setPositiveButton("ВЫЙТИ") { _, _ -> acc.logout(); refreshAccount(); load() }
            .setNegativeButton("Отмена", null).show()
    }

    private fun buildFilters() {
        filters.removeAllViews()
        val cur = if (cat.isEmpty()) "Все" else cat
        for (k in 0 until cats.size step 3) {
            val part = cats.subList(k, minOf(k + 3, cats.size))
            val sel = part.indexOf(cur)
            ui.chips(filters, part, sel) { i -> cat = if (part[i] == "Все") "" else part[i]; buildFilters(); load() }
        }
        ui.chips(filters, listOf("Новые", "Популярные", "Мои"), sort) { i -> sort = i; load() }
        ui.field(filters, "Поиск по названию", query) { s -> query = s.trim() }
        ui.btn(filters, "НАЙТИ", false) { load() }
    }

    private fun load() {
        if (!Config.ok) return
        list.removeAllViews()
        ui.note(list, "Загрузка…")
        Thread {
            try {
                val r = acc.api.list(cat, query, sort)
                runOnUiThread { show(r) }
            } catch (e: Api.Err) { runOnUiThread { list.removeAllViews(); ui.note(list, e.message ?: "Ошибка") } }
            catch (e: Exception) { runOnUiThread { list.removeAllViews(); ui.note(list, "Нет соединения с сервером") } }
        }.start()
    }

    private fun show(r: List<Api.PackInfo>) {
        list.removeAllViews()
        if (r.isEmpty()) { ui.note(list, "Здесь пока пусто. Опубликуй первым!"); return }
        for (pk in r) {
            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.background = ui.bg(Color.parseColor("#0E1511"), ui.LINE, 20)
            card.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12))
            val t1 = TextView(this)
            t1.text = pk.title; t1.setTextColor(Color.WHITE); t1.textSize = 17f
            val t2 = TextView(this)
            t2.text = pk.author + " · " + pk.category + " · ⬇ " + pk.downloads
            t2.setTextColor(ui.DIM); t2.textSize = 12f
            card.addView(t1); card.addView(t2)
            if (pk.desc.isNotEmpty()) {
                val t3 = TextView(this)
                t3.text = pk.desc; t3.setTextColor(ui.DIM); t3.textSize = 13f; t3.setPadding(0, ui.dp(4), 0, 0)
                card.addView(t3)
            }
            card.setOnClickListener { open(pk) }
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(8) })
        }
    }

    private fun reject(m: String?) {
        AlertDialog.Builder(this).setTitle("Работа отклонена").setMessage(m ?: "Файл не прошёл проверку").setPositiveButton("ОК", null).show()
    }

    private fun open(pk: Api.PackInfo) {
        ui.toast("Загружаю…")
        Thread {
            try {
                val text = acc.api.fetch(pk.id)
                runOnUiThread { preview(pk, text) }
            } catch (e: Api.Err) { runOnUiThread { ui.toast(e.message ?: "Ошибка") } }
            catch (e: Exception) { runOnUiThread { ui.toast("Нет соединения") } }
        }.start()
    }

    private fun preview(pk: Api.PackInfo, text: String) {
        val poa = try { Formats.parsePoa(text) } catch (e: Formats.Invalid) { reject(e.message); return }
        val tp = Prefs(this, "preview")
        tp.sp.edit().clear().apply()
        Formats.applyPoa(tp, this, poa, Formats.items(poa).map { it.first }.toSet(), false)
        val r = Renderer(this, tp)
        r.reload()
        val pv = PreviewView(this, r, tp)
        val dm = resources.displayMetrics
        val ph = (dm.heightPixels * 0.5f).toInt()
        val pw = (ph * dm.widthPixels.toFloat() / dm.heightPixels).toInt()
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), 0)
        box.addView(pv, LinearLayout.LayoutParams(pw, ph))
        val info = TextView(this)
        info.text = pk.title + "\n" + pk.author + " · " + pk.category + " · ⬇ " + pk.downloads + (if (poa.signed) " · ✓ подпись" else "")
        info.gravity = Gravity.CENTER; info.setPadding(0, ui.dp(8), 0, 0)
        box.addView(info)
        val d = AlertDialog.Builder(this).setView(box)
            .setPositiveButton("УСТАНОВИТЬ") { _, _ -> install(pk, poa) }
            .setNegativeButton("Закрыть", null)
        if (p.uid.isNotEmpty() && pk.uid == p.uid) d.setNeutralButton("УДАЛИТЬ") { _, _ -> removeMine(pk) }
        else d.setNeutralButton("ПОЖАЛОВАТЬСЯ") { _, _ -> complain(pk) }
        d.show()
    }

    private fun install(pk: Api.PackInfo, poa: Formats.Poa) {
        ui.showPoa(poa, p) { Thread { try { acc.api.countDownload(pk.id) } catch (e: Exception) { } }.start() }
    }

    private fun removeMine(pk: Api.PackInfo) {
        Thread {
            val m = try { acc.api.delete(pk.id); "Удалено" } catch (e: Api.Err) { e.message ?: "Ошибка" } catch (e: Exception) { "Нет соединения" }
            runOnUiThread { ui.toast(m); load() }
        }.start()
    }

    private fun complain(pk: Api.PackInfo) {
        ui.prompt("Причина жалобы", "например: обман или спам", "") { reason ->
            Thread {
                val m = try { acc.api.report(pk.id, reason.ifEmpty { "без причины" }); "Жалоба отправлена" } catch (e: Api.Err) { e.message ?: "Ошибка" } catch (e: Exception) { "Нет соединения" }
                runOnUiThread { ui.toast(m) }
            }.start()
        }
    }

    private fun publish() {
        if (!acc.loggedIn()) { acc.loginDialog { refreshAccount() }; return }
        ui.prompt("Название работы", "например: Неоновая чёрная дыра", "") { name ->
            if (name.isNotEmpty()) ui.prompt("Описание", "необязательно, до 120 символов", "") { desc ->
                AlertDialog.Builder(this).setTitle("Категория").setItems(Formats.CATEGORIES.toTypedArray()) { _, w -> doPublish(name, desc, Formats.CATEGORIES[w]) }.show()
            }
        }
    }

    private fun doPublish(name: String, desc: String, cat: String) {
        val m = Shapes.resolve(this, p.shapeA)
        val text = Formats.buildPoa(p, Shapes.nameOf(p.shapeA), m, name, p.author, cat, desc)
        try { Formats.parsePoa(text) } catch (e: Formats.Invalid) { reject(e.message); return }
        Thread {
            val msg = try { acc.api.upload(name.take(32), cat, p.author, desc.take(120), text); "Опубликовано!" } catch (e: Api.Err) { e.message ?: "Ошибка" } catch (e: Exception) { "Нет соединения" }
            runOnUiThread { ui.toast(msg); load() }
        }.start()
    }
}
