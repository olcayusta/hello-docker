package io.github.olcayusta.locationalarm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class GeoResult(val latitude: Double, val longitude: Double, val displayName: String)

object NominatimClient {
    private const val USER_AGENT = "LocationAlarm/1.0 (github.com/olcayusta/hello-docker)"

    suspend fun search(query: String): GeoResult? = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://nominatim.openstreetmap.org/search?format=json&limit=1&q=$encoded")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val results = JSONArray(body)
            if (results.length() == 0) return@withContext null

            val first = results.getJSONObject(0)
            GeoResult(
                latitude = first.getString("lat").toDouble(),
                longitude = first.getString("lon").toDouble(),
                displayName = first.optString("display_name", query)
            )
        } finally {
            connection.disconnect()
        }
    }
}
