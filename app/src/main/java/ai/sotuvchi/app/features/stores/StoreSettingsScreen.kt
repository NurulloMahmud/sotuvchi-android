package ai.sotuvchi.app.features.stores

import ai.sotuvchi.app.app.AppModel
import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.model.CardType
import ai.sotuvchi.app.core.model.Store
import ai.sotuvchi.app.core.model.StorePatch
import ai.sotuvchi.app.core.network.ApiError
import ai.sotuvchi.app.core.network.connectTelegram
import ai.sotuvchi.app.core.network.disconnectTelegram
import ai.sotuvchi.app.core.network.isCancellation
import ai.sotuvchi.app.core.network.store
import ai.sotuvchi.app.core.network.testTelegram
import ai.sotuvchi.app.core.network.updateStore
import ai.sotuvchi.app.core.network.userMessage
import ai.sotuvchi.app.design.ButtonKind
import ai.sotuvchi.app.design.Card
import ai.sotuvchi.app.design.ConfirmDialog
import ai.sotuvchi.app.design.EmptyState
import ai.sotuvchi.app.design.ErrorBanner
import ai.sotuvchi.app.design.InputField
import ai.sotuvchi.app.design.KeyValueRow
import ai.sotuvchi.app.design.LabeledField
import ai.sotuvchi.app.design.LoadingView
import ai.sotuvchi.app.design.Notice
import ai.sotuvchi.app.design.PillOption
import ai.sotuvchi.app.design.PillTabs
import ai.sotuvchi.app.design.SButton
import ai.sotuvchi.app.design.ScreenFrame
import ai.sotuvchi.app.design.ScreenScroll
import ai.sotuvchi.app.design.SectionHeader
import ai.sotuvchi.app.design.SelectField
import ai.sotuvchi.app.design.Space
import ai.sotuvchi.app.design.Spinner
import ai.sotuvchi.app.design.Theme
import ai.sotuvchi.app.design.Toast
import ai.sotuvchi.app.design.ToggleRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Storefront
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/** What the form holds while the owner edits. Compared with the saved copy to find changes. */
data class StoreDraft(
    val name: String = "",
    val description: String = "",
    val isActive: Boolean = true,
    val aiEnabled: Boolean = true,
    val aiInstructions: String = "",
    val cardNumber: String = "",
    val cardHolder: String = "",
    val cardType: CardType = CardType.UZCARD,
    val holdMinutes: Int = 30,
    val pickupAddress: String = "",
    val notifyEmail: Boolean = true,
    val notifyLocale: String = "uz",
) {
    companion object {
        fun from(store: Store) = StoreDraft(
            name = store.name,
            description = store.description ?: "",
            isActive = store.isActive,
            aiEnabled = store.aiEnabled,
            aiInstructions = store.aiInstructions ?: "",
            cardNumber = if (store.cardMasked == true) store.cardNumber ?: "" else groupCard(store.cardNumber ?: ""),
            cardHolder = store.cardHolder ?: "",
            cardType = store.cardType ?: CardType.UZCARD,
            holdMinutes = store.holdMinutes ?: 30,
            pickupAddress = store.pickupAddress ?: "",
            notifyEmail = store.notifyEmail ?: true,
            notifyLocale = store.notifyLocale ?: "uz",
        )

        fun groupCard(value: String): String = value.filter { it.isDigit() }.take(16).chunked(4).joinToString(" ")

        fun holdOptions(current: Int): List<Int> = (listOf(15, 30, 60, 120, 240, 720, 1440) + current).toSet().sorted()

        fun durationText(minutes: Int): String =
            if (minutes % 60 == 0) L(one = "{count} hour", other = "{count} hours", count = minutes / 60)
            else L("{count} min", mapOf("count" to minutes))

        /** The changed fields only, or an error message when something does not validate. */
        fun diff(draft: StoreDraft, original: StoreDraft, cardMasked: Boolean): Result<StorePatch> {
            var patch = StorePatch()
            val name = draft.name.trim()
            if (name != original.name) {
                if (name.isEmpty() || name.length > 100) return Result.failure(IllegalArgumentException(L("Enter a store name.")))
                patch = patch.copy(name = name)
            }
            if (draft.description != original.description) patch = patch.copy(description = draft.description.take(1000))
            if (draft.isActive != original.isActive) patch = patch.copy(isActive = draft.isActive)
            if (draft.aiEnabled != original.aiEnabled) patch = patch.copy(aiEnabled = draft.aiEnabled)
            if (draft.aiInstructions != original.aiInstructions) patch = patch.copy(aiInstructions = draft.aiInstructions)
            if (draft.cardNumber != original.cardNumber && !cardMasked) {
                val digits = draft.cardNumber.filter { it.isDigit() }
                if (digits.length != 16) return Result.failure(IllegalArgumentException(L("The card number must have 16 digits.")))
                patch = patch.copy(cardNumber = digits)
            }
            if (draft.cardHolder != original.cardHolder) patch = patch.copy(cardHolder = draft.cardHolder.take(100))
            if (draft.cardType != original.cardType) patch = patch.copy(cardType = draft.cardType.raw)
            if (draft.holdMinutes != original.holdMinutes) patch = patch.copy(holdMinutes = draft.holdMinutes)
            if (draft.pickupAddress != original.pickupAddress) patch = patch.copy(pickupAddress = draft.pickupAddress.take(300))
            if (draft.notifyEmail != original.notifyEmail) patch = patch.copy(notifyEmail = draft.notifyEmail)
            if (draft.notifyLocale != original.notifyLocale) patch = patch.copy(notifyLocale = draft.notifyLocale)
            return Result.success(patch)
        }
    }
}

/**
 * Store settings: what the AI says, how customers pay, how long stock is held,
 * and where order alerts go. Only changed fields are sent.
 */
@Composable
fun StoreSettingsScreen(model: AppModel, storeId: String, onBack: () -> Unit) {
    val app by model.state.collectAsStateWithLifecycle()
    val api = model.container.api
    val scope = rememberCoroutineScope()
    var store by remember { mutableStateOf<Store?>(null) }
    var draft by remember { mutableStateOf(StoreDraft()) }
    var original by remember { mutableStateOf(StoreDraft()) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<Throwable?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val canEdit = app.canManage
    val isDirty = draft != original

    suspend fun load() {
        try {
            val fresh = api.store(storeId)
            val wasDirty = draft != original
            store = fresh
            original = StoreDraft.from(fresh)
            if (!wasDirty) draft = original
            loadError = null
        } catch (e: Exception) {
            if (e.isCancellation) throw e
            if (store == null) loadError = e
        }
    }

    fun save() {
        val current = store ?: return
        val patch = StoreDraft.diff(draft, original, current.cardMasked == true).getOrElse {
            errorMessage = it.message
            return
        }
        if (patch.isEmpty) return
        scope.launch {
            isSaving = true
            try {
                val updated = api.updateStore(storeId, patch)
                store = updated
                original = StoreDraft.from(updated)
                draft = original
                errorMessage = null
                model.show(Toast(Toast.Kind.Success, L("Store settings saved.")))
                model.refreshStores()
            } catch (e: Exception) {
                if (e.isCancellation) throw e
                errorMessage = e.userMessage
            } finally {
                isSaving = false
            }
        }
    }

    LaunchedEffect(storeId) { load() }

    ScreenFrame(
        title = store?.name ?: L("Store settings"),
        onBack = onBack,
        actions = {
            if (canEdit && isDirty) {
                if (isSaving) Spinner(Modifier.padding(end = Space.sm))
                else Text(
                    L("Save"),
                    style = Theme.type.button.copy(fontWeight = FontWeight.SemiBold),
                    color = Theme.colors.ink,
                    modifier = Modifier.clickable { save() }.padding(Space.sm).testTag("store.save"),
                )
            }
        },
    ) {
        val current = store
        when {
            current != null -> ScreenScroll(
                refreshing = refreshing,
                onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
            ) {
                errorMessage?.let { ErrorBanner(it) }
                if (!canEdit) Notice(L("Only the owner or an admin can change store settings."), icon = Icons.Outlined.Lock)
                Form(current, draft, canEdit) { draft = it }
                TelegramCard(model, current, canEdit, onChange = { load() })
                WarehouseCard(model, current)
            }
            loadError != null -> EmptyState(
                Icons.Outlined.Storefront,
                loadError?.userMessage ?: "",
                actionTitle = L("Retry"),
                onAction = { scope.launch { load() } },
            )
            else -> LoadingView()
        }
    }
}

@Composable
private fun Form(store: Store, draft: StoreDraft, canEdit: Boolean, onChange: (StoreDraft) -> Unit) {
    val colors = Theme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        SectionHeader(L("Store"))
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                LabeledField(L("Store name")) {
                    InputField(draft.name, { onChange(draft.copy(name = it)) }, placeholder = L("Store name"), enabled = canEdit, modifier = Modifier.testTag("store.name"))
                }
                LabeledField(L("Description"), hint = L("What you sell. The AI introduces the store with this.")) {
                    InputField(draft.description, { onChange(draft.copy(description = it)) }, placeholder = L("Optional"), multiline = true, minLines = 2, enabled = canEdit)
                }
                ToggleRow(
                    L("Store is active"), draft.isActive, { onChange(draft.copy(isActive = it)) },
                    subtitle = L("While paused, the AI does not sell from this store."), enabled = canEdit,
                )
            }
        }

        SectionHeader(L("AI seller"))
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                ToggleRow(
                    L("AI replies to new chats"), draft.aiEnabled, { onChange(draft.copy(aiEnabled = it)) },
                    subtitle = L("Turn it off to answer every chat yourself."), enabled = canEdit, tint = colors.accent,
                    modifier = Modifier.testTag("store.aiEnabled"),
                )
                LabeledField(L("Instructions for the AI seller"), hint = L("Tone, return policy, anything the AI should know.")) {
                    InputField(
                        draft.aiInstructions, { onChange(draft.copy(aiInstructions = it.take(2000))) },
                        placeholder = L("For example: be polite, delivery in Tashkent takes one day"),
                        multiline = true, minLines = 4, enabled = canEdit,
                        modifier = Modifier.testTag("store.instructions"),
                    )
                }
            }
        }

        SectionHeader(L("Payment card"))
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Text(L("Customers transfer money to this card. The AI sends it with the order."), style = Theme.type.caption, color = colors.inkSubtle)
                LabeledField(L("Card number")) {
                    InputField(
                        draft.cardNumber,
                        { if (store.cardMasked != true) onChange(draft.copy(cardNumber = StoreDraft.groupCard(it))) },
                        placeholder = "8600 0000 0000 0000",
                        keyboardType = KeyboardType.Number,
                        enabled = canEdit && store.cardMasked != true,
                        modifier = Modifier.testTag("store.cardNumber"),
                    )
                }
                LabeledField(L("Card holder")) {
                    InputField(
                        draft.cardHolder, { onChange(draft.copy(cardHolder = it)) },
                        placeholder = "A. Karimov", enabled = canEdit, capitalization = KeyboardCapitalization.Characters,
                    )
                }
                LabeledField(L("Card type")) {
                    PillTabs(
                        options = listOf(PillOption(CardType.UZCARD, "Uzcard"), PillOption(CardType.HUMO, "Humo")),
                        selection = draft.cardType,
                        onSelect = { if (canEdit) onChange(draft.copy(cardType = it)) },
                    )
                }
            }
        }

        SectionHeader(L("Orders"))
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                LabeledField(L("Hold stock while paying"), hint = L("How long a new order keeps the items for the customer while they pay.")) {
                    SelectField(
                        text = StoreDraft.durationText(draft.holdMinutes),
                        options = StoreDraft.holdOptions(draft.holdMinutes).map { it to StoreDraft.durationText(it) },
                        onSelect = { onChange(draft.copy(holdMinutes = it)) },
                        enabled = canEdit,
                    )
                }
                LabeledField(L("Pickup address"), hint = L("Leave empty if you do not offer pickup")) {
                    InputField(draft.pickupAddress, { onChange(draft.copy(pickupAddress = it)) }, placeholder = L("Optional"), multiline = true, enabled = canEdit)
                }
            }
        }

        SectionHeader(L("Alerts"))
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                ToggleRow(
                    L("Email alerts"), draft.notifyEmail, { onChange(draft.copy(notifyEmail = it)) },
                    subtitle = L("Email the workspace owners and admins"), enabled = canEdit,
                )
                LabeledField(L("Language of email and Telegram alerts")) {
                    PillTabs(
                        options = listOf(PillOption("uz", "O'zbekcha"), PillOption("ru", "Русский"), PillOption("en", "English")),
                        selection = draft.notifyLocale,
                        onSelect = { if (canEdit) onChange(draft.copy(notifyLocale = it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WarehouseCard(model: AppModel, store: Store) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        SectionHeader(L("Warehouse"))
        Card {
            Column {
                KeyValueRow(L("Products"), store.catalogSource.title)
                val integration = store.integration
                if (integration != null && store.isExternalCatalog) {
                    KeyValueRow(
                        L("Connection"),
                        if (integration.connected) L("Connected") else L("Not connected"),
                        valueColor = if (integration.connected) Theme.colors.successInk else Theme.colors.error,
                    )
                    integration.warehouseName?.let { KeyValueRow(L("Warehouse"), it) }
                    integration.organizationName?.let { KeyValueRow(L("Legal entity"), it) }
                    SButton(
                        L("Change on the web"), { model.openWeb("/stores") },
                        kind = ButtonKind.Secondary, fullWidth = false, compact = true,
                        modifier = Modifier.padding(top = Space.xs),
                    )
                }
            }
        }
    }
}

/**
 * Telegram order alerts. The only way to get alerts while the app is closed
 * until the backend supports push notifications.
 */
@Composable
private fun TelegramCard(model: AppModel, store: Store, canEdit: Boolean, onChange: suspend () -> Unit) {
    val colors = Theme.colors
    val api = model.container.api
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var isWorking by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }

    fun work(block: suspend () -> Unit) {
        scope.launch {
            isWorking = true
            try {
                block()
            } catch (e: ApiError) {
                // 422 carries a message that tells the owner what to fix.
                val message = e.serverMessage
                if (message != null) model.show(Toast(Toast.Kind.Error, message)) else model.showError(e)
            } catch (e: Exception) {
                if (e.isCancellation) throw e
                model.showError(e)
            } finally {
                isWorking = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        SectionHeader(L("Telegram alerts"))
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                if (store.telegramConfigured == true) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.CheckCircle, null, tint = colors.successInk, modifier = Modifier.size(18.dp))
                        Text(L("Connected. New orders and receipts are sent to your Telegram."), style = Theme.type.bodySmall, color = colors.successInk)
                    }
                    if (canEdit) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            SButton(
                                L("Send a test"),
                                { work { api.testTelegram(store.id); model.show(Toast(Toast.Kind.Success, L("Test alert sent. Check your Telegram."))) } },
                                kind = ButtonKind.Secondary, loading = isWorking, enabled = !isWorking,
                                modifier = Modifier.weight(1f).testTag("telegram.test"),
                            )
                            SButton(
                                L("Disconnect"), { confirmDisconnect = true },
                                kind = ButtonKind.Destructive, enabled = !isWorking,
                                modifier = Modifier.weight(1f).testTag("telegram.disconnect"),
                            )
                        }
                    }
                } else {
                    Text(
                        L("Get an alert in Telegram for every new order and receipt, even when this app is closed."),
                        style = Theme.type.bodySmall, color = colors.inkMuted,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Step(1, L("Create a bot with @BotFather and copy its token."))
                        Step(2, L("Open your bot and press Start."))
                        Step(3, L("Paste the token here and connect."))
                    }
                    if (canEdit) {
                        InputField(token, { token = it }, placeholder = "123456:ABC-DEF…", modifier = Modifier.testTag("telegram.token"))
                        SButton(
                            L("Connect Telegram"),
                            {
                                work {
                                    val result = api.connectTelegram(store.id, token.trim())
                                    token = ""
                                    model.show(
                                        Toast(
                                            Toast.Kind.Success,
                                            result.botUsername?.let { L("Connected to @{bot}. A confirmation was sent to your Telegram.", mapOf("bot" to it)) }
                                                ?: L("Connected. A confirmation was sent to your Telegram."),
                                        ),
                                    )
                                    onChange()
                                }
                            },
                            loading = isWorking, enabled = !isWorking && token.isNotBlank(),
                            modifier = Modifier.testTag("telegram.connect"),
                        )
                    }
                }
            }
        }
    }
    if (confirmDisconnect) {
        ConfirmDialog(
            title = L("Disconnect Telegram alerts?"),
            confirmText = L("Disconnect"),
            destructive = true,
            onConfirm = { confirmDisconnect = false; work { api.disconnectTelegram(store.id); onChange() } },
            onDismiss = { confirmDisconnect = false },
        )
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.Top) {
        Text("$number.", style = Theme.type.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = Theme.colors.inkMuted)
        Text(text, style = Theme.type.bodySmall, color = Theme.colors.ink)
    }
}
