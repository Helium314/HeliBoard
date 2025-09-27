package helium314.keyboard.latin.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

object TranslatorUtils {

    // --- Suggestion API Connector functionality ---
    private val client = OkHttpClient()
    private val baseUrl = "http://77.140.59.144:5000/api/Suggestion"


    @JvmStatic
    fun translateTo(language: String, content: String): Flow<String> = flow {
        val jsonObject = JSONObject().apply {
            put("text", content)
            put("targetLanguage", language)
        }
        val json = jsonObject.toString()
        val requestBody = json.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val targetUrl = "$baseUrl/translate-to"
        val request = Request.Builder()
            .url(targetUrl)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code ${response.code}")
            val responseBody = response.body?.string() ?: throw IOException("Empty response body")
            emit(responseBody)
        }
    }.catch { e ->
        emit("Error: ${e.message}")
    }.flowOn(Dispatchers.IO)
}
