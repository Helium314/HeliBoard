// SPDX-License-Identifier: GPL-3.0-only
package org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser

import android.content.Context
import android.os.Build
import org.dslul.openboard.inputmethod.keyboard.Key
import org.dslul.openboard.inputmethod.keyboard.Key.KeyParams
import org.dslul.openboard.inputmethod.keyboard.KeyboardId
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardParams
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.common.Constants
import org.dslul.openboard.inputmethod.latin.common.StringUtils
import org.dslul.openboard.inputmethod.latin.settings.Settings
import kotlin.math.round
import kotlin.math.sqrt

class EmojiParser(private val params: KeyboardParams, private val context: Context) {

    fun parse(splitKeyboard: Boolean): ArrayList<ArrayList<KeyParams>> { // todo: split should be read from params, but currently this is disabled, right?
        val emojiArrayId = when (params.mId.mElementId) {
            KeyboardId.ELEMENT_EMOJI_RECENTS -> R.array.emoji_recents
            KeyboardId.ELEMENT_EMOJI_CATEGORY1 -> R.array.emoji_smileys_emotion
            KeyboardId.ELEMENT_EMOJI_CATEGORY2 -> R.array.emoji_people_body
            KeyboardId.ELEMENT_EMOJI_CATEGORY3 -> R.array.emoji_animals_nature
            KeyboardId.ELEMENT_EMOJI_CATEGORY4 -> R.array.emoji_food_drink
            KeyboardId.ELEMENT_EMOJI_CATEGORY5 -> R.array.emoji_travel_places
            KeyboardId.ELEMENT_EMOJI_CATEGORY6 -> R.array.emoji_activities
            KeyboardId.ELEMENT_EMOJI_CATEGORY7 -> R.array.emoji_objects
            KeyboardId.ELEMENT_EMOJI_CATEGORY8 -> R.array.emoji_symbols
            KeyboardId.ELEMENT_EMOJI_CATEGORY9 -> R.array.emoji_flags
            KeyboardId.ELEMENT_EMOJI_CATEGORY10 -> R.array.emoji_emoticons
            else -> throw(IllegalStateException("can only parse emoji categories where an array exists"))
        }
        val emojiArray = context.resources.getStringArray(emojiArrayId)
        val moreEmojisArray = if (params.mId.mElementId == KeyboardId.ELEMENT_EMOJI_CATEGORY2)
                context.resources.getStringArray(R.array.emoji_people_body_more)
            else null
        if (moreEmojisArray != null && emojiArray.size != moreEmojisArray.size)
            throw(IllegalStateException("Inconsistent array size between codesArray and moreKeysArray"))

        // now we have the params in one long list -> split into lines and maybe add spacer
        // todo: disabled, because it doesn't work properly... spacer keys get added to the end every 3 rows
        //  the sorting and sizing seems to be done in DynamicGridKeyboard
        //  only the template keys there are relevant for dimensions, resizing keys here doesn't have any effect
        //  -> this is really weird and unexpected, and should be changed (might also help with the text emojis...)
/*        val numColumns = (1 / params.mDefaultRelativeKeyWidth).toInt()
        val spacerNumKeys: Int
        val spacerWidth: Float
        if (splitKeyboard) {
            val spacerRelativeWidth = Settings.getInstance().current.mSpacerRelativeWidth
            // adjust gaps for the whole keyboard, so it's the same for all rows
            params.mRelativeHorizontalGap *= 1f / (1f + spacerRelativeWidth)
            params.mHorizontalGap = (params.mRelativeHorizontalGap * params.mId.mWidth).toInt()
            // round the spacer width, so it's a number of keys, and number should be even if emoji count is even, odd otherwise
            spacerNumKeys = (spacerRelativeWidth / params.mDefaultRelativeKeyWidth).roundTo(numColumns % 2 == 0)
            spacerWidth = spacerNumKeys * params.mDefaultRelativeKeyWidth
        } else {
            spacerNumKeys = 0
            spacerWidth = 0f
        }
        val spacerIndex = if (spacerNumKeys > 0) (numColumns - spacerNumKeys) / 2 else -1
*/
        val row = ArrayList<KeyParams>(emojiArray.size)
        var currentX = params.mLeftPadding.toFloat()
        val currentY = params.mTopPadding.toFloat()

        val heightScale = sqrt(Settings.getInstance().current.mKeyboardHeightScale) // resize emojis a little with height scale
        val numColumnsNew = round(1f / (params.mDefaultRelativeKeyWidth * heightScale))
        val numColumnsOld = round(1f / params.mDefaultRelativeKeyWidth)
        // slightly adjust scale to have emojis nicely fill the full width
        // this looks much better than setting some offset in DynamicGridKeyboard (to center the rows)
        val widthScale = numColumnsOld / numColumnsNew - 0.0001f // small offset to have more emojis in a row in edge cases
        // extra scale for height only, to undo the effect of number row increasing absolute key height
        // todo: with this things look ok, but number row still slightly affects emoji size
        val numScale = if (Settings.getInstance().current.mShowsNumberRow) 1.25f else 1f

        emojiArray.forEachIndexed { i, codeArraySpec ->
            val keyParams = parseEmojiKey(codeArraySpec, moreEmojisArray?.get(i)?.takeIf { it.isNotEmpty() }) ?: return@forEachIndexed
            keyParams.setDimensionsFromRelativeSize(currentX, currentY)
            // height is already fully scaled, this undoes part of the rescale
            // we use rawScale here because it influences the emoji size, and looks better that way
            keyParams.mFullHeight /= numScale // hmm... this looks ok
            // scale width to have reasonably sized gaps between emojis (also affects number of emojis per row)
            keyParams.mFullWidth *= widthScale
            currentX += keyParams.mFullWidth
            row.add(keyParams)
//            if (row.size % numColumns == spacerIndex) { // also removed for now (would be missing setting the size and updating x
//                repeat(spacerNumKeys) { row.add(KeyParams.newSpacer(params, params.mDefaultRelativeKeyWidth)) }
//            }
        }
        return arrayListOf(row)
    }

//    private fun Float.roundTo(even: Boolean) = if (toInt() % 2 == if (even) 0 else 1) toInt() else toInt() + 1

    private fun getLabelAndCode(spec: String): Pair<String, Int>? {
        val specAndSdk = spec.split("||")
        if (specAndSdk.getOrNull(1)?.toIntOrNull()?.let { it > Build.VERSION.SDK_INT } == true) return null
        if ("," !in specAndSdk.first()) {
            val code = specAndSdk.first().toIntOrNull(16) ?: return specAndSdk.first() to Constants.CODE_OUTPUT_TEXT // text emojis
            val label = StringUtils.newSingleCodePointString(code)
            return label to code
        }
        val labelBuilder = StringBuilder()
        for (codePointString in specAndSdk.first().split(",")) {
            val cp = codePointString.toInt(16)
            labelBuilder.appendCodePoint(cp)
        }
        return labelBuilder.toString() to Constants.CODE_OUTPUT_TEXT
    }

    private fun parseEmojiKey(spec: String, moreKeysString: String? = null): Key.KeyParams? {
        val (label, code) = getLabelAndCode(spec) ?: return null
        val sb = StringBuilder()
        moreKeysString?.split(";")?.let { moreKeys ->
            moreKeys.forEach {
                val (mkLabel, _) = getLabelAndCode(it) ?: return@forEach
                sb.append(mkLabel).append(",")
            }
        }
        val moreKeysSpec = if (sb.isNotEmpty()) {
            sb.deleteCharAt(sb.length - 1)
            sb.toString()
        } else null
        return KeyParams(
            label,
            code,
            if (moreKeysSpec != null) EMOJI_HINT_LABEL else null,
            moreKeysSpec,
            Key.LABEL_FLAGS_FONT_NORMAL,
            params
        )
    }
}

const val EMOJI_HINT_LABEL = "◥"