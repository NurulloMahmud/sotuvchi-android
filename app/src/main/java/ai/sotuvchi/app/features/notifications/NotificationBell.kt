package ai.sotuvchi.app.features.notifications

import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.design.Theme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Bell with the unread count. The bell is ink-colored; only the unread dot uses the accent. */
@Composable
fun NotificationBell(unreadCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("bell")
            .semantics { contentDescription = L("Notifications") + if (unreadCount > 0) " $unreadCount" else "" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Notifications, contentDescription = null, tint = Theme.colors.ink)
        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = 8.dp, y = (-8).dp)
                    .size(9.dp)
                    .background(Theme.colors.accent, CircleShape),
            )
        }
    }
}
