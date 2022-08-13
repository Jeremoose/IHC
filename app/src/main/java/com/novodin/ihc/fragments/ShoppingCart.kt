package com.novodin.ihc.fragments

import android.app.Activity
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.novodin.ihc.R
import com.novodin.ihc.adapters.ArticleRecyclerViewAdapter
import com.novodin.ihc.model.Article
import com.novodin.ihc.model.QuantityType
import com.novodin.ihc.placeholder.PlaceholderContent
import com.novodin.ihc.scanner.BarcodeScanner

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

    // Barcode
    private lateinit var barcodeScanner: BarcodeScanner


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO init shopping cart API call
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // init view elements
        rvArticleList = view.findViewById(R.id.rvArticleList) as RecyclerView
        tvItemCount = view.findViewById(R.id.tvItemCount) as TextView
        tvPUCount = view.findViewById(R.id.tvPUCount) as TextView
        tvUnitCount = view.findViewById(R.id.tvUnitCount) as TextView

        ibRemove = view.findViewById(R.id.ibRemove) as ImageButton
        ibDelete = view.findViewById(R.id.ibDelete) as ImageButton
        ibStop = view.findViewById(R.id.ibStop) as ImageButton
        ibAdd = view.findViewById(R.id.ibAdd) as ImageButton

        tvItemCount.text = itemCount.toString()
        tvPUCount.text = puCount.toString()
        tvUnitCount.text = unitCount.toString()

        rvArticleList.adapter = ArticleRecyclerViewAdapter(articleList)

        ibAdd.setOnClickListener {
            val adapter = rvArticleList.adapter as ArticleRecyclerViewAdapter
            val pos = adapter.selectedValuePosition
            if (pos == -1) return@setOnClickListener

            val article = articleList[pos]
            article.count++
            when (article.quantityType) {
                QuantityType.ITEM -> tvItemCount.text = (++itemCount).toString()
                QuantityType.PU -> tvPUCount.text = (++puCount).toString()
                QuantityType.UNIT -> tvUnitCount.text = (++unitCount).toString()
            }
            adapter.notifyItemChanged(pos)
        }

        ibRemove.setOnClickListener {
            val adapter = rvArticleList.adapter as ArticleRecyclerViewAdapter
            val pos = adapter.selectedValuePosition
            if (pos == -1) return@setOnClickListener

            val article = articleList[pos]
            if (article.count == 1) {
                // Remove article from list
                articleList.removeAt(pos)
                adapter.selectedValuePosition = -1
                adapter.notifyItemRemoved(pos)
            } else {
                article.count--
                adapter.notifyItemChanged(pos)
            }
            when (article.quantityType) {
                QuantityType.ITEM -> tvItemCount.text = (--itemCount).toString()
                QuantityType.PU -> tvPUCount.text = (--puCount).toString()
                QuantityType.UNIT -> tvUnitCount.text = (--unitCount).toString()
            }
        }

        barcodeScanner = BarcodeScanner(requireContext(), {
            //todo make async
            val article = PlaceholderContent.createPlaceholderItem(it)
            articleList.add(article)
            when (article.quantityType) {
                QuantityType.ITEM -> tvItemCount.text = (++itemCount).toString()
                QuantityType.PU -> tvPUCount.text = (++puCount).toString()
                QuantityType.UNIT -> tvUnitCount.text = (++unitCount).toString()
            }
            (requireContext() as Activity).runOnUiThread {
                (rvArticleList.adapter as ArticleRecyclerViewAdapter).notifyItemInserted(articleList.size - 1)
            }

        }, {
            (requireContext() as Activity).runOnUiThread {
                Toast.makeText(requireContext(),
                    it,
                    Toast.LENGTH_LONG).show()
            }
        })
    }
}