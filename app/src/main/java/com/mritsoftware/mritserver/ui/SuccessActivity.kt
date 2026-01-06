package com.mritsoftware.mritserver.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.mritsoftware.mritserver.MainActivity
import com.mritsoftware.mritserver.R

class SuccessActivity : AppCompatActivity() {
    
    private lateinit var continueButton: MaterialButton
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_success)
        
        continueButton = findViewById(R.id.continueButton)
        
        continueButton.setOnClickListener {
            startMainActivity()
        }
        
        // Auto-redirect após 3 segundos
        Handler(Looper.getMainLooper()).postDelayed({
            startMainActivity()
        }, 3000)
    }
    
    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

