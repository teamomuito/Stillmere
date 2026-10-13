package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

/**
 * New colonies use a 250 by 250 map. Saves store their own width and height and keep them when read, so a save written before
 * the change (100 by 100) is never resized onto a different map.
 */
class ColonyMapSizeTest {
    /**
     * Each committed legacy save and the side it stores. Measured on the current code; several older fixtures store 250, not 100
     * (see docs/fidelity/map.md, "Saves on disk"). colony_v36 is a new colony, written by the code before the bump to version 37.
     * A new fixture must be added here.
     */
    private val storedSides = mapOf(
        "colony_v24.sav" to 100, "colony_v25.sav" to 100, "colony_v25_graves.sav" to 100, "colony_v25_head.sav" to 100,
        "colony_v25_thoughts.sav" to 100, "colony_v26.sav" to 100, "colony_v27.sav" to 100, "colony_v28.sav" to 100,
        "colony_v34.sav" to 100, "colony_v35.sav" to 100,
        "colony_v25_adults.sav" to 250, "colony_v27_sites.sav" to 250, "colony_v29.sav" to 250, "colony_v30.sav" to 250,
        "colony_v31.sav" to 250, "colony_v32.sav" to 250, "colony_v32_xp.sav" to 250, "colony_v33.sav" to 250,
        "colony_v36.sav" to 250, "colony_v37.sav" to 250, "colony_v38.sav" to 250, "colony_v39.sav" to 250,
    )

    @Test fun everyLegacySaveKeepsTheSideItStores() {
        val onDisk = File(javaClass.getResource("/legacy")!!.toURI()).listFiles()!!.map { it.name }.filter { it.endsWith(".sav") }
        assertEquals("every legacy fixture is in the table", storedSides.keys.sorted(), onDisk.sorted())
        for ((name, side) in storedSides) {
            val fixture = javaClass.getResourceAsStream("/legacy/$name")!!.use { it.readBytes() }
            val g = SaveGame.read(fixture)
            assertEquals("$name width", side, g.map.w)
            assertEquals("$name height", side, g.map.h)
            val again = SaveGame.read(SaveGame.write(g))
            assertEquals("$name width after a rewrite", side, again.map.w)
            assertEquals("$name height after a rewrite", side, again.map.h)
        }
    }

    @Test fun aNewColonyRoundTripsAt250() {
        val g = Game(7L)
        g.startNewColony(Scenario.QUIET_ENCLAVE)
        val back = SaveGame.read(SaveGame.write(g))
        assertEquals(250, back.map.w)
        assertEquals(250, back.map.h)
    }

    @Test fun aNewColonyHasA250ByAn250Map() {
        val g = Game(5L)
        g.startNewColony(Scenario.QUIET_ENCLAVE)
        assertEquals(250, g.map.w)
        assertEquals(250, g.map.h)
    }

    /** Version 36 still loads, and a new save is written as the current version (40, the door holds and room temperatures). */
    @Test fun aVersion36SaveLoadsAndANewSaveIsTheCurrentVersion() {
        val fixture = javaClass.getResourceAsStream("/legacy/colony_v36.sav")!!.use { it.readBytes() }
        val g = SaveGame.read(fixture)
        assertEquals(250, g.map.w)
        assertEquals(5, g.colonists.size)
        assertEquals(40, ByteBuffer.wrap(SaveGame.write(g)).int)
    }

    /**
     * The trip of [SettleTest], made on a 100 by 100 colony. Settling used to copy the old map's size, so the new colony was 100 by 100.
     * It is [Game.MAP_SIZE] now, and the archived old colony still stores its own 100 by 100 map.
     */
    @Test fun aColonyFoundedFromAHundredByHundredSaveIsTwoFiftyByTwoFifty() {
        val seed = 22L
        val g = Game(seed, GameMap.generateFor(100, 100, seed, Biome.TEMPERATE))
        g.startNewColony(Scenario.QUIET_ENCLAVE)
        g.mentalBreaksEnabled = false
        g.pawns.removeAll { it.isAnimal && it.faction == Faction.WILD }
        g.nextRaid = Long.MAX_VALUE; g.nextMisc = Long.MAX_VALUE; g.nextWanderer = Long.MAX_VALUE; g.nextTrader = Long.MAX_VALUE
        g.randomCaravanEvents = false
        g.paintZone(g.homeX - 3, g.homeY - 3, g.homeX + 3, g.homeY + 3, ZoneKind.STOCKPILE)
        g.map.drop(ItemType.STEEL, 60, g.homeX, g.homeY)
        val homeTile = g.world.homeTile
        val goal = (0 until g.world.w * g.world.h).first { g.world.passable(it) && g.world.settlementAt(it) == null && it != homeTile && g.world.path(homeTile, it)?.size in 3..8 }
        assertNull(g.formCaravan(g.colonists.take(2), mapOf(ItemType.STEEL to 10), goal))
        val c = g.caravans.single()
        var guard = 0
        while (g.caravans.contains(c) && c.tile != goal && guard++ < 3000) repeat(tk(250)) { g.step() }
        assertEquals("the caravan arrived", goal, c.tile)
        val st = g.settle(c, "Newhold")
        assertNotNull(st)
        val ng = st!!.game
        assertEquals(Game.MAP_SIZE, ng.map.w)
        assertEquals(Game.MAP_SIZE, ng.map.h)
        assertEquals(250, SaveGame.read(SaveGame.write(ng)).map.w)
        assertEquals(100, SaveGame.read(st.archivedOld).map.w)
        assertEquals(100, SaveGame.read(st.archivedOld).map.h)
    }

    @Test fun aSaveWrittenBeforeTheChangeKeepsItsHundredByHundredMap() {
        val fixture = javaClass.getResourceAsStream("/legacy/colony_v28.sav")!!.use { it.readBytes() }
        val g = SaveGame.read(fixture)
        assertEquals(100, g.map.w)
        assertEquals(100, g.map.h)
    }

    @Test fun aRoundTripKeepsTheStoredSizeEvenWhenItIsNotASquare() {
        val g = Game(6L, GameMap.generateFor(120, 90, 6L, Biome.TEMPERATE))
        g.startNewColony(Scenario.QUIET_ENCLAVE)
        val back = SaveGame.read(SaveGame.write(g))
        assertEquals(120, back.map.w)
        assertEquals(90, back.map.h)
    }
}
