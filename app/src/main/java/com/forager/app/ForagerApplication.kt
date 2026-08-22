package com.forager.app

import android.app.Application
import android.os.Build
import com.forager.app.crash.CrashUncaughtExceptionHandler

class ForagerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        installCrashHandler()
    }

    /**
     * See [CrashUncaughtExceptionHandler]'s own doc comment for why this exists. Captures the
     * platform's current default handler before replacing it, so this one can chain to it after
     * writing a trace — process-death behavior is unchanged from before this method existed.
     */
    private fun installCrashHandler() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(
            CrashUncaughtExceptionHandler(container.crashFileStore, Build.VERSION.SDK_INT, previousHandler),
        )
    }
}
