package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream

/**
 * Saves written before the renames (see docs/fidelity/names.md). They hold the old names: backstory titles, thought labels,
 * an incident name and the temporary event name. Reading them must give the current names. Graveyard entries and log lines are kept
 * exactly as they were saved (names.md section 2). Saving again must write the current version (32).
 *
 * The old names are test data, so they are not written here. They are in sim/src/test/resources/legacy/old_text_cases.tsv
 * (columns: id, since, kind, old text, new text; "since" is the version whose rename the row belongs to), and the fixtures in
 * the same folder hold the old save data:
 *  - colony_v25_head.sav: version 25, written by the code at git HEAD, which is the version-25 code;
 *  - colony_v25.sav and colony_v25_thoughts.sav: version 25 (colony_v25_thoughts.sav came from a generator outside the
 *    repository, see names.md);
 *  - colony_v26.sav: version 26, written by the current layout with the header set to 26 and the names version 26 held;
 *  - colony_v27.sav: version 27, written by the version-27 code before the version-28 renames;
 *  - colony_v28.sav: version 28, written by the version-28 code (before the version-29 renames) by a temporary generator, whose
 *    expectation file lists the labels the version-28 code wrote.
 *  - colony_v29.sav: version 29, written by the version-29 code before the version-30 renames, by a temporary generator (Game(4242L),
 *    startNewColony(QUIET_ENCLAVE), five colonists with the version-29 labels, a temporary event and two incidents). Its
 *    expectation file lists what it wrote.
 *  - colony_v30.sav: version 30, written by the version-30 code (the working tree before the version-31 change) by a temporary
 *    generator (Game(4242L), startNewColony(QUIET_ENCLAVE)). It holds version-30 labels, a graveyard with the version-30 causes,
 *    and two incidents. Its expectation file lists what it wrote.
 *  - colony_v25_graves.sav: version 25, written by the code at git HEAD by a temporary generator (Game(4242L),
 *    startNewColony(the scenario that is now QUIET_ENCLAVE)). Its graveyard holds the version-25 death causes, which are read
 *    unchanged. Its expectation file
 *    lists what it wrote.
 *  - colony_v25_adults.sav: version 25, written with the current layout and the header set to 25 by a temporary generator
 *    (Game(4242L), startNewColony(QUIET_ENCLAVE)). Its two adult titles are the lower-case forms that HEAD writes (T14 and T13).
 *  - colony_v27_sites.sav: version 27, written with the current layout and the header set to 27 by a temporary generator
 *    (Game(4242L), startNewColony(QUIET_ENCLAVE)). It holds a pirate camp, a pending fight against it and a caravan whose last
 *    event names it. Its expectation file lists what it wrote.
 *  - colony_v24.sav (tracked, read by LegacySaveMigrationTest) holds a lower-case adult title (T13) and is read here too.
 */
class LegacyNamesMigrationTest {
    private val fixtureVersion = 25

    private fun resource(name: String): ByteArray =
        javaClass.getResourceAsStream("/legacy/$name")?.use { it.readBytes() } ?: error("missing test resource legacy/$name")

    private fun expectations(name: String): Map<String, String> =
        resource(name).decodeToString().lines().filter { it.contains('=') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }

    private fun thoughtsOf(value: String?): List<String> = value.orEmpty().split("|").filter { it.isNotEmpty() }

    private val fixture = resource("colony_v25.sav")
    private val expect = expectations("colony_v25_expect.txt")

    private val thoughtsFixture = resource("colony_v25_thoughts.sav")
    private val thoughtsExpect = expectations("colony_v25_thoughts_expect.txt")

    private val headV25 = resource("colony_v25_head.sav")
    private val headExpect = expectations("colony_v25_head_expect.txt")

    private val v26 = resource("colony_v26.sav")
    private val v26Expect = expectations("colony_v26_expect.txt")

    private val v27 = resource("colony_v27.sav")
    private val v27Expect = expectations("colony_v27_expect.txt")

    private val v28 = resource("colony_v28.sav")
    private val v28Expect = expectations("colony_v28_expect.txt")

    private val v29 = resource("colony_v29.sav")
    private val v29Expect = expectations("colony_v29_expect.txt")

    private val v30 = resource("colony_v30.sav")
    private val v30Expect = expectations("colony_v30_expect.txt")

    private val gravesV25 = resource("colony_v25_graves.sav")
    private val gravesExpect = expectations("colony_v25_graves_expect.txt")

    private val adultsV25 = resource("colony_v25_adults.sav")
    private val adultsV25Expect = expectations("colony_v25_adults_expect.txt")

    private val sitesV27 = resource("colony_v27_sites.sav")
    private val sitesV27Expect = expectations("colony_v27_sites_expect.txt")

    private val v24 = resource("colony_v24.sav")

    private class Case(val since: Int, val kind: String, val old: String, val new: String)

    private val cases: List<Case> = resource("old_text_cases.tsv").decodeToString()
        .lines().drop(1).filter { it.isNotBlank() }.map { it.split('\t') }
        .map { Case(it[1].toInt(), it[2], it[3], it[4]) }

    private fun pairs(kind: String): List<Pair<String, String>> = cases.filter { it.kind == kind }.map { it.old to it.new }

    /** The old text of the first case-file row with this kind, rename version and new text (old names stay in the case file). */
    private fun oldTextOf(kind: String, since: Int, new: String): String =
        cases.first { it.kind == kind && it.since == since && it.new == new }.old

    /**
     * The lower-case form of the old adult title that [new] replaced (T12, T17, T19, T20, T21). [new] is matched without case, so a
     * capitalised name finds the row whose new text is lower case. Old names stay in the case file.
     */
    private fun lowerCaseOldTextOf(new: String): String =
        cases.single { it.kind == "title" && it.new.equals(new, ignoreCase = true) && it.old == it.old.lowercase() }.old

    private fun versionOf(bytes: ByteArray): Int = DataInputStream(ByteArrayInputStream(bytes)).readInt()

    /** Every pawn a loaded game holds: the pawns on the map, battle members, captives, caravan members and a pending battle's snapshot. */
    private fun everyPawnIn(g: Game): List<Pawn> =
        g.pawns + g.battleMembers + g.world.captives.map { it.pawn } + g.caravans.flatMap { it.members } + (g.parent?.let { everyPawnIn(it) } ?: emptyList())

    @Test fun theFixturesAreReallyTheirVersions() {
        assertEquals(fixtureVersion, versionOf(fixture))
        assertEquals(fixtureVersion, versionOf(thoughtsFixture))
        assertEquals(26, versionOf(v26))
        assertEquals(27, versionOf(v27))
        assertEquals(28, versionOf(v28))
        assertEquals(29, versionOf(v29))
        assertEquals(30, versionOf(v30))
        assertEquals(25, versionOf(gravesV25))
        assertEquals(25, versionOf(adultsV25))
        assertEquals(27, versionOf(sitesV27))
        assertEquals(24, versionOf(v24))
    }

    @Test fun anOldSaveReadsTheNewBackstoryTitlesAndKeepsTheUnchangedOnes() {
        val g = SaveGame.read(fixture)
        assertEquals(expect.getValue("colonists").toInt(), g.colonists.size)
        assertEquals(
            listOf(
                "Cradle-line recruit, palisade watch",
                "Street kid, cook",
                "Camp foundling, scholar",
                "Ringside tyke, surgeon",
                "Hull scrapper, trader",
                "Hold-born ward, miner",
            ),
            g.colonists.map { it.backstory },
        )
    }

    @Test fun theExpectationFileGivesTheSameTitlesThroughTheTranslation() {
        val g = SaveGame.read(fixture)
        g.colonists.forEachIndexed { i, p ->
            assertEquals("colonist $i", LegacyNames.backstory(expect.getValue("backstory.$i"), fixtureVersion), p.backstory)
        }
    }

    @Test fun savingAnOldGameWritesTheCurrentVersionAndTheTitlesSurviveARoundTrip() {
        val g = SaveGame.read(fixture)
        val bytes = SaveGame.write(g)
        assertEquals(34, versionOf(bytes))
        assertEquals(g.colonists.map { it.backstory }, SaveGame.read(bytes).colonists.map { it.backstory })
    }

    @Test fun everySavedBackstoryInTheCaseFileIsTranslatedPartByPart() {
        for ((old, new) in pairs("backstory")) assertEquals(new, LegacyNames.backstory(old, fixtureVersion))
    }

    @Test fun everyOldTitleInTheCaseFileIsTranslatedAndOtherTitlesAreNot() {
        for ((old, new) in pairs("title")) assertEquals(new, LegacyNames.backstory(old, fixtureVersion))
    }

    @Test fun everyOldThoughtLabelInTheCaseFileIsTranslatedAndOthersAreNot() {
        for ((old, new) in pairs("thought")) assertEquals(new, LegacyNames.thoughtLabel(old, fixtureVersion))
    }

    @Test fun theOldIncidentNameIsTranslatedAndOthersAreNot() {
        for ((old, new) in pairs("incident")) assertEquals(new, LegacyNames.incidentName(old, fixtureVersion))
    }

    @Test fun aVersion25SaveWrittenByTheHeadCodeReadsEveryRenameSinceVersion25() {
        // colony_v25_head.sav was written by the code at git HEAD (version 25), so it is a fixture of that code. It holds
        // the version-25 names; every rename made since is applied when it is read.
        val g = SaveGame.read(headV25)
        assertEquals(headExpect.getValue("colonists").toInt(), g.colonists.size)
        assertEquals(
            listOf(
                "Hold-born ward, carpenter",
                "Ringside tyke, surgeon",
                "Ringside tyke, warden",
                "Hold-born ward, roadwalker",
                "Cradle-line recruit, palisade watch",
            ),
            g.colonists.map { it.backstory },
        )
        assertEquals(
            listOf(
                listOf("Lucid high"),
                listOf("Pale dust high", "White rush high"),
                listOf("White rush high", "Drug use"),
                listOf("Mind hum", "Ate chitin cuts"),
                listOf("Sunny outlook", "Ate a roasted platter"),
            ),
            g.colonists.map { p -> p.thoughts.map { it.label } },
        )
        assertEquals(g.tick - 100L, g.incidentLast[Incident.SKYHORN_VISIT])
        assertEquals(g.tick - 200L, g.incidentLast[Incident.ANIMAL_JOINS])
    }

    @Test fun aVersion26FixtureReadsTheRenamesOfVersions27AndTwentySeven() {
        // Version 26 held the version-26 names. Names renamed in version 26 are already new in the file; names renamed in
        // version 27 and version 28 are still the older text, and they are translated when the file is read.
        val g = SaveGame.read(v26)
        assertEquals(v26Expect.getValue("colonists").toInt(), g.colonists.size)
        assertEquals(
            listOf(
                "Cradle-line recruit, Palisade watch",
                "Furrow-born, Palisade watch",
                "Street kid, cook",
                "Pasture-born, scholar",
                "Furrow-born, hunter",
            ),
            g.colonists.map { it.backstory },
        )
        assertEquals(
            listOf(
                listOf("Lucid high"),
                listOf("Ate a roasted platter", "Hushed calm"),
                listOf("Pale dust high", "Ate chitin cuts"),
                listOf("Mind hum", "White rush high", "Drug use"),
                emptyList(),
            ),
            g.colonists.map { p -> p.thoughts.map { it.label } },
        )
        assertEquals(g.tick - 300L, g.incidentLast[Incident.SKYHORN_VISIT])
    }

    @Test fun theVersion26ExpectationFileGivesTheSameLabelsThroughTheTranslation() {
        val g = SaveGame.read(v26)
        g.colonists.forEachIndexed { i, p ->
            assertEquals("colonist $i title", LegacyNames.backstory(v26Expect.getValue("backstory.$i"), 26), p.backstory)
            assertEquals(
                "colonist $i thoughts",
                thoughtsOf(v26Expect["thoughts.$i"]).map { LegacyNames.thoughtLabel(it, 26) },
                p.thoughts.map { it.label },
            )
        }
    }

    @Test fun aVersion27FixtureKeepsItsTitlesAndTranslatesOnlyTheVersion28Thoughts() {
        val g = SaveGame.read(v27)
        assertEquals(v27Expect.getValue("colonists").toInt(), g.colonists.size)
        // Titles written by version 27 are translated only where a rename came later: version 31 renamed three titles.
        g.colonists.forEachIndexed { i, p ->
            assertEquals("colonist $i title", LegacyNames.backstory(v27Expect.getValue("backstory.$i"), 27), p.backstory)
        }
        assertEquals(
            listOf(
                listOf("Ate chitin cuts"),
                listOf("Ate manflesh", "Ate manflesh (loved it)"),
                listOf("Full belly", "Refit pride"),
                listOf("Sunny outlook", "Grey outlook"),
                emptyList(),
            ),
            g.colonists.map { p -> p.thoughts.map { it.label } },
        )
        assertEquals(g.tick - 300L, g.incidentLast[Incident.SKYHORN_VISIT])
    }

    @Test fun aVersion28FixtureReadsItsThoughtsUnderTheVersion29Labels() {
        // Version 28 held the version-28 labels. The labels renamed in version 29 are translated when the file is read.
        val g = SaveGame.read(v28)
        assertEquals(v28Expect.getValue("colonists").toInt(), g.colonists.size)
        g.colonists.forEachIndexed { i, p ->
            assertEquals("colonist $i title", LegacyNames.backstory(v28Expect.getValue("backstory.$i"), 28), p.backstory)
        }
        assertEquals(
            listOf(
                listOf("Gulped raw fare", "Brew haze"),
                listOf("Floor meal", "Craving ache", "Lucid high"),
                listOf("Warm talk", "Hushed calm"),
                listOf("Sky curtain glow", "Drab rooms", "Ate chitin cuts"),
                listOf("Restless mind", "Ate manflesh (loved it)", "Sunny outlook"),
            ),
            g.colonists.map { p -> p.thoughts.map { it.label } },
        )
        assertEquals(g.tick - 300L, g.incidentLast[Incident.SKYHORN_VISIT])
        assertEquals(g.tick - 400L, g.incidentLast[Incident.ANIMAL_JOINS])
    }

    @Test fun theVersion28ExpectationFileGivesTheSameLabelsThroughTheTranslation() {
        val g = SaveGame.read(v28)
        g.colonists.forEachIndexed { i, p ->
            assertEquals(
                "colonist $i thoughts",
                thoughtsOf(v28Expect["thoughts.$i"]).map { LegacyNames.thoughtLabel(it, 28) },
                p.thoughts.map { it.label },
            )
        }
    }

    @Test fun everyThoughtRenamedInVersion29IsTranslatedFromAVersion28Save() {
        for (c in cases.filter { it.since == 29 && it.kind == "thought" }) assertEquals(c.new, LegacyNames.thoughtLabel(c.old, 28))
    }

    @Test fun thoughtRenamesOfVersion29DoNotTouchVersion29Saves() {
        for (c in cases.filter { it.since == 29 && it.kind == "thought" }) assertEquals(c.old, LegacyNames.thoughtLabel(c.old, 29))
    }

    @Test fun everyTemporaryEventNameRenamedInVersion29IsTranslatedFromAnOlderSave() {
        val rows = cases.filter { it.since == 29 && it.kind == "temp" }
        assertEquals(3, rows.size)
        for (c in rows) {
            assertEquals(c.new, LegacyNames.tempEventName(c.old, 28))
            assertEquals(c.old, LegacyNames.tempEventName(c.old, 29))
        }
    }

    @Test fun everyThoughtRenamedInVersion28IsTranslatedFromAVersion27Save() {
        for ((old, new) in cases.filter { it.since == 28 && it.kind == "thought" }.map { it.old to it.new }) {
            assertEquals(new, LegacyNames.thoughtLabel(old, 27))
        }
    }

    @Test fun thoughtRenamesOfVersion28DoNotTouchVersion28Saves() {
        for (c in cases.filter { it.since == 28 && it.kind == "thought" }) assertEquals(c.old, LegacyNames.thoughtLabel(c.old, 28))
    }

    @Test fun oldThoughtLabelsInAnOldSaveAreTranslatedWhenItIsRead() {
        val g = SaveGame.read(thoughtsFixture)
        assertEquals(
            listOf(listOf("Lucid high"), listOf("Pale dust high"), listOf("White rush high", "Drug use")),
            g.colonists.take(3).map { p -> p.thoughts.map { it.label } },
        )
    }

    @Test fun theThoughtsExpectationFileGivesTheSameLabelsThroughTheTranslation() {
        val g = SaveGame.read(thoughtsFixture)
        g.colonists.take(3).forEachIndexed { i, p ->
            val saved = thoughtsExpect.getValue("thoughts.$i").split("|")
            assertEquals("colonist $i", saved.map { LegacyNames.thoughtLabel(it, fixtureVersion) }, p.thoughts.map { it.label })
        }
    }

    @Test fun theOldIncidentIsReadUnderItsNewNameWithItsTickAndTheOtherIncidentIsUnchanged() {
        val g = SaveGame.read(thoughtsFixture)
        val incidentLines = thoughtsExpect.filterKeys { it.startsWith("incident.") }
        assertEquals(incidentLines.size, g.incidentLast.size)
        for ((key, tick) in incidentLines) {
            val name = LegacyNames.incidentName(key.removePrefix("incident."), fixtureVersion)
            assertEquals("incident $key", tick.toLong(), g.incidentLast[Incident.valueOf(name)])
        }
    }

    @Test fun aSaveWrittenNowKeepsItsTextAndTheNewIncidentName() {
        // The translation runs only for saves below version 28, so text that looks old in a current save is left alone.
        val g = SaveGame.read(thoughtsFixture)
        val oldLabel = pairs("thought").first { it.second == "Lucid high" }.first
        g.colonists[0].addThought(oldLabel, 0.1f, g.tick, 1000)
        val bytes = SaveGame.write(g)
        assertEquals(34, versionOf(bytes))
        val back = SaveGame.read(bytes)
        assertEquals(listOf("Lucid high", oldLabel), back.colonists[0].thoughts.map { it.label })
        assertEquals(g.incidentLast[Incident.SKYHORN_VISIT], back.incidentLast[Incident.SKYHORN_VISIT])
    }

    @Test fun aSaveWrittenNowKeepsItsTitleAndGraveyardText() {
        // Version 31 saves hold the current titles and causes. Text that looks old in a current save is left alone.
        val oldTitle = oldTextOf("title", 31, "Battlefield bandager")
        val oldCause = oldTextOf("cause", 31, "chest cough")
        val g = SaveGame.read(thoughtsFixture)
        g.colonists[0].backstory = "Street kid, $oldTitle"
        g.graveyard.add("Ada Vance - $oldCause (day 3)")
        val back = SaveGame.read(SaveGame.write(g))
        assertEquals("Street kid, $oldTitle", back.colonists[0].backstory)
        assertEquals(g.graveyard, back.graveyard)
        assertEquals("Ada Vance - $oldCause (day 3)", back.graveyard.last())
    }

    @Test fun aVersion29FixtureReadsItsLabelsAndTemporaryEventUnderTheVersion30Names() {
        // Version 29 held the version-29 labels. The labels renamed in version 30 are translated when the file is read.
        val g = SaveGame.read(v29)
        assertEquals(v29Expect.getValue("colonists").toInt(), g.colonists.size)
        assertEquals(
            listOf(
                listOf("Full belly", "Brew haze"),
                listOf("Refit pride", "Candid exchange"),
                listOf("Friendly ribbing", "Restless mind"),
                listOf("Gulped raw fare", "Ate chitin cuts"),
                listOf("Sunny outlook"),
            ),
            g.colonists.map { p -> p.thoughts.map { it.label } },
        )
        assertEquals("smoke pall", g.tempEventName)
        assertEquals(g.tick - 300L, g.incidentLast[Incident.SKYHORN_VISIT])
        assertEquals(g.tick - 400L, g.incidentLast[Incident.ANIMAL_JOINS])
    }

    @Test fun theVersion29ExpectationFileGivesTheSameLabelsThroughTheTranslation() {
        val g = SaveGame.read(v29)
        g.colonists.forEachIndexed { i, p ->
            assertEquals(
                "colonist $i thoughts",
                thoughtsOf(v29Expect["thoughts.$i"]).map { LegacyNames.thoughtLabel(it, 29) },
                p.thoughts.map { it.label },
            )
        }
        assertEquals(LegacyNames.tempEventName(v29Expect.getValue("temp"), 29), g.tempEventName)
    }

    @Test fun everyThoughtRenamedInVersion30IsTranslatedFromAVersion29Save() {
        for (c in cases.filter { it.since == 30 && it.kind == "thought" }) assertEquals(c.new, LegacyNames.thoughtLabel(c.old, 29))
    }

    @Test fun thoughtRenamesOfVersion30DoNotTouchVersion30Saves() {
        for (c in cases.filter { it.since == 30 && it.kind == "thought" }) assertEquals(c.old, LegacyNames.thoughtLabel(c.old, 30))
    }

    @Test fun theTemporaryEventRenamedInVersion30IsTranslatedFromAVersion29Save() {
        val rows = cases.filter { it.since == 30 && it.kind == "temp" }
        assertEquals(1, rows.size)
        for (c in rows) {
            assertEquals(c.new, LegacyNames.tempEventName(c.old, 29))
            assertEquals(c.old, LegacyNames.tempEventName(c.old, 30))
        }
    }

    @Test fun everyTitleRenamedInVersion31IsTranslatedFromAVersion30SaveAndNotFromAVersion31Save() {
        val rows = cases.filter { it.since == 31 && it.kind == "title" }
        assertEquals(5, rows.size)
        for (c in rows) {
            assertEquals(c.new, LegacyNames.backstory(c.old, 30))
            assertEquals(c.old, LegacyNames.backstory(c.old, 31))
        }
    }

    @Test fun aVersion28TitleIsTranslatedToItsVersion31NameInAPairedAdult() {
        assertEquals(
            "Pasture-born, Battlefield bandager",
            LegacyNames.backstory("Pasture-born, ${oldTextOf("title", 31, "Battlefield bandager")}", 28),
        )
        assertEquals("Hull scrapper, Roadwalker", LegacyNames.backstory("Hull scrapper, ${oldTextOf("title", 31, "Roadwalker")}", 27))
    }

    @Test fun aVersion30FixtureReadsItsTitlesThroughTheVersion31TranslationAndKeepsItsLabels() {
        // Version 30 held the version-30 labels and the lower-case adult titles that HEAD wrote. The titles renamed in version 31 are
        // translated when the file is read; the labels and the temporary event are unchanged.
        val g = SaveGame.read(v30)
        assertEquals(v30Expect.getValue("colonists").toInt(), g.colonists.size)
        g.colonists.forEachIndexed { i, p ->
            assertEquals("colonist $i title", LegacyNames.backstory(v30Expect.getValue("backstory.$i"), 30), p.backstory)
            assertEquals("colonist $i thoughts", thoughtsOf(v30Expect["thoughts.$i"]), p.thoughts.map { it.label })
        }
        assertEquals("Infirmary helper, battlefield bandager", g.colonists[3].backstory)
        assertEquals("smoke pall", g.tempEventName)
        assertEquals(v30Expect.getValue("incident.SKYHORN_VISIT").toLong(), g.incidentLast[Incident.SKYHORN_VISIT])
        assertEquals(v30Expect.getValue("incident.ANIMAL_JOINS").toLong(), g.incidentLast[Incident.ANIMAL_JOINS])
    }

    @Test fun aVersion30GraveyardIsReadWithItsCurrentCausesUnchanged() {
        val g = SaveGame.read(v30)
        assertEquals(
            listOf(
                "Ada Vance - exposure (day 4)",
                "Bo Fenn - overheating (day 9)",
                "Cy Marsh - bleed-out (day 12)",
                "Dee Holt - lost in captivity (day 15)",
            ),
            g.graveyard,
        )
    }

    @Test fun aVersion30SaveWrittenAgainIsVersion31AndKeepsItsText() {
        val g = SaveGame.read(v30)
        val bytes = SaveGame.write(g)
        assertEquals(34, versionOf(bytes))
        val back = SaveGame.read(bytes)
        assertEquals(g.graveyard, back.graveyard)
        assertEquals(g.colonists.map { it.backstory }, back.colonists.map { it.backstory })
        assertEquals(g.colonists.map { p -> p.thoughts.map { it.label } }, back.colonists.map { p -> p.thoughts.map { it.label } })
    }

    @Test fun aVersion25GraveyardKeepsEveryDeathCauseAsItWasSaved() {
        // colony_v25_graves.sav was written by the code at git HEAD, with the version-25 death causes in its graveyard. Graveyard
        // entries are never translated (names.md section 2), so each entry loads exactly as its expectation line gives it.
        val g = SaveGame.read(gravesV25)
        assertEquals(gravesExpect.getValue("colonists").toInt(), g.colonists.size)
        assertEquals((0..10).map { gravesExpect.getValue("graveyard.$it") }, g.graveyard)
    }

    @Test fun everyOldDeathCauseInTheCaseFileIsInTheVersion25GraveyardAsSaved() {
        // Each row of the case file is the cause text that a save held before version 31. Every one of them is read unchanged.
        val rows = cases.filter { it.since == 31 && it.kind == "cause" }
        assertEquals(9, rows.size)
        val g = SaveGame.read(gravesV25)
        for (c in rows) {
            assertTrue("${c.old} is kept as saved", g.graveyard.any { it.contains(" - ${c.old} (day ") })
            assertTrue("${c.new} is not written into an old entry", g.graveyard.none { it.contains(" - ${c.new} (day ") })
        }
    }

    @Test fun anOldGraveyardEntryInAVersion30SaveLoadsUnchanged() {
        // A version 30 save is older than the graveyard decision, so this is the path an old entry takes. Every old cause, and a name
        // with a dash in it, comes back exactly as it was written.
        val rows = cases.filter { it.since == 31 && it.kind == "cause" }
        val g = SaveGame.read(thoughtsFixture)
        g.graveyard.clear()
        for (c in rows) g.graveyard.add("Ada Vance - ${c.old} (day 4)")
        g.graveyard.add("Ada - Bo - ${oldTextOf("cause", 31, "chest cough")} (day 1)")
        g.graveyard.add("Ed Rook - starvation (day 2)")
        val bytes = SaveGame.write(g, 30)
        assertEquals(30, versionOf(bytes))
        assertEquals(g.graveyard, SaveGame.read(bytes).graveyard)
    }

    @Test fun anOldGraveyardStringStillLoadsUnchangedWhenItIsSavedNow() {
        // The current writer and reader, with no version change: an entry that holds an old cause reads back exactly as it was
        // written, and the current cause text is not substituted for it (names.md section 2, graveyard decision).
        val oldEntry = "Ada Vance - ${oldTextOf("cause", 31, "exposure")} (day 4)"
        val g = SaveGame.read(thoughtsFixture)
        g.graveyard.clear()
        g.graveyard.add(oldEntry)
        val back = SaveGame.read(SaveGame.write(g))
        assertEquals(listOf(oldEntry), back.graveyard)
        assertTrue(back.graveyard.single().contains(" - ${oldTextOf("cause", 31, "exposure")} (day "))
    }

    @Test fun anOldLogLineIsKeptAsSavedAndNotTranslated() {
        // Log lines are a record of past events, like graveyard entries (names.md section 2). A line that names an old label reads back
        // exactly as it was saved, in a version 30 save. The old labels come from the case file, so none is written in code.
        val rows = cases.filter { it.kind == "temp" }
        assertEquals(4, rows.size)
        val g = SaveGame.read(thoughtsFixture)
        g.log.clear()
        val lines = rows.mapIndexed { i, c -> "Day ${i + 1}: a ${c.old} began." }
        lines.forEachIndexed { i, text -> g.log.add(LogEntry(g.tick + i, text, 0)) }
        val bytes = SaveGame.write(g, 30)
        assertEquals(30, versionOf(bytes))
        assertEquals(lines, SaveGame.read(bytes).log.map { it.text })
    }

    @Test fun aVersion24SaveHoldingALowerCaseAdultTitleReadsTheNewAdultTitle() {
        // colony_v24.sav is version 24. It holds two saved backstories: one with the old childhood title T02 and the lower-case form of the
        // old adult title T13 (T20), and one with the old childhood title T04 and the lower-case form of the old adult title T11 (T12).
        // Every title is translated. The check covers every pawn the save holds, not only the pawns in g.pawns.
        val backstories = everyPawnIn(SaveGame.read(v24)).map { it.backstory }
        assertTrue(backstories.contains("Street kid, stockman"))
        assertTrue(backstories.contains("Ringside tyke, palisade watch"))
        assertTrue(backstories.none { it.contains(lowerCaseOldTextOf("Stockman")) })
        assertTrue(backstories.none { it.contains(lowerCaseOldTextOf("Palisade watch")) })
    }

    @Test fun aVersion25SaveHoldingTheLowerCaseAdultTitlesReadsTheNewTitles() {
        // colony_v25_adults.sav is version 25. Its adult titles are the lower-case forms HEAD writes, for T14 and T13 (T21 and T20 in the
        // case file). Each is translated, as the expectation file gives it, and the childhood titles are translated too.
        val g = SaveGame.read(adultsV25)
        assertEquals(adultsV25Expect.getValue("colonists").toInt(), g.colonists.size)
        assertEquals("Hull scrapper, sloop hand", g.colonists[0].backstory)
        assertEquals("Camp foundling, stockman", g.colonists[1].backstory)
        assertEquals(LegacyNames.backstory(adultsV25Expect.getValue("backstory.0"), 25), g.colonists[0].backstory)
        assertEquals(LegacyNames.backstory(adultsV25Expect.getValue("backstory.1"), 25), g.colonists[1].backstory)
    }

    @Test fun everyLowerCaseAdultTitleIsTranslatedFromItsVersionAndNotFromLater() {
        // HEAD wrote every adult title in lower case, so each renamed adult title has a lower-case form that a save holds (names.md
        // section 3): T12, T17, T19, T20 and T21. Each one is translated below the version of its rename and kept from that version on.
        val rows = cases.filter { it.kind == "title" && it.old == it.old.lowercase() }
        assertEquals(5, rows.size)
        for (c in rows) {
            assertEquals(c.new, LegacyNames.backstory("Hull scrapper, ${c.old}", c.since - 1).substringAfter(", "))
            assertEquals(c.old, LegacyNames.backstory("Hull scrapper, ${c.old}", c.since).substringAfter(", "))
        }
    }

    @Test fun aMigratedLowerCaseAdultTitleIsTheTextTheCurrentCodeWritesForAnAdult() {
        // Game.kt writes an adult title as Backstories gives it, in lower case. A translated lower-case adult title must be that same
        // text, so a colonist from an old save and a new colonist with the same title carry the same backstory form.
        val rows = cases.filter { it.kind == "title" && it.old == it.old.lowercase() }
        assertEquals(5, rows.size)
        for (c in rows) {
            val translated = LegacyNames.backstory("Hull scrapper, ${c.old}", c.since - 1).substringAfter(", ")
            assertEquals(c.new, translated)
            assertTrue("${c.new} is not a lower-case adult title", Backstories.adulthood.any { it.title.lowercase() == translated })
        }
    }

    @Test fun aVersion27SaveWithAPirateCampReadsTheCampAndTheFightUnderTheNewNames() {
        // colony_v27_sites.sav is version 27. Its camp (a site) and the label of its pending fight carry the old pirate faction name, and
        // both are translated. The caravan's last event is a record of a past battle, so it keeps the text it was saved with.
        val g = SaveGame.read(sitesV27)
        assertEquals(sitesV27Expect.getValue("sites").toInt(), g.world.sites.size)
        assertEquals(LegacyNames.siteName(sitesV27Expect.getValue("site.0"), 27), g.world.sites.single().name)
        assertEquals("Rough-Blade gang camp", g.world.sites.single().name)
        assertEquals(LegacyNames.battleLabel(sitesV27Expect.getValue("battle"), 27), g.pendingBattle!!.label)
        assertEquals("the Rough-Blade gang camp", g.pendingBattle!!.label)
        assertEquals(sitesV27Expect.getValue("lastEvent"), g.caravans.single().lastEvent)
    }

    @Test fun aVersion27SaveWrittenAgainKeepsTheCampTextsAndTheLastEvent() {
        // A translated name is written as the current text, and it is not translated a second time.
        val g = SaveGame.read(sitesV27)
        val back = SaveGame.read(SaveGame.write(g))
        assertEquals("Rough-Blade gang camp", back.world.sites.single().name)
        assertEquals("the Rough-Blade gang camp", back.pendingBattle!!.label)
        assertEquals(sitesV27Expect.getValue("lastEvent"), back.caravans.single().lastEvent)
    }

    @Test fun everyPirateCampTextRenamedInVersion28IsTranslatedFromAVersion27SaveAndNotFromAVersion28() {
        val sites = cases.filter { it.since == 28 && it.kind == "site" }
        val battles = cases.filter { it.since == 28 && it.kind == "battle" }
        assertEquals(1, sites.size)
        assertEquals(2, battles.size)
        for (c in sites) {
            assertEquals(c.new, LegacyNames.siteName(c.old, 27))
            assertEquals(c.old, LegacyNames.siteName(c.old, 28))
        }
        for (c in battles) {
            assertEquals(c.new, LegacyNames.battleLabel(c.old, 27))
            assertEquals(c.old, LegacyNames.battleLabel(c.old, 28))
        }
    }
}
