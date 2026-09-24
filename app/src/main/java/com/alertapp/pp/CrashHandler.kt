package com.alertapp.pp

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter

object CrashHandler {
    fun install(context: Context) {
        val appCtx = context.applicationContext
        val prefs = Prefs(appCtx)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            try {
                val sw = StringWriter()
                ex.printStackTrace(PrintWriter(sw))
                prefs.lastCrash = sw.toString()
            } catch (ignored: Exception) {
                // não deixa o handler de erro causar outro erro
            }
            previous?.uncaughtException(thread, ex)
        }
    }
}
