package com.example.emailassistantapp.data.model

import java.util.Date

data class Email(
    val id: String,
    val from: String,
    val to: List<String>,
    val subject: String,
    val content: String,
    val date: Date,
    val isRead: Boolean,
    val hasAttachment: Boolean,
    val folder: String,
    val priority: Priority = Priority.NORMAL,
    var summary: String? = null
)

enum class Priority {
    HIGH,
    NORMAL,
    LOW
}

data class EmailFolder(
    val name: String,
    val fullName: String,
    val messageCount: Int,
    val unreadCount: Int
)
