package com.mritsoftware.mritserver.server

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.mritsoftware.mritserver.model.TuyaDevice
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.IOException

class TuyaServer(
    private val context: Context,
    private val port: Int = 8000
) : NanoHTTPD(port) {
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("TuyaGateway", Context.MODE_PRIVATE)
    
    private var siteName: String = "SITE_DESCONHECIDO"
    
    init {
        siteName = sharedPreferences.getString("site_name", "SITE_DESCONHECIDO") ?: "SITE_DESCONHECIDO"
    }
    
    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        
        Log.d("TuyaServer", "Request: $method $uri")
        
        return when {
            uri == "/health" && method == Method.GET -> {
                handleHealth()
            }
            uri == "/tuya/command" && method == Method.POST -> {
                handleTuyaCommand(session)
            }
            else -> {
                newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    MIME_PLAINTEXT,
                    "Not Found"
                )
            }
        }
    }
    
    private fun handleHealth(): Response {
        val response = JSONObject().apply {
            put("status", "ok")
            put("site", siteName)
        }
        
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            response.toString()
        )
    }
    
    private fun handleTuyaCommand(session: IHTTPSession): Response {
        try {
            val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
            val buffer = ByteArray(contentLength)
            session.inputStream.read(buffer)
            val body = String(buffer)
            
            Log.d("TuyaServer", "Command body: $body")
            
            val json = JSONObject(body)
            val action = json.optString("action", "")
            val tuyaDeviceId = json.optString("tuya_device_id", "")
            val localKey = json.optString("local_key", "")
            val lanIp = json.optString("lan_ip", "auto")
            
            if (action !in listOf("on", "off")) {
                return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    "application/json",
                    JSONObject().apply {
                        put("ok", false)
                        put("error", "action deve ser 'on' ou 'off'")
                    }.toString()
                )
            }
            
            if (tuyaDeviceId.isEmpty() || localKey.isEmpty()) {
                return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    "application/json",
                    JSONObject().apply {
                        put("ok", false)
                        put("error", "tuya_device_id e local_key são obrigatórios")
                    }.toString()
                )
            }
            
            // Enviar comando para o dispositivo Tuya
            val success = sendTuyaCommand(action, tuyaDeviceId, localKey, lanIp)
            
            if (success) {
                return newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    JSONObject().apply {
                        put("ok", true)
                    }.toString()
                )
            } else {
                return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    "application/json",
                    JSONObject().apply {
                        put("ok", false)
                        put("error", "Erro ao enviar comando para o dispositivo")
                    }.toString()
                )
            }
            
        } catch (e: Exception) {
            Log.e("TuyaServer", "Erro ao processar comando", e)
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                JSONObject().apply {
                    put("ok", false)
                    put("error", e.message ?: "Erro desconhecido")
                }.toString()
            )
        }
    }
    
    private fun sendTuyaCommand(
        action: String,
        tuyaDeviceId: String,
        localKey: String,
        lanIp: String
    ): Boolean {
        try {
            Log.d("TuyaServer", "[$siteName] Enviando '$action' → $tuyaDeviceId @ $lanIp")
            
            // Se lanIp for "auto", tentar descobrir o IP
            var deviceIp: String = lanIp
            if (lanIp == "auto" || lanIp.isEmpty()) {
                Log.d("TuyaServer", "Descobrindo IP do dispositivo $tuyaDeviceId...")
                // Por enquanto, tentar descobrir (implementação simplificada)
                // Em produção, usar cache ou descoberta mais robusta
                val discoveredIp = discoverDeviceIp(tuyaDeviceId)
                if (discoveredIp == null) {
                    Log.e("TuyaServer", "Não foi possível descobrir IP do dispositivo")
                    return false
                }
                deviceIp = discoveredIp
            }
            
            // Enviar comando usando protocolo Tuya
            val success = com.mritsoftware.mritserver.tuya.TuyaProtocol.sendCommand(
                deviceId = tuyaDeviceId,
                localKey = localKey,
                ip = deviceIp,
                command = action,
                version = 3.3
            )
            
            if (!success && action == "on") {
                // Tentar com versão 3.4 se 3.3 falhar
                Log.d("TuyaServer", "Tentando com versão 3.4...")
                return com.mritsoftware.mritserver.tuya.TuyaProtocol.sendCommand(
                    deviceId = tuyaDeviceId,
                    localKey = localKey,
                    ip = deviceIp,
                    command = action,
                    version = 3.4
                )
            }
            
            Log.d("TuyaServer", "Comando enviado com sucesso")
            return success
            
        } catch (e: Exception) {
            Log.e("TuyaServer", "Erro ao enviar comando Tuya", e)
            return false
        }
    }
    
    private fun discoverDeviceIp(deviceId: String): String? {
        // Implementação simplificada - em produção usar cache ou descoberta mais robusta
        // Por enquanto retorna null - precisa implementar descoberta real
        return null
    }
    
    fun setSiteName(name: String) {
        siteName = name
        sharedPreferences.edit().putString("site_name", name).apply()
    }
    
    fun getSiteName(): String = siteName
}

