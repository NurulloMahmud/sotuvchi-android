package ai.sotuvchi.app.design

import ai.sotuvchi.app.core.l10n.L
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** A confirmation with a plain title and message. The destructive flavour colors the confirm action red. */
@Composable
fun ConfirmDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    confirmText: String,
    dismissText: String = L("Cancel"),
    destructive: Boolean = false,
) {
    val colors = Theme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = RoundedCornerShape(Radius.xl),
        containerColor = colors.surface,
        title = { Text(title, style = Theme.type.cardTitle, color = colors.ink) },
        text = message?.let { { Text(it, style = Theme.type.body, color = colors.inkMuted) } },
        confirmButton = {
            Text(
                confirmText,
                style = Theme.type.button,
                color = if (destructive) colors.error else colors.ink,
                modifier = Modifier
                    .testTag("dialog.confirm")
                    .clickableNoRipple(onConfirm)
                    .padding(horizontal = Space.sm, vertical = Space.sm),
            )
        },
        dismissButton = {
            Text(
                dismissText,
                style = Theme.type.button,
                color = colors.inkMuted,
                modifier = Modifier
                    .testTag("dialog.dismiss")
                    .clickableNoRipple(onDismiss)
                    .padding(horizontal = Space.sm, vertical = Space.sm),
            )
        },
    )
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

/** A bottom sheet on the cream canvas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SotuvchiSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Theme.colors.canvas,
        shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.md),
            content = content,
        )
    }
}

/** A row of actions at the bottom of a screen, over a hairline. */
@Composable
fun ActionBar(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier.fillMaxWidth().background0()) {
        HairlineDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.md)
                .padding(top = Space.sm, bottom = Space.xs),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) { content() }
    }
}

@Composable
private fun Modifier.background0(): Modifier =
    this.then(Modifier.background(Theme.colors.canvas))
