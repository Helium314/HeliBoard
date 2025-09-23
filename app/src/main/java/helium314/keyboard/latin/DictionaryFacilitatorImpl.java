/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin;

import android.Manifest;
import android.content.Context;
import android.provider.UserDictionary;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.inputmethod.InputMethodSubtype;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import helium314.keyboard.keyboard.Keyboard;
import helium314.keyboard.latin.NgramContext.WordInfo;
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo;
import helium314.keyboard.latin.common.ComposedData;
import helium314.keyboard.latin.common.Constants;
import helium314.keyboard.latin.common.StringUtils;
import helium314.keyboard.latin.common.StringUtilsKt;
import helium314.keyboard.latin.permissions.PermissionsUtil;
import helium314.keyboard.latin.personalization.UserHistoryDictionary;
import helium314.keyboard.latin.settings.Settings;
import helium314.keyboard.latin.settings.SettingsValues;
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion;
import helium314.keyboard.latin.utils.ExecutorUtils;
import helium314.keyboard.latin.utils.KtxKt;
import helium314.keyboard.latin.utils.Log;
import helium314.keyboard.latin.utils.SubtypeSettings;
import helium314.keyboard.latin.utils.SubtypeUtilsKt;
import helium314.keyboard.latin.utils.SuggestionResults;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
 * <p>
 * Currently AndroidSpellCheckerService and LatinIME both use DictionaryFacilitator as
 * a client for interacting with dictionaries.
 */
public class DictionaryFacilitatorImpl implements DictionaryFacilitator {
    public static final String TAG = DictionaryFacilitatorImpl.class.getSimpleName();

    // HACK: This threshold is being used when adding a capitalized entry in the User History
    // dictionary.
    private static final int CAPITALIZED_FORM_MAX_PROBABILITY_FOR_INSERT = 140;

    private ArrayList<DictionaryGroup> mDictionaryGroups = new ArrayList<>() {{ add(new DictionaryGroup()); }};
    private volatile CountDownLatch mLatchForWaitingLoadingMainDictionaries = new CountDownLatch(0);
    // To synchronize assigning mDictionaryGroup to ensure closing dictionaries.
    private final Object mLock = new Object();
    // library does not deal well with ngram history for auto-capitalized words, so we adjust the ngram
    // context to store next word suggestions for such cases
    private boolean mTryChangingWords = false;
    private String mChangeFrom = "";
    private String mChangeTo = "";

    // todo: write cache never set, and never read (only written)
    //  tried to use read cache for a while, but small performance improvements are not worth the work (https://github.com/Helium314/SociaKeyboard/issues/307)
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
     * A group of dictionaries that work together for a single language.
     */
    private static class DictionaryGroup {
        private static final int MAX_CONFIDENCE = 2;

        /**
         * The locale associated with the dictionary group.
         */
        @NonNull public final Locale mLocale;

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
        }

        // If confidence is above max, drop to max confidence. This does not change weights and
        // allows conveniently typing single words from the other language without affecting suggestions
        public void decreaseConfidence() {
            if (mConfidence > MAX_CONFIDENCE)
                mConfidence = MAX_CONFIDENCE;
            else if (mConfidence > 0) {
                mConfidence -= 1;
            }
        }

        public float getWeightForTypingInLocale(List<DictionaryGroup> groups) {
            return getWeightForLocale(groups, 0.15f);
        }

        public float getWeightForGesturingInLocale(List<DictionaryGroup> groups) {
            return getWeightForLocale(groups, 0.05f);
        }

        // might need some more tuning
        private float getWeightForLocale(final List<DictionaryGroup> groups, final float step) {
            if (groups.size() == 1) return 1f;
            if (mConfidence < 2) return 1f - step * (MAX_CONFIDENCE - mConfidence);
            for (DictionaryGroup group : groups) {
                if (group != this && group.mConfidence >= mConfidence) return 1f - step / 2f;
            }
            return 1f;
        }
        public final ConcurrentHashMap<String, ExpandableBinaryDictionary> mSubDictMap =
                new ConcurrentHashMap<>();

        public DictionaryGroup() {
            this(new Locale(""), null, null, Collections.emptyMap());
        }

        public DictionaryGroup(@NonNull final Locale locale,
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
        return !mDictionaryGroups.get(0).mLocale.getLanguage().isEmpty();
    }

    @Override
    @NonNull
    public Locale getMainLocale() {
        return mDictionaryGroups.get(0).mLocale;
    }

    @Override
    public Locale getCurrentLocale() {
        return getCurrentlyPreferredDictionaryGroup().mLocale;
    }

    public boolean usesContacts() {
        return mDictionaryGroups.get(0).getSubDict(Dictionary.TYPE_CONTACTS) != null;
    }

    public boolean usesPersonalization() {
        return mDictionaryGroups.get(0).getSubDict(Dictionary.TYPE_USER_HISTORY) != null;
    }

    @Override
    public String getAccount() {
        return null;
    }

    @Override
    public boolean usesSameSettings(@NonNull final List<Locale> locales, final boolean contacts,
            final boolean personalization, @Nullable final String account) {
        final boolean first = usesContacts() == contacts && usesPersonalization() == personalization
                && TextUtils.equals(mDictionaryGroups.get(0).mAccount, account)
                && locales.size() == mDictionaryGroups.size();
        if (!first) return false;
        for (int i = 0; i < locales.size(); i++) {
            if (locales.get(i) != mDictionaryGroups.get(i).mLocale) return false;
        }
        return true;
    }

    @Nullable
    private static ExpandableBinaryDictionary getSubDict(final String dictType,
            final Context context, final Locale locale, final File dictFile,
            final String dictNamePrefix, @Nullable final String account) {
        ExpandableBinaryDictionary dict = null;
        try {
            dict = switch (dictType) {
                case Dictionary.TYPE_USER_HISTORY -> UserHistoryDictionary.getDictionary(context, locale, dictFile, dictNamePrefix, account);
                case Dictionary.TYPE_USER -> UserBinaryDictionary.getDictionary(context, locale, dictFile, dictNamePrefix, account);
                case Dictionary.TYPE_CONTACTS -> ContactsBinaryDictionary.getDictionary(context, locale, dictFile, dictNamePrefix, account);
                default -> null;
            };
        } catch (final SecurityException | IllegalArgumentException e) {
            Log.e(TAG, "Cannot create dictionary: " + dictType, e);
        }
        if (dict == null)
            Log.e(TAG, "Cannot create dictionary for " + dictType);
        return dict;
    }

    @Nullable
    static DictionaryGroup findDictionaryGroupWithLocale(final List<DictionaryGroup> dictionaryGroups,
            @NonNull final Locale locale) {
        if (dictionaryGroups == null) return null;
        for (DictionaryGroup dictionaryGroup : dictionaryGroups) {
            if (locale.equals(dictionaryGroup.mLocale))
                return dictionaryGroup;
        }
        return null;
    }

    // original
    public void resetDictionaries(
            final Context context,
            @NonNull final Locale newLocale,
            final boolean useContactsDict,
            final boolean usePersonalizedDicts,
            final boolean forceReloadMainDictionary,
            @Nullable final String account,
            final String dictNamePrefix,
            @Nullable final DictionaryInitializationListener listener) {
        final HashMap<Locale, ArrayList<String>> existingDictionariesToCleanup = new HashMap<>();
        final HashSet<String> subDictTypesToUse = new HashSet<>();
        subDictTypesToUse.add(Dictionary.TYPE_USER);
        Log.i(TAG, "resetDictionaries, force reloading main dictionary: " + forceReloadMainDictionary);
        final List<Locale> allLocales = new ArrayList<>() {{
            add(newLocale);

            // adding secondary locales is a bit tricky since they depend on the subtype
            // but usually this is called with the selected subtype locale
            final InputMethodSubtype selected = SubtypeSettings.INSTANCE.getSelectedSubtype(KtxKt.prefs(context));
            if (SubtypeUtilsKt.locale(selected).equals(newLocale)) {
                addAll(SubtypeUtilsKt.getSecondaryLocales(selected.getExtraValue()));
            } else {
                // probably we're called from the spell checker when using a different app as keyboard
                final List<InputMethodSubtype> enabled = SubtypeSettings.INSTANCE.getEnabledSubtypes(false);
                for (InputMethodSubtype subtype : enabled) {
                    if (SubtypeUtilsKt.locale(subtype).equals(newLocale))
                        addAll(SubtypeUtilsKt.getSecondaryLocales(subtype.getExtraValue()));
                }
            }
        }};

        // Do not use contacts dictionary if we do not have permissions to read contacts.
        if (useContactsDict
                && PermissionsUtil.checkAllPermissionsGranted(context, Manifest.permission.READ_CONTACTS)) {
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
            final DictionaryGroup oldDictionaryGroupForLocale = findDictionaryGroupWithLocale(mDictionaryGroups, locale);
            final ArrayList<String> dictTypesToCleanupForLocale = existingDictionariesToCleanup.get(locale);
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
                if (noExistingDictsForThisLocale || forceReloadMainDictionary
                        || !oldDictionaryGroupForLocale.hasDict(subDictType, account)) {
                    // Create a new dictionary.
                    subDict = getSubDict(subDictType, context, locale, null, dictNamePrefix, account);
                    if (subDict == null) continue; // https://github.com/Helium314/SociaKeyboard/issues/293
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
                newDictGroup.blacklistFileName = context.getFilesDir().getAbsolutePath() + File.separator + "blacklists" + File.separator + locale.toLanguageTag() + ".txt";
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
            final ArrayList<String> dictTypesToCleanUp = existingDictionariesToCleanup.get(localeToCleanUp);
            final DictionaryGroup dictionarySetToCleanup = findDictionaryGroupWithLocale(oldDictionaryGroups, localeToCleanUp);
            for (final String dictType : dictTypesToCleanUp) {
                dictionarySetToCleanup.closeDict(dictType);
            }
        }

        if (mValidSpellingWordWriteCache != null) {
            mValidSpellingWordWriteCache.evictAll();
        }
        if (mValidSpellingWordReadCache != null) {
            mValidSpellingWordReadCache.evictAll();
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
            mainDicts[i] = DictionaryFactoryKt.createMainDictionary(context, dictionaryGroup.mLocale);
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

    public void addToUserHistory(final String suggestion, final boolean wasAutoCapitalized,
            @NonNull final NgramContext ngramContext, final long timeStampInSeconds,
            final boolean blockPotentiallyOffensive) {
        // Update the spelling cache before learning. Words that are not yet added to user history
        // and appear in no other language model are not considered valid.
        putWordIntoValidSpellingWordCache("addToUserHistory", suggestion);

        final String[] words = suggestion.split(Constants.WORD_SEPARATOR);

        // increase / decrease confidence if we have more than one dictionary group
        boolean[] validWordForDictionary; // store results to avoid unnecessary duplicate lookups
        if (mDictionaryGroups.size() > 1 && words.length == 1) { // ignore if more than a single word, this only happens with (badly working) spaceAwareGesture
            validWordForDictionary = adjustConfidencesInternal(suggestion, wasAutoCapitalized);
        } else
            validWordForDictionary = null;

        // add word to user dictionary if it is in no other dictionary except user history dictionary,
        // reasoning: typing the same word again -> we probably want it in some dictionary permanently
        final SettingsValues sv = Settings.getValues();
        if (sv.mAddToPersonalDictionary // require the setting
                && sv.mAutoCorrectEnabled == sv.mAutoCorrectionEnabledPerUserSettings // don't add if user wants autocorrect but input field does not, see https://github.com/Helium314/SociaKeyboard/issues/427#issuecomment-1905438000
                && mDictionaryGroups.get(0).hasDict(Dictionary.TYPE_USER_HISTORY, mDictionaryGroups.get(0).mAccount) // require personalized suggestions
                && !wasAutoCapitalized // we can't be 100% sure about what the user intended to type, so better don't add it
                && words.length == 1) { // ignore if more than a single word, this only happens with (badly working) spaceAwareGesture
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

    @Override public void adjustConfidences(final String word, final boolean wasAutoCapitalized) {
        if (mDictionaryGroups.size() > 1 && !word.contains(Constants.WORD_SEPARATOR))
            adjustConfidencesInternal(word, wasAutoCapitalized);
    }

    private boolean[] adjustConfidencesInternal(final String word, final boolean wasAutoCapitalized) {
        final boolean[] validWordForDictionary = new boolean[mDictionaryGroups.size()];
        // if suggestion was auto-capitalized, check against both the suggestion and the de-capitalized suggestion
        final String decapitalizedSuggestion;
        if (wasAutoCapitalized)
            decapitalizedSuggestion = StringUtilsKt.decapitalize(word, getCurrentLocale());
        else
            decapitalizedSuggestion = word;
        for (int i = 0; i < mDictionaryGroups.size(); i ++) {
            final DictionaryGroup dictionaryGroup = mDictionaryGroups.get(i);
            final boolean isValidWord = isValidWord(word, ALL_DICTIONARY_TYPES, dictionaryGroup);
            if (isValidWord || (wasAutoCapitalized && isValidWord(decapitalizedSuggestion, ALL_DICTIONARY_TYPES, dictionaryGroup)))
                dictionaryGroup.increaseConfidence();
            else dictionaryGroup.decreaseConfidence();
            validWordForDictionary[i] = isValidWord;
        }
        return validWordForDictionary;
    }

    // main and secondary isValid provided to avoid duplicate lookups
    private void addToPersonalDictionaryIfInvalidButInHistory(String word, boolean[] validWordForDictionary) {
        final DictionaryGroup dictionaryGroup = getClearlyPreferredDictionaryGroupOrNull();
        if (dictionaryGroup == null) return;
        if (validWordForDictionary == null
                ? isValidWord(word, ALL_DICTIONARY_TYPES, dictionaryGroup)
                : validWordForDictionary[mDictionaryGroups.indexOf(dictionaryGroup)]
        )
            return;

        final ExpandableBinaryDictionary userDict = dictionaryGroup.getSubDict(Dictionary.TYPE_USER);
        final Dictionary userHistoryDict = dictionaryGroup.getSubDict(Dictionary.TYPE_USER_HISTORY);
        if (userDict == null || userHistoryDict == null) return;

        // user history always reports words as invalid, so here we need to check isInDictionary instead
        //  update: now getFrequency returns the correct value instead of -1, so better use that
        //  a little testing shows that after 2 times adding, the frequency is 111, and then rises slowly with usage
        //  120 is after 3 uses of the word, so we simply require more than that.
        // also maybe a problem: words added to dictionaries (user and history) are apparently found
        //  only after some delay. but this is not too bad, it just delays adding
        if (userHistoryDict.getFrequency(word) > 120) {
            if (userDict.isInDictionary(word)) // is this check necessary?
                return;
            ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute(() ->
                    UserDictionary.Words.addWord(userDict.mContext, word,
                    250 /*FREQUENCY_FOR_USER_DICTIONARY_ADDS*/, null, dictionaryGroup.mLocale));
        }
    }

    private void putWordIntoValidSpellingWordCache(
            @NonNull final String caller,
            @NonNull final String originalWord) {
        if (mValidSpellingWordWriteCache == null) {
            return;
        }

        final String lowerCaseWord = originalWord.toLowerCase(getCurrentLocale());
        final boolean lowerCaseValid = isValidSpellingWord(lowerCaseWord);
        mValidSpellingWordWriteCache.put(lowerCaseWord, lowerCaseValid);

        final String capitalWord =
                StringUtils.capitalizeFirstAndDowncaseRest(originalWord, getCurrentLocale());
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
        final int mainFreq = dictionaryGroup.hasDict(Dictionary.TYPE_MAIN, null)
                ? dictionaryGroup.getDict(Dictionary.TYPE_MAIN).getFrequency(word)
                : Dictionary.NOT_A_PROBABILITY;
        if (mainFreq == 0 && blockPotentiallyOffensive) {
            return;
        }
        if (mTryChangingWords)
            mTryChangingWords = ngramContext.changeWordIfAfterBeginningOfSentence(mChangeFrom, mChangeTo);
        final String secondWord;
        // check for isBeginningOfSentenceContext too, because not all text fields auto-capitalize in this case
        // and even if the user capitalizes manually, they most likely don't want the capitalized form suggested
        if (wasAutoCapitalized || ngramContext.isBeginningOfSentenceContext()) {
            // used word with lower-case first letter instead of all lower-case, as auto-capitalize
            // does not affect the other letters
            final String decapitalizedWord = StringUtilsKt.decapitalize(word, dictionaryGroup.mLocale);
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
            final int lowerCaseFreqInMainDict = dictionaryGroup.hasDict(Dictionary.TYPE_MAIN, null)
                    ? dictionaryGroup.getDict(Dictionary.TYPE_MAIN).getFrequency(lowerCasedWord)
                    : Dictionary.NOT_A_PROBABILITY;
            if (mainFreq < lowerCaseFreqInMainDict
                    && lowerCaseFreqInMainDict >= CAPITALIZED_FORM_MAX_PROBABILITY_FOR_INSERT) {
                // Use lower cased word as the word can be a distracter of the popular word.
                secondWord = lowerCasedWord;
            } else {
                secondWord = word;
            }
        }
        // We demote unrecognized words (frequency < 0, below) by specifying them as "invalid".
        // We don't add words with 0-frequency (assuming they would be profanity etc.).
        // comment: so this means words not in main dict are always invalid... weird (but still works)
        final boolean isValid = mainFreq > 0;
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

    private DictionaryGroup getClearlyPreferredDictionaryGroupOrNull() {
        // we want one clearly preferred group and return null otherwise
        if (mDictionaryGroups.size() == 1)
            return mDictionaryGroups.get(0);
        // that preferred group should have at least MAX_CONFIDENCE, and all others should have 0 (we want to be really sure!)
        int preferredGroup = -1;
        for (int i = 0; i < mDictionaryGroups.size(); i ++) {
            final DictionaryGroup dictionaryGroup = mDictionaryGroups.get(i);
            if (dictionaryGroup.mConfidence == 0) continue;
            if (dictionaryGroup.mConfidence >= DictionaryGroup.MAX_CONFIDENCE && preferredGroup == -1) {
                preferredGroup = i;
                continue;
            }
            // either we have 2 groups with high confidence, or a group with low but non-0 confidence
            // in either case, we're not sure enough and return null
            return null;
        }
        if (preferredGroup == -1) return null;
        return mDictionaryGroups.get(preferredGroup);
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
        float weightForLocale = composedData.mIsBatchMode
                ? dictGroup.getWeightForGesturingInLocale(mDictionaryGroups)
                : dictGroup.getWeightForTypingInLocale(mDictionaryGroups);
        for (final String dictType : ALL_DICTIONARY_TYPES) {
            final Dictionary dictionary = dictGroup.getDict(dictType);
            if (null == dictionary) continue;
            final ArrayList<SuggestedWordInfo> dictionarySuggestions =
                    dictionary.getSuggestions(composedData, ngramContext,
                            proximityInfoHandle, settingsValuesForSuggestion, sessionId,
                            weightForLocale, weightOfLangModelVsSpatialModel);
            if (null == dictionarySuggestions) continue;

            // for some reason, garbage words are produced when glide typing
            // for user history and main dictionary we can filter them out by checking whether the
            // dictionary actually contains the word
            // but personal dictionary and addon dictionaries may contain shortcuts, which do not
            // pass an isInDictionary check (e.g. emojis)
            // (if the main dict contains shortcuts to non-words, this will break)
            final boolean checkForGarbage = composedData.mIsBatchMode && (dictType.equals(Dictionary.TYPE_USER_HISTORY) || dictType.equals(Dictionary.TYPE_MAIN));
            for (SuggestedWordInfo info : dictionarySuggestions) {
                final String word = info.getWord();
                if (!isBlacklisted(word)) { // don't add blacklisted words
                    if (checkForGarbage
                            // only check history and "main main dictionary"
                            // consider the user might use custom main dictionary containing shortcuts
                            //  assume this is unlikely to happen, and take care about common shortcuts that are not actual words (emoji, symbols)
                            && word.length() > 2 // should exclude most symbol shortcuts
                            && info.mSourceDict.mDictType.equals(dictType) // dictType is always main, but info.mSourceDict.mDictType contains the actual dict (main dict is a dictionary group)
                            && !StringUtils.mightBeEmoji(word) // emojis often have more than 2 chars; simplified check for performance reasons
                            && !dictionary.isInDictionary(word))
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
        boolean result = false;
        for (DictionaryGroup dictionaryGroup : mDictionaryGroups) {
            if (isValidWord(word, ALL_DICTIONARY_TYPES, dictionaryGroup)) {
                result = true;
                break;
            }
        }
        if (mValidSpellingWordReadCache != null)
            mValidSpellingWordReadCache.put(word, result);
        return result;
    }

    // this is unused, so leave it for now (redirecting to isValidWord seems to defeat the purpose...)
    public boolean isValidSuggestionWord(final String word) {
        return isValidWord(word, ALL_DICTIONARY_TYPES, mDictionaryGroups.get(0));
    }

    private boolean isValidWord(final String word, final String[] dictionariesToCheck, final DictionaryGroup dictionaryGroup) {
        if (TextUtils.isEmpty(word)) {
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
        if (contactsDict != null) {
            if (contactsDict.isInDictionary(word)) {
                contactsDict.removeUnigramEntryDynamically(word); // will be gone until next reload of dict
                addToBlacklist(word, group);
                return;
            }
        }
        if (!group.hasDict(Dictionary.TYPE_MAIN, null))
            return;
        if (group.getDict(Dictionary.TYPE_MAIN).isValidWord(word)) {
            addToBlacklist(word, group);
            return;
        }
        final String lowercase = word.toLowerCase(group.mLocale);
        if (group.getDict(Dictionary.TYPE_MAIN).isValidWord(lowercase)) {
            addToBlacklist(lowercase, group);
        }
    }

    private void addToBlacklist(final String word, final DictionaryGroup group) {
        if (!group.blacklist.add(word))
            return;
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

    @Override
    public boolean clearUserHistoryDictionary(final Context context) {
        for (DictionaryGroup dictionaryGroup : mDictionaryGroups) {
            final ExpandableBinaryDictionary dictionary = dictionaryGroup.getSubDict(Dictionary.TYPE_USER_HISTORY);
            if (dictionary == null) {
                return false; // should only ever happen for primary dictionary, so this is safe
            }
            dictionary.clear();
        }
        return true;
    }

    @Override
    public String localesAndConfidences() {
        if (mDictionaryGroups.size() < 2) return null;
        final StringBuilder sb = new StringBuilder();
        for (final DictionaryGroup dictGroup : mDictionaryGroups) {
            if (sb.length() > 0)
                sb.append(", ");
            sb.append(dictGroup.mLocale).append(" ").append(dictGroup.mConfidence);
        }
        return sb.toString();
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
