/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin;

import android.content.Context;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import helium314.keyboard.keyboard.Keyboard;
import helium314.keyboard.latin.common.ComposedData;
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion;
import helium314.keyboard.latin.utils.SuggestionResults;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Interface that facilitates interaction with different kinds of dictionaries. Provides APIs to
 * instantiate and select the correct dictionaries (based on language and settings), update entries
 * and fetch suggestions. Currently AndroidSpellCheckerService and LatinIME both use
 * DictionaryFacilitator as a client for interacting with dictionaries.
 */
public interface DictionaryFacilitator {

    String[] ALL_DICTIONARY_TYPES = new String[] {
            Dictionary.TYPE_MAIN,
            Dictionary.TYPE_CONTACTS,
            Dictionary.TYPE_APPS,
            Dictionary.TYPE_USER_HISTORY,
            Dictionary.TYPE_USER};

    String[] DYNAMIC_DICTIONARY_TYPES = new String[] {
            Dictionary.TYPE_CONTACTS,
            Dictionary.TYPE_APPS,
            Dictionary.TYPE_USER_HISTORY,
            Dictionary.TYPE_USER};

    /**
     * The facilitator will put words into the cache whenever it decodes them.
     */
    void setValidSpellingWordReadCache(final LruCache<String, Boolean> cache);

    /**
     * The facilitator will get words from the cache whenever it needs to check their spelling.
     */
    void setValidSpellingWordWriteCache(final LruCache<String, Boolean> cache);

    /**
     * Returns whether this facilitator is exactly for this locale.
     *
     * @param locale the locale to test against
     */
    boolean isForLocale(final Locale locale);

    interface DictionaryInitializationListener {
        void onUpdateMainDictionaryAvailability(boolean isMainDictionaryAvailable);
    }

    /**
     * Called every time {@link LatinIME} starts on a new text field.
     * <p>
     * WARNING: The service methods that call start/finish are very spammy.
     */
    void onStartInput();

    /**
     * Called every time the {@link LatinIME} finishes with the current text field.
     * May be followed by {@link #onStartInput} again in another text field,
     * or it may be done for a while.
     * <p>
     * WARNING: The service methods that call start/finish are very spammy.
     */
    void onFinishInput(Context context);

    boolean isActive();

    @NonNull Locale getMainLocale();

    // useful for multilingual typing
    Locale getCurrentLocale();

    boolean usesSameSettings(
            @NonNull final List<Locale> locales,
            final boolean contacts,
            final boolean apps,
            final boolean personalization
    );

    void resetDictionaries(
            final Context context,
            final Locale newLocale,
            final boolean useContactsDict,
            final boolean useAppsDict,
            final boolean usePersonalizedDicts,
            final boolean forceReloadMainDictionary,
            final String dictNamePrefix,
            @Nullable final DictionaryInitializationListener listener);

    void removeWord(String word);

    void closeDictionaries();

    // The main dictionaries are loaded asynchronously. Don't cache the return value
    // of these methods.
    boolean hasAtLeastOneInitializedMainDictionary();

    boolean hasAtLeastOneUninitializedMainDictionary();

    void waitForLoadingMainDictionaries(final long timeout, final TimeUnit unit)
            throws InterruptedException;

    void addToUserHistory(final String suggestion, final boolean wasAutoCapitalized,
            @NonNull final NgramContext ngramContext, final long timeStampInSeconds,
            final boolean blockPotentiallyOffensive);

    void adjustConfidences(final String word, final boolean wasAutoCapitalized);

    void unlearnFromUserHistory(final String word,
            @NonNull final NgramContext ngramContext, final long timeStampInSeconds,
            final int eventType);

    // TODO: Revise the way to fusion suggestion results.
    @NonNull SuggestionResults getSuggestionResults(final ComposedData composedData,
            final NgramContext ngramContext, @NonNull final Keyboard keyboard,
            final SettingsValuesForSuggestion settingsValuesForSuggestion, final int sessionId,
            final int inputStyle);

    boolean isValidSpellingWord(final String word);

    boolean isValidSuggestionWord(final String word);

    boolean clearUserHistoryDictionary(final Context context);

    String dump(final Context context);

    String localesAndConfidences();

    void dumpDictionaryForDebug(final String dictName);

    @NonNull List<DictionaryStats> getDictionaryStats(final Context context);
}
