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

// adapted from https://github.com/rkkr/simple-keyboard/blob/master/app/src/main/java/rkr/simplekeyboard/inputmethod/latin/settings/ColorDialogPreference.java
package org.dslul.openboard.inputmethod.latin.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import org.dslul.openboard.inputmethod.latin.R;

public class ColorPickerDialog extends AlertDialog implements SeekBar.OnSeekBarChangeListener {
    protected ColorPickerDialog(Context context, String title, SharedPreferences prefs, String colorPref) {
        super(context);
        setTitle(title);
        View view = getLayoutInflater().inflate(R.layout.color_dialog, null);
        mSeekBarRed = (SeekBar)view.findViewById(R.id.seek_bar_dialog_bar_red);
        mSeekBarRed.setMax(255);
        mSeekBarRed.setOnSeekBarChangeListener(this);
        mSeekBarRed.getProgressDrawable().setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);
        mSeekBarRed.getThumb().setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);
        mSeekBarGreen = (SeekBar)view.findViewById(R.id.seek_bar_dialog_bar_green);
        mSeekBarGreen.setMax(255);
        mSeekBarGreen.setOnSeekBarChangeListener(this);
        mSeekBarGreen.getThumb().setColorFilter(Color.GREEN, PorterDuff.Mode.SRC_IN);
        mSeekBarGreen.getProgressDrawable().setColorFilter(Color.GREEN, PorterDuff.Mode.SRC_IN);
        mSeekBarBlue = (SeekBar)view.findViewById(R.id.seek_bar_dialog_bar_blue);
        mSeekBarBlue.setMax(255);
        mSeekBarBlue.setOnSeekBarChangeListener(this);
        mSeekBarBlue.getThumb().setColorFilter(Color.BLUE, PorterDuff.Mode.SRC_IN);
        mSeekBarBlue.getProgressDrawable().setColorFilter(Color.BLUE, PorterDuff.Mode.SRC_IN);
        mValueView = (TextView)view.findViewById(R.id.seek_bar_dialog_value);
        setView(view);

        // init with correct values
        // using onShowListener?
        setOnShowListener(new OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                int color = prefs.getInt(colorPref, 0);
                mSeekBarRed.setProgress(Color.red(color));
                mSeekBarGreen.setProgress(Color.green(color));
                mSeekBarBlue.setProgress(Color.blue(color));
                setHeaderText(color);
            }
        });

        // set on ok and on cancel listeners
        setButton(BUTTON_NEGATIVE, context.getText(android.R.string.cancel), new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dismiss();
                    }
                });
        setButton(BUTTON_POSITIVE, context.getText(android.R.string.ok), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                final int value = Color.rgb(
                        mSeekBarRed.getProgress(),
                        mSeekBarGreen.getProgress(),
                        mSeekBarBlue.getProgress());
                prefs.edit().putInt(colorPref, value).apply();
                dismiss();
            }
        });
    }

    private TextView mValueView;
    private SeekBar mSeekBarRed;
    private SeekBar mSeekBarGreen;
    private SeekBar mSeekBarBlue;

    @Override
    public void onProgressChanged(final SeekBar seekBar, final int progress, final boolean fromUser) {
        int color = Color.rgb(
                mSeekBarRed.getProgress(),
                mSeekBarGreen.getProgress(),
                mSeekBarBlue.getProgress());
        setHeaderText(color);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }

    private void setHeaderText(int color) {
        mValueView.setText(getValueText(color));
        boolean bright = Color.red(color) + Color.green(color) + Color.blue(color) > 128 * 3;
        mValueView.setTextColor(bright ? Color.BLACK : Color.WHITE);
        mValueView.setBackgroundColor(color);
    }

    private String getValueText(final int value) {
        String temp = Integer.toHexString(value);
        for (; temp.length() < 8; temp = "0" + temp);
        return temp.substring(2).toUpperCase();
    }

}
