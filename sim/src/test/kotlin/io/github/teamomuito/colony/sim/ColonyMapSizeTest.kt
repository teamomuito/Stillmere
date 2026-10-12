package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * New colonies use a 250 by 250 map. Saves store their own width and height and keep them when read, so a save written before
 * the change (100 by 100) is never resized onto a different map.
 */
class ColonyMapSizeTest {
    @Test fun aNewColonyHasA250ByAn250Map() {
        val g = Game(5L)
        g.startNewColony(Scenario.QUIET_ENCLAVE)
        assertEquals(250, g.map.w)
        assertEquals(250, g.map.h)
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
