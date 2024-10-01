package org.oscar.kb.AIEngine

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.oscar.kb.latin.setup.AppDatabase
import org.oscar.kb.latin.setup.Prompt
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

        //if (inputText.isEmpty() || inputText.length < 50) {
//            val errorMessage = if (inputText.isEmpty()) {
//                "Text is empty and cannot be processed."
//            } else {
//                "Text is too short to process."
//            }

//            Log.d("SummarizeViewModel", errorMessage)
//            _uiState.value = SummarizeUiState.Error(errorMessage)
//            // Post the error event
//            EventBus.getDefault().post(SummarizeErrorEvent(errorMessage))
            //return
        //}
        val prompt =
                    "Please correct the following text for any spelling and grammatical errors only in English. \n" +
                    "Do not change the structure, paraphrase, translate, or alter the original meaning of the text. \n" +
                    "Keep the text strictly in English. \n" +
                    "For longer texts, make sure to carefully correct all grammatical errors and spelling mistakes without modifying the original structure or meaning. \n" +
                    "If the text is too short, just fix grammar or spelling without making any other changes:\n: $inputText"

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

                        // Save to database (insert your DB logic here)
                        // Save to database (insert your DB logic here)
                        // log values
                        Log.d("SummarizeViewModel", "outputContent: $outputContent")

                        _uiState.value = SummarizeUiState.Success(outputContent)
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

    // Database saving function
//    private suspend fun saveToDatabase(content: String) {
//        try {
//            // Insert content into the database
//            yourDatabase.yourDao().insert(ContentEntity(content = content))
//            Log.d("SummarizeViewModel", "Saved content to database: $content")
//        } catch (e: Exception) {
//            Log.d("SummarizeViewModel", "Error saving to database: ${e.localizedMessage}")
//        }
//    }

//    private fun saveAIResponseToDatabase(aiText: String) {
//        val db = AppDatabase.getDatabase(getApplication<Application>().applicationContext)
//        val aiTextEntity = Prompt(aiText, Prompt.PromptType.AI_OUTPUT) // Set the type to AI_OUTPUT
//
//        viewModelScope.launch(Dispatchers.IO) {
//            db.promptDao().insert(aiTextEntity)
//        }
//    }


}