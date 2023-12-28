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
import org.dslul.openboard.inputmethod.latin.utils.ResourceUtils
import kotlin.math.sqrt

class EmojiParser(private val params: KeyboardParams, private val context: Context) {

    fun parse(): ArrayList<ArrayList<KeyParams>> {
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
        val moreEmojisArray = if (params.mId.mElementId != KeyboardId.ELEMENT_EMOJI_CATEGORY2) null
            else context.resources.getStringArray(R.array.emoji_people_body_more)
        if (moreEmojisArray != null && emojiArray.size != moreEmojisArray.size)
            throw(IllegalStateException("Inconsistent array size between codesArray and moreKeysArray"))

        val row = ArrayList<KeyParams>(emojiArray.size)
        var currentX = params.mLeftPadding.toFloat()
        val currentY = params.mTopPadding.toFloat() // no need to ever change, assignment to rows into rows is done in DynamicGridKeyboard

        // determine key width for default settings (no number row, no one-handed mode, 100% height and bottom padding scale)
        // this is a bit long, but ensures that emoji size stays the same, independent of these settings
        val defaultKeyWidth = (ResourceUtils.getDefaultKeyboardWidth(context.resources) - params.mLeftPadding - params.mRightPadding) * params.mDefaultRelativeKeyWidth
        val keyWidth = defaultKeyWidth * sqrt(Settings.getInstance().current.mKeyboardHeightScale)
        val defaultKeyboardHeight = ResourceUtils.getDefaultKeyboardHeight(context.resources, false)
        val defaultBottomPadding = context.resources.getFraction(R.fraction.config_keyboard_bottom_padding_holo, defaultKeyboardHeight, defaultKeyboardHeight)
        val emojiKeyboardHeight = ResourceUtils.getDefaultKeyboardHeight(context.resources, false) * 0.75f + params.mVerticalGap - defaultBottomPadding - context.resources.getDimensionPixelSize(R.dimen.config_emoji_category_page_id_height)
        val keyHeight = emojiKeyboardHeight * params.mDefaultRelativeRowHeight * Settings.getInstance().current.mKeyboardHeightScale // still apply height scale to key

        emojiArray.forEachIndexed { i, codeArraySpec ->
            val keyParams = parseEmojiKey(codeArraySpec, moreEmojisArray?.get(i)?.takeIf { it.isNotEmpty() }) ?: return@forEachIndexed
            keyParams.xPos = currentX
            keyParams.yPos = currentY
            keyParams.mFullWidth = keyWidth
            keyParams.mFullHeight = keyHeight
            currentX += keyParams.mFullWidth
            row.add(keyParams)
        }
        return arrayListOf(row)
    }

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

    private fun parseEmojiKey(spec: String, moreKeysString: String? = null): KeyParams? {
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