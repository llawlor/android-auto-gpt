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
        
        // Show processing status
        updatePlaybackState(PlaybackState.STATE_BUFFERING, "Processing: $text")
        
        // Process the recognized speech with OpenAI
        serviceScope.launch {
            try {
                val response = openAIClient.sendMessage(text)
                response.onSuccess { aiResponse ->
                    Log.d(TAG, "AI Response: $aiResponse")
                    updatePlaybackState(PlaybackState.STATE_PLAYING, "Speaking response...")
                    voiceManager.speak(aiResponse)
                }.onFailure { error ->
                    Log.e(TAG, "OpenAI API error", error)
                    val errorMessage = when {
                        error.message?.contains("API key") == true -> "Please set your OpenAI API key in settings"
                        error.message?.contains("network") == true -> "Network error. Please check your connection"
                        else -> "Sorry, I couldn't process your request right now"
                    }
                    updatePlaybackState(PlaybackState.STATE_ERROR, "Error: $errorMessage")
                    voiceManager.speak(errorMessage)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing speech", e)
                updatePlaybackState(PlaybackState.STATE_ERROR, "Error processing request")
                voiceManager.speak("Sorry, there was an error processing your request")
            }
        }
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
        
        // Process the voice query with OpenAI
        serviceScope.launch {
            try {
                val response = openAIClient.sendMessage(query)
                response.onSuccess { aiResponse ->
                    Log.d(TAG, "AI Response to query: $aiResponse")
                    updatePlaybackState(PlaybackState.STATE_PLAYING, "Speaking response...")
                    voiceManager.speak(aiResponse)
                }.onFailure { error ->
                    Log.e(TAG, "OpenAI API error for query", error)
                    val errorMessage = when {
                        error.message?.contains("API key") == true -> "Please set your OpenAI API key in settings"
                        error.message?.contains("network") == true -> "Network error. Please check your connection"
                        else -> "Sorry, I couldn't process your request right now"
                    }
                    updatePlaybackState(PlaybackState.STATE_ERROR, "Error: $errorMessage")
                    voiceManager.speak(errorMessage)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing voice query", e)
                updatePlaybackState(PlaybackState.STATE_ERROR, "Error processing request")
                voiceManager.speak("Sorry, there was an error processing your request")
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
