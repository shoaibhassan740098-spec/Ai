package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createSession_authenticatedUser_success(): Unit = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repository = ChatRepository(firestore)

    val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createSession("Urdu AI Chat", "ur")
    }
    assertTrue(createResult.isSuccess)
    val sessionId = createResult.getOrThrow()
    assertTrue(sessionId.isNotEmpty())
  }

  @Test
  fun observeSessions_authenticatedUser_emitsSessions(): Unit = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repository = ChatRepository(firestore)
    val sessionId = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createSession("Tech Discussion", "en").getOrThrow()
    }

    val sessions = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.observeSessions().first { list -> list.any { it.id == sessionId } }
    }
    assertTrue(sessions.any { it.id == sessionId })
  }

  @Test
  fun addMessage_authenticatedUser_success(): Unit = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repository = ChatRepository(firestore)
    val sessionId = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createSession("Q&A Session", "en").getOrThrow()
    }

    val msgResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.addMessage(sessionId, "user", "What is Machine Learning?", "en")
    }
    assertTrue(msgResult.isSuccess)
    val msgId = msgResult.getOrThrow()
    assertTrue(msgId.isNotEmpty())
  }

  @Test
  fun createSession_unauthenticatedUser_fails(): Unit = runBlocking {
    auth.signOut()
    val repository = ChatRepository(firestore)

    var failed = false
    try {
      repository.createSession("Unauthenticated Chat", "en")
    } catch (e: IllegalStateException) {
      failed = true
    }
    assertTrue(failed)
  }

  private companion object {
    const val ALICE_EMAIL = "alice_test@myai.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
  }
}
