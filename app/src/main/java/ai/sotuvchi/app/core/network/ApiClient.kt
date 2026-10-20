package ai.sotuvchi.app.core.network

import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** One HTTP call to the Sotuvchi backend. */
data class ApiRequest(
    val method: String = "GET",
    val path: String,
    val query: List<Pair<String, String>> = emptyList(),
    val body: ByteArray? = null,
    val contentType: String? = null,
    val timeoutSeconds: Long = 30,
) {
    fun withJson(json: String): ApiRequest = copy(body = json.toByteArray(Charsets.UTF_8), contentType = "application/json")

    companion object {
        fun get(path: String, query: List<Pair<String, String>> = emptyList(), timeoutSeconds: Long = 30) =
            ApiRequest("GET", path, query, timeoutSeconds = timeoutSeconds)

        fun post(path: String, query: List<Pair<String, String>> = emptyList()) =
            ApiRequest("POST", path, query)

        fun <T> post(path: String, serializer: KSerializer<T>, body: T): ApiRequest =
            ApiRequest("POST", path).withJson(ApiJson.json.encodeToString(serializer, body))

        fun <T> patch(path: String, serializer: KSerializer<T>, body: T, query: List<Pair<String, String>> = emptyList()): ApiRequest =
            ApiRequest("PATCH", path, query).withJson(ApiJson.json.encodeToString(serializer, body))

        fun patchJson(path: String, json: String, query: List<Pair<String, String>> = emptyList()): ApiRequest =
            ApiRequest("PATCH", path, query).withJson(json)

        fun postJson(path: String, json: String): ApiRequest = ApiRequest("POST", path).withJson(json)

        fun delete(path: String): ApiRequest = ApiRequest("DELETE", path)

        fun <T> delete(path: String, serializer: KSerializer<T>, body: T): ApiRequest =
            ApiRequest("DELETE", path).withJson(ApiJson.json.encodeToString(serializer, body))
    }

    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * HTTP client for the Sotuvchi API.
 *
 * The app signs in with an emailed code (see [EmailCodeAuth]) and sends the
 * session token it gets back as `Authorization: Bearer <token>` on every
 * request. No cookie jar is used.
 */
class ApiClient(
    /** API root, for example `https://sotuvchi.ai/api`. Endpoint paths are relative to it. */
    val apiUrl: String,
    sessionToken: String? = null,
    httpClient: OkHttpClient? = null,
) {
    /** The site the API belongs to (`https://sotuvchi.ai`): web pages, the Auth.js endpoints and server-relative image paths. */
    val siteUrl: HttpUrl = origin(apiUrl.toHttpUrl())

    @Volatile
    var sessionToken: String? = sessionToken
        private set

    /** Called when any request answers 401. */
    @Volatile
    var onUnauthorized: (() -> Unit)? = null

    val http: OkHttpClient = httpClient ?: defaultClient()

    fun setSessionToken(token: String?) {
        sessionToken = token
    }

    /**
     * Name of the web session cookie, which carries the same kind of token.
     * Only the password sign-in screen reads it. Auth.js picks the name from
     * the protocol: `__Secure-` over HTTPS.
     */
    val sessionCookieName: String
        get() = if (siteUrl.scheme == "https") "__Secure-authjs.session-token" else "authjs.session-token"

    /** A page of the web app, for things the phone hands off to the browser. */
    fun webPage(path: String): HttpUrl = (siteUrl.toString().trimEnd('/') + path).toHttpUrl()

    // region Requests

    /** Sends a request and decodes the `data` field of the envelope. */
    suspend fun <T> data(request: ApiRequest, serializer: KSerializer<T>): T {
        val raw = perform(request)
        try {
            val envelope = ApiJson.json.decodeFromString(Envelope.serializer(serializer), raw)
            return envelope.data ?: throw ApiError.Decoding("Missing data")
        } catch (error: ApiError) {
            throw error
        } catch (error: Exception) {
            throw ApiError.Decoding(error.toString())
        }
    }

    suspend inline fun <reified T> data(request: ApiRequest): T = data(request, ApiJson.json.serializersModule.serializer<T>())

    /**
     * Sends a request and decodes the whole body (for responses with siblings
     * next to `data`, such as orders with `summary` and `range`).
     */
    suspend fun <T> decode(request: ApiRequest, serializer: KSerializer<T>): T {
        val raw = perform(request)
        try {
            return ApiJson.json.decodeFromString(serializer, raw)
        } catch (error: Exception) {
            throw ApiError.Decoding(error.toString())
        }
    }

    suspend inline fun <reified T> decode(request: ApiRequest): T = decode(request, ApiJson.json.serializersModule.serializer<T>())

    /** Sends a request whose success body carries nothing the app needs. */
    suspend fun send(request: ApiRequest) {
        perform(request)
    }

    /**
     * Fetches raw bytes from an absolute or server-relative URL, attaching the
     * session only when the URL points at this backend.
     */
    suspend fun bytes(url: HttpUrl): ByteArray {
        val builder = Request.Builder().url(url)
        if (isOwnHost(url)) authorize(builder)
        val call = http.newCall(builder.build())
        call.timeout().timeout(60, TimeUnit.SECONDS)
        val response = execute(call)
        response.use {
            if (!it.isSuccessful) {
                if (it.code == 401 && isOwnHost(url)) onUnauthorized?.invoke()
                throw ApiError.from(it.code, null)
            }
            return it.body.bytes()
        }
    }

    fun resolve(urlString: String): HttpUrl? {
        if (urlString.startsWith("/")) return siteUrl.resolve(urlString)
        return urlString.toHttpUrlOrNull()
    }

    fun isOwnHost(url: HttpUrl): Boolean =
        url.host.equals(siteUrl.host, ignoreCase = true) && url.scheme == siteUrl.scheme && url.port == siteUrl.port

    /**
     * Sends a request without the session and without the 401 handling, and
     * hands back the raw response. Sign-in uses it: there a 401 means a wrong
     * code, not an ended session.
     */
    suspend fun exchange(request: ApiRequest): RawResponse {
        val call = http.newCall(buildRequest(request))
        call.timeout().timeout(request.timeoutSeconds, TimeUnit.SECONDS)
        val response = execute(call)
        response.use {
            return RawResponse(it.code, it.body.string(), it.header("Retry-After"))
        }
    }

    // endregion

    private suspend fun perform(request: ApiRequest): String {
        val builder = buildRequestBuilder(request)
        authorize(builder)
        val call = http.newCall(builder.build())
        call.timeout().timeout(request.timeoutSeconds, TimeUnit.SECONDS)
        val response = execute(call)
        response.use {
            val text = it.body.string()
            if (!it.isSuccessful) {
                val message = runCatching { ApiJson.json.decodeFromString(ErrorBody.serializer(), text).error }.getOrNull()
                if (it.code == 401) onUnauthorized?.invoke()
                throw ApiError.from(it.code, message)
            }
            return text
        }
    }

    private suspend fun execute(call: Call): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWithException(ApiError.from(e))
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }
        })
    }

    /** Builds the request, without the session. Public for tests. */
    fun buildRequest(request: ApiRequest): Request = buildRequestBuilder(request).build()

    /** Builds the request with the Bearer header, as every signed-in call sends it. */
    fun buildAuthorizedRequest(request: ApiRequest): Request {
        val builder = buildRequestBuilder(request)
        authorize(builder)
        return builder.build()
    }

    private fun buildRequestBuilder(request: ApiRequest): Request.Builder {
        val url = (apiUrl.trimEnd('/') + request.path).toHttpUrlOrNull()
            ?: throw ApiError.Transport("Bad URL")
        val urlBuilder = url.newBuilder()
        for ((name, value) in request.query) urlBuilder.addQueryParameter(name, value)
        val builder = Request.Builder().url(urlBuilder.build()).header("Accept", "application/json")
        val body = request.body
        val body2: RequestBody? = when {
            body != null -> body.toRequestBody((request.contentType ?: "application/json").toMediaType())
            request.method == "GET" -> null
            else -> ByteArray(0).toRequestBody(null)
        }
        builder.method(request.method, body2)
        return builder
    }

    private fun authorize(builder: Request.Builder) {
        val token = sessionToken ?: return
        builder.header("Authorization", "Bearer $token")
    }

    companion object {
        const val PRODUCTION_API_URL = "https://sotuvchi.ai/api"

        /** Scheme, host and port of a URL, without its path. */
        fun origin(url: HttpUrl): HttpUrl =
            HttpUrl.Builder().scheme(url.scheme).host(url.host).port(url.port).build()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}

data class RawResponse(val status: Int, val body: String, val retryAfter: String?)

/** Multipart request with one file part, for product pictures. */
object Multipart {
    fun request(path: String, name: String, filename: String, mimeType: String, data: ByteArray, timeoutSeconds: Long = 90): ApiRequest {
        val form = okhttp3.MultipartBody.Builder(UUID.randomUUID().toString())
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart(name, filename, data.toRequestBody(mimeType.toMediaType()))
            .build()
        val buffer = okio.Buffer()
        form.writeTo(buffer)
        return ApiRequest("POST", path, body = buffer.readByteArray(), contentType = form.contentType().toString(), timeoutSeconds = timeoutSeconds)
    }
}
