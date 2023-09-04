package org.dslul.openboard.inputmethod.dictionarypack

import android.content.Context
import android.preference.Preference
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import org.dslul.openboard.inputmethod.latin.R
import java.util.*

/**
 * A preference for one word list.
 *
 * This preference refers to a single word list, as available in the dictionary
 * pack. Upon being pressed, it displays a menu to allow the user to install, disable,
 * enable or delete it as appropriate for the current state of the word list.
 */
@Suppress("deprecation")
class WordListPreference(context: Context?,
                         private val mInterfaceState: DictionaryListInterfaceState, // The id of the client for which this preference is.
                         private val mClientId: String?,
        // Members
// The metadata word list id and version of this word list.
                         val mWordlistId: String, val mVersion: Int, val mLocale: Locale,
                         val mDescription: String, status: Int, // The size of the dictionary file
                         private val mFilesize: Int) : Preference(context, null) {
    // The status
    private var mStatus = 0

    fun setStatus(status: Int) {
        if (status == mStatus) return
        mStatus = status
        summary = getSummary(status)
    }

    fun hasStatus(status: Int): Boolean {
        return status == mStatus
    }

    public override fun onCreateView(parent: ViewGroup): View {
        val orphanedView = mInterfaceState.findFirstOrphanedView()
        if (null != orphanedView) return orphanedView // Will be sent to onBindView
        val newView = super.onCreateView(parent)
        return mInterfaceState.addToCacheAndReturnView(newView)
    }

    fun hasPriorityOver(otherPrefStatus: Int): Boolean { // Both of these should be one of MetadataDbHelper.STATUS_*
        return mStatus > otherPrefStatus
    }

    private fun getSummary(status: Int): String {
        val context = context
        return when (status) {
            MetadataDbHelper.STATUS_DELETING, MetadataDbHelper.STATUS_AVAILABLE -> context.getString(R.string.dictionary_available)
            MetadataDbHelper.STATUS_DOWNLOADING -> context.getString(R.string.dictionary_downloading)
            MetadataDbHelper.STATUS_INSTALLED -> context.getString(R.string.dictionary_installed)
            MetadataDbHelper.STATUS_DISABLED -> context.getString(R.string.dictionary_disabled)
            else -> NO_STATUS_MESSAGE
        }
    }

    private fun disableDict() {
        val context = context
        val prefs = CommonPreferences.getCommonPreferences(context)
        CommonPreferences.disable(prefs, mWordlistId)
        if (MetadataDbHelper.STATUS_DOWNLOADING == mStatus) {
            setStatus(MetadataDbHelper.STATUS_AVAILABLE)
        } else if (MetadataDbHelper.STATUS_INSTALLED == mStatus) { // Interface-wise, we should no longer be able to come here. However, this is still
// the right thing to do if we do come here.
            setStatus(MetadataDbHelper.STATUS_DISABLED)
        } else {
            Log.e(TAG, "Unexpected state of the word list for disabling $mStatus")
        }
    }

    private fun enableDict() {
        val context = context
        val prefs = CommonPreferences.getCommonPreferences(context)
        CommonPreferences.enable(prefs, mWordlistId)
        if (MetadataDbHelper.STATUS_AVAILABLE == mStatus) {
            setStatus(MetadataDbHelper.STATUS_DOWNLOADING)
        } else if (MetadataDbHelper.STATUS_DISABLED == mStatus
                || MetadataDbHelper.STATUS_DELETING == mStatus) { // If the status is DELETING, it means Android Keyboard
// has not deleted the word list yet, so we can safely
// turn it to 'installed'. The status DISABLED is still supported internally to
// avoid breaking older installations and all but there should not be a way to
// disable a word list through the interface any more.
            setStatus(MetadataDbHelper.STATUS_INSTALLED)
        } else {
            Log.e(TAG, "Unexpected state of the word list for enabling $mStatus")
        }
    }

    private fun deleteDict() {
        val context = context
        val prefs = CommonPreferences.getCommonPreferences(context)
        CommonPreferences.disable(prefs, mWordlistId)
        setStatus(MetadataDbHelper.STATUS_DELETING)
    }

    override fun onBindView(view: View) {
        super.onBindView(view)
        (view as ViewGroup).layoutTransition = null
        val buttonSwitcher = view.findViewById<View>(
                R.id.wordlist_button_switcher) as ButtonSwitcher
        // We need to clear the state of the button switcher, because we reuse views; if we didn't
// reset it would animate from whatever its old state was.
        buttonSwitcher.reset(mInterfaceState)
        if (mInterfaceState.isOpen(mWordlistId)) { // The button is open.
            val previousStatus = mInterfaceState.getStatus(mWordlistId)
            buttonSwitcher.setStatusAndUpdateVisuals(getButtonSwitcherStatus(previousStatus))
            if (previousStatus != mStatus) { // We come here if the status has changed since last time. We need to animate
// the transition.
                buttonSwitcher.setStatusAndUpdateVisuals(getButtonSwitcherStatus(mStatus))
                mInterfaceState.setOpen(mWordlistId, mStatus)
            }
        } else { // The button is closed.
            buttonSwitcher.setStatusAndUpdateVisuals(ButtonSwitcher.STATUS_NO_BUTTON)
        }
        buttonSwitcher.setInternalOnClickListener(View.OnClickListener { onActionButtonClicked() })
        view.setOnClickListener { v -> onWordListClicked(v) }
    }

    fun onWordListClicked(v: View) { // Note : v is the preference view
        val listView = v.parent as? ListView ?: return
        // Just in case something changed in the framework, test for the concrete class
        // Close all first, we'll open back any item that needs to be open.
        val wasOpen = mInterfaceState.isOpen(mWordlistId)
        mInterfaceState.closeAll()
        val indexToOpen = if (wasOpen) { // This button being shown. Take note that we don't want to open any button in the
// loop below.
            -1
        } else { // This button was not being shown. Open it, and remember the index of this
// child as the one to open in the following loop.
            mInterfaceState.setOpen(mWordlistId, mStatus)
            listView.indexOfChild(v)
        }
        val lastDisplayedIndex = listView.lastVisiblePosition - listView.firstVisiblePosition
        // The "lastDisplayedIndex" is actually displayed, hence the <=
        for (i in 0..lastDisplayedIndex) {
            val buttonSwitcher = listView.getChildAt(i)
                    .findViewById<View>(R.id.wordlist_button_switcher) as ButtonSwitcher
            if (i == indexToOpen) {
                buttonSwitcher.setStatusAndUpdateVisuals(getButtonSwitcherStatus(mStatus))
            } else {
                buttonSwitcher.setStatusAndUpdateVisuals(ButtonSwitcher.Companion.STATUS_NO_BUTTON)
            }
        }
    }

    fun onActionButtonClicked() {
        when (getActionIdFromStatusAndMenuEntry(mStatus)) {
            ACTION_ENABLE_DICT -> enableDict()
            ACTION_DISABLE_DICT -> disableDict()
            ACTION_DELETE_DICT -> deleteDict()
            else -> Log.e(TAG, "Unknown menu item pressed")
        }
    }

    companion object {
        private val TAG = WordListPreference::class.java.simpleName
        // What to display in the "status" field when we receive unknown data as a status from
// the content provider. Empty string sounds sensible.
        private const val NO_STATUS_MESSAGE = ""
        /// Actions
        private const val ACTION_UNKNOWN = 0
        private const val ACTION_ENABLE_DICT = 1
        private const val ACTION_DISABLE_DICT = 2
        private const val ACTION_DELETE_DICT = 3
        // The table below needs to be kept in sync with MetadataDbHelper.STATUS_* since it uses
// the values as indices.
        private val sStatusActionList = arrayOf(intArrayOf(), intArrayOf(ButtonSwitcher.STATUS_INSTALL, ACTION_ENABLE_DICT), intArrayOf(ButtonSwitcher.Companion.STATUS_CANCEL, ACTION_DISABLE_DICT), intArrayOf(ButtonSwitcher.Companion.STATUS_DELETE, ACTION_DELETE_DICT), intArrayOf(ButtonSwitcher.Companion.STATUS_DELETE, ACTION_DELETE_DICT), intArrayOf(ButtonSwitcher.Companion.STATUS_INSTALL, ACTION_ENABLE_DICT))

        fun getButtonSwitcherStatus(status: Int): Int {
            if (status >= sStatusActionList.size) {
                Log.e(TAG, "Unknown status $status")
                return ButtonSwitcher.STATUS_NO_BUTTON
            }
            return sStatusActionList[status][0]
        }

        fun getActionIdFromStatusAndMenuEntry(status: Int): Int {
            if (status >= sStatusActionList.size) {
                Log.e(TAG, "Unknown status $status")
                return ACTION_UNKNOWN
            }
            return sStatusActionList[status][1]
        }
    }

    init {
        layoutResource = R.layout.dictionary_line
        title = mDescription
        setStatus(status)
        key = mWordlistId
    }
}