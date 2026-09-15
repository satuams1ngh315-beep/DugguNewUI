package com.duggustore.app.data.repository

import com.duggustore.app.data.model.Product
import com.duggustore.app.data.remote.SessionManager
import com.duggustore.app.data.remote.SupabaseService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray

private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

class ProductRepository {

    private fun token(): String? = SessionManager.getAccessToken()

    /**
     * Writable columns only. Serializing the whole Product would also send id="" and
     * created_at="", neither of which is a valid value for its column type, so the
     * request came back 400 before it ever reached a policy check.
     */
    private fun body(product: Product): JsonObject = buildJsonObject {
        put("seller_id", product.sellerId)
        put("category_id", product.categoryId)
        put("name", product.name)
        put("description", product.description)
        put("price", product.price)
        product.discountPrice?.let { put("discount_price", it) }
        product.imageUrl?.let { put("image_url", it) }
        put("image_urls", JsonArray(product.imageUrls.map { JsonPrimitive(it) }))
        put("stock", product.stock)
        put("unit", product.unit)
        put("is_active", product.isActive)
        // Sent explicitly, null included — unlike discount_price/image_url
        // above, this one has a real "clear it back to not-applicable"
        // state a seller can pick, and an omitted key would leave a PATCH
        // unable to ever undo a previous true/false.
        put("is_veg", product.isVeg?.let { JsonPrimitive(it) } ?: JsonNull)
    }

    suspend fun getAllProducts(): Result<List<Product>> {
        return try {
            val list = SupabaseService.selectAll("products", token())
            Result.success(list.map { json.decodeFromString(Product.serializer(), it.toString()) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * One page of the active catalogue, server-side filtered and ordered —
     * Home's default browse feed, a category selection, and a search all
     * page through this same call rather than each having its own fetch
     * (or, worse, filtering a fully-loaded list in memory). Categories'
     * scroll-spy view and a direct product-by-id lookup still go through
     * [getAllProducts] — genuinely different jobs, not something this
     * should also try to serve.
     */
    suspend fun getProductsPage(
        page: Int,
        pageSize: Int,
        categoryId: String? = null,
        search: String? = null
    ): Result<List<Product>> {
        return try {
            val eqFilters = mutableMapOf("is_active" to "true")
            categoryId?.let { eqFilters["category_id"] = it }
            val list = SupabaseService.selectPage(
                table = "products",
                token = token(),
                eqFilters = eqFilters,
                orContainsColumns = if (search.isNullOrBlank()) emptyList() else listOf("name", "description"),
                orContainsValue = search,
                orderBy = "created_at.desc",
                limit = pageSize,
                offset = page * pageSize
            )
            Result.success(list.map { json.decodeFromString(Product.serializer(), it.toString()) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getProductsByCategory(categoryId: String): Result<List<Product>> {
        return try {
            val list = SupabaseService.select("products", token(), mapOf("category_id" to categoryId))
            Result.success(list.map { json.decodeFromString(Product.serializer(), it.toString()) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getProductsBySeller(sellerId: String): Result<List<Product>> {
        return try {
            val list = SupabaseService.select("products", token(), mapOf("seller_id" to sellerId))
            Result.success(list.map { json.decodeFromString(Product.serializer(), it.toString()) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getProduct(id: String): Result<Product?> {
        return try {
            val list = SupabaseService.select("products", token(), mapOf("id" to id))
            val product = list.firstOrNull()?.let { json.decodeFromString(Product.serializer(), it.toString()) }
            Result.success(product)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createProduct(product: Product): Result<Unit> {
        return try {
            SupabaseService.insert("products", body(product).toString(), token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProduct(product: Product): Result<Unit> {
        return try {
            SupabaseService.update("products", product.id, body(product).toString(), token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteProduct(id: String): Result<Unit> {
        return try {
            SupabaseService.delete("products", id, token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Uploads a product photo taken from the seller's own device to the
     * `product-images` bucket and returns its public URL, ready to store on
     * the product row — the alternative to asking the seller for an
     * already-hosted image link.
     */
    suspend fun uploadProductImage(sellerId: String, bytes: ByteArray, mimeType: String): Result<String> {
        return try {
            val extension = when (mimeType) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val path = "$sellerId/${java.util.UUID.randomUUID()}.$extension"
            val url = SupabaseService.uploadFile("product-images", path, bytes, mimeType, token())
            Result.success(url)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchProducts(query: String): Result<List<Product>> {
        return try {
            val all = SupabaseService.selectAll("products", token())
            val products = all.map { json.decodeFromString(Product.serializer(), it.toString()) }
            Result.success(products.filter {
                it.name.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true)
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Calls the `decrement_stock` Postgres function to atomically reduce stock
     * for every item in an order. Uses GREATEST(0, stock - qty) on the server
     * side, so a race condition can never drive stock below zero.
     *
     * Called right after [OrderRepository.createOrder] succeeds. Failures are
     * treated as non-fatal: the order is already placed and the seller can
     * correct stock manually, so we don't want to confuse the customer with
     * an error dialog at this point.
     */
    suspend fun decrementStock(items: List<Pair<String, Int>>): Result<Unit> {
        return try {
            val itemsJson = buildJsonArray {
                items.forEach { (productId, quantity) ->
                    add(buildJsonObject {
                        put("product_id", productId)
                        put("quantity", quantity)
                    })
                }
            }
            val body = buildJsonObject { put("items", itemsJson) }.toString()
            SupabaseService.rpc("decrement_stock", body, token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
