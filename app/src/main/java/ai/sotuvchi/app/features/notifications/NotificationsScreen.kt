package ai.sotuvchi.app.features.notifications

import ai.sotuvchi.app.app.AppModel
import ai.sotuvchi.app.core.format.DateText
import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.model.AppNotification
import ai.sotuvchi.app.core.model.NotificationType
import ai.sotuvchi.app.design.EmptyState
import ai.sotuvchi.app.design.HairlineDivider
import ai.sotuvchi.app.design.LoadingView
import ai.sotuvchi.app.design.Radius
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Sync
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/**
 * The bell feed (latest 30). Titles are built in the app from the type and
 * data, since the server sends no display text.
 */
@Composable
fun NotificationsScreen(model: AppModel, onBack: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    val items = state.feed?.items ?: emptyList()

    LaunchedEffect(Unit) {
        isLoading = true
        model.refreshNotifications()
        isLoading = false
    }

    ScreenFrame(
        title = L("Notifications"),
        onBack = onBack,
        actions = {
            if (state.unreadCount > 0) {
                Text(
                    L("Mark all read"),
                    style = Theme.type.button,
                    color = Theme.colors.ink,
                    modifier = Modifier
                        .clickable { scope.launch { model.markRead() } }
                        .padding(horizontal = Space.xs, vertical = Space.xs),
                )
            }
        },
    ) {
        ScreenScroll(
            spacing = Space.sm,
            refreshing = false,
            onRefresh = { scope.launch { model.refreshNotifications() } },
        ) {
            if (items.isEmpty()) {
                if (isLoading) {
                    LoadingView()
                } else {
                    EmptyState(
                        icon = Icons.Outlined.Notifications,
                        title = L("No notifications yet"),
                        message = L("New orders, receipts and customers who need you show up here."),
                    )
                }
            } else {
                Column(modifier = Modifier.cardBackground()) {
                    items.forEachIndexed { index, item ->
                        if (index > 0) HairlineDivider(Modifier.padding(start = 60.dp))
                        NotificationRow(
                            item,
                            onClick = {
                                if (!item.read) scope.launch { model.markRead(listOf(item.id)) }
                                item.destination?.let { model.open(it) }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun iconFor(type: NotificationType) = when (type) {
    NotificationType.NEW_ORDER -> Icons.Outlined.ShoppingBag
    NotificationType.PAYMENT_PROOF -> Icons.Outlined.Receipt
    NotificationType.HANDOFF -> Icons.Outlined.PanTool
    NotificationType.CUSTOMER_MESSAGE -> Icons.Outlined.ChatBubbleOutline
    NotificationType.ORDER_SYNC_FAILED -> Icons.Outlined.Sync
    NotificationType.UNKNOWN -> Icons.Outlined.Notifications
}

@Composable
fun NotificationRow(notification: AppNotification, onClick: () -> Unit) {
    val colors = Theme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(colors.surfaceAlt, RoundedCornerShape(Radius.md)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(iconFor(notification.type), contentDescription = null, tint = colors.ink, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    notification.title,
                    style = Theme.type.body.copy(fontWeight = if (notification.read) FontWeight.Normal else FontWeight.SemiBold),
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                if (!notification.read) {
                    Box(Modifier.padding(start = 4.dp).size(8.dp).background(colors.accent, CircleShape))
                }
            }
            val body = notification.body
            if (body.isNotEmpty()) {
                Text(body, style = Theme.type.bodySmall, color = colors.inkMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            val store = notification.storeName
            Text(
                DateText.relative(notification.createdAt) + if (store != null) " · $store" else "",
                style = Theme.type.caption,
                color = colors.inkSubtle,
            )
        }
    }
}
