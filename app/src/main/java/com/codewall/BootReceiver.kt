package com.codewall
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * После перезагрузки Android сам запускает установленные живые обои.
 * Приёмник «прогревает» сохранённую сцену: применяет автосмену сборок и обновляет метку настроек,
 * чтобы обои и виджеты точно стартовали с последней сценой.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            val p = Prefs(ctx)
            Presets.maybeSwitch(ctx, p)
            p.stamp = System.currentTimeMillis()
        } catch (e: Throwable) { CrashLog.add(ctx, "BootReceiver", e) }
    }
}
