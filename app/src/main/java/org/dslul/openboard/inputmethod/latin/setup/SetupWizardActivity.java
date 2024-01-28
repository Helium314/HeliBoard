/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.setup;

import static android.util.TypedValue.COMPLEX_UNIT_DIP;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.settings.SettingsActivity;
import org.dslul.openboard.inputmethod.latin.utils.JniUtils;
import org.dslul.openboard.inputmethod.latin.utils.LeakGuardHandlerWrapper;
import org.dslul.openboard.inputmethod.latin.utils.Log;
import org.dslul.openboard.inputmethod.latin.utils.ResourceUtils;
import org.dslul.openboard.inputmethod.latin.utils.UncachedInputMethodManagerUtils;

import java.util.ArrayList;

// TODO: Use Fragment to implement welcome screen and setup steps.
public final class SetupWizardActivity extends Activity implements View.OnClickListener {
    static final String TAG = SetupWizardActivity.class.getSimpleName();

    // For debugging purpose.
    private static final boolean FORCE_TO_SHOW_WELCOME_SCREEN = false;
    private static final boolean ENABLE_WELCOME_VIDEO = true;

    private InputMethodManager mImm;

    private View mSetupWizard;
    private View mWelcomeScreen;
    private View mSetupScreen;
    private Uri mWelcomeVideoUri;
    private VideoView mWelcomeVideoView;
    private ImageView mWelcomeImageView;
    private View mActionStart;
    private TextView mActionNext;
    private TextView mStep1Bullet;
    private TextView mActionFinish;
    private SetupStepGroup mSetupStepGroup;
    private static final String STATE_STEP = "step";
    private int mStepNumber;
    private boolean mNeedsToAdjustStepNumberToSystemState;
    private static final int STEP_WELCOME = 0;
    private static final int STEP_1 = 1;
    private static final int STEP_2 = 2;
    private static final int STEP_3 = 3;
    private static final int STEP_LAUNCHING_IME_SETTINGS = 4;
    private static final int STEP_BACK_FROM_IME_SETTINGS = 5;

    private SettingsPoolingHandler mHandler;

    private static final class SettingsPoolingHandler
            extends LeakGuardHandlerWrapper<SetupWizardActivity> {
        private static final int MSG_POLLING_IME_SETTINGS = 0;
        private static final long IME_SETTINGS_POLLING_INTERVAL = 200;

        private final InputMethodManager mImmInHandler;

        public SettingsPoolingHandler(@NonNull final SetupWizardActivity ownerInstance,
                final InputMethodManager imm) {
            super(ownerInstance);
            mImmInHandler = imm;
        }

        @Override
        public void handleMessage(@NonNull final Message msg) {
            final SetupWizardActivity setupWizardActivity = getOwnerInstance();
            if (setupWizardActivity == null) {
                return;
            }
            if (msg.what == MSG_POLLING_IME_SETTINGS) {
                if (UncachedInputMethodManagerUtils.isThisImeEnabled(setupWizardActivity,
                        mImmInHandler)) {
                    setupWizardActivity.invokeSetupWizardOfThisIme();
                    return;
                }
                startPollingImeSettings();
            }
        }

        public void startPollingImeSettings() {
            sendMessageDelayed(obtainMessage(MSG_POLLING_IME_SETTINGS),
                    IME_SETTINGS_POLLING_INTERVAL);
        }

        public void cancelPollingImeSettings() {
            removeMessages(MSG_POLLING_IME_SETTINGS);
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Context context = getApplicationContext();
        final boolean isNight = ResourceUtils.isNight(context.getResources());

        mImm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        mHandler = new SettingsPoolingHandler(this, mImm);
        setContentView(R.layout.setup_wizard);
        mSetupWizard = findViewById(R.id.setup_wizard);

        if (savedInstanceState == null) {
            mStepNumber = determineSetupStepNumberFromLauncher();
        } else {
            mStepNumber = savedInstanceState.getInt(STATE_STEP);
        }

        final String applicationName = getResources().getString(getApplicationInfo().labelRes);
        mWelcomeScreen = findViewById(R.id.setup_welcome_screen);
        final TextView welcomeTitle = findViewById(R.id.setup_welcome_title);
        welcomeTitle.setText(getString(R.string.setup_welcome_title, applicationName));

        // disable the "with gesture typing" when no library is available (at this point, this likely means library is in system and this is a system app)
        if (!JniUtils.sHaveGestureLib)
            ((TextView) findViewById(R.id.setup_welcome_description)).setText("");

        mSetupScreen = findViewById(R.id.setup_steps_screen);
        final TextView stepsTitle = findViewById(R.id.setup_title);
        stepsTitle.setText(getString(R.string.setup_steps_title, applicationName));

        final SetupStepIndicatorView indicatorView = findViewById(R.id.setup_step_indicator);
        mSetupStepGroup = new SetupStepGroup(indicatorView);

        mStep1Bullet = findViewById(R.id.setup_step1_bullet);
        mStep1Bullet.setOnClickListener(this);
        final SetupStep step1 = new SetupStep(STEP_1, applicationName,
                mStep1Bullet, findViewById(R.id.setup_step1),
                R.string.setup_step1_title, R.string.setup_step1_instruction,
                R.string.setup_step1_finished_instruction, R.drawable.ic_setup_key,
                R.string.setup_step1_action);
        final SettingsPoolingHandler handler = mHandler;
        step1.setAction(() -> {
            invokeLanguageAndInputSettings();
            handler.startPollingImeSettings();
        });
        mSetupStepGroup.addStep(step1);

        final SetupStep step2 = new SetupStep(STEP_2, applicationName,
                findViewById(R.id.setup_step2_bullet), findViewById(R.id.setup_step2),
                R.string.setup_step2_title, R.string.setup_step2_instruction,
                0 /* finishedInstruction */, R.drawable.ic_setup_select,
                R.string.setup_step2_action);
        step2.setAction(this::invokeInputMethodPicker);
        mSetupStepGroup.addStep(step2);

        final SetupStep step3 = new SetupStep(STEP_3, applicationName,
                findViewById(R.id.setup_step3_bullet), findViewById(R.id.setup_step3),
                R.string.setup_step3_title, R.string.setup_step3_instruction,
                0 /* finishedInstruction */, R.drawable.sym_keyboard_language_switch,
                R.string.setup_step3_action);
        step3.setAction(() -> {
            final Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
            intent.setAction(Intent.ACTION_VIEW);
            startActivity(intent);
            finish();
        });
        mSetupStepGroup.addStep(step3);

        mWelcomeVideoUri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(getPackageName())
                .path(Integer.toString(R.raw.setup_welcome_video))
                .build();
        final VideoView welcomeVideoView = findViewById(R.id.setup_welcome_video);
        welcomeVideoView.setOnPreparedListener(mp -> {
            // Now VideoView has been laid-out and ready to play, remove background of it to
            // reveal the video.
            welcomeVideoView.setBackgroundResource(0);
            mp.setLooping(true);
        });
        welcomeVideoView.setOnErrorListener((mp, what, extra) -> {
            Log.e(TAG, "Playing welcome video causes error: what=" + what + " extra=" + extra);
            hideWelcomeVideoAndShowWelcomeImage();
            return true;
        });
        mWelcomeVideoView = welcomeVideoView;
        mWelcomeImageView = findViewById(R.id.setup_welcome_image);

        mActionStart = findViewById(R.id.setup_start_label);
        mActionStart.setOnClickListener(this);

        mActionNext = findViewById(R.id.setup_next);
        mActionNext.setOnClickListener(this);

        mActionFinish = findViewById(R.id.setup_finish);
        final Drawable finishDrawable = ContextCompat.getDrawable(this, R.drawable.ic_setup_check);
        if (finishDrawable == null) {
            return;
        }
        DrawableCompat.setTintList(finishDrawable, step1.mTextColorStateList);
        mActionFinish.setCompoundDrawablesRelativeWithIntrinsicBounds(finishDrawable, null, null, null);
        mActionFinish.setOnClickListener(this);

        // Set the status bar color
        getWindow().setStatusBarColor(getResources().getColor(R.color.setup_background));
        // Navigation bar color
        if (!isNight && !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)) {
            getWindow().setNavigationBarColor(ColorUtils.setAlphaComponent(Color.GRAY, 180));
        } else {
            getWindow().setNavigationBarColor(getResources().getColor(R.color.setup_background));
        }
        // Set the icons of the status bar and the navigation bar light or dark
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            final WindowInsetsController controller = getWindow().getInsetsController();
            if (controller == null) return;
            if (!isNight) {
                controller.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                controller.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS, WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final View view = getWindow().getDecorView();
            view.setSystemUiVisibility(!isNight ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0);
        }
    }

    @Override
    public void onClick(final View v) {
        if (v == mActionFinish) {
            finish();
            return;
        }
        final int currentStep = determineSetupStepNumber();
        final int nextStep;
        if (v == mActionStart) {
            nextStep = STEP_1;
        } else if (v == mActionNext) {
            nextStep = mStepNumber + 1;
        } else if (v == mStep1Bullet && currentStep == STEP_2) {
            nextStep = STEP_1;
        } else {
            nextStep = mStepNumber;
        }
        if (mStepNumber != nextStep) {
            mStepNumber = nextStep;
            updateSetupStepView();
        }
    }

    void invokeSetupWizardOfThisIme() {
        final Intent intent = new Intent();
        intent.setClass(this, SetupWizardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        mNeedsToAdjustStepNumberToSystemState = true;
    }

    private void invokeSettingsOfThisIme() {
        final Intent intent = new Intent();
        intent.setClass(this, SettingsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(SettingsActivity.EXTRA_ENTRY_KEY,
                SettingsActivity.EXTRA_ENTRY_VALUE_APP_ICON);
        startActivity(intent);
    }

    void invokeLanguageAndInputSettings() {
        final Intent intent = new Intent();
        intent.setAction(Settings.ACTION_INPUT_METHOD_SETTINGS);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        startActivity(intent);
        mNeedsToAdjustStepNumberToSystemState = true;
    }

    void invokeInputMethodPicker() {
        // Invoke input method picker.
        mImm.showInputMethodPicker();
        mNeedsToAdjustStepNumberToSystemState = true;
    }

    private int determineSetupStepNumberFromLauncher() {
        final int stepNumber = determineSetupStepNumber();
        if (stepNumber == STEP_1) {
            return STEP_WELCOME;
        }
        if (stepNumber == STEP_3) {
            return STEP_LAUNCHING_IME_SETTINGS;
        }
        return stepNumber;
    }

    private int determineSetupStepNumber() {
        mHandler.cancelPollingImeSettings();
        if (FORCE_TO_SHOW_WELCOME_SCREEN) {
            return STEP_1;
        }
        if (!UncachedInputMethodManagerUtils.isThisImeEnabled(this, mImm)) {
            return STEP_1;
        }
        if (!UncachedInputMethodManagerUtils.isThisImeCurrent(this, mImm)) {
            return STEP_2;
        }
        return STEP_3;
    }

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_STEP, mStepNumber);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mStepNumber = savedInstanceState.getInt(STATE_STEP);
    }

    private static boolean isInSetupSteps(final int stepNumber) {
        return stepNumber >= STEP_1 && stepNumber <= STEP_3;
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        // Probably the setup wizard has been invoked from "Recent" menu. The setup step number
        // needs to be adjusted to system state, because the state (IME is enabled and/or current)
        // may have been changed.
        if (isInSetupSteps(mStepNumber)) {
            mStepNumber = determineSetupStepNumber();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mStepNumber == STEP_LAUNCHING_IME_SETTINGS) {
            // Prevent white screen flashing while launching settings activity.
            mSetupWizard.setVisibility(View.INVISIBLE);
            invokeSettingsOfThisIme();
            mStepNumber = STEP_BACK_FROM_IME_SETTINGS;
            return;
        }
        if (mStepNumber == STEP_BACK_FROM_IME_SETTINGS) {
            finish();
            return;
        }
        updateSetupStepView();
    }

    @Override
    public void onBackPressed() {
        if (mStepNumber == STEP_1) {
            mStepNumber = STEP_WELCOME;
            updateSetupStepView();
            return;
        }
        super.onBackPressed();
    }

    void hideWelcomeVideoAndShowWelcomeImage() {
        mWelcomeVideoView.setVisibility(View.GONE);
        mWelcomeImageView.setImageResource(R.drawable.setup_welcome_image);
        mWelcomeImageView.setVisibility(View.VISIBLE);
    }

    private void showAndStartWelcomeVideo() {
        mWelcomeVideoView.setVisibility(View.VISIBLE);
        mWelcomeVideoView.setVideoURI(mWelcomeVideoUri);
        mWelcomeVideoView.start();
    }

    private void hideAndStopWelcomeVideo() {
        mWelcomeVideoView.stopPlayback();
        mWelcomeVideoView.setVisibility(View.GONE);
    }

    @Override
    protected void onPause() {
        hideAndStopWelcomeVideo();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(final boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && mNeedsToAdjustStepNumberToSystemState) {
            mNeedsToAdjustStepNumberToSystemState = false;
            mStepNumber = determineSetupStepNumber();
            updateSetupStepView();
        }
    }

    private void updateSetupStepView() {
        mSetupWizard.setVisibility(View.VISIBLE);
        final boolean welcomeScreen = (mStepNumber == STEP_WELCOME);
        mWelcomeScreen.setVisibility(welcomeScreen ? View.VISIBLE : View.GONE);
        mSetupScreen.setVisibility(welcomeScreen ? View.GONE : View.VISIBLE);
        if (welcomeScreen) {
            if (ENABLE_WELCOME_VIDEO) {
                showAndStartWelcomeVideo();
            } else {
                hideWelcomeVideoAndShowWelcomeImage();
            }
            return;
        }
        hideAndStopWelcomeVideo();
        final boolean isStepActionAlreadyDone = mStepNumber < determineSetupStepNumber();
        mSetupStepGroup.enableStep(mStepNumber, isStepActionAlreadyDone);
        mActionNext.setVisibility(isStepActionAlreadyDone ? View.VISIBLE : View.GONE);
        mActionFinish.setVisibility((mStepNumber == STEP_3) ? View.VISIBLE : View.GONE);
    }

    static final class SetupStep implements View.OnClickListener {
        public final int mStepNo;
        private final View mStepView;
        private final TextView mBulletView;
        private final ColorStateList mTextColorStateList;
        private final String mInstruction;
        private final String mFinishedInstruction;
        private final TextView mActionLabel;
        private Runnable mAction;

        public SetupStep(final int stepNo, final String applicationName, final TextView bulletView,
                final View stepView, final int title, final int instruction,
                final int finishedInstruction, final int actionIcon, final int actionLabel) {
            mStepNo = stepNo;
            mStepView = stepView;
            mBulletView = bulletView;
            final Resources res = stepView.getResources();
            mTextColorStateList = AppCompatResources.getColorStateList(mStepView.getContext(), R.color.setup_step_action_text);

            final TextView titleView = mStepView.findViewById(R.id.setup_step_title);
            titleView.setText(res.getString(title, applicationName));

            mInstruction = (instruction == 0) ? null : res.getString(instruction, applicationName);
            mFinishedInstruction = (finishedInstruction == 0) ? null : res.getString(finishedInstruction, applicationName);

            mActionLabel = mStepView.findViewById(R.id.setup_step_action_label);
            mActionLabel.setText(res.getString(actionLabel));
            final Drawable actionIconDrawable = ResourcesCompat.getDrawable(res, actionIcon, null);
            if (actionIconDrawable == null) {
                return;
            }
            DrawableCompat.setTintList(actionIconDrawable, mTextColorStateList);
            if (actionIcon == 0) {
                final int paddingEnd = mActionLabel.getPaddingEnd();
                mActionLabel.setPaddingRelative(paddingEnd, 0, paddingEnd, 0);
            } else {
                final int size = (int) TypedValue.applyDimension(COMPLEX_UNIT_DIP, 24f, res.getDisplayMetrics());
                actionIconDrawable.setBounds(0,0, size, size);
                mActionLabel.setCompoundDrawablesRelative(actionIconDrawable, null, null, null);
            }
        }

        public void setEnabled(final boolean enabled, final boolean isStepActionAlreadyDone) {
            mStepView.setVisibility(enabled ? View.VISIBLE : View.GONE);
            mBulletView.setTextColor(enabled
                    ? mBulletView.getContext().getResources().getColor(R.color.setup_step_action_text_pressed)
                    : mBulletView.getContext().getResources().getColor(R.color.setup_text_action));
            final TextView instructionView = mStepView.findViewById(R.id.setup_step_instruction);
            instructionView.setText(isStepActionAlreadyDone ? mFinishedInstruction : mInstruction);
            mActionLabel.setVisibility(isStepActionAlreadyDone ? View.GONE : View.VISIBLE);
        }

        public void setAction(final Runnable action) {
            mActionLabel.setOnClickListener(this);
            mAction = action;
        }

        @Override
        public void onClick(final View v) {
            if (v == mActionLabel && mAction != null)
                mAction.run();
        }
    }

    static final class SetupStepGroup {
        private final SetupStepIndicatorView mIndicatorView;
        private final ArrayList<SetupStep> mGroup = new ArrayList<>();

        public SetupStepGroup(final SetupStepIndicatorView indicatorView) {
            mIndicatorView = indicatorView;
        }

        public void addStep(final SetupStep step) {
            mGroup.add(step);
        }

        public void enableStep(final int enableStepNo, final boolean isStepActionAlreadyDone) {
            for (final SetupStep step : mGroup) {
                step.setEnabled(step.mStepNo == enableStepNo, isStepActionAlreadyDone);
            }
            mIndicatorView.setIndicatorPosition(enableStepNo - STEP_1, mGroup.size());
        }
    }
}
