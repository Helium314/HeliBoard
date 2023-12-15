/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.userdictionary;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

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
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setHasOptionsMenu(true);
        final ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        if (actionBar == null) return;
        actionBar.setTitle(R.string.edit_personal_dictionary);
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
            mContents = new UserDictionaryAddWordContents(mRootView, mContents);
        }
        return mRootView;
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        final Drawable deleteIcon = getBitmapFromVectorDrawable(R.drawable.ic_delete, 0.75f);
        final MenuItem actionItemDelete = menu.add(0, OPTIONS_MENU_DELETE, 0,
                R.string.user_dict_settings_delete).setIcon(deleteIcon);
        actionItemDelete.setShowAsAction(
                MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        if (actionItemDelete.getIcon() != null)
            actionItemDelete.getIcon().setColorFilter(getResources().getColor(R.color.foreground_weak), PorterDuff.Mode.SRC_ATOP);

        final MenuItem actionItemAdd = menu.add(0, OPTIONS_MENU_ADD, 0,
                R.string.user_dict_settings_add_menu_title).setIcon(R.drawable.ic_plus);
        actionItemAdd.setShowAsAction(
                MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
    }

    // The bin icon is too big compared to the plus icon; we need to reduce it.
    // We therefore need to convert the Vector drawable image to Bitmap.
    private BitmapDrawable getBitmapFromVectorDrawable(int drawable, float scale) {
        Drawable vectorDrawable = ContextCompat.getDrawable(getContext(), drawable);
        final int h = (int) (scale * vectorDrawable.getIntrinsicHeight());
        final int w = (int) (scale * vectorDrawable.getIntrinsicWidth());
        vectorDrawable.setBounds(0, 0, /*right*/ w, /*bottom*/ h);
        Bitmap bitmap = Bitmap.createBitmap(/*width*/ w, /*height*/ h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.draw(canvas);
        return new BitmapDrawable(getResources(), bitmap);
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
            requireActivity().onBackPressed();
            return true;
        }
        if (item.getItemId() == OPTIONS_MENU_DELETE) {
            mContents.delete(getActivity());
            mIsDeleting = true;
            requireActivity().onBackPressed();
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

    @Override
    public void onStart() {
        super.onStart();
        final ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        if (actionBar == null) return;
        actionBar.setTitle(R.string.user_dict_settings_add_dialog_title);
        actionBar.setSubtitle(UserDictionarySettingsUtils.getLocaleDisplayName(getActivity(), mContents.getCurrentUserDictionaryLocale()));
    }

    @Override
    public void onStop() {
        super.onStop();
        final ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        if (actionBar == null) return;
        actionBar.setSubtitle(null);
    }

    private void updateSpinner() {
        final ArrayList<LocaleRenderer> localesList = mContents.getLocalesList(getActivity());

        final Spinner localeSpinner = mRootView.findViewById(R.id.user_dictionary_add_locale);
        final ArrayAdapter<LocaleRenderer> adapter = new ArrayAdapter<>(
                requireActivity(), android.R.layout.simple_spinner_item, localesList);
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
        // TODO: To be reactivated when UserDictionaryLocalePicker.UserDictionaryLocalePicker() is implemented
/*        if (locale.isMoreLanguages()) {
            AppCompatActivity activity = (AppCompatActivity) requireActivity();
            activity.getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new UserDictionaryLocalePicker())
                    .addToBackStack(null)
                    .commit();
        } else {
            mContents.updateLocale(locale.getLocaleString());
        }*/
        // TODO: To be deleted when UserDictionaryLocalePicker.UserDictionaryLocalePicker() is implemented
        mContents.updateLocale(locale.getLocaleString());
    }

    @Override
    public void onNothingSelected(final AdapterView<?> parent) {
        // I'm not sure we can come here, but if we do, that's the right thing to do.
        final Bundle args = getArguments();
        if (args == null) return;
        mContents.updateLocale(args.getString(UserDictionaryAddWordContents.EXTRA_LOCALE));
    }

    // Called by the locale picker
    @Override
    public void onLocaleSelected(final Locale locale) {
        mContents.updateLocale(locale.toString());
        requireActivity().onBackPressed();
    }
}

