package com.xaviers.library

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.view.animation.AccelerateInterpolator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.xaviers.library.databinding.ActivityMainBinding
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private enum class Surface {
        RITUAL,
        LIBRARY,
        VAULT
    }

    private lateinit var binding: ActivityMainBinding
    private val libraryEngine = LibraryEngine()
    private lateinit var playbackCoordinator: PlaybackRuntimeCoordinator
    private lateinit var sceneImageEngine: SceneImageEngine
    private lateinit var bookShelfAdapter: BookShelfAdapter
    private lateinit var vaultDeckAdapter: VaultDeckAdapter
    private lateinit var forYouAdapter: HomeStoryAdapter
    private lateinit var trendingAdapter: HomeStoryAdapter
    private lateinit var freshAdapter: HomeStoryAdapter
    private var currentState = TomeState.DORMANT
    private var currentBackgroundColor = 0
    private lateinit var currentSnapshot: RitualSnapshot
    private lateinit var currentContinuumSnapshot: StoryContinuumSnapshot
    private var playbackState = PlaybackVisualState()
    private var lastStoryboardKey = ""
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

    private val featuredAudioBars by lazy {
        listOf(
            binding.featuredBarOne,
            binding.featuredBarTwo,
            binding.featuredBarThree,
            binding.featuredBarFour,
            binding.featuredBarFive
        )
    }

    private val playerAudioBars by lazy {
        listOf(
            binding.playerBarOne,
            binding.playerBarTwo,
            binding.playerBarThree,
            binding.playerBarFour,
            binding.playerBarFive
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val voicePlaybackManager = VoicePlaybackManager(
            context = this,
            onVisualState = { state ->
                runOnUiThread {
                    playbackState = state
                    renderPlaybackState(state)
                }
            },
            onChapterFinished = { payload ->
                runOnUiThread {
                    handleChapterFinished(payload)
                }
            }
        )
        val storyBrainManager = StoryBrainManager(
            context = this,
            onPendingPayload = { payload ->
                runOnUiThread {
                    if (!isPayloadStillCurrent(payload)) return@runOnUiThread
                    voicePlaybackManager.seed(payload)
                }
            },
            onResolvedPayload = { payload ->
                runOnUiThread {
                    if (!isPayloadStillCurrent(payload)) return@runOnUiThread
                    voicePlaybackManager.start(payload)
                }
            },
            isPayloadStillCurrent = ::isPayloadStillCurrent
        )
        playbackCoordinator = PlaybackRuntimeCoordinator(
            storyBrainManager = storyBrainManager,
            voicePlaybackManager = voicePlaybackManager
        )
        sceneImageEngine = SceneImageEngine(this)

        selectedBookId = libraryEngine.currentBook().id
        setupNavigation()
        setupLists()
        setupActions()
        applyCompactPhoneChrome()
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
        val railClick: (HomeRailItem) -> Unit = { item ->
            selectedBookId = item.id
            renderState(
                state = currentState,
                animate = true,
                snapshot = libraryEngine.focusBook(item.id, currentState)
            )
            showSurface(Surface.RITUAL)
        }

        forYouAdapter = HomeStoryAdapter(railClick)
        trendingAdapter = HomeStoryAdapter(railClick)
        freshAdapter = HomeStoryAdapter(railClick)

        binding.forYouRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = forYouAdapter
            isNestedScrollingEnabled = false
        }
        binding.trendingRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = trendingAdapter
            isNestedScrollingEnabled = false
        }
        binding.freshRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = freshAdapter
            isNestedScrollingEnabled = false
        }

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
        binding.continueCard.setOnClickListener { toggleCurrentPlayback() }
        binding.continueCard.setOnLongClickListener {
            startActivity(Intent(this, HuggingFaceStoryBrainSettingsActivity::class.java))
            true
        }
        binding.playerCard.setOnClickListener { showCurrentStoryPopup() }
        binding.playerActionButton.setOnClickListener { toggleCurrentPlayback() }
        binding.playerActionButton.setOnLongClickListener {
            startActivity(Intent(this, CosyVoiceSettingsActivity::class.java))
            true
        }

        binding.imageCard.setOnClickListener { showCurrentStoryPopup() }
        binding.imageCard.setOnLongClickListener {
            startActivity(Intent(this, VisualBackendSettingsActivity::class.java))
            true
        }
        binding.tomeImage.setOnClickListener {
            toggleCurrentPlayback()
        }
        binding.tomeImage.setOnLongClickListener {
            startActivity(Intent(this, VisualBackendSettingsActivity::class.java))
            true
        }
        binding.visualDirectorCard.setOnLongClickListener {
            startActivity(Intent(this, PromptPackSettingsActivity::class.java))
            true
        }
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
        startCurrentPlayback()
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
        lastStoryboardKey = ""
        sceneImageEngine.cancelPending()
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
        binding.continueCard.strokeColor = accentColor
        binding.playerCard.strokeColor = accentColor
        updateIndicators(state, accentColor)
        applyNavigationButtonStyles(accentColor)
        applySurfaceChrome()
        refreshHomePanel(accentColor)
        refreshLibraryPanel(accentColor)
        refreshVaultPanel(accentColor)
        refreshContinuumPanels(accentColor)
        updatePrimaryButtonLabel()
        syncAudioVisualSelection()

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

    private fun refreshHomePanel(accentColor: Int) {
        val home = libraryEngine.homeSnapshot(currentState)
        val continuum = libraryEngine.storyContinuumSnapshot(currentState)
        currentContinuumSnapshot = continuum

        binding.featuredGenre.text = home.heroGenre
        binding.featuredGenre.backgroundTintList = ColorStateList.valueOf(accentColor)
        binding.continueLabel.text = home.continueLabel
            .substringBefore("•")
            .trim()
            .ifBlank { home.continueLabel }
        binding.continueMeta.text = home.continueMeta
        binding.continueProgress.progressTintList = ColorStateList.valueOf(accentColor)
        binding.continueProgress.progress = home.continueProgress
        binding.continueTimeLabel.text = "${home.continueProgress}% complete • ${home.continueTimeLabel}"

        binding.stateOverline.text = continuum.chapter.overline
        binding.stateTitle.text = continuum.chapter.title
        binding.stateSubtitle.text = continuum.chapter.summary
        binding.ritualHint.text = buildString {
            appendLine("Canon: ${continuum.chapter.canonEcho}")
            appendLine("Chapter counter: ${continuum.chapter.overline}")
            append(continuum.continuityDigest)
        }

        binding.genreChipRow.removeAllViews()
        home.featuredGenres.forEachIndexed { index, genre ->
            binding.genreChipRow.addView(createGenreChip(genre, accentColor, index))
        }

        forYouAdapter.submit(home.forYou, accentColor)
        trendingAdapter.submit(home.trending, accentColor)
        freshAdapter.submit(home.freshDrops, accentColor)
        renderIdleArtwork()
    }

    private fun refreshLibraryPanel(accentColor: Int) {
        val selectedBook = libraryEngine.bookById(selectedBookId)
        binding.selectedTomeTitle.text = "${selectedBook.tomeCode} • ${selectedBook.title}"
        binding.selectedTomeMeta.text = "${selectedBook.genre} • ${selectedBook.arcName} • ${selectedBook.sceneSignature}"
        binding.selectedTomeHint.text = buildString {
            appendLine("Seed ${selectedBook.canonAnchor.chapterSeed}: ${selectedBook.canonAnchor.omen}")
            appendLine("Friendship: ${selectedBook.friendshipThread}")
            if (selectedBook.id == libraryEngine.currentBook().id) {
                appendLine("Enemy: ${selectedBook.enemyThread}")
                append("Current continuum: ${currentContinuumSnapshot.chapter.title}")
            } else {
                append("Enemy: ${selectedBook.enemyThread}")
            }
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

    private fun refreshContinuumPanels(accentColor: Int) {
        val continuum = libraryEngine.storyContinuumSnapshot(currentState)
        currentContinuumSnapshot = continuum

        binding.visualDirectorCard.strokeColor = accentColor
        binding.worldFirstCard.strokeColor = accentColor
        binding.visualDirectorTitle.text = "${continuum.chapter.title} • ${continuum.visualDirectorTitle}"
        binding.visualDirectorPrompt.text = continuum.chapter.visualBeats.joinToString("\n") { beat ->
            "${beat.label}: ${beat.sceneTitle} • ${beat.focusCharacter} • ${beat.sceneSetting}"
        }
        binding.visualDirectorStatus.text = continuum.visualDirectorStatus

        binding.worldFirstTitle.text = "${continuum.totalFeatureCount} world-first features"
        binding.worldFirstSummary.text = continuum.featureDigest
        binding.worldFirstHighlights.text = continuum.highlightedFeatures.joinToString("\n") { feature ->
            "${feature.index}. ${feature.title} — ${feature.detail}"
        }
    }

    private fun syncAudioVisualSelection() {
        playbackCoordinator.seed(currentPlaybackPayload())
    }

    private fun toggleCurrentPlayback() {
        if (playbackState.isPlaying) {
            playbackCoordinator.stop()
            return
        }
        startCurrentPlayback()
    }

    private fun startCurrentPlayback() {
        playbackCoordinator.start(currentPlaybackPayload())
    }

    private fun currentPlaybackPayload(): PlaybackPayload {
        val book = libraryEngine.currentBook()
        val chapter = currentContinuumSnapshot.chapter
        val interludeSeconds = chapter.transitionPauseMs / 1000.0
        return PlaybackPayload(
            id = ((book.id + 1) * 10_000) + chapter.chapterNumber,
            bookId = book.id,
            title = book.title,
            subtitle = chapter.title,
            text = dramaScriptText(chapter),
            storyState = chapter.continuityLedger.joinToString("\n"),
            idleStatus = "CosyVoice ready • next chapter opens after ${"%.1f".format(interludeSeconds)}s",
            chapterNumber = chapter.chapterNumber,
            chapterLabel = chapter.overline,
            transitionPauseMs = chapter.transitionPauseMs,
            visualBeats = chapter.visualBeats,
            voiceLines = chapter.voiceLines,
            voiceEngineLabel = "CosyVoice cast",
            visualEngineLabel = "Scene flow",
            storyGenre = book.genre,
            storyArc = book.arcName,
            sceneSignature = book.sceneSignature,
            visualEraOverride = book.visualEraOverride,
            isContinuous = true,
            continuationHint = "Chapter ${chapter.chapterNumber} resting softly • next chapter opens after ${"%.1f".format(interludeSeconds)}s"
        )
    }

    private fun isPayloadStillCurrent(payload: PlaybackPayload): Boolean {
        if (isFinishing || isDestroyed || !::currentContinuumSnapshot.isInitialized) return false
        val currentBook = libraryEngine.currentBook()
        val currentChapter = currentContinuumSnapshot.chapter
        return currentBook.id == payload.bookId && currentChapter.chapterNumber == payload.chapterNumber
    }

    private fun dramaScriptText(chapter: StoryChapter): String {
        if (chapter.voiceLines.isEmpty()) return chapter.narration
        return chapter.voiceLines.joinToString("\n") { line ->
            "${line.speaker}: ${line.text}"
        }
    }

    private fun renderPlaybackState(state: PlaybackVisualState) {
        val accentColor = ContextCompat.getColor(this, currentState.accentRes)
        val averageLevel = state.levels.average().toFloat()
        binding.playerTitle.text = state.title.ifBlank { libraryEngine.currentBook().title }
        binding.playerTitle.isVisible = false
        binding.playerTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compactChrome) 14f else 15f)
        binding.playerChapterCounter.text = state.chapterLabel.ifBlank {
            "Chapter ${currentContinuumSnapshot.chapter.chapterNumber}"
        }
        binding.playerChapterCounter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
        binding.playerChapterCounter.isVisible = false
        binding.playerMeta.isVisible = false
        binding.playerStatus.isVisible = false
        binding.playerVisualPrompt.isVisible = false
        binding.playerProgress.progressTintList = ColorStateList.valueOf(accentColor)
        binding.playerProgress.progress = state.progress
        binding.playerProgress.isVisible = false
        binding.playerActionButton.backgroundTintList = ColorStateList.valueOf(accentColor)
        binding.playerActionButton.setTextColor(ContextCompat.getColor(this, R.color.nav_selected_text))
        binding.playerActionButton.text = if (state.isPlaying) {
            getString(R.string.player_action_stop)
        } else {
            getString(R.string.player_action_play)
        }

        binding.continueTimeLabel.text = state.status
        binding.continueCard.alpha = if (state.isPlaying) 1f else 0.97f
        binding.visualDirectorCard.alpha = if (state.isPlaying) 1f else 0.98f
        binding.playerCard.isVisible = state.isPlaying
        binding.playerCard.alpha = if (state.isPlaying) 0.96f else 0f
        binding.edgeAura.alpha = if (state.isPlaying) 0.08f + (averageLevel * 0.05f) else 0.06f

        renderStoryboardFrame(state)
        updateVisualizer(featuredAudioBars, state.levels, accentColor, state.isPlaying)
        updateVisualizer(playerAudioBars, state.levels, accentColor, state.isPlaying)
    }

    private fun renderStoryboardFrame(state: PlaybackVisualState) {
        if (!state.isPlaying || state.visualBeatLabel.isBlank()) {
            if (lastStoryboardKey.isNotBlank()) {
                renderIdleArtwork()
            }
            lastStoryboardKey = ""
            binding.imageCaption.text = "Tap the artwork for story details"
            return
        }

        val storyboardKey = listOf(
            state.visualBeatLabel,
            state.sceneTitle,
            state.sceneSetting,
            state.focusCharacter,
            state.supportingCharacter,
            state.frameVariant.toString()
        ).joinToString("|")
        if (storyboardKey != lastStoryboardKey) {
            lastStoryboardKey = storyboardKey
            renderSceneArtwork(state)
        }

        binding.imageCaption.text = "Tap the artwork for story details"
    }

    private fun renderSceneArtwork(state: PlaybackVisualState) {
        val width = binding.tomeImage.width
            .takeIf { it > 0 }
            ?: binding.imageCard.width.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels
        val height = binding.tomeImage.height
            .takeIf { it > 0 }
            ?: (width * 1.36f).roundToInt()

        sceneImageEngine.render(
            state = state,
            tomeState = currentState,
            width = width,
            height = height,
            onFallbackBitmap = { fallbackBitmap ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    displaySceneBitmap(fallbackBitmap)
                }
            },
            onCaptionUpdate = { caption ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    binding.imageCaption.text = caption
                }
            },
            onGeneratedBitmap = { generatedBitmap ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    displaySceneBitmap(generatedBitmap)
                }
            }
        )
    }

    private fun displaySceneBitmap(sceneBitmap: Bitmap) {
        binding.tomeImage.animate().cancel()
        if (!binding.tomeImage.isShown) {
            binding.tomeImage.alpha = 1f
            binding.tomeImage.scaleX = 1f
            binding.tomeImage.scaleY = 1f
            binding.tomeImage.setImageBitmap(sceneBitmap)
            return
        }
        binding.tomeImage.animate()
            .alpha(0f)
            .setDuration(120L)
            .withEndAction {
                binding.tomeImage.setImageBitmap(sceneBitmap)
                binding.tomeImage.scaleX = 1.05f
                binding.tomeImage.scaleY = 1.05f
                binding.tomeImage.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(320L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }

    private fun renderIdleArtwork() {
        if (!::currentContinuumSnapshot.isInitialized) return
        val chapter = currentContinuumSnapshot.chapter
        val idleBeat = chapter.visualBeats.firstOrNull()
        val idleState = PlaybackVisualState(
            title = chapter.title.ifBlank { libraryEngine.currentBook().title },
            subtitle = chapter.overline,
            chapterLabel = chapter.overline,
            status = chapter.summary,
            visualBeatLabel = idleBeat?.label ?: "Idle",
            sceneTitle = idleBeat?.sceneTitle ?: chapter.title,
            sceneSetting = idleBeat?.sceneSetting ?: libraryEngine.currentBook().sceneSignature,
            focusCharacter = idleBeat?.focusCharacter ?: "The lead vampire",
            supportingCharacter = idleBeat?.supportingCharacter ?: "The shadow court",
            shortCaption = currentSnapshot.imageCaption,
            visualPrompt = chapter.narration,
            storyGenre = libraryEngine.currentBook().genre,
            storyArc = libraryEngine.currentBook().arcName,
            sceneSignature = libraryEngine.currentBook().sceneSignature,
            visualEraOverride = libraryEngine.currentBook().visualEraOverride,
            frameVariant = chapter.chapterNumber % 4,
            engineLine = "Anime RPG story art",
            progress = 0,
            levels = List(5) { 0.02f },
            isPlaying = false,
            isReady = true
        )
        renderSceneArtwork(idleState)
    }

    private fun showCurrentStoryPopup() {
        val chapter = currentContinuumSnapshot.chapter
        val book = libraryEngine.currentBook()
        val title = chapter.title.ifBlank { book.title }
        val details = buildString {
            appendLine(chapter.overline)
            appendLine(chapter.summary)
            appendLine()
            append("Canon: ${chapter.canonEcho}")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(details)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun applyCompactPhoneChrome() {
        if (!compactChrome) return

        binding.kicker.isVisible = false
        binding.libraryHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        binding.librarySubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        binding.librarySubtitle.maxLines = 1
        binding.librarySubtitle.ellipsize = TextUtils.TruncateAt.END

        binding.playerTitle.maxLines = 1
        binding.playerTitle.ellipsize = TextUtils.TruncateAt.END
        binding.playerTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        binding.playerChapterCounter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
        binding.playerChapterCounter.isVisible = false
        binding.playerMeta.isVisible = false
        binding.playerStatus.isVisible = false
        binding.playerVisualPrompt.isVisible = false

        binding.playerTitle.maxLines = 1
        binding.playerTitle.ellipsize = TextUtils.TruncateAt.END
        binding.playerProgress.isVisible = false
        binding.playerBarOne.isVisible = false
        binding.playerBarTwo.isVisible = false
        binding.playerBarThree.isVisible = false
        binding.playerBarFour.isVisible = false
        binding.playerBarFive.isVisible = false
    }

    private fun handleChapterFinished(payload: PlaybackPayload) {
        if (payload.bookId != libraryEngine.currentBook().id) return

        val nextContinuum = libraryEngine.advanceContinuum(currentState)
        renderState(
            state = currentState,
            animate = true,
            snapshot = libraryEngine.snapshotFor(currentState)
        )
        Snackbar.make(
            binding.root,
            "Chapter ${nextContinuum.chapter.chapterNumber} opened • ${nextContinuum.chapter.title}",
            Snackbar.LENGTH_SHORT
        ).show()
        startCurrentPlayback()
    }

    private fun updateVisualizer(
        bars: List<View>,
        levels: List<Float>,
        accentColor: Int,
        isPlaying: Boolean
    ) {
        bars.forEachIndexed { index, view ->
            val level = levels.getOrElse(index) { 0.2f }
            view.backgroundTintList = ColorStateList.valueOf(accentColor)
            view.pivotY = view.height.toFloat()
            view.scaleY = if (isPlaying) {
                0.55f + (level * 1.25f)
            } else {
                0.56f + (index * 0.06f)
            }
            view.alpha = if (isPlaying) 0.96f else 0.44f + (index * 0.08f)
        }
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
                    if (compactChrome) 24f else 30f
                )
                binding.librarySubtitle.text = getString(R.string.surface_ritual_subtitle)
                binding.librarySubtitle.setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    if (compactChrome) 13f else 15f
                )
                binding.librarySubtitle.maxLines = if (compactChrome) 1 else 2
                binding.stateRow.isVisible = true
                binding.topGlow.alpha = if (compactChrome) 0.03f else 0.05f
                binding.bottomGlow.alpha = if (compactChrome) 0.02f else 0.03f
            }

            Surface.LIBRARY -> {
                binding.kicker.isVisible = true
                binding.kicker.text = getString(R.string.surface_library_kicker)
                binding.libraryHeader.text = getString(R.string.surface_library_title)
                binding.libraryHeader.setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    if (compactChrome) 21f else 24f
                )
                binding.librarySubtitle.text = getString(R.string.surface_library_subtitle)
                binding.librarySubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                binding.librarySubtitle.maxLines = 1
                binding.stateRow.isVisible = false
                binding.topGlow.alpha = if (compactChrome) 0.025f else 0.04f
                binding.bottomGlow.alpha = if (compactChrome) 0.015f else 0.025f
            }

            Surface.VAULT -> {
                binding.kicker.isVisible = true
                binding.kicker.text = getString(R.string.surface_vault_kicker)
                binding.libraryHeader.text = getString(R.string.surface_vault_title)
                binding.libraryHeader.setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    if (compactChrome) 21f else 24f
                )
                binding.librarySubtitle.text = getString(R.string.surface_vault_subtitle)
                binding.librarySubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                binding.librarySubtitle.maxLines = 1
                binding.stateRow.isVisible = false
                binding.topGlow.alpha = if (compactChrome) 0.02f else 0.03f
                binding.bottomGlow.alpha = if (compactChrome) 0.01f else 0.02f
            }
        }
    }

    private fun createGenreChip(label: String, accentColor: Int, index: Int): Chip {
        val isFeatured = index == 0
        return Chip(this).apply {
            text = label
            isCheckable = false
            isClickable = false
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (isFeatured) R.color.nav_selected_text else R.color.nav_unselected_text
                )
            )
            chipBackgroundColor = ColorStateList.valueOf(
                if (isFeatured) accentColor else ContextCompat.getColor(this@MainActivity, R.color.card_surface_soft)
            )
            chipStrokeColor = ColorStateList.valueOf(accentColor)
            chipStrokeWidth = 1f
            shapeAppearanceModel = shapeAppearanceModel
                .toBuilder()
                .setAllCornerSizes(22f)
                .build()
            setEnsureMinTouchTargetSize(false)
            textSize = 12f
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    12f,
                    resources.displayMetrics
                ).toInt()
            }
            layoutParams = params
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
        pulseView(binding.topGlow, minAlpha = 0.02f, maxAlpha = 0.06f, duration = 4200L)
        pulseView(binding.bottomGlow, minAlpha = 0.01f, maxAlpha = 0.04f, duration = 4800L)
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

    override fun onStop() {
        playbackCoordinator.stop()
        sceneImageEngine.cancelPending()
        super.onStop()
    }

    override fun onDestroy() {
        playbackCoordinator.release()
        sceneImageEngine.cancelPending()
        super.onDestroy()
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
