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
import com.novodin.ihc.R
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

class Filler(
    private var badge: String,
    private var accessToken: String,
    private var backend: Backend,
) :
    Fragment(R.layout.fragment_filler) {
    private var intentFilter = IntentFilter()

    private var dialog: AlertDialog? = null

    // Filler variables
    private lateinit var scanTimeout: Runnable
    private var scanTimeoutHandler: Handler = Handler(Looper.getMainLooper())
    private var selectedArticle: Article? = null
    private var adding: Int = 0

    // View elements
    private lateinit var tvAdding: TextView
    private lateinit var tvLabelAdding: TextView
    private lateinit var tvNewTotal: TextView
    private lateinit var tvItemCount: TextView
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

        tvAdding = view.findViewById(R.id.tvAdding) as TextView
        tvLabelAdding = view.findViewById(R.id.tvLabelAdding) as TextView
        tvNewTotal = view.findViewById(R.id.tvNewTotal) as TextView
        tvItemCount = view.findViewById(R.id.tvItemCount) as TextView
        tvArticleName = view.findViewById(R.id.tvArticleName) as TextView
        tvArticleNumber = view.findViewById(R.id.tvArticleNumber) as TextView
        tvQuantityType = view.findViewById(R.id.tvQuantityType) as TextView
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
                    scanTimeoutHandler.removeCallbacks(scanTimeout)
                    CoroutineScope(Dispatchers.IO).launch {
                        backend.loginRelease(badge, accessToken)
                    }
                    requireActivity().supportFragmentManager.popBackStack()
                }
                .setNegativeButton("No") { dialog, _ ->
                    dialog.dismiss()
                }
            val alert = builder.create()
            alert.window?.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            alert.show()
        }

        val barcodeScanner = BarcodeScanner(requireContext())
        barcodeScanner.setDataCallback(::onBarcodeScanData)
        barcodeScanner.setStatusCallback(::onBarcodeScanStatus)

        scanTimeout = Runnable { onScanTimeout() }

    }

    private fun onScanTimeout() {
        CoroutineScope(Dispatchers.IO).launch {
            backend.setFillerItem(selectedArticle!!.barcode,
                adding.toString(),
                accessToken) {
                println(it)
                Toast.makeText(requireContext(), "Something went wrong", Toast.LENGTH_LONG)
                    .show()
            }
            selectedArticle = null
            adding = 0
            (requireContext() as Activity).runOnUiThread {
                tvLabelAdding.text = "Adding"
                tvAdding.text = "0"
                tvNewTotal.text = "0"

                tvArticleName.text = ""
                tvArticleNumber.text = ""
                tvQuantityType.text = ""
                tvItemCount.text = ""
            }
        }
    }

    private fun onModifyAmount(add: Boolean) {
        if (selectedArticle != null) {
            if (add) adding++ else adding--
            val addingLabel = if (adding < 0) "Removing" else "Adding"
            (requireContext() as Activity).runOnUiThread {
                tvLabelAdding.text = addingLabel
                tvAdding.text = abs(adding).toString()
                tvNewTotal.text = (selectedArticle!!.count + adding).toString()
            }
        }
    }

    private fun onBarcodeScanData(barcode: String) {
        CoroutineScope(Dispatchers.IO).launch {
            scanTimeoutHandler.removeCallbacks(scanTimeout)

            val item = backend.getFillerItem(barcode, accessToken) {
                Toast.makeText(requireContext(), "Unknown barcode", Toast.LENGTH_LONG).show()
            }
            val article = Article(item!!.getInt("id"),
                barcode,
                item.getString("name"),
                item.getString("number"),
                QuantityType.fromInt(item.getInt("cat")),
                item.getInt("currentamount"))

            if (selectedArticle != null) {
                if (selectedArticle!!.id != article.id) {
                    backend.setFillerItem(selectedArticle!!.barcode,
                        adding.toString(),
                        accessToken) {
                        println(it)
                        Toast.makeText(requireContext(), "Something went wrong", Toast.LENGTH_LONG)
                            .show()
                    }
                    selectedArticle = article
                } else {
                    selectedArticle = article
                    backend.setFillerItem(selectedArticle!!.barcode, "1", accessToken) {
                        println(it)
                        Toast.makeText(requireContext(), "Something went wrong", Toast.LENGTH_LONG)
                            .show()
                    }
                    selectedArticle!!.count++
                }
            } else {
                selectedArticle = article
            }

            adding = 0
            (requireContext() as Activity).runOnUiThread {
                tvLabelAdding.text = "Adding"
                tvAdding.text = "0"
                tvNewTotal.text = selectedArticle!!.count.toString()

                tvArticleName.text = selectedArticle!!.name
                tvArticleNumber.text = selectedArticle!!.number
                tvQuantityType.text = selectedArticle!!.quantityType.toString()
                tvItemCount.text = selectedArticle!!.count.toString()
            }

            scanTimeoutHandler.postDelayed(scanTimeout, 5000)
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
                // 1. release user
                // 2. stop cradletimeout
                // 3. stop passive timeout
                // 4. unregister receiver
                // 5. pop backstack
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


}