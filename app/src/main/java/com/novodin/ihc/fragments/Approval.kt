package com.novodin.ihc.fragments

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.novodin.ihc.R
import com.novodin.ihc.network.Backend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONException


class Approval(private var backend: Backend) : Fragment(R.layout.fragment_project_selection) {

    // View color variables
    private var colorPrimaryEnabled = 0
    private var colorPrimaryDisabled = 0

    private lateinit var etBadgeNumber: EditText
    private lateinit var ibNavFour: ImageButton
    private lateinit var tvNavFour: TextView

    private var accessToken: String = ""

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
                requireActivity().supportFragmentManager.popBackStack()


            }
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
}