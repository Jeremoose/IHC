package com.novodin.ihc.fragments

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import java.lang.Runnable
import kotlin.math.abs
import android.util.Base64

class Filler(
    private var badge: String,
    private var accessToken: String,
    private var backend: Backend,
    private var fromPackingslip: Boolean,
) :
    Fragment(R.layout.fragment_filler) {
    private var intentFilter = IntentFilter()

    private var dialog: AlertDialog? = null

    // Filler variables
    private lateinit var scanTimeout: Runnable
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
    private lateinit var tvArticleName: TextView
    private lateinit var tvArticleNumber: TextView
    private lateinit var tvQuantityType: TextView
    private lateinit var ibAdd: ImageButton
    private lateinit var ibRemove: ImageButton
    private lateinit var ibStop: ImageButton

    // Timer variables
    private lateinit var passiveTimeout: CountDownTimer
    private lateinit var removeFromCradleTimeout: CountDownTimer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intentFilter.addAction("com.symbol.intent.device.DOCKED")
        intentFilter.addAction("com.symbol.intent.device.UNDOCKED")


        // setup listener to battery change (cradle detection)
        requireContext().registerReceiver(dockChangeReceiver, intentFilter)
        // setup "remove from cradle" timeout
        removeFromCradleTimeout =
            object :
                CountDownTimer(Config.PassiveRemoveFromCradleTimeout.toLong(),
                    Config.PassiveRemoveFromCradleTimeout.toLong()) {
                override fun onTick(p0: Long) {}
                override fun onFinish() {
                    if (btnAdding != 0) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val b64encodedBarcodeSelected = Base64.encodeToString(selectedArticle!!.barcode.toByteArray(), Base64.DEFAULT)
                            backend.setFillerItem(b64encodedBarcodeSelected, btnAdding.toString(), accessToken) {
                                Toast.makeText(requireContext(), "Updating the database failed", Toast.LENGTH_LONG)
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
                    requireActivity().supportFragmentManager.popBackStack()
                }
            }
        // setup passive user timeout
        passiveTimeout =
            object : CountDownTimer(Config.PassiveTimeoutLong.toLong(), Config.PassiveTimeoutLong.toLong()) {
                override fun onTick(p0: Long) {}
                override fun onFinish() {
                    if (btnAdding != 0) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val b64encodedBarcodeSelected = Base64.encodeToString(selectedArticle!!.barcode.toByteArray(), Base64.DEFAULT)
                            backend.setFillerItem(b64encodedBarcodeSelected, btnAdding.toString(), accessToken) {
                                Toast.makeText(requireContext(), "Updating the database failed", Toast.LENGTH_LONG)
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
                    Log.d("Filler:debug_unregister_fatal", "passivetimeout unregister")
                    try {
                        requireContext().unregisterReceiver(dockChangeReceiver)

                    } catch (e: IllegalArgumentException) {
                        Log.d("Filler:debug_unregister_catch",
                            "passivetimeout unregister error: $e")
                    }
                    requireActivity().supportFragmentManager.popBackStack()
                }
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

        tvAdding = view.findViewById(R.id.tvAdding) as TextView
        tvLabelAdding = view.findViewById(R.id.tvLabelAdding) as TextView
        tvNewTotal = view.findViewById(R.id.tvNewTotal) as TextView
        tvItemCount = view.findViewById(R.id.tvItemCount) as TextView

        ibAdd = view.findViewById(R.id.ibNavFour) as ImageButton
        ibRemove = view.findViewById(R.id.ibNavOne) as ImageButton
        ibStop = view.findViewById(R.id.ibNavThree) as ImageButton

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
                        }
                    }
                    CoroutineScope(Dispatchers.IO).launch {
                        backend.loginRelease(badge, accessToken)
                    }
                    requireActivity().supportFragmentManager.popBackStack()
                }
                .setNegativeButton("No") { dialog, _ ->
                    dialog.dismiss()
                }
            dialog = builder.create()
            dialog!!.window?.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            dialog!!.show()
//            val alert = builder.create()
//            alert.window?.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
//                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
//            alert.show()
        }

        val barcodeScanner = BarcodeScanner(requireContext())
        barcodeScanner.setDataCallback(::onBarcodeScanData)
        barcodeScanner.setStatusCallback(::onBarcodeScanStatus)
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
                Toast.makeText(requireContext(), "Unknown barcode", Toast.LENGTH_LONG).show()
            }
            val article = Article(item!!.getInt("id"),
                barcode,
                item.getString("name"),
                item.getString("number"),
                QuantityType.fromInt(item.getInt("cat")),
                item.getInt("currentamount"))

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
            Log.d("debug_unregister_fatal_intent", "action: ${intent.action}")
            if (intent.action == "com.symbol.intent.device.DOCKED") {
                // 1. update database with added items
                // 2. release user
                // 3. stop cradletimeout
                // 4. stop passive timeout
                // 5. unregister receiver
                // 6. pop backstack
                if (btnAdding != 0) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val b64encodedBarcodeSelected = Base64.encodeToString(selectedArticle!!.barcode.toByteArray(), Base64.DEFAULT)
                        backend.setFillerItem(b64encodedBarcodeSelected, btnAdding.toString(), accessToken) {
                            Toast.makeText(requireContext(), "Updating the database failed", Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                }
                badge?.let {
                    CoroutineScope(Dispatchers.IO).launch {
                        backend.loginRelease(badge!!, accessToken)
                    }
                }
                removeFromCradleTimeout.cancel()
                passiveTimeout.cancel()
                dialog?.cancel()
                Log.d("Filler:passiveTimeout", "passivetimeout cancel - dockChangeReceiver")

                try {
                    requireContext().unregisterReceiver(this)

                } catch (e: IllegalArgumentException) {
                    Log.d("Filler:debug_unregister_catch",
                        "battery_status_charging and unregistering receiver here error: $e")
                }
                requireActivity().supportFragmentManager.popBackStack()
            }

            if (intent.action == "com.symbol.intent.device.UNDOCKED") {
                // 1. stop cradle timeout
                // 2. start passive timeout
                removeFromCradleTimeout.cancel()
                passiveTimeout.start()
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