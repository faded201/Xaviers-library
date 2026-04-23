package com.xaviers.library

object VisualPromptLock {

    fun build(
        state: PlaybackVisualState,
        visualEngine: VisualEngineConfig,
        visualDictionary: VisualDictionaryConfig? = null,
        extraStyle: String = ""
    ): String {
        val sceneDescription = sceneDescription(state, visualEngine.defaultScene)
        val styleLine = listOf(
            visualEngine.styleLock,
            visualDictionary?.globalStyleProfile?.artStyle?.takeIf { it.isNotBlank() },
            visualDictionary?.globalStyleProfile?.lightingRules?.takeIf { it.isNotBlank() },
            extraStyle.trim().takeIf { it.isNotBlank() }
        ).filterNotNull().joinToString(", ")

        val characterReferences = characterReferences(state, visualDictionary)
        val locationReference = matchingLocation(state, visualDictionary)
        val sceneReference = matchingSceneReference(state, visualDictionary)
        val consistencyRules = visualDictionary?.consistencyRules.orEmpty()

        return """
            STYLE LOCK:
            $styleLine

            WORLD RULES:
            ${listOf(
                visualEngine.worldRules,
                locationReference?.visualDesign?.takeIf { it.isNotBlank() },
                visualDictionary?.globalStyleProfile?.renderingQuality?.takeIf { it.isNotBlank() }
            ).filterNotNull().joinToString(". ")}

            CHARACTER LOCK:
            ${listOf(visualEngine.characterLock).plus(characterReferences).joinToString("\n")}

            CAMERA:
            ${listOf(
                visualEngine.camera,
                visualDictionary?.globalStyleProfile?.cameraStyle?.takeIf { it.isNotBlank() }
            ).filterNotNull().joinToString(", ")}

            SCENE:
            $sceneDescription

            LOCATION REFERENCE:
            ${locationReference?.masterImagePrompt ?: "No matched location sheet. Keep architecture and palette consistent with the world rules."}

            SCENE REFERENCE:
            ${sceneReference?.imagePrompt ?: "No exact scene sheet matched. Keep the scene concrete and readable."}

            MOOD:
            ${listOf(
                visualEngine.mood,
                locationReference?.mood?.takeIf { it.isNotBlank() },
                sceneReference?.styleConsistencyReference?.takeIf { it.isNotBlank() }
            ).filterNotNull().joinToString(", ")}

            CONSISTENCY RULES:
            ${consistencyRules.takeIf { it.isNotEmpty() }?.joinToString("\n") { "- $it" } ?: "- Keep character appearance, outfit, and location design stable unless the story explicitly changes them."}
        """.trimIndent()
    }

    private fun sceneDescription(state: PlaybackVisualState, defaultScene: String): String {
        val sceneParts = listOf(
            state.sceneTitle.takeIf { it.isNotBlank() },
            state.sceneSetting.takeIf { it.isNotBlank() },
            state.shortCaption.takeIf { it.isNotBlank() },
            state.visualPrompt.takeIf { it.isNotBlank() },
            dynamicSubjectDetails(state).takeIf { it.isNotBlank() }
        )
            .filterNotNull()
            .joinToString(". ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return sceneParts.ifBlank { defaultScene }
    }

    private fun characterReferences(
        state: PlaybackVisualState,
        visualDictionary: VisualDictionaryConfig?
    ): List<String> {
        val dictionary = visualDictionary ?: return emptyList()
        val namesToMatch = listOf(state.focusCharacter, state.supportingCharacter)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return dictionary.characterSheets
            .filter { sheet ->
                namesToMatch.any { candidate ->
                    candidate.contains(sheet.name, ignoreCase = true) ||
                        sheet.name.contains(candidate, ignoreCase = true)
                }
            }
            .map { sheet -> "${sheet.name}: ${sheet.masterImagePrompt}" }
    }

    private fun matchingLocation(
        state: PlaybackVisualState,
        visualDictionary: VisualDictionaryConfig?
    ): VisualLocationConfig? {
        val dictionary = visualDictionary ?: return null
        val sceneText = listOf(state.sceneSetting, state.sceneTitle, state.sceneSignature)
            .joinToString(" ")
            .trim()
        return dictionary.locationDictionary.firstOrNull { location ->
            sceneText.contains(location.name, ignoreCase = true)
        }
    }

    private fun matchingSceneReference(
        state: PlaybackVisualState,
        visualDictionary: VisualDictionaryConfig?
    ): VisualScenePromptConfig? {
        val dictionary = visualDictionary ?: return null
        return dictionary.sceneImagePrompts.firstOrNull { scenePrompt ->
            scenePrompt.sceneId.equals(state.visualBeatLabel, ignoreCase = true)
        }
    }

    private fun dynamicSubjectDetails(state: PlaybackVisualState): String {
        val text = listOf(
            state.sceneTitle,
            state.sceneSetting,
            state.shortCaption,
            state.visualPrompt,
            state.focusCharacter,
            state.supportingCharacter
        ).joinToString(" ").lowercase()

        val details = mutableListOf<String>()
        if (listOf("family", "mother", "father", "mom", "dad", "son", "daughter", "girl", "boy", "child", "children").any(text::contains)) {
            details += "a grounded family grouping staged naturally in the frame"
        }
        if (listOf("cat", "kitten", "feline").any(text::contains)) {
            details += "a cat moving through the mist"
        }
        if (listOf("dog", "puppy", "canine").any(text::contains)) {
            details += "a dog framed in the foreground"
        }
        if ("bus" in text) {
            details += "a ruined bus translated into the same lost-tech architecture language"
        }
        if ("truck" in text || "lorry" in text) {
            details += "a heavy transport truck with worn lost-tech detailing"
        }
        if ("taxi" in text || "cab" in text) {
            details += "a taxi designed in the same ancient-tech visual language"
        }
        if (listOf("car", "vehicle", "road").any(text::contains)) {
            details += "a vehicle integrated into the ruined world design"
        }
        return details.joinToString(", ")
    }
}
