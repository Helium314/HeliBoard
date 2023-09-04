package org.dslul.openboard.inputmethod.dictionarypack

import android.content.ContentValues

/**
 * The metadata for a single word list.
 *
 * Instances of this class are always immutable.
 */
class WordListMetadata // In milliseconds
(val mId: String?, // Type, as of MetadataDbHelper#TYPE_*
 val mType: Int,
 val mDescription: String?, val mLastUpdate: Long, val mFileSize: Long,
 val mRawChecksum: String?, val mChecksum: String?, var mRetryCount: Int,
 val mLocalFilename: String?, val mRemoteFilename: String?,
        // version of this word list
 val mVersion: Int, // Version number of the format.
// This implementation of the DictionaryDataService knows how to handle format 1 only.
// This is only for forward compatibility, to be able to upgrade the format without
// breaking old implementations.
 val mFormatVersion: Int,
        // Always 0 in this version, reserved for future use
 val mFlags: Int, // The locale is matched against the locale requested by the client. The matching algorithm
// is a standard locale matching with fallback; it is implemented in
// DictionaryProvider#getDictionaryFileForContentUri.
 val mLocale: String?) {

    override fun toString(): String {
        val sb = StringBuilder(WordListMetadata::class.java.simpleName)
        sb.append(" : ").append(mId)
        sb.append("\nType : ").append(mType)
        sb.append("\nDescription : ").append(mDescription)
        sb.append("\nLastUpdate : ").append(mLastUpdate)
        sb.append("\nFileSize : ").append(mFileSize)
        sb.append("\nRawChecksum : ").append(mRawChecksum)
        sb.append("\nChecksum : ").append(mChecksum)
        sb.append("\nRetryCount: ").append(mRetryCount)
        sb.append("\nLocalFilename : ").append(mLocalFilename)
        sb.append("\nRemoteFilename : ").append(mRemoteFilename)
        sb.append("\nVersion : ").append(mVersion)
        sb.append("\nFormatVersion : ").append(mFormatVersion)
        sb.append("\nFlags : ").append(mFlags)
        sb.append("\nLocale : ").append(mLocale)
        return sb.toString()
    }

    companion object {
        /**
         * Create a WordListMetadata from the contents of a ContentValues.
         *
         * If this lacks any required field, IllegalArgumentException is thrown.
         */
        fun createFromContentValues(values: ContentValues): WordListMetadata {
            val id = values.getAsString(MetadataDbHelper.WORDLISTID_COLUMN)
            val type = values.getAsInteger(MetadataDbHelper.TYPE_COLUMN)
            val description = values.getAsString(MetadataDbHelper.DESCRIPTION_COLUMN)
            val lastUpdate = values.getAsLong(MetadataDbHelper.DATE_COLUMN)
            val fileSize = values.getAsLong(MetadataDbHelper.FILESIZE_COLUMN)
            val rawChecksum = values.getAsString(MetadataDbHelper.RAW_CHECKSUM_COLUMN)
            val checksum = values.getAsString(MetadataDbHelper.CHECKSUM_COLUMN)
            val retryCount = values.getAsInteger(MetadataDbHelper.RETRY_COUNT_COLUMN)
            val localFilename = values.getAsString(MetadataDbHelper.LOCAL_FILENAME_COLUMN)
            val remoteFilename = values.getAsString(MetadataDbHelper.REMOTE_FILENAME_COLUMN)
            val version = values.getAsInteger(MetadataDbHelper.VERSION_COLUMN)
            val formatVersion = values.getAsInteger(MetadataDbHelper.FORMATVERSION_COLUMN)
            val flags = values.getAsInteger(MetadataDbHelper.FLAGS_COLUMN)
            val locale = values.getAsString(MetadataDbHelper.LOCALE_COLUMN)
            require(!(null == id || null == type || null == description || null == lastUpdate || null == fileSize || null == checksum || null == localFilename || null == remoteFilename || null == version || null == formatVersion || null == flags || null == locale))
            return WordListMetadata(id, type, description, lastUpdate, fileSize, rawChecksum,
                    checksum, retryCount, localFilename, remoteFilename, version, formatVersion,
                    flags, locale)
        }
    }

}