package org.oscar.kb.gemini

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import org.oscar.kb.gemini.GeminiClient.API.key

class GeminiClient() {

    object API {
        const val key = "AIzaSyDyRGBVF3NbS3pEKDfX1TgCawREkmLbkmE"
    }
    val geminiFlashModel by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = key,
            safetySettings = listOf(SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE),
                SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE),
                SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE),
                SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE),
                SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE),
        ),)
    }
}