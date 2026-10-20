package ai.sotuvchi.app.core.model

import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.network.InstantSerializer
import ai.sotuvchi.app.core.network.LenientEnumSerializer
import java.time.Instant
import kotlinx.serialization.Serializable

@Serializable(with = OrderStatusSerializer::class)
enum class OrderStatus(val raw: String) {
    DRAFT("DRAFT"),
    AWAITING_PAYMENT("AWAITING_PAYMENT"),
    PROOF_SUBMITTED("PROOF_SUBMITTED"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED"),
    EXPIRED("EXPIRED"),
    CANCELLED("CANCELLED"),
    UNKNOWN("UNKNOWN");

    val title: String
        get() = when (this) {
            DRAFT -> L("Draft")
            AWAITING_PAYMENT -> L("Awaiting payment")
            PROOF_SUBMITTED -> L("Receipt sent")
            APPROVED -> L("Approved")
            REJECTED -> L("Rejected")
            EXPIRED -> L("Expired")
            CANCELLED -> L("Cancelled")
            UNKNOWN -> L("Unknown")
        }

    /** The server allows approve and reject from these states. */
    val canDecide: Boolean get() = this == AWAITING_PAYMENT || this == PROOF_SUBMITTED || this == EXPIRED

    /** Only an order still waiting for payment can be cancelled. A submitted receipt has to be rejected instead. */
    val canCancel: Boolean get() = this == AWAITING_PAYMENT

    val isOpen: Boolean get() = this == AWAITING_PAYMENT || this == PROOF_SUBMITTED
}

object OrderStatusSerializer :
    LenientEnumSerializer<OrderStatus>("OrderStatus", OrderStatus.entries, OrderStatus.UNKNOWN, { it.raw })

@Serializable(with = DeliveryTypeSerializer::class)
enum class DeliveryType(val raw: String) {
    DELIVERY("DELIVERY"),
    PICKUP("PICKUP"),
    UNKNOWN("UNKNOWN");

    val title: String
        get() = when (this) {
            DELIVERY -> L("Delivery")
            PICKUP -> L("Pickup")
            UNKNOWN -> L("Unknown")
        }
}

object DeliveryTypeSerializer :
    LenientEnumSerializer<DeliveryType>("DeliveryType", DeliveryType.entries, DeliveryType.UNKNOWN, { it.raw })

@Serializable
data class OrderItem(
    val id: String,
    val productId: String? = null,
    val variantId: String? = null,
    val productName: String,
    val variantLabel: String? = null,
    val unitPrice: Int,
    val listPrice: Int? = null,
    val quantity: Int,
) {
    val lineTotal: Int get() = unitPrice * quantity

    /** The customer bargained the price down. */
    val isDiscounted: Boolean get() = listPrice != null && listPrice != unitPrice
}

@Serializable
data class PaymentProof(
    val id: String,
    val orderId: String? = null,
    val contentType: String? = null,
    /** Read from the receipt by an AI model. A hint only; the owner decides. */
    val extractedAmount: Int? = null,
    val extractedNote: String? = null,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    /** Signed, expiring link, or null when storage is down. */
    val url: String? = null,
)

@Serializable
data class EntityRef(val id: String, val name: String? = null)

@Serializable
data class ConversationRef(val id: String, val customerName: String? = null)

@Serializable
data class Order(
    val id: String,
    val number: Int,
    val storeId: String,
    val conversationId: String? = null,
    val status: OrderStatus,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val deliveryType: DeliveryType? = null,
    val address: String? = null,
    val total: Int,
    @Serializable(with = InstantSerializer::class) val holdExpiresAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val decidedAt: Instant? = null,
    val rejectReason: String? = null,
    val externalOrderId: String? = null,
    val externalDemandId: String? = null,
    @Serializable(with = InstantSerializer::class) val externalSyncedAt: Instant? = null,
    val externalSyncError: String? = null,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant? = null,
    val items: List<OrderItem> = emptyList(),
    val proofs: List<PaymentProof> = emptyList(),
    val store: EntityRef? = null,
    val conversation: ConversationRef? = null,
) {
    val displayCustomer: String
        get() {
            val name = customerName ?: conversation?.customerName
            if (name != null && name.isNotBlank()) return name
            return L("Unknown customer")
        }

    val latestProof: PaymentProof?
        get() = proofs.maxByOrNull { it.createdAt ?: Instant.MIN }

    val itemCount: Int get() = items.sumOf { it.quantity }

    val itemsSummary: String
        get() = items.joinToString(", ") { item ->
            val label = item.variantLabel?.let { "${item.productName} ($it)" } ?: item.productName
            if (item.quantity > 1) "$label x${item.quantity}" else label
        }

    /** Difference between what the receipt says and the order total. */
    val receiptMismatch: Int?
        get() {
            val amount = latestProof?.extractedAmount ?: return null
            val difference = amount - total
            return if (difference == 0) null else difference
        }

    fun matches(search: String): Boolean {
        val query = search.trim().lowercase()
        if (query.isEmpty()) return true
        val digits = query.filter { it.isDigit() }
        if (digits.isNotEmpty() && number.toString().contains(digits)) return true
        if (displayCustomer.lowercase().contains(query)) return true
        val phone = customerPhone
        if (phone != null && digits.isNotEmpty() && phone.filter { it.isDigit() }.contains(digits)) return true
        return items.any { it.productName.lowercase().contains(query) }
    }
}

@Serializable
data class OrdersSummary(
    val orders: Int,
    val approvedCount: Int,
    val approvedSum: Int,
    val openCount: Int,
    val rejectedCount: Int? = null,
)

@Serializable
data class AppliedRange(val preset: String? = null, val from: String? = null, val to: String? = null)

@Serializable
data class OrdersResponse(
    val data: List<Order> = emptyList(),
    val summary: OrdersSummary? = null,
    val range: AppliedRange? = null,
)

/**
 * Result of approve and reject. The decision stands even when the customer
 * could not be messaged or the warehouse write failed.
 */
@Serializable
data class OrderDecisionResult(
    val notified: Boolean? = null,
    val notifyError: String? = null,
    val syncError: String? = null,
)

@Serializable
data class RejectBody(val reason: String? = null)

/** Order list filter shown as tabs. */
enum class OrderFilter(val key: String) {
    REVIEW("review"),
    OPEN("open"),
    APPROVED("approved"),
    REJECTED("rejected"),
    ALL("all");

    val title: String
        get() = when (this) {
            REVIEW -> L("To review")
            OPEN -> L("Open")
            APPROVED -> L("Approved")
            REJECTED -> L("Rejected")
            ALL -> L("All")
        }

    val queryValue: String?
        get() = when (this) {
            REVIEW -> OrderStatus.PROOF_SUBMITTED.raw
            OPEN -> "open"
            APPROVED -> OrderStatus.APPROVED.raw
            REJECTED -> OrderStatus.REJECTED.raw
            ALL -> null
        }
}

/** Date range filter shared by orders and the dashboard. */
enum class RangePreset(val raw: String) {
    TODAY("today"),
    YESTERDAY("yesterday"),
    WEEK("7d"),
    THIRTY_DAYS("30d"),
    MONTH("month"),
    ALL("all");

    val title: String
        get() = when (this) {
            TODAY -> L("Today")
            YESTERDAY -> L("Yesterday")
            WEEK -> L("7 days")
            THIRTY_DAYS -> L("30 days")
            MONTH -> L("This month")
            ALL -> L("All time")
        }

    val comparisonLabel: String?
        get() = when (this) {
            TODAY -> L("vs yesterday")
            YESTERDAY -> L("vs the day before")
            WEEK -> L("vs previous 7 days")
            THIRTY_DAYS -> L("vs previous 30 days")
            MONTH -> L("vs same period last month")
            ALL -> null
        }
}
