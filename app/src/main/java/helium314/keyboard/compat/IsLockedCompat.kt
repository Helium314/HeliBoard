package helium314.keyboard.compat

import android.app.KeyguardManager
import android.content.Context
import android.os.Build

fun isDeviceLocked(context: Context): Boolean {
    val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1)
        keyguardManager.isDeviceLocked
    else
        keyguardManager.isKeyguardLocked
}
