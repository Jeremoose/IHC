package com.novodin.ihc.adapters

import android.graphics.Color
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import com.novodin.ihc.R

import com.novodin.ihc.databinding.FragmentArticleBinding
import com.novodin.ihc.model.Article

class ArticleRecyclerViewAdapter(
    private val values: List<Article>
) : RecyclerView.Adapter<ArticleRecyclerViewAdapter.ViewHolder>() {

    var selectedValuePosition: Int = -1
    var isEmpty: Boolean = true

    private var onItemSelectExtra: () -> Unit = {}
    private var onItemAddedExtra: () -> Unit = {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            FragmentArticleBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val article = values[position]
        holder.itemCountView.text = article.count.toString()
        holder.articleNameView.text = article.name
        holder.quantityTypeView.text = article.quantityType.toString()
        holder.articleNumberView.text = article.number

        holder.itemView.setOnClickListener {
            selectedValuePosition = position
            onItemSelectExtra.invoke()
//            notifyItemChanged(position)
            notifyDataSetChanged()
        }

        if (isEmpty) {
            onItemAddedExtra.invoke()
            isEmpty = false
        }

        if (position == selectedValuePosition) {
//            holder.itemView.setBackgroundColor(holder.itemView.context.getColor(R.color.selected_overlay))
            holder.itemView.setBackgroundColor(Color.parseColor("#AED6F1"))
        } else {
            holder.itemView.setBackgroundColor(holder.itemView.context.getColor(R.color.white))
        }
    }

    override fun getItemCount(): Int = values.size

    fun setOnItemSelectExtra(cb: () -> Unit) {
        onItemSelectExtra = cb
    }

    fun setOnItemAddedExtra(cb: () -> Unit) {
        onItemAddedExtra = cb
    }

    inner class ViewHolder(binding: FragmentArticleBinding) :
        RecyclerView.ViewHolder(binding.root) {
        val itemCountView: TextView = binding.tvItemCount
        val articleNameView: TextView = binding.tvArticleName
        val quantityTypeView: TextView = binding.tvQuantityType
        val articleNumberView: TextView = binding.tvArticleNumber
    }

}