package ai.sotuvchi.app.app

/** Navigation routes. The five tab roots sit on one back stack, with saved state per tab. */
object Routes {
    const val HOME = "home"
    const val ORDERS = "orders"
    const val CHATS = "chats"
    const val CATALOG = "catalog"
    const val MORE = "more"

    const val ORDER = "order/{id}"
    const val CHAT = "chat/{id}"
    const val NOTIFICATIONS = "notifications"
    const val STORES = "stores"
    const val STORE = "store/{id}"
    const val TEAM = "team"
    const val CAMPAIGNS = "campaigns"
    const val CAMPAIGN_LOGS = "campaignLogs"
    const val INSTAGRAM_ACCOUNTS = "instagramAccounts"
    const val CATEGORIES = "categories/{storeId}"
    const val DELETE_ACCOUNT = "deleteAccount"
    const val PRODUCT_EDITOR = "product/{storeId}/{productId}"

    fun order(id: String) = "order/$id"
    fun chat(id: String) = "chat/$id"
    fun store(id: String) = "store/$id"
    fun categories(storeId: String) = "categories/$storeId"

    /** `productId` is "new" for a product that does not exist yet. */
    fun productEditor(storeId: String, productId: String?) = "product/$storeId/${productId ?: "new"}"

    fun tabRoute(tab: AppTab): String = when (tab) {
        AppTab.Home -> HOME
        AppTab.Orders -> ORDERS
        AppTab.Chats -> CHATS
        AppTab.Catalog -> CATALOG
        AppTab.More -> MORE
    }

    val tabRoots = setOf(HOME, ORDERS, CHATS, CATALOG, MORE)
}
