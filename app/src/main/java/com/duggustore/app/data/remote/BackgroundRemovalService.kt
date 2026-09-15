package com.duggustore.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.duggustore.app.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Service for triggering server-side background removal.
 * This calls your backend API which will process the image and update the database.
 */
object BackgroundRemovalService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    /**
     * Triggers background removal for a product image.
     * This is a non-blocking call - the image will be processed asynchronously.
     * 
     * @param imageUrl The URL of the image to process
     * @param productId The ID of the product to update with the cutout image
     * @return Result indicating success or failure
     */
    suspend fun processImage(imageUrl: String, productId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val jsonBody = JSONObject().apply {
                    put("image_url", imageUrl)
                    put("product_id", productId)
                }
                
                val request = Request.Builder()
                    .url("${BuildConfig.BACKGROUND_REMOVAL_API_URL}/process-image")
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Server returned ${response.code}: ${response.message}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Triggers background removal for multiple images in batch.
     * Useful for processing existing products.
     * 
     * @param imageProductPairs List of (imageUrl, productId) pairs
     * @return Result indicating overall success or failure
     */
    suspend fun processBatch(imageProductPairs: List<Pair<String, String>>): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = JSONObject().apply {
                    put("images", org.json.JSONArray().apply {
                        imageProductPairs.forEach { (imageUrl, productId) ->
                            put(JSONObject().apply {
                                put("image_url", imageUrl)
                                put("product_id", productId)
                            })
                        }
                    })
                }
                
                val request = Request.Builder()
                    .url("${BuildConfig.BACKGROUND_REMOVAL_API_URL}/process-batch")
                    .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Server returned ${response.code}: ${response.message}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Checks if background removal is currently enabled.
     * This can be used to disable the feature if the API is down or during testing.
     */
    fun isEnabled(): Boolean {
        val url = BuildConfig.BACKGROUND_REMOVAL_API_URL
        return url.isNotBlank() && !url.contains("your-api.com", ignoreCase = true)
    }
}