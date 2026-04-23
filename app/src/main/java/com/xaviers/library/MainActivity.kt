package com.xaviers.library

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.xaviers.library.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private enum class Surface {
        RITUAL,
        LIBRARY,
        VAULT
    }

    private lateinit var binding: ActivityMainBinding
    private val libraryEngine = LibraryEngine()
    private lateinit var bookShelfAdapter: BookShelfAdapter
    private lateinit var vaultDeckAdapter: VaultDeckAdapter
    private var currentState = TomeState.DORMANT
    private var currentBackgroundColor = 0
    private lateinit var currentSnapshot: RitualSnapshot
    private var activeSurface = Surface.RITUAL
    private var selectedBookId = 0
    private val compactChrome by lazy { resources.configuration.screenHeightDp < 760 }

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

        selectedBookId = libraryEngine.currentBook().id
        setupNavigation()
        setupLists()
        setupActions()
        startAmbientPulse()
        startTomeDrift()

        renderState(
            state = currentState,
            animate = false,
            snapshot = libraryEngine.snapshotFor(currentState)
        )
        showSurface(Surface.RITUAL, animate = false)
    }

    private fun setupNavigation() {
        binding.navRitualButton.setOnClickListener { showSurface(Surface.RITUAL) }
        binding.navLibraryButton.setOnClickListener { showSurface(Surface.LIBRARY) }
        binding.navVaultButton.setOnClickListener { showSurface(Surface.VAULT) }
    }

    private fun setupLists() {
        bookShelfAdapter = BookShelfAdapter { entry ->
            selectedBookId = entry.id
            refreshLibraryPanel(ContextCompat.getColor(this, currentState.accentRes))
            updatePrimaryButtonLabel()
        }
        binding.bookRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = bookShelfAdapter
            setHasFixedSize(true)
        }

        vaultDeckAdapter = VaultDeckAdapter()
        binding.vaultDeckRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = vaultDeckAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupActions() {
        binding.advanceStateButton.setOnClickListener { handlePrimaryAction() }
        binding.advanceStateButton.setOnLongClickListener { shareCurrentOmen() }

        binding.tomeImage.setOnClickListener {
            advanceRitual()
        }
        binding.tomeImage.setOnLongClickListener { shareCurrentOmen() }
    }

    private fun handlePrimaryAction() {
        when (activeSurface) {
            Surface.RITUAL -> advanceRitual()
            Surface.LIBRARY -> focusSelectedTome()
            Surface.VAULT -> shareCurrentOmen()
        }
    }

    private fun advanceRitual() {
        val nextState = currentState.next()
        renderState(
            state = nextState,
            animate = true,
            snapshot = libraryEngine.advanceTo(nextState)
        )
    }

    private fun focusSelectedTome() {
        renderState(
            state = currentState,
            animate = true,
            snapshot = libraryEngine.focusBook(selectedBookId, currentState)
        )
        showSurface(Surface.RITUAL)
    }

    private fun renderState(state: TomeState, animate: Boolean, snapshot: RitualSnapshot) {
        val previousBackground = currentBackgroundColor
        val nextBackground = ContextCompat.getColor(this, state.backgroundRes)
        currentState = state
        currentBackgroundColor = nextBackground
        currentSnapshot = snapshot
        selectedBookId = libraryEngine.currentBook().id

        binding.stateBadge.text = getString(state.titleRes)
        binding.libraryStatus.text = snapshot.librarySubtitle
        binding.stateOverline.text = snapshot.stateOverline
        binding.stateTitle.text = getString(state.titleRes)
        binding.stateSubtitle.text = getString(state.subtitleRes)
        binding.ritualHint.text = snapshot.ritualHint
        binding.imageCaption.text = snapshot.imageCaption
        binding.tomeImage.setImageResource(state.imageRes)

        val accentColor = ContextCompat.getColor(this, state.accentRes)
        binding.stateBadge.chipBackgroundColor = ContextCompat.getColorStateList(this, state.accentRes)
        binding.advanceStateButton.backgroundTintList = ColorStateList.valueOf(accentColor)
        binding.topGlow.backgroundTintList = ColorStateList.valueOf(accentColor)
        binding.bottomGlow.backgroundTintList = ColorStateList.valueOf(accentColor)
        binding.edgeAura.backgroundTintList = ColorStateList.valueOf(accentColor)
        binding.ghostFlash.backgroundTintList = ColorStateList.valueOf(accentColor)
        binding.stateCard.strokeColor = accentColor
        binding.imageCard.strokeColor = accentColor
        binding.selectedTomeCard.strokeColor = accentColor
        binding.vaultDeckCard.strokeColor = accentColor
        binding.vaultEconomyCard.strokeColor = accentColor
        binding.vaultArchivistCard.strokeColor = accentColor
        updateIndicators(state, accentColor)
        applyNavigationButtonStyles(accentColor)
        applySurfaceChrome()
        refreshLibraryPanel(accentColor)
        refreshVaultPanel(accentColor)
        updatePrimaryButtonLabel()

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

    private fun refreshLibraryPanel(accentColor: Int) {
        val selectedBook = libraryEngine.bookById(selectedBookId)
        binding.selectedTomeTitle.text = "${selectedBook.tomeCode} • ${selectedBook.title}"
        binding.selectedTomeMeta.text = "${selectedBook.arcName} • ${selectedBook.sceneSignature}"
        binding.selectedTomeHint.text = buildString {
            appendLine("Seed ${selectedBook.canonAnchor.chapterSeed}: ${selectedBook.canonAnchor.omen}")
            appendLine("Friendship: ${selectedBook.friendshipThread}")
            append("Enemy: ${selectedBook.enemyThread}")
        }

        bookShelfAdapter.submit(
            items = libraryEngine.shelfEntries(),
            selectedBookId = selectedBookId,
            accentColor = accentColor
        )
    }

    private fun refreshVaultPanel(accentColor: Int) {
        val snapshot = libraryEngine.vaultSnapshot()
        binding.vaultInkValue.text = "${snapshot.aetherInk} Aether Ink"
        binding.vaultTierCounts.text = snapshot.tierCounts.joinToString("   •   ") {
            "${it.tier.label}: ${it.count}"
        }
        binding.vaultFusionGuide.text = snapshot.fusionGuide
        binding.vaultArchivistNote.text = snapshot.archivistNote
        vaultDeckAdapter.submit(snapshot.activeDeck, accentColor)
    }

    private fun showSurface(surface: Surface, animate: Boolean = true) {
        activeSurface = surface
        val accentColor = ContextCompat.getColor(this, currentState.accentRes)
        applyNavigationButtonStyles(accentColor)
        applySurfaceChrome()
        updatePrimaryButtonLabel()

        val panelMap = mapOf(
            Surface.RITUAL to binding.ritualPanel,
            Surface.LIBRARY to binding.libraryPanel,
            Surface.VAULT to binding.vaultPanel
        )

        panelMap.forEach { (targetSurface, panel) ->
            val shouldShow = targetSurface == surface
            if (animate) {
                animatePanel(panel, shouldShow)
            } else {
                panel.alpha = if (shouldShow) 1f else 0f
                panel.isVisible = shouldShow
            }
        }
    }

    private fun applySurfaceChrome() {
        when (activeSurface) {
            Surface.RITUAL -> {
                binding.kicker.isVisible = !compactChrome
                binding.kicker.text = getString(R.string.surface_ritual_kicker)
                binding.libraryHeader.text = getString(R.string.surface_ritual_title)
                binding.libraryHeader.setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    if (compactChrome) 26f else 30f
                )
                binding.librarySubtitle.text = getString(R.string.surface_ritual_subtitle)
                binding.librarySubtitle.setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    if (compactChrome) 13f else 15f
                )
                binding.librarySubtitle.maxLines = if (compactChrome) 1 else 2
                binding.stateRow.isVisible = true
                binding.topGlow.alpha = 0.28f
                binding.bottomGlow.alpha = 0.18f
            }

            Surface.LIBRARY -> {
                binding.kicker.isVisible = true
                binding.kicker.text = getString(R.string.surface_library_kicker)
                binding.libraryHeader.text = getString(R.string.surface_library_title)
                binding.libraryHeader.setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    if (compactChrome) 22f else 24f
                )
                binding.librarySubtitle.text = getString(R.string.surface_library_subtitle)
                binding.librarySubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                binding.librarySubtitle.maxLines = 2
                binding.stateRow.isVisible = false
                binding.topGlow.alpha = 0.18f
                binding.bottomGlow.alpha = 0.12f
            }

            Surface.VAULT -> {
                binding.kicker.isVisible = true
                binding.kicker.text = getString(R.string.surface_vault_kicker)
                binding.libraryHeader.text = getString(R.string.surface_vault_title)
                binding.libraryHeader.setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    if (compactChrome) 22f else 24f
                )
                binding.librarySubtitle.text = getString(R.string.surface_vault_subtitle)
                binding.librarySubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                binding.librarySubtitle.maxLines = 2
                binding.stateRow.isVisible = false
                binding.topGlow.alpha = 0.16f
                binding.bottomGlow.alpha = 0.1f
            }
        }
    }

    private fun animatePanel(panel: View, show: Boolean) {
        if (show) {
            panel.isVisible = true
            panel.alpha = 0f
            panel.translationY = 18f
            panel.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else if (panel.isVisible) {
            panel.animate()
                .alpha(0f)
                .translationY(12f)
                .setDuration(160L)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    panel.isVisible = false
                    panel.translationY = 0f
                }
                .start()
        }
    }

    private fun updatePrimaryButtonLabel() {
        binding.advanceStateButton.text = when (activeSurface) {
            Surface.RITUAL -> currentSnapshot.buttonLabel
            Surface.LIBRARY -> if (selectedBookId == libraryEngine.currentBook().id) {
                getString(R.string.button_return_to_rite)
            } else {
                getString(R.string.button_focus_selected_tome)
            }
            Surface.VAULT -> getString(R.string.button_share_active_omen)
        }
    }

    private fun applyNavigationButtonStyles(accentColor: Int) {
        styleNavButton(binding.navRitualButton, activeSurface == Surface.RITUAL, accentColor)
        styleNavButton(binding.navLibraryButton, activeSurface == Surface.LIBRARY, accentColor)
        styleNavButton(binding.navVaultButton, activeSurface == Surface.VAULT, accentColor)
    }

    private fun styleNavButton(button: MaterialButton, isSelected: Boolean, accentColor: Int) {
        val backgroundColor = if (isSelected) accentColor else ContextCompat.getColor(this, R.color.nav_surface)
        val strokeColor = if (isSelected) accentColor else ContextCompat.getColor(this, R.color.card_stroke)
        val textColor = if (isSelected) {
            ContextCompat.getColor(this, R.color.nav_selected_text)
        } else {
            ContextCompat.getColor(this, R.color.nav_unselected_text)
        }

        button.backgroundTintList = ColorStateList.valueOf(backgroundColor)
        button.strokeColor = ColorStateList.valueOf(strokeColor)
        button.setTextColor(textColor)
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
