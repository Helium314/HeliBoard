/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.dslul.openboard.inputmethod.latin.makedict.DictionaryHeader;
import org.dslul.openboard.inputmethod.latin.utils.DictionaryInfoUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Locale;

/**
 * Factory for dictionary instances.
 */
public final class DictionaryFactory {

    /**
     * Initializes a main dictionary collection from a dictionary pack, with explicit flags.
     * <p>
     * This searches for a content provider providing a dictionary pack for the specified
     * locale. If none is found, it falls back to the built-in dictionary - if any.
     * @param context application context for reading resources
     * @param locale the locale for which to create the dictionary
     * @return an initialized instance of DictionaryCollection
     */
    public static DictionaryCollection createMainDictionaryFromManager(final Context context, @NonNull final Locale locale) {
        final LinkedList<Dictionary> dictList = new LinkedList<>();
        ArrayList<AssetFileAddress> assetFileList =
                BinaryDictionaryGetter.getDictionaryFiles(locale, context, false);

        boolean mainFound = false;
        for (AssetFileAddress fileAddress : assetFileList) {
            if (fileAddress.mFilename.contains("main")) {
                mainFound = true;
                break;
            }
        }
        if (!mainFound) // try again and allow weaker match
            assetFileList = BinaryDictionaryGetter.getDictionaryFiles(locale, context, true);

        for (final AssetFileAddress f : assetFileList) {
            final DictionaryHeader header = DictionaryInfoUtils.getDictionaryFileHeaderOrNull(new File(f.mFilename), f.mOffset, f.mLength);
            String dictType = Dictionary.TYPE_MAIN;
            if (header != null) {
                // make sure the suggested words dictionary has the correct type
                dictType = header.mIdString.split(":")[0];
            }
            final ReadOnlyBinaryDictionary readOnlyBinaryDictionary =
                    new ReadOnlyBinaryDictionary(f.mFilename, f.mOffset, f.mLength,
                            false /* useFullEditDistance */, locale, dictType);
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
            // notify the user if possible (toast not showing up on Android 13+ when not in settings)
            //  but not that important, as the not working dictionary should be obvious
            final String wordlistId = DictionaryInfoUtils.getWordListIdFromFileName(new File(f.mFilename).getName());
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, "dictionary "+wordlistId+" is invalid, deleting", Toast.LENGTH_LONG).show()
            );
        }
    }
}
