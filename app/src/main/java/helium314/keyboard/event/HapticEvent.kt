package helium314.keyboard.event

import android.view.HapticFeedbackConstants

enum class HapticEvent(@JvmField val feedbackConstant: Int) {
    NO_HAPTICS(HapticFeedbackConstants.NO_HAPTICS),
    KEY_PRESS(HapticFeedbackConstants.KEYBOARD_TAP),
//    KEY_RELEASE(
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
//            HapticFeedbackConstants.KEYBOARD_RELEASE
//        } else {
//            HapticFeedbackConstants.?
//        }
//    ),
    KEY_LONG_PRESS(HapticFeedbackConstants.LONG_PRESS),
//    KEY_REPEAT(HapticFeedbackConstants.?),
//    GESTURE_START(
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//            HapticFeedbackConstants.GESTURE_START
//        } else {
//            HapticFeedbackConstants.?
//        }
//    ),
    GESTURE_MOVE(HapticFeedbackConstants.CLOCK_TICK),
//    GESTURE_END(
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//            HapticFeedbackConstants.GESTURE_END
//        } else {
//            HapticFeedbackConstants.?
//        }
//    );
}
