package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * A version-24 save written by the game before the time base changed (1,000 ticks per hour). The expectation file lists
 * the tick values the old game held, so the migration can be checked against what the old game actually did.
 */
class LegacySaveMigrationTest {
    private val fixture = javaClass.getResourceAsStream("/legacy/colony_v24.sav")!!.use { it.readBytes() }
    private val expect: Map<String, String> = javaClass.getResourceAsStream("/legacy/colony_v24_expect.txt")!!
        .bufferedReader().readLines().filter { it.contains('=') }.associate { it.substringBefore('=') to it.substringAfter('=') }

    private fun legacyLong(key: String): Long = expect.getValue(key).toLong()
    private fun legacyFloat(key: String): Float = expect.getValue(key).toFloat()

    /** What a stored time should read as in the new base: positive times scale, sentinels and "never" stay put. */
    private fun scaledTime(v: Long): Long = if (v <= 0L || v >= Long.MAX_VALUE / 4) v else Math.round(v * TIME_SCALE.toDouble())
    /** What a stored counter should read as: scaled, with the -1 "none" marker kept. */
    private fun scaledCount(v: Int): Int = if (v == -1) -1 else Math.round(v * TIME_SCALE)

    private fun stateHash(g: Game): String =
        MessageDigest.getInstance("SHA-256").digest(SaveGame.write(g)).joinToString("") { "%02x".format(it) }

    @Test fun everyTimeInTheSaveIsConvertedToTheNewBase() {
        val g = SaveGame.read(fixture)
        val c = g.colonists[0]
        assertEquals("tick", scaledTime(legacyLong("tick")), g.tick)
        assertEquals("nextRaid", scaledTime(legacyLong("nextRaid")), g.nextRaid)
        assertEquals("nextWanderer", scaledTime(legacyLong("nextWanderer")), g.nextWanderer)
        assertEquals("nextPod", scaledTime(legacyLong("nextPod")), g.nextPod)
        assertEquals("nextTempEvent", scaledTime(legacyLong("nextTempEvent")), g.nextTempEvent)
        assertEquals("nextMisc", scaledTime(legacyLong("nextMisc")), g.nextMisc)
        assertEquals("nextTrader", scaledTime(legacyLong("nextTrader")), g.nextTrader)
        assertEquals("weatherUntil", scaledTime(legacyLong("weatherUntil")), g.weatherUntil)
        assertEquals("raidLastEnded keeps its -1 marker", -1L, g.raidLastEnded)
        assertEquals("raidEnds", scaledTime(legacyLong("raidEnds")), g.raidEnds)
        assertEquals("raidStartedAt", scaledTime(legacyLong("raidStartedAt")), g.raidStartedAt)
        assertEquals("tempEventUntil", scaledTime(legacyLong("tempEventUntil")), g.tempEventUntil)
        assertEquals("solarFlareUntil", scaledTime(legacyLong("solarFlareUntil")), g.solarFlareUntil)
        assertEquals("eclipseUntil", scaledTime(legacyLong("eclipseUntil")), g.eclipseUntil)
        assertEquals("toxicFalloutUntil", scaledTime(legacyLong("toxicFalloutUntil")), g.toxicFalloutUntil)
        assertEquals("trader leaveAt", scaledTime(legacyLong("traderLeaveAt")), g.traders.first().leaveAt)
        assertEquals("caravan progress is in ticks too", legacyFloat("caravanProgress") * TIME_SCALE, g.caravans.first().progress, 0.01f)
        assertEquals("pawn breakUntil", scaledTime(legacyLong("breakUntil")), c.breakUntil)
        assertEquals("pawn lastSocial", scaledTime(legacyLong("lastSocial")), c.lastSocial)
        assertEquals("pawn escapeTick", scaledTime(legacyLong("escapeTick")), c.escapeTick)
        assertEquals("pawn suppressedUntil", scaledTime(legacyLong("suppressedUntil")), c.suppressedUntil)
        assertEquals("hediff age", scaledCount(expect.getValue("hediffAge").toInt()), c.hediff(HediffKind.FLU)!!.age)
        assertEquals("injury age", scaledCount(expect.getValue("injuryAge").toInt()), c.injuries.first().age)
        assertEquals("move total", scaledCount(expect.getValue("moveTotal").toInt()), c.moveTotal)
        assertEquals("job timer", scaledCount(expect.getValue("jobTimer").toInt()), c.job!!.timer)
    }

    @Test fun theCalendarReadsTheSameDayHourAndSeasonAsTheOldGame() {
        val g = SaveGame.read(fixture)
        val oldTick = legacyLong("tick")
        val oldDay = (oldTick / 24_000).toInt()
        val oldHour = ((oldTick % 24_000) / 1_000).toInt()
        val oldSeason = Season.entries[(oldDay / 15) % 4]
        assertEquals("day", oldDay, g.day)
        assertEquals("hour", oldHour, g.hour)
        assertEquals("season", oldSeason, g.season)
    }

    @Test fun aConvertedGameSavesAndLoadsWithoutChangingAgain() {
        val g = SaveGame.read(fixture)
        val first = stateHash(g)
        val again = SaveGame.read(SaveGame.write(g))
        assertEquals("re-saved and re-loaded state", first, stateHash(again))
        assertTrue("the converted game still runs", run { repeat(tk(1000)) { again.step() }; again.tick > g.tick })
    }
}
