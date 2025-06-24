package com.example.emailassistantapp.network

import android.util.Log
import com.example.emailassistantapp.BuildConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class EmailResponseGenerator {
    private val apiKey = BuildConfig.GEMINI_API_KEY
    private val geminiModel = "gemini-2.0-flash" // or update to gemini-2.0-flash if available
    private val geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/$geminiModel:generateContent?key=$apiKey"
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun generateResponses(emailContent: String, callback: (List<String>?) -> Unit) {
        // Option mapping fix: Option number should match prompt index (1-based for UI, 0-based for code)
        // This function generates two types of responses: supportive and neutral/alternative
        // If you want to add more options, expand the prompts list and ensure UI sends correct index

        val prompts = listOf(
            // Option 1: Accept the offer as the user
            "Write a reply to the following email as if you are the recipient. Your reply should politely ACCEPT the offer, confirm your acceptance, and mention any next steps if appropriate. Do not introduce yourself or state your occupation unless the email specifically asks for it. Here is the email:\n$emailContent",
            // Option 2: Politely reject the offer as the user
            "Write a reply to the following email as if you are the recipient. Your reply should politely REJECT the offer, express gratitude, and optionally provide a brief reason for declining. Do not introduce yourself or state your occupation unless the email specifically asks for it. Here is the email:\n$emailContent"
        )

        val responses = mutableListOf<String>()
        var completedRequests = 0

        for (prompt in prompts) {
            val geminiRequestBody = """
                {
                  "contents": [
                    {
                      "parts": [
                        { "text": "${prompt.replace("\"", "\\\"").replace("\n", " ")}" }
                      ]
                    }
                  ]
                }
            """.trimIndent().toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url(geminiUrl)
                .post(geminiRequestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("EmailResponseGenerator", "Failed to generate response (Gemini)", e)
                    synchronized(responses) {
                        completedRequests++
                        if (completedRequests == prompts.size) {
                            callback(if (responses.isEmpty()) null else responses)
                        }
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val responseBody = response.body?.string()
                        Log.d("EmailResponseGenerator", "Gemini AI raw response: $responseBody")
                        val json = org.json.JSONObject(responseBody)
                        val candidates = json.optJSONArray("candidates")
                        val generatedResponse = if (candidates != null && candidates.length() > 0) {
                            val content = candidates.getJSONObject(0).optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                parts.getJSONObject(0).optString("text", "")
                            } else ""
                        } else ""
                        synchronized(responses) {
                            responses.add(generatedResponse)
                            completedRequests++
                            if (completedRequests == prompts.size) {
                                callback(responses)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("EmailResponseGenerator", "Error parsing Gemini response", e)
                        synchronized(responses) {
                            completedRequests++
                            if (completedRequests == prompts.size) {
                                callback(if (responses.isEmpty()) null else responses)
                            }
                        }
                    }
                }
            })
        }
    }
}
