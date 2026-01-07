// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import java.text.SimpleDateFormat
import java.util.Calendar

fun getTimestamp(context: Context): String = getTimestampFormatter(context).format(Calendar.getInstance().time)

fun getTimestampFormatter(context: Context): SimpleDateFormat {
    val format = context.prefs().getString(Settings.PREF_TIMESTAMP_FORMAT, Defaults.PREF_TIMESTAMP_FORMAT)
    return runCatching<SimpleDateFormat> { SimpleDateFormat(format, Settings.getValues().mLocale) }.getOrNull()
        ?: SimpleDateFormat(Defaults.PREF_TIMESTAMP_FORMAT, Settings.getValues().mLocale)
}

fun checkTimestampFormat(format: String) = runCatching { SimpleDateFormat(format, Settings.getValues().mLocale) }.isSuccess
