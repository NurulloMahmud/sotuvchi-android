package ai.sotuvchi.app.app

import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.model.AppDestination
import ai.sotuvchi.app.design.Theme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private data class TabSpec(val tab: AppTab, val icon: ImageVector, val title: () -> String)

private val tabs = listOf(
    TabSpec(AppTab.Home, Icons.Outlined.Home) { L("Home") },
    TabSpec(AppTab.Orders, Icons.Outlined.ShoppingBag) { L("Orders") },
    TabSpec(AppTab.Chats, Icons.Outlined.ChatBubbleOutline) { L("Chats") },
    TabSpec(AppTab.Catalog, Icons.Outlined.Inventory2) { L("Products") },
    TabSpec(AppTab.More, Icons.Outlined.MoreHoriz) { L("More") },
)

/** Routes that use the whole screen: no tab bar under them. */
private fun hidesTabBar(route: String?): Boolean =
    route == Routes.CHAT || route == Routes.PRODUCT_EDITOR

@Composable
fun MainScaffold(model: AppModel) {
    val nav = rememberNavController()
    val state by model.state.collectAsStateWithLifecycle()
    val backEntry by nav.currentBackStackEntryAsState()
    val route = backEntry?.destination?.route

    LaunchedEffect(Unit) {
        model.navCommands.collect { command ->
            when (command) {
                is NavCommand.SelectTab -> nav.selectTab(command.tab)
                is NavCommand.Open -> when (val destination = command.destination) {
                    is AppDestination.OrderDest -> nav.openOn(AppTab.Orders, Routes.order(destination.id))
                    is AppDestination.ChatDest -> nav.openOn(AppTab.Chats, Routes.chat(destination.id))
                }
            }
        }
    }

    Scaffold(
        containerColor = Theme.colors.canvas,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (!hidesTabBar(route)) {
                Column {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.colors.hairline))
                    NavigationBar(containerColor = Theme.colors.canvas, tonalElevation = 0.dp) {
                        for (spec in tabs) {
                            val rootRoute = Routes.tabRoute(spec.tab)
                            val badgeCount = when (spec.tab) {
                                AppTab.Orders -> state.badges.ordersToReview
                                AppTab.Chats -> state.badges.chatsNeedingYou
                                else -> 0
                            }
                            NavigationBarItem(
                                selected = backStackTab(nav, route) == rootRoute,
                                onClick = { nav.selectTab(spec.tab) },
                                icon = {
                                    BadgedBox(badge = {
                                        if (badgeCount > 0) {
                                            Badge(containerColor = Theme.colors.primary, contentColor = Theme.colors.onPrimary) { Text(badgeCount.toString()) }
                                        }
                                    }) { Icon(spec.icon, contentDescription = null) }
                                },
                                label = { Text(spec.title(), style = Theme.type.caption) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Theme.colors.ink,
                                    selectedTextColor = Theme.colors.ink,
                                    unselectedIconColor = Theme.colors.inkSubtle,
                                    unselectedTextColor = Theme.colors.inkSubtle,
                                    indicatorColor = Theme.colors.surfaceAlt,
                                ),
                                modifier = Modifier.testTag("tab.${spec.tab.name.lowercase()}"),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AppNavHost(nav, model)
        }
    }
}

/** Which tab root the current back stack belongs to. */
private fun backStackTab(nav: NavController, route: String?): String? {
    if (route in Routes.tabRoots) return route
    val chain = nav.currentBackStack.value.mapNotNull { it.destination.route }
    return chain.lastOrNull { it in Routes.tabRoots }
}

/** Switches tab, keeping each tab's own stack of screens. */
fun NavController.selectTab(tab: AppTab) {
    navigate(Routes.tabRoute(tab)) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Lands on a tab with one detail screen above its root, replacing what the tab showed before. */
fun NavController.openOn(tab: AppTab, detailRoute: String) {
    navigate(Routes.tabRoute(tab)) {
        popUpTo(graph.findStartDestination().id) { saveState = false }
        launchSingleTop = true
    }
    navigate(detailRoute)
}
