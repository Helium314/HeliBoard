// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import java.util.HashSet

class AppsManager(context: Context) {
    private val mPackageManager: PackageManager = context.packageManager

    /**
     * Returns all app labels associated with a launcher icon, sorted arbitrarily.
     */
    fun getNames(): HashSet<String> {
        val filter = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        // activities with an entry/icon for the launcher
        val launcherApps: List<ResolveInfo> = mPackageManager.queryIntentActivities(filter, 0)

        val names = HashSet<String>(launcherApps.size)
        for (info in launcherApps) {
            val name = info.activityInfo.loadLabel(mPackageManager).toString()
            names.add(name)
        }

        return names
    }
}
