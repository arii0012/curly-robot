package com.codewall
import android.content.Context
import android.os.SystemClock
import kotlin.math.max
import kotlin.math.min

/**
 * Следит за временем кадра и сам подбирает плотность частиц:
 * кадры медленные — плотность снижается (до 40%), нагрузка спала — плавно возвращается.
 * optimize() — ручная очистка кэша и сброс плотности.
 */
object PerformanceGuard {
    @Volatile var scale = 1f
    private var slow = 0
    private var fast = 0
    private var lastChange = 0L

    /** Вызывается из рендера примерно раз в 30 кадров. frameMs — среднее время подготовки кадра. */
    @Synchronized
    fun report(frameMs: Float, budgetMs: Float) {
        val now = SystemClock.uptimeMillis()
        if (frameMs > budgetMs * 0.7f) { slow++; fast = 0 }
        else if (frameMs < budgetMs * 0.25f) { fast++; slow = 0 }
        else { slow = 0; fast = 0 }
        if (now - lastChange < 4000) return
        if (slow >= 3 && scale > 0.45f) { scale = max(0.4f, scale * 0.8f); lastChange = now; slow = 0 }
        else if (fast >= 10 && scale < 1f) { scale = min(1f, scale * 1.15f); lastChange = now; fast = 0 }
    }

    fun optimize(ctx: Context): String {
        val rt = Runtime.getRuntime()
        val before = (rt.totalMemory() - rt.freeMemory()) / 1048576L
        Thumbs.clear()
        scale = 1f
        slow = 0; fast = 0
        System.gc()
        val after = (rt.totalMemory() - rt.freeMemory()) / 1048576L
        return "Кэш очищен, освобождено ≈ " + max(0L, before - after) + " МБ. Используется " + after + " МБ."
    }
}
