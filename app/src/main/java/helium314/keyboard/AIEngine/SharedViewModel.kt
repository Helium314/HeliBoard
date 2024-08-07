package helium314.keyboard.AIEngine

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedViewModel : ViewModel() {
    private val _textToSummarize = MutableLiveData<String>()
    val textToSummarize: LiveData<String> = _textToSummarize

    fun setSummarizeText(text: String) {
        _textToSummarize.value = text
    }
}