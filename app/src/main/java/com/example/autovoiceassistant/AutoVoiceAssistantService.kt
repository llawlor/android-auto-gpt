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
    private lateinit var perplexityClient: PerplexityClient
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
                updateMediaMetadata(
                    "Voice Commands", 
                    "How to use", 
                    "Use your voice button and say: 'Search [your question] on Auto Voice Assistant'. For example: 'Search what's the weather like on Auto Voice Assistant'"
                )
                voiceManager.speak("Hello! I'm your AI assistant. Use your voice button and say: Search what's the weather like on Auto Voice Assistant. You can ask me anything using the Search pattern!")
                updatePlaybackState(PlaybackState.STATE_PAUSED)
            }
            
            override fun onStop() {
                Log.d(TAG, "onStop - Stopping voice recognition and speech")
                stopVoiceRecognition()
            }
            
            override fun onPause() {
                Log.d(TAG, "onPause - Pausing voice recognition and stopping speech")
                voiceManager.stopListening()
                voiceManager.stopSpeaking()
                updatePlaybackState(PlaybackState.STATE_PAUSED, "Paused")
            }
            
            override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                Log.d(TAG, "onPlayFromSearch - Voice search query: $query")
                // Handle voice search from Android Auto
                if (query.isNullOrBlank()) {
                    // No query provided, prompt user with proper command format
                    updateMediaMetadata(
                        "No Query Received", 
                        "Try again", 
                        "Please say 'Search [your question] on Auto Voice Assistant' to get started."
                    )
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
        
        // Initialize Perplexity client - You'll need to set your API key
        val apiKey = getApiKeyFromPreferences() // Implement this method
        perplexityClient = PerplexityClient(apiKey)
        
        // Initialize TTS and set welcome screen
        serviceScope.launch {
            val ttsInitialized = voiceManager.initializeTextToSpeech()
            if (ttsInitialized) {
                Log.d(TAG, "Text-to-Speech initialized successfully")
                updateMediaMetadata(
                    "AI Voice Assistant Ready", 
                    "Press play to start", 
                    "Say 'Search [your question] on Auto Voice Assistant' to get started. You can ask me anything!"
                )
                voiceManager.speak("Voice assistant ready. Press play to start talking.")
            } else {
                Log.e(TAG, "Failed to initialize Text-to-Speech")
                updateMediaMetadata(
                    "Setup Error", 
                    "TTS initialization failed", 
                    "Text-to-Speech could not be initialized. Please restart the app."
                )
            }
        }
        
        updatePlaybackState(PlaybackState.STATE_STOPPED)
    }
    
    private fun getApiKeyFromPreferences(): String {
        // In a real app, store this securely in SharedPreferences or use a secure storage solution
        // For now, return a placeholder - user needs to set this
        val prefs = getSharedPreferences("voice_assistant_prefs", MODE_PRIVATE)
        return prefs.getString("perplexity_api_key", "") ?: ""
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
            .setActions(
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_STOP or
                PlaybackState.ACTION_PLAY_FROM_SEARCH
            )
            .setState(state, 0, 1.0f)
            .setErrorMessage(if (state == PlaybackState.STATE_ERROR) statusText else null)
            .build()
        
        mediaSession.setPlaybackState(playbackState)
        
        Log.d(TAG, "Playback state updated: $state, status: $statusText")
    }
    
    private fun updateMediaMetadata(title: String, subtitle: String = "", description: String = "") {
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "AI Voice Assistant")
            .putString(MediaMetadata.METADATA_KEY_ALBUM, subtitle)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, subtitle)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION, description)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, -1) // Unknown duration
            .build()
        
        mediaSession.setMetadata(metadata)
        Log.d(TAG, "Media metadata updated: title='$title', subtitle='$subtitle'")
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
        
        // Display the query being processed
        updateMediaMetadata("Processing Query", "Thinking...", query)
        
        // Check for special commands
        val normalizedQuery = query.lowercase().trim()
        if (normalizedQuery.contains("clear conversation") || 
            normalizedQuery.contains("start new conversation") ||
            normalizedQuery.contains("forget previous questions")) {
            clearConversationHistory()
            return
        }
        
        // Validate query length and content
        if (query.isBlank()) {
            Log.w(TAG, "Empty query received")
            updateMediaMetadata("Error", "No Query", "I didn't receive a question. Please try asking again.")
            updatePlaybackState(PlaybackState.STATE_ERROR, "No query provided")
            voiceManager.speak("I didn't receive a question. Please try asking again.")
            return
        }
        
        if (query.length > 500) {
            Log.w(TAG, "Query too long: ${query.length} characters")
            updateMediaMetadata("Error", "Query Too Long", "Your question is too long. Please try asking a shorter question.")
            updatePlaybackState(PlaybackState.STATE_ERROR, "Query too long")
            voiceManager.speak("Your question is too long. Please try asking a shorter question.")
            return
        }
        
        // Process the voice query with Perplexity
        serviceScope.launch {
            try {
                Log.d(TAG, "Starting Perplexity request for query: ${query.take(50)}...")
                updatePlaybackState(PlaybackState.STATE_BUFFERING, "Connecting to AI...")
                
                val response = perplexityClient.sendMessage(query)
                response.onSuccess { aiResponse ->
                    Log.d(TAG, "Successfully received AI response (${aiResponse.length} chars)")
                    
                    // Validate response before attempting to speak
                    if (aiResponse.isBlank()) {
                        Log.w(TAG, "Received blank response from Perplexity")
                        updatePlaybackState(PlaybackState.STATE_ERROR, "Empty response received")
                        updateMediaMetadata("Error", "Empty Response", "I received an empty response. Please try asking your question again.")
                        voiceManager.speak("I received an empty response. Please try asking your question again.")
                        return@onSuccess
                    }
                    
                    // Display the response on screen
                    val displayTitle = "AI Response"
                    val displaySubtitle = "Query: ${query.take(50)}${if (query.length > 50) "..." else ""}"
                    
                    if (aiResponse.length > 1000) {
                        Log.w(TAG, "Response is very long (${aiResponse.length} chars), truncating for TTS")
                        val truncatedResponse = aiResponse.take(800) + "... That's the main point."
                        
                        // Show full response on screen, speak truncated version
                        updateMediaMetadata(displayTitle, displaySubtitle, aiResponse)
                        updatePlaybackState(PlaybackState.STATE_PLAYING, "Speaking response...")
                        voiceManager.speak(truncatedResponse)
                    } else {
                        // Show response on screen and speak it
                        updateMediaMetadata(displayTitle, displaySubtitle, aiResponse)
                        updatePlaybackState(PlaybackState.STATE_PLAYING, "Speaking response...")
                        voiceManager.speak(aiResponse)
                    }
                }.onFailure { error ->
                    Log.e(TAG, "Perplexity API error for query: ${error.message}", error)
                    
                    // Use the detailed error messages from the enhanced Perplexity client
                    val errorMessage = error.message ?: "Sorry, I couldn't process your request right now"
                    
                    // Provide user-friendly feedback based on error type
                    val userMessage = when {
                        errorMessage.contains("API key", ignoreCase = true) -> {
                            "Perplexity API key issue. Please check your settings."
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
                    
                    // Display error on screen
                    val errorTitle = "Error"
                    val errorSubtitle = "Query: ${query.take(50)}${if (query.length > 50) "..." else ""}"
                    updateMediaMetadata(errorTitle, errorSubtitle, userMessage)
                    updatePlaybackState(PlaybackState.STATE_ERROR, "Error: $userMessage")
                    voiceManager.speak(userMessage)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error processing voice query", e)
                val errorSubtitle = "Query: ${query.take(50)}${if (query.length > 50) "..." else ""}"
                updateMediaMetadata("Unexpected Error", errorSubtitle, "Sorry, there was an unexpected error. Please try again.")
                updatePlaybackState(PlaybackState.STATE_ERROR, "Unexpected error")
                voiceManager.speak("Sorry, there was an unexpected error. Please try again.")
            }
        }
    }
    
    private fun clearConversationHistory() {
        perplexityClient.clearConversationHistory()
        updateMediaMetadata(
            "Conversation Cleared", 
            "Fresh start", 
            "I've cleared our conversation history. You can start asking new questions."
        )
        updatePlaybackState(PlaybackState.STATE_STOPPED, "Conversation cleared")
        voiceManager.speak("I've cleared our conversation history. You can start asking new questions.")
        Log.d(TAG, "Conversation history cleared by user request")
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
