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
    // Exemple : met tout le texte en majuscule
    @JvmStatic
    fun toUpperCase(text: String?): String {
        return text?.uppercase() ?: ""
    }
    // Tu pourras ajouter ici d'autres fonctions de traduction par langue

    // --- Suggestion API Connector functionality ---
    private val client = OkHttpClient()
    private val baseUrl = "http://77.140.59.144:5000/api/Suggestion"

    /**
     * Récupère des suggestions depuis l'API Suggestion
     * @param url L'URL de la page
     * @param selection Le texte sélectionné
     * @param onScreenMemory Mémoire à l'écran
     * @param fullHtml HTML complet
     * @param isSummary true pour un résumé, false pour une auto-réponse
     * @return Flow<String> émettant les suggestions ou erreurs
     */
    @JvmStatic
    fun getSuggestions(
        url: String?,
        selection: String,
        onScreenMemory: String,
        fullHtml: String,
        isSummary: Boolean = false
    ): Flow<String> = flow {
        val jsonObject = JSONObject().apply {
            put("url", url)
            put("selection", selection)
            put("onScreenMemory", onScreenMemory)
            put("fullHtml", fullHtml)
        }
        val json = jsonObject.toString()
        val requestBody = json.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val targetUrl = if (isSummary) { "$baseUrl/summary" } else { "$baseUrl/auto-reply" }
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
                    val suggestion = parseJson(jsonString)
                    emit(suggestion)
                }
            }
        }
    }.flowOn(Dispatchers.IO)
        .catch { e -> emit("Error: ${e.message}") }

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
     * Traduit le contenu dans la langue spécifiée en utilisant l'API de suggestion
     * @param language Langue cible (ex: "fr", "en", "es")
     * @param content Texte à traduire
     * @return Flow<String> émettant la traduction ou une erreur
     */
    @JvmStatic
    fun translateTo(language: String, content: String): Flow<String> {
        val prompt = "Translate this text in $language : $content"
        return getSuggestions(
            url = "SociaKeyboard/translate",
            selection = prompt,
            onScreenMemory = "",
            fullHtml = "",
            isSummary = false
        )
    }
}
