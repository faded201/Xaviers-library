package com.xaviers.library

import android.content.Context

data class PromptPackFileStatus(
    val sourceLabel: String,
    val lastImportedFileName: String?,
    val validationMessage: String
)

data class PromptPackRuntimeStatus(
    val promptPack: PromptPackFileStatus,
    val visualDictionary: PromptPackFileStatus
)

class PromptPackRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val storage = PromptPackStorage(appContext)

    @Volatile
    private var loadedState: LoadedState = loadState()

    fun reload(): PromptPackRuntimeStatus {
        val refreshed = loadState()
        loadedState = refreshed
        return refreshed.status
    }

    fun status(): PromptPackRuntimeStatus {
        return loadedState.status
    }

    fun buildStoryPrompt(payload: PlaybackPayload, styleOverride: String): String {
        val snapshot = loadedState
        return XavierCorePromptSystem.buildStoryPrompt(
            payload = payload,
            styleOverride = styleOverride,
            promptPack = snapshot.promptPack,
            visualDictionary = snapshot.visualDictionary
        )
    }

    fun buildVisualPrompt(state: PlaybackVisualState, extraStyle: String = ""): String {
        val snapshot = loadedState
        return VisualPromptLock.build(
            state = state,
            extraStyle = extraStyle,
            visualEngine = snapshot.promptPack.visualEngine,
            visualDictionary = snapshot.visualDictionary
        )
    }

    private fun loadState(): LoadedState {
        val promptPackResult = loadPromptPack()
        val visualDictionaryResult = loadVisualDictionary()

        return LoadedState(
            promptPack = promptPackResult.config,
            visualDictionary = visualDictionaryResult.config,
            status = PromptPackRuntimeStatus(
                promptPack = promptPackResult.status,
                visualDictionary = visualDictionaryResult.status
            )
        )
    }

    private fun loadPromptPack(): LoadedFile<PromptPackConfig> {
        val importedFile = storage.fileFor(PromptPackAsset.PROMPT_PACK)
        val importedName = storage.lastImportedName(PromptPackAsset.PROMPT_PACK)
            ?: importedFile.takeIf { it.exists() }?.name
        return if (importedFile.exists()) {
            runCatching {
                PromptPackParser.parsePromptPack(importedFile.readText())
            }.fold(
                onSuccess = { parsed ->
                    LoadedFile(
                        config = parsed,
                        status = PromptPackFileStatus(
                            sourceLabel = "Imported app copy",
                            lastImportedFileName = importedName,
                            validationMessage = "Valid prompt pack loaded."
                        )
                    )
                },
                onFailure = { throwable ->
                    val fallback = loadBuiltInPromptPack()
                    LoadedFile(
                        config = fallback,
                        status = PromptPackFileStatus(
                            sourceLabel = "Built-in app asset",
                            lastImportedFileName = importedName,
                            validationMessage = "Imported prompt pack invalid. Using built-in default. ${throwable.message.orEmpty()}".trim()
                        )
                    )
                }
            )
        } else {
            LoadedFile(
                config = loadBuiltInPromptPack(),
                status = PromptPackFileStatus(
                    sourceLabel = "Built-in app asset",
                    lastImportedFileName = importedName,
                    validationMessage = "No imported prompt pack found. Using built-in default."
                )
            )
        }
    }

    private fun loadVisualDictionary(): LoadedFile<VisualDictionaryConfig> {
        val importedFile = storage.fileFor(PromptPackAsset.VISUAL_DICTIONARY)
        val importedName = storage.lastImportedName(PromptPackAsset.VISUAL_DICTIONARY)
            ?: importedFile.takeIf { it.exists() }?.name
        return if (importedFile.exists()) {
            runCatching {
                PromptPackParser.parseVisualDictionary(importedFile.readText())
            }.fold(
                onSuccess = { parsed ->
                    LoadedFile(
                        config = parsed,
                        status = PromptPackFileStatus(
                            sourceLabel = "Imported app copy",
                            lastImportedFileName = importedName,
                            validationMessage = "Valid visual dictionary loaded."
                        )
                    )
                },
                onFailure = { throwable ->
                    val fallback = loadBuiltInVisualDictionary()
                    LoadedFile(
                        config = fallback,
                        status = PromptPackFileStatus(
                            sourceLabel = "Built-in app asset",
                            lastImportedFileName = importedName,
                            validationMessage = "Imported visual dictionary invalid. Using built-in default. ${throwable.message.orEmpty()}".trim()
                        )
                    )
                }
            )
        } else {
            LoadedFile(
                config = loadBuiltInVisualDictionary(),
                status = PromptPackFileStatus(
                    sourceLabel = "Built-in app asset",
                    lastImportedFileName = importedName,
                    validationMessage = "No imported visual dictionary found. Using built-in default."
                )
            )
        }
    }

    private fun loadBuiltInPromptPack(): PromptPackConfig {
        val assetJson = appContext.assets.open(PromptPackAsset.PROMPT_PACK.assetFileName)
            .bufferedReader()
            .use { it.readText() }
        return PromptPackParser.parsePromptPack(assetJson)
    }

    private fun loadBuiltInVisualDictionary(): VisualDictionaryConfig {
        val assetJson = appContext.assets.open(PromptPackAsset.VISUAL_DICTIONARY.assetFileName)
            .bufferedReader()
            .use { it.readText() }
        return PromptPackParser.parseVisualDictionary(assetJson)
    }

    private data class LoadedState(
        val promptPack: PromptPackConfig,
        val visualDictionary: VisualDictionaryConfig,
        val status: PromptPackRuntimeStatus
    )

    private data class LoadedFile<T>(
        val config: T,
        val status: PromptPackFileStatus
    )

    companion object {
        @Volatile
        private var instance: PromptPackRepository? = null

        fun get(context: Context): PromptPackRepository {
            return instance ?: synchronized(this) {
                instance ?: PromptPackRepository(context).also { instance = it }
            }
        }
    }
}
