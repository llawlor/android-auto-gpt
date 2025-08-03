package com.example.autovoiceassistant

import android.content.Context
import android.content.Intent
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
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                        callback?.onTTSStarted()
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                        callback?.onTTSFinished()
                    }
                    
                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                        callback?.onTTSFinished()
                    }
                })
                continuation.resume(true)
            } else {
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
    
    fun speak(text: String) {
        if (!isSpeaking) {
            val utteranceId = UUID.randomUUID().toString()
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }
    }
    
    fun stopSpeaking() {
        textToSpeech?.stop()
        isSpeaking = false
    }
    
    fun isCurrentlyListening(): Boolean = isListening
    fun isCurrentlySpeaking(): Boolean = isSpeaking
    
    fun destroy() {
        speechRecognizer?.destroy()
        textToSpeech?.shutdown()
        speechRecognizer = null
        textToSpeech = null
    }
}
