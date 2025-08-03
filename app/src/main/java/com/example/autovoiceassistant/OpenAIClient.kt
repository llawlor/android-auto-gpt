package com.example.autovoiceassistant

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class OpenAIClient(private val apiKey: String) {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val baseUrl = "https://api.openai.com/v1"

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
        try {
            val messages = listOf(
                Message("system", "You are a helpful AI assistant designed for use while driving. Keep responses concise, clear, and safe for audio consumption. Avoid long lists or complex formatting."),
                Message("user", userMessage)
            )

            val request = ChatRequest(messages = messages)
            val json = gson.toJson(request)
            val body = json.toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            val response = client.newCall(httpRequest).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val chatResponse = gson.fromJson(responseBody, ChatResponse::class.java)
                    val assistantMessage = chatResponse.choices.firstOrNull()?.message?.content
                    if (assistantMessage != null) {
                        Result.success(assistantMessage.trim())
                    } else {
                        Result.failure(Exception("No response from OpenAI"))
                    }
                } else {
                    Result.failure(Exception("Empty response from OpenAI"))
                }
            } else {
                Result.failure(Exception("OpenAI API error: ${response.code} ${response.message}"))
            }
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
