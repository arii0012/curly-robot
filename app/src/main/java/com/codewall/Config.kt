package com.codewall

// Этот файл при сборке на GitHub автоматически перезаписывается значениями из Secrets
// (SUPABASE_URL и SUPABASE_KEY). Вручную здесь ничего менять не нужно.
object Config {
    const val URL = ""
    const val KEY = ""
    val ok: Boolean get() = URL.startsWith("https://") && KEY.length > 20
}
