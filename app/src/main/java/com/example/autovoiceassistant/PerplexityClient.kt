package com.example.autovoiceassistant

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class PerplexityClient(private val apiKey: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val gson = Gson()
    private val baseUrl = "https://api.perplexity.ai"
    
    // Add conversation history
    private val conversationHistory = mutableListOf<Message>()
    private val maxHistorySize = 10 // Keep last 10 messages to avoid token limits

    companion object {
        private const val TAG = "PerplexityClient"
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY = 1000L // 1 second
    }

    data class ChatRequest(
        val model: String = "sonar",
        val messages: List<Message>,
        @SerializedName("max_tokens") val maxTokens: Int = 150,
        val temperature: Double = 0.7,
        val stream: Boolean = false
    )

    data class Message(
        val role: String,
        val content: String
    )

    data class ChatResponse(
        val choices: List<Choice>
    )

    data class Choice(
        val message: Message,
        @SerializedName("finish_reason") val finishReason: String
    )

    suspend fun sendMessage(userMessage: String): Result<String> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Sending message to Perplexity: $userMessage")

        if (apiKey.isBlank()) {
            Log.e(TAG, "Perplexity API key is not configured")
            return@withContext Result.failure(Exception("Perplexity API key is not configured. Please set your API key in settings."))
        }

        var lastException: Exception? = null

        repeat(MAX_RETRIES) { attempt ->
            try {
                Log.d(TAG, "Attempt ${attempt + 1} of $MAX_RETRIES")

                // Build messages with conversation history
                val messages = mutableListOf<Message>()
                
                // Add system message
                messages.add(Message("system", "You are a helpful AI assistant designed for use while driving. Keep responses concise, clear, and safe for audio consumption. Avoid long lists or complex formatting. Provide direct, actionable answers. Use web search when needed for current information. You can reference previous questions in the conversation."))
                
                // Add conversation history
                messages.addAll(conversationHistory)
                
                // Add current user message
                val currentUserMessage = Message("user", userMessage)
                messages.add(currentUserMessage)

                val request = ChatRequest(messages = messages)
                val json = gson.toJson(request)
                val body = json.toRequestBody("application/json".toMediaType())

                Log.d(TAG, "Request JSON: $json")
                Log.d(TAG, "API Key prefix: ${apiKey.take(8)}...")
                Log.d(TAG, "Model: ${request.model}")
                Log.d(TAG, "Conversation history size: ${conversationHistory.size}")

                val httpRequest = Request.Builder()
                    .url("$baseUrl/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("User-Agent", "AndroidAutoVoiceAssistant/1.0")
                    .post(body)
                    .build()

                Log.d(TAG, "Making HTTP request to: ${httpRequest.url}")
                Log.d(TAG, "Headers: ${httpRequest.headers}")
                val response = client.newCall(httpRequest).execute()

                Log.d(TAG, "Received response: ${response.code} ${response.message}")

                response.use { resp ->
                    if (resp.isSuccessful) {
                        val responseBody = resp.body?.string()
                        Log.d(TAG, "Response body length: ${responseBody?.length ?: 0}")
                        Log.d(TAG, "Response body: $responseBody")
                        
                        if (responseBody != null) {
                            try {
                                val chatResponse = gson.fromJson(responseBody, ChatResponse::class.java)
                                val assistantMessage = chatResponse.choices.firstOrNull()?.message?.content
                                
                                Log.d(TAG, "Parsed choices count: ${chatResponse.choices.size}")
                                Log.d(TAG, "Assistant message: $assistantMessage")
                                
                                if (assistantMessage != null && assistantMessage.isNotBlank()) {
                                    Log.d(TAG, "Successfully received response from Perplexity")
                                    
                                    // Add both user message and assistant response to history
                                    addToConversationHistory(currentUserMessage)
                                    addToConversationHistory(Message("assistant", assistantMessage.trim()))
                                    
                                    return@withContext Result.success(assistantMessage.trim())
                                } else {
                                    Log.w(TAG, "Empty or null response content from Perplexity")
                                    Log.w(TAG, "Full response object: $chatResponse")
                                    lastException = Exception("Perplexity returned an empty response")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to parse Perplexity response", e)
                                Log.e(TAG, "Raw response: $responseBody")
                                lastException = Exception("Failed to parse Perplexity response: ${e.message}")
                            }
                        } else {
                            Log.w(TAG, "Empty response body from Perplexity")
                            lastException = Exception("Empty response from Perplexity")
                        }
                    } else {
                        val errorBody = resp.body?.string()
                        Log.e(TAG, "Perplexity API error: ${resp.code} ${resp.message}, body: $errorBody")

                        val errorMessage = when (resp.code) {
                            401 -> "Invalid API key. Please check your Perplexity API key in settings."
                            429 -> "Rate limit exceeded. Please try again in a moment."
                            500, 502, 503, 504 -> "Perplexity service is temporarily unavailable. Retrying..."
                            else -> "Perplexity API error (${resp.code}): ${resp.message}"
                        }

                        lastException = Exception(errorMessage)

                        // Don't retry on authentication or client errors (4xx except 429)
                        if (resp.code in 400..499 && resp.code != 429) {
                            Log.e(TAG, "Non-retryable error: ${resp.code}")
                            return@withContext Result.failure(lastException!!)
                        }
                    }
                }

            } catch (e: SocketTimeoutException) {
                Log.w(TAG, "Request timeout on attempt ${attempt + 1}", e)
                lastException = Exception("Request timed out. Please check your internet connection.")
            } catch (e: UnknownHostException) {
                Log.w(TAG, "Network unavailable on attempt ${attempt + 1}", e)
                lastException = Exception("No internet connection. Please check your network.")
            } catch (e: IOException) {
                Log.w(TAG, "Network error on attempt ${attempt + 1}", e)
                lastException = Exception("Network error: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error on attempt ${attempt + 1}", e)
                lastException = Exception("Unexpected error: ${e.message}")
            }

            // Wait before retrying (exponential backoff)
            if (attempt < MAX_RETRIES - 1) {
                val delayMs = INITIAL_RETRY_DELAY * (1 shl attempt) // 1s, 2s, 4s
                Log.d(TAG, "Waiting ${delayMs}ms before retry...")
                delay(delayMs)
            }
        }

        Log.e(TAG, "All retry attempts failed")
        Result.failure(lastException ?: Exception("Failed to communicate with Perplexity after $MAX_RETRIES attempts"))
    }
    
    private fun addToConversationHistory(message: Message) {
        conversationHistory.add(message)
        
        // Keep history size manageable to avoid token limits
        if (conversationHistory.size > maxHistorySize) {
            // Remove oldest messages (keep pairs of user/assistant messages)
            conversationHistory.removeAt(0)
            if (conversationHistory.isNotEmpty() && conversationHistory[0].role == "assistant") {
                conversationHistory.removeAt(0)
            }
        }
        
        Log.d(TAG, "Added message to history. Current history size: ${conversationHistory.size}")
    }
    
    fun clearConversationHistory() {
        conversationHistory.clear()
        Log.d(TAG, "Conversation history cleared")
    }
    
    fun getConversationHistorySize(): Int {
        return conversationHistory.size
    }
}
