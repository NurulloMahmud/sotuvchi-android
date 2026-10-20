package ai.sotuvchi.app.app

import ai.sotuvchi.app.features.campaigns.CampaignLogsScreen
import ai.sotuvchi.app.features.campaigns.CampaignsScreen
import ai.sotuvchi.app.features.catalog.CatalogScreen
import ai.sotuvchi.app.features.catalog.CategoriesScreen
import ai.sotuvchi.app.features.catalog.ProductEditorScreen
import ai.sotuvchi.app.features.more.DeleteAccountScreen
import ai.sotuvchi.app.features.more.InstagramAccountsScreen
import ai.sotuvchi.app.features.more.MoreScreen
import ai.sotuvchi.app.features.stores.StoreSettingsScreen
import ai.sotuvchi.app.features.stores.StoresScreen
import ai.sotuvchi.app.features.team.TeamScreen
import ai.sotuvchi.app.features.chats.ChatThreadScreen
import ai.sotuvchi.app.features.chats.ChatsScreen
import ai.sotuvchi.app.features.home.HomeScreen
import ai.sotuvchi.app.features.notifications.NotificationsScreen
import ai.sotuvchi.app.features.orders.OrderDetailScreen
import ai.sotuvchi.app.features.orders.OrdersScreen
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

@Composable
fun AppNavHost(nav: NavHostController, model: AppModel, modifier: Modifier = Modifier) {
    val openNotifications = { nav.navigate(Routes.NOTIFICATIONS) }
    NavHost(navController = nav, startDestination = Routes.HOME, modifier = modifier) {
        composable(Routes.HOME) {
            HomeScreen(model, onOpenNotifications = openNotifications, onOpenCampaigns = { nav.navigate(Routes.CAMPAIGNS) })
        }
        composable(Routes.ORDERS) {
            OrdersScreen(model, onOpenOrder = { nav.navigate(Routes.order(it)) }, onOpenNotifications = openNotifications)
        }
        composable(Routes.CHATS) {
            ChatsScreen(model, onOpenChat = { nav.navigate(Routes.chat(it)) }, onOpenNotifications = openNotifications)
        }
        composable(Routes.CATALOG) {
            CatalogScreen(
                model,
                onOpenEditor = { storeId, productId -> nav.navigate(Routes.productEditor(storeId, productId)) },
                onOpenCategories = { nav.navigate(Routes.categories(it)) },
                onOpenNotifications = openNotifications,
            )
        }
        composable(Routes.MORE) {
            MoreScreen(
                model,
                onOpenStores = { nav.navigate(Routes.STORES) },
                onOpenTeam = { nav.navigate(Routes.TEAM) },
                onOpenCampaigns = { nav.navigate(Routes.CAMPAIGNS) },
                onOpenInstagram = { nav.navigate(Routes.INSTAGRAM_ACCOUNTS) },
                onOpenNotifications = openNotifications,
                onOpenDeleteAccount = { nav.navigate(Routes.DELETE_ACCOUNT) },
            )
        }

        composable(Routes.ORDER, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            OrderDetailScreen(
                model,
                orderId = entry.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack() },
                onOpenChat = { nav.navigate(Routes.chat(it)) },
            )
        }
        composable(Routes.CHAT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            ChatThreadScreen(
                model,
                conversationId = entry.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack() },
                onOpenOrder = { nav.navigate(Routes.order(it)) },
            )
        }
        composable(Routes.NOTIFICATIONS) { NotificationsScreen(model, onBack = { nav.popBackStack() }) }
        composable(Routes.STORES) {
            StoresScreen(model, onBack = { nav.popBackStack() }, onOpenStore = { nav.navigate(Routes.store(it)) })
        }
        composable(Routes.STORE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            StoreSettingsScreen(model, storeId = entry.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
        }
        composable(Routes.TEAM) { TeamScreen(model, onBack = { nav.popBackStack() }) }
        composable(Routes.CAMPAIGNS) {
            CampaignsScreen(model, onBack = { nav.popBackStack() }, onOpenLogs = { nav.navigate(Routes.CAMPAIGN_LOGS) })
        }
        composable(Routes.CAMPAIGN_LOGS) { CampaignLogsScreen(model, onBack = { nav.popBackStack() }) }
        composable(Routes.INSTAGRAM_ACCOUNTS) { InstagramAccountsScreen(model, onBack = { nav.popBackStack() }) }
        composable(Routes.CATEGORIES, arguments = listOf(navArgument("storeId") { type = NavType.StringType })) { entry ->
            CategoriesScreen(model, storeId = entry.arguments?.getString("storeId").orEmpty(), onBack = { nav.popBackStack() })
        }
        composable(Routes.DELETE_ACCOUNT) { DeleteAccountScreen(model, onBack = { nav.popBackStack() }) }
        composable(
            Routes.PRODUCT_EDITOR,
            arguments = listOf(
                navArgument("storeId") { type = NavType.StringType },
                navArgument("productId") { type = NavType.StringType },
            ),
        ) { entry ->
            val productId = entry.arguments?.getString("productId").orEmpty()
            ProductEditorScreen(
                model,
                storeId = entry.arguments?.getString("storeId").orEmpty(),
                productId = productId.takeIf { it != "new" },
                onClose = { nav.popBackStack() },
            )
        }
    }
}
