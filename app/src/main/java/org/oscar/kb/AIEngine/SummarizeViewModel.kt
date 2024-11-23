package org.oscar.kb.AIEngine

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.oscar.kb.latin.utils.Log

class SummarizeViewModel(
    private val generativeModel: GenerativeModel,
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


    private val _aiOutputLiveData = MutableLiveData<String>()
    val aiOutputLiveData: LiveData<String> get() = _aiOutputLiveData


    fun setAiOutput(aiOutput: String) {
        _aiOutputLiveData.value = aiOutput
    }

    private val _outputTextStateFlow = MutableStateFlow("")
    val outputTextStateFlow: StateFlow<String> = _outputTextStateFlow

    fun updateOutputText(outputText: String) {
        _outputTextStateFlow.value = outputText
        // Pass the outputText to Java class using an interface or EventBus
    }

    private var onTextUpdatedListener: OnTextUpdatedListener? = null

    fun setOnTextUpdatedListener(listener: OnTextUpdatedListener) {
        onTextUpdatedListener = listener
    }

    private fun processResponse(text: String?): String {
        // Implement your text processing logic here
        // For example, you might remove extra spaces, handle special characters, etc.
        return text!! // A simple example
    }

    fun summarizeStreaming(inputText: String) {
        _uiState.value = SummarizeUiState.Loading

        val prompt =
            "Please correct the following text for any spelling and grammatical errors only in English. \n" +
                    "Do not change the meaning, translate, or paraphrase the text. \n" +
                    "For longer texts, carefully structure the content into clear, coherent paragraphs. \n" +
                    "Each paragraph should focus on a single idea or topic, while preserving the original meaning. \n" +
                    "Ensure proper grammar and spelling throughout without altering the overall structure unnecessarily. \n" +
                    "For shorter texts, only fix grammar and spelling:\n: $inputText"

        viewModelScope.launch {
            try {
                var outputContent = "" // Accumulate response here

                generativeModel.generateContentStream(prompt)
                    .collect { response ->
                        outputContent = response.text.toString()  // Update with complete text
                    }

                // Update UI with complete output
                _uiState.value = SummarizeUiState.Success(outputContent)

                // Call the callback with the processed text
                onTextUpdatedListener?.onTextUpdated(outputContent)
                EventBus.getDefault().post(TextUpdatedEvent(outputContent))

            } catch (e: Exception) {
//                val errorMessage = e.localizedMessage ?: "An unknown error occurred"
//                _uiState.value = SummarizeUiState.Error(e.localizedMessage ?: "")

                val errorMessage = extractErrorMessage(e.message ?: "")
                _uiState.value = SummarizeUiState.Error(errorMessage)
                // Post the error event
                EventBus.getDefault().post(SummarizeErrorEvent(errorMessage))
                Log.d("SummarizeViewModel", "Error: ${e.localizedMessage}")
            }
        }
    }

    private fun extractErrorMessage(errorMessage: String): String {
        val regex = """\"message\": \"(.*?)\"""".toRegex()
        val matchResult = regex.find(errorMessage)
        return matchResult?.groupValues?.get(1) ?: "An unexpected error occurred"
    }

}