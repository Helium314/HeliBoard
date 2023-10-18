/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.inputlogic;

/**
 * Class for managing space states.
 *
 * At any given time, the input logic is in one of five possible space states. Depending on the
 * current space state, some behavior will change; the prime example of this is the PHANTOM state,
 * in which any subsequent letter input will input a space before the letter. Read on the
 * description inside this class for each of the space states.
 */
public class SpaceState {
    // None: the state where all the keyboard behavior is the most "standard" and no automatic
    // input is added or removed. In this state, all self-inserting keys only insert themselves,
    // and backspace removes one character.
    public static final int NONE = 0;
    // Double space: the state where the user pressed space twice quickly, which LatinIME
    // resolved as period-space. In this state, pressing backspace will undo the
    // double-space-to-period insertion: it will replace ". " with "  ".
    public static final int DOUBLE = 1;
    // Swap punctuation: the state where a weak space and a punctuation from the suggestion strip
    // have just been swapped. In this state, pressing backspace will undo the swap: the
    // characters will be swapped back back, and the space state will go to WEAK.
    public static final int SWAP_PUNCTUATION = 2;
    // Weak space: a space that should be swapped only by suggestion strip punctuation. Weak
    // spaces happen when the user presses space, accepting the current suggestion (whether
    // it's an auto-correction or not). In this state, pressing a punctuation from the suggestion
    // strip inserts it before the space (while it inserts it after the space in the NONE state).
    public static final int WEAK = 3;
    // Phantom space: a not-yet-inserted space that should get inserted on the next input,
    // character provided it's not a separator. If it's a separator, the phantom space is dropped.
    // Phantom spaces happen when a user chooses a word from the suggestion strip. In this state,
    // non-separators insert a space before they get inserted.
    public static final int PHANTOM = 4;

    private SpaceState() {
        // This class is not publicly instantiable.
    }
}
