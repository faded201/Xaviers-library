package com.xaviers.library

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class PromptPackConfig(
    val storyEngine: StoryEngineConfig,
    val visualEngine: VisualEngineConfig,
    val continuity: ContinuityConfig
)

data class StoryEngineConfig(
    val systemRole: String,
    val primaryGoals: List<String>,
    val globalRules: List<String>,
    val worldDefaults: WorldDefaultsConfig,
    val styleDefaults: StyleDefaultsConfig,
    val outputContract: OutputContractConfig
)

data class WorldDefaultsConfig(
    val setting: String,
    val technologyLevel: String,
    val tone: String,
    val magicSystem: String,
    val rules: List<String>
)

data class StyleDefaultsConfig(
    val writingStyle: List<String>,
    val pacing: String,
    val storyTone: String,
    val visualStyle: String
)

data class OutputContractConfig(
    val format: String,
    val fields: List<String>
)

data class VisualEngineConfig(
    val styleLock: String,
    val worldRules: String,
    val characterLock: String,
    val camera: String,
    val mood: String,
    val defaultScene: String
)

data class ContinuityConfig(
    val worldBible: WorldBibleConfig,
    val worldState: WorldStateConfig,
    val characters: List<ContinuityCharacterConfig>,
    val locations: List<String>,
    val plotThreads: List<PlotThreadConfig>,
    val unresolvedThreads: List<String>,
    val continuityNotes: List<String>
)

data class WorldBibleConfig(
    val rulesOfWorld: List<String>,
    val techLevel: String,
    val magicSystem: String,
    val visualStyleNotes: List<String>
)

data class WorldStateConfig(
    val setting: String,
    val technologyLevel: String,
    val tone: String,
    val rules: List<String>
)

data class ContinuityCharacterConfig(
    val name: String,
    val appearance: String,
    val traits: String,
    val goals: String,
    val evolution: String
)

data class PlotThreadConfig(
    val thread: String,
    val status: String,
    val twistPotential: String
)

data class VisualDictionaryConfig(
    val globalStyleProfile: VisualStyleProfileConfig,
    val characterSheets: List<VisualCharacterSheetConfig>,
    val objectAndItemDictionary: List<VisualObjectConfig>,
    val locationDictionary: List<VisualLocationConfig>,
    val sceneImagePrompts: List<VisualScenePromptConfig>,
    val consistencyRules: List<String>
)

data class VisualStyleProfileConfig(
    val artStyle: String,
    val lightingRules: String,
    val colorPalette: List<String>,
    val textureLevel: String,
    val cameraStyle: String,
    val renderingQuality: String
)

data class VisualCharacterSheetConfig(
    val name: String,
    val visualIdentity: VisualIdentityConfig,
    val clothing: VisualClothingConfig,
    val signatureTraits: List<String>,
    val poseStyle: String,
    val masterImagePrompt: String
)

data class VisualIdentityConfig(
    val faceShape: String,
    val hair: String,
    val eyes: String,
    val skin: String,
    val bodyType: String
)

data class VisualClothingConfig(
    val outfit: String,
    val materials: String,
    val colors: List<String>
)

data class VisualObjectConfig(
    val name: String,
    val type: String,
    val description: String,
    val specialFeatures: List<String>,
    val masterImagePrompt: String
)

data class VisualLocationConfig(
    val name: String,
    val type: String,
    val visualDesign: String,
    val lighting: String,
    val mood: String,
    val masterImagePrompt: String
)

data class VisualScenePromptConfig(
    val sceneId: String,
    val imagePrompt: String,
    val styleConsistencyReference: String
)

class PromptPackValidationException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

object PromptPackParser {

    fun parsePromptPack(rawJson: String): PromptPackConfig {
        val root = parseRoot(rawJson, "prompt_pack.json")
        val storyEngineObject = requiredObject(root, "story_engine")
        val visualEngineObject = requiredObject(root, "visual_engine")
        val continuityObject = requiredObject(root, "continuity")

        return PromptPackConfig(
            storyEngine = parseStoryEngine(storyEngineObject),
            visualEngine = parseVisualEngine(visualEngineObject),
            continuity = parseContinuity(continuityObject)
        )
    }

    fun parseVisualDictionary(rawJson: String): VisualDictionaryConfig {
        val root = parseRoot(rawJson, "visual_dictionary.json")
        return VisualDictionaryConfig(
            globalStyleProfile = parseGlobalStyleProfile(root.optJSONObject("global_style_profile")),
            characterSheets = parseCharacterSheets(root.optJSONArray("character_sheets")),
            objectAndItemDictionary = parseObjects(root.optJSONArray("object_and_item_dictionary")),
            locationDictionary = parseLocations(root.optJSONArray("location_dictionary")),
            sceneImagePrompts = parseScenePrompts(root.optJSONArray("scene_image_prompts")),
            consistencyRules = root.optJSONArray("consistency_rules").toStringList()
        )
    }

    private fun parseRoot(rawJson: String, fileLabel: String): JSONObject {
        try {
            return JSONObject(rawJson)
        } catch (exception: JSONException) {
            throw PromptPackValidationException("Malformed $fileLabel: ${exception.message}", exception)
        }
    }

    private fun requiredObject(root: JSONObject, key: String): JSONObject {
        return root.optJSONObject(key)
            ?: throw PromptPackValidationException("Missing required section: $key")
    }

    private fun parseStoryEngine(source: JSONObject): StoryEngineConfig {
        return StoryEngineConfig(
            systemRole = source.optString("system_role").trim(),
            primaryGoals = source.optJSONArray("primary_goals").toStringList(),
            globalRules = source.optJSONArray("global_rules").toStringList(),
            worldDefaults = parseWorldDefaults(source.optJSONObject("world_defaults")),
            styleDefaults = parseStyleDefaults(source.optJSONObject("style_defaults")),
            outputContract = parseOutputContract(source.optJSONObject("output_contract"))
        )
    }

    private fun parseWorldDefaults(source: JSONObject?): WorldDefaultsConfig {
        val safeSource = source ?: JSONObject()
        return WorldDefaultsConfig(
            setting = safeSource.optString("setting").trim(),
            technologyLevel = safeSource.optString("technology_level").trim(),
            tone = safeSource.optString("tone").trim(),
            magicSystem = safeSource.optString("magic_system").trim(),
            rules = safeSource.optJSONArray("rules").toStringList()
        )
    }

    private fun parseStyleDefaults(source: JSONObject?): StyleDefaultsConfig {
        val safeSource = source ?: JSONObject()
        return StyleDefaultsConfig(
            writingStyle = safeSource.optJSONArray("writing_style").toStringList(),
            pacing = safeSource.optString("pacing").trim(),
            storyTone = safeSource.optString("story_tone").trim(),
            visualStyle = safeSource.optString("visual_style").trim()
        )
    }

    private fun parseOutputContract(source: JSONObject?): OutputContractConfig {
        val safeSource = source ?: JSONObject()
        return OutputContractConfig(
            format = safeSource.optString("format").trim(),
            fields = safeSource.optJSONArray("fields").toStringList()
        )
    }

    private fun parseVisualEngine(source: JSONObject): VisualEngineConfig {
        return VisualEngineConfig(
            styleLock = source.optString("style_lock").trim(),
            worldRules = source.optString("world_rules").trim(),
            characterLock = source.optString("character_lock").trim(),
            camera = source.optString("camera").trim(),
            mood = source.optString("mood").trim(),
            defaultScene = source.optString("default_scene").trim()
        )
    }

    private fun parseContinuity(source: JSONObject): ContinuityConfig {
        return ContinuityConfig(
            worldBible = parseWorldBible(source.optJSONObject("world_bible")),
            worldState = parseWorldState(source.optJSONObject("world_state")),
            characters = parseContinuityCharacters(source.optJSONArray("characters")),
            locations = source.optJSONArray("locations").toStringList(),
            plotThreads = parsePlotThreads(source.optJSONArray("plot_threads")),
            unresolvedThreads = source.optJSONArray("unresolved_threads").toStringList(),
            continuityNotes = source.optJSONArray("continuity_notes").toStringList()
        )
    }

    private fun parseWorldBible(source: JSONObject?): WorldBibleConfig {
        val safeSource = source ?: JSONObject()
        return WorldBibleConfig(
            rulesOfWorld = safeSource.optJSONArray("rules_of_world").toStringList(),
            techLevel = safeSource.optString("tech_level").trim(),
            magicSystem = safeSource.optString("magic_system").trim(),
            visualStyleNotes = safeSource.optJSONArray("visual_style_notes").toStringList()
        )
    }

    private fun parseWorldState(source: JSONObject?): WorldStateConfig {
        val safeSource = source ?: JSONObject()
        return WorldStateConfig(
            setting = safeSource.optString("setting").trim(),
            technologyLevel = safeSource.optString("technology_level").trim(),
            tone = safeSource.optString("tone").trim(),
            rules = safeSource.optJSONArray("rules").toStringList()
        )
    }

    private fun parseContinuityCharacters(source: JSONArray?): List<ContinuityCharacterConfig> {
        return source.toObjectList { item ->
            ContinuityCharacterConfig(
                name = item.optString("name").trim(),
                appearance = item.optString("appearance").trim(),
                traits = item.optString("traits").trim(),
                goals = item.optString("goals").trim(),
                evolution = item.optString("evolution").trim()
            )
        }
    }

    private fun parsePlotThreads(source: JSONArray?): List<PlotThreadConfig> {
        return source.toObjectList { item ->
            PlotThreadConfig(
                thread = item.optString("thread").trim(),
                status = item.optString("status").trim(),
                twistPotential = item.optString("twist_potential").trim()
            )
        }
    }

    private fun parseGlobalStyleProfile(source: JSONObject?): VisualStyleProfileConfig {
        val safeSource = source ?: JSONObject()
        return VisualStyleProfileConfig(
            artStyle = safeSource.optString("art_style").trim(),
            lightingRules = safeSource.optString("lighting_rules").trim(),
            colorPalette = safeSource.optJSONArray("color_palette").toStringList(),
            textureLevel = safeSource.optString("texture_level").trim(),
            cameraStyle = safeSource.optString("camera_style").trim(),
            renderingQuality = safeSource.optString("rendering_quality").trim()
        )
    }

    private fun parseCharacterSheets(source: JSONArray?): List<VisualCharacterSheetConfig> {
        return source.toObjectList { item ->
            VisualCharacterSheetConfig(
                name = item.optString("name").trim(),
                visualIdentity = parseVisualIdentity(item.optJSONObject("visual_identity")),
                clothing = parseVisualClothing(item.optJSONObject("clothing")),
                signatureTraits = item.optJSONArray("signature_traits").toStringList(),
                poseStyle = item.optString("pose_style").trim(),
                masterImagePrompt = item.optString("master_image_prompt").trim()
            )
        }
    }

    private fun parseVisualIdentity(source: JSONObject?): VisualIdentityConfig {
        val safeSource = source ?: JSONObject()
        return VisualIdentityConfig(
            faceShape = safeSource.optString("face_shape").trim(),
            hair = safeSource.optString("hair").trim(),
            eyes = safeSource.optString("eyes").trim(),
            skin = safeSource.optString("skin").trim(),
            bodyType = safeSource.optString("body_type").trim()
        )
    }

    private fun parseVisualClothing(source: JSONObject?): VisualClothingConfig {
        val safeSource = source ?: JSONObject()
        return VisualClothingConfig(
            outfit = safeSource.optString("outfit").trim(),
            materials = safeSource.optString("materials").trim(),
            colors = safeSource.optJSONArray("colors").toStringList()
        )
    }

    private fun parseObjects(source: JSONArray?): List<VisualObjectConfig> {
        return source.toObjectList { item ->
            VisualObjectConfig(
                name = item.optString("name").trim(),
                type = item.optString("type").trim(),
                description = item.optString("description").trim(),
                specialFeatures = item.optJSONArray("special_features").toStringList(),
                masterImagePrompt = item.optString("master_image_prompt").trim()
            )
        }
    }

    private fun parseLocations(source: JSONArray?): List<VisualLocationConfig> {
        return source.toObjectList { item ->
            VisualLocationConfig(
                name = item.optString("name").trim(),
                type = item.optString("type").trim(),
                visualDesign = item.optString("visual_design").trim(),
                lighting = item.optString("lighting").trim(),
                mood = item.optString("mood").trim(),
                masterImagePrompt = item.optString("master_image_prompt").trim()
            )
        }
    }

    private fun parseScenePrompts(source: JSONArray?): List<VisualScenePromptConfig> {
        return source.toObjectList { item ->
            VisualScenePromptConfig(
                sceneId = item.optString("scene_id").trim(),
                imagePrompt = item.optString("image_prompt").trim(),
                styleConsistencyReference = item.optString("style_consistency_reference").trim()
            )
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index)
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?.let(::add)
            }
        }
    }

    private fun <T> JSONArray?.toObjectList(transform: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val objectValue = optJSONObject(index) ?: continue
                add(transform(objectValue))
            }
        }
    }
}
