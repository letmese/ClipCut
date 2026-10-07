package com.clipcut.core.log

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AppLog — central structured logger. All layers (core, data, ui) emit here.
 * The IME-style LogViewer screen consumes logsFlow.
 *
 * LogEntry.toLogLine() is the canonical single-line format written to disk
 * by flush(); LogViewerScreen reads via getFullLogText().
 */
data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: Level,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
) {
    enum class Level { VERBOSE, DEBUG, INFO, WARN, ERROR, FATAL }

    companion object {
        private val dateFormat = ThreadLocal.withInitial {
            SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        }
    }
    val formattedTime: String get() = dateFormat.get()?.format(Date(timestamp)) ?: ""

    fun toLogLine(): String {
        val t = throwable?.let { sw -> StringWriter().also { pw -> sw.printStackTrace(PrintWriter(pw)) }.toString() } ?: ""
        return "${formattedTime} ${level.name.first()} $tag: $message${if (t.isNotEmpty()) "\n$t" else ""}"
    }
}

object AppLog {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private const val MAX_ENTRIES = 2_000
    private var logFile: File? = null

    fun init(context: Context) {
        try {
            logFile = File(context.filesDir, "clipcut.log")
            if (!logFile!!.exists()) logFile!!.createNewFile()
            Log.i("AppLog", "init at ${logFile!!.absolutePath}")
        } catch (t: Throwable) {
            Log.w("AppLog", "Failed creating log file: ${t.message}")
        }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            f("CrashHandler", "FATAL CRASH on thread ${thread.name}: ${throwable.message}", throwable)
            flush()
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun v(tag: String, msg: String) = add(LogEntry.Level.VERBOSE, tag, msg)
    fun d(tag: String, msg: String) = add(LogEntry.Level.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = add(LogEntry.Level.INFO, tag, msg)
    fun w(tag: String, msg: String, tr: Throwable? = null) = add(LogEntry.Level.WARN, tag, msg, tr)
    fun e(tag: String, msg: String, tr: Throwable? = null) = add(LogEntry.Level.ERROR, tag, msg, tr)
    fun f(tag: String, msg: String, tr: Throwable? = null) = add(LogEntry.Level.FATAL, tag, msg, tr)

    private fun add(level: LogEntry.Level, tag: String, msg: String, tr: Throwable? = null) {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, msg, tr)
        val current = _logs.value.toMutableList()
        current.add(entry)
        if (current.size > MAX_ENTRIES) {
            for (i in 0 until current.size - MAX_ENTRIES) current.removeAt(0)
        }
        _logs.value = current
        when (level) {
            LogEntry.Level.VERBOSE -> Log.v(tag, msg)
            LogEntry.Level.DEBUG -> Log.d(tag, msg)
            LogEntry.Level.INFO -> Log.i(tag, msg)
            LogEntry.Level.WARN -> Log.w(tag, msg, tr)
            LogEntry.Level.ERROR -> Log.e(tag, msg, tr)
            LogEntry.Level.FATAL -> Log.wtf(tag, msg, tr)
        }
    }

    fun flush() {
        logFile?.let { file ->
            file.bufferedWriter().use { w ->
                _logs.value.forEach { w.appendLine(it.toLogLine()) }
            }
        }
    }

    fun getLogFile(context: Context): File {
        logFile?.let { return it }
        init(context)
        return logFile!!
    }

    fun getFullLogText(context: Context): String {
        return _logs.value.joinToString("\n") { it.toLogLine() }
    }

    fun clear(context: Context) {
        _logs.value = emptyList()
        logFile?.let { if (it.exists()) it.delete(); it.createNewFile() }
    }

    fun shareLogs(context: Context) {
        val file = getLogFile(context)
        flush()
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "ClipCut logs")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, "Share logs").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (t: Throwable) {
            e("AppLog", "shareLogs failed", t)
        }
    }
}
