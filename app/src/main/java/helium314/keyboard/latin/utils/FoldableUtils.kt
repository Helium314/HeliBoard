package helium314.keyboard.latin.utils

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build

object FoldableUtils {
    fun isFoldable(context: Context) = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        && context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)

    // todo: needs to be tested, folded state detection seems to be pretty insane
    //  Apparently we can't read the sensor directly, that would be too simple.
    //  So we register a listener and unregister it on first event, and hope it triggers quickly0
    // could also try using code from https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/WindowManager/Jetpack/src/androidx/window
    // apparently there is some information encoded in undocumented "display_features" setting in Settings.Global that requires a whole library to parse?
    // todo: consider alternative by using smallest width >= 600dp (i.e. is tablet)
    fun isFolded(context: Context): Boolean {
        if (!isFoldable(context)) return false
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        var angle: Float? = null
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
            override fun onSensorChanged(event: SensorEvent) {
                angle = event.values[0]
                sm.unregisterListener(this)
            }
        }
        sm.registerListener(listener, sm.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE), SensorManager.SENSOR_DELAY_FASTEST)
        var wait = 0
        while (angle == null && wait < 50) {
            Thread.sleep(0, 100000)
            wait++
        }
        Log.i("FoldableUtils", "return $angle after $wait")
        return (angle ?: 180f) < 90
    }
}
