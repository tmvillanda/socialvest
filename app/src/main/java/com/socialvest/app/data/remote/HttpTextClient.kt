package com.socialvest.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/** Coarse HTTP failure categories used to build an honest refresh summary. */
enum class HttpFailure {
    NOT_FOUND,
    RATE_LIMITED,
    SERVER,
    NETWORK,
    TIMEOUT
}

sealed interface HttpResult {
    data class Success(val body: String) : HttpResult
    data class Failure(val reason: HttpFailure) : HttpResult
}

/**
 * Minimal JSON-over-HTTP client built on [HttpURLConnection] so the app needs
 * no extra runtime dependency. It identifies itself as a browser because the
 * public Yahoo Finance endpoint rejects unknown clients, and it retries once
 * when the provider signals rate limiting or a transient server error.
 */
class HttpTextClient(
    private val connectTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_TIMEOUT_MS
) {

    suspend fun getJson(url: String): HttpResult = withContext(Dispatchers.IO) {
        when (val first = request(url)) {
            is HttpResult.Success -> first
            is HttpResult.Failure -> {
                val retryable = first.reason == HttpFailure.RATE_LIMITED ||
                    first.reason == HttpFailure.SERVER
                if (!retryable) {
                    first
                } else {
                    delay(RETRY_DELAY_MS)
                    request(url)
                }
            }
        }
    }

    private fun request(url: String): HttpResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
            }

            when (val code = connection.responseCode) {
                in 200..299 -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    HttpResult.Success(body)
                }
                404 -> HttpResult.Failure(HttpFailure.NOT_FOUND)
                429 -> HttpResult.Failure(HttpFailure.RATE_LIMITED)
                in 500..599 -> HttpResult.Failure(HttpFailure.SERVER)
                else -> HttpResult.Failure(HttpFailure.NETWORK)
            }
        } catch (timeout: SocketTimeoutException) {
            HttpResult.Failure(HttpFailure.TIMEOUT)
        } catch (io: IOException) {
            HttpResult.Failure(HttpFailure.NETWORK)
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 8_000
        private const val RETRY_DELAY_MS = 500L
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }
}
