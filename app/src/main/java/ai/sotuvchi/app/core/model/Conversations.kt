package ai.sotuvchi.app.core.model

import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.network.InstantSerializer
import ai.sotuvchi.app.core.network.LenientEnumSerializer
import java.time.Duration
import java.time.Instant
import kotlinx.serialization.Serializable

@Serializable(with = ChatRoleSerializer::class)
enum class ChatRole(val raw: String) {
    CUSTOMER("CUSTOMER"),
    AI("AI"),
    OWNER("OWNER"),
    UNKNOWN("UNKNOWN"),
}

object ChatRoleSerializer :
    LenientEnumSerializer<ChatRole>("ChatRole", ChatRole.entries, ChatRole.UNKNOWN, { it.raw })

@Serializable(with = AttachmentKindSerializer::class)
enum class AttachmentKind(val raw: String) {
    IMAGE("IMAGE"),
    VIDEO("VIDEO"),
    AUDIO("AUDIO"),
    FILE("FILE"),
    LINK("LINK"),
    UNKNOWN("UNKNOWN"),
}

object AttachmentKindSerializer :
    LenientEnumSerializer<AttachmentKind>("AttachmentKind", AttachmentKind.entries, AttachmentKind.UNKNOWN, { it.raw })

@Serializable
data class LastMessage(
    val role: ChatRole,
    val text: String? = null,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
)

@Serializable
data class Conversation(
    val id: String,
    val storeId: String? = null,
    val customerId: String? = null,
    val customerName: String? = null,
    val aiPaused: Boolean = false,
    val needsAttention: Boolean = false,
    val attentionReason: String? = null,
    @Serializable(with = InstantSerializer::class) val lastCustomerMessageAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant? = null,
    val store: EntityRef? = null,
    val lastMessage: LastMessage? = null,
) {
    val displayName: String get() = ChatText.customerTitle(customerName, customerId)

    val activityDate: Instant? get() = lastMessage?.createdAt ?: updatedAt ?: createdAt
}

@Serializable
data class Attachment(
    val id: String,
    val position: Int? = null,
    val kind: AttachmentKind,
    val contentType: String? = null,
    val sizeBytes: Int? = null,
    /** Signed expiring link, or the plain shared address for LINK. */
    val url: String? = null,
)

@Serializable
data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String? = null,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    val attachments: List<Attachment> = emptyList(),
)

@Serializable
data class LinkedOrder(
    val id: String,
    val number: Int,
    val status: OrderStatus,
    val total: Int,
)

@Serializable
data class ConversationDetail(
    val id: String,
    val storeId: String? = null,
    val customerId: String? = null,
    val customerName: String? = null,
    val aiPaused: Boolean = false,
    val needsAttention: Boolean = false,
    val attentionReason: String? = null,
    @Serializable(with = InstantSerializer::class) val lastCustomerMessageAt: Instant? = null,
    val store: EntityRef? = null,
    val messages: List<ChatMessage> = emptyList(),
    val orders: List<LinkedOrder> = emptyList(),
) {
    val displayName: String get() = ChatText.customerTitle(customerName, customerId)
}

@Serializable
data class ConversationPatch(val aiPaused: Boolean? = null, val needsAttention: Boolean? = null)

@Serializable
data class SendMessageBody(val text: String)

/**
 * Instagram only allows replies within 24 hours of the customer's last
 * message. The composer is disabled outside the window; the server still
 * has the final word and answers 502 if it closed in the meantime.
 */
object ReplyWindow {
    val length: Duration = Duration.ofHours(24)
    const val MAX_MESSAGE_LENGTH = 950

    fun isOpen(lastCustomerMessageAt: Instant?, now: Instant = Instant.now()): Boolean {
        val last = lastCustomerMessageAt ?: return false
        return Duration.between(last, now) < length
    }

    /** Time left before the window closes, or null when closed. */
    fun remaining(lastCustomerMessageAt: Instant?, now: Instant = Instant.now()): Duration? {
        val last = lastCustomerMessageAt ?: return null
        val left = length.minus(Duration.between(last, now))
        return if (left > Duration.ZERO) left else null
    }
}

/** How the backend's chat values read in the app, matching the web inbox. */
object ChatText {
    /** A message that is only media is stored with one of these as its text. */
    val placeholders: Map<String, AttachmentKind> = mapOf(
        "[photo]" to AttachmentKind.IMAGE,
        "[video]" to AttachmentKind.VIDEO,
        "[voice message]" to AttachmentKind.AUDIO,
        "[file]" to AttachmentKind.FILE,
        "[link]" to AttachmentKind.LINK,
    )

    fun isPlaceholder(text: String?): Boolean = text != null && placeholders.containsKey(text.trim())

    fun label(kind: AttachmentKind): String = when (kind) {
        AttachmentKind.IMAGE -> L("Photo")
        AttachmentKind.VIDEO -> L("Video")
        AttachmentKind.AUDIO -> L("Voice message")
        AttachmentKind.LINK -> L("Shared link")
        else -> L("File")
    }

    /** Message text for a list or a notification: placeholders become a label. */
    fun preview(text: String?): String {
        if (text.isNullOrEmpty()) return L("Attachment")
        placeholders[text.trim()]?.let { return label(it) }
        return text
    }

    /** The customer's Instagram name, or "Customer ...6661" from their id, as on the web. */
    fun customerTitle(name: String?, customerId: String?): String {
        if (name != null && name.isNotBlank()) return name
        if (!customerId.isNullOrEmpty()) return "${L("Customer")} …${customerId.takeLast(4)}"
        return L("Unknown customer")
    }

    /**
     * Reasons the system sets are codes; a handover reason is free text
     * written by the AI and is shown as it is.
     */
    fun attentionReason(reason: String?): String = when (reason) {
        "payment_proof" -> L("A payment proof is waiting for you")
        "customer_message" -> L("The customer wrote while the AI was paused")
        "daily_ai_limit" -> L("The daily limit of AI replies was reached")
        null -> L("The customer needs you")
        else -> reason.ifEmpty { L("The customer needs you") }
    }

    /** Short form for a badge in the chat list. */
    fun attentionBadge(reason: String?): String = when (reason) {
        "payment_proof" -> L("Receipt to check")
        "customer_message" -> L("New message")
        "daily_ai_limit" -> L("AI limit reached")
        else -> L("Needs you")
    }
}
