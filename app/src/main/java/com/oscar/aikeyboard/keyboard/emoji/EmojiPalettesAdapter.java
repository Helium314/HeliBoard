/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.oscar.aikeyboard.keyboard.emoji;

import com.oscar.aikeyboard.latin.utils.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.oscar.aikeyboard.keyboard.Key;
import com.oscar.aikeyboard.keyboard.Keyboard;
import com.oscar.aikeyboard.keyboard.KeyboardView;
import com.oscar.aikeyboard.R;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.oscar.aikeyboard.latin.settings.Settings;


final class EmojiPalettesAdapter extends RecyclerView.Adapter<EmojiPalettesAdapter.ViewHolder>{
    private static final String TAG = EmojiPalettesAdapter.class.getSimpleName();
    private static final boolean DEBUG_PAGER = false;

    private final OnKeyEventListener mListener;
    private final DynamicGridKeyboard mRecentsKeyboard;
    private final SparseArray<EmojiPageKeyboardView> mActiveKeyboardViews = new SparseArray<>();
    private final EmojiCategory mEmojiCategory;
    private int mActivePosition = 0;

    public EmojiPalettesAdapter(final EmojiCategory emojiCategory,
            final OnKeyEventListener listener) {
        mEmojiCategory = emojiCategory;
        mListener = listener;
        mRecentsKeyboard = mEmojiCategory.getKeyboard(EmojiCategory.ID_RECENTS, 0);
    }

    public void flushPendingRecentKeys() {
        mRecentsKeyboard.flushPendingRecentKeys();
        final KeyboardView recentKeyboardView =
                mActiveKeyboardViews.get(mEmojiCategory.getRecentTabId());
        if (recentKeyboardView != null) {
            recentKeyboardView.invalidateAllKeys();
        }
    }

    public void addRecentKey(final Key key) {
        if (Settings.getInstance().getCurrent().mIncognitoModeEnabled) {
            // We do not want to log recent keys while being in incognito
            return;
        }
        if (mEmojiCategory.isInRecentTab()) {
            mRecentsKeyboard.addPendingKey(key);
            return;
        }
        mRecentsKeyboard.addKeyFirst(key);
        final KeyboardView recentKeyboardView =
                mActiveKeyboardViews.get(mEmojiCategory.getRecentTabId());
        if (recentKeyboardView != null) {
            recentKeyboardView.invalidateAllKeys();
        }
    }

    public void onPageScrolled() {
        releaseCurrentKey(false /* withKeyRegistering */);
    }

    public void releaseCurrentKey(final boolean withKeyRegistering) {
        // Make sure the delayed key-down event (highlight effect and haptic feedback) will be
        // canceled.
        final EmojiPageKeyboardView currentKeyboardView =
                mActiveKeyboardViews.get(mActivePosition);
        if (currentKeyboardView == null) {
            return;
        }
        currentKeyboardView.releaseCurrentKey(withKeyRegistering);
    }

/*
    @Override
    public Object instantiateItem(final ViewGroup container, final int position) {
        if (DEBUG_PAGER) {
            Log.d(TAG, "instantiate item: " + position);
        }
        final EmojiPageKeyboardView oldKeyboardView = mActiveKeyboardViews.get(position);
        if (oldKeyboardView != null) {
            oldKeyboardView.deallocateMemory();
            // This may be redundant but wanted to be safer..
            mActiveKeyboardViews.remove(position);
        }
        final Keyboard keyboard =
                mEmojiCategory.getKeyboardFromPagePosition(position);
        final LayoutInflater inflater = LayoutInflater.from(container.getContext());
        final EmojiPageKeyboardView keyboardView = (EmojiPageKeyboardView) inflater.inflate(
                R.layout.emoji_keyboard_page, container, false);
        keyboardView.setKeyboard(keyboard);
        keyboardView.setOnKeyEventListener(mListener);
        container.addView(keyboardView);
        mActiveKeyboardViews.put(position, keyboardView);
        return keyboardView;
    }

    @Override
    public void setPrimaryItem(final ViewGroup container, final int position,
            final Object object) {
        if (mActivePosition == position) {
            return;
        }
        final EmojiPageKeyboardView oldKeyboardView = mActiveKeyboardViews.get(mActivePosition);
        if (oldKeyboardView != null) {
            oldKeyboardView.releaseCurrentKey(false);
            oldKeyboardView.deallocateMemory();
        }
        mActivePosition = position;
    }

    @Override
    public boolean isViewFromObject(final View view, final Object object) {
        return view == object;
    }

    @Override
    public void destroyItem(final ViewGroup container, final int position,
            final Object object) {
        if (DEBUG_PAGER) {
            Log.d(TAG, "destroy item: " + position + ", " + object.getClass().getSimpleName());
        }
        final EmojiPageKeyboardView keyboardView = mActiveKeyboardViews.get(position);
        if (keyboardView != null) {
            keyboardView.deallocateMemory();
            mActiveKeyboardViews.remove(position);
        }
        if (object instanceof View) {
            container.removeView((View)object);
        } else {
            Log.w(TAG, "Warning!!! Emoji palette may be leaking. " + object);
        }
    }
*/

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        /*if (DEBUG_PAGER) {
            Log.d(TAG, "instantiate item: " + viewType);
        }
        final EmojiPageKeyboardView oldKeyboardView = mActiveKeyboardViews.get(viewType);
        if (oldKeyboardView != null) {
            oldKeyboardView.deallocateMemory();
            // This may be redundant but wanted to be safer..
            mActiveKeyboardViews.remove(viewType);
        }
        final Keyboard keyboard =
                mEmojiCategory.getKeyboardFromPagePosition(parent.getVerticalScrollbarPosition());*/
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        final EmojiPageKeyboardView keyboardView = (EmojiPageKeyboardView)inflater.inflate(
                R.layout.emoji_keyboard_page, parent, false);
        /*keyboardView.setKeyboard(keyboard);
        keyboardView.setOnKeyEventListener(mListener);
        parent.addView(keyboardView);
        mActiveKeyboardViews.put(parent.getVerticalScrollbarPosition(), keyboardView);*/
        return new ViewHolder(keyboardView);
    }

    @Override
    public void onBindViewHolder(@NonNull EmojiPalettesAdapter.ViewHolder holder, int position) {
        if (DEBUG_PAGER) {
            Log.d(TAG, "instantiate item: " + position);
        }
        final EmojiPageKeyboardView oldKeyboardView = mActiveKeyboardViews.get(position);
        if (oldKeyboardView != null) {
            oldKeyboardView.deallocateMemory();
            // This may be redundant but wanted to be safer..
            mActiveKeyboardViews.remove(position);
        }
        final Keyboard keyboard =
                mEmojiCategory.getKeyboardFromAdapterPosition(position);
        holder.getKeyboardView().setKeyboard(keyboard);
        holder.getKeyboardView().setOnKeyEventListener(mListener);
        //parent.addView(keyboardView);
        mActiveKeyboardViews.put(position, holder.getKeyboardView());

        /*if (mActivePosition == position) {
            return;
        }
        final EmojiPageKeyboardView oldKeyboardView = mActiveKeyboardViews.get(mActivePosition);
        if (oldKeyboardView != null) {
            oldKeyboardView.releaseCurrentKey(false);
            oldKeyboardView.deallocateMemory();
        }
        mActivePosition = position;*/
    }

    @Override
    public int getItemCount() {
        return mEmojiCategory.getCurrentCategoryPageCount();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private EmojiPageKeyboardView customView;

        public ViewHolder(View v) {
            super(v);
            customView = (EmojiPageKeyboardView) v;
        }

        public EmojiPageKeyboardView getKeyboardView() {
            return customView;
        }

    }
}

