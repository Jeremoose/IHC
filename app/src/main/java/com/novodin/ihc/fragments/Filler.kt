package com.novodin.ihc.fragments

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.novodin.ihc.R
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

    // Barcode
    private lateinit var barcodeScanner: BarcodeScanner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onDestroy() {
        super.onDestroy()
        barcodeScanner.onClosed()
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
        ibAdd = view.findViewById(R.id.ibAdd) as ImageButton
        ibRemove = view.findViewById(R.id.ibRemove) as ImageButton
        ibStop = view.findViewById(R.id.ibStop) as ImageButton

        tvAdding.text = "0"
        tvNewTotal.text = "0"

        ibAdd.setOnClickListener { onModifyAmount(true) }
        ibRemove.setOnClickListener { onModifyAmount(false) }
        ibStop.setOnClickListener {
            val builder = AlertDialog.Builder(requireContext())
            builder.setMessage("Are you sure you want to stop?")
                .setCancelable(false)
                .setPositiveButton("Yes") { _, _ ->
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

        barcodeScanner =
            BarcodeScanner(requireContext(), ::onBarcodeScanData, ::onBarcodeScanStatus)

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

            scanTimeoutHandler.postDelayed(scanTimeout, 120000)
        }
    }

    private fun onBarcodeScanStatus(status: String) {
        println(status)
    }


}