package com.novodin.ihc.network

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.RequestFuture
import com.android.volley.toolbox.Volley
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class Backend(context: Context, private val baseURL: String) {
    private val requestQueue = Volley.newRequestQueue(context)
    private val apiKey = "ZKbaStU4C2YcNEfuLLSbgeq8vKXgt994"

    suspend fun login(badge: String, errorListener: Response.ErrorListener): JSONObject? {
        return runJSONObjectRequest(Request.Method.POST,
            "/login",
            JSONObject("{\"badge\":\"$badge\"}"),
            errorListener
        )
    }

    suspend fun createCart(projectId: Int, bearerToken: String): JSONObject? {
        return runJSONObjectRequest(Request.Method.POST,
            "/api/cart",
            JSONObject("{\"projectId\":$projectId}"),
            { error -> error.printStackTrace() },
            hashMapOf<String, String>("Authorization" to "Bearer $bearerToken"))
    }

    suspend fun addItem(
        cartId: Int,
        barcode: String,
        bearerToken: String,
        errorListener: Response.ErrorListener,
    ): JSONObject? {
        return runJSONObjectRequest(Request.Method.POST,
            "/api/cart/$cartId/item",
            JSONObject("{\"barcode\":\"$barcode\"}"),
            errorListener,
            hashMapOf<String, String>("Authorization" to "Bearer $bearerToken"))
    }

    suspend fun removeItem(cartId: Int, itemId: Int, bearerToken: String): JSONObject? {
        return runJSONObjectRequest(Request.Method.DELETE,
            "/api/cart/$cartId/item/$itemId",
            null,
            { error -> error.printStackTrace() },
            hashMapOf<String, String>("Authorization" to "Bearer $bearerToken"))
    }

    suspend fun approve(
        cartId: Int,
        bearerToken: String,
        shoppingCartArticlesJSON: JSONArray,
    ): JSONArray? {
        return runJSONArrayRequest(Request.Method.POST,
            "/api/cart/$cartId/approve",
            shoppingCartArticlesJSON,
            { error -> error.printStackTrace() },
            hashMapOf<String, String>("Authorization" to "Bearer $bearerToken"))
    }

    suspend fun getProjects(bearerToken: String): JSONArray? {

        return runJSONArrayRequest(Request.Method.GET,
            "/api/project",
            null,
            { error -> error.printStackTrace() },
            hashMapOf<String, String>("Authorization" to "Bearer $bearerToken"))
    }

    private fun getHeaders(extraHeaders: MutableMap<String, String>): MutableMap<String, String> {
        val headers = hashMapOf<String, String>()
        headers["x-api-key"] = apiKey
        headers += extraHeaders
        return headers
    }

    private suspend fun runJSONObjectRequest(
        method: Int,
        api: String,
        payload: JSONObject?,
        errorListener: Response.ErrorListener,
        extraHeaders: MutableMap<String, String> = hashMapOf<String, String>(),
    ): JSONObject? {
        return suspendCancellableCoroutine {
            val request = object : JsonObjectRequest(method,
                "$baseURL$api",
                payload,
                { resp -> it.resumeWith(Result.success(resp)) },
                errorListener) {
                override fun getHeaders(): MutableMap<String, String> {
                    return this@Backend.getHeaders(extraHeaders)
                }
            }
            requestQueue.add(request)
        }
    }

    private suspend fun runJSONArrayRequest(
        method: Int,
        api: String,
        payload: JSONArray?,
        errorListener: Response.ErrorListener,
        extraHeaders: MutableMap<String, String> = hashMapOf<String, String>(),
    ): JSONArray? {
        return suspendCancellableCoroutine {
            val request = object : JsonArrayRequest(method,
                "$baseURL$api",
                payload,
                { resp -> it.resumeWith(Result.success(resp)) },
                errorListener) {
                override fun getHeaders(): MutableMap<String, String> {
                    return this@Backend.getHeaders(extraHeaders)
                }
            }
            requestQueue.add(request)
        }
    }


}