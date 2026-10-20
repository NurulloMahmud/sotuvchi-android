package ai.sotuvchi.app.features.more

import ai.sotuvchi.app.BuildConfig
import ai.sotuvchi.app.app.AppModel
import ai.sotuvchi.app.app.openInCustomTab
import ai.sotuvchi.app.core.format.DateText
import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.l10n.Localizer
import ai.sotuvchi.app.core.model.PlanStatus
import ai.sotuvchi.app.core.model.WorkspaceRole
import ai.sotuvchi.app.design.Avatar
import ai.sotuvchi.app.design.Badge
import ai.sotuvchi.app.design.BadgeTone
import ai.sotuvchi.app.design.ButtonKind
import ai.sotuvchi.app.design.Card
import ai.sotuvchi.app.design.ConfirmDialog
import ai.sotuvchi.app.design.HairlineDivider
import ai.sotuvchi.app.design.Notice
import ai.sotuvchi.app.design.NavRow
import ai.sotuvchi.app.design.Radius
import ai.sotuvchi.app.design.SButton
import ai.sotuvchi.app.design.ScreenFrame
import ai.sotuvchi.app.design.ScreenScroll
import ai.sotuvchi.app.design.SectionHeader
import ai.sotuvchi.app.design.Space
import ai.sotuvchi.app.design.Theme
import ai.sotuvchi.app.features.auth.LanguageMenu
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Language as GlobeIcon
import androidx.compose.material.icons.outlined.NoAccounts
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Verified
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun MoreScreen(
    model: AppModel,
    onOpenStores: () -> Unit,
    onOpenTeam: () -> Unit,
    onOpenCampaigns: () -> Unit,
    onOpenInstagram: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenDeleteAccount: () -> Unit,
) {
    val app by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmSignOut by remember { mutableStateOf(false) }
    var languageMenu by remember { mutableStateOf(false) }
    val language = Localizer.language

    LaunchedEffect(Unit) {
        launch { model.refreshStores() }
        launch { model.refreshPlan() }
    }

    ScreenFrame(title = L("More")) {
        ScreenScroll {
            AccountCard(app.userName, app.email, app.role, app.workspaceName)

            if (app.billingEnabled) {
                SectionHeader(L("Plan"))
                PlanCard(app.plan)
            }

            SectionHeader(L("Business"))
            Card(padding = Space.sm) {
                NavRow(Icons.Outlined.Storefront, L("Stores"), detail = app.stores.size.takeIf { it > 0 }?.toString(), onClick = onOpenStores, modifier = Modifier.testTag("more.stores"))
                HairlineDivider()
                NavRow(Icons.Outlined.Group, L("Team"), onClick = onOpenTeam, modifier = Modifier.testTag("more.team"))
                HairlineDivider()
                NavRow(Icons.Outlined.ChatBubbleOutline, L("Campaigns"), onClick = onOpenCampaigns, modifier = Modifier.testTag("more.campaigns"))
                HairlineDivider()
                NavRow(Icons.Outlined.CameraAlt, L("Instagram accounts"), onClick = onOpenInstagram)
            }

            SectionHeader(L("App"))
            Card(padding = Space.sm) {
                NavRow(Icons.Outlined.Notifications, L("Notifications"), badge = app.unreadCount, onClick = onOpenNotifications)
                HairlineDivider()
                Box {
                    NavRow(Icons.Outlined.Language, L("Language"), detail = language.nativeName, onClick = { languageMenu = true }, modifier = Modifier.testTag("more.language"))
                    LanguageMenu(expanded = languageMenu, onDismiss = { languageMenu = false }, onPick = {
                        model.setLanguage(it)
                        languageMenu = false
                    })
                }
                HairlineDivider()
                NavRow(Icons.Outlined.OpenInBrowser, L("Open the web dashboard"), onClick = { model.openWeb("/dashboard") }, modifier = Modifier.testTag("more.openWeb"))
            }

            Notice(L("To get alerts while the app is closed, connect Telegram in your store settings."), icon = Icons.Outlined.Send)

            SectionHeader(L("Help"))
            Card(padding = Space.sm) {
                NavRow(Icons.Outlined.Email, L("Contact support"), onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@nahl-group.com"))) }
                })
                HairlineDivider()
                NavRow(Icons.Outlined.PanTool, L("Privacy policy"), onClick = { openInCustomTab(context, model.container.api.webPage("/privacy").toString()) })
                HairlineDivider()
                NavRow(Icons.Outlined.Description, L("Terms of service"), onClick = { openInCustomTab(context, model.container.api.webPage("/terms").toString()) })
            }

            SectionHeader(L("Account"))
            Card(padding = Space.sm) {
                NavRow(
                    Icons.Outlined.NoAccounts, L("Delete account"),
                    onClick = onOpenDeleteAccount,
                    modifier = Modifier.testTag("more.deleteAccount"),
                )
            }

            SButton(
                L("Sign out"), { confirmSignOut = true },
                kind = ButtonKind.Destructive,
                modifier = Modifier.padding(top = Space.xs).testTag("more.signOut"),
            )

            Text(
                "Sotuvchi ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = Theme.type.caption,
                color = Theme.colors.inkTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = Space.sm),
            )
        }
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = L("Sign out?"),
            message = L("You will need to sign in again to access your workspace."),
            confirmText = L("Sign out"),
            destructive = true,
            onConfirm = {
                confirmSignOut = false
                scope.launch { model.signOut() }
            },
            onDismiss = { confirmSignOut = false },
        )
    }
}

/**
 * Plan status only. Subscriptions sold inside an app have to go through the
 * store's own billing, so there is no buy or renew button here.
 */
@Composable
private fun PlanCard(plan: PlanStatus) {
    val colors = Theme.colors
    Card {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(30.dp).background(colors.surfaceAlt, RoundedCornerShape(Radius.md)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Verified, null, tint = colors.ink, modifier = Modifier.size(17.dp)) }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Sotuvchi Pro", style = Theme.type.body.copy(fontWeight = FontWeight.Medium), color = colors.ink)
                Text(planDetail(plan), style = Theme.type.caption, color = colors.inkSubtle)
            }
            when (plan) {
                is PlanStatus.Active -> Badge(L("Active"), tone = if (plan.endsSoon()) BadgeTone.Warning else BadgeTone.Success)
                is PlanStatus.Ended -> Badge(L("Expired"), tone = BadgeTone.Warning)
                PlanStatus.None -> {}
            }
        }
    }
}

private fun planDetail(plan: PlanStatus): String = when (plan) {
    is PlanStatus.Active -> L("Until {date}", mapOf("date" to DateText.shortDate(plan.until)))
    is PlanStatus.Ended -> L("Ended {date}", mapOf("date" to DateText.shortDate(plan.on)))
    PlanStatus.None -> L("No active plan")
}

@Composable
private fun AccountCard(userName: String?, email: String?, role: WorkspaceRole, workspace: String?) {
    val colors = Theme.colors
    Card {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
            Avatar(userName ?: email, size = 52.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(userName ?: email ?: L("Your account"), style = Theme.type.subhead, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (userName != null && email != null) {
                    Text(email, style = Theme.type.caption, color = colors.inkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Badge(role.title, tone = if (role == WorkspaceRole.OWNER) BadgeTone.Inverse else BadgeTone.Neutral)
                    workspace?.let { Text(it, style = Theme.type.caption, color = colors.inkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}
