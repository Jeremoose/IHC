package com.novodin.ihc.fragments

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.novodin.ihc.R
import com.novodin.ihc.config.Config
import com.novodin.ihc.model.PackingSlipItem
import com.novodin.ihc.model.Project
import com.novodin.ihc.network.Backend
import com.novodin.ihc.zebra.Cradle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException


class StandbyScreen() : Fragment(R.layout.fragment_standby_screen) {
//    private var intentFilter = IntentFilter()

    // View elements
    private lateinit var ivStandby: ImageView
    private lateinit var bSetIP: Button
    private lateinit var tvIP: TextView

    // general novodin scanner app vars
    private lateinit var backend: Backend
    private lateinit var cradle: Cradle

    // Polling variables
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED)

        cradle = Cradle(requireContext())
        backend =
            Backend(requireContext(), "http://${Config.BackendIpAddress}:${Config.BackendPort}")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ivStandby = view.findViewById(R.id.ivStandby) as ImageView
        ivStandby.setImageResource(R.drawable.ic_standby_screen)

        tvIP = view.findViewById(R.id.tvIP)
        tvIP.text = Config.BackendIpAddress

        bSetIP = view.findViewById(R.id.bSetIP) as Button
        bSetIP.setOnClickListener {
            val builder = AlertDialog.Builder(requireContext())
            builder.setTitle("Set IP")

            // Set up the input

            // Set up the input
            val input = EditText(requireContext())
            // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
            input.inputType = InputType.TYPE_CLASS_TEXT
            builder.setView(input)

            // Set up the buttons

            // Set up the buttons
            builder.setPositiveButton("OK"
            ) { _, _ ->
                Config.BackendIpAddress = input.text.toString()
                backend = Backend(requireContext(),
                    "http://${Config.BackendIpAddress}:${Config.BackendPort}")
                (requireContext() as Activity).runOnUiThread {
                    tvIP.text = Config.BackendIpAddress
                }
            }
            builder.setNegativeButton("Cancel"
            ) { dialog, _ -> dialog.cancel() }

            builder.show()

//            val alert = builder.create()
//            alert.window?.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
//                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
//            alert.show()
        }

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
            replace(R.id.flFragment,
                ProjectSelection(accessToken, backend, projectsArrayList, badge))
            addToBackStack("standby")
            commit()
        }
    }

    private fun goToFiller(badge: String, accessToken: String, packingSlipItems: JSONArray) {
        cradle.unlock()
        handler.removeCallbacks(runnable)
        if (packingSlipItems.length() > 0) {
            val builder = AlertDialog.Builder(requireContext())
            builder.setMessage("Use packing slips?")
                .setCancelable(false)
                .setPositiveButton("Yes") { _, _ ->
                    val packingSlipItemArrayList = ArrayList<PackingSlipItem>()
                    for (i in 0 until packingSlipItems.length()) {
                        val projectJSON = packingSlipItems.getJSONObject(i)
                        packingSlipItemArrayList.add(PackingSlipItem(projectJSON.getString("company_name"),
                            projectJSON.getString("number")))
                    }
                    goToPackingSlipFragment(accessToken, packingSlipItemArrayList)
                }
                .setNegativeButton("No") { _, _ ->
                    goToFillerFragment(badge, accessToken)
                }
            (requireContext() as Activity).runOnUiThread {
                val alert = builder.create()
                alert.window?.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                alert.show()
            }
        } else {
            goToFillerFragment(badge, accessToken)
        }
    }

    private fun goToPackingSlipFragment(
        accessToken: String,
        packingSlipItemArrayList: ArrayList<PackingSlipItem>,
    ) {
        lifecycleScope.launchWhenResumed {
            parentFragmentManager.beginTransaction().apply {
                replace(R.id.flFragment,
                    PackingSlip(accessToken, backend, packingSlipItemArrayList))
                addToBackStack("standby")
                commit()
            }
        }
    }

    private fun goToFillerFragment(badge: String, accessToken: String) {
        lifecycleScope.launchWhenResumed {
            parentFragmentManager.beginTransaction().apply {
                replace(R.id.flFragment,
                    Filler(badge, accessToken, backend))
                addToBackStack("standby")
                commit()
            }
        }
    }

    //not needed here
//    private val batteryChangeReceiver: BroadcastReceiver = object : BroadcastReceiver() {
//        override fun onReceive(context: Context?, intent: Intent) {
//            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
//            if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
//                Toast.makeText(requireContext(), "IN CRADLE", Toast.LENGTH_SHORT).show()
//            }
//            if (status == BatteryManager.BATTERY_STATUS_DISCHARGING) {
//                Toast.makeText(requireContext(), "REMOVED FROM CRADLE", Toast.LENGTH_SHORT)
//                    .show()
//            }
//        }
//    }
}