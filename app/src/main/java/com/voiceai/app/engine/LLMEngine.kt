package com.voiceai.app.engine

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LLMEngine(private val context: Context) {

    private var llmInference: LlmInference? = null

    val modelFile: File
        get() = File(context.filesDir, "models/$MODEL_FILENAME")

    val modelExists: Boolean
        get() = modelFile.exists() && modelFile.length() > MIN_MODEL_SIZE_BYTES

    companion object {
        const val MODEL_FILENAME = "gemma-2b-it-cpu-int4.bin"

        // Direct download URL for the MediaPipe-optimised Gemma 2B IT CPU INT4 model.
        // Hosted by Google; no authentication required.
        const val MODEL_DOWNLOAD_URL =
            "https://storage.googleapis.com/mediapipe-models/llm_inference/" +
                    "gemma-2b-it-cpu-int4/float16/1/gemma-2b-it-cpu-int4.bin"

        // Guard against partial downloads being treated as valid models
        private const val MIN_MODEL_SIZE_BYTES = 100_000_000L

        private const val MAX_TOKENS = 512

        // Prompt engineering: small instruction-tuned models respond best to
        // a terse, explicit tool contract so they reliably emit [SEARCH:...]
        // rather than hallucinating stale facts.
        private val SYSTEM_PROMPT = """
You are a concise voice assistant. Rules:
1. Keep every answer under 3 sentences — it will be read aloud.
2. When you need real-time data (weather, news, live scores, current prices, today's date/time) reply with ONLY: [SEARCH: your query]
3. For everything else, answer directly.
""".trimIndent()
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val options = LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(MAX_TOKENS)
            .setTopK(40)
            .setTemperature(0.7f)
            .setRandomSeed(101)
            .build()
        llmInference = LlmInference.createFromOptions(context, options)
    }

    suspend fun generate(userMessage: String, searchContext: String? = null): String =
        withContext(Dispatchers.IO) {
            val prompt = buildGemmaPrompt(userMessage, searchContext)
            llmInference?.generateResponse(prompt)?.trim() ?: ""
        }

    // Returns the search query if the model signalled it needs live data,
    // or null if the response is a direct answer.
    fun extractSearchQuery(response: String): String? {
        val match = Regex("""\[SEARCH:\s*(.+?)\]""").find(response)
        return match?.groupValues?.get(1)?.trim()
    }

    private fun buildGemmaPrompt(userMessage: String, searchContext: String?): String {
        val userContent = if (searchContext != null) {
            "Search results: $searchContext\n\nUsing those results, answer: $userMessage"
        } else {
            userMessage
        }
        // Gemma instruction-tuned conversation format
        return "<start_of_turn>user\n$SYSTEM_PROMPT\n\n$userContent<end_of_turn>\n<start_of_turn>model\n"
    }

    fun release() {
        llmInference?.close()
        llmInference = null
    }
}
