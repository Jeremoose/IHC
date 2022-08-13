package com.novodin.ihc.scanner

import android.widget.TextView
import android.os.Bundle
import com.novodin.ihc.R
import android.app.Activity
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.symbol.emdk.barcode.*
import com.symbol.emdk.barcode.ScanDataCollection.ScanData
import com.symbol.emdk.barcode.StatusData.ScannerStates
import java.lang.Exception

class ScannerTestFragment : Fragment(R.layout.fragment_scanner_test) {
    private lateinit var tvStatus: TextView
    private lateinit var tvData: TextView
    private lateinit var barcodeScanner: BarcodeScanner

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvStatus = view.findViewById(R.id.tvBarcodeStatus)
        tvData = view.findViewById(R.id.tvBarcodeData)
        barcodeScanner = BarcodeScanner(requireContext(), ::updateData, ::updateStatus)

    }

    private fun updateStatus(text: String) {
        (requireContext() as Activity).runOnUiThread { tvStatus.text = text }
    }

    private fun updateData(text: String) {
        (requireContext() as Activity).runOnUiThread { tvData.text = text }
    }

//    override fun onDestroy() {
//        super.onDestroy()
//        // Release all the EMDK resources
//        if (emdkManager != null) {
//            emdkManager!!.release()
//            emdkManager = null
//        }
//    }
}