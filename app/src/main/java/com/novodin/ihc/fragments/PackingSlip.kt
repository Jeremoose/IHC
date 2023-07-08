package com.novodin.ihc.fragments

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.novodin.ihc.R
import com.novodin.ihc.SessionManager
import com.novodin.ihc.adapters.ArticleRecyclerViewAdapter
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
    private var badge: String,
//    private val packingSlipItemList: ArrayList<PackingSlipItem>,
    private val packingSlipItems: JSONArray,
) :
    Fragment(R.layout.fragment_packing_slip) {
    private var intentFilter = IntentFilter()
    private val packingSlipItemList: MutableList<PackingSlipItem> = ArrayList()
    private var selectedItemPosition: Int = -1

    private lateinit var rvPackingSlipItemList: RecyclerView
    private var dialog: AlertDialog? = null

    private lateinit var ibNavThree: ImageButton
    private lateinit var textView5: TextView
    private lateinit var ibNavFour: ImageButton
    private lateinit var textView6: TextView

    // View color variables
    private var colorPrimaryEnabled = 0
    private var colorPrimaryDisabled = 0


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
//                    requireActivity().supportFragmentManager.popBackStack()
                    parentFragmentManager.popBackStack("standby",
                        FragmentManager.POP_BACK_STACK_INCLUSIVE)
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
//                    requireActivity().supportFragmentManager.popBackStack()
                    parentFragmentManager.popBackStack("standby",
                        FragmentManager.POP_BACK_STACK_INCLUSIVE)
                }
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        colorPrimaryDisabled =
            requireContext().getColor(com.google.android.material.R.color.material_on_primary_disabled)
        colorPrimaryEnabled =
            requireContext().getColor(com.google.android.material.R.color.material_on_primary_emphasis_high_type)

        for (i in 0 until packingSlipItems.length()) {
            val projectJSON = packingSlipItems.getJSONObject(i)
            packingSlipItemList.add(PackingSlipItem(projectJSON.getString("company_name"),
                projectJSON.getString("number")))
        }

        val builder = AlertDialog.Builder(requireContext())
        builder.setMessage("Use packing slips?")
            .setCancelable(false)
            .setPositiveButton("Yes") { _, _ ->
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
        (rvPackingSlipItemList.adapter as PackingSlipItemRecyclerViewAdapter).setOnItemSelectExtra {
            enableAddButton()
        }


        ibNavThree = view.findViewById(R.id.ibNavThree) as ImageButton
        ibNavFour = view.findViewById(R.id.ibNavFour) as ImageButton
        textView6 = view.findViewById(R.id.textView6) as TextView

        ibNavThree.setOnClickListener { stop() }
        ibNavFour.setOnClickListener { add() }

        initNavButtons(view)

    }

    override fun onDestroy() {
        super.onDestroy()
        val sessionId = SessionManager.getInstance().getSessionId()
        if (sessionId != null) {
            Log.d("PackingSlip:onDestroy sessionId = ", sessionId)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val sessionId = SessionManager.getInstance().getSessionId()
        if (sessionId != null) {
            Log.d("PackingSlip:onDestroyView sessionId = ", sessionId)
        }
    }


    private fun add() {
        val adapter = rvPackingSlipItemList.adapter as PackingSlipItemRecyclerViewAdapter
        val pos = adapter.selectedValuePosition
        Log.d("PackingslipSelection:add", "selectedPos=$pos")
        if (pos == -1) return // return if nothing is selected
        if (pos >= packingSlipItemList.size) {
            Log.d("PackingslipSelection:add packingSlipItemList.size = ", packingSlipItemList.size.toString())
            adapter.selectedValuePosition = -1
            return // return if pos is out of range
        }
        val packingSlipListItem = packingSlipItemList[pos]

        removeFromCradleTimeout.cancel()
        resetPassiveTimeout()

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
        (requireContext() as Activity).runOnUiThread {
            adapter.notifyItemRemoved(pos)
            rvPackingSlipItemList.scrollToPosition(0)
            adapter.selectedValuePosition = -1
        }

        disableAddButton()
    }

    private fun stop() {
        val builder = AlertDialog.Builder(requireContext())
        resetPassiveTimeout()
        builder.setMessage("Are you sure you want to stop?")
            .setCancelable(false)
            .setPositiveButton("Yes") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    passiveTimeout.cancel()
                    Log.d("ShoppingCart:debug_double-passivetimeout", "passivetimeout cancel - stop - approvalState true")
                    requireContext().unregisterReceiver(dockChangeReceiver)
                    backend.loginRelease(badge, accessToken)
                }
//                requireActivity().supportFragmentManager.popBackStack()
                parentFragmentManager.popBackStack("standby",
                    FragmentManager.POP_BACK_STACK_INCLUSIVE)
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
                resetPassiveTimeout()
            }
        dialog = builder.create()
        dialog!!.window?.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        dialog!!.show()
    }

    private fun enableAddButton() {
        Log.d("Packingslip::enableAddButton", "enter")
        Log.d("Packingslip::enableAddButton", ibNavFour.toString())

        val adapter = rvPackingSlipItemList.adapter as PackingSlipItemRecyclerViewAdapter
        val pos = adapter.selectedValuePosition
        Log.d("PackingslipSelection:enableAddButton", "selectedPos=$pos")
        if (pos != -1 && pos < packingSlipItemList.size) {
            ibNavFour.isEnabled = true
            ibNavFour.imageTintList = ColorStateList.valueOf(Color.WHITE)
            textView6.setTextColor(colorPrimaryEnabled)
        }
        resetPassiveTimeout()
    }

    private fun disableAddButton() {
        ibNavFour.isEnabled = false
        ibNavFour.imageTintList = ColorStateList.valueOf(colorPrimaryDisabled)
        textView6.setTextColor(colorPrimaryDisabled)
    }

    private fun initNavButtons(view: View) {
        // First nav button
        (view.findViewById(R.id.ibNavOne) as ImageButton).apply {
            this.setImageResource(R.drawable.ic_minus)
            this.isEnabled = false
        }

        // Second nav button
        (view.findViewById(R.id.ibNavTwo) as ImageButton).apply {
            this.setImageResource(R.drawable.ic_arrow_back)
            this.isEnabled = false
        }

        // Third nav button
        (view.findViewById(R.id.ibNavThree) as ImageButton).apply {
            this.setImageResource(R.drawable.ic_cancel)
            this.isEnabled = true
        }

        // Fourth nav button
        (view.findViewById(R.id.ibNavFour) as ImageButton).apply {
            this.setImageResource(R.drawable.ic_next)
            this.isEnabled = false
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
//                requireActivity().supportFragmentManager.popBackStack()
                parentFragmentManager.popBackStack("standby",
                    FragmentManager.POP_BACK_STACK_INCLUSIVE)
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
        passiveTimeout.cancel()
        passiveTimeout.start()
    }
}
