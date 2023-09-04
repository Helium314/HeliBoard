/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.ContentProviderClient;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.dslul.openboard.inputmethod.latin.utils.DictionaryInfoUtils;

import java.io.File;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Locale;

/**
 * Factory for dictionary instances.
 */
public final class DictionaryFactory {
    private static final String TAG = DictionaryFactory.class.getSimpleName();

    /**
     * Initializes a main dictionary collection from a dictionary pack, with explicit flags.
     *
     * This searches for a content provider providing a dictionary pack for the specified
     * locale. If none is found, it falls back to the built-in dictionary - if any.
     * @param context application context for reading resources
     * @param locale the locale for which to create the dictionary
     * @return an initialized instance of DictionaryCollection
     */
    public static DictionaryCollection createMainDictionaryFromManager(final Context context,
            final Locale locale) {
        if (null == locale) {
            Log.e(TAG, "No locale defined for dictionary");
            return new DictionaryCollection(Dictionary.TYPE_MAIN, locale,
                    createReadOnlyBinaryDictionary(context, locale));
        }

        final LinkedList<Dictionary> dictList = new LinkedList<>();
        ArrayList<AssetFileAddress> assetFileList =
                BinaryDictionaryGetter.getDictionaryFiles(locale, context, true, false);

        boolean mainFound = false;
        for (AssetFileAddress fileAddress : assetFileList) {
            if (fileAddress.mFilename.contains("main")) {
                mainFound = true;
                break;
            }
        }
        if (!mainFound) // try again and allow weaker match
            assetFileList = BinaryDictionaryGetter.getDictionaryFiles(locale, context, true, true);

        for (final AssetFileAddress f : assetFileList) {
            final ReadOnlyBinaryDictionary readOnlyBinaryDictionary =
                    new ReadOnlyBinaryDictionary(f.mFilename, f.mOffset, f.mLength,
                            false /* useFullEditDistance */, locale, Dictionary.TYPE_MAIN);
            if (readOnlyBinaryDictionary.isValidDictionary()) {
                if(locale.getLanguage().equals("ko")) {
                    // Use KoreanDictionary for Korean locale
                    dictList.add(new KoreanDictionary(readOnlyBinaryDictionary));
                } else {
                    dictList.add(readOnlyBinaryDictionary);
                }
            } else {
                readOnlyBinaryDictionary.close();
                // Prevent this dictionary to do any further harm.
                killDictionary(context, f);
            }
        }

        // If the list is empty, that means we should not use any dictionary (for example, the user
        // explicitly disabled the main dictionary), so the following is okay. dictList is never
        // null, but if for some reason it is, DictionaryCollection handles it gracefully.
        return new DictionaryCollection(Dictionary.TYPE_MAIN, locale, dictList);
    }

    /**
     * Kills a dictionary so that it is never used again, if possible.
     * @param context The context to contact the dictionary provider, if possible.
     * @param f A file address to the dictionary to kill.
     */
    public static void killDictionary(final Context context, final AssetFileAddress f) {
        if (f.pointsToPhysicalFile()) {
            f.deleteUnderlyingFile();
            // Warn the dictionary provider if the dictionary came from there.
            final ContentProviderClient providerClient;
            try {
                providerClient = context.getContentResolver().acquireContentProviderClient(
                        BinaryDictionaryFileDumper.getProviderUriBuilder("").build());
            } catch (final SecurityException e) {
                Log.e(TAG, "No permission to communicate with the dictionary provider", e);
                return;
            }
            if (null == providerClient) {
                Log.e(TAG, "Can't establish communication with the dictionary provider");
                return;
            }
            final String wordlistId =
                    DictionaryInfoUtils.getWordListIdFromFileName(new File(f.mFilename).getName());
            // TODO: this is a reasonable last resort, but it is suboptimal.
            // The following will remove the entry for this dictionary with the dictionary
            // provider. When the metadata is downloaded again, we will try downloading it
            // again.
            // However, in the practice that will mean the user will find themselves without
            // the new dictionary. That's fine for languages where it's included in the APK,
            // but for other languages it will leave the user without a dictionary at all until
            // the next update, which may be a few days away.
            // Ideally, we would trigger a new download right away, and use increasing retry
            // delays for this particular id/version combination.
            // Then again, this is expected to only ever happen in case of human mistake. If
            // the wrong file is on the server, the following is still doing the right thing.
            // If it's a file left over from the last version however, it's not great.
            BinaryDictionaryFileDumper.reportBrokenFileToDictionaryProvider(
                    providerClient,
                    context.getString(R.string.dictionary_pack_client_id),
                    wordlistId);
        }
    }

    /**
     * Initializes a read-only binary dictionary from a raw resource file
     * @param context application context for reading resources
     * @param locale the locale to use for the resource
     * @return an initialized instance of ReadOnlyBinaryDictionary
     */
    private static ReadOnlyBinaryDictionary createReadOnlyBinaryDictionary(final Context context,
            final Locale locale) {
        AssetFileDescriptor afd = null;
        try {
            final int resId = DictionaryInfoUtils.getMainDictionaryResourceIdIfAvailableForLocale(
                    context.getResources(), locale);
            if (0 == resId) return null;
            afd = context.getResources().openRawResourceFd(resId);
            if (afd == null) {
                Log.e(TAG, "Found the resource but it is compressed. resId=" + resId);
                return null;
            }
            final String sourceDir = context.getApplicationInfo().sourceDir;
            final File packagePath = new File(sourceDir);
            // TODO: Come up with a way to handle a directory.
            if (!packagePath.isFile()) {
                Log.e(TAG, "sourceDir is not a file: " + sourceDir);
                return null;
            }
            return new ReadOnlyBinaryDictionary(sourceDir, afd.getStartOffset(), afd.getLength(),
                    false /* useFullEditDistance */, locale, Dictionary.TYPE_MAIN);
        } catch (android.content.res.Resources.NotFoundException e) {
            Log.e(TAG, "Could not find the resource");
            return null;
        } finally {
            if (null != afd) {
                try {
                    afd.close();
                } catch (java.io.IOException e) {
                    /* IOException on close ? What am I supposed to do ? */
                }
            }
        }
    }
}
