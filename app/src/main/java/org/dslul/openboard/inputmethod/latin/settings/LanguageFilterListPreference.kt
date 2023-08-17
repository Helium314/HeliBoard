package org.dslul.openboard.inputmethod.latin.settings

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.preference.Preference
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.widget.*
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import org.dslul.openboard.inputmethod.compat.InputMethodManagerCompatWrapper
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.RichInputMethodManager
import org.dslul.openboard.inputmethodcommon.InputMethodSettingsFragment

class LanguageFilterListPreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {

    private var preferenceView: View? = null
    private val adapter = LanguageAdapter()
    private val sortedSubtypes = mutableListOf<SubtypeInfo>()
    val searchAdapter = SearchAdapter(context,
        { search -> sortedSubtypes.filter { it.displayName.startsWith(search) } },
        { it.displayName }
    )

    override fun onBindView(view: View?) {
        super.onBindView(view)
        preferenceView = view
        val r = preferenceView?.findViewById<RecyclerView>(R.id.language_list)!!
        r.adapter = adapter
        val searchField = preferenceView?.findViewById<EditText>(R.id.search_field)!!
        searchField.doAfterTextChanged { text ->
            adapter.list = sortedSubtypes.filter { it.displayName.startsWith(text.toString()) }.toMutableList()
        }
    }

    fun setLanguages(list: List<SubtypeInfo>) {
        sortedSubtypes.clear()
        sortedSubtypes.addAll(list)
        adapter.list = sortedSubtypes
    }

}

// todo: decide class
class LanguageAdapter(list: List<SubtypeInfo> = listOf()) :
    RecyclerView.Adapter<LanguageAdapter.ViewHolder>() {

    var list: MutableList<SubtypeInfo> = list.toMutableList()
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
            view.findViewById<Switch>(R.id.language_switch).isChecked = info.isEnabled
            view.findViewById<Switch>(R.id.language_switch).setOnCheckedChangeListener { _, b ->
                // todo: what now?
                // todo: maybe need to copy some code from CustomInputStyleSettingsFragment
                // i really should have checked this... apparently the only way to enable an inputmethod is using the system
                //  or not exposing different subtypes to the system... which is better?
                if (b) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        // calls the default menu... maybe try this, at least it might not be crashing
//                        view.context.getSystemService(InputMethodManager::class.java).showInputMethodAndSubtypeEnabler(null);
                    }
                    RichInputMethodManager.getInstance().setAdditionalInputMethodSubtypes(arrayOf(info.subtype)) // nah
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
            // todo: set other text
        }
    }
}

// todo: remove?
// adapter with filtering, taken from old version of StreetComplete
class SearchAdapter<T>(
    private val context: Context,
    private val filterQuery: (term: String) -> List<T>,
    private val convertToString: (T) -> String
) : BaseAdapter(), Filterable {

    private val filter = SearchFilter()

    private var items: List<T> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): T = items[position]
    override fun getItemId(position: Int): Long = position.toLong()
    override fun getFilter() = filter

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(android.R.layout.simple_dropdown_item_1line, parent, false)
        (view as TextView).text = filter.convertResultToString(getItem(position))
        return view
    }

    inner class SearchFilter : Filter() {
        override fun performFiltering(constraint: CharSequence?) = FilterResults().also {
            val term = constraint?.toString() ?: ""
            val results = filterQuery(term)
            it.count = results.size
            it.values = results
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults) {
            // results should always come from performFiltering, but still got a crash report with
            // NPE here, which happens on click ok (and not actually anything where filtering happens)
            (results?.values as? List<T>)?.let { items = it }
        }

        override fun convertResultToString(resultValue: Any?): CharSequence {
            return (resultValue as? T?)?.let(convertToString) ?: super.convertResultToString(resultValue)
        }
    }
}
