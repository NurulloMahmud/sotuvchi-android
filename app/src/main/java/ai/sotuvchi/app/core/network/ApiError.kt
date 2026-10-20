package ai.sotuvchi.app.core.network

import ai.sotuvchi.app.core.l10n.L
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException

/**
 * Errors from the Sotuvchi API. The server's `error` text is English and meant
 * for developers, so user-facing copy comes from [userMessage], with the
 * server text kept for cases where it is the only useful explanation (for
 * example Instagram refusing a message).
 */
sealed class ApiError(message: String? = null) : Exception(message) {
    data object Unauthorized : ApiError()
    data class Forbidden(val detail: String?) : ApiError(detail)
    data class NotFound(val detail: String?) : ApiError(detail)
    data class Conflict(val detail: String?) : ApiError(detail)
    data class Invalid(val detail: String?) : ApiError(detail)
    data object TooLarge : ApiError()
    data object UnsupportedMedia : ApiError()
    data class Upstream(val detail: String?) : ApiError(detail)
    data class Server(val status: Int, val detail: String?) : ApiError(detail)
    data object Offline : ApiError()
    data object TimedOut : ApiError()
    data class Transport(val detail: String) : ApiError(detail)
    data class Decoding(val detail: String) : ApiError(detail)

    val serverMessage: String?
        get() = when (this) {
            is Forbidden -> detail
            is NotFound -> detail
            is Conflict -> detail
            is Invalid -> detail
            is Upstream -> detail
            is Server -> detail
            else -> null
        }

    val userMessage: String
        get() = when (this) {
            Unauthorized -> L("Your session has ended. Please sign in again.")
            is Forbidden -> L("Only the owner or an admin can do this.")
            is NotFound -> L("This item no longer exists.")
            is Conflict -> L("This changed in the meantime. Pull to refresh and try again.")
            is Invalid -> detail ?: L("Please check what you entered.")
            TooLarge -> L("The picture is too large. The limit is 5 MB.")
            UnsupportedMedia -> L("Use a JPEG, PNG or WebP picture.")
            is Upstream -> detail?.let { L("The connected service refused: {reason}", mapOf("reason" to it)) }
                ?: L("A connected service is not responding. Try again later.")
            is Server -> L("Something went wrong on our side. Try again in a moment.")
            Offline -> L("You are offline. Check your connection.")
            TimedOut -> L("The server took too long to answer. Try again.")
            is Transport -> L("Could not reach the server. Try again.")
            is Decoding -> L("The server sent something unexpected. Update the app or try again later.")
        }

    companion object {
        fun from(status: Int, message: String?): ApiError = when (status) {
            401 -> Unauthorized
            403 -> Forbidden(message)
            404 -> NotFound(message)
            409 -> Conflict(message)
            400, 422 -> Invalid(message)
            410 -> NotFound(message)
            413 -> TooLarge
            415 -> UnsupportedMedia
            502, 503 -> Upstream(message)
            else -> Server(status, message)
        }

        fun from(io: IOException): ApiError = when (io) {
            is SocketTimeoutException -> TimedOut
            is UnknownHostException, is ConnectException -> Offline
            else -> if (io.message?.contains("timeout", ignoreCase = true) == true) TimedOut
            else Transport(io.message ?: io.javaClass.simpleName)
        }
    }
}

/** Readable copy for any error the UI shows. */
val Throwable.userMessage: String
    get() = when (this) {
        is ApiError -> userMessage
        is IOException -> ApiError.from(this).userMessage
        else -> {
            android.util.Log.w("SotuvchiError", "Unexpected error", this)
            message ?: L("Something went wrong on our side. Try again in a moment.")
        }
    }

val Throwable.isCancellation: Boolean get() = this is CancellationException
