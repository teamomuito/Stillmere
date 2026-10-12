package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream

/**
 * colony_v33.sav is a version-33 save, written by the version-33 code before the recent-incident list of version 34
 * (temporary generator: Game(4343L), startNewColony(QUIET_ENCLAVE), two days of steps, then METEORITE and AURORA run by
 * hand). Its expectation file lists the colonists, the clock, the incident history and the channel timers it held. It
 * must still load with them, its recent list must read as empty, and version 34 must keep the list it is given.
 */
class LegacyThreatsMigrationTest {
    private fun resource(name: String): ByteArray =
        javaClass.getResourceAsStream("/legacy/$name")?.use { it.readBytes() } ?: error("missing test resource legacy/$name")

    private fun expectations(name: String): Map<String, String> =
        resource(name).decodeToString().lines().filter { it.contains('=') }.associate { it.substringBefore('=') to it.substringAfter('=') }

    private val fixture = resource("colony_v33.sav")
    private val expect = expectations("colony_v33_expect.txt")

    private fun versionOf(bytes: ByteArray): Int = DataInputStream(ByteArrayInputStream(bytes)).readInt()

    @Test fun theFixtureIsReallyVersionThirtyThree() {
        assertEquals(33, versionOf(fixture))
    }

    @Test fun aVersionThirtyThreeSaveLoadsItsColonistsClockAndHistory() {
        val g = SaveGame.read(fixture)
        assertEquals(expect.getValue("colonists").toInt(), g.colonists.size)
        g.colonists.forEachIndexed { k, p -> assertEquals("name $k", expect.getValue("name.$k"), p.name) }
        assertEquals(expect.getValue("tick").toLong(), g.tick)
        for ((key, value) in expect) if (key.startsWith("incident.")) {
            val id = Incident.valueOf(key.removePrefix("incident."))
            assertEquals(key, value.toLong(), g.incidentLast[id])
        }
        assertEquals(expect.getValue("raidLastEnded").toLong(), g.raidLastEnded)
        assertEquals(expect.getValue("nextRaid").toLong(), g.nextRaid)
        assertEquals(expect.getValue("nextMisc").toLong(), g.nextMisc)
        assertEquals(expect.getValue("raidCounter").toInt(), g.raidCounter)
    }

    @Test fun itHasNoRecentIncidentsBecauseVersionThirtyThreeDidNotSaveThem() {
        assertTrue(SaveGame.read(fixture).lastRandomIncidents.isEmpty())
    }

    @Test fun aVersionThirtyThreeSaveWrittenAgainIsTheCurrentVersion() {
        assertEquals(36, versionOf(SaveGame.write(SaveGame.read(fixture))))
    }

    @Test fun theRecentIncidentsSurviveASaveAtTheCurrentVersion() {
        val g = SaveGame.read(fixture)
        g.lastRandomIncidents.addAll(listOf(Incident.AURORA, Incident.METEORITE))
        val again = SaveGame.read(SaveGame.write(g))
        assertEquals(listOf(Incident.AURORA, Incident.METEORITE), again.lastRandomIncidents)
    }

    @Test fun aSaveAtVersionThirtyThreeLeavesTheRecentListOut() {
        val g = SaveGame.read(fixture)
        g.lastRandomIncidents.addAll(listOf(Incident.AURORA))
        val bytes = SaveGame.write(g, version = 33)
        assertEquals(33, versionOf(bytes))
        val back = SaveGame.read(bytes)
        assertTrue(back.lastRandomIncidents.isEmpty())
        assertEquals(g.incidentLast, back.incidentLast)
    }
}
