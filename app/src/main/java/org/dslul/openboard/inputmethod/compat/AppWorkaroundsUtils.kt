/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.compat

import android.content.pm.PackageInfo

/**
 * A class to encapsulate work-arounds specific to particular apps.
 */
class AppWorkaroundsUtils(private val mPackageInfo: PackageInfo?) {
    override fun toString(): String {
        if (mPackageInfo?.applicationInfo == null) {
            return ""
        }
        val s = StringBuilder()
        s.append("Target application : ")
                .append(mPackageInfo.applicationInfo.name)
                .append("\nPackage : ")
                .append(mPackageInfo.applicationInfo.packageName)
                .append("\nTarget app sdk version : ")
                .append(mPackageInfo.applicationInfo.targetSdkVersion)
        return s.toString()
    }

}