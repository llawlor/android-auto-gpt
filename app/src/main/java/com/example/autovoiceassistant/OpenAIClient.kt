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

class OpenAIClient(private val apiKey: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    
    private val gson = Gson()
    private val baseUrl = "https://api.openai.com/v1"
    
    companion object {
        private const val TAG = "OpenAIClient"
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY = 1000L // 1 second
    }

    data class ChatRequest(
        val model: String = "gpt-3.5-turbo",
        val messages: List<Message>,
        @SerializedName("max_tokens") val maxTokens: Int = 150,
        val temperature: Double = 0.7
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
        Log.d(TAG, "Sending message to OpenAI: $userMessage")
        
        if (apiKey.isBlank()) {
            Log.e(TAG, "OpenAI API key is not configured")
            return@withContext Result.failure(Exception("OpenAI API key is not configured. Please set your API key in settings."))
        }
        
        var lastException: Exception? = null
        
        repeat(MAX_RETRIES) { attempt ->
            try {
                Log.d(TAG, "Attempt ${attempt + 1} of $MAX_RETRIES")
                
                val messages = listOf(
                    Message("system", "You are a helpful AI assistant designed for use while driving. Keep responses concise, clear, and safe for audio consumption. Avoid long lists or complex formatting. Provide direct, actionable answers."),
                    Message("user", userMessage)
                )

                val request = ChatRequest(messages = messages)
                val json = gson.toJson(request)
                val body = json.toRequestBody("application/json".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("User-Agent", "AndroidAutoVoiceAssistant/1.0")
                    .post(body)
                    .build()

                Log.d(TAG, "Making HTTP request to OpenAI API")
                val response = client.newCall(httpRequest).execute()
                
                Log.d(TAG, "Received response: ${response.code} ${response.message}")
                
                response.use { resp ->
                    if (resp.isSuccessful) {
                        val responseBody = resp.body?.string()
                        Log.d(TAG, "Response body length: ${responseBody?.length ?: 0}")
                        
                        if (responseBody != null) {
                            try {
                                val chatResponse = gson.fromJson(responseBody, ChatResponse::class.java)
                                val assistantMessage = chatResponse.choices.firstOrNull()?.message?.content
                                
                                if (assistantMessage != null && assistantMessage.isNotBlank()) {
                                    Log.d(TAG, "Successfully received response from OpenAI")
                                    return@withContext Result.success(assistantMessage.trim())
                                } else {
                                    Log.w(TAG, "Empty or null response content from OpenAI")
                                    lastException = Exception("OpenAI returned an empty response")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to parse OpenAI response", e)
                                lastException = Exception("Failed to parse OpenAI response: ${e.message}")
                            }
                        } else {
                            Log.w(TAG, "Empty response body from OpenAI")
                            lastException = Exception("Empty response from OpenAI")
                        }
                    } else {
                        val errorBody = resp.body?.string()
                        Log.e(TAG, "OpenAI API error: ${resp.code} ${resp.message}, body: $errorBody")
                        
                        val errorMessage = when (resp.code) {
                            401 -> "Invalid API key. Please check your OpenAI API key in settings."
                            429 -> "Rate limit exceeded. Please try again in a moment."
                            500, 502, 503, 504 -> "OpenAI service is temporarily unavailable. Retrying..."
                            else -> "OpenAI API error (${resp.code}): ${resp.message}"
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
        Result.failure(lastException ?: Exception("Failed to communicate with OpenAI after $MAX_RETRIES attempts"))
    }
}
