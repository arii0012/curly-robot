package com.codewall
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLException

/** Загрузка модели по ссылке (только https, до 12 МБ) и разбор файла любого поддерживаемого формата. */
object ModelLoader {
    const val MAX_BYTES = 12 * 1024 * 1024
    const val STORE_TRIS = 6000

    class Imported(val kind: String, val mesh: Mesh?, val mask: Mask?, val title: String)

    /** Готовые ссылки для проверки. Адреса сторонние: если файл переехал, вставь свою ссылку. */
    val EXAMPLES = listOf(
        "Утка (.glb)" to "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/HEAD/2.0/Duck/glTF-Binary/Duck.glb",
        "Коробка (.glb)" to "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/HEAD/2.0/Box/glTF-Binary/Box.glb",
        "Голова (.obj)" to "https://raw.githubusercontent.com/mrdoob/three.js/HEAD/examples/models/obj/walt/WaltHead.obj",
        "Диск (.stl)" to "https://raw.githubusercontent.com/mrdoob/three.js/HEAD/examples/models/stl/ascii/slotted_disk.stl",
        "Сердце (.svg)" to "https://raw.githubusercontent.com/twbs/icons/HEAD/icons/heart-fill.svg"
    )

    /** Приводит ссылку к рабочему виду: добавляет https, превращает страницу файла на GitHub в прямую ссылку. */
    fun normalizeUrl(raw: String): String {
        var u = raw.trim()
        if (u.isEmpty()) throw ModelException("Вставь ссылку на файл модели.")
        if (!u.contains("://")) u = "https://$u"
        if (u.startsWith("http://", true)) u = "https://" + u.substring(7)
        if (!u.startsWith("https://", true)) throw ModelException("Поддерживаются только ссылки https://")
        val m = Regex("^https://github\\.com/([^/]+)/([^/]+)/blob/([^/]+)/(.+)$").find(u)
        if (m != null) u = "https://raw.githubusercontent.com/" + m.groupValues[1] + "/" + m.groupValues[2] + "/" + m.groupValues[3] + "/" + m.groupValues[4]
        u = u.substringBefore('#')
        if (u.endsWith("?raw=true")) u = u.removeSuffix("?raw=true")
        return u.replace(" ", "%20")
    }

    private fun looksHtml(data: ByteArray): Boolean {
        val head = String(data, 0, minOf(data.size, 256), Charsets.UTF_8).trimStart().lowercase()
        return head.startsWith("<!doctype html") || head.startsWith("<html")
    }

    /** Скачивает файл. onProgress получает 0..100 (или -1, если размер неизвестен). Можно отменить через cancel. */
    fun fetch(url: String, onProgress: (Int) -> Unit, cancel: AtomicBoolean): ByteArray {
        var cur = url
        var hops = 0
        try {
            while (true) {
                val conn = URL(cur).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 12000; conn.readTimeout = 20000
                conn.setRequestProperty("User-Agent", "PoiPaper/3.0")
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (loc == null) throw ModelException("Сервер вернул неверное перенаправление.")
                    val next = URL(URL(cur), loc).toString()
                    if (!next.startsWith("https://")) throw ModelException("Ссылка ведёт на небезопасный адрес (http). Нужна ссылка https://")
                    if (++hops > 5) throw ModelException("Слишком много перенаправлений.")
                    cur = next
                    continue
                }
                if (code == 404) throw ModelException("Файл не найден (ошибка 404). Проверь ссылку.")
                if (code == 403) throw ModelException("Доступ запрещён (403). Нужна прямая публичная ссылка на файл.")
                if (code !in 200..299) throw ModelException("Сервер ответил ошибкой $code.")
                val len = conn.contentLengthLong
                if (len > MAX_BYTES) throw ModelException("Файл слишком большой (" + (len / 1048576) + " МБ). Максимум 12 МБ.")
                val ct = (conn.contentType ?: "").lowercase()
                val out = ByteArrayOutputStream(if (len in 1..MAX_BYTES.toLong()) len.toInt() else 65536)
                val buf = ByteArray(32768)
                conn.inputStream.use { ins ->
                    var total = 0L
                    while (true) {
                        if (cancel.get()) throw ModelException("Загрузка отменена.")
                        val n = ins.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) throw ModelException("Файл слишком большой. Максимум 12 МБ.")
                        out.write(buf, 0, n)
                        onProgress(if (len > 0) (total * 100 / len).toInt().coerceIn(0, 100) else -1)
                    }
                }
                conn.disconnect()
                val data = out.toByteArray()
                if (ct.contains("text/html") || looksHtml(data))
                    throw ModelException("Это веб-страница, а не файл. Открой страницу модели, нажми Download и вставь прямую ссылку на .glb, .obj, .stl или .svg.")
                return data
            }
        } catch (e: ModelException) { throw e }
        catch (e: UnknownHostException) { throw ModelException("Нет интернета или адрес указан неверно.") }
        catch (e: SocketTimeoutException) { throw ModelException("Сервер слишком долго отвечает. Попробуй ещё раз.") }
        catch (e: SSLException) { throw ModelException("Не удалось установить защищённое соединение.") }
        catch (e: IOException) { throw ModelException("Ошибка сети: " + (e.message ?: "соединение прервано")) }
    }

    fun detect(name: String, data: ByteArray): String {
        val n = name.lowercase().substringBefore('?').substringBefore('#')
        if (n.endsWith(".glb")) return "glb"
        if (n.endsWith(".gltf")) return "gltf"
        if (n.endsWith(".obj")) return "obj"
        if (n.endsWith(".stl")) return "stl"
        if (n.endsWith(".svg")) return "svg"
        if (data.size > 4 && data[0] == 'g'.code.toByte() && data[1] == 'l'.code.toByte() && data[2] == 'T'.code.toByte() && data[3] == 'F'.code.toByte()) return "glb"
        val head = String(data, 0, minOf(data.size, 1024), Charsets.UTF_8).trimStart()
        if (head.contains("<svg", true)) return "svg"
        if (head.startsWith("solid", true)) return "stl"
        if (data.size > 84) {
            val c = (data[80].toInt() and 0xFF) or ((data[81].toInt() and 0xFF) shl 8) or ((data[82].toInt() and 0xFF) shl 16) or ((data[83].toInt() and 0xFF) shl 24)
            if (c > 0 && 84L + 50L * c == data.size.toLong()) return "stl"
        }
        if (Regex("(?m)^v\\s+[-0-9.]").containsMatchIn(head)) return "obj"
        return "?"
    }

    private fun titleOf(name: String): String =
        name.substringBefore('?').substringAfterLast('/').substringBeforeLast('.').take(24).ifEmpty { "Модель" }

    /** Разбор, центрирование, склейка вершин и упрощение (до 6000 треугольников). */
    fun importBytes(name: String, data: ByteArray, onStage: (String) -> Unit): Imported {
        val kind = detect(name, data)
        val title = titleOf(name)
        when (kind) {
            "svg" -> {
                onStage("Разбираю SVG…")
                return Imported("svg", null, SvgParser.parse(data), title)
            }
            "obj", "stl", "glb" -> {
                onStage("Разбираю модель…")
                val raw = when (kind) { "obj" -> ObjParser.parse(data); "stl" -> StlParser.parse(data); else -> GlbParser.parse(data) }
                onStage("Центрирую и объединяю вершины…")
                var m = try { raw.cleaned() } catch (e: IllegalArgumentException) { throw ModelException(e.message ?: "Модель пустая.") }
                if (m.triCount > STORE_TRIS) {
                    onStage("Упрощаю: " + m.triCount + " → " + STORE_TRIS + " треугольников…")
                    m = m.decimated(STORE_TRIS)
                }
                return Imported(kind, m, null, title)
            }
            "gltf" -> throw ModelException("Это .gltf с внешними файлами. Экспортируй из Blender в формате .glb (Binary).")
            else -> throw ModelException("Не удалось определить формат. Поддерживаются .obj, .stl, .glb и .svg.")
        }
    }
}
