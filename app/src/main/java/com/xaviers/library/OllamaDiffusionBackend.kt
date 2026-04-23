package com.xaviers.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class OllamaDiffusionBackend(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("visual_backend", Context.MODE_PRIVATE)
    private val cacheDir = File(appContext.cacheDir, "visual-story-diffusion").apply { mkdirs() }
    @Volatile private var lastFailureAtMs = 0L

    fun isConfigured(): Boolean {
        return prefs.getBoolean(KEY_ENABLED, true) && baseUrl().isNotBlank()
    }

    fun generateScene(
        state: PlaybackVisualState,
        width: Int,
        height: Int,
        onComplete: (Bitmap?) -> Unit
    ) {
        if (!isConfigured() || isInFailureCooldown()) {
            onComplete(null)
            return
        }

        Thread {
            val bitmap = runCatching {
                val prompt = buildPrompt(state)
                val size = generationSize(width, height)
                val cacheFile = File(cacheDir, "${sha256(prompt + size.first + size.second)}.png")
                if (cacheFile.exists()) {
                    BitmapFactory.decodeFile(cacheFile.absolutePath)?.let { return@runCatching it }
                }

                val generated = requestTxt2Img(prompt, size.first, size.second)
                generated?.let { bitmap ->
                    cacheFile.outputStream().use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    }
                }
                generated
            }.getOrElse {
                lastFailureAtMs = System.currentTimeMillis()
                null
            }
            onComplete(bitmap)
        }.start()
    }

    private fun requestTxt2Img(prompt: String, width: Int, height: Int): Bitmap? {
        val endpoint = "${baseUrl().trimEnd('/')}/sdapi/v1/txt2img"
        val body = JSONObject()
            .put("prompt", prompt)
            .put("negative_prompt", NEGATIVE_PROMPT)
            .put("steps", prefs.getString(KEY_STEPS, "28")?.toIntOrNull()?.coerceIn(12, 48) ?: 28)
            .put("cfg_scale", prefs.getString(KEY_CFG, "7.0")?.toDoubleOrNull()?.coerceIn(4.0, 10.0) ?: 7.0)
            .put("width", width)
            .put("height", height)
            .put("sampler_name", "DPM++ 2M Karras")
            .put("enable_hr", true)
            .put("hr_scale", 1.45)
            .put("hr_upscaler", "4x-UltraSharp")
            .put("denoising_strength", 0.34)
            .put("restore_faces", true)
            .put("send_images", true)
            .put("save_images", false)

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 3500
            readTimeout = 90000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        connection.outputStream.use { stream ->
            stream.write(body.toString().toByteArray(Charsets.UTF_8))
        }

        if (connection.responseCode !in 200..299) {
            connection.disconnect()
            return null
        }

        val response = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()

        val rawImage = JSONObject(response)
            .optJSONArray("images")
            ?.optString(0)
            ?.substringAfter(",", "")
            .orEmpty()
        if (rawImage.isBlank()) return null

        val bytes = Base64.decode(rawImage, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun buildPrompt(state: PlaybackVisualState): String {
        val customStyle = prefs.getString(KEY_STYLE, DEFAULT_STYLE).orEmpty().ifBlank { DEFAULT_STYLE }
        return PromptPackRepository.get(appContext).buildVisualPrompt(state, customStyle)
    }

    private fun generationSize(width: Int, height: Int): Pair<Int, Int> {
        val safeWidth = width.coerceAtLeast(512)
        val safeHeight = height.coerceAtLeast(512)
        val scale = min(896f / safeWidth, 768f / safeHeight).coerceAtMost(1f)
        val generatedWidth = roundToMultiple(max(512, (safeWidth * scale).roundToInt()), 64)
        val generatedHeight = roundToMultiple(max(512, (safeHeight * scale).roundToInt()), 64)
        return generatedWidth to generatedHeight
    }

    private fun roundToMultiple(value: Int, multiple: Int): Int {
        return ((value + multiple / 2) / multiple * multiple).coerceAtLeast(multiple)
    }

    private fun isInFailureCooldown(): Boolean {
        return System.currentTimeMillis() - lastFailureAtMs < 15_000L
    }

    private fun baseUrl(): String {
        return prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL).orEmpty().trim()
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val KEY_ENABLED = "diffusion_enabled"
        const val KEY_BASE_URL = "diffusion_base_url"
        const val KEY_STYLE = "diffusion_style"
        const val KEY_STEPS = "diffusion_steps"
        const val KEY_CFG = "diffusion_cfg"
        const val DEFAULT_BASE_URL = "http://127.0.0.1:7860"
        const val DEFAULT_STYLE =
            "premium cinematic rendering, complete body composition, no text, no logos, consistent world continuity across scenes"
        private const val NEGATIVE_PROMPT =
            "watermark, logo, text, caption, subtitles, lowres, low quality, blurry, muddy, flat, overexposed, underexposed, bad anatomy, bad hands, extra fingers, missing fingers, distorted face, duplicate body, deformed eyes, broken limbs, cropped head, cut off body, malformed vehicle, jpeg artifacts"
    }
}
