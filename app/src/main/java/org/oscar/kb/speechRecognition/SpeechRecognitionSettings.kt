package org.oscar.kb.speechRecognition

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.oscar.kb.R

//import com.application.voiceflashlight.R

open class SpeechRecognitionSettings(private val context: Context) {
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val sharedPreferencesListener = createOnSharedPreferenceChangeListener()
//
//    var commandTurnOnFlashlight = fetchCommandTurnOnFlashlight()
//        private set
//    var commandTurnOffFlashlight = fetchCommandTurnOffFlashlight()
//        private set
    var recognitionLanguage = fetchRecognitionLanguage()
        private set

    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferencesListener)
    }

    private fun createOnSharedPreferenceChangeListener(): SharedPreferences.OnSharedPreferenceChangeListener {
        return SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
//                context.getString(R.string.key_command_turnOn_flashlight) -> {
//                    commandTurnOnFlashlight = fetchCommandTurnOnFlashlight()
//                }
//
//                context.getString(R.string.key_command_turnOff_flashlight) -> {
//                    commandTurnOffFlashlight = fetchCommandTurnOffFlashlight()
//                }

                context.getString(R.string.key_recognition_language) -> {
                    recognitionLanguage = fetchRecognitionLanguage()
                }
            }
        }
    }
//
//    private fun fetchCommandTurnOnFlashlight(): String? {
//        return sharedPreferences.getString(
//            context.getString(R.string.key_command_turnOn_flashlight),
//            ""
//        )?.lowercase()
//    }
//
//    private fun fetchCommandTurnOffFlashlight(): String? {
//        return sharedPreferences.getString(
//            context.getString(R.string.key_command_turnOff_flashlight),
//            ""
//        )?.lowercase()
//    }

    private fun fetchRecognitionLanguage(): String? {
        return sharedPreferences.getString(
            context.getString(R.string.key_recognition_language),
            ""
        )
    }

    fun unregisterOnSharedPreferenceChangeListener() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(sharedPreferencesListener)
    }
}