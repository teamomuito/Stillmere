package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream

/**
 * colony_v39.sav is a version-39 save, written by the version-39 code before the heat state was saved (version 40,
 * docs/fidelity/heat-exchange.md). It was made by a temporary generator, since deleted: Game(3939L), startNewColony(QUIET_ENCLAVE),
 * a sealed 3 by 3 room with a wooden door and a powered heater, then three slow steps. colony_v39_expect.txt holds what the loaded
 * game must show. A save of this version has no door holds and no room temperatures, so the door reads shut and the room reads the
 * outdoor temperature on load, as it did before version 40.
 */
class LegacyHeatMigrationTest {
    private fun resource(name: String): ByteArray =
        javaClass.getResourceAsStream("/legacy/$name")?.use { it.readBytes() } ?: error("missing test resource legacy/$name")

    private fun expectations(name: String): Map<String, String> =
        resource(name).decodeToString().lines().filter { it.contains('=') }.associate { it.substringBefore('=') to it.substringAfter('=') }

    private val fixture = resource("colony_v39.sav")
    private val expect = expectations("colony_v39_expect.txt")

    private fun versionOf(bytes: ByteArray): Int = DataInputStream(ByteArrayInputStream(bytes)).readInt()

    private fun doorCell(g: Game): Int = g.map.idx(expect.getValue("door.x").toInt(), expect.getValue("door.y").toInt())

    private fun roomOf(g: Game): Int = g.map.roomId[g.map.idx(expect.getValue("room.x").toInt(), expect.getValue("room.y").toInt())]

    @Test fun theFixtureIsReallyVersionThirtyNine() {
        assertEquals(39, versionOf(fixture))
        assertEquals(39, expect.getValue("version").toInt())
    }

    @Test fun itLoadsWithTheSameColonyAndRoomLayout() {
        val g = SaveGame.read(fixture)
        assertEquals(expect.getValue("tick").toLong(), g.tick)
        assertEquals(expect.getValue("colonists").toInt(), g.colonists.size)
        assertEquals(expect.getValue("buildings").toInt(), g.map.buildings().count { it != null })
        val room = roomOf(g)
        assertTrue(g.map.roomIndoor[room])
        assertEquals(expect.getValue("room.size").toInt(), g.map.roomSize[room])
    }

    @Test fun aVersionThirtyNineSaveHasNoDoorHoldAndItsRoomsStartOutdoors() {
        val g = SaveGame.read(fixture)
        assertEquals(0, g.map.building[doorCell(g)]!!.doorHold)
        assertEquals(expect.getValue("outdoor").toFloat(), g.map.roomTemp[roomOf(g)], 1e-3f)
    }

    @Test fun aVersionFortySaveKeepsTheDoorHoldAndTheRoomTemperature() {
        val g = SaveGame.read(fixture)
        g.map.building[doorCell(g)]!!.doorHold = 200
        g.map.roomTemp[roomOf(g)] = 3.25f
        val bytes = SaveGame.write(g)
        assertEquals(SaveGame.VERSION, versionOf(bytes))
        assertEquals(40, SaveGame.VERSION)
        val back = SaveGame.read(bytes)
        assertEquals(200, back.map.building[doorCell(back)]!!.doorHold)
        assertEquals(3.25f, back.map.roomTemp[roomOf(back)], 0f)
    }
}
