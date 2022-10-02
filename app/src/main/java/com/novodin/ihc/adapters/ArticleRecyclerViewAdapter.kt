package com.novodin.ihc.adapters

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import com.novodin.ihc.R

import com.novodin.ihc.databinding.FragmentArticleBinding
import com.novodin.ihc.model.Article

class ArticleRecyclerViewAdapter(
    private val values: List<Article>,
) : RecyclerView.Adapter<ArticleRecyclerViewAdapter.ViewHolder>() {

    var selectedValuePosition: Int = -1

    private var onItemSelectExtra: () -> Unit = {}

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

        if (position == selectedValuePosition) {
            holder.itemView.setBackgroundColor(holder.itemView.context.getColor(R.color.selected_overlay))
        } else {
            holder.itemView.setBackgroundColor(holder.itemView.context.getColor(R.color.white))
        }
    }

    override fun getItemCount(): Int = values.size

    fun setOnItemSelectExtra(cb: () -> Unit) {
        onItemSelectExtra = cb
    }

    inner class ViewHolder(binding: FragmentArticleBinding) :
        RecyclerView.ViewHolder(binding.root) {
        val itemCountView: TextView = binding.tvItemCount
        val articleNameView: TextView = binding.tvArticleName
        val quantityTypeView: TextView = binding.tvQuantityType
        val articleNumberView: TextView = binding.tvArticleNumber
    }

}