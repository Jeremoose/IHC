package com.novodin.ihc.fragments

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.novodin.ihc.R
import com.novodin.ihc.config.Config
import com.novodin.ihc.model.PackingSlipItem
import com.novodin.ihc.model.Project
import com.novodin.ihc.network.Backend
import com.novodin.ihc.zebra.BarcodeScanner
import com.novodin.ihc.zebra.Cradle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import java.util.regex.Pattern
import android.content.SharedPreferences
import android.content.Context

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


    override fun onCreate(savedInstanceState: Bundle?) {
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
        super.onViewCreated(view, savedInstanceState)
        ivStandby = view.findViewById(R.id.ivStandby) as ImageView
        ivStandby.setImageResource(R.drawable.ic_standby_screen)

        val delay = 1000 // 1000 milliseconds == 1 second

        runnable = object : Runnable {
            override fun run() {
                CoroutineScope(Dispatchers.IO).launch {
                    val resp = backend.pollLogin {
                        println("poll error")
                        println(it)
//                        Toast.makeText(requireContext(), "Something went wrong", Toast.LENGTH_LONG)
//                            .show()
                    }
                    println("poll result")
                    try {
                        when (resp!!.getInt("type")) {
                            0 -> goToShoppingCart(resp.getString("accessToken"),
                                resp.getJSONArray("projects"),
                                resp.getString("badge"))
                            1 -> println("is an approver")
                            2 -> goToFiller(resp.getString("badge"),
                                resp.getString("accessToken"),
                                resp.getJSONArray("packingslips"))
                        }
                    } catch (e: JSONException) {
                    }
                }
                handler.postDelayed(this, delay.toLong())
            }
        }

        handler.postDelayed(runnable, delay.toLong())
    }

    private fun goToShoppingCart(accessToken: String, projects: JSONArray, badge: String) {
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
                    Filler(badge, accessToken, backend))
                addToBackStack("standby")
                commit()
            }
        }
    }

//    private fun goToFillerFragment(badge: String, accessToken: String) {
//        lifecycleScope.launchWhenResumed {
//            parentFragmentManager.beginTransaction().apply {
//                barcodeScanner.onClosed()
//                replace(R.id.flFragment,
//                    Filler(badge, accessToken, backend))
//                addToBackStack("standby")
//                commit()
//            }
//        }
//    }

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