package com.socialvest.app.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal thumbnail loader.
 *
 * The project has no image library on purpose (no Firebase Storage, no image
 * uploads), so course thumbnails are fetched directly with a short timeout and
 * a hard size cap. Any failure returns `null` and the UI keeps its local
 * placeholder — the cache is not needed because only one image is shown at a
 * time (course detail).
 */
object RemoteImage {

    private const val TIMEOUT_MS = 8_000
    private const val MAX_BYTES = 2 * 1024 * 1024

    suspend fun load(url: String?): Bitmap? = withContext(Dispatchers.IO) {
        val target = url?.trim().orEmpty()
        if (!target.startsWith("https://") && !target.startsWith("http://")) {
            return@withContext null
        }

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(target).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("Accept", "image/*")
            }
            if (connection.responseCode !in 200..299) return@withContext null

            val bytes = connection.inputStream.use(::readCapped) ?: return@withContext null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** Reads at most [MAX_BYTES]; oversized downloads are discarded, not kept. */
    private fun readCapped(input: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (output.size() + read > MAX_BYTES) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
