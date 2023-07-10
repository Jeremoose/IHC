package com.novodin.ihc.fragments

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
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
import com.novodin.ihc.config.Config
import com.novodin.ihc.model.Article
import com.novodin.ihc.model.QuantityType
import com.novodin.ihc.network.Backend
import com.novodin.ihc.zebra.BarcodeScanner
import kotlinx.coroutines.*
import org.json.JSONArray
import android.util.Base64
import androidx.fragment.app.FragmentManager
import com.novodin.ihc.SessionManager

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
    private lateinit var shoppingSessionId: String

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
    private var dialog: AlertDialog? = null

    // Barcode scanner
    private lateinit var barcodeScanner: BarcodeScanner

    // Timer variables
    private lateinit var passiveTimeout: CountDownTimer

    private var isApproved: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("ShoppingCart:debug_double-passivetimeout", "fun onCreate")
        super.onCreate(savedInstanceState)
        intentFilter.addAction("com.symbol.intent.device.DOCKED")
        requireContext().registerReceiver(dockChangeReceiver, intentFilter)

        val sessionId = SessionManager.getInstance().getSessionId()
        val sessionActive = SessionManager.getInstance().getSessionState()
        if (sessionId != null) {
            Log.d("ShoppingCart:sessionId", sessionId)
            Log.d("ShoppingCart:sessionState", sessionActive.toString())
            shoppingSessionId = sessionId
        }

        approvalState = false
        isApproved = false
        cartId = 0

        // setup passive user timeout
        passiveTimeout =
            object : CountDownTimer(Config.PassiveTimeoutLong.toLong(), Config.PassiveTimeoutLong.toLong()) {
                override fun onTick(p0: Long) {}
                override fun onFinish() {
                    Log.d("ShoppingCart:passivetimeout:onFinish", "timer onFinish")
                    Log.d("ShoppingCart:passivetimeout:onFinish isApproved = ", isApproved.toString())
                    Log.d("ShoppingCart:passivetimeout:onFinish approvalState = ", approvalState.toString())
                    Log.d("ShoppingCart:passivetimeout:onFinish shoppingSessionId = ", shoppingSessionId)

                    val sessionActive = SessionManager.getInstance().getSessionState()
                    val sessionId = SessionManager.getInstance().getSessionId()
                    if (sessionId != null) {
                        Log.d("ShoppingCart:passivetimeout:onFinish sessionId = ", sessionId)
                    }
                    Log.d("ShoppingCart:passivetimeout:onFinish sessionActive = ", sessionActive.toString())

                    if (sessionId.equals(shoppingSessionId)) {

                        SessionManager.getInstance().setSessionState(false)
                        if (!isApproved) {
                            if (approvalState) {
                                barcodeScanner.onClosed()
                                CoroutineScope(Dispatchers.IO).launch {
                                    backend.approve(cartId, accessToken, JSONArray(articleList))
                                }
                                Toast.makeText(requireContext(),
                                    "Successfully approved",
                                    Toast.LENGTH_LONG)
                                    .show()
                            }

                            // release the user
                            CoroutineScope(Dispatchers.IO).launch {
                                Log.d("ShoppingCart:passivetimeout:onFinish release user with badge = ", badge)
                                backend.loginRelease(badge!!, accessToken)
                            }
                        }

                        try {
                            dialog?.cancel()
                            requireContext().unregisterReceiver(dockChangeReceiver)
                        } catch (e: IllegalArgumentException) {
                            Log.d("ShoppingCart:debug_unregister_catch",
                                "passivetimeout unregister error: $e")
                        }

//                        requireActivity().supportFragmentManager.popBackStack("standby",
//                            FragmentManager.POP_BACK_STACK_INCLUSIVE)
                        parentFragmentManager.popBackStack("standby",
                            FragmentManager.POP_BACK_STACK_INCLUSIVE)

                    }
                }
            }

        passiveTimeout.start()
        setFragmentResultListener("approveLogin") { _, bundle ->
            // We use a String here, but any type that can be put in a Bundle is supported
            Log.d("ShoppingCart:setFragmentResultListener", "approvalState=$approvalState, isApproved=$isApproved")
            val success = bundle.getBoolean("success")
            val accessToken = bundle.getString("accessToken")
            val badge = bundle.getString("badge")
            if (badge != null) {
                Log.d("ShoppingCart:setFragmentResultListener approver badge = ", badge)
            }

            if (!success) {
                Toast.makeText(requireContext(), "Not an approver badge", Toast.LENGTH_LONG)
                    .show()
                return@setFragmentResultListener
            }

            try {
                this.accessToken = accessToken!!
                this.badge = badge!!
                approvalState = true
                resetPassiveTimeout()
                val log =  "enter shoppingcart with approval badge: $badge, accessToken: $accessToken"
                CoroutineScope(Dispatchers.IO).launch {
                    backend.log(log) {
                        Log.d("Shoppingcart:ibNavFour.setOnClickListener backend.log", "error: $it")
                    }
                }
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

    // should enter here after returning from approval login
    // does not go back into onCreate!
    override fun onResume() {
        super.onResume()

        val sessionId = SessionManager.getInstance().getSessionId()
        if (sessionId != null) {
            Log.d("ShoppingCart:onResume sessionId = ", sessionId)
            Log.d("ShoppingCart:onResume shoppingSessionId = ", shoppingSessionId)
        }

        if (sessionId.equals(shoppingSessionId)) {
            Log.d("ShoppingCart:onResume", "resetPassiveTimeout")
            resetPassiveTimeout()
        }

        barcodeScanner = BarcodeScanner(requireContext())
        barcodeScanner.setDataCallback(::dataCallback)
        barcodeScanner.setStatusCallback(::statusCallback)
        requireContext().registerReceiver(dockChangeReceiver, intentFilter)
        initNavButtons(requireView())
    }

    override fun onPause() {
        super.onPause()
        val sessionId = SessionManager.getInstance().getSessionId()
        if (sessionId != null) {
            Log.d("ShoppingCart:onPause sessionId = ", sessionId)
            Log.d("ShoppingCart:onPause shoppingSessionId = ", shoppingSessionId)
        }

        barcodeScanner.onClosed()
        passiveTimeout.cancel()
        Log.d("ShoppingCart:onPause", "cancel passivetimeout")


    }

    override fun onDestroy() {
        super.onDestroy()
        val sessionId = SessionManager.getInstance().getSessionId()
        if (sessionId != null) {
            Log.d("ShoppingCart:onDestroy sessionId = ", sessionId)
            Log.d("ShoppingCart:onDestroy shoppingSessionId = ", shoppingSessionId)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val sessionId = SessionManager.getInstance().getSessionId()
        if (sessionId != null) {
            Log.d("ShoppingCart:onDestroyView sessionId = ", sessionId)
            Log.d("ShoppingCart:onDestroyView shoppingSessionId = ", shoppingSessionId)
        }
    }

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
    private fun statusCallback(message: String) {
        println(message)
    }

    private fun dataCallback(barcode: String) {
        Log.d("ShoppingCart:dataCallback scan = ", barcode)
        Log.d("ShoppingCart:dataCallback", "reset passivetimeout")
        resetPassiveTimeout()
        val sessionId = SessionManager.getInstance().getSessionId()
        if (sessionId != null) {
            Log.d("ShoppingCart:dataCallback sessionId = ", sessionId)
            Log.d("ShoppingCart:dataCallback shoppingSessionId = ", shoppingSessionId)
        }

        if (cartId == 0) {
            Toast.makeText(requireContext(),
                "not finished initializing...",
                Toast.LENGTH_LONG).show()
            return
        }

        val b64encodedBarcode = Base64.encodeToString(barcode.toByteArray(), Base64.DEFAULT)
        CoroutineScope(Dispatchers.IO).launch {
            val item = backend.addItem(cartId, b64encodedBarcode, accessToken) {
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
                    article.count = articleList[i].count +1
                    articleList.removeAt(i)
                    articleList.add(0,article)
                    (requireContext() as Activity).runOnUiThread {
                        adapter.notifyItemRemoved(i)
                        adapter.notifyItemInserted(0)
                        rvArticleList.scrollToPosition(0)
                    }
                    break
                }
            }

            if (!alreadyExisted) {
                Log.d("article-add-debug: article", article.toString())
                Log.d("article-add-debug: articleList before :", articleList.toString())
                articleList.add(0,article)
                Log.d("article-add-debug: articleList after :", articleList.toString())
                (requireContext() as Activity).runOnUiThread {
                    adapter.notifyItemInserted(0)
                    rvArticleList.scrollToPosition(0)
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
        resetPassiveTimeout()
        val adapter = rvArticleList.adapter as ArticleRecyclerViewAdapter
        val pos = adapter.selectedValuePosition
        if (pos == -1) return // return if nothing is selected

        val article = articleList[pos]

        // add item in backend
        val b64encodedBarcode = Base64.encodeToString(article.barcode.toByteArray(), Base64.DEFAULT)
        CoroutineScope(Dispatchers.IO).launch {
            backend.addItem(cartId,
                b64encodedBarcode,
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
        resetPassiveTimeout()
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
                    isApproved = true
                    passiveTimeout.cancel()
                    Log.d("ShoppingCart:stop", "cancel passivetimeout, approvalState = true")
                    requireContext().unregisterReceiver(dockChangeReceiver)
                    CoroutineScope(Dispatchers.IO).launch {
                        backend.approve(cartId, accessToken, JSONArray(articleList))
                    }
                    Log.d("ShoppingCart:stop release user with badge = ", badge)
                    badge?.let {
                        CoroutineScope(Dispatchers.IO).launch {

                            backend.loginRelease(badge!!, accessToken)
                        }
                    }
                    Toast.makeText(requireContext(), "Successfully approved", Toast.LENGTH_LONG)
                        .show()
                    // TODO figure out why at this popbackstack, the fragment doesn't finish its lifecycle
//                    requireActivity().supportFragmentManager.popBackStack()
//                    requireActivity().supportFragmentManager.popBackStack("standby",
//                        FragmentManager.POP_BACK_STACK_INCLUSIVE)
                    parentFragmentManager.popBackStack("standby",
                        FragmentManager.POP_BACK_STACK_INCLUSIVE)
                }
                .setNegativeButton("No") { dialog, _ ->
                    // TODO handler for unapproved
                    dialog.dismiss()
                }
            dialog = builder.create()
            dialog!!.window?.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            dialog!!.show()
        } else {
            val builder = AlertDialog.Builder(requireContext())
            builder.setMessage("What would you like to do?")
                .setCancelable(false)
                .setPositiveButton("Verify") { _, _ ->
                    parentFragmentManager.beginTransaction().apply {
                        passiveTimeout.cancel()
                        Log.d("ShoppingCart:stop", "cancel passivetimeout, approvalState = false")
                        requireContext().unregisterReceiver(dockChangeReceiver)
                        CoroutineScope(Dispatchers.IO).launch {
                            Log.d("ShoppingCart:stop release user with badge = ", badge)
                            backend.loginRelease(badge, accessToken)
                        }
                        replace(R.id.flFragment, Approval(backend, cartId, articleList))
                        addToBackStack("shoppingcart")
                        commit()
                    }
                }
                .setNegativeButton("Continue") { dialog, _ ->
                    dialog.dismiss()
                }
            dialog = builder.create()
            dialog!!.window?.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            dialog!!.show()
        }
    }

    private val dockChangeReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            if (intent.action == "com.symbol.intent.device.DOCKED") {
                // 1. release user
                // 2. stop cradletimeout
                // 3. stop passive timeout
                // 4. unregister receiver
                // 5. pop backstack

                val sessionActive = SessionManager.getInstance().getSessionState()
                val sessionId = SessionManager.getInstance().getSessionId()
                if (sessionId != null) {
                    Log.d("ShoppingCart:dockChangeReceiver sessionId = ", sessionId)
                    Log.d("ShoppingCart:dockChangeReceiver shoppingSessionId = ", shoppingSessionId)
                }

                if (sessionActive == true && sessionId.equals(shoppingSessionId)) {
                    SessionManager.getInstance().setSessionState(false)
                    badge?.let {
                        CoroutineScope(Dispatchers.IO).launch {
                            Log.d("ShoppingCart:dockChangeReceiver release user with badge = ", badge)
                            backend.loginRelease(badge!!, accessToken)
                        }
                    }

                    if (approvalState) {
                        approvalState = false
                        CoroutineScope(Dispatchers.IO).launch {
                            Log.d("Approval:dockChangeReceiver backend.approve ", "cartId=$cartId, accessToken=$accessToken")
                            backend.approve(cartId, accessToken, JSONArray(articleList))
                        }
                        Toast.makeText(requireContext(),
                            "Successfully approved",
                            Toast.LENGTH_LONG)
                            .show()
                    }
                }

                passiveTimeout.cancel()
                approvalState = false
                isApproved = false
                dialog?.cancel()
                Log.d("ShoppingCart:dockChangeReceiver", "cancel passivetimeout")
                try {
                    requireContext().unregisterReceiver(this)

                } catch (e: IllegalArgumentException) {
                    Log.d("ProjectSelection:debug_unregister_catch",
                        "battery_status_charging and unregistering receiver here error: $e")
                }
//                requireActivity().supportFragmentManager.popBackStack()
//                requireActivity().supportFragmentManager.popBackStack("standby",
//                    FragmentManager.POP_BACK_STACK_INCLUSIVE)
                parentFragmentManager.popBackStack("standby",
                    FragmentManager.POP_BACK_STACK_INCLUSIVE)
            }
        }
    }

    private fun resetPassiveTimeout() {
        Log.d("ShoppingCart:debug_double-passivetimeout: isApproved = ", isApproved.toString())
        passiveTimeout.cancel()
        passiveTimeout.start()
    }
}