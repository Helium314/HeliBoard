/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.latin

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.AsyncTask
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import helium314.keyboard.compat.locale
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.LocaleUtils.getBestMatch
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.LanguageOnSpacebarUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ScriptUtils.script
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getSecondaryLocales
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.mainLayoutNameOrQwerty
import helium314.keyboard.latin.utils.prefs
import java.util.Locale

/** Enrichment class for InputMethodManager to simplify interaction and add functionality. */
class RichInputMethodManager private constructor() {
    private lateinit var context: Context
    private lateinit var imm: InputMethodManager
    private lateinit var inputMethodInfoCache: InputMethodInfoCache
    private lateinit var currentRichInputMethodSubtype: RichInputMethodSubtype
    private var shortcutInputMethodInfo: InputMethodInfo? = null
    private var shortcutSubtype: InputMethodSubtype? = null

    private val isInitializedInternal get() = this::imm.isInitialized

    val currentSubtypeLocale get() = forcedSubtypeForTesting?.locale ?: currentSubtype.locale

    val currentSubtype get() = forcedSubtypeForTesting ?: currentRichInputMethodSubtype

    val combiningRulesExtraValueOfCurrentSubtype get() =
        SubtypeLocaleUtils.getCombiningRulesExtraValue(currentSubtype.rawSubtype)

    val inputMethodInfoOfThisIme get() = inputMethodInfoCache.inputMethodOfThisIme

    val inputMethodManager: InputMethodManager get() {
        checkInitialized()
        return imm
    }

    val isShortcutImeReady get() = shortcutInputMethodInfo != null

    fun hasShortcutIme() = isShortcutImeReady // todo

    fun checkIfSubtypeBelongsToThisImeAndEnabled(subtype: InputMethodSubtype?) =
        getEnabledInputMethodSubtypeList(inputMethodInfoOfThisIme, true).contains(subtype)

    // todo: same as SubtypeSettings.getEnabledSubtypes(allowsImplicitlySelectedSubtypes), right?
    fun getMyEnabledInputMethodSubtypeList(allowsImplicitlySelectedSubtypes: Boolean) =
        getEnabledInputMethodSubtypeList(inputMethodInfoOfThisIme, allowsImplicitlySelectedSubtypes)

    fun getEnabledInputMethodSubtypeList(imi: InputMethodInfo, allowsImplicitlySelectedSubtypes: Boolean) =
        inputMethodInfoCache.getEnabledInputMethodSubtypeList(imi, allowsImplicitlySelectedSubtypes)

    // could also check SubtypeSettings.getEnabledSubtypes(allowsImplicitlySelectedSubtypes)
    fun checkIfSubtypeBelongsToThisImeAndImplicitlyEnabled(subtype: InputMethodSubtype): Boolean {
        val subtypeEnabled = checkIfSubtypeBelongsToThisImeAndEnabled(subtype)
        val subtypeExplicitlyEnabled = getMyEnabledInputMethodSubtypeList(false)
            .contains(subtype)
        return subtypeEnabled && !subtypeExplicitlyEnabled
    }

    fun hasMultipleEnabledIMEsOrSubtypes(shouldIncludeAuxiliarySubtypes: Boolean) =
        hasMultipleEnabledSubtypes(shouldIncludeAuxiliarySubtypes, imm.enabledInputMethodList)

    fun hasMultipleEnabledSubtypesInThisIme(shouldIncludeAuxiliarySubtypes: Boolean) =
        hasMultipleEnabledSubtypes(shouldIncludeAuxiliarySubtypes, listOf(inputMethodInfoOfThisIme))

    fun getNextSubtypeInThisIme(onlyCurrentIme: Boolean): InputMethodSubtype? {
        val currentSubtype = currentSubtype.rawSubtype
        val enabledSubtypes = getMyEnabledInputMethodSubtypeList(true)
        val currentIndex = enabledSubtypes.indexOf(currentSubtype)
        if (currentIndex == -1) {
            Log.w(TAG, "Can't find current subtype in enabled subtypes: subtype=" +
                    SubtypeLocaleUtils.getSubtypeNameForLogging(currentSubtype))
            return if (onlyCurrentIme) enabledSubtypes[0] // just return first enabled subtype
            else null
        }
        val nextIndex = (currentIndex + 1) % enabledSubtypes.size
        if (nextIndex <= currentIndex && !onlyCurrentIme) {
            // The current subtype is the last or only enabled one and it needs to switch to next IME.
            return null
        }
        return enabledSubtypes[nextIndex]
    }

    // todo: this is about main layout, not layout set
    fun findSubtypeByLocaleAndKeyboardLayoutSet(locale: Locale, keyboardLayoutSetName: String): InputMethodSubtype? {
        val myImi = inputMethodInfoOfThisIme
        val count = myImi.subtypeCount
        for (i in 0..<count) {
            val subtype = myImi.getSubtypeAt(i)
            if (locale == subtype.locale() && keyboardLayoutSetName == subtype.mainLayoutNameOrQwerty()) {
                return subtype
            }
        }
        return null
    }

    fun findSubtypeForHintLocale(locale: Locale): InputMethodSubtype? {
        // Find the best subtype based on a locale matching
        val subtypes = getMyEnabledInputMethodSubtypeList(true)
        var bestMatch = getBestMatch(locale, subtypes) { it.locale() }
        if (bestMatch != null) return bestMatch

        // search for first secondary language & script match
        val language = locale.language
        val script = locale.script()
        for (subtype in subtypes) {
            val subtypeLocale = subtype.locale()
            if (subtypeLocale.script() != script) continue  // need compatible script

            bestMatch = subtype
            val secondaryLocales = getSecondaryLocales(subtype.extraValue)
            for (secondaryLocale in secondaryLocales) {
                if (secondaryLocale.language == language) {
                    return bestMatch
                }
            }
        }
        // if wanted script is not compatible to current subtype, return a subtype with compatible script if available
        if (script != currentSubtypeLocale.script()) {
            return bestMatch
        }
        return null
    }

    fun onSubtypeChanged(newSubtype: InputMethodSubtype) {
        SubtypeSettings.setSelectedSubtype(context.prefs(), newSubtype)
        currentRichInputMethodSubtype = RichInputMethodSubtype.get(newSubtype)
        updateShortcutIme()
        if (DEBUG) {
            Log.w(TAG, "onSubtypeChanged: $currentRichInputMethodSubtype")
        }
    }

    fun refreshSubtypeCaches() {
        inputMethodInfoCache.clear()
        currentRichInputMethodSubtype = RichInputMethodSubtype.get(SubtypeSettings.getSelectedSubtype(context.prefs()))
        updateShortcutIme()
    }

    // todo: deprecated
    fun switchToShortcutIme(inputMethodService: InputMethodService) {
        val imiId = shortcutInputMethodInfo?.id ?: return
        val token = inputMethodService.window.window?.attributes?.token ?: return
        object : AsyncTask<Void?, Void?, Void?>() {
            override fun doInBackground(vararg params: Void?): Void? {
                imm.setInputMethodAndSubtype(token, imiId, shortcutSubtype)
                return null
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    // todo: is shortcutIme only voice input, or can it be something else?
    //  if always voice input, rename it and other things like mHasShortcutKey
    private fun updateShortcutIme() {
        if (DEBUG) {
            val subtype = shortcutSubtype?.let { "${it.locale()}, ${it.mode}" } ?: "<null>"
            Log.d(TAG, ("Update shortcut IME from: ${shortcutInputMethodInfo?.id ?: "<null>"}, $subtype"))
        }
        val richSubtype = currentRichInputMethodSubtype
        val implicitlyEnabledSubtype = checkIfSubtypeBelongsToThisImeAndImplicitlyEnabled(richSubtype.rawSubtype)
        val systemLocale = context.resources.configuration.locale()
        LanguageOnSpacebarUtils.onSubtypeChanged(richSubtype, implicitlyEnabledSubtype, systemLocale)
        LanguageOnSpacebarUtils.setEnabledSubtypes(getMyEnabledInputMethodSubtypeList(true))

        // TODO: Update an icon for shortcut IME
        val shortcuts = inputMethodManager.shortcutInputMethodsAndSubtypes
        shortcutInputMethodInfo = null
        shortcutSubtype = null
        for (imi in shortcuts.keys) {
            val subtypes = shortcuts[imi] ?: continue
            // TODO: Returns the first found IMI for now. Should handle all shortcuts as appropriate.
            shortcutInputMethodInfo = imi
            // TODO: Pick up the first found subtype for now. Should handle all subtypes as appropriate.
            shortcutSubtype = if (subtypes.size > 0) subtypes[0] else null
            break
        }
        if (DEBUG) {
            val subtype = shortcutSubtype?.let { "${it.locale()}, ${it.mode}" } ?: "<null>"
            Log.d(TAG, ("Update shortcut IME to: ${shortcutInputMethodInfo?.id ?: "<null>"}, $subtype"))
        }
    }

    private fun hasMultipleEnabledSubtypes(shouldIncludeAuxiliarySubtypes: Boolean, imiList: List<InputMethodInfo>): Boolean {
        // Number of the filtered IMEs
        var filteredImisCount = 0

        imiList.forEach { imi ->
            // We can return true immediately after we find two or more filtered IMEs.
            if (filteredImisCount > 1) return true
            val subtypes = getEnabledInputMethodSubtypeList(imi, true)
            // IMEs that have no subtypes should be counted.
            if (subtypes.isEmpty()) {
                ++filteredImisCount
                return@forEach
            }

            var auxCount = 0
            for (subtype in subtypes) {
                if (!subtype.isAuxiliary) {
                    // IMEs that have one or more non-auxiliary subtypes should be counted.
                    ++filteredImisCount
                    return@forEach
                }
                ++auxCount
            }

            // If shouldIncludeAuxiliarySubtypes is true, IMEs that have two or more auxiliary
            // subtypes should be counted as well.
            if (shouldIncludeAuxiliarySubtypes && auxCount > 1) {
                ++filteredImisCount
            }
        }

        if (filteredImisCount > 1) {
            return true
        }
        val subtypes = getMyEnabledInputMethodSubtypeList(true)
        // imm.getEnabledInputMethodSubtypeList(null, true) will return the current IME's
        // both explicitly and implicitly enabled input method subtype.
        // (The current IME should be LatinIME.)
        return subtypes.count { it.mode == Constants.Subtype.KEYBOARD_MODE } > 1
    }

    private fun checkInitialized() {
        if (!isInitializedInternal) {
            throw RuntimeException("$TAG is used before initialization")
        }
    }

    private fun initInternal(ctx: Context) {
        if (isInitializedInternal) {
            return
        }
        imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        context = ctx
        inputMethodInfoCache = InputMethodInfoCache(imm, ctx.packageName)

        // Initialize the current input method subtype and the shortcut IME.
        refreshSubtypeCaches()
    }

    companion object {
        private val TAG = RichInputMethodManager::class.java.simpleName
        private const val DEBUG = false

        private val instance = RichInputMethodManager()

        @JvmStatic
        fun getInstance(): RichInputMethodManager {
            instance.checkInitialized()
            return instance
        }

        fun init(ctx: Context) {
            instance.initInternal(ctx)
        }

        @JvmStatic
        fun isInitialized() = instance.isInitializedInternal

        private var forcedSubtypeForTesting: RichInputMethodSubtype? = null

        fun forceSubtype(subtype: InputMethodSubtype) {
            forcedSubtypeForTesting = RichInputMethodSubtype.get(subtype)
        }

        fun canSwitchLanguage(): Boolean {
            if (!isInitialized()) return false
            if (Settings.getValues().mLanguageSwitchKeyToOtherSubtypes && instance.hasMultipleEnabledSubtypesInThisIme(false)) return true
            if (Settings.getValues().mLanguageSwitchKeyToOtherImes && instance.imm.enabledInputMethodList.size > 1) return true
            return false
        }
    }
}

private class InputMethodInfoCache(private val imm: InputMethodManager, private val imePackageName: String) {
    private var cachedThisImeInfo: InputMethodInfo? = null
    private val cachedSubtypeListWithImplicitlySelected = HashMap<InputMethodInfo, List<InputMethodSubtype>>()

    private val cachedSubtypeListOnlyExplicitlySelected = HashMap<InputMethodInfo, List<InputMethodSubtype>>()

    @get:Synchronized
    val inputMethodOfThisIme: InputMethodInfo get() {
        if (cachedThisImeInfo == null)
            cachedThisImeInfo = imm.inputMethodList.firstOrNull { it.packageName == imePackageName }
        cachedThisImeInfo?.let { return it }
        throw RuntimeException("Input method id for $imePackageName not found, only found " +
                imm.inputMethodList.map { it.packageName })
    }

    @Synchronized
    fun getEnabledInputMethodSubtypeList(imi: InputMethodInfo, allowsImplicitlySelectedSubtypes: Boolean): List<InputMethodSubtype> {
        val cache = if (allowsImplicitlySelectedSubtypes) cachedSubtypeListWithImplicitlySelected
            else cachedSubtypeListOnlyExplicitlySelected
        cache[imi]?.let { return it }
        val result = if (imi === inputMethodOfThisIme) {
            // allowsImplicitlySelectedSubtypes means system should choose if nothing is enabled,
            // use it to fall back to system locales or en_US to avoid returning an empty list
            SubtypeSettings.getEnabledSubtypes(allowsImplicitlySelectedSubtypes)
        } else {
            imm.getEnabledInputMethodSubtypeList(imi, allowsImplicitlySelectedSubtypes)
        }
        cache[imi] = result
        return result
    }

    @Synchronized
    fun clear() {
        cachedThisImeInfo = null
        cachedSubtypeListWithImplicitlySelected.clear()
        cachedSubtypeListOnlyExplicitlySelected.clear()
    }
}
