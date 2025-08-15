package com.example.autovoiceassistant

import android.content.Intent
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.service.media.MediaBrowserService
import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import kotlinx.coroutines.delay

class AutoVoiceAssistantService : MediaBrowserService(), VoiceManager.VoiceCallback {
    
    private lateinit var mediaSession: MediaSession
    private lateinit var voiceManager: VoiceManager
    private lateinit var openAIClient: OpenAIClient
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    companion object {
        private const val TAG = "AutoVoiceAssistant"
        private const val MEDIA_ROOT_ID = "voice_assistant_root"
        private const val VOICE_COMMAND_ID = "voice_command"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        // Initialize MediaSession for Android Auto
        mediaSession = MediaSession(this, TAG)
        mediaSession.setCallback(object : MediaSession.Callback() {
            override fun onPlay() {
                Log.d(TAG, "onPlay - Providing voice command instructions")
                voiceManager.speak("Hello! I'm your AI assistant. Use your voice button and say: Search what's the weather like on Auto Voice Assistant. You can ask me anything using the Search pattern!")
                updatePlaybackState(PlaybackState.STATE_PAUSED)
            }
            
            override fun onStop() {
                Log.d(TAG, "onStop - Stopping voice recognition")
                stopVoiceRecognition()
            }
            
            override fun onPause() {
                Log.d(TAG, "onPause - Pausing voice recognition")
                voiceManager.stopListening()
            }
            
            override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                Log.d(TAG, "onPlayFromSearch - Voice search query: $query")
                // Handle voice search from Android Auto
                if (query.isNullOrBlank()) {
                    // No query provided, prompt user with proper command format
                    voiceManager.speak("I didn't receive your question. Please say 'Search your question on Auto Voice Assistant' to get started.")
                } else {
                    handleVoiceQuery(query)
                }
            }
        })
        
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        sessionToken = mediaSession.sessionToken
        
        // Initialize voice manager
        voiceManager = VoiceManager(this)
        voiceManager.setCallback(this)
        voiceManager.initializeSpeechRecognizer()
        
        // Initialize OpenAI client - You'll need to set your API key
        val apiKey = getApiKeyFromPreferences() // Implement this method
        openAIClient = OpenAIClient(apiKey)
        
        // Initialize TTS
        serviceScope.launch {
            val ttsInitialized = voiceManager.initializeTextToSpeech()
            if (ttsInitialized) {
                Log.d(TAG, "Text-to-Speech initialized successfully")
                voiceManager.speak("Voice assistant ready. Press play to start talking.")
            } else {
                Log.e(TAG, "Failed to initialize Text-to-Speech")
            }
        }
        
        updatePlaybackState(PlaybackState.STATE_STOPPED)
    }
    
    private fun getApiKeyFromPreferences(): String {
        // In a real app, store this securely in SharedPreferences or use a secure storage solution
        // For now, return a placeholder - user needs to set this
        val prefs = getSharedPreferences("voice_assistant_prefs", MODE_PRIVATE)
        return prefs.getString("openai_api_key", "") ?: ""
    }
    
    private fun startVoiceRecognition() {
        if (!voiceManager.isCurrentlyListening() && !voiceManager.isCurrentlySpeaking()) {
            updatePlaybackState(PlaybackState.STATE_PLAYING)
            voiceManager.startListening()
        }
    }
    
    private fun stopVoiceRecognition() {
        voiceManager.stopListening()
        voiceManager.stopSpeaking()
        updatePlaybackState(PlaybackState.STATE_STOPPED)
    }
    
    private fun updatePlaybackState(state: Int, statusText: String = "") {
        val playbackState = PlaybackState.Builder()
            .setState(state, 0, 1.0f)
            .build()
        mediaSession.setPlaybackState(playbackState)
        
        // Update metadata to show current status
        if (statusText.isNotEmpty()) {
            val metadata = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "AI Voice Assistant")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, statusText)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, "AI Voice Assistant")
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, statusText)
                .build()
            mediaSession.setMetadata(metadata)
        }
    }
    
    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        Log.d(TAG, "onGetRoot: $clientPackageName")
        return BrowserRoot(MEDIA_ROOT_ID, null)
    }
    
    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowser.MediaItem>>) {
        Log.d(TAG, "onLoadChildren called with parentId: $parentId")
        
        val mediaItems = mutableListOf<MediaBrowser.MediaItem>()
        
        if (parentId == MEDIA_ROOT_ID) {
            // Create a voice command item
            val description = MediaDescription.Builder()
                .setMediaId(VOICE_COMMAND_ID)
                .setTitle("Voice Assistant")
                .setSubtitle("Tap to start voice conversation")
                .build()
            
            val mediaItem = MediaBrowser.MediaItem(description, MediaBrowser.MediaItem.FLAG_PLAYABLE)
            mediaItems.add(mediaItem)
        }
        
        result.sendResult(mediaItems)
    }
    

    
    // VoiceManager.VoiceCallback implementations
    override fun onSpeechRecognized(text: String) {
        Log.d(TAG, "Speech recognized: $text")
        
        // Use the same robust handling as handleVoiceQuery
        handleVoiceQuery(text)
    }
    
    override fun onSpeechError(error: String) {
        Log.e(TAG, "Speech error: $error")
        
        val userFriendlyMessage = when {
            error.contains("No speech input") -> "I didn't hear anything. Please try again."
            error.contains("Network") -> "Network error. Please check your connection."
            error.contains("permissions") -> "Microphone permission is required."
            else -> "Sorry, I couldn't understand. Please try again."
        }
        
        updatePlaybackState(PlaybackState.STATE_ERROR, "Error: $userFriendlyMessage")
        voiceManager.speak(userFriendlyMessage)
    }
    
    override fun onSpeechStarted() {
        Log.d(TAG, "Speech started")
        updatePlaybackState(PlaybackState.STATE_PLAYING, "Listening...")
    }
    
    override fun onSpeechFinished() {
        Log.d(TAG, "Speech finished")
        updatePlaybackState(PlaybackState.STATE_BUFFERING, "Processing your request...")
    }
    
    override fun onTTSStarted() {
        Log.d(TAG, "TTS started")
        updatePlaybackState(PlaybackState.STATE_PLAYING, "Speaking response...")
    }
    
    override fun onTTSFinished() {
        Log.d(TAG, "TTS finished")
        updatePlaybackState(PlaybackState.STATE_STOPPED, "Ready for next question")
    }
    
    private fun handleVoiceQuery(query: String) {
        Log.d(TAG, "Handling voice query: $query")
        updatePlaybackState(PlaybackState.STATE_BUFFERING, "Processing: $query")
        
        // Validate query length and content
        if (query.isBlank()) {
            Log.w(TAG, "Empty query received")
            updatePlaybackState(PlaybackState.STATE_ERROR, "No query provided")
            voiceManager.speak("I didn't receive a question. Please try asking again.")
            return
        }
        
        if (query.length > 500) {
            Log.w(TAG, "Query too long: ${query.length} characters")
            updatePlaybackState(PlaybackState.STATE_ERROR, "Query too long")
            voiceManager.speak("Your question is too long. Please try asking a shorter question.")
            return
        }
        
        // Process the voice query with OpenAI
        serviceScope.launch {
            try {
                Log.d(TAG, "Starting OpenAI request for query: ${query.take(50)}...")
                updatePlaybackState(PlaybackState.STATE_BUFFERING, "Connecting to AI...")
                
                val response = openAIClient.sendMessage(query)
                response.onSuccess { aiResponse ->
                    Log.d(TAG, "Successfully received AI response (${aiResponse.length} chars)")
                    
                    // Validate response before attempting to speak
                    if (aiResponse.isBlank()) {
                        Log.w(TAG, "Received blank response from OpenAI")
                        updatePlaybackState(PlaybackState.STATE_ERROR, "Empty response received")
                        voiceManager.speak("I received an empty response. Please try asking your question again.")
                        return@onSuccess
                    }
                    
                    if (aiResponse.length > 1000) {
                        Log.w(TAG, "Response is very long (${aiResponse.length} chars), truncating for TTS")
                        val truncatedResponse = aiResponse.take(800) + "... That's the main point."
                        updatePlaybackState(PlaybackState.STATE_PLAYING, "Speaking response...")
                        voiceManager.speak(truncatedResponse)
                    } else {
                        updatePlaybackState(PlaybackState.STATE_PLAYING, "Speaking response...")
                        voiceManager.speak(aiResponse)
                    }
                }.onFailure { error ->
                    Log.e(TAG, "OpenAI API error for query: ${error.message}", error)
                    
                    // Use the detailed error messages from the enhanced OpenAI client
                    val errorMessage = error.message ?: "Sorry, I couldn't process your request right now"
                    
                    // Provide user-friendly feedback based on error type
                    val userMessage = when {
                        errorMessage.contains("API key", ignoreCase = true) -> {
                            "OpenAI API key issue. Please check your settings."
                        }
                        errorMessage.contains("network", ignoreCase = true) || 
                        errorMessage.contains("connection", ignoreCase = true) -> {
                            "Network connection problem. Please check your internet and try again."
                        }
                        errorMessage.contains("timeout", ignoreCase = true) -> {
                            "Request timed out. Please try again with a simpler question."
                        }
                        errorMessage.contains("rate limit", ignoreCase = true) -> {
                            "Too many requests. Please wait a moment and try again."
                        }
                        errorMessage.contains("unavailable", ignoreCase = true) -> {
                            "AI service is temporarily unavailable. Please try again in a few minutes."
                        }
                        else -> {
                            "Sorry, I couldn't process your request. Please try again."
                        }
                    }
                    
                    updatePlaybackState(PlaybackState.STATE_ERROR, "Error: $userMessage")
                    voiceManager.speak(userMessage)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error processing voice query", e)
                updatePlaybackState(PlaybackState.STATE_ERROR, "Unexpected error")
                voiceManager.speak("Sorry, there was an unexpected error. Please try again.")
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        
        voiceManager.destroy()
        mediaSession.release()
        serviceScope.cancel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle voice query from Google Assistant
        intent?.getStringExtra("voice_query")?.let { query ->
            Log.d(TAG, "Received voice query from Google Assistant: $query")
            handleVoiceQuery(query)
        }
        return START_STICKY
    }
}
