package com.mritsoftware.mritserver.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.mritsoftware.mritserver.server.TuyaServer
import fi.iki.elonen.NanoHTTPD

class ServerService : Service() {
    
    private var tuyaServer: TuyaServer? = null
    private val port = 8000
    
    override fun onCreate() {
        super.onCreate()
        Log.d("ServerService", "Service criado")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startServer()
        return START_STICKY // Serviço será reiniciado se for morto
    }
    
    private fun startServer() {
        if (tuyaServer == null) {
            try {
                tuyaServer = TuyaServer(this, port)
                tuyaServer?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                Log.d("ServerService", "Servidor Tuya iniciado na porta $port")
            } catch (e: Exception) {
                Log.e("ServerService", "Erro ao iniciar servidor", e)
            }
        }
    }
    
    private fun stopServer() {
        tuyaServer?.stop()
        tuyaServer = null
        Log.d("ServerService", "Servidor Tuya parado")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopServer()
        Log.d("ServerService", "Service destruído")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    fun isServerRunning(): Boolean {
        return tuyaServer?.isAlive == true
    }
    
    fun getServerUrl(): String {
        return "http://0.0.0.0:$port"
    }
}

