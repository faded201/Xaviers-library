package com.xaviers.library

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

data class PlaybackPayload(
    val id: Int,
    val title: String,
    val subtitle: String,
    val text: String,
    val idleStatus: String
)

data class PlaybackVisualState(
    val title: String = "",
    val subtitle: String = "",
    val status: String = "",
    val progress: Int = 0,
    val levels: List<Float> = List(5) { 0.18f },
    val isPlaying: Boolean = false,
    val isReady: Boolean = false
)

class AudioVisualEngine(
    context: Context,
    private val onVisualState: (PlaybackVisualState) -> Unit
) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = TextToSpeech(appContext, this)
    private var currentPayload: PlaybackPayload? = null
    private var pendingPayload: PlaybackPayload? = null
    private var activeUtteranceId: String? = null
    private var isReady = false
    private var isPlaying = false
    private var startedAtMs = 0L
    private var estimatedDurationMs = 0L
    private var lastProgress = 0

    private val frameLoop = object : Runnable {
        override fun run() {
            val payload = currentPayload ?: return
            if (!isPlaying) return

            val elapsed = SystemClock.elapsedRealtime() - startedAtMs
            lastProgress = ((elapsed * 100f) / estimatedDurationMs)
                .roundToInt()
                .coerceIn(1, 99)

            val remainingSeconds = ((estimatedDurationMs - elapsed).coerceAtLeast(0L) / 1000f)
                .roundToInt()
                .coerceAtLeast(1)

            dispatch(
                PlaybackVisualState(
                    title = payload.title,
                    subtitle = payload.subtitle,
                    status = "Live narration • ${remainingSeconds}s left",
                    progress = lastProgress,
                    levels = animatedLevels(elapsed),
                    isPlaying = true,
                    isReady = isReady
                )
            )

            if (isPlaying) {
                mainHandler.postDelayed(this, 90L)
            }
        }
    }

    override fun onInit(status: Int) {
        isReady = status == TextToSpeech.SUCCESS

        if (isReady) {
            tts?.language = Locale.US
            tts?.setSpeechRate(0.92f)
            tts?.setPitch(0.98f)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    if (utteranceId != activeUtteranceId) return
                    mainHandler.post {
                        finishPlayback()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId != activeUtteranceId) return
                    mainHandler.post {
                        stopInternal("Voice faltered • tap play again.", resetProgress = false)
                    }
                }
            })
        }

        val payload = currentPayload ?: pendingPayload
        if (payload != null) {
            dispatch(idleState(payload, if (isReady) payload.idleStatus else "Voice engine warming..."))
        }

        pendingPayload?.takeIf { isReady }?.let { queuedPayload ->
            pendingPayload = null
            start(queuedPayload)
        }
    }

    fun seed(payload: PlaybackPayload) {
        if (isPlaying && currentPayload?.id == payload.id) return
        if (isPlaying && currentPayload?.id != payload.id) {
            stopInternal("Narration hushed.", resetProgress = true)
        }
        currentPayload = payload
        dispatch(idleState(payload, if (isReady) payload.idleStatus else "Voice engine warming..."))
    }

    fun toggle(payload: PlaybackPayload) {
        if (isPlaying && currentPayload?.id == payload.id) {
            stopInternal("Narration hushed.", resetProgress = false)
        } else {
            start(payload)
        }
    }

    fun start(payload: PlaybackPayload) {
        currentPayload = payload
        lastProgress = 0

        if (!isReady) {
            pendingPayload = payload
            dispatch(idleState(payload, "Voice engine warming..."))
            return
        }

        pendingPayload = null
        isPlaying = true
        startedAtMs = SystemClock.elapsedRealtime()
        estimatedDurationMs = estimateDurationMs(payload.text)
        activeUtteranceId = "xavier-${payload.id}-${startedAtMs}"

        dispatch(
            PlaybackVisualState(
                title = payload.title,
                subtitle = payload.subtitle,
                status = "Live narration • opening the tome",
                progress = 1,
                levels = List(5) { 0.42f },
                isPlaying = true,
                isReady = isReady
            )
        )

        mainHandler.removeCallbacks(frameLoop)
        mainHandler.post(frameLoop)

        tts?.speak(payload.text, TextToSpeech.QUEUE_FLUSH, Bundle(), activeUtteranceId)
    }

    fun stop() {
        stopInternal("Narration hushed.", resetProgress = false)
    }

    fun release() {
        mainHandler.removeCallbacks(frameLoop)
        tts?.stop()
        tts?.shutdown()
        tts = null
        isPlaying = false
        activeUtteranceId = null
    }

    private fun finishPlayback() {
        val payload = currentPayload ?: return
        mainHandler.removeCallbacks(frameLoop)
        tts?.stop()
        isPlaying = false
        activeUtteranceId = null
        lastProgress = 100
        dispatch(idleState(payload, "Episode complete • tap play to relive it.", progress = 100))
    }

    private fun stopInternal(status: String, resetProgress: Boolean) {
        val payload = currentPayload ?: return
        mainHandler.removeCallbacks(frameLoop)
        tts?.stop()
        isPlaying = false
        activeUtteranceId = null
        if (resetProgress) {
            lastProgress = 0
        }
        dispatch(idleState(payload, status, progress = lastProgress))
    }

    private fun idleState(
        payload: PlaybackPayload,
        status: String,
        progress: Int = 0
    ): PlaybackVisualState {
        return PlaybackVisualState(
            title = payload.title,
            subtitle = payload.subtitle,
            status = status,
            progress = progress.coerceIn(0, 100),
            levels = List(5) { index -> 0.18f + (index * 0.04f) },
            isPlaying = false,
            isReady = isReady
        )
    }

    private fun dispatch(state: PlaybackVisualState) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onVisualState(state)
        } else {
            mainHandler.post { onVisualState(state) }
        }
    }

    private fun estimateDurationMs(text: String): Long {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
        return (words * 380L).coerceIn(4500L, 22000L)
    }

    private fun animatedLevels(elapsed: Long): List<Float> {
        val phase = elapsed / 140.0
        return List(5) { index ->
            val wave = (sin(phase + index * 0.82) + 1.0) / 2.0
            val jitter = Random.nextDouble(0.04, 0.16)
            (0.24 + wave * 0.52 + jitter).coerceIn(0.18, 1.0).toFloat()
        }
    }
}
