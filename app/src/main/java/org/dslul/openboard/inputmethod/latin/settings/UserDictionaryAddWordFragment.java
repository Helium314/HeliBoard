/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.settings;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.settings.UserDictionaryAddWordContents.LocaleRenderer;

import java.util.ArrayList;
import java.util.Collections;

// Caveat: This class is basically taken from
// packages/apps/Settings/src/com/android/settings/inputmethod/UserDictionaryAddWordFragment.java
// in order to deal with some devices that have issues with the user dictionary handling

/**
 * Fragment to add a word/shortcut to the user dictionary.
 * As opposed to the UserDictionaryActivity, this is only invoked within Settings
 * from the UserDictionarySettings.
 */
public class UserDictionaryAddWordFragment extends SubScreenFragment
        // TODO: To be reactivated when UserDictionaryLocalePicker.UserDictionaryLocalePicker() is implemented
        /*implements LocationChangedListener*/ {

    private static final int OPTIONS_MENU_ADD = Menu.FIRST;
    private static final int OPTIONS_MENU_DELETE = Menu.FIRST + 1;

    private UserDictionaryAddWordContents mContents;
    private View mRootView;
    private EditText mWordEditText;
    private InputMethodManager mInput;
    private ActionBar mActionBar;

    @NonNull
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedState) {
        mRootView = inflater.inflate(R.layout.user_dictionary_add_word_fullscreen, null);
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

        mWordEditText = mRootView.findViewById(R.id.user_dictionary_add_word_text);

        final Bundle args = getArguments();
        mActionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        if (args != null) {
            if (args.getInt(UserDictionaryAddWordContents.EXTRA_MODE) == UserDictionaryAddWordContents.MODE_EDIT) {
                mActionBar.setTitle(R.string.user_dict_settings_edit_dialog_title);
            } else {
                mActionBar.setTitle(R.string.user_dict_settings_add_dialog_title);
            }
            mActionBar.setSubtitle(UserDictionarySettings.getLocaleDisplayName(getActivity(), mContents.getDropDownMenuLanguage()));
        }
        setHasOptionsMenu(true);
        return mRootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Automatically display the keyboard when we want to add or modify a word
        mInput = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        mInput.showSoftInput(mWordEditText, InputMethodManager.SHOW_IMPLICIT);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu, @NonNull final MenuInflater inflater) {
        final Drawable deleteIcon = getBitmapFromVectorDrawable(R.drawable.ic_delete, 0.75f);
        final Bundle args = getArguments();
        // The delete icon only appears if a word is already present.
        if (args == null) return;
        if (args.getInt(UserDictionaryAddWordContents.EXTRA_MODE) == UserDictionaryAddWordContents.MODE_EDIT) {
            final MenuItem actionItemDelete = menu.add(0, OPTIONS_MENU_DELETE, 0, null).setIcon(deleteIcon);
            actionItemDelete.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            if (actionItemDelete.getIcon() != null)
                actionItemDelete.getIcon().setColorFilter(getResources().getColor(R.color.foreground_weak), PorterDuff.Mode.SRC_ATOP);
        }

        final MenuItem actionItemAdd = menu.add(0, OPTIONS_MENU_ADD, 0, null).setIcon(R.drawable.ic_save);
        actionItemAdd.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        if (actionItemAdd.getIcon() != null)
            actionItemAdd.getIcon().setColorFilter(getResources().getColor(R.color.foreground_weak), PorterDuff.Mode.SRC_ATOP);
    }

    // The bin icon is too big compared to the plus icon; we need to reduce it.
    // We therefore need to convert the Vector drawable image to Bitmap.
    private BitmapDrawable getBitmapFromVectorDrawable(int drawable, float scale) {
        Drawable vectorDrawable = ContextCompat.getDrawable(requireContext(), drawable);
        if (vectorDrawable == null) return null;
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
            if (mContents.isExistingWord(requireContext())) {
                new AlertDialog.Builder(requireContext())
                        .setMessage(R.string.user_dict_word_already_present)
                        .setPositiveButton(R.string.user_dict_update_button, (dialog, which) -> {
                            mContents.editWord(requireContext());
                            mActionBar.setTitle(R.string.user_dict_settings_edit_dialog_title);
                        })
                        .setNegativeButton(R.string.user_dict_correct_button, (dialog, i) -> dialog.dismiss())
                        .show();
            } else {
                mContents.apply(requireContext());
                requireActivity().onBackPressed();
            }
            return true;
        }
        if (item.getItemId() == OPTIONS_MENU_DELETE) {
            mContents.delete(requireActivity());
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

    private void updateSpinner() {
        final ArrayList<LocaleRenderer> localesList = mContents.getLocalesList(requireActivity());

        final Spinner localeSpinner = mRootView.findViewById(R.id.user_dictionary_add_locale);
        final ArrayAdapter<LocaleRenderer> adapter = new ArrayAdapter<>(
                requireActivity(), android.R.layout.simple_spinner_item, localesList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        localeSpinner.setAdapter(adapter);
        localeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                final LocaleRenderer locale = (LocaleRenderer)parent.getItemAtPosition(position);
                // TODO: To be reactivated when UserDictionaryLocalePicker.UserDictionaryLocalePicker() is implemented
                /*if (locale.isMoreLanguages()) {
                    AppCompatActivity activity = (AppCompatActivity) requireActivity();
                    activity.getSupportFragmentManager().beginTransaction()
                            .replace(android.R.id.content, new UserDictionaryLocalePicker())
                            .addToBackStack(null)
                            .commit();
                } else {
                    mContents.updateLocale(locale.getLocaleString());
                    // To have the selected language at the top of the list, this one is removed from the list
                    localesList.remove(position);
                    // Then the other languages are sorted alphabetically
                    Collections.sort(localesList, (locale1, locale2)
                            -> locale1.getLocaleString().compareToIgnoreCase(locale2.getLocaleString()));

                    // Set "For all languages" to the end of the list
                    if (!locale.getLocaleString().equals("")) {
                        // After alphabetical sorting, "For all languages" is always in 1st position.
                        // (The position is 0 because the spinner menu item count starts at 0)
                        final LocaleRenderer forAllLanguages = adapter.getItem(0);
                        // So we delete its entry ...
                        localesList.remove(forAllLanguages);
                        // ... and we set it at the end of the list.
                        localesList.add(localesList.size(), forAllLanguages);
                    }

                    // Finally, we add the selected language to the top of the list.
                    localesList.add(0, locale);

                    // When a language is selected, the keyboard layout changes automatically
                    mInput.restartInput(mWordEditText);
                }*/

                // TODO: To be deleted when UserDictionaryLocalePicker.UserDictionaryLocalePicker() is implemented
                mContents.updateLocale(locale.getLocaleString());
                // To have the selected language at the top of the list, this one is removed from the list
                localesList.remove(position);
                // Then the other languages are sorted alphabetically
                Collections.sort(localesList, (locale1, locale2)
                        -> locale1.getLocaleString().compareToIgnoreCase(locale2.getLocaleString()));

                // Set "For all languages" to the end of the list
                if (!locale.getLocaleString().equals("")) {
                    // After alphabetical sorting, "For all languages" is always in 1st position.
                    // (The position is 0 because the spinner menu item count starts at 0)
                    final LocaleRenderer forAllLanguages = adapter.getItem(0);
                    // So we delete its entry ...
                    localesList.remove(forAllLanguages);
                    // ... and we set it at the end of the list.
                    localesList.add(localesList.size(), forAllLanguages);
                }

                // Finally, we add the selected language to the top of the list.
                localesList.add(0, locale);

                // When a language is selected, the keyboard layout changes automatically
                mInput.restartInput(mWordEditText);

                // The action bar subtitle is updated when a language is selected in the drop-down menu
                mActionBar.setSubtitle(locale.toString());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // I'm not sure we can come here, but if we do, that's the right thing to do.
                final Bundle args = getArguments();
                if (args == null) return;
                mContents.updateLocale(args.getString(UserDictionaryAddWordContents.EXTRA_LOCALE));
            }
        });
    }

    // TODO: To be reactivated when UserDictionaryLocalePicker.UserDictionaryLocalePicker() is implemented
    // Called by the locale picker
/*    @Override
    public void onLocaleSelected(final Locale locale) {
        mContents.updateLocale(locale.toString());
        requireActivity().onBackPressed();
    }*/
}

