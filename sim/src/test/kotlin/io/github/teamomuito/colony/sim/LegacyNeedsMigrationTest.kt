package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream

/**
 * colony_v31.sav is a version-31 save, written by the version-31 code before the needs and drug-tolerance fields of version 32
 * (temporary generator: Game(4242L), startNewColony(QUIET_ENCLAVE), 2000 base ticks). Its expectation file lists the values it held.
 * It must still load, its new fields must read as their defaults, and version 32 must keep what is added to it.
 */
class LegacyNeedsMigrationTest {
    private fun resource(name: String): ByteArray =
        javaClass.getResourceAsStream("/legacy/$name")?.use { it.readBytes() } ?: error("missing test resource legacy/$name")

    private fun expectations(name: String): Map<String, String> =
        resource(name).decodeToString().lines().filter { it.contains('=') }.associate { it.substringBefore('=') to it.substringAfter('=') }

    private val fixture = resource("colony_v31.sav")
    private val expect = expectations("colony_v31_expect.txt")

    private fun versionOf(bytes: ByteArray): Int = DataInputStream(ByteArrayInputStream(bytes)).readInt()

    @Test fun theFixtureIsReallyVersionThirtyOne() {
        assertEquals(31, versionOf(fixture))
    }

    @Test fun aVersionThirtyOneSaveLoadsWithItsColonistsAndTheirNeeds() {
        val g = SaveGame.read(fixture)
        assertEquals(expect.getValue("colonists").toInt(), g.colonists.size)
        g.colonists.forEachIndexed { k, p ->
            assertEquals(expect.getValue("name.$k"), p.name)
            assertEquals("food $k", expect.getValue("food.$k").toFloat(), p.food, 0f)
            assertEquals("rest $k", expect.getValue("rest.$k").toFloat(), p.rest, 0f)
            assertEquals("joy $k", expect.getValue("joy.$k").toFloat(), p.joy, 0f)
            assertEquals("mood $k", expect.getValue("mood.$k").toFloat(), p.mood, 0f)
        }
    }

    @Test fun theNewFieldsReadAsTheirDefaultsFromAVersionThirtyOneSave() {
        val g = SaveGame.read(fixture)
        for (p in g.colonists) {
            assertEquals(1f, p.outdoorsNeed, 0f)
            assertEquals(1f, p.indoorsNeed, 0f)
            assertEquals(0, p.ticksAtZeroRest)
            assertEquals(0, p.moodBelowExtremeTicks)
            assertEquals(0, p.moodBelowMajorTicks)
            assertEquals(0, p.moodBelowMinorTicks)
            assertEquals(0, p.breakCooldown)
            assertTrue("no tolerance was saved", p.drugTolerance.isEmpty())
        }
    }

    @Test fun aVersionThirtyOneSaveWrittenAgainIsVersionThirtyTwo() {
        assertEquals(32, versionOf(SaveGame.write(SaveGame.read(fixture))))
    }

    @Test fun needsAndToleranceSurviveASaveAtVersionThirtyTwo() {
        val g = SaveGame.read(fixture)
        val p = g.colonists.first()
        p.drugTolerance[ItemType.BEER] = 0.35f
        p.drugTolerance[ItemType.LUCID_DRAUGHT] = 0.6f
        p.outdoorsNeed = 0.33f
        p.indoorsNeed = 0.77f
        p.ticksAtZeroRest = 1234
        p.moodBelowExtremeTicks = 625
        p.moodBelowMajorTicks = 1250
        p.moodBelowMinorTicks = 2500
        p.breakCooldown = 9000

        val again = SaveGame.read(SaveGame.write(g)).colonists.first()
        assertEquals(0.35f, again.drugTolerance[ItemType.BEER]!!, 0f)
        assertEquals(0.6f, again.drugTolerance[ItemType.LUCID_DRAUGHT]!!, 0f)
        assertEquals(2, again.drugTolerance.size)
        assertEquals(0.33f, again.outdoorsNeed, 0f)
        assertEquals(0.77f, again.indoorsNeed, 0f)
        assertEquals(1234, again.ticksAtZeroRest)
        assertEquals(625, again.moodBelowExtremeTicks)
        assertEquals(1250, again.moodBelowMajorTicks)
        assertEquals(2500, again.moodBelowMinorTicks)
        assertEquals(9000, again.breakCooldown)
    }

    @Test fun aVersionThirtyOneWriteLeavesTheNewFieldsOut() {
        val g = SaveGame.read(fixture)
        g.colonists.first().drugTolerance[ItemType.BEER] = 0.5f
        val bytes = SaveGame.write(g, 31)
        assertEquals(31, versionOf(bytes))
        assertTrue("version 31 holds no tolerance", SaveGame.read(bytes).colonists.first().drugTolerance.isEmpty())
    }
}
