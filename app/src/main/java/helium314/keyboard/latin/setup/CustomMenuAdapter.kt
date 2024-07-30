package helium314.keyboard.latin.setup

import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import helium314.keyboard.latin.R

class CustomMenuAdapter(private val menu: Menu) : RecyclerView.Adapter<CustomMenuAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.nav_menu_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = menu.getItem(position)
        holder.icon.setImageDrawable(item.icon)
        holder.title.text = item.title
        holder.itemView.setOnClickListener {
            // Handle item click
        }
    }

    override fun getItemCount(): Int {
        return menu.size()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_navDrawer)
        val title: TextView = view.findViewById(R.id.menu_title)
    }
}
