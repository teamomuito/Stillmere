package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream

/**
 * colony_v37.sav is a version-37 save, written by the version-37 code before the roof grid of version 38 (temporary generator, since
 * deleted: Game(3737L), startNewColony(QUIET_ENCLAVE), then four walled areas: a small sealed room, a 900-cell room with no natural
 * roof, a 900-cell room with a natural-roof majority, and a room on the map edge). colony_v37_expect.txt holds the indoor status of
 * one probe cell per room as the old code computed it. Loading must keep every one of them, and each probe must read as roofed as it
 * did before (docs/fidelity/roofs.md, "Save format and migration").
 */
class LegacyRoofMigrationTest {
    private fun resource(name: String): ByteArray =
        javaClass.getResourceAsStream("/legacy/$name")?.use { it.readBytes() } ?: error("missing test resource legacy/$name")

    private fun expectations(name: String): Map<String, String> =
        resource(name).decodeToString().lines().filter { it.contains('=') }.associate { it.substringBefore('=') to it.substringAfter('=') }

    private val fixture = resource("colony_v37.sav")
    private val expect = expectations("colony_v37_expect.txt")
    private val probes = listOf("small", "bigOpen", "bigNatural", "bigNaturalSouth", "edge", "small2")

    private fun versionOf(bytes: ByteArray): Int = DataInputStream(ByteArrayInputStream(bytes)).readInt()

    private fun cellOf(g: Game, name: String): Int =
        g.map.idx(expect.getValue("probe.$name.x").toInt(), expect.getValue("probe.$name.y").toInt())

    @Test fun theFixtureIsReallyVersionThirtySeven() {
        assertEquals(37, versionOf(fixture))
    }

    @Test fun everyRoomKeepsItsStatusOnLoad() {
        val g = SaveGame.read(fixture)
        assertEquals(expect.getValue("tick").toLong(), g.tick)
        assertEquals(expect.getValue("colonists").toInt(), g.colonists.size)
        for (name in probes) {
            assertEquals("probe $name", expect.getValue("probe.$name.indoor").toBoolean(), g.map.roomIndoorAt(cellOf(g, name)))
        }
    }

    @Test fun aRoomIndoorUnderTheOldRuleIsRoofedOnLoad() {
        val g = SaveGame.read(fixture)
        assertTrue("the small room is roofed", g.map.roof[cellOf(g, "small")])
        assertTrue("the small room's second probe is roofed", g.map.roof[cellOf(g, "small2")])
        assertTrue("the natural-majority room is roofed on every open cell", g.map.roof[cellOf(g, "bigNaturalSouth")])
    }

    @Test fun aRoomOutdoorUnderTheOldRuleGetsNoRoof() {
        val g = SaveGame.read(fixture)
        assertTrue("the open room gets no roof", !g.map.roof[cellOf(g, "bigOpen")] && !g.map.roofed(cellOf(g, "bigOpen")))
        assertTrue("the edge room gets no roof", !g.map.roof[cellOf(g, "edge")] && !g.map.roofed(cellOf(g, "edge")))
    }

    @Test fun eachProbeReadsAsRoofedAsBeforeTheMigration() {
        // Before version 38, roofed(i) was the natural roof or an indoor room. The migrated roof grid gives the same answer.
        val g = SaveGame.read(fixture)
        for (name in probes) {
            val i = cellOf(g, name)
            val oldRoofed = g.map.natRoof[i] || expect.getValue("probe.$name.indoor").toBoolean()
            assertEquals("probe $name roofed", oldRoofed, g.map.roofed(i))
        }
    }

    @Test fun aVersionThirtySevenSaveWrittenAgainIsTheCurrentVersionAndKeepsItsRooms() {
        val bytes = SaveGame.write(SaveGame.read(fixture))
        assertEquals(SaveGame.VERSION, versionOf(bytes))
        val back = SaveGame.read(bytes)
        for (name in probes) {
            assertEquals("probe $name after a rewrite", expect.getValue("probe.$name.indoor").toBoolean(), back.map.roomIndoorAt(cellOf(back, name)))
        }
    }
}
