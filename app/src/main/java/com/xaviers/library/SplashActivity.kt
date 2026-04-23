package com.xaviers.library

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.xaviers.library.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playEntrance()
        binding.root.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, 1350L)
    }

    private fun playEntrance() {
        pulse(binding.topGlow, 0.18f, 0.36f, 3200L)
        pulse(binding.bottomGlow, 0.08f, 0.18f, 4000L)

        binding.wordmarkWrap.apply {
            alpha = 0f
            translationY = 28f
            scaleX = 0.96f
            scaleY = 0.96f
            animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(620L)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }

        binding.splashKicker.apply {
            alpha = 0f
            translationY = -10f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(460L)
                .setStartDelay(120L)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }

        binding.splashSubtitle.apply {
            alpha = 0f
            translationY = 12f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(520L)
                .setStartDelay(200L)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }

        binding.splashSigil.apply {
            alpha = 0f
            scaleX = 0.8f
            scaleY = 0.8f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(540L)
                .setStartDelay(180L)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
    }

    private fun pulse(view: View, minAlpha: Float, maxAlpha: Float, duration: Long) {
        ObjectAnimator.ofFloat(view, View.ALPHA, minAlpha, maxAlpha).apply {
            this.duration = duration
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }
}
