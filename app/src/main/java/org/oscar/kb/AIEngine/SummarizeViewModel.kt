package org.oscar.kb.AIEngine

import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import org.oscar.kb.latin.utils.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus

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

    // ... other ViewModel logic

    fun updateOutputText(outputText: String) {
        _outputTextStateFlow.value = outputText
        // Pass the outputText to Java class using an interface or EventBus
    }

    private var onTextUpdatedListener: OnTextUpdatedListener? = null

    fun setOnTextUpdatedListener(listener: OnTextUpdatedListener) {
        onTextUpdatedListener = listener
    }

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

    private fun processResponse(text: String?): String {
        // Implement your text processing logic here
        // For example, you might remove extra spaces, handle special characters, etc.
        return text!! // A simple example
    }
    fun summarizeStreaming(inputText: String) {
        _uiState.value = SummarizeUiState.Loading

        if (inputText.isEmpty() || inputText.length < 50) {
            val errorMessage = if (inputText.isEmpty()) {
                "Text is empty and cannot be processed."
            } else {
                "Text is too short to process."
            }

            Log.d("SummarizeViewModel", errorMessage)
            _uiState.value = SummarizeUiState.Error(errorMessage)
            // Post the error event
            EventBus.getDefault().post(SummarizeErrorEvent(errorMessage))
            return
        }
        val prompt =
            "Please correct the following text for any spelling and grammatical errors, and slightly paraphrase it while keeping the original language:\n: $inputText"

        viewModelScope.launch {
            try {
                var outputContent = ""
//                var outputContent = StringBuilder() // use a StringBuilder for better performance
                generativeModel.generateContentStream(prompt)
                    .collect { response ->
//                        outputContent.append(response.text.toString()) // Accumulate text here

                        outputContent = response.text.toString()

                        // Call the callback with the processed text
                        onTextUpdatedListener?.onTextUpdated(outputContent)

                        // Post the EventBus event
                        EventBus.getDefault().post(TextUpdatedEvent(outputContent))
                        //log sent event
                        Log.d("SummarizeViewModel", "TextUpdatedEventBus: $outputContent")

                        // log values
                        Log.d("SummarizeViewModel", "outputContent: $outputContent")

                        _uiState.value = SummarizeUiState.Success(outputContent.toString())
                        Log.d("SummarizeViewModel", "outputContent: $outputContent")
                    }
            } catch (e: Exception) {
                val errorMessage = e.localizedMessage ?: "An unknown error occurred"
                _uiState.value = SummarizeUiState.Error(e.localizedMessage ?: "")
                Log.d("SummarizeViewModel", "Error: ${e.localizedMessage}")
                // Post the error event
                EventBus.getDefault().post(SummarizeErrorEvent(errorMessage))
            }
        }
    }

}