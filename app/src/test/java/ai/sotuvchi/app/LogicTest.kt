package ai.sotuvchi.app

import ai.sotuvchi.app.TestSupport.decode
import ai.sotuvchi.app.core.format.DateText
import ai.sotuvchi.app.core.format.Money
import ai.sotuvchi.app.core.l10n.AppLanguage
import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.l10n.Localizer
import ai.sotuvchi.app.core.model.*
import ai.sotuvchi.app.design.Initials
import ai.sotuvchi.app.features.catalog.ProductDraft
import ai.sotuvchi.app.features.catalog.VariantDraft
import ai.sotuvchi.app.features.catalog.ImageCompressor
import ai.sotuvchi.app.features.home.ChartScale
import ai.sotuvchi.app.features.stores.StoreDraft
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

private const val NBSP = " "

class MoneyTest {
    @Test
    fun formatsWholeSumsPerLanguage() {
        assertEquals("450${NBSP}000${NBSP}so'm", Money.format(450000, AppLanguage.UZ))
        assertEquals("1${NBSP}250${NBSP}000${NBSP}сум", Money.format(1250000, AppLanguage.RU))
        assertEquals("999${NBSP}UZS", Money.format(999, AppLanguage.EN))
    }

    @Test
    fun groupsDigits() {
        assertEquals("0", Money.grouped(0))
        assertEquals("1 000", Money.grouped(1000))
        assertEquals("-25 000", Money.grouped(-25000))
        assertEquals("1 000 000 000", Money.grouped(1_000_000_000))
    }

    @Test
    fun compactAmounts() {
        assertEquals("950", Money.compact(950))
        assertEquals("450K", Money.compact(450000))
        assertEquals("3.2M", Money.compact(3_150_000))
        assertEquals("2B", Money.compact(2_000_000_000))
    }

    @Test
    fun trendChange() {
        assertEquals(50.0, Trend.change(150, 100)!!, 0.0001)
        assertEquals(-50.0, Trend.change(50, 100)!!, 0.0001)
        assertNull(Trend.change(5, 0))
        assertNull(Trend.change(null, 10))
        assertEquals("+12%", Trend.label(12.4))
        assertEquals("-4%", Trend.label(-3.6))
    }

    @Test
    fun parsesTashkentDays() {
        val date = DateText.parseDay("2026-10-01")!!
        assertEquals(1, date.dayOfMonth)
        assertNull(DateText.parseDay("2026-10"))
        // A moment late on 30 September in UTC is already 1 October in Tashkent (UTC+5).
        val late = Instant.parse("2026-09-30T20:30:00Z")
        assertEquals(date, DateText.tashkentDay(late))
    }

    @Test
    fun relativeTimeUsesTheSharedWords() {
        TestSupport.useLanguage(AppLanguage.EN)
        val now = Instant.parse("2026-10-17T10:00:00Z")
        assertEquals("just now", DateText.relative(now.minusSeconds(20), now))
        assertEquals("5 min ago", DateText.relative(now.minus(Duration.ofMinutes(5)), now))
        assertEquals("3 h ago", DateText.relative(now.minus(Duration.ofHours(3)), now))
        assertEquals("2 d ago", DateText.relative(now.minus(Duration.ofDays(2)), now))
        TestSupport.useLanguage(AppLanguage.UZ)
        assertEquals("5 daqiqa oldin", DateText.relative(now.minus(Duration.ofMinutes(5)), now))
        TestSupport.useLanguage(AppLanguage.EN)
    }
}

class ReplyWindowTest {
    private val now = Instant.ofEpochSecond(1_800_000_000)

    @Test
    fun openWithinTwentyFourHours() {
        val last = now.minusSeconds(3600)
        assertTrue(ReplyWindow.isOpen(last, now))
        assertEquals(Duration.ofHours(23), ReplyWindow.remaining(last, now))
    }

    @Test
    fun closedAfterTwentyFourHoursOrWithoutMessages() {
        assertFalse(ReplyWindow.isOpen(now.minus(Duration.ofHours(24)), now))
        assertFalse(ReplyWindow.isOpen(null, now))
        assertNull(ReplyWindow.remaining(now.minus(Duration.ofHours(25)), now))
    }
}

class OrderLogicTest {
    @Before
    fun english() = TestSupport.useLanguage(AppLanguage.EN)

    private fun order(status: OrderStatus, total: Int = 100, receipt: Int? = null): Order {
        val proofs = receipt?.let { """[{ "id": "p", "extractedAmount": $it, "createdAt": "2026-10-17T10:00:00Z", "url": null }]""" } ?: "[]"
        return decode(
            """
            { "id": "o1", "number": 1042, "storeId": "s1", "status": "${status.raw}", "customerName": "Aziza",
              "customerPhone": "+998 90 123 45 67", "total": $total, "createdAt": "2026-10-17T10:00:00Z",
              "items": [{ "id": "i1", "productName": "Sneakers", "variantLabel": "Red / 42", "unitPrice": 50, "listPrice": null, "quantity": 2 }],
              "proofs": $proofs }
            """,
        )
    }

    @Test
    fun decisionRules() {
        assertTrue(OrderStatus.PROOF_SUBMITTED.canDecide)
        assertTrue(OrderStatus.AWAITING_PAYMENT.canDecide)
        assertTrue(OrderStatus.EXPIRED.canDecide)
        assertFalse(OrderStatus.APPROVED.canDecide)
        assertTrue(OrderStatus.AWAITING_PAYMENT.canCancel)
        assertFalse(OrderStatus.PROOF_SUBMITTED.canCancel)
    }

    @Test
    fun receiptMismatch() {
        assertNull(order(OrderStatus.PROOF_SUBMITTED, 100, 100).receiptMismatch)
        assertEquals(-10, order(OrderStatus.PROOF_SUBMITTED, 100, 90).receiptMismatch)
        assertNull(order(OrderStatus.PROOF_SUBMITTED, 100).receiptMismatch)
    }

    @Test
    fun searchByNumberNamePhoneAndProduct() {
        val sample = order(OrderStatus.APPROVED)
        assertTrue(sample.matches("1042"))
        assertTrue(sample.matches("aziz"))
        assertTrue(sample.matches("901234567"))
        assertTrue(sample.matches("sneak"))
        assertFalse(sample.matches("boots"))
        assertEquals("Sneakers (Red / 42) x2", sample.itemsSummary)
    }

    @Test
    fun productBodySendsExplicitNulls() {
        val body = ProductBody(name = "Bag", price = 100, description = null, minPrice = null, stock = 3, isActive = true, categoryId = null, variants = null)
        val json = body.toJson().toString()
        assertTrue(json, json.contains("\"minPrice\":null"))
        assertTrue(json, json.contains("\"categoryId\":null"))
        assertFalse(json.contains("variants"))
    }

    @Test
    fun storePatchOnlySendsChangedFields() {
        var patch = StorePatch()
        assertTrue(patch.isEmpty)
        patch = patch.copy(holdMinutes = 60)
        val json = ai.sotuvchi.app.core.network.ApiJson.json.encodeToString(StorePatch.serializer(), patch)
        assertEquals("""{"holdMinutes":60}""", json)
    }

    @Test
    fun storeDraftFindsChangesAndValidates() {
        val original = StoreDraft(name = "Zebo", cardNumber = "8600 1234 5678 9012")
        assertTrue(StoreDraft.diff(original, original, cardMasked = false).getOrThrow().isEmpty)
        val changed = original.copy(holdMinutes = 60, name = "  Zebo Shoes  ")
        val patch = StoreDraft.diff(changed, original, cardMasked = false).getOrThrow()
        assertEquals("Zebo Shoes", patch.name)
        assertEquals(60, patch.holdMinutes)
        assertNull(patch.description)
        assertTrue(StoreDraft.diff(original.copy(name = " "), original, false).isFailure)
        assertTrue(StoreDraft.diff(original.copy(cardNumber = "8600"), original, false).isFailure)
        // A masked card number (member view) is never sent.
        assertTrue(StoreDraft.diff(original.copy(cardNumber = "8600"), original, true).getOrThrow().isEmpty)
        assertEquals("8600 1234 5678 9012", StoreDraft.groupCard("8600123456789012999"))
        assertEquals(listOf(15, 30, 45, 60, 120, 240, 720, 1440), StoreDraft.holdOptions(45))
    }

    @Test
    fun productDraftValidation() {
        val ok = ProductDraft(name = "Bag", price = 100_000, minPrice = 90_000)
        assertTrue(ProductDraft.validate(ok, emptyList()).isSuccess)
        assertTrue(ProductDraft.validate(ok.copy(name = " "), emptyList()).isFailure)
        assertTrue(ProductDraft.validate(ok.copy(price = null), emptyList()).isFailure)
        assertTrue(ProductDraft.validate(ok.copy(minPrice = 100_000), emptyList()).isFailure)
        val duplicate = ok.copy(variants = listOf(VariantDraft(size = "42", color = "Red"), VariantDraft(size = "42", color = "red")))
        assertTrue(ProductDraft.validate(duplicate, emptyList()).isFailure)
        assertTrue(ProductDraft.validate(ok.copy(variants = listOf(VariantDraft())), emptyList()).isFailure)
        val body = ProductDraft.validate(ok.copy(stock = 5), emptyList()).getOrThrow()
        assertEquals(5, body.stock)
        val withVariant = ProductDraft.validate(
            ok.copy(variants = listOf(VariantDraft(size = "42", imageId = "gone", stock = 2))), emptyList(),
        ).getOrThrow()
        assertNull(withVariant.stock)
        assertNull(withVariant.variants!!.first().imageId)
    }
}

class NotificationTextTest {
    @Before
    fun english() = TestSupport.useLanguage(AppLanguage.EN)

    @Test
    fun paymentProofTitleAndBody() {
        val notification = AppNotification(
            id = "n1", type = NotificationType.PAYMENT_PROOF,
            data = NotificationData(orderId = "o1", number = 1042, customer = "Aziza", total = 450000, receiptAmount = 450000),
            storeName = "Shop", read = false, createdAt = Instant.now(),
        )
        assertEquals("Payment proof for order #1042", notification.title)
        assertEquals("Aziza · 450${NBSP}000${NBSP}UZS. Receipt shows 450${NBSP}000${NBSP}UZS.", notification.body)
        assertEquals(AppDestination.OrderDest("o1"), notification.destination)
    }

    @Test
    fun handoffOpensTheChat() {
        val notification = AppNotification(
            id = "n2", type = NotificationType.HANDOFF,
            data = NotificationData(conversationId = "c1", customer = "Aziza", reason = "wants a human"),
            storeName = null, read = true, createdAt = Instant.now(),
        )
        assertEquals("A customer needs you", notification.title)
        assertEquals("Aziza: wants a human", notification.body)
        assertEquals(AppDestination.ChatDest("c1"), notification.destination)
    }

    @Test
    fun missingDataStillReads() {
        val notification = AppNotification("n3", NotificationType.NEW_ORDER, NotificationData(), null, false, Instant.now())
        assertEquals("New order #?", notification.title)
        assertNull(notification.destination)
    }
}

class ChatTextTest {
    @Before
    fun english() = TestSupport.useLanguage(AppLanguage.EN)

    @Test
    fun placeholdersReadAsLabels() {
        assertEquals("Shared link", ChatText.preview("[link]"))
        assertEquals("Photo", ChatText.preview("[photo]"))
        assertEquals("Voice message", ChatText.preview(" [voice message] "))
        assertEquals("Is it in stock?", ChatText.preview("Is it in stock?"))
        assertEquals("Attachment", ChatText.preview(null))
        assertTrue(ChatText.isPlaceholder("[video]"))
        assertFalse(ChatText.isPlaceholder("[sale]"))
    }

    @Test
    fun unnamedCustomersUseTheirId() {
        assertEquals("Aziza", ChatText.customerTitle("Aziza", "1784140001"))
        assertEquals("Customer …6661", ChatText.customerTitle("  ", "1784146661"))
        assertEquals("Unknown customer", ChatText.customerTitle(null, null))
    }

    @Test
    fun attentionCodesReadAsText() {
        assertEquals("The customer wrote while the AI was paused", ChatText.attentionReason("customer_message"))
        assertEquals("A payment proof is waiting for you", ChatText.attentionReason("payment_proof"))
        assertEquals("The daily limit of AI replies was reached", ChatText.attentionReason("daily_ai_limit"))
        assertEquals("Asked about a refund", ChatText.attentionReason("Asked about a refund"))
        assertEquals("The customer needs you", ChatText.attentionReason(null))
        assertEquals("AI limit reached", ChatText.attentionBadge("daily_ai_limit"))
        assertEquals("Needs you", ChatText.attentionBadge("Asked about a refund"))
    }

    @Test
    fun countsHaveOneAndOtherForms() {
        assertEquals("1 product", L(one = "{count} product", other = "{count} products", count = 1))
        assertEquals("3 products", L(one = "{count} product", other = "{count} products", count = 3))
        assertEquals("0 products", L(one = "{count} product", other = "{count} products", count = 0))
    }
}

class AvatarTest {
    @Test
    fun initialsFromNamesEmailsAndHandles() {
        assertEquals("DK", Initials.of("Dilnoza Karimova"))
        assertEquals("M", Initials.of("makhammatoffb@gmail.com"))
        assertEquals("JD", Initials.of("john.doe@example.com"))
        assertEquals("D", Initials.of("@devbobur"))
        assertNull(Initials.of(null))
        assertNull(Initials.of(""))
    }
}

class ChartAndImageTest {
    @Test
    fun niceAxisScale() {
        assertEquals(1 to 4, ChartScale.niceScale(0))
        assertEquals(100 to 400, ChartScale.niceScale(330))
        assertEquals(250_000 to 1_000_000, ChartScale.niceScale(900_000))
        val (step, top) = ChartScale.niceScale(3_150_000)
        assertTrue(top >= 3_150_000)
        assertEquals(step * 4, top)
    }

    @Test
    fun labelIndicesAreSpreadEvenly() {
        assertEquals(emptyList<Int>(), ChartScale.labelIndices(0))
        assertEquals(listOf(0, 1, 2), ChartScale.labelIndices(3))
        assertEquals(listOf(0, 3, 6, 9), ChartScale.labelIndices(10))
    }

    @Test
    fun photosAreResizedAndCompressed() {
        assertEquals(800 to 600, ImageCompressor.fit(800, 600))
        assertEquals(1600 to 800, ImageCompressor.fit(4000, 2000))
        assertEquals(800 to 1600, ImageCompressor.fit(2000, 4000))
        assertEquals(listOf(82, 70, 58, 46, 34), ImageCompressor.qualitySteps())
    }
}

class LocalizationTest {
    @Test
    fun uzbekAndRussianHaveTheSameKeys() {
        val uz = TestSupport.table(AppLanguage.UZ)
        val ru = TestSupport.table(AppLanguage.RU)
        assertTrue(uz.isNotEmpty())
        assertEquals(uz.keys, ru.keys)
    }

    @Test
    fun translationsKeepEveryPlaceholder() {
        val pattern = Regex("\\{[a-zA-Z]+\\}")
        for (language in listOf(AppLanguage.UZ, AppLanguage.RU)) {
            for ((key, value) in TestSupport.table(language)) {
                val expected = pattern.findAll(key).map { it.value }.toSet()
                val actual = pattern.findAll(value).map { it.value }.toSet()
                assertEquals("${language.code}: $key", expected, actual)
            }
        }
    }

    @Test
    fun noEmDashes() {
        for (language in listOf(AppLanguage.UZ, AppLanguage.RU)) {
            for (value in TestSupport.table(language).values) assertFalse(value, value.contains("—"))
        }
    }

    @Test
    fun everySourceStringHasATranslation() {
        // The same check as `make l10n-check`, run from the unit tests.
        val pattern = Regex("""\bL\(\s*(?:one\s*=\s*)?"((?:[^"\\]|\\.)*)"(?:\s*,\s*other\s*=\s*"((?:[^"\\]|\\.)*)")?""")
        val keys = mutableSetOf<String>()
        java.io.File("src/main/java").walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            pattern.findAll(file.readText()).forEach { match ->
                match.groupValues.drop(1).filter { it.isNotEmpty() }.forEach { keys += it.replace("\\\"", "\"").replace("\\$", "$").replace("\\\\", "\\") }
            }
        }
        assertTrue(keys.size > 300)
        for (language in listOf(AppLanguage.UZ, AppLanguage.RU)) {
            val table = TestSupport.table(language)
            val missing = keys.filter { it !in table }
            assertTrue("${language.code} is missing $missing", missing.isEmpty())
        }
    }

    @Test
    fun fillsPlaceholders() {
        TestSupport.useLanguage(AppLanguage.UZ)
        assertEquals("Buyurtma #7", L("Order #{number}", mapOf("number" to 7)))
        TestSupport.useLanguage(AppLanguage.EN)
        assertEquals("1 order", L(one = "{count} order", other = "{count} orders", count = 1))
        assertEquals("3 orders", L(one = "{count} order", other = "{count} orders", count = 3))
        assertEquals("Order #7", L("Order #{number}", mapOf("number" to 7)))
    }
}
