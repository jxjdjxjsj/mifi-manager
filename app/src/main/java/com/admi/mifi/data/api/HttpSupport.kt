package com.admi.mifi.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

class MemoryCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            this.cookies.removeAll {
                it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path
            }
            this.cookies += cookie
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        cookies.filter { it.matches(url) }

    @Synchronized
    fun clear() {
        cookies.clear()
    }

    @Synchronized
    fun dump(): String = cookies.joinToString("; ") { "${it.name}=${it.value}" }
}

data class HttpResult(
    val code: Int,
    val body: String,
    val headers: Map<String, String>,
) {
    val isSuccessful: Boolean get() = code in 200..299
    fun header(name: String): String? = headers.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value
}

object Http {
    val cookieJar = MemoryCookieJar()

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(6000, TimeUnit.MILLISECONDS)
        .writeTimeout(4000, TimeUnit.MILLISECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36 MifiManager/1.0"

    private val formMediaType = "application/x-www-form-urlencoded; charset=UTF-8".toMediaType()
    private val xmlMediaType = "application/xml; charset=UTF-8".toMediaType()

    suspend fun get(
        url: HttpUrl,
        headers: Map<String, String> = emptyMap(),
    ): HttpResult = withContext(Dispatchers.IO) {
        execute(
            Request.Builder()
                .url(url)
                .get()
                .apply {
                    header("User-Agent", USER_AGENT)
                    header("Accept", "application/json, text/javascript, application/xml, text/xml, */*")
                    header("Accept-Language", "zh-CN,zh;q=0.9")
                    headers.forEach { (key, value) -> header(key, value) }
                }
                .build(),
        )
    }

    suspend fun postForm(
        url: HttpUrl,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ): HttpResult = withContext(Dispatchers.IO) {
        execute(
            Request.Builder()
                .url(url)
                .post(body.toRequestBody(formMediaType))
                .apply {
                    header("User-Agent", USER_AGENT)
                    header("Accept", "application/json, text/javascript, */*")
                    header("Accept-Language", "zh-CN,zh;q=0.9")
                    headers.forEach { (key, value) -> header(key, value) }
                }
                .build(),
        )
    }

    suspend fun postXml(
        url: HttpUrl,
        xml: String,
        headers: Map<String, String> = emptyMap(),
    ): HttpResult = withContext(Dispatchers.IO) {
        execute(
            Request.Builder()
                .url(url)
                .post(xml.toRequestBody(xmlMediaType))
                .apply {
                    header("User-Agent", USER_AGENT)
                    header("Accept", "application/json, text/javascript, application/xml, text/xml, */*")
                    header("Accept-Language", "zh-CN,zh;q=0.9")
                    headers.forEach { (key, value) -> header(key, value) }
                }
                .build(),
        )
    }

    private fun execute(request: Request): HttpResult {
        val response = try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            throw IOException(readableNetworkError(error), error)
        }
        response.use {
            val body = it.body?.string().orEmpty()
            val headers = buildMap {
                for (name in it.headers.names()) {
                    put(name, it.headers.values(name).joinToString(", "))
                }
            }
            return HttpResult(code = it.code, body = body, headers = headers)
        }
    }

    private fun readableNetworkError(error: IOException): String {
        val message = error.message?.lowercase(Locale.ROOT).orEmpty()
        return when {
            message.contains("timeout") || message.contains("timed out") ->
                "连接设备超时，请确认已连接随身 WiFi 且管理地址正确"
            message.contains("failed to connect") || message.contains("connect") ->
                "无法连接到设备，请检查管理地址"
            message.contains("network is unreachable") ->
                "网络不可达，请先连接随身 WiFi"
            else -> "网络请求失败：${error.message ?: "未知错误"}"
        }
    }
}

data class HostConfig(
    val baseUrl: HttpUrl,
    val origin: String,
    val displayHost: String,
    val pathPrefix: String,
) {
    fun url(path: String, query: Map<String, String> = emptyMap()): HttpUrl {
        val normalized = if (path.startsWith("/")) path else "/$path"
        val url = baseUrl.newBuilder()
            .encodedPath(pathPrefix.trimEnd('/') + normalized)
            .query(null)
            .fragment(null)
        query.forEach { (key, value) -> url.addQueryParameter(key, value) }
        return url.build()
    }

    fun urlWithQuery(path: String, query: Map<String, String> = emptyMap()): HttpUrl =
        url(path, query)

    fun defaultHeaders(): Map<String, String> = mapOf(
        "Referer" to "$origin/index.html",
        "Origin" to origin,
        "X-Requested-With" to "XMLHttpRequest",
    )

    companion object {
        fun parse(input: String): HostConfig? {
            var value = input.trim()
            if (value.isEmpty()) return null
            if (!value.startsWith("http://", true) && !value.startsWith("https://", true)) {
                value = "http://$value"
            }
            val parsed = value.toHttpUrlOrNull() ?: return null
            val prefix = parsed.encodedPath.trimEnd('/').takeIf { it.isNotEmpty() && it != "/" } ?: ""
            val base = parsed.newBuilder()
                .encodedPath("/")
                .query(null)
                .fragment(null)
                .build()
            val defaultPort = (base.scheme == "http" && base.port == 80) ||
                (base.scheme == "https" && base.port == 443)
            val hostLabel = if (defaultPort) base.host else "${base.host}:${base.port}"
            return HostConfig(
                baseUrl = base,
                origin = "${base.scheme}://$hostLabel",
                displayHost = hostLabel,
                pathPrefix = prefix,
            )
        }
    }
}
