package org.dslul.openboard.inputmethod.dictionarypack

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.text.TextUtils
import android.util.Log
import org.dslul.openboard.inputmethod.dictionarypack.ActionBatch.DisableAction
import org.dslul.openboard.inputmethod.dictionarypack.ActionBatch.EnableAction
import org.dslul.openboard.inputmethod.dictionarypack.ActionBatch.FinishDeleteAction
import org.dslul.openboard.inputmethod.dictionarypack.ActionBatch.ForgetAction
import org.dslul.openboard.inputmethod.dictionarypack.ActionBatch.MakeAvailableAction
import org.dslul.openboard.inputmethod.dictionarypack.ActionBatch.MarkPreInstalledAction
import org.dslul.openboard.inputmethod.dictionarypack.ActionBatch.StartDeleteAction
import org.dslul.openboard.inputmethod.dictionarypack.ActionBatch.UpdateDataAction
import org.dslul.openboard.inputmethod.latin.utils.DebugLogUtils
import java.util.*

/**
 * Object representing an upgrade from one state to another.
 *
 * This implementation basically encapsulates a list of Runnable objects. In the future
 * it may manage dependencies between them. Concretely, it does not use Runnable because the
 * actions need an argument.
 */
/*

The state of a word list follows the following scheme.

       |                                   ^
  MakeAvailable                            |
       |        .------------Forget--------'
       V        |
 STATUS_AVAILABLE  <-------------------------.
       |                                     |
StartDownloadAction                  FinishDeleteAction
       |                                     |
       V                                     |
STATUS_DOWNLOADING      EnableAction-- STATUS_DELETING
       |                     |               ^
InstallAfterDownloadAction   |               |
       |     .---------------'        StartDeleteAction
       |     |                               |
       V     V                               |
 STATUS_INSTALLED  <--EnableAction--   STATUS_DISABLED
                    --DisableAction-->

  It may also be possible that DisableAction or StartDeleteAction or
  DownloadAction run when the file is still downloading.  This cancels
  the download and returns to STATUS_AVAILABLE.
  Also, an UpdateDataAction may apply in any state. It does not affect
  the state in any way (nor type, local filename, id or version) but
  may update other attributes like description or remote filename.

  Forget is an DB maintenance action that removes the entry if it is not installed or disabled.
  This happens when the word list information disappeared from the server, or when a new version
  is available and we should forget about the old one.
*/
class ActionBatch {
    /**
     * A piece of update.
     *
     * Action is basically like a Runnable that takes an argument.
     */
    interface Action {
        /**
         * Execute this action NOW.
         * @param context the context to get system services, resources, databases
         */
        fun execute(context: Context?)
    }

    /**
     * An action that enables an existing word list.
     */
    class EnableAction(clientId: String, wordList: WordListMetadata?) : Action {
        private val mClientId: String
        // The state to upgrade from. May not be null.
        val mWordList: WordListMetadata?

        override fun execute(context: Context?) {
            if (null == mWordList) {
                Log.e(TAG, "EnableAction with a null parameter!")
                return
            }
            DebugLogUtils.l("Enabling word list")
            val db: SQLiteDatabase = MetadataDbHelper.getDb(context, mClientId)
            val values: ContentValues = MetadataDbHelper.getContentValuesByWordListId(db,
                    mWordList.mId, mWordList.mVersion)!!
            val status = values.getAsInteger(MetadataDbHelper.STATUS_COLUMN)
            if (MetadataDbHelper.STATUS_DISABLED != status
                    && MetadataDbHelper.STATUS_DELETING != status) {
                Log.e(TAG, "Unexpected state of the word list '" + mWordList.mId + " : " + status
                        + " for an enable action. Cancelling")
                return
            }
            MetadataDbHelper.markEntryAsEnabled(db, mWordList.mId, mWordList.mVersion)
        }

        companion object {
            val TAG = "DictionaryProvider:" + EnableAction::class.java.simpleName
        }

        init {
            DebugLogUtils.l("New EnableAction for client ", clientId, " : ", wordList)
            mClientId = clientId
            mWordList = wordList
        }
    }

    /**
     * An action that disables a word list.
     */
    class DisableAction(clientId: String, wordlist: WordListMetadata?) : Action {
        private val mClientId: String
        // The word list to disable. May not be null.
        val mWordList: WordListMetadata?

        override fun execute(context: Context?) {
            if (null == mWordList) { // This should never happen
                Log.e(TAG, "DisableAction with a null word list!")
                return
            }
            DebugLogUtils.l("Disabling word list : $mWordList")
            val db: SQLiteDatabase = MetadataDbHelper.getDb(context, mClientId)
            val values: ContentValues = MetadataDbHelper.getContentValuesByWordListId(db,
                    mWordList.mId, mWordList.mVersion)!!
            val status = values.getAsInteger(MetadataDbHelper.STATUS_COLUMN)
            if (MetadataDbHelper.STATUS_INSTALLED == status) { // Disabling an installed word list
                MetadataDbHelper.markEntryAsDisabled(db, mWordList.mId, mWordList.mVersion)
            } else {
                if (MetadataDbHelper.STATUS_DOWNLOADING != status) {
                    Log.e(TAG, "Unexpected state of the word list '" + mWordList.mId + "' : "
                            + status + " for a disable action. Fall back to marking as available.")
                }
                // The word list is still downloading. Cancel the download and revert the
// word list status to "available".
            }
        }

        companion object {
            val TAG = "DictionaryProvider:" + DisableAction::class.java.simpleName
        }

        init {
            DebugLogUtils.l("New Disable action for client ", clientId, " : ", wordlist)
            mClientId = clientId
            mWordList = wordlist
        }
    }

    /**
     * An action that makes a word list available.
     */
    class MakeAvailableAction(clientId: String, wordlist: WordListMetadata?) : Action {
        private val mClientId: String
        // The word list to make available. May not be null.
        val mWordList: WordListMetadata?

        override fun execute(context: Context?) {
            if (null == mWordList) { // This should never happen
                Log.e(TAG, "MakeAvailableAction with a null word list!")
                return
            }
            val db: SQLiteDatabase = MetadataDbHelper.getDb(context, mClientId)
            if (null != MetadataDbHelper.getContentValuesByWordListId(db,
                            mWordList.mId, mWordList.mVersion)) {
                Log.e(TAG, "Unexpected state of the word list '" + mWordList.mId + "' "
                        + " for a makeavailable action. Marking as available anyway.")
            }
            DebugLogUtils.l("Making word list available : $mWordList")
            // If mLocalFilename is null, then it's a remote file that hasn't been downloaded
// yet, so we set the local filename to the empty string.
            val values: ContentValues = MetadataDbHelper.makeContentValues(0,
                    MetadataDbHelper.TYPE_BULK, MetadataDbHelper.STATUS_AVAILABLE,
                    mWordList.mId, mWordList.mLocale, mWordList.mDescription,
                    mWordList.mLocalFilename ?: "",
                    mWordList.mRemoteFilename, mWordList.mLastUpdate, mWordList.mRawChecksum,
                    mWordList.mChecksum, mWordList.mRetryCount, mWordList.mFileSize,
                    mWordList.mVersion, mWordList.mFormatVersion)
            PrivateLog.log("Insert 'available' record for " + mWordList.mDescription
                    + " and locale " + mWordList.mLocale)
            db.insert(MetadataDbHelper.METADATA_TABLE_NAME, null, values)
        }

        companion object {
            val TAG = "DictionaryProvider:" + MakeAvailableAction::class.java.simpleName
        }

        init {
            DebugLogUtils.l("New MakeAvailable action", clientId, " : ", wordlist)
            mClientId = clientId
            mWordList = wordlist
        }
    }

    /**
     * An action that marks a word list as pre-installed.
     *
     * This is almost the same as MakeAvailableAction, as it only inserts a line with parameters
     * received from outside.
     * Unlike MakeAvailableAction, the parameters are not received from a downloaded metadata file
     * but from the client directly; it marks a word list as being "installed" and not "available".
     * It also explicitly sets the filename to the empty string, so that we don't try to open
     * it on our side.
     */
    class MarkPreInstalledAction(clientId: String?, wordlist: WordListMetadata?) : Action {
        private val mClientId: String?
        // The word list to mark pre-installed. May not be null.
        val mWordList: WordListMetadata?

        override fun execute(context: Context?) {
            if (null == mWordList) { // This should never happen
                Log.e(TAG, "MarkPreInstalledAction with a null word list!")
                return
            }
            val db: SQLiteDatabase = MetadataDbHelper.getDb(context, mClientId)
            if (null != MetadataDbHelper.getContentValuesByWordListId(db,
                            mWordList.mId, mWordList.mVersion)) {
                Log.e(TAG, "Unexpected state of the word list '" + mWordList.mId + "' "
                        + " for a markpreinstalled action. Marking as preinstalled anyway.")
            }
            DebugLogUtils.l("Marking word list preinstalled : $mWordList")
            // This word list is pre-installed : we don't have its file. We should reset
// the local file name to the empty string so that we don't try to open it
// accidentally. The remote filename may be set by the application if it so wishes.
            val values: ContentValues = MetadataDbHelper.makeContentValues(0,
                    MetadataDbHelper.TYPE_BULK, MetadataDbHelper.STATUS_INSTALLED,
                    mWordList.mId, mWordList.mLocale, mWordList.mDescription,
                    if (TextUtils.isEmpty(mWordList.mLocalFilename)) "" else mWordList.mLocalFilename,
                    mWordList.mRemoteFilename, mWordList.mLastUpdate,
                    mWordList.mRawChecksum, mWordList.mChecksum, mWordList.mRetryCount,
                    mWordList.mFileSize, mWordList.mVersion, mWordList.mFormatVersion)
            PrivateLog.log("Insert 'preinstalled' record for " + mWordList.mDescription
                    + " and locale " + mWordList.mLocale)
            db.insert(MetadataDbHelper.METADATA_TABLE_NAME, null, values)
        }

        companion object {
            val TAG = ("DictionaryProvider:"
                    + MarkPreInstalledAction::class.java.simpleName)
        }

        init {
            DebugLogUtils.l("New MarkPreInstalled action", clientId, " : ", wordlist)
            mClientId = clientId
            mWordList = wordlist
        }
    }

    /**
     * An action that updates information about a word list - description, locale etc
     */
    class UpdateDataAction(clientId: String, wordlist: WordListMetadata?) : Action {
        private val mClientId: String
        val mWordList: WordListMetadata?
        override fun execute(context: Context?) {
            if (null == mWordList) { // This should never happen
                Log.e(TAG, "UpdateDataAction with a null word list!")
                return
            }
            val db: SQLiteDatabase = MetadataDbHelper.getDb(context, mClientId)
            val oldValues: ContentValues = MetadataDbHelper.getContentValuesByWordListId(db,
                    mWordList.mId, mWordList.mVersion)!!
            if (null == oldValues) {
                Log.e(TAG, "Trying to update data about a non-existing word list. Bailing out.")
                return
            }
            DebugLogUtils.l("Updating data about a word list : $mWordList")
            val values: ContentValues = MetadataDbHelper.makeContentValues(
                    oldValues.getAsInteger(MetadataDbHelper.PENDINGID_COLUMN),
                    oldValues.getAsInteger(MetadataDbHelper.TYPE_COLUMN),
                    oldValues.getAsInteger(MetadataDbHelper.STATUS_COLUMN),
                    mWordList.mId, mWordList.mLocale, mWordList.mDescription,
                    oldValues.getAsString(MetadataDbHelper.LOCAL_FILENAME_COLUMN),
                    mWordList.mRemoteFilename, mWordList.mLastUpdate, mWordList.mRawChecksum,
                    mWordList.mChecksum, mWordList.mRetryCount, mWordList.mFileSize,
                    mWordList.mVersion, mWordList.mFormatVersion)
            PrivateLog.log("Updating record for " + mWordList.mDescription
                    + " and locale " + mWordList.mLocale)
            db.update(MetadataDbHelper.METADATA_TABLE_NAME, values,
                    MetadataDbHelper.WORDLISTID_COLUMN + " = ? AND "
                            + MetadataDbHelper.VERSION_COLUMN + " = ?", arrayOf(mWordList.mId, Integer.toString(mWordList.mVersion)))
        }

        companion object {
            val TAG = "DictionaryProvider:" + UpdateDataAction::class.java.simpleName
        }

        init {
            DebugLogUtils.l("New UpdateData action for client ", clientId, " : ", wordlist)
            mClientId = clientId
            mWordList = wordlist
        }
    }

    /**
     * An action that deletes the metadata about a word list if possible.
     *
     * This is triggered when a specific word list disappeared from the server, or when a fresher
     * word list is available and the old one was not installed.
     * If the word list has not been installed, it's possible to delete its associated metadata.
     * Otherwise, the settings are retained so that the user can still administrate it.
     */
    class ForgetAction(clientId: String, wordlist: WordListMetadata?,
                       hasNewerVersion: Boolean) : Action {
        private val mClientId: String
        // The word list to remove. May not be null.
        val mWordList: WordListMetadata?
        val mHasNewerVersion: Boolean
        override fun execute(context: Context?) {
            if (null == mWordList) { // This should never happen
                Log.e(TAG, "TryRemoveAction with a null word list!")
                return
            }
            DebugLogUtils.l("Trying to remove word list : $mWordList")
            val db: SQLiteDatabase = MetadataDbHelper.getDb(context, mClientId)
            val values: ContentValues = MetadataDbHelper.getContentValuesByWordListId(db,
                    mWordList.mId, mWordList.mVersion)!!
            if (null == values) {
                Log.e(TAG, "Trying to update the metadata of a non-existing wordlist. Cancelling.")
                return
            }
            val status = values.getAsInteger(MetadataDbHelper.STATUS_COLUMN)
            if (mHasNewerVersion && MetadataDbHelper.STATUS_AVAILABLE != status) { // If we have a newer version of this word list, we should be here ONLY if it was
// not installed - else we should be upgrading it.
                Log.e(TAG, "Unexpected status for forgetting a word list info : " + status
                        + ", removing URL to prevent re-download")
            }
            if (MetadataDbHelper.STATUS_INSTALLED == status || MetadataDbHelper.STATUS_DISABLED == status || MetadataDbHelper.STATUS_DELETING == status) { // If it is installed or disabled, we need to mark it as deleted so that LatinIME
// will remove it next time it enquires for dictionaries.
// If it is deleting and we don't have a new version, then we have to wait until
// LatinIME actually has deleted it before we can remove its metadata.
// In both cases, remove the URI from the database since it is not supposed to
// be accessible any more.
                values.put(MetadataDbHelper.REMOTE_FILENAME_COLUMN, "")
                values.put(MetadataDbHelper.STATUS_COLUMN, MetadataDbHelper.STATUS_DELETING)
                db.update(MetadataDbHelper.METADATA_TABLE_NAME, values,
                        MetadataDbHelper.WORDLISTID_COLUMN + " = ? AND "
                                + MetadataDbHelper.VERSION_COLUMN + " = ?", arrayOf(mWordList.mId, Integer.toString(mWordList.mVersion)))
            } else { // If it's AVAILABLE or DOWNLOADING or even UNKNOWN, delete the entry.
                db.delete(MetadataDbHelper.METADATA_TABLE_NAME,
                        MetadataDbHelper.WORDLISTID_COLUMN + " = ? AND "
                                + MetadataDbHelper.VERSION_COLUMN + " = ?", arrayOf(mWordList.mId, Integer.toString(mWordList.mVersion)))
            }
        }

        companion object {
            val TAG = "DictionaryProvider:" + ForgetAction::class.java.simpleName
        }

        init {
            DebugLogUtils.l("New TryRemove action for client ", clientId, " : ", wordlist)
            mClientId = clientId
            mWordList = wordlist
            mHasNewerVersion = hasNewerVersion
        }
    }

    /**
     * An action that sets the word list for deletion as soon as possible.
     *
     * This is triggered when the user requests deletion of a word list. This will mark it as
     * deleted in the database, and fire an intent for Android Keyboard to take notice and
     * reload its dictionaries right away if it is up. If it is not up now, then it will
     * delete the actual file the next time it gets up.
     * A file marked as deleted causes the content provider to supply a zero-sized file to
     * Android Keyboard, which will overwrite any existing file and provide no words for this
     * word list. This is not exactly a "deletion", since there is an actual file which takes up
     * a few bytes on the disk, but this allows to override a default dictionary with an empty
     * dictionary. This way, there is no need for the user to make a distinction between
     * dictionaries installed by default and add-on dictionaries.
     */
    class StartDeleteAction(clientId: String, wordlist: WordListMetadata?) : Action {
        private val mClientId: String
        // The word list to delete. May not be null.
        val mWordList: WordListMetadata?

        override fun execute(context: Context?) {
            if (null == mWordList) { // This should never happen
                Log.e(TAG, "StartDeleteAction with a null word list!")
                return
            }
            DebugLogUtils.l("Trying to delete word list : $mWordList")
            val db: SQLiteDatabase = MetadataDbHelper.getDb(context, mClientId)
            val values: ContentValues = MetadataDbHelper.getContentValuesByWordListId(db,
                    mWordList.mId, mWordList.mVersion)!!
            if (null == values) {
                Log.e(TAG, "Trying to set a non-existing wordlist for removal. Cancelling.")
                return
            }
            val status = values.getAsInteger(MetadataDbHelper.STATUS_COLUMN)
            if (MetadataDbHelper.STATUS_DISABLED != status) {
                Log.e(TAG, "Unexpected status for deleting a word list info : $status")
            }
            MetadataDbHelper.markEntryAsDeleting(db, mWordList.mId, mWordList.mVersion)
        }

        companion object {
            val TAG = "DictionaryProvider:" + StartDeleteAction::class.java.simpleName
        }

        init {
            DebugLogUtils.l("New StartDelete action for client ", clientId, " : ", wordlist)
            mClientId = clientId
            mWordList = wordlist
        }
    }

    /**
     * An action that validates a word list as deleted.
     *
     * This will restore the word list as available if it still is, or remove the entry if
     * it is not any more.
     */
    class FinishDeleteAction(clientId: String, wordlist: WordListMetadata?) : Action {
        private val mClientId: String
        // The word list to delete. May not be null.
        val mWordList: WordListMetadata?

        override fun execute(context: Context?) {
            if (null == mWordList) { // This should never happen
                Log.e(TAG, "FinishDeleteAction with a null word list!")
                return
            }
            DebugLogUtils.l("Trying to delete word list : $mWordList")
            val db: SQLiteDatabase = MetadataDbHelper.getDb(context, mClientId)
            val values: ContentValues = MetadataDbHelper.getContentValuesByWordListId(db,
                    mWordList.mId, mWordList.mVersion)!!
            if (null == values) {
                Log.e(TAG, "Trying to set a non-existing wordlist for removal. Cancelling.")
                return
            }
            val status = values.getAsInteger(MetadataDbHelper.STATUS_COLUMN)
            if (MetadataDbHelper.STATUS_DELETING != status) {
                Log.e(TAG, "Unexpected status for finish-deleting a word list info : $status")
            }
            val remoteFilename = values.getAsString(MetadataDbHelper.REMOTE_FILENAME_COLUMN)
            // If there isn't a remote filename any more, then we don't know where to get the file
// from any more, so we remove the entry entirely. As a matter of fact, if the file was
// marked DELETING but disappeared from the metadata on the server, it ended up
// this way.
            if (TextUtils.isEmpty(remoteFilename)) {
                db.delete(MetadataDbHelper.METADATA_TABLE_NAME,
                        MetadataDbHelper.WORDLISTID_COLUMN + " = ? AND "
                                + MetadataDbHelper.VERSION_COLUMN + " = ?", arrayOf(mWordList.mId, Integer.toString(mWordList.mVersion)))
            } else {
                MetadataDbHelper.markEntryAsAvailable(db, mWordList.mId, mWordList.mVersion)
            }
        }

        companion object {
            val TAG = "DictionaryProvider:" + FinishDeleteAction::class.java.simpleName
        }

        init {
            DebugLogUtils.l("New FinishDelete action for client", clientId, " : ", wordlist)
            mClientId = clientId
            mWordList = wordlist
        }
    }

    // An action batch consists of an ordered queue of Actions that can execute.
    private val mActions: Queue<Action>

    fun add(a: Action) {
        mActions.add(a)
    }

    /**
     * Append all the actions of another action batch.
     * @param that the upgrade to merge into this one.
     */
    fun append(that: ActionBatch) {
        for (a in that.mActions) {
            add(a)
        }
    }

    /**
     * Execute this batch.
     *
     * @param context the context for getting resources, databases, system services.
     * @param reporter a Reporter to send errors to.
     */
    fun execute(context: Context?, reporter: ProblemReporter?) {
        DebugLogUtils.l("Executing a batch of actions")
        val remainingActions = mActions
        while (!remainingActions.isEmpty()) {
            val a = remainingActions.poll()
            try {
                a.execute(context)
            } catch (e: Exception) {
                reporter?.report(e)
            }
        }
    }

    init {
        mActions = LinkedList()
    }
}