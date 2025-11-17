// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

@SuppressLint("AppBundleLocaleChanges")
fun <T> runInLocale(context: Context, locale: Locale, run: (Context) -> T): T {
    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    val localeContext = context.createConfigurationContext(config) ?: context
    return run(localeContext)
}

// slower than context and deprecated, but still better than original style
fun <T> runInLocale(resources: Resources, locale: Locale, run: (Resources) -> T): T {
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    @Suppress("deprecation") val res = Resources(resources.assets, resources.displayMetrics, config)
    return run(res)
}
