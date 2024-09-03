package org.oscar.kb.compat

import android.content.res.Configuration
import android.os.Build
import java.util.Locale

fun Configuration.locale(): Locale =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        locales[0]
    } else {
        @Suppress("Deprecation") locale
    }
