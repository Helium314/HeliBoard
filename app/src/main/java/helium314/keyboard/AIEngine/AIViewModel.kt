package helium314.keyboard.AIEngine

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.type.content
import helium314.keyboard.gemini.GeminiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AIViewModel(
    private val geminiClient: GeminiClient
) : ViewModel() {

    private val _state = MutableStateFlow(AIState())
    val state: StateFlow<AIState> = _state.asStateFlow()

    fun onAICorrection(context: Context) {

        val generativeModel = geminiClient.geminiFlashModel

        val inputContent = content {
            text("Please correct the following text for any spelling and grammatical errors, and slightly paraphrase it while keeping the original language and the markdown format:\n")
        }
        viewModelScope.launch {
            try {
                val response = generativeModel.generateContent(inputContent)
                _state.update { it.copy(isAICorrecting = true) }
                Toast.makeText(context, "Text Corrected With AIEngine", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error Correcting Text With AIEngine", Toast.LENGTH_SHORT)
                    .show()
            } finally {
                _state.update { it.copy(isAICorrecting = false) }
            }
        }
    }
}