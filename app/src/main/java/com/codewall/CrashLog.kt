package com.codewall
import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Журнал сбоев: пишет необработанные и пойманные ошибки в файл, чтобы их можно было прочитать и скопировать. */
object CrashLog {
    private const val MAX = 20000
    private fun file(ctx: Context) = File(ctx.filesDir, "crash.log")

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            add(app, "СБОЙ в потоке " + t.name, e)
            prev?.uncaughtException(t, e)
        }
    }

    fun add(ctx: Context, what: String, e: Throwable?) {
        try {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val sw = StringWriter()
            e?.printStackTrace(PrintWriter(sw))
            val f = file(ctx)
            val old = if (f.exists()) f.readText() else ""
            var all = "[$stamp] $what\n$sw\n$old"
            if (all.length > MAX) all = all.substring(0, MAX)
            f.writeText(all)
        } catch (x: Throwable) { }
    }

    fun read(ctx: Context): String = try { val f = file(ctx); if (f.exists()) f.readText() else "" } catch (e: Throwable) { "" }
    fun clear(ctx: Context) { try { file(ctx).delete() } catch (e: Throwable) { } }
}
