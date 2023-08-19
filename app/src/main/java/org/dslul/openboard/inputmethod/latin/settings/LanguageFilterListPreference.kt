package org.dslul.openboard.inputmethod.latin.settings

import android.content.Context
import android.graphics.Rect
import android.preference.Preference
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.edit
import androidx.core.view.doOnLayout
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.RichInputMethodManager
import org.dslul.openboard.inputmethod.latin.utils.AdditionalSubtypeUtils
import org.dslul.openboard.inputmethod.latin.utils.DeviceProtectedUtils

class LanguageFilterListPreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {

    private var preferenceView: View? = null
    private val adapter = LanguageAdapter(emptyList(), context)
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
class LanguageAdapter(list: List<SubtypeInfo> = listOf(), context: Context) :
    RecyclerView.Adapter<LanguageAdapter.ViewHolder>() {
    var disableSwitches = false
    private val prefs = DeviceProtectedUtils.getSharedPreferences(context)

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
            // todo: when loading subtypes from settings using additional subtype thing, the string is rather bad
            //  probably issue there (and intended for these subtypes, as layout is shown)
            view.findViewById<TextView>(R.id.language_name).text = info.displayName
//            view.findViewById<TextView>(R.id.language_details).text = // some short info, no more than 2 lines
            view.findViewById<LinearLayout>(R.id.language_text).setOnClickListener {
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
                    val enabledSubtypes = prefs.getStringSet(Settings.PREF_ENABLED_INPUT_STYLES, emptySet())!!.toHashSet()
                    if (enabledSubtypes.isEmpty())
                        enabledSubtypes.addAll(getDefaultEnabledSubtypes(context).mapNotNull { AdditionalSubtypeUtils.getPrefSubtype(it) })
                    if (b) {
                        prefs.edit { putStringSet(Settings.PREF_ENABLED_INPUT_STYLES, enabledSubtypes + AdditionalSubtypeUtils.getPrefSubtype(info.subtype)) }
                    } else {
                        prefs.edit { putStringSet(Settings.PREF_ENABLED_INPUT_STYLES, enabledSubtypes - AdditionalSubtypeUtils.getPrefSubtype(info.subtype)) }
                    }
                }
            }
            // todo: set other text
        }
    }
}
