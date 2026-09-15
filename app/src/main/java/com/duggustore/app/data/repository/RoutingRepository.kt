package com.duggustore.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class RoutePoint(val latitude: Double, val longitude: Double)

data class RouteResult(
    val points: List<RoutePoint>,
    val distanceMetres: Double,
    val durationSeconds: Double
)

/**
 * Turn-by-turn road geometry from OSRM's public demo server — free, no API
 * key or billing account, real road-network routing rather than a straight
 * line between two points. It's a shared, rate-limited demo instance
 * (documented as best-effort by the OSRM project itself), so this is
 * treated as best effort throughout: a failure here just means the map
 * falls back to drawing a straight line between pickup and drop instead of
 * a real route.
 */
class RoutingRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Both run the same OSRM API, so the second is a straight substitute when
     * the first refuses. Being shared demo instances, a single rate-limited
     * response used to be enough to leave the rider looking at a straight line
     * for the whole trip — so each is tried twice before giving up.
     */
    private val endpoints = listOf(
        "https://router.project-osrm.org",
        "https://routing.openstreetmap.de/routed-car"
    )

    suspend fun getDrivingRoute(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double
    ): Result<RouteResult> = withContext(Dispatchers.IO) {
        var lastError: Exception = Exception("Route lookup failed")

        for (baseUrl in endpoints) {
            repeat(ATTEMPTS_PER_ENDPOINT) { attempt ->
                if (attempt > 0) delay(600)
                try {
                    val result = requestRoute(baseUrl, fromLat, fromLng, toLat, toLng)
                    return@withContext Result.success(result)
                } catch (e: Exception) {
                    lastError = e
                }
            }
        }

        Result.failure(lastError)
    }

    private fun requestRoute(
        baseUrl: String,
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double
    ): RouteResult {
        // OSRM takes coordinates as lon,lat (not lat,lon).
        val url = "$baseUrl/route/v1/driving/" +
            "$fromLng,$fromLat;$toLng,$toLat?overview=full&geometries=geojson"
        val request = Request.Builder()
            .url(url)
            .get()
            // These are volunteer-run instances; their usage policy asks that
            // callers identify themselves rather than arrive anonymously.
            .header("User-Agent", "DugguStore/1.0 (Android)")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful || body.isBlank()) {
                throw Exception("Route lookup failed (${response.code})")
            }

            val root = json.parseToJsonElement(body).jsonObject
            // OSRM answers 200 with a code of its own for "routed fine, but
            // there is no road route between these two points".
            val code = root["code"]?.jsonPrimitive?.content
            if (code != null && code != "Ok") {
                throw Exception("No route found ($code)")
            }

            val route = root["routes"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: throw Exception("No route found")
            val coordinates = route["geometry"]?.jsonObject?.get("coordinates")?.jsonArray
                ?: throw Exception("No route geometry")

            val points = coordinates.map { point ->
                val pair = point.jsonArray
                RoutePoint(latitude = pair[1].jsonPrimitive.double, longitude = pair[0].jsonPrimitive.double)
            }
            if (points.size < 2) throw Exception("No route geometry")

            val distance = route["distance"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            val duration = route["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0

            return RouteResult(points, distance, duration)
        }
    }

    private companion object {
        const val ATTEMPTS_PER_ENDPOINT = 2
    }
}
