package com.codewall
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout

class Account(private val a: Activity, private val p: Prefs, private val ui: Ui) {
    val api = Api(p)

    fun loggedIn() = p.token.isNotEmpty()

    fun loginDialog(done: () -> Unit) {
        if (!Config.ok) { ui.toast("Сервер Workshop не настроен"); return }
        val box = LinearLayout(a)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), 0)
        val em = EditText(a)
        em.hint = "Почта"; em.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        val pw = EditText(a)
        pw.hint = "Пароль (от 6 символов)"; pw.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        box.addView(em); box.addView(pw)
        AlertDialog.Builder(a).setTitle("Вход").setView(box)
            .setPositiveButton("ВОЙТИ") { _, _ -> go(false, em.text.toString().trim(), pw.text.toString(), done) }
            .setNegativeButton("РЕГИСТРАЦИЯ") { _, _ -> go(true, em.text.toString().trim(), pw.text.toString(), done) }
            .setNeutralButton("GOOGLE") { _, _ -> google() }
            .show()
    }

    private fun go(signup: Boolean, email: String, pw: String, done: () -> Unit) {
        if (email.isEmpty() || pw.length < 6) { ui.toast("Введи почту и пароль от 6 символов"); return }
        Thread {
            val m = try {
                if (signup) { if (api.signUp(email, pw)) "Аккаунт создан" else "Подтверди почту по письму, потом войди" }
                else { api.signIn(email, pw); "Вы вошли" }
            } catch (e: Api.Err) { e.message ?: "Ошибка" } catch (e: Exception) { "Нет соединения" }
            a.runOnUiThread { ui.toast(m); done() }
        }.start()
    }

    fun google() {
        if (!Config.ok) { ui.toast("Сервер Workshop не настроен"); return }
        a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(api.googleUrl())))
    }

    fun handleLink(i: Intent?): Boolean {
        val d = i?.data ?: return false
        if (d.scheme != "poipaper") return false
        api.sessionFromFragment(d.fragment ?: "")
        return true
    }

    fun logout() { api.signOut() }
}
