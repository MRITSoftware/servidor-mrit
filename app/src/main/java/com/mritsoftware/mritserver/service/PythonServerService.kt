package com.mritsoftware.mritserver.service

import android.app.*
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.mritsoftware.mritserver.MainActivity
import com.mritsoftware.mritserver.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PythonServerService : Service() {
    
    private val TAG = "PythonServerService"
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "tuya_server_channel"
    private var pythonThread: Thread? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service criado")
        createNotificationChannel()
        
        // Inicializar Python se ainda não foi inicializado
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
            Log.d(TAG, "Python inicializado")
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Iniciar como foreground service para rodar em background
        startForeground(NOTIFICATION_ID, createNotification())
        startPythonServer()
        return START_STICKY // Serviço será reiniciado se for morto
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Servidor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Servidor rodando em background"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MRIT Server")
            .setContentText("Servidor rodando na porta 8000")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun startPythonServer() {
        if (pythonThread?.isAlive == true) {
            Log.d(TAG, "Servidor Python já está rodando")
            return
        }
        
        // Atualizar site_name se necessário
        updateSiteName()
        
        pythonThread = Thread {
            try {
                val python = Python.getInstance()
                val module = python.getModule("tuya_server")
                
                Log.d(TAG, "Iniciando servidor Flask Python...")
                
                // Iniciar servidor Flask em thread separada
                module.callAttr("start_server", "0.0.0.0", 8000)
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao iniciar servidor Python", e)
            }
        }
        
        pythonThread?.start()
        Log.d(TAG, "Thread do servidor Python iniciada")
    }
    
    private fun updateSiteName() {
        try {
            val prefs = getSharedPreferences("TuyaGateway", MODE_PRIVATE)
            val siteName = prefs.getString("site_name", "ANDROID_DEVICE") ?: "ANDROID_DEVICE"
            
            val python = Python.getInstance()
            val module = python.getModule("tuya_server")
            
            // Garantir que config existe
            module.callAttr("create_config_if_needed")
            
            // Atualizar site_name
            module.callAttr("update_site_name", siteName)
            
            Log.d(TAG, "Site name configurado: $siteName")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar site name", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Parar thread do servidor
        pythonThread?.interrupt()
        pythonThread = null
        Log.d(TAG, "Service destruído")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    fun isServerRunning(): Boolean {
        return pythonThread?.isAlive == true
    }
}

