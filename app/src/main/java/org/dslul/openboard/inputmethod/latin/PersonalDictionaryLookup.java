/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.UserDictionary;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.dslul.openboard.inputmethod.annotations.UsedForTesting;
import org.dslul.openboard.inputmethod.latin.common.CollectionUtils;
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils;
import org.dslul.openboard.inputmethod.latin.define.DebugFlags;
import org.dslul.openboard.inputmethod.latin.utils.ExecutorUtils;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class provides the ability to look into the system-wide "Personal dictionary". It loads the
 * data once when created and reloads it when notified of changes to {@link UserDictionary}
 *
 * It can be used directly to validate words or expand shortcuts, and it can be used by instances
 * of {@link PersonalLanguageModelHelper} that create language model files for a specific input
 * locale.
 *
 * Note, that the initial dictionary loading happens asynchronously so it is possible (hopefully
 * rarely) that {@link #isValidWord} or {@link #expandShortcut} is called before the initial load
 * has started.
 *
 * The caller should explicitly call {@link #close} when the object is no longer needed, in order
 * to release any resources and references to this object.  A service should create this object in
 * {@link android.app.Service#onCreate} and close it in {@link android.app.Service#onDestroy}.
 */
public class PersonalDictionaryLookup implements Closeable {

    /**
     * To avoid loading too many dictionary entries in memory, we cap them at this number.  If
     * that number is exceeded, the lowest-frequency items will be dropped.  Note, there is no
     * explicit cap on the number of locales in every entry.
     */
    private static final int MAX_NUM_ENTRIES = 1000;

    /**
     * The delay (in milliseconds) to impose on reloads.  Previously scheduled reloads will be
     * cancelled if a new reload is scheduled before the delay expires.  Thus, only the last
     * reload in the series of frequent reloads will execute.
     *
     * Note, this value should be low enough to allow the "Add to dictionary" feature in the
     * TextView correction (red underline) drop-down menu to work properly in the following case:
     *
     *   1. User types OOV (out-of-vocabulary) word.
     *   2. The OOV is red-underlined.
     *   3. User selects "Add to dictionary".  The red underline disappears while the OOV is
     *      in a composing span.
     *   4. The user taps space.  The red underline should NOT reappear.  If this value is very
     *      high and the user performs the space tap fast enough, the red underline may reappear.
     */
    @UsedForTesting
    static final int RELOAD_DELAY_MS = 200;

    @UsedForTesting
    static final Locale ANY_LOCALE = new Locale("");

    private final String mTag;
    private final ContentResolver mResolver;
    private final String mServiceName;

    /**
     * Interface to implement for classes interested in getting notified of updates.
     */
    public interface PersonalDictionaryListener {
        void onUpdate();
    }

    private final Set<PersonalDictionaryListener> mListeners = new HashSet<>();

    public void addListener(@NonNull final PersonalDictionaryListener listener) {
        mListeners.add(listener);
    }

    public void removeListener(@NonNull final PersonalDictionaryListener listener) {
        mListeners.remove(listener);
    }

    /**
     * Broadcast the update to all the Locale-specific language models.
     */
    @UsedForTesting
    void notifyListeners() {
        for (PersonalDictionaryListener listener : mListeners) {
            listener.onUpdate();
        }
    }

    /**
     *  Content observer for changes to the personal dictionary. It has the following properties:
     *    1. It spawns off a reload in another thread, after some delay.
     *    2. It cancels previously scheduled reloads, and only executes the latest.
     *    3. It may be called multiple times quickly in succession (and is in fact called so
     *       when the dictionary is edited through its settings UI, when sometimes multiple
     *       notifications are sent for the edited entry, but also for the entire dictionary).
     */
    private class PersonalDictionaryContentObserver extends ContentObserver implements Runnable {
        public PersonalDictionaryContentObserver() {
            super(null);
        }

        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        // Support pre-API16 platforms.
        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (DebugFlags.DEBUG_ENABLED) {
                Log.d(mTag, "onChange() : URI = " + uri);
            }
            // Cancel (but don't interrupt) any pending reloads (except the initial load).
            if (mReloadFuture != null && !mReloadFuture.isCancelled() &&
                    !mReloadFuture.isDone()) {
                // Note, that if already cancelled or done, this will do nothing.
                boolean isCancelled = mReloadFuture.cancel(false);
                if (DebugFlags.DEBUG_ENABLED) {
                    if (isCancelled) {
                        Log.d(mTag, "onChange() : Canceled previous reload request");
                    } else {
                        Log.d(mTag, "onChange() : Failed to cancel previous reload request");
                    }
                }
            }

            if (DebugFlags.DEBUG_ENABLED) {
                Log.d(mTag, "onChange() : Scheduling reload in " + RELOAD_DELAY_MS + " ms");
            }

            // Schedule a new reload after RELOAD_DELAY_MS.
            mReloadFuture = ExecutorUtils.getBackgroundExecutor(mServiceName)
                    .schedule(this, RELOAD_DELAY_MS, TimeUnit.MILLISECONDS);
        }

        @Override
        public void run() {
            loadPersonalDictionary();
        }
    }

    private final PersonalDictionaryContentObserver mPersonalDictionaryContentObserver =
            new PersonalDictionaryContentObserver();

    /**
     * Indicates that a load is in progress, so no need for another.
     */
    private AtomicBoolean mIsLoading = new AtomicBoolean(false);

    /**
     * Indicates that this lookup object has been close()d.
     */
    private AtomicBoolean mIsClosed = new AtomicBoolean(false);

    /**
     * We store a map from a dictionary word to the set of locales & raw string(as it appears)
     * We then iterate over the set of locales to find a match using LocaleUtils.
     */
    private volatile HashMap<String, HashMap<Locale, String>> mDictWords;

    /**
     * We store a map from a shortcut to a word for each locale.
     * Shortcuts that apply to any locale are keyed by {@link #ANY_LOCALE}.
     */
    private volatile HashMap<Locale, HashMap<String, String>> mShortcutsPerLocale;

    /**
     *  The last-scheduled reload future.  Saved in order to cancel a pending reload if a new one
     * is coming.
     */
    private volatile ScheduledFuture<?> mReloadFuture;

    private volatile List<DictionaryStats> mDictionaryStats;

    /**
     * @param context the context from which to obtain content resolver
     */
    public PersonalDictionaryLookup(
            @NonNull final Context context,
            @NonNull final String serviceName) {
        mTag = serviceName + ".Personal";

        Log.i(mTag, "create()");

        mServiceName = serviceName;
        mDictionaryStats = new ArrayList<DictionaryStats>();
        mDictionaryStats.add(new DictionaryStats(ANY_LOCALE, Dictionary.TYPE_USER, 0));
        mDictionaryStats.add(new DictionaryStats(ANY_LOCALE, Dictionary.TYPE_USER_SHORTCUT, 0));

        // Obtain a content resolver.
        mResolver = context.getContentResolver();
    }

    public List<DictionaryStats> getDictionaryStats() {
        return mDictionaryStats;
    }

    public void open() {
        Log.i(mTag, "open()");

        // Schedule the initial load to run immediately.  It's possible that the first call to
        // isValidWord occurs before the dictionary has actually loaded, so it should not
        // assume that the dictionary has been loaded.
        loadPersonalDictionary();

        // Register the observer to be notified on changes to the personal dictionary and all
        // individual items.
        //
        // If the user is interacting with the Personal Dictionary settings UI, or with the
        // "Add to dictionary" drop-down option, duplicate notifications will be sent for the same
        // edit: if a new entry is added, there is a notification for the entry itself, and
        // separately for the entire dictionary. However, when used programmatically,
        // only notifications for the specific edits are sent. Thus, the observer is registered to
        // receive every possible notification, and instead has throttling logic to avoid doing too
        // many reloads.
        mResolver.registerContentObserver(
                UserDictionary.Words.CONTENT_URI,
                true /* notifyForDescendents */,
                mPersonalDictionaryContentObserver);
    }

    /**
     * To be called by the garbage collector in the off chance that the service did not clean up
     * properly.  Do not rely on this getting called, and make sure close() is called explicitly.
     */
    @Override
    public void finalize() throws Throwable {
        try {
            if (DebugFlags.DEBUG_ENABLED) {
                Log.d(mTag, "finalize()");
            }
            close();
        } finally {
            super.finalize();
        }
    }

    /**
     * Cleans up PersonalDictionaryLookup: shuts down any extra threads and unregisters the observer.
     *
     * It is safe, but not advised to call this multiple times, and isValidWord would continue to
     * work, but no data will be reloaded any longer.
     */
    @Override
    public void close() {
        if (DebugFlags.DEBUG_ENABLED) {
            Log.d(mTag, "close() : Unregistering content observer");
        }
        if (mIsClosed.compareAndSet(false, true)) {
            // Unregister the content observer.
            mResolver.unregisterContentObserver(mPersonalDictionaryContentObserver);
        }
    }

    /**
     * Returns true if the initial load has been performed.
     *
     * @return true if the initial load is successful
     */
    public boolean isLoaded() {
        return mDictWords != null && mShortcutsPerLocale != null;
    }

    /**
     * Returns the set of words defined for the given locale and more general locales.
     *
     * For example, input locale en_US uses data for en_US, en, and the global dictionary.
     *
     * Note that this method returns expanded words, not shortcuts. Shortcuts are handled
     * by {@link #getShortcutsForLocale}.
     *
     * @param inputLocale the locale to restrict for
     * @return set of words that apply to the given locale.
     */
    public Set<String> getWordsForLocale(@NonNull final Locale inputLocale) {
        final HashMap<String, HashMap<Locale, String>> dictWords = mDictWords;
        if (CollectionUtils.isNullOrEmpty(dictWords)) {
            return Collections.emptySet();
        }

        final Set<String> words = new HashSet<>();
        final String inputLocaleString = inputLocale.toString();
        for (String word : dictWords.keySet()) {
            HashMap<Locale, String> localeStringMap = dictWords.get(word);
                if (!CollectionUtils.isNullOrEmpty(localeStringMap)) {
                    for (Locale wordLocale : localeStringMap.keySet()) {
                        final String wordLocaleString = wordLocale.toString();
                        final int match = LocaleUtils.getMatchLevel(wordLocaleString, inputLocaleString);
                        if (LocaleUtils.isMatch(match)) {
                            words.add(localeStringMap.get(wordLocale));
                        }
                    }
            }
        }
        return words;
    }

    /**
     * Returns the set of shortcuts defined for the given locale and more general locales.
     *
     * For example, input locale en_US uses data for en_US, en, and the global dictionary.
     *
     * Note that this method returns shortcut keys, not expanded words. Words are handled
     * by {@link #getWordsForLocale}.
     *
     * @param inputLocale the locale to restrict for
     * @return set of shortcuts that apply to the given locale.
     */
    public Set<String> getShortcutsForLocale(@NonNull final Locale inputLocale) {
        final Map<Locale, HashMap<String, String>> shortcutsPerLocale = mShortcutsPerLocale;
        if (CollectionUtils.isNullOrEmpty(shortcutsPerLocale)) {
            return Collections.emptySet();
        }

        final Set<String> shortcuts = new HashSet<>();
        if (!TextUtils.isEmpty(inputLocale.getCountry())) {
            // First look for the country-specific shortcut: en_US, en_UK, fr_FR, etc.
            final Map<String, String> countryShortcuts = shortcutsPerLocale.get(inputLocale);
            if (!CollectionUtils.isNullOrEmpty(countryShortcuts)) {
                shortcuts.addAll(countryShortcuts.keySet());
            }
        }

        // Next look for the language-specific shortcut: en, fr, etc.
        final Locale languageOnlyLocale =
                LocaleUtils.constructLocaleFromString(inputLocale.getLanguage());
        final Map<String, String> languageShortcuts = shortcutsPerLocale.get(languageOnlyLocale);
        if (!CollectionUtils.isNullOrEmpty(languageShortcuts)) {
            shortcuts.addAll(languageShortcuts.keySet());
        }

        // If all else fails, look for a global shortcut.
        final Map<String, String> globalShortcuts = shortcutsPerLocale.get(ANY_LOCALE);
        if (!CollectionUtils.isNullOrEmpty(globalShortcuts)) {
            shortcuts.addAll(globalShortcuts.keySet());
        }

        return shortcuts;
    }

    /**
     * Determines if the given word is a valid word in the given locale based on the dictionary.
     * It tries hard to find a match: for example, casing is ignored and if the word is present in a
     * more general locale (e.g. en or all locales), and isValidWord is asking for a more specific
     * locale (e.g. en_US), it will be considered a match.
     *
     * @param word the word to match
     * @param inputLocale the locale in which to match the word
     * @return true iff the word has been matched for this locale in the dictionary.
     */
    public boolean isValidWord(@NonNull final String word, @NonNull final Locale inputLocale) {
        if (!isLoaded()) {
            // This is a corner case in the event the initial load of the dictionary has not
            // completed. In that case, we assume the word is not a valid word in the dictionary.
            if (DebugFlags.DEBUG_ENABLED) {
                Log.d(mTag, "isValidWord() : Initial load not complete");
            }
            return false;
        }

        if (DebugFlags.DEBUG_ENABLED) {
            Log.d(mTag, "isValidWord() : Word [" + word + "] in Locale [" + inputLocale + "]");
        }
        // Atomically obtain the current copy of mDictWords;
        final HashMap<String, HashMap<Locale, String>> dictWords = mDictWords;
        // Lowercase the word using the given locale. Note, that dictionary
        // words are lowercased using their locale, and theoretically the
        // lowercasing between two matching locales may differ. For simplicity
        // we ignore that possibility.
        final String lowercased = word.toLowerCase(inputLocale);
        final HashMap<Locale, String> dictLocales = dictWords.get(lowercased);

        if (CollectionUtils.isNullOrEmpty(dictLocales)) {
            if (DebugFlags.DEBUG_ENABLED) {
                Log.d(mTag, "isValidWord() : No entry for word [" + word + "]");
            }
            return false;
        } else {
            if (DebugFlags.DEBUG_ENABLED) {
                Log.d(mTag, "isValidWord() : Found entry for word [" + word + "]");
            }
            // Iterate over the locales this word is in.
            for (final Locale dictLocale : dictLocales.keySet()) {
                final int matchLevel = LocaleUtils.getMatchLevel(dictLocale.toString(),
                        inputLocale.toString());
                if (DebugFlags.DEBUG_ENABLED) {
                    Log.d(mTag, "isValidWord() : MatchLevel for DictLocale [" + dictLocale
                            + "] and InputLocale [" + inputLocale + "] is " + matchLevel);
                }
                if (LocaleUtils.isMatch(matchLevel)) {
                    if (DebugFlags.DEBUG_ENABLED) {
                        Log.d(mTag, "isValidWord() : MatchLevel " + matchLevel + " IS a match");
                    }
                    return true;
                }
                if (DebugFlags.DEBUG_ENABLED) {
                    Log.d(mTag, "isValidWord() : MatchLevel " + matchLevel + " is NOT a match");
                }
            }
            if (DebugFlags.DEBUG_ENABLED) {
                Log.d(mTag, "isValidWord() : False, since none of the locales matched");
            }
            return false;
        }
    }

    /**
     * Expands the given shortcut for the given locale.
     *
     * @param shortcut the shortcut to expand
     * @param inputLocale the locale in which to expand the shortcut
     * @return expanded shortcut iff the word is a shortcut in the dictionary.
     */
    @Nullable public String expandShortcut(
            @NonNull final String shortcut, @NonNull final Locale inputLocale) {
        if (DebugFlags.DEBUG_ENABLED) {
            Log.d(mTag, "expandShortcut() : Shortcut [" + shortcut + "] for [" + inputLocale + "]");
        }

        // Atomically obtain the current copy of mShortcuts;
        final HashMap<Locale, HashMap<String, String>> shortcutsPerLocale = mShortcutsPerLocale;

        // Exit as early as possible. Most users don't use shortcuts.
        if (CollectionUtils.isNullOrEmpty(shortcutsPerLocale)) {
            if (DebugFlags.DEBUG_ENABLED) {
                Log.d(mTag, "expandShortcut() : User has no shortcuts");
            }
            return null;
        }

        if (!TextUtils.isEmpty(inputLocale.getCountry())) {
            // First look for the country-specific shortcut: en_US, en_UK, fr_FR, etc.
            final String expansionForCountry = expandShortcut(
                    shortcutsPerLocale, shortcut, inputLocale);
            if (!TextUtils.isEmpty(expansionForCountry)) {
                if (DebugFlags.DEBUG_ENABLED) {
                    Log.d(mTag, "expandShortcut() : Country expansion is ["
                            + expansionForCountry + "]");
                }
                return expansionForCountry;
            }
        }

        // Next look for the language-specific shortcut: en, fr, etc.
        final Locale languageOnlyLocale =
                LocaleUtils.constructLocaleFromString(inputLocale.getLanguage());
        final String expansionForLanguage = expandShortcut(
                shortcutsPerLocale, shortcut, languageOnlyLocale);
        if (!TextUtils.isEmpty(expansionForLanguage)) {
            if (DebugFlags.DEBUG_ENABLED) {
                Log.d(mTag, "expandShortcut() : Language expansion is ["
                        + expansionForLanguage + "]");
            }
            return expansionForLanguage;
        }

        // If all else fails, look for a global shortcut.
        final String expansionForGlobal = expandShortcut(shortcutsPerLocale, shortcut, ANY_LOCALE);
        if (!TextUtils.isEmpty(expansionForGlobal) && DebugFlags.DEBUG_ENABLED) {
            Log.d(mTag, "expandShortcut() : Global expansion is [" + expansionForGlobal + "]");
        }
        return expansionForGlobal;
    }

    @Nullable private String expandShortcut(
            @Nullable final HashMap<Locale, HashMap<String, String>> shortcutsPerLocale,
            @NonNull final String shortcut,
            @NonNull final Locale locale) {
        if (CollectionUtils.isNullOrEmpty(shortcutsPerLocale)) {
            return null;
        }
        final HashMap<String, String> localeShortcuts = shortcutsPerLocale.get(locale);
        if (CollectionUtils.isNullOrEmpty(localeShortcuts)) {
            return null;
        }
        return localeShortcuts.get(shortcut);
    }

    /**
     * Loads the personal dictionary in the current thread.
     *
     * Only one reload can happen at a time. If already running, will exit quickly.
     */
    private void loadPersonalDictionary() {
        // Bail out if already in the process of loading.
        if (!mIsLoading.compareAndSet(false, true)) {
            Log.i(mTag, "loadPersonalDictionary() : Already Loading (exit)");
            return;
        }
        Log.i(mTag, "loadPersonalDictionary() : Start Loading");
        HashMap<String, HashMap<Locale, String>> dictWords = new HashMap<>();
        HashMap<Locale, HashMap<String, String>> shortcutsPerLocale = new HashMap<>();
        // Load the dictionary.  Items are returned in the default sort order (by frequency).
        Cursor cursor = mResolver.query(UserDictionary.Words.CONTENT_URI,
                null, null, null, UserDictionary.Words.DEFAULT_SORT_ORDER);
        if (null == cursor || cursor.getCount() < 1) {
            Log.i(mTag, "loadPersonalDictionary() : Empty");
        } else {
            // Iterate over the entries in the personal dictionary.  Note, that iteration is in
            // descending frequency by default.
            while (dictWords.size() < MAX_NUM_ENTRIES && cursor.moveToNext()) {
                // If there is no column for locale, skip this entry. An empty
                // locale on the other hand will not be skipped.
                final int dictLocaleIndex = cursor.getColumnIndex(UserDictionary.Words.LOCALE);
                if (dictLocaleIndex < 0) {
                    if (DebugFlags.DEBUG_ENABLED) {
                        Log.d(mTag, "loadPersonalDictionary() : Entry without LOCALE, skipping");
                    }
                    continue;
                }
                // If there is no column for word, skip this entry.
                final int dictWordIndex = cursor.getColumnIndex(UserDictionary.Words.WORD);
                if (dictWordIndex < 0) {
                    if (DebugFlags.DEBUG_ENABLED) {
                        Log.d(mTag, "loadPersonalDictionary() : Entry without WORD, skipping");
                    }
                    continue;
                }
                // If the word is null, skip this entry.
                final String rawDictWord = cursor.getString(dictWordIndex);
                if (null == rawDictWord) {
                    if (DebugFlags.DEBUG_ENABLED) {
                        Log.d(mTag, "loadPersonalDictionary() : Null word");
                    }
                    continue;
                }
                // If the locale is null, that's interpreted to mean all locales. Note, the special
                // zz locale for an Alphabet (QWERTY) layout will not match any actual language.
                String localeString = cursor.getString(dictLocaleIndex);
                if (null == localeString) {
                    if (DebugFlags.DEBUG_ENABLED) {
                        Log.d(mTag, "loadPersonalDictionary() : Null locale for word [" +
                                rawDictWord + "], assuming all locales");
                    }
                    // For purposes of LocaleUtils, an empty locale matches everything.
                    localeString = "";
                }
                final Locale dictLocale = LocaleUtils.constructLocaleFromString(localeString);
                // Lowercase the word before storing it.
                final String dictWord = rawDictWord.toLowerCase(dictLocale);
                if (DebugFlags.DEBUG_ENABLED) {
                    Log.d(mTag, "loadPersonalDictionary() : Adding word [" + dictWord
                            + "] for locale " + dictLocale + "with value" + rawDictWord);
                }
                // Check if there is an existing entry for this word.
                HashMap<Locale, String> dictLocales = dictWords.get(dictWord);
                if (CollectionUtils.isNullOrEmpty(dictLocales)) {
                    // If there is no entry for this word, create one.
                    if (DebugFlags.DEBUG_ENABLED) {
                        Log.d(mTag, "loadPersonalDictionary() : Word [" + dictWord +
                                "] not seen for other locales, creating new entry");
                    }
                    dictLocales = new HashMap<>();
                    dictWords.put(dictWord, dictLocales);
                }
                // Append the locale to the list of locales this word is in.
                dictLocales.put(dictLocale, rawDictWord);

                // If there is no column for a shortcut, we're done.
                final int shortcutIndex = cursor.getColumnIndex(UserDictionary.Words.SHORTCUT);
                if (shortcutIndex < 0) {
                    if (DebugFlags.DEBUG_ENABLED) {
                        Log.d(mTag, "loadPersonalDictionary() : Entry without SHORTCUT, done");
                    }
                    continue;
                }
                // If the shortcut is null, we're done.
                final String shortcut = cursor.getString(shortcutIndex);
                if (shortcut == null) {
                    if (DebugFlags.DEBUG_ENABLED) {
                        Log.d(mTag, "loadPersonalDictionary() : Null shortcut");
                    }
                    continue;
                }
                // Else, save the shortcut.
                HashMap<String, String> localeShortcuts = shortcutsPerLocale.get(dictLocale);
                if (localeShortcuts == null) {
                    localeShortcuts = new HashMap<>();
                    shortcutsPerLocale.put(dictLocale, localeShortcuts);
                }
                // Map to the raw input, which might be capitalized.
                // This lets the user create a shortcut from "gm" to "General Motors".
                localeShortcuts.put(shortcut, rawDictWord);
            }
        }

        List<DictionaryStats> stats = new ArrayList<>();
        stats.add(new DictionaryStats(ANY_LOCALE, Dictionary.TYPE_USER, dictWords.size()));
        int numShortcuts = 0;
        for (HashMap<String, String> shortcuts : shortcutsPerLocale.values()) {
            numShortcuts += shortcuts.size();
        }
        stats.add(new DictionaryStats(ANY_LOCALE, Dictionary.TYPE_USER_SHORTCUT, numShortcuts));
        mDictionaryStats = stats;

        // Atomically replace the copy of mDictWords and mShortcuts.
        mDictWords = dictWords;
        mShortcutsPerLocale = shortcutsPerLocale;

        // Allow other calls to loadPersonalDictionary to execute now.
        mIsLoading.set(false);

        Log.i(mTag, "loadPersonalDictionary() : Loaded " + mDictWords.size()
                + " words and " + numShortcuts + " shortcuts");

        notifyListeners();
    }
}
