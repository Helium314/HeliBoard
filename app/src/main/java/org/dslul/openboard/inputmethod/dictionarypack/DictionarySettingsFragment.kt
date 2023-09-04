package org.dslul.openboard.inputmethod.dictionarypack

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceFragment
import android.preference.PreferenceGroup
import android.util.Log
import android.view.*
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils
import java.util.*

/**
 * Preference screen.
 */
// keep the fragment for now, as it may contain something useful on dictionary management, but
// suppress warnings
// todo: check this fragment, take what could be useful, and remove the fragment
//  same for WordListPreference and WordListMetadata
@Suppress("deprecation")
class DictionarySettingsFragment
/**
 * Empty constructor for fragment generation.
 */
    : PreferenceFragment() {
    private var mLoadingView: View? = null
    private var mClientId: String? = null
    //private ConnectivityManager mConnectivityManager;
    private val mUpdateNowMenu: MenuItem? = null
    private var mChangedSettings = false
    private val mDictionaryListInterfaceState = DictionaryListInterfaceState()
    // never null
    private var mCurrentPreferenceMap = TreeMap<String, WordListPreference>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.loading_page, container, true)
        mLoadingView = v.findViewById(R.id.loading_container)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val activity = activity
        mClientId = activity.intent.getStringExtra(DICT_SETTINGS_FRAGMENT_CLIENT_ID_ARGUMENT)
        /*mConnectivityManager =
                (ConnectivityManager)activity.getSystemService(Context.CONNECTIVITY_SERVICE);*/addPreferencesFromResource(R.xml.dictionary_settings)
        refreshInterface()
        setHasOptionsMenu(true)
    }

    override fun onResume() {
        super.onResume()
        mChangedSettings = false
        val activity = activity
        val filter = IntentFilter()
        object : Thread("onResume") {
            override fun run() {
                if (!MetadataDbHelper.Companion.isClientKnown(activity, mClientId)) {
                    Log.i(TAG, "Unknown dictionary pack client: " + mClientId
                            + ". Requesting info.")
                    val unknownClientBroadcast = Intent(DictionaryPackConstants.UNKNOWN_DICTIONARY_PROVIDER_CLIENT)
                    unknownClientBroadcast.putExtra(
                            DictionaryPackConstants.DICTIONARY_PROVIDER_CLIENT_EXTRA, mClientId)
                    activity.sendBroadcast(unknownClientBroadcast)
                }
            }
        }.start()
    }

    override fun onPause() {
        super.onPause()
        val activity = activity
        if (mChangedSettings) {
            val newDictBroadcast = Intent(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION)
            activity.sendBroadcast(newDictBroadcast)
            mChangedSettings = false
        }
    }

    private fun findWordListPreference(id: String): WordListPreference? {
        val prefScreen: PreferenceGroup? = preferenceScreen
        if (null == prefScreen) {
            Log.e(TAG, "Could not find the preference group")
            return null
        }
        for (i in prefScreen.preferenceCount - 1 downTo 0) {
            val pref = prefScreen.getPreference(i)
            if (pref is WordListPreference) {
                val wlPref = pref
                if (id == wlPref.mWordlistId) {
                    return wlPref
                }
            }
        }
        Log.e(TAG, "Could not find the preference for a word list id $id")
        return null
    }

    fun refreshInterface() {
        val activity = activity ?: return
        val prefScreen: PreferenceGroup = preferenceScreen
        val prefList = createInstalledDictSettingsCollection(mClientId)
        activity.runOnUiThread {
            // TODO: display this somewhere
// if (0 != lastUpdate) mUpdateNowPreference.setSummary(updateNowSummary);
            removeAnyDictSettings(prefScreen)
            var i = 0
            for (preference in prefList) {
                preference.order = i++
                prefScreen.addPreference(preference)
            }
        }
    }

    /**
     * Creates a WordListPreference list to be added to the screen.
     *
     * This method only creates the preferences but does not add them.
     * Thus, it can be called on another thread.
     *
     * @param clientId the id of the client for which we want to display the dictionary list
     * @return A collection of preferences ready to add to the interface.
     */
    private fun createInstalledDictSettingsCollection(
            clientId: String?): Collection<Preference> { // This will directly contact the DictionaryProvider and request the list exactly like
// any regular client would do.
// Considering the respective value of the respective constants used here for each path,
// segment, the url generated by this is of the form (assuming "clientId" as a clientId)
// content://org.dslul.openboard.inputmethod.latin.dictionarypack/clientId/list?procotol=2
        val contentUri = Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(getString(R.string.authority))
                .appendPath(clientId)
                .appendPath(DICT_LIST_ID) // Need to use version 2 to get this client's list
                .appendQueryParameter(DictionaryProvider.Companion.QUERY_PARAMETER_PROTOCOL_VERSION, "2")
                .build()
        val activity = activity
        val cursor = activity?.contentResolver?.query(contentUri, null, null, null, null)
        if (null == cursor) {
            val result = ArrayList<Preference>()
            result.add(createErrorMessage(activity, R.string.cannot_connect_to_dict_service))
            return result
        }
        return try {
            if (!cursor.moveToFirst()) {
                val result = ArrayList<Preference>()
                result.add(createErrorMessage(activity, R.string.no_dictionaries_available))
                return result
            }
            val systemLocaleString = Locale.getDefault().toString()
            val prefMap = TreeMap<String, WordListPreference>()
            val idIndex = cursor.getColumnIndex(MetadataDbHelper.WORDLISTID_COLUMN)
            val versionIndex = cursor.getColumnIndex(MetadataDbHelper.VERSION_COLUMN)
            val localeIndex = cursor.getColumnIndex(MetadataDbHelper.LOCALE_COLUMN)
            val descriptionIndex = cursor.getColumnIndex(MetadataDbHelper.DESCRIPTION_COLUMN)
            val statusIndex = cursor.getColumnIndex(MetadataDbHelper.STATUS_COLUMN)
            val filesizeIndex = cursor.getColumnIndex(MetadataDbHelper.FILESIZE_COLUMN)
            do {
                val wordlistId = cursor.getString(idIndex)
                val version = cursor.getInt(versionIndex)
                val localeString = cursor.getString(localeIndex)
                val locale = Locale(localeString)
                val description = cursor.getString(descriptionIndex)
                val status = cursor.getInt(statusIndex)
                val matchLevel = LocaleUtils.getMatchLevel(systemLocaleString, localeString)
                val matchLevelString = LocaleUtils.getMatchLevelSortedString(matchLevel)
                val filesize = cursor.getInt(filesizeIndex)
                // The key is sorted in lexicographic order, according to the match level, then
// the description.
                val key = "$matchLevelString.$description.$wordlistId"
                val existingPref = prefMap[key]
                if (null == existingPref || existingPref.hasPriorityOver(status)) {
                    val oldPreference = mCurrentPreferenceMap[key]
                    val pref: WordListPreference
                    pref = if (null != oldPreference && oldPreference.mVersion == version && oldPreference.hasStatus(status)
                            && oldPreference.mLocale == locale) { // If the old preference has all the new attributes, reuse it. Ideally,
// we should reuse the old pref even if its status is different and call
// setStatus here, but setStatus calls Preference#setSummary() which
// needs to be done on the UI thread and we're not on the UI thread
// here. We could do all this work on the UI thread, but in this case
// it's probably lighter to stay on a background thread and throw this
// old preference out.
                        oldPreference
                    } else { // Otherwise, discard it and create a new one instead.
// TODO: when the status is different from the old one, we need to
// animate the old one out before animating the new one in.
                        WordListPreference(activity, mDictionaryListInterfaceState,
                                mClientId, wordlistId, version, locale, description, status,
                                filesize)
                    }
                    prefMap[key] = pref
                }
            } while (cursor.moveToNext())
            mCurrentPreferenceMap = prefMap
            prefMap.values
        } finally {
            cursor.close()
        }
    }

    companion object {
        private val TAG = DictionarySettingsFragment::class.java.simpleName
        private const val DICT_LIST_ID = "list"
        const val DICT_SETTINGS_FRAGMENT_CLIENT_ID_ARGUMENT = "clientId"
//        private const val MENU_UPDATE_NOW = Menu.FIRST
        private fun createErrorMessage(activity: Activity?, messageResource: Int): Preference {
            val message = Preference(activity)
            message.setTitle(messageResource)
            message.isEnabled = false
            return message
        }

        fun removeAnyDictSettings(prefGroup: PreferenceGroup) {
            for (i in prefGroup.preferenceCount - 1 downTo 0) {
                prefGroup.removePreference(prefGroup.getPreference(i))
            }
        }
    }
}