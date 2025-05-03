// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.internal.keyboard_parser

import android.content.Context
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Key.KeyParams
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils
import java.io.FileNotFoundException
import kotlin.math.sqrt

class EmojiParser(private val params: KeyboardParams, private val context: Context, private val maxSdk: Int) {
    private val MAX_POPUP_LENGTH = 25

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
        val popupEmojisArray = if (params.mId.mElementId != KeyboardId.ELEMENT_EMOJI_CATEGORY2) null
            else context.resources.getStringArray(R.array.emoji_people_body_more)
        if (popupEmojisArray != null && emojiArray.size != popupEmojisArray.size)
            throw(IllegalStateException("Inconsistent array size between codesArray and popupKeysArray"))

        val row = ArrayList<KeyParams>(emojiArray.size)
        var currentX = params.mLeftPadding.toFloat()
        val currentY = params.mTopPadding.toFloat() // no need to ever change, assignment to rows into rows is done in DynamicGridKeyboard

        // determine key width for default settings (no number row, no one-handed mode, 100% height and bottom padding scale)
        // this is a bit long, but ensures that emoji size stays the same, independent of these settings
        // we also ignore side padding for key width, and prefer fewer keys per row over narrower keys
        val defaultKeyWidth = ResourceUtils.getDefaultKeyboardWidth(context)  * params.mDefaultKeyWidth
        val keyWidth = defaultKeyWidth * sqrt(Settings.getValues().mKeyboardHeightScale) * Settings.getValues().mFontSizeMultiplierEmoji
        val defaultKeyboardHeight = ResourceUtils.getDefaultKeyboardHeight(context.resources, false)
        val defaultBottomPadding = context.resources.getFraction(R.fraction.config_keyboard_bottom_padding_holo, defaultKeyboardHeight, defaultKeyboardHeight)
        val emojiKeyboardHeight = ResourceUtils.getDefaultKeyboardHeight(context.resources, false) * 0.75f + params.mVerticalGap - defaultBottomPadding - context.resources.getDimensionPixelSize(R.dimen.config_emoji_category_page_id_height)
        val keyHeight = emojiKeyboardHeight * params.mDefaultRowHeight * Settings.getValues().mKeyboardHeightScale // still apply height scale to key
        val locale = RichInputMethodManager.getInstance().currentSubtype.locale
        val descriptions = getDescriptions(locale.language).plus(getDescriptions(locale.toLanguageTag()))
        emojiArray.forEachIndexed { i, codeArraySpec ->
            val keyParams = parseEmojiKey(codeArraySpec, popupEmojisArray?.get(i)?.takeIf { it.isNotEmpty() }, descriptions) ?: return@forEachIndexed
            keyParams.xPos = currentX
            keyParams.yPos = currentY
            keyParams.mAbsoluteWidth = keyWidth
            keyParams.mAbsoluteHeight = keyHeight
            currentX += keyParams.mAbsoluteWidth
            row.add(keyParams)
        }
        return arrayListOf(row)
    }

    private fun getDescriptions(locale: String): Map<String, String> {
        try {
            val maxPopupLength = (MAX_POPUP_LENGTH / Settings.getValues().mFontSizeMultiplierEmoji).toInt()
            return context.assets.open("emoji/descriptions/$locale.txt")
                .reader().readLines().drop(1).map { it.split('=') }.associateBy(
                    { it[0] },
                    { if (it[1].length < maxPopupLength) it[1] else it[1].take(maxPopupLength - 1) + "…" })
        } catch (_: FileNotFoundException) {
            return emptyMap()
        }
    }

    private fun getLabelAndCode(spec: String): Pair<String, Int>? {
        val specAndSdk = spec.split("||")
        if (specAndSdk.getOrNull(1)?.toIntOrNull()?.let { it > maxSdk } == true) return null
        if ("," !in specAndSdk.first()) {
            val code = specAndSdk.first().toIntOrNull(16) ?: return specAndSdk.first() to KeyCode.MULTIPLE_CODE_POINTS // text emojis
            val label = StringUtils.newSingleCodePointString(code)
            return label to code
        }
        val labelBuilder = StringBuilder()
        for (codePointString in specAndSdk.first().split(",")) {
            val cp = codePointString.toInt(16)
            labelBuilder.appendCodePoint(cp)
        }
        return labelBuilder.toString() to KeyCode.MULTIPLE_CODE_POINTS
    }

    private fun parseEmojiKey(spec: String, popupKeysString: String? = null, descriptions: Map<String, String>): KeyParams? {
        val (label, code) = getLabelAndCode(spec) ?: return null
        val sb = StringBuilder()
        popupKeysString?.split(";")?.let { popupKeys ->
            popupKeys.forEach {
                val (mkLabel, _) = getLabelAndCode(it) ?: return@forEach
                sb.append(mkLabel).append(",")
            }
        }
        val popupKeysSpec = if (sb.isNotEmpty()) {
            sb.deleteCharAt(sb.length - 1)
            sb.toString()
        } else descriptions[label]
        return KeyParams(
            label,
            code,
            if (sb.isNotEmpty()) EMOJI_EXPAND_HINT_LABEL else if (popupKeysSpec != null) EMOJI_INFO_HINT_LABEL else null,
            popupKeysSpec,
            Key.LABEL_FLAGS_FONT_NORMAL,
            params
        )
    }
}

const val EMOJI_EXPAND_HINT_LABEL = "◥"
const val EMOJI_INFO_HINT_LABEL = "?"
