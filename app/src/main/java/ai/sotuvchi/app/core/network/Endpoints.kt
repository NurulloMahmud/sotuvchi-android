package ai.sotuvchi.app.core.network

import ai.sotuvchi.app.core.model.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Typed calls for every endpoint the app uses. Paths are relative to the API
 * root (`https://sotuvchi.ai/api`); shapes follow mobile-api.md.
 */

// region Orders

suspend fun ApiClient.orders(filter: OrderFilter, range: RangePreset, storeId: String?): OrdersResponse {
    val query = buildList {
        add("range" to range.raw)
        filter.queryValue?.let { add("status" to it) }
        storeId?.let { add("storeId" to it) }
    }
    return decode(ApiRequest.get("/orders", query), OrdersResponse.serializer())
}

/** There is no `GET /api/orders/{id}`, so the detail is found in the list. */
suspend fun ApiClient.order(id: String, storeId: String? = null): Order? {
    val base = buildList {
        add("range" to RangePreset.ALL.raw)
        storeId?.let { add("storeId" to it) }
    }
    val open = decode(ApiRequest.get("/orders", base + ("status" to "open")), OrdersResponse.serializer())
    open.data.firstOrNull { it.id == id }?.let { return it }
    val all = decode(ApiRequest.get("/orders", base), OrdersResponse.serializer())
    return all.data.firstOrNull { it.id == id }
}

suspend fun ApiClient.approveOrder(id: String): OrderDecisionResult =
    data(ApiRequest.post("/orders/$id/approve"), OrderDecisionResult.serializer())

suspend fun ApiClient.rejectOrder(id: String, reason: String?): OrderDecisionResult =
    data(ApiRequest.post("/orders/$id/reject", RejectBody.serializer(), RejectBody(reason)), OrderDecisionResult.serializer())

suspend fun ApiClient.cancelOrder(id: String) = send(ApiRequest.post("/orders/$id/cancel"))

suspend fun ApiClient.syncOrder(id: String) = send(ApiRequest.post("/orders/$id/sync"))

// endregion

// region Chats

suspend fun ApiClient.conversations(storeId: String? = null): List<Conversation> {
    val query = storeId?.let { listOf("storeId" to it) } ?: emptyList()
    return data(ApiRequest.get("/conversations", query), ListSerializer(Conversation.serializer()))
}

suspend fun ApiClient.conversation(id: String): ConversationDetail =
    data(ApiRequest.get("/conversations/$id"), ConversationDetail.serializer())

suspend fun ApiClient.updateConversation(id: String, patch: ConversationPatch) =
    send(ApiRequest.patch("/conversations/$id", ConversationPatch.serializer(), patch))

suspend fun ApiClient.sendMessage(conversationId: String, text: String) =
    send(ApiRequest.post("/conversations/$conversationId/messages", SendMessageBody.serializer(), SendMessageBody(text)))

// endregion

// region Notifications

suspend fun ApiClient.notifications(): NotificationFeed =
    data(ApiRequest.get("/notifications"), NotificationFeed.serializer())

suspend fun ApiClient.markNotificationsRead(ids: List<String>? = null) =
    send(ApiRequest.post("/notifications/read", MarkReadBody.serializer(), MarkReadBody(ids)))

// endregion

// region Dashboard

suspend fun ApiClient.dashboard(range: RangePreset): DashboardStats =
    data(ApiRequest.get("/dashboard/stats", listOf("range" to range.raw), timeoutSeconds = 45), DashboardStats.serializer())

// endregion

// region Stores

suspend fun ApiClient.stores(): List<Store> = data(ApiRequest.get("/stores"), ListSerializer(Store.serializer()))

suspend fun ApiClient.store(id: String): Store = data(ApiRequest.get("/stores/$id"), Store.serializer())

suspend fun ApiClient.updateStore(id: String, patch: StorePatch): Store =
    data(ApiRequest.patch("/stores/$id", StorePatch.serializer(), patch), Store.serializer())

suspend fun ApiClient.connectTelegram(storeId: String, botToken: String): TelegramConnectResult =
    data(
        ApiRequest.post("/stores/$storeId/telegram", TelegramConnectBody.serializer(), TelegramConnectBody(botToken)),
        TelegramConnectResult.serializer(),
    )

suspend fun ApiClient.disconnectTelegram(storeId: String) = send(ApiRequest.delete("/stores/$storeId/telegram"))

suspend fun ApiClient.testTelegram(storeId: String) = send(ApiRequest.post("/stores/$storeId/telegram/test"))

// endregion

// region Products

suspend fun ApiClient.products(storeId: String, search: String? = null): ProductsResponse {
    val query = if (!search.isNullOrEmpty()) listOf("search" to search) else emptyList()
    return decode(ApiRequest.get("/stores/$storeId/products", query), ProductsResponse.serializer())
}

suspend fun ApiClient.createProduct(storeId: String, body: ProductBody): Product =
    data(ApiRequest.postJson("/stores/$storeId/products", body.toJson().toString()), Product.serializer())

suspend fun ApiClient.updateProduct(id: String, body: ProductBody): Product =
    data(ApiRequest.patchJson("/products/$id", body.toJson().toString()), Product.serializer())

suspend fun ApiClient.deleteProduct(id: String) = send(ApiRequest.delete("/products/$id"))

suspend fun ApiClient.uploadProductImage(productId: String, jpeg: ByteArray): UploadedImage =
    data(Multipart.request("/products/$productId/images", "file", "photo.jpg", "image/jpeg", jpeg), UploadedImage.serializer())

suspend fun ApiClient.reorderProductImages(productId: String, order: List<String>) =
    send(ApiRequest.patch("/products/$productId/images", ImageOrderBody.serializer(), ImageOrderBody(order)))

suspend fun ApiClient.deleteProductImage(productId: String, imageId: String) =
    send(ApiRequest.delete("/products/$productId/images/$imageId"))

// endregion

// region Categories

suspend fun ApiClient.categories(storeId: String): List<ProductCategory> =
    data(ApiRequest.get("/stores/$storeId/categories"), ListSerializer(ProductCategory.serializer()))

suspend fun ApiClient.createCategory(storeId: String, name: String): ProductCategory =
    data(ApiRequest.post("/stores/$storeId/categories", CategoryBody.serializer(), CategoryBody(name)), ProductCategory.serializer())

suspend fun ApiClient.renameCategory(id: String, name: String): ProductCategory =
    data(ApiRequest.patch("/categories/$id", CategoryBody.serializer(), CategoryBody(name)), ProductCategory.serializer())

suspend fun ApiClient.deleteCategory(id: String) = send(ApiRequest.delete("/categories/$id"))

// endregion

// region Instagram

suspend fun ApiClient.instagramAccounts(): InstagramAccountsPayload =
    data(ApiRequest.get("/instagram/accounts"), InstagramAccountsPayload.serializer())

// endregion

// region Team

suspend fun ApiClient.team(): TeamPayload = data(ApiRequest.get("/workspace/members"), TeamPayload.serializer())

suspend fun ApiClient.invite(email: String, role: WorkspaceRole): TeamPayload =
    data(ApiRequest.post("/workspace/members", InviteBody.serializer(), InviteBody(email, role.raw)), TeamPayload.serializer())

suspend fun ApiClient.changeRole(memberId: String, role: WorkspaceRole): TeamPayload =
    data(
        ApiRequest.patch("/workspace/members", RoleChangeBody.serializer(), RoleChangeBody(memberId, role.raw)),
        TeamPayload.serializer(),
    )

suspend fun ApiClient.removeMember(id: String): TeamPayload =
    data(ApiRequest.delete("/workspace/members", RemoveMemberBody.serializer(), RemoveMemberBody(memberId = id)), TeamPayload.serializer())

suspend fun ApiClient.revokeInvitation(id: String): TeamPayload =
    data(ApiRequest.delete("/workspace/members", RemoveMemberBody.serializer(), RemoveMemberBody(invitationId = id)), TeamPayload.serializer())

// endregion

// region Account

/** Permanently deletes the signed-in user and ends all their sessions. It can take a few seconds. */
suspend fun ApiClient.deleteAccount(): AccountDeletionResult =
    data(ApiClient.deleteAccountRequest(), AccountDeletionResult.serializer())

/** A link that opens `path` on the website already signed in. */
suspend fun ApiClient.webHandoff(path: String): WebHandoff =
    data(ApiClient.webHandoffRequest(path), WebHandoff.serializer())

/**
 * The address to open for a page of the website. Signed in, it is a one-time
 * link that signs the browser in, so the user carries on there without
 * logging in again; the session token itself never goes into a URL. Signed
 * out, or when the server cannot make a link, it is the plain page, where the
 * user signs in.
 */
suspend fun ApiClient.signedInWebUrl(path: String): String {
    val plain = webPage(path).toString()
    if (sessionToken == null) return plain
    val handoff = try {
        webHandoff(path)
    } catch (error: kotlinx.coroutines.CancellationException) {
        throw error
    } catch (error: Exception) {
        return plain
    }
    val url = handoff.url.toHttpUrlOrNull() ?: return plain
    return if (isOwnHost(url)) url.toString() else plain
}

/** Short timeout: the user is waiting for the browser to open, and the plain page is the fallback. */
fun ApiClient.Companion.webHandoffRequest(path: String): ApiRequest =
    ApiRequest.post("/mobile/auth/web-handoff", WebHandoffBody.serializer(), WebHandoffBody(path)).copy(timeoutSeconds = 8)

fun ApiClient.Companion.deleteAccountRequest(): ApiRequest =
    ApiRequest.delete("/account", DeleteAccountBody.serializer(), DeleteAccountBody()).copy(timeoutSeconds = 60)

// endregion

// region Campaigns

suspend fun ApiClient.campaigns(): List<Campaign> = data(ApiRequest.get("/automations"), ListSerializer(Campaign.serializer()))

suspend fun ApiClient.setCampaignActive(id: String, isActive: Boolean) =
    send(ApiRequest.patch("/automations", CampaignActiveBody.serializer(), CampaignActiveBody(isActive), listOf("id" to id)))

suspend fun ApiClient.campaignLogs(page: Int, status: DmStatus?): LogsPage {
    val query = buildList {
        add("page" to page.toString())
        add("limit" to "30")
        if (status != null && status != DmStatus.UNKNOWN) add("status" to status.raw)
    }
    return data(ApiRequest.get("/logs", query), LogsPage.serializer())
}

// endregion
