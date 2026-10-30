package com.example.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "AiService"

class AiService {

    // Lazy initialization of Gemini 3.8 Flash for text chat & Q&A
    private val textModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    """
                    You are "My AI", an exceptionally smart, polite, and helpful bilingual AI assistant fluent in Urdu (اردو) and English.
                    - If the user writes in Urdu or requests Urdu, respond in natural, elegant, grammatically pristine Urdu (اردو).
                    - If the user writes in English, respond in articulate, professional English.
                    - For topics in Education, Science, Technology, Business, and General Knowledge, provide comprehensive, structured, easy-to-read insights with bullet points and clear takeaways.
                    - Always be encouraging, insightful, and concise yet thorough.
                    """.trimIndent()
                )
            }
        )
    }

    // Lazy initialization of image generation model
    private val imageModel by lazy {
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = config
        )
    }

    // Stream text generation
    fun generateChatStream(prompt: String, conversationHistory: List<Pair<String, String>> = emptyList()): Flow<String> = flow {
        // Construct prompt with context if history exists
        val formattedPrompt = if (conversationHistory.isNotEmpty()) {
            val historyContext = conversationHistory.takeLast(6).joinToString("\n") { (role, text) ->
                "${if (role == "user") "User" else "Assistant"}: $text"
            }
            "$historyContext\nUser: $prompt\nAssistant:"
        } else {
            prompt
        }

        withTimeout(60_000L) {
            val responseStream = textModel.generateContentStream(formattedPrompt)
            responseStream.collect { chunk ->
                val chunkText = chunk.text.orEmpty()
                if (chunkText.isNotEmpty()) {
                    emit(chunkText)
                }
            }
        }
    }

    // Unary text generation for quick Q&A explanations
    suspend fun generateAnswer(question: String, category: String, language: String): Result<String> {
        return try {
            val prompt = if (language == "ur") {
                "موضوع: $category\nسوال: $question\nبراہ کرم اس کا مفصل اور آسان فہم جواب اردو میں فراہم کریں۔"
            } else {
                "Category: $category\nQuestion: $question\nPlease provide a clear, comprehensive, and well-structured answer."
            }

            val response = withTimeout(60_000L) {
                textModel.generateContent(prompt)
            }
            val text = response.text
            if (!text.isNullOrBlank()) {
                Result.success(text)
            } else {
                Result.failure(Exception("Model returned an empty answer."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "generateAnswer failed", e)
            Result.failure(e)
        }
    }

    // Text to image generation
    suspend fun generateImage(prompt: String): Result<Bitmap> {
        return try {
            val response = withTimeout(60_000L) {
                imageModel.generateContent(prompt)
            }
            val imagePart = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()

            val bitmap = imagePart?.image
            if (bitmap != null) {
                Result.success(bitmap)
            } else {
                Result.failure(Exception("No image was returned by the AI model."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "generateImage failed", e)
            Result.failure(e)
        }
    }
}
