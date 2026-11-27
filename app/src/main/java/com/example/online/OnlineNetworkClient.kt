package com.example.online

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets

interface NetworkConnectivityChecker {
    fun isOnline(): Boolean
}

class AndroidNetworkConnectivityChecker(
    private val context: Context,
) : NetworkConnectivityChecker {
    override fun isOnline(): Boolean {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(false)
    }
}

sealed interface HttpFetchResult<out T> {
    data class Success<T>(val body: T) : HttpFetchResult<T>
    data class HttpError(val code: Int, val message: String) : HttpFetchResult<Nothing>
    data class NetworkError(val message: String) : HttpFetchResult<Nothing>
}

class OnlineNetworkClient(
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 10_000,
) {
    fun getText(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): HttpFetchResult<String> {
        return when (val res = execute(method = "GET", url = url, headers = headers, jsonBody = null)) {
            is HttpFetchResult.Success -> HttpFetchResult.Success(String(res.body, StandardCharsets.UTF_8))
            is HttpFetchResult.HttpError -> res
            is HttpFetchResult.NetworkError -> res
        }
    }

    fun getBytes(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): HttpFetchResult<ByteArray> {
        return execute(method = "GET", url = url, headers = headers, jsonBody = null)
    }

    fun postJson(
        url: String,
        jsonBody: String,
        headers: Map<String, String> = emptyMap(),
    ): HttpFetchResult<String> {
        return when (val res = execute(method = "POST", url = url, headers = headers, jsonBody = jsonBody)) {
            is HttpFetchResult.Success -> HttpFetchResult.Success(String(res.body, StandardCharsets.UTF_8))
            is HttpFetchResult.HttpError -> res
            is HttpFetchResult.NetworkError -> res
        }
    }

    private fun execute(
        method: String,
        url: String,
        headers: Map<String, String>,
        jsonBody: String?,
        redirectsRemaining: Int = MAX_REDIRECTS,
    ): HttpFetchResult<ByteArray> {
        if (!isAllowedHttpsUrl(url)) {
            return HttpFetchResult.NetworkError("Blocked disallowed or non-HTTPS URL")
        }

        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json, application/xml, text/xml, */*")
                headers.forEach { (k, v) ->
                    if (!k.contains('\r') && !k.contains('\n') && !v.contains('\r') && !v.contains('\n')) {
                        setRequestProperty(k, v)
                    }
                }
                if (jsonBody != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    outputStream.use { out ->
                        out.write(jsonBody.toByteArray(StandardCharsets.UTF_8))
                    }
                }
            }

            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")?.trim()
                if (!location.isNullOrEmpty() && redirectsRemaining > 0) {
                    val resolved = runCatching { URI(url).resolve(location).toString() }.getOrNull()
                    if (resolved != null && isAllowedHttpsUrl(resolved)) {
                        conn.disconnect()
                        return execute(
                            method = "GET",
                            url = resolved,
                            headers = emptyMap(),
                            jsonBody = null,
                            redirectsRemaining = redirectsRemaining - 1,
                        )
                    }
                }
                return HttpFetchResult.HttpError(code, "Redirect rejected")
            }

            if (code !in 200..299) {
                val errText = runCatching {
                    conn.errorStream?.use { readCapped(it) }?.let { String(it, StandardCharsets.UTF_8) }
                }.getOrNull().orEmpty()
                return HttpFetchResult.HttpError(code, errText.ifBlank { conn.responseMessage ?: "HTTP $code" })
            }

            val bytes = conn.inputStream.use { readCapped(it) }
            HttpFetchResult.Success(bytes)
        } catch (e: Exception) {
            HttpFetchResult.NetworkError(e.message ?: "Network error")
        } finally {
            conn?.disconnect()
        }
    }

    private fun readCapped(stream: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val read = stream.read(buf)
            if (read <= 0) break
            if (total + read > MAX_RESPONSE_BYTES) {
                throw IOException("Response exceeded $MAX_RESPONSE_BYTES bytes")
            }
            out.write(buf, 0, read)
            total += read
        }
        return out.toByteArray()
    }

    companion object {
        const val USER_AGENT = "CLMMediaPlayer/1.0 (Android)"
        private const val MAX_REDIRECTS = 3
        private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024

        private val EXACT_ALLOWED_HOSTS = setOf(
            "lrclib.net",
            "podnapisi.net",
            "www.podnapisi.net",
            "api.opensubtitles.com",
            "en.wikipedia.org",
        )

        private val ALLOWED_HOST_SUFFIXES = setOf(
            ".opensubtitles.com",
            ".opensubtitles.org",
            ".podnapisi.net",
        )

        fun isAllowedHttpsUrl(rawUrl: String): Boolean {
            val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return false
            if (!uri.scheme.equals("https", ignoreCase = true)) return false
            val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
            if (host.isEmpty()) return false
            if (host in EXACT_ALLOWED_HOSTS) return true
            return ALLOWED_HOST_SUFFIXES.any { suffix -> host.endsWith(suffix) }
        }
    }
}
