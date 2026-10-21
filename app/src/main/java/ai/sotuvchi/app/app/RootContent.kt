package ai.sotuvchi.app.app

import ai.sotuvchi.app.R
import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.design.Spinner
import ai.sotuvchi.app.design.Theme
import ai.sotuvchi.app.design.ToastHost
import ai.sotuvchi.app.features.auth.SignInScreen
import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RootContent(model: AppModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { model.bootstrap() }
    LaunchedEffect(Unit) {
        model.uiEvents.collect { event ->
            when (event) {
                is UiEvent.OpenUrl -> openInCustomTab(context, event.url, onFailure = {
                    model.show(ai.sotuvchi.app.design.Toast(ai.sotuvchi.app.design.Toast.Kind.Error, L("Could not reach the server. Try again.")))
                })
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { model.onForeground() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { model.stopPolling() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.phase) {
        // The sign-in field leaves with its screen; drop the keyboard with it.
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        if (state.phase == Phase.SignedIn) model.startPolling()
    }

    CompositionLocalProvider(
        LocalAppModel provides model,
        LocalAppContainer provides model.container,
    ) {
        Box(Modifier.fillMaxSize().background(Theme.colors.canvas)) {
            when (state.phase) {
                Phase.Launching -> SplashScreen()
                Phase.SignedOut -> SignInScreen(model)
                Phase.SignedIn -> MainScaffold(model)
            }
            ToastHost(toast = state.toast, onDismiss = model::dismissToast, modifier = Modifier.align(Alignment.TopCenter))
            if (model.container.config.showServerBanner) {
                ServerBanner(model.container.config.apiUrl, Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    Column(
        modifier = Modifier.fillMaxSize().background(Theme.colors.canvas),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.logo),
            contentDescription = null,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)),
        )
        Spinner(modifier = Modifier.padding(top = 16.dp), size = 22.dp)
    }
}

/** Web pages open in a Custom Tab: the browser's own session, not the app's. */
fun openInCustomTab(context: Context, url: String, onFailure: () -> Unit = {}) {
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url))
    } catch (_: ActivityNotFoundException) {
        onFailure()
    }
}
