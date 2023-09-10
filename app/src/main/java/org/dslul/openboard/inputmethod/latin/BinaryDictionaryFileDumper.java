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
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import org.dslul.openboard.inputmethod.dictionarypack.DictionaryPackConstants;
import org.dslul.openboard.inputmethod.latin.define.DecoderSpecificConstants;
import org.dslul.openboard.inputmethod.latin.utils.DictionaryInfoUtils;
import org.dslul.openboard.inputmethod.latin.utils.DictionaryInfoUtils.DictionaryInfo;
import org.dslul.openboard.inputmethod.latin.utils.MetadataFileUriGetter;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Group class for static methods to help with creation and getting of the binary dictionary
 * file from the dictionary provider
 */
public final class BinaryDictionaryFileDumper {
    private static final String TAG = BinaryDictionaryFileDumper.class.getSimpleName();

    /**
     * The size of the temporary buffer to copy files.
     */
    private static final int FILE_READ_BUFFER_SIZE = 8192;
    // TODO: make the following data common with the native code
    private static final byte[] MAGIC_NUMBER_VERSION_1 =
            new byte[] { (byte)0x78, (byte)0xB1, (byte)0x00, (byte)0x00 };
    private static final byte[] MAGIC_NUMBER_VERSION_2 =
            new byte[] { (byte)0x9B, (byte)0xC1, (byte)0x3A, (byte)0xFE };

    private static final boolean SHOULD_VERIFY_MAGIC_NUMBER = DecoderSpecificConstants.SHOULD_VERIFY_MAGIC_NUMBER;
    private static final boolean SHOULD_VERIFY_CHECKSUM = DecoderSpecificConstants.SHOULD_VERIFY_CHECKSUM;

    private static final String[] DICTIONARY_PROJECTION = {"id"};

    private static final String QUERY_PARAMETER_MAY_PROMPT_USER = "mayPrompt";
    private static final String QUERY_PARAMETER_TRUE = "true";
    private static final String QUERY_PARAMETER_DELETE_RESULT = "result";
    private static final String QUERY_PARAMETER_SUCCESS = "success";
    private static final String QUERY_PARAMETER_FAILURE = "failure";

    // Using protocol version 2 to communicate with the dictionary pack
    private static final String QUERY_PARAMETER_PROTOCOL = "protocol";
    private static final String QUERY_PARAMETER_PROTOCOL_VALUE = "2";

    // The path fragment to append after the client ID for dictionary info requests.
    private static final String QUERY_PATH_DICT_INFO = "dict";
    // The path fragment to append after the client ID for dictionary datafile requests.
    private static final String QUERY_PATH_DATAFILE = "datafile";
    // The path fragment to append after the client ID for updating the metadata URI.
    private static final String QUERY_PATH_METADATA = "metadata";
    private static final String INSERT_METADATA_CLIENT_ID_COLUMN = "clientid";
    private static final String INSERT_METADATA_METADATA_URI_COLUMN = "uri";
    private static final String INSERT_METADATA_METADATA_ADDITIONAL_ID_COLUMN = "additionalid";

    // Prevents this class to be accidentally instantiated.
    private BinaryDictionaryFileDumper() {
    }

    /**
     * Returns a URI builder pointing to the dictionary pack.
     *
     * This creates a URI builder able to build a URI pointing to the dictionary
     * pack content provider for a specific dictionary id.
     */
    public static Uri.Builder getProviderUriBuilder(final String path) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(DictionaryPackConstants.AUTHORITY).appendPath(path);
    }

    /**
     * Gets the content URI builder for a specified type.
     *
     * Supported types include QUERY_PATH_DICT_INFO, which takes the locale as
     * the extraPath argument, and QUERY_PATH_DATAFILE, which needs a wordlist ID
     * as the extraPath argument.
     *
     * @param clientId the clientId to use
     * @param contentProviderClient the instance of content provider client
     * @param queryPathType the path element encoding the type
     * @param extraPath optional extra argument for this type (typically word list id)
     * @return a builder that can build the URI for the best supported protocol version
     * @throws RemoteException if the client can't be contacted
     */
    private static Uri.Builder getContentUriBuilderForType(final String clientId,
            final ContentProviderClient contentProviderClient, final String queryPathType,
            final String extraPath) throws RemoteException {
        // Check whether protocol v2 is supported by building a v2 URI and calling getType()
        // on it. If this returns null, v2 is not supported.
        final Uri.Builder uriV2Builder = getProviderUriBuilder(clientId);
        uriV2Builder.appendPath(queryPathType);
        uriV2Builder.appendPath(extraPath);
        uriV2Builder.appendQueryParameter(QUERY_PARAMETER_PROTOCOL,
                QUERY_PARAMETER_PROTOCOL_VALUE);
        if (null != contentProviderClient.getType(uriV2Builder.build())) return uriV2Builder;
        // Protocol v2 is not supported, so create and return the protocol v1 uri.
        return getProviderUriBuilder(extraPath);
    }

    /**
     * Queries a content provider for the list of word lists for a specific locale
     * available to copy into Latin IME.
     */
    private static List<WordListInfo> getWordListWordListInfos(final Locale locale,
            final Context context, final boolean hasDefaultWordList) {
        final String clientId = context.getString(R.string.dictionary_pack_client_id);
        final ContentProviderClient client = context.getContentResolver().
                acquireContentProviderClient(getProviderUriBuilder("").build());
        if (null == client) return Collections.emptyList();
        Cursor cursor = null;
        try {
            final Uri.Builder builder = getContentUriBuilderForType(clientId, client,
                    QUERY_PATH_DICT_INFO, locale.toString());
            if (!hasDefaultWordList) {
                builder.appendQueryParameter(QUERY_PARAMETER_MAY_PROMPT_USER,
                        QUERY_PARAMETER_TRUE);
            }
            final Uri queryUri = builder.build();
            final boolean isProtocolV2 = (QUERY_PARAMETER_PROTOCOL_VALUE.equals(
                    queryUri.getQueryParameter(QUERY_PARAMETER_PROTOCOL)));

            cursor = client.query(queryUri, DICTIONARY_PROJECTION, null, null, null);
            if (isProtocolV2 && null == cursor) {
                reinitializeClientRecordInDictionaryContentProvider(context, client, clientId);
                cursor = client.query(queryUri, DICTIONARY_PROJECTION, null, null, null);
            }
            if (null == cursor) return Collections.emptyList();
            if (cursor.getCount() <= 0 || !cursor.moveToFirst()) {
                return Collections.emptyList();
            }
            final ArrayList<WordListInfo> list = new ArrayList<>();
            do {
                final String wordListId = cursor.getString(0);
                final String wordListLocale = cursor.getString(1);
                final String wordListRawChecksum = cursor.getString(2);
                if (TextUtils.isEmpty(wordListId)) continue;
                list.add(new WordListInfo(wordListId, wordListLocale, wordListRawChecksum));
            } while (cursor.moveToNext());
            return list;
        } catch (RemoteException e) {
            // The documentation is unclear as to in which cases this may happen, but it probably
            // happens when the content provider got suddenly killed because it crashed or because
            // the user disabled it through Settings.
            Log.e(TAG, "RemoteException: communication with the dictionary pack cut", e);
            return Collections.emptyList();
        } catch (Exception e) {
            // A crash here is dangerous because crashing here would brick any encrypted device -
            // we need the keyboard to be up and working to enter the password, so we don't want
            // to die no matter what. So let's be as safe as possible.
            Log.e(TAG, "Unexpected exception communicating with the dictionary pack", e);
            return Collections.emptyList();
        } finally {
            if (null != cursor) {
                cursor.close();
            }
            client.release();
        }
    }


    /**
     * Helper method to encapsulate exception handling.
     */
    private static AssetFileDescriptor openAssetFileDescriptor(
            final ContentProviderClient providerClient, final Uri uri) {
        try {
            return providerClient.openAssetFile(uri, "r");
        } catch (FileNotFoundException e) {
            // I don't want to log the word list URI here for security concerns. The exception
            // contains the name of the file, so let's not pass it to Log.e here.
            Log.e(TAG, "Could not find a word list from the dictionary provider."
                    /* intentionally don't pass the exception (see comment above) */);
            return null;
        } catch (RemoteException e) {
            Log.e(TAG, "Can't communicate with the dictionary pack", e);
            return null;
        }
    }

    public static boolean reportBrokenFileToDictionaryProvider(
            final ContentProviderClient providerClient, final String clientId,
            final String wordlistId) {
        try {
            final Uri.Builder wordListUriBuilder = getContentUriBuilderForType(clientId,
                    providerClient, QUERY_PATH_DATAFILE, wordlistId /* extraPath */);
            wordListUriBuilder.appendQueryParameter(QUERY_PARAMETER_DELETE_RESULT,
                    QUERY_PARAMETER_FAILURE);
            if (0 >= providerClient.delete(wordListUriBuilder.build(), null, null)) {
                Log.e(TAG, "Unable to delete a word list.");
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Communication with the dictionary provider was cut", e);
            return false;
        }
        return true;
    }

    // Ideally the two following methods should be merged, but AssetFileDescriptor does not
    // implement Closeable although it does implement #close(), and Java does not have
    // structural typing.
    private static void closeAssetFileDescriptorAndReportAnyException(
            final AssetFileDescriptor file) {
        try {
            if (null != file) file.close();
        } catch (Exception e) {
            Log.e(TAG, "Exception while closing a file", e);
        }
    }

    private static void closeCloseableAndReportAnyException(final Closeable file) {
        try {
            if (null != file) file.close();
        } catch (Exception e) {
            Log.e(TAG, "Exception while closing a file", e);
        }
    }

    /**
     * Copies the data in an input stream to a target file if the magic number matches.
     *
     * If the magic number does not match the expected value, this method throws an
     * IOException. Other usual conditions for IOException or FileNotFoundException
     * also apply.
     *
     * @param input the stream to be copied.
     * @param output an output stream to copy the data to.
     */
    public static void checkMagicAndCopyFileTo(final BufferedInputStream input,
            final BufferedOutputStream output) throws IOException {
        // Check the magic number
        final int length = MAGIC_NUMBER_VERSION_2.length;
        final byte[] magicNumberBuffer = new byte[length];
        final int readMagicNumberSize = input.read(magicNumberBuffer, 0, length);
        if (readMagicNumberSize < length) {
            throw new IOException("Less bytes to read than the magic number length");
        }
        if (SHOULD_VERIFY_MAGIC_NUMBER) {
            if (!Arrays.equals(MAGIC_NUMBER_VERSION_2, magicNumberBuffer)) {
                if (!Arrays.equals(MAGIC_NUMBER_VERSION_1, magicNumberBuffer)) {
                    throw new IOException("Wrong magic number for downloaded file");
                }
            }
        }
        output.write(magicNumberBuffer);

        // Actually copy the file
        final byte[] buffer = new byte[FILE_READ_BUFFER_SIZE];
        for (int readBytes = input.read(buffer); readBytes >= 0; readBytes = input.read(buffer)) {
            output.write(buffer, 0, readBytes);
        }
        input.close();
    }

    private static void reinitializeClientRecordInDictionaryContentProvider(final Context context,
            final ContentProviderClient client, final String clientId) throws RemoteException {
        final String metadataFileUri = MetadataFileUriGetter.getMetadataUri(context);
        Log.i(TAG, "reinitializeClientRecordInDictionaryContentProvider() : MetadataFileUri = "
                + metadataFileUri);
        final String metadataAdditionalId = MetadataFileUriGetter.getMetadataAdditionalId(context);
        // Tell the content provider to reset all information about this client id
        final Uri metadataContentUri = getProviderUriBuilder(clientId)
                .appendPath(QUERY_PATH_METADATA)
                .appendQueryParameter(QUERY_PARAMETER_PROTOCOL, QUERY_PARAMETER_PROTOCOL_VALUE)
                .build();
        client.delete(metadataContentUri, null, null);
        // Update the metadata URI
        final ContentValues metadataValues = new ContentValues();
        metadataValues.put(INSERT_METADATA_CLIENT_ID_COLUMN, clientId);
        metadataValues.put(INSERT_METADATA_METADATA_URI_COLUMN, metadataFileUri);
        metadataValues.put(INSERT_METADATA_METADATA_ADDITIONAL_ID_COLUMN, metadataAdditionalId);
        client.insert(metadataContentUri, metadataValues);

        // Update the dictionary list.
        final Uri dictionaryContentUriBase = getProviderUriBuilder(clientId)
                .appendPath(QUERY_PATH_DICT_INFO)
                .appendQueryParameter(QUERY_PARAMETER_PROTOCOL, QUERY_PARAMETER_PROTOCOL_VALUE)
                .build();
        final ArrayList<DictionaryInfo> dictionaryList =
                DictionaryInfoUtils.getCurrentDictionaryFileNameAndVersionInfo(context);
        final int length = dictionaryList.size();
        for (int i = 0; i < length; ++i) {
            final DictionaryInfo info = dictionaryList.get(i);
            Log.i(TAG, "reinitializeClientRecordInDictionaryContentProvider() : Insert " + info);
            client.insert(Uri.withAppendedPath(dictionaryContentUriBase, info.mId),
                    info.toContentValues());
        }

        // Read from metadata file in resources to get the baseline dictionary info.
        // This ensures we start with a sane list of available dictionaries.
        final int metadataResourceId = context.getResources().getIdentifier("metadata",
                "raw", DictionaryInfoUtils.RESOURCE_PACKAGE_NAME);
        if (metadataResourceId == 0) {
            Log.w(TAG, "Missing metadata.json resource");
            return;
        }
        InputStream inputStream = null;
        try {
            inputStream = context.getResources().openRawResource(metadataResourceId);
        } catch (Exception e) {
            Log.w(TAG, "Failed to read metadata.json from resources", e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    Log.w(TAG, "Failed to close metadata.json", e);
                }
            }
        }
    }

    /**
     * Initialize a client record with the dictionary content provider.
     *
     * This merely acquires the content provider and calls
     * #reinitializeClientRecordInDictionaryContentProvider.
     *
     * @param context the context for resources and providers.
     * @param clientId the client ID to use.
     */
    public static void initializeClientRecordHelper(final Context context, final String clientId) {
        try {
            final ContentProviderClient client = context.getContentResolver().
                    acquireContentProviderClient(getProviderUriBuilder("").build());
            if (null == client) return;
            reinitializeClientRecordInDictionaryContentProvider(context, client, clientId);
        } catch (RemoteException e) {
            Log.e(TAG, "Cannot contact the dictionary content provider", e);
        }
    }
}
