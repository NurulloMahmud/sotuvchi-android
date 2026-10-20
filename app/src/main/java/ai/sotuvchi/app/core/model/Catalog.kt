package ai.sotuvchi.app.core.model

import ai.sotuvchi.app.core.network.LooseIntSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class ProductImage(val id: String, val position: Int? = null, val url: String? = null)

@Serializable
data class ProductVariant(
    val id: String,
    val label: String? = null,
    val size: String? = null,
    val color: String? = null,
    val colorHex: String? = null,
    val imageId: String? = null,
    /** MoySklad stock comes from a stock report and can be fractional, so numbers are read leniently. */
    @Serializable(with = LooseIntSerializer::class) val stock: Int = 0,
    @Serializable(with = LooseIntSerializer::class) val priceOverride: Int? = null,
    @Serializable(with = LooseIntSerializer::class) val position: Int? = null,
)

@Serializable
data class CategoryRef(val id: String, val name: String)

@Serializable
data class Product(
    val id: String,
    val storeId: String? = null,
    val name: String,
    val description: String? = null,
    @Serializable(with = LooseIntSerializer::class) val price: Int = 0,
    /** The lowest price the AI may bargain down to. Null for members and for external catalogues. */
    @Serializable(with = LooseIntSerializer::class) val minPrice: Int? = null,
    @Serializable(with = LooseIntSerializer::class) val stock: Int? = null,
    val isActive: Boolean = true,
    val position: Int? = null,
    val categoryId: String? = null,
    val category: CategoryRef? = null,
    val variants: List<ProductVariant> = emptyList(),
    val images: List<ProductImage> = emptyList(),
) {
    val sortedImages: List<ProductImage> get() = images.sortedBy { it.position ?: 0 }

    val mainImageUrl: String? get() = sortedImages.firstOrNull()?.url

    /** Variant stock when variants exist, the product stock otherwise. */
    val totalStock: Int get() = if (variants.isEmpty()) (stock ?: 0) else variants.sumOf { it.stock }

    val isOutOfStock: Boolean get() = totalStock <= 0
}

@Serializable
data class ProductsResponse(
    val data: List<Product> = emptyList(),
    /** True when the list comes live from MoySklad and is read-only. */
    val external: Boolean? = null,
)

@Serializable
data class ProductCategory(
    val id: String,
    val name: String,
    @SerialName("_count") val count: CountProducts? = null,
) {
    val productCount: Int get() = count?.products ?: 0
}

@Serializable
data class CategoryBody(val name: String)

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: Int?) {
    if (value != null) put(key, value) else put(key, JsonNull)
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: String?) {
    if (value != null) put(key, value) else put(key, JsonNull)
}

/**
 * Create and update body. Variants use full-replacement semantics: send the
 * complete list, or null to leave them alone.
 */
data class ProductBody(
    val name: String,
    val price: Int,
    val description: String? = null,
    val minPrice: Int? = null,
    val stock: Int? = null,
    val isActive: Boolean = true,
    val categoryId: String? = null,
    val variants: List<VariantBody>? = null,
) {
    /**
     * `minPrice` and `categoryId` are sent as explicit nulls so clearing them
     * on an edit is not mistaken for "leave unchanged".
     */
    fun toJson(): JsonObject = buildJsonObject {
        put("name", name)
        put("price", price)
        put("description", description ?: "")
        putNullable("minPrice", minPrice)
        if (stock != null) put("stock", stock)
        put("isActive", isActive)
        putNullable("categoryId", categoryId)
        if (variants != null) {
            put("variants", kotlinx.serialization.json.JsonArray(variants.map { it.toJson() }))
        }
    }
}

data class VariantBody(
    val id: String? = null,
    val size: String? = null,
    val color: String? = null,
    val colorHex: String? = null,
    val imageId: String? = null,
    val stock: Int = 0,
    val priceOverride: Int? = null,
) {
    fun toJson(): JsonObject = buildJsonObject {
        if (id != null) put("id", id)
        if (size != null) put("size", size)
        if (color != null) put("color", color)
        if (colorHex != null) put("colorHex", colorHex)
        putNullable("imageId", imageId)
        put("stock", stock)
        putNullable("priceOverride", priceOverride)
    }
}

@Serializable
data class UploadedImage(val id: String)

@Serializable
data class ImageOrderBody(val order: List<String>)
