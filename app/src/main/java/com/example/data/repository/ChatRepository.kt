package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.model.SavedCreation
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ChatRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            FirebaseApp.getInstance(),
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeSessions(): Flow<List<ChatSession>> = callbackFlow {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            close(e)
            return@callbackFlow
        }

        val query = db.collection("chat_sessions")
            .whereEqualTo("userId", uid)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "chat_sessions")
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val sessions = snapshot.toObjects(ChatSession::class.java)
                    .sortedByDescending { it.updatedAt ?: it.createdAt }
                trySend(sessions)
            }
        }

        awaitClose {
            registration.remove()
        }
    }

    suspend fun createSession(title: String, language: String): Result<String> {
        val uid = requireUserId()
        val sessionId = "session_${UUID.randomUUID().toString().replace("-", "")}"
        val docRef = db.collection("chat_sessions").document(sessionId)
        val payload = mapOf(
            "userId" to uid,
            "title" to title.take(200),
            "language" to language,
            "lastMessage" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(sessionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateSessionLastMessage(sessionId: String, lastMessage: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("chat_sessions").document(sessionId)
        return try {
            docRef.update(
                mapOf(
                    "lastMessage" to lastMessage.take(1000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteSession(sessionId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("chat_sessions").document(sessionId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeMessages(sessionId: String): Flow<List<ChatMessage>> = callbackFlow {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            close(e)
            return@callbackFlow
        }

        val query = db.collection("chat_messages")
            .whereEqualTo("userId", uid)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "chat_messages")
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val messages = snapshot.toObjects(ChatMessage::class.java)
                    .filter { it.sessionId == sessionId }
                    .sortedBy { it.createdAt }
                trySend(messages)
            }
        }

        awaitClose {
            registration.remove()
        }
    }

    suspend fun addMessage(
        sessionId: String,
        role: String,
        content: String,
        language: String
    ): Result<String> {
        val uid = requireUserId()
        val messageId = "msg_${UUID.randomUUID().toString().replace("-", "")}"
        val docRef = db.collection("chat_messages").document(messageId)
        val payload = mapOf(
            "sessionId" to sessionId,
            "userId" to uid,
            "role" to role,
            "content" to content.take(20000),
            "language" to language,
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            updateSessionLastMessage(sessionId, content)
            Result.success(messageId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveCreation(
        type: String,
        prompt: String,
        title: String,
        category: String,
        language: String,
        content: String
    ): Result<String> {
        val uid = requireUserId()
        val creationId = "creation_${UUID.randomUUID().toString().replace("-", "")}"
        val docRef = db.collection("saved_creations").document(creationId)
        val payload = mapOf(
            "userId" to uid,
            "type" to type,
            "prompt" to prompt.take(2000),
            "title" to title.take(200),
            "category" to category.take(50),
            "language" to language,
            "content" to content.take(20000),
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(creationId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeSavedCreations(): Flow<List<SavedCreation>> = callbackFlow {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            close(e)
            return@callbackFlow
        }

        val query = db.collection("saved_creations")
            .whereEqualTo("userId", uid)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "saved_creations")
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val creations = snapshot.toObjects(SavedCreation::class.java)
                    .sortedByDescending { it.createdAt }
                trySend(creations)
            }
        }

        awaitClose {
            registration.remove()
        }
    }
}
