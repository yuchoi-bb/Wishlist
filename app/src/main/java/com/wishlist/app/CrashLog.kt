package com.wishlist.app

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter

data class CrashInfo(val code: String, val fullTrace: String)

/**
 * Minimal on-device crash recorder: no adb/logcat access is available while developing this app
 * remotely, so this saves the last uncaught exception to SharedPreferences and surfaces it on the
 * next launch as a short, readable-aloud code plus the full trace.
 */
object CrashLog {
    private const val PREFS_NAME = "crash_log"
    private const val KEY_CODE = "last_crash_code"
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
            .putString(KEY_CODE, shortCode(throwable))
            .putString(KEY_TRACE, writer.toString())
            .commit()
    }

    /** e.g. "NullPointerException @ AuthManager.kt:34" — short enough to read aloud or type. */
    private fun shortCode(throwable: Throwable): String {
        val type = throwable.javaClass.simpleName
        val frame = throwable.stackTrace.firstOrNull { it.className.startsWith("com.wishlist.app") }
        return if (frame != null) "$type @ ${frame.fileName}:${frame.lineNumber}" else type
    }

    fun readAndClear(context: Context): CrashInfo? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val code = prefs.getString(KEY_CODE, null) ?: return null
        val trace = prefs.getString(KEY_TRACE, "")
        prefs.edit().remove(KEY_CODE).remove(KEY_TRACE).apply()
        return CrashInfo(code, trace.orEmpty())
    }
}
