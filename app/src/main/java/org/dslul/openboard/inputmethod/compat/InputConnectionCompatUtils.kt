package org.dslul.openboard.inputmethod.compat

import android.view.inputmethod.InputConnection
import org.dslul.openboard.inputmethod.compat.CompatUtils.ClassWrapper
import org.dslul.openboard.inputmethod.compat.CompatUtils.ToBooleanMethodWrapper

object InputConnectionCompatUtils {
    private var sInputConnectionType: ClassWrapper? = null
    private var sRequestCursorUpdatesMethod: ToBooleanMethodWrapper? = null
    private val isRequestCursorUpdatesAvailable: Boolean
        get() = sRequestCursorUpdatesMethod != null

    /**
     * Local copies of some constants in InputConnection until the SDK becomes publicly available.
     */
    private const val CURSOR_UPDATE_IMMEDIATE = 1 shl 0
    private const val CURSOR_UPDATE_MONITOR = 1 shl 1
    private fun requestCursorUpdatesImpl(inputConnection: InputConnection,
                                         cursorUpdateMode: Int): Boolean {
        return if (!isRequestCursorUpdatesAvailable) {
            false
        } else sRequestCursorUpdatesMethod!!.invoke(inputConnection, cursorUpdateMode)
    }

    /**
     * Requests the editor to call back [InputMethodManager.updateCursorAnchorInfo].
     * @param inputConnection the input connection to which the request is to be sent.
     * @param enableMonitor `true` to request the editor to call back the method whenever the
     * cursor/anchor position is changed.
     * @param requestImmediateCallback `true` to request the editor to call back the method
     * as soon as possible to notify the current cursor/anchor position to the input method.
     * @return `false` if the request is not handled. Otherwise returns `true`.
     */
    @JvmStatic
    fun requestCursorUpdates(inputConnection: InputConnection,
                             enableMonitor: Boolean, requestImmediateCallback: Boolean): Boolean {
        val cursorUpdateMode = ((if (enableMonitor) CURSOR_UPDATE_MONITOR else 0)
                or if (requestImmediateCallback) CURSOR_UPDATE_IMMEDIATE else 0)
        return requestCursorUpdatesImpl(inputConnection, cursorUpdateMode)
    }

    init {
        sInputConnectionType = ClassWrapper(InputConnection::class.java)
        sRequestCursorUpdatesMethod = sInputConnectionType!!.getPrimitiveMethod(
                "requestCursorUpdates", false, Int::class.javaPrimitiveType)
    }
}