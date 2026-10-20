package ai.sotuvchi.app.app

import ai.sotuvchi.app.core.l10n.AppLanguage
import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.l10n.Localizer
import ai.sotuvchi.app.core.model.AppDestination
import ai.sotuvchi.app.core.model.AppNotification
import ai.sotuvchi.app.core.model.DashboardStats
import ai.sotuvchi.app.core.model.NotificationBadges
import ai.sotuvchi.app.core.model.NotificationFeed
import ai.sotuvchi.app.core.model.OrderFilter
import ai.sotuvchi.app.core.model.PlanStatus
import ai.sotuvchi.app.core.model.RangePreset
import ai.sotuvchi.app.core.model.Store
import ai.sotuvchi.app.core.model.WorkspaceRole
import ai.sotuvchi.app.core.network.ApiError
import ai.sotuvchi.app.core.network.dashboard
import ai.sotuvchi.app.core.network.deleteAccount
import ai.sotuvchi.app.core.network.isCancellation
import ai.sotuvchi.app.core.network.markNotificationsRead
import ai.sotuvchi.app.core.network.notifications
import ai.sotuvchi.app.core.network.signedInWebUrl
import ai.sotuvchi.app.core.network.stores
import ai.sotuvchi.app.core.network.team
import ai.sotuvchi.app.core.network.userMessage
import ai.sotuvchi.app.core.auth.EmailCodeAuth
import ai.sotuvchi.app.design.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Phase { Launching, SignedOut, SignedIn }

enum class AppTab { Home, Orders, Chats, Catalog, More }

/** One-shot instructions for the navigation shell. */
sealed class NavCommand {
    data class SelectTab(val tab: AppTab) : NavCommand()
    data class Open(val destination: AppDestination) : NavCommand()
}

/** One-shot instructions for the activity (things only a Context can do). */
sealed class UiEvent {
    data class OpenUrl(val url: String) : UiEvent()
}

data class AppState(
    val phase: Phase = Phase.Launching,
    val role: WorkspaceRole = WorkspaceRole.MEMBER,
    val email: String? = null,
    val userName: String? = null,
    val workspaceName: String? = null,
    val feed: NotificationFeed? = null,
    val stores: List<Store> = emptyList(),
    val toast: Toast? = null,
    /** The account was created by the sign-in that just happened. */
    val isNewAccount: Boolean = false,
    /** Billing is switched on for this server; without it there is no plan. */
    val billingEnabled: Boolean = false,
    val planExpiresAt: Instant? = null,
) {
    val canManage: Boolean get() = role.canManage
    val unreadCount: Int get() = feed?.unreadCount ?: 0
    val badges: NotificationBadges get() = feed?.badges ?: NotificationBadges()
    val plan: PlanStatus get() = PlanStatus.of(planExpiresAt)
}

/**
 * App-wide state: the session, the user's role, notification badges and the
 * shared store list.
 */
class AppModel(val container: AppContainer) : ViewModel() {
    private val api = container.api
    private val auth = container.auth
    private val config = container.config
    private val kv = container.kv

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val navChannel = Channel<NavCommand>(Channel.BUFFERED)
    val navCommands = navChannel.receiveAsFlow()

    private val eventChannel = Channel<UiEvent>(Channel.BUFFERED)
    val uiEvents = eventChannel.receiveAsFlow()

    /** Orders tab filter, so "Review orders" on Home lands on the right tab. */
    private val _ordersFilter = MutableStateFlow(OrderFilter.REVIEW)
    val ordersFilter: StateFlow<OrderFilter> = _ordersFilter.asStateFlow()

    private var pollJob: Job? = null
    private var seenNotificationIds: Set<String> = emptySet()
    private var hasLoadedFeedOnce = false
    private var isOpeningWeb = false
    private var toastJob: Job? = null

    val canManage: Boolean get() = _state.value.canManage

    init {
        api.onUnauthorized = { sessionExpired() }
    }

    // region Language

    fun setLanguage(language: AppLanguage) {
        Localizer.set(language)
        viewModelScope.launch { kv.set(LANGUAGE_KEY, language.code) }
    }

    // endregion

    // region Session

    suspend fun bootstrap() {
        if (_state.value.phase != Phase.Launching) return
        val token = container.tokenStore.read()
        if (token == null) {
            _state.update { it.copy(phase = Phase.SignedOut, email = kv.get(config.emailKey)) }
            return
        }
        api.setSessionToken(token)
        val email = kv.get(config.emailKey)
        val role = WorkspaceRole.fromRaw(kv.get(config.roleKey)) ?: WorkspaceRole.MEMBER
        _state.update { it.copy(email = email, role = role) }
        try {
            loadAccount()
            _state.update { it.copy(phase = Phase.SignedIn) }
        } catch (error: ApiError.Unauthorized) {
            clearSession()
        } catch (error: Exception) {
            if (error.isCancellation) throw error
            // Offline or server trouble: keep the saved session and let the
            // screens show their own errors and retry.
            _state.update { it.copy(phase = Phase.SignedIn) }
        }
    }

    /** Emails a 6-digit sign-in code. Works for new and existing accounts. */
    suspend fun requestSignInCode(email: String): EmailCodeAuth.CodeRequest {
        val result = auth.requestCode(email)
        val normalized = EmailCodeAuth.normalize(email)
        kv.set(config.emailKey, normalized)
        _state.update { it.copy(email = normalized) }
        return result
    }

    /** Exchanges the emailed code for a session. */
    suspend fun verifySignInCode(email: String, code: String) {
        val signedIn = auth.verify(email, code)
        signedIn.user?.email?.let { confirmed ->
            _state.update { it.copy(email = confirmed) }
            kv.set(config.emailKey, confirmed)
        }
        finishSignIn(signedIn.token)
        _state.update { it.copy(isNewAccount = signedIn.isNewUser) }
    }

    /**
     * Finishes sign-in with a session token, from a verified code or from the
     * web password screen (the same kind of database session).
     */
    suspend fun finishSignIn(token: String) {
        api.setSessionToken(token)
        try {
            loadAccount()
        } catch (error: Exception) {
            api.setSessionToken(null)
            throw error
        }
        container.tokenStore.write(token)
        _state.update { it.copy(phase = Phase.SignedIn) }
    }

    /** Forgets the session right away, then ends it on the server. */
    suspend fun signOut() {
        val token = api.sessionToken
        clearSession()
        if (token != null) auth.logout(token)
    }

    /**
     * Deletes the account on the server, then forgets everything this device
     * kept about it. On failure nothing changes and the user stays signed in.
     */
    suspend fun deleteAccount() {
        api.deleteAccount()
        clearSession()
        kv.remove(config.emailKey)
        kv.remove(config.roleKey)
        _state.update { it.copy(email = null) }
        container.imageLoader?.memoryCache?.clear()
        show(Toast(Toast.Kind.Success, L("Your account was deleted.")))
    }

    private fun sessionExpired() {
        if (_state.value.phase != Phase.SignedIn) return
        viewModelScope.launch {
            clearSession()
            show(Toast(Toast.Kind.Warning, L("Your session has ended. Please sign in again.")))
        }
    }

    private suspend fun clearSession() {
        stopPolling()
        container.tokenStore.clear()
        api.setSessionToken(null)
        seenNotificationIds = emptySet()
        hasLoadedFeedOnce = false
        _ordersFilter.value = OrderFilter.REVIEW
        _state.update {
            AppState(phase = Phase.SignedOut, email = it.email, toast = it.toast)
        }
    }

    /** Learns the role, which drives what the UI offers. */
    private suspend fun loadAccount() {
        val team = api.team()
        kv.set(config.roleKey, team.currentUserRole.raw)
        val email = _state.value.email
        val me = team.members.firstOrNull { it.user.email?.lowercase() == email }
        _state.update { it.copy(role = team.currentUserRole, userName = me?.user?.name ?: it.userName) }
        viewModelScope.launch { refreshStores() }
    }

    // endregion

    // region Shared data

    suspend fun refreshStores() {
        try {
            val stores = api.stores()
            _state.update { it.copy(stores = stores) }
        } catch (error: Exception) {
            if (error.isCancellation) throw error
        }
    }

    /** Workspace name, user name and plan from the dashboard, which Home loads anyway. */
    fun apply(stats: DashboardStats) {
        _state.update { current ->
            current.copy(
                workspaceName = stats.workspace?.name ?: current.workspaceName,
                userName = if (current.userName == null && !stats.userName.isNullOrEmpty()) stats.userName else current.userName,
                billingEnabled = stats.billingEnabled ?: false,
                planExpiresAt = stats.workspace?.planExpiresAt,
            )
        }
    }

    /** Re-reads the plan, for example after the owner paid on the web. */
    suspend fun refreshPlan() {
        val stats = try {
            api.dashboard(RangePreset.TODAY)
        } catch (error: Exception) {
            if (error.isCancellation) throw error
            return
        }
        apply(stats)
    }

    fun store(id: String?): Store? = id?.let { wanted -> _state.value.stores.firstOrNull { it.id == wanted } }

    fun dismissNewAccount() = _state.update { it.copy(isNewAccount = false) }

    // endregion

    // region Website

    /**
     * Opens a page of the website in the browser, already signed in, so the
     * user carries on there without logging in again.
     */
    fun openWeb(path: String) {
        // The link is made on the server, so ignore taps while one is on the way.
        if (isOpeningWeb) return
        isOpeningWeb = true
        viewModelScope.launch {
            try {
                val url = api.signedInWebUrl(path)
                eventChannel.send(UiEvent.OpenUrl(url))
            } finally {
                isOpeningWeb = false
            }
        }
    }

    /** A public page (privacy, terms): a plain link, no session. */
    fun openPublic(path: String) {
        viewModelScope.launch { eventChannel.send(UiEvent.OpenUrl(api.webPage(path).toString())) }
    }

    // endregion

    // region Navigation

    fun open(destination: AppDestination) {
        navChannel.trySend(NavCommand.Open(destination))
    }

    fun selectTab(tab: AppTab) {
        navChannel.trySend(NavCommand.SelectTab(tab))
    }

    fun showOrders(filter: OrderFilter) {
        _ordersFilter.value = filter
        selectTab(AppTab.Orders)
    }

    fun setOrdersFilter(filter: OrderFilter) {
        _ordersFilter.value = filter
    }

    // endregion

    // region Notifications polling

    /** The API has no push channel, so the feed is polled while the app is in the foreground. It is a cheap call. */
    fun startPolling() {
        if (_state.value.phase != Phase.SignedIn || pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (true) {
                refreshNotifications()
                delay(30_000)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    /** The app came back to the foreground: poll again and re-read the plan. */
    fun onForeground() {
        if (_state.value.phase != Phase.SignedIn) return
        startPolling()
        viewModelScope.launch { refreshPlan() }
    }

    suspend fun refreshNotifications() {
        if (_state.value.phase != Phase.SignedIn) return
        val feed = try {
            api.notifications()
        } catch (error: Exception) {
            if (error.isCancellation) throw error
            return
        }
        val fresh = feed.items.filter { !it.read && it.id !in seenNotificationIds }
        seenNotificationIds = seenNotificationIds + feed.items.map { it.id }
        _state.update { it.copy(feed = feed) }
        val newest = fresh.firstOrNull()
        if (hasLoadedFeedOnce && newest != null) announce(newest, fresh.size - 1)
        hasLoadedFeedOnce = true
    }

    suspend fun markRead(ids: List<String>? = null) {
        try {
            api.markNotificationsRead(ids)
            refreshNotifications()
        } catch (error: Exception) {
            if (error.isCancellation) throw error
        }
    }

    private fun announce(notification: AppNotification, extra: Int) {
        var message = notification.title
        if (extra > 0) message += " " + L("and {count} more", mapOf("count" to extra))
        val destination = notification.destination
        show(
            Toast(
                Toast.Kind.Info,
                message,
                actionTitle = destination?.let { L("View") },
                action = destination?.let { { open(it) } },
            ),
        )
    }

    // endregion

    // region Toasts

    fun show(toast: Toast) {
        _state.update { it.copy(toast = toast) }
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(if (toast.kind == Toast.Kind.Error) 6_000 else 4_000)
            _state.update { if (it.toast?.id == toast.id) it.copy(toast = null) else it }
        }
    }

    fun dismissToast() = _state.update { it.copy(toast = null) }

    fun showError(error: Throwable) {
        if (error.isCancellation) return
        show(Toast(Toast.Kind.Error, error.userMessage))
    }

    // endregion
}

const val LANGUAGE_KEY = "app.language"
