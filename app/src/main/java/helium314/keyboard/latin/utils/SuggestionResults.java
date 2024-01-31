/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils;

import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo;
import helium314.keyboard.latin.define.ProductionFlags;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.TreeSet;

/**
 * A TreeSet of SuggestedWordInfo that is bounded in size and throws everything that's smaller
 * than its limit
 */
public final class SuggestionResults extends TreeSet<SuggestedWordInfo> {
    public final ArrayList<SuggestedWordInfo> mRawSuggestions;
    // TODO: Instead of a boolean , we may want to include the context of this suggestion results,
    // such as {@link NgramContext}.
    public final boolean mIsBeginningOfSentence;
    public final boolean mFirstSuggestionExceedsConfidenceThreshold;
    private final int mCapacity;

    public SuggestionResults(final int capacity, final boolean isBeginningOfSentence,
            final boolean firstSuggestionExceedsConfidenceThreshold) {
        this(sSuggestedWordInfoComparator, capacity, isBeginningOfSentence,
                firstSuggestionExceedsConfidenceThreshold);
    }

    private SuggestionResults(final Comparator<SuggestedWordInfo> comparator, final int capacity,
            final boolean isBeginningOfSentence,
            final boolean firstSuggestionExceedsConfidenceThreshold) {
        super(comparator);
        mCapacity = capacity;
        if (ProductionFlags.INCLUDE_RAW_SUGGESTIONS) {
            mRawSuggestions = new ArrayList<>();
        } else {
            mRawSuggestions = null;
        }
        mIsBeginningOfSentence = isBeginningOfSentence;
        mFirstSuggestionExceedsConfidenceThreshold = firstSuggestionExceedsConfidenceThreshold;
    }

    @Override
    public boolean add(final SuggestedWordInfo e) {
        if (size() < mCapacity) return super.add(e);
        if (comparator().compare(e, last()) > 0) return false;
        super.add(e);
        pollLast(); // removes the last element
        return true;
    }

    @Override
    public boolean addAll(final Collection<? extends SuggestedWordInfo> e) {
        if (null == e) return false;
        return super.addAll(e);
    }

    static final class SuggestedWordInfoComparator implements Comparator<SuggestedWordInfo> {
        // This comparator ranks the word info with the higher frequency first. That's because
        // that's the order we want our elements in.
        @Override
        public int compare(final SuggestedWordInfo o1, final SuggestedWordInfo o2) {
            if (o1.mScore > o2.mScore) return -1;
            if (o1.mScore < o2.mScore) return 1;
            if (o1.mCodePointCount < o2.mCodePointCount) return -1;
            if (o1.mCodePointCount > o2.mCodePointCount) return 1;
            return o1.mWord.compareTo(o2.mWord);
        }
    }

    private static final SuggestedWordInfoComparator sSuggestedWordInfoComparator =
            new SuggestedWordInfoComparator();
}
