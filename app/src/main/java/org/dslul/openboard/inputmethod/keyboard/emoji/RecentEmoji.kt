package org.dslul.openboard.inputmethod.keyboard.emoji

import org.dslul.openboard.inputmethod.keyboard.Key
import org.dslul.openboard.inputmethod.latin.common.Constants
import kotlin.math.log10

class RecentEmoji(
    val code: Int = Constants.NOT_A_CODE,
    val text: String? = null,
    private var uses: LongArray = LongArray(0)
) : Comparable<RecentEmoji> {

    constructor(key: Key) : this(key.code, key.outputText)

    private var frequency = BASE_FREQUENCY

    val lastUse: Long
        get() = uses.lastOrNull() ?: 0

    val usesToStore: List<Long>
        get() {
            return if (uses.size > MIN_COUNT_FOR_NEGLIGIBLE_USES_REMOVAL) {
                val now = now()
                val usesWithMagnitudes = uses.associateWith { orderOfMagnitude(now, it) }
                val magnitudeThreshold = usesWithMagnitudes.maxOf { it.value } + MAGNITUDE_RELATIVE_THRESHOLD_FOR_REMOVAL
                usesWithMagnitudes.filterValues { it > magnitudeThreshold }.keys.toList()
            } else {
                uses.toList()
            }
        }

    fun addUseNow() {
        uses += now()
    }

    fun setSingleUseNow() {
        uses = longArrayOf(now())
    }

    fun setSingleUseFromLastUse() {
        if (uses.size > 1) {
            uses = longArrayOf(lastUse)
        }
    }

    fun updateFrequency() {
        val now = now()
        frequency = uses.fold(BASE_FREQUENCY) { acc, it -> acc * computeUnitFrequency(now, it) }
    }

    override fun compareTo(other: RecentEmoji): Int {
        return other.frequency.compareTo(frequency)
    }

    fun copy() = RecentEmoji(code, text).also { it.uses = uses.copyOf(uses.size) }

    companion object {

        private const val BASE_FREQUENCY = 1f
        private const val BASE_USE_TIME = 24 * 3600 * 1000L // 12 hours in ms
        private const val MIN_COUNT_FOR_NEGLIGIBLE_USES_REMOVAL = 32
        private const val MAGNITUDE_RELATIVE_THRESHOLD_FOR_REMOVAL = -3

        fun now() = System.currentTimeMillis()

        // Ranges in [2.0; 1.0[ with 1.125 for BASE_USE_TIME
        private fun computeUnitFrequency(now: Long, use: Long): Float {
            val x = ((now - use) / BASE_USE_TIME.toFloat()).coerceAtLeast(0f)
            // f(x)=1+(1/(x+1))**2
            val inv = 1f / (x + 1)
            return 1f + inv * inv
        }

        private fun orderOfMagnitude(now: Long, use: Long): Int {
            return log10(computeUnitFrequency(now, use) - 1).toInt()
        }
    }
}