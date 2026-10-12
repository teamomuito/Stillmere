package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream

/**
 * colony_v34.sav is a version-34 save, written by the version-34 code before the rot change of version 35 (temporary generator:
 * Game(4343L), startNewColony(SALVAGE_CREW), 2000 steps, then by hand a raw meat stack at rot 0.25, a corpse at rot 0.3, a
 * battery at charge 300 and FORGEWORK progress 123.5). Its expectation file lists those values. Version 35 keeps the research
 * progress unit and the battery charge, and rescales the rot of raw meat (4 days to 2) and of corpses (2.2 days to 2.5) when
 * it reads an older save. See docs/fidelity/economy.md.
 */
class LegacyEconomyMigrationTest {
    private fun resource(name: String): ByteArray =
        javaClass.getResourceAsStream("/legacy/$name")?.use { it.readBytes() } ?: error("missing test resource legacy/$name")

    private fun expectations(name: String): Map<String, String> =
        resource(name).decodeToString().lines().filter { it.contains('=') }.associate { it.substringBefore('=') to it.substringAfter('=') }

    private val fixture = resource("colony_v34.sav")
    private val expect = expectations("colony_v34_expect.txt")

    private fun versionOf(bytes: ByteArray): Int = DataInputStream(ByteArrayInputStream(bytes)).readInt()

    private fun Game.meatStack(): ItemStack =
        map.items.values.first { it.type == ItemType.MEAT && it.corpseOf == null && it.count == expect.getValue("meat.count").toInt() }

    private fun Game.corpseStack(): ItemStack = map.items.values.first { it.corpseOf == "Old Test" }

    @Test fun theFixtureIsReallyVersionThirtyFour() {
        assertEquals(34, versionOf(fixture))
    }

    @Test fun aVersionThirtyFourSaveLoadsItsClockColonistsResearchAndBattery() {
        val g = SaveGame.read(fixture)
        assertEquals(expect.getValue("tick").toLong(), g.tick)
        assertEquals(expect.getValue("colonists").toInt(), g.colonists.size)
        assertEquals("the research unit is unchanged", expect.getValue("research.forge").toFloat(), g.researchProgress.getValue(Research.FORGEWORK), 1e-4f)
        val battery = g.map.buildings().first { it.def == BuildDef.BATTERY }
        assertEquals(expect.getValue("battery.charge").toFloat(), battery.charge, 1e-4f)
    }

    @Test fun rawMeatRotIsRescaledFromFourDaysToTwo() {
        // The saved share of spoil time doubles, since the same time now takes half the spoil time (4 / 2).
        val g = SaveGame.read(fixture)
        assertEquals(expect.getValue("meat.rot").toFloat() * 2f, g.meatStack().rot, 1e-6f)
    }

    @Test fun aCorpsesRotIsRescaledFromTwoPointTwoDaysToTwoAndAHalf() {
        val g = SaveGame.read(fixture)
        assertEquals(expect.getValue("corpse.rot").toFloat() * 2.2f / 2.5f, g.corpseStack().rot, 1e-6f)
    }

    @Test fun aVersionThirtyFourSaveWrittenAgainIsTheCurrentVersion() {
        assertEquals(36, versionOf(SaveGame.write(SaveGame.read(fixture))))
    }

    @Test fun aRescaledRotIsNotRescaledAgainOnTheNextLoad() {
        val first = SaveGame.read(fixture)
        val rotAfterFirstLoad = first.meatStack().rot
        val corpseAfterFirstLoad = first.corpseStack().rot
        val again = SaveGame.read(SaveGame.write(first))
        assertEquals(rotAfterFirstLoad, again.meatStack().rot, 1e-6f)
        assertEquals(corpseAfterFirstLoad, again.corpseStack().rot, 1e-6f)
    }

    // The held-food rot is saved in the eat job. These tests write a version-34 stream with the current writer, since the layout
    // is the same at 34 and 35; only the meaning of the saved rot differs.

    /** A colonist eating [food] with [rot] held, saved as version 34. */
    private fun eatingSaveAsVersionThirtyFour(food: ItemType, rot: Float): Pair<Game, ByteArray> {
        val g = Game(4343L).also { it.startNewColony(Scenario.QUIET_ENCLAVE) }
        g.colonists[0].job = Job(JobType.EAT, g.homeX, g.homeY).also { it.item = food; it.heldRot = rot }
        return g to SaveGame.write(g, version = 34)
    }

    @Test fun theRotOfRawMealAnEatJobHoldsIsRescaledFromFourDaysToTwo() {
        val (g, bytes) = eatingSaveAsVersionThirtyFour(ItemType.MEAT, 0.3f)
        val p = g.colonists[0]
        assertEquals(0.6f, SaveGame.read(bytes).pawns.first { it.id == p.id }.job!!.heldRot, 1e-6f)
    }

    @Test fun theRotOfOtherFoodIsNotRescaled() {
        val (g, bytes) = eatingSaveAsVersionThirtyFour(ItemType.BERRIES, 0.3f)
        val p = g.colonists[0]
        assertEquals(0.3f, SaveGame.read(bytes).pawns.first { it.id == p.id }.job!!.heldRot, 0f)
    }

    @Test fun aHeldRotIsNotRescaledAgainOnTheNextLoad() {
        val (g, bytes) = eatingSaveAsVersionThirtyFour(ItemType.MEAT, 0.3f)
        val p = g.colonists[0]
        val first = SaveGame.read(bytes)
        val again = SaveGame.read(SaveGame.write(first))
        assertEquals(0.6f, again.pawns.first { it.id == p.id }.job!!.heldRot, 1e-6f)
    }
}
