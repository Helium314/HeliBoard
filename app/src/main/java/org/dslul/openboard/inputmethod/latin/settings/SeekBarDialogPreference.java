/*
 * Copyright (C) 2013 The Android Open Source Project
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
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import org.dslul.openboard.inputmethod.latin.R;

public final class SeekBarDialogPreference extends Preference
        implements SeekBar.OnSeekBarChangeListener, DialogInterface.OnClickListener {
    public interface ValueProxy {
        int readValue(final String key);
        int readDefaultValue(final String key);
        void writeValue(final int value, final String key);
        void writeDefaultValue(final String key);
        String getValueText(final int value);
        void feedbackValue(final int value);
    }

    private final int mMaxValue;
    private final int mMinValue;
    private final int mStepValue;

    private TextView mValueView;
    private SeekBar mSeekBar;

    private ValueProxy mValueProxy;

    public SeekBarDialogPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.SeekBarDialogPreference, 0, 0);
        mMaxValue = a.getInt(R.styleable.SeekBarDialogPreference_maxValue, 0);
        mMinValue = a.getInt(R.styleable.SeekBarDialogPreference_minValue, 0);
        mStepValue = a.getInt(R.styleable.SeekBarDialogPreference_stepValue, 0);
        a.recycle();
    }

    public void setInterface(final ValueProxy proxy) {
        mValueProxy = proxy;
        final int value = mValueProxy.readValue(getKey());
        setSummary(mValueProxy.getValueText(value));
    }

    private int getProgressFromValue(final int value) {
        return value - mMinValue;
    }

    private int getValueFromProgress(final int progress) {
        return progress + mMinValue;
    }

    private int clipValue(final int value) {
        final int clippedValue = Math.min(mMaxValue, Math.max(mMinValue, value));
        if (mStepValue <= 1) {
            return clippedValue;
        }
        return clippedValue - (clippedValue % mStepValue);
    }

    private int getClippedValueFromProgress(final int progress) {
        return clipValue(getValueFromProgress(progress));
    }

    private void onCreateDialogView(final View view) {
        mSeekBar = view.findViewById(R.id.seek_bar_dialog_bar);
        mSeekBar.setMax(mMaxValue - mMinValue);
        mSeekBar.setOnSeekBarChangeListener(this);
        mValueView = view.findViewById(R.id.seek_bar_dialog_value);
        final int value = mValueProxy.readValue(getKey());
        mValueView.setText(mValueProxy.getValueText(value));
        mSeekBar.setProgress(getProgressFromValue(clipValue(value)));
    }

    @Override
    public void onClick() {
        final View view = LayoutInflater.from(getContext()).inflate(R.layout.seek_bar_dialog, null);
        final AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle(getTitle())
                .setView(view)
                .setPositiveButton(android.R.string.ok, this)
                .setNegativeButton(android.R.string.cancel, this)
                .setNeutralButton(R.string.button_default, this)
                .create();
        dialog.setOnShowListener((d) -> onCreateDialogView(view));
        dialog.show();
    }

    @Override
    public void onClick(final DialogInterface dialog, final int which) {
        final String key = getKey();
        if (which == DialogInterface.BUTTON_NEUTRAL) {
            final int value = mValueProxy.readDefaultValue(key);
            setSummary(mValueProxy.getValueText(value));
            mValueProxy.writeDefaultValue(key);
            return;
        }
        if (which == DialogInterface.BUTTON_POSITIVE) {
            final int value = getClippedValueFromProgress(mSeekBar.getProgress());
            setSummary(mValueProxy.getValueText(value));
            mValueProxy.writeValue(value, key);
        }
    }

    @Override
    public void onProgressChanged(final SeekBar seekBar, final int progress,
            final boolean fromUser) {
        final int value = getClippedValueFromProgress(progress);
        mValueView.setText(mValueProxy.getValueText(value));
        if (!fromUser) {
            mSeekBar.setProgress(getProgressFromValue(value));
        }
    }

    @Override
    public void onStartTrackingTouch(final SeekBar seekBar) {}

    @Override
    public void onStopTrackingTouch(final SeekBar seekBar) {
        mValueProxy.feedbackValue(getClippedValueFromProgress(seekBar.getProgress()));
    }
}
