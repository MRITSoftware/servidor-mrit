package com.mritsoftware.mritserver.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.mritsoftware.mritserver.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var siteNameInput: TextInputEditText
    private lateinit var testButton: MaterialButton
    private lateinit var saveButton: MaterialButton
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        setupToolbar()
        setupViews()
        setupListeners()
    }
    
    private fun setupToolbar() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Configurações"
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    private fun setupViews() {
        siteNameInput = findViewById(R.id.siteNameInput)
        testButton = findViewById(R.id.testButton)
        saveButton = findViewById(R.id.saveButton)
        
        val prefs = getSharedPreferences("TuyaGateway", MODE_PRIVATE)
        siteNameInput.setText(prefs.getString("site_name", "ANDROID_DEVICE") ?: "ANDROID_DEVICE")
    }
    
    private fun setupListeners() {
        testButton.setOnClickListener {
            testConnection()
        }
        
        saveButton.setOnClickListener {
            saveSettings()
        }
    }
    
    private fun testConnection() {
        testButton.isEnabled = false
        testButton.text = "Testando..."
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = java.net.URL("http://127.0.0.1:8000/health")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 2000
                connection.readTimeout = 2000
                
                val responseCode = connection.responseCode
                val response = if (responseCode == 200) {
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(connection.inputStream))
                    val responseText = reader.readText()
                    reader.close()
                    org.json.JSONObject(responseText)
                } else {
                    null
                }
                
                connection.disconnect()
                
                CoroutineScope(Dispatchers.Main).launch {
                    testButton.isEnabled = true
                    testButton.text = "Testar Conexão"
                    
                    if (responseCode == 200 && response != null) {
                        val site = response.optString("site", "N/A")
                        Toast.makeText(
                            this@SettingsActivity,
                            "Servidor OK! Site: $site",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this@SettingsActivity,
                            "Servidor não está respondendo. Aguarde alguns segundos.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                CoroutineScope(Dispatchers.Main).launch {
                    testButton.isEnabled = true
                    testButton.text = "Testar Conexão"
                    Toast.makeText(
                        this@SettingsActivity,
                        "Erro: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    private fun saveSettings() {
        val siteName = siteNameInput.text?.toString()?.trim() ?: ""
        
        if (siteName.isBlank()) {
            Toast.makeText(this, "Digite o nome do site", Toast.LENGTH_SHORT).show()
            return
        }
        
        val prefs = getSharedPreferences("TuyaGateway", MODE_PRIVATE)
        prefs.edit().putString("site_name", siteName).apply()
        
        Toast.makeText(this, "Configurações salvas! Reinicie o app para aplicar.", Toast.LENGTH_LONG).show()
        finish()
    }
}

