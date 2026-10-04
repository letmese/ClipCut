package com.clipcut.core.log

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: Level,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
) {
    enum class Level { VERBOSE, DEBUG, INFO, WARN, ERROR, FATAL }

    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))

    fun toLogLine(): String {
        val base = "[$formattedTime] [${level.name.first()}] [$tag] $message"
        return if (throwable != null) {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            "$base\n$sw"
        } else {
            base
        }
    }
}

object AppLog {
    private const val MAX_MEMORY_LOGS = 2000
    private const val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024 // 5 MB
    private val diskExecutor = Executors.newSingleThreadExecutor()

    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    private val inMemoryLogs = ArrayDeque<LogEntry>(MAX_MEMORY_LOGS)
    private var logDir: File? = null
    private var logFile: File? = null

    fun init(context: Context) {
        val dir = File(context.filesDir, "logs")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        logDir = dir
        logFile = File(dir, "clipcut.log")

        // Set uncaught exception handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            f("CRASH", "FATAL CRASH on thread ${thread.name}: ${throwable.message}", throwable)
            flush()
            defaultHandler?.uncaughtException(thread, throwable)
        }

        i("AppLog", "=== ClipCut Log System Initialized ===")
        i("AppLog", "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (SDK ${android.os.Build.VERSION.SDK_INT})")
    }

    fun v(tag: String, msg: String) = log(LogEntry.Level.VERBOSE, tag, msg)
    fun d(tag: String, msg: String) = log(LogEntry.Level.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = log(LogEntry.Level.INFO, tag, msg)
    fun w(tag: String, msg: String, tr: Throwable? = null) = log(LogEntry.Level.WARN, tag, msg, tr)
    fun e(tag: String, msg: String, tr: Throwable? = null) = log(LogEntry.Level.ERROR, tag, msg, tr)
    fun f(tag: String, msg: String, tr: Throwable? = null) = log(LogEntry.Level.FATAL, tag, msg, tr)

    @Synchronized
    private fun log(level: LogEntry.Level, tag: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(level = level, tag = tag, message = message, throwable = throwable)

        // Native logcat
        when (level) {
            LogEntry.Level.VERBOSE -> Log.v(tag, message, throwable)
            LogEntry.Level.DEBUG -> Log.d(tag, message, throwable)
            LogEntry.Level.INFO -> Log.i(tag, message, throwable)
            LogEntry.Level.WARN -> Log.w(tag, message, throwable)
            LogEntry.Level.ERROR -> Log.e(tag, message, throwable)
            LogEntry.Level.FATAL -> Log.wtf(tag, message, throwable)
        }

        // Memory buffer
        if (inMemoryLogs.size >= MAX_MEMORY_LOGS) {
            inMemoryLogs.removeFirst()
        }
        inMemoryLogs.addLast(entry)
        _logsFlow.value = inMemoryLogs.toList()

        // Disk write
        val target = logFile
        if (target != null) {
            val line = entry.toLogLine()
            diskExecutor.execute {
                try {
                    if (target.exists() && target.length() > MAX_FILE_SIZE_BYTES) {
                        val backup = File(target.parentFile, "clipcut_prev.log")
                        if (backup.exists()) backup.delete()
                        target.renameTo(backup)
                    }
                    FileWriter(target, true).use { fw ->
                        fw.write(line)
                        fw.write("\n")
                    }
                } catch (t: Throwable) {
                    Log.e("AppLog", "Failed to write log to disk", t)
                }
            }
        }
    }

    fun flush() {
        try {
            diskExecutor.submit {}.get()
        } catch (_: Exception) {}
    }

    fun getLogFile(context: Context): File {
        val file = logFile ?: File(context.filesDir, "logs/clipcut.log")
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }
        return file
    }

    fun getFullLogText(context: Context): String {
        return try {
            val file = getLogFile(context)
            if (file.exists()) file.readText() else "No logs recorded yet."
        } catch (e: Exception) {
            "Error reading log file: ${e.message}"
        }
    }

    fun clear(context: Context) {
        synchronized(this) {
            inMemoryLogs.clear()
            _logsFlow.value = emptyList()
        }
        diskExecutor.execute {
            try {
                logFile?.delete()
                val prev = File(logDir, "clipcut_prev.log")
                if (prev.exists()) prev.delete()
            } catch (e: Exception) {
                Log.e("AppLog", "Failed to clear log files", e)
            }
        }
        i("AppLog", "Logs cleared by user")
    }

    fun shareLogs(context: Context) {
        flush()
        try {
            val file = getLogFile(context)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "ClipCut Error Log - ${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}")
                putExtra(Intent.EXTRA_TEXT, "ClipCut diagnostic log file attached.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Log File"))
        } catch (e: Exception) {
            e("AppLog", "Failed to share log file", e)
        }
    }
}
