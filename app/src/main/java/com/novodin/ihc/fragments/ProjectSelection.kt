package com.novodin.ihc.fragments

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.widget.Spinner
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import com.novodin.ihc.R
import com.novodin.ihc.config.Config
import com.novodin.ihc.model.Project
import com.novodin.ihc.network.Backend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject


class ProjectSelection(
    private var accessToken: String,
    private val backend: Backend,
    private var projects: ArrayList<Project>? = null,
    private var badge: String? = null,
) : Fragment(R.layout.fragment_project_selection) {
    private var intentFilter = IntentFilter()

    // View color variables
    private var colorPrimaryEnabled = 0
    private var colorPrimaryDisabled = 0

    // Project selection variables
    private var selectedProject: Project? = null

    // View elements
    private lateinit var etBadgeNumber: EditText
    private lateinit var sProjects: Spinner
    private lateinit var tvLabelProjects: TextView
    private lateinit var ibNavFour: ImageButton
    private lateinit var tvNavFour: TextView

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
                    Log.d("ProjectSelection:debug_unregister_fatal",
                        "removefromcradletimeout unregister")
                    try {
                        requireContext().unregisterReceiver(dockChangeReceiver)

                    } catch (e: IllegalArgumentException) {
                        Log.d("ProjectSelection:debug_unregister_catch",
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
                    Log.d("ProjectSelection:debug_unregister_fatal", "passivetimeout unregister")
                    try {
                        requireContext().unregisterReceiver(dockChangeReceiver)

                    } catch (e: IllegalArgumentException) {
                        Log.d("ProjectSelection:debug_unregister_catch",
                            "passivetimeout unregister error: $e")
                    }
                    requireActivity().supportFragmentManager.popBackStack()
                }
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Start timeouts
        removeFromCradleTimeout.start()
//        passiveTimeout.start()
        // reset timer when user clicks anywhere in screen
        view.setOnClickListener {
            resetPassiveTimeout()
        }

        colorPrimaryDisabled =
            requireContext().getColor(com.google.android.material.R.color.material_on_primary_disabled)
        colorPrimaryEnabled =
            requireContext().getColor(com.google.android.material.R.color.material_on_primary_emphasis_high_type)

        etBadgeNumber = view.findViewById(R.id.etBadgeNumber) as EditText
        sProjects = view.findViewById(R.id.sProjects) as Spinner
        tvLabelProjects = view.findViewById(R.id.tvLabelProjects) as TextView

        ibNavFour = view.findViewById(R.id.ibNavFour) as ImageButton
        tvNavFour = view.findViewById(R.id.tvNavFour) as TextView

        initNavButtons(view)

        initSpinnerProjects()

        if (badge != null) {
            etBadgeNumber.setText(badge!!)
        }

        if (projects != null) {
            val projectNamesArray: Array<String> = Array(projects!!.size) { "" }
            for ((i, project) in projects!!.withIndex()) {
                projectNamesArray[i] = project.name
            }
            sProjects.adapter = ArrayAdapter(requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                projectNamesArray)
        } else {
            sProjects.adapter = ArrayAdapter<String>(requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                arrayOf("No projects retrieved yet..."))
        }

        // onSubmit is an extended function on the EditText of the badge number
        // the onSubmit when the user is done with the on screen keyboard
        etBadgeNumber.onSubmit {
            CoroutineScope(Dispatchers.IO).launch {
                val loginResponse = backend.login(etBadgeNumber.text.toString()) {
                    Toast.makeText(requireContext(), "Unknown badge number", Toast.LENGTH_LONG)
                        .show()
                }

                accessToken = loginResponse!!.getString("accessToken")

                val type = loginResponse.getInt("type")
                // type 2 is filler
                if (type == 2) {
                    // go to filler
                    parentFragmentManager.beginTransaction().apply {
                        removeFromCradleTimeout.cancel()
                        passiveTimeout.cancel()
                        requireContext().unregisterReceiver(dockChangeReceiver)
                        replace(R.id.flFragment,
                            Filler(badge!!, accessToken, backend))
                        commit()
                    }
                    return@launch
                }

                val jsonProjects = backend.getProjects(accessToken)
                // empty projects property in case it was already filled
                projects!!.clear()

                for (i in 0 until jsonProjects!!.length()) {
                    val jsonProject: JSONObject = jsonProjects.getJSONObject(i)
                    projects!!.add(Project(
                        jsonProject.getInt("id"),
                        jsonProject.getString("number"), // todo switch name and number, right now done because of backend issue
                        jsonProject.getString("name"),
                        jsonProject.getInt("start"),
                        jsonProject.getInt("end")
                    ))
                }

                val projectNamesArray: Array<String> = Array(projects!!.size) { "" }
                for ((i, project) in projects!!.withIndex()) {
                    projectNamesArray[i] = project.name
                }

                val arrayAdapter: ArrayAdapter<String> = ArrayAdapter(requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    projectNamesArray)

                (requireContext() as Activity).runOnUiThread {
                    sProjects.adapter = arrayAdapter
                    sProjects.visibility = View.VISIBLE
                    tvLabelProjects.visibility = View.VISIBLE
                }
            }
        }

        // next button set onclick listener
        ibNavFour.setOnClickListener {
            selectedProject?.let { project ->
                val builder = AlertDialog.Builder(requireContext())
                builder.setMessage("Is this the correct project?")
                    .setCancelable(false)
                    .setPositiveButton("Yes") { _, _ ->
                        parentFragmentManager.beginTransaction().apply {
                            removeFromCradleTimeout.cancel()
                            passiveTimeout.cancel()
                            requireContext().unregisterReceiver(dockChangeReceiver)
                            replace(R.id.flFragment,
                                ShoppingCart(badge!!,
                                    project.id,
                                    accessToken,
                                    backend))
                            commit()
                        }
                    }
                    .setNegativeButton("No") { dialog, _ ->
                        dialog.dismiss()
                    }
                val dialog = builder.create()
                dialog.window?.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                dialog.show()
            }
        }
    }

    private fun enableNextButton() {
        ibNavFour.isEnabled = true
        tvNavFour.setTextColor(colorPrimaryEnabled)
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

        // Third nav button
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

    private fun initSpinnerProjects() {
        sProjects.avoidDropdownFocus()
        if (projects == null) {
            sProjects.visibility = View.GONE
            tvLabelProjects.visibility = View.GONE
        }
        sProjects.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                resetPassiveTimeout()
                if (position >= projects!!.size)
                    Log.e("err", "position bigger than size of projects")
                else {
                    selectedProject = projects!![position]
                    if (!ibNavFour.isEnabled) {
                        enableNextButton()
                    }
                }
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

    private fun Spinner.avoidDropdownFocus() {
        try {
            val isAppCompat = this is androidx.appcompat.widget.AppCompatSpinner
            val spinnerClass =
                if (isAppCompat) androidx.appcompat.widget.AppCompatSpinner::class.java else Spinner::class.java
            val popupWindowClass =
                if (isAppCompat) androidx.appcompat.widget.ListPopupWindow::class.java else android.widget.ListPopupWindow::class.java

            val listPopup = spinnerClass
                .getDeclaredField("mPopup")
                .apply { isAccessible = true }
                .get(this)
            if (popupWindowClass.isInstance(listPopup)) {
                val popup = popupWindowClass
                    .getDeclaredField("mPopup")
                    .apply { isAccessible = true }
                    .get(listPopup)
                if (popup is PopupWindow) {
                    popup.isFocusable = false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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

    private fun resetPassiveTimeout() {
        passiveTimeout.cancel()
        Log.d("ProjectSelection:debug_double-passivetimeout",
            "reset passive timeout")
        passiveTimeout.start()
    }
}