package com.xaviers.library

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.xaviers.library.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val libraryEngine = LibraryEngine()
    private var currentState = TomeState.DORMANT
    private var currentBackgroundColor = 0
    private lateinit var currentSnapshot: RitualSnapshot
    private val indicatorViews by lazy {
        listOf(
            binding.indicatorOne,
            binding.indicatorTwo,
            binding.indicatorThree,
            binding.indicatorFour
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        startAmbientPulse()
        startTomeDrift()

        binding.advanceStateButton.setOnClickListener {
            val nextState = currentState.next()
            renderState(nextState, animate = true, snapshot = libraryEngine.advanceTo(nextState))
        }
        binding.advanceStateButton.setOnLongClickListener { shareCurrentOmen() }

        binding.tomeImage.setOnClickListener {
            val nextState = currentState.next()
            renderState(nextState, animate = true, snapshot = libraryEngine.advanceTo(nextState))
        }
        binding.tomeImage.setOnLongClickListener { shareCurrentOmen() }

        renderState(currentState, animate = false, snapshot = libraryEngine.snapshotFor(currentState))
    }

    private fun renderState(state: TomeState, animate: Boolean, snapshot: RitualSnapshot) {
        val previousBackground = currentBackgroundColor
        val nextBackground = ContextCompat.getColor(this, state.backgroundRes)
        currentState = state
        currentBackgroundColor = nextBackground
        currentSnapshot = snapshot

        binding.stateBadge.text = getString(state.titleRes)
        binding.librarySubtitle.text = snapshot.librarySubtitle
        binding.stateOverline.text = snapshot.stateOverline
        binding.stateTitle.text = getString(state.titleRes)
        binding.stateSubtitle.text = getString(state.subtitleRes)
        binding.ritualHint.text = snapshot.ritualHint
        binding.imageCaption.text = snapshot.imageCaption
        binding.tomeImage.setImageResource(state.imageRes)
        binding.advanceStateButton.text = snapshot.buttonLabel

        val accentColor = ContextCompat.getColor(this, state.accentRes)
        binding.stateBadge.chipBackgroundColor = ContextCompat.getColorStateList(this, state.accentRes)
        binding.advanceStateButton.backgroundTintList = ColorStateList.valueOf(accentColor)
        binding.topGlow.backgroundTintList = ColorStateList.valueOf(accentColor)
        binding.bottomGlow.backgroundTintList = ColorStateList.valueOf(accentColor)
        binding.edgeAura.backgroundTintList = ColorStateList.valueOf(accentColor)
        binding.ghostFlash.backgroundTintList = ColorStateList.valueOf(accentColor)
        binding.stateCard.strokeColor = accentColor
        binding.imageCard.strokeColor = accentColor
        updateIndicators(state, accentColor)

        if (animate) {
            binding.tomeImage.apply {
                alpha = 0f
                scaleX = 0.96f
                scaleY = 0.96f
                animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setInterpolator(DecelerateInterpolator())
                    .setDuration(420L)
                    .start()
            }

            binding.imageCaption.apply {
                alpha = 0f
                translationY = 12f
                animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(300L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }

            binding.stateCard.apply {
                alpha = 0.82f
                translationY = 12f
                animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(320L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }

            binding.stateBadge.apply {
                scaleX = 0.92f
                scaleY = 0.92f
                animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(260L)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }

            binding.imageCard.apply {
                scaleX = 0.985f
                scaleY = 0.985f
                animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setInterpolator(DecelerateInterpolator())
                    .setDuration(360L)
                    .start()
            }

            ValueAnimator.ofObject(ArgbEvaluator(), previousBackground, nextBackground).apply {
                duration = 500L
                addUpdateListener { animator ->
                    binding.root.setBackgroundColor(animator.animatedValue as Int)
                }
                start()
            }

            playGhostFlash()
            playShimmerSweep()
        } else {
            binding.root.setBackgroundColor(nextBackground)
            binding.imageCaption.alpha = 1f
            binding.shimmerSweep.alpha = 0f
            binding.ghostFlash.alpha = 0f
        }

        snapshot.eventBanner?.let { event ->
            Snackbar.make(binding.root, event, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun updateIndicators(state: TomeState, accentColor: Int) {
        indicatorViews.forEachIndexed { index, view ->
            val isReached = index <= state.ordinal
            val color = if (isReached) accentColor else ContextCompat.getColor(this, R.color.text_tertiary)
            val alpha = if (isReached) 1f else 0.28f
            view.backgroundTintList = ColorStateList.valueOf(color)
            view.alpha = alpha
            view.animate()
                .scaleX(if (isReached) 1.15f else 1f)
                .scaleY(if (isReached) 1.15f else 1f)
                .setDuration(220L)
                .start()
        }
    }

    private fun startAmbientPulse() {
        pulseView(binding.topGlow, minAlpha = 0.14f, maxAlpha = 0.34f, duration = 3600L)
        pulseView(binding.bottomGlow, minAlpha = 0.08f, maxAlpha = 0.2f, duration = 4200L)
    }

    private fun pulseView(view: View, minAlpha: Float, maxAlpha: Float, duration: Long) {
        ObjectAnimator.ofFloat(view, View.ALPHA, minAlpha, maxAlpha).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun startTomeDrift() {
        ObjectAnimator.ofFloat(binding.tomeImage, View.TRANSLATION_Y, -8f, 8f).apply {
            duration = 4200L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        ObjectAnimator.ofFloat(binding.imageCard, View.TRANSLATION_X, -3f, 3f).apply {
            duration = 5200L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun playGhostFlash() {
        binding.ghostFlash.apply {
            alpha = 0f
            scaleX = 1f
            scaleY = 1f
            animate()
                .alpha(0.36f)
                .scaleX(1.03f)
                .scaleY(1.03f)
                .setDuration(120L)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    animate()
                        .alpha(0f)
                        .scaleX(1.08f)
                        .scaleY(1.08f)
                        .setDuration(300L)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                .start()
        }
    }

    private fun playShimmerSweep() {
        binding.shimmerSweep.apply {
            alpha = 0f
            translationX = -binding.imageCard.width.toFloat() * 0.55f
            animate().cancel()
            animate()
                .alpha(0.52f)
                .translationX(binding.imageCard.width.toFloat() * 0.7f)
                .setDuration(760L)
                .setInterpolator(LinearInterpolator())
                .withEndAction { alpha = 0f }
                .start()
        }
    }

    private fun shareCurrentOmen(): Boolean {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
            putExtra(Intent.EXTRA_TEXT, currentSnapshot.shareText)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_chooser)))
        return true
    }
}
