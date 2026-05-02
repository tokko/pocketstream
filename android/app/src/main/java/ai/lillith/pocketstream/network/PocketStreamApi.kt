package ai.lillith.pocketstream.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class EnqueueRequest(val url: String)
data class EnqueueResponse(val id: String, val status: String, val message: String? = null)
data class HealthResponse(val status: String, val version: String? = null)

class PocketStreamApi(
    private var baseUrl: String,
    private var apiKey: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun updateConfig(baseUrl: String, apiKey: String) {
        this.baseUrl = baseUrl.trimEnd('/')
        this.apiKey = apiKey
    }

    suspend fun enqueue(youtubeUrl: String): Result<EnqueueResponse> = runCatching {
        val body = gson.toJson(EnqueueRequest(youtubeUrl))
            .toRequestBody(jsonType)

        val request = Request.Builder()
            .url("$baseUrl/api/enqueue")
            .addHeader("X-API-Key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw Exception("Empty response from server")

        if (!response.isSuccessful) {
            val errorBody = try {
                gson.fromJson(responseBody, Map::class.java)?.get("detail")?.toString() ?: responseBody
            } catch (_: Exception) {
                responseBody
            }
            throw Exception("Server error ${response.code}: $errorBody")
        }

        gson.fromJson(responseBody, EnqueueResponse::class.java)
            ?: throw Exception("Failed to parse response")
    }

    suspend fun healthCheck(): Result<HealthResponse> = runCatching {
        val request = Request.Builder()
            .url("$baseUrl/health")
            .get()
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw Exception("Empty response from server")

        if (!response.isSuccessful) {
            throw Exception("Server returned ${response.code}")
        }

        gson.fromJson(responseBody, HealthResponse::class.java)
            ?: throw Exception("Failed to parse response")
    }
}
