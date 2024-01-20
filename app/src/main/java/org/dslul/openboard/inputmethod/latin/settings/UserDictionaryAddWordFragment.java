/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.settings;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
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

/**
 * Fragment to add a word/shortcut to the user dictionary.
 * As opposed to the UserDictionaryActivity, this is only invoked within Settings
 * from the UserDictionarySettings.
 */
public class UserDictionaryAddWordFragment extends SubScreenFragment {

    private UserDictionaryAddWordContents mContents;
    private View mRootView;
    private EditText mWordEditText;
    private EditText mWeightEditText;
    private InputMethodManager mInput;
    private ActionBar mActionBar;
    private String mLocaleDisplayString;

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
        mWeightEditText = mRootView.findViewById(R.id.user_dictionary_add_weight);

        final Bundle args = getArguments();
        mActionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        mLocaleDisplayString = UserDictionarySettings.getLocaleDisplayName(requireContext(), mContents.getLocale());
        if (args != null && mActionBar != null) {
            if (args.getInt(UserDictionaryAddWordContents.EXTRA_MODE) == UserDictionaryAddWordContents.MODE_EDIT) {
                mActionBar.setTitle(R.string.user_dict_settings_edit_dialog_title);
            } else {
                mActionBar.setTitle(R.string.user_dict_settings_add_dialog_title);
            }
            mActionBar.setSubtitle(mLocaleDisplayString);
        }

        final Button saveWordButton = mRootView.findViewById(R.id.user_dictionary_save_button);
        saveWordButton.setOnClickListener(v -> addWord());

        final Button deleteWordButton = mRootView.findViewById(R.id.user_dictionary_delete_button);
        final Drawable deleteWordIcon = getBitmapFromVectorDrawable(R.drawable.ic_delete, 0.75f);
        deleteWordButton.setCompoundDrawablesWithIntrinsicBounds(null, null, deleteWordIcon, null);
        deleteWordButton.setOnClickListener(v -> {
            mContents.delete(requireContext());
            requireActivity().onBackPressed();
        });

        return mRootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Automatically display the keyboard when we want to add or modify a word
        mInput = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        mInput.showSoftInput(mWordEditText, InputMethodManager.SHOW_IMPLICIT);

        // Add a word using the Enter key
        mWeightEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addWord();
            }
            return false;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // We are being shown: display the word
        updateSpinner();
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

    private void addWord() {
        if (mContents.isExistingWord(requireContext())) {
            new AlertDialog.Builder(requireContext())
                    .setMessage(getString(R.string.user_dict_word_already_present, mLocaleDisplayString))
                    .setPositiveButton(android.R.string.ok, (dialog, which) ->
                            mInput.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, InputMethodManager.HIDE_IMPLICIT_ONLY))
                    .show();

            mWordEditText.requestFocus();

        } else if (!mWordEditText.getText().toString().isEmpty()) {
            mContents.apply(requireContext());
            requireActivity().onBackPressed();
        }
    }

    private void updateSpinner() {
        final ArrayList<LocaleRenderer> localesList = mContents.getLocaleRendererList(requireContext());

        final Spinner localeSpinner = mRootView.findViewById(R.id.user_dictionary_add_locale);
        final ArrayAdapter<LocaleRenderer> adapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, localesList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        localeSpinner.setAdapter(adapter);
        localeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                final LocaleRenderer locale = (LocaleRenderer)parent.getItemAtPosition(position);

                mContents.updateLocale(requireContext(), locale.getLocaleString());
                // To have the selected language at the top of the list, this one is removed from the list
                localesList.remove(position);
                // The other languages are then sorted alphabetically by name, with the exception of "For all languages"
                Collections.sort(localesList, (locale1, locale2) -> {
                    if (!locale1.getLocaleString().equals("") && !locale2.getLocaleString().equals("")) {
                        return locale1.toString().compareToIgnoreCase(locale2.toString());
                    } else {
                        return locale1.getLocaleString().compareToIgnoreCase(locale2.getLocaleString());
                    }
                });

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
                mContents.updateLocale(requireContext(), args.getString(UserDictionaryAddWordContents.EXTRA_LOCALE));
            }
        });
    }

}