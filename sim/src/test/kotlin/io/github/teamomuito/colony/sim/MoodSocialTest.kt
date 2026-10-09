package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A calm colony where only the timers a test sets can fire. */
private fun calmGame(seed: Long): Game {
    val g = Game(seed)
    g.startNewColony(Scenario.LOST_TRIBE)
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
        social.traits += Trait.SOCIABLE
        g.tick = 5L * TICKS_PER_DAY
        quiet.lastSocial = 0L; social.lastSocial = 0L
        g.moodUpdate(quiet); g.moodUpdate(social)
        val q = quiet.situ.first { it.label == "Lonely" }.mood
        val s = social.situ.first { it.label == "Lonely" }.mood
        assertTrue("sociable pawns take a bigger hit", s < q)
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
        a.traits += Trait.BEAUTIFUL
        var complimented = false
        repeat(400) {
            g.tick += (TICKS_PER_DAY / 4).toLong()
            g.socialInteract(a, b)
            if (b.thoughts.any { it.label == "Complimented" }) complimented = true
        }
        assertTrue("a compliment happens over many chats", complimented)
    }

    // ----------------------------------------------------------------------------------- tortured artist

    @Test fun aTorturedArtistCarriesAMoodPenalty() {
        val g = calmGame(2207)
        val p = g.colonists.first()
        assertFalse(p.feels("Tortured soul", g))
        p.traits += Trait.TORTURED_ARTIST
        assertTrue(p.feels("Tortured soul", g))
    }
}
