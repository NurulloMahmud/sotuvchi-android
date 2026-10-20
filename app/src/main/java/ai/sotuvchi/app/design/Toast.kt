package ai.sotuvchi.app.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.util.UUID

/** Short-lived message shown at the top of the screen. */
data class Toast(
    val kind: Kind,
    val message: String,
    val actionTitle: String? = null,
    val action: (() -> Unit)? = null,
    val id: String = UUID.randomUUID().toString(),
) {
    enum class Kind { Success, Warning, Error, Info }
}

@Composable
fun ToastHost(toast: Toast?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = toast != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            // Keep the last toast around while it animates out.
            val shown = androidx.compose.runtime.remember(toast?.id) { toast }
            if (shown != null) ToastView(shown, onDismiss)
        }
    }
}

@Composable
private fun ToastView(toast: Toast, onDismiss: () -> Unit) {
    val colors = Theme.colors
    val (icon, tint) = when (toast.kind) {
        Toast.Kind.Success -> Icons.Filled.CheckCircle to colors.successInk
        Toast.Kind.Warning -> Icons.Filled.Warning to colors.warningInk
        Toast.Kind.Error -> Icons.Filled.Error to colors.error
        Toast.Kind.Info -> Icons.Filled.Notifications to colors.ink
    }
    Row(
        modifier = Modifier
            .padding(horizontal = Space.md, vertical = Space.xs)
            .fillMaxWidth()
            .cardBackground(Radius.lg)
            .clickable(onClick = onDismiss)
            .padding(Space.sm)
            .testTag("toast"),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(toast.message, style = Theme.type.bodySmall, color = colors.ink, modifier = Modifier.weight(1f))
        val title = toast.actionTitle
        val action = toast.action
        if (title != null && action != null) {
            Text(
                title,
                style = Theme.type.button,
                color = colors.ink,
                modifier = Modifier.clickable {
                    action()
                    onDismiss()
                },
            )
        }
    }
}
