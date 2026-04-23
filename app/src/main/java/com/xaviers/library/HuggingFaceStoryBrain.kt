package com.xaviers.library

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class HuggingFaceStoryBrain(context: Context) {

    companion object {
        const val KEY_ENABLED = "hf_story_enabled"
        const val KEY_API_TOKEN = "hf_api_token"
        const val KEY_MODEL = "hf_model_id"
        const val KEY_STYLE = "hf_style_prompt"
        const val KEY_MAX_TOKENS = "hf_max_tokens"
        const val KEY_TEMPERATURE = "hf_temperature"

        const val DEFAULT_MODEL = "Qwen/Qwen2.5-7B-Instruct"
        const val DEFAULT_STYLE =
            "Keep the same universe locked, continue Kael's long-form arc, preserve cinematic continuity, and return usable memory tracking."
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("hf_story_brain", Context.MODE_PRIVATE)

    fun isConfigured(): Boolean {
        return prefs.getBoolean(KEY_ENABLED, false) &&
            prefs.getString(KEY_API_TOKEN, "").orEmpty().isNotBlank()
    }

    fun enhancePayload(payload: PlaybackPayload, callback: (PlaybackPayload?) -> Unit) {
        if (!isConfigured()) {
            callback(null)
            return
        }

        Thread {
            val token = prefs.getString(KEY_API_TOKEN, "").orEmpty().trim()
            val model = prefs.getString(KEY_MODEL, DEFAULT_MODEL).orEmpty().trim().ifBlank { DEFAULT_MODEL }
            val style = prefs.getString(KEY_STYLE, DEFAULT_STYLE).orEmpty().trim().ifBlank { DEFAULT_STYLE }
            val maxTokens = prefs.getString(KEY_MAX_TOKENS, "420").orEmpty().toIntOrNull()?.coerceIn(180, 900) ?: 420
            val temperature = prefs.getString(KEY_TEMPERATURE, "0.7").orEmpty().toDoubleOrNull()?.coerceIn(0.1, 1.2) ?: 0.7

            val prompt = buildPrompt(payload, style)
            val rawText = requestWithRouter(token, model, prompt, maxTokens, temperature)
                ?: requestWithClassicInference(token, model, prompt, maxTokens, temperature)

            if (rawText.isNullOrBlank()) {
                callback(null)
                return@Thread
            }

            callback(parseEnhancedPayload(rawText, payload))
        }.start()
    }

    private fun buildPrompt(payload: PlaybackPayload, style: String): String {
        return PromptPackRepository.get(appContext).buildStoryPrompt(payload, style)
    }

    private fun requestWithRouter(
        token: String,
        model: String,
        prompt: String,
        maxTokens: Int,
        temperature: Double
    ): String? {
        return runCatching {
            val url = URL("https://router.huggingface.co/hf-inference/models/$model/v1/chat/completions")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 9_000
                readTimeout = 16_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
            }

            val body = JSONObject().apply {
                put("model", model)
                put("temperature", temperature)
                put("max_tokens", maxTokens)
                put(
                    "messages",
                    JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                )
            }

            connection.outputStream.use { out ->
                out.write(body.toString().toByteArray(StandardCharsets.UTF_8))
            }

            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: return@runCatching null
            }
            val response = stream.bufferedReader().use { reader -> reader.readText() }
            val json = JSONObject(response)
            json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
        }.getOrNull()
    }

    private fun requestWithClassicInference(
        token: String,
        model: String,
        prompt: String,
        maxTokens: Int,
        temperature: Double
    ): String? {
        return runCatching {
            val url = URL("https://api-inference.huggingface.co/models/$model")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 9_000
                readTimeout = 18_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
            }

            val body = JSONObject().apply {
                put("inputs", prompt)
                put("options", JSONObject().put("wait_for_model", true))
                put(
                    "parameters",
                    JSONObject()
                        .put("temperature", temperature)
                        .put("max_new_tokens", maxTokens)
                        .put("return_full_text", false)
                )
            }

            connection.outputStream.use { out ->
                out.write(body.toString().toByteArray(StandardCharsets.UTF_8))
            }

            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: return@runCatching null
            }
            val response = InputStreamReader(stream, StandardCharsets.UTF_8).readText()

            val trimmed = response.trim()
            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                array.optJSONObject(0)?.optString("generated_text")?.trim()
            } else if (trimmed.startsWith("{")) {
                JSONObject(trimmed).optString("generated_text").trim().ifBlank { null }
            } else {
                null
            }
        }.getOrNull()
    }

    private fun parseEnhancedPayload(raw: String, payload: PlaybackPayload): PlaybackPayload? {
        val parsedJson = extractJsonObject(raw) ?: return null
        val subtitle = parsedJson.optString("subtitle").ifBlank { payload.subtitle }
        val summary = parsedJson.optString("summary").ifBlank { payload.text }
        val imageStyle = parsedJson.optString("image_style").trim()
        val memoryTracking = parsedJson.optJSONArray("memory_tracking")
            ?.let(::parseStringArray)
            ?.takeIf { it.isNotEmpty() }
            ?: payload.memoryTracking
        val enhancedBeats = parsedJson.optJSONArray("beats")?.let { beats ->
            payload.visualBeats.map { beat ->
                val matchedBoost = findBeatBoost(beats, beat.label)
                if (matchedBoost.isBlank() && imageStyle.isBlank()) {
                    beat
                } else {
                    beat.copy(
                        prompt = listOf(beat.prompt, imageStyle, matchedBoost)
                            .filter { it.isNotBlank() }
                            .joinToString(", "),
                        shortCaption = if (imageStyle.isBlank()) beat.shortCaption else "${beat.shortCaption} • ${imageStyle.lowercase()}"
                    )
                }
            }
        } ?: payload.visualBeats

        if (summary.isBlank() && enhancedBeats == payload.visualBeats && subtitle == payload.subtitle) {
            return null
        }
        return payload.copy(
            subtitle = subtitle,
            text = summary,
            memoryTracking = memoryTracking,
            idleStatus = "Story AI locked the chapter and visual direction",
            visualBeats = enhancedBeats,
            visualEngineLabel = "AI scene direction"
        )
    }

    private fun findBeatBoost(beats: JSONArray, label: String): String {
        for (index in 0 until beats.length()) {
            val item = beats.optJSONObject(index) ?: continue
            if (item.optString("label").trim().equals(label.trim(), ignoreCase = true)) {
                return item.optString("prompt_boost").trim()
            }
        }
        return ""
    }

    private fun extractJsonObject(text: String): JSONObject? {
        val fenced = Regex("```(?:json)?\\s*(\\{.*?})\\s*```", RegexOption.DOT_MATCHES_ALL)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        val candidate = fenced ?: run {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            text.substring(start, end + 1).trim()
        }
        return runCatching { JSONObject(candidate) }.getOrNull()
    }

    private fun parseStringArray(array: JSONArray): List<String> {
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index)
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?.let(::add)
            }
        }
    }
}
