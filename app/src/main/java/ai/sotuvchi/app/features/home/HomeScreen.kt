package ai.sotuvchi.app.features.home

import ai.sotuvchi.app.app.AppModel
import ai.sotuvchi.app.app.appViewModel
import ai.sotuvchi.app.core.format.DateText
import ai.sotuvchi.app.core.format.Money
import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.model.DashboardStats
import ai.sotuvchi.app.core.model.OrderFilter
import ai.sotuvchi.app.core.model.RangePreset
import ai.sotuvchi.app.core.model.SalesStats
import ai.sotuvchi.app.core.model.Trend
import ai.sotuvchi.app.core.network.dashboard
import ai.sotuvchi.app.core.network.isCancellation
import ai.sotuvchi.app.core.network.userMessage
import ai.sotuvchi.app.design.AIMark
import ai.sotuvchi.app.design.ButtonKind
import ai.sotuvchi.app.design.Card
import ai.sotuvchi.app.design.EmptyState
import ai.sotuvchi.app.design.ErrorBanner
import ai.sotuvchi.app.design.HairlineDivider
import ai.sotuvchi.app.design.LoadingView
import ai.sotuvchi.app.design.NavRow
import ai.sotuvchi.app.design.Notice
import ai.sotuvchi.app.design.NoticeTone
import ai.sotuvchi.app.design.PillOption
import ai.sotuvchi.app.design.PillTabs
import ai.sotuvchi.app.design.SButton
import ai.sotuvchi.app.design.ScreenFrame
import ai.sotuvchi.app.design.ScreenScroll
import ai.sotuvchi.app.design.Space
import ai.sotuvchi.app.design.StatTile
import ai.sotuvchi.app.design.Theme
import ai.sotuvchi.app.design.TrendLabel
import ai.sotuvchi.app.design.cardBackground
import ai.sotuvchi.app.features.notifications.NotificationBell
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(private val model: AppModel) : ViewModel() {
    data class UiState(
        val range: RangePreset = RangePreset.MONTH,
        val stats: DashboardStats? = null,
        val isLoading: Boolean = false,
        val error: Throwable? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun setRange(range: RangePreset) {
        if (range == _state.value.range) return
        _state.update { it.copy(range = range) }
        load()
    }

    fun load() {
        viewModelScope.launch { reload() }
    }

    suspend fun reload() {
        _state.update { it.copy(isLoading = it.stats == null) }
        try {
            val fresh = model.container.api.dashboard(_state.value.range)
            _state.update { it.copy(stats = fresh, error = null, isLoading = false) }
            model.apply(fresh)
        } catch (error: Exception) {
            if (error.isCancellation) throw error
            _state.update { it.copy(error = error, isLoading = false) }
        }
    }
}

private val rangeOptions = listOf(RangePreset.TODAY, RangePreset.WEEK, RangePreset.MONTH, RangePreset.ALL)

/**
 * Home dashboard: what needs the owner now, then sales for the period, then
 * comment-campaign figures.
 */
@Composable
fun HomeScreen(model: AppModel, onOpenNotifications: () -> Unit, onOpenCampaigns: () -> Unit) {
    val vm = appViewModel { HomeViewModel(it) }
    val ui by vm.state.collectAsStateWithLifecycle()
    val app by model.state.collectAsStateWithLifecycle()
    val stats = ui.stats

    ScreenFrame(
        title = L("Home"),
        actions = { NotificationBell(app.unreadCount, onOpenNotifications) },
    ) {
        var refreshing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        ScreenScroll(
            refreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    vm.reload()
                    model.refreshNotifications()
                    refreshing = false
                }
            },
        ) {
            Greeting(workspace = stats?.workspace?.name, name = stats?.userName ?: app.userName)
            PillTabs(
                options = rangeOptions.map { PillOption(it, it.title) },
                selection = ui.range,
                onSelect = vm::setRange,
            )
            ui.error?.let { ErrorBanner(it.userMessage, onRetry = vm::load) }
            if (app.isNewAccount) WelcomeCard(onSetup = { model.openWeb("/settings") }, onLater = model::dismissNewAccount)
            NeedsYouCard(
                ordersToReview = app.badges.ordersToReview,
                chatsNeedingYou = app.badges.chatsNeedingYou,
                onReview = { model.showOrders(OrderFilter.REVIEW) },
                onChats = { model.selectTab(ai.sotuvchi.app.app.AppTab.Chats) },
            )
            PlanNotice(stats)
            if (stats != null) {
                val sales = stats.sales
                if (sales != null) {
                    RevenueCard(sales, ui.range)
                    KpiGrid(sales, app.badges.ordersToReview, model)
                    ChartCard(sales)
                    TopProductsCard(sales)
                } else if (!app.isNewAccount) {
                    Card {
                        EmptyState(
                            icon = Icons.Outlined.Storefront,
                            title = L("No store yet"),
                            message = L("Connect Instagram and create your store on the web. Sales show up here as soon as the AI takes its first order."),
                            actionTitle = L("Open stores on the web"),
                            onAction = { model.openWeb("/stores") },
                        )
                    }
                }
                CampaignsCard(stats, onOpenCampaigns)
            } else if (ui.isLoading) {
                LoadingView()
            }
        }
    }
}

@Composable
private fun Greeting(workspace: String?, name: String?) {
    Column(
        modifier = Modifier.padding(top = Space.xs, start = Space.xxs, end = Space.xxs),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!workspace.isNullOrEmpty()) Text(workspace, style = Theme.type.eyebrow, color = Theme.colors.inkMuted)
        val first = name?.split(" ")?.firstOrNull { it.isNotEmpty() }
        Text(
            if (!first.isNullOrEmpty()) L("Hello, {name}", mapOf("name" to first)) else L("Hello"),
            style = Theme.type.headline,
            color = Theme.colors.ink,
        )
    }
}

@Composable
private fun NeedsYouCard(ordersToReview: Int, chatsNeedingYou: Int, onReview: () -> Unit, onChats: () -> Unit) {
    if (ordersToReview <= 0 && chatsNeedingYou <= 0) return
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text(L("Needs you"), style = Theme.type.cardTitle, color = Theme.colors.ink)
            if (ordersToReview > 0) {
                NavRow(
                    Icons.Outlined.Receipt,
                    L(one = "{count} receipt to review", other = "{count} receipts to review", count = ordersToReview),
                    onClick = onReview,
                )
            }
            if (ordersToReview > 0 && chatsNeedingYou > 0) HairlineDivider()
            if (chatsNeedingYou > 0) {
                NavRow(
                    Icons.Outlined.PanTool,
                    L(one = "{count} chat needs you", other = "{count} chats need you", count = chatsNeedingYou),
                    onClick = onChats,
                )
            }
            if (ordersToReview > 0) {
                SButton(L("Review orders"), onReview, modifier = Modifier.padding(top = Space.xxs))
            }
        }
    }
}

/** Information only, with no link to pay: store policy for subscriptions sold inside an app. */
@Composable
private fun PlanNotice(stats: DashboardStats?) {
    val expires = stats?.workspace?.planExpiresAt ?: return
    if (stats.billingEnabled != true) return
    if (Duration.between(Instant.now(), expires) >= Duration.ofDays(7)) return
    val date = DateText.shortDate(expires)
    Notice(
        text = if (expires.isBefore(Instant.now())) L("Your plan ended {date}.", mapOf("date" to date))
        else L("Your plan ends {date}.", mapOf("date" to date)),
        tone = NoticeTone.Warning,
        icon = Icons.Outlined.Verified,
        modifier = Modifier.testTag("planNotice"),
    )
}

@Composable
private fun RevenueCard(sales: SalesStats, range: RangePreset) {
    Card(padding = Space.lg) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(L("Approved sales"), style = Theme.type.eyebrow, color = Theme.colors.inkMuted)
            val total = Money.format(sales.approvedSum ?: 0)
            // Long amounts step down so the figure stays on one line.
            val size = when {
                total.length <= 13 -> 34
                total.length <= 17 -> 28
                else -> 22
            }
            Text(
                total,
                style = Theme.type.display.copy(fontSize = size.sp, lineHeight = (size * 1.2f).sp),
                color = Theme.colors.ink,
                maxLines = 1,
            )
            val change = Trend.change(sales.approvedSum, sales.previous?.approvedSum)
            val label = range.comparisonLabel
            if (change != null && label != null) TrendLabel(change, caption = label)
            Text(salesSummary(sales), style = Theme.type.bodySmall, color = Theme.colors.inkMuted)
        }
    }
}

private fun salesSummary(sales: SalesStats): String {
    val parts = mutableListOf(
        L("{approved} of {orders} orders approved", mapOf("approved" to (sales.approvedCount ?: 0), "orders" to (sales.orders ?: 0))),
    )
    sales.averageOrder?.let { parts += L("average order {amount}", mapOf("amount" to Money.format(it))) }
    return parts.joinToString(" · ")
}

@Composable
private fun KpiGrid(sales: SalesStats, ordersToReview: Int, model: AppModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        // IntrinsicSize.Max makes both tiles in a row as tall as the taller one.
        Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            StatTile(
                L("Orders"), (sales.orders ?: 0).toString(),
                change = Trend.change(sales.orders, sales.previous?.orders),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatTile(
                L("Approved"), (sales.approvedCount ?: 0).toString(),
                change = Trend.change(sales.approvedCount, sales.previous?.approvedCount),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            StatTile(
                L("Open orders"), (sales.openCount ?: 0).toString(),
                onClick = { model.showOrders(OrderFilter.OPEN) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatTile(
                L("To review"), ordersToReview.toString(),
                onClick = { model.showOrders(OrderFilter.REVIEW) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun ChartCard(sales: SalesStats) {
    val days = (sales.daily ?: emptyList()).mapNotNull { entry -> entry.day?.let { it to (entry.revenue ?: 0) } }
    Card(modifier = Modifier.testTag("home.chart")) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Text(L("Revenue"), style = Theme.type.cardTitle, color = Theme.colors.ink)
            if (days.isEmpty() || days.all { it.second == 0 }) {
                Text(
                    L("No approved sales in this period yet"),
                    style = Theme.type.bodySmall,
                    color = Theme.colors.inkMuted,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                )
            } else {
                RevenueChart(days)
            }
        }
    }
}

@Composable
private fun TopProductsCard(sales: SalesStats) {
    val products = (sales.topProducts ?: emptyList()).take(5)
    if (products.isEmpty()) return
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(L("Top products"), style = Theme.type.cardTitle, color = Theme.colors.ink)
                Text(L("From approved orders"), style = Theme.type.caption, color = Theme.colors.inkSubtle)
            }
            products.forEachIndexed { index, product ->
                if (index > 0) HairlineDivider()
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    Text((index + 1).toString(), style = Theme.type.bodySmall, color = Theme.colors.inkSubtle, modifier = Modifier.width(18.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(product.name, style = Theme.type.body, color = Theme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(L("{count} sold", mapOf("count" to (product.quantity ?: 0))), style = Theme.type.caption, color = Theme.colors.inkSubtle)
                    }
                    Text(Money.format(product.revenue ?: 0), style = Theme.type.bodySmall, color = Theme.colors.ink)
                }
            }
        }
    }
}

/** First sign-in of a new account: the store is set up on the web. */
@Composable
private fun WelcomeCard(onSetup: () -> Unit, onLater: () -> Unit) {
    Card(padding = Space.lg) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            AIMark(label = "Sotuvchi")
            Text(L("Welcome to Sotuvchi"), style = Theme.type.cardTitle, color = Theme.colors.ink)
            Text(
                L("Your account is ready. Connect your Instagram account and set up your store on the web, then orders and chats show up here."),
                style = Theme.type.body,
                color = Theme.colors.inkMuted,
            )
            SButton(L("Set up on the web"), onSetup, modifier = Modifier.padding(top = Space.xxs))
            SButton(L("Later"), onLater, kind = ButtonKind.Tertiary)
        }
    }
}

@Composable
private fun CampaignsCard(stats: DashboardStats, onClick: () -> Unit) {
    Card(modifier = Modifier.clickable(onClick = onClick)) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(L("Comment campaigns"), style = Theme.type.cardTitle, color = Theme.colors.ink)
                    Text(
                        L("{active} of {total} active", mapOf("active" to (stats.activeAutomations ?: 0), "total" to (stats.totalAutomations ?: 0))),
                        style = Theme.type.caption,
                        color = Theme.colors.inkSubtle,
                    )
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Theme.colors.inkTertiary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                Metric(L("DMs sent"), (stats.period?.dmsSent ?: 0).toString(), Trend.change(stats.period?.dmsSent, stats.previous?.dmsSent), Modifier.weight(1f))
                Metric(L("Clicks"), (stats.period?.clicks ?: 0).toString(), Trend.change(stats.period?.clicks, stats.previous?.clicks), Modifier.weight(1f))
                Metric(L("CTR"), String.format(java.util.Locale.US, "%.1f%%", stats.period?.ctr ?: 0.0), null, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Metric(title: String, value: String, change: Double?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = Theme.type.caption, color = Theme.colors.inkSubtle)
        Text(value, style = Theme.type.subhead, color = Theme.colors.ink)
        if (change != null) TrendLabel(change)
    }
}
