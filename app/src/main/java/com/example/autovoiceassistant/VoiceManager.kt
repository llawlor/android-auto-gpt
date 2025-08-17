package com.example.autovoiceassistant

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.*
import kotlin.coroutines.resume

class VoiceManager(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isListening = false
    private var isSpeaking = false
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    
    companion object {
        private const val TAG = "VoiceManager"
    }
    
    interface VoiceCallback {
        fun onSpeechRecognized(text: String)
        fun onSpeechError(error: String)
        fun onSpeechStarted()
        fun onSpeechFinished()
        fun onTTSStarted()
        fun onTTSFinished()
    }
    
    private var callback: VoiceCallback? = null
    
    fun setCallback(callback: VoiceCallback) {
        this.callback = callback
    }
    
    suspend fun initializeTextToSpeech(): Boolean = suspendCancellableCoroutine { continuation ->
        Log.d(TAG, "Initializing TextToSpeech...")
        
        // Initialize AudioManager for Android Auto
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                Log.d(TAG, "TTS initialization successful")
                val langResult = textToSpeech?.setLanguage(Locale.US)
                
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "TTS language not supported, using default")
                }
                
                // Configure TTS for Android Auto with proper audio attributes
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    
                    textToSpeech?.setAudioAttributes(audioAttributes)
                    Log.d(TAG, "TTS audio attributes set for Android Auto")
                }
                
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "TTS started for utterance: $utteranceId")
                        isSpeaking = true
                        callback?.onTTSStarted()
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "TTS completed for utterance: $utteranceId")
                        isSpeaking = false
                        releaseAudioFocus()
                        callback?.onTTSFinished()
                    }
                    
                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "TTS error for utterance: $utteranceId")
                        isSpeaking = false
                        releaseAudioFocus()
                        callback?.onTTSFinished()
                    }
                })
                continuation.resume(true)
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
                continuation.resume(false)
            }
        }
    }
    
    fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d("VoiceManager", "Ready for speech")
                }
                
                override fun onBeginningOfSpeech() {
                    Log.d("VoiceManager", "Beginning of speech")
                    callback?.onSpeechStarted()
                }
                
                override fun onRmsChanged(rmsdB: Float) {
                    // Audio level changed
                }
                
                override fun onBufferReceived(buffer: ByteArray?) {
                    // Audio buffer received
                }
                
                override fun onEndOfSpeech() {
                    Log.d("VoiceManager", "End of speech")
                    isListening = false
                    callback?.onSpeechFinished()
                }
                
                override fun onError(error: Int) {
                    isListening = false
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech input"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                        else -> "Unknown error"
                    }
                    Log.e("VoiceManager", "Speech recognition error: $errorMessage")
                    callback?.onSpeechError(errorMessage)
                }
                
                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val recognizedText = matches[0]
                        Log.d("VoiceManager", "Recognized: $recognizedText")
                        callback?.onSpeechRecognized(recognizedText)
                    }
                }
                
                override fun onPartialResults(partialResults: Bundle?) {
                    // Partial results received
                }
                
                override fun onEvent(eventType: Int, params: Bundle?) {
                    // Reserved for future use
                }
            })
        }
    }
    
    fun startListening() {
        if (!isListening && !isSpeaking) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your question...")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            
            isListening = true
            speechRecognizer?.startListening(intent)
        }
    }
    
    fun stopListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
        }
    }
    
    private fun requestAudioFocus(): Boolean {
        audioManager?.let { am ->
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(audioAttributes)
                    .setOnAudioFocusChangeListener { focusChange ->
                        Log.d(TAG, "Audio focus changed: $focusChange")
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_LOSS,
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                                Log.d(TAG, "Lost audio focus, stopping TTS")
                                hasAudioFocus = false
                                stopSpeaking()
                            }
                            AudioManager.AUDIOFOCUS_GAIN -> {
                                Log.d(TAG, "Gained audio focus")
                                hasAudioFocus = true
                            }
                        }
                    }
                    .build()
                
                val result = am.requestAudioFocus(audioFocusRequest!!)
                hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                Log.d(TAG, "Audio focus request result: $result, hasAudioFocus: $hasAudioFocus")
                hasAudioFocus
            } else {
                @Suppress("DEPRECATION")
                val result = am.requestAudioFocus(
                    { focusChange ->
                        Log.d(TAG, "Audio focus changed (legacy): $focusChange")
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_LOSS,
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                                Log.d(TAG, "Lost audio focus (legacy), stopping TTS")
                                hasAudioFocus = false
                                stopSpeaking()
                            }
                            AudioManager.AUDIOFOCUS_GAIN -> {
                                Log.d(TAG, "Gained audio focus (legacy)")
                                hasAudioFocus = true
                            }
                        }
                    },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
                hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                Log.d(TAG, "Audio focus request result (legacy): $result, hasAudioFocus: $hasAudioFocus")
                hasAudioFocus
            }
        }
        return false
    }
    
    private fun releaseAudioFocus() {
        audioManager?.let { am ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { request ->
                    am.abandonAudioFocusRequest(request)
                    Log.d(TAG, "Audio focus released")
                }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus { }
                Log.d(TAG, "Audio focus released (legacy)")
            }
            hasAudioFocus = false
        }
    }
    
    fun speak(text: String) {
        Log.d(TAG, "Attempting to speak text: '${text.take(50)}...' (length: ${text.length})")
        
        // Validate text before speaking
        if (text.isBlank()) {
            Log.w(TAG, "Cannot speak empty or blank text")
            callback?.onTTSFinished() // Immediately call finished since there's nothing to speak
            return
        }
        
        if (textToSpeech == null) {
            Log.e(TAG, "TTS not initialized, cannot speak")
            callback?.onTTSFinished()
            return
        }
        
        if (!isSpeaking) {
            // Request audio focus before speaking
            if (!requestAudioFocus()) {
                Log.e(TAG, "Failed to gain audio focus, cannot speak")
                callback?.onTTSFinished()
                return
            }
            
            val utteranceId = UUID.randomUUID().toString()
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                // Force TTS to use media stream for proper audio focus handling
                putString(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC.toString())
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            
            Log.d(TAG, "Starting TTS for utterance: $utteranceId with audio focus")
            val result = textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            
            if (result == TextToSpeech.ERROR) {
                Log.e(TAG, "TTS speak() returned ERROR")
                isSpeaking = false
                releaseAudioFocus()
                callback?.onTTSFinished()
            }
        } else {
            Log.w(TAG, "Already speaking, ignoring new speak request")
        }
    }
    
    fun stopSpeaking() {
        textToSpeech?.stop()
        isSpeaking = false
    }
    
    fun isCurrentlyListening(): Boolean = isListening
    fun isCurrentlySpeaking(): Boolean = isSpeaking
    
    fun destroy() {
        releaseAudioFocus()
        speechRecognizer?.destroy()
        textToSpeech?.shutdown()
        speechRecognizer = null
        textToSpeech = null
        audioManager = null
        audioFocusRequest = null
    }
}
