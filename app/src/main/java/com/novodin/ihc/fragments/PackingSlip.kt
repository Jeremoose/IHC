package com.novodin.ihc.fragments

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.novodin.ihc.R
import com.novodin.ihc.adapters.PackingSlipItemRecyclerViewAdapter
import com.novodin.ihc.config.Config
import com.novodin.ihc.model.Article
import com.novodin.ihc.model.PackingSlipItem
import com.novodin.ihc.network.Backend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray


class PackingSlip(
    private var accessToken: String,
    private var backend: Backend,
    private var badge: String? = null,
//    private val packingSlipItemList: ArrayList<PackingSlipItem>,
    private val packingSlipItems: JSONArray,
) :
    Fragment(R.layout.fragment_packing_slip) {
    private var intentFilter = IntentFilter()
    private val packingSlipItemList: MutableList<PackingSlipItem> = ArrayList()

    private lateinit var rvPackingSlipItemList: RecyclerView
    private var dialog: AlertDialog? = null

    private lateinit var ibAdd: ImageButton

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
                        Log.d("PackingslipSelection:debug_unregister_catch",
                            "removefromcradletimeout unregister error: $e")
                    }
                    requireActivity().supportFragmentManager.popBackStack()
                }
            }
        // setup passive user timeout
        passiveTimeout =
            object : CountDownTimer(Config.PassiveTimeoutMedium.toLong(), Config.PassiveTimeoutMedium.toLong()) {
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
                    Log.d("PackingslipSelection:debug_unregister_fatal", "passivetimeout unregister")
                    try {
                        requireContext().unregisterReceiver(dockChangeReceiver)

                    } catch (e: IllegalArgumentException) {
                        Log.d("PackingslipSelection:debug_unregister_catch",
                            "passivetimeout unregister error: $e")
                    }
                    requireActivity().supportFragmentManager.popBackStack()
                }
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        for (i in 0 until packingSlipItems.length()) {
            val projectJSON = packingSlipItems.getJSONObject(i)
            packingSlipItemList.add(PackingSlipItem(projectJSON.getString("company_name"),
                projectJSON.getString("number")))
        }

        val builder = AlertDialog.Builder(requireContext())
        builder.setMessage("Use packing slips?")
            .setCancelable(false)
            .setPositiveButton("Yes") { _, _ ->
//                val packingSlipItemArrayList = ArrayList<PackingSlipItem>()
//                for (i in 0 until packingSlipItems.length()) {
//                    val projectJSON = packingSlipItems.getJSONObject(i)
//                    packingSlipItemList.add(PackingSlipItem(projectJSON.getString("company_name"),
//                        projectJSON.getString("number")))
//                }
//                goToPackingSlipFragment(accessToken, badge, packingSlipItemArrayList)
                dialog?.dismiss()
            }
            .setNegativeButton("No") { _, _ ->
                parentFragmentManager.beginTransaction().apply {
                    removeFromCradleTimeout.cancel()
                    passiveTimeout.cancel()
                    Log.d("PackingslipSelection:passiveTimeout", "passivetimeout cancel - ibNavFour.setOnClickListener")
                    requireContext().unregisterReceiver(dockChangeReceiver)
                    replace(R.id.flFragment,
                        Filler(badge!!,
                            accessToken,
                            backend, true))
                    commit()
                }
//                goToFillerFragment(badge, accessToken)
            }
        dialog = builder.create()
        dialog!!.window?.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        dialog!!.show()

        removeFromCradleTimeout.start()

        rvPackingSlipItemList = view.findViewById(R.id.rvPackingSlip) as RecyclerView
        rvPackingSlipItemList.adapter = PackingSlipItemRecyclerViewAdapter(packingSlipItemList)

        ibAdd = view.findViewById(R.id.ibNavFour) as ImageButton

        ibAdd.setOnClickListener {
            val adapter = rvPackingSlipItemList.adapter as PackingSlipItemRecyclerViewAdapter
            val pos = adapter.selectedValuePosition
            if (pos == -1) return@setOnClickListener // return if nothing is selected

            val packingSlipListItem = packingSlipItemList[pos]

            removeFromCradleTimeout.cancel()
            passiveTimeout.cancel()

            // add item in backend
            CoroutineScope(Dispatchers.IO).launch {
                backend.packingSlip(packingSlipListItem.number, accessToken)
                (requireContext() as Activity).runOnUiThread {
                    Toast.makeText(requireContext(),
                        "Filled stock from packing slip ${packingSlipListItem.number}",
                        Toast.LENGTH_LONG)
                        .show()
                }
            }
            packingSlipItemList.removeAt(pos)

            adapter.notifyItemChanged(pos)

        }

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
                Log.d("ProjectSelection:passiveTimeout", "passivetimeout cancel - dockChangeReceiver")

                try {
                    requireContext().unregisterReceiver(this)

                } catch (e: IllegalArgumentException) {
                    Log.d("ProjectSelection:debug_unregister_catch",
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
