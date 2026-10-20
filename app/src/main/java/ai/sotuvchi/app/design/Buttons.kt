package ai.sotuvchi.app.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Button roles from the design doc. Charcoal is the default CTA; the AI accent
 * is reserved for AI actions (pause or resume the AI seller) and must not
 * share a viewport with a charcoal CTA.
 */
enum class ButtonKind { Primary, Secondary, Tertiary, Accent, Destructive }

@Composable
fun SButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    fullWidth: Boolean = true,
    compact: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val colors = Theme.colors
    val (fg, bg) = when (kind) {
        ButtonKind.Primary -> colors.onPrimary to colors.primary
        ButtonKind.Secondary -> colors.ink to colors.surface
        ButtonKind.Tertiary -> colors.ink to Color.Transparent
        ButtonKind.Accent -> Color.White to colors.accent
        ButtonKind.Destructive -> colors.error to colors.errorSoft
    }
    val shape = RoundedCornerShape(Radius.md)
    Box(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = if (compact) 36.dp else ControlHeight)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(bg, shape)
            .then(if (kind == ButtonKind.Secondary) Modifier.border(BorderStroke(1.dp, colors.hairline), shape) else Modifier)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (compact) 14.dp else 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.alpha(if (loading) 0f else 1f),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Text(text, style = Theme.type.button, color = fg, maxLines = 1)
        }
        if (loading) Spinner(size = 18.dp, color = fg)
    }
}
