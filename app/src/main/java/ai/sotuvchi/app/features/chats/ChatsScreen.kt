package ai.sotuvchi.app.features.chats

import ai.sotuvchi.app.app.AppModel
import ai.sotuvchi.app.app.appViewModel
import ai.sotuvchi.app.core.format.DateText
import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.model.ChatRole
import ai.sotuvchi.app.core.model.ChatText
import ai.sotuvchi.app.core.model.Conversation
import ai.sotuvchi.app.core.network.conversations
import ai.sotuvchi.app.core.network.isCancellation
import ai.sotuvchi.app.core.network.userMessage
import ai.sotuvchi.app.design.Avatar
import ai.sotuvchi.app.design.Badge
import ai.sotuvchi.app.design.BadgeTone
import ai.sotuvchi.app.design.BarIconButton
import ai.sotuvchi.app.design.EmptyState
import ai.sotuvchi.app.design.ErrorBanner
import ai.sotuvchi.app.design.HairlineDivider
import ai.sotuvchi.app.design.LoadingView
import ai.sotuvchi.app.design.PillOption
import ai.sotuvchi.app.design.PillTabs
import ai.sotuvchi.app.design.ScreenFrame
import ai.sotuvchi.app.design.ScreenScroll
import ai.sotuvchi.app.design.SearchField
import ai.sotuvchi.app.design.Space
import ai.sotuvchi.app.design.Theme
import ai.sotuvchi.app.design.cardBackground
import ai.sotuvchi.app.features.notifications.NotificationBell
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ChatFilter {
    ALL, NEEDS_YOU, PAUSED;

    val title: String
        get() = when (this) {
            ALL -> L("All")
            NEEDS_YOU -> L("Needs you")
            PAUSED -> L("AI paused")
        }
}

class ChatsViewModel(private val model: AppModel) : ViewModel() {
    data class UiState(
        val conversations: List<Conversation> = emptyList(),
        val filter: ChatFilter = ChatFilter.ALL,
        val search: String = "",
        val storeId: String? = null,
        val isLoading: Boolean = false,
        val loaded: Boolean = false,
        val error: Throwable? = null,
    ) {
        val visible: List<Conversation>
            get() {
                val query = search.trim().lowercase()
                return conversations
                    .filter {
                        when (filter) {
                            ChatFilter.ALL -> true
                            ChatFilter.NEEDS_YOU -> it.needsAttention
                            ChatFilter.PAUSED -> it.aiPaused
                        }
                    }
                    .filter {
                        query.isEmpty() || it.displayName.lowercase().contains(query) ||
                            (it.lastMessage?.text?.lowercase()?.contains(query) ?: false)
                    }
            }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun setFilter(filter: ChatFilter) = _state.update { it.copy(filter = filter) }
    fun setSearch(search: String) = _state.update { it.copy(search = search) }
    fun setStore(storeId: String?) = _state.update { it.copy(storeId = storeId) }

    fun load() {
        viewModelScope.launch { reload() }
    }

    suspend fun reload() {
        _state.update { it.copy(isLoading = true) }
        try {
            val list = model.container.api.conversations(_state.value.storeId)
            _state.update { it.copy(conversations = list, loaded = true, error = null, isLoading = false) }
        } catch (error: Exception) {
            if (error.isCancellation) throw error
            _state.update { it.copy(error = error, isLoading = false) }
        }
    }
}

/** Customer conversations handled by the AI seller. Chats that need the owner come first, as the server orders them. */
@Composable
fun ChatsScreen(model: AppModel, onOpenChat: (String) -> Unit, onOpenNotifications: () -> Unit) {
    val vm = appViewModel { ChatsViewModel(it) }
    val ui by vm.state.collectAsStateWithLifecycle()
    val app by model.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var hasResumed by remember { mutableStateOf(false) }
    var storeMenu by remember { mutableStateOf(false) }

    LaunchedEffect(ui.storeId) { vm.reload() }
    LaunchedEffect(app.feed?.unreadCount) { vm.load() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (hasResumed) vm.load()
        hasResumed = true
    }

    ScreenFrame(
        title = L("Chats"),
        actions = {
            if (app.stores.size > 1) {
                Box {
                    BarIconButton(Icons.Outlined.Storefront, L("Store"), { storeMenu = true }, tint = if (ui.storeId == null) Theme.colors.ink else Theme.colors.accent)
                    DropdownMenu(
                        expanded = storeMenu,
                        onDismissRequest = { storeMenu = false },
                        containerColor = Theme.colors.surface,
                        shadowElevation = 0.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Theme.colors.hairline),
                    ) {
                        DropdownMenuItem(
                            text = { Text(L("All stores"), style = Theme.type.body, color = Theme.colors.ink) },
                            trailingIcon = { if (ui.storeId == null) Icon(Icons.Filled.Check, null, Modifier.size(18.dp), Theme.colors.ink) },
                            onClick = { vm.setStore(null); storeMenu = false },
                        )
                        for (store in app.stores) {
                            DropdownMenuItem(
                                text = { Text(store.name, style = Theme.type.body, color = Theme.colors.ink) },
                                trailingIcon = { if (ui.storeId == store.id) Icon(Icons.Filled.Check, null, Modifier.size(18.dp), Theme.colors.ink) },
                                onClick = { vm.setStore(store.id); storeMenu = false },
                            )
                        }
                    }
                }
            }
            NotificationBell(app.unreadCount, onOpenNotifications)
        },
    ) {
        ScreenScroll(
            spacing = Space.sm,
            refreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; vm.reload(); refreshing = false } },
        ) {
            PillTabs(
                options = ChatFilter.entries.map { option ->
                    PillOption(option, option.title, if (option == ChatFilter.NEEDS_YOU) ui.conversations.count { it.needsAttention } else null)
                },
                selection = ui.filter,
                onSelect = vm::setFilter,
            )
            SearchField(ui.search, vm::setSearch, L("Search by name or message"), Modifier.testTag("chats.search"))
            ui.error?.let { ErrorBanner(it.userMessage, onRetry = vm::load) }
            val visible = ui.visible
            if (!ui.loaded && ui.isLoading) {
                LoadingView()
            } else if (ui.loaded && visible.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Forum,
                    title = if (ui.search.isEmpty()) emptyTitle(ui.filter) else L("No chats match your search."),
                    message = if (ui.search.isEmpty() && ui.filter == ChatFilter.ALL) {
                        L("When customers write to your Instagram, the AI answers them and the chats show up here.")
                    } else null,
                )
            } else if (visible.isNotEmpty()) {
                Column(modifier = Modifier.cardBackground()) {
                    visible.forEachIndexed { index, conversation ->
                        if (index > 0) HairlineDivider(Modifier.padding(start = 68.dp))
                        ConversationRow(conversation, showsStore = app.stores.size > 1, onClick = { onOpenChat(conversation.id) })
                    }
                }
            }
        }
    }
}

private fun emptyTitle(filter: ChatFilter) = when (filter) {
    ChatFilter.ALL -> L("No chats yet")
    ChatFilter.NEEDS_YOU -> L("Nobody is waiting for you")
    ChatFilter.PAUSED -> L("The AI is answering every chat")
}

@Composable
fun ConversationRow(conversation: Conversation, showsStore: Boolean, onClick: () -> Unit) {
    val colors = Theme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm)
            .testTag("chat.${conversation.id}"),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Box {
            Avatar(conversation.customerName, size = 44.dp)
            if (conversation.needsAttention) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(12.dp)
                        .background(colors.accent, CircleShape)
                        .border(2.dp, colors.surface, CircleShape),
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(
                    conversation.displayName,
                    style = Theme.type.body.copy(fontWeight = if (conversation.needsAttention) FontWeight.SemiBold else FontWeight.Medium),
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                conversation.activityDate?.let { Text(DateText.listStamp(it), style = Theme.type.caption, color = colors.inkSubtle) }
            }
            Text(preview(conversation), style = Theme.type.bodySmall, color = colors.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (conversation.needsAttention) {
                    Badge(ChatText.attentionBadge(conversation.attentionReason), tone = BadgeTone.Accent, icon = Icons.Filled.PanTool)
                }
                if (conversation.aiPaused) Badge(L("AI paused"), icon = Icons.Filled.Pause)
                val store = conversation.store?.name
                if (showsStore && store != null) Text(store, style = Theme.type.caption, color = colors.inkSubtle, maxLines = 1)
            }
        }
    }
}

private fun preview(conversation: Conversation): String {
    val last = conversation.lastMessage ?: return L("No messages yet")
    val text = ChatText.preview(last.text)
    return when (last.role) {
        ChatRole.AI -> L("AI: {text}", mapOf("text" to text))
        ChatRole.OWNER -> L("You: {text}", mapOf("text" to text))
        else -> text
    }
}
