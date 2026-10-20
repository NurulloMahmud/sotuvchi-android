package ai.sotuvchi.app.features.more

import ai.sotuvchi.app.app.AppModel
import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.model.InstagramAccountSummary
import ai.sotuvchi.app.core.network.ApiError
import ai.sotuvchi.app.core.network.instagramAccounts
import ai.sotuvchi.app.core.network.isCancellation
import ai.sotuvchi.app.core.network.userMessage
import ai.sotuvchi.app.design.Avatar
import ai.sotuvchi.app.design.Badge
import ai.sotuvchi.app.design.ButtonKind
import ai.sotuvchi.app.design.EmptyState
import ai.sotuvchi.app.design.ErrorBanner
import ai.sotuvchi.app.design.HairlineDivider
import ai.sotuvchi.app.design.LoadingView
import ai.sotuvchi.app.design.SButton
import ai.sotuvchi.app.design.ScreenFrame
import ai.sotuvchi.app.design.ScreenScroll
import ai.sotuvchi.app.design.Space
import ai.sotuvchi.app.design.Theme
import ai.sotuvchi.app.design.cardBackground
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun InstagramAccountsScreen(model: AppModel, onBack: () -> Unit) {
    val app by model.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf<List<InstagramAccountSummary>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    suspend fun load() {
        try {
            accounts = model.container.api.instagramAccounts().instagramAccounts
            loaded = true
            error = null
        } catch (e: ApiError.Invalid) {
            accounts = emptyList()
            loaded = true
        } catch (e: Exception) {
            if (e.isCancellation) throw e
            loaded = true
            error = e
        }
    }
    LaunchedEffect(Unit) { load() }

    ScreenFrame(title = L("Instagram accounts"), onBack = onBack) {
        ScreenScroll(
            spacing = Space.sm,
            refreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
        ) {
            error?.let { ErrorBanner(it.userMessage, onRetry = { scope.launch { load() } }) }
            if (!loaded) {
                LoadingView()
            } else if (accounts.isEmpty()) {
                EmptyState(
                    Icons.Outlined.CameraAlt,
                    L("No Instagram account connected"),
                    message = L("Connecting Instagram happens on the web, where Meta's sign-in runs."),
                    actionTitle = L("Connect on the web"),
                    onAction = { model.openWeb("/settings") },
                )
            } else {
                Column(modifier = Modifier.cardBackground()) {
                    accounts.forEachIndexed { index, account ->
                        if (index > 0) HairlineDivider(Modifier.padding(start = 64.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm),
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(account.username, size = 40.dp)
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("@" + (account.username ?: "-"), style = Theme.type.body, color = Theme.colors.ink)
                                account.name?.takeIf { it.isNotEmpty() }?.let {
                                    Text(it, style = Theme.type.caption, color = Theme.colors.inkSubtle)
                                }
                            }
                            app.stores.firstOrNull { it.instagramAccountId == account.id }?.let {
                                Badge(it.name, icon = Icons.Outlined.Storefront)
                            }
                        }
                    }
                }
                SButton(L("Connect another account on the web"), { model.openWeb("/settings") }, kind = ButtonKind.Tertiary)
            }
        }
    }
}
