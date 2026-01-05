package com.mritsoftware.mritserver.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.mritsoftware.mritserver.MainActivity
import com.mritsoftware.mritserver.R

class LoginActivity : AppCompatActivity() {
    
    private lateinit var apiKeyInput: TextInputEditText
    private lateinit var apiSecretInput: TextInputEditText
    private lateinit var loginButton: MaterialButton
    private lateinit var sharedPreferences: SharedPreferences
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sharedPreferences = getSharedPreferences("TuyaGateway", MODE_PRIVATE)
        
        // Verificar se já está logado
        if (isLoggedIn()) {
            startMainActivity()
            return
        }
        
        setContentView(R.layout.activity_login)
        
        setupViews()
        setupListeners()
    }
    
    private fun setupViews() {
        apiKeyInput = findViewById(R.id.apiKeyInput)
        apiSecretInput = findViewById(R.id.apiSecretInput)
        loginButton = findViewById(R.id.loginButton)
        
        // Preencher com valores salvos se existirem
        apiKeyInput.setText(sharedPreferences.getString("api_key", ""))
        apiSecretInput.setText(sharedPreferences.getString("api_secret", ""))
    }
    
    private fun setupListeners() {
        loginButton.setOnClickListener {
            val apiKey = apiKeyInput.text?.toString() ?: ""
            val apiSecret = apiSecretInput.text?.toString() ?: ""
            
            if (apiKey.isBlank() || apiSecret.isBlank()) {
                Toast.makeText(this, "Por favor, preencha todos os campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Salvar credenciais
            sharedPreferences.edit()
                .putString("api_key", apiKey)
                .putString("api_secret", apiSecret)
                .putBoolean("is_logged_in", true)
                .apply()
            
            Toast.makeText(this, "Conectando...", Toast.LENGTH_SHORT).show()
            startMainActivity()
        }
    }
    
    private fun isLoggedIn(): Boolean {
        return sharedPreferences.getBoolean("is_logged_in", false) &&
               sharedPreferences.getString("api_key", null) != null
    }
    
    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}


