package org.oscar.kb.audioService

import android.content.Context
import org.oscar.kb.speechRecognition.SpeechRecognitionSettings

class FlashlightControlBasedOnPreference(context: Context) : Flashlight(context) {
    private val speechRecognitionSettings = SpeechRecognitionSettings(context)

    fun controlFlashlight(command: String) {
        when (command.lowercase()) {
            //speechRecognitionSettings.commandTurnOnFlashlight -> turnOnFlashlight()
            //speechRecognitionSettings.commandTurnOffFlashlight -> turnOffFlashlight()
        }
    }
}