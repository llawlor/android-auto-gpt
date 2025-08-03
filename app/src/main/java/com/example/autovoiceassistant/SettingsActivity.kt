package com.example.autovoiceassistant

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.autovoiceassistant.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        loadSettings()
    }
    
    private fun setupUI() {
        binding.titleText.text = "Settings"
        
        binding.saveButton.setOnClickListener {
            saveSettings()
        }
        
        binding.backButton.setOnClickListener {
            finish()
        }
        
        // Add help text for API key
        binding.apiKeyHelpText.text = "Get your API key from https://platform.openai.com/api-keys"
    }
    
    private fun loadSettings() {
        val prefs = getSharedPreferences("voice_assistant_prefs", MODE_PRIVATE)
        val apiKey = prefs.getString("openai_api_key", "")
        
        // Show masked API key if it exists
        if (!apiKey.isNullOrEmpty()) {
            binding.apiKeyEditText.setText("sk-" + "*".repeat(apiKey.length - 3))
        }
    }
    
    private fun saveSettings() {
        val apiKey = binding.apiKeyEditText.text.toString().trim()
        
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Please enter your OpenAI API key", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!apiKey.startsWith("sk-")) {
            Toast.makeText(this, "OpenAI API key should start with 'sk-'", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Don't save if it's the masked version
        if (apiKey.contains("*")) {
            Toast.makeText(this, "API key already saved", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        val prefs = getSharedPreferences("voice_assistant_prefs", MODE_PRIVATE)
        prefs.edit()
            .putString("openai_api_key", apiKey)
            .apply()
        
        Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show()
        finish()
    }
}
