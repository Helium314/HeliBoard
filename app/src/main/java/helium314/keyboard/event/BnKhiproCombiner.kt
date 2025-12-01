// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.event

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import java.util.ArrayList

/**
 * Bengali Khipro combiner - converts Latin to Bengali using state machine.
 *
 * Mappings: assets/khipro-mappings.json
 * Source: https://github.com/KhiproKeyboard/Khipro-Mappings
 * Note: Downloaded `khipro-mappings-heliboard.json` from source and renamed to `khipro-mappings.json`.
 */
class BnKhiproCombiner : Combiner {

    private val composingText = StringBuilder()

    enum class State {
        INIT,
        SHOR_STATE,
        REPH_STATE,
        BYANJON_STATE
    }

    companion object {
        private val loadedMappings: Map<String, Map<String, String>> by lazy {
            try {
                KhiproMappingLoader.loadMappings(helium314.keyboard.latin.App.instance)
            } catch (e: Exception) {
                android.util.Log.e("BnKhiproCombiner", "Failed to load mappings", e)
                emptyMap()
            }
        }

        // Mapping groups
        private val SHOR: Map<String, String> by lazy { loadedMappings["shor"] ?: emptyMap() }
        private val FKAR: Map<String, String> by lazy { loadedMappings["fkar"] ?: emptyMap() }
        private val BYANJON: Map<String, String> by lazy { loadedMappings["byanjon"] ?: emptyMap() }
        private val JUKTOBORNO: Map<String, String> by lazy { loadedMappings["juktoborno"] ?: emptyMap() }
        private val REPH: Map<String, String> by lazy { loadedMappings["reph"] ?: emptyMap() }
        private val PHOLA: Map<String, String> by lazy { loadedMappings["phola"] ?: emptyMap() }
        private val KAR: Map<String, String> by lazy { loadedMappings["kar"] ?: emptyMap() }
        private val DIACRITIC: Map<String, String> by lazy { loadedMappings["diacritic"] ?: emptyMap() }
        private val BIRAM: Map<String, String> by lazy { loadedMappings["biram"] ?: emptyMap() }
        private val PRITHAYOK: Map<String, String> by lazy { loadedMappings["prithayok"] ?: emptyMap() }
        private val AE: Map<String, String> by lazy { loadedMappings["ae"] ?: emptyMap() }
        private val NG: Map<String, String> by lazy { loadedMappings["ng"] ?: emptyMap() }

        private val GROUP_MAPS = mapOf(
            "shor" to SHOR,
            "fkar" to FKAR,
            "byanjon" to BYANJON,
            "juktoborno" to JUKTOBORNO,
            "reph" to REPH,
            "phola" to PHOLA,
            "kar" to KAR,
            "diacritic" to DIACRITIC,
            "biram" to BIRAM,
            "prithayok" to PRITHAYOK,
            "ae" to AE,
            "ng" to NG
        )

        private val STATE_GROUP_ORDER = mapOf(
            State.INIT to listOf("diacritic", "ng", "shor", "fkar", "prithayok", "biram", "reph", "byanjon", "juktoborno"),
            State.SHOR_STATE to listOf("diacritic", "ng", "shor", "fkar", "biram", "prithayok", "reph", "byanjon", "juktoborno"),
            State.REPH_STATE to listOf("prithayok", "ae", "byanjon", "juktoborno", "ng", "kar"),
            State.BYANJON_STATE to listOf("diacritic", "ng", "prithayok", "biram", "kar", "phola", "byanjon", "juktoborno")
        )

        private val MAXLEN_PER_GROUP = GROUP_MAPS.mapValues { (_, map) ->
            map.keys.maxOfOrNull { it.length } ?: 0
        }


        private fun findLongest(state: State, text: String, i: Int): Triple<String, String, String> {
            val allowed = STATE_GROUP_ORDER[state] ?: return Triple("", "", "")

            val maxlen = allowed.maxOfOrNull { MAXLEN_PER_GROUP[it] ?: 0 } ?: 0
            val end = minOf(text.length, i + maxlen)

            for (l in (end - i) downTo 1) {
                val chunk = text.substring(i, i + l)
                for (g in allowed) {
                    val map = GROUP_MAPS[g]
                    if (map?.containsKey(chunk) == true) {
                        return Triple(g, chunk, map[chunk]!!)
                    }
                }
            }
            return Triple("", "", "")
        }


        private fun applyTransition(state: State, group: String): State {
            return when (state) {
                State.INIT -> when (group) {
                    "diacritic", "shor", "fkar" -> State.SHOR_STATE
                    "prithayok" -> State.SHOR_STATE
                    "biram" -> State.SHOR_STATE
                    "reph" -> State.REPH_STATE
                    "byanjon" -> State.BYANJON_STATE
                    "juktoborno" -> State.BYANJON_STATE
                    "ng" -> State.SHOR_STATE
                    else -> state
                }
                State.SHOR_STATE -> when (group) {
                    "diacritic", "shor", "fkar" -> State.SHOR_STATE
                    "biram" -> State.SHOR_STATE
                    "prithayok" -> State.SHOR_STATE
                    "reph" -> State.REPH_STATE
                    "byanjon" -> State.BYANJON_STATE
                    "juktoborno" -> State.BYANJON_STATE
                    "ng" -> State.SHOR_STATE
                    else -> state
                }
                State.REPH_STATE -> when (group) {
                    "diacritic" -> State.SHOR_STATE
                    "prithayok" -> State.SHOR_STATE
                    "ae" -> State.SHOR_STATE
                    "byanjon" -> State.BYANJON_STATE
                    "juktoborno" -> State.BYANJON_STATE
                    "kar" -> State.SHOR_STATE
                    "ng" -> State.SHOR_STATE
                    else -> state
                }
                State.BYANJON_STATE -> when (group) {
                    "diacritic", "kar" -> State.SHOR_STATE
                    "prithayok" -> State.SHOR_STATE
                    "biram" -> State.SHOR_STATE
                    "byanjon" -> State.BYANJON_STATE
                    "juktoborno" -> State.BYANJON_STATE
                    "ng" -> State.SHOR_STATE
                    else -> state
                }
            }
        }


        fun convert(text: String): String {
            var i = 0
            val n = text.length
            var state = State.INIT
            val out = mutableListOf<String>()

            while (i < n) {
                val (group, key, value) = findLongest(state, text, i)
                if (group.isEmpty()) {
                    out.add(text[i].toString())
                    i += 1
                    state = State.INIT
                    continue
                }

                if (state == State.BYANJON_STATE && group == "phola") {
                    out.add("্")
                    out.add(value)
                } else {
                    out.add(value)
                }

                i += key.length
                state = applyTransition(state, group)
            }

            return out.joinToString("")
        }
    }

    override fun processEvent(previousEvents: ArrayList<Event>?, event: Event): Event {
        if (event.keyCode == KeyCode.SHIFT) return event

        if (Character.isWhitespace(event.codePoint)) {
            val text = combiningStateFeedback
            reset()
            return createEventChainFromSequence(text, event)
        } else if (event.isFunctionalKeyEvent) {
            if (event.keyCode == KeyCode.DELETE) {
                val text = combiningStateFeedback
                reset()
                return createEventChainFromSequence(text, event)
            }
            val text = combiningStateFeedback
            reset()
            return createEventChainFromSequence(text, event)
        } else {
            composingText.append(Character.toChars(event.codePoint))

            val text = composingText.toString()
            if (text.endsWith(".ff")) {
                val result = combiningStateFeedback
                reset()
                return createEventChainFromSequence(result, event)
            }

            return Event.createConsumedEvent(event)
        }
    }

    override val combiningStateFeedback: CharSequence
        get() = convert(composingText.toString())

    override fun reset() {
        composingText.setLength(0)
    }

    private fun createEventChainFromSequence(text: CharSequence, originalEvent: Event): Event {
        return Event.createSoftwareTextEvent(text, KeyCode.MULTIPLE_CODE_POINTS, originalEvent)
    }
}