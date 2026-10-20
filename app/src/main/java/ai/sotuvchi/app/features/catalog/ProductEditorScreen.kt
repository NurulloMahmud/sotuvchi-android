package ai.sotuvchi.app.features.catalog

import ai.sotuvchi.app.app.AppModel
import ai.sotuvchi.app.app.appViewModel
import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.model.Product
import ai.sotuvchi.app.core.model.ProductCategory
import ai.sotuvchi.app.core.model.ProductImage
import ai.sotuvchi.app.core.network.categories
import ai.sotuvchi.app.core.network.createCategory
import ai.sotuvchi.app.core.network.createProduct
import ai.sotuvchi.app.core.network.deleteProduct
import ai.sotuvchi.app.core.network.deleteProductImage
import ai.sotuvchi.app.core.network.isCancellation
import ai.sotuvchi.app.core.network.products
import ai.sotuvchi.app.core.network.reorderProductImages
import ai.sotuvchi.app.core.network.updateProduct
import ai.sotuvchi.app.core.network.uploadProductImage
import ai.sotuvchi.app.core.network.userMessage
import ai.sotuvchi.app.design.ButtonKind
import ai.sotuvchi.app.design.Card
import ai.sotuvchi.app.design.ConfirmDialog
import ai.sotuvchi.app.design.ErrorBanner
import ai.sotuvchi.app.design.InputField
import ai.sotuvchi.app.design.LabeledField
import ai.sotuvchi.app.design.MoneyField
import ai.sotuvchi.app.design.Radius
import ai.sotuvchi.app.design.RemoteImage
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
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Create or edit a product of a manual catalogue: photos, price and the secret
 * lowest price for bargaining, stock, category and variants.
 *
 * Pictures can only be uploaded to an existing product, so on create they are
 * queued and uploaded right after the product is saved. On edit, picture
 * changes apply immediately.
 */
class ProductEditorViewModel(
    private val model: AppModel,
    private val storeId: String,
    private val productId: String?,
) : ViewModel() {
    data class UiState(
        val product: Product? = null,
        val ready: Boolean = false,
        val draft: ProductDraft = ProductDraft(),
        val images: List<ProductImage> = emptyList(),
        val pending: List<PendingPhoto> = emptyList(),
        val categories: List<ProductCategory> = emptyList(),
        val uploadingCount: Int = 0,
        val isSaving: Boolean = false,
        val error: String? = null,
    ) {
        val imageCount: Int get() = images.size + pending.size
    }

    private val api = model.container.api
    private val _state = MutableStateFlow(UiState(ready = productId == null))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val categories = try { api.categories(storeId) } catch (e: Exception) { if (e.isCancellation) throw e; emptyList() }
            _state.update { it.copy(categories = categories) }
            if (productId != null) {
                val product = model.productCache[productId]
                    ?: try { api.products(storeId).data.firstOrNull { it.id == productId } } catch (e: Exception) { if (e.isCancellation) throw e; null }
                _state.update {
                    it.copy(
                        product = product,
                        draft = ProductDraft.from(product),
                        images = product?.sortedImages ?: emptyList(),
                        ready = true,
                    )
                }
            }
        }
    }

    fun update(draft: ProductDraft) = _state.update { it.copy(draft = draft) }
    fun showError(message: String?) = _state.update { it.copy(error = message) }

    fun updateVariant(key: String, change: (VariantDraft) -> VariantDraft) =
        _state.update { s -> s.copy(draft = s.draft.copy(variants = s.draft.variants.map { if (it.key == key) change(it) else it })) }

    fun addVariant() = _state.update { it.copy(draft = it.draft.copy(variants = it.draft.variants + VariantDraft())) }

    fun removeVariant(key: String) = _state.update { it.copy(draft = it.draft.copy(variants = it.draft.variants.filter { v -> v.key != key })) }

    fun removePending(key: String) = _state.update { it.copy(pending = it.pending.filter { p -> p.key != key }) }

    /** Compresses picked photos, then queues them (new product) or uploads them one at a time (editing). */
    fun addPhotos(raws: List<ByteArray?>) {
        viewModelScope.launch {
            _state.update { it.copy(uploadingCount = raws.size) }
            try {
                val prepared = mutableListOf<PendingPhoto>()
                for (raw in raws) {
                    val jpeg = raw?.let { withContext(Dispatchers.Default) { ImageCompressor.jpeg(it) } }
                    if (jpeg == null) showError(L("One of the photos could not be read.")) else prepared += PendingPhoto(data = jpeg)
                }
                val product = _state.value.product
                if (productId == null || product == null) {
                    _state.update { it.copy(pending = it.pending + prepared.take(MAX_PRODUCT_IMAGES - it.imageCount)) }
                    return@launch
                }
                // Editing: upload one at a time, as the API asks.
                for (photo in prepared) {
                    try {
                        api.uploadProductImage(product.id, photo.data)
                    } catch (e: Exception) {
                        if (e.isCancellation) throw e
                        showError(e.userMessage)
                        break
                    }
                }
                refreshImages()
                model.bumpCatalog()
            } finally {
                _state.update { it.copy(uploadingCount = 0) }
            }
        }
    }

    private suspend fun refreshImages() {
        val id = productId ?: return
        val fresh = try { api.products(storeId).data.firstOrNull { it.id == id } } catch (e: Exception) { if (e.isCancellation) throw e; null } ?: return
        model.productCache[fresh.id] = fresh
        _state.update { it.copy(images = fresh.sortedImages, product = fresh) }
    }

    fun makeMain(image: ProductImage) {
        val id = productId ?: return
        viewModelScope.launch {
            val order = listOf(image.id) + _state.value.images.map { it.id }.filter { it != image.id }
            try {
                api.reorderProductImages(id, order)
                _state.update { s -> s.copy(images = order.mapNotNull { oid -> s.images.firstOrNull { it.id == oid } }) }
                model.bumpCatalog()
            } catch (e: Exception) {
                if (e.isCancellation) throw e
                showError(e.userMessage)
            }
        }
    }

    fun deleteImage(image: ProductImage) {
        val id = productId ?: return
        viewModelScope.launch {
            try {
                api.deleteProductImage(id, image.id)
                _state.update { s ->
                    s.copy(
                        images = s.images.filter { it.id != image.id },
                        draft = s.draft.copy(variants = s.draft.variants.map { if (it.imageId == image.id) it.copy(imageId = null) else it }),
                    )
                }
                model.bumpCatalog()
            } catch (e: Exception) {
                if (e.isCancellation) throw e
                showError(e.userMessage)
            }
        }
    }

    fun createCategory(name: String) {
        val trimmed = name.trim().take(60)
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                val category = api.createCategory(storeId, trimmed)
                _state.update { s ->
                    s.copy(
                        categories = if (s.categories.any { it.id == category.id }) s.categories else s.categories + category,
                        draft = s.draft.copy(categoryId = category.id),
                    )
                }
            } catch (e: Exception) {
                if (e.isCancellation) throw e
                showError(e.userMessage)
            }
        }
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        val body = ProductDraft.validate(s.draft, s.images).getOrElse {
            showError(it.message)
            return
        }
        showError(null)
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                val product = s.product
                if (product != null) {
                    api.updateProduct(product.id, body)
                    model.show(Toast(Toast.Kind.Success, L("Product saved.")))
                } else {
                    val created = api.createProduct(storeId, body)
                    var failures = 0
                    for (photo in s.pending) {
                        try { api.uploadProductImage(created.id, photo.data) } catch (e: Exception) { if (e.isCancellation) throw e; failures++ }
                    }
                    if (failures > 0) {
                        model.show(Toast(Toast.Kind.Warning, L("The product was saved, but {count} photos failed to upload. Open it to try again.", mapOf("count" to failures))))
                    } else {
                        model.show(Toast(Toast.Kind.Success, L("Product added.")))
                    }
                }
                model.bumpCatalog()
                onDone()
            } catch (e: Exception) {
                if (e.isCancellation) throw e
                showError(e.userMessage)
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        val product = _state.value.product ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                api.deleteProduct(product.id)
                model.show(Toast(Toast.Kind.Success, L("Product deleted.")))
                model.bumpCatalog()
                onDone()
            } catch (e: Exception) {
                if (e.isCancellation) throw e
                showError(e.userMessage)
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }
}

private val swatches = listOf(
    "#111111", "#ffffff", "#9ca3af", "#ef4444", "#f97316", "#facc15", "#22c55e", "#14b8a6",
    "#3b82f6", "#1e3a8a", "#8b5cf6", "#ec4899", "#92400e", "#d6b88a", "#7f1d1d", "#166534",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductEditorScreen(model: AppModel, storeId: String, productId: String?, onClose: () -> Unit) {
    val vm = appViewModel(key = "product-$storeId-${productId ?: "new"}") { ProductEditorViewModel(it, storeId, productId) }
    val ui by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var showsNewCategory by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    val busy = ui.isSaving || ui.uploadingCount > 0
    val isEditing = productId != null
    val draft = ui.draft

    BackHandler(enabled = busy) {}

    suspend fun readAll(uris: List<Uri>): List<ByteArray?> = withContext(Dispatchers.IO) {
        uris.map { uri -> runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
    }

    val remaining = (MAX_PRODUCT_IMAGES - ui.imageCount).coerceAtLeast(1)
    val multiPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(remaining.coerceAtLeast(2).coerceAtMost(MediaStore.getPickImagesMaxLimit())),
    ) { uris -> if (uris.isNotEmpty()) scope.launch { vm.addPhotos(readAll(uris.take(remaining))) } }
    val singlePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { vm.addPhotos(readAll(listOf(uri))) }
    }

    ScreenFrame(
        title = if (isEditing) L("Edit product") else L("New product"),
        onBack = if (busy) null else onClose,
        actions = {
            if (ui.isSaving) {
                Spinner(Modifier.padding(end = Space.sm))
            } else {
                Text(
                    L("Save"),
                    style = Theme.type.button.copy(fontWeight = FontWeight.SemiBold),
                    color = Theme.colors.ink.copy(alpha = if (busy) 0.4f else 1f),
                    modifier = Modifier.clickable(enabled = !busy) { vm.save(onClose) }.padding(Space.sm).testTag("product.save"),
                )
            }
        },
    ) {
        if (!ui.ready) {
            ai.sotuvchi.app.design.LoadingView()
        } else {
            ScreenScroll {
                ui.error?.let { ErrorBanner(it) }
                PhotosSection(
                    ui, isEditing,
                    onAdd = { if (remaining >= 2) multiPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) else singlePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onRemovePending = vm::removePending,
                    onMakeMain = vm::makeMain,
                    onDeleteImage = vm::deleteImage,
                )

                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                        LabeledField(L("Product name")) {
                            InputField(draft.name, { vm.update(draft.copy(name = it)) }, placeholder = L("For example, Nike Air sneakers"), modifier = Modifier.testTag("product.name"))
                        }
                        LabeledField(L("Description"), hint = L("Material, sizes, care. The AI uses this to answer questions.")) {
                            InputField(draft.description, { vm.update(draft.copy(description = it)) }, placeholder = L("Optional"), multiline = true, minLines = 3)
                        }
                        LabeledField(L("Category")) {
                            val noCategory = L("No category")
                            SelectField(
                                text = ui.categories.firstOrNull { it.id == draft.categoryId }?.name ?: noCategory,
                                options = listOf<Pair<String?, String>>(null to noCategory) +
                                    ui.categories.map { it.id to it.name } + listOf("__new" to L("New category…")),
                                onSelect = { id ->
                                    if (id == "__new") { newCategoryName = ""; showsNewCategory = true } else vm.update(draft.copy(categoryId = id))
                                },
                            )
                        }
                        ToggleRow(L("Visible to the AI"), draft.isActive, { vm.update(draft.copy(isActive = it)) }, subtitle = L("When off, the AI does not offer this product."))
                    }
                }

                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                        LabeledField(L("Price")) {
                            MoneyField(draft.price, { vm.update(draft.copy(price = it)) }, "0", Modifier.testTag("product.price"))
                        }
                        LabeledField(
                            L("Lowest price for bargaining"),
                            hint = L("The AI never goes below this. Customers never see it. Leave empty for a fixed price."),
                        ) {
                            MoneyField(draft.minPrice, { vm.update(draft.copy(minPrice = it)) }, L("Fixed price"), Modifier.testTag("product.minPrice"))
                        }
                    }
                }

                if (draft.variants.isEmpty()) {
                    Card {
                        LabeledField(L("Quantity in stock")) {
                            IntField(draft.stock, { vm.update(draft.copy(stock = it)) }, Modifier.testTag("product.stock"))
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    SectionHeader(L("Sizes or variants"))
                    if (draft.variants.isNotEmpty()) {
                        Text(
                            L("Stock is counted per variant. Leave Own price empty to use the product price."),
                            style = Theme.type.caption, color = Theme.colors.inkSubtle,
                            modifier = Modifier.padding(horizontal = Space.xxs),
                        )
                    }
                    draft.variants.forEach { variant ->
                        VariantCard(
                            variant, ui.images,
                            onChange = { change -> vm.updateVariant(variant.key, change) },
                            onRemove = { vm.removeVariant(variant.key) },
                        )
                    }
                    SButton(L("Add variant"), vm::addVariant, kind = ButtonKind.Secondary, icon = Icons.Outlined.Add, modifier = Modifier.testTag("product.addVariant"))
                }

                if (isEditing) {
                    SButton(
                        L("Delete product"), { confirmDelete = true },
                        kind = ButtonKind.Destructive,
                        enabled = !busy,
                        modifier = Modifier.padding(top = Space.sm).testTag("product.delete"),
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = L("Delete this product? This cannot be undone."),
            message = L("Past orders keep the name and price."),
            confirmText = L("Delete product"),
            destructive = true,
            onConfirm = { confirmDelete = false; vm.delete(onClose) },
            onDismiss = { confirmDelete = false },
        )
    }
    if (showsNewCategory) {
        AlertDialog(
            onDismissRequest = { showsNewCategory = false },
            containerColor = Theme.colors.surface,
            title = { Text(L("New category"), style = Theme.type.cardTitle, color = Theme.colors.ink) },
            text = { InputField(newCategoryName, { newCategoryName = it.take(60) }, placeholder = L("Category name")) },
            confirmButton = {
                Text(L("Create"), style = Theme.type.button, color = Theme.colors.ink,
                    modifier = Modifier.clickable { showsNewCategory = false; vm.createCategory(newCategoryName) }.padding(Space.sm))
            },
            dismissButton = {
                Text(L("Cancel"), style = Theme.type.button, color = Theme.colors.inkMuted,
                    modifier = Modifier.clickable { showsNewCategory = false }.padding(Space.sm))
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotosSection(
    ui: ProductEditorViewModel.UiState,
    isEditing: Boolean,
    onAdd: () -> Unit,
    onRemovePending: (String) -> Unit,
    onMakeMain: (ProductImage) -> Unit,
    onDeleteImage: (ProductImage) -> Unit,
) {
    val colors = Theme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        SectionHeader(L("Photos"))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            ui.images.forEachIndexed { index, image ->
                var menu by remember { mutableStateOf(false) }
                Box {
                    Box(
                        Modifier
                            .size(92.dp)
                            .clip(RoundedCornerShape(Radius.md))
                            .combinedClickable(onClick = { menu = true }, onLongClick = { menu = true }),
                    ) {
                        RemoteImage(image.url, Modifier.size(92.dp))
                        if (index == 0) MainBadge(Modifier.align(Alignment.BottomStart))
                    }
                    DropdownMenu(
                        expanded = menu,
                        onDismissRequest = { menu = false },
                        containerColor = colors.surface,
                        shadowElevation = 0.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.hairline),
                    ) {
                        if (index > 0) {
                            DropdownMenuItem(text = { Text(L("Make main photo"), style = Theme.type.body, color = colors.ink) }, onClick = { menu = false; onMakeMain(image) })
                        }
                        DropdownMenuItem(text = { Text(L("Delete photo"), style = Theme.type.body, color = colors.error) }, onClick = { menu = false; onDeleteImage(image) })
                    }
                }
            }
            ui.pending.forEachIndexed { index, photo ->
                val bitmap = remember(photo.key) { BitmapFactory.decodeByteArray(photo.data, 0, photo.data.size)?.asImageBitmap() }
                Box(Modifier.size(92.dp).clip(RoundedCornerShape(Radius.md))) {
                    if (bitmap != null) Image(bitmap, null, Modifier.size(92.dp), contentScale = ContentScale.Crop)
                    if (ui.images.isEmpty() && index == 0) MainBadge(Modifier.align(Alignment.BottomStart))
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(22.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .clickable { onRemovePending(photo.key) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Close, L("Remove photo"), tint = Color.White, modifier = Modifier.size(13.dp)) }
                }
            }
            if (ui.uploadingCount > 0) {
                Box(Modifier.size(92.dp).background(colors.surfaceAlt, RoundedCornerShape(Radius.md)), contentAlignment = Alignment.Center) { Spinner() }
            }
            if (ui.imageCount < MAX_PRODUCT_IMAGES) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                Column(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(colors.surface)
                        .drawBehind {
                            drawRoundRect(
                                colors.hairline,
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(Radius.md.toPx()),
                                style = Stroke(width = 1.dp.toPx(), pathEffect = dash),
                            )
                        }
                        .clickable(enabled = ui.uploadingCount == 0, onClick = onAdd)
                        .testTag("product.addPhoto"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Outlined.Add, null, tint = colors.ink)
                    Text(L("Add"), style = Theme.type.caption, color = colors.ink)
                }
            }
        }
        Text(
            L("Up to {count} photos. The first one is the main picture. Press and hold a photo for options.", mapOf("count" to MAX_PRODUCT_IMAGES)),
            style = Theme.type.caption, color = colors.inkSubtle,
        )
    }
}

@Composable
private fun MainBadge(modifier: Modifier = Modifier) {
    Text(
        L("Main"),
        style = Theme.type.caption.copy(fontWeight = FontWeight.SemiBold),
        color = Color.White,
        modifier = modifier
            .padding(6.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(Radius.xs))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun VariantCard(
    variant: VariantDraft,
    images: List<ProductImage>,
    onChange: ((VariantDraft) -> VariantDraft) -> Unit,
    onRemove: () -> Unit,
) {
    val colors = Theme.colors
    var showsColors by remember { mutableStateOf(false) }
    var imageMenu by remember { mutableStateOf(false) }
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                LabeledField(L("Size"), modifier = Modifier.weight(1f)) {
                    InputField(variant.size, { v -> onChange { it.copy(size = v) } }, placeholder = "42, XL")
                }
                LabeledField(L("Color"), modifier = Modifier.weight(1f)) {
                    InputField(
                        variant.color, { v -> onChange { it.copy(color = v) } }, placeholder = L("Red"),
                        trailing = {
                            Box(
                                Modifier
                                    .padding(start = Space.xs)
                                    .size(24.dp)
                                    .background(parseHexColor(variant.colorHex) ?: colors.surfaceAlt, CircleShape)
                                    .border(1.dp, colors.hairline, CircleShape)
                                    .clickable { showsColors = true },
                            )
                        },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                LabeledField(L("Stock"), modifier = Modifier.width(110.dp)) {
                    IntField(variant.stock, { v -> onChange { it.copy(stock = v) } })
                }
                LabeledField(L("Own price"), modifier = Modifier.weight(1f)) {
                    MoneyField(variant.priceOverride, { v -> onChange { it.copy(priceOverride = v) } }, L("Same"))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (images.isNotEmpty()) {
                    Box {
                        Row(
                            Modifier.clickable { imageMenu = true }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            val picked = images.firstOrNull { it.id == variant.imageId }
                            if (picked != null) RemoteImage(picked.url, Modifier.size(28.dp).clip(RoundedCornerShape(Radius.xs)))
                            else Icon(Icons.Outlined.Image, null, tint = colors.ink, modifier = Modifier.size(20.dp))
                            Text(L("Picture of this variant"), style = Theme.type.bodySmall, color = colors.ink)
                        }
                        DropdownMenu(
                            expanded = imageMenu,
                            onDismissRequest = { imageMenu = false },
                            containerColor = colors.surface,
                            shadowElevation = 0.dp,
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.hairline),
                        ) {
                            DropdownMenuItem(text = { Text(L("No photo"), style = Theme.type.body, color = colors.ink) }, onClick = { imageMenu = false; onChange { it.copy(imageId = null) } })
                            images.forEachIndexed { index, image ->
                                DropdownMenuItem(
                                    text = { Text(L("Photo {number}", mapOf("number" to index + 1)), style = Theme.type.body, color = colors.ink) },
                                    onClick = { imageMenu = false; onChange { it.copy(imageId = image.id) } },
                                )
                            }
                        }
                    }
                }
                Box(Modifier.weight(1f))
                Row(
                    Modifier.clickable(onClick = onRemove).padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Outlined.DeleteOutline, null, tint = colors.error, modifier = Modifier.size(18.dp))
                    Text(L("Remove variant"), style = Theme.type.bodySmall, color = colors.error)
                }
            }
        }
    }
    if (showsColors) {
        ColorDialog(
            selected = variant.colorHex,
            onPick = { hex -> showsColors = false; onChange { it.copy(colorHex = hex) } },
            onDismiss = { showsColors = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorDialog(selected: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val colors = Theme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(L("Color"), style = Theme.type.cardTitle, color = colors.ink) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                for (hex in swatches) {
                    val isSelected = hex.equals(selected, ignoreCase = true)
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(parseHexColor(hex) ?: Color.Gray, CircleShape)
                            .border(if (isSelected) 3.dp else 1.dp, if (isSelected) colors.accent else colors.hairline, CircleShape)
                            .clickable { onPick(hex) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Text(L("Cancel"), style = Theme.type.button, color = colors.inkMuted, modifier = Modifier.clickable(onClick = onDismiss).padding(Space.sm))
        },
    )
}

/** Whole-number input for stock counts. */
@Composable
fun IntField(value: Int, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    InputField(
        value = value.toString(),
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(7)
            onValueChange(minOf(digits.toIntOrNull() ?: 0, 1_000_000))
        },
        keyboardType = KeyboardType.Number,
        modifier = modifier,
    )
}
