package helium314.keyboard.latin.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

object TranslatorUtils {

    // --- Suggestion API Connector functionality ---
    private val client = OkHttpClient()
    private val baseUrl = "http://77.140.59.144:5000/api/Suggestion"


    @JvmStatic
    private fun parseJson(jsonString: String): String {
        return try {
            val jsonObject = Json.parseToJsonElement(jsonString).jsonObject
            val textValue = jsonObject["Text"]?.jsonPrimitive?.contentOrNull
            textValue ?: "No text found"
        } catch (e: Exception) {
            "Error parsing JSON: ${e.message}"
        }
    }

    /**
     * Traduit le contenu dans la langue spécifiée en utilisant le nouvel endpoint API /translate-to
     * @param language Langue cible (ex: "fr", "en", "es")
     * @param content Texte à traduire
     * @return Flow<String> émettant la traduction ou une erreur
     */
    @JvmStatic
    fun translateTo(language: String, content: String): Flow<String> = flow {
        val jsonObject = JSONObject().apply {
            put("Text", content)
            put("TargetLanguage", language)
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
            val stream = response.body?.byteStream() ?: throw IOException("Empty response body")
            val reader = BufferedReader(InputStreamReader(stream))
            val jsonBuffer = StringBuilder()
            val buffer = CharArray(1)
            while (reader.read(buffer) != -1) {
                jsonBuffer.append(buffer[0])
                if (jsonBuffer.endsWith("}")) {
                    val jsonString = jsonBuffer.toString()
                    jsonBuffer.clear()
                    val translation = parseJson(jsonString)
                    emit(translation)
                }
            }
        }
    }.flowOn(Dispatchers.IO)
        .catch { e -> emit("Error: ${e.message}") }
}
