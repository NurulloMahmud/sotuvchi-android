package ai.sotuvchi.app

import ai.sotuvchi.app.TestSupport.decode
import ai.sotuvchi.app.core.model.*
import ai.sotuvchi.app.core.network.Envelope
import ai.sotuvchi.app.core.network.parseIsoInstant
import ai.sotuvchi.app.core.l10n.AppLanguage
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Decodes the sample payloads from mobile-api.md. */
class DecodingTest {
    @Before
    fun english() = TestSupport.useLanguage(AppLanguage.EN)

    @Test
    fun ordersListWithSummaryAndRange() {
        val response = decode<OrdersResponse>(
            """
            {
              "success": true,
              "data": [{
                "id": "o1", "number": 1042, "storeId": "s1", "conversationId": "c1",
                "status": "PROOF_SUBMITTED", "customerName": "Aziza", "customerPhone": "+998901234567",
                "deliveryType": "DELIVERY", "address": "Tashkent", "total": 450000,
                "holdExpiresAt": "2026-10-17T10:30:00.000Z", "decidedAt": null, "rejectReason": null,
                "externalOrderId": null, "externalDemandId": null, "externalSyncedAt": null, "externalSyncError": null,
                "createdAt": "2026-10-17T10:00:00.000Z", "updatedAt": "2026-10-17T10:05:00.000Z",
                "items": [{ "id": "i1", "productId": "p1", "variantId": "v1", "productName": "Sneakers",
                            "variantLabel": "Red / 42", "unitPrice": 225000, "listPrice": 250000, "quantity": 2 }],
                "proofs": [{ "id": "pr1", "orderId": "o1", "contentType": "image/jpeg", "extractedAmount": 440000,
                             "extractedNote": "Uzcard", "createdAt": "2026-10-17T10:04:00Z", "url": "https://s3/receipt" }],
                "store": { "id": "s1", "name": "My Shop" },
                "conversation": { "id": "c1", "customerName": "Aziza" }
              }],
              "summary": { "orders": 12, "approvedCount": 7, "approvedSum": 3150000, "openCount": 3, "rejectedCount": 2 },
              "range": { "preset": "all", "from": null, "to": null }
            }
            """,
        )
        val order = response.data.first()
        assertEquals(1042, order.number)
        assertEquals(OrderStatus.PROOF_SUBMITTED, order.status)
        assertEquals(DeliveryType.DELIVERY, order.deliveryType)
        assertTrue(order.items.first().isDiscounted)
        assertEquals(450000, order.items.first().lineTotal)
        assertEquals(-10000, order.receiptMismatch)
        assertEquals("https://s3/receipt", order.latestProof?.url)
        assertEquals(3150000, response.summary?.approvedSum)
        assertEquals("all", response.range?.preset)
    }

    @Test
    fun unknownEnumValuesDoNotFailDecoding() {
        val item = decode<LastMessage>("""{ "role": "BOT_V2", "text": "hi", "createdAt": "2026-10-17T09:50:00Z" }""")
        assertEquals(ChatRole.UNKNOWN, item.role)
        val statuses = decode<List<OrderStatus>>("""["APPROVED", "ON_HOLD"]""")
        assertEquals(listOf(OrderStatus.APPROVED, OrderStatus.UNKNOWN), statuses)
    }

    @Test
    fun conversationList() {
        val envelope = decode<Envelope<List<Conversation>>>(
            """
            { "success": true, "data": [{
              "id": "c1", "storeId": "s1", "customerId": "17841", "customerName": "Aziza",
              "aiPaused": false, "needsAttention": true, "attentionReason": "Customer asked for a human",
              "lastCustomerMessageAt": "2026-10-17T09:50:00.000Z", "createdAt": "2026-10-16T09:50:00.000Z",
              "updatedAt": "2026-10-17T09:51:00.000Z", "store": { "id": "s1", "name": "My Shop" },
              "lastMessage": { "role": "CUSTOMER", "text": "Salom", "createdAt": "2026-10-17T09:50:00.000Z" }
            }, {
              "id": "c2", "storeId": "s1", "customerId": "1", "customerName": null, "aiPaused": true,
              "needsAttention": false, "attentionReason": null, "lastCustomerMessageAt": null,
              "createdAt": "2026-10-16T09:50:00.000Z", "updatedAt": "2026-10-16T09:50:00.000Z",
              "store": { "id": "s1", "name": "My Shop" }, "lastMessage": null
            }] }
            """,
        )
        val conversations = envelope.data!!
        assertEquals(2, conversations.size)
        assertTrue(conversations[0].needsAttention)
        assertNull(conversations[1].lastMessage)
    }

    @Test
    fun conversationDetailWithAttachmentsAndOrders() {
        val detail = decode<Envelope<ConversationDetail>>(
            """
            { "success": true, "data": {
              "id": "c1", "storeId": "s1", "customerId": "1", "customerName": "Aziza",
              "aiPaused": true, "needsAttention": false, "attentionReason": null,
              "lastCustomerMessageAt": "2026-10-17T09:50:00Z", "createdAt": "2026-10-17T09:00:00Z", "updatedAt": "2026-10-17T09:50:00Z",
              "store": { "id": "s1", "name": "My Shop" },
              "messages": [
                { "id": "m1", "conversationId": "c1", "role": "CUSTOMER", "text": "", "igMessageId": "x", "createdAt": "2026-10-17T09:00:00Z",
                  "attachments": [{ "id": "a1", "messageId": "m1", "position": 0, "kind": "IMAGE", "contentType": "image/jpeg",
                                    "sizeBytes": 123456, "createdAt": "2026-10-17T09:00:00Z", "url": "https://s3/a1" }] },
                { "id": "m2", "conversationId": "c1", "role": "AI", "text": "Narxi 250 000", "igMessageId": null, "createdAt": "2026-10-17T09:01:00Z" }
              ],
              "orders": [{ "id": "o1", "number": 1042, "status": "APPROVED", "total": 450000 }]
            } }
            """,
        ).data!!
        assertEquals(2, detail.messages.size)
        assertEquals(AttachmentKind.IMAGE, detail.messages[0].attachments.first().kind)
        assertTrue(detail.messages[1].attachments.isEmpty())
        assertEquals(OrderStatus.APPROVED, detail.orders.first().status)
    }

    @Test
    fun notificationFeed() {
        val feed = decode<Envelope<NotificationFeed>>(
            """
            { "success": true, "data": {
              "unreadCount": 4,
              "badges": { "ordersToReview": 2, "chatsNeedingYou": 1 },
              "items": [
                { "id": "n1", "type": "PAYMENT_PROOF", "data": { "orderId": "o1", "number": 1042, "customer": "Aziza", "total": 450000, "receiptAmount": 450000 },
                  "storeName": "My Shop", "read": false, "createdAt": "2026-10-17T10:00:00Z" },
                { "id": "n2", "type": "HANDOFF", "data": { "conversationId": "c1", "customer": "Aziza", "reason": "wants a human", "number": "12" },
                  "storeName": null, "read": true, "createdAt": "2026-10-17T09:00:00Z" },
                { "id": "n3", "type": "SOMETHING_NEW", "data": null, "storeName": null, "read": false, "createdAt": "2026-10-17T08:00:00Z" }
              ]
            } }
            """,
        ).data!!
        assertEquals(4, feed.unreadCount)
        assertEquals(2, feed.badges.ordersToReview)
        assertEquals(AppDestination.OrderDest("o1"), feed.items[0].destination)
        assertEquals(AppDestination.ChatDest("c1"), feed.items[1].destination)
        assertEquals(12, feed.items[1].data.number)
        assertEquals(NotificationType.UNKNOWN, feed.items[2].type)
        assertNull(feed.items[2].destination)
    }

    @Test
    fun dashboardWithAndWithoutSales() {
        val stats = decode<Envelope<DashboardStats>>(
            """
            { "success": true, "data": {
              "userName": "Bahrom", "contactsCount": 320,
              "workspace": { "name": "Shop", "dmsSentThisPeriod": 41, "planExpiresAt": "2026-11-01T00:00:00Z" },
              "billingEnabled": true, "instagramAccounts": [{ "id": "ig1", "username": "myshop", "instagramId": "1", "name": "My Shop", "provider": "META", "tokenExpiresAt": null, "webhookSubscribed": true }],
              "totalAutomations": 5, "activeAutomations": 3,
              "range": { "preset": "month", "from": "2026-10-01", "to": "2026-10-17" },
              "period": { "dmsSent": 120, "dmsSkipped": 4, "dmsFailed": 1, "clicks": 33, "ctr": 27.5 },
              "previous": { "dmsSent": 90, "dmsSkipped": 2, "dmsFailed": 0, "clicks": 20, "ctr": 22.2 },
              "sales": {
                "byStatus": [{ "status": "APPROVED", "count": 7, "sum": 3150000 }],
                "orders": 12, "approvedCount": 7, "approvedSum": 3150000, "openCount": 3,
                "previous": { "orders": 10, "approvedCount": 5, "approvedSum": 2000000, "openCount": 1 },
                "daily": [{ "date": "2026-10-01", "orders": 2, "approved": 1, "revenue": 450000 }],
                "topProducts": [{ "name": "Sneakers", "quantity": 9, "revenue": 2025000 }]
              }
            } }
            """,
        ).data!!
        assertEquals(450000, stats.sales?.averageOrder)
        assertNotNull(stats.sales?.daily?.first()?.day)
        assertEquals(27.5, stats.period?.ctr)

        val empty = decode<Envelope<DashboardStats>>("""{ "success": true, "data": { "userName": null, "sales": null } }""")
        assertNull(empty.data?.sales)
    }

    @Test
    fun storeForMemberIsMasked() {
        val store = decode<Envelope<List<Store>>>(
            """
            { "success": true, "data": [{
              "id": "s1", "workspaceId": "w", "instagramAccountId": "ig1", "name": "My Shop", "description": null,
              "cardNumber": "8600 **** **** 1234", "cardHolder": "A. Karimov", "cardType": "UZCARD", "pickupAddress": null,
              "holdMinutes": 30, "aiEnabled": true, "aiInstructions": null, "isActive": true, "notifyEmail": true,
              "notifyLocale": "uz", "catalogSource": "MOYSKLAD", "telegramChatId": null, "createdAt": "2026-10-01T00:00:00Z",
              "updatedAt": "2026-10-01T00:00:00Z", "telegramConfigured": false,
              "integration": { "connected": true, "warehouseName": "Main", "organizationName": "LLC" },
              "instagramAccount": { "id": "ig1", "username": "myshop" }, "_count": { "products": 14 }, "cardMasked": true
            }] }
            """,
        ).data!!.first()
        assertTrue(store.isExternalCatalog)
        assertEquals(14, store.productCount)
        assertEquals("8600 **** **** 1234", store.displayCardNumber)
    }

    @Test
    fun manualAndExternalProducts() {
        val manual = decode<ProductsResponse>(
            """
            { "success": true, "data": [{
              "id": "p1", "storeId": "s1", "name": "Sneakers", "description": "Leather", "price": 250000, "minPrice": 200000,
              "stock": 0, "isActive": true, "position": 0, "categoryId": "cat1", "createdAt": "2026-10-01T00:00:00Z",
              "updatedAt": "2026-10-01T00:00:00Z", "category": { "id": "cat1", "name": "Shoes" },
              "variants": [{ "id": "v1", "productId": "p1", "label": "Red / 42", "size": "42", "color": "Red", "colorHex": "#ff0000",
                             "imageId": "img2", "stock": 5, "priceOverride": null, "position": 0 }],
              "images": [{ "id": "img2", "position": 1, "url": "https://s3/2" }, { "id": "img1", "position": 0, "url": "https://s3/1" }]
            }] }
            """,
        )
        val product = manual.data.first()
        assertNull(manual.external)
        assertEquals("https://s3/1", product.mainImageUrl)
        assertEquals(5, product.totalStock)
        assertFalse(product.isOutOfStock)

        val external = decode<ProductsResponse>(
            """
            { "success": true, "external": true, "data": [{
              "id": "ms-1", "name": "Bag", "price": 99000, "minPrice": null, "stock": 3, "isActive": true,
              "categoryId": null, "category": { "id": "Bags", "name": "Bags" }, "variants": [],
              "images": [{ "id": "x", "position": 0, "url": "/api/stores/s1/external-image?src=abc" }]
            }] }
            """,
        )
        assertEquals(true, external.external)

        val fractional = decode<ProductsResponse>(
            """
            { "success": true, "external": true, "data": [{
              "id": "ms-2", "name": "Rice", "description": null, "price": 18000, "minPrice": null, "stock": 0, "isActive": true,
              "categoryId": null, "category": null, "images": [],
              "variants": [{ "id": "v", "label": "5 kg", "size": "5 kg", "color": null, "colorHex": null, "imageId": null,
                             "stock": 2.5, "priceOverride": null }]
            }] }
            """,
        )
        assertEquals(3, fractional.data.first().variants.first().stock)
        assertTrue(external.data.first().mainImageUrl!!.startsWith("/api/"))
    }

    @Test
    fun teamPayload() {
        val team = decode<Envelope<TeamPayload>>(
            """
            { "success": true, "data": {
              "currentUserRole": "ADMIN",
              "members": [{ "id": "m1", "role": "OWNER", "createdAt": "2026-10-01T00:00:00Z", "user": { "id": "u1", "email": "a@b.uz", "name": "Bahrom" } }],
              "invitations": [{ "id": "i1", "email": "c@d.uz", "role": "MEMBER", "token": "t", "expiresAt": "2026-10-24T00:00:00Z",
                                "createdAt": "2026-10-17T00:00:00Z", "inviteUrl": "https://sotuvchi.ai/invite/t" }]
            } }
            """,
        ).data!!
        assertEquals(WorkspaceRole.ADMIN, team.currentUserRole)
        assertTrue(team.currentUserRole.canManage)
        assertFalse(WorkspaceRole.MEMBER.canManage)
        assertEquals("https://sotuvchi.ai/invite/t", team.invitations.first().inviteUrl)
    }

    @Test
    fun campaignsAndLogs() {
        val campaign = decode<Envelope<List<Campaign>>>(
            """
            { "success": true, "data": [{
              "id": "a1", "workspaceId": "w", "instagramAccountId": "ig1", "name": "Price DM", "goal": null,
              "postId": null, "postUrl": null, "pendingNextReel": false, "matchAnyPost": true, "keywords": ["narx", "price"],
              "matchAnyWord": false, "wholeWordMatch": true, "dmTriggerEnabled": false, "dmMessage": "Hi {username}",
              "isActive": true, "createdAt": "2026-10-01T00:00:00Z", "updatedAt": "2026-10-01T00:00:00Z",
              "instagramAccount": { "username": "myshop", "instagramId": "1" }, "_count": { "dmLogs": 3 },
              "trackedLinks": [], "reportUrl": null,
              "analytics": { "sent": 10, "skipped": 1, "failed": 0, "clicks": 4, "topKeywords": [{ "keyword": "narx", "count": 8 }], "ctr": 40 }
            }] }
            """,
        ).data!!.first()
        assertEquals("narx, price", campaign.triggerDescription)
        assertEquals(40.0, campaign.analytics?.ctr)

        val logs = decode<Envelope<LogsPage>>(
            """
            { "success": true, "data": {
              "logs": [{ "id": "l1", "workspaceId": "w", "automationId": "a1", "instagramAccountId": "ig1", "commenterId": "c",
                         "commenterName": "aziza", "commentText": "narx?", "commentId": "x", "matchedKeyword": "narx",
                         "status": "SKIPPED_DEDUP", "attempts": 1, "dmSentAt": null, "errorMessage": null,
                         "createdAt": "2026-10-17T00:00:00Z", "updatedAt": "2026-10-17T00:00:00Z",
                         "automation": { "name": "Price DM", "keywords": ["narx"] }, "instagramAccount": { "username": "myshop" } }],
              "pagination": { "page": 1, "limit": 20, "total": 134, "totalPages": 7 }
            } }
            """,
        ).data!!
        assertEquals(DmStatus.SKIPPED_DEDUP, logs.logs.first().status)
        assertEquals(7, logs.pagination.totalPages)
    }

    @Test
    fun decisionResultWithWarnings() {
        val result = decode<Envelope<OrderDecisionResult>>(
            """{ "success": true, "data": { "notified": false, "notifyError": "window closed", "syncError": null } }""",
        ).data!!
        assertEquals(false, result.notified)
        assertEquals("window closed", result.notifyError)
    }

    @Test
    fun datesWithAndWithoutFractionalSeconds() {
        assertNotNull(parseIsoInstant("2026-10-17T10:00:00.000Z"))
        assertNotNull(parseIsoInstant("2026-10-17T10:00:00Z"))
        assertNotNull(parseIsoInstant("2026-10-17T15:00:00+05:00"))
        assertNull(parseIsoInstant("17/10/2026"))
    }
}
