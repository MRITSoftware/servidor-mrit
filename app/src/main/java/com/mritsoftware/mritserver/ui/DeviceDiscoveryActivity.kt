package com.mritsoftware.mritserver.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.mritsoftware.mritserver.R
import com.mritsoftware.mritserver.adapter.DiscoveredDeviceAdapter
import com.mritsoftware.mritserver.model.DiscoveredDevice
import com.mritsoftware.mritserver.tuya.TuyaProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DeviceDiscoveryActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var scanButton: MaterialButton
    private lateinit var adapter: DiscoveredDeviceAdapter
    private val devices = mutableListOf<DiscoveredDevice>()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_discovery)
        
        setupToolbar()
        setupViews()
        setupRecyclerView()
        setupListeners()
    }
    
    private fun setupToolbar() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Descobrir Dispositivos"
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    private fun setupViews() {
        recyclerView = findViewById(R.id.discoveredDevicesRecyclerView)
        scanButton = findViewById(R.id.scanButton)
    }
    
    private fun setupRecyclerView() {
        adapter = DiscoveredDeviceAdapter(devices) { device ->
            onDeviceSelected(device)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }
    
    private fun setupListeners() {
        scanButton.setOnClickListener {
            scanDevices()
        }
    }
    
    private fun scanDevices() {
        scanButton.isEnabled = false
        scanButton.text = "Escaneando..."
        devices.clear()
        adapter.notifyDataSetChanged()
        
        Toast.makeText(this, "Escaneando rede local...", Toast.LENGTH_SHORT).show()
        
        coroutineScope.launch {
            try {
                val discovered = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    TuyaProtocol.discoverDevices(timeout = 5000)
                }
                
                discovered.forEach { (deviceId, ip) ->
                    devices.add(DiscoveredDevice(deviceId, ip))
                }
                
                adapter.notifyDataSetChanged()
                
                if (devices.isEmpty()) {
                    Toast.makeText(this@DeviceDiscoveryActivity, "Nenhum dispositivo encontrado", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@DeviceDiscoveryActivity, "${devices.size} dispositivo(s) encontrado(s)", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@DeviceDiscoveryActivity, "Erro ao escanear: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                scanButton.isEnabled = true
                scanButton.text = "Escanear Dispositivos"
            }
        }
    }
    
    private fun onDeviceSelected(device: DiscoveredDevice) {
        // Salvar dispositivo descoberto
        val prefs = getSharedPreferences("TuyaGateway", MODE_PRIVATE)
        prefs.edit()
            .putString("device_${device.deviceId}_ip", device.ip)
            .apply()
        
        Toast.makeText(this, "Dispositivo ${device.deviceId} salvo (IP: ${device.ip})", Toast.LENGTH_SHORT).show()
    }
}


