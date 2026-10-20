package ai.sotuvchi.app.features.team

import ai.sotuvchi.app.app.AppModel
import ai.sotuvchi.app.core.auth.EmailCodeAuth
import ai.sotuvchi.app.core.format.DateText
import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.model.TeamPayload
import ai.sotuvchi.app.core.model.WorkspaceInvitation
import ai.sotuvchi.app.core.model.WorkspaceMember
import ai.sotuvchi.app.core.model.WorkspaceRole
import ai.sotuvchi.app.core.network.ApiError
import ai.sotuvchi.app.core.network.changeRole
import ai.sotuvchi.app.core.network.invite
import ai.sotuvchi.app.core.network.isCancellation
import ai.sotuvchi.app.core.network.removeMember
import ai.sotuvchi.app.core.network.revokeInvitation
import ai.sotuvchi.app.core.network.team
import ai.sotuvchi.app.core.network.userMessage
import ai.sotuvchi.app.design.Avatar
import ai.sotuvchi.app.design.Badge
import ai.sotuvchi.app.design.BadgeTone
import ai.sotuvchi.app.design.BarIconButton
import ai.sotuvchi.app.design.ConfirmDialog
import ai.sotuvchi.app.design.ErrorBanner
import ai.sotuvchi.app.design.HairlineDivider
import ai.sotuvchi.app.design.InputField
import ai.sotuvchi.app.design.LabeledField
import ai.sotuvchi.app.design.LoadingView
import ai.sotuvchi.app.design.PillOption
import ai.sotuvchi.app.design.PillTabs
import ai.sotuvchi.app.design.SButton
import ai.sotuvchi.app.design.ScreenFrame
import ai.sotuvchi.app.design.ScreenScroll
import ai.sotuvchi.app.design.SectionHeader
import ai.sotuvchi.app.design.SotuvchiSheet
import ai.sotuvchi.app.design.Space
import ai.sotuvchi.app.design.Theme
import ai.sotuvchi.app.design.Toast
import ai.sotuvchi.app.design.cardBackground
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/** Workspace members and pending invitations. */
@Composable
fun TeamScreen(model: AppModel, onBack: () -> Unit) {
    val app by model.state.collectAsStateWithLifecycle()
    val api = model.container.api
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var team by remember { mutableStateOf<TeamPayload?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var showsInvite by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<WorkspaceMember?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    suspend fun load() {
        try {
            team = api.team()
            error = null
        } catch (e: Exception) {
            if (e.isCancellation) throw e
            error = e
        }
    }

    suspend fun apply(change: suspend () -> TeamPayload) {
        try {
            team = change()
        } catch (e: ApiError.Invalid) {
            val message = e.serverMessage
            if (message != null) model.show(Toast(Toast.Kind.Error, message)) else model.showError(e)
        } catch (e: Exception) {
            if (e.isCancellation) throw e
            model.showError(e)
        }
    }

    LaunchedEffect(Unit) { load() }

    ScreenFrame(
        title = L("Team"),
        onBack = onBack,
        actions = {
            if (app.canManage) BarIconButton(Icons.Outlined.PersonAdd, L("Invite"), { showsInvite = true })
        },
    ) {
        ScreenScroll(
            spacing = Space.sm,
            refreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
        ) {
            error?.let { ErrorBanner(it.userMessage, onRetry = { scope.launch { load() } }) }
            val current = team
            if (current == null) {
                LoadingView()
            } else {
                Text(
                    L("People who can manage orders, products and chats. Members can only view."),
                    style = Theme.type.bodySmall,
                    color = Theme.colors.inkMuted,
                    modifier = Modifier.padding(horizontal = Space.xxs),
                )
                SectionHeader(L("Members"))
                Column(modifier = Modifier.cardBackground()) {
                    current.members.forEachIndexed { index, member ->
                        if (index > 0) HairlineDivider(Modifier.padding(start = 64.dp))
                        MemberRow(
                            member,
                            canManage = app.canManage,
                            onMakeAdmin = { scope.launch { apply { api.changeRole(member.id, WorkspaceRole.ADMIN) } } },
                            onMakeMember = { scope.launch { apply { api.changeRole(member.id, WorkspaceRole.MEMBER) } } },
                            onRemove = { removing = member },
                        )
                    }
                }
                if (current.invitations.isNotEmpty()) {
                    SectionHeader(L("Pending invites"))
                    Column(modifier = Modifier.cardBackground()) {
                        current.invitations.forEachIndexed { index, invitation ->
                            if (index > 0) HairlineDivider()
                            InvitationRow(
                                invitation,
                                canManage = app.canManage,
                                onShare = { link ->
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, L("Join my Sotuvchi workspace") + "\n" + link)
                                    }
                                    context.startActivity(Intent.createChooser(send, L("Share invite link")))
                                },
                                onRevoke = { scope.launch { apply { api.revokeInvitation(invitation.id) } } },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showsInvite) {
        InviteSheet(
            onDismiss = { showsInvite = false },
            onInvite = { email, role ->
                try {
                    val previous = team?.invitations?.map { it.id }?.toSet() ?: emptySet()
                    val updated = api.invite(email, role)
                    team = updated
                    val created = updated.invitations.firstOrNull { it.id !in previous }
                    model.show(
                        Toast(
                            Toast.Kind.Success,
                            if (created != null) L("Invite created for {email}. Share the link with them; no email is sent.", mapOf("email" to created.email))
                            else L("{email} was added to the team.", mapOf("email" to email)),
                        ),
                    )
                    true
                } catch (e: Exception) {
                    if (e.isCancellation) throw e
                    model.showError(e)
                    false
                }
            },
        )
    }
    removing?.let { member ->
        ConfirmDialog(
            title = L("Remove this member?"),
            message = L("They lose access to this workspace."),
            confirmText = L("Remove {name}", mapOf("name" to member.displayName)),
            destructive = true,
            onConfirm = {
                removing = null
                scope.launch { apply { api.removeMember(member.id) } }
            },
            onDismiss = { removing = null },
        )
    }
}

@Composable
private fun MemberRow(
    member: WorkspaceMember,
    canManage: Boolean,
    onMakeAdmin: () -> Unit,
    onMakeMember: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = Theme.colors
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(member.user.name ?: member.user.email, size = 40.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(member.displayName, style = Theme.type.body, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val email = member.user.email
            if (email != null && member.user.name?.isNotEmpty() == true) {
                Text(email, style = Theme.type.caption, color = colors.inkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Badge(member.role.title, tone = if (member.role == WorkspaceRole.OWNER) BadgeTone.Inverse else BadgeTone.Neutral)
        if (canManage && member.role != WorkspaceRole.OWNER) {
            Box {
                BarIconButton(Icons.Outlined.MoreVert, L("More"), { menu = true }, modifier = Modifier.testTag("member.menu"))
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    containerColor = colors.surface,
                    shadowElevation = 0.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.hairline),
                ) {
                    if (member.role == WorkspaceRole.MEMBER) {
                        DropdownMenuItem(text = { Text(L("Make admin"), style = Theme.type.body, color = colors.ink) }, onClick = { menu = false; onMakeAdmin() })
                    } else {
                        DropdownMenuItem(text = { Text(L("Make member"), style = Theme.type.body, color = colors.ink) }, onClick = { menu = false; onMakeMember() })
                    }
                    DropdownMenuItem(text = { Text(L("Remove"), style = Theme.type.body, color = colors.error) }, onClick = { menu = false; onRemove() })
                }
            }
        }
    }
}

@Composable
private fun InvitationRow(
    invitation: WorkspaceInvitation,
    canManage: Boolean,
    onShare: (String) -> Unit,
    onRevoke: () -> Unit,
) {
    val colors = Theme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(invitation.email, style = Theme.type.body, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val expires = invitation.expiresAt
            Text(
                invitation.role.title + if (expires != null) " · " + L("expires {date}", mapOf("date" to DateText.day(expires))) else "",
                style = Theme.type.caption,
                color = colors.inkSubtle,
            )
        }
        invitation.inviteUrl?.let { link ->
            BarIconButton(Icons.Outlined.Share, L("Share invite link"), { onShare(link) })
        }
        if (canManage) BarIconButton(Icons.Outlined.Close, L("Revoke invite"), onRevoke, tint = colors.error)
    }
}

@Composable
private fun InviteSheet(onDismiss: () -> Unit, onInvite: suspend (String, WorkspaceRole) -> Boolean) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(WorkspaceRole.MEMBER) }
    var isSending by remember { mutableStateOf(false) }
    val clean = email.trim()
    SotuvchiSheet(onDismiss) {
        Text(L("Invite to the team"), style = Theme.type.cardTitle, color = Theme.colors.ink)
        LabeledField(label = L("Email")) {
            InputField(
                value = email,
                onValueChange = { email = it },
                placeholder = "name@shop.uz",
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Email,
                modifier = Modifier.testTag("invite.email"),
            )
        }
        LabeledField(
            label = L("Role"),
            hint = if (role == WorkspaceRole.ADMIN) L("Admins can approve orders, edit products and reply to customers.")
            else L("Members can view orders, chats and products, but not change them."),
        ) {
            PillTabs(
                options = listOf(
                    PillOption(WorkspaceRole.MEMBER, WorkspaceRole.MEMBER.title),
                    PillOption(WorkspaceRole.ADMIN, WorkspaceRole.ADMIN.title),
                ),
                selection = role,
                onSelect = { role = it },
            )
        }
        SButton(
            L("Invite"),
            onClick = {
                scope.launch {
                    isSending = true
                    val ok = onInvite(clean.lowercase(), role)
                    isSending = false
                    if (ok) onDismiss()
                }
            },
            loading = isSending,
            enabled = !isSending && EmailCodeAuth.isValidEmail(clean),
            modifier = Modifier.testTag("invite.submit"),
        )
    }
}
