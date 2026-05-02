package ai.lillith.pocketstream.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

data class EnqueueRequest(val url: String)
data class EnqueueResponse(val id: String, val status: String, val message: String? = null)
data class HealthResponse(val status: String, val version: String? = null)
data class ApiConfig(val baseUrl: String, val apiKey: String)

class PocketStreamApi(baseUrl: String, apiKey: String) {
    private val config = AtomicReference(ApiConfig(baseUrl.trimEnd('/'), apiKey))

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    val currentConfig: ApiConfig get() = config.get()

    fun updateConfig(baseUrl: String, apiKey: String) {
        config.set(ApiConfig(baseUrl.trimEnd('/'), apiKey))
    }

    suspend fun enqueue(youtubeUrl: String): Result<EnqueueResponse> = runCatching {
        withContext(Dispatchers.IO) {
            val cfg = config.get()
            val body = gson.toJson(EnqueueRequest(youtubeUrl))
                .toRequestBody(jsonType)

            val request = Request.Builder()
                .url("${cfg.baseUrl}/api/enqueue")
                .addHeader("X-API-Key", cfg.apiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
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
        }
    }

    suspend fun healthCheck(): Result<HealthResponse> = runCatching {
        withContext(Dispatchers.IO) {
            val cfg = config.get()
            val request = Request.Builder()
                .url("${cfg.baseUrl}/health")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                    ?: throw Exception("Empty response from server")

                if (!response.isSuccessful) {
                    throw Exception("Server returned ${response.code}")
                }

                gson.fromJson(responseBody, HealthResponse::class.java)
                    ?: throw Exception("Failed to parse response")
            }
        }
    }
}
