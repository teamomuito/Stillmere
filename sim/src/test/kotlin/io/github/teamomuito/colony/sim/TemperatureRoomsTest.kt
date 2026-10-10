package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** An empty map with a ring of wall around the rectangle (x0, y0, w, h), outer size including the walls. */
private fun GameMap.wallRing(x0: Int, y0: Int, w: Int, h: Int, wall: BuildDef = BuildDef.WOOD_WALL) {
    for (x in x0 until x0 + w) for (y in y0 until y0 + h) {
        if (x == x0 || y == y0 || x == x0 + w - 1 || y == y0 + h - 1) setBuilding(Building(wall, x, y, true))
    }
}

private fun GameMap.put(d: BuildDef, x: Int, y: Int, powered: Boolean = true): Building {
    val b = Building(d, x, y, true)
    b.powered = powered
    setBuilding(b)
    return b
}

private fun GameMap.roomAt(x: Int, y: Int) = roomId[idx(x, y)]

/** A game with one closed 3x3 room (outer 5x5) with a door in the south wall, on cleared ground. */
private fun heatedRoomGame(seed: Long, withDoor: Boolean = true): Pair<Game, IntArray> {
    val g = Game(seed)
    g.startNewColony(Scenario.LOST_TRIBE)
    val m = g.map
    val x0 = g.homeX; val y0 = g.homeY - 2
    for (y in y0 - 1..y0 + 5) for (x in x0 - 1..x0 + 5) {
        val i = m.idx(x, y)
        m.terrain[i] = Terrain.SOIL; m.plant[i] = null; m.building[i] = null; m.items.remove(i); m.natRoof[i] = false
    }
    m.wallRing(x0, y0, 5, 5)
    if (withDoor) m.put(BuildDef.DOOR, x0 + 2, y0 + 4) else m.put(BuildDef.WOOD_WALL, x0 + 2, y0 + 4)
    m.rebuildRooms(0f)
    return g to intArrayOf(x0, y0)
}

class TemperatureRoomsTest {

    // ----------------------------------------------------------------------------------------------- outdoor temperature

    private fun tickAt(day: Int, hour: Float): Long = day.toLong() * TICKS_PER_DAY + (hour * TICKS_PER_HOUR).toLong()
    private fun temp(day: Int, hour: Float, b: Biome = Biome.TEMPERATE) = Climate.outdoorTemp(b, tickAt(day, hour), 0f, Weather.CLEAR)
    private fun dayMean(day: Int, b: Biome = Biome.TEMPERATE) = (0 until 24).map { temp(day, it.toFloat(), b) }.average()

    @Test fun outdoorTemperatureFollowsTheSeasons() {
        val spring = dayMean(7); val summer = dayMean(22); val fall = dayMean(37); val winter = dayMean(52)
        assertTrue("summer $summer > spring $spring", summer > spring)
        assertTrue("spring $spring > fall $fall", spring > fall)
        assertTrue("fall $fall > winter $winter", fall > winter)
        // Hand calculation, temperate day 22 (summer, 7 days in): 24 + (10 - 24) * (7/15) * 0.5 = 20.7333
        assertEquals(20.7333, dayMean(22), 0.01)
        // A year later is the same weather.
        assertEquals(temp(22, 9f), temp(22 + DAYS_PER_YEAR, 9f), 0f)
    }

    @Test fun outdoorTemperatureFollowsDayAndNight() {
        val hours = (0 until 24).map { it to temp(22, it.toFloat()) }
        assertEquals("coldest at 03:00", 3, hours.minByOrNull { it.second }!!.first)
        assertEquals("warmest at 15:00", 15, hours.maxByOrNull { it.second }!!.first)
        assertEquals("swing of +/-7 C", 14.0, (temp(22, 15f) - temp(22, 3f)).toDouble(), 0.001)
        assertEquals("dry biomes swing +/-10 C", 20.0, (temp(22, 15f, Biome.DESERT) - temp(22, 3f, Biome.DESERT)).toDouble(), 0.001)
        // The cycle repeats every day.
        assertEquals(temp(22, 6f), temp(23, 6f) - (Climate.seasonalBase(Biome.TEMPERATE, 23) - Climate.seasonalBase(Biome.TEMPERATE, 22)), 0.001f)
    }

    @Test fun eventsAndWeatherShiftTheOutdoorTemperature() {
        val t = tickAt(22, 12f)
        val clear = Climate.outdoorTemp(Biome.TEMPERATE, t, 0f, Weather.CLEAR)
        assertEquals(clear - 17f, Climate.outdoorTemp(Biome.TEMPERATE, t, -17f, Weather.CLEAR), 0.001f)
        assertEquals(clear - 3f, Climate.outdoorTemp(Biome.TEMPERATE, t, 0f, Weather.SNOW), 0.001f)
    }

    @Test fun theGameUsesTheClimateModel() {
        val g = Game(4); g.startNewColony(Scenario.LOST_TRIBE)
        repeat(3 * TICKS_PER_HOUR) { g.step() }
        assertEquals(Climate.outdoorTemp(g.map.biome, g.tick, g.tempOffset, g.weather), g.outdoorTemp(), 0f)
    }

    // ----------------------------------------------------------------------------------------------- room detection

    /**
     * Outer wall x 3..12, y 3..8 (10 x 6). A wall across x = 8. Interior: x 4..7 (4 wide) and x 9..11 (3 wide), y 4..7 (4 tall),
     * so the west side has 16 cells and the east side 12. The cross wall has a door at (8, 5).
     */
    private fun twoRoomPlan(crossing: BuildDef?): GameMap {
        val m = GameMap(20, 14)
        m.wallRing(3, 3, 10, 6)
        for (y in 4..7) m.put(BuildDef.WOOD_WALL, 8, y)
        if (crossing != null) m.put(crossing, 8, 5) else m.removeBuilding(m.building[m.idx(8, 5)]!!)
        m.rebuildRooms(5f)
        return m
    }

    @Test fun aDoorSplitsTheFloorPlanIntoTwoRooms() {
        val m = twoRoomPlan(BuildDef.DOOR)
        val west = m.roomAt(5, 5); val east = m.roomAt(10, 5)
        assertTrue(west >= 0 && east >= 0)
        assertNotEquals("the door separates the rooms", west, east)
        assertEquals(16, m.roomSize[west])
        assertEquals(12, m.roomSize[east])
        assertTrue(m.roomIndoor[west] && m.roomIndoor[east])
        assertEquals("the door cell belongs to neither room", -1, m.roomAt(8, 5))
        // Every interior cell is in one of the two rooms.
        for (y in 4..7) for (x in 4..11) if (x != 8) assertTrue(m.roomAt(x, y) == west || m.roomAt(x, y) == east)
    }

    @Test fun anOpeningInsteadOfADoorJoinsTheRooms() {
        val m = twoRoomPlan(null)
        val west = m.roomAt(5, 5)
        assertEquals("one room", west, m.roomAt(10, 5))
        assertEquals("16 + 12 + the open cell", 29, m.roomSize[west])
        assertTrue(m.roomIndoor[west])
    }

    @Test fun aWallInsteadOfADoorKeepsTheRoomsApart() {
        val m = twoRoomPlan(BuildDef.WOOD_WALL)
        assertNotEquals(m.roomAt(5, 5), m.roomAt(10, 5))
        assertEquals(16, m.roomSize[m.roomAt(5, 5)])
    }

    @Test fun aDoorToTheOutsideDoesNotMakeARoomOutdoor() {
        val m = twoRoomPlan(BuildDef.DOOR)
        m.put(BuildDef.DOOR, 3, 5)
        m.rebuildRooms(5f)
        val west = m.roomAt(5, 5)
        assertTrue("a closed-in room with an outside door is still indoors", m.roomIndoor[west])
        assertEquals(16, m.roomSize[west])
        val outside = m.roomAt(0, 0)
        assertFalse(m.roomIndoor[outside])
    }

    @Test fun aBrokenWallLetsTheOutdoorsIn() {
        val m = twoRoomPlan(BuildDef.DOOR)
        m.removeBuilding(m.building[m.idx(3, 6)]!!)
        m.rebuildRooms(5f)
        assertFalse("a gap in the outer wall makes the west room outdoor", m.roomIndoor[m.roomAt(5, 5)])
        assertTrue("the east room is still sealed", m.roomIndoor[m.roomAt(10, 5)])
    }

    // ----------------------------------------------------------------------------------------------- heat

    @Test fun anEnclosedRoomWithAHeaterHoldsItsTargetTemperature() {
        val (g, o) = heatedRoomGame(11)
        val m = g.map
        m.put(BuildDef.HEATER, o[0] + 1, o[1] + 1)
        m.rebuildRooms(0f)
        val r = m.roomAt(o[0] + 1, o[1] + 2)
        assertTrue(m.roomIndoor[r]); assertEquals(9, m.roomSize[r])
        val seen = ArrayList<Float>()
        repeat(300) { g.roomClimate(0f); if (it >= 250) seen.add(m.roomTemp[r]) }
        for (t in seen) assertTrue("room at $t C should stay near ${Thermal.SETPOINT}", t in 19.5f..23.5f)
        // The same room without power sinks to the outdoor temperature.
        m.building[m.idx(o[0] + 1, o[1] + 1)]!!.powered = false
        repeat(300) { g.roomClimate(0f) }
        assertEquals(0f, m.roomTemp[r], 0.5f)
    }

    @Test fun aCoolerHoldsAHotRoomAtItsTarget() {
        val (g, o) = heatedRoomGame(12)
        val m = g.map
        m.put(BuildDef.COOLER, o[0] + 1, o[1] + 1)
        m.rebuildRooms(40f)
        val r = m.roomAt(o[0] + 1, o[1] + 3)
        repeat(300) { g.roomClimate(40f) }
        assertEquals(Thermal.SETPOINT, m.roomTemp[r], 2.5f)
        assertTrue(m.roomTemp[r] < 30f)
    }

    @Test fun aDoorLeaksMoreHeatThanAWall() {
        val warm = { withDoor: Boolean ->
            val (g, o) = heatedRoomGame(13, withDoor)
            val m = g.map
            val r = m.roomAt(o[0] + 1, o[1] + 1)
            m.roomTemp[r] = 20f
            repeat(10) { g.roomClimate(-10f) }
            m.roomTemp[r]
        }
        val sealed = warm(false); val withDoor = warm(true)
        assertTrue("sealed $sealed should be warmer than with a door $withDoor", sealed > withDoor)
        assertTrue(withDoor < 20f && sealed < 20f)
    }

    @Test fun heatMovesBetweenNeighbouringRoomsThroughADoor() {
        val m = twoRoomPlan(BuildDef.DOOR)
        val west = m.roomAt(5, 5); val east = m.roomAt(10, 5)
        m.roomTemp[west] = 25f; m.roomTemp[east] = 5f
        val heat = FloatArray(m.roomTemp.size)
        var temps = m.roomTemp
        repeat(40) { temps = Thermal.step(temps, m.roomSize, m.roomIndoor, heat, m.linkA, m.linkB, m.linkC, 5f) }
        assertTrue("the cold room warms through the door: ${temps[east]}", temps[east] > 5f)
        assertTrue("the warm room cools: ${temps[west]}", temps[west] < 25f)
        assertTrue("the east room, with fewer cells, sits closer to the west one than the outdoors", temps[east] > temps[west] - 20f)
        // A room that has no outside link cannot gain heat from nowhere: with the outdoors at 5 both only fall.
        assertTrue(temps[west] <= 25f && temps[east] <= 25f)
    }

    @Test fun roomsDoNotDriftWhenEverythingIsAtTheSameTemperature() {
        val m = twoRoomPlan(BuildDef.DOOR)
        for (r in m.roomTemp.indices) m.roomTemp[r] = 5f
        val heat = FloatArray(m.roomTemp.size)
        val next = Thermal.step(m.roomTemp, m.roomSize, m.roomIndoor, heat, m.linkA, m.linkB, m.linkC, 5f)
        for (r in next.indices) assertEquals(5f, next[r], 1e-5f)
    }

    @Test fun sameSeedAndInputsGiveTheSameTemperatures() {
        fun run(): FloatArray {
            val (g, o) = heatedRoomGame(21)
            g.map.put(BuildDef.HEATER, o[0] + 1, o[1] + 1)
            g.map.rebuildRooms(0f)
            repeat(20 * Game.SLOW_TICK.toInt()) { g.step() }
            return g.map.roomTemp.copyOf()
        }
        val a = run(); val b = run()
        assertEquals(a.size, b.size)
        for (i in a.indices) assertEquals("room $i", a[i].toRawBits(), b[i].toRawBits())
    }

    // ----------------------------------------------------------------------------------------------- room stats

    @Test fun roomStatsMatchAHandCalculatedLayout() {
        // 4 x 3 interior (12 cells), wood floor everywhere, one small sculpture, one cell with filth 2.
        val m = GameMap(12, 10)
        m.wallRing(2, 2, 6, 5)
        for (y in 3..5) for (x in 3..6) m.floor[m.idx(x, y)] = BuildDef.WOOD_FLOOR
        m.put(BuildDef.SCULPTURE_SMALL, 4, 4)
        m.filth[m.idx(5, 3)] = 2
        m.rebuildRooms(10f)
        m.computeRoomStats()
        val r = m.roomAt(4, 3)
        assertEquals(12, m.roomSize[r])
        // beauty: (12 * 0.3 + 6 - 2 * 0.9) / 12 = 7.8 / 12
        assertEquals(0.65f, m.roomBeauty[r], 1e-4f)
        // cleanliness: -2 / 12
        assertEquals(-1f / 6f, m.roomClean[r], 1e-4f)
        // wealth: the sculpture's value
        assertEquals(BuildDef.SCULPTURE_SMALL.totalCost, m.roomWealth[r], 1e-3f)
        assertEquals(80f, m.roomWealth[r], 1e-3f)
        // impressiveness: 0.65 * 6 * (12 / 14) + 12 * 0.18 + 80 / 450 + (-1/6) * 2.5
        assertEquals(5.26397f, m.roomImpress[r], 1e-3f)
        assertEquals("no beds, tables or benches", 0, m.roomRole[r])
    }

    @Test fun aDiningRoomHasARoleAndABareFloorIsUgly() {
        // 3 x 3 interior with no floor, a 2 x 2 table and one chair.
        val m = GameMap(12, 10)
        m.wallRing(2, 2, 5, 5)
        m.put(BuildDef.TABLE, 3, 3)
        m.put(BuildDef.CHAIR, 5, 3)
        m.rebuildRooms(10f)
        m.computeRoomStats()
        val r = m.roomAt(3, 5)
        assertEquals(9, m.roomSize[r])
        // beauty: 9 bare floor cells at -0.2, the table (0.4, counted once) and the chair (0.3): (-1.8 + 0.4 + 0.3) / 9
        assertEquals(-1.1f / 9f, m.roomBeauty[r], 1e-4f)
        assertEquals(3, m.roomRole[r])
        assertEquals(BuildDef.TABLE.totalCost + BuildDef.CHAIR.totalCost, m.roomWealth[r], 1e-3f)
    }

    @Test fun outdoorCellsAndPlantsAndRoofAreScored() {
        val m = GameMap(12, 10)
        m.wallRing(2, 2, 4, 4) // interior 2 x 2
        // A growing plant adds 0.4 to its cell; a mountain roof takes 0.1 off.
        m.natRoof[m.idx(3, 3)] = true
        m.plant[m.idx(4, 4)] = Plant(PlantType.entries.first { !it.isTree }, 4, 4, 1f)
        m.rebuildRooms(10f)
        m.computeRoomStats()
        val r = m.roomAt(3, 3)
        assertEquals(4, m.roomSize[r])
        // 4 bare cells (-0.8) - 0.1 (roof) + 0.4 (plant) = -0.5, over 4 cells
        assertEquals(-0.125f, m.roomBeauty[r], 1e-4f)
    }
}
