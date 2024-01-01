/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin;

import android.content.Context;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.os.AsyncTask;
import android.os.IBinder;
import org.dslul.openboard.inputmethod.latin.utils.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import org.dslul.openboard.inputmethod.annotations.UsedForTesting;
import org.dslul.openboard.inputmethod.compat.InputMethodSubtypeCompatUtils;
import org.dslul.openboard.inputmethod.latin.settings.Settings;
import org.dslul.openboard.inputmethod.latin.utils.DeviceProtectedUtils;
import org.dslul.openboard.inputmethod.latin.utils.LanguageOnSpacebarUtils;
import org.dslul.openboard.inputmethod.latin.utils.ScriptUtils;
import org.dslul.openboard.inputmethod.latin.utils.SubtypeLocaleUtils;
import org.dslul.openboard.inputmethod.latin.utils.SubtypeSettingsKt;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.dslul.openboard.inputmethod.latin.common.Constants.Subtype.KEYBOARD_MODE;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Enrichment class for InputMethodManager to simplify interaction and add functionality.
 */
// non final for easy mocking.
public class RichInputMethodManager {
    private static final String TAG = RichInputMethodManager.class.getSimpleName();
    private static final boolean DEBUG = false;

    private RichInputMethodManager() {
        // This utility class is not publicly instantiable.
    }

    private static final RichInputMethodManager sInstance = new RichInputMethodManager();

    private Context mContext;
    private InputMethodManager mImm;
    private InputMethodInfoCache mInputMethodInfoCache;
    private RichInputMethodSubtype mCurrentRichInputMethodSubtype;
    private InputMethodInfo mShortcutInputMethodInfo;
    private InputMethodSubtype mShortcutSubtype;

    private static final int INDEX_NOT_FOUND = -1;

    public static RichInputMethodManager getInstance() {
        sInstance.checkInitialized();
        return sInstance;
    }

    public static void init(final Context context) {
        sInstance.initInternal(context);
    }

    private boolean isInitialized() {
        return mImm != null;
    }

    private void checkInitialized() {
        if (!isInitialized()) {
            throw new RuntimeException(TAG + " is used before initialization");
        }
    }

    private void initInternal(final Context context) {
        if (isInitialized()) {
            return;
        }
        mImm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        mContext = context;
        mInputMethodInfoCache = new InputMethodInfoCache(
                mImm, context.getPackageName());

        // Initialize subtype utils.
        SubtypeLocaleUtils.init(context);

        // Initialize the current input method subtype and the shortcut IME.
        refreshSubtypeCaches();
    }

    public InputMethodManager getInputMethodManager() {
        checkInitialized();
        return mImm;
    }

    public List<InputMethodSubtype> getMyEnabledInputMethodSubtypeList(
            boolean allowsImplicitlySelectedSubtypes) {
        return getEnabledInputMethodSubtypeList(
                getInputMethodInfoOfThisIme(), allowsImplicitlySelectedSubtypes);
    }

    public @Nullable InputMethodSubtype getNextSubtypeInThisIme(final boolean onlyCurrentIme) {
        final InputMethodSubtype currentSubtype = getCurrentSubtype().getRawSubtype();
        final List<InputMethodSubtype> enabledSubtypes = getMyEnabledInputMethodSubtypeList(true);
        final int currentIndex = enabledSubtypes.indexOf(currentSubtype);
        if (currentIndex == INDEX_NOT_FOUND) {
            Log.w(TAG, "Can't find current subtype in enabled subtypes: subtype="
                    + SubtypeLocaleUtils.getSubtypeNameForLogging(currentSubtype));
            if (onlyCurrentIme) return enabledSubtypes.get(0); // just return first enabled subtype
            else return null;
        }
        final int nextIndex = (currentIndex + 1) % enabledSubtypes.size();
        if (nextIndex <= currentIndex && !onlyCurrentIme) {
            // The current subtype is the last or only enabled one and it needs to switch to next IME.
            return null;
        }
        return enabledSubtypes.get(nextIndex);
    }

    private static class InputMethodInfoCache {
        private final InputMethodManager mImm;
        private final String mImePackageName;

        private InputMethodInfo mCachedThisImeInfo;
        private final HashMap<InputMethodInfo, List<InputMethodSubtype>>
                mCachedSubtypeListWithImplicitlySelected;
        private final HashMap<InputMethodInfo, List<InputMethodSubtype>>
                mCachedSubtypeListOnlyExplicitlySelected;

        public InputMethodInfoCache(final InputMethodManager imm, final String imePackageName) {
            mImm = imm;
            mImePackageName = imePackageName;
            mCachedSubtypeListWithImplicitlySelected = new HashMap<>();
            mCachedSubtypeListOnlyExplicitlySelected = new HashMap<>();
        }

        public synchronized InputMethodInfo getInputMethodOfThisIme() {
            if (mCachedThisImeInfo != null) {
                return mCachedThisImeInfo;
            }
            for (final InputMethodInfo imi : mImm.getInputMethodList()) {
                if (imi.getPackageName().equals(mImePackageName)) {
                    mCachedThisImeInfo = imi;
                    return imi;
                }
            }
            throw new RuntimeException("Input method id for " + mImePackageName + " not found.");
        }

        public synchronized List<InputMethodSubtype> getEnabledInputMethodSubtypeList(
                final InputMethodInfo imi, final boolean allowsImplicitlySelectedSubtypes) {
            final HashMap<InputMethodInfo, List<InputMethodSubtype>> cache =
                    allowsImplicitlySelectedSubtypes
                    ? mCachedSubtypeListWithImplicitlySelected
                    : mCachedSubtypeListOnlyExplicitlySelected;
            final List<InputMethodSubtype> cachedList = cache.get(imi);
            if (cachedList != null) {
                return cachedList;
            }
            final List<InputMethodSubtype> result;
            if (imi == getInputMethodOfThisIme()) {
                // allowsImplicitlySelectedSubtypes means system should choose if nothing is enabled,
                // use it to fall back to system locales or en_US to avoid returning an empty list
                result = SubtypeSettingsKt.getEnabledSubtypes(DeviceProtectedUtils.getSharedPreferences(sInstance.mContext), allowsImplicitlySelectedSubtypes);
            } else {
                result = mImm.getEnabledInputMethodSubtypeList(imi, allowsImplicitlySelectedSubtypes);
            }
            cache.put(imi, result);
            return result;
        }

        public synchronized void clear() {
            mCachedThisImeInfo = null;
            mCachedSubtypeListWithImplicitlySelected.clear();
            mCachedSubtypeListOnlyExplicitlySelected.clear();
        }
    }

    public InputMethodInfo getInputMethodInfoOfThisIme() {
        return mInputMethodInfoCache.getInputMethodOfThisIme();
    }

    public boolean checkIfSubtypeBelongsToThisImeAndEnabled(final InputMethodSubtype subtype) {
        return getEnabledInputMethodSubtypeList(getInputMethodInfoOfThisIme(), true)
                .contains(subtype);
    }

    public boolean checkIfSubtypeBelongsToThisImeAndImplicitlyEnabled(final InputMethodSubtype subtype) {
        final boolean subtypeEnabled = checkIfSubtypeBelongsToThisImeAndEnabled(subtype);
        final boolean subtypeExplicitlyEnabled = getMyEnabledInputMethodSubtypeList(false)
                .contains(subtype);
        return subtypeEnabled && !subtypeExplicitlyEnabled;
    }

    public void onSubtypeChanged(@NonNull final InputMethodSubtype newSubtype) {
        updateCurrentSubtype(newSubtype);
        updateShortcutIme();
        if (DEBUG) {
            Log.w(TAG, "onSubtypeChanged: " + mCurrentRichInputMethodSubtype.getNameForLogging());
        }
    }

    private static RichInputMethodSubtype sForcedSubtypeForTesting = null;

    @UsedForTesting
    static void forceSubtype(@NonNull final InputMethodSubtype subtype) {
        sForcedSubtypeForTesting = RichInputMethodSubtype.getRichInputMethodSubtype(subtype);
    }

    @NonNull
    public Locale getCurrentSubtypeLocale() {
        if (null != sForcedSubtypeForTesting) {
            return sForcedSubtypeForTesting.getLocale();
        }
        return getCurrentSubtype().getLocale();
    }

    @NonNull
    public RichInputMethodSubtype getCurrentSubtype() {
        if (null != sForcedSubtypeForTesting) {
            return sForcedSubtypeForTesting;
        }
        return mCurrentRichInputMethodSubtype;
    }


    public String getCombiningRulesExtraValueOfCurrentSubtype() {
        return SubtypeLocaleUtils.getCombiningRulesExtraValue(getCurrentSubtype().getRawSubtype());
    }

    public boolean hasMultipleEnabledIMEsOrSubtypes(final boolean shouldIncludeAuxiliarySubtypes) {
        final List<InputMethodInfo> enabledImis = mImm.getEnabledInputMethodList();
        return hasMultipleEnabledSubtypes(shouldIncludeAuxiliarySubtypes, enabledImis);
    }

    public boolean hasMultipleEnabledSubtypesInThisIme(
            final boolean shouldIncludeAuxiliarySubtypes) {
        final List<InputMethodInfo> imiList = Collections.singletonList(
                getInputMethodInfoOfThisIme());
        return hasMultipleEnabledSubtypes(shouldIncludeAuxiliarySubtypes, imiList);
    }

    private boolean hasMultipleEnabledSubtypes(final boolean shouldIncludeAuxiliarySubtypes,
            final List<InputMethodInfo> imiList) {
        // Number of the filtered IMEs
        int filteredImisCount = 0;

        for (InputMethodInfo imi : imiList) {
            // We can return true immediately after we find two or more filtered IMEs.
            if (filteredImisCount > 1) return true;
            final List<InputMethodSubtype> subtypes = getEnabledInputMethodSubtypeList(imi, true);
            // IMEs that have no subtypes should be counted.
            if (subtypes.isEmpty()) {
                ++filteredImisCount;
                continue;
            }

            int auxCount = 0;
            for (InputMethodSubtype subtype : subtypes) {
                if (subtype.isAuxiliary()) {
                    ++auxCount;
                }
            }
            final int nonAuxCount = subtypes.size() - auxCount;

            // IMEs that have one or more non-auxiliary subtypes should be counted.
            // If shouldIncludeAuxiliarySubtypes is true, IMEs that have two or more auxiliary
            // subtypes should be counted as well.
            if (nonAuxCount > 0 || (shouldIncludeAuxiliarySubtypes && auxCount > 1)) {
                ++filteredImisCount;
            }
        }

        if (filteredImisCount > 1) {
            return true;
        }
        final List<InputMethodSubtype> subtypes = getMyEnabledInputMethodSubtypeList(true);
        int keyboardCount = 0;
        // imm.getEnabledInputMethodSubtypeList(null, true) will return the current IME's
        // both explicitly and implicitly enabled input method subtype.
        // (The current IME should be LatinIME.)
        for (InputMethodSubtype subtype : subtypes) {
            if (KEYBOARD_MODE.equals(subtype.getMode())) {
                ++keyboardCount;
            }
        }
        return keyboardCount > 1;
    }

    public InputMethodSubtype findSubtypeByLocaleAndKeyboardLayoutSet(final String localeString,
            final String keyboardLayoutSetName) {
        final InputMethodInfo myImi = getInputMethodInfoOfThisIme();
        final int count = myImi.getSubtypeCount();
        for (int i = 0; i < count; i++) {
            final InputMethodSubtype subtype = myImi.getSubtypeAt(i);
            final String layoutName = SubtypeLocaleUtils.getKeyboardLayoutSetName(subtype);
            if (localeString.equals(subtype.getLocale())
                    && keyboardLayoutSetName.equals(layoutName)) {
                return subtype;
            }
        }
        return null;
    }

    public InputMethodSubtype findSubtypeByLocale(final Locale locale) {
        // Find the best subtype based on a straightforward matching algorithm.
        final List<InputMethodSubtype> subtypes =
                getMyEnabledInputMethodSubtypeList(true /* allowsImplicitlySelectedSubtypes */);
        final int count = subtypes.size();
        // search for exact match
        for (int i = 0; i < count; ++i) {
            final InputMethodSubtype subtype = subtypes.get(i);
            final Locale subtypeLocale = InputMethodSubtypeCompatUtils.getLocaleObject(subtype);
            if (subtypeLocale.equals(locale)) {
                return subtype;
            }
        }
        // search for language + country + variant match
        for (int i = 0; i < count; ++i) {
            final InputMethodSubtype subtype = subtypes.get(i);
            final Locale subtypeLocale = InputMethodSubtypeCompatUtils.getLocaleObject(subtype);
            if (subtypeLocale.getLanguage().equals(locale.getLanguage()) &&
                    subtypeLocale.getCountry().equals(locale.getCountry()) &&
                    subtypeLocale.getVariant().equals(locale.getVariant())) {
                return subtype;
            }
        }
        // search for language + country match
        for (int i = 0; i < count; ++i) {
            final InputMethodSubtype subtype = subtypes.get(i);
            final Locale subtypeLocale = InputMethodSubtypeCompatUtils.getLocaleObject(subtype);
            if (subtypeLocale.getLanguage().equals(locale.getLanguage()) &&
                    subtypeLocale.getCountry().equals(locale.getCountry())) {
                return subtype;
            }
        }
        // search for secondary locale match
        final SharedPreferences prefs = DeviceProtectedUtils.getSharedPreferences(mContext);
        for (int i = 0; i < count; ++i) {
            final InputMethodSubtype subtype = subtypes.get(i);
            final String subtypeLocale = subtype.getLocale();
            final List<Locale> secondaryLocales = Settings.getSecondaryLocales(prefs, subtypeLocale);
            for (final Locale secondaryLocale : secondaryLocales) {
                if (secondaryLocale.equals(locale)) {
                    return subtype;
                }
            }
        }
        // search for language match
        for (int i = 0; i < count; ++i) {
            final InputMethodSubtype subtype = subtypes.get(i);
            final Locale subtypeLocale = InputMethodSubtypeCompatUtils.getLocaleObject(subtype);
            if (subtypeLocale.getLanguage().equals(locale.getLanguage())) {
                return subtype;
            }
        }
        // search for secondary language match
        for (int i = 0; i < count; ++i) {
            final InputMethodSubtype subtype = subtypes.get(i);
            final String subtypeLocale = subtype.getLocale();
            final List<Locale> secondaryLocales = Settings.getSecondaryLocales(prefs, subtypeLocale);
            for (final Locale secondaryLocale : secondaryLocales) {
                if (secondaryLocale.getLanguage().equals(locale.getLanguage())) {
                    return subtype;
                }
            }
        }

        // extra: if current script is not compatible to current subtype, search for compatible script
        // this is acceptable only because this function is only used for switching to a certain locale using EditorInfo.hintLocales
        final int script = ScriptUtils.getScriptFromSpellCheckerLocale(locale);
        if (script != ScriptUtils.getScriptFromSpellCheckerLocale(getCurrentSubtypeLocale())) {
            for (int i = 0; i < count; ++i) {
                final InputMethodSubtype subtype = subtypes.get(i);
                final Locale subtypeLocale = InputMethodSubtypeCompatUtils.getLocaleObject(subtype);
                if (ScriptUtils.getScriptFromSpellCheckerLocale(subtypeLocale) == script) {
                    return subtype;
                }
            }
        }
        return null;
    }

    public List<InputMethodSubtype> getEnabledInputMethodSubtypeList(final InputMethodInfo imi,
            final boolean allowsImplicitlySelectedSubtypes) {
        return mInputMethodInfoCache.getEnabledInputMethodSubtypeList(
                imi, allowsImplicitlySelectedSubtypes);
    }

    public void refreshSubtypeCaches() {
        mInputMethodInfoCache.clear();
        SharedPreferences prefs = DeviceProtectedUtils.getSharedPreferences(mContext);
        updateCurrentSubtype(SubtypeSettingsKt.getSelectedSubtype(prefs));
        updateShortcutIme();
    }

    private void updateCurrentSubtype(final InputMethodSubtype subtype) {
        SubtypeSettingsKt.setSelectedSubtype(DeviceProtectedUtils.getSharedPreferences(mContext), subtype);
        mCurrentRichInputMethodSubtype = RichInputMethodSubtype.getRichInputMethodSubtype(subtype);
    }

    // todo: is shortcutIme only voice input, or can it be something else?
    //  if always voice input, rename it and other things like mHasShortcutKey
    private void updateShortcutIme() {
        if (DEBUG) {
            Log.d(TAG, "Update shortcut IME from : "
                    + (mShortcutInputMethodInfo == null
                            ? "<null>" : mShortcutInputMethodInfo.getId()) + ", "
                    + (mShortcutSubtype == null ? "<null>" : (
                            mShortcutSubtype.getLocale() + ", " + mShortcutSubtype.getMode())));
        }
        final RichInputMethodSubtype richSubtype = mCurrentRichInputMethodSubtype;
        final boolean implicitlyEnabledSubtype = checkIfSubtypeBelongsToThisImeAndImplicitlyEnabled(
                richSubtype.getRawSubtype());
        final Locale systemLocale = mContext.getResources().getConfiguration().locale;
        LanguageOnSpacebarUtils.onSubtypeChanged(
                richSubtype, implicitlyEnabledSubtype, systemLocale);
        LanguageOnSpacebarUtils.setEnabledSubtypes(getMyEnabledInputMethodSubtypeList(
                true /* allowsImplicitlySelectedSubtypes */));

        // TODO: Update an icon for shortcut IME
        final Map<InputMethodInfo, List<InputMethodSubtype>> shortcuts =
                getInputMethodManager().getShortcutInputMethodsAndSubtypes();
        mShortcutInputMethodInfo = null;
        mShortcutSubtype = null;
        for (final InputMethodInfo imi : shortcuts.keySet()) {
            final List<InputMethodSubtype> subtypes = shortcuts.get(imi);
            // TODO: Returns the first found IMI for now. Should handle all shortcuts as
            // appropriate.
            mShortcutInputMethodInfo = imi;
            // TODO: Pick up the first found subtype for now. Should handle all subtypes
            // as appropriate.
            mShortcutSubtype = subtypes.size() > 0 ? subtypes.get(0) : null;
            break;
        }
        if (DEBUG) {
            Log.d(TAG, "Update shortcut IME to : "
                    + (mShortcutInputMethodInfo == null
                            ? "<null>" : mShortcutInputMethodInfo.getId()) + ", "
                    + (mShortcutSubtype == null ? "<null>" : (
                            mShortcutSubtype.getLocale() + ", " + mShortcutSubtype.getMode())));
        }
    }

    public void switchToShortcutIme(final InputMethodService context) {
        if (mShortcutInputMethodInfo == null) {
            return;
        }

        final String imiId = mShortcutInputMethodInfo.getId();
        switchToTargetIME(imiId, mShortcutSubtype, context);
    }

    public boolean hasShortcutIme() {
        return mShortcutInputMethodInfo != null;
    }

    private void switchToTargetIME(final String imiId, final InputMethodSubtype subtype,
            final InputMethodService context) {
        final IBinder token = context.getWindow().getWindow().getAttributes().token;
        if (token == null) {
            return;
        }
        final InputMethodManager imm = getInputMethodManager();
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                imm.setInputMethodAndSubtype(token, imiId, subtype);
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public boolean isShortcutImeReady() {
        return mShortcutInputMethodInfo != null;
    }
}
