package ai.sotuvchi.app.features.catalog

import ai.sotuvchi.app.core.l10n.L
import ai.sotuvchi.app.core.model.Product
import ai.sotuvchi.app.core.model.ProductBody
import ai.sotuvchi.app.core.model.ProductImage
import ai.sotuvchi.app.core.model.VariantBody
import java.util.UUID

const val MAX_PRODUCT_IMAGES = 10

data class VariantDraft(
    val key: String = UUID.randomUUID().toString(),
    val serverId: String? = null,
    val size: String = "",
    val color: String = "",
    val colorHex: String? = null,
    val imageId: String? = null,
    val stock: Int = 0,
    val priceOverride: Int? = null,
)

/** A photo picked on the phone, already compressed, waiting for its product to exist. */
class PendingPhoto(val key: String = UUID.randomUUID().toString(), val data: ByteArray)

/** What the editor form holds. */
data class ProductDraft(
    val name: String = "",
    val description: String = "",
    val price: Int? = null,
    val minPrice: Int? = null,
    val stock: Int = 0,
    val isActive: Boolean = true,
    val categoryId: String? = null,
    val variants: List<VariantDraft> = emptyList(),
) {
    companion object {
        fun from(product: Product?): ProductDraft {
            if (product == null) return ProductDraft()
            return ProductDraft(
                name = product.name,
                description = product.description ?: "",
                price = product.price,
                minPrice = product.minPrice,
                stock = product.stock ?: 0,
                isActive = product.isActive,
                categoryId = product.categoryId ?: product.category?.id,
                variants = product.variants.sortedBy { it.position ?: 0 }.map {
                    VariantDraft(
                        serverId = it.id,
                        size = it.size ?: "",
                        color = it.color ?: "",
                        colorHex = it.colorHex,
                        imageId = it.imageId,
                        stock = it.stock,
                        priceOverride = it.priceOverride,
                    )
                },
            )
        }

        /** The body to send, or the message to show when something does not validate. */
        fun validate(draft: ProductDraft, images: List<ProductImage>): Result<ProductBody> {
            val name = draft.name.trim()
            if (name.isEmpty() || name.length > 150) return fail(L("Enter a product name."))
            val price = draft.price
            if (price == null || price < 1 || price > 1_000_000_000) return fail(L("Enter the price as a whole number of UZS."))
            val minPrice = draft.minPrice
            if (minPrice != null && minPrice >= price) return fail(L("The lowest price must be lower than the price."))
            val labels = mutableSetOf<String>()
            for (variant in draft.variants) {
                val size = variant.size.trim()
                val color = variant.color.trim()
                if (size.isEmpty() && color.isEmpty()) return fail(L("Every variant needs a size or a color."))
                if (!labels.add("${color.lowercase()}/${size.lowercase()}")) return fail(L("Variant names must be different from each other."))
            }
            val description = draft.description.trim()
            return Result.success(
                ProductBody(
                    name = name,
                    price = price,
                    description = description.ifEmpty { null }?.take(2000),
                    minPrice = minPrice,
                    stock = if (draft.variants.isEmpty()) draft.stock else null,
                    isActive = draft.isActive,
                    categoryId = draft.categoryId,
                    variants = draft.variants.map { variant ->
                        VariantBody(
                            id = variant.serverId,
                            size = variant.size.trim().ifEmpty { null },
                            color = variant.color.trim().ifEmpty { null },
                            colorHex = variant.colorHex,
                            imageId = variant.imageId?.takeIf { id -> images.any { it.id == id } },
                            stock = variant.stock,
                            priceOverride = variant.priceOverride,
                        )
                    },
                ),
            )
        }

        private fun fail(message: String): Result<ProductBody> = Result.failure(IllegalArgumentException(message))
    }
}
