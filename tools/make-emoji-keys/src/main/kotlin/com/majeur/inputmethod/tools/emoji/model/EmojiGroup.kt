// SPDX-License-Identifier: GPL-3.0-only

package com.majeur.inputmethod.tools.emoji.model

enum class EmojiGroup(val rawName: String) {
    SMILEYS_AND_EMOTION("Smileys & Emotion"),
    PEOPLE_AND_BODY("People & Body"),
    COMPONENT("Component"),
    ANIMALS_AND_NATURE("Animals & Nature"),
    FOOD_AND_DRINK("Food & Drink"),
    TRAVEL_AND_PLACES("Travel & Places"),
    ACTIVITIES("Activities"),
    OBJECTS("Objects"),
    SYMBOLS("Symbols"),
    FLAGS("Flags");

    companion object {
        fun get(rawName: String) = entries.first { it.rawName == rawName }
    }

}
