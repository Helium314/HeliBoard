package org.dslul.openboard.inputmethod.latin.utils

import android.util.SparseArray
import androidx.core.util.forEach

public inline fun <V> SparseArray<V>.filter(predicate: (Int, V) -> Boolean): SparseArray<V> {
    return filterTo(SparseArray<V>(), predicate)
}

public inline fun <V, C : SparseArray<in V>> SparseArray<V>.filterTo(destination: C, predicate: (Int, V) -> Boolean): C {
    forEach { key, value -> if (predicate(key, value)) destination.append(key, value) }
    return destination
}

public inline fun <V, R> SparseArray<V>.map(transform: (Int, V) -> R): SparseArray<R> {
    return mapTo(SparseArray<R>(), transform)
}

public inline fun <V, R, C : SparseArray<in R>> SparseArray<V>.mapTo(destination: C, transform: (Int, V) -> R): C {
    forEach { key, value -> destination.put(key, transform(key, value)) }
    return destination
}

public inline fun <V> SparseArray<V>.getOrPut(key: Int, defaultValue: () -> V): V {
    val value = get(key)
    return if (value == null) {
        val answer = defaultValue()
        put(key, answer)
        answer
    } else {
        value
    }
}