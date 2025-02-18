package helium314.keyboard

import helium314.keyboard.latin.utils.LayoutType
import kotlin.test.Test
import kotlin.test.assertEquals

class LayoutTest {
    // todo: add more
    @Test fun extraValueToMainLayout() {
        val extraValue = "KeyboardLayoutSet=MAIN:qwertz+,SupportTouchPositionCorrection"
        assertEquals("qwertz+", LayoutType.getMainLayoutFromExtraValue(extraValue))
    }
}