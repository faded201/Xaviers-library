package com.xaviers.library

import org.json.JSONArray
import org.json.JSONObject

object XavierCorePromptSystem {

    fun buildStoryPrompt(
        payload: PlaybackPayload,
        styleOverride: String,
        promptPack: PromptPackConfig,
        visualDictionary: VisualDictionaryConfig? = null
    ): String {
        val storyEngine = promptPack.storyEngine
        val continuity = promptPack.continuity
        val worldStateJson = continuity.worldState.toJson().toString(2)
        val worldBibleJson = continuity.worldBible.toJson().toString(2)
        val charactersJson = continuity.characters.toCharacterJsonArray().toString(2)
        val plotThreadsJson = mergedPlotThreads(payload, continuity.plotThreads).toPlotThreadJsonArray().toString(2)
        val beatsBlock = payload.visualBeats.take(4).joinToString("\n") { beat ->
            "- ${beat.label}: ${beat.sceneTitle} in ${beat.sceneSetting}; ${beat.focusCharacter} vs ${beat.supportingCharacter}"
        }
        val outputSchema = JSONObject().apply {
            put("subtitle", "short cinematic chapter subtitle")
            put("summary", "2 to 4 sentence next-chapter continuation summary")
            put("image_style", "one line visual direction that matches the same universe")
            put(
                "beats",
                JSONArray().apply {
                    put(JSONObject().put("label", "Opening tableau").put("prompt_boost", "..."))
                    put(JSONObject().put("label", "Pressure rise").put("prompt_boost", "..."))
                    put(JSONObject().put("label", "Twist fracture").put("prompt_boost", "..."))
                    put(JSONObject().put("label", "Cliffhanger breach").put("prompt_boost", "..."))
                }
            )
            put(
                "memory_tracking",
                JSONArray().put("5 bullet-style continuity anchors for future chapters")
            )
        }.toString(2)

        val writingStyleLine = storyEngine.styleDefaults.writingStyle
            .plus(styleOverride.trim().takeIf { it.isNotBlank() })
            .filterNotNull()
            .joinToString(", ")

        return """
            SYSTEM_ROLE:
            ${storyEngine.systemRole}

            PRIMARY GOALS:
            ${storyEngine.primaryGoals.toBulletList()}

            GLOBAL RULES:
            ${storyEngine.globalRules.toBulletList()}

            WORLD_DEFAULTS:
            ${storyEngine.worldDefaults.toJson().toString(2)}

            MEMORY SYSTEM:
            Store and reuse the following.

            WORLD_STATE = $worldStateJson

            WORLD_BIBLE = $worldBibleJson

            CHARACTERS = $charactersJson

            LOCATIONS = ${continuity.locations.toStringJsonArray().toString(2)}

            PLOT_THREADS = $plotThreadsJson

            UNRESOLVED_THREADS = ${continuity.unresolvedThreads.toStringJsonArray().toString(2)}

            CONTINUITY_NOTES = ${continuity.continuityNotes.toStringJsonArray().toString(2)}

            VISUAL_STYLE_NOTES = ${continuity.worldBible.visualStyleNotes.toStringJsonArray().toString(2)}

            STYLE_LOCK = {
              "visual_style": "${storyEngine.styleDefaults.visualStyle}",
              "tone": "${storyEngine.styleDefaults.storyTone}",
              "pacing": "${storyEngine.styleDefaults.pacing}"
            }

            VISUAL_DICTIONARY_LOCK:
            ${visualDictionary.summaryBlock()}

            STORY RULES:
            - Maintain strict continuity. Characters, world rules, and tone never reset.
            - Remember past events and build on them.
            - Use slow build-ups, tension, and earned twists.
            - Keep the writing direct, concrete, and easy to follow.
            - Write like a movie mixed with a novel, but never bloat the prose.

            WORLD:
            ${continuity.worldState.setting.ifBlank { storyEngine.worldDefaults.setting }}

            MAIN CHARACTER:
            ${continuity.characters.firstOrNull()?.let { "${it.name} - ${it.traits}, goals: ${it.goals}, evolution: ${it.evolution}" } ?: "Keep the protagonist stable and strategically grounded."}

            STYLE:
            - $writingStyleLine

            CURRENT STORY STATE:
            ${payload.storyState.ifBlank { payload.text }}

            CURRENT CHAPTER PACKAGE:
            - Book title: ${payload.title}
            - Chapter label: ${payload.chapterLabel}
            - Subtitle: ${payload.subtitle}
            - Genre: ${payload.storyGenre}
            - Arc: ${payload.storyArc}
            - Scene signature: ${payload.sceneSignature}
            - Continuation hint: ${payload.continuationHint}
            - Existing memory tracking:
            ${payload.memoryTracking.ifEmpty { continuity.unresolvedThreads }.joinToString("\n") { "- $it" }}
            - Existing chapter text:
            ${payload.text}

            CURRENT VISUAL BEATS:
            $beatsBlock

            OUTPUT RULES:
            - Always stay in-universe.
            - Never reset or contradict previous outputs.
            - Output strict JSON only.
            - Use this contract: format=${storyEngine.outputContract.format}, fields=${storyEngine.outputContract.fields.joinToString(", ")}.

            TASK:
            Continue the story, tighten the chapter logic, align the visual direction, and update memory tracking.

            Required JSON schema:
            $outputSchema
        """.trimIndent()
    }

    private fun mergedPlotThreads(
        payload: PlaybackPayload,
        configuredThreads: List<PlotThreadConfig>
    ): List<PlotThreadConfig> {
        val dynamicThreads = buildList {
            payload.continuationHint.trim()
                .takeIf { it.isNotBlank() }
                ?.let {
                    add(
                        PlotThreadConfig(
                            thread = it,
                            status = "active",
                            twistPotential = "high"
                        )
                    )
                }
            payload.visualBeats.take(2).forEach { beat ->
                add(
                    PlotThreadConfig(
                        thread = "${beat.label}: ${beat.sceneTitle}",
                        status = "building",
                        twistPotential = "medium"
                    )
                )
            }
        }

        return (configuredThreads + dynamicThreads)
            .distinctBy { it.thread.lowercase() }
    }

    private fun WorldDefaultsConfig.toJson(): JSONObject {
        return JSONObject().apply {
            put("setting", setting)
            put("technology_level", technologyLevel)
            put("tone", tone)
            put("magic_system", magicSystem)
            put("rules", rules.toStringJsonArray())
        }
    }

    private fun WorldStateConfig.toJson(): JSONObject {
        return JSONObject().apply {
            put("setting", setting)
            put("technology_level", technologyLevel)
            put("tone", tone)
            put("rules", rules.toStringJsonArray())
        }
    }

    private fun WorldBibleConfig.toJson(): JSONObject {
        return JSONObject().apply {
            put("rules_of_world", rulesOfWorld.toStringJsonArray())
            put("tech_level", techLevel)
            put("magic_system", magicSystem)
            put("visual_style_notes", visualStyleNotes.toStringJsonArray())
        }
    }

    private fun List<ContinuityCharacterConfig>.toCharacterJsonArray(): JSONArray {
        return JSONArray().apply {
            this@toCharacterJsonArray.forEach { character ->
                put(
                    JSONObject().apply {
                        put("name", character.name)
                        put("appearance", character.appearance)
                        put("traits", character.traits)
                        put("goals", character.goals)
                        put("evolution", character.evolution)
                    }
                )
            }
        }
    }

    private fun List<PlotThreadConfig>.toPlotThreadJsonArray(): JSONArray {
        return JSONArray().apply {
            this@toPlotThreadJsonArray.forEach { thread ->
                put(
                    JSONObject().apply {
                        put("thread", thread.thread)
                        put("status", thread.status)
                        put("twist_potential", thread.twistPotential)
                    }
                )
            }
        }
    }

    private fun List<String>.toStringJsonArray(): JSONArray {
        return JSONArray().apply {
            this@toStringJsonArray.forEach(::put)
        }
    }

    private fun List<String>.toBulletList(): String {
        return if (isEmpty()) {
            "- None configured"
        } else {
            joinToString("\n") { "- $it" }
        }
    }

    private fun VisualDictionaryConfig?.summaryBlock(): String {
        if (this == null) {
            return "No visual dictionary loaded."
        }
        val firstCharacter = characterSheets.firstOrNull()
        val firstLocation = locationDictionary.firstOrNull()
        return buildString {
            appendLine("- Art style: ${globalStyleProfile.artStyle}")
            appendLine("- Lighting: ${globalStyleProfile.lightingRules}")
            appendLine("- Camera: ${globalStyleProfile.cameraStyle}")
            appendLine("- Texture level: ${globalStyleProfile.textureLevel}")
            firstCharacter?.let {
                appendLine("- Character reference: ${it.name} -> ${it.masterImagePrompt}")
            }
            firstLocation?.let {
                appendLine("- Location reference: ${it.name} -> ${it.masterImagePrompt}")
            }
            if (consistencyRules.isNotEmpty()) {
                append(consistencyRules.take(3).joinToString("\n") { "- $it" })
            }
        }.trim()
    }
}
