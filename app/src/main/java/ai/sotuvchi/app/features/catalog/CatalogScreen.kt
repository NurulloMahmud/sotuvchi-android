package ai.sotuvchi.app.features.catalog

import ai.sotuvchi.app.app.AppModel
import ai.sotuvchi.app.app.appViewModel
import ai.sotuvchi.app.core.format.Money
import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.model.Product
import ai.sotuvchi.app.core.model.ProductCategory
import ai.sotuvchi.app.core.model.Store
import ai.sotuvchi.app.core.network.categories
import ai.sotuvchi.app.core.network.isCancellation
import ai.sotuvchi.app.core.network.products
import ai.sotuvchi.app.core.network.userMessage
import ai.sotuvchi.app.design.Badge
import ai.sotuvchi.app.design.BadgeTone
import ai.sotuvchi.app.design.BarIconButton
import ai.sotuvchi.app.design.Card
import ai.sotuvchi.app.design.EmptyState
import ai.sotuvchi.app.design.ErrorBanner
import ai.sotuvchi.app.design.KeyValueRow
import ai.sotuvchi.app.design.LoadingView
import ai.sotuvchi.app.design.Notice
import ai.sotuvchi.app.design.PillOption
import ai.sotuvchi.app.design.PillTabs
import ai.sotuvchi.app.design.Radius
import ai.sotuvchi.app.design.RemoteImage
import ai.sotuvchi.app.design.ScreenFrame
import ai.sotuvchi.app.design.ScreenScroll
import ai.sotuvchi.app.design.SearchField
import ai.sotuvchi.app.design.SectionHeader
import ai.sotuvchi.app.design.SotuvchiSheet
import ai.sotuvchi.app.design.Space
import ai.sotuvchi.app.design.Theme
import ai.sotuvchi.app.design.cardBackground
import ai.sotuvchi.app.features.notifications.NotificationBell
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STORE_KEY = "catalog.storeId"

class CatalogViewModel(private val model: AppModel) : ViewModel() {
    data class UiState(
        val savedStoreId: String = "",
        val products: List<Product> = emptyList(),
        val isExternal: Boolean = false,
        val categories: List<ProductCategory> = emptyList(),
        val categoryFilter: String = "",
        val search: String = "",
        val isLoading: Boolean = false,
        val loadedStoreId: String? = null,
        val error: Throwable? = null,
    ) {
        val visible: List<Product>
            get() {
                val query = search.trim().lowercase()
                return products
                    .filter { categoryFilter.isEmpty() || it.categoryId == categoryFilter || it.category?.id == categoryFilter }
                    .filter { product ->
                        isExternal || query.isEmpty() || product.name.lowercase().contains(query) ||
                            (product.description?.lowercase()?.contains(query) ?: false)
                    }
            }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = model.container.kv.get(STORE_KEY) ?: ""
            _state.update { it.copy(savedStoreId = saved) }
        }
    }

    fun setStore(id: String) {
        _state.update { it.copy(savedStoreId = id, categoryFilter = "") }
        viewModelScope.launch { model.container.kv.set(STORE_KEY, id) }
    }

    fun setSearch(value: String) = _state.update { it.copy(search = value) }
    fun setCategoryFilter(value: String) = _state.update { it.copy(categoryFilter = value) }

    suspend fun load(store: Store?) {
        if (store == null) return
        _state.update { it.copy(isLoading = true) }
        try {
            val api = model.container.api
            val current = _state.value
            val response = api.products(store.id, search = if (current.isExternal) current.search else null)
            val external = response.external ?: false
            response.data.forEach { model.productCache[it.id] = it }
            val categories = if (!external && !store.isExternalCatalog) {
                try { api.categories(store.id) } catch (e: Exception) { if (e.isCancellation) throw e; emptyList() }
            } else emptyList()
            _state.update {
                it.copy(
                    products = response.data,
                    isExternal = external,
                    categories = categories,
                    categoryFilter = if (it.categoryFilter.isNotEmpty() && categories.none { c -> c.id == it.categoryFilter }) "" else it.categoryFilter,
                    loadedStoreId = store.id,
                    error = null,
                    isLoading = false,
                )
            }
        } catch (error: Exception) {
            if (error.isCancellation) throw error
            _state.update { it.copy(error = error, isLoading = false) }
        }
    }
}

/**
 * Products of one store. Manual catalogues are editable here; MoySklad
 * catalogues are a live, read-only list from the seller's warehouse.
 */
@Composable
fun CatalogScreen(
    model: AppModel,
    onOpenEditor: (storeId: String, productId: String?) -> Unit,
    onOpenCategories: (storeId: String) -> Unit,
    onOpenNotifications: () -> Unit,
) {
    val vm = appViewModel { CatalogViewModel(it) }
    val ui by vm.state.collectAsStateWithLifecycle()
    val app by model.state.collectAsStateWithLifecycle()
    val revision by model.catalogRevision.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var storeMenu by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Product?>(null) }
    var isLoadingStores by remember { mutableStateOf(false) }

    val store = app.stores.firstOrNull { it.id == ui.savedStoreId } ?: app.stores.firstOrNull()
    val canEdit = app.canManage && store?.isExternalCatalog == false && !ui.isExternal

    LaunchedEffect(store?.id, revision) { vm.load(store) }
    LaunchedEffect(Unit) {
        if (app.stores.isEmpty()) {
            isLoadingStores = true
            model.refreshStores()
            isLoadingStores = false
        }
    }

    ScreenFrame(
        title = L("Products"),
        actions = {
            if (app.stores.size > 1) {
                Box {
                    BarIconButton(Icons.Outlined.Storefront, L("Store"), { storeMenu = true })
                    DropdownMenu(
                        expanded = storeMenu,
                        onDismissRequest = { storeMenu = false },
                        containerColor = Theme.colors.surface,
                        shadowElevation = 0.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Theme.colors.hairline),
                    ) {
                        for (s in app.stores) {
                            DropdownMenuItem(
                                text = { Text(s.name, style = Theme.type.body, color = Theme.colors.ink) },
                                trailingIcon = { if (s.id == store?.id) Icon(Icons.Filled.Check, null, Modifier.size(18.dp), Theme.colors.ink) },
                                onClick = { vm.setStore(s.id); storeMenu = false },
                            )
                        }
                    }
                }
            }
            if (canEdit && store != null) {
                Box {
                    BarIconButton(Icons.Outlined.Add, L("Add product"), { addMenu = true }, modifier = Modifier.testTag("catalog.add"))
                    DropdownMenu(
                        expanded = addMenu,
                        onDismissRequest = { addMenu = false },
                        containerColor = Theme.colors.surface,
                        shadowElevation = 0.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Theme.colors.hairline),
                    ) {
                        DropdownMenuItem(
                            text = { Text(L("Add product"), style = Theme.type.body, color = Theme.colors.ink) },
                            leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp), Theme.colors.ink) },
                            onClick = { addMenu = false; onOpenEditor(store.id, null) },
                        )
                        DropdownMenuItem(
                            text = { Text(L("Categories"), style = Theme.type.body, color = Theme.colors.ink) },
                            leadingIcon = { Icon(Icons.Outlined.Folder, null, Modifier.size(18.dp), Theme.colors.ink) },
                            onClick = { addMenu = false; onOpenCategories(store.id) },
                        )
                    }
                }
            }
            NotificationBell(app.unreadCount, onOpenNotifications)
        },
    ) {
        ScreenScroll(
            spacing = Space.sm,
            refreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; vm.load(store); refreshing = false } },
        ) {
            if (app.stores.isEmpty()) {
                if (isLoadingStores) LoadingView() else EmptyState(
                    Icons.Outlined.Storefront,
                    L("No store yet"),
                    message = L("Connect Instagram and create your store on the web. Your products show up here after that."),
                    actionTitle = L("Open stores on the web"),
                    onAction = { model.openWeb("/stores") },
                )
            } else if (store != null) {
                StoreHeader(store)
                if (ui.isExternal || store.isExternalCatalog) {
                    Notice(L("Products come live from {source}. Edit them there.", mapOf("source" to store.catalogSource.title)), icon = Icons.Outlined.Inventory2)
                    SearchField(ui.search, vm::setSearch, L("Search products…"), Modifier.testTag("catalog.search"))
                } else {
                    if (ui.categories.isNotEmpty()) {
                        PillTabs(
                            options = listOf(PillOption("", L("All"))) + ui.categories.map { PillOption(it.id, it.name) },
                            selection = ui.categoryFilter,
                            onSelect = vm::setCategoryFilter,
                        )
                    }
                    SearchField(ui.search, vm::setSearch, L("Search products…"), Modifier.testTag("catalog.search"))
                }
                ui.error?.let { ErrorBanner(it.userMessage, onRetry = { scope.launch { vm.load(store) } }) }
                val visible = ui.visible
                if (ui.isLoading && ui.loadedStoreId != store.id) {
                    LoadingView()
                } else if (visible.isEmpty()) {
                    EmptyState(
                        Icons.Outlined.Inventory2,
                        if (ui.search.isEmpty()) L("No products yet.") else L("No products match your search."),
                        message = if (ui.search.isEmpty() && canEdit) L("Add what you sell so the AI can offer it to customers.") else null,
                        actionTitle = if (ui.search.isEmpty() && canEdit) L("Add product") else null,
                        onAction = { onOpenEditor(store.id, null) },
                    )
                } else {
                    val inStock = visible.count { !it.isOutOfStock }
                    Text(
                        L(one = "{count} product", other = "{count} products", count = visible.size) + " · " +
                            L("{count} in stock", mapOf("count" to inStock)),
                        style = Theme.type.caption,
                        color = Theme.colors.inkSubtle,
                        modifier = Modifier.padding(horizontal = Space.xxs),
                    )
                    visible.forEach { product ->
                        ProductRow(
                            product,
                            showsMinPrice = app.canManage,
                            onClick = { if (canEdit) onOpenEditor(store.id, product.id) else viewing = product },
                        )
                    }
                }
            }
        }
    }

    viewing?.let { product ->
        SotuvchiSheet(onDismiss = { viewing = null }) { ProductInfo(product, showsMinPrice = app.canManage) }
    }
}

@Composable
private fun StoreHeader(store: Store) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.xxs),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(store.name, style = Theme.type.eyebrow, color = Theme.colors.inkMuted)
        store.instagramAccount?.username?.let { Text("@$it", style = Theme.type.caption, color = Theme.colors.inkSubtle) }
    }
}

@Composable
fun ProductRow(product: Product, showsMinPrice: Boolean, onClick: () -> Unit) {
    val colors = Theme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cardBackground()
            .clickable(onClick = onClick)
            .padding(Space.sm)
            .testTag("product.${product.name}"),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(product.mainImageUrl, Modifier.size(64.dp).clip(RoundedCornerShape(Radius.md)))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(product.name, style = Theme.type.body.copy(fontWeight = FontWeight.Medium), color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (!product.isActive) Badge(L("Hidden"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Money.format(product.price), style = Theme.type.bodySmall, color = colors.ink)
                val minPrice = product.minPrice
                if (showsMinPrice && minPrice != null) {
                    Text(L("min {price}", mapOf("price" to Money.format(minPrice))), style = Theme.type.caption, color = colors.inkSubtle)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (product.isOutOfStock) {
                    Badge(L("Out of stock"), tone = BadgeTone.Error)
                } else {
                    Text(
                        L("{count} in stock", mapOf("count" to product.totalStock)),
                        style = Theme.type.caption,
                        color = if (product.totalStock <= 3) colors.warningInk else colors.inkSubtle,
                    )
                }
                if (product.variants.isNotEmpty()) {
                    Text("· " + L(one = "{count} variant", other = "{count} variants", count = product.variants.size), style = Theme.type.caption, color = colors.inkSubtle)
                }
                product.category?.name?.let { Text("· $it", style = Theme.type.caption, color = colors.inkSubtle, maxLines = 1) }
            }
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = colors.inkTertiary, modifier = Modifier.size(18.dp))
    }
}

/** Read-only product view for members and external catalogues. */
@Composable
private fun ProductInfo(product: Product, showsMinPrice: Boolean) {
    val colors = Theme.colors
    val images = product.sortedImages
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        if (images.isNotEmpty()) {
            val pager = rememberPagerState { images.size }
            Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(Radius.xl))) {
                HorizontalPager(pager, modifier = Modifier.fillMaxWidth().height(300.dp)) { page ->
                    RemoteImage(images[page].url, Modifier.fillMaxWidth().height(300.dp), contentScale = ContentScale.Fit)
                }
                if (images.size > 1) {
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = Space.xs),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        repeat(images.size) { index ->
                            Box(Modifier.size(7.dp).background(if (index == pager.currentPage) colors.ink else colors.hairline, CircleShape))
                        }
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(product.name, style = Theme.type.headline, color = colors.ink)
            Text(Money.format(product.price), style = Theme.type.subhead, color = colors.ink)
            product.description?.takeIf { it.isNotEmpty() }?.let { Text(it, style = Theme.type.body, color = colors.inkMuted) }
        }
        Card {
            Column {
                product.category?.name?.let { KeyValueRow(L("Category"), it) }
                val minPrice = product.minPrice
                if (showsMinPrice && minPrice != null) KeyValueRow(L("Lowest price"), Money.format(minPrice))
                KeyValueRow(L("In stock"), product.totalStock.toString())
                KeyValueRow(L("Visible to the AI"), if (product.isActive) L("Yes") else L("No"))
            }
        }
        if (product.variants.isNotEmpty()) {
            SectionHeader(L("Variants"))
            Card {
                Column {
                    product.variants.forEach { variant ->
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.xs),
                        ) {
                            parseHexColor(variant.colorHex)?.let { color ->
                                Box(Modifier.size(14.dp).background(color, CircleShape).border(1.dp, colors.hairline, CircleShape))
                            }
                            Text(
                                variant.label ?: listOfNotNull(variant.color, variant.size).joinToString(" / "),
                                style = Theme.type.body,
                                color = colors.ink,
                                modifier = Modifier.weight(1f),
                            )
                            variant.priceOverride?.let { Text(Money.format(it), style = Theme.type.bodySmall, color = colors.inkMuted) }
                            Text(
                                L("{count} in stock", mapOf("count" to variant.stock)),
                                style = Theme.type.caption,
                                color = if (variant.stock > 0) colors.inkSubtle else colors.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Parses "#1a2b3c" or "1a2b3c"; anything else gives null. */
fun parseHexColor(value: String?): Color? {
    val hex = value?.trim()?.removePrefix("#") ?: return null
    if (hex.length != 6) return null
    val parsed = hex.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or parsed)
}

/** "#rrggbb" for a color, as the API stores swatches. */
fun hexString(color: Color): String {
    fun byte(v: Float) = (v.coerceIn(0f, 1f) * 255).toInt()
    return String.format("#%02x%02x%02x", byte(color.red), byte(color.green), byte(color.blue))
}
