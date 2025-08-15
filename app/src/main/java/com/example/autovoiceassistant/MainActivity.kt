package com.example.autovoiceassistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
        handleGoogleAssistantIntent()
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
    
    private fun handleGoogleAssistantIntent() {
        val intent = intent
        val action = intent.action
        val data = intent.data
        
        Log.d("MainActivity", "Intent action: $action, data: $data")
        
        when (action) {
            Intent.ACTION_VIEW -> {
                // Handle deep link from Google Assistant
                data?.let { uri ->
                    if (uri.scheme == "autovoiceassistant") {
                        val path = uri.host ?: ""
                        val query = uri.getQueryParameter("query") ?: uri.getQueryParameter("location")
                        
                        Log.d("MainActivity", "Google Assistant deep link - path: $path, query: $query")
                        
                        when (path) {
                            "conversation" -> {
                                // Handle "talk to Auto Voice Assistant"
                                if (!query.isNullOrBlank()) {
                                    handleAssistantQuery(query)
                                } else {
                                    // Start conversation mode
                                    handleAssistantQuery("Hello, how can I help you?")
                                }
                            }
                            "ask" -> {
                                // Handle "ask Auto Voice Assistant about..."
                                if (!query.isNullOrBlank()) {
                                    handleAssistantQuery(query)
                                }
                            }
                            "weather" -> {
                                // Handle weather-specific queries
                                val location = query ?: "current location"
                                handleAssistantQuery("What's the weather like in $location?")
                            }
                            "question" -> {
                                // Handle general questions
                                if (!query.isNullOrBlank()) {
                                    handleAssistantQuery(query)
                                }
                            }
                            "text" -> {
                                // Handle text-based queries
                                if (!query.isNullOrBlank()) {
                                    handleAssistantQuery(query)
                                }
                            }
                            else -> {
                                // Fallback for any other deep links
                                if (!query.isNullOrBlank()) {
                                    handleAssistantQuery(query)
                                }
                            }
                        }
                    }
                }
            }
            Intent.ACTION_SEARCH, Intent.ACTION_VOICE_COMMAND -> {
                // Handle voice search intent
                val query = intent.getStringExtra("query") ?: intent.getStringExtra("android.intent.extra.TEXT")
                Log.d("MainActivity", "Voice search query: $query")
                if (!query.isNullOrBlank()) {
                    handleAssistantQuery(query)
                }
            }
            Intent.ACTION_ASSIST -> {
                // Handle Google Assistant assist intent
                val query = intent.getStringExtra("android.intent.extra.TEXT") ?: 
                           intent.getStringExtra("query") ?:
                           intent.getStringExtra("android.intent.extra.ASSIST_INPUT_HINT_TEXT")
                Log.d("MainActivity", "Assistant query: $query")
                if (!query.isNullOrBlank()) {
                    handleAssistantQuery(query)
                } else {
                    // No specific query, start general conversation
                    handleAssistantQuery("Hello, I'm your AI assistant. How can I help you today?")
                }
            }
            Intent.ACTION_SEND -> {
                // Handle text sent from Google Assistant
                if (intent.type == "text/plain") {
                    val query = intent.getStringExtra(Intent.EXTRA_TEXT)
                    Log.d("MainActivity", "Text sent from Assistant: $query")
                    if (!query.isNullOrBlank()) {
                        handleAssistantQuery(query)
                    }
                }
            }
        }
    }
    
    private fun handleAssistantQuery(query: String) {
        Log.d("MainActivity", "Processing Assistant query: $query")
        
        // Start the voice service to handle the query
        val serviceIntent = Intent(this, AutoVoiceAssistantService::class.java)
        serviceIntent.putExtra("voice_query", query)
        startService(serviceIntent)
        
        // Show feedback to user
        Toast.makeText(this, "Processing: $query", Toast.LENGTH_SHORT).show()
        
        // Optionally finish the activity to return to Android Auto
        finish()
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
