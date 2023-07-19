package com.novodin.ihc.fragments

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.*
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.novodin.ihc.R
import com.novodin.ihc.adapters.ArticleRecyclerViewAdapter
import com.novodin.ihc.config.Config
import com.novodin.ihc.model.Article
import com.novodin.ihc.model.QuantityType
import com.novodin.ihc.network.Backend
import com.novodin.ihc.zebra.BarcodeScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs
import android.util.Base64
import android.view.Gravity
import androidx.fragment.app.FragmentManager
import com.novodin.ihc.SessionManager

class Filler(
    private var badge: String,
    private var accessToken: String,
    private var backend: Backend,
    private var fromPackingslip: Boolean,
) :
    Fragment(R.layout.fragment_filler) {
    private var intentFilter = IntentFilter()

    private var dialog: AlertDialog? = null
    private lateinit var fillerSessionId: String

    // Barcode scanner
    private lateinit var barcodeScanner: BarcodeScanner

    // Filler variables
    private val articleList: MutableList<Article> = ArrayList()
    private var selectedArticle: Article? = null
    private var adding: Int = 0
    private var btnAdding: Int = 0
    private var itemCount: Int = 0
    private var puCount: Int = 0
    private var unitCount: Int = 0

    // View elements
    private lateinit var rvArticleList: RecyclerView
    private lateinit var tvItemCount: TextView
    private lateinit var tvPUCount: TextView
    private lateinit var tvUnitCount: TextView

    private lateinit var tvAdding: TextView
    private lateinit var tvLabelAdding: TextView
    private lateinit var tvNewTotal: TextView
    private lateinit var ibAdd: ImageButton
    private lateinit var ibRemove: ImageButton
    private lateinit var ibStop: ImageButton
    private lateinit var tvAdd: TextView
    private lateinit var tvRemove: TextView

    // View color variables
    private var colorPrimaryEnabled = 0
    private var colorPrimaryDisabled = 0

    // Timer variables
    private lateinit var passiveTimeout: CountDownTimer
    private lateinit var removeFromCradleTimeout: CountDownTimer

    private var isCompleted: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intentFilter.addAction("com.symbol.intent.device.DOCKED")
        intentFilter.addAction("com.symbol.intent.device.UNDOCKED")
        requireContext().registerReceiver(dockChangeReceiver, intentFilter)

        val sessionId = SessionManager.getInstance().getSessionId()
        val sessionActive = SessionManager.getInstance().getSessionState()
        if (sessionId != null) {
            Log.d("Filler:sessionId", sessionId)
            Log.d("Filler:sessionState", sessionActive.toString())
            fillerSessionId = sessionId
        }

        isCompleted = false

        // setup "remove from cradle" timeout
        removeFromCradleTimeout =
            object :
                CountDownTimer(Config.PassiveRemoveFromCradleTimeout.toLong(),
                    Config.PassiveRemoveFromCradleTimeout.toLong()) {
                override fun onTick(p0: Long) {}
                override fun onFinish() {
                    val sessionId = SessionManager.getInstance().getSessionId()
                    if (sessionId != null) {
                        Log.d("Filler:passivetimeout:onFinish sessionId = ", sessionId)
                        Log.d("Filler:passivetimeout:onFinish fillerSessionId = ", fillerSessionId)
                        if (sessionId.equals(fillerSessionId)) {

                            SessionManager.getInstance().setSessionState(false)
                            isCompleted = true
                            if (btnAdding != 0) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    val b64encodedBarcodeSelected = Base64.encodeToString(
                                        selectedArticle!!.barcode.toByteArray(),
                                        Base64.DEFAULT)
                                    backend.setFillerItem(b64encodedBarcodeSelected,
                                        btnAdding.toString(),
                                        accessToken) {
                                        Toast.makeText(requireContext(),
                                            "Updating the database failed",
                                            Toast.LENGTH_LONG)
                                            .show()
                                    }
                                }
                            }
                            // release the user if the user has already been identified
                            badge?.let {
                                CoroutineScope(Dispatchers.IO).launch {
                                    backend.loginRelease(badge!!, accessToken)
                                }
                            }
                            passiveTimeout.cancel()
                            dialog?.cancel()

                            try {
                                requireContext().unregisterReceiver(dockChangeReceiver)

                            } catch (e: IllegalArgumentException) {
                                Log.d("Filler:debug_unregister_catch",
                                    "removefromcradletimeout unregister error: $e")
                            }
                            parentFragmentManager.popBackStack("standby",
                                FragmentManager.POP_BACK_STACK_INCLUSIVE)
                        }
                    }
                }
            }
        // setup passive user timeout
        passiveTimeout =
            object : CountDownTimer(Config.PassiveTimeoutLong.toLong(), Config.PassiveTimeoutLong.toLong()) {
                override fun onTick(p0: Long) {}
                override fun onFinish() {
                    isCompleted = true
                    val sessionActive = SessionManager.getInstance().getSessionState()
                    val sessionId = SessionManager.getInstance().getSessionId()
                    if (sessionId != null) {
                        Log.d("Filler:passivetimeout:onFinish sessionId = ", sessionId)
                        Log.d("Filler:passivetimeout:onFinish fillerSessionId = ", fillerSessionId)
                        if (sessionId.equals(fillerSessionId)) {
                            if (btnAdding != 0) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    val b64encodedBarcodeSelected = Base64.encodeToString(
                                        selectedArticle!!.barcode.toByteArray(),
                                        Base64.DEFAULT)
                                    backend.setFillerItem(b64encodedBarcodeSelected,
                                        btnAdding.toString(),
                                        accessToken) {
                                        Toast.makeText(requireContext(),
                                            "Updating the database failed",
                                            Toast.LENGTH_LONG)
                                            .show()
                                    }
                                }
                            }
                            // release the user if the user has already been identified
                            badge?.let {
                                CoroutineScope(Dispatchers.IO).launch {
                                    backend.loginRelease(badge!!, accessToken)
                                }
                            }
                            removeFromCradleTimeout.cancel()
                            dialog?.cancel()
                            Log.d("Filler:onCreate", "passivetimeout unregister")
                            try {
                                requireContext().unregisterReceiver(dockChangeReceiver)
                                parentFragmentManager.popBackStack("standby",
                                    FragmentManager.POP_BACK_STACK_INCLUSIVE)

                            } catch (e: IllegalArgumentException) {
                                Log.d("Filler:unregisterReceiver",
                                    "error: $e")
                            } catch (e2: IllegalStateException) {
                                Log.d("Filler:unregisterReceiver",
                                    "error: $e2")
                            }
                            val log =  "Filler passivetimeout, badge $badge "
                            CoroutineScope(Dispatchers.IO).launch {
                                backend.log(log) {
                                    Log.d("Filler:passivetimeout backend.log", "error: $it")
                                }
                            }
                        }
                    }
                }
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d("Filler:onViewCreated", "")
        super.onViewCreated(view, savedInstanceState)

        colorPrimaryDisabled =
            requireContext().getColor(com.google.android.material.R.color.material_on_primary_disabled)
        colorPrimaryEnabled =
            requireContext().getColor(com.google.android.material.R.color.material_on_primary_emphasis_high_type)

        initNavButtons(view)

        if (fromPackingslip) {
            passiveTimeout.start()
        } else {
            removeFromCradleTimeout.start()
        }

        rvArticleList = view.findViewById(R.id.rvArticleList) as RecyclerView
        tvItemCount = view.findViewById(R.id.tvItemCount) as TextView
        tvPUCount = view.findViewById(R.id.tvPUCount) as TextView
        tvUnitCount = view.findViewById(R.id.tvUnitCount) as TextView

        tvItemCount.text = itemCount.toString()
        tvPUCount.text = puCount.toString()
        tvUnitCount.text = unitCount.toString()

        rvArticleList.adapter = ArticleRecyclerViewAdapter(articleList)
        (rvArticleList.adapter as ArticleRecyclerViewAdapter).setOnItemAddedExtra { enablePlusMinus() }

        tvAdding = view.findViewById(R.id.tvAdding) as TextView
        tvLabelAdding = view.findViewById(R.id.tvLabelAdding) as TextView
        tvNewTotal = view.findViewById(R.id.tvNewTotal) as TextView
        tvItemCount = view.findViewById(R.id.tvItemCount) as TextView

        ibAdd = view.findViewById(R.id.ibNavFour) as ImageButton
        ibRemove = view.findViewById(R.id.ibNavOne) as ImageButton
        ibStop = view.findViewById(R.id.ibNavThree) as ImageButton
        tvAdd = view.findViewById(R.id.textView10) as TextView
        tvRemove = view.findViewById(R.id.textView7) as TextView

        tvAdding.text = "0"
        tvNewTotal.text = "0"

        ibAdd.setOnClickListener { onModifyAmount(true) }
        ibRemove.setOnClickListener { onModifyAmount(false) }
        ibStop.setOnClickListener {
            val builder = AlertDialog.Builder(requireContext())
            builder.setMessage("Are you sure you want to stop?")
                .setCancelable(false)
                .setPositiveButton("Yes") { _, _ ->
                    if (btnAdding != 0) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val b64encodedBarcodeSelected = Base64.encodeToString(selectedArticle!!.barcode.toByteArray(), Base64.DEFAULT)
                            backend.setFillerItem(b64encodedBarcodeSelected, btnAdding.toString(), accessToken) {
                                Toast.makeText(requireContext(), "Updating the database failed", Toast.LENGTH_LONG)
                                    .show()
                            }
                            btnAdding = 0
                        }
                    }

                    isCompleted = true
                    CoroutineScope(Dispatchers.IO).launch {
                        backend.loginRelease(badge, accessToken)
                    }
                    passiveTimeout.cancel()
                    parentFragmentManager.popBackStack("standby",
                        FragmentManager.POP_BACK_STACK_INCLUSIVE)
                    try {
                        requireContext().unregisterReceiver(dockChangeReceiver)
                    } catch (e: IllegalArgumentException) {
                        Log.d("Filler:ibStop unregisterReceiver",
                            "error: $e")
                    } catch (e2: IllegalStateException) {
                        Log.d("Filler:ibStop unregisterReceiver",
                            "error: $e2")
                    }
                }
                .setNegativeButton("No") { dialog, _ ->
                    resetPassiveTimeout()
                    dialog.dismiss()
                }
            dialog = builder.create()
            dialog!!.window?.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            dialog!!.show()
        }
    }

    override fun onResume() {
        super.onResume()

        val sessionId = SessionManager.getInstance().getSessionId()
        if (sessionId != null) {
            Log.d("Filler:onResume sessionId = ", sessionId)
            Log.d("Filler:onResume fillerSessionId = ", fillerSessionId)
        }

        if (sessionId.equals(fillerSessionId)) {
            Log.d("Filler:onResume", "resetPassiveTimeout")
            resetPassiveTimeout()
        }

        barcodeScanner = BarcodeScanner(requireContext())
        barcodeScanner.setDataCallback(::onBarcodeScanData)
        barcodeScanner.setStatusCallback(::onBarcodeScanStatus)
        requireContext().registerReceiver(dockChangeReceiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        val sessionId = SessionManager.getInstance().getSessionId()
        if (sessionId != null) {
            Log.d("Filler:onPause sessionId = ", sessionId)
            Log.d("Filler:onPause fillerSessionId = ", fillerSessionId)
        }

        barcodeScanner.onClosed()
        passiveTimeout.cancel()
        Log.d("Filler:onPause", "cancel passivetimeout")
    }

    override fun onDestroy() {
        super.onDestroy()
        val sessionId = SessionManager.getInstance().getSessionId()
        if (sessionId != null) {
            Log.d("Filler:onDestroy sessionId = ", sessionId)
            Log.d("Filler:onDestroy fillerSessionId = ", fillerSessionId)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val sessionId = SessionManager.getInstance().getSessionId()
        if (sessionId != null) {
            Log.d("Filler:onDestroyView sessionId = ", sessionId)
            Log.d("Filler:onDestroyView fillerSessionId = ", fillerSessionId)
        }
    }
    private fun initNavButtons(view: View) {
        // First nav button
        (view.findViewById(R.id.ibNavOne) as ImageButton).apply {
            this.setImageResource(R.drawable.ic_minus)
            this.isEnabled = false
        }
        (view.findViewById(R.id.textView7) as TextView).apply {
            this.text = "Remove"
            this.setTextColor(colorPrimaryDisabled)
        }

        // Second nav button
        (view.findViewById(R.id.ibNavTwo) as ImageButton).apply {
            this.setImageResource(R.drawable.ic_arrow_back)
            this.isEnabled = false
        }
        (view.findViewById(R.id.textView8) as TextView).apply {
            this.text = "Back"
            this.setTextColor(colorPrimaryDisabled)
        }

        // Third nav button (always enabled)

        (view.findViewById(R.id.ibNavThree) as ImageButton).apply {
            this.setImageResource(R.drawable.ic_cancel)
            this.isEnabled = true
        }
        (view.findViewById(R.id.textView9) as TextView).apply {
            this.text = "Stop"
            this.setTextColor(colorPrimaryEnabled)
        }


        // Fourth nav button
        (view.findViewById(R.id.ibNavFour) as ImageButton).apply {
            this.setImageResource(R.drawable.ic_plus)
            this.isEnabled = false
        }
        (view.findViewById(R.id.textView10) as TextView).apply {
            this.text = "Add"
            this.setTextColor(colorPrimaryDisabled)
        }
    }

    private fun enablePlusMinus() {
        if (!(ibRemove.isEnabled || ibAdd.isEnabled)) {
            ibRemove.isEnabled = true
            tvRemove.setTextColor(colorPrimaryEnabled)
            ibAdd.isEnabled = true
            tvAdd.setTextColor(colorPrimaryEnabled)
        }
    }


    private fun disablePlusMinus() {
        if (ibRemove.isEnabled || ibAdd.isEnabled) {
            ibRemove.isEnabled = false
            tvRemove.setTextColor(colorPrimaryDisabled)
            ibAdd.isEnabled = false
            tvAdd.setTextColor(colorPrimaryDisabled)
        }
    }

    private fun onModifyAmount(add: Boolean) {
        resetPassiveTimeout()
        if (selectedArticle != null) {
            if (add) {
                adding++
                btnAdding++
                selectedArticle!!.count++
                changeQuantity(selectedArticle!!.quantityType, 1)
            } else {
                adding--
                btnAdding--
                selectedArticle!!.count--
                changeQuantity(selectedArticle!!.quantityType, -1)
            }
            val adapter = rvArticleList.adapter as ArticleRecyclerViewAdapter

            val addingLabel = if (adding < 0) "Removing" else "Adding"
            (requireContext() as Activity).runOnUiThread {
                tvLabelAdding.text = addingLabel
                tvAdding.text = abs(adding).toString()
                tvNewTotal.text = (selectedArticle!!.count).toString()
                adapter.notifyItemChanged(0,selectedArticle)
            }
        }
    }

    private fun onBarcodeScanData(barcode: String) {
        resetPassiveTimeout()
        CoroutineScope(Dispatchers.IO).launch {

            val b64encodedBarcode = Base64.encodeToString(barcode.toByteArray(), Base64.DEFAULT)
            val item = backend.getFillerItem(b64encodedBarcode, accessToken) {
//                Toast.makeText(requireContext(), "Unknown barcode", Toast.LENGTH_LONG).show()
                val toast = Toast.makeText(requireContext(), "Unknown barcode", Toast.LENGTH_LONG)
                val toastView = toast.view
                val textView = toastView?.findViewById<TextView>(android.R.id.message)
                textView?.textSize = 28f
                textView?.setTextColor(Color.RED)
                toast.setGravity(Gravity.CENTER, 0, 0)
                toast.show()
            }
            val article = Article(item!!.getInt("id"),
                barcode,
                item.getString("name"),
                item.getString("number"),
                QuantityType.fromInt(item.getInt("cat")),
                item.getInt("currentamount"))

            Log.d("Filler:onBarcodeScanData article = ", article.toString())
            var isPreviousScannedArticle = false
            // If a new article is scanned, the previous one should be stored in the database
            // If the last article is scanned again it should be increased with 1 item

            if (selectedArticle != null) {
                val b64encodedBarcodeSelected = Base64.encodeToString(selectedArticle!!.barcode.toByteArray(), Base64.DEFAULT)
                if (selectedArticle!!.id != article.id) {
                    if (btnAdding != 0) {
                        backend.setFillerItem(b64encodedBarcodeSelected, btnAdding.toString(), accessToken) {
                            println(it)
                            Toast.makeText(requireContext(),"Something went wrong", Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                    adding = 0
                    selectedArticle = article
                } else {
                    isPreviousScannedArticle = true
                    selectedArticle = article
                    backend.setFillerItem(b64encodedBarcodeSelected, (1+btnAdding).toString(), accessToken) {
                        println(it)
                        Toast.makeText(requireContext(), "Something went wrong", Toast.LENGTH_LONG)
                            .show()
                    }
                    // Add addtional count to the selected article, added count for this article and
                    // the item, PU and Unit counts at the top of the screen
                    selectedArticle!!.count += 1+btnAdding
                    adding++
                    changeQuantity(article.quantityType, 1)
                }
            } else {
                adding = 0
                selectedArticle = article
            }

            btnAdding = 0

            val adapter = rvArticleList.adapter as ArticleRecyclerViewAdapter
            if (isPreviousScannedArticle) {
                (requireContext() as Activity).runOnUiThread {
                    articleList.removeAt(0)
                    articleList.add(0, selectedArticle!!)
                    adapter.notifyItemChanged(0)
                }
            } else {

                // put scanned article at the top position in the list, scroll to top of list if needed
                var alreadyExisted = false
                for ((i, art) in articleList.withIndex()) {
                    if (art.id == selectedArticle!!.id) {
                        alreadyExisted = true
                        articleList.removeAt(i)
                        articleList.add(0, selectedArticle!!)
                        (requireContext() as Activity).runOnUiThread {
                            adapter.notifyItemRemoved(i)
                            adapter.notifyItemInserted(0)
                            rvArticleList.scrollToPosition(0)
                        }
                        break
                    }
                }
                if (!alreadyExisted) {
                    articleList.add(0, article)
                    (requireContext() as Activity).runOnUiThread {
                        adapter.notifyItemInserted(0)
                        rvArticleList.scrollToPosition(0)
                    }
                }
            }

            (requireContext() as Activity).runOnUiThread {
                tvLabelAdding.text = "Adding"
                tvAdding.text = adding.toString()
                tvNewTotal.text = selectedArticle!!.count.toString()
            }
        }
    }

    private fun onBarcodeScanStatus(status: String) {
        println(status)
    }

    private val dockChangeReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.Q)
        override fun onReceive(context: Context?, intent: Intent) {
            Log.d("Filler:dockChangeReceiver", "action: ${intent.action}")
            if (intent.action == "com.symbol.intent.device.DOCKED") {
                // 1. update database with added items
                // 2. release user
                // 3. stop cradletimeout
                // 4. stop passive timeout
                // 5. unregister receiver
                // 6. pop backstack
                val sessionId = SessionManager.getInstance().getSessionId()
                if (sessionId != null) {
                    Log.d("Filler:dockChangeReceiver:onReceive sessionId = ", sessionId)
                    Log.d("Filler:dockChangeReceiver:onReceive fillerSessionId = ", fillerSessionId)
                }
                if (sessionId.equals(fillerSessionId)) {
                    if (!isCompleted) {
                        isCompleted = true
                        if (btnAdding != 0) {
                            CoroutineScope(Dispatchers.IO).launch {
                                val b64encodedBarcodeSelected =
                                    Base64.encodeToString(selectedArticle!!.barcode.toByteArray(),
                                        Base64.DEFAULT)
                                backend.setFillerItem(b64encodedBarcodeSelected,
                                    btnAdding.toString(),
                                    accessToken) {
                                    Toast.makeText(requireContext(),
                                        "Updating the database failed",
                                        Toast.LENGTH_LONG)
                                        .show()
                                }
                            }
                        }
                        badge?.let {
                            CoroutineScope(Dispatchers.IO).launch {
                                backend.loginRelease(badge!!, accessToken)
                            }
                        }
                        parentFragmentManager.popBackStack("standby",
                            FragmentManager.POP_BACK_STACK_INCLUSIVE)
                    }
                    removeFromCradleTimeout.cancel()
                    passiveTimeout.cancel()
                    dialog?.cancel()
                    Log.d("Filler:passiveTimeout", "passivetimeout cancel - dockChangeReceiver")
                    try {
                        requireContext().unregisterReceiver(this)
                        //                        parentFragmentManager.popBackStack("standby",
                        //                            FragmentManager.POP_BACK_STACK_INCLUSIVE)
                    } catch (e: IllegalArgumentException) {
                        Log.d("Filler:unregisterReceiver",
                            "error: $e")
                    } catch (e2: IllegalStateException) {
                        Log.d("Filler:unregisterReceiver",
                            "error: $e2")
                    }
                }

            }
            if (intent.action == "com.symbol.intent.device.UNDOCKED") {
                val sessionId = SessionManager.getInstance().getSessionId()
                if (sessionId != null) {
                    Log.d("Filler:dockChangeReceiver:UNDOCKED sessionId = ", sessionId)
                    Log.d("Filler:dockChangeReceiver:UNDOCKED fillerSessionId = ", fillerSessionId)
                }
                if (sessionId.equals(fillerSessionId)) {
                    // 1. stop cradle timeout
                    // 2. start passive timeout
                    removeFromCradleTimeout.cancel()
                    passiveTimeout.start()
                }
            }
        }
    }

    private fun resetPassiveTimeout() {
        Log.d("Filler:debug_double-passivetimeout: ", "resetPassiveTimeout ")
        passiveTimeout.cancel()
        passiveTimeout.start()
    }

    private fun changeQuantity(quantityType :QuantityType, amount :Int) {
        when (quantityType) {
            QuantityType.ITEM -> {
                itemCount += amount
                tvItemCount.text = (itemCount).toString()
            }
            QuantityType.PU -> {
                puCount += amount
                tvPUCount.text = (puCount).toString()
            }
            QuantityType.UNIT -> {
                unitCount += amount
                tvUnitCount.text = (unitCount).toString()
            }
        }
    }

}