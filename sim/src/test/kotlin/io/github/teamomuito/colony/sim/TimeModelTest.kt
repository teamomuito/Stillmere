package io.github.teamomuito.colony.sim

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

private fun timeTestColony(seed: Long): Game {
    val g = Game(seed)
    g.startNewColony(Scenario.CRASHLANDED)
    return g
}

/** A full-state fingerprint: the save format holds every piece of game state, so its bytes identify the whole game. */
private fun stateHash(g: Game): String =
    MessageDigest.getInstance("SHA-256").digest(SaveGame.write(g)).joinToString("") { "%02x".format(it) }

private fun Game.stepTicks(n: Int) { repeat(n) { step() } }

class TimeModelTest {
    @Test fun calendarMatchesVanillaTimeBase() {
        assertEquals(2500, TICKS_PER_HOUR)
        assertEquals(60_000, TICKS_PER_DAY)
        assertEquals(24, HOURS_PER_DAY)
        assertEquals(15, DAYS_PER_SEASON)
        assertEquals(4, SEASONS_PER_YEAR)
        assertEquals(60, DAYS_PER_YEAR)
        assertEquals(TICKS_PER_DAY, HOURS_PER_DAY * TICKS_PER_HOUR)
    }

    @Test fun legacyDurationsKeepTheirMeaningInHoursAndDays() {
        // A constant written when an hour was 1,000 ticks still lasts the same amount of game time.
        assertEquals(TICKS_PER_HOUR, tk(LEGACY_TICKS_PER_HOUR))
        assertEquals(TICKS_PER_DAY, tk(24_000))
        assertEquals(625, tk(250))                  // 250 old ticks is 625 now: the slow-tick interval
        assertEquals(250L * 5 / 2, tk(250L))
    }

    @Test fun seasonsAndYearsRollOverOnTheirOwnDays() {
        val g = timeTestColony(3)
        g.tick = 0
        assertEquals(Season.SPRING, g.season)
        assertEquals(1, g.dayOfSeason)
        g.tick = (DAYS_PER_SEASON * TICKS_PER_DAY).toLong()
        assertEquals(Season.SUMMER, g.season)
        assertEquals(1, g.dayOfSeason)
        g.tick = (DAYS_PER_YEAR * TICKS_PER_DAY).toLong()
        assertEquals(Season.SPRING, g.season)
        assertEquals(g.year, 5501)
    }

    @Test fun hourOfDayFollowsTicks() {
        val g = timeTestColony(3)
        g.tick = 0
        assertEquals(0, g.hour)
        g.tick = (12L * TICKS_PER_HOUR)
        assertEquals(12, g.hour)
        g.tick = (TICKS_PER_DAY - 1).toLong()
        assertEquals(23, g.hour)
    }

    @Test fun sunlightFollowsTheDayAndNight() {
        val g = timeTestColony(3)
        g.weather = Weather.CLEAR
        g.eclipseUntil = 0
        g.tick = 2L * TICKS_PER_HOUR      // 02:00: dark
        assertEquals(0f, g.daylight(), 0f)
        g.tick = 12L * TICKS_PER_HOUR     // 12:00: full light
        assertEquals(1f, g.daylight(), 0f)
        g.tick = 6L * TICKS_PER_HOUR      // 06:00: dawn, half light
        assertEquals(0.5f, g.daylight(), 0.001f)
        g.tick = 19L * TICKS_PER_HOUR     // 19:00: dusk, half light
        assertEquals(0.5f, g.daylight(), 0.001f)
    }

    @Test fun outdoorTemperatureSwingsThroughTheDay() {
        val g = timeTestColony(3)
        g.tick = 3L * TICKS_PER_HOUR      // the diurnal curve bottoms out at 03:00
        val cold = g.outdoorTemp()
        g.tick = 15L * TICKS_PER_HOUR     // and peaks at 15:00
        val warm = g.outdoorTemp()
        assertTrue("afternoon ($warm) warmer than night ($cold)", warm > cold)
    }

    @Test fun sameSeedGivesTheSameStateAfterTheSameTicks() {
        val ticks = (TICKS_PER_DAY * 3 / 2)     // a day and a half
        val a = timeTestColony(11); a.stepTicks(ticks)
        val b = timeTestColony(11); b.stepTicks(ticks)
        assertEquals("state hash after $ticks ticks", stateHash(a), stateHash(b))
        assertEquals(a.tick, b.tick)
    }

    @Test fun differentSeedsGiveDifferentStates() {
        val a = timeTestColony(11); a.stepTicks(2000)
        val b = timeTestColony(12); b.stepTicks(2000)
        assertNotEquals(stateHash(a), stateHash(b))
    }

    @Test fun saveAndLoadLeavesTheStateIdentical() {
        val g = timeTestColony(21)
        g.stepTicks(TICKS_PER_DAY / 2)
        val before = stateHash(g)
        val loaded = SaveGame.read(SaveGame.write(g))
        assertEquals("hash straight after load", before, stateHash(loaded))
        assertArrayEquals("bytes after load", SaveGame.write(g), SaveGame.write(loaded))
    }

    @Test fun savedThenLoadedGameRunsExactlyLikeTheOriginal() {
        val g = timeTestColony(22)
        g.stepTicks(TICKS_PER_DAY / 2)
        val loaded = SaveGame.read(SaveGame.write(g))
        val more = TICKS_PER_DAY / 2
        g.stepTicks(more)
        loaded.stepTicks(more)
        assertEquals("hash after $more more ticks, original vs loaded", stateHash(g), stateHash(loaded))
    }

    @Test fun simulationReadsNoWallClockAndNoUnseededRandomness() {
        // Enforces the rule in CLAUDE.md. Gradle runs tests from the module directory.
        val banned = listOf(
            "currentTimeMillis", "nanoTime", "randomUUID", "java.util.Random()", "Math.random", "kotlin.random", "SecureRandom",
            "Instant.now", "LocalDateTime.now", "System.getenv",
        )
        val root = File("src/main/kotlin/io/github/teamomuito/colony/sim")
        val offenders = ArrayList<String>()
        for (f in root.listFiles().orEmpty().filter { it.extension == "kt" }) {
            f.readLines().forEachIndexed { i, line ->
                val code = line.substringBefore("//")
                for (b in banned) if (code.contains(b)) offenders += "${f.name}:${i + 1}: $b"
            }
        }
        assertTrue("sim/ must not read the clock or unseeded randomness: $offenders", offenders.isEmpty())
    }
}
