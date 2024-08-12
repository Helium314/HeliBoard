package helium314.keyboard.AIEngine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.ai.client.generativeai.GenerativeModel
import helium314.keyboard.gemini.GeminiClient

class SummarizeViewModelFactory(
    //private val geminiClient: GeminiClient,
    private val generativeModel: GenerativeModel
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SummarizeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SummarizeViewModel( generativeModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}