package com.mritsoftware.mritserver.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
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
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class WelcomeActivity : AppCompatActivity() {
    
    private lateinit var sharedPreferences: SharedPreferences
    
    // Views
    private lateinit var searchingLayout: View
    private lateinit var devicesFoundLayout: View
    private lateinit var noDevicesLayout: View
    private lateinit var searchProgressBar: ProgressBar
    private lateinit var searchingText: TextView
    private lateinit var searchingSubtext: TextView
    private lateinit var devicesRecyclerView: RecyclerView
    private lateinit var startServerButton: MaterialButton
    private lateinit var retrySearchButton: MaterialButton
    private lateinit var retrySearchButton2: MaterialButton
    
    private lateinit var deviceAdapter: WelcomeDeviceAdapter
    private val devices = mutableListOf<WelcomeDevice>()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    
    private var isServerStarted = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sharedPreferences = getSharedPreferences("TuyaGateway", MODE_PRIVATE)
        
        // Verificar se já configurou o nome do site
        if (isSiteConfigured()) {
            startMainActivity()
            return
        }
        
        setContentView(R.layout.activity_welcome)
        
        setupViews()
        setupRecyclerView()
        setupListeners()
        
        // Iniciar servidor Python em background
        startPythonServer()
        
        // Aguardar servidor iniciar e então buscar dispositivos
        coroutineScope.launch {
            delay(3000) // Dar tempo para o servidor Python iniciar
            searchDevices()
        }
    }
    
    private fun setupViews() {
        searchingLayout = findViewById(R.id.searchingLayout)
        devicesFoundLayout = findViewById(R.id.devicesFoundLayout)
        noDevicesLayout = findViewById(R.id.noDevicesLayout)
        searchProgressBar = findViewById(R.id.searchProgressBar)
        searchingText = findViewById(R.id.searchingText)
        searchingSubtext = findViewById(R.id.searchingSubtext)
        devicesRecyclerView = findViewById(R.id.devicesRecyclerView)
        startServerButton = findViewById(R.id.startServerButton)
        retrySearchButton = findViewById(R.id.retrySearchButton)
        retrySearchButton2 = findViewById(R.id.retrySearchButton2)
    }
    
    private fun setupRecyclerView() {
        deviceAdapter = WelcomeDeviceAdapter(devices)
        devicesRecyclerView.layoutManager = LinearLayoutManager(this)
        devicesRecyclerView.adapter = deviceAdapter
    }
    
    private fun setupListeners() {
        startServerButton.setOnClickListener {
            syncWithSupabase()
        }
        
        retrySearchButton.setOnClickListener {
            searchDevices()
        }
        
        retrySearchButton2.setOnClickListener {
            searchDevices()
        }
    }
    
    private fun startPythonServer() {
        val intent = Intent(this, PythonServerService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        isServerStarted = true
    }
    
    private fun searchDevices() {
        // Mostrar tela de busca
        searchingLayout.visibility = View.VISIBLE
        devicesFoundLayout.visibility = View.GONE
        noDevicesLayout.visibility = View.GONE
        
        searchingText.text = "Buscando dispositivos..."
        searchingSubtext.text = "Escaneando a rede local"
        
        coroutineScope.launch {
            try {
                // Inicializar Python se necessário
                if (!com.chaquo.python.Python.isStarted()) {
                    com.chaquo.python.Python.start(com.chaquo.python.android.AndroidPlatform(this@WelcomeActivity))
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
                        Log.e("WelcomeActivity", "Erro ao escanear dispositivos", e)
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
                    
                    // Mostrar dispositivos encontrados
                    deviceAdapter.updateDevices(devices)
                    showDevicesFound()
                } else {
                    // Nenhum dispositivo encontrado
                    showNoDevices()
                }
            } catch (e: Exception) {
                Log.e("WelcomeActivity", "Erro ao buscar dispositivos", e)
                showNoDevices()
            }
        }
    }
    
    private fun showDevicesFound() {
        searchingLayout.visibility = View.GONE
        devicesFoundLayout.visibility = View.VISIBLE
        noDevicesLayout.visibility = View.GONE
    }
    
    private fun showNoDevices() {
        searchingLayout.visibility = View.GONE
        devicesFoundLayout.visibility = View.GONE
        noDevicesLayout.visibility = View.VISIBLE
    }
    
    private fun syncWithSupabase() {
        // Mostrar loading
        startServerButton.isEnabled = false
        startServerButton.text = "Sincronizando..."
        
        coroutineScope.launch {
            try {
                // Preparar dados para sincronização
                val devicesData = JSONObject()
                for (device in devices) {
                    devicesData.put(device.id, JSONObject().apply {
                        // Não enviar name nem local_key, apenas deixar o servidor atualizar protocol_version e lan_ip
                    })
                }
                
                val syncBody = JSONObject().apply {
                    put("devices", devicesData)
                }
                
                // Chamar endpoint de sincronização
                val syncResult = withContext(Dispatchers.IO) {
                    syncWithServer(syncBody.toString())
                }
                
                if (syncResult) {
                    Toast.makeText(this@WelcomeActivity, "Sincronização concluída com sucesso!", Toast.LENGTH_SHORT).show()
                    
                    // Salvar que a configuração foi concluída
                    sharedPreferences.edit()
                        .putBoolean("welcome_completed", true)
                        .putString("site_name", "ANDROID_DEVICE") // Nome padrão
                        .apply()
                    
                    // Ir para MainActivity
                    delay(1000)
                    startMainActivity()
                } else {
                    Toast.makeText(this@WelcomeActivity, "Erro ao sincronizar com o servidor", Toast.LENGTH_LONG).show()
                    startServerButton.isEnabled = true
                    startServerButton.text = "Ligar Servidor"
                }
                
            } catch (e: Exception) {
                Log.e("WelcomeActivity", "Erro na sincronização", e)
                Toast.makeText(this@WelcomeActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                startServerButton.isEnabled = true
                startServerButton.text = "Ligar Servidor"
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
            val writer = OutputStreamWriter(outputStream, "UTF-8")
            writer.write(body)
            writer.flush()
            writer.close()
            
            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()
                
                val json = JSONObject(response)
                connection.disconnect()
                return@withContext json.getBoolean("ok")
            }
            
            connection.disconnect()
            false
        } catch (e: Exception) {
            Log.e("WelcomeActivity", "Erro ao sincronizar", e)
            false
        }
    }
    
    private fun isSiteConfigured(): Boolean {
        return sharedPreferences.getBoolean("welcome_completed", false) &&
               sharedPreferences.getString("site_name", null) != null
    }
    
    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Não parar o serviço aqui - deixar rodando em background
    }
}
