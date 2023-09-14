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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

    private ArrayList<DictionaryGroup> mDictionaryGroups = new ArrayList<DictionaryGroup>() {{ add(new DictionaryGroup()); }};
    private volatile CountDownLatch mLatchForWaitingLoadingMainDictionaries = new CountDownLatch(0);
    // To synchronize assigning mDictionaryGroup to ensure closing dictionaries.
    private final Object mLock = new Object();
    // library does not deal well with ngram history for auto-capitalized words, so we adjust the ngram
    // context to store next word suggestions for such cases
    private boolean mTryChangingWords = false;
    private String mChangeFrom = "";
    private String mChangeTo = "";

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

    // todo: these caches are never even set, as the corresponding functions are not called...
    //  and even if they were set, one is only written, but never read, and the other one
    //  is only read and thus empty and useless -> why?
    //  anyway, we could just set the same cache using the set functions
    //  but before doing this, check the potential performance gains
    //   i.e. how long does a "isValidWord" check take -> on S4 mini 300 µs per dict if ok, but
    //   sometimes it can also be a few ms
    //   os if the spell checker is enabled, it's definitely reasonable to cache the results
    //   but this needs to be done internally, as it should be by language
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

    // judging by usage, this should check primary locale only
    @Override
    public boolean isForLocale(final Locale locale) {
        return locale != null && locale.equals(mDictionaryGroups.get(0).mLocale);
    }

    private boolean hasLocale(final Locale locale) {
        if (locale == null) return false;
        for (DictionaryGroup dictionaryGroup : mDictionaryGroups) {
            if (locale.equals(dictionaryGroup.mLocale)) return true;
        }
        return false;
    }

    /**
     * Returns whether this facilitator is exactly for this account.
     *
     * @param account the account to test against.
     */
    public boolean isForAccount(@Nullable final String account) {
        return TextUtils.equals(mDictionaryGroups.get(0).mAccount, account);
    }

    /**
     * A group of dictionaries that work together for a single language.
     */
    private static class DictionaryGroup {
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

        // todo: might need some more tuning, maybe more confidence steps
        private void updateWeights() {
            mWeightForTypingInLocale = 1f - 0.15f * (MAX_CONFIDENCE - mConfidence);
            mWeightForGesturingInLocale = 1f - 0.05f * (MAX_CONFIDENCE - mConfidence);
        }

        public float mWeightForTypingInLocale = 1f;
        public float mWeightForGesturingInLocale = 1f;
        public final ConcurrentHashMap<String, ExpandableBinaryDictionary> mSubDictMap =
                new ConcurrentHashMap<>();

        public DictionaryGroup() {
            this(null /* locale */, null /* mainDict */, null /* account */, Collections.emptyMap() /* subDicts */);
        }

        public DictionaryGroup(@Nullable final Locale locale,
                @Nullable final Dictionary mainDict,
                @Nullable final String account,
                @NonNull final Map<String, ExpandableBinaryDictionary> subDicts) {
            mLocale = locale;
            mAccount = account;
            // The main dictionary can be asynchronously loaded.
            setMainDict(mainDict);
            for (final Map.Entry<String, ExpandableBinaryDictionary> entry : subDicts.entrySet()) {
                setSubDict(entry.getKey(), entry.getValue());
            }
        }

        private void setSubDict(@NonNull final String dictType, @NonNull final ExpandableBinaryDictionary dict) {
            mSubDictMap.put(dictType, dict);
        }

        public void setMainDict(@Nullable final Dictionary mainDict) {
            // Close old dictionary if exists. Main dictionary can be assigned multiple times.
            final Dictionary oldDict = mMainDict;
            mMainDict = mainDict;
            if (oldDict != null && mainDict != oldDict) {
                oldDict.close();
            }
        }

        public @Nullable Dictionary getDict(@NonNull final String dictType) {
            if (Dictionary.TYPE_MAIN.equals(dictType)) {
                return mMainDict;
            }
            return getSubDict(dictType);
        }

        public @Nullable ExpandableBinaryDictionary getSubDict(@NonNull final String dictType) {
            return mSubDictMap.get(dictType);
        }

        public boolean hasDict(@NonNull final String dictType, @Nullable final String account) {
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

        public void closeDict(@NonNull final String dictType) {
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
        for (DictionaryGroup dictionaryGroup : mDictionaryGroups) {
            for (final String dictType : ALL_DICTIONARY_TYPES) {
                Dictionary dict = dictionaryGroup.getDict(dictType);
                if (dict != null) dict.onFinishInput();
            }
        }
    }

    @Override
    public boolean isActive() {
        return mDictionaryGroups.get(0).mLocale != null;
    }

    // used in
    //  putWordIntoValidSpellingWordCache -> should probably return most confidence locale, but the cache is not used anyway
    //  LatinIME.resetSuggestMainDict -> should return primary locale
    //  Suggest.getSuggestedWordsFor... -> should not matter if suggestions have a word locale, todo: check whether they do!
    //  InputLogic.getDictionaryFacilitatorLocale -> not sure, but probably doesn't matter
    @Override
    public Locale getLocale() {
        return mDictionaryGroups.get(0).mLocale;
    }

    @Override
    public Locale getCurrentLocale() {
        return getCurrentlyPreferredDictionaryGroup().mLocale;
    }

    @Override
    public boolean usesContacts() {
        return mDictionaryGroups.get(0).getSubDict(Dictionary.TYPE_CONTACTS) != null;
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
    static DictionaryGroup findDictionaryGroupWithLocale(final List<DictionaryGroup> dictionaryGroups,
            final Locale locale) {
        if (dictionaryGroups == null) return null;
        for (DictionaryGroup dictionaryGroup : dictionaryGroups) {
            if (locale == null && dictionaryGroup.mLocale == null)
                return dictionaryGroup;
            if (locale != null && locale.equals(dictionaryGroup.mLocale))
                return dictionaryGroup;
        }
        return null;
    }

    // original
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
        final List<Locale> allLocales = new ArrayList<Locale>() {{
            add(newLocale);
            addAll(Settings.getInstance().getCurrent().mSecondaryLocales);
        }};

        // Do not use contacts dictionary if we do not have permissions to read contacts.
        final boolean contactsPermissionGranted = PermissionsUtil.checkAllPermissionsGranted(
                context, Manifest.permission.READ_CONTACTS);
        if (useContactsDict && contactsPermissionGranted) {
            subDictTypesToUse.add(Dictionary.TYPE_CONTACTS);
        }
        if (usePersonalizedDicts) {
            subDictTypesToUse.add(Dictionary.TYPE_USER_HISTORY);
        }

        // Gather all dictionaries by locale. We may remove some from the list to clean up later.
        for (DictionaryGroup dictionaryGroup : mDictionaryGroups) {
            final ArrayList<String> dictTypeForLocale = new ArrayList<>();
            existingDictionariesToCleanup.put(dictionaryGroup.mLocale, dictTypeForLocale);

            for (final String dictType : DYNAMIC_DICTIONARY_TYPES) {
                if (dictionaryGroup.hasDict(dictType, account)) {
                    dictTypeForLocale.add(dictType);
                }
            }
            if (dictionaryGroup.hasDict(Dictionary.TYPE_MAIN, account)) {
                dictTypeForLocale.add(Dictionary.TYPE_MAIN);
            }
        }

        // create new dictionary groups and remove dictionaries to re-use from existingDictionariesToCleanup
        final ArrayList<DictionaryGroup> newDictionaryGroups = new ArrayList<>(allLocales.size());
        for (Locale locale : allLocales) {
            // get existing dictionary group for new locale
            final DictionaryGroup oldDictionaryGroupForLocale =
                    findDictionaryGroupWithLocale(mDictionaryGroups, locale);
            final ArrayList<String> dictTypesToCleanupForLocale =
                    existingDictionariesToCleanup.get(locale);
            final boolean noExistingDictsForThisLocale = (null == oldDictionaryGroupForLocale);

            // create new or re-use already loaded main dict
            final Dictionary mainDict;
            if (forceReloadMainDictionary || noExistingDictsForThisLocale
                    || !oldDictionaryGroupForLocale.hasDict(Dictionary.TYPE_MAIN, account)) {
                mainDict = null;
            } else {
                mainDict = oldDictionaryGroupForLocale.getDict(Dictionary.TYPE_MAIN);
                dictTypesToCleanupForLocale.remove(Dictionary.TYPE_MAIN);
            }

            // create new or re-use already loaded sub-dicts
            final Map<String, ExpandableBinaryDictionary> subDicts = new HashMap<>();
            for (final String subDictType : subDictTypesToUse) {
                final ExpandableBinaryDictionary subDict;
                if (noExistingDictsForThisLocale
                        || !oldDictionaryGroupForLocale.hasDict(subDictType, account)) {
                    // Create a new dictionary.
                    subDict = getSubDict(subDictType, context, locale, null /* dictFile */, dictNamePrefix, account);
                } else {
                    // Reuse the existing dictionary, and don't close it at the end
                    subDict = oldDictionaryGroupForLocale.getSubDict(subDictType);
                    dictTypesToCleanupForLocale.remove(subDictType);
                }
                subDicts.put(subDictType, subDict);
            }
            DictionaryGroup newDictGroup = new DictionaryGroup(locale, mainDict, account, subDicts);
            newDictionaryGroups.add(newDictGroup);

            // load blacklist
            if (noExistingDictsForThisLocale) {
                newDictGroup.blacklistFileName = context.getFilesDir().getAbsolutePath() + File.separator + "blacklists" + File.separator + locale.toString().toLowerCase(Locale.ENGLISH) + ".txt";
                if (!new File(newDictGroup.blacklistFileName).exists())
                    new File(context.getFilesDir().getAbsolutePath() + File.separator + "blacklists").mkdirs();
                newDictGroup.blacklist.addAll(readBlacklistFile(newDictGroup.blacklistFileName));
            } else {
                // re-use if possible
                newDictGroup.blacklistFileName = oldDictionaryGroupForLocale.blacklistFileName;
                newDictGroup.blacklist.addAll(oldDictionaryGroupForLocale.blacklist);
            }
        }


        // Replace Dictionaries.
        final List<DictionaryGroup> oldDictionaryGroups;
        synchronized (mLock) {
            oldDictionaryGroups = mDictionaryGroups;
            mDictionaryGroups = newDictionaryGroups;
            if (hasAtLeastOneUninitializedMainDictionary()) {
                asyncReloadUninitializedMainDictionaries(context, allLocales, listener);
            }
        }

        if (listener != null) {
            listener.onUpdateMainDictionaryAvailability(hasAtLeastOneInitializedMainDictionary());
        }

        // Clean up old dictionaries.
        for (final Locale localeToCleanUp : existingDictionariesToCleanup.keySet()) {
            final ArrayList<String> dictTypesToCleanUp =
                    existingDictionariesToCleanup.get(localeToCleanUp);
            final DictionaryGroup dictionarySetToCleanup =
                    findDictionaryGroupWithLocale(oldDictionaryGroups, localeToCleanUp);
            for (final String dictType : dictTypesToCleanUp) {
                dictionarySetToCleanup.closeDict(dictType);
            }
        }

        if (mValidSpellingWordWriteCache != null) {
            mValidSpellingWordWriteCache.evictAll();
        }
    }

    private void asyncReloadUninitializedMainDictionaries(final Context context,
            final List<Locale> locales, final DictionaryInitializationListener listener) {
        final CountDownLatch latchForWaitingLoadingMainDictionary = new CountDownLatch(1);
        mLatchForWaitingLoadingMainDictionaries = latchForWaitingLoadingMainDictionary;
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute(() ->
                doReloadUninitializedMainDictionaries(context, locales, listener, latchForWaitingLoadingMainDictionary));
    }

    void doReloadUninitializedMainDictionaries(final Context context, final List<Locale> locales,
            final DictionaryInitializationListener listener,
            final CountDownLatch latchForWaitingLoadingMainDictionary) {
        final Dictionary[] mainDicts = new Dictionary[locales.size()];
        final ArrayList<DictionaryGroup> dictionaryGroups = new ArrayList<>();
        for (int i = 0; i < locales.size(); i++) {
            Locale locale = locales.get(i);
            DictionaryGroup dictionaryGroup = findDictionaryGroupWithLocale(mDictionaryGroups, locale);
            if (null == dictionaryGroup) {
                // This should never happen, but better safe than crashy
                Log.w(TAG, "Expected a dictionary group for " + locale + " but none found");
                return;
            }
            dictionaryGroups.add(dictionaryGroup);
            // do nothing if main dict already initialized
            if (dictionaryGroup.mMainDict != null && dictionaryGroup.mMainDict.isInitialized()) {
                mainDicts[i] = null;
                continue;
            }
            mainDicts[i] = DictionaryFactory.createMainDictionaryFromManager(context, dictionaryGroup.mLocale);
        }

        synchronized (mLock) {
            for (int i = 0; i < locales.size(); i++) {
                final Locale locale = locales.get(i);
                if (mainDicts[i] == null)
                    continue;
                if (locale.equals(dictionaryGroups.get(i).mLocale)) {
                    dictionaryGroups.get(i).setMainDict(mainDicts[i]);
                } else {
                    // Dictionary facilitator has been reset for another locale.
                    mainDicts[i].close();
                }
            }
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
        mDictionaryGroups.clear();
        mDictionaryGroups.add(new DictionaryGroup(locale, mainDictionary, account, subDicts));
    }

    public void closeDictionaries() {
        final ArrayList<DictionaryGroup> dictionaryGroupsToClose;
        synchronized (mLock) {
            dictionaryGroupsToClose = new ArrayList<>(mDictionaryGroups);
            mDictionaryGroups.clear();
            mDictionaryGroups.add(new DictionaryGroup());
        }
        for (DictionaryGroup dictionaryGroup : dictionaryGroupsToClose) {
            for (final String dictType : ALL_DICTIONARY_TYPES) {
                dictionaryGroup.closeDict(dictType);
            }
        }
    }

    @UsedForTesting
    public ExpandableBinaryDictionary getSubDictForTesting(final String dictName) {
        return mDictionaryGroups.get(0).getSubDict(dictName);
    }

    // The main dictionaries are loaded asynchronously.  Don't cache the return value
    // of these methods.
    public boolean hasAtLeastOneInitializedMainDictionary() {
        for (DictionaryGroup dictionaryGroup : mDictionaryGroups) {
            final Dictionary mainDict = dictionaryGroup.getDict(Dictionary.TYPE_MAIN);
            if (mainDict != null && mainDict.isInitialized()) return true;
        }
        return false;
    }

    public boolean hasAtLeastOneUninitializedMainDictionary() {
        for (DictionaryGroup dictionaryGroup : mDictionaryGroups) {
            final Dictionary mainDict = dictionaryGroup.getDict(Dictionary.TYPE_MAIN);
            if (mainDict == null || !mainDict.isInitialized()) return true;
        }
        return false;
    }

    public void waitForLoadingMainDictionaries(final long timeout, final TimeUnit unit)
            throws InterruptedException {
        mLatchForWaitingLoadingMainDictionaries.await(timeout, unit);
    }

    @UsedForTesting
    public void waitForLoadingDictionariesForTesting(final long timeout, final TimeUnit unit)
            throws InterruptedException {
        waitForLoadingMainDictionaries(timeout, unit);
        for (final ExpandableBinaryDictionary dict : mDictionaryGroups.get(0).mSubDictMap.values()) {
            dict.waitAllTasksForTests();
        }
    }

    public void addToUserHistory(final String suggestion, final boolean wasAutoCapitalized,
            @NonNull final NgramContext ngramContext, final long timeStampInSeconds,
            final boolean blockPotentiallyOffensive) {
        // Update the spelling cache before learning. Words that are not yet added to user history
        // and appear in no other language model are not considered valid.
        putWordIntoValidSpellingWordCache("addToUserHistory", suggestion);

        final String[] words = suggestion.split(Constants.WORD_SEPARATOR);

        // increase / decrease confidence if we have more than one dictionary group
        boolean[] validWordForDictionary; // store results to avoid unnecessary duplicate lookups
        if (mDictionaryGroups.size() > 1 && words.length == 1) {
            validWordForDictionary = new boolean[mDictionaryGroups.size()];
            for (int i = 0; i < mDictionaryGroups.size(); i ++) {
                final DictionaryGroup dictionaryGroup = mDictionaryGroups.get(i);
                final boolean isValidWord = isValidWord(suggestion, ALL_DICTIONARY_TYPES, dictionaryGroup);
                // if suggestion was auto-capitalized, check against both the suggestion and the de-capitalized suggestion
                if (isValidWord
                        || (wasAutoCapitalized
                        && isValidWord(StringUtils.decapitalizeFirstCodePoint(suggestion, dictionaryGroup.mLocale), ALL_DICTIONARY_TYPES, dictionaryGroup)
                ))
                    dictionaryGroup.increaseConfidence();
                else dictionaryGroup.decreaseConfidence();
                validWordForDictionary[i] = isValidWord;
            }
        } else
            validWordForDictionary = null;

        // add word to user dictionary if it is in no other dictionary except user history dictionary,
        // reasoning: typing the same word again -> we probably want it in some dictionary permanently
        if (mDictionaryGroups.get(0).hasDict(Dictionary.TYPE_USER_HISTORY, mDictionaryGroups.get(0).mAccount) // require personalized suggestions to be on
                && Settings.getInstance().getCurrent().mAddToPersonalDictionary // ...and the setting
                && !wasAutoCapitalized && words.length == 1) {
            addToPersonalDictionaryIfInvalidButInHistory(suggestion, validWordForDictionary);
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

            // remove manually entered blacklisted words from blacklist
            for (DictionaryGroup dictionaryGroup : mDictionaryGroups) {
                if (dictionaryGroup.blacklist.remove(currentWord))
                    removeWordFromBlacklistFile(currentWord, dictionaryGroup.blacklistFileName);
            }
        }
    }

    // main and secondary isValid provided to avoid duplicate lookups
    private void addToPersonalDictionaryIfInvalidButInHistory(String suggestion, boolean[] validWordForDictionary) {
        // we need one clearly preferred group to assign it to the correct language
        int highestGroup = -1;
        // require confidence to be MAX_CONFIDENCE, to be sure about language
        // since the word is unknown, confidence has already been reduced, but after a first miss
        // confidence is actually reduced to MAX_CONFIDENCE if it was larger
        int highestGroupConfidence = DictionaryGroup.MAX_CONFIDENCE - 1;
        for (int i = 0; i < mDictionaryGroups.size(); i ++) {
            final DictionaryGroup dictionaryGroup = mDictionaryGroups.get(i);
            if (dictionaryGroup.mConfidence > highestGroupConfidence) {
                highestGroup = i;
                highestGroupConfidence = dictionaryGroup.mConfidence;
            } else if (dictionaryGroup.mConfidence == highestGroupConfidence) {
                highestGroup = -1;
            }
        }
        // no preferred group or word is valid -> do nothing
        if (highestGroup == -1) return;
        final DictionaryGroup dictionaryGroup = mDictionaryGroups.get(highestGroup);
        if (validWordForDictionary == null
                ? isValidWord(suggestion, ALL_DICTIONARY_TYPES, dictionaryGroup)
                : validWordForDictionary[highestGroup]
        )
            return;

        final ExpandableBinaryDictionary userDict = dictionaryGroup.getSubDict(Dictionary.TYPE_USER);
        final Dictionary userHistoryDict = dictionaryGroup.getSubDict(Dictionary.TYPE_USER_HISTORY);
        // user history always reports words as invalid, so here we need to check isInDictionary instead
        // also maybe a problem: words added to dictionaries (user and history) are apparently found
        //  only after some delay. but this is not too bad, it just delays adding
        if (userDict != null && userHistoryDict.isInDictionary(suggestion)) {
            if (userDict.isInDictionary(suggestion)) // is this check necessary?
                return;
            ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute(() ->
                    UserDictionary.Words.addWord(userDict.mContext, suggestion,
                    250 /*FREQUENCY_FOR_USER_DICTIONARY_ADDS*/, null, dictionaryGroup.mLocale));
        }

    }

    private void putWordIntoValidSpellingWordCache(
            @NonNull final String caller,
            @NonNull final String originalWord) {
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
        if (mTryChangingWords)
            mTryChangingWords = ngramContext.changeWordIfAfterBeginningOfSentence(mChangeFrom, mChangeTo);
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
                mTryChangingWords = true;
                mChangeFrom = word;
                mChangeTo = secondWord;
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

    /** returns the dictionaryGroup with most confidence, first group when tied */
    private DictionaryGroup getCurrentlyPreferredDictionaryGroup() {
        DictionaryGroup dictGroup = null;
        int highestConfidence = -1;
        for (DictionaryGroup dictionaryGroup : mDictionaryGroups) {
            if (dictionaryGroup.mConfidence > highestConfidence) {
                dictGroup = dictionaryGroup;
                highestConfidence = dictGroup.mConfidence;
            }
        }
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
            @NonNull final NgramContext ngramContext, final long timeStampInSeconds,
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
    @SuppressWarnings("unchecked")
    @NonNull public SuggestionResults getSuggestionResults(ComposedData composedData,
            NgramContext ngramContext, @NonNull final Keyboard keyboard,
            SettingsValuesForSuggestion settingsValuesForSuggestion, int sessionId,
            int inputStyle) {
        long proximityInfoHandle = keyboard.getProximityInfo().getNativeProximityInfo();
        final SuggestionResults suggestionResults = new SuggestionResults(
                SuggestedWords.MAX_SUGGESTIONS, ngramContext.isBeginningOfSentenceContext(),
                false /* firstSuggestionExceedsConfidenceThreshold */);
        final float[] weightOfLangModelVsSpatialModel =
                new float[] { Dictionary.NOT_A_WEIGHT_OF_LANG_MODEL_VS_SPATIAL_MODEL };

        // start getting suggestions for non-main locales first, but in background
        final ArrayList<SuggestedWordInfo>[] otherDictionarySuggestions = (ArrayList<SuggestedWordInfo>[]) new ArrayList[mDictionaryGroups.size() - 1];
        final CountDownLatch waitForOtherDictionaries;
        if (mDictionaryGroups.size() > 1) {
            waitForOtherDictionaries = new CountDownLatch(mDictionaryGroups.size() - 1);
            for (int i = 1; i < mDictionaryGroups.size(); i ++) {
                final DictionaryGroup dictionaryGroup = mDictionaryGroups.get(i);
                final int index = i - 1;
                ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute(() -> {
                    otherDictionarySuggestions[index] = getSuggestions(composedData,
                            ngramContext, settingsValuesForSuggestion, sessionId, proximityInfoHandle,
                            weightOfLangModelVsSpatialModel, dictionaryGroup);
                    waitForOtherDictionaries.countDown();
                });
            }
        } else
            waitForOtherDictionaries = null;

        // get main locale suggestions
        final ArrayList<SuggestedWordInfo> dictionarySuggestions = getSuggestions(composedData,
                ngramContext, settingsValuesForSuggestion, sessionId, proximityInfoHandle,
                weightOfLangModelVsSpatialModel, mDictionaryGroups.get(0));
        suggestionResults.addAll(dictionarySuggestions);
        if (null != suggestionResults.mRawSuggestions) {
            suggestionResults.mRawSuggestions.addAll(dictionarySuggestions);
        }

        // wait for other locale suggestions
        if (waitForOtherDictionaries != null) {
            try { waitForOtherDictionaries.await(); }
            catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while trying to get secondary locale suggestions", e);
            }
            for (int i = 1; i < mDictionaryGroups.size(); i ++) {
                suggestionResults.addAll(otherDictionarySuggestions[i - 1]);
                if (null != suggestionResults.mRawSuggestions) {
                    suggestionResults.mRawSuggestions.addAll(otherDictionarySuggestions[i - 1]);
                }
            }
        }

        return suggestionResults;
    }

    private ArrayList<SuggestedWordInfo> getSuggestions(ComposedData composedData,
                NgramContext ngramContext, SettingsValuesForSuggestion settingsValuesForSuggestion,
                int sessionId, long proximityInfoHandle, float[] weightOfLangModelVsSpatialModel,
                DictionaryGroup dictGroup) {
        final ArrayList<SuggestedWordInfo> suggestions = new ArrayList<>();
        final float weightForLocale = composedData.mIsBatchMode
                ? dictGroup.mWeightForGesturingInLocale
                : dictGroup.mWeightForTypingInLocale;
        for (final String dictType : ALL_DICTIONARY_TYPES) {
            final Dictionary dictionary = dictGroup.getDict(dictType);
            if (null == dictionary) continue;
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
        for (DictionaryGroup dictionaryGroup : mDictionaryGroups) {
            if (isValidWord(word, ALL_DICTIONARY_TYPES, dictionaryGroup))
                return true;
        }
        return false;
    }

    // this is unused, so leave it for now (redirecting to isValidWord seems to defeat the purpose...)
    public boolean isValidSuggestionWord(final String word) {
        return isValidWord(word, ALL_DICTIONARY_TYPES, mDictionaryGroups.get(0));
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
        for (DictionaryGroup dictionaryGroup : mDictionaryGroups) {
            if (dictionaryGroup.blacklist.contains(word))
                return true;
        }
        return false;
    }

    @Override
    public void removeWord(String word) {
        for (DictionaryGroup dictionaryGroup : mDictionaryGroups) {
            removeWordFromGroup(word, dictionaryGroup);
        }
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

        final ExpandableBinaryDictionary contactsDict = group.getSubDict(Dictionary.TYPE_CONTACTS);
        final boolean isInContacts;
        if (contactsDict != null) {
            isInContacts = contactsDict.isInDictionary(word);
            if (isInContacts)
                contactsDict.removeUnigramEntryDynamically(word); // will be gone until next reload of dict
        } else isInContacts = false;

        // add to blacklist if in main or contacts dictionaries
        if ((isInContacts || group.getDict(Dictionary.TYPE_MAIN).isValidWord(word)) && group.blacklist.add(word)) {
            // write to file if word wasn't already in blacklist
            ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute(() -> {
                try {
                    FileOutputStream fos = new FileOutputStream(group.blacklistFileName, true);
                    fos.write((word + "\n").getBytes(StandardCharsets.UTF_8));
                    fos.close();
                } catch (IOException e) {
                    Log.e(TAG, "Exception while trying to write blacklist", e);
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
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute(() -> {
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
        for (DictionaryGroup dictionaryGroup : mDictionaryGroups) {
            final ExpandableBinaryDictionary dictionary = dictionaryGroup.getSubDict(dictName);
            if (dictionary == null) {
                return false; // should only ever happen for primary dictionary, so this is safe
            }
            dictionary.clear();
        }
        return true;
    }

    @Override
    public boolean clearUserHistoryDictionary(final Context context) {
        return clearSubDictionary(Dictionary.TYPE_USER_HISTORY);
    }

    @Override
    public void dumpDictionaryForDebug(final String dictName) {
        final ExpandableBinaryDictionary dictToDump = mDictionaryGroups.get(0).getSubDict(dictName);
        if (dictToDump == null) {
            Log.e(TAG, "Cannot dump " + dictName + ". "
                    + "The dictionary is not being used for suggestion or cannot be dumped.");
            return;
        }
        dictToDump.dumpAllWordsForDebug();
    }

    @Override
    // this is unused, so leave it for now
    @NonNull public List<DictionaryStats> getDictionaryStats(final Context context) {
        final ArrayList<DictionaryStats> statsOfEnabledSubDicts = new ArrayList<>();
        for (final String dictType : DYNAMIC_DICTIONARY_TYPES) {
            final ExpandableBinaryDictionary dictionary = mDictionaryGroups.get(0).getSubDict(dictType);
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
