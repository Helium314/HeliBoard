package org.oscar.kb.audioService

import android.content.Context
import android.hardware.camera2.CameraManager

open class Flashlight(context: Context) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val cameraId = cameraManager.cameraIdList[0]

//    fun turnOnFlashlight() {
//        cameraManager.setTorchMode(cameraId, true)
//    }
//
//    fun turnOffFlashlight() {
//        cameraManager.setTorchMode(cameraId, false)
//    }
}