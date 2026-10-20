package ai.sotuvchi.app.core.model

import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.network.LenientEnumSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable(with = CatalogSourceSerializer::class)
enum class CatalogSource(val raw: String) {
    MANUAL("MANUAL"),
    MOYSKLAD("MOYSKLAD"),
    BILLZ("BILLZ"),
    UNKNOWN("UNKNOWN");

    val title: String
        get() = when (this) {
            MANUAL -> L("Managed in Sotuvchi")
            MOYSKLAD -> "MoySklad"
            BILLZ -> "Billz"
            UNKNOWN -> L("Unknown")
        }
}

object CatalogSourceSerializer :
    LenientEnumSerializer<CatalogSource>("CatalogSource", CatalogSource.entries, CatalogSource.UNKNOWN, { it.raw })

@Serializable(with = CardTypeSerializer::class)
enum class CardType(val raw: String) {
    UZCARD("UZCARD"),
    HUMO("HUMO"),
    UNKNOWN("UNKNOWN");

    val title: String
        get() = when (this) {
            UZCARD -> "Uzcard"
            HUMO -> "Humo"
            UNKNOWN -> "-"
        }
}

object CardTypeSerializer :
    LenientEnumSerializer<CardType>("CardType", CardType.entries, CardType.UNKNOWN, { it.raw })

@Serializable
data class StoreIntegration(
    val connected: Boolean = false,
    val warehouseName: String? = null,
    val organizationName: String? = null,
)

@Serializable
data class InstagramRef(val id: String, val username: String? = null)

@Serializable
data class CountProducts(val products: Int? = null)

@Serializable
data class Store(
    val id: String,
    val instagramAccountId: String? = null,
    val name: String,
    val description: String? = null,
    val cardNumber: String? = null,
    val cardHolder: String? = null,
    val cardType: CardType? = null,
    val pickupAddress: String? = null,
    val holdMinutes: Int? = null,
    val aiEnabled: Boolean = false,
    val aiInstructions: String? = null,
    val isActive: Boolean = true,
    val notifyEmail: Boolean? = null,
    val notifyLocale: String? = null,
    val catalogSource: CatalogSource = CatalogSource.MANUAL,
    val telegramConfigured: Boolean? = null,
    val integration: StoreIntegration? = null,
    val instagramAccount: InstagramRef? = null,
    @SerialName("_count") val count: CountProducts? = null,
    val cardMasked: Boolean? = null,
) {
    val productCount: Int? get() = count?.products
    val isExternalCatalog: Boolean get() = catalogSource != CatalogSource.MANUAL

    val displayCardNumber: String?
        get() {
            val number = cardNumber
            if (number.isNullOrEmpty()) return null
            if (cardMasked == true) return number
            return number.chunked(4).joinToString(" ")
        }
}

/** Fields a manager can change. Only changed fields are sent. */
@Serializable
data class StorePatch(
    val name: String? = null,
    val description: String? = null,
    val cardNumber: String? = null,
    val cardHolder: String? = null,
    val cardType: String? = null,
    val pickupAddress: String? = null,
    val holdMinutes: Int? = null,
    val aiEnabled: Boolean? = null,
    val aiInstructions: String? = null,
    val isActive: Boolean? = null,
    val notifyEmail: Boolean? = null,
    val notifyLocale: String? = null,
) {
    val isEmpty: Boolean
        get() = name == null && description == null && cardNumber == null && cardHolder == null && cardType == null &&
            pickupAddress == null && holdMinutes == null && aiEnabled == null && aiInstructions == null &&
            isActive == null && notifyEmail == null && notifyLocale == null
}

@Serializable
data class TelegramConnectBody(val botToken: String)

@Serializable
data class TelegramConnectResult(val botUsername: String? = null)

@Serializable
data class InstagramAccountsPayload(
    val instagramAccounts: List<InstagramAccountSummary> = emptyList(),
    val selectedInstagramAccountId: String? = null,
)
