package com.novodin.ihc

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageButton
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.novodin.ihc.fragments.ShoppingCart
import com.novodin.ihc.fragments.ProjectSelection
import com.novodin.ihc.scanner.ScannerTestFragment
import org.json.JSONException
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private var requestQueue: RequestQueue? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

//        val projectSelectionFragment = ProjectSelection()

//
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.flShoppingCart, ShoppingCart())
            commit()
        }
//
//        requestQueue = Volley.newRequestQueue(this)
//
//        val imgbtn2 = findViewById<ImageButton>(R.id.ibDelete)
//        imgbtn2.setOnClickListener {
//            httpCall()
//        }


    }

    private fun httpCall() {
        val url = "http://34.68.16.30:3001/login"
        val request = object : JsonObjectRequest(
            Request.Method.POST,
            url,
            JSONObject("{\"badge\":\"3785135106\"}"),
            { response ->
                try {
                    println(response)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            },
            { error -> error.printStackTrace() }) {
            override fun getHeaders(): MutableMap<String, String> {
                var headers = hashMapOf<String, String>()
                headers["x-api-key"] = "ZKbaStU4C2YcNEfuLLSbgeq8vKXgt994"
                return headers
            }
        }



        requestQueue?.add(request)
    }
}