package com.xaviers.library

class LibraryEngine {

    private val canonAnchors = buildCanonAnchors()
    private val books = buildBooks()
    private val cardArchetypes = buildCardArchetypes()
    private val inventory = mutableMapOf<Pair<String, CollectorTier>, Int>()

    private var activeBookIndex = 0
    private var awakenCount = 0
    private var aetherInk = 72

    init {
        seedStarterDeck()
    }

    fun snapshotFor(state: TomeState): RitualSnapshot {
        return buildSnapshot(state, eventBanner = null)
    }

    fun currentBook(): LibraryBook = books[activeBookIndex]

    fun bookById(id: Int): LibraryBook = books[id.coerceIn(0, books.lastIndex)]

    fun focusBook(bookId: Int, state: TomeState): RitualSnapshot {
        activeBookIndex = bookId.coerceIn(0, books.lastIndex)
        val book = currentBook()
        return buildSnapshot(
            state = state,
            eventBanner = "${book.tomeCode} has been drawn from the shelf. The rite now bends toward ${book.title}."
        )
    }

    fun advanceTo(state: TomeState): RitualSnapshot {
        if (state == TomeState.DORMANT && awakenCount > 0) {
            activeBookIndex = (activeBookIndex + 1) % books.size
        }

        val reward = rewardFor(state)
        aetherInk += reward.inkDelta
        awakenCount += 1

        return buildSnapshot(state, reward.eventBanner)
    }

    fun shelfEntries(): List<BookShelfEntry> {
        val focusedId = currentBook().id
        return books.map { book ->
            BookShelfEntry(
                id = book.id,
                tomeCode = book.tomeCode,
                title = book.title,
                arcName = book.arcName,
                sceneSignature = book.sceneSignature,
                seedLine = "Seed ${book.canonAnchor.chapterSeed} • ${book.canonAnchor.omen}",
                isFocused = book.id == focusedId
            )
        }
    }

    fun vaultSnapshot(): VaultSnapshot {
        val current = currentBook()
        val activeDeck = activeDeck()
        val tierCounts = CollectorTier.entries.map { tier ->
            TierCount(
                tier = tier,
                count = inventory.entries
                    .filter { it.key.second == tier }
                    .sumOf { it.value }
            )
        }

        val fusionGuide = CollectorTier.entries
            .mapNotNull { tier ->
                val cost = tier.fusionCost ?: return@mapNotNull null
                val next = tier.nextTier() ?: return@mapNotNull null
                "${cost} ${tier.label} -> 1 ${next.label}"
            }
            .joinToString("   •   ")

        val deckNames = if (activeDeck.isEmpty()) {
            "the shelf is quiet"
        } else {
            activeDeck.joinToString(", ") { it.archetype.name }
        }

        return VaultSnapshot(
            aetherInk = aetherInk,
            activeDeck = activeDeck,
            tierCounts = tierCounts,
            fusionGuide = fusionGuide,
            archivistNote = "Current deck: $deckNames. ${current.title} is listening for ${current.canonAnchor.payoff.lowercase()}."
        )
    }

    private fun buildSnapshot(state: TomeState, eventBanner: String?): RitualSnapshot {
        val book = books[activeBookIndex]
        val activeDeck = activeDeck()
        val deckLine = activeDeck.joinToString(" • ") {
            "${it.archetype.name} ${it.tier.label}"
        }
        val collectionLine = CollectorTier.entries.joinToString(" / ") { tier ->
            val count = inventory.entries
                .filter { it.key.second == tier }
                .sumOf { it.value }
            "${tier.label.first()}:$count"
        }

        val ritualHint = buildString {
            appendLine("${book.title} // ${book.arcName}")
            appendLine("Seed chapter ${book.canonAnchor.chapterSeed}: ${book.canonAnchor.omen}")
            appendLine("Payoff thread: ${book.canonAnchor.payoff}")
            appendLine("Friendship: ${book.friendshipThread}")
            appendLine("Enemy vector: ${book.enemyThread}")
            appendLine("Deck: $deckLine")
            append("Long-press the tome or rite button to share this omen.")
        }

        val shareText = buildString {
            appendLine("Xavier's Library // ${book.tomeCode}")
            appendLine(book.title)
            appendLine(getStateShareLine(state, book))
            appendLine("Deck: $deckLine")
            appendLine("Seed chapter ${book.canonAnchor.chapterSeed}: ${book.canonAnchor.omen}")
            appendLine("Aether Ink: $aetherInk")
            append("https://xavierslibrary.app/tome/${book.id + 1}")
        }

        return RitualSnapshot(
            librarySubtitle = "${book.tomeCode} of 388 • Aether Ink $aetherInk • $collectionLine",
            stateOverline = "Current Manifestation // ${book.sceneSignature}",
            ritualHint = ritualHint,
            imageCaption = "${book.tomeCode.lowercase()} // ${book.title}",
            buttonLabel = if (state == TomeState.UNLEASHED) {
                "Open Next Tome"
            } else {
                "Advance The Rite"
            },
            eventBanner = eventBanner,
            shareText = shareText
        )
    }

    private fun rewardFor(state: TomeState): RitualReward {
        val book = books[activeBookIndex]
        val inkDelta = when (state) {
            TomeState.DORMANT -> 4
            TomeState.STIRRING -> 10
            TomeState.AWAKENED -> 18
            TomeState.UNLEASHED -> 32
        }

        val shouldDropCard = when (state) {
            TomeState.DORMANT -> false
            TomeState.STIRRING -> (book.id + awakenCount) % 3 == 0
            TomeState.AWAKENED -> (book.id + awakenCount) % 2 == 0
            TomeState.UNLEASHED -> true
        }

        val cardDrop = if (shouldDropCard) {
            val archetype = cardArchetypes[(book.id + awakenCount + state.ordinal) % cardArchetypes.size]
            addCard(archetype, CollectorTier.COMMON, 1)
            CardStack(archetype, CollectorTier.COMMON, 1)
        } else {
            null
        }

        val fusionText = cardDrop?.let { attemptFusion(it.archetype) }
        val eventBanner = buildString {
            append("Aether Ink +$inkDelta")
            cardDrop?.let { append(" • Holographic ${it.tier.label.lowercase()} ${it.archetype.name} recovered") }
            fusionText?.let { append(" • $it") }
            if (cardDrop == null) {
                append(" • The tome yielded memory instead of matter")
            }
        }

        return RitualReward(
            inkDelta = inkDelta,
            cardDrop = cardDrop,
            fusionText = fusionText,
            eventBanner = eventBanner
        )
    }

    private fun activeDeck(): List<CardStack> {
        return inventory.entries
            .mapNotNull { (key, copies) ->
                val archetype = cardArchetypes.firstOrNull { it.id == key.first } ?: return@mapNotNull null
                CardStack(archetype = archetype, tier = key.second, copies = copies)
            }
            .sortedWith(
                compareByDescending<CardStack> { it.tier.ordinal }
                    .thenByDescending { it.copies }
                    .thenBy { it.archetype.name }
            )
            .take(3)
            .ifEmpty {
                listOf(
                    CardStack(cardArchetypes[0], CollectorTier.COMMON, 1),
                    CardStack(cardArchetypes[1], CollectorTier.COMMON, 1),
                    CardStack(cardArchetypes[2], CollectorTier.COMMON, 1)
                )
            }
    }

    private fun addCard(archetype: CardArchetype, tier: CollectorTier, copies: Int) {
        val key = archetype.id to tier
        inventory[key] = inventory.getOrDefault(key, 0) + copies
    }

    private fun attemptFusion(archetype: CardArchetype): String? {
        var currentTier = CollectorTier.COMMON
        var fusionMessage: String? = null

        while (true) {
            val requirement = currentTier.fusionCost ?: break
            val currentKey = archetype.id to currentTier
            val copies = inventory.getOrDefault(currentKey, 0)
            if (copies < requirement) break

            val nextTier = currentTier.nextTier() ?: break
            inventory[currentKey] = copies - requirement
            if (inventory[currentKey] == 0) {
                inventory.remove(currentKey)
            }

            val nextKey = archetype.id to nextTier
            inventory[nextKey] = inventory.getOrDefault(nextKey, 0) + 1
            fusionMessage = "${archetype.name} ascended to ${nextTier.label}"
            currentTier = nextTier
        }

        return fusionMessage
    }

    private fun seedStarterDeck() {
        addCard(cardArchetypes[0], CollectorTier.COMMON, 2)
        addCard(cardArchetypes[1], CollectorTier.COMMON, 2)
        addCard(cardArchetypes[2], CollectorTier.COMMON, 2)
        addCard(cardArchetypes[3], CollectorTier.COMMON, 1)
        addCard(cardArchetypes[4], CollectorTier.COMMON, 1)
    }

    private fun buildBooks(): List<LibraryBook> {
        val titles = buildUniqueTitles(388)
        val arcNames = listOf(
            "Primordial Record", "Ash Court Cycle", "Velvet Eclipse", "Salt Cathedral", "Hollow Mercy",
            "The Glass Oath", "Starless Bloom", "The Red Lantern Pact"
        )
        val friendships = listOf(
            "a friend arrives before the wound, carrying the only key",
            "the softest ally already knows the ending and hides it",
            "a future rival begins as the one soul who still believes",
            "the truest companion is always introduced beside firelight",
            "every oath-bond begins as a rescue no one remembers clearly"
        )
        val enemies = listOf(
            "the smiling witness becomes the patient enemy",
            "the enemy enters first as a benefactor in mourning silk",
            "a rival house plants mercy now to collect obedience later",
            "the hunter appears disguised as a guide to safer rooms",
            "the quiet villain is written into the architecture from chapter one"
        )
        val scenes = listOf(
            "moonlit dust and breathing iron",
            "ember glass under sacred rain",
            "cyan runes listening through velvet dark",
            "holographic relic fire and cathedral fog",
            "ink-gold smoke over sleeping shelves"
        )

        return List(388) { index ->
            val anchor = canonAnchors[index % canonAnchors.size]
            LibraryBook(
                id = index,
                title = titles[index],
                arcName = arcNames[index % arcNames.size],
                friendshipThread = friendships[index % friendships.size],
                enemyThread = enemies[(index + 2) % enemies.size],
                sceneSignature = scenes[(index + 1) % scenes.size],
                canonAnchor = anchor
            )
        }
    }

    private fun buildUniqueTitles(count: Int): List<String> {
        val signatureTitles = listOf(
            "My Hollowblood System",
            "Birth of the Cinder Blade",
            "The Gravebound Prince",
            "A Lantern for the Hollow King",
            "Chronicle of the Moon-Eater",
            "The Necromancer Who Stole Dawn",
            "Ashen Throne of the Last Saint",
            "The Obsidian Fox Oath",
            "Requiem of the Starved Cathedral",
            "The Blood Archivist's Promise",
            "The Warden of Broken Suns",
            "The Silent Tyrant's Library",
            "Oracle of the Rift Garden",
            "The Seraph Beneath Black Water",
            "Empire of the Lantern Dead",
            "The Wolf of the Drowned Crown",
            "Saint of the Ninth Labyrinth",
            "The Grave Harbor Reclaimer",
            "Moonscript for a Fallen Queen",
            "The Cursed Heir of Ember Glass",
            "The Voidbound Alchemist",
            "The Prince Who Fed the Abyss",
            "The Crimson Library Pact",
            "A Cathedral Built for Monsters",
            "The Last Gate of Hollow Mercy",
            "Queen of the Ash Orchard",
            "The Sword Saint of Quiet Ruin",
            "The Dreaming Warden of Salt",
            "The Reaper's Golden Scripture",
            "Blood Oath for the Sleeping Dragon",
            "The Ivory Monarch Reborn",
            "The Fox Who Remembered War",
            "Tower of the Starless Saint",
            "The Graveflower Cultivator",
            "The Hollow Seraph's Trial",
            "The Archive of Unfinished Kings",
            "The Eclipse Hunter's Reign",
            "Throne of the Ninth Ember",
            "The Moonlit Tyrant's Mercy",
            "The Demon's Book of Glass Teeth",
            "The Last Archivist of Cinder Vale",
            "Prayer for the Obsidian Wolf",
            "The Crownless Reaper",
            "Stormgate of the Forgotten Heir",
            "The Velvet Necromancer's Return",
            "The Dragon Beneath the Library",
            "The Hollow Prince Ascends",
            "Book of the Bleeding Oracle",
            "The Saint Who Taught the Night to Burn",
            "The Riftborn Swordsman",
            "Kingdom of the Lantern Serpent",
            "The Drowned Queen's Codex",
            "The Gravebound Sovereign",
            "The Moon Wolf's Ascension",
            "The Emperor of Broken Tomes",
            "The Whispering Cathedral Trial",
            "The Ashborn Harbinger",
            "The Last Demon of Silver Rain",
            "The Library at the End of Mourning",
            "The Cinderblood Chronicle",
            "The Serpent Crown Testament",
            "The Iron Saint's Awakening",
            "Reign of the Hollow Archive",
            "The Blade That Drank Moonlight",
            "The Wraith King's Gentle Promise",
            "The Eclipse Daughter of Ruin",
            "The Black Harbor Requiem",
            "Bloodfire for the Sleeping Throne",
            "The Cultivator of Forgotten Graves",
            "The Night Library Sovereign",
            "The Obsidian Saint's Return",
            "The Gatekeeper of Ashen Stars",
            "The Last Blessing of the Grave Fox",
            "The Tyrant Who Collected Tomorrows",
            "The Moonbound Monastery War",
            "The Archivist of the Burning Garden",
            "The Silent Library of Red Snow"
        )
        val personalAspects = listOf(
            "Vampire", "Dragonic", "Shadow", "Demonic", "Graveborn", "Voidwalker",
            "Runeblade", "Moonbound", "Bloodforged", "Ashen", "Nightborn", "Stormbound",
            "Oracle", "Seraph", "Ironblood", "Cinderborn", "Eclipse", "Starfallen",
            "Ghostfire", "Hollowborn", "Thornbound", "Frostgrave"
        )
        val personalTracks = listOf(
            "System", "Legacy", "Codex", "Ascension", "Dominion", "Archive",
            "Trial", "Awakening", "Scripture", "Reckoning", "Cycle", "Pact"
        )
        val birthDescriptors = listOf(
            "Demonic", "Cinder", "Voidforged", "Moonlit", "Bloodbound", "Obsidian",
            "Fallen", "Starved", "Runeborn", "Graveborn", "Hollow", "Nightbound",
            "Ashen", "Ironblood", "Stormscarred", "Ghostfire"
        )
        val birthEntities = listOf(
            "Sword", "Tyrant", "Prince", "Saint", "Necromancer", "Dragon",
            "Reaper", "Monarch", "Warden", "Seraph", "Hunter", "Oracle",
            "Fox", "Wolf", "Cultivator", "King"
        )
        val realmDescriptors = listOf(
            "Ashen", "Moonlit", "Hollow", "Obsidian", "Velvet", "Gravebound",
            "Lantern", "Crimson", "Cinder", "Silent", "Starless", "Ivory",
            "Riftborn", "Eclipse", "Bloodbound", "Stormglass", "Golden",
            "Nightbound", "Wraith", "Runebound"
        )
        val roles = listOf(
            "Archivist", "Prince", "Saint", "Warden", "Hunter", "Queen",
            "King", "Alchemist", "Tyrant", "Seraph", "Reaper", "Cultivator",
            "Monarch", "Fox", "Oracle", "Dragon", "Heir", "Wolf",
            "Swordsman", "Harbinger"
        )
        val relics = listOf(
            "Archive", "Cathedral", "Crown", "Vault", "Garden", "Throne",
            "Labyrinth", "Harbor", "Gate", "Library", "Citadel", "Scripture",
            "Sanctum", "Kingdom", "Engine", "Requary"
        )
        val storyTokens = listOf(
            "Requiem", "Legacy", "Throne", "Curse", "Covenant", "Empire",
            "Mercy", "Scripture", "Labyrinth", "Vow", "Harvest", "Chronicle",
            "Ascension", "Silence", "Eclipse", "Prophecy"
        )
        val endings = listOf(
            "The Last Oath", "Empire of Ash", "The Broken Crown", "The Ninth Gate",
            "A Kingdom of Embers", "The Silent Reign", "The Final Mercy",
            "The Starless War", "The Hollow Promise", "The Red Scripture",
            "The Grave Bloom", "The Black Tide"
        )
        val verbs = listOf(
            "Wakes", "Breaks", "Bleeds", "Ascends", "Hungers", "Returns",
            "Burns", "Remembers", "Opens", "Kneels", "Howls", "Falls"
        )

        val titles = mutableListOf<String>()
        val usedTitles = linkedSetOf<String>()

        signatureTitles.forEach { title ->
            if (usedTitles.add(title)) {
                titles += title
            }
        }

        var cursor = 0
        while (titles.size < count) {
            val candidate = when (cursor % 8) {
                0 -> "My ${personalAspects[cursor % personalAspects.size]} ${personalTracks[(cursor / 2) % personalTracks.size]}"
                1 -> "Birth of the ${birthDescriptors[cursor % birthDescriptors.size]} ${birthEntities[(cursor / 3) % birthEntities.size]}"
                2 -> "The ${realmDescriptors[cursor % realmDescriptors.size]} ${roles[(cursor / 5) % roles.size]}"
                3 -> "${storyTokens[cursor % storyTokens.size]} of the ${realmDescriptors[(cursor / 4) % realmDescriptors.size]} ${relics[(cursor / 6) % relics.size]}"
                4 -> "Chronicle of the ${realmDescriptors[cursor % realmDescriptors.size]} ${roles[(cursor / 7) % roles.size]}"
                5 -> "${realmDescriptors[cursor % realmDescriptors.size]} ${roles[(cursor / 3) % roles.size]}: ${endings[(cursor / 5) % endings.size]}"
                6 -> "When the ${storyTokens[cursor % storyTokens.size]} ${verbs[(cursor / 2) % verbs.size]}"
                else -> "The ${roles[cursor % roles.size]}'s ${endings[(cursor / 4) % endings.size]}"
            }

            if (usedTitles.add(candidate)) {
                titles += candidate
            }
            cursor += 1
        }

        return titles.take(count)
    }

    private fun buildCanonAnchors(): List<CanonAnchor> {
        val subjects = listOf(
            "A silver key", "A blind archivist", "A drowned crown", "A blood oath", "A false saint",
            "A moonlit wolf", "An ivory gate", "A lantern child", "A cracked choir bell", "A sleeping serpent"
        )
        val actions = listOf(
            "is offered in kindness",
            "is mistaken for mercy",
            "is hidden in a vow",
            "is seen in a dream before the war",
            "is mentioned in passing beside a feast",
            "is carried by the first true friend",
            "is placed on the wrong altar",
            "is recorded, then erased",
            "is laughed off as folklore",
            "is witnessed by the future traitor"
        )
        val payoffs = listOf(
            "later becomes the hinge of the kingdom's betrayal",
            "returns as the proof that love and ruin were always twins",
            "reveals the enemy was welcomed long before he was feared",
            "turns the final ally into the one person who can end the curse",
            "proves the first chapters already told the ending in miniature",
            "rewrites a friendship into a sacrificial vow at the climax",
            "opens the one forbidden room that was promised in chapter three",
            "recasts a joke as the book's deepest prophecy",
            "forces the hero to choose memory over victory",
            "binds the final page to the earliest omen"
        )
        val tags = listOf(
            listOf("betrayal", "key", "mercy"),
            listOf("friendship", "oath", "moon"),
            listOf("enemy", "cathedral", "crowns"),
            listOf("rebirth", "curse", "embers"),
            listOf("oracle", "serpent", "vault")
        )

        return List(80) { index ->
            CanonAnchor(
                chapterSeed = index + 1,
                omen = "${subjects[index % subjects.size]} ${actions[(index + 3) % actions.size]}",
                payoff = payoffs[(index + 5) % payoffs.size],
                influenceTags = tags[index % tags.size]
            )
        }
    }

    private fun buildCardArchetypes(): List<CardArchetype> {
        return listOf(
            CardArchetype(
                id = "moon_archivist",
                name = "Moon Archivist",
                family = CardFamily.CHARACTER,
                lore = "A keeper of forbidden indexes who can read a betrayal before it blooms.",
                influenceTags = listOf("scholarly", "tragic", "silver", "memory"),
                holographicFinish = "cyan-silver ghost foil"
            ),
            CardArchetype(
                id = "broken_crown",
                name = "Broken Crown",
                family = CardFamily.RELIC,
                lore = "Royal authority preserved in fragments sharp enough to cut fate.",
                influenceTags = listOf("royal", "curse", "betrayal", "cathedral"),
                holographicFinish = "ember-gold fracture foil"
            ),
            CardArchetype(
                id = "lantern_fox",
                name = "Lantern Fox",
                family = CardFamily.CHARACTER,
                lore = "A sly guide that leads heroes toward secret rooms and inconvenient truths.",
                influenceTags = listOf("mischief", "guidance", "secret-door", "warmth"),
                holographicFinish = "opal ember foil"
            ),
            CardArchetype(
                id = "ash_seraph",
                name = "Ash Seraph",
                family = CardFamily.CHARACTER,
                lore = "A fallen radiant thing that trades purity for necessary violence.",
                influenceTags = listOf("divine", "violent", "ashes", "judgment"),
                holographicFinish = "white-fire feather foil"
            ),
            CardArchetype(
                id = "void_key",
                name = "Void Key",
                family = CardFamily.RELIC,
                lore = "A key that opens sealed chapters and the versions of you that never healed.",
                influenceTags = listOf("void", "unlock", "twist", "abyss"),
                holographicFinish = "black prism foil"
            ),
            CardArchetype(
                id = "blood_orchard",
                name = "Blood Orchard",
                family = CardFamily.REALM,
                lore = "A harvest where every blossom remembers a name the hero tried to forget.",
                influenceTags = listOf("lush", "blood", "memory", "temptation"),
                holographicFinish = "crimson nectar foil"
            ),
            CardArchetype(
                id = "oracle_veil",
                name = "Oracle Veil",
                family = CardFamily.VOICE,
                lore = "A voice card that turns prophecy into a whisper too beautiful to distrust.",
                influenceTags = listOf("soft", "prophetic", "velvet", "ominous"),
                holographicFinish = "pearl haze foil"
            ),
            CardArchetype(
                id = "grave_harbor",
                name = "Grave Harbor",
                family = CardFamily.REALM,
                lore = "A shoreline where enemies arrive first as grieving pilgrims.",
                influenceTags = listOf("harbor", "mourning", "ghost", "salt"),
                holographicFinish = "stormglass foil"
            )
        )
    }

    private fun getStateShareLine(state: TomeState, book: LibraryBook): String {
        return when (state) {
            TomeState.DORMANT -> "${book.tomeCode} sleeps, but its first betrayal is already planted."
            TomeState.STIRRING -> "${book.tomeCode} has begun to stir. The friend and enemy have entered the stage."
            TomeState.AWAKENED -> "${book.tomeCode} is awake. The early omens are now bending the living plot."
            TomeState.UNLEASHED -> "${book.tomeCode} is unleashed. Cards, canon, and voice are tearing the future open."
        }
    }
}
