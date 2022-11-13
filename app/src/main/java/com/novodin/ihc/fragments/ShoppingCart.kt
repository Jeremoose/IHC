package com.novodin.ihc.fragments

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
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
    private var intentFilter = IntentFilter()

    // View color variables
    private var colorPrimaryEnabled = 0
    private var colorPrimaryDisabled = 0

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
    private lateinit var ibNavOne: ImageButton
    private lateinit var ibNavThree: ImageButton
    private lateinit var ibNavFour: ImageButton
    private lateinit var tvNavOne: TextView
    private lateinit var tvNavFour: TextView

    // Barcode scanner
    private lateinit var barcodeScanner: BarcodeScanner

    private fun initNavButtons(view: View) {
        // First nav button
        (view.findViewById(R.id.ibNavOne) as ImageButton).apply {
            this.setImageResource(R.drawable.ic_minus)
            this.isEnabled = false
        }
        (view.findViewById(R.id.tvNavOne) as TextView).apply {
            this.text = "Remove"
            this.setTextColor(colorPrimaryDisabled)
        }

        // Second nav button
        (view.findViewById(R.id.ibNavTwo) as ImageButton).apply {
            this.setImageResource(R.drawable.ic_arrow_back)
            this.isEnabled = false
        }
        (view.findViewById(R.id.tvNavTwo) as TextView).apply {
            this.text = "Back"
            this.setTextColor(colorPrimaryDisabled)
        }

        // Third nav button (always enabled)
        if (approvalState) {
            (view.findViewById(R.id.ibNavThree) as ImageButton).apply {
                this.setImageResource(R.drawable.ic_check)
                this.isEnabled = true
            }
            (view.findViewById(R.id.tvNavThree) as TextView).apply {
                this.text = "Approve"
                this.setTextColor(colorPrimaryEnabled)
            }
        } else {
            (view.findViewById(R.id.ibNavThree) as ImageButton).apply {
                this.setImageResource(R.drawable.ic_cancel)
                this.isEnabled = true
            }
            (view.findViewById(R.id.tvNavThree) as TextView).apply {
                this.text = "Stop"
                this.setTextColor(colorPrimaryEnabled)
            }
        }

        // Fourth nav button
        (view.findViewById(R.id.ibNavFour) as ImageButton).apply {
            this.setImageResource(R.drawable.ic_plus)
            this.isEnabled = false
        }
        (view.findViewById(R.id.tvNavFour) as TextView).apply {
            this.text = "Add"
            this.setTextColor(colorPrimaryDisabled)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED)
        requireContext().registerReceiver(batteryChangeReceiver, intentFilter)
        setFragmentResultListener("approveLogin") { _, bundle ->
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        colorPrimaryDisabled =
            requireContext().getColor(com.google.android.material.R.color.material_on_primary_disabled)
        colorPrimaryEnabled =
            requireContext().getColor(com.google.android.material.R.color.material_on_primary_emphasis_high_type)

        initNavButtons(view)
        // init view elements
        rvArticleList = view.findViewById(R.id.rvArticleList) as RecyclerView
        tvItemCount = view.findViewById(R.id.tvItemCount) as TextView
        tvPUCount = view.findViewById(R.id.tvPUCount) as TextView
        tvUnitCount = view.findViewById(R.id.tvUnitCount) as TextView

        tvItemCount.text = itemCount.toString()
        tvPUCount.text = puCount.toString()
        tvUnitCount.text = unitCount.toString()

        rvArticleList.adapter = ArticleRecyclerViewAdapter(articleList)
        (rvArticleList.adapter as ArticleRecyclerViewAdapter).setOnItemSelectExtra { enablePlusMinus() }

        ibNavOne = view.findViewById(R.id.ibNavOne) as ImageButton
        ibNavThree = view.findViewById(R.id.ibNavThree) as ImageButton
        ibNavFour = view.findViewById(R.id.ibNavFour) as ImageButton

        tvNavOne = view.findViewById(R.id.tvNavOne) as TextView
        tvNavFour = view.findViewById(R.id.tvNavFour) as TextView

        ibNavOne.setOnClickListener { remove() }
        ibNavThree.setOnClickListener { stop() }
        ibNavFour.setOnClickListener { add() }
    }

    private fun enablePlusMinus() {
        if (!(ibNavOne.isEnabled || ibNavFour.isEnabled)) {
            ibNavOne.isEnabled = true
            tvNavOne.setTextColor(colorPrimaryEnabled)
            ibNavFour.isEnabled = true
            tvNavFour.setTextColor(colorPrimaryEnabled)
        }
    }

    private fun disablePlusMinus() {
        if (ibNavOne.isEnabled || ibNavFour.isEnabled) {
            ibNavOne.isEnabled = false
            tvNavOne.setTextColor(colorPrimaryDisabled)
            ibNavFour.isEnabled = false
            tvNavFour.setTextColor(colorPrimaryDisabled)
        }
    }

    override fun onResume() {
        super.onResume()
        barcodeScanner = BarcodeScanner(requireContext())
        barcodeScanner.setDataCallback(::dataCallback)
        barcodeScanner.setStatusCallback(::statusCallback)

        // Sanity null check, view should always exist here
        view?.let {
            initNavButtons(it)
        }
    }

    override fun onPause() {
        super.onPause()
        barcodeScanner.onClosed()
    }

    private fun statusCallback(message: String) {
        println(message)
    }

    private fun dataCallback(barcode: String) {
        if (cartId == 0) {
            Toast.makeText(requireContext(),
                "not finished initializing...",
                Toast.LENGTH_LONG).show()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val item = backend.addItem(cartId, barcode, accessToken) {
                Toast.makeText(requireContext(), "Unknown barcode", Toast.LENGTH_LONG).show()
            }


            val article = Article(item!!.getInt("id"),
                barcode,
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

    private fun add() {
        val adapter = rvArticleList.adapter as ArticleRecyclerViewAdapter
        val pos = adapter.selectedValuePosition
        if (pos == -1) return // return if nothing is selected

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

    private fun remove() {
        val adapter = rvArticleList.adapter as ArticleRecyclerViewAdapter
        val pos = adapter.selectedValuePosition
        if (pos == -1) return // return if nothing is selected

        val article = articleList[pos]

        // Remove item in backend
        CoroutineScope(Dispatchers.IO).launch {
            backend.removeItem(cartId,
                article.id,
                accessToken)
        }

        // Remove item in UI
        if (article.count == 1) {
            // Remove article from list
            articleList.removeAt(pos)
            adapter.selectedValuePosition = -1
            adapter.notifyItemRemoved(pos)
            // Disable plus and minus buttons
            disablePlusMinus()
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

    private fun stop() {
        if (approvalState) {
            val builder = AlertDialog.Builder(requireContext())
            builder.setMessage("Are you sure?")
                .setCancelable(false)
                .setPositiveButton("Yes") { _, _ ->
                    barcodeScanner.onClosed()
                    CoroutineScope(Dispatchers.IO).launch {
                        backend.approve(cartId, accessToken, JSONArray(articleList))
                        backend.loginRelease(badge, accessToken)

                    }
                    Toast.makeText(requireContext(), "Successfully approved", Toast.LENGTH_LONG)
                        .show()
                    // TODO figure out why at this popbackstack, the fragment doesn't finish its lifecycle
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
                        requireContext().unregisterReceiver(batteryChangeReceiver)
                        CoroutineScope(Dispatchers.IO).launch {
                            backend.loginRelease(badge, accessToken)
                        }
                        replace(R.id.flFragment, Approval(backend))
                        addToBackStack("")
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

    private val batteryChangeReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                Toast.makeText(requireContext(), "IN CRADLE", Toast.LENGTH_SHORT).show()
            }
            if (status == BatteryManager.BATTERY_STATUS_DISCHARGING) {
                Toast.makeText(requireContext(), "REMOVED FROM CRADLE", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }
}