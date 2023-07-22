/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.content.Context;
import android.util.LruCache;

import org.dslul.openboard.inputmethod.annotations.UsedForTesting;
import org.dslul.openboard.inputmethod.keyboard.Keyboard;
import org.dslul.openboard.inputmethod.latin.common.ComposedData;
import org.dslul.openboard.inputmethod.latin.settings.SettingsValuesForSuggestion;
import org.dslul.openboard.inputmethod.latin.utils.SuggestionResults;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Interface that facilitates interaction with different kinds of dictionaries. Provides APIs to
 * instantiate and select the correct dictionaries (based on language or account), update entries
 * and fetch suggestions. Currently AndroidSpellCheckerService and LatinIME both use
 * DictionaryFacilitator as a client for interacting with dictionaries.
 */
public interface DictionaryFacilitator {

    String[] ALL_DICTIONARY_TYPES = new String[] {
            Dictionary.TYPE_MAIN,
            Dictionary.TYPE_CONTACTS,
            Dictionary.TYPE_USER_HISTORY,
            Dictionary.TYPE_USER};

    String[] DYNAMIC_DICTIONARY_TYPES = new String[] {
            Dictionary.TYPE_CONTACTS,
            Dictionary.TYPE_USER_HISTORY,
            Dictionary.TYPE_USER};

    /**
     * The facilitator will put words into the cache whenever it decodes them.
     * @param cache
     */
    void setValidSpellingWordReadCache(final LruCache<String, Boolean> cache);

    /**
     * The facilitator will get words from the cache whenever it needs to check their spelling.
     * @param cache
     */
    void setValidSpellingWordWriteCache(final LruCache<String, Boolean> cache);

    /**
     * Returns whether this facilitator is exactly for this locale.
     *
     * @param locale the locale to test against
     */
    boolean isForLocale(final Locale locale);

    /**
     * Returns whether this facilitator is exactly for this account.
     *
     * @param account the account to test against.
     */
    boolean isForAccount(@Nullable final String account);

    interface DictionaryInitializationListener {
        void onUpdateMainDictionaryAvailability(boolean isMainDictionaryAvailable);
    }

    /**
     * Called every time {@link LatinIME} starts on a new text field.
     * Dot not affect {@link AndroidSpellCheckerService}.
     *
     * WARNING: The service methods that call start/finish are very spammy.
     */
    void onStartInput();

    /**
     * Called every time the {@link LatinIME} finishes with the current text field.
     * May be followed by {@link #onStartInput} again in another text field,
     * or it may be done for a while.
     * Dot not affect {@link AndroidSpellCheckerService}.
     *
     * WARNING: The service methods that call start/finish are very spammy.
     */
    void onFinishInput(Context context);

    boolean isActive();

    Locale getLocale();

    boolean usesContacts();

    String getAccount();

    void resetDictionaries(
            final Context context,
            final Locale newLocale,
            final boolean useContactsDict,
            final boolean usePersonalizedDicts,
            final boolean forceReloadMainDictionary,
            @Nullable final String account,
            final String dictNamePrefix,
            @Nullable final DictionaryInitializationListener listener);

    void removeWord(String word);

    @UsedForTesting
    void resetDictionariesForTesting(
            final Context context,
            final Locale locale,
            final ArrayList<String> dictionaryTypes,
            final HashMap<String, File> dictionaryFiles,
            final Map<String, Map<String, String>> additionalDictAttributes,
            @Nullable final String account);

    void closeDictionaries();

    @UsedForTesting
    ExpandableBinaryDictionary getSubDictForTesting(final String dictName);

    // The main dictionaries are loaded asynchronously. Don't cache the return value
    // of these methods.
    boolean hasAtLeastOneInitializedMainDictionary();

    boolean hasAtLeastOneUninitializedMainDictionary();

    void waitForLoadingMainDictionaries(final long timeout, final TimeUnit unit)
            throws InterruptedException;

    @UsedForTesting
    void waitForLoadingDictionariesForTesting(final long timeout, final TimeUnit unit)
            throws InterruptedException;

    void addToUserHistory(final String suggestion, final boolean wasAutoCapitalized,
            @Nonnull final NgramContext ngramContext, final long timeStampInSeconds,
            final boolean blockPotentiallyOffensive);

    void unlearnFromUserHistory(final String word,
            @Nonnull final NgramContext ngramContext, final long timeStampInSeconds,
            final int eventType);

    // TODO: Revise the way to fusion suggestion results.
    @Nonnull SuggestionResults getSuggestionResults(final ComposedData composedData,
            final NgramContext ngramContext, @Nonnull final Keyboard keyboard,
            final SettingsValuesForSuggestion settingsValuesForSuggestion, final int sessionId,
            final int inputStyle);

    boolean isValidSpellingWord(final String word);

    boolean isValidSuggestionWord(final String word);

    boolean clearUserHistoryDictionary(final Context context);

    String dump(final Context context);

    void dumpDictionaryForDebug(final String dictName);

    @Nonnull List<DictionaryStats> getDictionaryStats(final Context context);
}
