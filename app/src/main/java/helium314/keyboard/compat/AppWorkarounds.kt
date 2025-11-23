// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.compat

import android.text.InputType
import helium314.keyboard.latin.utils.InputTypeUtils

object AppWorkarounds {
    fun adjustInputType(inputType: Int, packageName: String?): Int {
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return inputType
        if (InputTypeUtils.isAnyPasswordInputType(inputType)) return inputType
        return when (packageName) {
            // Firefox and forks (assuming all of them) don't set these flags, so we simply force them for all text fields in the app
            // missing TYPE_TEXT_VARIATION_WEB_EDIT_TEXT is strange, considering (almost) all fields except URL bar should set it
            // missing TYPE_TEXT_FLAG_NO_SUGGESTIONS is horrible, because JavaScript stuff does not interact properly with composing region
            "org.mozilla.fennec_fdroid", "org.mozilla.fenix", "org.mozilla.firefox_beta", "org.mozilla.focus",
            "org.mozilla.klar", "org.mozilla.firefox", "org.ironfoxoss.ironfox", "net.waterfox.android.release",
            "io.github.forkmaintainers.iceraven", "com.zen.web.tools.browser" -> inputType or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT
            else -> inputType
        }
    }
}
