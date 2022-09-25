package com.novodin.ihc.zebra

import android.content.Context
import com.symbol.emdk.EMDKManager
import com.symbol.emdk.EMDKResults
import com.symbol.emdk.barcode.*
import java.lang.Exception

class BarcodeScanner(context: Context) :
    EMDKManager.EMDKListener, Scanner.StatusListener,
    Scanner.DataListener {

    private var emdkManager: EMDKManager? = null
    private var barcodeManager: BarcodeManager? = null
    private var scanner: Scanner? = null

    private var dataCallback: (String) -> Unit = {}
    private var statusCallback: (String) -> Unit = {}

    init {
        val results = EMDKManager.getEMDKManager(context, this)
        if (results.statusCode != EMDKResults.STATUS_CODE.SUCCESS) {
            throw Exception("EMDKManager object request failed")
        }
    }

    fun setDataCallback(cb: (String) -> Unit) {
        dataCallback = cb
    }

    fun setStatusCallback(cb: (String) -> Unit) {
        statusCallback = cb
    }

    override fun onOpened(emdkManager: EMDKManager) {
        this.emdkManager = emdkManager
        barcodeManager =
            this.emdkManager!!.getInstance(EMDKManager.FEATURE_TYPE.BARCODE) as BarcodeManager
        initScanner()
    }

    override fun onData(scanDataCollection: ScanDataCollection) {
        if (scanDataCollection.result == ScannerResults.SUCCESS) {
            dataCallback.run {
                val scanData = scanDataCollection.scanData
                var dataStr = ""
                for (data in scanData) {
                    dataStr = data.data
                }
                invoke(dataStr)
            }
        }
    }

    override fun onClosed() {
        if (scanner != null) {
            if (scanner!!.isEnabled) scanner!!.disable()
        }
        scanner = null
        emdkManager?.release()
        emdkManager = null
    }


    override fun onStatus(statusData: StatusData) {
        // The status will be returned on multiple cases. Check the state and take the action.
        // Get the current state of scanner in background
        val state = statusData.state
        var statusStr = ""
        when (state) {
            StatusData.ScannerStates.IDLE -> {
                // Scanner is idle and ready to change configuration and submit read.
                statusStr = statusData.friendlyName + " is   enabled and idle..."
                // Change scanner configuration. This should be done while the scanner is in IDLE state.
                setConfig()
                try {
                    // Starts an asynchronous Scan. The method will NOT turn ON the scanner beam,
                    //but puts it in a  state in which the scanner can be turned on automatically or by pressing a hardware trigger.
                    scanner!!.read()
                } catch (e: ScannerException) {
                    e.message?.let { statusCallback.invoke(it) }
                }
            }
            StatusData.ScannerStates.WAITING ->                 // Scanner is waiting for trigger press to scan...
                statusStr = "Scanner is waiting for trigger press..."
            StatusData.ScannerStates.SCANNING ->                 // Scanning is in progress...
                statusStr = "Scanning..."
            StatusData.ScannerStates.DISABLED -> {}
            StatusData.ScannerStates.ERROR ->                 // Error has occurred during scanning
                statusStr = "An error has occurred."
            else -> {}
        }

        // Updates TextView with scanner state on UI thread.
        statusCallback.invoke(statusStr)
    }

    private fun initScanner() {
        // Get default scanner defined on the device
        scanner = barcodeManager!!.getDevice(BarcodeManager.DeviceIdentifier.DEFAULT)

        // Implement the DataListener interface and pass the pointer of this object to get the data callbacks.
        scanner!!.addDataListener(this)

        // Implement the StatusListener interface and pass the pointer of this object to get the status callbacks.
        scanner!!.addStatusListener(this)

        // Hard trigger. When this mode is set, the user has to manually
        // press the trigger on the device after issuing the read call.
        // NOTE: For devices without a hard trigger, use TriggerType.SOFT_ALWAYS.
        scanner!!.triggerType = Scanner.TriggerType.HARD
        if (!scanner!!.isEnabled) {
            try {
                // Enable the scanner
                // NOTE: After calling enable(), wait for IDLE status before calling other scanner APIs
                // such as setConfig() or read().
                scanner!!.enable()
            } catch (e: ScannerException) {
                println(e)
                e.message?.let { statusCallback.invoke(it) }
//                scanner?.release()
            }
        }
    }

    private fun setConfig() {
        scanner?.let {
            try {
                // Get scanner config
                val config = scanner!!.config
                // Enable haptic feedback
                if (config.isParamSupported("config.scanParams.decodeHapticFeedback")) {
                    config.scanParams.decodeHapticFeedback = true
                }
                // Set scanner config
                scanner!!.config = config
            } catch (e: ScannerException) {
                throw e
            }
        }
    }

}