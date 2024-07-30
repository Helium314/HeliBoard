package helium314.keyboard.AIEngine

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import helium314.keyboard.gemini.GeminiClient
import helium314.keyboard.latin.utils.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SummarizeViewModel(
    private val generativeModel: GenerativeModel
) : ViewModel() {

    private val _state = MutableStateFlow(AIState())
    val state: StateFlow<AIState> = _state.asStateFlow()


    private val _aiState = MutableLiveData<AIState>()
    val replyData: LiveData<AIState>
        get() = _aiState

    private val _isAICorrecting = MutableLiveData<Boolean>()
    val isAICorrecting: LiveData<Boolean>
        get() = _isAICorrecting

    private val _uiState: MutableStateFlow<SummarizeUiState> =
        MutableStateFlow(SummarizeUiState.Initial)
    val uiState: StateFlow<SummarizeUiState> =
        _uiState.asStateFlow()

//    fun onAICorrection(context: Context) {
//
//        val generativeModel = geminiClient.geminiFlashModel
//
//        val inputContent = content {
//            text("Please correct the following text for any spelling and grammatical errors, and slightly paraphrase it while keeping the original language and the markdown format:\n")
//        }
//        viewModelScope.launch {
//            try {
//                val response = generativeModel.generateContent(inputContent)
//                _state.update { it.copy(isAICorrecting = true) }
//                Toast.makeText(context, "Text Corrected With AIEngine", Toast.LENGTH_SHORT).show()
//            } catch (e: Exception) {
//                Toast.makeText(context, "Error Correcting Text With AIEngine", Toast.LENGTH_SHORT)
//                    .show()
//            } finally {
//                _state.update { it.copy(isAICorrecting = false) }
//            }
//        }
//    }

    fun summarizeStreaming(inputText: String) {
        _uiState.value = SummarizeUiState.Loading

        val prompt =
            "Please correct the following text for any spelling and grammatical errors, and slightly paraphrase it while keeping the original language and the markdown format:\n: $inputText"

        viewModelScope.launch {
            try {
                var outputContent = ""
                generativeModel.generateContentStream(prompt)
                    .collect { response ->
                        outputContent += response.text
                        _uiState.value = SummarizeUiState.Success(outputContent)
                        Log.d("SummarizeViewModel", "outputContent: $outputContent")
                    }
            } catch (e: Exception) {
                _uiState.value = SummarizeUiState.Error(e.localizedMessage ?: "")
                Log.d("SummarizeViewModel", "Error: ${e.localizedMessage}")
            }
        }
    }

//    fun summarizeStreamingLiveData(inputText: String) {
//        //_uiState.value = SummarizeUiState.Loading
//
//        val prompt =
//            "Please correct the following text for any spelling and grammatical errors, and slightly paraphrase it while keeping the original language and the markdown format:\n: $inputText"
//
//        viewModelScope.launch {
//            try {
//                var outputContent = ""
//                val response = generativeModel.generateContentStream(prompt)
//                val response = AIState(prompt)
//                _aiState.postValue(response)
//
//                //_aiState.value = generativeModel.generateContentStream(prompt)
//
////                    .collect { response ->
////                        outputContent += response.text
////                        _uiState.value = SummarizeUiState.Success(outputContent)
////                    }
//            } catch (e: Exception) {
//                _uiState.value = SummarizeUiState.Error(e.localizedMessage ?: "")
//            }
//        }
//    }

}