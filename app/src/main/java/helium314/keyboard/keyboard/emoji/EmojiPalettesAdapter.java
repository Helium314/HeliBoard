/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.emoji;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import helium314.keyboard.keyboard.Keyboard;
import helium314.keyboard.latin.R;

final class EmojiPalettesAdapter extends RecyclerView.Adapter<EmojiPalettesAdapter.ViewHolder> {
    private final int mCategoryId;
    private final EmojiViewCallback mEmojiViewCallback;
    private final EmojiCategory mEmojiCategory;
    private final FocusRequestHandler mFocusHandler;

    public interface FocusRequestHandler {
        boolean requestFocusOnTab(int index);
        boolean requestFocusOnBottomRow();
    }

    public EmojiPalettesAdapter(final EmojiCategory emojiCategory,
                                int categoryId,
                                final EmojiViewCallback emojiViewCallback,
                                final FocusRequestHandler focusHandler) {
        mEmojiCategory = emojiCategory;
        mCategoryId = categoryId;
        mEmojiViewCallback = emojiViewCallback;
        mFocusHandler = focusHandler;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        final EmojiPageKeyboardView keyboardView = (EmojiPageKeyboardView) inflater.inflate(
            R.layout.emoji_keyboard_page, parent, false);
        keyboardView.setEmojiViewCallback(mEmojiViewCallback);
        keyboardView.setFocusable(true);
        keyboardView.setFocusableInTouchMode(true);
        keyboardView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    return mFocusHandler.requestFocusOnTab(0);
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    return mFocusHandler.requestFocusOnBottomRow();
                }
            }
            return false;
        });

        return new ViewHolder(keyboardView);
    }


    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final Keyboard keyboard = mEmojiCategory.getKeyboardFromAdapterPosition(mCategoryId, position);
        holder.getKeyboardView().setKeyboard(keyboard);
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull ViewHolder holder) {
        holder.getKeyboardView().releaseCurrentKey(false);
        holder.getKeyboardView().deallocateMemory();
    }

    @Override
    public int getItemCount() {
        return mEmojiCategory.getCategoryPageCount(mCategoryId);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final EmojiPageKeyboardView customView;

        public ViewHolder(@NonNull View v) {
            super(v);
            customView = (EmojiPageKeyboardView) v;
        }

        public EmojiPageKeyboardView getKeyboardView() {
            return customView;
        }
    }
}
