package org.oscar.kb.speechRecognition

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.oscar.kb.audioService.FlashlightControlBasedOnPreference
import org.oscar.kb.latin.setup.AppDatabase
import org.oscar.kb.latin.setup.Prompt

//import org.oscar.kb.audioService.FlashlightControlBasedOnPreference
//import org.oscar.kb.speechRecognition.SpeechRecognitionSettings

class SpeechRecognition(context: Context) : SpeechRecognitionSettings(context) {

    private val TAG_SPEECH_RECOGNITION = "SpeechRecognition"
    private val TAG_SPEECH_RECOGNITION_FINAL = "SpeechRecognitionFinal"

//    private val speechRecognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//        // Recognition will occur offline (including Android 12 and above)
//        SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
//    } else {
//        // Recognition will occur via the Internet :(
//        SpeechRecognizer.createSpeechRecognizer(context)
//    }
// Always use internet-based recognition (no offline support)
    private val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

    private val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        // Force internet-based recognition
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
    }

    private val flashlightController = FlashlightControlBasedOnPreference(context)

    private var accumulatedText = ""

    init {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p0: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(p0: Float) {}
            override fun onBufferReceived(p0: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(p0: Int) {
                startSpeechRecognition()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {

                    val newText = matches[INDEX_OF_MOST_LIKELY_PHRASE]
                    accumulatedText += " $newText"  // Append new text

                    flashlightController.controlFlashlight(matches[INDEX_OF_MOST_LIKELY_PHRASE])
                    // matches[INDEX_OF_MOST_LIKELY_PHRASE] -
                    // The most likely phrase spoken by the user, according to the recognition system
                    Log.d(TAG_SPEECH_RECOGNITION, matches[INDEX_OF_MOST_LIKELY_PHRASE])
                    Log.d(TAG_SPEECH_RECOGNITION_FINAL, accumulatedText)

                    // Send a broadcast with the recognized text
                    val intent = Intent("SpeechRecognitionResults")
                    intent.putExtra("recognizedText", accumulatedText)
                    //context.sendBroadcast(intent)
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent)  // Use LocalBroadcastManager
                }
                startSpeechRecognition()
            }

            override fun onPartialResults(p0: Bundle?) {}
            override fun onEvent(p0: Int, p1: Bundle?) {}
        })
    }

    fun startSpeechRecognition() {
        setupLanguageRecognition()
        speechRecognizer.startListening(speechRecognizerIntent)
    }

    private fun setupLanguageRecognition() {
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLanguage)
    }

    fun destroy() {
        unregisterOnSharedPreferenceChangeListener()
        //flashlightController.turnOffFlashlight()
        speechRecognizer.destroy()
    }

    companion object {
        private const val INDEX_OF_MOST_LIKELY_PHRASE = 0
    }

}