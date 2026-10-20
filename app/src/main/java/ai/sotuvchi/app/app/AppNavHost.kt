package ai.sotuvchi.app.app

import ai.sotuvchi.app.features.Placeholder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

@Composable
fun AppNavHost(nav: NavHostController, model: AppModel, modifier: Modifier = Modifier) {
    NavHost(navController = nav, startDestination = Routes.HOME, modifier = modifier) {
        composable(Routes.HOME) { Placeholder("Home", model) }
        composable(Routes.ORDERS) { Placeholder("Orders", model) }
        composable(Routes.CHATS) { Placeholder("Chats", model) }
        composable(Routes.CATALOG) { Placeholder("Products", model) }
        composable(Routes.MORE) { Placeholder("More", model) }
    }
}
