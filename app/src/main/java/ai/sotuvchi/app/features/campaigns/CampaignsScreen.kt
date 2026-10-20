package ai.sotuvchi.app.features.campaigns

import ai.sotuvchi.app.app.AppModel
import ai.sotuvchi.app.core.format.DateText
import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.model.Campaign
import ai.sotuvchi.app.core.model.DmLog
import ai.sotuvchi.app.core.model.DmStatus
import ai.sotuvchi.app.core.network.ApiError
import ai.sotuvchi.app.core.network.campaignLogs
import ai.sotuvchi.app.core.network.campaigns
import ai.sotuvchi.app.core.network.isCancellation
import ai.sotuvchi.app.core.network.setCampaignActive
import ai.sotuvchi.app.core.network.userMessage
import ai.sotuvchi.app.design.Badge
import ai.sotuvchi.app.design.BadgeTone
import ai.sotuvchi.app.design.BarIconButton
import ai.sotuvchi.app.design.ButtonKind
import ai.sotuvchi.app.design.Card
import ai.sotuvchi.app.design.EmptyState
import ai.sotuvchi.app.design.ErrorBanner
import ai.sotuvchi.app.design.HairlineDivider
import ai.sotuvchi.app.design.LoadingView
import ai.sotuvchi.app.design.PillOption
import ai.sotuvchi.app.design.PillTabs
import ai.sotuvchi.app.design.Radius
import ai.sotuvchi.app.design.SButton
import ai.sotuvchi.app.design.SSwitch
import ai.sotuvchi.app.design.ScreenFrame
import ai.sotuvchi.app.design.ScreenScroll
import ai.sotuvchi.app.design.Space
import ai.sotuvchi.app.design.Spinner
import ai.sotuvchi.app.design.Theme
import ai.sotuvchi.app.design.Toast
import ai.sotuvchi.app.design.cardBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ListAlt
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Comment-to-DM campaigns: see results and pause or resume on the go. Creating
 * and editing stays on the web, where the post picker lives.
 */
@Composable
fun CampaignsScreen(model: AppModel, onBack: () -> Unit, onOpenLogs: () -> Unit) {
    val app by model.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var campaigns by remember { mutableStateOf<List<Campaign>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var togglingId by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val api = model.container.api

    suspend fun load() {
        try {
            campaigns = api.campaigns()
            loaded = true
            error = null
        } catch (e: ApiError.Invalid) {
            // 400 until an Instagram account is connected.
            campaigns = emptyList()
            loaded = true
        } catch (e: Exception) {
            loaded = true
            if (e.isCancellation) throw e
            error = e
        }
    }

    fun setActive(campaign: Campaign, isActive: Boolean) {
        scope.launch {
            togglingId = campaign.id
            try {
                api.setCampaignActive(campaign.id, isActive)
                model.show(Toast(Toast.Kind.Success, if (isActive) L("Campaign resumed.") else L("Campaign paused.")))
                load()
            } catch (e: Exception) {
                if (e.isCancellation) throw e
                model.showError(e)
            } finally {
                togglingId = null
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    ScreenFrame(
        title = L("Campaigns"),
        onBack = onBack,
        actions = { BarIconButton(Icons.Outlined.ListAlt, L("DM log"), onOpenLogs, modifier = Modifier.testTag("campaigns.logs")) },
    ) {
        ScreenScroll(
            spacing = Space.sm,
            refreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
        ) {
            error?.let { ErrorBanner(it.userMessage, onRetry = { scope.launch { load() } }) }
            if (!loaded) {
                LoadingView()
            } else if (campaigns.isEmpty()) {
                EmptyState(
                    Icons.Outlined.ChatBubbleOutline,
                    L("No campaigns yet"),
                    message = L("A campaign sends a DM when someone comments a keyword on your post. Create one on the web."),
                    actionTitle = L("Create a campaign"),
                    onAction = { model.openWeb("/campaigns/new") },
                )
            } else {
                campaigns.forEach { campaign ->
                    CampaignCard(campaign, canManage = app.canManage, toggling = togglingId == campaign.id, onToggle = { setActive(campaign, it) })
                }
                SButton(L("Create or edit campaigns on the web"), { model.openWeb("/campaigns") }, kind = ButtonKind.Tertiary)
            }
        }
    }
}

@Composable
private fun CampaignCard(campaign: Campaign, canManage: Boolean, toggling: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = Theme.colors
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (campaign.name.isEmpty()) L("Untitled campaign") else campaign.name,
                        style = Theme.type.body.copy(fontWeight = FontWeight.Medium),
                        color = colors.ink,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Badge(
                            if (campaign.isActive) L("Active") else L("Paused"),
                            tone = if (campaign.isActive) BadgeTone.Success else BadgeTone.Neutral,
                        )
                        campaign.instagramAccount?.username?.let { Text("@$it", style = Theme.type.caption, color = colors.inkSubtle) }
                    }
                }
                if (canManage) {
                    if (toggling) Spinner(modifier = Modifier.padding(8.dp))
                    else SSwitch(campaign.isActive, onToggle, modifier = Modifier.testTag("campaign.toggle"))
                }
            }
            Text(
                campaign.targetDescription + " · " + campaign.triggerDescription,
                style = Theme.type.caption,
                color = colors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            campaign.dmMessage?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    it,
                    style = Theme.type.bodySmall,
                    color = colors.ink,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceAlt.copy(alpha = 0.6f), RoundedCornerShape(Radius.md))
                        .padding(Space.sm),
                )
            }
            campaign.analytics?.let { a ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    Stat(L("Sent"), (a.sent ?: 0).toString(), Modifier.weight(1f))
                    Stat(L("Clicks"), (a.clicks ?: 0).toString(), Modifier.weight(1f))
                    Stat(L("CTR"), String.format(Locale.US, "%.1f%%", a.ctr ?: 0.0), Modifier.weight(1f))
                    Stat(L("Failed"), (a.failed ?: 0).toString(), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Stat(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = Theme.type.caption, color = Theme.colors.inkSubtle)
        Text(value, style = Theme.type.body.copy(fontWeight = FontWeight.Medium), color = Theme.colors.ink)
    }
}

/** What each campaign sent, skipped or failed. Paged. */
@Composable
fun CampaignLogsScreen(model: AppModel, onBack: () -> Unit) {
    val api = model.container.api
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf<List<DmLog>>(emptyList()) }
    var page by remember { mutableStateOf(0) }
    var totalPages by remember { mutableStateOf(1) }
    var status by remember { mutableStateOf<DmStatus?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val statusOptions = listOf<DmStatus?>(null, DmStatus.SENT, DmStatus.FAILED, DmStatus.SKIPPED_DEDUP, DmStatus.SKIPPED_RATE_LIMIT, DmStatus.SKIPPED_PLAN_LIMIT)

    suspend fun loadMore() {
        if (isLoading || page >= totalPages) return
        isLoading = true
        try {
            val result = api.campaignLogs(page + 1, status)
            logs = logs + result.logs.filter { new -> logs.none { it.id == new.id } }
            page = result.pagination.page
            totalPages = result.pagination.totalPages
            error = null
        } catch (e: Exception) {
            if (e.isCancellation) throw e
            error = e
        } finally {
            isLoading = false
        }
    }

    suspend fun reload() {
        page = 0
        totalPages = 1
        logs = emptyList()
        loadMore()
    }

    LaunchedEffect(status) { reload() }

    ScreenFrame(title = L("DM log"), onBack = onBack) {
        ScreenScroll(
            spacing = Space.sm,
            refreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; reload(); refreshing = false } },
        ) {
            PillTabs(
                options = statusOptions.map { PillOption(it, it?.title ?: L("All")) },
                selection = status,
                onSelect = { status = it },
            )
            error?.let { ErrorBanner(it.userMessage, onRetry = { scope.launch { reload() } }) }
            if (logs.isEmpty() && !isLoading) EmptyState(Icons.Outlined.ListAlt, L("Nothing here yet"))
            if (logs.isNotEmpty()) {
                Column(modifier = Modifier.cardBackground()) {
                    logs.forEachIndexed { index, log ->
                        if (index > 0) HairlineDivider()
                        LogRow(log)
                        if (index == logs.lastIndex) LaunchedEffect(log.id) { loadMore() }
                    }
                }
            }
            if (isLoading) LoadingViewSmall()
        }
    }
}

@Composable
private fun LoadingViewSmall() {
    Row(Modifier.fillMaxWidth().padding(Space.md), horizontalArrangement = Arrangement.Center) { Spinner() }
}

@Composable
private fun LogRow(log: DmLog) {
    val colors = Theme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                log.commenterName?.let { "@$it" } ?: L("Unknown"),
                style = Theme.type.body.copy(fontWeight = FontWeight.Medium),
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            Badge(
                log.status.title,
                tone = when (log.status.tone) {
                    "success" -> BadgeTone.Success
                    "error" -> BadgeTone.Error
                    "neutral" -> BadgeTone.Neutral
                    else -> BadgeTone.Warning
                },
            )
        }
        log.commentText?.takeIf { it.isNotEmpty() }?.let {
            Text("“$it”", style = Theme.type.bodySmall, color = colors.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val parts = buildList {
            log.automation?.name?.let { add(it) }
            log.matchedKeyword?.let { add("· $it") }
            add("· " + DateText.relative(log.createdAt))
        }
        Text(parts.joinToString(" "), style = Theme.type.caption, color = colors.inkSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        log.errorMessage?.takeIf { it.isNotEmpty() }?.let { Text(it, style = Theme.type.caption, color = colors.error) }
    }
}
