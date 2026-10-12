package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream

/**
 * colony_v35.sav is a version-35 save, written by the version-35 code before the combat change of version 36 (temporary generator,
 * since deleted: Game(3535L), startNewColony(QUIET_ENCLAVE), 2500 steps, then a good long coat with 123.5 hit points on the first
 * colonist). Its expectation file lists those values. Version 36 adds the burn size and the stagger end to each pawn record; an
 * older save reads both as zero (docs/fidelity/combat.md).
 */
class LegacyCombatMigrationTest {
    private fun resource(name: String): ByteArray =
        javaClass.getResourceAsStream("/legacy/$name")?.use { it.readBytes() } ?: error("missing test resource legacy/$name")

    private fun expectations(name: String): Map<String, String> =
        resource(name).decodeToString().lines().filter { it.contains('=') }.associate { it.substringBefore('=') to it.substringAfter('=') }

    private val fixture = resource("colony_v35.sav")
    private val expect = expectations("colony_v35_expect.txt")

    private fun versionOf(bytes: ByteArray): Int = DataInputStream(ByteArrayInputStream(bytes)).readInt()

    @Test fun theFixtureIsReallyVersionThirtyFive() {
        assertEquals(35, versionOf(fixture))
    }

    @Test fun aVersionThirtyFiveSaveLoadsItsClockColonistsAndCoat() {
        val g = SaveGame.read(fixture)
        assertEquals(expect.getValue("tick").toLong(), g.tick)
        assertEquals(expect.getValue("colonists").toInt(), g.colonists.size)
        val p = g.colonists[0]
        assertEquals(expect.getValue("pawn0.name"), p.name)
        val coat = p.apparel.last()
        assertEquals(expect.getValue("coat.hp").toFloat(), coat.hp, 1e-4f)
        assertEquals(expect.getValue("coat.quality"), coat.quality.name)
    }

    @Test fun aVersionThirtyFiveSaveReadsNoBurningAndNoStagger() {
        val g = SaveGame.read(fixture)
        assertTrue("no pawn is alight", g.pawns.all { it.burnSize == 0f })
        assertTrue("no pawn is staggered", g.pawns.all { it.staggerUntil == 0L })
    }

    @Test fun aVersionThirtyFiveSaveWrittenAgainIsVersionThirtySixAndKeepsItsCoat() {
        val bytes = SaveGame.write(SaveGame.read(fixture))
        assertEquals(36, versionOf(bytes))
        val back = SaveGame.read(bytes)
        assertEquals(expect.getValue("coat.hp").toFloat(), back.colonists[0].apparel.last().hp, 1e-4f)
    }
}
