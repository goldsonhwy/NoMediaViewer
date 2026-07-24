package com.nomedia.viewer

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private var appContext: Context? = null
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        appContext = context.applicationContext
        log("App启动")
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            log("崩溃线程=${t.name}\n${e.stackTraceToString()}")
            old?.uncaughtException(t, e)
        }
    }

    fun log(msg: String) {
        runCatching {
            val ctx = appContext ?: return
            val f = logFile(ctx)
            f.parentFile?.mkdirs()
            f.appendText("[${fmt.format(Date())}] $msg\n")
        }
    }

    fun logFile(context: Context): File = File(context.filesDir, "logs/yellow-gallery-log.txt")

    fun exportFile(context: Context): File {
        val f = logFile(context)
        f.parentFile?.mkdirs()
        if (!f.exists()) f.writeText("暂无日志\n")
        return f
    }
}
