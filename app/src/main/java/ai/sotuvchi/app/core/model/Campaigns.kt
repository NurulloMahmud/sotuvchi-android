package ai.sotuvchi.app.core.model

import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.network.InstantSerializer
import ai.sotuvchi.app.core.network.LenientEnumSerializer
import java.time.Instant
import kotlinx.serialization.Serializable

@Serializable(with = DmStatusSerializer::class)
enum class DmStatus(val raw: String) {
    PENDING("PENDING"),
    SENT("SENT"),
    FAILED("FAILED"),
    SKIPPED_DEDUP("SKIPPED_DEDUP"),
    SKIPPED_RATE_LIMIT("SKIPPED_RATE_LIMIT"),
    SKIPPED_PLAN_LIMIT("SKIPPED_PLAN_LIMIT"),
    SKIPPED_NO_MATCH("SKIPPED_NO_MATCH"),
    UNKNOWN("UNKNOWN");

    val title: String
        get() = when (this) {
            PENDING -> L("Pending")
            SENT -> L("Sent")
            FAILED -> L("Failed")
            SKIPPED_DEDUP -> L("Already messaged")
            SKIPPED_RATE_LIMIT -> L("Rate limited")
            SKIPPED_PLAN_LIMIT -> L("Plan limit")
            SKIPPED_NO_MATCH -> L("No match")
            UNKNOWN -> L("Unknown")
        }

    /** Which badge tone the log row uses. */
    val tone: String
        get() = when (this) {
            SENT -> "success"
            FAILED -> "error"
            PENDING -> "neutral"
            else -> "warning"
        }
}

object DmStatusSerializer :
    LenientEnumSerializer<DmStatus>("DmStatus", DmStatus.entries, DmStatus.UNKNOWN, { it.raw })

@Serializable
data class KeywordCount(val keyword: String, val count: Int)

@Serializable
data class CampaignAnalytics(
    val sent: Int? = null,
    val skipped: Int? = null,
    val failed: Int? = null,
    val clicks: Int? = null,
    val ctr: Double? = null,
    val topKeywords: List<KeywordCount>? = null,
)

@Serializable
data class CampaignAccount(val username: String? = null)

@Serializable
data class Campaign(
    val id: String,
    val name: String = "",
    val goal: String? = null,
    val postId: String? = null,
    val postUrl: String? = null,
    val pendingNextReel: Boolean? = null,
    val matchAnyPost: Boolean? = null,
    val keywords: List<String> = emptyList(),
    val matchAnyWord: Boolean? = null,
    val dmTriggerEnabled: Boolean? = null,
    val dmMessage: String? = null,
    val openingDmEnabled: Boolean? = null,
    val openingDmMessage: String? = null,
    val requireFollow: Boolean? = null,
    val followUpEnabled: Boolean? = null,
    val publicReplyEnabled: Boolean? = null,
    val isActive: Boolean = false,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    val instagramAccount: CampaignAccount? = null,
    val analytics: CampaignAnalytics? = null,
    val reportUrl: String? = null,
) {
    val targetDescription: String
        get() = when {
            matchAnyPost == true -> L("Any post or reel")
            pendingNextReel == true -> L("Next reel you post")
            else -> L("One post")
        }

    val triggerDescription: String
        get() = if (matchAnyWord == true) L("Any comment") else keywords.joinToString(", ")
}

@Serializable
data class CampaignActiveBody(val isActive: Boolean)

@Serializable
data class LogAutomation(val name: String? = null)

@Serializable
data class DmLog(
    val id: String,
    val commenterName: String? = null,
    val commentText: String? = null,
    val matchedKeyword: String? = null,
    val status: DmStatus,
    val errorMessage: String? = null,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    val automation: LogAutomation? = null,
    val instagramAccount: CampaignAccount? = null,
)

@Serializable
data class LogPagination(val page: Int, val limit: Int, val total: Int, val totalPages: Int)

@Serializable
data class LogsPage(val logs: List<DmLog> = emptyList(), val pagination: LogPagination)
