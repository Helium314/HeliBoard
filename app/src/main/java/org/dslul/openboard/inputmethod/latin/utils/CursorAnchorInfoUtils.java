/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.utils;

import android.annotation.TargetApi;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.inputmethodservice.ExtractEditText;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.text.Layout;
import android.text.Spannable;
import android.text.Spanned;
import android.view.View;
import android.view.ViewParent;
import android.view.inputmethod.CursorAnchorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.dslul.openboard.inputmethod.compat.CursorAnchorInfoCompatWrapper;

/**
 * This class allows input methods to extract {@link CursorAnchorInfo} directly from the given
 * {@link TextView}. This is useful and even necessary to support full-screen mode where the default
 * {@link InputMethodService#onUpdateCursorAnchorInfo(CursorAnchorInfo)} event callback must be
 * ignored because it reports the character locations of the target application rather than
 * characters on {@link ExtractEditText}.
 */
public final class CursorAnchorInfoUtils {
    private CursorAnchorInfoUtils() {
        // This helper class is not instantiable.
    }

    private static boolean isPositionVisible(final View view, final float positionX,
            final float positionY) {
        final float[] position = new float[] { positionX, positionY };
        View currentView = view;

        while (currentView != null) {
            if (currentView != view) {
                // Local scroll is already taken into account in positionX/Y
                position[0] -= currentView.getScrollX();
                position[1] -= currentView.getScrollY();
            }

            if (position[0] < 0 || position[1] < 0 ||
                    position[0] > currentView.getWidth() || position[1] > currentView.getHeight()) {
                return false;
            }

            if (!currentView.getMatrix().isIdentity()) {
                currentView.getMatrix().mapPoints(position);
            }

            position[0] += currentView.getLeft();
            position[1] += currentView.getTop();

            final ViewParent parent = currentView.getParent();
            if (parent instanceof View) {
                currentView = (View) parent;
            } else {
                // We've reached the ViewRoot, stop iterating
                currentView = null;
            }
        }

        // We've been able to walk up the view hierarchy and the position was never clipped
        return true;
    }

    /**
     * Extracts {@link CursorAnchorInfoCompatWrapper} from the given {@link TextView}.
     * @param textView the target text view from which {@link CursorAnchorInfoCompatWrapper} is to
     * be extracted.
     * @return the {@link CursorAnchorInfoCompatWrapper} object based on the current layout.
     * {@code null} if {@code Build.VERSION.SDK_INT} is 20 or prior or {@link TextView} is not
     * ready to provide layout information.
     */
    @Nullable
    public static CursorAnchorInfoCompatWrapper extractFromTextView(
            @NonNull final TextView textView) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return null;
        }
        return CursorAnchorInfoCompatWrapper.wrap(extractFromTextViewInternal(textView));
    }

    /**
     * Returns {@link CursorAnchorInfo} from the given {@link TextView}.
     * @param textView the target text view from which {@link CursorAnchorInfo} is to be extracted.
     * @return the {@link CursorAnchorInfo} object based on the current layout. {@code null} if it
     * is not feasible.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Nullable
    private static CursorAnchorInfo extractFromTextViewInternal(@NonNull final TextView textView) {
        final Layout layout = textView.getLayout();
        if (layout == null) {
            return null;
        }

        final CursorAnchorInfo.Builder builder = new CursorAnchorInfo.Builder();

        final int selectionStart = textView.getSelectionStart();
        builder.setSelectionRange(selectionStart, textView.getSelectionEnd());

        // Construct transformation matrix from view local coordinates to screen coordinates.
        final Matrix viewToScreenMatrix = new Matrix(textView.getMatrix());
        final int[] viewOriginInScreen = new int[2];
        textView.getLocationOnScreen(viewOriginInScreen);
        viewToScreenMatrix.postTranslate(viewOriginInScreen[0], viewOriginInScreen[1]);
        builder.setMatrix(viewToScreenMatrix);

        if (layout.getLineCount() == 0) {
            return null;
        }
        final Rect lineBoundsWithoutOffset = new Rect();
        final Rect lineBoundsWithOffset = new Rect();
        layout.getLineBounds(0, lineBoundsWithoutOffset);
        textView.getLineBounds(0, lineBoundsWithOffset);
        final float viewportToContentHorizontalOffset = lineBoundsWithOffset.left
                - lineBoundsWithoutOffset.left - textView.getScrollX();
        final float viewportToContentVerticalOffset = lineBoundsWithOffset.top
                - lineBoundsWithoutOffset.top - textView.getScrollY();

        final CharSequence text = textView.getText();
        if (text instanceof Spannable) {
            // Here we assume that the composing text is marked as SPAN_COMPOSING flag. This is not
            // necessarily true, but basically works.
            int composingTextStart = text.length();
            int composingTextEnd = 0;
            final Spannable spannable = (Spannable) text;
            final Object[] spans = spannable.getSpans(0, text.length(), Object.class);
            for (Object span : spans) {
                final int spanFlag = spannable.getSpanFlags(span);
                if ((spanFlag & Spanned.SPAN_COMPOSING) != 0) {
                    composingTextStart = Math.min(composingTextStart,
                            spannable.getSpanStart(span));
                    composingTextEnd = Math.max(composingTextEnd, spannable.getSpanEnd(span));
                }
            }

            final boolean hasComposingText =
                    (0 <= composingTextStart) && (composingTextStart < composingTextEnd);
            if (hasComposingText) {
                final CharSequence composingText = text.subSequence(composingTextStart,
                        composingTextEnd);
                builder.setComposingText(composingTextStart, composingText);

                final int minLine = layout.getLineForOffset(composingTextStart);
                final int maxLine = layout.getLineForOffset(composingTextEnd - 1);
                for (int line = minLine; line <= maxLine; ++line) {
                    final int lineStart = layout.getLineStart(line);
                    final int lineEnd = layout.getLineEnd(line);
                    final int offsetStart = Math.max(lineStart, composingTextStart);
                    final int offsetEnd = Math.min(lineEnd, composingTextEnd);
                    final boolean ltrLine =
                            layout.getParagraphDirection(line) == Layout.DIR_LEFT_TO_RIGHT;
                    final float[] widths = new float[offsetEnd - offsetStart];
                    layout.getPaint().getTextWidths(text, offsetStart, offsetEnd, widths);
                    final float top = layout.getLineTop(line);
                    final float bottom = layout.getLineBottom(line);
                    for (int offset = offsetStart; offset < offsetEnd; ++offset) {
                        final float charWidth = widths[offset - offsetStart];
                        final boolean isRtl = layout.isRtlCharAt(offset);
                        final float primary = layout.getPrimaryHorizontal(offset);
                        final float secondary = layout.getSecondaryHorizontal(offset);
                        // TODO: This doesn't work perfectly for text with custom styles and TAB
                        // chars.
                        final float left;
                        final float right;
                        if (ltrLine) {
                            if (isRtl) {
                                left = secondary - charWidth;
                                right = secondary;
                            } else {
                                left = primary;
                                right = primary + charWidth;
                            }
                        } else {
                            if (!isRtl) {
                                left = secondary;
                                right = secondary + charWidth;
                            } else {
                                left = primary - charWidth;
                                right = primary;
                            }
                        }
                        // TODO: Check top-right and bottom-left as well.
                        final float localLeft = left + viewportToContentHorizontalOffset;
                        final float localRight = right + viewportToContentHorizontalOffset;
                        final float localTop = top + viewportToContentVerticalOffset;
                        final float localBottom = bottom + viewportToContentVerticalOffset;
                        final boolean isTopLeftVisible = isPositionVisible(textView,
                                localLeft, localTop);
                        final boolean isBottomRightVisible =
                                isPositionVisible(textView, localRight, localBottom);
                        int characterBoundsFlags = 0;
                        if (isTopLeftVisible || isBottomRightVisible) {
                            characterBoundsFlags |= CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION;
                        }
                        if (!isTopLeftVisible || !isTopLeftVisible) {
                            characterBoundsFlags |= CursorAnchorInfo.FLAG_HAS_INVISIBLE_REGION;
                        }
                        if (isRtl) {
                            characterBoundsFlags |= CursorAnchorInfo.FLAG_IS_RTL;
                        }
                        // Here offset is the index in Java chars.
                        builder.addCharacterBounds(offset, localLeft, localTop, localRight,
                                localBottom, characterBoundsFlags);
                    }
                }
            }
        }

        // Treat selectionStart as the insertion point.
        if (0 <= selectionStart) {
            final int offset = selectionStart;
            final int line = layout.getLineForOffset(offset);
            final float insertionMarkerX = layout.getPrimaryHorizontal(offset)
                    + viewportToContentHorizontalOffset;
            final float insertionMarkerTop = layout.getLineTop(line)
                    + viewportToContentVerticalOffset;
            final float insertionMarkerBaseline = layout.getLineBaseline(line)
                    + viewportToContentVerticalOffset;
            final float insertionMarkerBottom = layout.getLineBottom(line)
                    + viewportToContentVerticalOffset;
            final boolean isTopVisible =
                    isPositionVisible(textView, insertionMarkerX, insertionMarkerTop);
            final boolean isBottomVisible =
                    isPositionVisible(textView, insertionMarkerX, insertionMarkerBottom);
            int insertionMarkerFlags = 0;
            if (isTopVisible || isBottomVisible) {
                insertionMarkerFlags |= CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION;
            }
            if (!isTopVisible || !isBottomVisible) {
                insertionMarkerFlags |= CursorAnchorInfo.FLAG_HAS_INVISIBLE_REGION;
            }
            if (layout.isRtlCharAt(offset)) {
                insertionMarkerFlags |= CursorAnchorInfo.FLAG_IS_RTL;
            }
            builder.setInsertionMarkerLocation(insertionMarkerX, insertionMarkerTop,
                    insertionMarkerBaseline, insertionMarkerBottom, insertionMarkerFlags);
        }
        return builder.build();
    }
}
