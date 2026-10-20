package ai.sotuvchi.app.features.catalog

import ai.sotuvchi.app.app.AppModel
import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.model.ProductCategory
import ai.sotuvchi.app.core.network.ApiError
import ai.sotuvchi.app.core.network.categories
import ai.sotuvchi.app.core.network.createCategory
import ai.sotuvchi.app.core.network.deleteCategory
import ai.sotuvchi.app.core.network.isCancellation
import ai.sotuvchi.app.core.network.renameCategory
import ai.sotuvchi.app.core.network.userMessage
import ai.sotuvchi.app.design.BarIconButton
import ai.sotuvchi.app.design.ConfirmDialog
import ai.sotuvchi.app.design.EmptyState
import ai.sotuvchi.app.design.ErrorBanner
import ai.sotuvchi.app.design.HairlineDivider
import ai.sotuvchi.app.design.InputField
import ai.sotuvchi.app.design.LoadingView
import ai.sotuvchi.app.design.ScreenFrame
import ai.sotuvchi.app.design.ScreenScroll
import ai.sotuvchi.app.design.Space
import ai.sotuvchi.app.design.Theme
import ai.sotuvchi.app.design.Toast
import ai.sotuvchi.app.design.cardBackground
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Product categories of a manual store. */
@Composable
fun CategoriesScreen(model: AppModel, storeId: String, onBack: () -> Unit) {
    val api = model.container.api
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<ProductCategory>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var showsEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProductCategory?>(null) }
    var nameDraft by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<ProductCategory?>(null) }

    suspend fun load() {
        try {
            categories = api.categories(storeId)
            error = null
        } catch (e: Exception) {
            if (e.isCancellation) throw e
            error = e
        }
        loaded = true
    }

    fun startEditing(category: ProductCategory?) {
        editing = category
        nameDraft = category?.name ?: ""
        showsEditor = true
    }

    fun save() {
        val name = nameDraft.trim().take(60)
        if (name.isEmpty()) return
        showsEditor = false
        scope.launch {
            try {
                val target = editing
                if (target != null) api.renameCategory(target.id, name) else api.createCategory(storeId, name)
                load()
                model.bumpCatalog()
            } catch (e: ApiError.Conflict) {
                model.show(Toast(Toast.Kind.Warning, L("A category with this name already exists.")))
            } catch (e: Exception) {
                if (e.isCancellation) throw e
                model.showError(e)
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    ScreenFrame(
        title = L("Categories"),
        onBack = onBack,
        actions = { BarIconButton(Icons.Outlined.Add, L("New category"), { startEditing(null) }, modifier = Modifier.testTag("categories.add")) },
    ) {
        ScreenScroll(
            spacing = Space.sm,
            refreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
        ) {
            error?.let { ErrorBanner(it.userMessage, onRetry = { scope.launch { load() } }) }
            if (!loaded) {
                LoadingView()
            } else if (categories.isEmpty()) {
                EmptyState(
                    Icons.Outlined.FolderOpen,
                    L("No categories yet"),
                    message = L("Group products so customers and the AI find them faster."),
                    actionTitle = L("New category"),
                    onAction = { startEditing(null) },
                )
            } else {
                Column(modifier = Modifier.cardBackground()) {
                    categories.forEachIndexed { index, category ->
                        if (index > 0) HairlineDivider()
                        var menu by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.xxs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(category.name, style = Theme.type.body, color = Theme.colors.ink)
                                Text(
                                    L(one = "{count} product", other = "{count} products", count = category.productCount),
                                    style = Theme.type.caption,
                                    color = Theme.colors.inkSubtle,
                                )
                            }
                            Box {
                                BarIconButton(Icons.Outlined.MoreVert, L("More"), { menu = true })
                                DropdownMenu(
                                    expanded = menu,
                                    onDismissRequest = { menu = false },
                                    containerColor = Theme.colors.surface,
                                    shadowElevation = 0.dp,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Theme.colors.hairline),
                                ) {
                                    DropdownMenuItem(text = { Text(L("Rename"), style = Theme.type.body, color = Theme.colors.ink) }, onClick = { menu = false; startEditing(category) })
                                    DropdownMenuItem(text = { Text(L("Delete"), style = Theme.type.body, color = Theme.colors.error) }, onClick = { menu = false; deleting = category })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showsEditor) {
        AlertDialog(
            onDismissRequest = { showsEditor = false },
            containerColor = Theme.colors.surface,
            title = { Text(if (editing == null) L("New category") else L("Rename category"), style = Theme.type.cardTitle, color = Theme.colors.ink) },
            text = { InputField(nameDraft, { nameDraft = it.take(60) }, placeholder = L("Category name"), modifier = Modifier.testTag("categories.name")) },
            confirmButton = {
                Text(L("Save"), style = Theme.type.button, color = Theme.colors.ink,
                    modifier = Modifier.clickableText { save() }.padding(Space.sm).testTag("categories.save"))
            },
            dismissButton = {
                Text(L("Cancel"), style = Theme.type.button, color = Theme.colors.inkMuted,
                    modifier = Modifier.clickableText { showsEditor = false }.padding(Space.sm))
            },
        )
    }
    deleting?.let { category ->
        ConfirmDialog(
            title = L("Delete this category?"),
            message = L("Its products are kept and become uncategorised."),
            confirmText = L("Delete"),
            destructive = true,
            onConfirm = {
                deleting = null
                scope.launch {
                    try {
                        api.deleteCategory(category.id)
                        load()
                        model.bumpCatalog()
                    } catch (e: Exception) {
                        if (e.isCancellation) throw e
                        model.showError(e)
                    }
                }
            },
            onDismiss = { deleting = null },
        )
    }
}

private fun Modifier.clickableText(onClick: () -> Unit): Modifier = this.then(Modifier.clickable(onClick = onClick))
