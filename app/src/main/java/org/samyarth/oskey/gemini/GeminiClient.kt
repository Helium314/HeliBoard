package org.samyarth.oskey.gemini

import com.google.ai.client.generativeai.GenerativeModel
import org.samyarth.oskey.gemini.GeminiClient.API.key

class GeminiClient() {

    object API {
        const val key = "AIzaSyDyRGBVF3NbS3pEKDfX1TgCawREkmLbkmE"
    }
    val geminiFlashModel by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = key,
        )
    }
}