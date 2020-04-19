package com.example.stacklayout

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class StackAdapter(private val context: Context, private val size: Int) :
    RecyclerView.Adapter<StackAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StackAdapter.Holder {
        val view: View
        if (viewType == 1) {
            view = LayoutInflater.from(context)
                .inflate(R.layout.item_image, parent, false)
        } else {
            view = LayoutInflater.from(context)
                .inflate(R.layout.item_image1, parent, false)
        }
        return Holder(view)
    }

    override fun getItemCount(): Int {
        return size
    }

    override fun getItemViewType(position: Int): Int {
        if (position % 2 == 0) {
            return 1
        }
        return 2
    }

    override fun onBindViewHolder(holder: StackAdapter.Holder, position: Int) {
        holder.textView.setText("第$position 项")
        holder.itemView.setOnClickListener {
            Toast.makeText(context, "点击了第$position 项", Toast.LENGTH_SHORT).show()
        }
    }

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var textView: TextView = itemView.findViewById(R.id.item_tv)
    }

}