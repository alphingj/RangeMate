package com.rangemate.data.log

import com.rangemate.BuildConfig
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sends captured log files to the developer's Telegram chat via the Bot API.
 *
 * Active only in builds whose BuildConfig carries credentials (debug field-test
 * builds). Release builds get empty values and every call short-circuits —
 * the token can never leak through a public APK.
 *
 * No networking library required: plain HttpURLConnection multipart POST to
 * `sendDocument` (chat_id + caption + document).
 */
@Singleton
class TelegramLogUploader @Inject constructor() {

    val isConfigured: Boolean =
        BuildConfig.TELEGRAM_BOT_TOKEN.isNotBlank() &&
            BuildConfig.TELEGRAM_CHAT_ID.isNotBlank()

    sealed interface UploadResult {
        data object Sent : UploadResult
        data class Failed(val retryable: Boolean, val message: String) : UploadResult
    }

    suspend fun uploadLog(file: File, caption: String): UploadResult =
        withContext(Dispatchers.IO) {
            if (!isConfigured) {
                return@withContext UploadResult.Failed(false, "Telegram upload not configured in this build")
            }
            if (!file.exists()) {
                return@withContext UploadResult.Failed(false, "Log file missing")
            }
            try {
                val boundary = "RangeMate${System.currentTimeMillis()}"
                val conn = (URL("$API_BASE/bot${BuildConfig.TELEGRAM_BOT_TOKEN}/sendDocument").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                }

                DataOutputStream(conn.outputStream).use { out ->
                    writeTextPart(out, boundary, "chat_id", BuildConfig.TELEGRAM_CHAT_ID)
                    writeTextPart(out, boundary, "caption", caption.take(CAPTION_MAX))
                    writeFilePart(out, boundary, "document", file)
                    out.writeBytes("--$boundary--\r\n")
                    out.flush()
                }

                val code = conn.responseCode
                val body = readBody(conn, code)
                if (code == HttpURLConnection.HTTP_OK && body.contains("\"ok\":true")) {
                    UploadResult.Sent
                } else {
                    val description = DESCRIPTION_REGEX.find(body)?.groupValues?.getOrNull(1)
                        ?: "HTTP $code"
                    UploadResult.Failed(
                        retryable = code == 429 || code >= 500,
                        message = description
                    )
                }
            } catch (e: IOException) {
                UploadResult.Failed(retryable = true, message = e.message ?: "network error")
            } catch (e: Exception) {
                UploadResult.Failed(retryable = false, message = e.message ?: "unexpected error")
            }
        }

    private fun writeTextPart(out: DataOutputStream, boundary: String, name: String, value: String) {
        out.writeBytes("--$boundary\r\n")
        out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
        out.write(value.toByteArray(Charsets.UTF_8))
        out.writeBytes("\r\n")
    }

    private fun writeFilePart(out: DataOutputStream, boundary: String, name: String, file: File) {
        out.writeBytes("--$boundary\r\n")
        out.writeBytes("Content-Disposition: form-data; name=\"$name\"; filename=\"${file.name}\"\r\n")
        out.writeBytes("Content-Type: text/plain\r\n\r\n")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                out.write(buffer, 0, read)
            }
        }
        out.writeBytes("\r\n")
    }

    private fun readBody(conn: HttpURLConnection, code: Int): String = runCatching {
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        stream?.bufferedReader()?.use { it.readText() } ?: ""
    }.getOrDefault("")

    companion object {
        private const val API_BASE = "https://api.telegram.org"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val CAPTION_MAX = 1024
        private val DESCRIPTION_REGEX = Regex("\"description\":\"([^\"]*)\"")
    }
}
