package ai.sotuvchi.app.core.model

import ai.sotuvchi.app.core.network.InstantSerializer
import ai.sotuvchi.app.core.network.LooseIntSerializer
import java.time.Duration
import java.time.Instant
import kotlinx.serialization.Serializable

/** `DELETE /api/account` is irreversible, so the server only acts on an explicit `{ "confirm": true }`. */
@Serializable
data class DeleteAccountBody(val confirm: Boolean = true)

/**
 * What the server removed. Workspaces the user owned alone are deleted;
 * shared ones go to their longest-standing other member.
 */
@Serializable
data class AccountDeletionResult(
    @Serializable(with = LooseIntSerializer::class) val deletedWorkspaces: Int = 0,
    @Serializable(with = LooseIntSerializer::class) val transferredWorkspaces: Int = 0,
    @Serializable(with = LooseIntSerializer::class) val disconnectedInstagramAccounts: Int = 0,
)

/**
 * The workspace plan, from `workspace.planExpiresAt` on the dashboard. The app
 * only shows it: plans are paid for on the web.
 */
sealed class PlanStatus {
    /** Paid until this date. */
    data class Active(val until: Instant) : PlanStatus()

    /** Was paid, ended on this date. */
    data class Ended(val on: Instant) : PlanStatus()

    /** Never paid. */
    data object None : PlanStatus()

    val isActive: Boolean get() = this is Active

    /** An active plan with less than a week left. */
    fun endsSoon(now: Instant = Instant.now()): Boolean {
        val active = this as? Active ?: return false
        return Duration.between(now, active.until) < Duration.ofDays(7)
    }

    companion object {
        fun of(expiresAt: Instant?, now: Instant = Instant.now()): PlanStatus = when {
            expiresAt == null -> None
            expiresAt.isAfter(now) -> Active(expiresAt)
            else -> Ended(expiresAt)
        }
    }
}

/** `POST /api/mobile/auth/web-handoff`: the page to open on the website. */
@Serializable
data class WebHandoffBody(val path: String)

/**
 * A one-time link that signs the browser in and opens the page. It works once,
 * for about a minute, so it is opened right away and never stored.
 */
@Serializable
data class WebHandoff(val url: String, val expiresInSeconds: Int? = null)
