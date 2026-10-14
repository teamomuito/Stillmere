package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A calm colony where only the timers a test sets can fire. */
private fun calmGame(seed: Long): Game {
    val g = Game(seed)
    g.startNewColony(Scenario.QUIET_ENCLAVE)
    g.mentalBreaksEnabled = false
    g.pawns.removeAll { it.isAnimal && it.faction == Faction.WILD }
    val never = Long.MAX_VALUE
    g.nextRaid = never; g.nextMisc = never; g.nextWanderer = never; g.nextTrader = never; g.nextPod = never; g.nextTempEvent = never
    return g
}

private fun Pawn.feels(label: String, g: Game): Boolean {
    g.moodUpdate(this)
    return situ.any { it.label == label }
}

class MoodSocialTest {

    // ----------------------------------------------------------------------------------- loneliness

    @Test fun aColonistWhoHasNotTalkedForTwoDaysIsLonely() {
        val g = calmGame(2201)
        val p = g.colonists.first()
        g.tick = 5L * TICKS_PER_DAY
        p.lastSocial = 0L
        assertTrue(p.feels("Lonely", g))
    }

    @Test fun aRecentConversationKeepsLonelinessAway() {
        val g = calmGame(2202)
        val p = g.colonists.first()
        g.tick = 5L * TICKS_PER_DAY
        p.lastSocial = g.tick - TICKS_PER_DAY
        assertFalse(p.feels("Lonely", g))
    }

    @Test fun sociableColonistsFeelLonelinessHarder() {
        val g = calmGame(2203)
        val quiet = g.colonists.first()
        val social = g.colonists.last()
        social.traits += Trait.EASY_COMPANY
        g.tick = 5L * TICKS_PER_DAY
        quiet.lastSocial = 0L; social.lastSocial = 0L
        g.moodUpdate(quiet); g.moodUpdate(social)
        val q = quiet.situ.first { it.label == "Lonely" }.mood
        val s = social.situ.first { it.label == "Lonely" }.mood
        assertTrue("gregarious pawns take a bigger hit", s < q)
    }

    @Test fun aNewColonyIsNotLonelyInItsFirstTwoDays() {
        val g = calmGame(2204)
        val p = g.colonists.first()
        g.tick = TICKS_PER_DAY.toLong()
        p.lastSocial = 0L
        assertFalse(p.feels("Lonely", g))
    }

    // ----------------------------------------------------------------------------------- conversation

    @Test fun aConversationMarksBothPeopleAsRecentlySocial() {
        val g = calmGame(2205)
        val a = g.colonists[0]; val b = g.colonists[1]
        g.tick = 3L * TICKS_PER_DAY
        a.lastSocial = 0L; b.lastSocial = 0L
        g.socialInteract(a, b)
        assertEquals(g.tick, a.lastSocial)
        assertEquals(g.tick, b.lastSocial)
    }

    @Test fun aBeautifulSpeakerSometimesComplimentsTheListener() {
        val g = calmGame(2206)
        val a = g.colonists[0]; val b = g.colonists[1]
        a.traits += Trait.STRIKING_LOOKS
        var complimented = false
        repeat(400) {
            g.tick += (TICKS_PER_DAY / 4).toLong()
            g.socialInteract(a, b)
            if (b.thoughts.any { it.label == "Complimented" }) complimented = true
        }
        assertTrue("a compliment happens over many chats", complimented)
    }

    // ----------------------------------------------------------------------------------- brooding muse

    @Test fun aTorturedArtistCarriesAMoodPenalty() {
        val g = calmGame(2207)
        val p = g.colonists.first()
        assertFalse(p.feels("Restless mind", g))
        p.traits += Trait.BROODING_MUSE
        assertTrue(p.feels("Restless mind", g))
    }

    @Test fun looksAffectMood() {
        val g = calmGame(2208)
        val pretty = g.colonists[0]; val plain = g.colonists[1]
        pretty.traits += Trait.STRIKING_LOOKS
        plain.traits += Trait.PLAIN_LOOKS
        assertTrue(pretty.feels("Pleasant looks", g))
        assertTrue(plain.feels("Looks bad", g))
        assertFalse(pretty.feels("Looks bad", g))
    }

    @Test fun aSpouseFarAwayIsMissed() {
        val g = calmGame(2209)
        val p = g.colonists[0]; val s = g.colonists[1]
        p.spouse = s.id; s.spouse = p.id
        s.x = p.x; s.y = p.y
        assertFalse(p.feels("Apart from spouse", g))
        s.x = if (p.x < g.map.w / 2) g.map.w - 2 else 1
        assertTrue(p.feels("Apart from spouse", g))
    }

    // ----------------------------------------------------------------------------------- hiding

    @Test fun aHidingPawnGoesToABedAndStaysThere() {
        val g = calmGame(2210)
        val p = g.colonists.first()
        val bx = p.x + 3; val by = p.y
        for (y in by - 2..by + 2) for (x in bx - 2..bx + 2) if (g.map.inB(x, y)) {
            val i = g.map.idx(x, y)
            g.map.terrain[i] = Terrain.SOIL; g.map.plant[i] = null; g.map.building[i] = null
        }
        g.map.setBuilding(Building(BuildDef.BED, bx, by, true))
        p.food = 1f; p.rest = 1f
        g.startBreak(p, Break.HIDE)
        repeat(400) { g.step() }
        assertEquals(Break.HIDE, p.breakKind)
        assertTrue("the pawn is at the bed", g.distance(p.x, p.y, bx, by) <= 1f)
    }

    // ----------------------------------------------------------------------------------- conversations

    @Test fun aSociableColonistSometimesGivesFriendlyRibbing() {
        val g = calmGame(2211)
        val a = g.colonists[0]; val b = g.colonists[1]
        a.traits += Trait.EASY_COMPANY
        var ribbed = false
        repeat(400) {
            g.tick += (TICKS_PER_DAY / 4).toLong()
            g.socialInteract(a, b)
            if (b.thoughts.any { it.label == "Friendly ribbing" }) ribbed = true
        }
        assertTrue(ribbed)
    }

    @Test fun pawnsWhoDislikeEachOtherCanArgue() {
        val g = calmGame(2212)
        val a = g.colonists[0]; val b = g.colonists[1]
        var argued = false
        repeat(400) {
            // Keep the dislike in place: an ordinary chat would otherwise warm them up.
            a.opinion[b.id] = -12; b.opinion[a.id] = -12
            g.tick += (TICKS_PER_DAY / 4).toLong()
            g.socialInteract(a, b)
            if (a.thoughts.any { it.label.startsWith("Argued") }) argued = true
        }
        assertTrue(argued)
    }

    // ----------------------------------------------------------------------------------- furniture

    @Test fun newFurnitureIsAppendedSoSavedOrdinalsStillMatch() {
        assertEquals(BuildDef.SENTRY_POD.ordinal + 1, BuildDef.LOUNGE_SEAT.ordinal)
        assertEquals(BuildDef.LOUNGE_SEAT.ordinal + 1, BuildDef.BOOKSHELF.ordinal)
        assertEquals(BuildDef.BOOKSHELF.ordinal + 1, BuildDef.WARDROBE.ordinal)
        assertEquals(BuildDef.WARDROBE.ordinal + 1, BuildDef.COFFEE_TABLE.ordinal)
    }

    @Test fun aSofaIsComfortableAndNeedsComplexFurniture() {
        assertTrue(BuildDef.LOUNGE_SEAT.comfort > BuildDef.ARMCHAIR.comfort)
        assertEquals(Research.JOINERY_DETAIL, BuildDef.LOUNGE_SEAT.research)
        assertEquals(Research.JOINERY_DETAIL, BuildDef.BOOKSHELF.research)
    }
}
