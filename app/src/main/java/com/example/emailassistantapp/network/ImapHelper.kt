package com.example.emailassistantapp.network

import android.util.Log
import com.example.emailassistantapp.BuildConfig
import com.example.emailassistantapp.data.model.Email
import com.example.emailassistantapp.data.model.EmailFolder
import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPStore
import java.util.Properties
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.internet.MimeBodyPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import javax.mail.search.AndTerm
import javax.mail.search.SentDateTerm
import javax.mail.search.ComparisonTerm
import javax.mail.search.FlagTerm
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.suspendCancellableCoroutine

class ImapHelper {
    private var store: IMAPStore? = null
    private var folder: IMAPFolder? = null
    private val emailSummarizer = EmailSummarizer()  // Remove API key parameter

    suspend fun connect(
        email: String,
        password: String,
        host: String = "imap.gmail.com",
        port: Int = 993
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d("ImapHelper", "Starting connection attempt to $host:$port")
            
            // Validate input
            if (email.isEmpty() || password.isEmpty()) {
                throw Exception("Email and password cannot be empty")
            }

            // Create properties with minimal required settings
            val props = Properties().apply {
                // Basic IMAP settings
                put("mail.store.protocol", "imaps")
                put("mail.imaps.host", host)
                put("mail.imaps.port", port.toString())
                
                // SSL settings
                put("mail.imaps.ssl.enable", "true")
                put("mail.imaps.ssl.trust", "*")
                put("mail.imaps.ssl.protocols", "TLSv1.2")
                put("mail.imaps.ssl.ciphersuites", "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256 TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")
                
                // Authentication settings
                put("mail.imaps.auth.plain.disable", "false")
                put("mail.imaps.auth.ntlm.disable", "true")
                put("mail.imaps.auth.gssapi.disable", "true")
                
                // Socket settings
                put("mail.imaps.connectiontimeout", "20000")
                put("mail.imaps.timeout", "20000")
                
                // Debug
                put("mail.debug", "true")
                put("mail.debug.auth", "true")
            }

            Log.d("ImapHelper", "Created mail properties")

            // Create session with debug enabled
            val session = Session.getInstance(props, null).apply {
                debug = true
                debugOut = object : java.io.PrintStream(System.out) {
                    override fun println(message: String) {
                        Log.d("JavaMail", message)
                    }
                }
            }

            Log.d("ImapHelper", "Created mail session")

            // Close any existing connections
            try {
                disconnect()
                Log.d("ImapHelper", "Cleaned up existing connections")
            } catch (e: Exception) {
                Log.w("ImapHelper", "Error during cleanup", e)
            }

            try {
                // Get the store
                Log.d("ImapHelper", "Getting store...")
                store = session.getStore("imaps") as IMAPStore
                
                // Connect to the store with explicit authentication
                Log.d("ImapHelper", "Connecting to store with explicit authentication...")
                store?.connect(host, port, email, password)
                
                if (store?.isConnected == true) {
                    Log.d("ImapHelper", "Successfully connected to store")
                    
                    // Test the connection with a simple NOOP command
                    Log.d("ImapHelper", "Testing connection...")
                    try {
                        (store as IMAPStore).isConnected  // This will execute a NOOP command
                        Log.d("ImapHelper", "Connection is active")
                    } catch (e: Exception) {
                        Log.w("ImapHelper", "Connection test failed", e)
                        throw Exception("Connection established but not responding: ${e.message}")
                    }
                    
                    // Test INBOX access
                    Log.d("ImapHelper", "Testing INBOX access...")
                    val inbox = store?.getFolder("INBOX") as? IMAPFolder
                    inbox?.open(Folder.READ_ONLY)
                    val messageCount = inbox?.messageCount ?: 0
                    Log.d("ImapHelper", "INBOX access successful, message count: $messageCount")
                    inbox?.close(false)
                    
                    Log.d("ImapHelper", "Connection test completed successfully")
                    return@withContext
                } else {
                    throw Exception("Store connected but returned false for isConnected check")
                }
            } catch (e: MessagingException) {
                Log.e("ImapHelper", "Detailed connection error", e)
                e.nextException?.let { 
                    Log.e("ImapHelper", "Nested exception:", it)
                }
                
                // Check if there's a nested SSL exception
                var currentEx: Exception? = e
                while (currentEx != null) {
                    if (currentEx is javax.net.ssl.SSLException) {
                        Log.e("ImapHelper", "SSL Exception found in chain", currentEx)
                        throw Exception("SSL Error: Please check if your device's date/time is correct and try again")
                    }
                    currentEx = currentEx.cause as? Exception
                }
                
                val errorMessage = when {
                    e.message?.contains("socket", ignoreCase = true) == true -> 
                        "Network Error: Unable to establish a secure connection. Please check:\n" +
                        "1. Your internet connection\n" +
                        "2. Any VPN or firewall settings\n" +
                        "3. Try connecting to a different network"
                    e.message?.contains("authentication", ignoreCase = true) == true ->
                        "Authentication Error: Unable to log in. Please check:\n" +
                        "1. Generate a new App Password\n" +
                        "2. Enable IMAP in Gmail settings\n" +
                        "3. Allow less secure app access if not using 2FA"
                    else -> "Connection Error: ${e.message}\nDetailed error: ${e.nextException?.message ?: "No additional details"}"
                }
                
                throw Exception(errorMessage)
            }

        } catch (e: Exception) {
            Log.e("ImapHelper", "Final error", e)
            throw e
        }
    }

    suspend fun getFolders(): List<EmailFolder> {
        return withContext(Dispatchers.IO) {
            try {
                val folders = store?.defaultFolder?.list() ?: emptyArray()
                folders.map { folder ->
                    EmailFolder(
                        name = folder.name,
                        fullName = folder.fullName,
                        messageCount = folder.messageCount,
                        unreadCount = folder.unreadMessageCount
                    )
                }
            } catch (e: Exception) {
                Log.e("ImapHelper", "Error getting folders: ${e.message}")
                emptyList()
            }
        }
    }

    suspend fun getEmails(folderName: String = "INBOX", maxEmails: Int = 20, maxUnread: Int = 5) = withContext(Dispatchers.IO) {
        try {
            Log.d("ImapHelper", "Opening folder $folderName for email fetch...")
            val imapFolder = store?.getFolder(folderName) as? IMAPFolder
            if (imapFolder == null || !imapFolder.exists()) {
                throw Exception("Folder $folderName not found")
            }

            imapFolder.open(Folder.READ_ONLY)
            folder = imapFolder

            val totalMessages = imapFolder.messageCount
            val recentStart = maxOf(1, totalMessages - maxEmails + 1)
            val recentMessages = imapFolder.getMessages(recentStart, totalMessages)
            Log.d("ImapHelper", "Fetched headers for ${recentMessages.size} latest emails (from $recentStart to $totalMessages)")

            // Sort by received date descending
            val sortedMessages = recentMessages.sortedByDescending { it.receivedDate ?: Date(0) }
            val unreadMessages = sortedMessages.filter { !it.isSet(Flags.Flag.SEEN) }
            val readMessages = sortedMessages.filter { it.isSet(Flags.Flag.SEEN) }

            val selectedUnread = unreadMessages.take(maxUnread)
            val selectedRead = (sortedMessages - selectedUnread).take(maxEmails - selectedUnread.size)
            val selectedMessages = selectedUnread + selectedRead

            Log.d("ImapHelper", "Selected ${selectedUnread.size} unread and ${selectedRead.size} read emails for content fetch.")

            val emails = mutableListOf<Email>()
            selectedMessages.forEachIndexed { index, message ->
                try {
                    val messageId = message.messageNumber.toString()
                    val subject = message.subject ?: "(No subject)"
                    val from = (message.from?.firstOrNull() as? InternetAddress)?.address ?: ""
                    val recipients = message.allRecipients?.map { (it as InternetAddress).address } ?: emptyList()
                    val date = message.receivedDate ?: Date()
                    val isRead = message.isSet(Flags.Flag.SEEN)
                    val hasAttachment = hasAttachments(message)

                    // Only fetch content for selected unread emails, fetch preview for read
                    val content = if (!isRead && index < selectedUnread.size) {
                        Log.d("ImapHelper", "Fetching full content for unread email: $subject")
                        getMessageContent(message)
                    } else {
                        Log.d("ImapHelper", "Fetching preview for read email: $subject")
                        (message.content as? String)?.take(100) ?: ""
                    }

                    emails.add(
                        Email(
                            id = messageId,
                            from = from,
                            to = recipients,
                            subject = subject,
                            content = content,
                            date = date,
                            isRead = isRead,
                            hasAttachment = hasAttachment,
                            folder = folderName,
                            summary = null
                        )
                    )
                    Log.d("ImapHelper", "Added email $index: $subject (isRead=$isRead)")
                } catch (e: Exception) {
                    Log.e("ImapHelper", "Error processing message: ${e.message}")
                }
            }

            Log.d("ImapHelper", "Returning ${emails.size} emails (unread prioritized)")
            return@withContext emails

        } catch (e: Exception) {
            Log.e("ImapHelper", "❌ Error fetching emails", e)
            throw Exception("Failed to fetch emails: ${e.message}")
        } finally {
            try {
                folder?.close(false)
            } catch (e: Exception) {
                Log.e("ImapHelper", "⚠️ Error closing folder", e)
            }
        }
    }

    private fun hasAttachments(message: Message): Boolean {
        return try {
            message.contentType.contains("multipart") &&
            (message.content as? Multipart)?.let { multipart ->
                (0 until multipart.count).any { i ->
                    multipart.getBodyPart(i).disposition?.equals(Part.ATTACHMENT, ignoreCase = true) == true
                }
            } ?: false
        } catch (e: Exception) {
            Log.e("ImapHelper", "Error checking attachments", e)
            false
        }
    }

    private fun getMessageContent(message: Message): String {
        fun extractText(part: Part): String {
            return when {
                part.isMimeType("text/plain") -> part.content as? String ?: ""
                part.isMimeType("text/html") -> {
                    val html = part.content as? String ?: ""
                    android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
                }
                part.isMimeType("multipart/*") -> {
                    val multipart = part.content as? Multipart
                    if (multipart != null) {
                        // Prefer text/plain over text/html
                        val parts = (0 until multipart.count).map { multipart.getBodyPart(it) }
                        val plain = parts.firstOrNull { it.isMimeType("text/plain") }
                        if (plain != null) return extractText(plain)
                        val html = parts.firstOrNull { it.isMimeType("text/html") }
                        if (html != null) return extractText(html)
                        // Fallback: concatenate all parts
                        parts.joinToString("\n") { extractText(it) }
                    } else ""
                }
                else -> ""
            }
        }
        return extractText(message).trim()
    }

    fun disconnect() {
        try {
            if (folder?.isOpen == true) {
                folder?.close(false)
            }
            store?.close()
        } catch (e: Exception) {
            Log.e("ImapHelper", "Error disconnecting: ${e.message}")
        } finally {
            folder = null
            store = null
        }
    }
}