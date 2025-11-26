package com.mritsoftware.mritserver.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.mritsoftware.mritserver.R
import com.mritsoftware.mritserver.model.DiscoveredDevice

class DiscoveredDeviceAdapter(
    private val devices: MutableList<DiscoveredDevice>,
    private val onDeviceClick: (DiscoveredDevice) -> Unit
) : RecyclerView.Adapter<DiscoveredDeviceAdapter.ViewHolder>() {
    
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView = itemView.findViewById(R.id.deviceCard)
        val deviceId: TextView = itemView.findViewById(R.id.deviceId)
        val deviceIp: TextView = itemView.findViewById(R.id.deviceIp)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_discovered_device, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]
        holder.deviceId.text = "ID: ${device.deviceId}"
        holder.deviceIp.text = "IP: ${device.ip}"
        
        holder.cardView.setOnClickListener {
            onDeviceClick(device)
        }
    }
    
    override fun getItemCount() = devices.size
}


