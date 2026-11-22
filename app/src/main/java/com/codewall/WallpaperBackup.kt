package com.codewall
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import java.io.File

/**
 * Безопасный откат: перед установкой PoiPaper запоминаем прежние обои.
 * Живые обои восстанавливаются всегда. Прежнюю картинку Android отдаёт только при наличии доступа —
 * если не отдал, откат откроет системный выбор обоев.
 */
object WallpaperBackup {
    private fun file(ctx: Context) = File(ctx.filesDir, "prev_wallpaper.png")

    fun save(ctx: Context, p: Prefs) {
        p.prevLive = ""
        p.prevStatic = false
        try {
            val wm = WallpaperManager.getInstance(ctx)
            val info = wm.wallpaperInfo
            if (info != null) {
                if (info.packageName != ctx.packageName) p.prevLive = info.component.flattenToString()
            } else {
                val d = wm.drawable
                if (d != null) {
                    val dm = ctx.resources.displayMetrics
                    val bmp = Bitmap.createBitmap(dm.widthPixels, dm.heightPixels, Bitmap.Config.ARGB_8888)
                    d.setBounds(0, 0, bmp.width, bmp.height)
                    d.draw(Canvas(bmp))
                    file(ctx).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
                    bmp.recycle()
                    p.prevStatic = true
                }
            }
        } catch (e: Throwable) {
            CrashLog.add(ctx, "Не удалось сохранить прежние обои (нужен доступ)", e)
        }
    }

    fun canRestore(ctx: Context, p: Prefs): Boolean = p.prevLive.isNotEmpty() || (p.prevStatic && file(ctx).exists())

    /** Интент для возврата прежних живых обоев, либо null. */
    fun liveIntent(p: Prefs): Intent? {
        val cn = if (p.prevLive.isEmpty()) null else ComponentName.unflattenFromString(p.prevLive)
        if (cn == null) return null
        val i = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, cn)
        return i
    }

    /** Возврат прежней картинки на оба экрана. Вызывать не из главного потока. */
    fun restoreStatic(ctx: Context): Boolean = try {
        val f = file(ctx)
        val bmp = BitmapFactory.decodeFile(f.path)
        if (bmp == null) false else {
            WallpaperManager.getInstance(ctx).setBitmap(bmp, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            true
        }
    } catch (e: Throwable) { CrashLog.add(ctx, "Откат картинки не удался", e); false }
}
