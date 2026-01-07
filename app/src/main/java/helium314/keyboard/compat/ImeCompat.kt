// SPDX-License-Identifier: GPL-3.0-only
@file:Suppress("DEPRECATION")

package helium314.keyboard.compat

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodSubtype
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.settings.Settings

object ImeCompat {
    fun InputMethodService.switchInputMethod(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return switchToNextInputMethod(false)
        val window = window.window ?: return false
        val token = window.attributes.token
        return RichInputMethodManager.getInstance().inputMethodManager.switchToNextInputMethod(token, false)
    }

    fun InputMethodService.shouldSwitchToOtherInputMethods(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return shouldOfferSwitchingToNextInputMethod()
        val settingsValues = Settings.getValues()
        val window = window.window ?: return settingsValues.mLanguageSwitchKeyToOtherImes
        val token = window.attributes.token ?: return settingsValues.mLanguageSwitchKeyToOtherImes
        return RichInputMethodManager.getInstance().inputMethodManager.shouldOfferSwitchingToNextInputMethod(token)
    }

    fun InputMethodService.switchInputMethodAndSubtype(imi: InputMethodInfo, subtype: InputMethodSubtype) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchInputMethod(imi.id, subtype)
        } else {
            val window = window.window ?: return
            val token = window.attributes.token
            RichInputMethodManager.getInstance().inputMethodManager.setInputMethodAndSubtype(token, imi.id, subtype)
        }
    }
}
