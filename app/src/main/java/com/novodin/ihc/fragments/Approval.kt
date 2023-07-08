package com.novodin.ihc.fragments

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
import androidx.fragment.app.setFragmentResult
import com.android.volley.NoConnectionError
import com.android.volley.TimeoutError
import com.novodin.ihc.R
import com.novodin.ihc.SessionManager
import com.novodin.ihc.config.Config
import com.novodin.ihc.model.Article
import com.novodin.ihc.network.Backend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException

class Approval(
    private var backend: Backend,
    private var cartId: Int,
    private val articleList: MutableList<Article> = ArrayList(),
) : Fragment(R.layout.fragment_project_selection) {
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
    private var userType: Int = 0
    private var approvalState: Boolean = false
    private var badge: String = ""

    // Timer variables
    private lateinit var passiveTimeout: CountDownTimer

    // Polling variables
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("Approval", "fun onCreate")
        super.onCreate(savedInstanceState)
        intentFilter.addAction("com.symbol.intent.device.DOCKED")
        requireContext().registerReceiver(dockChangeReceiver, intentFilter)
        passiveTimeout =
            object : CountDownTimer(Config.PassiveTimeoutShort.toLong(), Config.PassiveTimeoutShort.toLong()) {
                override fun onTick(p0: Long) {}
                override fun onFinish() {
                    Log.d("Approval:onCreate:passiveTimeout:onFinish", "approvalState=$approvalState")
                    val sessionActive = SessionManager.getInstance().getSessionState()
                    if (sessionActive != null && sessionActive) {
                        SessionManager.getInstance().setSessionState(false)
                        if (approvalState) {
                            CoroutineScope(Dispatchers.IO).launch {
                                Log.d("Approval:onCreate:passiveTimeout:onFinish backend.approve ", "cartId=$cartId, accessToken=$accessToken")
                                backend.approve(cartId, accessToken, JSONArray(articleList))
                            }
                            Toast.makeText(requireContext(),
                                "Successfully approved",
                                Toast.LENGTH_LONG)
                                .show()
                            // release the user
                            CoroutineScope(Dispatchers.IO).launch {
                                backend.loginRelease(badge!!, accessToken)
                            }
                        }

                        try {
                            requireContext().unregisterReceiver(dockChangeReceiver)
                        } catch (e: IllegalArgumentException) {
                            Log.d("Approval:debug_unregister_catch",
                                "passivetimeout unregister error: $e")
                        }
                        runnable?.let { handler.removeCallbacks(it) }

                        if (approvalState) {
//                            requireActivity().supportFragmentManager.popBackStack("standby",
//                                POP_BACK_STACK_INCLUSIVE)
                            parentFragmentManager.popBackStack("standby",
                                POP_BACK_STACK_INCLUSIVE)
                        } else {
//                            requireActivity().supportFragmentManager.popBackStack()
                            parentFragmentManager.popBackStack()
                        }
                    }
                }
            }
        Log.d("Approval:onCreate", "start passivetimeout")
        passiveTimeout.start()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Start timeout
//        passiveTimeout.start()
//        Log.d("Approval:passiveTimeout", "passivetimeout start - onViewCreated")
        // reset timer when user clicks anywhere in screen
//        requireView().setOnClickListener {
//            passiveTimeout.cancel()
//            passiveTimeout.start()
//            Log.d("Approval:passiveTimeout", "passivetimeout cancel / start - onViewCreated - setOnclickListener")
//        }

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
                        when (it) {
                            is NoConnectionError -> {
                                Log.d("api-error:pollAprover", "NoConnectionError")
                                // Handle no connection error
                            }
                            is TimeoutError   -> {
                                Log.d("api-error:pollAprover", "TimeoutError")
                                // Handle timeout error
                            }
                            else -> {
                                Log.d("api-error:pollAprover", "Unknown error")
                            }
                        }
                    }

                    try {
                        if (resp!!.getInt("type") == 1) {
                            (requireContext() as Activity).runOnUiThread {
                                badge = resp.getString("badge")
                                approvalState = true
                                etBadgeNumber.setText(badge)
                                ibNavFour.isEnabled = true
                                tvNavFour.setTextColor(colorPrimaryEnabled)

                                CoroutineScope(Dispatchers.IO).launch {
                                    val loginResponse = backend.login(badge) {
                                        Toast.makeText(requireContext(), "Unknown badge number", Toast.LENGTH_LONG)
                                            .show()
                                    }
                                    Log.d("Approval:onViewCreated loginResponse = ", loginResponse.toString())
                                    accessToken = loginResponse!!.getString("accessToken")
                                    userType = loginResponse!!.getInt("type")
                                    resetPassiveTimeout()
//                                    Log.d("Approval:onViewCreated", "cancel passiveTimeout" )
//                                    passiveTimeout.cancel()
//                                    setFragmentResult("approveLogin",
//                                        bundleOf(Pair("success", userType == 1),
//                                            Pair("accessToken", if (userType == 1) accessToken else "")))
//                                    requireContext().unregisterReceiver(dockChangeReceiver)
//                                    requireActivity().supportFragmentManager.popBackStack()
                                }

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
//                val loginResponse = backend.login(etBadgeNumber.text.toString()) {
//                    Toast.makeText(requireContext(), "Unknown badge number", Toast.LENGTH_LONG)
//                        .show()
//                }
//                Log.d("Approval:onViewCreated:ibNavFour.setOnClickListener loginResponse = ", loginResponse.toString())
//                accessToken = loginResponse!!.getString("accessToken")
//                val userType = loginResponse!!.getInt("type")
                passiveTimeout.cancel()
                Log.d("Approval:onViewCreated:ibNavFour.setOnClickListener", "cancel passivetimeout")
//                setFragmentResult("approveLogin",
//                    bundleOf(Pair("success", userType == 1),
//                        Pair("accessToken", if (userType == 1) accessToken else "")))
                setFragmentResult("approveLogin",
                    bundleOf(Pair("success", userType == 1),
                        Pair("accessToken", if (userType == 1) accessToken else ""),
                        Pair("badge", if (userType == 1) badge else "")))


                handler.removeCallbacks(runnable)
                requireContext().unregisterReceiver(dockChangeReceiver)
//                requireActivity().supportFragmentManager.popBackStack()

                val log =  "confirm approval badge: $badge, accessToken: $accessToken"
                backend.log(log) {
                    Log.d("Approval:ibNavFour.setOnClickListener backend.log", "error: $it")
                }

                parentFragmentManager.popBackStack()
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


                passiveTimeout.cancel()
                Log.d("Approval:onViewCreated:etBadgeNumber.onSubmit", "cancel passivetimeout")
                requireContext().unregisterReceiver(dockChangeReceiver)
//                requireActivity().supportFragmentManager.popBackStack()
                parentFragmentManager.popBackStack()
                setFragmentResult("approveLogin",
                    bundleOf(Pair("success", userType == 1),
                        Pair("accessToken", if (userType == 1) accessToken else ""),
                        Pair("badge", if (userType == 1) etBadgeNumber.text.toString() else "")))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val sessionId = SessionManager.getInstance().getSessionId()
        if (sessionId != null) {
            Log.d("Approval:onDestroy sessionId = ", sessionId)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val sessionId = SessionManager.getInstance().getSessionId()
        if (sessionId != null) {
            Log.d("Approval:onDestroyView sessionId = ", sessionId)
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

    private val dockChangeReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            if (intent.action == "com.symbol.intent.device.DOCKED") {
                // 1. release user
                // 2. stop cradletimeout
                // 3. stop passive timeout
                // 4. unregister receiver
                // 5. pop backstack
                Log.d("Approval:dockChangeReceiver", "approvalState=$approvalState")
                SessionManager.getInstance().setSessionState(false)
                Log.d("Approval:dockChangeReceiver", "cancel passivetimeout")
                passiveTimeout.cancel()
                if (approvalState) {
                    approvalState = false
                    CoroutineScope(Dispatchers.IO).launch {
                        Log.d("Approval:dockChangeReceiver backend.approve ", "cartId=$cartId, accessToken=$accessToken")
                        backend.approve(cartId, accessToken, JSONArray(articleList))
                    }
                    Toast.makeText(requireContext(),
                        "Successfully approved",
                        Toast.LENGTH_LONG)
                        .show()
                }
                badge?.let {
                    CoroutineScope(Dispatchers.IO).launch {
                        Log.d("Approval:dockChangeReceiver loginRelease badge = ", badge)
                        backend.loginRelease(badge!!, accessToken)
                    }
                }


                try {
                    requireContext().unregisterReceiver(this)

                } catch (e: IllegalArgumentException) {
                    Log.d("ProjectSelection:debug_unregister_catch",
                        "battery_status_charging and unregistering receiver here error: $e")
                }
                handler.removeCallbacks(runnable)
//                requireActivity().supportFragmentManager.popBackStack("standby", POP_BACK_STACK_INCLUSIVE)
                parentFragmentManager.popBackStack("standby", POP_BACK_STACK_INCLUSIVE)
            }
        }
    }

    private fun resetPassiveTimeout() {
        Log.d("Approval:passiveTimeout", "passivetimeout resetPassiveTimeout()")
        passiveTimeout.cancel()
        passiveTimeout.start()
    }
}


