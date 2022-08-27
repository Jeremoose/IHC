package com.novodin.ihc.fragments

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.fragment.app.Fragment
import com.novodin.ihc.R
import com.novodin.ihc.model.Project
import com.novodin.ihc.network.Backend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject


class ProjectSelection() : Fragment(R.layout.fragment_project_selection) {

    private lateinit var etBadgeNumber: EditText
    private lateinit var sProjects: Spinner
    private lateinit var ibAdd: ImageButton

    private lateinit var backend: Backend
    private var accessToken: String = ""
    private val projects: ArrayList<Project> = ArrayList()
    private var selectedProject: Project? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backend = Backend(requireContext(), "http://34.68.16.30:3001")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

//        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        etBadgeNumber = view.findViewById(R.id.etBadgeNumber)
        sProjects = view.findViewById(R.id.sProjects)
        ibAdd = view.findViewById(R.id.ibAdd)

        sProjects.adapter = ArrayAdapter<String>(requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            arrayOf("No projects retrieved yet..."))

        sProjects.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                if (position >= projects.size)
                    Log.e("err", "position bigger than size of projects")
                else
                    selectedProject = projects[position]
            }

        }

        etBadgeNumber.onSubmit {
            CoroutineScope(Dispatchers.IO).launch {
                val loginResponse = backend.login(etBadgeNumber.text.toString()) {
                    Toast.makeText(requireContext(), "Unknown badge number", Toast.LENGTH_LONG)
                        .show()
                }
                accessToken = loginResponse!!.getString("accessToken")
                val jsonProjects = backend.getProjects(accessToken)
                // empty projects property in case it was already filled
                projects.clear()

                for (i in 0 until jsonProjects!!.length()) {
                    val jsonProject: JSONObject = jsonProjects.getJSONObject(i)
                    projects.add(Project(
                        jsonProject.getInt("id"),
                        jsonProject.getString("number"), // todo switch name and number, right now done because of backend issue
                        jsonProject.getString("name"),
                        jsonProject.getInt("start"),
                        jsonProject.getInt("end")
                    ))
                }

                val projectNamesArray: Array<String> = Array(projects.size) { "" }
                for ((i, project) in projects.withIndex()) {
                    projectNamesArray[i] = project.name
                }

                val arrayAdapter: ArrayAdapter<String> = ArrayAdapter(requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    projectNamesArray)

                (requireContext() as Activity).runOnUiThread { sProjects.adapter = arrayAdapter }
            }
        }

        ibAdd.setOnClickListener {
            selectedProject?.let { project ->
                val builder = AlertDialog.Builder(requireContext())
                builder.setMessage("Is this the correct project?")
                    .setCancelable(false)
                    .setPositiveButton("Yes") { _, _ ->
                        parentFragmentManager.beginTransaction().apply {
                            replace(R.id.flFragment,
                                ShoppingCart(project.id, accessToken, backend))
                            addToBackStack(null)
                            commit()
                        }
                    }
                    .setNegativeButton("No") { dialog, _ ->
                        dialog.dismiss()
                    }
                val dialog = builder.create()
                dialog.show()
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