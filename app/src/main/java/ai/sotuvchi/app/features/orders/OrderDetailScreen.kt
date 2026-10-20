package ai.sotuvchi.app.features.orders

import ai.sotuvchi.app.app.AppModel
import ai.sotuvchi.app.app.appViewModel
import ai.sotuvchi.app.core.format.DateText
import ai.sotuvchi.app.core.format.Money
import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.model.Order
import ai.sotuvchi.app.core.model.OrderDecisionResult
import ai.sotuvchi.app.core.model.OrderStatus
import ai.sotuvchi.app.core.model.PaymentProof
import ai.sotuvchi.app.core.network.ApiError
import ai.sotuvchi.app.core.network.approveOrder
import ai.sotuvchi.app.core.network.cancelOrder
import ai.sotuvchi.app.core.network.isCancellation
import ai.sotuvchi.app.core.network.order
import ai.sotuvchi.app.core.network.rejectOrder
import ai.sotuvchi.app.core.network.syncOrder
import ai.sotuvchi.app.core.network.userMessage
import ai.sotuvchi.app.design.AIMark
import ai.sotuvchi.app.design.ActionBar
import ai.sotuvchi.app.design.Badge
import ai.sotuvchi.app.design.BadgeTone
import ai.sotuvchi.app.design.ButtonKind
import ai.sotuvchi.app.design.Card
import ai.sotuvchi.app.design.ConfirmDialog
import ai.sotuvchi.app.design.ControlHeight
import ai.sotuvchi.app.design.EmptyState
import ai.sotuvchi.app.design.HairlineDivider
import ai.sotuvchi.app.design.InputField
import ai.sotuvchi.app.design.KeyValueRow
import ai.sotuvchi.app.design.LoadingView
import ai.sotuvchi.app.design.Notice
import ai.sotuvchi.app.design.NoticeTone
import ai.sotuvchi.app.design.Radius
import ai.sotuvchi.app.design.RemoteImage
import ai.sotuvchi.app.design.SButton
import ai.sotuvchi.app.design.ScreenFrame
import ai.sotuvchi.app.design.ScreenScroll
import ai.sotuvchi.app.design.SotuvchiSheet
import ai.sotuvchi.app.design.Space
import ai.sotuvchi.app.design.Theme
import ai.sotuvchi.app.design.Toast
import ai.sotuvchi.app.design.cardBackground
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.HighlightOff
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.OpenInFull
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OrderAction { Approve, Reject, Cancel, Sync }

/**
 * One order: receipt, items, customer, and the approve / reject / cancel
 * decisions. There is no order detail endpoint, so the order is refreshed by
 * finding it in the list again.
 */
class OrderDetailViewModel(private val model: AppModel, private val orderId: String) : ViewModel() {
    data class UiState(
        val order: Order? = null,
        val isLoading: Boolean = false,
        val error: Throwable? = null,
        val busy: OrderAction? = null,
    )

    private val api = model.container.api
    private val _state = MutableStateFlow(UiState(order = model.orderCache[orderId]))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch { refresh() }
    }

    suspend fun refresh() {
        _state.update { it.copy(isLoading = true) }
        try {
            val fresh = api.order(orderId, _state.value.order?.storeId)
            if (fresh != null) model.orderCache[fresh.id] = fresh
            _state.update { it.copy(order = fresh ?: it.order, error = null, isLoading = false) }
        } catch (error: Exception) {
            if (error.isCancellation) throw error
            _state.update { it.copy(error = error, isLoading = false) }
        }
    }

    fun approve() = act(OrderAction.Approve) {
        val result = api.approveOrder(orderId)
        model.show(decisionToast(result, L("Order approved. The customer was told.")))
        afterDecision()
    }

    fun reject(reason: String?) = act(OrderAction.Reject) {
        val result = api.rejectOrder(orderId, reason)
        model.show(decisionToast(result, L("Order rejected. The customer was told.")))
        afterDecision()
    }

    fun cancel() = act(OrderAction.Cancel) {
        api.cancelOrder(orderId)
        model.show(Toast(Toast.Kind.Success, L("Order cancelled.")))
        afterDecision()
    }

    fun sync() {
        viewModelScope.launch {
            _state.update { it.copy(busy = OrderAction.Sync) }
            try {
                api.syncOrder(orderId)
                model.show(Toast(Toast.Kind.Success, L("Saved in your warehouse.")))
                refresh()
            } catch (error: Exception) {
                if (error.isCancellation) throw error
                model.showError(error)
            } finally {
                _state.update { it.copy(busy = null) }
            }
        }
    }

    private fun act(action: OrderAction, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = action) }
            try {
                block()
            } catch (error: ApiError.Conflict) {
                model.show(Toast(Toast.Kind.Warning, L("This order changed in the meantime. It has been refreshed.")))
                refresh()
            } catch (error: Exception) {
                if (error.isCancellation) throw error
                model.showError(error)
            } finally {
                _state.update { it.copy(busy = null) }
            }
        }
    }

    /**
     * Approve and reject succeed even when the customer could not be messaged
     * or the warehouse write failed; those become warnings.
     */
    private fun decisionToast(result: OrderDecisionResult, success: String): Toast {
        val warnings = mutableListOf<String>()
        if (result.notified == false) {
            warnings += L(
                "Saved, but the customer could not be messaged: {reason}",
                mapOf("reason" to (result.notifyError ?: L("the 24-hour reply window may have closed"))),
            )
        }
        result.syncError?.let { warnings += L("Not saved in your warehouse: {reason}", mapOf("reason" to it)) }
        if (warnings.isEmpty()) return Toast(Toast.Kind.Success, success)
        return Toast(Toast.Kind.Warning, warnings.joinToString("\n"))
    }

    private suspend fun afterDecision() {
        refresh()
        model.refreshNotifications()
    }
}

@Composable
fun OrderDetailScreen(model: AppModel, orderId: String, onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    val vm = appViewModel(key = "order-$orderId") { OrderDetailViewModel(it, orderId) }
    val ui by vm.state.collectAsStateWithLifecycle()
    val app by model.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var confirmApprove by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var showsReject by remember { mutableStateOf(false) }
    var viewerUrl by remember { mutableStateOf<String?>(null) }
    val order = ui.order

    ScreenFrame(
        title = order?.let { L("Order #{number}", mapOf("number" to it.number)) } ?: L("Order"),
        onBack = onBack,
    ) {
        if (order == null) {
            if (ui.isLoading) {
                LoadingView()
            } else {
                EmptyState(
                    icon = Icons.Outlined.ShoppingBag,
                    title = L("Order not found"),
                    message = ui.error?.userMessage ?: L("It may be older than the latest 100 orders."),
                    actionTitle = L("Retry"),
                    onAction = vm::reload,
                )
            }
        } else {
            ScreenScroll(
                modifier = Modifier.weight(1f),
                refreshing = refreshing,
                onRefresh = { scope.launch { refreshing = true; vm.refresh(); refreshing = false } },
            ) {
                Header(order)
                StatusNotice(order)
                order.externalSyncError?.let { SyncCard(it, canManage = app.canManage, busy = ui.busy, onRetry = vm::sync) }
                if (order.proofs.isNotEmpty()) ReceiptCard(order, onOpen = { viewerUrl = it })
                ItemsCard(order)
                CustomerCard(order, onOpenChat)
            }
            if (app.canManage && order.status.canDecide) {
                ActionBar {
                    if (order.status.canCancel) {
                        CancelMenu(enabled = ui.busy == null, onCancel = { confirmCancel = true })
                    }
                    SButton(
                        L("Reject"), { showsReject = true },
                        kind = ButtonKind.Secondary,
                        loading = ui.busy == OrderAction.Reject,
                        enabled = ui.busy == null,
                        modifier = Modifier.weight(1f).testTag("order.reject"),
                    )
                    SButton(
                        L("Approve"), { confirmApprove = true },
                        icon = Icons.Outlined.Check,
                        loading = ui.busy == OrderAction.Approve,
                        enabled = ui.busy == null,
                        modifier = Modifier.weight(1f).testTag("order.approve"),
                    )
                }
            }
        }
    }

    if (confirmApprove && order != null) {
        ConfirmDialog(
            title = approveTitle(order),
            message = L("The customer gets a confirmation message in Instagram."),
            confirmText = L("Approve payment"),
            onConfirm = { confirmApprove = false; vm.approve() },
            onDismiss = { confirmApprove = false },
        )
    }
    if (confirmCancel) {
        ConfirmDialog(
            title = L("Cancel this order?"),
            message = L("The held stock is released."),
            confirmText = L("Cancel order"),
            dismissText = L("Keep order"),
            destructive = true,
            onConfirm = { confirmCancel = false; vm.cancel() },
            onDismiss = { confirmCancel = false },
        )
    }
    if (showsReject && order != null) {
        RejectSheet(order, onDismiss = { showsReject = false }, onReject = { reason -> showsReject = false; vm.reject(reason) })
    }
    viewerUrl?.let { ReceiptViewer(it, onClose = { viewerUrl = null }) }
}

private fun approveTitle(order: Order): String =
    if (order.proofs.isEmpty()) {
        L("No receipt yet. Approve order #{number} for {total} anyway?", mapOf("number" to order.number, "total" to Money.format(order.total)))
    } else {
        L("Approve order #{number} for {total}?", mapOf("number" to order.number, "total" to Money.format(order.total)))
    }

@Composable
private fun Header(order: Order) {
    Column(
        modifier = Modifier.padding(horizontal = Space.xxs).padding(top = Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            OrderStatusBadge(order.status)
            order.store?.name?.let { Text(it, style = Theme.type.caption, color = Theme.colors.inkSubtle) }
        }
        val total = Money.format(order.total)
        val size = if (total.length <= 13) 34 else if (total.length <= 17) 28 else 22
        Text(total, style = Theme.type.display.copy(fontSize = size.sp, lineHeight = (size * 1.2f).sp), color = Theme.colors.ink, maxLines = 1)
        Text(DateText.dayTime(order.createdAt), style = Theme.type.bodySmall, color = Theme.colors.inkMuted)
    }
}

@Composable
private fun StatusNotice(order: Order) {
    when (order.status) {
        OrderStatus.PROOF_SUBMITTED ->
            Notice(L("The customer sent a payment receipt. Check it against your bank app, then approve."), tone = NoticeTone.Warning, icon = Icons.Outlined.Receipt)
        OrderStatus.AWAITING_PAYMENT -> {
            val hold = order.holdExpiresAt
            if (hold != null && hold.isAfter(Instant.now())) {
                Notice(L("Customer has not paid yet. Stock held until {time}.", mapOf("time" to DateText.dayTime(hold))), icon = Icons.Outlined.AccessTime)
            } else {
                Notice(L("Customer has not paid yet"), icon = Icons.Outlined.AccessTime)
            }
        }
        OrderStatus.EXPIRED ->
            Notice(L("The payment time ran out. If the customer paid anyway, you can still approve; stock is checked again."), icon = Icons.Outlined.AccessTime)
        OrderStatus.REJECTED ->
            Notice(order.rejectReason?.let { L("Rejected: {reason}", mapOf("reason" to it)) } ?: L("Rejected"), icon = Icons.Outlined.HighlightOff)
        OrderStatus.APPROVED -> order.decidedAt?.let {
            Notice(L("Approved {date}", mapOf("date" to DateText.dayTime(it))), icon = Icons.Outlined.CheckCircleOutline)
        }
        OrderStatus.CANCELLED -> Notice(L("This order was cancelled."), icon = Icons.Outlined.HighlightOff)
        else -> {}
    }
}

@Composable
private fun SyncCard(error: String, canManage: Boolean, busy: OrderAction?, onRetry: () -> Unit) {
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                Icon(Icons.Outlined.Sync, null, tint = Theme.colors.error, modifier = Modifier.size(20.dp))
                Text(L("Not saved in your warehouse"), style = Theme.type.body.copy(fontWeight = FontWeight.Medium), color = Theme.colors.error)
            }
            Text(error, style = Theme.type.bodySmall, color = Theme.colors.inkMuted)
            if (canManage) {
                SButton(
                    L("Retry"), onRetry,
                    kind = ButtonKind.Secondary, fullWidth = false, compact = true,
                    icon = Icons.Outlined.Refresh,
                    loading = busy == OrderAction.Sync,
                    enabled = busy == null,
                )
            }
        }
    }
}

@Composable
private fun ReceiptCard(order: Order, onOpen: (String) -> Unit) {
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Text(L("Receipt"), style = Theme.type.cardTitle, color = Theme.colors.ink)
            order.proofs.sortedByDescending { it.createdAt ?: Instant.MIN }.forEach { proof ->
                ProofBlock(proof, order.total, onOpen)
            }
        }
    }
}

@Composable
private fun ProofBlock(proof: PaymentProof, total: Int, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(Radius.md))
                .then(if (proof.url != null) Modifier.clickable { onOpen(proof.url) } else Modifier)
                .testTag("receipt.image"),
        ) {
            RemoteImage(proof.url, Modifier.fillMaxWidth().height(280.dp), contentScale = ContentScale.Fit)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Space.xs)
                    .size(30.dp)
                    .background(Theme.colors.surface.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.OpenInFull, null, tint = Theme.colors.ink, modifier = Modifier.size(15.dp)) }
        }
        proof.extractedAmount?.let { AmountCheck(it, total) }
        proof.extractedNote?.takeIf { it.isNotEmpty() }?.let {
            Text(it, style = Theme.type.caption, color = Theme.colors.inkSubtle)
        }
        proof.createdAt?.let {
            Text(L("Sent {date}", mapOf("date" to DateText.dayTime(it))), style = Theme.type.caption, color = Theme.colors.inkSubtle)
        }
    }
}

/** What the AI read from the receipt against the order total. A hint only. */
@Composable
private fun AmountCheck(receipt: Int, total: Int) {
    val difference = receipt - total
    val colors = Theme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceAlt.copy(alpha = 0.6f), RoundedCornerShape(Radius.md))
            .padding(Space.sm),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AIMark(label = L("Read by AI"))
            Spacer(Modifier.weight(1f))
            Text(Money.format(receipt), style = Theme.type.body.copy(fontWeight = FontWeight.Medium), color = colors.ink)
        }
        if (difference == 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.CheckCircle, null, tint = colors.successInk, modifier = Modifier.size(14.dp))
                Text(L("Matches the order total"), style = Theme.type.caption, color = colors.successInk)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.Warning, null, tint = colors.warningInk, modifier = Modifier.size(14.dp))
                Text(
                    if (difference < 0) L("{amount} less than the order total", mapOf("amount" to Money.format(-difference)))
                    else L("{amount} more than the order total", mapOf("amount" to Money.format(difference))),
                    style = Theme.type.caption,
                    color = colors.warningInk,
                )
            }
        }
        Text(L("This is a hint. Always check your bank app."), style = Theme.type.caption, color = colors.inkSubtle)
    }
}

@Composable
private fun ItemsCard(order: Order) {
    val colors = Theme.colors
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text(L("Items"), style = Theme.type.cardTitle, color = colors.ink)
            order.items.forEach { item ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.productName, style = Theme.type.body, color = colors.ink)
                        item.variantLabel?.takeIf { it.isNotEmpty() }?.let {
                            Text(it, style = Theme.type.caption, color = colors.inkSubtle)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${item.quantity} x ${Money.format(item.unitPrice)}", style = Theme.type.caption, color = colors.inkMuted)
                            val list = item.listPrice
                            if (item.isDiscounted && list != null) {
                                Text(
                                    Money.format(list),
                                    style = Theme.type.caption.copy(textDecoration = TextDecoration.LineThrough),
                                    color = colors.inkTertiary,
                                )
                            }
                        }
                        if (item.isDiscounted) Badge(L("Bargained"), tone = BadgeTone.Accent, icon = Icons.Filled.AutoAwesome)
                    }
                    Text(Money.format(item.lineTotal), style = Theme.type.body, color = colors.ink)
                }
                HairlineDivider()
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L("Total"), style = Theme.type.body.copy(fontWeight = FontWeight.Medium), color = colors.ink, modifier = Modifier.weight(1f))
                Text(Money.format(order.total), style = Theme.type.subhead.copy(fontWeight = FontWeight.Medium), color = colors.ink)
            }
        }
    }
}

@Composable
private fun CustomerCard(order: Order, onOpenChat: (String) -> Unit) {
    val colors = Theme.colors
    val context = LocalContext.current
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(L("Customer"), style = Theme.type.cardTitle, color = colors.ink, modifier = Modifier.padding(bottom = 4.dp))
            KeyValueRow(L("Name"), order.displayCustomer)
            order.customerPhone?.takeIf { it.isNotEmpty() }?.let { phone ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(L("Phone"), style = Theme.type.bodySmall, color = colors.inkMuted, modifier = Modifier.weight(1f))
                    Text(
                        phone,
                        style = Theme.type.body.copy(textDecoration = TextDecoration.Underline),
                        color = colors.ink,
                        modifier = Modifier.clickable {
                            val digits = phone.filter { it.isDigit() || it == '+' }
                            runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits"))) }
                        },
                    )
                }
            }
            order.deliveryType?.let { KeyValueRow(L("Delivery"), it.title) }
            order.address?.takeIf { it.isNotEmpty() }?.let {
                Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(L("Address"), style = Theme.type.bodySmall, color = colors.inkMuted)
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(it, style = Theme.type.body, color = colors.ink)
                    }
                }
            }
            (order.conversationId ?: order.conversation?.id)?.let { chatId ->
                SButton(
                    L("Open chat"), { onOpenChat(chatId) },
                    kind = ButtonKind.Secondary,
                    icon = Icons.Outlined.ChatBubbleOutline,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }
        }
    }
}

@Composable
private fun CancelMenu(enabled: Boolean, onCancel: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(ControlHeight)
                .cardBackground(Radius.md)
                .clickable(enabled = enabled) { open = true }
                .testTag("order.more"),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.MoreHoriz, contentDescription = L("Cancel order"), tint = Theme.colors.ink) }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Theme.colors.surface,
            shadowElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Theme.colors.hairline),
        ) {
            DropdownMenuItem(
                text = { Text(L("Cancel order"), style = Theme.type.body, color = Theme.colors.error) },
                leadingIcon = { Icon(Icons.Outlined.Close, null, tint = Theme.colors.error, modifier = Modifier.size(18.dp)) },
                onClick = { open = false; onCancel() },
            )
        }
    }
}

/** Reason for rejecting. The customer sees it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RejectSheet(order: Order, onDismiss: () -> Unit, onReject: (String?) -> Unit) {
    var reason by remember { mutableStateOf("") }
    val presets = listOf(
        L("Payment not received"),
        L("The amount does not match the order"),
        L("The receipt is not readable"),
        L("Out of stock"),
    )
    SotuvchiSheet(onDismiss) {
        Text(L("Reject"), style = Theme.type.cardTitle, color = Theme.colors.ink)
        Text(L("Reason for rejecting (the customer will see it):"), style = Theme.type.body, color = Theme.colors.ink)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            presets.forEach { preset ->
                SButton(
                    preset, { reason = preset },
                    kind = if (reason == preset) ButtonKind.Primary else ButtonKind.Secondary,
                    fullWidth = false, compact = true,
                )
            }
        }
        InputField(
            value = reason,
            onValueChange = { reason = it.take(300) },
            placeholder = L("Write a reason (optional)"),
            multiline = true,
            minLines = 3,
            modifier = Modifier.testTag("reject.reason"),
        )
        Text("${reason.length}/300", style = Theme.type.caption, color = Theme.colors.inkSubtle, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
        SButton(
            L("Reject order #{number}", mapOf("number" to order.number)),
            { onReject(reason.trim().ifEmpty { null }) },
            kind = ButtonKind.Destructive,
            modifier = Modifier.testTag("reject.confirm"),
        )
    }
}
