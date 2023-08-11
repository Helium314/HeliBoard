/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.preference.Preference;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.RichInputMethodManager;
import org.dslul.openboard.inputmethod.latin.utils.AdditionalSubtypeUtils;
import org.dslul.openboard.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.TreeSet;

final class CustomInputStylePreference extends DialogPreference
        implements DialogInterface.OnCancelListener {
    private static final boolean DEBUG_SUBTYPE_ID = false;

    interface Listener {
        void onRemoveCustomInputStyle(CustomInputStylePreference stylePref);
        void onSaveCustomInputStyle(CustomInputStylePreference stylePref);
        void onAddCustomInputStyle(CustomInputStylePreference stylePref);
        SubtypeLocaleAdapter getSubtypeLocaleAdapter();
        KeyboardLayoutSetAdapter getKeyboardLayoutSetAdapter();
    }

    private static final String KEY_PREFIX = "subtype_pref_";
    private static final String KEY_NEW_SUBTYPE = KEY_PREFIX + "new";

    private InputMethodSubtype mSubtype;
    private InputMethodSubtype mPreviousSubtype;

    private final Listener mProxy;
    private Spinner mSubtypeLocaleSpinner;
    private Spinner mKeyboardLayoutSetSpinner;

    public static CustomInputStylePreference newIncompleteSubtypePreference(
            final Context context, final Listener proxy) {
        return new CustomInputStylePreference(context, null, proxy);
    }

    public CustomInputStylePreference(final Context context, final InputMethodSubtype subtype,
            final Listener proxy) {
        super(context, null);
        setDialogLayoutResource(R.layout.additional_subtype_dialog);
        setPersistent(false);
        mProxy = proxy;
        setSubtype(subtype);
    }

    public void show() {
        showDialog(null);
    }

    public final boolean isIncomplete() {
        return mSubtype == null;
    }

    public InputMethodSubtype getSubtype() {
        return mSubtype;
    }

    public void setSubtype(final InputMethodSubtype subtype) {
        mPreviousSubtype = mSubtype;
        mSubtype = subtype;
        if (isIncomplete()) {
            setTitle(null);
            setDialogTitle(R.string.add_style);
            setKey(KEY_NEW_SUBTYPE);
        } else {
            final String displayName =
                    SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(subtype);
            setTitle(displayName);
            setDialogTitle(displayName);
            setKey(KEY_PREFIX + subtype.getLocale() + "_"
                    + SubtypeLocaleUtils.getKeyboardLayoutSetName(subtype));
        }
    }

    public void revert() {
        setSubtype(mPreviousSubtype);
    }

    public boolean hasBeenModified() {
        return mSubtype != null && !mSubtype.equals(mPreviousSubtype);
    }

    @Override
    protected View onCreateDialogView() {
        final View v = super.onCreateDialogView();
        mSubtypeLocaleSpinner = v.findViewById(R.id.subtype_locale_spinner);
        mSubtypeLocaleSpinner.setAdapter(mProxy.getSubtypeLocaleAdapter());
        mKeyboardLayoutSetSpinner = v.findViewById(R.id.keyboard_layout_set_spinner);
        mKeyboardLayoutSetSpinner.setAdapter(mProxy.getKeyboardLayoutSetAdapter());
        // All keyboard layout names are in the Latin script and thus left to right. That means
        // the view would align them to the left even if the system locale is RTL, but that
        // would look strange. To fix this, we align them to the view's start, which will be
        // natural for any direction.
        mKeyboardLayoutSetSpinner.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        return v;
    }

    @Override
    protected void onPrepareDialogBuilder(final AlertDialog.Builder builder) {
        builder.setCancelable(true).setOnCancelListener(this);
        if (isIncomplete()) {
            builder.setPositiveButton(R.string.add, this)
                    .setNegativeButton(R.string.cancel, this);
        } else {
            builder.setPositiveButton(R.string.save, this)
                    .setNeutralButton(R.string.cancel, this)
                    .setNegativeButton(R.string.remove, this);
            final SubtypeLocaleItem localeItem = new SubtypeLocaleItem(mSubtype);
            final KeyboardLayoutSetItem layoutItem = new KeyboardLayoutSetItem(mSubtype);
            setSpinnerPosition(mSubtypeLocaleSpinner, localeItem);
            setSpinnerPosition(mKeyboardLayoutSetSpinner, layoutItem);
        }
    }

    private static void setSpinnerPosition(final Spinner spinner, final Object itemToSelect) {
        final SpinnerAdapter adapter = spinner.getAdapter();
        final int count = adapter.getCount();
        for (int i = 0; i < count; i++) {
            final Object item = spinner.getItemAtPosition(i);
            if (item.equals(itemToSelect)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    @Override
    public void onCancel(final DialogInterface dialog) {
        if (isIncomplete()) {
            mProxy.onRemoveCustomInputStyle(this);
        }
    }

    @Override
    public void onClick(final DialogInterface dialog, final int which) {
        super.onClick(dialog, which);
        switch (which) {
        case DialogInterface.BUTTON_POSITIVE:
            final boolean isEditing = !isIncomplete();
            final SubtypeLocaleItem locale =
                    (SubtypeLocaleItem) mSubtypeLocaleSpinner.getSelectedItem();
            final KeyboardLayoutSetItem layout =
                    (KeyboardLayoutSetItem) mKeyboardLayoutSetSpinner.getSelectedItem();
            final InputMethodSubtype subtype =
                    AdditionalSubtypeUtils.createAsciiEmojiCapableAdditionalSubtype(
                            locale.mLocaleString, layout.mLayoutName);
            setSubtype(subtype);
            notifyChanged();
            if (isEditing) {
                mProxy.onSaveCustomInputStyle(this);
            } else {
                mProxy.onAddCustomInputStyle(this);
            }
            break;
        case DialogInterface.BUTTON_NEUTRAL:
            // Nothing to do
            break;
        case DialogInterface.BUTTON_NEGATIVE:
            mProxy.onRemoveCustomInputStyle(this);
            break;
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        final Dialog dialog = getDialog();
        if (dialog == null || !dialog.isShowing()) {
            return superState;
        }

        final SavedState myState = new SavedState(superState);
        myState.mSubtype = mSubtype;
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(final Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        final SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        setSubtype(myState.mSubtype);
    }

    static final class SavedState extends Preference.BaseSavedState {
        InputMethodSubtype mSubtype;

        public SavedState(final Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(final Parcel dest, final int flags) {
            super.writeToParcel(dest, flags);
            dest.writeParcelable(mSubtype, 0);
        }

        public SavedState(final Parcel source) {
            super(source);
            mSubtype = source.readParcelable(null);
        }

        @SuppressWarnings("hiding")
        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    @Override
                    public SavedState createFromParcel(final Parcel source) {
                        return new SavedState(source);
                    }

                    @Override
                    public SavedState[] newArray(final int size) {
                        return new SavedState[size];
                    }
                };
    }

    static final class SubtypeLocaleItem implements Comparable<SubtypeLocaleItem> {
        public final String mLocaleString;
        private final String mDisplayName;

        public SubtypeLocaleItem(final InputMethodSubtype subtype) {
            mLocaleString = subtype.getLocale();
            mDisplayName = SubtypeLocaleUtils.getSubtypeLocaleDisplayNameInSystemLocale(
                    mLocaleString);
        }

        // {@link ArrayAdapter<T>} that hosts the instance of this class needs {@link #toString()}
        // to get display name.
        @Override
        public String toString() {
            return mDisplayName;
        }

        @Override
        public int compareTo(final SubtypeLocaleItem o) {
            return mLocaleString.compareTo(o.mLocaleString);
        }
    }

    static final class SubtypeLocaleAdapter extends ArrayAdapter<SubtypeLocaleItem> {
        private static final String TAG_SUBTYPE = SubtypeLocaleAdapter.class.getSimpleName();

        public SubtypeLocaleAdapter(final Context context) {
            super(context, android.R.layout.simple_spinner_item);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            final TreeSet<SubtypeLocaleItem> items = new TreeSet<>();
            final InputMethodInfo imi = RichInputMethodManager.getInstance()
                    .getInputMethodInfoOfThisIme();
            final int count = imi.getSubtypeCount();
            for (int i = 0; i < count; i++) {
                final InputMethodSubtype subtype = imi.getSubtypeAt(i);
                if (DEBUG_SUBTYPE_ID) {
                    Log.d(TAG_SUBTYPE, String.format("%-6s 0x%08x %11d %s",
                            subtype.getLocale(), subtype.hashCode(), subtype.hashCode(),
                            SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(subtype)));
                }
                if (subtype.isAsciiCapable()) {
                    items.add(new SubtypeLocaleItem(subtype));
                }
            }
            // TODO: Should filter out already existing combinations of locale and layout.
            addAll(items);
        }
    }

    static final class KeyboardLayoutSetItem {
        public final String mLayoutName;
        private final String mDisplayName;

        public KeyboardLayoutSetItem(final InputMethodSubtype subtype) {
            mLayoutName = SubtypeLocaleUtils.getKeyboardLayoutSetName(subtype);
            mDisplayName = SubtypeLocaleUtils.getKeyboardLayoutSetDisplayName(subtype);
        }

        // {@link ArrayAdapter<T>} that hosts the instance of this class needs {@link #toString()}
        // to get display name.
        @Override
        public String toString() {
            return mDisplayName;
        }
    }

    static final class KeyboardLayoutSetAdapter extends ArrayAdapter<KeyboardLayoutSetItem> {
        public KeyboardLayoutSetAdapter(final Context context) {
            super(context, android.R.layout.simple_spinner_item);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            final String[] predefinedKeyboardLayoutSet = context.getResources().getStringArray(
                    R.array.predefined_layouts);
            // TODO: Should filter out already existing combinations of locale and layout.
            for (final String layout : predefinedKeyboardLayoutSet) {
                // This is a dummy subtype with NO_LANGUAGE, only for display.
                final InputMethodSubtype subtype =
                        AdditionalSubtypeUtils.createDummyAdditionalSubtype(
                                SubtypeLocaleUtils.NO_LANGUAGE, layout);
                add(new KeyboardLayoutSetItem(subtype));
            }
        }
    }
}
