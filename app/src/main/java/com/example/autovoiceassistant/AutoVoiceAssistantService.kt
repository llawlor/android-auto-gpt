package com.example.autovoiceassistant

import android.content.Intent
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.service.media.MediaBrowserService
import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

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
                Log.d(TAG, "onPlay - Starting voice recognition")
                startVoiceRecognition()
            }
            
            override fun onStop() {
                Log.d(TAG, "onStop - Stopping voice recognition")
                stopVoiceRecognition()
            }
            
            override fun onPause() {
                Log.d(TAG, "onPause - Pausing voice recognition")
                voiceManager.stopListening()
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
    
    private fun updatePlaybackState(state: Int) {
        val playbackState = PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_STOP or PlaybackState.ACTION_PAUSE)
            .setState(state, 0, 1.0f)
            .build()
        mediaSession.setPlaybackState(playbackState)
    }
    
    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        Log.d(TAG, "onGetRoot: $clientPackageName")
        return BrowserRoot(MEDIA_ROOT_ID, null)
    }
    
    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowser.MediaItem>>) {
        Log.d(TAG, "onLoadChildren: $parentId")
        
        val mediaItems = mutableListOf<MediaBrowser.MediaItem>()
        
        if (parentId == MEDIA_ROOT_ID) {
            val voiceCommandItem = MediaBrowser.MediaItem(
                MediaBrowser.MediaItem.MediaDescription.Builder()
                    .setMediaId(VOICE_COMMAND_ID)
                    .setTitle("Voice Assistant")
                    .setSubtitle("Tap to start voice conversation")
                    .build(),
                MediaBrowser.MediaItem.FLAG_PLAYABLE
            )
            mediaItems.add(voiceCommandItem)
        }
        
        result.sendResult(mediaItems)
    }
    
    // VoiceManager.VoiceCallback implementations
    override fun onSpeechRecognized(text: String) {
        Log.d(TAG, "Speech recognized: $text")
        
        // Process the recognized speech with OpenAI
        serviceScope.launch {
            try {
                val response = openAIClient.sendMessage(text)
                response.onSuccess { aiResponse ->
                    Log.d(TAG, "AI Response: $aiResponse")
                    voiceManager.speak(aiResponse)
                }.onFailure { error ->
                    Log.e(TAG, "OpenAI API error", error)
                    val errorMessage = when {
                        error.message?.contains("API key") == true -> "Please set your OpenAI API key in settings"
                        error.message?.contains("network") == true -> "Network error. Please check your connection"
                        else -> "Sorry, I couldn't process your request right now"
                    }
                    voiceManager.speak(errorMessage)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing speech", e)
                voiceManager.speak("Sorry, there was an error processing your request")
            }
        }
    }
    
    override fun onSpeechError(error: String) {
        Log.e(TAG, "Speech error: $error")
        updatePlaybackState(PlaybackState.STATE_ERROR)
        
        val userFriendlyMessage = when {
            error.contains("No speech input") -> "I didn't hear anything. Please try again."
            error.contains("Network") -> "Network error. Please check your connection."
            error.contains("permissions") -> "Microphone permission is required."
            else -> "Sorry, I couldn't understand. Please try again."
        }
        
        voiceManager.speak(userFriendlyMessage)
    }
    
    override fun onSpeechStarted() {
        Log.d(TAG, "Speech started")
        updatePlaybackState(PlaybackState.STATE_PLAYING)
    }
    
    override fun onSpeechFinished() {
        Log.d(TAG, "Speech finished")
        // Keep in playing state while processing
    }
    
    override fun onTTSStarted() {
        Log.d(TAG, "TTS started")
        updatePlaybackState(PlaybackState.STATE_PLAYING)
    }
    
    override fun onTTSFinished() {
        Log.d(TAG, "TTS finished")
        updatePlaybackState(PlaybackState.STATE_STOPPED)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        
        voiceManager.destroy()
        mediaSession.release()
        serviceScope.cancel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}
