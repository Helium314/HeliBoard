package org.dslul.openboard.inputmethod.dictionarypack

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.AbstractCursor
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.TextUtils
import android.util.Log
import org.dslul.openboard.inputmethod.dictionarypack.ActionBatch.MarkPreInstalledAction
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils
import org.dslul.openboard.inputmethod.latin.utils.DebugLogUtils
import java.io.FileNotFoundException
import java.util.*

/**
 * Provider for dictionaries.
 *
 * This class is a ContentProvider exposing all available dictionary data as managed by
 * the dictionary pack.
 */
@Suppress("deprecation")
class DictionaryProvider : ContentProvider() {
    companion object {
        private val TAG = DictionaryProvider::class.java.simpleName
        const val DEBUG = false
        val CONTENT_URI = Uri.parse(ContentResolver.SCHEME_CONTENT + "://" + DictionaryPackConstants.AUTHORITY)
        private const val QUERY_PARAMETER_MAY_PROMPT_USER = "mayPrompt"
        private const val QUERY_PARAMETER_TRUE = "true"
        private const val QUERY_PARAMETER_DELETE_RESULT = "result"
        private const val QUERY_PARAMETER_FAILURE = "failure"
        const val QUERY_PARAMETER_PROTOCOL_VERSION = "protocol"
        private const val NO_MATCH = 0
        private const val DICTIONARY_V1_WHOLE_LIST = 1
        private const val DICTIONARY_V1_DICT_INFO = 2
        private const val DICTIONARY_V2_METADATA = 3
        private const val DICTIONARY_V2_WHOLE_LIST = 4
        private const val DICTIONARY_V2_DICT_INFO = 5
        private const val DICTIONARY_V2_DATAFILE = 6
        private val sUriMatcherV1 = UriMatcher(NO_MATCH)
        private val sUriMatcherV2 = UriMatcher(NO_MATCH)
        // MIME types for dictionary and dictionary list, as required by ContentProvider contract.
        const val DICT_LIST_MIME_TYPE = "vnd.android.cursor.item/vnd.google.dictionarylist"
        const val DICT_DATAFILE_MIME_TYPE = "vnd.android.cursor.item/vnd.google.dictionary"
        const val ID_CATEGORY_SEPARATOR = ":"
        private fun matchUri(uri: Uri): Int {
            var protocolVersion = 1
            val protocolVersionArg = uri.getQueryParameter(QUERY_PARAMETER_PROTOCOL_VERSION)
            if ("2" == protocolVersionArg) protocolVersion = 2
            return when (protocolVersion) {
                1 -> sUriMatcherV1.match(uri)
                2 -> sUriMatcherV2.match(uri)
                else -> NO_MATCH
            }
        }

        private fun getClientId(uri: Uri): String? {
            var protocolVersion = 1
            val protocolVersionArg = uri.getQueryParameter(QUERY_PARAMETER_PROTOCOL_VERSION)
            if ("2" == protocolVersionArg) protocolVersion = 2
            return when (protocolVersion) {
                1 -> null // In protocol 1, the client ID is always null.
                2 -> uri.pathSegments[0]
                else -> null
            }
        }

        init {
            sUriMatcherV1.addURI(DictionaryPackConstants.AUTHORITY, "list", DICTIONARY_V1_WHOLE_LIST)
            sUriMatcherV1.addURI(DictionaryPackConstants.AUTHORITY, "*", DICTIONARY_V1_DICT_INFO)
            sUriMatcherV2.addURI(DictionaryPackConstants.AUTHORITY, "*/metadata",
                    DICTIONARY_V2_METADATA)
            sUriMatcherV2.addURI(DictionaryPackConstants.AUTHORITY, "*/list", DICTIONARY_V2_WHOLE_LIST)
            sUriMatcherV2.addURI(DictionaryPackConstants.AUTHORITY, "*/dict/*",
                    DICTIONARY_V2_DICT_INFO)
            sUriMatcherV2.addURI(DictionaryPackConstants.AUTHORITY, "*/datafile/*",
                    DICTIONARY_V2_DATAFILE)
        }
    }

    private class WordListInfo(val mId: String, val mLocale: String, val mRawChecksum: String,
                               val mMatchLevel: Int)

    /**
     * A cursor for returning a list of file ids from a List of strings.
     *
     * This simulates only the necessary methods. It has no error handling to speak of,
     * and does not support everything a database does, only a few select necessary methods.
     */
    private class ResourcePathCursor(wordLists: Collection<WordListInfo>) : AbstractCursor() {
        // The list of word lists served by this provider that match the client request.
        val mWordLists: Array<WordListInfo>

        override fun getColumnNames(): Array<String> {
            return columnNames
        }

        override fun getCount(): Int {
            return mWordLists.size
        }

        override fun getDouble(column: Int): Double {
            return 0.0
        }

        override fun getFloat(column: Int): Float {
            return 0F
        }

        override fun getInt(column: Int): Int {
            return 0
        }

        override fun getShort(column: Int): Short {
            return 0
        }

        override fun getLong(column: Int): Long {
            return 0
        }

        override fun getString(column: Int): String? {
            return when (column) {
                0 -> mWordLists[mPos].mId
                1 -> mWordLists[mPos].mLocale
                2 -> mWordLists[mPos].mRawChecksum
                else -> null
            }
        }

        override fun isNull(column: Int): Boolean {
            return if (mPos >= mWordLists.size) true else column != 0
        }

        companion object {
            // Column names for the cursor returned by this content provider.
            private val columnNames = arrayOf<String>(MetadataDbHelper.WORDLISTID_COLUMN,
                    MetadataDbHelper.LOCALE_COLUMN, MetadataDbHelper.RAW_CHECKSUM_COLUMN)
        }

        // Note : the cursor also uses mPos, which is defined in AbstractCursor.
        init { // Allocating a 0-size WordListInfo here allows the toArray() method
// to ensure we have a strongly-typed array. It's thrown out. That's
// what the documentation of #toArray says to do in order to get a
// new strongly typed array of the correct size.
            mWordLists = wordLists.toTypedArray()
            mPos = 0
        }
    }

    override fun onCreate(): Boolean {
        return true
    }

    /**
     * Returns the MIME type of the content associated with an Uri
     *
     * @see android.content.ContentProvider.getType
     * @param uri the URI of the content the type of which should be returned.
     * @return the MIME type, or null if the URL is not recognized.
     */
    override fun getType(uri: Uri): String? {
        PrivateLog.log("Asked for type of : $uri")
        val match = matchUri(uri)
        return when (match) {
            NO_MATCH -> null
            DICTIONARY_V1_WHOLE_LIST, DICTIONARY_V1_DICT_INFO, DICTIONARY_V2_WHOLE_LIST, DICTIONARY_V2_DICT_INFO -> DICT_LIST_MIME_TYPE
            DICTIONARY_V2_DATAFILE -> DICT_DATAFILE_MIME_TYPE
            else -> null
        }
    }

    /**
     * Query the provider for dictionary files.
     *
     * This version dispatches the query according to the protocol version found in the
     * ?protocol= query parameter. If absent or not well-formed, it defaults to 1.
     * @see android.content.ContentProvider.query
     * @param uri a content uri (see sUriMatcherV{1,2} at the top of this file for format)
     * @param projection ignored. All columns are always returned.
     * @param selection ignored.
     * @param selectionArgs ignored.
     * @param sortOrder ignored. The results are always returned in no particular order.
     * @return a cursor matching the uri, or null if the URI was not recognized.
     */
    override fun query(uri: Uri, projection: Array<String>?, selection: String?,
                       selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        DebugLogUtils.l("Uri =", uri)
        PrivateLog.log("Query : $uri")
        val clientId = getClientId(uri)
        val match = matchUri(uri)
        return when (match) {
            DICTIONARY_V1_WHOLE_LIST, DICTIONARY_V2_WHOLE_LIST -> {
                val c: Cursor = MetadataDbHelper.queryDictionaries(context, clientId)
                DebugLogUtils.l("List of dictionaries with count", c.count)
                PrivateLog.log("Returned a list of " + c.count + " items")
                c
            }
            DICTIONARY_V2_DICT_INFO -> {
                // In protocol version 2, we return null if the client is unknown. Otherwise
// we behave exactly like for protocol 1.
                if (!MetadataDbHelper.isClientKnown(context, clientId)) return null
                val locale = uri.lastPathSegment
                val dictFiles = getDictionaryWordListsForLocale(clientId, locale)
                // TODO: pass clientId to the following function
                if (null != dictFiles && dictFiles.size > 0) {
                    PrivateLog.log("Returned " + dictFiles.size + " files")
                    return ResourcePathCursor(dictFiles)
                }
                PrivateLog.log("No dictionary files for this URL")
                ResourcePathCursor(emptyList())
            }
            DICTIONARY_V1_DICT_INFO -> {
                val locale = uri.lastPathSegment
                val dictFiles = getDictionaryWordListsForLocale(clientId, locale)
                if (null != dictFiles && dictFiles.size > 0) {
                    PrivateLog.log("Returned " + dictFiles.size + " files")
                    return ResourcePathCursor(dictFiles)
                }
                PrivateLog.log("No dictionary files for this URL")
                ResourcePathCursor(emptyList())
            }
            else -> null
        }
    }

    /**
     * Helper method to get the wordlist metadata associated with a wordlist ID.
     *
     * @param clientId the ID of the client
     * @param wordlistId the ID of the wordlist for which to get the metadata.
     * @return the metadata for this wordlist ID, or null if none could be found.
     */
    private fun getWordlistMetadataForWordlistId(clientId: String?,
                                                 wordlistId: String?): ContentValues? {
        val context = context
        if (TextUtils.isEmpty(wordlistId)) return null
        val db: SQLiteDatabase = MetadataDbHelper.getDb(context, clientId)
        return MetadataDbHelper.getInstalledOrDeletingWordListContentValuesByWordListId(
                db, wordlistId)
    }

    /**
     * Opens an asset file for an URI.
     *
     * Called by [android.content.ContentResolver.openAssetFileDescriptor] or
     * [android.content.ContentResolver.openInputStream] from a client requesting a
     * dictionary.
     * @see android.content.ContentProvider.openAssetFile
     * @param uri the URI the file is for.
     * @param mode the mode to read the file. MUST be "r" for readonly.
     * @return the descriptor, or null if the file is not found or if mode is not equals to "r".
     */
    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        if (null == mode || "r" != mode) return null
        val match = matchUri(uri)
        if (DICTIONARY_V1_DICT_INFO != match && DICTIONARY_V2_DATAFILE != match) { // Unsupported URI for openAssetFile
            Log.w(TAG, "Unsupported URI for openAssetFile : $uri")
            return null
        }
        val wordlistId = uri.lastPathSegment
        val clientId = getClientId(uri)
        val wordList = getWordlistMetadataForWordlistId(clientId, wordlistId) ?: return null
        try {
            val status = wordList.getAsInteger(MetadataDbHelper.STATUS_COLUMN)
            if (MetadataDbHelper.STATUS_DELETING == status) { // This will return an empty file (R.raw.empty points at an empty dictionary)
// This is how we "delete" the files. It allows Android Keyboard to fake deleting
// a default dictionary - which is actually in its assets and can't be really
// deleted.
                return context!!.resources.openRawResourceFd(
                        R.raw.empty)
            }
            val localFilename = wordList.getAsString(MetadataDbHelper.LOCAL_FILENAME_COLUMN)
            val f = context!!.getFileStreamPath(localFilename)
            val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
            return AssetFileDescriptor(pfd, 0, pfd.statSize)
        } catch (e: FileNotFoundException) { // No file : fall through and return null
        }
        return null
    }

    /**
     * Reads the metadata and returns the collection of dictionaries for a given locale.
     *
     * Word list IDs are expected to be in the form category:manual_id. This method
     * will select only one word list for each category: the one with the most specific
     * locale matching the locale specified in the URI. The manual id serves only to
     * distinguish a word list from another for the purpose of updating, and is arbitrary
     * but may not contain a colon.
     *
     * @param clientId the ID of the client requesting the list
     * @param locale the locale for which we want the list, as a String
     * @return a collection of ids. It is guaranteed to be non-null, but may be empty.
     */
    private fun getDictionaryWordListsForLocale(clientId: String?,
                                                locale: String?): Collection<WordListInfo> {
        val context = context
        val results: Cursor = MetadataDbHelper.queryInstalledOrDeletingOrAvailableDictionaryMetadata(context,
                clientId)
        return try {
            val dicts = HashMap<String, WordListInfo>()
            val idIndex = results.getColumnIndex(MetadataDbHelper.WORDLISTID_COLUMN)
            val localeIndex = results.getColumnIndex(MetadataDbHelper.LOCALE_COLUMN)
            val localFileNameIndex = results.getColumnIndex(MetadataDbHelper.LOCAL_FILENAME_COLUMN)
            val rawChecksumIndex = results.getColumnIndex(MetadataDbHelper.RAW_CHECKSUM_COLUMN)
            val statusIndex = results.getColumnIndex(MetadataDbHelper.STATUS_COLUMN)
            if (results.moveToFirst()) {
                do {
                    val wordListId = results.getString(idIndex)
                    if (TextUtils.isEmpty(wordListId)) continue
                    val wordListIdArray = TextUtils.split(wordListId, ID_CATEGORY_SEPARATOR)
                    val wordListCategory: String
                    // This is at the category:manual_id format.
                    wordListCategory = wordListIdArray[0]
                    val wordListLocale = results.getString(localeIndex)
                    val wordListLocalFilename = results.getString(localFileNameIndex)
                    val wordListRawChecksum = results.getString(rawChecksumIndex)
                    val wordListStatus = results.getInt(statusIndex)
                    // Test the requested locale against this wordlist locale. The requested locale
// has to either match exactly or be more specific than the dictionary - a
// dictionary for "en" would match both a request for "en" or for "en_US", but a
// dictionary for "en_GB" would not match a request for "en_US". Thus if all
// three of "en" "en_US" and "en_GB" dictionaries are installed, a request for
// "en_US" would match "en" and "en_US", and a request for "en" only would only
// match the generic "en" dictionary. For more details, see the documentation
// for LocaleUtils#getMatchLevel.
                    val matchLevel = LocaleUtils.getMatchLevel(wordListLocale, locale)
                    if (!LocaleUtils.isMatch(matchLevel)) { // The locale of this wordlist does not match the required locale.
// Skip this wordlist and go to the next.
                        continue
                    }
                    if (MetadataDbHelper.STATUS_INSTALLED == wordListStatus) { // If the file does not exist, it has been deleted and the IME should
// already have it. Do not return it. However, this only applies if the
// word list is INSTALLED, for if it is DELETING we should return it always
// so that Android Keyboard can perform the actual deletion.
                        val f = getContext()!!.getFileStreamPath(wordListLocalFilename)
                        if (!f.isFile) {
                            continue
                        }
                    }
                    val currentBestMatch = dicts[wordListCategory]
                    if (null == currentBestMatch
                            || currentBestMatch.mMatchLevel < matchLevel) {
                        dicts[wordListCategory] = WordListInfo(wordListId, wordListLocale,
                                wordListRawChecksum, matchLevel)
                    }
                } while (results.moveToNext())
            }
            Collections.unmodifiableCollection(dicts.values)
        } finally {
            results.close()
        }
    }

    /**
     * Deletes the file pointed by Uri, as returned by openAssetFile.
     *
     * @param uri the URI the file is for.
     * @param selection ignored
     * @param selectionArgs ignored
     * @return the number of files deleted (0 or 1 in the current implementation)
     * @see android.content.ContentProvider.delete
     */
    @Throws(UnsupportedOperationException::class)
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        val match = matchUri(uri)
        if (DICTIONARY_V1_DICT_INFO == match || DICTIONARY_V2_DATAFILE == match) {
            return deleteDataFile(uri)
        }
        return if (DICTIONARY_V2_METADATA == match) {
            if (MetadataDbHelper.deleteClient(context, getClientId(uri))) {
                1
            } else 0
        } else 0
        // Unsupported URI for delete
    }

    private fun deleteDataFile(uri: Uri): Int {
        val wordlistId = uri.lastPathSegment
        val clientId = getClientId(uri)
        val wordList = getWordlistMetadataForWordlistId(clientId, wordlistId) ?: return 0
        val status = wordList.getAsInteger(MetadataDbHelper.STATUS_COLUMN)
        val version = wordList.getAsInteger(MetadataDbHelper.VERSION_COLUMN)
        return 0
    }

    /**
     * Insert data into the provider. May be either a metadata source URL or some dictionary info.
     *
     * @param uri the designated content URI. See sUriMatcherV{1,2} for available URIs.
     * @param values the values to insert for this content uri
     * @return the URI for the newly inserted item. May be null if arguments don't allow for insert
     */
    @Throws(UnsupportedOperationException::class)
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (null == values) return null // Should never happen but let's be safe
        PrivateLog.log("Insert, uri = $uri")
        val clientId = getClientId(uri)
        when (matchUri(uri)) {
            DICTIONARY_V2_METADATA ->  // The values should contain a valid client ID and a valid URI for the metadata.
// The client ID may not be null, nor may it be empty because the empty client ID
// is reserved for internal use.
// The metadata URI may not be null, but it may be empty if the client does not
// want the dictionary pack to update the metadata automatically.
                MetadataDbHelper.updateClientInfo(context, clientId, values)
            DICTIONARY_V2_DICT_INFO -> try {
                val newDictionaryMetadata: WordListMetadata = WordListMetadata.createFromContentValues(
                        MetadataDbHelper.completeWithDefaultValues(values))
                MarkPreInstalledAction(clientId, newDictionaryMetadata)
                        .execute(context)
            } catch (e: BadFormatException) {
                Log.w(TAG, "Not enough information to insert this dictionary $values", e)
            }
            DICTIONARY_V1_WHOLE_LIST, DICTIONARY_V1_DICT_INFO -> {
                PrivateLog.log("Attempt to insert : $uri")
                throw UnsupportedOperationException(
                        "Insertion in the dictionary is not supported in this version")
            }
        }
        return uri
    }

    /**
     * Updating data is not supported, and will throw an exception.
     * @see android.content.ContentProvider.update
     * @see android.content.ContentProvider.insert
     */
    @Throws(UnsupportedOperationException::class)
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<String>?): Int {
        PrivateLog.log("Attempt to update : $uri")
        throw UnsupportedOperationException("Updating dictionary words is not supported")
    }
}