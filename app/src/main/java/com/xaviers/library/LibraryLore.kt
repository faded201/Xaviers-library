package com.xaviers.library

data class CanonAnchor(
    val chapterSeed: Int,
    val omen: String,
    val payoff: String,
    val influenceTags: List<String>
)

data class LibraryBook(
    val id: Int,
    val title: String,
    val arcName: String,
    val friendshipThread: String,
    val enemyThread: String,
    val sceneSignature: String,
    val canonAnchor: CanonAnchor
) {
    val tomeCode: String
        get() = "Tome ${"%03d".format(id + 1)}"
}

enum class CardFamily {
    CHARACTER,
    RELIC,
    REALM,
    FATE,
    VOICE
}

enum class CollectorTier(val label: String, val fusionCost: Int?) {
    COMMON("Common", 4),
    HERO("Hero", 4),
    MYTHICAL("Mythical", 3),
    LEGENDARY("Legendary", 2),
    IMMORTAL("Immortal", null);

    fun nextTier(): CollectorTier? = when (this) {
        COMMON -> HERO
        HERO -> MYTHICAL
        MYTHICAL -> LEGENDARY
        LEGENDARY -> IMMORTAL
        IMMORTAL -> null
    }
}

data class CardArchetype(
    val id: String,
    val name: String,
    val family: CardFamily,
    val lore: String,
    val influenceTags: List<String>,
    val holographicFinish: String
)

data class CardStack(
    val archetype: CardArchetype,
    val tier: CollectorTier,
    val copies: Int
)

data class RitualReward(
    val inkDelta: Int,
    val cardDrop: CardStack?,
    val fusionText: String?,
    val eventBanner: String
)

data class RitualSnapshot(
    val librarySubtitle: String,
    val stateOverline: String,
    val ritualHint: String,
    val imageCaption: String,
    val buttonLabel: String,
    val eventBanner: String?,
    val shareText: String
)
