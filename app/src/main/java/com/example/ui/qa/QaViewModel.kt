package com.example.ui.qa

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.SavedCreation
import com.example.data.repository.AiService
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

private const val TAG = "QaViewModel"

data class CategoryItem(
    val id: String,
    val nameEn: String,
    val nameUr: String,
    val iconName: String,
    val sampleQuestionsEn: List<String>,
    val sampleQuestionsUr: List<String>
)

data class QaUiState(
    val selectedCategory: String = "education",
    val selectedLanguage: String = "ur", // "ur" or "en"
    val currentQuestion: String = "",
    val currentAnswer: String? = null,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val savedCreations: List<SavedCreation> = emptyList(),
    val errorMessage: String? = null
)

class QaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application)
    private val aiService = AiService()

    private val _uiState = MutableStateFlow(QaUiState())
    val uiState: StateFlow<QaUiState> = _uiState.asStateFlow()

    val categories = listOf(
        CategoryItem(
            id = "education",
            nameEn = "Education",
            nameUr = "تعلیم و تربیت",
            iconName = "School",
            sampleQuestionsEn = listOf(
                "How to develop deep critical thinking and memory recall?",
                "What are the best study habits backed by cognitive science?",
                "How will AI transform higher education in the next decade?",
                "Tips for preparing for competitive exams without stress"
            ),
            sampleQuestionsUr = listOf(
                "پڑھائی میں یادداشت اور توجہ مرکوز کرنے کے بہترین سائنسی طریقے کیا ہیں؟",
                "امتحانات کی موثر اور بغیر پریشانی تیاری کے رہنما اصول",
                "تعلیم میں مصنوعی ذہانت (AI) کا درست استعمال کیسے کریں؟",
                "طالب علموں کے لیے کیریئر کونسلنگ اور صحیح شعبے کا انتخاب"
            )
        ),
        CategoryItem(
            id = "technology",
            nameEn = "Technology",
            nameUr = "ٹیکنالوجی و جدت",
            iconName = "Computer",
            sampleQuestionsEn = listOf(
                "Explain the fundamentals and real-world impact of Quantum Computing.",
                "How do Large Language Models (LLMs) learn and generate human-like text?",
                "Future of Cybersecurity: How to protect against zero-day exploits?",
                "What is Web3 and Decentralized Identity?"
            ),
            sampleQuestionsUr = listOf(
                "کوانٹم کمپیوٹنگ کیا ہے اور یہ روایتی کمپیوٹرز سے کیسے مختلف ہے؟",
                "لارج لینگویج ماڈلز (LLMs) کیسے کام کرتے ہیں؟",
                "سائبر سیکیورٹی میں ڈیٹا اور پاس ورڈز کی حفاظت کے اہم طریقے",
                "کلاؤڈ کمپیوٹنگ کے بنیادی اصول اور مستقبل کے رجحانات"
            )
        ),
        CategoryItem(
            id = "science",
            nameEn = "Science",
            nameUr = "سائنس و کائنات",
            iconName = "Science",
            sampleQuestionsEn = listOf(
                "How does CRISPR gene editing work and what are its ethical boundaries?",
                "Explain Black Holes, Event Horizons, and Hawking Radiation.",
                "How do mRNA vaccines work inside the human immune system?",
                "What are the leading theories about dark matter and dark energy?"
            ),
            sampleQuestionsUr = listOf(
                "بلیک ہولز اور کشش ثقل کے کائناتی راز کیا ہیں؟",
                "انسانی ڈی این اے (DNA) اور جین ایڈیٹنگ کیسے کام کرتی ہے؟",
                "زمین کے ماحولیاتی تغیرات (Climate Change) کے سائنسی اسباب",
                "کائنات کی وسعت اور ڈارک میٹر کی پراسرار حقیقتیں"
            )
        ),
        CategoryItem(
            id = "business",
            nameEn = "Business",
            nameUr = "کاروبار و معیشت",
            iconName = "Business",
            sampleQuestionsEn = listOf(
                "How to validate a SaaS startup idea before building an MVP?",
                "Strategies for customer acquisition and retention in competitive markets.",
                "How to create a balanced financial investment portfolio in 2026?",
                "Principles of ethical leadership and organizational growth."
            ),
            sampleQuestionsUr = listOf(
                "کم سرمائے سے آن لائن یا ای کامرس بزنس کیسے شروع کریں؟",
                "مارکیٹنگ اور کسٹمر لائلٹی بڑھانے کی مؤثر حکمت عملی",
                "ذاتی مالیات (Personal Finance) اور بجٹ بنانے کے اہم اصول",
                "ایک کامیاب کاروباری لیڈر کی بنیادی خصوصیات"
            )
        ),
        CategoryItem(
            id = "general",
            nameEn = "General Knowledge",
            nameUr = "عام معلومات و تاریخ",
            iconName = "Public",
            sampleQuestionsEn = listOf(
                "What was the Silk Road and how did it shape world trade and culture?",
                "How does the United Nations international law system function?",
                "The evolution of money: From barter system to digital central bank currencies.",
                "Why do oceans have tides and how do lunar cycles affect Earth?"
            ),
            sampleQuestionsUr = listOf(
                "شاہراہ ریشم (Silk Road) کی تاریخی اہمیت اور تہذیبی اثرات کیا تھے؟",
                "دنیا کے سات نئے عجائبات اور ان کا تاریخی پس منظر",
                "روپے اور کرنسی کی تاریخ: بارٹر سسٹم سے ڈیجیٹل کرنسی تک",
                "سمندروں میں مد و جزر (Tides) اور چاند کی کشش ثقل کا تعلق"
            )
        )
    )

    init {
        observeSavedCreations()
    }

    private fun observeSavedCreations() {
        viewModelScope.launch {
            repository.observeSavedCreations()
                .catch { e -> Log.e(TAG, "Failed to observe saved creations", e) }
                .collect { list ->
                    _uiState.value = _uiState.value.copy(savedCreations = list)
                }
        }
    }

    fun selectCategory(categoryId: String) {
        _uiState.value = _uiState.value.copy(selectedCategory = categoryId)
    }

    fun toggleLanguage() {
        val next = if (_uiState.value.selectedLanguage == "ur") "en" else "ur"
        _uiState.value = _uiState.value.copy(selectedLanguage = next)
    }

    fun askQuestion(questionText: String) {
        val q = questionText.trim()
        if (q.isEmpty() || _uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                currentQuestion = q,
                isLoading = true,
                isSaved = false,
                errorMessage = null
            )

            val category = categories.find { it.id == _uiState.value.selectedCategory }?.nameEn ?: "General"
            val result = aiService.generateAnswer(q, category, _uiState.value.selectedLanguage)

            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    currentAnswer = result.getOrThrow(),
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Failed to generate answer."
                )
            }
        }
    }

    fun saveCurrentAnswer() {
        val answer = _uiState.value.currentAnswer ?: return
        val question = _uiState.value.currentQuestion
        val category = _uiState.value.selectedCategory
        val lang = _uiState.value.selectedLanguage

        viewModelScope.launch {
            val title = if (question.length > 40) question.take(40) + "..." else question
            val result = repository.saveCreation(
                type = "qa",
                prompt = question,
                title = title,
                category = category,
                language = lang,
                content = answer
            )
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(isSaved = true)
            }
        }
    }
}
