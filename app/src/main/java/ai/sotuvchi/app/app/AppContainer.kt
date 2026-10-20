package ai.sotuvchi.app.app

import ai.sotuvchi.app.BuildConfig
import ai.sotuvchi.app.core.auth.EmailCodeAuth
import ai.sotuvchi.app.core.network.ApiClient
import ai.sotuvchi.app.core.store.DataStoreKeyValueStore
import ai.sotuvchi.app.core.store.KeyValueStore
import ai.sotuvchi.app.core.store.KeystoreSecretBox
import ai.sotuvchi.app.core.store.SecretBox
import ai.sotuvchi.app.core.store.TokenStore
import android.content.Context
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade

/**
 * What the app needs from outside: where the API lives and where the session
 * is kept. UI tests (launched with the `uiTesting` extra) keep their session
 * under separate keys, so they never touch a real sign-in saved on the same
 * device.
 */
data class AppConfig(
    val apiUrl: String = BuildConfig.API_URL,
    val keyPrefix: String = "",
) {
    val sessionTokenKey: String get() = keyPrefix + "session-token"
    val emailKey: String get() = keyPrefix + "account.email"
    val roleKey: String get() = keyPrefix + "account.role"
}

/** Simple manual dependency injection: one container per app session. */
class AppContainer(
    val config: AppConfig,
    val kv: KeyValueStore,
    secretBox: SecretBox,
    val api: ApiClient = ApiClient(config.apiUrl),
    val imageLoader: ImageLoader? = null,
) {
    val auth = EmailCodeAuth(api)
    val tokenStore = TokenStore(kv, secretBox, config.sessionTokenKey)

    companion object {
        fun create(context: Context, config: AppConfig): AppContainer {
            val app = context.applicationContext
            val kv = DataStoreKeyValueStore(app)
            val api = ApiClient(config.apiUrl)
            return AppContainer(config, kv, KeystoreSecretBox(), api, buildImageLoader(app, api))
        }

        /**
         * Receipt, chat and product URLs are signed and expire, so pictures are
         * only cached in memory for the session. MoySklad product pictures come
         * through a server proxy that needs the session, so requests to our own
         * host carry the Bearer token.
         */
        private fun buildImageLoader(context: Context, api: ApiClient): ImageLoader {
            val client = api.http.newBuilder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val token = api.sessionToken
                    if (token != null && api.isOwnHost(request.url)) {
                        chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
                    } else {
                        chain.proceed(request)
                    }
                }
                .build()
            return ImageLoader.Builder(context)
                .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
                .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.2).build() }
                .diskCache(null)
                .crossfade(true)
                .build()
        }
    }
}
