package com.novodin.ihc.fragments

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.setFragmentResultListener
import androidx.recyclerview.widget.RecyclerView
import com.novodin.ihc.R
import com.novodin.ihc.adapters.ArticleRecyclerViewAdapter
import com.novodin.ihc.model.Article
import com.novodin.ihc.model.QuantityType
import com.novodin.ihc.network.Backend
import com.novodin.ihc.zebra.BarcodeScanner
import kotlinx.coroutines.*
import org.json.JSONArray

class ShoppingCart(
    private var badge: String,
    private var projectId: Int,
    private var accessToken: String,
    private var backend: Backend,
) : Fragment(R.layout.fragment_shopping_cart) {
    // Shopping cart variables
    private val articleList: MutableList<Article> = ArrayList()
    private var itemCount: Int = 0
    private var puCount: Int = 0
    private var unitCount: Int = 0
    private var cartId: Int = 0
    private var approvalState: Boolean = false;

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
        setFragmentResultListener("approveLogin") { requestKey, bundle ->
            // We use a String here, but any type that can be put in a Bundle is supported
            val success = bundle.getBoolean("success")
            val accessToken = bundle.getString("accessToken")

            if (!success) {
                Toast.makeText(requireContext(), "Not an approver badge", Toast.LENGTH_LONG)
                    .show()
                return@setFragmentResultListener
            }

            try {
                this.accessToken = accessToken!!
                approvalState = true
            } catch (e: NullPointerException) {
                println("something went wrong $e")
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            val cartIdResponse = backend.createCart(projectId, accessToken)
            cartId = cartIdResponse!!.getInt("id")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        barcodeScanner.onClosed()
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
            if (pos == -1) return@setOnClickListener // return if nothing is selected

            val article = articleList[pos]

            // add item in backend
            CoroutineScope(Dispatchers.IO).launch {
                backend.addItem(cartId,
                    article.barcode,
                    accessToken
                ) {
                    Toast.makeText(requireContext(), "Unkown barcode", Toast.LENGTH_LONG).show()
                }
            }

            // add item in UI
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
            if (pos == -1) return@setOnClickListener // return if nothing is selected

            val article = articleList[pos]

            // remove item in backend
            CoroutineScope(Dispatchers.IO).launch {
                backend.removeItem(cartId,
                    article.id,
                    accessToken)
            }

            // remove item in UI
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

        ibStop.setOnClickListener {
            if (approvalState) {
                val builder = AlertDialog.Builder(requireContext())
                builder.setMessage("Approve?")
                    .setCancelable(false)
                    .setPositiveButton("Yes") { _, _ ->
                        CoroutineScope(Dispatchers.IO).launch {
                            backend.approve(cartId, accessToken, JSONArray(articleList))
                            backend.loginRelease(badge, accessToken)

                        }
                        barcodeScanner.onClosed()
                        requireActivity().supportFragmentManager.popBackStack()
                    }
                    .setNegativeButton("No") { dialog, _ ->
                        // TODO handler for unapproved
                        dialog.dismiss()
                    }
                val alert = builder.create()
                alert.window?.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                alert.show()
            } else {
                val builder = AlertDialog.Builder(requireContext())
                builder.setMessage("What would you like to do?")
                    .setCancelable(false)
                    .setPositiveButton("Verify") { _, _ ->
                        parentFragmentManager.beginTransaction().apply {
                            CoroutineScope(Dispatchers.IO).launch {
                                backend.loginRelease(badge, accessToken)
                            }
                            replace(R.id.flFragment, Approval(backend))
                            addToBackStack("")
                            barcodeScanner.onClosed()
                            commit()
                        }
                    }
                    .setNegativeButton("Continue") { dialog, _ ->
                        dialog.dismiss()
                    }
                val alert = builder.create()
                alert.window?.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                alert.show()
            }
        }

        barcodeScanner = BarcodeScanner(requireContext(), {
            if (cartId == 0) {
                Toast.makeText(requireContext(),
                    "not finished initializing...",
                    Toast.LENGTH_LONG).show()
                return@BarcodeScanner
            }
            CoroutineScope(Dispatchers.IO).launch {
                val item = backend.addItem(cartId, it, accessToken) {
                    Toast.makeText(requireContext(), "Unknown barcode", Toast.LENGTH_LONG).show()
                }


                val article = Article(item!!.getInt("id"),
                    it,
                    item.getString("name"),
                    item.getString("number"),
                    QuantityType.fromInt(item.getInt("cat")),
                    1)

                val adapter = rvArticleList.adapter as ArticleRecyclerViewAdapter

                var alreadyExisted = false
                for ((i, art) in articleList.withIndex()) {
                    if (art.id == article.id) {
                        alreadyExisted = true
                        articleList[i].count++
                        (requireContext() as Activity).runOnUiThread {
                            adapter.notifyItemChanged(i)
                        }
                        break
                    }
                }

                if (!alreadyExisted) {
                    articleList.add(article)
                    (requireContext() as Activity).runOnUiThread {
                        adapter.notifyItemInserted(articleList.size - 1)
                    }
                }

                when (article.quantityType) {
                    QuantityType.ITEM -> tvItemCount.text = (++itemCount).toString()
                    QuantityType.PU -> tvPUCount.text = (++puCount).toString()
                    QuantityType.UNIT -> tvUnitCount.text = (++unitCount).toString()
                }
            }

        }
        ) {
            // status
            println(it)
        }
    }
}