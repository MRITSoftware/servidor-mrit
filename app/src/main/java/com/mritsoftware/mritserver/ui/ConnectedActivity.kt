package com.mritsoftware.mritserver.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.mritsoftware.mritserver.MainActivity
import com.mritsoftware.mritserver.R
import com.mritsoftware.mritserver.adapter.WelcomeDevice
import com.mritsoftware.mritserver.adapter.WelcomeDeviceAdapter
import com.mritsoftware.mritserver.service.PythonServerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class ConnectedActivity : AppCompatActivity() {
    
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var statusCircle: View
    private lateinit var changeDeviceButton: com.google.android.material.button.MaterialButton
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sharedPreferences = getSharedPreferences("TuyaGateway", MODE_PRIVATE)
        
        // Verificar se já está configurado, se não, voltar para welcome
        if (!isSiteConfigured()) {
            startWelcomeActivity()
            return
        }
        
        setContentView(R.layout.activity_connected)
        
        setupViews()
        setupAnimations()
        startServerService()
        checkServerStatus()
    }
    
    private fun setupViews() {
        statusCircle = findViewById(R.id.statusCircle)
        changeDeviceButton = findViewById(R.id.changeDeviceButton)
        
        // Configurar botão de trocar dispositivo
        changeDeviceButton.setOnClickListener {
            showChangeDeviceDialog()
        }
        
        // Aplicar insets para status bar
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
    
    private fun showChangeDeviceDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_device, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.devicesRecyclerView)
        val progressBar = dialogView.findViewById<View>(R.id.progressBar)
        val emptyText = dialogView.findViewById<TextView>(R.id.emptyText)
        val scanButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.scanButton)
        
        val devices = mutableListOf<WelcomeDevice>()
        val adapter = WelcomeDeviceAdapter(devices)
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recyclerView.adapter = adapter
        
        // Buscar dispositivos ao abrir
        scanDevicesForDialog(dialogView, progressBar, emptyText, recyclerView, devices, adapter, dialog)
        
        scanButton.setOnClickListener {
            scanDevicesForDialog(dialogView, progressBar, emptyText, recyclerView, devices, adapter, dialog)
        }
        
        dialog.show()
    }
    
    private fun scanDevicesForDialog(
        dialogView: View,
        progressBar: View,
        emptyText: TextView,
        recyclerView: androidx.recyclerview.widget.RecyclerView,
        devices: MutableList<WelcomeDevice>,
        adapter: WelcomeDeviceAdapter,
        dialog: androidx.appcompat.app.AlertDialog
    ) {
        progressBar.visibility = View.VISIBLE
        emptyText.visibility = View.GONE
        recyclerView.visibility = View.GONE
        
        coroutineScope.launch {
            try {
                // Inicializar Python se necessário
                if (!com.chaquo.python.Python.isStarted()) {
                    com.chaquo.python.Python.start(com.chaquo.python.android.AndroidPlatform(this@ConnectedActivity))
                }
                
                val python = com.chaquo.python.Python.getInstance()
                val module = python.getModule("tuya_server")
                
                // Chamar scan_devices do Python
                val scanResult = withContext(Dispatchers.IO) {
                    try {
                        val result = module.callAttr("scan_devices")
                        if (result != null) {
                            val devicesMap = mutableMapOf<String, Map<String, String>>()
                            
                            val pyDict = result.asMap()
                            for ((key, value) in pyDict) {
                                val deviceId = key.toString()
                                val deviceInfo = value?.asMap() ?: emptyMap()
                                
                                val deviceMap = mutableMapOf<String, String>()
                                for ((infoKey, infoValue) in deviceInfo) {
                                    deviceMap[infoKey.toString()] = infoValue?.toString() ?: ""
                                }
                                devicesMap[deviceId] = deviceMap
                            }
                            devicesMap
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ConnectedActivity", "Erro ao escanear dispositivos", e)
                        null
                    }
                }
                
                devices.clear()
                
                if (scanResult != null && scanResult.isNotEmpty()) {
                    for ((deviceId, deviceInfo) in scanResult) {
                        val ip = deviceInfo["ip"] ?: ""
                        val version = deviceInfo["version"]?.toString()
                        
                        devices.add(
                            WelcomeDevice(
                                id = deviceId,
                                ip = ip,
                                protocolVersion = version
                            )
                        )
                    }
                    
                    // Atualizar UI no thread principal
                    runOnUiThread {
                        adapter.updateDevices(devices)
                        progressBar.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        emptyText.visibility = View.GONE
                    }
                } else {
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        recyclerView.visibility = View.GONE
                        emptyText.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ConnectedActivity", "Erro ao buscar dispositivos", e)
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    recyclerView.visibility = View.GONE
                    emptyText.visibility = View.VISIBLE
                }
            }
        }
        
        // Configurar clique no dispositivo
        adapter.setOnDeviceClickListener { device ->
            connectNewDevice(device, dialog)
        }
    }
    
    private fun connectNewDevice(device: WelcomeDevice, dialog: androidx.appcompat.app.AlertDialog) {
        val siteName = sharedPreferences.getString("site_name", "") ?: ""
        if (siteName.isBlank()) {
            android.widget.Toast.makeText(this, "Nome da unidade não encontrado", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        coroutineScope.launch {
            try {
                // Preparar dados para sincronização com apenas o novo dispositivo
                val devicesData = org.json.JSONObject().apply {
                    put(device.id, org.json.JSONObject().apply {
                        put("name", siteName)
                    })
                }
                
                val syncBody = org.json.JSONObject().apply {
                    put("site_id", siteName)
                    put("devices", devicesData)
                }
                
                // Chamar endpoint de sincronização
                val syncResult = withContext(Dispatchers.IO) {
                    syncWithServer(syncBody.toString())
                }
                
                if (syncResult) {
                    android.widget.Toast.makeText(this@ConnectedActivity, "Dispositivo trocado com sucesso", android.widget.Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    android.widget.Toast.makeText(this@ConnectedActivity, "Erro ao trocar dispositivo", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("ConnectedActivity", "Erro ao trocar dispositivo", e)
                android.widget.Toast.makeText(this@ConnectedActivity, "Erro: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private suspend fun syncWithServer(body: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://127.0.0.1:8000/tuya/sync")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            
            val outputStream = connection.outputStream
            val writer = java.io.OutputStreamWriter(outputStream, "UTF-8")
            writer.write(body)
            writer.flush()
            writer.close()
            
            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val reader = java.io.BufferedReader(java.io.InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()
                
                val json = org.json.JSONObject(response)
                connection.disconnect()
                return@withContext json.getBoolean("ok")
            }
            
            connection.disconnect()
            false
        } catch (e: Exception) {
            android.util.Log.e("ConnectedActivity", "Erro ao sincronizar", e)
            false
        }
    }
    
    private fun setupAnimations() {
        // Animação de pulso no círculo
        val pulseAnimator = ObjectAnimator.ofFloat(statusCircle, "scaleX", 1f, 1.1f, 1f)
        pulseAnimator.duration = 2000
        pulseAnimator.repeatCount = ValueAnimator.INFINITE
        pulseAnimator.repeatMode = ValueAnimator.REVERSE
        pulseAnimator.interpolator = LinearInterpolator()
        pulseAnimator.start()
        
        val pulseAnimatorY = ObjectAnimator.ofFloat(statusCircle, "scaleY", 1f, 1.1f, 1f)
        pulseAnimatorY.duration = 2000
        pulseAnimatorY.repeatCount = ValueAnimator.INFINITE
        pulseAnimatorY.repeatMode = ValueAnimator.REVERSE
        pulseAnimatorY.interpolator = LinearInterpolator()
        pulseAnimatorY.start()
    }
    
    private fun startServerService() {
        // Iniciar servidor Python em background
        val intent = Intent(this, PythonServerService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
    
    private fun checkServerStatus() {
        coroutineScope.launch {
            // Aguardar um pouco para o servidor iniciar
            delay(2000)
            
            while (true) {
                val isConnected = withContext(Dispatchers.IO) {
                    checkServerHealth()
                }
                
                // Atualizar UI baseado no status
                runOnUiThread {
                    updateConnectionStatus(isConnected)
                }
                
                // Verificar novamente após 5 segundos
                delay(5000)
            }
        }
    }
    
    private fun checkServerHealth(): Boolean {
        return try {
            val url = URL("http://127.0.0.1:8000/health")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            
            val responseCode = connection.responseCode
            connection.disconnect()
            
            responseCode == 200
        } catch (e: Exception) {
            false
        }
    }
    
    private fun updateConnectionStatus(isConnected: Boolean) {
        // O círculo já está animado, apenas garantir que está visível
        if (isConnected) {
            statusCircle.alpha = 1f
        } else {
            statusCircle.alpha = 0.5f
        }
    }
    
    private fun isSiteConfigured(): Boolean {
        return sharedPreferences.getBoolean("welcome_completed", false) &&
               sharedPreferences.getString("site_name", null) != null
    }
    
    private fun startWelcomeActivity() {
        val intent = Intent(this, WelcomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    override fun onBackPressed() {
        // Não permitir voltar, apenas minimizar o app
        moveTaskToBack(true)
    }
}

