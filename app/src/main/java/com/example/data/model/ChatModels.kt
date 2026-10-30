package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class ChatSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val language: String = "en",
    val lastMessage: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val sessionId: String = "",
    val userId: String = "",
    val role: String = "user", // "user" or "model"
    val content: String = "",
    val language: String = "en",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SavedCreation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val type: String = "image", // "image" or "qa"
    val prompt: String = "",
    val title: String = "",
    val category: String = "general",
    val language: String = "en",
    val content: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
