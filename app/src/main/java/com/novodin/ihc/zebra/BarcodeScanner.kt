package com.novodin.ihc.zebra

import android.content.Context
import com.symbol.emdk.EMDKManager
import com.symbol.emdk.EMDKResults
import com.symbol.emdk.barcode.*

class BarcodeScanner(
    context: Context,
    private val dataCallback: (String) -> Unit,
    private val statusCallback: (String) -> Unit,
) :
    EMDKManager.EMDKListener, Scanner.StatusListener,
    Scanner.DataListener {

    private var emdkManager: EMDKManager? = null
    private var barcodeManager: BarcodeManager? = null
    private var scanner: Scanner? = null

    init {
        val results = EMDKManager.getEMDKManager(context, this)
        if (results.statusCode != EMDKResults.STATUS_CODE.SUCCESS)
            throw error("EMDKManager object request failed")
    }

    override fun onOpened(emdkManager: EMDKManager) {
        this.emdkManager = emdkManager
        initBarcodeManager()
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
        deInitScanner()
        emdkManager?.release()
        emdkManager = null

        statusCallback.invoke("EMDK closed unexpectedly! Please close and restart the application")
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

    private fun initBarcodeManager() {
        barcodeManager =
            emdkManager!!.getInstance(EMDKManager.FEATURE_TYPE.BARCODE) as BarcodeManager
    }

    private fun initScanner() {
        scanner?.let { return } // return if scanner != null

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
        try {
            // Enable the scanner
            // NOTE: After calling enable(), wait for IDLE status before calling other scanner APIs
            // such as setConfig() or read().
            scanner!!.enable()
        } catch (e: ScannerException) {
            e.message?.let { statusCallback.invoke(it) }
            deInitScanner()
        }
    }

    private fun deInitScanner() {
        if (scanner != null) {
            scanner!!.release()
        }
        scanner = null
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