package com.novodin.ihc.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.novodin.ihc.R
import com.novodin.ihc.adapters.PackingSlipItemRecyclerViewAdapter
import com.novodin.ihc.model.PackingSlipItem
import com.novodin.ihc.model.QuantityType
import com.novodin.ihc.network.Backend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class PackingSlip(
    private var accessToken: String,
    private var backend: Backend,
    private val packingSlipItemList: ArrayList<PackingSlipItem>,
) :
    Fragment(R.layout.fragment_packing_slip) {
    private lateinit var rvPackingSlipItemList: RecyclerView

    private lateinit var ibAdd: ImageButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvPackingSlipItemList = view.findViewById(R.id.rvPackingSlip) as RecyclerView
        rvPackingSlipItemList.adapter = PackingSlipItemRecyclerViewAdapter(packingSlipItemList)

        ibAdd = view.findViewById(R.id.ibAdd) as ImageButton

        ibAdd.setOnClickListener {
            val adapter = rvPackingSlipItemList.adapter as PackingSlipItemRecyclerViewAdapter
            val pos = adapter.selectedValuePosition
            if (pos == -1) return@setOnClickListener // return if nothing is selected

            val packingSlipListItem = packingSlipItemList[pos]

            // add item in backend
            CoroutineScope(Dispatchers.IO).launch {
                backend.packingSlip(packingSlipListItem.number, accessToken)
            }

            adapter.notifyItemChanged(pos)
        }

    }

}
