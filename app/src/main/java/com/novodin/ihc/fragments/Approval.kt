package com.novodin.ihc.fragments

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
import androidx.fragment.app.setFragmentResult
import com.novodin.ihc.R
import com.novodin.ihc.config.Config
import com.novodin.ihc.network.Backend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONException

class Approval(private var backend: Backend) : Fragment(R.layout.fragment_project_selection) {
    private var intentFilter = IntentFilter()

    // View color variables
    private var colorPrimaryEnabled = 0
    private var colorPrimaryDisabled = 0

    // View elements
    private lateinit var etBadgeNumber: EditText
    private lateinit var ibNavFour: ImageButton
    private lateinit var tvNavFour: TextView

    // Approval variables
    private var accessToken: String = ""

    // Timer variables
    private lateinit var passiveTimeout: CountDownTimer

    // Polling variables
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED)
        requireContext().registerReceiver(batteryChangeReceiver, intentFilter)
        passiveTimeout =
            object : CountDownTimer(Config.PassiveTimeoutShort, Config.PassiveTimeoutShort) {
                override fun onTick(p0: Long) {}
                override fun onFinish() {
                    // release the user if the user has already been identified
                    requireContext().unregisterReceiver(batteryChangeReceiver)
                    runnable?.let { handler.removeCallbacks(it) }
                    requireActivity().supportFragmentManager.popBackStack()
                }
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Start timeout
        passiveTimeout.start()
        // reset timer when user clicks anywhere in screen
        requireView().setOnClickListener {
            passiveTimeout.cancel()
            passiveTimeout.start()
        }

        colorPrimaryDisabled =
            requireContext().getColor(com.google.android.material.R.color.material_on_primary_disabled)
        colorPrimaryEnabled =
            requireContext().getColor(com.google.android.material.R.color.material_on_primary_emphasis_high_type)

        initNavButtons(view)

        etBadgeNumber = view.findViewById(R.id.etBadgeNumber)
        val sProjects = view.findViewById<Spinner>(R.id.sProjects)
        sProjects.visibility = View.GONE
        val tvLabelProjects = view.findViewById<TextView>(R.id.tvLabelProjects)
        tvLabelProjects.visibility = View.GONE

        ibNavFour = view.findViewById(R.id.ibNavFour) as ImageButton
        tvNavFour = view.findViewById(R.id.tvNavFour) as TextView

        val delay = 1000
        runnable = object : Runnable {
            override fun run() {
                CoroutineScope(Dispatchers.IO).launch {
                    val resp = backend.pollApprover {
                        println("poll error")
                        println(it)
                    }
                    println("approver poll result")
                    try {
                        if (resp!!.getInt("type") == 1) {
                            (requireContext() as Activity).runOnUiThread {
                                etBadgeNumber.setText(resp.getString("badge"))
                                ibNavFour.isEnabled = true
                                tvNavFour.setTextColor(colorPrimaryEnabled)
                            }
                            handler.removeCallbacks(runnable)
                        }
                    } catch (e: JSONException) {
                    }
                }
                handler.postDelayed(this, delay.toLong())
            }
        }

        handler.postDelayed(runnable, delay.toLong())

        ibNavFour.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                val loginResponse = backend.login(etBadgeNumber.text.toString()) {
                    Toast.makeText(requireContext(), "Unknown badge number", Toast.LENGTH_LONG)
                        .show()
                }
                accessToken = loginResponse!!.getString("accessToken")
                val userType = loginResponse!!.getInt("type")

                setFragmentResult("approveLogin",
                    bundleOf(Pair("success", userType == 1),
                        Pair("accessToken", if (userType == 1) accessToken else "")))
                passiveTimeout.cancel()
                handler.removeCallbacks(runnable)
                requireContext().unregisterReceiver(batteryChangeReceiver)
                requireActivity().supportFragmentManager.popBackStack()
            }
        }

        etBadgeNumber.onSubmit {
            CoroutineScope(Dispatchers.IO).launch {
                val loginResponse = backend.login(etBadgeNumber.text.toString()) {
                    Toast.makeText(requireContext(), "Unknown badge number", Toast.LENGTH_LONG)
                        .show()
                }
                accessToken = loginResponse!!.getString("accessToken")
                val userType = loginResponse!!.getInt("type")

                setFragmentResult("approveLogin",
                    bundleOf(Pair("success", userType == 1),
                        Pair("accessToken", if (userType == 1) accessToken else "")))
                passiveTimeout.cancel()
                requireContext().unregisterReceiver(batteryChangeReceiver)
                requireActivity().supportFragmentManager.popBackStack()


            }
        }
    }

    private fun initNavButtons(view: View) {
        // First nav button
        (view.findViewById(R.id.ibNavOne) as ImageButton).apply {
            this.setImageResource(R.drawable.ic_minus)
            this.isEnabled = false
        }
        (view.findViewById(R.id.tvNavOne) as TextView).apply {
            this.text = "Remove"
            this.setTextColor(colorPrimaryDisabled)
        }

        // Second nav button
        (view.findViewById(R.id.ibNavTwo) as ImageButton).apply {
            this.setImageResource(R.drawable.ic_arrow_back)
            this.isEnabled = false
        }
        (view.findViewById(R.id.tvNavTwo) as TextView).apply {
            this.text = "Back"
            this.setTextColor(colorPrimaryDisabled)
        }

        // Third nav button (always enabled)
        (view.findViewById(R.id.ibNavThree) as ImageButton).apply {
            this.setImageResource(R.drawable.ic_cancel)
            this.isEnabled = false
        }
        (view.findViewById(R.id.tvNavThree) as TextView).apply {
            this.text = "Stop"
            this.setTextColor(colorPrimaryDisabled)
        }

        // Fourth nav button
        (view.findViewById(R.id.ibNavFour) as ImageButton).apply {
            this.setImageResource(R.drawable.ic_next)
            this.isEnabled = false
        }
        (view.findViewById(R.id.tvNavFour) as TextView).apply {
            this.text = "Next"
            this.setTextColor(colorPrimaryDisabled)
        }
    }

    private fun EditText.onSubmit(func: () -> Unit) {
        setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                clearFocus() // if needed
                hideKeyboard()
                func()
            }
            true
        }
    }

    private fun EditText.hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(this.windowToken, 0)
    }

    private val batteryChangeReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                handler.removeCallbacks(runnable)
                passiveTimeout.cancel()
                requireContext().unregisterReceiver(this)
                requireActivity().supportFragmentManager.popBackStack("standby", POP_BACK_STACK_INCLUSIVE)
            }
        }

    }
}
