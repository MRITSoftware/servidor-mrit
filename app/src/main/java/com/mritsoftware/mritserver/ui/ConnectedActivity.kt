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
    private lateinit var siteNameText: TextView
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
        siteNameText = findViewById(R.id.siteNameText)
        
        // Exibir nome do site
        val siteName = sharedPreferences.getString("site_name", "Unidade")
        siteNameText.text = siteName
        
        // Aplicar insets para status bar
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
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

