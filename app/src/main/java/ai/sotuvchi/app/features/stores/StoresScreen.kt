package ai.sotuvchi.app.features.stores

import ai.sotuvchi.app.app.AppModel
import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.model.Store
import ai.sotuvchi.app.design.Badge
import ai.sotuvchi.app.design.BadgeTone
import ai.sotuvchi.app.design.ButtonKind
import ai.sotuvchi.app.design.EmptyState
import ai.sotuvchi.app.design.LoadingView
import ai.sotuvchi.app.design.Radius
import ai.sotuvchi.app.design.SButton
import ai.sotuvchi.app.design.ScreenFrame
import ai.sotuvchi.app.design.ScreenScroll
import ai.sotuvchi.app.design.Space
import ai.sotuvchi.app.design.Theme
import ai.sotuvchi.app.design.cardBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun StoresScreen(model: AppModel, onBack: () -> Unit, onOpenStore: (String) -> Unit) {
    val app by model.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoading = true
        model.refreshStores()
        isLoading = false
    }

    ScreenFrame(title = L("Stores"), onBack = onBack) {
        ScreenScroll(
            spacing = Space.sm,
            refreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; model.refreshStores(); refreshing = false } },
        ) {
            if (app.stores.isEmpty()) {
                if (isLoading) {
                    LoadingView()
                } else {
                    EmptyState(
                        Icons.Outlined.Storefront,
                        L("No stores yet."),
                        message = L("Connect an Instagram account first, then create its store. Both are done on the web."),
                        actionTitle = L("Open stores on the web"),
                        onAction = { model.openWeb("/stores") },
                    )
                }
            } else {
                app.stores.forEach { store -> StoreRow(store, onClick = { onOpenStore(store.id) }) }
                SButton(L("Add a store on the web"), { model.openWeb("/stores") }, kind = ButtonKind.Tertiary)
            }
        }
    }
}

@Composable
fun StoreRow(store: Store, onClick: () -> Unit) {
    val colors = Theme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cardBackground()
            .clickable(onClick = onClick)
            .padding(Space.md)
            .testTag("store.${store.name}"),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(colors.surfaceAlt, RoundedCornerShape(Radius.md)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Storefront, null, tint = colors.ink, modifier = Modifier.size(22.dp)) }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(store.name, style = Theme.type.body.copy(fontWeight = FontWeight.Medium), color = colors.ink)
            val parts = buildList {
                store.instagramAccount?.username?.let { add("@$it") }
                store.productCount?.let { add("· " + L(one = "{count} product", other = "{count} products", count = it)) }
            }
            if (parts.isNotEmpty()) Text(parts.joinToString(" "), style = Theme.type.caption, color = colors.inkSubtle)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Badge(if (store.isActive) L("Active") else L("Paused"), tone = if (store.isActive) BadgeTone.Success else BadgeTone.Neutral)
                if (store.aiEnabled) Badge(L("AI on"), tone = BadgeTone.Accent, icon = Icons.Filled.AutoAwesome) else Badge(L("AI off"))
                if (store.telegramConfigured == true) Badge("Telegram", icon = Icons.Outlined.Send)
            }
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = colors.inkTertiary, modifier = Modifier.size(18.dp))
    }
}
