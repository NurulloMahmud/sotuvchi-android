package ai.sotuvchi.app.core.model

import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.network.InstantSerializer
import ai.sotuvchi.app.core.network.LenientEnumSerializer
import java.time.Instant
import kotlinx.serialization.Serializable

@Serializable(with = WorkspaceRoleSerializer::class)
enum class WorkspaceRole(val raw: String) {
    OWNER("OWNER"),
    ADMIN("ADMIN"),
    MEMBER("MEMBER"),
    UNKNOWN("UNKNOWN");

    val title: String
        get() = when (this) {
            OWNER -> L("Owner")
            ADMIN -> L("Admin")
            MEMBER -> L("Member")
            UNKNOWN -> L("Unknown")
        }

    /** Owners and admins can approve orders, edit products and reply in chats. */
    val canManage: Boolean get() = this == OWNER || this == ADMIN

    companion object {
        fun fromRaw(raw: String?): WorkspaceRole? = entries.firstOrNull { it.raw == raw }
    }
}

object WorkspaceRoleSerializer :
    LenientEnumSerializer<WorkspaceRole>("WorkspaceRole", WorkspaceRole.entries, WorkspaceRole.UNKNOWN, { it.raw })

@Serializable
data class MemberUser(val id: String, val email: String? = null, val name: String? = null)

@Serializable
data class WorkspaceMember(
    /** The membership id, used for update and delete. Not the user id. */
    val id: String,
    val role: WorkspaceRole,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    val user: MemberUser,
) {
    val displayName: String
        get() {
            val name = user.name
            if (!name.isNullOrEmpty()) return name
            return user.email ?: L("Unknown member")
        }
}

@Serializable
data class WorkspaceInvitation(
    val id: String,
    val email: String,
    val role: WorkspaceRole,
    @Serializable(with = InstantSerializer::class) val expiresAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    val inviteUrl: String? = null,
)

@Serializable
data class TeamPayload(
    val currentUserRole: WorkspaceRole,
    val members: List<WorkspaceMember> = emptyList(),
    val invitations: List<WorkspaceInvitation> = emptyList(),
)

@Serializable
data class InviteBody(val email: String, val role: String)

@Serializable
data class RoleChangeBody(val memberId: String, val role: String)

@Serializable
data class RemoveMemberBody(val memberId: String? = null, val invitationId: String? = null)
