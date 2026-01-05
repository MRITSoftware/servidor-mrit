package com.mritsoftware.mritserver.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.mritsoftware.mritserver.service.PythonServerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NetworkChangeReceiver : BroadcastReceiver() {
    
    private val TAG = "NetworkChangeReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == ConnectivityManager.CONNECTIVITY_ACTION || 
            action == "android.net.conn.CONNECTIVITY_CHANGE") {
            Log.d(TAG, "Mudança de conectividade detectada")
            
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val isConnected = connectivityManager?.let { cm ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val network = cm.activeNetwork
                    val capabilities = network?.let { cm.getNetworkCapabilities(it) }
                    capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                } else {
                    @Suppress("DEPRECATION")
                    val activeNetwork = cm.activeNetworkInfo
                    activeNetwork?.isConnected == true
                }
            } ?: false
            
            if (isConnected) {
                Log.d(TAG, "Internet conectada - Verificando servidor...")
                
                // Aguardar alguns segundos para a rede estabilizar
                CoroutineScope(Dispatchers.IO).launch {
                    delay(5000) // 5 segundos
                    checkAndRestartServer(context)
                }
            } else {
                Log.d(TAG, "Internet desconectada")
            }
        }
    }
    
    private fun checkAndRestartServer(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Verificar se o servidor está respondendo
                val url = java.net.URL("http://127.0.0.1:8000/health")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                
                val responseCode = connection.responseCode
                connection.disconnect()
                
                if (responseCode == 200) {
                    Log.d(TAG, "Servidor está respondendo corretamente")
                } else {
                    Log.w(TAG, "Servidor não está respondendo (código: $responseCode) - Reiniciando...")
                    restartServer(context)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Servidor não está respondendo - Reiniciando...", e)
                restartServer(context)
            }
        }
    }
    
    private fun restartServer(context: Context) {
        try {
            Log.d(TAG, "Reiniciando PythonServerService...")
            
            // Parar o serviço atual
            val stopIntent = Intent(context, PythonServerService::class.java).apply {
                action = "STOP_SERVICE"
            }
            context.stopService(stopIntent)
            
            // Aguardar um pouco antes de reiniciar
            CoroutineScope(Dispatchers.IO).launch {
                delay(2000)
                
                // Reiniciar o serviço
                val startIntent = Intent(context, PythonServerService::class.java).apply {
                    setPackage(context.packageName)
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        context.startForegroundService(startIntent)
                        Log.d(TAG, "Serviço reiniciado com sucesso")
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao reiniciar serviço", e)
                    }
                } else {
                    context.startService(startIntent)
                    Log.d(TAG, "Serviço reiniciado com sucesso")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao reiniciar servidor", e)
        }
    }
}

