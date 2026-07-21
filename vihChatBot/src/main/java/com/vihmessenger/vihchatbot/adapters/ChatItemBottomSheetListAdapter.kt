package com.vihmessenger.vihchatbot.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.vihmessenger.vihchatbot.R
import com.vihmessenger.vihchatbot.data.model.ListItem

class ChatItemBottomSheetListAdapter(
    private val context: Context,
    private val items: List<ListItem>
) : BaseAdapter() {

    private val inflater: LayoutInflater = LayoutInflater.from(context)

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): ListItem = items[position]

    override fun getItemId(position: Int): Long = items[position].id.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View
        val viewHolder: ViewHolder

        if (convertView == null) {
            view = inflater.inflate(R.layout.item_list, parent, false)
            viewHolder = ViewHolder(
                view.findViewById(R.id.iv_icon),
                view.findViewById(R.id.tv_title),
            )
            view.tag = viewHolder
        } else {
            view = convertView
            viewHolder = view.tag as ViewHolder
        }

        val item = getItem(position)
        viewHolder.icon.setImageResource(item.iconResId)
        viewHolder.title.text = item.title

        return view
    }

    private class ViewHolder(
        val icon: ImageView,
        val title: TextView,
    )
}