package com.novodin.ihc.adapters

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import com.novodin.ihc.R

import com.novodin.ihc.databinding.FragmentPackingSlipItemBinding
import com.novodin.ihc.model.PackingSlipItem

class PackingSlipItemRecyclerViewAdapter(
    private val packingSlipItems: List<PackingSlipItem>,
) : RecyclerView.Adapter<PackingSlipItemRecyclerViewAdapter.ViewHolder>() {

    var selectedValuePosition: Int = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            FragmentPackingSlipItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val packingSlipItem = packingSlipItems[position]
        holder.companyName.text = packingSlipItem.companyName
        holder.number.text = packingSlipItem.number

        holder.itemView.setOnClickListener {
            selectedValuePosition = position
            notifyDataSetChanged()
        }

        if (position == selectedValuePosition)
            holder.itemView.setBackgroundColor(holder.itemView.context.getColor(R.color.selected_overlay))
        else
            holder.itemView.setBackgroundColor(holder.itemView.context.getColor(R.color.white))
    }

    override fun getItemCount(): Int = packingSlipItems.size

    inner class ViewHolder(binding: FragmentPackingSlipItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        val companyName: TextView = binding.tvCompanyName
        val number: TextView = binding.tvNumber
    }

}