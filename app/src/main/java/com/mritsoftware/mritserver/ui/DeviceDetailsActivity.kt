package com.mritsoftware.mritserver.ui

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.mritsoftware.mritserver.R
import com.mritsoftware.mritserver.model.TuyaDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeviceDetailsActivity : AppCompatActivity() {
    
    private lateinit var deviceName: TextView
    private lateinit var deviceId: TextView
    private lateinit var deviceIp: TextView
    private lateinit var deviceStatus: TextView
    private lateinit var discoverIpButton: MaterialButton
    private lateinit var deviceCard: MaterialCardView
    
    private var device: TuyaDevice? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_details)
        
        device = intent.getSerializableExtra("device") as? TuyaDevice
        
        if (device == null) {
            Toast.makeText(this, "Dispositivo não encontrado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        setupToolbar()
        setupViews()
        loadDeviceInfo()
        setupListeners()
    }
    
    private fun setupToolbar() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = device?.name ?: "Detalhes"
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    private fun setupViews() {
        deviceName = findViewById(R.id.deviceName)
        deviceId = findViewById(R.id.deviceId)
        deviceIp = findViewById(R.id.deviceIp)
        deviceStatus = findViewById(R.id.deviceStatus)
        discoverIpButton = findViewById(R.id.discoverIpButton)
        deviceCard = findViewById(R.id.deviceCard)
    }
    
    private fun loadDeviceInfo() {
        device?.let { dev ->
            deviceName.text = dev.name
            
            // Mostrar apenas os últimos 5 caracteres do ID
            val deviceIdFull = dev.id
            val deviceIdMasked = if (deviceIdFull.length > 5) {
                "***${deviceIdFull.takeLast(5)}"
            } else {
                deviceIdFull
            }
            deviceId.text = "ID: $deviceIdMasked"
            
            deviceStatus.text = if (dev.isOnline) "Status: Online" else "Status: Offline"
            
            // Buscar IP do SharedPreferences
            val prefs = getSharedPreferences("TuyaGateway", MODE_PRIVATE)
            val savedIp = prefs.getString("device_${dev.id}_ip", null)
            
            // Se tiver IP salvo, usar ele
            if (savedIp != null && dev.lanIp == null) {
                dev.lanIp = savedIp
            }
            
            // Atualizar IP se tiver
            deviceIp.text = "IP Local: ${dev.lanIp ?: savedIp ?: "Não configurado"}"
            
            // Cor do status
            deviceStatus.setTextColor(
                if (dev.isOnline) getColor(R.color.teal_700)
                else getColor(android.R.color.holo_red_dark)
            )
        }
    }
    
    private fun setupListeners() {
        discoverIpButton.setOnClickListener {
            discoverDeviceIp()
        }
    }
    
    private fun discoverDeviceIp() {
        device?.let { dev ->
            discoverIpButton.isEnabled = false
            discoverIpButton.text = "Descobrindo..."
            
            coroutineScope.launch {
                try {
                    // Inicializar Python se necessário
                    if (!com.chaquo.python.Python.isStarted()) {
                        com.chaquo.python.Python.start(com.chaquo.python.android.AndroidPlatform(this@DeviceDetailsActivity))
                    }
                    
                    // Chamar função Python para descobrir IP
                    val python = com.chaquo.python.Python.getInstance()
                    val module = python.getModule("tuya_server")
                    
                    val discoveredIp = withContext(Dispatchers.IO) {
                        try {
                            val result = module.callAttr("discover_tuya_ip", dev.id)
                            if (result != null && result.toString() != "None") {
                                result.toString()
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            Log.e("DeviceDetails", "Erro ao descobrir IP", e)
                            null
                        }
                    }
                    
                    if (discoveredIp != null && discoveredIp.isNotBlank()) {
                        dev.lanIp = discoveredIp
                        deviceIp.text = "IP Local: $discoveredIp"
                        
                        // Salvar IP
                        val prefs = getSharedPreferences("TuyaGateway", MODE_PRIVATE)
                        prefs.edit().putString("device_${dev.id}_ip", discoveredIp).apply()
                        
                        Toast.makeText(this@DeviceDetailsActivity, "IP descoberto: $discoveredIp", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@DeviceDetailsActivity, "IP não encontrado. Verifique se o dispositivo está na mesma rede.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("DeviceDetails", "Erro ao descobrir IP", e)
                    Toast.makeText(this@DeviceDetailsActivity, "Erro ao descobrir IP: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    discoverIpButton.isEnabled = true
                    discoverIpButton.text = "Descobrir IP"
                }
            }
        }
    }
}

