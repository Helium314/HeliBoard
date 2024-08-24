// SPDX-License-Identifier: GPL-3.0-only

package org.samyarth.oskey.keyboard.clipboard

interface OnKeyEventListener {

    fun onKeyDown(clipId: Long)

    fun onKeyUp(clipId: Long)

}