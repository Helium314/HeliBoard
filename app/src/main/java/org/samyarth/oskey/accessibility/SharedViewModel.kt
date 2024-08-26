package org.samyarth.oskey.accessibility

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.samyarth.oskey.latin.utils.Log

class SharedViewModel : ViewModel() {
//    private val _textToSummarize = MutableLiveData<String>()
//    val textToSummarize: LiveData<String> = _textToSummarize
//
//    fun setSummarizeText(text: String) {
//        _textToSummarize.value = text
//    }

    private val _outputText = MutableLiveData<String>()
    val outputText: LiveData<String> get() = _outputText

    fun updateOutputText(outputText: String) {
        _outputText.value = outputText
        Log.d("SharedViewModel", "updateOutputText: $outputText")
    }
}