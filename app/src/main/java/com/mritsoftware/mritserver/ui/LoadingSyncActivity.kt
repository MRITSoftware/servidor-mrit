package com.mritsoftware.mritserver.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.mritsoftware.mritserver.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class LoadingSyncActivity : AppCompatActivity() {
    
    private lateinit var loadingText: TextView
    private lateinit var loadingSubtext: TextView
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    
    companion object {
        const val EXTRA_DEVICE_ID = "device_id"
        const val EXTRA_DEVICE_NAME = "device_name"
        const val EXTRA_LOCAL_KEY = "local_key"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading_sync)
        
        loadingText = findViewById(R.id.loadingText)
        loadingSubtext = findViewById(R.id.loadingSubtext)
        
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
        val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME)
        val localKey = intent.getStringExtra(EXTRA_LOCAL_KEY)
        
        if (deviceId == null || deviceName == null) {
            finishWithError("Dados do dispositivo não fornecidos")
            return
        }
        
        startSync(deviceId, deviceName, localKey)
    }
    
    private fun startSync(deviceId: String, deviceName: String, localKey: String?) {
        coroutineScope.launch {
            try {
                updateStatus("Buscando dispositivo na rede...")
                
                // 1. Fazer scan na rede para encontrar o dispositivo
                val lanDevices = withContext(Dispatchers.IO) {
                    scanLanDevices()
                }
                
                if (lanDevices.isEmpty()) {
                    finishWithError("Nenhum dispositivo encontrado na rede. Verifique se o dispositivo está conectado.")
                    return@launch
                }
                
                // 2. Verificar se o deviceId está na rede
                val deviceInNetwork = lanDevices.find { it.getString("id") == deviceId }
                
                if (deviceInNetwork == null) {
                    finishWithError("Dispositivo $deviceId não encontrado na rede.")
                    return@launch
                }
                
                updateStatus("Dispositivo encontrado! Sincronizando com servidor...")
                
                // 3. Preparar dados para sincronização
                val devicesData = JSONObject().apply {
                    put(deviceId, JSONObject().apply {
                        put("name", deviceName)
                        if (localKey != null) {
                            put("local_key", localKey)
                        }
                    })
                }
                
                val syncBody = JSONObject().apply {
                    put("devices", devicesData)
                }
                
                // 4. Chamar endpoint de sincronização
                val syncResult = withContext(Dispatchers.IO) {
                    syncWithServer(syncBody.toString())
                }
                
                if (syncResult) {
                    updateStatus("Sincronização concluída com sucesso!")
                    
                    // Aguardar um pouco antes de fechar
                    kotlinx.coroutines.delay(1000)
                    
                    // Retornar resultado
                    val resultIntent = Intent().apply {
                        putExtra("sync_success", true)
                        putExtra("device_id", deviceId)
                        putExtra("device_name", deviceName)
                        putExtra("lan_ip", deviceInNetwork.optString("ip", ""))
                        putExtra("protocol_version", deviceInNetwork.optString("version", ""))
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish()
                } else {
                    finishWithError("Erro ao sincronizar com o servidor.")
                }
                
            } catch (e: Exception) {
                Log.e("LoadingSync", "Erro na sincronização", e)
                finishWithError("Erro: ${e.message}")
            }
        }
    }
    
    private suspend fun scanLanDevices(): List<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://127.0.0.1:8000/tuya/devices")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()
                
                val json = JSONObject(response)
                if (json.getBoolean("ok")) {
                    val devicesArray = json.getJSONArray("devices")
                    val devicesList = mutableListOf<JSONObject>()
                    for (i in 0 until devicesArray.length()) {
                        devicesList.add(devicesArray.getJSONObject(i))
                    }
                    return@withContext devicesList
                }
            }
            connection.disconnect()
            emptyList()
        } catch (e: Exception) {
            Log.e("LoadingSync", "Erro ao escanear dispositivos", e)
            emptyList()
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
            Log.e("LoadingSync", "Erro ao sincronizar", e)
            false
        }
    }
    
    private fun updateStatus(text: String) {
        runOnUiThread {
            loadingSubtext.text = text
        }
    }
    
    private fun finishWithError(message: String) {
        val resultIntent = Intent().apply {
            putExtra("sync_success", false)
            putExtra("error_message", message)
        }
        setResult(RESULT_CANCELED, resultIntent)
        finish()
    }
}



