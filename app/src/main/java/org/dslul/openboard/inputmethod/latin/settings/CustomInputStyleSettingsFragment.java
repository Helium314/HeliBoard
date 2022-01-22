/*
 * Copyright (C) 2012 The Android Open Source Project
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

package org.dslul.openboard.inputmethod.latin.settings;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.Toast;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.RichInputMethodManager;
import org.dslul.openboard.inputmethod.latin.utils.AdditionalSubtypeUtils;
import org.dslul.openboard.inputmethod.latin.utils.DialogUtils;
import org.dslul.openboard.inputmethod.latin.utils.IntentUtils;
import org.dslul.openboard.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.ArrayList;

import androidx.core.view.ViewCompat;

public final class CustomInputStyleSettingsFragment extends PreferenceFragment
        implements CustomInputStylePreference.Listener {
    private static final String TAG = CustomInputStyleSettingsFragment.class.getSimpleName();
    // Note: We would like to turn this debug flag true in order to see what input styles are
    // defined in a bug-report.
    private static final boolean DEBUG_CUSTOM_INPUT_STYLES = true;

    private RichInputMethodManager mRichImm;
    private SharedPreferences mPrefs;
    private CustomInputStylePreference.SubtypeLocaleAdapter mSubtypeLocaleAdapter;
    private CustomInputStylePreference.KeyboardLayoutSetAdapter mKeyboardLayoutSetAdapter;

    private boolean mIsAddingNewSubtype;
    private AlertDialog mSubtypeEnablerNotificationDialog;
    private String mSubtypePreferenceKeyForSubtypeEnabler;

    private static final String KEY_IS_ADDING_NEW_SUBTYPE = "is_adding_new_subtype";
    private static final String KEY_IS_SUBTYPE_ENABLER_NOTIFICATION_DIALOG_OPEN =
            "is_subtype_enabler_notification_dialog_open";
    private static final String KEY_SUBTYPE_FOR_SUBTYPE_ENABLER = "subtype_for_subtype_enabler";

    public CustomInputStyleSettingsFragment() {
        // Empty constructor for fragment generation.
    }

    static void updateCustomInputStylesSummary(final Preference pref) {
        // When we are called from the Settings application but we are not already running, some
        // singleton and utility classes may not have been initialized.  We have to call
        // initialization method of these classes here. See {@link LatinIME#onCreate()}.
        SubtypeLocaleUtils.init(pref.getContext());

        final Resources res = pref.getContext().getResources();
        final SharedPreferences prefs = pref.getSharedPreferences();
        final String prefSubtype = Settings.readPrefAdditionalSubtypes(prefs, res);
        final InputMethodSubtype[] subtypes =
                AdditionalSubtypeUtils.createAdditionalSubtypesArray(prefSubtype);
        final ArrayList<String> subtypeNames = new ArrayList<>();
        for (final InputMethodSubtype subtype : subtypes) {
            subtypeNames.add(SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(subtype));
        }
        // TODO: A delimiter of custom input styles should be localized.
        pref.setSummary(TextUtils.join(", ", subtypeNames));
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            getPreferenceManager().setStorageDeviceProtected();
        }
        mPrefs = getPreferenceManager().getSharedPreferences();
        RichInputMethodManager.init(getActivity());
        mRichImm = RichInputMethodManager.getInstance();
        addPreferencesFromResource(R.xml.additional_subtype_settings);
        setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        final ActionBar actionBar = getActivity().getActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.custom_input_styles_title);
        }
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        final View view = super.onCreateView(inflater, container, savedInstanceState);
        // For correct display in RTL locales, we need to set the layout direction of the
        // fragment's top view.
        ViewCompat.setLayoutDirection(view, ViewCompat.LAYOUT_DIRECTION_LOCALE);
        return view;
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        final Context context = getActivity();
        mSubtypeLocaleAdapter = new CustomInputStylePreference.SubtypeLocaleAdapter(context);
        mKeyboardLayoutSetAdapter =
                new CustomInputStylePreference.KeyboardLayoutSetAdapter(context);

        final String prefSubtypes =
                Settings.readPrefAdditionalSubtypes(mPrefs, getResources());
        if (DEBUG_CUSTOM_INPUT_STYLES) {
            Log.i(TAG, "Load custom input styles: " + prefSubtypes);
        }
        setPrefSubtypes(prefSubtypes, context);

        mIsAddingNewSubtype = (savedInstanceState != null)
                && savedInstanceState.containsKey(KEY_IS_ADDING_NEW_SUBTYPE);
        if (mIsAddingNewSubtype) {
            getPreferenceScreen().addPreference(
                    CustomInputStylePreference.newIncompleteSubtypePreference(context, this));
        }

        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState != null && savedInstanceState.containsKey(
                KEY_IS_SUBTYPE_ENABLER_NOTIFICATION_DIALOG_OPEN)) {
            mSubtypePreferenceKeyForSubtypeEnabler = savedInstanceState.getString(
                    KEY_SUBTYPE_FOR_SUBTYPE_ENABLER);
            mSubtypeEnablerNotificationDialog = createDialog();
            mSubtypeEnablerNotificationDialog.show();
        }
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mIsAddingNewSubtype) {
            outState.putBoolean(KEY_IS_ADDING_NEW_SUBTYPE, true);
        }
        if (mSubtypeEnablerNotificationDialog != null
                && mSubtypeEnablerNotificationDialog.isShowing()) {
            outState.putBoolean(KEY_IS_SUBTYPE_ENABLER_NOTIFICATION_DIALOG_OPEN, true);
            outState.putString(
                    KEY_SUBTYPE_FOR_SUBTYPE_ENABLER, mSubtypePreferenceKeyForSubtypeEnabler);
        }
    }

    @Override
    public void onRemoveCustomInputStyle(final CustomInputStylePreference stylePref) {
        mIsAddingNewSubtype = false;
        final PreferenceGroup group = getPreferenceScreen();
        group.removePreference(stylePref);
        mRichImm.setAdditionalInputMethodSubtypes(getSubtypes());
    }

    @Override
    public void onSaveCustomInputStyle(final CustomInputStylePreference stylePref) {
        final InputMethodSubtype subtype = stylePref.getSubtype();
        if (!stylePref.hasBeenModified()) {
            return;
        }
        if (findDuplicatedSubtype(subtype) == null) {
            final InputMethodSubtype[] subTypes = saveAndGetSubTypes();
            mRichImm.setAdditionalInputMethodSubtypes(subTypes);
            return;
        }

        // Saved subtype is duplicated.
        final PreferenceGroup group = getPreferenceScreen();
        group.removePreference(stylePref);
        stylePref.revert();
        group.addPreference(stylePref);
        showSubtypeAlreadyExistsToast(subtype);
    }

    @Override
    public void onAddCustomInputStyle(final CustomInputStylePreference stylePref) {
        mIsAddingNewSubtype = false;
        final InputMethodSubtype subtype = stylePref.getSubtype();
        if (findDuplicatedSubtype(subtype) == null) {
            final InputMethodSubtype[] subTypes = saveAndGetSubTypes();
            mRichImm.setAdditionalInputMethodSubtypes(subTypes);
            mSubtypePreferenceKeyForSubtypeEnabler = stylePref.getKey();
            mSubtypeEnablerNotificationDialog = createDialog();
            mSubtypeEnablerNotificationDialog.show();
            return;
        }

        // Newly added subtype is duplicated.
        final PreferenceGroup group = getPreferenceScreen();
        group.removePreference(stylePref);
        showSubtypeAlreadyExistsToast(subtype);
    }

    @Override
    public CustomInputStylePreference.SubtypeLocaleAdapter getSubtypeLocaleAdapter() {
        return mSubtypeLocaleAdapter;
    }

    @Override
    public CustomInputStylePreference.KeyboardLayoutSetAdapter getKeyboardLayoutSetAdapter() {
        return mKeyboardLayoutSetAdapter;
    }

    private void showSubtypeAlreadyExistsToast(final InputMethodSubtype subtype) {
        final Context context = getActivity();
        final Resources res = context.getResources();
        final String message = res.getString(R.string.custom_input_style_already_exists,
                SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(subtype));
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    private InputMethodSubtype findDuplicatedSubtype(final InputMethodSubtype subtype) {
        final String localeString = subtype.getLocale();
        final String keyboardLayoutSetName = SubtypeLocaleUtils.getKeyboardLayoutSetName(subtype);
        return mRichImm.findSubtypeByLocaleAndKeyboardLayoutSet(
                localeString, keyboardLayoutSetName);
    }

    private AlertDialog createDialog() {
        final String imeId = mRichImm.getInputMethodIdOfThisIme();
        final AlertDialog.Builder builder = new AlertDialog.Builder(
                DialogUtils.getPlatformDialogThemeContext(getActivity()));
        builder.setTitle(R.string.custom_input_styles_title)
                .setMessage(R.string.custom_input_style_note_message)
                .setNegativeButton(R.string.not_now, null)
                .setPositiveButton(R.string.enable, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final Intent intent = IntentUtils.getInputLanguageSelectionIntent(
                                imeId,
                                Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        // TODO: Add newly adding subtype to extra value of the intent as a hint
                        // for the input language selection activity.
                        // intent.putExtra("newlyAddedSubtype", subtypePref.getSubtype());
                        startActivity(intent);
                    }
                });

        return builder.create();
    }

    private void setPrefSubtypes(final String prefSubtypes, final Context context) {
        final PreferenceGroup group = getPreferenceScreen();
        group.removeAll();
        final InputMethodSubtype[] subtypesArray =
                AdditionalSubtypeUtils.createAdditionalSubtypesArray(prefSubtypes);
        for (final InputMethodSubtype subtype : subtypesArray) {
            final CustomInputStylePreference pref =
                    new CustomInputStylePreference(context, subtype, this);
            group.addPreference(pref);
        }
    }

    private InputMethodSubtype[] getSubtypes() {
        final PreferenceGroup group = getPreferenceScreen();
        final ArrayList<InputMethodSubtype> subtypes = new ArrayList<>();
        final int count = group.getPreferenceCount();
        for (int i = 0; i < count; i++) {
            final Preference pref = group.getPreference(i);
            if (pref instanceof CustomInputStylePreference) {
                final CustomInputStylePreference subtypePref = (CustomInputStylePreference)pref;
                // We should not save newly adding subtype to preference because it is incomplete.
                if (subtypePref.isIncomplete()) continue;
                subtypes.add(subtypePref.getSubtype());
            }
        }
        return subtypes.toArray(new InputMethodSubtype[subtypes.size()]);
    }

    @Override
    public void onPause() {
        super.onPause();
        final InputMethodSubtype[] subtypes = saveAndGetSubTypes();
        mRichImm.setAdditionalInputMethodSubtypes(subtypes);
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.add_style, menu);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final TypedValue value = new TypedValue();
            getActivity().getTheme().resolveAttribute(android.R.attr.colorForeground, value, true);
            menu.findItem(R.id.action_add_style).getIcon().setTint(value.data);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.action_add_style) {
            final CustomInputStylePreference newSubtype =
                    CustomInputStylePreference.newIncompleteSubtypePreference(getActivity(), this);
            getPreferenceScreen().addPreference(newSubtype);
            newSubtype.show();
            mIsAddingNewSubtype = true;
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private InputMethodSubtype[] saveAndGetSubTypes() {
        final String oldSubtypes = Settings.readPrefAdditionalSubtypes(mPrefs, getResources());
        final InputMethodSubtype[] subtypes = getSubtypes();
        final String prefSubtypes = AdditionalSubtypeUtils.createPrefSubtypes(subtypes);
        if (DEBUG_CUSTOM_INPUT_STYLES) {
            Log.i(TAG, "Save custom input styles: " + prefSubtypes);
        }
        if (prefSubtypes.equals(oldSubtypes)) {
            return null;
        }
        Settings.writePrefAdditionalSubtypes(mPrefs, prefSubtypes);
        return subtypes;
    }
}
