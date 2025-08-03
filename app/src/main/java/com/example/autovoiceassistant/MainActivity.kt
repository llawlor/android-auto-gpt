package com.example.autovoiceassistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.autovoiceassistant.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val RECORD_AUDIO_PERMISSION_CODE = 1001
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        checkPermissions()
    }
    
    private fun setupUI() {
        binding.titleText.text = "AI Voice Assistant"
        binding.descriptionText.text = "Connect to Android Auto to use voice commands while driving"
        
        binding.settingsButton.setOnClickListener {
            // Open settings to configure API key
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
        
        binding.testButton.setOnClickListener {
            // Test voice recognition locally
            testVoiceRecognition()
        }
        
        // Start the Android Auto service
        val serviceIntent = Intent(this, AutoVoiceAssistantService::class.java)
        startService(serviceIntent)
    }
    
    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_CODE
            )
        }
    }
    
    private fun testVoiceRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            == PackageManager.PERMISSION_GRANTED) {
            
            // Check if API key is configured
            val prefs = getSharedPreferences("voice_assistant_prefs", MODE_PRIVATE)
            val apiKey = prefs.getString("openai_api_key", "")
            
            if (apiKey.isNullOrEmpty()) {
                Toast.makeText(this, "Please configure your OpenAI API key in settings", Toast.LENGTH_LONG).show()
                return
            }
            
            Toast.makeText(this, "Voice test feature coming soon!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            RECORD_AUDIO_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Microphone permission is required for voice recognition", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
