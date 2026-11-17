package com.codewall
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Клиент Workshop на Supabase (REST, без SDK). Все запросы идут только по HTTPS. */
class Api(private val p: Prefs) {
    class Err(m: String) : Exception(m)
    class PackInfo(val id: String, val title: String, val author: String, val category: String,
                   val desc: String, val downloads: Int, val uid: String)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun req(method: String, path: String, body: String?, token: String?, prefer: String? = null): Pair<Int, String> {
        val c = URL(Config.URL.trimEnd('/') + path).openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 12000; c.readTimeout = 20000
        c.setRequestProperty("apikey", Config.KEY)
        c.setRequestProperty("Authorization", "Bearer " + (token ?: Config.KEY))
        c.setRequestProperty("Content-Type", "application/json")
        if (prefer != null) c.setRequestProperty("Prefer", prefer)
        if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) } }
        val code = c.responseCode
        val st = if (code in 200..299) c.inputStream else c.errorStream
        val text = st?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        c.disconnect()
        return Pair(code, text)
    }

    private fun msg(t: String): String = try {
        val o = JSONObject(t)
        listOf("msg", "error_description", "message", "error").map { o.optString(it, "") }.firstOrNull { it.isNotEmpty() } ?: "Ошибка сервера"
    } catch (e: Exception) { "Ошибка сервера" }

    private fun claim(jwt: String, key: String): String = try {
        val seg = jwt.split('.')[1]
        JSONObject(String(Base64.decode(seg, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), Charsets.UTF_8)).optString(key, "")
    } catch (e: Exception) { "" }

    private fun save(o: JSONObject) {
        val at = o.optString("access_token")
        if (at.isEmpty()) throw Err("Сервер не вернул токен")
        p.token = at
        p.refresh = o.optString("refresh_token", "")
        val u = o.optJSONObject("user")
        p.uid = u?.optString("id", "") ?: claim(at, "sub")
        p.email = u?.optString("email", "") ?: claim(at, "email")
        if (p.uid.isEmpty()) p.uid = claim(at, "sub")
        if (p.email.isEmpty()) p.email = claim(at, "email")
    }

    fun signIn(email: String, pw: String) {
        val b = JSONObject().put("email", email).put("password", pw).toString()
        val r = req("POST", "/auth/v1/token?grant_type=password", b, null)
        if (r.first !in 200..299) throw Err(msg(r.second))
        save(JSONObject(r.second))
    }

    fun signUp(email: String, pw: String): Boolean {
        val b = JSONObject().put("email", email).put("password", pw).toString()
        val r = req("POST", "/auth/v1/signup", b, null)
        if (r.first !in 200..299) throw Err(msg(r.second))
        val o = JSONObject(r.second)
        if (o.has("access_token")) { save(o); return true }
        return false
    }

    private fun refresh(): Boolean {
        val rt = p.refresh
        if (rt.isEmpty()) return false
        val r = req("POST", "/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", rt).toString(), null)
        if (r.first !in 200..299) return false
        return try { save(JSONObject(r.second)); true } catch (e: Exception) { false }
    }

    fun googleUrl(): String =
        Config.URL.trimEnd('/') + "/auth/v1/authorize?provider=google&redirect_to=" + enc("poipaper://auth")

    fun sessionFromFragment(frag: String) {
        val m = HashMap<String, String>()
        for (part in frag.split('&')) { val kv = part.split('=', limit = 2); if (kv.size == 2) m[kv[0]] = java.net.URLDecoder.decode(kv[1], "UTF-8") }
        val at = m["access_token"] ?: return
        p.token = at; p.refresh = m["refresh_token"] ?: ""
        p.uid = claim(at, "sub"); p.email = claim(at, "email")
    }

    fun signOut() { p.token = ""; p.refresh = ""; p.uid = ""; p.email = "" }

    private fun authed(method: String, path: String, body: String?, prefer: String? = null): Pair<Int, String> {
        val tk = p.token.ifEmpty { null }
        var r = req(method, path, body, tk, prefer)
        if (r.first == 401 && refresh()) r = req(method, path, body, p.token, prefer)
        return r
    }

    fun list(category: String, query: String, sort: Int): List<PackInfo> {
        val sb = StringBuilder("/rest/v1/packs?select=id,title,author,category,description,downloads,user_id&limit=40")
        if (sort == 2) { if (p.uid.isEmpty()) throw Err("Сначала войди в аккаунт"); sb.append("&user_id=eq.").append(p.uid) }
        else sb.append("&status=eq.approved")
        if (category.isNotEmpty()) sb.append("&category=eq.").append(enc(category))
        if (query.isNotEmpty()) sb.append("&title=ilike.").append(enc("*" + query.replace("*", "") + "*"))
        sb.append(if (sort == 1) "&order=downloads.desc" else "&order=created_at.desc")
        val r = authed("GET", sb.toString(), null)
        if (r.first !in 200..299) throw Err(msg(r.second))
        val a = JSONArray(r.second); val out = ArrayList<PackInfo>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            out.add(PackInfo(o.optString("id"), o.optString("title"), o.optString("author"), o.optString("category"),
                o.optString("description"), o.optInt("downloads"), o.optString("user_id")))
        }
        return out
    }

    fun fetch(id: String): String {
        val r = authed("GET", "/rest/v1/packs?select=data&id=eq." + enc(id), null)
        if (r.first !in 200..299) throw Err(msg(r.second))
        val a = JSONArray(r.second)
        if (a.length() == 0) throw Err("Работа не найдена")
        return a.getJSONObject(0).getString("data")
    }

    fun countDownload(id: String) { authed("POST", "/rest/v1/rpc/increment_downloads", JSONObject().put("pack_id", id).toString()) }

    fun upload(title: String, category: String, author: String, desc: String, data: String) {
        if (p.token.isEmpty()) throw Err("Сначала войди в аккаунт")
        val b = JSONObject().put("title", title).put("category", category).put("author", author).put("description", desc).put("data", data).toString()
        val r = authed("POST", "/rest/v1/packs", b, "return=minimal")
        if (r.first !in 200..299) throw Err(msg(r.second))
    }

    fun delete(id: String) {
        val r = authed("DELETE", "/rest/v1/packs?id=eq." + enc(id), null)
        if (r.first !in 200..299) throw Err(msg(r.second))
    }

    fun report(id: String, reason: String) {
        if (p.token.isEmpty()) throw Err("Сначала войди в аккаунт")
        val r = authed("POST", "/rest/v1/reports", JSONObject().put("pack_id", id).put("reason", reason.take(200)).toString(), "return=minimal")
        if (r.first !in 200..299) throw Err(msg(r.second))
    }
}
