package com.example.emailassistantapp.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.emailassistantapp.data.model.Email
import com.example.emailassistantapp.data.model.EmailFolder
import com.example.emailassistantapp.network.ImapHelper
import com.example.emailassistantapp.network.EmailSummarizer
import com.example.emailassistantapp.network.EmailResponseGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.delay
import kotlin.coroutines.resume

class EmailViewModel : ViewModel() {
    private val imapHelper = ImapHelper()
    private val emailSummarizer = EmailSummarizer() // Remove API key parameter
    private val emailResponseGenerator = EmailResponseGenerator()

    private val _emails = MutableStateFlow<List<Email>>(emptyList())
    val emails: StateFlow<List<Email>> = _emails.asStateFlow()
    
    private val _folders = MutableStateFlow<List<EmailFolder>>(emptyList())
    val folders: StateFlow<List<EmailFolder>> = _folders.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _progressStatus = MutableStateFlow<String>("")
    val progressStatus: StateFlow<String> = _progressStatus.asStateFlow()
    
    private val _summarizedEmails = MutableStateFlow<List<String>>(emptyList())
    val summarizedEmails: StateFlow<List<String>> = _summarizedEmails.asStateFlow()

    fun connect(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _progressStatus.value = "Connecting to email server..."
            try {
                withContext(Dispatchers.IO) {
                    imapHelper.connect(email, password)
                }
                _progressStatus.value = "Connected successfully"
                // Remove automatic email loading
                // loadEmails() - This line should be removed
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error occurred"
                _progressStatus.value = "Connection failed"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun loadFolders() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _progressStatus.value = "Loading folders..."
            try {
                withContext(Dispatchers.IO) {
                    _folders.value = imapHelper.getFolders()
                }
                _progressStatus.value = "Folders loaded successfully"
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load folders"
                _progressStatus.value = "Failed to load folders"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun loadEmails() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _progressStatus.value = "Fetching unread emails..."
            try {
                Log.d("EmailViewModel", "Requesting only latest 5 unread emails...")
                val fetchedEmails = withContext(Dispatchers.IO) {
                    imapHelper.getEmails("INBOX", maxEmails = 20, maxUnread = 5)
                        .filter { !it.isRead }
                        .sortedByDescending { it.date }
                        .take(5)
                }

                if (fetchedEmails.isEmpty()) {
                    _error.value = "No unread emails found in inbox"
                    Log.w("EmailViewModel", "No unread emails found in inbox")
                    _emails.value = emptyList()
                    return@launch
                }

                _emails.value = fetchedEmails
                Log.d("EmailViewModel", "Loaded ${fetchedEmails.size} unread emails. Starting summarization...")

                // Summarize all unread emails (up to 5) using Gemini
                fetchedEmails.forEachIndexed { index, email ->
                    _progressStatus.value = "Summarizing unread email ${index + 1} of ${fetchedEmails.size}"
                    delay(1000)
                    try {
                        suspendCancellableCoroutine<Unit> { continuation ->
                            emailSummarizer.summarizeEmailWithGemini(email.content) { summary ->
                                email.summary = summary
                                _emails.value = _emails.value.toMutableList().apply {
                                    set(index, email)
                                }
                                Log.d("EmailViewModel", "Summarized unread email ${index + 1}: ${email.subject}")
                                continuation.resume(Unit)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("EmailViewModel", "Failed to summarize unread email ${index + 1}", e)
                    }
                }

                _progressStatus.value = "Successfully loaded ${fetchedEmails.size} unread emails"
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to fetch emails"
                Log.e("EmailViewModel", "Failed to fetch emails", e)
                _emails.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun refreshEmails() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _progressStatus.value = "Refreshing emails..."
            try {
                Log.d("EmailViewModel", "Starting email refresh...")
                val fetchedEmails = withContext(Dispatchers.IO) {
                    imapHelper.getEmails("INBOX")
                }
                
                if (fetchedEmails.isEmpty()) {
                    _error.value = "No emails found in inbox"
                    _progressStatus.value = "No emails found"
                    return@launch
                }

                _emails.value = fetchedEmails
                _progressStatus.value = "Successfully refreshed ${fetchedEmails.size} emails"
                Log.d("EmailViewModel", "Successfully refreshed ${fetchedEmails.size} emails")
            } catch (e: Exception) {
                Log.e("EmailViewModel", "Failed to refresh emails", e)
                _error.value = e.message ?: "Failed to refresh emails"
                _progressStatus.value = "Failed to refresh emails"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Remove automatic summary fetching on init
    fun fetchSummarizedEmails() {
        // This function should only be called explicitly when needed
    }

    private suspend fun summarizeEmail(content: String): String? {
        if (content.isBlank()) {
            Log.w("EmailViewModel", "Empty content provided for summarization")
            return "Error: No content to summarize"
        }
        Log.d("EmailViewModel", "Summarizing content of length: ${content.length}")
        Log.d("EmailViewModel", "Content preview: ${content.take(100)}...")
        return suspendCancellableCoroutine { continuation ->
            emailSummarizer.summarizeEmailWithGemini(content) { summary ->
                if (summary != null) {
                    Log.d("EmailViewModel", "Generated summary: $summary")
                } else {
                    Log.e("EmailViewModel", "Failed to generate summary")
                }
                continuation.resume(summary)
            }
        }
    }

    fun testHuggingFaceAPI() {
        viewModelScope.launch {
            _isLoading.value = true
            _progressStatus.value = "Testing Gemini API connection..."
            try {
                val testContent = "This is a test email content to verify the Gemini API connection."
                summarizeEmail(testContent)?.let { summary ->
                    if (summary.startsWith("Error:")) {
                        _error.value = summary
                        _progressStatus.value = "API test failed"
                    } else {
                        _progressStatus.value = "API test successful!"
                        Log.d("EmailViewModel", "✅ Test summary generated: $summary")
                    }
                }
            } catch (e: Exception) {
                _error.value = "API test failed: ${e.message}"
                _progressStatus.value = "API test failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun generateResponses(emailContent: String, callback: (List<String>) -> Unit) {
        _isLoading.value = true
        _progressStatus.value = "Generating responses..."
        emailResponseGenerator.generateResponses(emailContent) { responses ->
            viewModelScope.launch {
                _isLoading.value = false
                if (responses != null) {
                    callback(responses)
                } else {
                    _error.value = "Failed to generate responses."
                }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        imapHelper.disconnect()
    }
}