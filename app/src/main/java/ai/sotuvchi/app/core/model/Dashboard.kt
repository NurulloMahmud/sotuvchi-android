package ai.sotuvchi.app.core.model

import ai.sotuvchi.app.core.format.DateText
import ai.sotuvchi.app.core.network.InstantSerializer
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.Serializable

/**
 * The parts of `/api/dashboard/stats` a seller app needs. Every field is
 * optional so a new or empty workspace still decodes.
 */
@Serializable
data class DashboardStats(
    val userName: String? = null,
    val contactsCount: Int? = null,
    val workspace: WorkspaceInfo? = null,
    val billingEnabled: Boolean? = null,
    val instagramAccounts: List<InstagramAccountSummary>? = null,
    val totalAutomations: Int? = null,
    val activeAutomations: Int? = null,
    val range: AppliedRange? = null,
    val period: CampaignPeriod? = null,
    val previous: CampaignPeriod? = null,
    val sales: SalesStats? = null,
)

@Serializable
data class WorkspaceInfo(
    val name: String? = null,
    val dmsSentThisPeriod: Int? = null,
    @Serializable(with = InstantSerializer::class) val planExpiresAt: Instant? = null,
)

@Serializable
data class InstagramAccountSummary(
    val id: String,
    val username: String? = null,
    val instagramId: String? = null,
    val name: String? = null,
    val provider: String? = null,
    @Serializable(with = InstantSerializer::class) val tokenExpiresAt: Instant? = null,
    val webhookSubscribed: Boolean? = null,
)

@Serializable
data class CampaignPeriod(
    val dmsSent: Int? = null,
    val dmsSkipped: Int? = null,
    val dmsFailed: Int? = null,
    val clicks: Int? = null,
    val ctr: Double? = null,
)

@Serializable
data class SalesTotals(
    val orders: Int? = null,
    val approvedCount: Int? = null,
    val approvedSum: Int? = null,
    val openCount: Int? = null,
)

@Serializable
data class SalesStats(
    val byStatus: List<StatusCount>? = null,
    val orders: Int? = null,
    val approvedCount: Int? = null,
    val approvedSum: Int? = null,
    val openCount: Int? = null,
    /** Null for the all-time range: nothing to compare with. */
    val previous: SalesTotals? = null,
    val daily: List<DailySales>? = null,
    val topProducts: List<TopProduct>? = null,
) {
    val averageOrder: Int?
        get() {
            val sum = approvedSum ?: return null
            val count = approvedCount ?: return null
            return if (count > 0) sum / count else null
        }
}

@Serializable
data class StatusCount(val status: OrderStatus, val count: Int, val sum: Int? = null)

@Serializable
data class DailySales(
    val date: String,
    val orders: Int? = null,
    val approved: Int? = null,
    val revenue: Int? = null,
) {
    val day: LocalDate? get() = DateText.parseDay(date)
}

@Serializable
data class TopProduct(val name: String, val quantity: Int? = null, val revenue: Int? = null)

/** Percentage change from a previous value, or null when there is nothing to compare with. */
object Trend {
    fun change(current: Int?, previous: Int?): Double? {
        if (current == null || previous == null || previous <= 0) return null
        return (current.toDouble() - previous.toDouble()) / previous.toDouble() * 100
    }

    fun label(change: Double): String {
        val rounded = Math.round(change).toInt()
        return if (rounded > 0) "+$rounded%" else "$rounded%"
    }
}
