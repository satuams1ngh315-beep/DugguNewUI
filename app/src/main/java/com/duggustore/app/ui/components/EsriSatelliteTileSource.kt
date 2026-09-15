package com.duggustore.app.ui.components

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

private const val ESRI_WORLD_IMAGERY_BASE_URL =
    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"

/**
 * Esri's public World Imagery service — real satellite/aerial photography,
 * free and keyless like the OpenStreetMap street tiles it replaces. Esri
 * serves tiles as {z}/{y}/{x}, the reverse of the {z}/{x}/{y} order
 * osmdroid's built-in XYTileSource assumes, so the URL is built by hand
 * here instead of using that default.
 */
object EsriSatelliteTileSource : OnlineTileSourceBase(
    "EsriWorldImagery",
    0, 19, 256, ".jpg",
    arrayOf(ESRI_WORLD_IMAGERY_BASE_URL)
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "$ESRI_WORLD_IMAGERY_BASE_URL$zoom/$y/$x.jpg"
    }
}
