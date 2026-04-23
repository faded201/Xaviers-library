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
            "Warriors Stand",
            "Enemies Till Dawn",
            "No Mercy for Kings",
            "Blood Before Sunrise",
            "The Last Crown",
            "Until the Kingdom Burns",
            "The Queen I Couldn't Save",
            "Heir to a Dead Throne",
            "A Sword for the Broken",
            "When Wolves Return",
            "The Boy Who Challenged Kings",
            "We Were Never Saints",
            "The Night the Empire Fell",
            "I Buried the Hero",
            "The Enemy Wore My Face",
            "A Crown of Quiet Fire",
            "The Last Oath Between Us",
            "Mercy for the Damned",
            "The Blade and the Betrayal",
            "Kingdom of Second Chances",
            "The One Who Survived the War",
            "Brothers of the Black Dawn",
            "Before the Throne Breaks",
            "A Kingdom Without Mercy",
            "She Chose the Enemy",
            "The King Who Feared the Dark",
            "Blood of the Last Heir",
            "I Won't Die for This Crown",
            "The Day the Saints Fell",
            "Wolves at the Palace Gate",
            "A Promise Made in Ash",
            "The Last Boy of Winter",
            "Enemy of My Last Dawn",
            "The Crown They Couldn't Kill",
            "When the Strongest Falls",
            "The Girl Who Outlived the Kingdom",
            "No Grave for Heroes",
            "The First Betrayal",
            "Our Last Night as Kings",
            "Fire Under the Crown",
            "The Enemy I Could Not Hate",
            "A Throne Built on Names",
            "Until Only Shadows Remain",
            "The Last War Between Brothers",
            "The Queen of a Broken House",
            "I Refused the Ending",
            "The Sword That Chose Revenge",
            "Winter Oath",
            "A Kingdom for the Faithless",
            "If We Survive the Dawn",
            "The Last Mercy of Kings",
            "Ashes Under Gold",
            "The Enemy at My Table",
            "The Boy Who Carried the Crown",
            "Crown of the Unforgiven",
            "The Night We Became Monsters",
            "No Prince for This Kingdom",
            "The War After Midnight",
            "The Last Promise of Summer",
            "The Wolf Who Wouldn't Kneel",
            "I Inherited Ruin",
            "A Fire No King Could Name",
            "The Saint Who Broke the War",
            "Daughters of the Fallen Crown",
            "The Enemy Before Morning",
            "When the Crown Went Dark",
            "My Last Life as a Swordsman",
            "The King Beneath the Ashes",
            "Nothing Left but the Throne",
            "The One Who Returned at Dawn",
            "We Buried the Sun",
            "The Last Hunt of Winter",
            "A Throne for the Enemy",
            "The Crown at World's End",
            "A Blade for the Lost",
            "The House That Refused to Fall",
            "The Last Loyal Heart",
            "The Kingdom We Couldn't Save",
            "Bloodline of the Betrayed",
            "If the Wolves Win",
            "The Boy From the Burned City",
            "A Crown for the Enemy",
            "The Last Friend I Kept",
            "The Strongest One Left Behind",
            "The Oath They Made Me Break",
            "Fire on the Seventh Night",
            "The Enemy's Last Prayer",
            "The Prince Who Survived Nothing",
            "War for the Final Dawn",
            "No Light for the Ruthless",
            "The Queen Who Never Forgave",
            "I Chose the Wrong Kingdom",
            "A Grave for Every Oath",
            "The Last Soldier of Spring",
            "Where the Crown Still Bleeds",
            "The Heir Nobody Wanted",
            "The Kingdom Beyond Revenge",
            "Sword of the Last Morning",
            "The Enemy Who Saved Me",
            "Before the Heroes Wake",
            "The House of Quiet Knives",
            "Dawn Belongs to the Damned",
            "The Last Name in the Fire",
            "I Took the Enemy's Hand",
            "The War We Couldn't Escape",
            "A Crown Worth Killing For",
            "The Night Before the Throne",
            "Return of the Lost Blade",
            "The Ranker Who Wouldn't Kneel",
            "My Last System",
            "The Strongest After Betrayal",
            "I Became the Enemy's Sword",
            "Reborn for the Final War",
            "My Second Life as the Villain's Heir",
            "The Hunter Who Came Back Empty",
            "The Blade That Refused to Break",
            "The Last Enemy Standing",
            "Only the Broken Rise",
            "The War No One Survived",
            "The Crown After the Fall",
            "Enemies Until the End",
            "The Swordsman the Kingdom Feared",
            "The King I Had to Betray",
            "The World After Our Last Victory",
            "One More Dawn to Win"
        )
        val simpleSubjects = listOf(
            "Warriors", "Enemies", "Kings", "Queens", "Wolves", "Hunters",
            "Brothers", "Sisters", "Shadows", "Reapers", "Outcasts", "Rivals",
            "Ghosts", "Soldiers", "Saints", "Giants"
        )
        val simpleHooks = listOf(
            "Stand", "Return", "Still Rise", "Hold the Gate",
            "Wait for Dawn", "Choose War", "Refuse to Kneel", "Come Back Stronger"
        )
        val noValues = listOf(
            "Mercy", "Light", "Grace", "Peace", "Rest", "Forgiveness",
            "Pity", "Shelter"
        )
        val noTargets = listOf(
            "Kings", "Traitors", "Saints", "the Ruthless", "the Faithless",
            "the Damned", "the Broken", "Monsters", "the Exiled"
        )
        val whenSubjects = listOf(
            "Empires", "Crowns", "Kingdoms", "Oaths", "Thrones", "Saints",
            "Swords", "Brothers", "Queens", "Wolves", "Cities", "Names"
        )
        val whenVerbs = listOf(
            "Burn", "Break", "Bleed", "Fall", "Return",
            "Rise", "Kneel", "Wake", "Shatter", "Turn"
        )
        val beforeNouns = listOf(
            "Blood", "Fire", "War", "Ash", "Silence", "Mercy",
            "Steel", "Ruin", "Vengeance", "Storm", "Night", "Winter"
        )
        val beforeTimes = listOf(
            "Sunrise", "Dawn", "Morning", "Midnight",
            "the Storm", "the End", "Winter", "the Fall"
        )
        val heirTargets = listOf(
            "a Dead Throne", "the Broken Kingdom", "the Last Empire", "the Burned House",
            "the Silent Crown", "the Black Dawn", "the Fallen Gate", "the Red War"
        )
        val offerObjects = listOf(
            "Sword", "Crown", "Home", "Kingdom", "Blade", "Last Chance",
            "Name", "Oath", "War", "Fire", "Promise", "Throne"
        )
        val offerTargets = listOf(
            "Broken", "Faithless", "Fallen", "Lost",
            "Exiled", "Damned", "Forgotten", "Hungry"
        )
        val roles = listOf(
            "Boy", "Girl", "King", "Queen", "Prince", "Hunter",
            "Swordsman", "Reaper", "Heir", "Soldier", "Widow", "Villain",
            "Saint", "Wolf", "Daughter", "Son"
        )
        val roleActions = listOf(
            "Challenged Kings", "Outlived the War", "Wouldn't Kneel", "Lost Everything",
            "Came Back Stronger", "Buried the Crown", "Chose Revenge", "Survived the Fire",
            "Refused the Throne", "Broke the Oath", "Hunted the Night", "Saved the Enemy"
        )
        val ofNouns = listOf(
            "Kingdom", "Crown", "War", "Mercy", "Empire", "House",
            "Blade", "Bloodline", "Promise", "Shadow", "Fire", "Dawn"
        )
        val ofQualifiers = listOf(
            "Second Chances", "Broken Vows", "Quiet Knives", "Black Wolves",
            "Burned Cities", "Last Chances", "Open Graves", "Midnight Fires",
            "Fallen Kings", "Empty Thrones", "Lost Sons", "Bleeding Crowns"
        )
        val returnTargets = listOf(
            "Lost Blade", "Last Heir", "Broken King", "Fallen Queen", "Black Wolf",
            "Dead Empire", "Final Hunter", "Burned Crown", "Quiet Prince", "Forgotten Sword"
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
                0 -> "${simpleSubjects[cursor % simpleSubjects.size]} ${simpleHooks[(cursor / 2) % simpleHooks.size]}"
                1 -> "No ${noValues[cursor % noValues.size]} for ${noTargets[(cursor / 3) % noTargets.size]}"
                2 -> "When ${whenSubjects[cursor % whenSubjects.size]} ${whenVerbs[(cursor / 4) % whenVerbs.size]}"
                3 -> "${beforeNouns[cursor % beforeNouns.size]} Before ${beforeTimes[(cursor / 5) % beforeTimes.size]}"
                4 -> "Heir to ${heirTargets[cursor % heirTargets.size]}"
                5 -> "A ${offerObjects[cursor % offerObjects.size]} for the ${offerTargets[(cursor / 4) % offerTargets.size]}"
                6 -> "The ${roles[cursor % roles.size]} Who ${roleActions[(cursor / 5) % roleActions.size]}"
                else -> "${ofNouns[cursor % ofNouns.size]} of ${ofQualifiers[(cursor / 6) % ofQualifiers.size]}"
            }

            if (usedTitles.add(candidate)) {
                titles += candidate
            }
            cursor += 1
        }

        var returnCursor = 0
        while (titles.size < count) {
            val candidate = "Return of the ${returnTargets[returnCursor % returnTargets.size]}"
            if (usedTitles.add(candidate)) {
                titles += candidate
            }
            returnCursor += 1
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
