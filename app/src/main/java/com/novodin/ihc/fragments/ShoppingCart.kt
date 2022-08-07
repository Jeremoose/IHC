package com.novodin.ihc.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.novodin.ihc.R
import com.novodin.ihc.adapters.ArticleRecyclerViewAdapter
import com.novodin.ihc.model.Article
import com.novodin.ihc.model.QuantityType
import com.novodin.ihc.placeholder.PlaceholderContent

class ShoppingCart : Fragment(R.layout.fragment_shopping_cart) {
    // Shopping cart variables
    private val articleList: MutableList<Article> = ArrayList()
    private var itemCount: Int = 0
    private var puCount: Int = 0
    private var unitCount: Int = 0

    // View elements
    private lateinit var rvArticleList: RecyclerView
    private lateinit var tvItemCount: TextView
    private lateinit var tvPUCount: TextView
    private lateinit var tvUnitCount: TextView
    private lateinit var ibRemove: ImageButton
    private lateinit var ibDelete: ImageButton
    private lateinit var ibStop: ImageButton
    private lateinit var ibAdd: ImageButton


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO init shopping cart API call
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // init view elements
        rvArticleList = view.findViewById(R.id.rvArticleList) as RecyclerView
        tvItemCount = view.findViewById(R.id.tvItemCount) as TextView
        tvPUCount = view.findViewById(R.id.tvPUCount) as TextView
        tvUnitCount = view.findViewById(R.id.tvUnitCount) as TextView

        ibRemove = view.findViewById(R.id.ibRemove) as ImageButton
        ibDelete = view.findViewById(R.id.ibDelete) as ImageButton
        ibStop = view.findViewById(R.id.ibStop) as ImageButton
        ibAdd = view.findViewById(R.id.ibAdd) as ImageButton

        rvArticleList.adapter = ArticleRecyclerViewAdapter(articleList)

        ibAdd.setOnClickListener {
            val article =
                articleList[(rvArticleList.adapter as ArticleRecyclerViewAdapter).selectedValuePosition]
            article.count++
            when (article.quantityType) {
                QuantityType.ITEM -> tvItemCount.text = (++itemCount).toString()
                QuantityType.PU -> tvPUCount.text = (++puCount).toString()
                QuantityType.UNIT -> tvUnitCount.text = (++unitCount).toString()
            }
            (rvArticleList.adapter as ArticleRecyclerViewAdapter).notifyDataSetChanged()
        }

        tvItemCount.text = itemCount.toString()
        tvPUCount.text = puCount.toString()
        tvUnitCount.text = unitCount.toString()

        ibRemove.setOnClickListener {
            val adapter = rvArticleList.adapter as ArticleRecyclerViewAdapter
            val position = adapter.selectedValuePosition
            if (position == -1) {
                return@setOnClickListener
            }

            val article = articleList[position]
            if (article.count == 1) {
                // Remove article from list
                articleList.removeAt(position)
                adapter.selectedValuePosition = -1
                adapter.notifyDataSetChanged()
            } else {
                article.count--
                adapter.notifyDataSetChanged()
            }
            when (article.quantityType) {
                QuantityType.ITEM -> tvItemCount.text = (--itemCount).toString()
                QuantityType.PU -> tvPUCount.text = (--puCount).toString()
                QuantityType.UNIT -> tvUnitCount.text = (--unitCount).toString()
            }
        }

        ibStop.setOnClickListener {
            val article = PlaceholderContent.createPlaceholderItem()
            articleList.add(article)
            when (article.quantityType) {
                QuantityType.ITEM -> tvItemCount.text = (++itemCount).toString()
                QuantityType.PU -> tvPUCount.text = (++puCount).toString()
                QuantityType.UNIT -> tvUnitCount.text = (++unitCount).toString()
            }
            (rvArticleList.adapter as ArticleRecyclerViewAdapter).notifyDataSetChanged()
        }
        super.onViewCreated(view, savedInstanceState)
    }


}