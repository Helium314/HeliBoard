/*
7 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dslul.openboard.inputmethod.latin;

import android.Manifest;
import android.content.Context;
import android.provider.UserDictionary;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;

import org.dslul.openboard.inputmethod.annotations.UsedForTesting;
import org.dslul.openboard.inputmethod.keyboard.Keyboard;
import org.dslul.openboard.inputmethod.latin.NgramContext.WordInfo;
import org.dslul.openboard.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import org.dslul.openboard.inputmethod.latin.common.ComposedData;
import org.dslul.openboard.inputmethod.latin.common.Constants;
import org.dslul.openboard.inputmethod.latin.common.StringUtils;
import org.dslul.openboard.inputmethod.latin.permissions.PermissionsUtil;
import org.dslul.openboard.inputmethod.latin.personalization.UserHistoryDictionary;
import org.dslul.openboard.inputmethod.latin.settings.Settings;
import org.dslul.openboard.inputmethod.latin.settings.SettingsValuesForSuggestion;
import org.dslul.openboard.inputmethod.latin.utils.ExecutorUtils;
import org.dslul.openboard.inputmethod.latin.utils.ScriptUtils;
import org.dslul.openboard.inputmethod.latin.utils.SuggestionResults;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Facilitates interaction with different kinds of dictionaries. Provides APIs
 * to instantiate and select the correct dictionaries (based on language or account),
 * update entries and fetch suggestions.
 *
 * Currently AndroidSpellCheckerService and LatinIME both use DictionaryFacilitator as
 * a client for interacting with dictionaries.
 */
public class DictionaryFacilitatorImpl implements DictionaryFacilitator {
    // TODO: Consolidate dictionaries in native code.
    public static final String TAG = DictionaryFacilitatorImpl.class.getSimpleName();

    // HACK: This threshold is being used when adding a capitalized entry in the User History
    // dictionary.
    private static final int CAPITALIZED_FORM_MAX_PROBABILITY_FOR_INSERT = 140;

    private DictionaryGroup mDictionaryGroup = new DictionaryGroup();
    private DictionaryGroup mSecondaryDictionaryGroup = new DictionaryGroup();
    private volatile CountDownLatch mLatchForWaitingLoadingMainDictionaries = new CountDownLatch(0);
    // To synchronize assigning mDictionaryGroup to ensure closing dictionaries.
    private final Object mLock = new Object();

    public static final Map<String, Class<? extends ExpandableBinaryDictionary>>
            DICT_TYPE_TO_CLASS = new HashMap<>();

    static {
        DICT_TYPE_TO_CLASS.put(Dictionary.TYPE_USER_HISTORY, UserHistoryDictionary.class);
        DICT_TYPE_TO_CLASS.put(Dictionary.TYPE_USER, UserBinaryDictionary.class);
        DICT_TYPE_TO_CLASS.put(Dictionary.TYPE_CONTACTS, ContactsBinaryDictionary.class);
    }

    private static final String DICT_FACTORY_METHOD_NAME = "getDictionary";
    private static final Class<?>[] DICT_FACTORY_METHOD_ARG_TYPES =
            new Class[] { Context.class, Locale.class, File.class, String.class, String.class };

    // these caches are never even set, as the corresponding functions are not called...
    // and even if they were set, one is only written, but never read, and the other one
    //  is only read and thus empty and useless
    private LruCache<String, Boolean> mValidSpellingWordReadCache;
    private LruCache<String, Boolean> mValidSpellingWordWriteCache;

    @Override
    public void setValidSpellingWordReadCache(final LruCache<String, Boolean> cache) {
        mValidSpellingWordReadCache = cache;
    }

    @Override
    public void setValidSpellingWordWriteCache(final LruCache<String, Boolean> cache) {
        mValidSpellingWordWriteCache = cache;
    }

    @Override
    public boolean isForLocale(final Locale locale) {
        return locale != null && locale.equals(mDictionaryGroup.mLocale);
    }

    private boolean hasLocale(final Locale locale) {
        return locale != null && (locale.equals(mDictionaryGroup.mLocale) ||
                (mSecondaryDictionaryGroup != null && locale.equals(mSecondaryDictionaryGroup.mLocale)));
    }

    /**
     * Returns whether this facilitator is exactly for this account.
     *
     * @param account the account to test against.
     */
    public boolean isForAccount(@Nullable final String account) {
        return TextUtils.equals(mDictionaryGroup.mAccount, account);
    }

    /**
     * A group of dictionaries that work together for a single language.
     */
    private static class DictionaryGroup {
        // TODO: Add null analysis annotations.
        // TODO: Run evaluation to determine a reasonable value for these constants. The current
        // values are ad-hoc and chosen without any particular care or methodology.
        public static final float WEIGHT_FOR_MOST_PROBABLE_LANGUAGE = 1.0f;
        public static final float WEIGHT_FOR_GESTURING_IN_NOT_MOST_PROBABLE_LANGUAGE = 0.95f;
        public static final float WEIGHT_FOR_TYPING_IN_NOT_MOST_PROBABLE_LANGUAGE = 0.6f;

        private static final int MAX_CONFIDENCE = 2;
        private static final int MIN_CONFIDENCE = 0;

        /**
         * The locale associated with the dictionary group.
         */
        @Nullable public final Locale mLocale;

        /**
         * The user account associated with the dictionary group.
         */
        @Nullable public final String mAccount;

        @Nullable private Dictionary mMainDict;
        // Confidence that the most probable language is actually the language the user is
        // typing in. For now, this is simply the number of times a word from this language
        // has been committed in a row, with an exception when typing a single word not contained
        // in this language.
        private int mConfidence = 1;

        // words cannot be removed from main dictionary, so we use a blacklist instead
        public String blacklistFileName = null;
        public Set<String> blacklist = new HashSet<>();

        // allow to go above max confidence, for better determination of currently preferred language
        // when decreasing confidence or getting weight factor, limit to maximum
        public void increaseConfidence() {
            mConfidence += 1;
            if (mConfidence <= MAX_CONFIDENCE)
                updateWeights();
        }

        // If confidence is above max, drop to max confidence. This does not change weights and
        // allows conveniently typing single words from the other language without affecting suggestions
        public void decreaseConfidence() {
            if (mConfidence > MAX_CONFIDENCE)
                mConfidence = MAX_CONFIDENCE;
            else if (mConfidence > MIN_CONFIDENCE) {
                mConfidence -= 1;
                updateWeights();
            }
        }

        // TODO: might need some more tuning, maybe more confidence steps
        private void updateWeights() {
            mWeightForTypingInLocale = 1f - 0.15f * (MAX_CONFIDENCE - mConfidence);
            mWeightForGesturingInLocale = 1f - 0.05f * (MAX_CONFIDENCE - mConfidence);
        }

        public float mWeightForTypingInLocale = WEIGHT_FOR_MOST_PROBABLE_LANGUAGE;
        public float mWeightForGesturingInLocale = WEIGHT_FOR_MOST_PROBABLE_LANGUAGE;
        public final ConcurrentHashMap<String, ExpandableBinaryDictionary> mSubDictMap =
                new ConcurrentHashMap<>();

        public DictionaryGroup() {
            this(null /* locale */, null /* mainDict */, null /* account */,
                    Collections.<String, ExpandableBinaryDictionary>emptyMap() /* subDicts */);
        }

        public DictionaryGroup(@Nullable final Locale locale,
                @Nullable final Dictionary mainDict,
                @Nullable final String account,
                final Map<String, ExpandableBinaryDictionary> subDicts) {
            mLocale = locale;
            mAccount = account;
            // The main dictionary can be asynchronously loaded.
            setMainDict(mainDict);
            for (final Map.Entry<String, ExpandableBinaryDictionary> entry : subDicts.entrySet()) {
                setSubDict(entry.getKey(), entry.getValue());
            }
        }

        private void setSubDict(final String dictType, final ExpandableBinaryDictionary dict) {
            if (dict != null) {
                mSubDictMap.put(dictType, dict);
            }
        }

        public void setMainDict(final Dictionary mainDict) {
            // Close old dictionary if exists. Main dictionary can be assigned multiple times.
            final Dictionary oldDict = mMainDict;
            mMainDict = mainDict;
            if (oldDict != null && mainDict != oldDict) {
                oldDict.close();
            }
        }

        public Dictionary getDict(final String dictType) {
            if (Dictionary.TYPE_MAIN.equals(dictType)) {
                return mMainDict;
            }
            return getSubDict(dictType);
        }

        public ExpandableBinaryDictionary getSubDict(final String dictType) {
            return mSubDictMap.get(dictType);
        }

        public boolean hasDict(final String dictType, @Nullable final String account) {
            if (Dictionary.TYPE_MAIN.equals(dictType)) {
                return mMainDict != null;
            }
            if (Dictionary.TYPE_USER_HISTORY.equals(dictType) &&
                    !TextUtils.equals(account, mAccount)) {
                // If the dictionary type is user history, & if the account doesn't match,
                // return immediately. If the account matches, continue looking it up in the
                // sub dictionary map.
                return false;
            }
            return mSubDictMap.containsKey(dictType);
        }

        public void closeDict(final String dictType) {
            final Dictionary dict;
            if (Dictionary.TYPE_MAIN.equals(dictType)) {
                dict = mMainDict;
            } else {
                dict = mSubDictMap.remove(dictType);
            }
            if (dict != null) {
                dict.close();
            }
        }
    }

    public DictionaryFacilitatorImpl() {
    }

    @Override
    public void onStartInput() {
    }

    @Override
    public void onFinishInput(Context context) {
        for (final String dictType : ALL_DICTIONARY_TYPES) {
            Dictionary dict = mDictionaryGroup.getDict(dictType);
            if (dict != null) dict.onFinishInput();
        }
        if (mSecondaryDictionaryGroup != null)
            for (final String dictType : ALL_DICTIONARY_TYPES) {
                Dictionary dict = mSecondaryDictionaryGroup.getDict(dictType);
                if (dict != null) dict.onFinishInput();
            }
    }

    @Override
    public boolean isActive() {
        return mDictionaryGroup.mLocale != null;
    }

    @Override
    public Locale getLocale() {
        return mDictionaryGroup.mLocale;
    }

    @Override
    public boolean usesContacts() {
        return mDictionaryGroup.getSubDict(Dictionary.TYPE_CONTACTS) != null;
    }

    @Override
    public String getAccount() {
        return null;
    }

    @Nullable
    private static ExpandableBinaryDictionary getSubDict(final String dictType,
            final Context context, final Locale locale, final File dictFile,
            final String dictNamePrefix, @Nullable final String account) {
        final Class<? extends ExpandableBinaryDictionary> dictClass =
                DICT_TYPE_TO_CLASS.get(dictType);
        if (dictClass == null) {
            return null;
        }
        try {
            final Method factoryMethod = dictClass.getMethod(DICT_FACTORY_METHOD_NAME,
                    DICT_FACTORY_METHOD_ARG_TYPES);
            final Object dict = factoryMethod.invoke(null /* obj */,
                    context, locale, dictFile, dictNamePrefix, account);
            return (ExpandableBinaryDictionary) dict;
        } catch (final NoSuchMethodException | SecurityException | IllegalAccessException
                | IllegalArgumentException | InvocationTargetException e) {
            Log.e(TAG, "Cannot create dictionary: " + dictType, e);
            return null;
        }
    }

    @Nullable
    static DictionaryGroup findDictionaryGroupWithLocale(final DictionaryGroup dictionaryGroup,
            final Locale locale) {
        if (dictionaryGroup == null) return null;
        return locale.equals(dictionaryGroup.mLocale) ? dictionaryGroup : null;
    }

    @Override
    // TODO: what if secondary locale changes, but main remains same?
    //  current reset doesn't consider this (not here, and not in other places where locales
    //  are checked against current locale)
    // but that actually shouldn't happen anyway...
    public void resetDictionaries(
            final Context context,
            final Locale newLocale,
            final boolean useContactsDict,
            final boolean usePersonalizedDicts,
            final boolean forceReloadMainDictionary,
            @Nullable final String account,
            final String dictNamePrefix,
            @Nullable final DictionaryInitializationListener listener) {
        final HashMap<Locale, ArrayList<String>> existingDictionariesToCleanup = new HashMap<>();
        // TODO: Make subDictTypesToUse configurable by resource or a static final list.
        final HashSet<String> subDictTypesToUse = new HashSet<>();
        subDictTypesToUse.add(Dictionary.TYPE_USER);

        // Do not use contacts dictionary if we do not have permissions to read contacts.
        final boolean contactsPermissionGranted = PermissionsUtil.checkAllPermissionsGranted(
                context, Manifest.permission.READ_CONTACTS);
        if (useContactsDict && contactsPermissionGranted) {
            subDictTypesToUse.add(Dictionary.TYPE_CONTACTS);
        }
        if (usePersonalizedDicts) {
            subDictTypesToUse.add(Dictionary.TYPE_USER_HISTORY);
        }

        // Gather all dictionaries. We'll remove them from the list to clean up later.
        final ArrayList<String> dictTypeForLocale = new ArrayList<>();
        existingDictionariesToCleanup.put(newLocale, dictTypeForLocale);
        final DictionaryGroup currentDictionaryGroupForLocale =
                findDictionaryGroupWithLocale(mDictionaryGroup, newLocale);
        if (currentDictionaryGroupForLocale != null) {
            for (final String dictType : DYNAMIC_DICTIONARY_TYPES) {
                if (currentDictionaryGroupForLocale.hasDict(dictType, account)) {
                    dictTypeForLocale.add(dictType);
                }
            }
            if (currentDictionaryGroupForLocale.hasDict(Dictionary.TYPE_MAIN, account)) {
                dictTypeForLocale.add(Dictionary.TYPE_MAIN);
            }
        }

        final DictionaryGroup dictionaryGroupForLocale =
                findDictionaryGroupWithLocale(mDictionaryGroup, newLocale);
        final ArrayList<String> dictTypesToCleanupForLocale =
                existingDictionariesToCleanup.get(newLocale);
        final boolean noExistingDictsForThisLocale = (null == dictionaryGroupForLocale);

        final Dictionary mainDict;
        if (forceReloadMainDictionary || noExistingDictsForThisLocale
                || !dictionaryGroupForLocale.hasDict(Dictionary.TYPE_MAIN, account)) {
            mainDict = null;
        } else {
            mainDict = dictionaryGroupForLocale.getDict(Dictionary.TYPE_MAIN);
            dictTypesToCleanupForLocale.remove(Dictionary.TYPE_MAIN);
        }

        final Map<String, ExpandableBinaryDictionary> subDicts = new HashMap<>();
        for (final String subDictType : subDictTypesToUse) {
            final ExpandableBinaryDictionary subDict;
            if (noExistingDictsForThisLocale
                    || !dictionaryGroupForLocale.hasDict(subDictType, account)) {
                // Create a new dictionary.
                subDict = getSubDict(subDictType, context, newLocale, null /* dictFile */,
                        dictNamePrefix, account);
            } else {
                // Reuse the existing dictionary, and don't close it at the end
                subDict = dictionaryGroupForLocale.getSubDict(subDictType);
                dictTypesToCleanupForLocale.remove(subDictType);
            }
            subDicts.put(subDictType, subDict);
        }
        DictionaryGroup newDictionaryGroup =
                new DictionaryGroup(newLocale, mainDict, account, subDicts);

        // create / load secondary dictionary
        final Locale secondaryLocale = Settings.getInstance().getCurrent().mSecondaryLocale;
        final DictionaryGroup newSecondaryDictionaryGroup;
        final Map<String, ExpandableBinaryDictionary> secondarySubDicts = new HashMap<>();

        if (secondaryLocale != null &&
                ScriptUtils.getScriptFromSpellCheckerLocale(secondaryLocale) == ScriptUtils.getScriptFromSpellCheckerLocale(newLocale)) {
            final ArrayList<String> dictTypesToCleanUp = new ArrayList<>();
            for (final String dictType : ALL_DICTIONARY_TYPES) {
                if (mSecondaryDictionaryGroup != null && mSecondaryDictionaryGroup.hasDict(dictType, account)) {
                    dictTypesToCleanUp.add(dictType);
                }
            }
            for (final String subDictType : subDictTypesToUse) {
                final ExpandableBinaryDictionary subDict =
                        getSubDict(subDictType, context, secondaryLocale, null, dictNamePrefix, account);
                secondarySubDicts.put(subDictType, subDict);
                dictTypesToCleanUp.remove(subDictType);
            }
            final Dictionary secondaryMainDict;
            if (forceReloadMainDictionary || findDictionaryGroupWithLocale(mSecondaryDictionaryGroup, secondaryLocale) == null
                    || !mSecondaryDictionaryGroup.hasDict(Dictionary.TYPE_MAIN, account)) {
                secondaryMainDict = null;
            } else {
                if (mSecondaryDictionaryGroup == null)
                    secondaryMainDict = null;
                else
                    secondaryMainDict = mSecondaryDictionaryGroup.getDict(Dictionary.TYPE_MAIN);
                dictTypesToCleanUp.remove(Dictionary.TYPE_MAIN);
            }
            newSecondaryDictionaryGroup = new DictionaryGroup(secondaryLocale, secondaryMainDict, account, secondarySubDicts);

            // do the cleanup like for main dict: look like this is for removing dictionaries
            // after user changed enabled types (e.g. disable personalized suggestions)
            existingDictionariesToCleanup.put(secondaryLocale, dictTypesToCleanUp);
        } else {
            newSecondaryDictionaryGroup = null;
        }

        // Replace Dictionaries.
        final DictionaryGroup oldDictionaryGroup;
        final DictionaryGroup oldSecondaryDictionaryGroup;
        synchronized (mLock) {
            oldDictionaryGroup = mDictionaryGroup;
            mDictionaryGroup = newDictionaryGroup;
            oldSecondaryDictionaryGroup = mSecondaryDictionaryGroup;
            mSecondaryDictionaryGroup = newSecondaryDictionaryGroup;
            if (hasAtLeastOneUninitializedMainDictionary()) {
                asyncReloadUninitializedMainDictionaries(context, newLocale,
                        mSecondaryDictionaryGroup == null ? null : secondaryLocale, listener);
            }
        }

        // load blacklists
        mDictionaryGroup.blacklistFileName = context.getFilesDir().getAbsolutePath() + File.separator + "blacklists" + File.separator + newLocale.toString().toLowerCase(Locale.ENGLISH) + ".txt";
        if (!new File(mDictionaryGroup.blacklistFileName).exists())
            new File(context.getFilesDir().getAbsolutePath() + File.separator + "blacklists").mkdirs();
        mDictionaryGroup.blacklist.addAll(readBlacklistFile(mDictionaryGroup.blacklistFileName));

        if (mSecondaryDictionaryGroup != null) {
            mSecondaryDictionaryGroup.blacklistFileName = context.getFilesDir().getAbsolutePath() + File.separator + "blacklists" + File.separator + secondaryLocale.toString().toLowerCase(Locale.ENGLISH) + ".txt";
            if (!new File(mSecondaryDictionaryGroup.blacklistFileName).exists())
                new File(context.getFilesDir().getAbsolutePath() + File.separator + "blacklists").mkdirs();
            mSecondaryDictionaryGroup.blacklist.addAll(readBlacklistFile(mSecondaryDictionaryGroup.blacklistFileName));
        }

        if (listener != null) {
            listener.onUpdateMainDictionaryAvailability(hasAtLeastOneInitializedMainDictionary());
        }

        // Clean up old dictionaries.
        for (final Locale localeToCleanUp : existingDictionariesToCleanup.keySet()) {
            final ArrayList<String> dictTypesToCleanUp =
                    existingDictionariesToCleanup.get(localeToCleanUp);
            DictionaryGroup dictionarySetToCleanup =
                    findDictionaryGroupWithLocale(oldDictionaryGroup, localeToCleanUp);
            if (dictionarySetToCleanup == null)
                dictionarySetToCleanup =
                        findDictionaryGroupWithLocale(oldSecondaryDictionaryGroup, localeToCleanUp);
            if (dictionarySetToCleanup == null)
                continue;
            for (final String dictType : dictTypesToCleanUp) {
                dictionarySetToCleanup.closeDict(dictType);
            }
        }

        if (mValidSpellingWordWriteCache != null) {
            mValidSpellingWordWriteCache.evictAll();
        }
    }

    private void asyncReloadUninitializedMainDictionaries(final Context context,
            final Locale locale, final Locale secondaryLocale, final DictionaryInitializationListener listener) {
        final CountDownLatch latchForWaitingLoadingMainDictionary = new CountDownLatch(1);
        mLatchForWaitingLoadingMainDictionaries = latchForWaitingLoadingMainDictionary;
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute(new Runnable() {
            @Override
            public void run() {
                doReloadUninitializedMainDictionaries(
                        context, locale, secondaryLocale, listener, latchForWaitingLoadingMainDictionary);
            }
        });
    }

    void doReloadUninitializedMainDictionaries(final Context context, final Locale locale,
            final Locale secondaryLocale, final DictionaryInitializationListener listener,
            final CountDownLatch latchForWaitingLoadingMainDictionary) {
        final DictionaryGroup dictionaryGroup =
                findDictionaryGroupWithLocale(mDictionaryGroup, locale);
        if (null == dictionaryGroup) {
            // This should never happen, but better safe than crashy
            Log.w(TAG, "Expected a dictionary group for " + locale + " but none found");
            return;
        }
        final Dictionary mainDict =
                DictionaryFactory.createMainDictionaryFromManager(context, locale);

        final DictionaryGroup secondaryDictionaryGroup;
        if (secondaryLocale == null)
            secondaryDictionaryGroup = null;
        else
            secondaryDictionaryGroup = findDictionaryGroupWithLocale(mSecondaryDictionaryGroup, secondaryLocale);
        final Dictionary secondaryMainDict;
        if (secondaryLocale == null)
            secondaryMainDict = null;
        else
            secondaryMainDict = DictionaryFactory.createMainDictionaryFromManager(context, secondaryLocale);

        synchronized (mLock) {
            if (locale.equals(dictionaryGroup.mLocale)) {
                dictionaryGroup.setMainDict(mainDict);
            } else {
                // Dictionary facilitator has been reset for another locale.
                mainDict.close();
            }
            if (secondaryDictionaryGroup != null && secondaryLocale.equals(secondaryDictionaryGroup.mLocale))
                secondaryDictionaryGroup.setMainDict(secondaryMainDict);
            else if (secondaryMainDict != null)
                secondaryMainDict.close();
        }
        if (listener != null) {
            listener.onUpdateMainDictionaryAvailability(hasAtLeastOneInitializedMainDictionary());
        }
        latchForWaitingLoadingMainDictionary.countDown();
    }

    @UsedForTesting
    public void resetDictionariesForTesting(final Context context, final Locale locale,
            final ArrayList<String> dictionaryTypes, final HashMap<String, File> dictionaryFiles,
            final Map<String, Map<String, String>> additionalDictAttributes,
            @Nullable final String account) {
        Dictionary mainDictionary = null;
        final Map<String, ExpandableBinaryDictionary> subDicts = new HashMap<>();

        for (final String dictType : dictionaryTypes) {
            if (dictType.equals(Dictionary.TYPE_MAIN)) {
                mainDictionary = DictionaryFactory.createMainDictionaryFromManager(context,
                        locale);
            } else {
                final File dictFile = dictionaryFiles.get(dictType);
                final ExpandableBinaryDictionary dict = getSubDict(
                        dictType, context, locale, dictFile, "" /* dictNamePrefix */, account);
                if (additionalDictAttributes.containsKey(dictType)) {
                    dict.clearAndFlushDictionaryWithAdditionalAttributes(
                            additionalDictAttributes.get(dictType));
                }
                if (dict == null) {
                    throw new RuntimeException("Unknown dictionary type: " + dictType);
                }
                dict.reloadDictionaryIfRequired();
                dict.waitAllTasksForTests();
                subDicts.put(dictType, dict);
            }
        }
        mDictionaryGroup = new DictionaryGroup(locale, mainDictionary, account, subDicts);
    }

    public void closeDictionaries() {
        final DictionaryGroup mainDictionaryGroupToClose;
        final DictionaryGroup secondaryDictionaryGroupToClose;
        synchronized (mLock) {
            mainDictionaryGroupToClose = mDictionaryGroup;
            secondaryDictionaryGroupToClose = mSecondaryDictionaryGroup;
            mDictionaryGroup = new DictionaryGroup();
            if (mSecondaryDictionaryGroup != null)
                mSecondaryDictionaryGroup = new DictionaryGroup();
        }
        for (final String dictType : ALL_DICTIONARY_TYPES) {
            mainDictionaryGroupToClose.closeDict(dictType);
            if (secondaryDictionaryGroupToClose != null)
                secondaryDictionaryGroupToClose.closeDict(dictType);
        }
    }

    @UsedForTesting
    public ExpandableBinaryDictionary getSubDictForTesting(final String dictName) {
        return mDictionaryGroup.getSubDict(dictName);
    }

    // The main dictionaries are loaded asynchronously.  Don't cache the return value
    // of these methods.
    public boolean hasAtLeastOneInitializedMainDictionary() {
        final Dictionary mainDict = mDictionaryGroup.getDict(Dictionary.TYPE_MAIN);
        return mainDict != null && mainDict.isInitialized();
    }

    public boolean hasAtLeastOneUninitializedMainDictionary() {
        final Dictionary mainDict = mDictionaryGroup.getDict(Dictionary.TYPE_MAIN);
        if (mSecondaryDictionaryGroup != null) {
            final Dictionary secondaryDict = mSecondaryDictionaryGroup.getDict(Dictionary.TYPE_MAIN);
            if (secondaryDict == null || !secondaryDict.isInitialized())
                return true;
        }
        return mainDict == null || !mainDict.isInitialized();
    }

    public void waitForLoadingMainDictionaries(final long timeout, final TimeUnit unit)
            throws InterruptedException {
        mLatchForWaitingLoadingMainDictionaries.await(timeout, unit);
    }

    @UsedForTesting
    public void waitForLoadingDictionariesForTesting(final long timeout, final TimeUnit unit)
            throws InterruptedException {
        waitForLoadingMainDictionaries(timeout, unit);
        for (final ExpandableBinaryDictionary dict : mDictionaryGroup.mSubDictMap.values()) {
            dict.waitAllTasksForTests();
        }
    }

    public void addToUserHistory(final String suggestion, final boolean wasAutoCapitalized,
            @Nonnull final NgramContext ngramContext, final long timeStampInSeconds,
            final boolean blockPotentiallyOffensive) {
        // Update the spelling cache before learning. Words that are not yet added to user history
        // and appear in no other language model are not considered valid.
        putWordIntoValidSpellingWordCache("addToUserHistory", suggestion);

        final String[] words = suggestion.split(Constants.WORD_SEPARATOR);

        // increase / decrease confidence if we have a secondary dictionary group
        Boolean validMainWord = null;
        Boolean validSecondaryWord = null;
        if (mSecondaryDictionaryGroup != null && words.length == 1) {
            // if suggestion was auto-capitalized, check against both the suggestion and the de-capitalized suggestion
            final String decapitalizedSuggestion;
            if (wasAutoCapitalized)
                decapitalizedSuggestion = suggestion.substring(0, 1).toLowerCase() + suggestion.substring(1);
            else
                decapitalizedSuggestion = suggestion;
            validMainWord = isValidWord(suggestion, ALL_DICTIONARY_TYPES, mDictionaryGroup);
            if ((wasAutoCapitalized && isValidWord(decapitalizedSuggestion, ALL_DICTIONARY_TYPES, mDictionaryGroup))
                || validMainWord)
                mDictionaryGroup.increaseConfidence();
            else mDictionaryGroup.decreaseConfidence();
            validSecondaryWord = isValidWord(suggestion, ALL_DICTIONARY_TYPES, mSecondaryDictionaryGroup);
            if ((wasAutoCapitalized && isValidWord(decapitalizedSuggestion, ALL_DICTIONARY_TYPES, mSecondaryDictionaryGroup))
                    || validSecondaryWord)
                mSecondaryDictionaryGroup.increaseConfidence();
            else mSecondaryDictionaryGroup.decreaseConfidence();
        }

        // add word to user dictionary if it is in no other dictionary except user history dictionary
        // reasoning: typing the same word again -> we probably want it in some dictionary permanently
        // we need a clearly preferred group to assign it to the correct language (in most cases at least...)
        if (mDictionaryGroup.hasDict(Dictionary.TYPE_USER_HISTORY, mDictionaryGroup.mAccount) // disable if personalized suggestions are off
                && Settings.getInstance().getCurrent().mAddToPersonalDictionary
                && (mSecondaryDictionaryGroup == null || mDictionaryGroup.mConfidence != mSecondaryDictionaryGroup.mConfidence)
                && !wasAutoCapitalized && words.length == 1) {
            addToPersonalDictionaryIfInvalidButInHistory(suggestion, validMainWord, validSecondaryWord);
        }

        NgramContext ngramContextForCurrentWord = ngramContext;
        for (int i = 0; i < words.length; i++) {
            final String currentWord = words[i];
            final boolean wasCurrentWordAutoCapitalized = (i == 0) && wasAutoCapitalized;
            // add to history for preferred dictionary group, to avoid mixing languages in history
            addWordToUserHistory(getCurrentlyPreferredDictionaryGroup(), ngramContextForCurrentWord, currentWord,
                    wasCurrentWordAutoCapitalized, (int) timeStampInSeconds,
                    blockPotentiallyOffensive);
            ngramContextForCurrentWord =
                    ngramContextForCurrentWord.getNextNgramContext(new WordInfo(currentWord));

            // remove entered words from blacklist
            if (mDictionaryGroup.blacklist.remove(currentWord))
                removeWordFromBlacklistFile(currentWord, mDictionaryGroup.blacklistFileName);
            if (mSecondaryDictionaryGroup != null && mSecondaryDictionaryGroup.blacklist.remove(currentWord))
                removeWordFromBlacklistFile(currentWord, mSecondaryDictionaryGroup.blacklistFileName);
        }
    }

    // main and secondary isValid provided to avoid duplicate lookups
    private void addToPersonalDictionaryIfInvalidButInHistory(String suggestion, Boolean validMainWord, Boolean validSecondaryWord) {
        // user history always reports words as invalid, so here we need to check isInDictionary instead
        // also maybe a problem: words added to dictionaries (user and history) are apparently found
        //  only after some delay. but this is not too bad, it just delays adding

        final DictionaryGroup dictionaryGroup = getCurrentlyPreferredDictionaryGroup();
        final ExpandableBinaryDictionary userDict = dictionaryGroup.getSubDict(Dictionary.TYPE_USER);
        final Dictionary userHistoryDict = dictionaryGroup.getSubDict(Dictionary.TYPE_USER_HISTORY);
        if (userDict != null && userHistoryDict.isInDictionary(suggestion)) {
            if (validMainWord == null)
                validMainWord = isValidWord(suggestion, ALL_DICTIONARY_TYPES, mDictionaryGroup);
            if (validMainWord)
                return;
            if (mSecondaryDictionaryGroup != null) {
                if (validSecondaryWord == null)
                    validSecondaryWord = isValidWord(suggestion, ALL_DICTIONARY_TYPES, mSecondaryDictionaryGroup);
                if (validSecondaryWord)
                    return;
            }
            if (userDict.isInDictionary(suggestion))
                return;
            ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute(new Runnable() {
                @Override
                public void run() {
                    UserDictionary.Words.addWord(userDict.mContext, suggestion,
                            250 /*FREQUENCY_FOR_USER_DICTIONARY_ADDS*/, null, dictionaryGroup.mLocale);
                }
            });
        }

    }

    private void putWordIntoValidSpellingWordCache(
            @Nonnull final String caller,
            @Nonnull final String originalWord) {
        if (mValidSpellingWordWriteCache == null) {
            return;
        }

        final String lowerCaseWord = originalWord.toLowerCase(getLocale());
        final boolean lowerCaseValid = isValidSpellingWord(lowerCaseWord);
        mValidSpellingWordWriteCache.put(lowerCaseWord, lowerCaseValid);

        final String capitalWord =
                StringUtils.capitalizeFirstAndDowncaseRest(originalWord, getLocale());
        final boolean capitalValid;
        if (lowerCaseValid) {
            // The lower case form of the word is valid, so the upper case must be valid.
            capitalValid = true;
        } else {
            capitalValid = isValidSpellingWord(capitalWord);
        }
        mValidSpellingWordWriteCache.put(capitalWord, capitalValid);
    }

    private void addWordToUserHistory(final DictionaryGroup dictionaryGroup,
            final NgramContext ngramContext, final String word, final boolean wasAutoCapitalized,
            final int timeStampInSeconds, final boolean blockPotentiallyOffensive) {
        final ExpandableBinaryDictionary userHistoryDictionary =
                dictionaryGroup.getSubDict(Dictionary.TYPE_USER_HISTORY);
        if (userHistoryDictionary == null || !hasLocale(userHistoryDictionary.mLocale)) {
            return;
        }
        final int maxFreq = getFrequency(word, dictionaryGroup);
        if (maxFreq == 0 && blockPotentiallyOffensive) {
            return;
        }
        final String secondWord;
        if (wasAutoCapitalized) {
            // used word with lower-case first letter instead of all lower-case, as auto-capitalize
            // does not affect the other letters
            final String decapitalizedWord = word.substring(0, 1).toLowerCase(dictionaryGroup.mLocale) + word.substring(1);
            if (isValidWord(word, ALL_DICTIONARY_TYPES, dictionaryGroup) && !isValidWord(decapitalizedWord, ALL_DICTIONARY_TYPES, dictionaryGroup)) {
                // If the word was auto-capitalized and exists only as a capitalized word in the
                // dictionary, then we must not downcase it before registering it. For example,
                // the name of the contacts in start-of-sentence position would come here with the
                // wasAutoCapitalized flag: if we downcase it, we'd register a lower-case version
                // of that contact's name which would end up popping in suggestions.
                secondWord = word;
            } else {
                // If however the word is not in the dictionary, or exists as a de-capitalized word
                // only, then we consider that was a lower-case word that had been auto-capitalized.
                secondWord = decapitalizedWord;
            }
        } else {
            // HACK: We'd like to avoid adding the capitalized form of common words to the User
            // History dictionary in order to avoid suggesting them until the dictionary
            // consolidation is done.
            // TODO: Remove this hack when ready.
            final String lowerCasedWord = word.toLowerCase(dictionaryGroup.mLocale);
            final int lowerCaseFreqInMainDict = dictionaryGroup.hasDict(Dictionary.TYPE_MAIN,
                    null /* account */) ?
                    dictionaryGroup.getDict(Dictionary.TYPE_MAIN).getFrequency(lowerCasedWord) :
                    Dictionary.NOT_A_PROBABILITY;
            if (maxFreq < lowerCaseFreqInMainDict
                    && lowerCaseFreqInMainDict >= CAPITALIZED_FORM_MAX_PROBABILITY_FOR_INSERT) {
                // Use lower cased word as the word can be a distracter of the popular word.
                secondWord = lowerCasedWord;
            } else {
                secondWord = word;
            }
        }
        // We demote unrecognized words (frequency < 0, below) by specifying them as "invalid".
        // We don't add words with 0-frequency (assuming they would be profanity etc.).
        final boolean isValid = maxFreq > 0;
        UserHistoryDictionary.addToDictionary(userHistoryDictionary, ngramContext, secondWord,
                isValid, timeStampInSeconds);
    }

    /** returns the dictionaryGroup with most confidence, main group when tied */
    private DictionaryGroup getCurrentlyPreferredDictionaryGroup() {
        final DictionaryGroup dictGroup;
        if (mSecondaryDictionaryGroup == null || mSecondaryDictionaryGroup.mConfidence <= mDictionaryGroup.mConfidence)
            dictGroup = mDictionaryGroup;
        else
            dictGroup = mSecondaryDictionaryGroup;
        return dictGroup;
    }

    private void removeWord(final String dictName, final String word) {
        final ExpandableBinaryDictionary dictionary = getCurrentlyPreferredDictionaryGroup().getSubDict(dictName);
        if (dictionary != null) {
            dictionary.removeUnigramEntryDynamically(word);
        }
    }

    @Override
    public void unlearnFromUserHistory(final String word,
            @Nonnull final NgramContext ngramContext, final long timeStampInSeconds,
            final int eventType) {
        // TODO: Decide whether or not to remove the word on EVENT_BACKSPACE.
        if (eventType != Constants.EVENT_BACKSPACE) {
            removeWord(Dictionary.TYPE_USER_HISTORY, word);
        }

        // Update the spelling cache after unlearning. Words that are removed from user history
        // and appear in no other language model are not considered valid.
        putWordIntoValidSpellingWordCache("unlearnFromUserHistory", word.toLowerCase());
    }

    // TODO: Revise the way to fusion suggestion results.
    @Override
    @Nonnull public SuggestionResults getSuggestionResults(ComposedData composedData,
            NgramContext ngramContext, @Nonnull final Keyboard keyboard,
            SettingsValuesForSuggestion settingsValuesForSuggestion, int sessionId,
            int inputStyle) {
        long proximityInfoHandle = keyboard.getProximityInfo().getNativeProximityInfo();
        final SuggestionResults suggestionResults = new SuggestionResults(
                SuggestedWords.MAX_SUGGESTIONS, ngramContext.isBeginningOfSentenceContext(),
                false /* firstSuggestionExceedsConfidenceThreshold */);
        final float[] weightOfLangModelVsSpatialModel =
                new float[] { Dictionary.NOT_A_WEIGHT_OF_LANG_MODEL_VS_SPATIAL_MODEL };

        // start getting suggestions for secondary locale first, but in separate thread
        final ArrayList<SuggestedWordInfo> dictionarySuggestionsSecondary = new ArrayList<>();
        final CountDownLatch waitForSecondaryDictionary = new CountDownLatch(1);
        if (mSecondaryDictionaryGroup != null) {
            ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute(new Runnable() {
                @Override
                public void run() {
                    dictionarySuggestionsSecondary.addAll(getSuggestions(composedData,
                            ngramContext, settingsValuesForSuggestion, sessionId, proximityInfoHandle,
                            weightOfLangModelVsSpatialModel, mSecondaryDictionaryGroup));
                    waitForSecondaryDictionary.countDown();
                }
            });
        }

        // get main locale suggestions
        final ArrayList<SuggestedWordInfo> dictionarySuggestions = getSuggestions(composedData,
                ngramContext, settingsValuesForSuggestion, sessionId, proximityInfoHandle,
                weightOfLangModelVsSpatialModel, mDictionaryGroup);
        suggestionResults.addAll(dictionarySuggestions);
        if (null != suggestionResults.mRawSuggestions) {
            suggestionResults.mRawSuggestions.addAll(dictionarySuggestions);
        }

        // wait for secondary locale suggestions
        if (mSecondaryDictionaryGroup != null) {
            try { waitForSecondaryDictionary.await(); }
            catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while trying to get secondary locale suggestions", e);
            }
            suggestionResults.addAll(dictionarySuggestionsSecondary);
            if (null != suggestionResults.mRawSuggestions) {
                suggestionResults.mRawSuggestions.addAll(dictionarySuggestionsSecondary);
            }
        }

        return suggestionResults;
    }

    private ArrayList<SuggestedWordInfo> getSuggestions(ComposedData composedData,
                NgramContext ngramContext, SettingsValuesForSuggestion settingsValuesForSuggestion,
                int sessionId, long proximityInfoHandle, float[] weightOfLangModelVsSpatialModel,
                DictionaryGroup dictGroup) {
        final ArrayList<SuggestedWordInfo> suggestions = new ArrayList<>();
        for (final String dictType : ALL_DICTIONARY_TYPES) {
            final Dictionary dictionary = dictGroup.getDict(dictType);
            if (null == dictionary) continue;
            final float weightForLocale = composedData.mIsBatchMode
                    ? dictGroup.mWeightForGesturingInLocale
                    : dictGroup.mWeightForTypingInLocale;
            final ArrayList<SuggestedWordInfo> dictionarySuggestions =
                    dictionary.getSuggestions(composedData, ngramContext,
                            proximityInfoHandle, settingsValuesForSuggestion, sessionId,
                            weightForLocale, weightOfLangModelVsSpatialModel);
            if (null == dictionarySuggestions) continue;

            // don't add blacklisted words
            // this may not be the most efficient way, but getting suggestions is much slower anyway
            for (SuggestedWordInfo info : dictionarySuggestions) {
                if (!isBlacklisted(info.getWord())) {
                    // for some reason, user history produces garbage words in batch mode
                    // this also happens for other dictionaries, but for those the score usually is much lower, so they are less visible
                    if (composedData.mIsBatchMode && dictType.equals(Dictionary.TYPE_USER_HISTORY) && !dictionary.isInDictionary(info.getWord()))
                        continue;
                    suggestions.add(info);
                }
             }
        }
        return suggestions;
    }

    // Spell checker is using this, and has its own instance of DictionaryFacilitatorImpl,
    // meaning that it always has default mConfidence. So we cannot choose to only check preferred
    // locale, and instead simply return true if word is in any of the available dictionaries
    public boolean isValidSpellingWord(final String word) {
        if (mValidSpellingWordReadCache != null) {
            final Boolean cachedValue = mValidSpellingWordReadCache.get(word);
            if (cachedValue != null) {
                return cachedValue;
            }
        }

        return isValidWord(word, ALL_DICTIONARY_TYPES, mDictionaryGroup) ||
                (mSecondaryDictionaryGroup != null && isValidWord(word, ALL_DICTIONARY_TYPES, mSecondaryDictionaryGroup));
    }

    public boolean isValidSuggestionWord(final String word) {
        return isValidWord(word, ALL_DICTIONARY_TYPES, mDictionaryGroup);
    }

    private boolean isValidWord(final String word, final String[] dictionariesToCheck, final DictionaryGroup dictionaryGroup) {
        if (TextUtils.isEmpty(word)) {
            return false;
        }
        if (dictionaryGroup.mLocale == null) {
            return false;
        }
        if (isBlacklisted(word)) return false;
        for (final String dictType : dictionariesToCheck) {
            final Dictionary dictionary = dictionaryGroup.getDict(dictType);
            // Ideally the passed map would come out of a {@link java.util.concurrent.Future} and
            // would be immutable once it's finished initializing, but concretely a null test is
            // probably good enough for the time being.
            if (null == dictionary) continue;
            if (dictionary.isValidWord(word)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlacklisted(final String word) {
        if (mDictionaryGroup.blacklist.contains(word))
            return true;
        if (mSecondaryDictionaryGroup != null && mSecondaryDictionaryGroup.blacklist.contains(word))
            return true;
        return false;
    }

    @Override
    public void removeWord(String word) {
        removeWordFromGroup(word, mDictionaryGroup);
        if (mSecondaryDictionaryGroup != null)
            removeWordFromGroup(word, mSecondaryDictionaryGroup);
    }

    private void removeWordFromGroup(String word, DictionaryGroup group) {
        // remove from user history
        final ExpandableBinaryDictionary historyDict = group.getSubDict(Dictionary.TYPE_USER_HISTORY);
        if (historyDict != null) {
            historyDict.removeUnigramEntryDynamically(word);
        }
        // and from personal dictionary
        final ExpandableBinaryDictionary userDict = group.getSubDict(Dictionary.TYPE_USER);
        if (userDict != null) {
            userDict.removeUnigramEntryDynamically(word);
        }

        // add to blacklist if in main dictionary
        if (group.getDict(Dictionary.TYPE_MAIN).isValidWord(word) && group.blacklist.add(word)) {
            // write to file if word wasn't already in blacklist
            ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        FileOutputStream fos = new FileOutputStream(group.blacklistFileName, true);
                        fos.write((word + "\n").getBytes(StandardCharsets.UTF_8));
                        fos.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Exception while trying to write blacklist", e);
                    }
                }
            });
        }
    }

    private ArrayList<String> readBlacklistFile(final String filename) {
        final ArrayList<String> blacklist = new ArrayList<>();
        if (filename == null) return blacklist;
        File blacklistFile = new File(filename);
        if (!blacklistFile.exists()) return blacklist;
        try {
            final Scanner scanner = new Scanner(blacklistFile, StandardCharsets.UTF_8.name()).useDelimiter("\n");
            while (scanner.hasNext()) {
                blacklist.add(scanner.next());
            }
        } catch (IOException e) {
            Log.e(TAG, "Exception while reading blacklist", e);
        }
        return blacklist;
    }

    private void removeWordFromBlacklistFile(String word, String filename) {
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute(new Runnable() {
            @Override
            public void run() {
                try {
                    ArrayList<String> blacklist = readBlacklistFile(filename);
                    blacklist.remove(word);
                    FileOutputStream fos = new FileOutputStream(filename);
                    for (String entry : blacklist) {
                        fos.write((entry + "\n").getBytes(StandardCharsets.UTF_8));
                    }
                    fos.close();
                } catch (IOException e) {
                    Log.e(TAG, "Exception while trying to write blacklist" + filename, e);
                }
            }
        });

    }

    // called from addWordToUserHistory with a specified dictionary, so provide this dictionary
    private int getFrequency(final String word, DictionaryGroup dictGroup) {
        if (TextUtils.isEmpty(word)) {
            return Dictionary.NOT_A_PROBABILITY;
        }
        int maxFreq = Dictionary.NOT_A_PROBABILITY;
        // ExpandableBinaryDictionary (means: all except main) always return NOT_A_PROBABILITY
        //  because it doesn't override getFrequency()
        // So why is it checked anyway?
        // Is this a bug, or intended by AOSP devs?
        for (final String dictType : ALL_DICTIONARY_TYPES) {
            final Dictionary dictionary = dictGroup.getDict(dictType);
            if (dictionary == null) continue;
            final int tempFreq = dictionary.getFrequency(word);
            if (tempFreq >= maxFreq) {
                maxFreq = tempFreq;
            }
        }
        return maxFreq;
    }

    private boolean clearSubDictionary(final String dictName) {
        final ExpandableBinaryDictionary dictionary = mDictionaryGroup.getSubDict(dictName);
        if (dictionary == null) {
            return false;
        }
        dictionary.clear();
        // called when not using personalized dictionaries, so should also reset secondary user history
        if (mSecondaryDictionaryGroup != null) {
            final ExpandableBinaryDictionary secondaryDictionary = mSecondaryDictionaryGroup.getSubDict(dictName);
            if (secondaryDictionary != null)
                secondaryDictionary.clear();
        }
        return true;
    }

    @Override
    public boolean clearUserHistoryDictionary(final Context context) {
        return clearSubDictionary(Dictionary.TYPE_USER_HISTORY);
    }

    @Override
    public void dumpDictionaryForDebug(final String dictName) {
        final ExpandableBinaryDictionary dictToDump = mDictionaryGroup.getSubDict(dictName);
        if (dictToDump == null) {
            Log.e(TAG, "Cannot dump " + dictName + ". "
                    + "The dictionary is not being used for suggestion or cannot be dumped.");
            return;
        }
        dictToDump.dumpAllWordsForDebug();
    }

    @Override
    @Nonnull public List<DictionaryStats> getDictionaryStats(final Context context) {
        final ArrayList<DictionaryStats> statsOfEnabledSubDicts = new ArrayList<>();
        for (final String dictType : DYNAMIC_DICTIONARY_TYPES) {
            final ExpandableBinaryDictionary dictionary = mDictionaryGroup.getSubDict(dictType);
            if (dictionary == null) continue;
            statsOfEnabledSubDicts.add(dictionary.getDictionaryStats());
        }
        return statsOfEnabledSubDicts;
    }

    @Override
    public String dump(final Context context) {
        return "";
    }
}
