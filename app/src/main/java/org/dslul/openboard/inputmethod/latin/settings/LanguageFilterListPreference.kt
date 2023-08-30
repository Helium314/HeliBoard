package org.dslul.openboard.inputmethod.latin.settings

import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import android.preference.Preference
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.doOnLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils
import org.dslul.openboard.inputmethod.latin.utils.*

class LanguageFilterListPreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {

    private var preferenceView: View? = null
    private val adapter = LanguageAdapter(emptyList(), context)
    private val sortedSubtypes = mutableListOf<MutableList<SubtypeInfo>>()

    fun setSettingsFragment(newFragment: LanguageSettingsFragment?) {
        adapter.fragment = newFragment
    }

    override fun onBindView(view: View?) {
        super.onBindView(view)
        preferenceView = view
        preferenceView?.findViewById<RecyclerView>(R.id.language_list)?.adapter = adapter
        val searchField = preferenceView?.findViewById<EditText>(R.id.search_field)!!
        searchField.doAfterTextChanged { text ->
            adapter.list = sortedSubtypes.filter { it.first().displayName.startsWith(text.toString(), ignoreCase = true) }
        }
        view?.doOnLayout {
            // set correct height for recycler view, so there is no scrolling of the outside view happening
            // not sure how, but probably this can also be achieved in xml...
            val windowFrame = Rect()
            it.getWindowVisibleDisplayFrame(windowFrame) // rect the app has, we want the bottom (above screen bottom/navbar/keyboard)
            val globalRect = Rect()
            it.getGlobalVisibleRect(globalRect) // rect the view takes, we want the top (below the system language preference)
            val recycler = it.findViewById<RecyclerView>(R.id.language_list)

            val newHeight = windowFrame.bottom - globalRect.top - it.findViewById<View>(R.id.search_container).height
            if (newHeight != recycler.layoutParams.height)
                recycler.layoutParams = recycler.layoutParams.apply { height = newHeight }
        }
    }

    fun setLanguages(list: Collection<MutableList<SubtypeInfo>>, onlySystemLocales: Boolean) {
        sortedSubtypes.clear()
        sortedSubtypes.addAll(list)
        adapter.onlySystemLocales = onlySystemLocales
        adapter.list = sortedSubtypes
    }

}

@Suppress("Deprecation")
class LanguageAdapter(list: List<MutableList<SubtypeInfo>> = listOf(), context: Context) :
    RecyclerView.Adapter<LanguageAdapter.ViewHolder>() {
    var onlySystemLocales = false
    private val prefs = DeviceProtectedUtils.getSharedPreferences(context)
    var fragment: LanguageSettingsFragment? = null

    var list: List<MutableList<SubtypeInfo>> = list
        set(value) {
            field = value
            notifyDataSetChanged()
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

        fun onBind(infos: MutableList<SubtypeInfo>) {
            sort(infos)
            fun setupDetailsTextAndSwitch() {
                view.findViewById<TextView>(R.id.language_details).apply {
                    // input styles if more than one in infos
                    val sb = SpannableStringBuilder()
                    if (infos.size > 1 && !onlySystemLocales) {
                        var start = true
                        infos.forEach {
                            val string = SpannableString(SubtypeLocaleUtils.getKeyboardLayoutSetDisplayName(it.subtype)
                                ?: SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(it.subtype))
                            if (it.isEnabled)
                                string.setSpan(StyleSpan(Typeface.BOLD), 0, string.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                            if (!start) {
                                sb.append(", ")
                            }
                            start = false
                            sb.append(string)
                        }
                    }
                    val secondaryLocales = Settings.getSecondaryLocales(prefs, infos.first().subtype.locale)
                    if (secondaryLocales.isNotEmpty()) {
                        if (sb.isNotEmpty())
                            sb.append("\n")
                        sb.append(Settings.getSecondaryLocales(prefs, infos.first().subtype.locale)
                            .joinToString(", ") {
                                LocaleUtils.getLocaleDisplayNameInSystemLocale(it, context)
                            })
                    }
                    text = sb
                    if (text.isBlank()) isGone = true
                        else isVisible = true
                }

                view.findViewById<Switch>(R.id.language_switch).apply {
                    isEnabled = !onlySystemLocales && infos.size == 1
                    // take care: isChecked changes if the language is scrolled out of view and comes back!
                    // disable the change listener when setting the checked status on scroll
                    // so it's only triggered on user interactions
                    setOnCheckedChangeListener(null)
                    isChecked = onlySystemLocales || infos.any { it.isEnabled }
                    setOnCheckedChangeListener { _, b ->
                        if (b) {
                            if (infos.size == 1) {
                                if (!infos.first().hasDictionary)
                                    showMissingDictionaryDialog(context, infos.first().subtype.locale.toLocale())
                                addEnabledSubtype(prefs, infos.first().subtype)
                                infos.single().isEnabled = true
                            } else {
                                // currently switch is disabled in this case
                                LanguageSettingsDialog(view.context, infos, fragment, onlySystemLocales, { setupDetailsTextAndSwitch() }).show()
                            }
                        } else {
                            if (infos.size == 1) {
                                removeEnabledSubtype(prefs, infos.first().subtype)
                                infos.single().isEnabled = false
                            } else {
                                // currently switch is disabled in this case
                                LanguageSettingsDialog(view.context, infos, fragment, onlySystemLocales, { setupDetailsTextAndSwitch() }).show()
                            }
                        }
                    }
                }
            }

            view.findViewById<TextView>(R.id.language_name).text = infos.first().displayName
            view.findViewById<LinearLayout>(R.id.language_text).setOnClickListener {
                LanguageSettingsDialog(view.context, infos, fragment, onlySystemLocales, { setupDetailsTextAndSwitch() }).show()
            }
            setupDetailsTextAndSwitch()
        }

        private fun sort(infos: MutableList<SubtypeInfo>) {
            if (infos.size <= 1) return
            infos.sortWith(compareBy({ isAdditionalSubtype(it.subtype) }, { it.displayName }))
        }
    }
}
