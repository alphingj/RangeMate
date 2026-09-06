package com.rangemate.data.log

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Thread-safe raw packet + event logger for field capture sessions.
 *
 * Design notes (deliberate deviations from naive implementations):
 * - A single [BufferedWriter] is held open per session instead of opening/closing
 *   the file on every line (BLE packet rates make per-line open/close wasteful).
 * - [stopLogging] drains the channel via a poison pill before returning the file,
 *   so a shared file is never truncated.
 * - Bounded channel with DROP_OLDEST + overflow counter instead of UNLIMITED growth.
 */
@Singleton
class RawPacketLogger @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private sealed interface LogMsg {
        data class Line(val text: String) : LogMsg
        data object Poison : LogMsg
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val channel = Channel<LogMsg>(
        capacity = 2048,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var writer: BufferedWriter? = null
    private var logFile: File? = null
    private var drainSignal: CompletableDeferred<Unit>? = null
    private var previousExceptionHandler: Thread.UncaughtExceptionHandler? = null
    private val droppedLines = AtomicLong(0)

    private val sessionStartMs = MutableStateFlow(0L)

    private val _isLogging = MutableStateFlow(false)
    val isLogging: StateFlow<Boolean> = _isLogging.asStateFlow()

    private val _entryCount = MutableStateFlow(0L)
    val entryCount: StateFlow<Long> = _entryCount.asStateFlow()

    private val _fileSizeBytes = MutableStateFlow(0L)
    val fileSizeBytes: StateFlow<Long> = _fileSizeBytes.asStateFlow()

    init {
        // Single lifetime consumer: routes lines to whichever writer is active.
        scope.launch {
            for (msg in channel) {
                when (msg) {
                    is LogMsg.Line -> {
                        try {
                            writer?.let {
                                it.appendLine(msg.text)
                                _entryCount.value = _entryCount.value + 1
                                // Flush every 25 lines so a crash doesn't lose the tail.
                                if (_entryCount.value % 25 == 0L) {
                                    it.flush()
                                    _fileSizeBytes.value = logFile?.length() ?: 0L
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Write failed", e)
                        }
                    }
                    LogMsg.Poison -> {
                        try {
                            writer?.flush()
                            writer?.close()
                            _fileSizeBytes.value = logFile?.length() ?: 0L
                        } catch (e: Exception) {
                            Log.e(TAG, "Close failed", e)
                        } finally {
                            writer = null
                            drainSignal?.complete(Unit)
                        }
                    }
                }
            }
        }
    }

    /** Starts a new capture session. Returns the file being written to. */
    fun startLogging(): File {
        if (_isLogging.value) return logFile ?: error("Logging active without file")
        val timestamp = fileTimestamp()
        val dir = File(context.cacheDir, LOG_DIR).apply { mkdirs() }
        logFile = File(dir, "bms_log_$timestamp.txt")
        droppedLines.set(0)
        drainSignal = CompletableDeferred()
        sessionStartMs.value = System.currentTimeMillis()
        writer = BufferedWriter(FileWriter(logFile, false), 8192)
        _entryCount.value = 0
        _fileSizeBytes.value = 0
        _isLogging.value = true

        installCrashHook()
        logEvent("SYSTEM", "Logging started at $timestamp")
        logEvent("SYSTEM", "Device=${Build.MANUFACTURER} ${Build.MODEL} SDK=${Build.VERSION.SDK_INT} app=${appVersion()}")
        return logFile!!
    }

    /**
     * Stops the session. The stop line is queued BEFORE shutdown, then the channel
     * is drained via poison pill, so the returned file is complete.
     */
    suspend fun stopLogging(): File? {
        if (!_isLogging.value) return null
        logEvent("SYSTEM", "Logging stopped. entries=${_entryCount.value} dropped=${droppedLines.get()}")
        _isLogging.value = false
        restoreCrashHook()
        channel.send(LogMsg.Poison)
        withTimeoutOrNull(DRAIN_TIMEOUT_MS) { drainSignal?.await() }
        val file = logFile
        logFile = null
        drainSignal = null
        return file
    }

    fun logEvent(tag: String, message: String) {
        if (!_isLogging.value) return
        val record = "[${clockTimestamp()}] [$tag] $message"
        if (!channel.trySend(LogMsg.Line(record)).isSuccess) {
            droppedLines.incrementAndGet()
        }
    }

    fun logPacket(direction: String, bytes: ByteArray, tag: String = "") {
        if (!_isLogging.value) return
        val hex = bytes.joinToString(" ") { String.format("%02X", it) }
        val label = if (tag.isBlank()) direction else "$direction [$tag]"
        logEvent("BLE_PACKET", "$label len=${bytes.size}: $hex")
    }

    fun activeFile(): File? = logFile

    fun sessionStartMs(): Long = sessionStartMs.value

    /** Shares [file] through the system chooser (WhatsApp, Gmail, Drive, …). */
    fun shareLogFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "RangeMate BMS log ${file.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share BMS log via:"))
        } catch (e: Exception) {
            Log.e(TAG, "Share failed", e)
        }
    }

    private fun installCrashHook() {
        previousExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val record = "[${clockTimestamp()}] [APP_ERR] Uncaught on ${thread.name}: " +
                    "${error::class.java.simpleName}: ${error.message}\n" +
                    error.stackTrace.take(25).joinToString("\n")
                // Best effort synchronous write; process may be dying.
                writer?.appendLine(record)
                writer?.flush()
            } catch (_: Exception) {
            } finally {
                previousExceptionHandler?.uncaughtException(thread, error)
            }
        }
    }

    private fun restoreCrashHook() {
        Thread.getDefaultUncaughtExceptionHandler()?.let { current ->
            // Only restore if ours is still installed.
            if (current != previousExceptionHandler) {
                Thread.setDefaultUncaughtExceptionHandler(previousExceptionHandler)
            }
        }
        previousExceptionHandler = null
    }

    @Suppress("DEPRECATION")
    private fun appVersion(): String = runCatching {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            pm.getPackageInfo(context.packageName, 0)
        }
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
        "${info.versionName} ($code)"
    }.getOrDefault("unknown")

    private fun clockTimestamp(): String =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())

    private fun fileTimestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    companion object {
        private const val TAG = "RawPacketLogger"
        private const val LOG_DIR = "debug_logs"
        private const val DRAIN_TIMEOUT_MS = 5000L
    }
}
