package org.dslul.openboard.inputmethod.latin.settings

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.preference.Preference
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.doOnLayout
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.RichInputMethodManager

class LanguageFilterListPreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {

    private var preferenceView: View? = null
    private val adapter = LanguageAdapter()
    private val sortedSubtypes = mutableListOf<SubtypeInfo>()

    override fun onBindView(view: View?) {
        super.onBindView(view)
        preferenceView = view
        preferenceView?.findViewById<RecyclerView>(R.id.language_list)?.adapter = adapter
        val searchField = preferenceView?.findViewById<EditText>(R.id.search_field)!!
        searchField.doAfterTextChanged { text ->
            adapter.list = sortedSubtypes.filter { it.displayName.startsWith(text.toString(), ignoreCase = true) }
        }
        view?.doOnLayout {
            // set correct height for recycler view, so there is no scrolling of the outside view happening
            // not sure how, but probably this can be achieved in xml...
            val windowFrame = Rect()
            it.getWindowVisibleDisplayFrame(windowFrame) // rect the app has, we want the bottom (above screen bottom/navbar/keyboard)
            val globalRect = Rect()
            it.getGlobalVisibleRect(globalRect) // rect the view takes, we want the top (below the system language preference)
            val recycler = it.findViewById<RecyclerView>(R.id.language_list)

            val newHeight = windowFrame.bottom - globalRect.top - it.findViewById<View>(R.id.search_container).height
            recycler.layoutParams = recycler.layoutParams.apply { height = newHeight }
        }
    }

    fun setLanguages(list: List<SubtypeInfo>, disableSwitches: Boolean) {
        sortedSubtypes.clear()
        sortedSubtypes.addAll(list)
        adapter.disableSwitches = disableSwitches
        adapter.list = sortedSubtypes
    }

}

// todo: decide class
class LanguageAdapter(list: List<SubtypeInfo> = listOf()) :
    RecyclerView.Adapter<LanguageAdapter.ViewHolder>() {
    var disableSwitches = false

    var list: List<SubtypeInfo> = list
        set(value) {
            field = value
            notifyDataSetChanged() // todo: check performance, maybe better do a diff if bad?
        }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.onBind(list[position])
    }

    override fun getItemCount() = list.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageAdapter.ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.language_list_item, parent, false)
        return ViewHolder(v)
    }

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        fun onBind(info: SubtypeInfo) {
            view.findViewById<TextView>(R.id.language_name).text = info.displayName
//            view.findViewById<TextView>(R.id.language_details).text = // some short info, no more than 2 lines
            view.findViewById<TextView>(R.id.language_text).setOnClickListener {
                // todo: click item dialog (better full screen with custom layout, not some alertDialog)
                //  secondary locale options similar to now
                //  add/remove dictionary thing, like now
                //   but also for secondary locales
                //  option to change / adjust layout (need to check how exactly)
            }
            view.findViewById<Switch>(R.id.language_switch).apply {
                // take care: isChecked changes if the language is scrolled out of view and comes back!
                // disable the change listener when setting the checked status on scroll
                // so it's only triggered on user interactions
                setOnCheckedChangeListener(null)
                isChecked = info.isEnabled
                isEnabled = !disableSwitches
                setOnCheckedChangeListener { _, b ->
                    // todo: what now?
                    // todo: maybe need to copy some code from CustomInputStyleSettingsFragment
                    // i really should have checked this... apparently the only way to enable an inputmethod is using the system
                    //  or not exposing different subtypes to the system... which is better?
                    if (b) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            // calls the default menu... maybe try this, at least it might not be crashing
//                          view.context.getSystemService(InputMethodManager::class.java).showInputMethodAndSubtypeEnabler(null);
                        }
                        RichInputMethodManager.getInstance()
                            .setAdditionalInputMethodSubtypes(arrayOf(info.subtype)) // nah
                        // there is putSelectedSubtype / putSelectedInputMethod in InputMethodUtils.InputMethodSettings (frameworks/base)
                        // and possibly i could write secure settings, but that's not really great
                        // but this is not public enough...
/*                    val a: InputMethodService
                        a.InputMethodImpl().
                        a.InputMethodSessionImpl().
                        InputMethodManager
                        InputMethodManagerCompatWrapper*/
                    } else {
//                    RichInputMethodManager.getInstance().
                    }
                }
            }
            // todo: set other text
        }
    }
}
