package com.xaviers.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptPackParserTest {

    @Test
    fun `valid prompt pack loads all top level sections`() {
        val parsed = PromptPackParser.parsePromptPack(validPromptPackJson())

        assertEquals("Story engine role", parsed.storyEngine.systemRole)
        assertEquals("Visual style", parsed.visualEngine.styleLock)
        assertEquals("Kael", parsed.continuity.characters.first().name)
    }

    @Test(expected = PromptPackValidationException::class)
    fun `missing story_engine fails validation`() {
        PromptPackParser.parsePromptPack(
            """
                {
                  "visual_engine": {},
                  "continuity": {}
                }
            """.trimIndent()
        )
    }

    @Test(expected = PromptPackValidationException::class)
    fun `malformed visual dictionary fails cleanly`() {
        PromptPackParser.parseVisualDictionary("{ bad json")
    }

    @Test
    fun `story prompt uses imported story settings`() {
        val promptPack = PromptPackParser.parsePromptPack(validPromptPackJson())
        val visualDictionary = PromptPackParser.parseVisualDictionary(validVisualDictionaryJson())

        val prompt = XavierCorePromptSystem.buildStoryPrompt(
            payload = samplePayload(),
            styleOverride = "keep the action clear",
            promptPack = promptPack,
            visualDictionary = visualDictionary
        )

        assertTrue(prompt.contains("Story engine role"))
        assertTrue(prompt.contains("floating city under storm glass"))
        assertTrue(prompt.contains("keep the action clear"))
        assertTrue(prompt.contains("Character reference: Kael"))
    }

    @Test
    fun `visual prompt uses imported visual settings and dictionary`() {
        val promptPack = PromptPackParser.parsePromptPack(validPromptPackJson())
        val visualDictionary = PromptPackParser.parseVisualDictionary(validVisualDictionaryJson())

        val prompt = VisualPromptLock.build(
            state = PlaybackVisualState(
                visualBeatLabel = "SCENE 1",
                sceneTitle = "Bridge watch",
                sceneSetting = "Nhal Veil upper bridge",
                focusCharacter = "Kael",
                shortCaption = "Kael watches the city",
                visualPrompt = "storm light over the city"
            ),
            visualEngine = promptPack.visualEngine,
            visualDictionary = visualDictionary,
            extraStyle = "sharp outlines"
        )

        assertTrue(prompt.contains("Visual style"))
        assertTrue(prompt.contains("Kael: Kael master prompt"))
        assertTrue(prompt.contains("Nhal Veil master prompt"))
        assertTrue(prompt.contains("sharp outlines"))
    }

    private fun samplePayload(): PlaybackPayload {
        return PlaybackPayload(
            id = 1,
            bookId = 23,
            title = "The Static Crown",
            subtitle = "Bridge of Witness",
            text = "Kael crosses the bridge and the city answers back.",
            storyState = "Kael has learned the relay is waking.",
            idleStatus = "Ready",
            chapterNumber = 3,
            chapterLabel = "Chapter 3",
            transitionPauseMs = 1500L,
            visualBeats = listOf(
                StoryVisualBeat(
                    label = "Opening tableau",
                    sceneTitle = "Bridge watch",
                    sceneSetting = "Nhal Veil upper bridge",
                    focusCharacter = "Kael",
                    supportingCharacter = "storm wardens",
                    shortCaption = "Kael stands above the city",
                    prompt = "Kael on the bridge",
                    cameraCue = "wide shot",
                    motionCue = "storm fog rolling",
                    frameVariant = 0
                )
            ),
            memoryTracking = listOf("Kael is still hiding the truth."),
            voiceEngineLabel = "CosyVoice",
            visualEngineLabel = "Diffusion",
            storyGenre = "dark sci-fi fantasy",
            storyArc = "Relay Awakening",
            sceneSignature = "floating city under storm glass",
            isContinuous = true,
            continuationHint = "The relay is close to waking."
        )
    }

    private fun validPromptPackJson(): String {
        return """
            {
              "story_engine": {
                "system_role": "Story engine role",
                "primary_goals": ["Keep continuity strict."],
                "global_rules": ["Never reset the universe."],
                "world_defaults": {
                  "setting": "dark sci-fi fantasy",
                  "technology_level": "lost advanced technology",
                  "tone": "mysterious and epic",
                  "magic_system": "ritual machinery",
                  "rules": ["Rule one"]
                },
                "style_defaults": {
                  "writing_style": ["direct", "clear"],
                  "pacing": "slow build",
                  "story_tone": "immersive",
                  "visual_style": "cinematic"
                },
                "output_contract": {
                  "format": "strict_json",
                  "fields": ["subtitle", "summary", "image_style", "beats", "memory_tracking"]
                }
              },
              "visual_engine": {
                "style_lock": "Visual style",
                "world_rules": "Consistent world rules",
                "character_lock": "Kael locked",
                "camera": "wide shot",
                "mood": "tense",
                "default_scene": "Kael on the bridge"
              },
              "continuity": {
                "world_bible": {
                  "rules_of_world": ["Stable world rule"],
                  "tech_level": "lost advanced technology",
                  "magic_system": "ritual machinery",
                  "visual_style_notes": ["Keep it readable"]
                },
                "world_state": {
                  "setting": "dark sci-fi fantasy",
                  "technology_level": "lost advanced technology",
                  "tone": "mysterious and epic",
                  "rules": ["Stable architecture"]
                },
                "characters": [
                  {
                    "name": "Kael",
                    "appearance": "pale skin, glowing blue eyes",
                    "traits": "quiet, strategic",
                    "goals": "stop the relay",
                    "evolution": "grows into power"
                  }
                ],
                "locations": ["Nhal Veil"],
                "plot_threads": [
                  {
                    "thread": "villain plan",
                    "status": "open",
                    "twist_potential": "high"
                  }
                ],
                "unresolved_threads": ["Who controls the relay"],
                "continuity_notes": ["Keep Kael stable"]
              }
            }
        """.trimIndent()
    }

    private fun validVisualDictionaryJson(): String {
        return """
            {
              "global_style_profile": {
                "art_style": "anime gothic",
                "lighting_rules": "dramatic light",
                "color_palette": ["#000000", "#990000"],
                "texture_level": "detailed",
                "camera_style": "cinematic",
                "rendering_quality": "sharp"
              },
              "character_sheets": [
                {
                  "name": "Kael",
                  "visual_identity": {
                    "face_shape": "sharp",
                    "hair": "long black hair",
                    "eyes": "blue glow",
                    "skin": "pale",
                    "body_type": "lean"
                  },
                  "clothing": {
                    "outfit": "dark armor",
                    "materials": "leather",
                    "colors": ["black", "gold"]
                  },
                  "signature_traits": ["serious expression"],
                  "pose_style": "controlled",
                  "master_image_prompt": "Kael master prompt"
                }
              ],
              "object_and_item_dictionary": [],
              "location_dictionary": [
                {
                  "name": "Nhal Veil",
                  "type": "city",
                  "visual_design": "stacked city",
                  "lighting": "blue storm light",
                  "mood": "tense",
                  "master_image_prompt": "Nhal Veil master prompt"
                }
              ],
              "scene_image_prompts": [
                {
                  "scene_id": "SCENE 1",
                  "image_prompt": "Scene 1 prompt",
                  "style_consistency_reference": "Keep the look fixed"
                }
              ],
              "consistency_rules": [
                "Kael always keeps the same armor."
              ]
            }
        """.trimIndent()
    }
}
