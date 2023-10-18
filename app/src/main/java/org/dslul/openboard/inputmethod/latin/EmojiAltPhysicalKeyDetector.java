/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin;

import android.content.res.Resources;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;

import androidx.annotation.NonNull;

import org.dslul.openboard.inputmethod.keyboard.KeyboardSwitcher;
import org.dslul.openboard.inputmethod.latin.settings.Settings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * A class for detecting Emoji-Alt physical key.
 */
final class EmojiAltPhysicalKeyDetector {
    private static final String TAG = "EmojiAltPhysKeyDetector";
    private static final boolean DEBUG = false;

    private final List<EmojiHotKeys> mHotKeysList;

    private static class HotKeySet extends HashSet<Pair<Integer, Integer>> { }

    private abstract class EmojiHotKeys {
        private final String mName;
        private final HotKeySet mKeySet;

        boolean mCanFire;
        int mMetaState;

        public EmojiHotKeys(final String name, HotKeySet keySet) {
            mName = name;
            mKeySet = keySet;
            mCanFire = false;
        }

        public void onKeyDown(@NonNull final KeyEvent keyEvent) {
            if (DEBUG) {
                Log.d(TAG, "EmojiHotKeys.onKeyDown() - " + mName + " - considering " + keyEvent);
            }

            final Pair<Integer, Integer> key =
                    Pair.create(keyEvent.getKeyCode(), keyEvent.getMetaState());
            if (mKeySet.contains(key)) {
                if (DEBUG) {
                   Log.d(TAG, "EmojiHotKeys.onKeyDown() - " + mName + " - enabling action");
                }
                mCanFire = true;
                mMetaState = keyEvent.getMetaState();
            } else if (mCanFire) {
                if (DEBUG) {
                   Log.d(TAG, "EmojiHotKeys.onKeyDown() - " + mName + " - disabling action");
                }
                mCanFire = false;
            }
        }

        public void onKeyUp(@NonNull final KeyEvent keyEvent) {
            if (DEBUG) {
                Log.d(TAG, "EmojiHotKeys.onKeyUp() - " + mName + " - considering " + keyEvent);
            }

            final int keyCode = keyEvent.getKeyCode();
            int metaState = keyEvent.getMetaState();
            if (KeyEvent.isModifierKey(keyCode)) {
                 // Try restoring meta stat in case the released key was a modifier.
                 // I am sure one can come up with scenarios to break this, but it
                 // seems to work well in practice.
                 metaState |= mMetaState;
            }

            final Pair<Integer, Integer> key = Pair.create(keyCode, metaState);
            if (mKeySet.contains(key)) {
                if (mCanFire) {
                    if (!keyEvent.isCanceled()) {
                        if (DEBUG) {
                            Log.d(TAG, "EmojiHotKeys.onKeyUp() - " + mName + " - firing action");
                        }
                        action();
                    } else {
                        // This key up event was a part of key combinations and
                        // should be ignored.
                        if (DEBUG) {
                            Log.d(TAG, "EmojiHotKeys.onKeyUp() - " + mName + " - canceled, ignoring action");
                        }
                    }
                    mCanFire = false;
                }
            }

            if (mCanFire) {
                if (DEBUG) {
                    Log.d(TAG, "EmojiHotKeys.onKeyUp() - " + mName + " - disabling action");
                }
                mCanFire = false;
            }
        }

        protected abstract void action();
    }

    public EmojiAltPhysicalKeyDetector(@NonNull final Resources resources) {
        mHotKeysList = new ArrayList<>();

        final HotKeySet emojiSwitchSet = parseHotKeys(
                resources, R.array.keyboard_switcher_emoji);
        final EmojiHotKeys emojiHotKeys = new EmojiHotKeys("emoji", emojiSwitchSet) {
            @Override
            protected void action() {
                final KeyboardSwitcher switcher = KeyboardSwitcher.getInstance();
                switcher.onToggleKeyboard(KeyboardSwitcher.KeyboardSwitchState.EMOJI);
            }
        };
        mHotKeysList.add(emojiHotKeys);

        final HotKeySet symbolsSwitchSet = parseHotKeys(
                resources, R.array.keyboard_switcher_symbols_shifted);
        final EmojiHotKeys symbolsHotKeys = new EmojiHotKeys("symbols", symbolsSwitchSet) {
            @Override
            protected void action() {
                final KeyboardSwitcher switcher = KeyboardSwitcher.getInstance();
                switcher.onToggleKeyboard(KeyboardSwitcher.KeyboardSwitchState.SYMBOLS_SHIFTED);
            }
        };
        mHotKeysList.add(symbolsHotKeys);
    }

    public void onKeyDown(@NonNull final KeyEvent keyEvent) {
        if (DEBUG) {
            Log.d(TAG, "onKeyDown(): " + keyEvent);
        }

        if (shouldProcessEvent(keyEvent)) {
            for (EmojiHotKeys hotKeys : mHotKeysList) {
                hotKeys.onKeyDown(keyEvent);
            }
        }
    }

    public void onKeyUp(@NonNull final KeyEvent keyEvent) {
        if (DEBUG) {
            Log.d(TAG, "onKeyUp(): " + keyEvent);
        }

        if (shouldProcessEvent(keyEvent)) {
            for (EmojiHotKeys hotKeys : mHotKeysList) {
                hotKeys.onKeyUp(keyEvent);
            }
        }
    }

    private static boolean shouldProcessEvent(@NonNull final KeyEvent keyEvent) {
        if (!Settings.getInstance().getCurrent().mEnableEmojiAltPhysicalKey) {
            // The feature is disabled.
            if (DEBUG) {
                Log.d(TAG, "shouldProcessEvent(): Disabled");
            }
            return false;
        }

        return true;
    }

    private static HotKeySet parseHotKeys(
            @NonNull final Resources resources, final int resourceId) {
        final HotKeySet keySet = new HotKeySet();
        final String name = resources.getResourceEntryName(resourceId);
        final String[] values = resources.getStringArray(resourceId);
        for (int i = 0; values != null && i < values.length; i++) {
            String[] valuePair = values[i].split(",");
            if (valuePair.length != 2) {
                Log.w(TAG, "Expected 2 integers in " + name + "[" + i + "] : " + values[i]);
            }
            try {
                final int keyCode = Integer.parseInt(valuePair[0]);
                final int metaState = Integer.parseInt(valuePair[1]);
                final Pair<Integer, Integer> key = Pair.create(
                        keyCode, KeyEvent.normalizeMetaState(metaState));
                keySet.add(key);
            } catch (NumberFormatException e) {
                Log.w(TAG, "Failed to parse " + name + "[" + i + "] : " + values[i], e);
            }
        }
        return keySet;
    }
}
