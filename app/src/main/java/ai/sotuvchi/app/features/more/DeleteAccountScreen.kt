package ai.sotuvchi.app.features.more

import ai.sotuvchi.app.app.AppModel
import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.model.TeamPayload
import ai.sotuvchi.app.core.model.WorkspaceRole
import ai.sotuvchi.app.core.network.isCancellation
import ai.sotuvchi.app.core.network.team
import ai.sotuvchi.app.core.network.userMessage
import ai.sotuvchi.app.design.ButtonKind
import ai.sotuvchi.app.design.Card
import ai.sotuvchi.app.design.ConfirmDialog
import ai.sotuvchi.app.design.ErrorBanner
import ai.sotuvchi.app.design.Radius
import ai.sotuvchi.app.design.SButton
import ai.sotuvchi.app.design.ScreenFrame
import ai.sotuvchi.app.design.ScreenScroll
import ai.sotuvchi.app.design.SectionHeader
import ai.sotuvchi.app.design.Space
import ai.sotuvchi.app.design.Theme
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PhoneAndroid
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

private data class Consequence(val icon: ImageVector, val text: String, val isLoss: Boolean = false)

/**
 * Deletes the account from inside the app, as the stores require for apps with
 * sign-up. The server deletes workspaces the user owns alone, hands shared
 * ones to the longest-standing other member and ends every session.
 */
@Composable
fun DeleteAccountScreen(model: AppModel, onBack: () -> Unit) {
    val app by model.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var team by remember { mutableStateOf<TeamPayload?>(null) }
    var confirming by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }

    LaunchedEffect(Unit) {
        team = try {
            model.container.api.team()
        } catch (e: Exception) {
            if (e.isCancellation) throw e
            null
        }
    }
    BackHandler(enabled = isDeleting) {}

    // Other people in the workspace. Until the team has loaded this is unknown,
    // and the stronger warning is shown.
    val me = app.email?.lowercase()
    val otherMembers = team?.members?.count { it.user.email?.lowercase() != me }
    val deletesWorkspace = app.role == WorkspaceRole.OWNER && (otherMembers ?: 0) == 0

    fun delete() {
        isDeleting = true
        error = null
        scope.launch {
            try {
                model.deleteAccount()
            } catch (e: Exception) {
                isDeleting = false
                if (e.isCancellation) throw e
                error = e
            }
        }
    }

    val consequences = buildList {
        if (deletesWorkspace) {
            add(Consequence(Icons.Outlined.DeleteOutline, L("Your workspace is deleted with everything in it: stores, products, orders, receipts, chats and campaigns."), true))
            add(Consequence(Icons.Outlined.CameraAlt, L("Your Instagram accounts are disconnected, and Sotuvchi deletes its access to them."), true))
        } else if (app.role == WorkspaceRole.OWNER) {
            add(Consequence(Icons.Outlined.Group, L("Your team keeps the workspace. The longest-standing member becomes its owner.")))
        } else {
            add(Consequence(Icons.AutoMirrored.Outlined.ExitToApp, L("You leave the workspace. Its stores, orders and chats stay with the team.")))
        }
        add(Consequence(Icons.Outlined.PhoneAndroid, L("You are signed out on every device.")))
        add(Consequence(Icons.Outlined.Email, L("Signing in later with the same email starts a new, empty account.")))
    }

    ScreenFrame(title = L("Delete account"), onBack = if (isDeleting) null else onBack) {
        ScreenScroll {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(L("Delete your account"), style = Theme.type.headline, color = Theme.colors.ink)
                Text(L("This permanently deletes your Sotuvchi account. It cannot be undone."), style = Theme.type.body, color = Theme.colors.inkMuted)
            }

            SectionHeader(L("What happens"))
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(Space.md), modifier = Modifier.testTag("deleteAccount.consequences")) {
                    for (item in consequences) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(if (item.isLoss) Theme.colors.errorSoft else Theme.colors.surfaceAlt, RoundedCornerShape(Radius.md)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(item.icon, null, tint = if (item.isLoss) Theme.colors.error else Theme.colors.ink, modifier = Modifier.size(17.dp))
                            }
                            Text(item.text, style = Theme.type.body, color = Theme.colors.ink, modifier = Modifier.weight(1f).padding(top = 3.dp))
                        }
                    }
                }
            }

            error?.let { ErrorBanner(it.userMessage, onRetry = { delete() }) }

            Column(verticalArrangement = Arrangement.spacedBy(Space.xs), modifier = Modifier.padding(top = Space.xs)) {
                SButton(
                    L("Delete account"), { confirming = true },
                    kind = ButtonKind.Destructive,
                    icon = Icons.Outlined.DeleteOutline,
                    loading = isDeleting,
                    enabled = !isDeleting,
                    modifier = Modifier.testTag("deleteAccount.delete"),
                )
                if (isDeleting) Text(L("This can take a few seconds."), style = Theme.type.caption, color = Theme.colors.inkSubtle)
            }
        }
    }

    if (confirming) {
        ConfirmDialog(
            title = L("Delete your account?"),
            message = if (deletesWorkspace) {
                L("Your account, stores, orders, chats and Instagram connection are deleted for good. This cannot be undone.")
            } else {
                L("Your account is deleted for good. This cannot be undone.")
            },
            confirmText = L("Delete account"),
            destructive = true,
            onConfirm = { confirming = false; delete() },
            onDismiss = { confirming = false },
        )
    }
}
