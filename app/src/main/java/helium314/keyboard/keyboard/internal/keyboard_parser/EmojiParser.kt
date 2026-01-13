// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.internal.keyboard_parser

import android.content.Context
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Key.KeyParams
import helium314.keyboard.keyboard.KeyboardElement
import helium314.keyboard.keyboard.emoji.SupportedEmojis
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.common.splitOnWhitespace
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.prefs
import java.util.Collections
import kotlin.math.sqrt

class EmojiParser(private val params: KeyboardParams, private val context: Context) {

    fun parse(): ArrayList<ArrayList<KeyParams>> {
        val element = params.mId.element
        val emojiFileName = when (element) {
            KeyboardElement.EMOJI_CATEGORY1 -> "SMILEYS_AND_EMOTION.txt"
            KeyboardElement.EMOJI_CATEGORY2 -> "PEOPLE_AND_BODY.txt"
            KeyboardElement.EMOJI_CATEGORY3 -> "ANIMALS_AND_NATURE.txt"
            KeyboardElement.EMOJI_CATEGORY4 -> "FOOD_AND_DRINK.txt"
            KeyboardElement.EMOJI_CATEGORY5 -> "TRAVEL_AND_PLACES.txt"
            KeyboardElement.EMOJI_CATEGORY6 -> "ACTIVITIES.txt"
            KeyboardElement.EMOJI_CATEGORY7 -> "OBJECTS.txt"
            KeyboardElement.EMOJI_CATEGORY8 -> "SYMBOLS.txt"
            KeyboardElement.EMOJI_CATEGORY9 -> "FLAGS.txt"
            KeyboardElement.EMOJI_CATEGORY10 -> "EMOTICONS.txt"
            else -> null
        }
        val emojiLines = if (emojiFileName == null) {
            listOf( // special template keys for recents category
                StringUtils.newSingleCodePointString(Constants.RECENTS_TEMPLATE_KEY_CODE_0),
                StringUtils.newSingleCodePointString(Constants.RECENTS_TEMPLATE_KEY_CODE_1),
            )
        } else {
            context.assets.open("emoji/$emojiFileName").reader().use { it.readLines() }
        }
        val defaultSkinTone = context.prefs().getString(Settings.PREF_EMOJI_SKIN_TONE, Defaults.PREF_EMOJI_SKIN_TONE)!!
        if (element == KeyboardElement.EMOJI_CATEGORY2 && defaultSkinTone != "") {
            // adjust PEOPLE_AND_BODY if we have a non-yellow default skin tone
            val modifiedLines = emojiLines.map { line ->
                val split = line.splitOnWhitespace().toMutableList()
                // find the line containing the skin tone, and swap with first
                val foundIndex = split.indexOfFirst { it.contains(defaultSkinTone) }
                if (foundIndex > 0) {
                    Collections.swap(split, 0, foundIndex)
                }
                split.joinToString(" ")
            }
            return parseLines(modifiedLines)
        }
        return parseLines(emojiLines)
    }

    private fun parseLines(lines: List<String>): ArrayList<ArrayList<KeyParams>> {
        val row = ArrayList<KeyParams>(lines.size)
        var currentX = params.mLeftPadding.toFloat()
        val currentY = params.mTopPadding.toFloat() // no need to ever change, assignment to rows into rows is done in DynamicGridKeyboard

        // determine key width for default settings (no number row, no one-handed mode, 100% height and bottom padding scale)
        // this is a bit long, but ensures that emoji size stays the same, independent of these settings
        // we also ignore side padding for key width, and prefer fewer keys per row over narrower keys
        val defaultKeyWidth = ResourceUtils.getDefaultKeyboardWidth(context)  * params.mDefaultKeyWidth
        var keyWidth = defaultKeyWidth * sqrt(Settings.getValues().mKeyboardHeightScale)
        val defaultKeyboardHeight = ResourceUtils.getDefaultKeyboardHeight(context.resources, false)
        val defaultBottomPadding = context.resources.getFraction(R.fraction.config_keyboard_bottom_padding_holo, defaultKeyboardHeight, defaultKeyboardHeight)
        val emojiKeyboardHeight = defaultKeyboardHeight * 0.75f + params.mVerticalGap - defaultBottomPadding - context.resources.getDimensionPixelSize(R.dimen.config_emoji_category_page_id_height)
        var keyHeight = emojiKeyboardHeight * params.mDefaultRowHeight * Settings.getValues().mKeyboardHeightScale // still apply height scale to key

        if (Settings.getValues().mEmojiKeyFit) {
            keyWidth *= Settings.getValues().mFontSizeMultiplierEmoji
            keyHeight *= Settings.getValues().mFontSizeMultiplierEmoji
        }


        lines.forEach { line ->
            val keyParams = parseEmojiKeyNew(line) ?: return@forEach
            keyParams.xPos = currentX
            keyParams.yPos = currentY
            keyParams.mAbsoluteWidth = keyWidth
            keyParams.mAbsoluteHeight = keyHeight
            currentX += keyParams.mAbsoluteWidth
            row.add(keyParams)
        }
        return arrayListOf(row)
    }

    private fun parseEmojiKeyNew(line: String): KeyParams? {
        if (!line.contains(" ") || params.mId.element == KeyboardElement.EMOJI_CATEGORY10) {
            // single emoji without popups, or emoticons (there is one that contains space...)
            return if (SupportedEmojis.isUnsupported(line)) null
            else KeyParams(line, line.getCode(), null, null, Key.LABEL_FLAGS_FONT_NORMAL, params)
        }
        val split = line.split(" ")
        val label = split.first()
        if (SupportedEmojis.isUnsupported(label)) return null
        val popupKeysSpec = split.drop(1).filterNot { SupportedEmojis.isUnsupported(it) }
            .takeIf { it.isNotEmpty() }?.joinToString(",")
        return KeyParams(
            label,
            label.getCode(),
            if (popupKeysSpec != null) EMOJI_HINT_LABEL else null,
            popupKeysSpec,
            Key.LABEL_FLAGS_FONT_NORMAL,
            params
        )
    }

    private fun String.getCode(): Int =
        if (StringUtils.codePointCount(this) != 1) KeyCode.MULTIPLE_CODE_POINTS
        else Character.codePointAt(this, 0)
}

const val EMOJI_HINT_LABEL = "◥"
