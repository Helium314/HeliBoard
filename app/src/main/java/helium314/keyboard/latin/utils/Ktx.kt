package helium314.keyboard.latin.utils

import android.content.Context

// generic extension functions

// adapted from Kotlin source: https://github.com/JetBrains/kotlin/blob/7a7d392b3470b38d42f80c896b7270678d0f95c3/libraries/stdlib/common/src/generated/_Collections.kt#L3004
inline fun <T> Iterable<T>.sumOf(selector: (T) -> Float): Float {
    var sum = 0f
    for (element in this) {
        sum += selector(element)
    }
    return sum
}

fun CharSequence.getStringResourceOrName(prefix: String, context: Context): CharSequence {
    val resId = context.resources.getIdentifier(prefix + this, "string", context.packageName)
    return if (resId == 0) this else context.getString(resId)
}
