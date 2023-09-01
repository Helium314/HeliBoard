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

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RadioButton;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import org.dslul.openboard.inputmethod.latin.R;

/**
 * Radio Button preference
 */
public class RadioButtonPreference extends Preference {
    interface OnRadioButtonClickedListener {
        /**
         * Called when this preference needs to be saved its state.
         *
         * @param preference This preference.
         */
        void onRadioButtonClicked(RadioButtonPreference preference);
    }

    private boolean mIsSelected;
    private RadioButton mRadioButton;
    private OnRadioButtonClickedListener mListener;
    private final View.OnClickListener mClickListener = new View.OnClickListener() {
        @Override
        public void onClick(final View v) {
            callListenerOnRadioButtonClicked();
        }
    };

    public RadioButtonPreference(final Context context) {
        this(context, null);
    }

    public RadioButtonPreference(final Context context, final AttributeSet attrs) {
        this(context, attrs, android.R.attr.preferenceStyle);
    }

    public RadioButtonPreference(final Context context, final AttributeSet attrs,
            final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWidgetLayoutResource(R.layout.radio_button_preference_widget);
    }

    public void setOnRadioButtonClickedListener(final OnRadioButtonClickedListener listener) {
        mListener = listener;
    }

    void callListenerOnRadioButtonClicked() {
        if (mListener != null) {
            mListener.onRadioButtonClicked(this);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        final View view = holder.itemView;
        mRadioButton = view.findViewById(R.id.radio_button);
        mRadioButton.setChecked(mIsSelected);
        mRadioButton.setOnClickListener(mClickListener);
        view.setOnClickListener(mClickListener);
    }

    public boolean isSelected() {
        return mIsSelected;
    }

    public void setSelected(final boolean selected) {
        if (selected == mIsSelected) {
            return;
        }
        mIsSelected = selected;
        if (mRadioButton != null) {
            mRadioButton.setChecked(selected);
        }
        notifyChanged();
    }
}
