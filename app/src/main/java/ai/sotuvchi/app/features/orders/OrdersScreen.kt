package ai.sotuvchi.app.features.orders

import ai.sotuvchi.app.app.AppModel
import ai.sotuvchi.app.app.appViewModel
import ai.sotuvchi.app.core.format.DateText
import ai.sotuvchi.app.core.format.Money
import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.model.Order
import ai.sotuvchi.app.core.model.OrderFilter
import ai.sotuvchi.app.core.model.OrderStatus
import ai.sotuvchi.app.core.model.OrdersResponse
import ai.sotuvchi.app.core.model.RangePreset
import ai.sotuvchi.app.core.network.isCancellation
import ai.sotuvchi.app.core.network.orders
import ai.sotuvchi.app.core.network.userMessage
import ai.sotuvchi.app.design.Badge
import ai.sotuvchi.app.design.BadgeTone
import ai.sotuvchi.app.design.BarIconButton
import ai.sotuvchi.app.design.Card
import ai.sotuvchi.app.design.EmptyState
import ai.sotuvchi.app.design.ErrorBanner
import ai.sotuvchi.app.design.InputField
import ai.sotuvchi.app.design.LoadingView
import ai.sotuvchi.app.design.PillOption
import ai.sotuvchi.app.design.PillTabs
import ai.sotuvchi.app.design.Radius
import ai.sotuvchi.app.design.ScreenFrame
import ai.sotuvchi.app.design.SearchField
import ai.sotuvchi.app.design.ScreenScroll
import ai.sotuvchi.app.design.Space
import ai.sotuvchi.app.design.Theme
import ai.sotuvchi.app.features.notifications.NotificationBell
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WarningAmber
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
import androidx.compose.ui.text.style.TextAlign
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

class OrdersViewModel(private val model: AppModel) : ViewModel() {
    data class UiState(
        val range: RangePreset = RangePreset.ALL,
        val storeId: String? = null,
        val response: OrdersResponse? = null,
        val search: String = "",
        val isLoading: Boolean = false,
        val error: Throwable? = null,
    ) {
        val visibleOrders: List<Order> get() = (response?.data ?: emptyList()).filter { it.matches(search) }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun setRange(range: RangePreset) = _state.update { it.copy(range = range) }
    fun setStore(storeId: String?) = _state.update { it.copy(storeId = storeId) }
    fun setSearch(search: String) = _state.update { it.copy(search = search) }

    fun load() {
        viewModelScope.launch { reload() }
    }

    suspend fun reload() {
        _state.update { it.copy(isLoading = true) }
        val current = _state.value
        try {
            val response = model.container.api.orders(model.ordersFilter.value, current.range, current.storeId)
            response.data.forEach { model.orderCache[it.id] = it }
            _state.update { it.copy(response = response, error = null, isLoading = false) }
        } catch (error: Exception) {
            if (error.isCancellation) throw error
            _state.update { it.copy(error = error, isLoading = false) }
        }
    }
}

/**
 * Orders by status, newest first. The server caps the list at 100, so the
 * range and store filters narrow it when needed.
 */
@Composable
fun OrdersScreen(model: AppModel, onOpenOrder: (String) -> Unit, onOpenNotifications: () -> Unit) {
    val vm = appViewModel { OrdersViewModel(it) }
    val ui by vm.state.collectAsStateWithLifecycle()
    val app by model.state.collectAsStateWithLifecycle()
    val filter by model.ordersFilter.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var hasResumed by remember { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }

    LaunchedEffect(filter, ui.range, ui.storeId) { vm.reload() }
    LaunchedEffect(app.badges.ordersToReview) { vm.load() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (hasResumed) vm.load()
        hasResumed = true
    }

    ScreenFrame(
        title = L("Orders"),
        actions = {
            androidx.compose.foundation.layout.Box {
                BarIconButton(
                    if (ui.range == RangePreset.ALL && ui.storeId == null) Icons.Outlined.FilterList else Icons.Outlined.FilterList,
                    L("Filter"),
                    { filterMenu = true },
                    tint = if (ui.range == RangePreset.ALL && ui.storeId == null) Theme.colors.ink else Theme.colors.accent,
                )
                FilterMenu(
                    expanded = filterMenu,
                    onDismiss = { filterMenu = false },
                    range = ui.range,
                    onRange = { vm.setRange(it) },
                    storeId = ui.storeId,
                    stores = app.stores.map { it.id to it.name },
                    onStore = { vm.setStore(it) },
                )
            }
            NotificationBell(app.unreadCount, onOpenNotifications)
        },
    ) {
        ScreenScroll(
            spacing = Space.sm,
            refreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    vm.reload()
                    refreshing = false
                }
            },
        ) {
            PillTabs(
                options = OrderFilter.entries.map { option ->
                    PillOption(
                        option,
                        option.title,
                        when (option) {
                            OrderFilter.REVIEW -> app.badges.ordersToReview
                            OrderFilter.OPEN -> ui.response?.summary?.openCount
                            else -> null
                        },
                    )
                },
                selection = filter,
                onSelect = model::setOrdersFilter,
                modifier = Modifier.testTag("orders.tabs"),
            )
            SearchField(ui.search, vm::setSearch, L("№, customer, phone or product"), Modifier.testTag("orders.search"))
            ui.response?.summary?.let { summary ->
                SummaryStrip(summary.approvedSum, summary.orders, ui.range)
            }
            ui.error?.let { ErrorBanner(it.userMessage, onRetry = vm::load) }
            val response = ui.response
            val visible = ui.visibleOrders
            if (response == null && ui.isLoading) {
                LoadingView()
            } else if (response != null && visible.isEmpty()) {
                EmptyState(
                    icon = if (ui.search.isEmpty()) Icons.Outlined.ShoppingBag else Icons.Outlined.Search,
                    title = if (ui.search.isEmpty()) emptyTitle(filter) else L("No orders match your search."),
                    message = if (ui.search.isEmpty()) emptyMessage(filter, ui.range) else null,
                )
            } else {
                visible.forEach { order ->
                    OrderRow(order, showsStore = app.stores.size > 1, onClick = { onOpenOrder(order.id) })
                }
                if ((response?.data?.size ?: 0) >= 100) {
                    Text(
                        L("Showing the latest 100 orders. Narrow the dates or store to see older ones."),
                        style = Theme.type.caption,
                        color = Theme.colors.inkSubtle,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = Space.sm),
                    )
                }
            }
        }
    }
}

private fun emptyTitle(filter: OrderFilter) = when (filter) {
    OrderFilter.REVIEW -> L("No receipts to review")
    else -> L("No orders here yet.")
}

private fun emptyMessage(filter: OrderFilter, range: RangePreset): String? = when (filter) {
    OrderFilter.REVIEW -> L("When a customer sends a payment receipt, the order appears here for you to approve.")
    else -> if (range == RangePreset.ALL) null else L("Try a longer period.")
}

@Composable
private fun FilterMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    range: RangePreset,
    onRange: (RangePreset) -> Unit,
    storeId: String?,
    stores: List<Pair<String, String>>,
    onStore: (String?) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = Theme.colors.surface,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Theme.colors.hairline),
    ) {
        Text(L("Period"), style = Theme.type.caption, color = Theme.colors.inkSubtle, modifier = Modifier.padding(horizontal = Space.md, vertical = Space.xs))
        for (preset in RangePreset.entries) {
            DropdownMenuItem(
                text = { Text(preset.title, style = Theme.type.body, color = Theme.colors.ink) },
                trailingIcon = { if (preset == range) Icon(Icons.Filled.Check, null, Modifier.size(18.dp), Theme.colors.ink) },
                onClick = { onRange(preset); onDismiss() },
            )
        }
        if (stores.size > 1) {
            Text(L("Store"), style = Theme.type.caption, color = Theme.colors.inkSubtle, modifier = Modifier.padding(horizontal = Space.md, vertical = Space.xs))
            DropdownMenuItem(
                text = { Text(L("All stores"), style = Theme.type.body, color = Theme.colors.ink) },
                trailingIcon = { if (storeId == null) Icon(Icons.Filled.Check, null, Modifier.size(18.dp), Theme.colors.ink) },
                onClick = { onStore(null); onDismiss() },
            )
            for ((id, name) in stores) {
                DropdownMenuItem(
                    text = { Text(name, style = Theme.type.body, color = Theme.colors.ink) },
                    trailingIcon = { if (id == storeId) Icon(Icons.Filled.Check, null, Modifier.size(18.dp), Theme.colors.ink) },
                    onClick = { onStore(id); onDismiss() },
                )
            }
        }
    }
}

@Composable
private fun SummaryStrip(approvedSum: Int, orders: Int, range: RangePreset) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Theme.colors.surfaceAlt.copy(alpha = 0.7f), RoundedCornerShape(Radius.lg))
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(L("Approved sales"), style = Theme.type.caption, color = Theme.colors.inkSubtle)
            Text(Money.format(approvedSum), style = Theme.type.subhead, color = Theme.colors.ink, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(range.title, style = Theme.type.caption, color = Theme.colors.inkSubtle)
            Text(L(one = "{count} order", other = "{count} orders", count = orders), style = Theme.type.bodySmall, color = Theme.colors.ink)
        }
    }
}

@Composable
fun OrderRow(order: Order, showsStore: Boolean = false, onClick: () -> Unit) {
    val colors = Theme.colors
    Card(modifier = Modifier.clickable(onClick = onClick).testTag("order.${order.number}")) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text("#${order.number}", style = Theme.type.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = colors.ink)
                OrderStatusBadge(order.status)
                Text(
                    DateText.listStamp(order.createdAt),
                    style = Theme.type.caption,
                    color = colors.inkSubtle,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                order.displayCustomer,
                style = Theme.type.body.copy(fontWeight = FontWeight.Medium),
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (order.items.isNotEmpty()) {
                Text(order.itemsSummary, style = Theme.type.bodySmall, color = colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(Money.format(order.total), style = Theme.type.body.copy(fontWeight = FontWeight.Medium), color = colors.ink)
                val store = order.store?.name
                if (showsStore && store != null) {
                    Text("· $store", style = Theme.type.caption, color = colors.inkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                if (order.externalSyncError != null) {
                    Icon(Icons.Outlined.Sync, contentDescription = L("Not saved in the warehouse"), tint = colors.error, modifier = Modifier.size(18.dp))
                }
                if (order.receiptMismatch != null && order.status == OrderStatus.PROOF_SUBMITTED) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = L("Receipt amount differs"), tint = colors.warningInk, modifier = Modifier.size(18.dp))
                }
                if (order.proofs.isNotEmpty()) {
                    Icon(Icons.Outlined.Receipt, contentDescription = L("Receipt"), tint = colors.inkMuted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun OrderStatusBadge(status: OrderStatus) {
    Badge(
        status.title,
        tone = when (status) {
            OrderStatus.PROOF_SUBMITTED -> BadgeTone.Warning
            OrderStatus.APPROVED -> BadgeTone.Success
            OrderStatus.REJECTED -> BadgeTone.Error
            else -> BadgeTone.Neutral
        },
    )
}
