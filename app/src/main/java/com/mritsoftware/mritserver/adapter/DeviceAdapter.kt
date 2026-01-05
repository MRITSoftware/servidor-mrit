package com.mritsoftware.mritserver.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.mritsoftware.mritserver.R
import com.mritsoftware.mritserver.model.TuyaDevice

class DeviceAdapter(
    private val devices: MutableList<TuyaDevice>,
    private val onDeviceClick: (TuyaDevice) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView = itemView.findViewById(R.id.deviceCard)
        val deviceName: TextView = itemView.findViewById(R.id.deviceName)
        val deviceType: TextView = itemView.findViewById(R.id.deviceType)
        val deviceStatus: TextView = itemView.findViewById(R.id.deviceStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        
        // Mostrar apenas o ID do dispositivo
        holder.deviceName.text = device.id
        holder.deviceType.text = "Tipo: ${device.type.name}"
        holder.deviceStatus.text = if (device.isOnline) "Online" else "Offline"
        holder.deviceStatus.setTextColor(
            if (device.isOnline) 0xFF4ECDC4.toInt() else 0xFFB0B0B0.toInt()
        )
        
        holder.cardView.setOnClickListener {
            onDeviceClick(device)
        }
        
        holder.cardView.alpha = if (device.isOnline) 1.0f else 0.6f
    }

    override fun getItemCount() = devices.size
    
    fun updateDevices(newDevices: List<TuyaDevice>) {
        devices.clear()
        devices.addAll(newDevices)
        notifyDataSetChanged()
    }
}

