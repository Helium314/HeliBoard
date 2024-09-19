// SPDX-License-Identifier: GPL-3.0-only

package org.oscar.kb.latin.settings

import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.viewbinding.ViewBinding
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// taken from StreetComplete, ViewBinder.kt
inline fun <reified T : ViewBinding> Fragment.viewBinding(
    noinline viewBinder: (View) -> T,
    rootViewId: Int? = null
) = FragmentViewBindingPropertyDelegate(this, viewBinder, rootViewId)

class FragmentViewBindingPropertyDelegate<T : ViewBinding>(
    private val fragment: Fragment,
    private val viewBinder: (View) -> T,
    private val rootViewId: Int? = null
) : ReadOnlyProperty<Fragment, T>, LifecycleEventObserver {

    private var binding: T? = null

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_DESTROY) {
            binding = null
            source.lifecycle.removeObserver(this)
        }
    }

    override fun getValue(thisRef: Fragment, property: KProperty<*>): T {
        if (binding == null) {
            val rootView = if (rootViewId != null) {
                thisRef.requireView().findViewById<ViewGroup>(rootViewId)!!.getChildAt(0)
            } else {
                thisRef.requireView()
            }
            binding = viewBinder(rootView)
            fragment.viewLifecycleOwner.lifecycle.addObserver(this)
        }
        return binding!!
    }
}
