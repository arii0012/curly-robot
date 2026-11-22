package com.codewall
import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
