package com.mritsoftware.mritserver.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.NetworkInterface
import java.net.URL
import java.net.HttpURLConnection
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.io.BufferedReader
import java.io.InputStreamReader

class LocalIpMonitorService(private val context: Context) {
    
    private val TAG = "LocalIpMonitor"
    private val PREFS_NAME = "TuyaGateway"
    private val KEY_LOCAL_IP = "local_ip"
    private val CHECK_INTERVAL = 60000L // 1 minuto
    private var job: Job? = null
    private var coroutineScope: CoroutineScope? = null
    private var monitoringJob: Job? = null
    
    fun startMonitoring() {
        stopMonitoring()
        
        val newJob = Job()
        job = newJob
        val scope = CoroutineScope(Dispatchers.IO + newJob)
        coroutineScope = scope
        
        monitoringJob = scope.launch {
            while (isActive) {
                try {
                    checkAndUpdateLocalIp()
                    delay(CHECK_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Erro no monitoramento de IP", e)
                    delay(CHECK_INTERVAL)
                }
            }
        }
        
        Log.d(TAG, "Monitoramento de IP local iniciado")
    }
    
    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        job?.cancel()
        job = null
        coroutineScope = null
        Log.d(TAG, "Monitoramento de IP local parado")
    }
    
    private fun getLocalIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        val ip = address.hostAddress
                        if (ip != null && !ip.startsWith("169.254")) { // Ignorar link-local
                            return ip
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao obter IP local", e)
            null
        }
    }
    
    private suspend fun checkAndUpdateLocalIp() {
        val currentIp = getLocalIpAddress()
        if (currentIp == null) {
            Log.d(TAG, "Não foi possível obter IP local")
            return
        }
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedIp = prefs.getString(KEY_LOCAL_IP, null)
        
        if (savedIp != null && savedIp != currentIp) {
            Log.d(TAG, "IP local mudou: $savedIp -> $currentIp")
            // IP mudou, fazer scan e atualizar dispositivos no banco
            scanAndUpdateDevicesInDatabase()
        } else if (savedIp == null) {
            // Primeira vez, apenas salvar
            Log.d(TAG, "Salvando IP local inicial: $currentIp")
        }
        
        // Sempre atualizar o IP salvo
        prefs.edit().putString(KEY_LOCAL_IP, currentIp).apply()
    }
    
    private suspend fun scanAndUpdateDevicesInDatabase() {
        try {
            // Verificar se o servidor está rodando
            val healthUrl = URL("http://127.0.0.1:8000/health")
            val healthConnection = healthUrl.openConnection() as HttpURLConnection
            healthConnection.requestMethod = "GET"
            healthConnection.connectTimeout = 3000
            healthConnection.readTimeout = 3000
            
            val healthCode = healthConnection.responseCode
            healthConnection.disconnect()
            
            if (healthCode != 200) {
                Log.w(TAG, "Servidor não está respondendo, não é possível atualizar dispositivos")
                return
            }
            
            // Buscar site name
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val siteName = prefs.getString("site_name", null)
            
            if (siteName == null) {
                Log.w(TAG, "Site name não encontrado, não é possível atualizar dispositivos")
                return
            }
            
            Log.d(TAG, "IP local mudou, fazendo scan de dispositivos e atualizando banco...")
            
            // Fazer scan de dispositivos (isso vai atualizar os IPs automaticamente)
            // O endpoint /tuya/sync faz scan e atualiza IPs automaticamente
            val syncBody = JSONObject().apply {
                put("site_id", siteName)
                put("devices", JSONObject()) // Objeto vazio, o servidor fará scan
            }
            
            // Chamar sync para fazer scan e atualizar IPs
            val syncUrl = URL("http://127.0.0.1:8000/tuya/sync")
            val syncConnection = syncUrl.openConnection() as HttpURLConnection
            syncConnection.requestMethod = "POST"
            syncConnection.setRequestProperty("Content-Type", "application/json")
            syncConnection.doOutput = true
            syncConnection.connectTimeout = 40000  // 40 segundos para dar tempo do scan
            syncConnection.readTimeout = 40000
            
            val writer = OutputStreamWriter(syncConnection.outputStream, "UTF-8")
            writer.write(syncBody.toString())
            writer.flush()
            writer.close()
            
            val syncCode = syncConnection.responseCode
            if (syncCode == 200) {
                val reader = BufferedReader(InputStreamReader(syncConnection.inputStream))
                val response = reader.readText()
                reader.close()
                
                val json = JSONObject(response)
                if (json.getBoolean("ok")) {
                    val updated = json.optInt("updated", 0)
                    val created = json.optInt("created", 0)
                    Log.d(TAG, "Dispositivos atualizados no banco: $updated atualizados, $created criados")
                }
            } else {
                Log.w(TAG, "Erro ao sincronizar dispositivos: código $syncCode")
            }
            syncConnection.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar dispositivos no banco", e)
        }
    }
}

