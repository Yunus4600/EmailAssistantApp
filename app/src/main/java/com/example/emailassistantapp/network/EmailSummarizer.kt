package com.example.emailassistantapp.network

import android.util.Log
import com.example.emailassistantapp.BuildConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException

class EmailSummarizer {
    private val apiKey = BuildConfig.HUGGING_FACE_API_KEY // Retrieve API key from BuildConfig
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val geminiApiKey = BuildConfig.GEMINI_API_KEY
    private val geminiModel = "gemini-2.0-flash" // Change to "gemini-2.0-flash" if available
    private val geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/$geminiModel:generateContent?key=$geminiApiKey"
    private val geminiClient = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // Primary, backup, and fallback model URLs (updated to recommended models)
    private val primaryModelUrl = "https://api-inference.huggingface.co/models/facebook/bart-large-cnn"
    private val backupModelUrl = "https://api-inference.huggingface.co/models/google/pegasus-xsum"
    private val fallbackModelUrl = "https://api-inference.huggingface.co/models/t5-base"
    private var currentModelUrl = primaryModelUrl

    // Increase delay between retries
    private val retryDelays = listOf(1000L, 2000L, 5000L) // Increasing delays in milliseconds

    private var lastRequestTime = 0L
    private val minRequestInterval = 1000L // Minimum 1 second between requests

    init {
        // Improve API key validation logging
        when {
            apiKey.isBlank() -> {
                Log.e("EmailSummarizer", "❌ API key is blank")
            }
            !apiKey.startsWith("hf_") -> {
                Log.e("EmailSummarizer", "❌ API key format invalid")
            }
            apiKey.length < 30 -> {
                Log.e("EmailSummarizer", "❌ API key length invalid")
            }
            else -> {
                Log.d("EmailSummarizer", "✅ API key validation passed: ${apiKey.take(10)}...")
            }
        }
    }

    private fun sanitizeEmailContent(content: String): String {
        return content
            .replace(Regex("=\\r?\\n"), "") // Remove soft line breaks
            .replace(Regex("=([0-9A-F]{2})"), { result ->
                result.groupValues[1].toInt(16).toChar().toString()
            }) // Decode quoted-printable
            .replace(Regex("\\r?\\n\\s*\\r?\\n"), "\n\n") // Normalize multiple blank lines
            .replace(Regex("(?<=\\n)[ \\t]+(?=\\n)"), "") // Remove whitespace-only lines
            .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "") // Remove control chars
            .trim()
    }

    private fun decodeBase64(content: String): String {
        return try {
            android.util.Base64.decode(content, android.util.Base64.DEFAULT).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w("EmailSummarizer", "Failed to decode base64 content", e)
            content
        }
    }

    private fun formatEmailContent(rawContent: String, contentTransferEncoding: String?): String {
        return when (contentTransferEncoding?.lowercase()) {
            "base64" -> decodeBase64(rawContent)
            "quoted-printable" -> sanitizeEmailContent(rawContent)
            else -> rawContent
        }.let { decodedContent ->
            // Further cleanup and formatting
            decodedContent
                .replace(Regex("<[^>]*>"), "") // Remove HTML tags
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace(Regex("\\s*\\n\\s*"), "\n") // Normalize line breaks
                .replace(Regex("\\n{3,}"), "\n\n") // Max 2 consecutive line breaks
                .trim()
        }
    }

    fun summarizeEmail(emailContent: String, contentTransferEncoding: String? = null, callback: (String?) -> Unit) {
        // Add rate limiting
        val now = System.currentTimeMillis()
        val timeSinceLastRequest = now - lastRequestTime
        if (timeSinceLastRequest < minRequestInterval) {
            Thread.sleep(minRequestInterval - timeSinceLastRequest)
        }
        lastRequestTime = System.currentTimeMillis()

        // Validate and format input content
        if (emailContent.isBlank()) {
            Log.e("EmailFormatter", "❌ Empty content provided")
            callback("Error: No content to summarize")
            return
        }

        val formattedContent = formatEmailContent(emailContent, contentTransferEncoding)
        
        if (formattedContent.length < 10) {
            Log.e("EmailFormatter", "❌ Content too short after formatting")
            callback("Error: Content too short to summarize")
            return
        }

        Log.d("EmailFormatter", """
            |📧 Content Formatting:
            |📝 Original length: ${emailContent.length} chars
            |📝 Formatted length: ${formattedContent.length} chars
            |🔍 Sample: ${formattedContent.take(100)}...
        """.trimMargin())

        // Truncate content if too long
        val truncatedContent = if (formattedContent.length > 1000) {
            formattedContent.take(1000) + "..."
        } else {
            formattedContent
        }

        val jsonObject = JSONObject().apply {
            put(
                "inputs",
                "Summarize the following email. Only use information that is explicitly present in the email body. " +
                        "Do NOT add, infer, or assume any details that are not stated. " +
                        "Clearly state the main points, required actions, and deadlines if mentioned. " +
                        "If the email is an offer, confirmation, or request, make that clear. " +
                        "Here is the email:\n\n$truncatedContent"
            )
            put("parameters", JSONObject().apply {
                put("max_length", 200)
                put("min_length", 50)
                put("do_sample", false)
                put("temperature", 0.3) // Lower temperature for more focused output
                put("num_beams", 4)     // Add beam search for better quality
                put("no_repeat_ngram_size", 3)  // Avoid repetition
            })
        }

        val requestBody = jsonObject.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(currentModelUrl)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()

        // Add retry mechanism
        var retryCount = 0
        val maxRetries = 3

        fun executeRequest() {
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (retryCount < maxRetries) {
                        retryCount++
                        Log.w("EmailSummarizer", "Retry attempt $retryCount after failure")
                        executeRequest()
                    } else {
                        callback("Error: Network issue after $maxRetries attempts")
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string()
                    Log.d("EmailSummarizer", "📨 Response code: ${response.code}")
                    
                    when (response.code) {
                        200 -> handleSuccessResponse(responseBody, callback)
                        503 -> {
                            if (retryCount < maxRetries) {
                                retryCount++
                                Log.w("EmailSummarizer", "Service unavailable, retry attempt $retryCount")
                                Thread.sleep(retryDelays[retryCount - 1]) // Use increasing delays
                                executeRequest()
                            } else if (currentModelUrl != fallbackModelUrl) {
                                currentModelUrl = fallbackModelUrl
                                retryCount = 0
                                Log.d("EmailSummarizer", "Switching to fallback model")
                                executeRequest()
                            } else {
                                callback("Error: Service unavailable after all attempts")
                            }
                        }
                        else -> {
                            if (currentModelUrl == primaryModelUrl) {
                                currentModelUrl = backupModelUrl
                                Log.d("EmailSummarizer", "Switching to backup model")
                                executeRequest()
                            } else {
                                callback("Error: API error ${response.code}")
                            }
                        }
                    }
                }
            })
        }

        executeRequest()
    }

    fun summarizeEmailWithGemini(emailContent: String, contentTransferEncoding: String? = null, callback: (String?) -> Unit) {
        val formattedContent = formatEmailContent(emailContent, contentTransferEncoding)
        if (formattedContent.length < 10) {
            callback("Error: Content too short to summarize")
            return
        }
        val truncatedContent = if (formattedContent.length > 1000) {
            formattedContent.take(1000) + "..."
        } else {
            formattedContent
        }
        val prompt = "Summarize the following email. Only use information that is explicitly present in the email body. Do NOT add, infer, or assume any details that are not stated. Clearly state the main points, required actions, and deadlines if mentioned. If the email is an offer, confirmation, request, or advertisement, make that clear. Here is the email:\n\n$truncatedContent"
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
        geminiClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("EmailSummarizer", "Failed to generate summary (Gemini)", e)
                callback("Error: Network issue with Gemini API")
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string()
                    Log.d("EmailSummarizer", "Gemini AI raw response: $responseBody")
                    val json = org.json.JSONObject(responseBody)
                    val candidates = json.optJSONArray("candidates")
                    val summary = if (candidates != null && candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            parts.getJSONObject(0).optString("text", "")
                        } else ""
                    } else ""
                    callback(summary)
                } catch (e: Exception) {
                    Log.e("EmailSummarizer", "Error parsing Gemini summary response", e)
                    callback("Error: Invalid Gemini response format")
                }
            }
        })
    }

    fun summarizeEmailWithFallback(emailContent: String, contentTransferEncoding: String? = null, callback: (String?) -> Unit) {
        val formattedContent = formatEmailContent(emailContent, contentTransferEncoding)
        // Remove HTML entities and invisible characters
        val cleaned = formattedContent
            .replace(Regex("&[a-zA-Z0-9#]+;"), "") // Remove HTML entities
            .replace(Regex("[\u200B-\u200D\uFEFF]"), "") // Remove zero-width and invisible chars
            .replace(Regex("""[^\p{L}\p{N}\s.,!?@#\$%&*()\-\[\]]"""), "") // Remove most non-alphanumeric except common punctuation
        if (cleaned.count { it.isLetterOrDigit() } < 20) {
            callback("No summary available for this email.")
            return
        }
        if (formattedContent.length < 50) {
            // If the email is very short, just return the original content as the summary
            callback(formattedContent)
            return
        }
        val truncatedContent = if (formattedContent.length > 1000) {
            formattedContent.take(1000) + "..."
        } else {
            formattedContent
        }
        val prompt = "Summarize the following email. Only use information that is explicitly present in the email body. Do NOT add, infer, or assume any details that are not stated. Clearly state the main points, required actions, and deadlines if mentioned. If the email is an offer, confirmation, request, or advertisement, make that clear. Here is the email:\n\n$truncatedContent"
        val parameters = JSONObject().apply {
            put("max_length", 200)
            put("min_length", 50)
            put("do_sample", false)
            put("temperature", 0.3)
            put("num_beams", 4)
            put("no_repeat_ngram_size", 3)
        }
        val jsonObject = JSONObject().apply {
            put("inputs", prompt)
            put("parameters", parameters)
        }
        val modelUrls = listOf(primaryModelUrl, backupModelUrl, fallbackModelUrl)
        fun tryModel(index: Int) {
            if (index >= modelUrls.size) {
                callback("Error: Could not generate summary with any model.")
                return
            }
            val request = Request.Builder()
                .url(modelUrls[index])
                .post(jsonObject.toString().toRequestBody(jsonMediaType))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("EmailSummarizer", "Model ${modelUrls[index]} failed: ${e.message}")
                    tryModel(index + 1)
                }
                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string()
                    if (response.isSuccessful && responseBody != null) {
                        try {
                            val jsonArray = org.json.JSONArray(responseBody)
                            val summary = jsonArray.getJSONObject(0).getString("summary_text").trim()
                            if (summary.isNotBlank() && !summary.startsWith("Error") && !summary.contains("Summarize the following email", ignoreCase = true)) {
                                callback(summary)
                            } else {
                                tryModel(index + 1)
                            }
                        } catch (e: Exception) {
                            Log.e("EmailSummarizer", "Parsing failed for model ${modelUrls[index]}", e)
                            tryModel(index + 1)
                        }
                    } else {
                        Log.e("EmailSummarizer", "Model ${modelUrls[index]} HTTP error: ${response.code}")
                        tryModel(index + 1)
                    }
                }
            })
        }
        tryModel(0)
    }

    private fun handleSuccessResponse(responseBody: String?, callback: (String?) -> Unit) {
        if (responseBody == null) {
            Log.e("EmailSummarizer", "Empty response body")
            callback("Error: Empty response")
            return
        }

        try {
            val jsonArray = JSONArray(responseBody)
            if (jsonArray.length() > 0) {
                val summary = jsonArray.getJSONObject(0)
                    .getString("summary_text")
                    .trim()
                    .replace(Regex("\\s+"), " ")
                    
                // Validate summary quality (without comparing to original content)
                if (summary.length < 20) {
                    Log.e("EmailSummarizer", "Low quality summary generated")
                    callback("Error: Could not generate meaningful summary")
                    return
                }

                Log.d("EmailSummarizer", """
                    |📝 Generated Summary:
                    |${summary}
                    |Length: ${summary.length} chars
                """.trimMargin())
                callback(summary)
            } else {
                Log.e("EmailSummarizer", "Empty summary array")
                callback("Error: No summary generated")
            }
        } catch (e: Exception) {
            Log.e("EmailSummarizer", "Error parsing response (Ask Gemini)", e)
            callback("Error: Invalid response format")
        }
    }
}