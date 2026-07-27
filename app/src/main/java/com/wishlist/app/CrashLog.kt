package com.wishlist.app

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Minimal on-device crash recorder: no adb/logcat access is available while developing this app
 * remotely, so this saves the last uncaught exception's stack trace to SharedPreferences and
 * surfaces it on the next launch so it can be screenshotted.
 */
object CrashLog {
    private const val PREFS_NAME = "crash_log"
    private const val KEY_TRACE = "last_crash_trace"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { record(appContext, throwable) }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun record(context: Context, throwable: Throwable) {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        // commit() (synchronous), not apply(): the process is about to die and an async write
        // could easily lose the race.
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TRACE, writer.toString())
            .commit()
    }

    fun readAndClear(context: Context): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val trace = prefs.getString(KEY_TRACE, null)
        if (trace != null) {
            prefs.edit().remove(KEY_TRACE).apply()
        }
        return trace
    }
}
