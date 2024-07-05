// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.compat;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.os.Build;

public class ClipboardManagerCompat {

    public static void clearPrimaryClip(ClipboardManager cm) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                cm.clearPrimaryClip();
            } catch (Exception e) {
                // workaround for system-caused crash in https://github.com/Helium314/HeliBoard/issues/203
                cm.setPrimaryClip(ClipData.newPlainText("", ""));
            }
        } else {
            cm.setPrimaryClip(ClipData.newPlainText("", ""));
        }
    }

    public static Long getClipTimestamp(ClipData cd) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return cd.getDescription().getTimestamp();
        } else {
            return null;
        }
    }

    public static Boolean getClipSensitivity(final ClipDescription cd) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return cd != null && cd.getExtras() != null && cd.getExtras().getBoolean("android.content.extra.IS_SENSITIVE");
        }
        return null; // can't determine
    }

}
