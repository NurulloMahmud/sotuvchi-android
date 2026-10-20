package ai.sotuvchi.app.features.chats

import ai.sotuvchi.app.app.AppModel
import ai.sotuvchi.app.app.appViewModel
import ai.sotuvchi.app.app.openInCustomTab
import ai.sotuvchi.app.core.format.Countdown
import ai.sotuvchi.app.core.format.DateText
import ai.sotuvchi.app.core.format.Money
import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.model.Attachment
import ai.sotuvchi.app.core.model.AttachmentKind
import ai.sotuvchi.app.core.model.ChatMessage
import ai.sotuvchi.app.core.model.ChatRole
import ai.sotuvchi.app.core.model.ChatText
import ai.sotuvchi.app.core.model.ConversationDetail
import ai.sotuvchi.app.core.model.ConversationPatch
import ai.sotuvchi.app.core.model.LinkedOrder
import ai.sotuvchi.app.core.model.ReplyWindow
import ai.sotuvchi.app.core.network.ApiError
import ai.sotuvchi.app.core.network.conversation
import ai.sotuvchi.app.core.network.isCancellation
import ai.sotuvchi.app.core.network.sendMessage
import ai.sotuvchi.app.core.network.updateConversation
import ai.sotuvchi.app.core.network.userMessage
import ai.sotuvchi.app.design.AIMark
import ai.sotuvchi.app.design.BarIconButton
import ai.sotuvchi.app.design.ButtonKind
import ai.sotuvchi.app.design.EmptyState
import ai.sotuvchi.app.design.HairlineDivider
import ai.sotuvchi.app.design.InputField
import ai.sotuvchi.app.design.LoadingView
import ai.sotuvchi.app.design.Notice
import ai.sotuvchi.app.design.Radius
import ai.sotuvchi.app.design.RemoteImage
import ai.sotuvchi.app.design.SButton
import ai.sotuvchi.app.design.ScreenFrame
import ai.sotuvchi.app.design.Space
import ai.sotuvchi.app.design.Spinner
import ai.sotuvchi.app.design.Theme
import ai.sotuvchi.app.design.Toast
import ai.sotuvchi.app.design.cardBackground
import ai.sotuvchi.app.features.orders.OrderStatusBadge
import ai.sotuvchi.app.features.orders.ReceiptViewer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatThreadViewModel(private val model: AppModel, private val conversationId: String) : ViewModel() {
    data class UiState(
        val detail: ConversationDetail? = null,
        val error: Throwable? = null,
        val isSending: Boolean = false,
        val isTogglingAi: Boolean = false,
    )

    private val api = model.container.api
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    suspend fun load() {
        try {
            val detail = api.conversation(conversationId)
            _state.update { it.copy(detail = detail, error = null) }
        } catch (error: Exception) {
            if (error.isCancellation) throw error
            if (_state.value.detail == null) _state.update { it.copy(error = error) }
        }
    }

    fun retry() {
        viewModelScope.launch { load() }
    }

    /** Returns true when the message went out, so the caller can clear the draft. */
    suspend fun send(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        _state.update { it.copy(isSending = true) }
        try {
            api.sendMessage(conversationId, trimmed)
            load()
            return true
        } catch (error: ApiError.Upstream) {
            model.show(Toast(Toast.Kind.Error, error.detail ?: L("Instagram refused the message. The 24-hour reply window may have closed.")))
        } catch (error: Exception) {
            if (error.isCancellation) throw error
            model.showError(error)
        } finally {
            _state.update { it.copy(isSending = false) }
        }
        return false
    }

    fun toggleAi() {
        val detail = _state.value.detail ?: return
        viewModelScope.launch {
            _state.update { it.copy(isTogglingAi = true) }
            val done = update(ConversationPatch(aiPaused = !detail.aiPaused))
            _state.update { it.copy(isTogglingAi = false) }
            if (done) {
                model.show(
                    Toast(
                        Toast.Kind.Success,
                        if (detail.aiPaused) L("The AI is answering this chat again.") else L("AI paused. You are answering this chat."),
                    ),
                )
            }
        }
    }

    fun dismissFlag() {
        viewModelScope.launch { update(ConversationPatch(needsAttention = false)) }
    }

    private suspend fun update(patch: ConversationPatch): Boolean = try {
        api.updateConversation(conversationId, patch)
        load()
        model.refreshNotifications()
        true
    } catch (error: Exception) {
        if (error.isCancellation) throw error
        model.showError(error)
        false
    }
}

private data class DayGroup(val day: LocalDate, val messages: List<ChatMessage>)

private fun groupedByDay(messages: List<ChatMessage>): List<DayGroup> {
    val zone = ZoneId.systemDefault()
    val groups = mutableListOf<DayGroup>()
    for (message in messages) {
        val day = message.createdAt.atZone(zone).toLocalDate()
        val last = groups.lastOrNull()
        if (last != null && last.day == day) groups[groups.size - 1] = last.copy(messages = last.messages + message)
        else groups += DayGroup(day, listOf(message))
    }
    return groups
}

/**
 * One customer conversation. The owner can take over from the AI, write
 * within Instagram's 24-hour window, and hand the chat back.
 */
@Composable
fun ChatThreadScreen(model: AppModel, conversationId: String, onBack: () -> Unit, onOpenOrder: (String) -> Unit) {
    val vm = appViewModel(key = "chat-$conversationId") { ChatThreadViewModel(it, conversationId) }
    val ui by vm.state.collectAsStateWithLifecycle()
    val app by model.state.collectAsStateWithLifecycle()
    val detail = ui.detail
    var viewerUrl by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    // Refreshes while the thread is on screen so new customer messages show up.
    LaunchedEffect(conversationId) {
        while (true) {
            vm.load()
            delay(15_000)
        }
    }

    ScreenFrame(
        title = detail?.displayName ?: L("Chat"),
        onBack = onBack,
        actions = {
            if (detail != null && app.canManage) {
                Box {
                    BarIconButton(Icons.Outlined.MoreVert, L("More"), { menuOpen = true })
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = Theme.colors.surface,
                        shadowElevation = 0.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Theme.colors.hairline),
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (detail.aiPaused) L("Resume AI") else L("Pause AI"), style = Theme.type.body, color = Theme.colors.ink) },
                            onClick = { menuOpen = false; vm.toggleAi() },
                        )
                        if (detail.needsAttention) {
                            DropdownMenuItem(
                                text = { Text(L("Dismiss the flag"), style = Theme.type.body, color = Theme.colors.ink) },
                                onClick = { menuOpen = false; vm.dismissFlag() },
                            )
                        }
                    }
                }
            }
        },
    ) {
        when {
            detail != null -> Thread(
                detail = detail,
                canManage = app.canManage,
                isTogglingAi = ui.isTogglingAi,
                isSending = ui.isSending,
                onToggleAi = vm::toggleAi,
                onDismissFlag = vm::dismissFlag,
                onSend = { vm.send(it) },
                onOpenImage = { viewerUrl = it },
                onOpenOrder = onOpenOrder,
            )
            ui.error != null -> EmptyState(
                icon = Icons.Outlined.ErrorOutline,
                title = ui.error?.userMessage ?: "",
                actionTitle = L("Retry"),
                onAction = vm::retry,
            )
            else -> LoadingView()
        }
    }
    viewerUrl?.let { ReceiptViewer(it, onClose = { viewerUrl = null }) }
}

@Composable
private fun Thread(
    detail: ConversationDetail,
    canManage: Boolean,
    isTogglingAi: Boolean,
    isSending: Boolean,
    onToggleAi: () -> Unit,
    onDismissFlag: () -> Unit,
    onSend: suspend (String) -> Boolean,
    onOpenImage: (String) -> Unit,
    onOpenOrder: (String) -> Unit,
) {
    val context = LocalContext.current
    val groups = remember(detail.messages) { groupedByDay(detail.messages) }
    val listState = rememberLazyListState()
    val itemCount = (if (detail.orders.isNotEmpty()) 1 else 0) + (if (detail.messages.isEmpty()) 1 else 0) + groups.sumOf { it.messages.size + 1 }

    // Open at the newest message, and follow new ones.
    LaunchedEffect(detail.messages.size) {
        if (itemCount > 0) listState.scrollToItem(itemCount - 1)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        StatusBar(detail, canManage, isTogglingAi, onToggleAi, onDismissFlag)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.md, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            if (detail.orders.isNotEmpty()) item { LinkedOrders(detail.orders, onOpenOrder) }
            if (detail.messages.isEmpty()) item { EmptyState(Icons.Outlined.ChatBubbleOutline, L("No messages yet")) }
            for (group in groups) {
                item(key = "day-${group.day}") {
                    Text(
                        DateText.day(group.day.atStartOfDay(ZoneId.systemDefault()).toInstant()),
                        style = Theme.type.caption,
                        color = Theme.colors.inkSubtle,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs),
                    )
                }
                items(group.messages.size, key = { group.messages[it].id }) { index ->
                    MessageBubble(
                        group.messages[index],
                        onOpenImage = onOpenImage,
                        onOpenLink = { url -> openInCustomTab(context, url) },
                    )
                }
            }
        }
        Composer(detail, canManage, isSending, onSend)
    }
}

@Composable
private fun LinkedOrders(orders: List<LinkedOrder>, onOpenOrder: (String) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        for (order in orders) {
            Row(
                modifier = Modifier
                    .cardBackground(Radius.md)
                    .clickable { onOpenOrder(order.id) }
                    .padding(horizontal = Space.sm, vertical = 8.dp)
                    .testTag("linkedOrder.${order.number}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Outlined.ShoppingBag, null, tint = Theme.colors.ink, modifier = Modifier.size(16.dp))
                Text("#${order.number}", style = Theme.type.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = Theme.colors.ink)
                Text(Money.format(order.total), style = Theme.type.bodySmall, color = Theme.colors.ink)
                OrderStatusBadge(order.status)
            }
        }
    }
}

/** AI status and attention reason, pinned under the top bar. */
@Composable
private fun StatusBar(
    detail: ConversationDetail,
    canManage: Boolean,
    isTogglingAi: Boolean,
    onToggleAi: () -> Unit,
    onDismissFlag: () -> Unit,
) {
    val colors = Theme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.canvas)
            .padding(horizontal = Space.md, vertical = Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        if (detail.needsAttention) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.accentSoft, RoundedCornerShape(Radius.md))
                    .padding(Space.sm),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Filled.PanTool, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                Text(ChatText.attentionReason(detail.attentionReason), style = Theme.type.bodySmall, color = colors.ink, modifier = Modifier.weight(1f))
                if (canManage) {
                    Text(
                        L("Dismiss"),
                        style = Theme.type.button,
                        color = colors.ink,
                        modifier = Modifier.clickable(onClick = onDismissFlag).padding(horizontal = 4.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .cardBackground(Radius.md)
                .padding(horizontal = Space.sm, vertical = Space.xs),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (detail.aiPaused) {
                Icon(Icons.Filled.PauseCircle, null, tint = colors.inkMuted, modifier = Modifier.size(20.dp))
                Text(L("AI paused. You are answering this chat."), style = Theme.type.bodySmall, color = colors.ink, modifier = Modifier.weight(1f))
            } else {
                Icon(Icons.Filled.AutoAwesome, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                Text(L("AI is replying"), style = Theme.type.bodySmall, color = colors.ink, modifier = Modifier.weight(1f))
            }
            if (canManage) {
                SButton(
                    if (detail.aiPaused) L("Resume AI") else L("Take over"),
                    onToggleAi,
                    kind = if (detail.aiPaused) ButtonKind.Accent else ButtonKind.Secondary,
                    fullWidth = false,
                    compact = true,
                    loading = isTogglingAi,
                    modifier = Modifier.testTag("chat.toggleAi"),
                )
            }
        }
    }
}

@Composable
private fun Composer(detail: ConversationDetail, canManage: Boolean, isSending: Boolean, onSend: suspend (String) -> Boolean) {
    val colors = Theme.colors
    var draft by remember { mutableStateOf("") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val windowOpen = ReplyWindow.isOpen(detail.lastCustomerMessageAt)
    val canSend = draft.isNotBlank() && !isSending

    Column(Modifier.fillMaxWidth().background(colors.canvas)) {
        HairlineDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Space.md, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!canManage) {
                Notice(L("Only the owner or an admin can reply to customers."), icon = Icons.Outlined.Lock)
            } else if (!windowOpen) {
                Notice(
                    L("Instagram allows replies only within 24 hours of the customer's last message. You can reply when they write again."),
                    icon = Icons.Outlined.AccessTime,
                    modifier = Modifier.testTag("chat.windowClosed"),
                )
            } else {
                val left = ReplyWindow.remaining(detail.lastCustomerMessageAt)
                if (left != null && left.toHours() < 3) {
                    Text(
                        L("Reply window closes in {time}", mapOf("time" to Countdown.hoursMinutes(left))),
                        style = Theme.type.caption,
                        color = colors.warningInk,
                        modifier = Modifier.testTag("chat.windowCountdown"),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.weight(1f)) {
                        InputField(
                            value = draft,
                            onValueChange = { draft = it.take(ReplyWindow.MAX_MESSAGE_LENGTH) },
                            placeholder = if (detail.aiPaused) L("Write a reply") else L("Write a reply. The AI pauses while you answer."),
                            multiline = true,
                            minLines = 1,
                            modifier = Modifier.testTag("chat.composer"),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(Radius.md))
                            .background(colors.primary.copy(alpha = if (canSend) 1f else 0.4f))
                            .clickable(enabled = canSend) {
                                val text = draft
                                scope.launch { if (onSend(text)) draft = "" }
                            }
                            .testTag("chat.send"),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSending) Spinner(size = 18.dp, color = colors.onPrimary)
                        else Icon(Icons.Outlined.ArrowUpward, contentDescription = L("Send"), tint = colors.onPrimary)
                    }
                }
                if (draft.length > ReplyWindow.MAX_MESSAGE_LENGTH - 100) {
                    Text("${draft.length}/${ReplyWindow.MAX_MESSAGE_LENGTH}", style = Theme.type.caption, color = colors.inkSubtle)
                }
            }
        }
    }
}

private fun iconFor(kind: AttachmentKind): ImageVector = when (kind) {
    AttachmentKind.IMAGE -> Icons.Outlined.Image
    AttachmentKind.VIDEO -> Icons.Outlined.PlayCircleOutline
    AttachmentKind.AUDIO -> Icons.Outlined.Mic
    AttachmentKind.LINK -> Icons.Outlined.Link
    else -> Icons.Outlined.AttachFile
}

/**
 * Customer on the left in white; the AI and the owner on the right. AI
 * messages use the soft brand-blue tint with a sparkle; the owner's are
 * charcoal.
 */
@Composable
fun MessageBubble(message: ChatMessage, onOpenImage: (String) -> Unit, onOpenLink: (String) -> Unit) {
    val colors = Theme.colors
    val outgoing = message.role != ChatRole.CUSTOMER
    val background = when (message.role) {
        ChatRole.AI -> colors.accentSoft
        ChatRole.OWNER -> colors.primary
        else -> colors.surface
    }
    val textColor = if (message.role == ChatRole.OWNER) colors.onPrimary else colors.ink
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start) {
        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (attachment in message.attachments.sortedBy { it.position ?: 0 }) {
                AttachmentView(attachment, onOpenImage, onOpenLink)
            }
            val text = message.text
            // A media-only message carries a placeholder text; the attachment says it better.
            if (!text.isNullOrEmpty() && !(ChatText.isPlaceholder(text) && message.attachments.isNotEmpty())) {
                SelectionContainer {
                    Text(
                        ChatText.preview(text),
                        style = Theme.type.body,
                        color = textColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.lg))
                            .background(background)
                            .then(if (message.role == ChatRole.CUSTOMER) Modifier.border(1.dp, colors.hairline, RoundedCornerShape(Radius.lg)) else Modifier)
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (message.role == ChatRole.AI) AIMark()
                else if (message.role == ChatRole.OWNER) {
                    Text(L("You"), style = Theme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.inkMuted)
                }
                Text(DateText.time(message.createdAt), style = Theme.type.caption, color = colors.inkSubtle)
            }
        }
    }
}

@Composable
private fun AttachmentView(attachment: Attachment, onOpenImage: (String) -> Unit, onOpenLink: (String) -> Unit) {
    val colors = Theme.colors
    val url = attachment.url
    if (attachment.kind == AttachmentKind.IMAGE) {
        RemoteImage(
            url,
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(Radius.lg))
                .then(if (url != null) Modifier.clickable { onOpenImage(url) } else Modifier),
        )
    } else {
        Row(
            modifier = Modifier
                .cardBackground(Radius.lg)
                .then(if (url != null) Modifier.clickable { onOpenLink(url) } else Modifier)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Icon(iconFor(attachment.kind), null, tint = colors.ink, modifier = Modifier.size(18.dp))
            Text(ChatText.label(attachment.kind), style = Theme.type.bodySmall, color = colors.ink)
            if (url != null) Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = colors.ink, modifier = Modifier.size(14.dp))
        }
    }
}
