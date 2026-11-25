package com.codewall
import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/** Диалоги импорта: по ссылке и из файла. Показывает ход загрузки, понятные ошибки и подсказки. */
class ModelImport(private val a: Activity, private val ui: Ui, private val p: Prefs, private val done: () -> Unit) {

    fun askUrl(prefill: String = "") {
        val box = LinearLayout(a)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), 0)
        val hint = TextView(a)
        hint.text = "Вставь прямую ссылку на .glb, .obj, .stl или .svg (только https). Ссылку на файл в GitHub можно вставлять как есть, она преобразуется сама. Файл до 12 МБ."
        hint.textSize = 13f
        box.addView(hint)
        val et = EditText(a)
        et.hint = "https://…"
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        et.setText(prefill)
        box.addView(et)
        AlertDialog.Builder(a).setTitle("Модель по ссылке").setView(box)
            .setPositiveButton("ЗАГРУЗИТЬ") { _, _ -> startUrl(et.text.toString()) }
            .setNeutralButton("ПРИМЕРЫ") { _, _ -> examples() }
            .setNegativeButton("Отмена", null).show()
    }

    private fun examples() {
        AlertDialog.Builder(a).setTitle("Примеры ссылок")
            .setItems(ModelLoader.EXAMPLES.map { it.first }.toTypedArray()) { _, i -> askUrl(ModelLoader.EXAMPLES[i].second) }
            .setNegativeButton("Назад") { _, _ -> askUrl() }.show()
    }

    private fun startUrl(raw: String) {
        val url = try { ModelLoader.normalizeUrl(raw) } catch (e: ModelException) { fail(e.message); return }
        run(url) { prog, cancel -> ModelLoader.fetch(url, prog, cancel) }
    }

    fun fromUri(uri: Uri) {
        val name = displayName(uri)
        run(name) { _, cancel -> readUri(uri, cancel) }
    }

    private fun displayName(uri: Uri): String = try {
        a.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "model"
    } catch (e: Exception) { "model" }

    private fun readUri(uri: Uri, cancel: AtomicBoolean): ByteArray {
        val ins = a.contentResolver.openInputStream(uri) ?: throw ModelException("Не удалось открыть файл.")
        return ins.use { s ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(32768)
            var total = 0
            while (true) {
                if (cancel.get()) throw ModelException("Загрузка отменена.")
                val n = s.read(buf)
                if (n < 0) break
                total += n
                if (total > ModelLoader.MAX_BYTES) throw ModelException("Файл слишком большой. Максимум 12 МБ.")
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
    }

    private fun run(name: String, load: (onProgress: (Int) -> Unit, cancel: AtomicBoolean) -> ByteArray) {
        val cancel = AtomicBoolean(false)
        val box = LinearLayout(a)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(4))
        val msg = TextView(a)
        msg.text = "Подключаюсь…"
        box.addView(msg)
        val pb = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal)
        pb.isIndeterminate = true
        pb.max = 100
        pb.progressTintList = ColorStateList.valueOf(ui.G)
        pb.indeterminateTintList = ColorStateList.valueOf(ui.G)
        box.addView(pb, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(10) })
        val dlg = AlertDialog.Builder(a).setTitle("Загрузка модели").setView(box).setCancelable(false)
            .setNegativeButton("Отмена") { _, _ -> cancel.set(true) }.create()
        dlg.show()
        Thread {
            var lastUi = 0L
            try {
                val data = load({ pr ->
                    val now = SystemClock.uptimeMillis()
                    if (now - lastUi > 120) {
                        lastUi = now
                        a.runOnUiThread {
                            if (pr >= 0) { pb.isIndeterminate = false; pb.progress = pr; msg.text = "Скачиваю… $pr%" } else msg.text = "Скачиваю…"
                        }
                    }
                }, cancel)
                val imp = ModelLoader.importBytes(name, data) { st -> a.runOnUiThread { pb.isIndeterminate = true; msg.text = st } }
                a.runOnUiThread { dlg.dismiss(); finish(imp) }
            } catch (e: ModelException) {
                a.runOnUiThread { dlg.dismiss(); fail(e.message) }
            } catch (e: OutOfMemoryError) {
                CrashLog.add(a, "Импорт модели: нехватка памяти", e)
                a.runOnUiThread { dlg.dismiss(); fail("Не хватило памяти. Выбери модель полегче или упрости её в Blender.") }
            } catch (e: Exception) {
                CrashLog.add(a, "Импорт модели", e)
                a.runOnUiThread { dlg.dismiss(); fail("Файл повреждён или не поддерживается.") }
            }
        }.start()
    }

    private fun finish(imp: ModelLoader.Imported) {
        val mesh = imp.mesh
        val mask = imp.mask
        if (mesh != null) {
            val id = ModelStore.save(a, imp.title, mesh)
            p.modelId = id
            p.scene = 7
            p.stamp = System.currentTimeMillis()
            ui.toast("Модель «" + imp.title + "»: " + mesh.triCount + " треугольников")
        } else if (mask != null) {
            val id = Shapes.save(a, imp.title, mask)
            p.shapeA = id
            p.scene = 0
            p.stamp = System.currentTimeMillis()
            ui.toast("SVG «" + imp.title + "» добавлен как фигура")
        }
        done()
    }

    private fun fail(msg: String?) {
        AlertDialog.Builder(a).setTitle("Не получилось загрузить")
            .setMessage(msg ?: "Неизвестная ошибка.")
            .setPositiveButton("ОК", null).show()
    }
}
