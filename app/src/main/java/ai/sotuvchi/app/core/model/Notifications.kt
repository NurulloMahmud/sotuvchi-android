package ai.sotuvchi.app.core.model

import ai.sotuvchi.app.core.format.Money
import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.network.InstantSerializer
import ai.sotuvchi.app.core.network.LenientEnumSerializer
import ai.sotuvchi.app.core.network.looseInt
import java.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable(with = NotificationTypeSerializer::class)
enum class NotificationType(val raw: String) {
    NEW_ORDER("NEW_ORDER"),
    PAYMENT_PROOF("PAYMENT_PROOF"),
    HANDOFF("HANDOFF"),
    CUSTOMER_MESSAGE("CUSTOMER_MESSAGE"),
    ORDER_SYNC_FAILED("ORDER_SYNC_FAILED"),
    UNKNOWN("UNKNOWN"),
}

object NotificationTypeSerializer : LenientEnumSerializer<NotificationType>(
    "NotificationType", NotificationType.entries, NotificationType.UNKNOWN, { it.raw },
)

/** Free-form notification payload. Every key is optional. */
@Serializable(with = NotificationDataSerializer::class)
data class NotificationData(
    val orderId: String? = null,
    val conversationId: String? = null,
    val number: Int? = null,
    val customer: String? = null,
    val total: Int? = null,
    val receiptAmount: Int? = null,
    val reason: String? = null,
    val text: String? = null,
)

object NotificationDataSerializer : KSerializer<NotificationData> {
    override val descriptor: SerialDescriptor =
        kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
            "NotificationData",
            kotlinx.serialization.descriptors.PrimitiveKind.STRING,
        )

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content

    override fun deserialize(decoder: Decoder): NotificationData {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        val obj = element as? JsonObject ?: return NotificationData()
        return NotificationData(
            orderId = obj.string("orderId"),
            conversationId = obj.string("conversationId"),
            number = looseInt(obj["number"]),
            customer = obj.string("customer"),
            total = looseInt(obj["total"]),
            receiptAmount = looseInt(obj["receiptAmount"]),
            reason = obj.string("reason"),
            text = obj.string("text"),
        )
    }

    override fun serialize(encoder: Encoder, value: NotificationData) {
        val obj = buildJsonObject {
            value.orderId?.let { put("orderId", it) }
            value.conversationId?.let { put("conversationId", it) }
            value.number?.let { put("number", it) }
            value.customer?.let { put("customer", it) }
            value.total?.let { put("total", it) }
            value.receiptAmount?.let { put("receiptAmount", it) }
            value.reason?.let { put("reason", it) }
            value.text?.let { put("text", it) }
        }
        encoder.encodeString(obj.toString())
    }
}

@Serializable
data class AppNotification(
    val id: String,
    val type: NotificationType,
    val data: NotificationData = NotificationData(),
    val storeName: String? = null,
    val read: Boolean = false,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
) {
    /** Where tapping the notification leads. */
    val destination: AppDestination?
        get() = when (type) {
            NotificationType.NEW_ORDER, NotificationType.PAYMENT_PROOF, NotificationType.ORDER_SYNC_FAILED ->
                data.orderId?.let { AppDestination.OrderDest(it) }
            NotificationType.HANDOFF, NotificationType.CUSTOMER_MESSAGE ->
                data.conversationId?.let { AppDestination.ChatDest(it) }
            NotificationType.UNKNOWN ->
                data.orderId?.let { AppDestination.OrderDest(it) }
                    ?: data.conversationId?.let { AppDestination.ChatDest(it) }
        }

    /**
     * The server sends no display text, so title and body are built here from
     * the type and data, using the same copy as the web app.
     */
    val title: String
        get() {
            val number = data.number?.toString() ?: "?"
            return when (type) {
                NotificationType.NEW_ORDER -> L("New order #{number}", mapOf("number" to number))
                NotificationType.PAYMENT_PROOF -> L("Payment proof for order #{number}", mapOf("number" to number))
                NotificationType.HANDOFF -> L("A customer needs you")
                NotificationType.CUSTOMER_MESSAGE -> L("New message while the AI is paused")
                NotificationType.ORDER_SYNC_FAILED ->
                    L("Order #{number} was not saved in your accounting system", mapOf("number" to number))
                NotificationType.UNKNOWN -> L("Notification")
            }
        }

    val body: String
        get() {
            val customer = data.customer ?: L("Unknown customer")
            val total = data.total?.let { Money.format(it) } ?: ""
            return when (type) {
                NotificationType.NEW_ORDER ->
                    L("{customer} · {total}. Waiting for their payment.", mapOf("customer" to customer, "total" to total))
                NotificationType.PAYMENT_PROOF -> {
                    val receipt = data.receiptAmount
                    if (receipt != null) {
                        L(
                            "{customer} · {total}. Receipt shows {receipt}.",
                            mapOf("customer" to customer, "total" to total, "receipt" to Money.format(receipt)),
                        )
                    } else {
                        L("{customer} · {total}. Check the receipt and approve.", mapOf("customer" to customer, "total" to total))
                    }
                }
                NotificationType.HANDOFF ->
                    L(
                        "{customer}: {reason}",
                        mapOf(
                            "customer" to customer,
                            "reason" to (data.reason?.let { ChatText.attentionReason(it) } ?: L("asked for a human")),
                        ),
                    )
                NotificationType.CUSTOMER_MESSAGE ->
                    L("{customer}: {message}", mapOf("customer" to customer, "message" to ChatText.preview(data.text)))
                NotificationType.ORDER_SYNC_FAILED ->
                    listOf<String?>(customer, total, data.reason).filter { !it.isNullOrEmpty() }.joinToString(" · ")
                NotificationType.UNKNOWN -> customer
            }
        }
}

@Serializable
data class NotificationBadges(val ordersToReview: Int = 0, val chatsNeedingYou: Int = 0)

@Serializable
data class NotificationFeed(
    val unreadCount: Int = 0,
    val badges: NotificationBadges = NotificationBadges(),
    val items: List<AppNotification> = emptyList(),
)

@Serializable
data class MarkReadBody(val ids: List<String>? = null)

/** Somewhere the app can navigate to from a notification or a link. */
sealed class AppDestination {
    data class OrderDest(val id: String) : AppDestination()
    data class ChatDest(val id: String) : AppDestination()
}
