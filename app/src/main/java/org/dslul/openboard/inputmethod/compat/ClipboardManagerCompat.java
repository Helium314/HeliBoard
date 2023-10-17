// SPDX-License-Identifier: GPL-3.0-only

package org.dslul.openboard.inputmethod.compat;

import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Build;

public class ClipboardManagerCompat {

    @TargetApi(Build.VERSION_CODES.P)
    public static void clearPrimaryClip(ClipboardManager cm) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                cm.clearPrimaryClip();
            } catch (Exception e) {
                // workaround for system-caused crash in https://github.com/Helium314/openboard/issues/203
                cm.setPrimaryClip(ClipData.newPlainText("", ""));
            }
        } else {
            cm.setPrimaryClip(ClipData.newPlainText("", ""));
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    public static Long getClipTimestamp(ClipData cd) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return cd.getDescription().getTimestamp();
        } else {
            return null;
        }
    }

}
