package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Heat exchange (docs/fidelity/heat-exchange.md). The constants are vanilla's, cited there; the expected values below are worked out
 * from them by hand in each test, so a change to a constant or to a rule shows up here.
 */

private fun noRoof(): RoofShares = RoofShares(FloatArray(1), FloatArray(1), FloatArray(1))

/** One indoor room of [cells] cells, [walls] wall edges to the outdoors of the given [weight], and the given roof shares. */
private fun stepRoom(t: Float, cells: Int, out: Float, walls: Int = 0, weight: Float = 1f, roof: RoofShares = noRoof(), heat: Float = 0f): Float {
    val r = Thermal.step(
        floatArrayOf(t), intArrayOf(cells), booleanArrayOf(true), floatArrayOf(heat),
        IntArray(walls), IntArray(walls) { Thermal.OUTDOORS }, FloatArray(walls) { weight }, IntArray(walls) { -1 },
        FloatArray(0), roof, out,
    )
    return r[0]
}

/** The temperature after one slow step when the gap to [target] closes by exponent [rate]: exact for a constant target. */
private fun settle(t: Float, target: Float, rate: Double): Float = (t + (target - t) * (1.0 - StrictMath.exp(-rate))).toFloat()

private val SLOW = Game.SLOW_TICK.toDouble()

/** Walls round the rectangle (x0, y0, w, h), outer size included. */
private fun ringWalls(m: GameMap, x0: Int, y0: Int, w: Int, h: Int) {
    for (x in x0 until x0 + w) for (y in y0 until y0 + h) {
        if (x == x0 || y == y0 || x == x0 + w - 1 || y == y0 + h - 1) m.setBuilding(Building(BuildDef.WOOD_WALL, x, y, true))
    }
}

/** Two indoor rooms joined by one door, with both sides in cells of [cellsA] and [cellsB]. */
private fun stepDoor(a: Float, b: Float, cellsA: Int, cellsB: Int, open: Float): FloatArray = Thermal.step(
    floatArrayOf(a, b), intArrayOf(cellsA, cellsB), booleanArrayOf(true, true), FloatArray(2),
    intArrayOf(0, 1), intArrayOf(1, 0), floatArrayOf(1f, 1f), intArrayOf(0, 0), floatArrayOf(open),
    RoofShares(FloatArray(2), FloatArray(2), FloatArray(2)), 0f,
)

/** A room of 5 by 5 outer walls with a 3 by 3 roofed floor, in a game whose colonists are removed. */
private fun cellRoom(seed: Long): Pair<Game, Pair<Int, Int>> {
    val g = Game(seed)
    g.startNewColony(Scenario.QUIET_ENCLAVE)
    g.pawns.clear()
    val m = g.map
    val x0 = g.homeX; val y0 = g.homeY - 2
    for (y in y0 - 1..y0 + 5) for (x in x0 - 1..x0 + 5) {
        val i = m.idx(x, y)
        m.terrain[i] = Terrain.SOIL; m.plant[i] = null; m.building[i] = null; m.items.remove(i); m.natRoof[i] = false
    }
    for (x in x0..x0 + 4) for (y in y0..y0 + 4) {
        if (x == x0 || y == y0 || x == x0 + 4 || y == y0 + 4) m.setBuilding(Building(BuildDef.WOOD_WALL, x, y, true))
    }
    m.setBuilding(Building(BuildDef.DOOR, x0 + 2, y0 + 4, true))
    for (y in y0 + 1..y0 + 3) for (x in x0 + 1..x0 + 3) m.setRoof(m.idx(x, y), true)
    m.rebuildRooms(10f)
    return g to (x0 to y0)
}

class HeatExchangeTest {

    // ------------------------------------------------------------------ walls, rock and water

    @Test fun aWallEdgeExchangesAtTheVanillaRatePerTick() {
        // 12 wall edges on a 9-cell room: exponent 12 x 0.00017 x 625 / 9 per slow step.
        val rate = 12.0 * 0.00017 * SLOW / 9.0
        assertEquals(settle(25f, 5f, rate), stepRoom(25f, 9, 5f, walls = 12), 1e-4f)
    }

    @Test fun aFarCellWithNoRoomCountsAtHalfTheGap() {
        // Twelve edges at half weight are the same as six at full weight.
        assertEquals(stepRoom(25f, 9, 5f, walls = 6, weight = 1f), stepRoom(25f, 9, 5f, walls = 12, weight = 0.5f), 1e-5f)
        assertEquals(settle(25f, 5f, 0.5 * 12.0 * 0.00017 * SLOW / 9.0), stepRoom(25f, 9, 5f, walls = 12, weight = 0.5f), 1e-4f)
    }

    @Test fun aRoomOfMoreCellsExchangesLessPerEdge() {
        val small = stepRoom(25f, 9, 5f, walls = 12)
        val big = stepRoom(25f, 36, 5f, walls = 12)
        assertTrue("a bigger room moves less: small $small, big $big", (25f - small) > (25f - big))
    }

    @Test fun aRoomWithNoLinksKeepsItsTemperature() {
        assertEquals(12f, stepRoom(12f, 9, -30f), 0f)
    }

    @Test fun uniformTemperaturesDoNotDrift() {
        assertEquals(5f, stepRoom(5f, 9, 5f, walls = 12), 1e-5f)
    }

    // ------------------------------------------------------------------ roofs

    @Test fun aThinRoofExchangesWithTheOutdoorsAtItsRate() {
        // 0.00005 per tick x 625 = 0.03125 per slow step, for a whole thin roof.
        val roof = RoofShares(floatArrayOf(0f), floatArrayOf(1f), floatArrayOf(0f))
        assertEquals(settle(20f, 0f, 0.00005 * SLOW), stepRoom(20f, 9, 0f, roof = roof), 1e-4f)
    }

    @Test fun anUnroofedShareExchangesFasterThanAThinRoof() {
        // 0.0007 per tick x 625 x 0.2 = 0.0875 per slow step, for a fifth of the room unroofed.
        val roof = RoofShares(floatArrayOf(0.2f), floatArrayOf(0f), floatArrayOf(0.8f))
        assertEquals(settle(5f, -15f, 0.0007 * SLOW * 0.2), stepRoom(5f, 25, -15f, roof = roof), 1e-4f)
    }

    @Test fun aLargeOutdoorDifferenceIsAmplifiedAsVanillaCountsIt() {
        // 160 degrees apart: 100 + 5 x 60 = 400 counts, so the roof's weight is 400 / 160 = 2.5 times its plain value.
        assertEquals(400f, Thermal.adjustedDifference(160f), 0f)
        assertEquals(-400f, Thermal.adjustedDifference(-160f), 0f)
        assertEquals(-50f, Thermal.adjustedDifference(-50f), 0f)
        val roof = RoofShares(floatArrayOf(0.2f), floatArrayOf(0f), floatArrayOf(0.8f))
        val plain = 0.0007 * SLOW * 0.2
        assertEquals(settle(-120f, 40f, plain * 2.5), stepRoom(-120f, 25, 40f, roof = roof), 1e-3f)
    }

    @Test fun aThickRoofPullsAWarmRoomDownToTheDeepGroundOnly() {
        // 0.00005 per tick x 625 = 0.03125 per slow step, toward 15 C from above.
        val thick = RoofShares(floatArrayOf(0f), floatArrayOf(0f), floatArrayOf(1f))
        assertEquals(settle(30f, 15f, 0.00005 * SLOW), stepRoom(30f, 9, -10f, roof = thick), 1e-4f)
        // A cold room under a thick roof is not warmed by it: the outdoors is at -10, and the room is at 10 C.
        assertEquals(10f, stepRoom(10f, 9, -10f, roof = thick), 0f)
    }

    // ------------------------------------------------------------------ doors

    @Test fun anOpenDoorPassesElevenTimesAsOftenAsAClosedOne() {
        // 625 / 34 against 625 / 375 passes per slow step: the ratio 375 / 34.
        assertEquals(375.0 / 34.0, (Thermal.doorPassesPerTick(1f) / Thermal.doorPassesPerTick(0f)).toDouble(), 1e-4)
        assertEquals((Thermal.doorPassesPerTick(0f) + Thermal.doorPassesPerTick(1f)) / 2f, Thermal.doorPassesPerTick(0.5f), 1e-9f)
    }

    @Test fun aDoorToTheOutdoorsClosesHalfAShareOfTheGapOnEachPass() {
        // A 9-cell room with a shut door to the outdoors: the share per pass is 1 / 9, the gap closes by half of it per pass,
        // and a closed door has 625 / 375 passes per slow step.
        val closed = -StrictMath.log(1.0 - (1.0 / 9.0) / 2.0) * (625.0 / 375.0)
        val got = Thermal.step(
            floatArrayOf(25f), intArrayOf(9), booleanArrayOf(true), floatArrayOf(0f),
            intArrayOf(0), intArrayOf(Thermal.OUTDOORS), floatArrayOf(1f), intArrayOf(0), floatArrayOf(0f), noRoof(), 5f,
        )[0]
        assertEquals(settle(25f, 5f, closed), got, 1e-4f)
    }

    @Test fun twoRoomsOnAnOpenDoorMeetAtTheirMeanWithoutSwapping() {
        // A at 0 and B at 100, 25 cells each, door open all the slow step. Each closes 1/25 of its gap to the mean per pass, so the gap
        // between them shrinks by (1 - 1/25) per pass, and the sides never cross.
        val passes = 625.0 / 34.0
        val gapAfter = 100.0 * StrictMath.pow(1.0 - 1.0 / 25.0, passes)
        val r = stepDoor(0f, 100f, 25, 25, 1f)
        assertTrue("A stays on its side: ${r[0]}", r[0] < r[1])
        assertEquals("the gap shrinks by the vanilla factor", gapAfter, (r[1] - r[0]).toDouble(), 0.05)
        assertEquals("the mean is kept", 50.0, ((r[0] + r[1]) / 2.0).toDouble(), 1e-3)
    }

    @Test fun aOneCellRoomOnAnOpenDoorStaysInsideTheRange() {
        // Share 1 per pass: the room reaches the mean of the two sides and stops there, never past it.
        val r = stepDoor(0f, 100f, 1, 25, 1f)
        assertTrue("between the sides: ${r[0]}", r[0] in 0f..100f)
        assertTrue("the small room reaches the mean: ${r[0]}", r[0] > 40f)
    }

    @Test fun aDoorToTheOutdoorsIsOneDoorLink() {
        // The door's far side is outdoors: one door link, from the room to the outdoor room.
        val (g, o) = cellRoom(41)
        val m = g.map
        val r = m.roomId[m.idx(o.first + 2, o.second + 2)]
        assertTrue(m.roomIndoor[r])
        assertEquals(1, m.doorCell.size)
        assertTrue(m.linkDoor.count { it >= 0 } == 1)
    }

    @Test fun aDoorWithNoRoomBehindItGivesNoExchange() {
        val m = GameMap(12, 10)
        ringWalls(m, 2, 2, 6, 5)
        m.setBuilding(Building(BuildDef.DOOR, 7, 4, true))
        m.setBuilding(Building(BuildDef.WOOD_WALL, 8, 4, true))
        for (y in 3..5) for (x in 3..6) m.setRoof(m.idx(x, y), true)
        m.rebuildRooms(10f)
        assertEquals("the door has no room behind it", 0, m.doorCell.size)
        assertFalse(m.linkDoor.any { it >= 0 })
    }

    @Test fun aDoorCellReadsTheMeanOfTheRoomsOnItsSides() {
        val m = GameMap(20, 14)
        ringWalls(m, 3, 3, 10, 6)
        for (y in 4..7) m.setBuilding(Building(BuildDef.WOOD_WALL, 8, y, true))
        m.setBuilding(Building(BuildDef.DOOR, 8, 5, true))
        for (y in 4..7) for (x in 4..11) if (m.building[m.idx(x, y)] == null) m.setRoof(m.idx(x, y), true)
        m.rebuildRooms(0f)
        val west = m.roomId[m.idx(5, 5)]; val east = m.roomId[m.idx(10, 5)]
        m.roomTemp[west] = 20f; m.roomTemp[east] = 10f
        assertEquals("the door reads the mean of its two rooms", 15f, m.tempAt(m.idx(8, 5), 0f), 1e-5f)
    }

    @Test fun aRockEdgeWithRockBehindItCountsAtHalfWeightToTheOutdoors() {
        val m = GameMap(16, 10)
        ringWalls(m, 2, 2, 5, 5)
        // The east side of the room is rock, two cells thick, so the far cell holds no room.
        for (y in 3..5) {
            m.removeBuilding(m.building[m.idx(6, y)]!!)
            m.terrain[m.idx(6, y)] = Terrain.ROCK
            m.terrain[m.idx(7, y)] = Terrain.ROCK
        }
        for (y in 3..5) for (x in 3..5) m.setRoof(m.idx(x, y), true)
        m.rebuildRooms(10f)
        val r = m.roomId[m.idx(5, 4)]
        val halfWeight = (0 until m.linkA.size).filter { m.linkA[it] == r && m.linkB[it] == Thermal.OUTDOORS && m.linkC[it] == Thermal.HALF_OUTDOOR_WEIGHT }
        assertEquals("one half-weight link per rock edge of the room", 3, halfWeight.size)
    }

    // ------------------------------------------------------------------ door state

    @Test fun aPawnSteppingIntoADoorHoldsItOpenForThreeHundredTicks() {
        val (g, o) = cellRoom(42)
        val m = g.map
        val d = m.idx(o.first + 2, o.second + 4)
        m.doorEntered(d)
        assertEquals(Thermal.DOOR_HOLD_OPEN, m.building[d]!!.doorHold)
        // The hold is spent over the next slow step: 300 of its 625 ticks.
        val first = g.doorOpenShares(m)
        assertEquals(300f / 625f, first[0], 1e-6f)
        assertEquals(0, m.building[d]!!.doorHold)
        assertEquals(0f, g.doorOpenShares(m)[0], 0f)
    }

    @Test fun aPawnStandingInADoorKeepsItOpenForTheCloseDelay() {
        val (g, o) = cellRoom(43)
        val m = g.map
        val p = Game(43L).let { h -> h.startNewColony(Scenario.QUIET_ENCLAVE); h.pawns.first() }
        p.x = o.first + 2; p.y = o.second + 4
        g.pawns.add(p)
        val share = g.doorOpenShares(m)[0]
        assertEquals(110f / 625f, share, 1e-6f)
    }

    @Test fun aSlideHatchIsOpenOnlyWhilePowered() {
        val (g, o) = cellRoom(44)
        val m = g.map
        val d = m.idx(o.first + 2, o.second + 4)
        m.setBuilding(Building(BuildDef.SHUTTER_DOOR, o.first + 2, o.second + 4, true).apply { powered = true })
        assertEquals(1f, g.doorOpenShares(m)[0], 0f)
        m.building[d]!!.powered = false
        assertEquals(0f, g.doorOpenShares(m)[0], 0f)
    }

    @Test fun aPawnEnteringADoorDoesNotHoldAShutterOpen() {
        val (g, o) = cellRoom(45)
        val m = g.map
        val d = m.idx(o.first + 2, o.second + 4)
        m.setBuilding(Building(BuildDef.SHUTTER_DOOR, o.first + 2, o.second + 4, true))
        m.doorEntered(d)
        assertEquals("the hatch has no hold", 0, m.building[d]!!.doorHold)
    }

    // ------------------------------------------------------------------ limits and determinism

    @Test fun roomTemperaturesStayWithinVanillaLimits() {
        assertEquals(1000f, stepRoom(995f, 9, 0f, heat = 1e6f), 0f)
        assertEquals(-273.15f, stepRoom(-270f, 9, -1000f, heat = -1e6f), 0f)
    }

    @Test fun aRoomOneCellSmallWithManyEdgesDoesNotOvershoot() {
        val r = stepRoom(80f, 1, 5f, walls = 20)
        assertTrue("between the outdoors and the room: $r", r in 5f..80f)
    }

    @Test fun aSaveKeepsTheDoorHoldAndTheRoomTemperatures() {
        val (g, o) = cellRoom(46)
        val m = g.map
        val d = m.idx(o.first + 2, o.second + 4)
        m.building[d]!!.doorHold = 123
        val room = m.roomId[m.idx(o.first + 2, o.second + 2)]
        m.roomTemp[room] = 7.5f
        val back = SaveGame.read(SaveGame.write(g))
        assertEquals(123, back.map.building[back.map.idx(o.first + 2, o.second + 4)]!!.doorHold)
        val backRoom = back.map.roomId[back.map.idx(o.first + 2, o.second + 2)]
        assertEquals(7.5f, back.map.roomTemp[backRoom], 0f)
    }

    @Test fun sameSeedAndInputsGiveTheSameTemperaturesWithDoorsAndColonists() {
        fun run(): FloatArray {
            val g = Game(47L)
            g.startNewColony(Scenario.QUIET_ENCLAVE)
            repeat(12 * Game.SLOW_TICK.toInt()) { g.step() }
            return g.map.roomTemp.copyOf()
        }
        val a = run(); val b = run()
        assertEquals(a.size, b.size)
        for (i in a.indices) assertEquals("room $i", a[i].toRawBits(), b[i].toRawBits())
    }
}
