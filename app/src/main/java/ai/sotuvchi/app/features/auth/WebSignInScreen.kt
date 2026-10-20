package ai.sotuvchi.app.features.auth

import ai.sotuvchi.app.app.AppModel
import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.network.userMessage
import ai.sotuvchi.app.design.ErrorBanner
import ai.sotuvchi.app.design.ScreenFrame
import ai.sotuvchi.app.design.Space
import ai.sotuvchi.app.design.Spinner
import ai.sotuvchi.app.design.Theme
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import ai.sotuvchi.app.design.BarIconButton
import kotlinx.coroutines.launch

/**
 * Password sign-in through the web login page (the App Review account signs in
 * this way, since a reviewer cannot read the code email). The page runs in a
 * WebView with a fresh cookie store; once the server sets the session cookie,
 * the app takes its token, which the API also accepts as a Bearer token, and
 * closes.
 */
@Composable
fun WebSignInScreen(model: AppModel, onClose: () -> Unit) {
    val api = model.container.api
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var delivered by remember { mutableStateOf(false) }

    BackHandler(onBack = onClose)

    // A fresh cookie store: nothing from an earlier session leaks in or out.
    DisposableEffect(Unit) {
        wipeWebData()
        onDispose { wipeWebData() }
    }

    val loginUrl = remember { api.webPage("/login").newBuilder().addQueryParameter("reviewer", "1").build().toString() }

    ScreenFrame(
        title = L("Sign in with a password"),
        actions = { BarIconButton(Icons.Outlined.Close, L("Cancel"), onClose) },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    @SuppressLint("SetJavaScriptEnabled")
                    fun configure(view: WebView) {
                        view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        view.settings.javaScriptEnabled = true
                        view.settings.domStorageEnabled = true
                        view.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                        @Suppress("DEPRECATION")
                        view.settings.saveFormData = false
                        view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                    WebView(context).also { view ->
                        configure(view)
                        view.webViewClient = object : WebViewClient() {
                            override fun onPageStarted(v: WebView, url: String?, favicon: Bitmap?) {
                                isLoading = true
                            }

                            override fun onPageFinished(v: WebView, url: String?) {
                                isLoading = false
                                if (delivered) return
                                val token = sessionToken(CookieManager.getInstance().getCookie(loginUrl), api.sessionCookieName)
                                    ?: sessionToken(CookieManager.getInstance().getCookie(url ?: loginUrl), api.sessionCookieName)
                                if (token.isNullOrEmpty()) return
                                delivered = true
                                v.stopLoading()
                                scope.launch {
                                    try {
                                        model.finishSignIn(token)
                                        onClose()
                                    } catch (error: Exception) {
                                        delivered = false
                                        errorMessage = error.userMessage
                                    }
                                }
                            }

                            override fun onReceivedError(v: WebView, request: WebResourceRequest, error: WebResourceError) {
                                if (request.isForMainFrame) isLoading = false
                            }
                        }
                        view.loadUrl(loginUrl)
                    }
                },
                onRelease = { it.stopLoading(); it.destroy() },
            )
            if (isLoading) Spinner(modifier = Modifier.align(Alignment.Center), size = 28.dp)
            errorMessage?.let {
                ErrorBanner(it, modifier = Modifier.align(Alignment.BottomCenter).padding(Space.md))
            }
        }
    }
}

private fun wipeWebData() {
    val cookies = CookieManager.getInstance()
    cookies.removeAllCookies(null)
    cookies.flush()
    WebStorage.getInstance().deleteAllData()
}

/** Picks one cookie's value out of a `Cookie` header string. */
internal fun sessionToken(cookieHeader: String?, name: String): String? =
    cookieHeader?.split(";")?.map { it.trim() }
        ?.firstOrNull { it.startsWith("$name=") }
        ?.substringAfter("=")
        ?.takeIf { it.isNotEmpty() }
