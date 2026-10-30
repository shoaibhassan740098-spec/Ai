package com.example.ui.imagegen

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AiService
import com.example.data.repository.ChatRepository
import com.example.util.ImageSaver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "ImageGenViewModel"

sealed interface ImageGenState {
    data object Idle : ImageGenState
    data object Loading : ImageGenState
    data class Success(val bitmap: Bitmap, val prompt: String) : ImageGenState
    data class Error(val message: String) : ImageGenState
}

data class ImageGenUiState(
    val state: ImageGenState = ImageGenState.Idle,
    val isSaving: Boolean = false,
    val savedUri: Uri? = null,
    val saveMessage: String? = null
)

class ImageGenViewModel(application: Application) : AndroidViewModel(application) {

    private val aiService = AiService()
    private val repository = ChatRepository(application)

    private val _uiState = MutableStateFlow(ImageGenUiState())
    val uiState: StateFlow<ImageGenUiState> = _uiState.asStateFlow()

    fun generateImage(userPrompt: String, stylePrefix: String = "") {
        val finalPrompt = if (stylePrefix.isNotBlank()) "$stylePrefix, $userPrompt" else userPrompt
        if (finalPrompt.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                state = ImageGenState.Loading,
                savedUri = null,
                saveMessage = null
            )

            val result = aiService.generateImage(finalPrompt)
            if (result.isSuccess) {
                val bitmap = result.getOrThrow()
                _uiState.value = _uiState.value.copy(
                    state = ImageGenState.Success(bitmap, finalPrompt)
                )

                // Save generation metadata to Firestore
                repository.saveCreation(
                    type = "image",
                    prompt = finalPrompt,
                    title = if (userPrompt.length > 30) userPrompt.take(30) + "..." else userPrompt,
                    category = "image_generation",
                    language = "en",
                    content = "AI Generated Artwork"
                )
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Image generation failed."
                Log.e(TAG, "Image generation error: $err")
                _uiState.value = _uiState.value.copy(
                    state = ImageGenState.Error(err)
                )
            }
        }
    }

    fun saveToPhone(context: Context, bitmap: Bitmap, prompt: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveMessage = null)

            val cleanTitle = prompt.take(20).replace(Regex("[^a-zA-Z0-9]"), "_")
            val saveResult = ImageSaver.saveBitmapToGallery(context, bitmap, "MyAI_$cleanTitle")

            if (saveResult.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    savedUri = saveResult.getOrThrow(),
                    saveMessage = "Image saved to phone gallery!"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveMessage = "Failed to save: ${saveResult.exceptionOrNull()?.message}"
                )
            }
        }
    }
}
