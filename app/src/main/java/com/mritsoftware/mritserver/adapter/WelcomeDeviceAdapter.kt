package com.mritsoftware.mritserver.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mritsoftware.mritserver.R

data class WelcomeDevice(
    val id: String,
    val ip: String,
    val protocolVersion: String?
)

class WelcomeDeviceAdapter(
    private val devices: MutableList<WelcomeDevice>
) : RecyclerView.Adapter<WelcomeDeviceAdapter.DeviceViewHolder>() {
    
    private var onDeviceClickListener: ((WelcomeDevice) -> Unit)? = null
    
    fun setOnDeviceClickListener(listener: (WelcomeDevice) -> Unit) {
        onDeviceClickListener = listener
    }

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceName: TextView = itemView.findViewById(R.id.deviceName)
        val deviceId: TextView = itemView.findViewById(R.id.deviceId)
        val deviceIp: TextView = itemView.findViewById(R.id.deviceIp)
        val deviceProtocol: TextView = itemView.findViewById(R.id.deviceProtocol)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device_welcome, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        
        // Mostrar últimos 5 caracteres do device ID de forma destacada
        val deviceIdSuffix = if (device.id.length >= 5) {
            device.id.takeLast(5)
        } else {
            device.id
        }
        
        holder.deviceName.text = "Dispositivo: $deviceIdSuffix"
        holder.deviceId.text = "ID: ${device.id}"
        holder.deviceIp.text = "IP: ${device.ip}"
        holder.deviceProtocol.text = "Protocolo: ${device.protocolVersion ?: "N/A"}"
        
        holder.itemView.setOnClickListener {
            onDeviceClickListener?.invoke(device)
        }
    }

    override fun getItemCount() = devices.size
    
    fun updateDevices(newDevices: List<WelcomeDevice>) {
        devices.clear()
        devices.addAll(newDevices)
        notifyDataSetChanged()
    }
}

