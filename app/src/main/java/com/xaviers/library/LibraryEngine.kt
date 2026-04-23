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
                genre = book.genre,
                arcName = book.arcName,
                sceneSignature = book.sceneSignature,
                seedLine = "Seed ${book.canonAnchor.chapterSeed} • ${book.canonAnchor.omen}",
                isFocused = book.id == focusedId
            )
        }
    }

    fun homeSnapshot(): HomeSnapshot {
        val featured = currentBook()
        val genreOrder = books.map { it.genre }.distinct().take(6)
        return HomeSnapshot(
            heroTitle = featured.title,
            heroGenre = featured.genre,
            heroHook = featured.hookLine,
            continueLabel = "Continue • Episode ${featuredEpisode(featured)} of ${featured.episodeCount}",
            continueMeta = "${featured.runtimeMinutes} min listen • ${featured.arcName}",
            continueProgress = listeningProgressFor(featured),
            continueTimeLabel = "${minutesRemainingFor(featured)} min left",
            featuredGenres = genreOrder,
            forYou = railItemsFrom(activeBookIndex, listOf(featured.genre, "Romance", "LitRPG"), 8),
            trending = railItemsFrom((activeBookIndex + 7) % books.size, listOf("War Epic", "Thriller", "Dark Fantasy"), 8),
            freshDrops = railItemsFrom((activeBookIndex + 14) % books.size, listOf("Sci-Fi", "Horror", "Academy", "Urban Fantasy"), 8)
        )
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
            librarySubtitle = "${book.genre} • Episode ${featuredEpisode(book)}/${book.episodeCount} • Aether Ink $aetherInk • $collectionLine",
            stateOverline = "Featured Tonight // ${book.sceneSignature}",
            ritualHint = ritualHint,
            imageCaption = "Ep ${featuredEpisode(book)} • ${book.runtimeMinutes} min",
            buttonLabel = if (state == TomeState.UNLEASHED) {
                "Play Next Episode"
            } else {
                "Continue Listening"
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
        val genres = listOf(
            "Dark Fantasy",
            "LitRPG",
            "Romance",
            "War Epic",
            "Thriller",
            "Sci-Fi",
            "Horror",
            "Academy",
            "Apocalypse",
            "Urban Fantasy",
            "Court Intrigue"
        )
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
            val genre = genres[index % genres.size]
            LibraryBook(
                id = index,
                title = titles[index],
                genre = genre,
                hookLine = buildHookLine(titles[index], genre),
                episodeCount = 36 + (index % 19),
                runtimeMinutes = 18 + (index % 14),
                arcName = arcNames[index % arcNames.size],
                friendshipThread = friendships[index % friendships.size],
                enemyThread = enemies[(index + 2) % enemies.size],
                sceneSignature = scenes[(index + 1) % scenes.size],
                canonAnchor = anchor
            )
        }
    }

    private fun buildHookLine(title: String, genre: String): String {
        return when (genre) {
            "Dark Fantasy" -> "A ruthless oath, a wounded kingdom, and one name the dead still whisper."
            "LitRPG" -> "Levels climb fast when the world wants you erased before sunrise."
            "Romance" -> "Love arrives wearing the wrong face, and the wrong choice still feels right."
            "War Epic" -> "Every victory costs a friend, and every banner hides a betrayal."
            "Thriller" -> "One secret, one missing witness, and one night before everything collapses."
            "Sci-Fi" -> "Signals from the dark keep finding the one survivor who should not exist."
            "Horror" -> "The door opens only once, and this story begins after it should have stayed shut."
            "Academy" -> "Power, ranking, rivalry, and the class everyone thinks won't survive the term."
            "Apocalypse" -> "The world ended badly; surviving the people left behind is even worse."
            "Urban Fantasy" -> "Magic runs under the city after midnight, and the rent is always blood."
            "Court Intrigue" -> "The crown smiles in public and sharpens knives in private."
            else -> "$title bends fate before the first episode ends."
        }
    }

    private fun railItemsFrom(startIndex: Int, preferredGenres: List<String>, count: Int): List<HomeRailItem> {
        val preferred = books
            .asSequence()
            .drop(startIndex)
            .plus(books.asSequence().take(startIndex))
            .filter { preferredGenres.contains(it.genre) }
            .take(count)
            .toList()

        val fallback = books
            .asSequence()
            .drop((startIndex + 5) % books.size)
            .plus(books.asSequence().take((startIndex + 5) % books.size))
            .filter { book -> preferred.none { it.id == book.id } }
            .take((count - preferred.size).coerceAtLeast(0))
            .toList()

        return (preferred + fallback).take(count).map { book ->
            HomeRailItem(
                id = book.id,
                title = book.title,
                genre = book.genre,
                meta = "Ep ${featuredEpisode(book)} • ${book.runtimeMinutes} min",
                hookLine = book.hookLine
            )
        }
    }

    private fun featuredEpisode(book: LibraryBook): Int {
        return ((book.id + awakenCount) % book.episodeCount) + 1
    }

    private fun listeningProgressFor(book: LibraryBook): Int {
        return 18 + (((book.id * 13) + (awakenCount * 11)) % 71)
    }

    private fun minutesRemainingFor(book: LibraryBook): Int {
        val progress = listeningProgressFor(book)
        return ((100 - progress) * book.runtimeMinutes / 100).coerceAtLeast(4)
    }

    private fun buildUniqueTitles(count: Int): List<String> {
        val curatedBanks = listOf(
            listOf(
                "The Last Crown",
                "The Queen of a Broken House",
                "The King Beneath the Ashes",
                "The Night We Became Monsters",
                "Crown of the Unforgiven",
                "The Enemy Before Morning",
                "The House of Quiet Knives",
                "The Blade and the Betrayal",
                "The Crown They Couldn't Kill",
                "Where the Crown Still Bleeds",
                "The Last Oath Between Us",
                "A Throne for the Enemy"
            ),
            listOf(
                "My Last System",
                "The Ranker Who Wouldn't Kneel",
                "Leveling After the Kingdom Fell",
                "Return of the Cursed Ranker",
                "My Second Life as the Villain's Heir",
                "The Strongest After Betrayal",
                "Reborn for the Final War",
                "The Tutorial No One Survived",
                "System of the Fallen Heir",
                "The Hunter Who Came Back Empty",
                "The Strongest One Left Behind",
                "After the Tower Burned"
            ),
            listOf(
                "Dungeon Heart",
                "Level 99 After Betrayal",
                "The Last Raid Party",
                "My Guild Wants Me Dead",
                "The Final Boss Was My Friend",
                "Solo Tank",
                "S-Rank After the Fall",
                "The Healer Who Took Revenge",
                "Respawn After Midnight",
                "The Dungeon Master of Black Gate",
                "The Player Who Refused to Log Out",
                "Stats of the Last Survivor"
            ),
            listOf(
                "The Queen I Couldn't Save",
                "If We Survive the Dawn",
                "She Chose the Enemy",
                "The Enemy Who Saved Me",
                "I Took the Enemy's Hand",
                "The Prince I Was Meant to Hate",
                "The Last Friend I Kept",
                "A Promise We Shouldn't Have Made",
                "The One I Lost at Dawn",
                "Hearts Beneath the Crown",
                "If the Villain Stays",
                "The Girl Who Outlived the Kingdom"
            ),
            listOf(
                "Warriors Stand",
                "No Mercy for Kings",
                "Blood Before Sunrise",
                "Until the Kingdom Burns",
                "Brothers of the Black Dawn",
                "The Last War Between Brothers",
                "The Day the Saints Fell",
                "War for the Final Dawn",
                "A Crown Worth Killing For",
                "The Kingdom We Couldn't Save",
                "Dawn Belongs to the Damned",
                "The Last Soldier of Spring"
            ),
            listOf(
                "The Enemy Wore My Face",
                "The Enemy at My Table",
                "Seven Lies Before Dawn",
                "I Buried the Hero",
                "Before the Heroes Wake",
                "No One Leaves the Palace",
                "The Witness in Red",
                "Don't Trust the Crown",
                "The Secret Under Blackwater Hall",
                "Somebody Killed the Saint",
                "The Boy Who Remembered Too Much",
                "The Night Before the Throne"
            ),
            listOf(
                "Orbit of the Last City",
                "The Girl Who Hacked Tomorrow",
                "After Earth Went Silent",
                "Ashfall Protocol",
                "The Last Signal from Orion",
                "Machine Hearts at Midnight",
                "The City That Remembered Us",
                "Save Me Before the Suns Die",
                "Ghost Signal",
                "Zero Hour Colony",
                "The Edge of Tomorrow's War",
                "We Were Built for the End"
            ),
            listOf(
                "When the Dead Knock",
                "Don't Open the Ninth Door",
                "The Town That Eats the Night",
                "If You Hear Her Singing",
                "The Graveyard Behind My Name",
                "We Buried the Wrong Body",
                "The House That Hates Us",
                "The Devil Came Home Smiling",
                "The Church Beneath the Lake",
                "Someone Is Breathing in the Walls",
                "The Last Prayer in Blackwood",
                "No Sleep in Hollow Vale"
            ),
            listOf(
                "The Strongest Freshman",
                "I Failed the Hero Exam",
                "Academy of Broken Kings",
                "My Rival Is the Chosen One",
                "The Last Student of Nightfall Academy",
                "The Girl Who Ranked First in Death Magic",
                "Semester of the Damned",
                "The Class Nobody Survived",
                "The Transfer Student Killed the Hero",
                "I Was Expelled for Saving the Villain",
                "The Worst Genius in the Academy",
                "The Professor of Forbidden Skills"
            ),
            listOf(
                "If the Wolves Win",
                "The Day the World Split Open",
                "Last Train After the End",
                "We Buried the Sun",
                "The Last Shelter",
                "Survive Until Morning",
                "Cities After Fire",
                "The Last Road to Mercy",
                "Ashes After Eden",
                "The Last Broadcast",
                "Hunger in the Snow",
                "Earth After Day Zero"
            ),
            listOf(
                "Night Shift for Monsters",
                "My Rent Is Paid in Blood",
                "The Last Witch in the City",
                "The Demon on 8th Street",
                "Borrowed Magic, Stolen Hearts",
                "The Detective Who Talked to Spirits",
                "I Met Death on the Night Bus",
                "The Girl Who Sold Curses",
                "The Vampire Next Door Is My Enemy",
                "The Secret Market Under the Moon",
                "Tea Shop at the Edge of Magic",
                "The Library at the World's Edge"
            ),
            listOf(
                "The Heir Nobody Wanted",
                "A Crown for the Enemy",
                "The Queen Who Never Forgave",
                "The King I Had to Betray",
                "Bloodline of the Betrayed",
                "Every Throne Has a Price",
                "The Court of Silent Blades",
                "The Widow Who Ruled the War",
                "The Last Vote Before Blood",
                "The House That Refused to Fall",
                "The Prince Who Survived Nothing",
                "The Crown After the Fall"
            )
        )
        val systemSubjects = listOf(
            "Vampire", "Dragon", "Shadow", "Necromancer", "Villain", "Hunter",
            "Sword Saint", "Healer", "Demon Lord", "Last Hero", "Fallen Heir", "Ghost Blade",
            "Dungeon", "Raid", "Guild", "Player", "Tank", "S-Rank Hunter"
        )
        val systemHooks = listOf(
            "System", "Ranker", "Awakening", "Legacy", "Reckoning", "Return",
            "Build", "Class", "Quest", "Respawn"
        )
        val romanceRoles = listOf(
            "Enemy", "Prince", "Villain", "Queen", "King", "Monster",
            "Hunter", "Hero", "Heir", "Rival"
        )
        val romanceActions = listOf(
            "Couldn't Save", "Was Meant to Hate", "Shouldn't Have Loved", "Lost at Dawn",
            "Promised to Betray", "Couldn't Leave", "Came Back For", "Refused to Forget"
        )
        val warSubjects = listOf(
            "Warriors", "Wolves", "Kings", "Queens", "Outcasts", "Soldiers",
            "Brothers", "Sisters", "Hunters", "Saints"
        )
        val warHooks = listOf(
            "Stand", "Hold the Gate", "Choose War", "Rise at Dawn",
            "Refuse to Kneel", "Return in Fire", "Break the Crown", "Take the Throne"
        )
        val thrillerActions = listOf("Trust", "Open", "Follow", "Answer", "Keep", "Bury")
        val thrillerObjects = listOf(
            "Crown", "Letter", "Ninth Door", "Red Room", "Last Signal", "Black Book",
            "Name", "Witness", "Knife", "Bell"
        )
        val sciFiPlaces = listOf(
            "Orion", "Sector Nine", "Titan Station", "the Last City",
            "the Black Moon", "the Ninth Colony", "the Dead Sun", "Tomorrow"
        )
        val sciFiEvents = listOf("Went Silent", "Burned", "Broke", "Woke", "Vanished", "Returned")
        val sciFiSignals = listOf("Ghost Signal", "Ashfall Protocol", "Zero Hour Colony", "Black Moon Directive")
        val horrorPlaces = listOf(
            "House", "Church", "Town", "Lake", "Hall", "Forest",
            "Chapel", "Road", "Graveyard", "Basement"
        )
        val horrorHooks = listOf(
            "That Hates Us", "That Shouldn't Exist", "That Eats the Night", "Beneath the Lake",
            "After Midnight", "Where No One Sleeps", "Where the Dead Wait", "With No Exit"
        )
        val academyRanks = listOf(
            "Strongest", "Worst", "Last", "First", "Top", "Transfer", "Final", "Hidden"
        )
        val academyRoles = listOf(
            "Freshman", "Student", "Professor", "Ranker", "Prodigy", "Classmate", "Healer", "Mage"
        )
        val academyNames = listOf(
            "Nightfall Academy", "Blackthorne Academy", "Saint Vale Academy", "Ashborn Academy",
            "Red Moon Academy", "Last Crown Academy", "Frostgate Academy"
        )
        val apocalypseRoutes = listOf(
            "Train", "Road", "Signal", "Shelter", "Broadcast", "Map"
        )
        val apocalypseTargets = listOf(
            "the End", "Tomorrow", "Mercy", "the Last Coast", "the Safe Zone", "Dawn", "the Final City"
        )
        val apocalypseStates = listOf(
            "Day Zero", "the Fire", "the Fall", "the Black Winter", "the Sirens", "the Last Storm"
        )
        val urbanCities = listOf("Blackwater", "Ash City", "Red Harbor", "Night Vale", "Hollow Point", "Mooncross")
        val urbanThreats = listOf("the Devil", "the Underworld", "City Curses", "Ghosts", "the Vampire King", "Monsters")
        val urbanJobs = listOf("Night Shift for", "Five Days to Stop", "The Last Witch in", "My Deal With", "The Detective Who Hunted")
        val politicalRoles = listOf("Heir", "Queen", "Prince", "Widow", "House", "Court", "Empire", "Kingdom")
        val politicalHooks = listOf(
            "Quiet Knives", "Broken Vows", "Bleeding Crowns", "Black Banners",
            "Dead Princes", "Empty Thrones", "Ashen Oaths", "Last Chances"
        )

        val titles = mutableListOf<String>()
        val usedTitles = linkedSetOf<String>()
        fun addTitle(candidate: String) {
            if (titles.size < count && usedTitles.add(candidate)) {
                titles += candidate
            }
        }

        curatedBanks.forEach { bank ->
            bank.forEach { title ->
                addTitle(title)
            }
        }

        for (subject in systemSubjects) {
            for (hook in systemHooks) {
                addTitle("My $subject $hook")
            }
        }
        for (role in romanceRoles) {
            for (action in romanceActions) {
                addTitle("The $role I $action")
            }
        }
        for (subject in warSubjects) {
            for (hook in warHooks) {
                addTitle("$subject $hook")
            }
        }
        for (action in thrillerActions) {
            for (obj in thrillerObjects) {
                addTitle("Don't $action the $obj")
            }
        }
        for (place in sciFiPlaces) {
            for (event in sciFiEvents) {
                addTitle("After $place $event")
            }
            addTitle("$place at the End of Time")
        }
        for (signal in sciFiSignals) {
            for (place in sciFiPlaces) {
                addTitle("$signal from $place")
            }
        }
        for (place in horrorPlaces) {
            for (hook in horrorHooks) {
                addTitle("The $place $hook")
            }
        }
        for (rank in academyRanks) {
            for (role in academyRoles) {
                for (academy in academyNames) {
                    addTitle("The $rank $role of $academy")
                }
            }
        }
        for (route in apocalypseRoutes) {
            for (target in apocalypseTargets) {
                addTitle("The Last $route to $target")
            }
        }
        apocalypseTargets.forEach { target ->
            addTitle("Survive Until $target")
        }
        apocalypseStates.forEach { state ->
            addTitle("After $state")
        }
        urbanThreats.forEach { threat ->
            addTitle("${urbanJobs[0]} $threat")
            addTitle("${urbanJobs[1]} $threat")
            addTitle("${urbanJobs[3]} $threat")
            addTitle("${urbanJobs[4]} $threat")
        }
        urbanCities.forEach { city ->
            addTitle("${urbanJobs[2]} $city")
        }
        for (role in politicalRoles) {
            for (hook in politicalHooks) {
                addTitle("The $role of $hook")
            }
        }

        var fallback = 1
        while (titles.size < count) {
            addTitle("Xavier's Library Chronicle $fallback")
            fallback += 1
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
