package com.novodin.ihc.model

import android.content.Context
import com.novodin.ihc.network.Backend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// TODO use this model
class ShoppingCart(context: Context) {
    private val articleList: MutableList<Article> = ArrayList()
    private var itemCount: Int = 0
    private var puCount: Int = 0
    private var unitCount: Int = 0
    private var cartId: Int = 0
    private var accessToken: String = ""

    init {
//        val backend = Backend(context, "http://34.68.16.30:3001")
//        CoroutineScope(Dispatchers.IO).launch {
//            val loginResponse = backend.login("3785135106")
//            accessToken = loginResponse!!.getString("accessToken")
    }
}


