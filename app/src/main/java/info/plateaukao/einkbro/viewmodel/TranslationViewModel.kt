package info.plateaukao.einkbro.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.plateaukao.einkbro.preference.ConfigManager
import info.plateaukao.einkbro.service.TranslateRepository
import info.plateaukao.einkbro.util.TranslationLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.withContext
import org.apache.commons.text.StringEscapeUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TranslationViewModel : ViewModel(), KoinComponent {
    private val translateRepository = TranslateRepository()
    private val config: ConfigManager by inject()

    private val _responseMessage = MutableStateFlow("")
    val responseMessage: StateFlow<String> = _responseMessage.asStateFlow()

    private val _inputMessage = MutableStateFlow("")
    val inputMessage: StateFlow<String> = _inputMessage.asStateFlow()

    private val _translationLanguage = MutableStateFlow(config.translationLanguage)
    val translationLanguage: StateFlow<TranslationLanguage> = _translationLanguage.asStateFlow()

    fun updateInputMessage(userMessage: String) {
        _inputMessage.value = StringEscapeUtils.unescapeJava(userMessage)
        _responseMessage.value = "..."
    }

    fun updateTranslationLanguage(language: TranslationLanguage) {
        _translationLanguage.value = language
        _responseMessage.value = "..."
        query(_inputMessage.value)
    }

    fun query(userMessage: String? = null) {
        if (userMessage != null) {
            _inputMessage.value = userMessage
        }

        viewModelScope.launch(Dispatchers.IO) {
            _responseMessage.value =
                translateRepository.gTranslate(
                    _inputMessage.value,
                    targetLanguage = config.translationLanguage.value
                )
                    ?: "Something went wrong."
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    val scope = CoroutineScope(newFixedThreadPoolContext(10, "requests"))

    suspend fun translateByParagraph(html: String): String {
        val parsedHtml = Jsoup.parse(html)
        val nodesWithText = fetchNodesWithText(parsedHtml)
        withContext(scope.coroutineContext) {
            nodesWithText.forEach { node ->
                async {
                    val translation =
                        translateRepository.gTranslate(
                            node.text(),
                            config.translationLanguage.value
                        )
                    node.append("<br><br>$translation")
                }.await()
            }
        }

        return parsedHtml.toString()
    }

    private fun fetchNodesWithText(
        element: Element,
    ): List<Element> {
        val result = mutableListOf<Element>()
        for (child in element.children()) {
            if ((child.children().size == 0 && child.text().isNotBlank()) ||
                child.tagName() == "p"
            ) {
                if (child.text().isNotEmpty()) {
                    result += child
                }
            } else {
                result += fetchNodesWithText(child)
            }
        }
        return result
    }
}