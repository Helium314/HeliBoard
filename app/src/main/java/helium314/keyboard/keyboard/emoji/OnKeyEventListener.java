// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.keyboard.emoji;

import helium314.keyboard.keyboard.Key;

/**
 * Interface to handle touch events from non-View-based elements
 * such as Emoji buttons.
 */
public interface OnKeyEventListener {

    /**
     * Called when a key is pressed by the user
     */
    void onPressKey(Key key);

    /**
     * Called when a key is released.
     * This may be called without any prior call to {@link OnKeyEventListener#onPressKey(Key)},
     * for example when a key from a more keys keyboard is selected by releasing touch on it.
     */
    void onReleaseKey(Key key);
}
