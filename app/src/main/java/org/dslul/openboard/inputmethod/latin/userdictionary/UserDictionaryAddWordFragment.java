/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.userdictionary;

import android.app.Fragment;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.userdictionary.UserDictionaryAddWordContents.LocaleRenderer;
import org.dslul.openboard.inputmethod.latin.userdictionary.UserDictionaryLocalePicker.LocationChangedListener;

import java.util.ArrayList;
import java.util.Locale;

// Caveat: This class is basically taken from
// packages/apps/Settings/src/com/android/settings/inputmethod/UserDictionaryAddWordFragment.java
// in order to deal with some devices that have issues with the user dictionary handling

/**
 * Fragment to add a word/shortcut to the user dictionary.
 *
 * As opposed to the UserDictionaryActivity, this is only invoked within Settings
 * from the UserDictionarySettings.
 */
public class UserDictionaryAddWordFragment extends Fragment
        implements AdapterView.OnItemSelectedListener, LocationChangedListener {

    private static final int OPTIONS_MENU_ADD = Menu.FIRST;
    private static final int OPTIONS_MENU_DELETE = Menu.FIRST + 1;

    private UserDictionaryAddWordContents mContents;
    private View mRootView;
    private boolean mIsDeleting = false;

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
        getActivity().getActionBar().setTitle(R.string.edit_personal_dictionary);
        // Keep the instance so that we remember mContents when configuration changes (eg rotation)
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedState) {
        mRootView = inflater.inflate(R.layout.user_dictionary_add_word_fullscreen, null);
        mIsDeleting = false;
        // If we have a non-null mContents object, it's the old value before a configuration
        // change (eg rotation) so we need to use its values. Otherwise, read from the arguments.
        if (null == mContents) {
            mContents = new UserDictionaryAddWordContents(mRootView, getArguments());
        } else {
            // We create a new mContents object to account for the new situation : a word has
            // been added to the user dictionary when we started rotating, and we are now editing
            // it. That means in particular if the word undergoes any change, the old version should
            // be updated, so the mContents object needs to switch to EDIT mode if it was in
            // INSERT mode.
            mContents = new UserDictionaryAddWordContents(mRootView,
                    mContents /* oldInstanceToBeEdited */);
        }
        getActivity().getActionBar().setSubtitle(UserDictionarySettingsUtils.getLocaleDisplayName(
                getActivity(), mContents.getCurrentUserDictionaryLocale()));
        return mRootView;
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        final MenuItem actionItemAdd = menu.add(0, OPTIONS_MENU_ADD, 0,
                R.string.user_dict_settings_add_menu_title).setIcon(R.drawable.ic_menu_add);
        actionItemAdd.setShowAsAction(
                MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        final MenuItem actionItemDelete = menu.add(0, OPTIONS_MENU_DELETE, 0,
                R.string.user_dict_settings_delete).setIcon(android.R.drawable.ic_menu_delete);
        actionItemDelete.setShowAsAction(
                MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
    }

    /**
     * Callback for the framework when a menu option is pressed.
     *
     * @param item the item that was pressed
     * @return false to allow normal menu processing to proceed, true to consume it here
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == OPTIONS_MENU_ADD) {
            // added the entry in "onPause"
            getActivity().onBackPressed();
            return true;
        }
        if (item.getItemId() == OPTIONS_MENU_DELETE) {
            mContents.delete(getActivity());
            mIsDeleting = true;
            getActivity().onBackPressed();
            return true;
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        // We are being shown: display the word
        updateSpinner();
    }

    private void updateSpinner() {
        final ArrayList<LocaleRenderer> localesList = mContents.getLocalesList(getActivity());

        final Spinner localeSpinner =
                mRootView.findViewById(R.id.user_dictionary_add_locale);
        final ArrayAdapter<LocaleRenderer> adapter = new ArrayAdapter<>(
                getActivity(), android.R.layout.simple_spinner_item, localesList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        localeSpinner.setAdapter(adapter);
        localeSpinner.setOnItemSelectedListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        // We are being hidden: commit changes to the user dictionary, unless we were deleting it
        if (!mIsDeleting) {
            mContents.apply(getActivity(), null);
        }
    }

    @Override
    public void onItemSelected(final AdapterView<?> parent, final View view, final int pos,
            final long id) {
        final LocaleRenderer locale = (LocaleRenderer)parent.getItemAtPosition(pos);
        if (locale.isMoreLanguages()) {
            PreferenceActivity preferenceActivity = (PreferenceActivity)getActivity();
            preferenceActivity.startPreferenceFragment(new UserDictionaryLocalePicker(), true);
        } else {
            mContents.updateLocale(locale.getLocaleString());
        }
    }

    @Override
    public void onNothingSelected(final AdapterView<?> parent) {
        // I'm not sure we can come here, but if we do, that's the right thing to do.
        final Bundle args = getArguments();
        mContents.updateLocale(args.getString(UserDictionaryAddWordContents.EXTRA_LOCALE));
    }

    // Called by the locale picker
    @Override
    public void onLocaleSelected(final Locale locale) {
        mContents.updateLocale(locale.toString());
        getActivity().onBackPressed();
    }
}

