package org.oscar.kb.broadcastReceiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.oscar.kb.service.SpeechRecognitionService

class DestroyServiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context?.stopService(Intent(context, SpeechRecognitionService::class.java))
    }
}