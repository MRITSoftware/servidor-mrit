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

class WelcomeActivity : AppCompatActivity() {
    
    private lateinit var siteNameInput: TextInputEditText
    private lateinit var continueButton: MaterialButton
    private lateinit var sharedPreferences: SharedPreferences
    
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
        setupListeners()
    }
    
    private fun setupViews() {
        siteNameInput = findViewById(R.id.siteNameInput)
        continueButton = findViewById(R.id.continueButton)
    }
    
    private fun setupListeners() {
        continueButton.setOnClickListener {
            val siteName = siteNameInput.text?.toString()?.trim() ?: ""
            
            if (siteName.isBlank()) {
                Toast.makeText(this, "Por favor, digite o nome do site/tablet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Salvar nome do site
            sharedPreferences.edit()
                .putString("site_name", siteName)
                .putBoolean("welcome_completed", true)
                .apply()
            
            // Atualizar no Python também (será feito quando o servidor iniciar)
            Toast.makeText(this, "Configuração salva!", Toast.LENGTH_SHORT).show()
            startMainActivity()
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
}


