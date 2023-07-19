package com.novodin.ihc.fragments

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.novodin.ihc.R
import com.novodin.ihc.SessionManager
import com.novodin.ihc.config.Config
import com.novodin.ihc.model.Project
import com.novodin.ihc.network.Backend
import com.novodin.ihc.zebra.BarcodeScanner
import com.novodin.ihc.zebra.Cradle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import java.util.*
import java.util.regex.Pattern


private var dialog: AlertDialog? = null

class StandbyScreen() : Fragment(R.layout.fragment_standby_screen) {
//    private var intentFilter = IntentFilter()

    // View elements
    private lateinit var ivStandby: ImageView

    // general novodin scanner app vars
    private lateinit var backend: Backend
    private lateinit var cradle: Cradle

    // Polling variables
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable

    // Barcode scanner
    private lateinit var barcodeScanner: BarcodeScanner

    private val healthHandler = Handler(Looper.getMainLooper())
    private val healthRunnable = Runnable { callHealthFunction() }
    private val inactivityTimeout: Long = 1 * 60 * 60 * 1000 // 1 hour in milliseconds


    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("StandbyScreen", "onCreate")
        super.onCreate(savedInstanceState)
//        intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED)

        barcodeScanner = BarcodeScanner(requireContext())
        barcodeScanner.setDataCallback(::dataCallback)
        barcodeScanner.setStatusCallback(::statusCallback)

        // Get confiuration from store
        val prefsHelper = SharedPreferencesHelper(requireContext())
        val decodedBarcode = prefsHelper.getValue()
        Log.d("qr-config:stored config = ", decodedBarcode.toString())

        // If configuration exists use it
        if (decodedBarcode != null) {
            val configValues = decodedBarcode?.split(Pattern.compile(";"), 0)
            var timerShort: UInt
            var timerMedium: UInt
            var timerLong: UInt
            var timerCVV: ULong

            try {
                timerShort = configValues!![2]!!.toUInt()
                timerMedium = configValues!![3]!!.toUInt()
                timerLong = configValues!![4]!!.toUInt()
                timerCVV = configValues!![5]!!.toULong()
                Config.BackendIpAddress = configValues[0]
                Config.BackendPort = configValues[1]
                Config.PassiveTimeoutShort = timerShort * 1000u
                Config.PassiveTimeoutMedium = timerMedium * 1000u
                Config.PassiveTimeoutLong = timerLong * 1000u
            } catch (_: java.lang.NumberFormatException) {
                // ignore non number values
                Log.d("qr-config:stored config", "One more of the timer values is not a number")
            }

        }
        Log.d("qr-config: config = ", Config.toString())
        cradle = Cradle(requireContext())
        backend =
            Backend(requireContext(), "http://${Config.BackendIpAddress}:${Config.BackendPort}")
    }

    private fun dataCallback(barcode: String) {
        Log.d("qr-config", barcode)

        var decodedBarcodeBytes: ByteArray

        try {
            decodedBarcodeBytes = Base64.decode(barcode, Base64.DEFAULT)
        } catch (_: java.lang.IllegalArgumentException) {
            // ignore if qr code value isn' t base64 encoded
            (requireContext() as Activity).runOnUiThread {
                val t = Toast.makeText(requireContext(),
                    "Unknown QR-code ",
                    Toast.LENGTH_LONG)
                t.setGravity(Gravity.TOP,0,0);
                t.show();
            }
            return
        }

        val decodedBarcode = String(decodedBarcodeBytes, Charsets.UTF_8)
        Log.d("qr-config", decodedBarcode)

        // config format
        // timers in SECONDS (not milliseconds!)
        // ip/host;port;timer_short;timer_medium;timer_long;sumtimer^2
        val configValues = decodedBarcode.split(Pattern.compile(";"), 0)
        if (configValues.size != 6) {
            Log.d("qr-config", "not a config qr value")
            (requireContext() as Activity).runOnUiThread {
                val t = Toast.makeText(requireContext(),
                    "Incorrect QR-code ",
                    Toast.LENGTH_LONG)
                t.setGravity(Gravity.TOP,0,0);
                t.show();
            }
            return
        }

        var timerShort: UInt
        var timerMedium: UInt
        var timerLong: UInt
        var timerCVV: ULong

        try {
            timerShort = configValues[2].toUInt()
            timerMedium = configValues[3].toUInt()
            timerLong = configValues[4].toUInt()
            timerCVV = configValues[5].toULong()
        } catch (_: java.lang.NumberFormatException) {
            // ignore non number values
            Log.d("qr-config", "one or more of the timer values is not a number")
            (requireContext() as Activity).runOnUiThread {
                val t = Toast.makeText(requireContext(),
                    "Incorrect QR-code ",
                    Toast.LENGTH_LONG)
                t.setGravity(Gravity.TOP,0,0);
                t.show();
            }
            return
        }

        val cvvPartOne = (timerShort.toULong() + timerMedium.toULong() + timerLong.toULong())
        if ((cvvPartOne * cvvPartOne) != timerCVV) {
            // config verification value is not correct
            Log.d("qr-config", "incorrect cvv")
            (requireContext() as Activity).runOnUiThread {
                val t = Toast.makeText(requireContext(),
                    "Incorrect QR-code ",
                    Toast.LENGTH_LONG)
                t.setGravity(Gravity.TOP,0,0);
                t.show();
            }
            return
        }

        Config.BackendIpAddress = configValues[0]
        Config.BackendPort = configValues[1]
        Config.PassiveTimeoutShort = timerShort * 1000u
        Config.PassiveTimeoutMedium = timerMedium * 1000u
        Config.PassiveTimeoutLong = timerLong * 1000u

        Log.d("qr-config", "new config:\n" +
                "${Config.BackendIpAddress}\n" +
                "${Config.BackendPort}\n" +
                "${Config.PassiveTimeoutShort}\n" +
                "${Config.PassiveTimeoutMedium}\n" +
                "${Config.PassiveTimeoutLong}"
        )
        backend =
            Backend(requireContext(), "http://${Config.BackendIpAddress}:${Config.BackendPort}")

        (requireContext() as Activity).runOnUiThread {
            val t = Toast.makeText(requireContext(),
                "QR-code Successfully scanned:\n\n" +
                        "Host: ${Config.BackendIpAddress}\n" +
                        "Port: ${Config.BackendPort}\n" +
                        "T1: ${timerShort}\n" +
                        "T2: ${timerMedium}\n" +
                        "T3: ${timerLong}",
                Toast.LENGTH_LONG)
            t.setGravity(Gravity.TOP,0,0);
            t.show();
        }
        val prefsHelper = SharedPreferencesHelper(requireContext())
        prefsHelper.insertValue(decodedBarcode)

    }

    private fun statusCallback(message: String) {
        Log.d("barcode-status", message)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d("StandbyScreen", "onViewCreated")
        super.onViewCreated(view, savedInstanceState)
        ivStandby = view.findViewById(R.id.ivStandby) as ImageView
        ivStandby.setImageResource(R.drawable.ic_standby_screen)

        val log = getMemoryInfo()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                backend.log(log) {
                    Log.d("StandbyScreen:onViewCreated", "error backend.log: $it")
                }
            } catch (e: JSONException){
                Log.d("StandbyScreen:onViewCreated backend.log error", "$e")
            } catch (e: Exception) {
                Log.d("StandbyScreen:onViewCreated backend.log error ", "$e")
            }
        }


        // Get the FragmentManager
        val fragmentManager = requireActivity().supportFragmentManager

        // Clear all fragments from the back stack
        while (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStackImmediate()
        }

        val delay = 1000 // 1000 milliseconds == 1 second

        runnable = object : Runnable {
            override fun run() {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val resp = backend.pollLogin {
                            Log.d("StandbyScreen:onViewCreated runnable ", "error pollLogin API: $it")
                        }

                        if (resp != null) {
                            if (resp.length() != 0) {
                                if (resp.has("type")) {
                                    when (resp.getInt("type")) {
                                        0 -> {
                                            val accessToken = resp.getString("accessToken")
                                            val projects = if (resp.has("projects")) resp.getJSONArray("projects") else JSONArray()
//                                            val projects = if (resp.has("projects") && resp.getJSONObject("projects").length() > 0) {
//                                                resp.getJSONArray("projects")
//                                            } else {
//                                                JSONArray()
//                                            }
                                            val badge = resp.getString("badge")
                                            goToShoppingCart(accessToken, projects, badge)
                                        }
                                        1 -> println("is an approver")
                                        2 -> {
                                            val badge = resp.getString("badge")
                                            val accessToken = resp.getString("accessToken")
                                            val packingSlips = if (resp.has("packingslips")) resp.getJSONArray("packingslips") else JSONArray()
                                            goToFiller(badge, accessToken, packingSlips)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: JSONException) {
                        Log.d("StandbyScreen:onViewCreated runnable backend.pollLogin error ", "$e")
                    }  catch (e: Exception) {
                        Log.d("StandbyScreen:onViewCreated runnable backend.pollLogin error ", "$e")
                }
                }
                handler.postDelayed(this, delay.toLong())
            }
        }

        handler.postDelayed(runnable, delay.toLong())
        resetHealthTimer()
    }

    override fun onResume() {
        super.onResume()
        // Restart the timer when the fragment is resumed
        resetHealthTimer()
    }

    override fun onPause() {
        super.onPause()
        // Stop the timer when the fragment is paused
        stopHealthTimer()
    }

    private fun goToShoppingCart(accessToken: String, projects: JSONArray, badge: String) {

        val uuid = UUID.randomUUID()
        SessionManager.getInstance().setSessionId(uuid.toString())
        SessionManager.getInstance().setSessionState(true)
        cradle.unlock()
        handler.removeCallbacks(runnable)
        val projectsArrayList = ArrayList<Project>()
        for (i in 0 until projects.length()) {
            val projectJSON = projects.getJSONObject(i)
            projectsArrayList.add(Project(projectJSON.getInt("id"),
                projectJSON.getString("name"),
                projectJSON.getString("number"),
                projectJSON.getInt("start"),
                projectJSON.getInt("end")))
        }
        parentFragmentManager.beginTransaction().apply {
            barcodeScanner.onClosed()
            replace(R.id.flFragment,
                ProjectSelection(accessToken, backend, projectsArrayList, badge))
            addToBackStack("standby")
            commit()
        }
    }

    private fun goToFiller(badge: String, accessToken: String, packingSlipItems: JSONArray) {
        val uuid = UUID.randomUUID()
        SessionManager.getInstance().setSessionId(uuid.toString())
        SessionManager.getInstance().setSessionState(true)
        cradle.unlock()
        handler.removeCallbacks(runnable)
        Log.d("filler:unlocked", packingSlipItems.toString())
        if (packingSlipItems.length() > 0) {
            parentFragmentManager.beginTransaction().apply {
                barcodeScanner.onClosed()
                replace(R.id.flFragment,
                    PackingSlip(accessToken, backend, badge, packingSlipItems))
                addToBackStack("standby")
                commit()
            }
        } else {
            parentFragmentManager.beginTransaction().apply {
                barcodeScanner.onClosed()
                replace(R.id.flFragment,
                    Filler(badge, accessToken, backend, false))
                addToBackStack("standby")
                commit()
            }
        }
    }

    private fun getMemoryInfo(): String {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()

        val debugMemoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(debugMemoryInfo)

        val heapSize = debugMemoryInfo.totalPrivateDirty
        val nativeHeapSize = debugMemoryInfo.nativePrivateDirty

        return "Used Memory: $usedMemory bytes\n" +
                "Heap Size: $heapSize KB\n" +
                "Native Heap Size: $nativeHeapSize KB"
    }

    private fun resetHealthTimer() {
        // Cancel any pending healthRunnable and start a new timer
        healthHandler.removeCallbacks(healthRunnable)
        healthHandler.postDelayed(healthRunnable, inactivityTimeout)
    }

    private fun stopHealthTimer() {
        // Cancel the healthRunnable if it is still pending
        healthHandler.removeCallbacks(healthRunnable)
    }

    private fun callHealthFunction() {
        val memoryInfo = getMemoryInfo()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                backend.health(memoryInfo) {
                    Log.d("StandbyScreen:callHealthFunction", "error backend.health: $it")
                }
            } catch (e: JSONException) {
                Log.d("SStandbyScreen:callHealthFunction backend.health error ", "$e")
            } catch (e: Exception) {
                Log.d("StandbyScreen:callHealthFunction backend.health error ", "$e")
            }
        }


        // Restart the timer after the call is made
        resetHealthTimer()
    }

    class SharedPreferencesHelper(context: Context) {
        companion object {
            private const val PREFS_NAME = "qrconfig"
            private const val VALUE_KEY = "value"
        }

        private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun insertValue(value: String) {
            prefs.edit().putString(VALUE_KEY, value).apply()
        }

        fun getValue(): String? {
            return prefs.getString(VALUE_KEY, null)
        }
    }
}