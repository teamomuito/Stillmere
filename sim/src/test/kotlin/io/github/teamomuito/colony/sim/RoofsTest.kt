package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The walls of a rectangle (x0, y0, w, h), outer size, as built wall buildings. */
private fun GameMap.walls(x0: Int, y0: Int, w: Int, h: Int, from: Int = 0) {
    for (x in x0 until x0 + w) for (y in y0 until y0 + h) {
        if (x == x0 || y == y0 || x == x0 + w - 1 || y == y0 + h - 1) setBuilding(Building(BuildDef.WOOD_WALL, x, y, true))
    }
}

/** A plain map of soil, sized so no room touches the edge. */
private fun plain(w: Int = 40, h: Int = 40): GameMap = GameMap(w, h)

private fun GameMap.roofRect(x0: Int, y0: Int, w: Int, h: Int) {
    for (x in x0 until x0 + w) for (y in y0 until y0 + h) setRoof(idx(x, y), true)
}

private fun GameMap.inside(x: Int, y: Int): Boolean = roomIndoorAt(idx(x, y))

class RoofsTest {

    // ------------------------------------------------------------------ the indoor rule (Verse/Room.cs:103-108)

    @Test fun theQuarterRuleIsOutdoorAtAQuarterOpenAndAboveAndNeverOnTheEdge() {
        assertTrue(plain().roomIsIndoor(4, 0, false))
        assertFalse("one open cell of four is a quarter: outdoors", plain().roomIsIndoor(4, 1, false))
        assertTrue("one open of five is under a quarter", plain().roomIsIndoor(5, 1, false))
        assertFalse("two open of five is outdoors", plain().roomIsIndoor(5, 2, false))
        assertTrue("24 open of 100 is indoors", plain().roomIsIndoor(100, 24, false))
        assertFalse("25 open of 100 is a quarter: outdoors", plain().roomIsIndoor(100, 25, false))
        assertTrue("25 open of 101 is indoors", plain().roomIsIndoor(101, 25, false))
        assertFalse("a room touching the edge is outdoors whatever its roof", plain().roomIsIndoor(9, 0, true))
    }

    @Test fun aSealedFourCellRoomIsIndoorOnlyWhenFullyRoofed() {
        val m = plain()
        m.walls(5, 5, 4, 4) // interior 6..7 by 6..7
        m.rebuildRooms(0f)
        assertFalse("no roof: an enclosed room is outdoors now", m.inside(6, 6))
        m.setRoof(m.idx(6, 6), true); m.setRoof(m.idx(7, 6), true); m.setRoof(m.idx(6, 7), true)
        m.rebuildRooms(0f)
        assertFalse("three of four roofed (75 percent) is outdoors", m.inside(6, 6))
        m.setRoof(m.idx(7, 7), true)
        m.rebuildRooms(0f)
        assertTrue("four of four roofed is indoors", m.inside(6, 6))
    }

    @Test fun seventyFivePercentRoofedIsOutdoorsAndSeventySixIsIndoors() {
        val m = plain(40, 40)
        m.walls(5, 5, 12, 12) // 10 by 10 inside, 100 cells
        var roofed = 0
        m.rebuildRooms(0f)
        for (y in 6..15) for (x in 6..15) {
            if (roofed < 75) { m.setRoof(m.idx(x, y), true); roofed++ }
        }
        m.rebuildRooms(0f)
        assertFalse("75 of 100 roofed is 75 percent or less: outdoors", m.inside(6, 6))
        m.setRoof(m.idx(15, 15), true)
        m.rebuildRooms(0f)
        assertTrue("76 of 100 roofed is indoors", m.inside(6, 6))
    }

    @Test fun naturalRoofCountsTowardTheRoofedShare() {
        val m = plain()
        m.walls(5, 5, 4, 4)
        m.natRoof[m.idx(6, 6)] = true; m.natRoof[m.idx(7, 6)] = true; m.natRoof[m.idx(6, 7)] = true
        m.rebuildRooms(0f)
        assertFalse("three natural cells and one open: outdoors", m.inside(6, 6))
        m.natRoof[m.idx(7, 7)] = true
        m.rebuildRooms(0f)
        assertTrue("natural roof on every cell: indoors", m.inside(6, 6))
    }

    @Test fun aRoomWithoutRoofIsOutdoorEvenWhenSmall() {
        val m = plain()
        m.walls(5, 5, 5, 5) // nine cells: indoors under the old rule, outdoors now
        m.rebuildRooms(0f)
        val r = m.roomId[m.idx(7, 7)]
        assertFalse(m.roomIndoor[r])
        assertFalse("no roof, not roofed", m.roofed(m.idx(7, 7)))
    }

    // ------------------------------------------------------------------ support (Verse/RoofCollapseUtility.cs:5-30)

    @Test fun aRoofSixCellsFromAWallIsHeldAndSevenIsNotEvenWithARoofedChain() {
        val m = plain()
        m.setBuilding(Building(BuildDef.WOOD_WALL, 10, 20, true))
        for (x in 11..16) m.setRoof(m.idx(x, 20), true)
        assertTrue("six cells from the wall, held through the chain", m.heldInRange(m.idx(16, 20)))
        m.setRoof(m.idx(17, 20), true)
        assertFalse("seven cells from the wall is past the support radius", m.heldInRange(m.idx(17, 20)))
    }

    @Test fun aRoofNextToAWallCanBeBuiltAndOneAFarCellCannot() {
        val m = plain()
        m.walls(5, 5, 8, 8)
        assertTrue("next to the wall, the cell is held", m.canBuildRoofAt(m.idx(6, 6)))
        assertFalse("the middle of an empty room has nothing to hold it", m.canBuildRoofAt(m.idx(9, 9)))
        m.setRoof(m.idx(6, 6), true)
        assertFalse("a cell that is roofed already", m.canBuildRoofAt(m.idx(6, 6)))
    }

    @Test fun naturalRockHoldsARoof() {
        val m = plain()
        m.terrain[m.idx(20, 20)] = Terrain.ROCK
        assertTrue(m.isRoofHolder(m.idx(20, 20)))
        assertTrue("a soil cell next to rock is held", m.canBuildRoofAt(m.idx(21, 20)))
    }

    // ------------------------------------------------------------------ collapse (Verse/RoofCollapseCellsFinder.cs)

    @Test fun removingOneWallOfAWellRoofedRoomDropsNothing() {
        val m = plain()
        m.walls(5, 5, 5, 5)
        m.roofRect(6, 6, 3, 3)
        m.removeBuilding(m.building[m.idx(5, 7)]!!)
        assertTrue("the other walls hold every roof", (6..8).all { x -> (6..8).all { y -> m.roof[m.idx(x, y)] } })
        assertTrue(m.pendingRoofFalls.isEmpty())
    }

    @Test fun aStripLosingItsWestWallFallsWithinTheRadiusAndTheRestStays() {
        val m = plain()
        val west = Building(BuildDef.WOOD_WALL, 10, 20, true)
        m.setBuilding(west)
        // A second wall far to the east holds the far end of the strip, out of reach of the cells near the west wall.
        m.setBuilding(Building(BuildDef.WOOD_WALL, 27, 20, true))
        for (x in 11..26) m.setRoof(m.idx(x, 20), true)
        m.removeBuilding(west)
        val fell = m.pendingRoofFalls.toSet()
        assertEquals("the six cells within the radius of the removed wall fall", (11..16).map { m.idx(it, 20) }.toSet(), fell)
        assertTrue("the held cells stay", (17..26).all { m.roof[m.idx(it, 20)] })
    }

    @Test fun removingTheWallThroughTheApiDropsTheUnheldStrip() {
        val m = plain()
        val wall = Building(BuildDef.WOOD_WALL, 10, 20, true)
        m.setBuilding(wall)
        for (x in 11..16) m.setRoof(m.idx(x, 20), true)
        m.removeBuilding(wall)
        assertTrue("a strip with no holder left falls", (11..16).none { m.roof[m.idx(it, 20)] })
        assertEquals("six cells queued", 6, m.pendingRoofFalls.size)
    }

    // Vanilla's group flood also marks a natural roof that is not thick when its group loses support. Stillmere keeps natural
    // roof (docs/fidelity/roofs.md, natural roof row); this test pins that choice.
    @Test fun naturalRoofNeverFalls() {
        val m = plain()
        val wall = Building(BuildDef.WOOD_WALL, 10, 20, true)
        m.setBuilding(wall)
        for (x in 11..14) { m.natRoof[m.idx(x, 20)] = true }
        m.setRoof(m.idx(15, 20), true)
        m.removeBuilding(wall)
        assertTrue("natural roof stays", (11..14).all { m.natRoof[m.idx(it, 20)] && m.roofed(m.idx(it, 20)) })
        assertFalse("the built roof falls", m.roof[m.idx(15, 20)])
    }

    @Test fun roofsOutsideTheRadiusOfTheRemovedHolderAreUntouched() {
        val m = plain()
        val wall = Building(BuildDef.WOOD_WALL, 10, 20, true)
        m.setBuilding(wall)
        m.setRoof(m.idx(11, 20), true)
        m.setRoof(m.idx(20, 25), true) // far from the wall and not connected to it
        m.removeBuilding(wall)
        assertFalse(m.roof[m.idx(11, 20)])
        assertTrue("the roof far from the wall stays", m.roof[m.idx(20, 25)])
    }

    @Test fun aDoorHoldsARoofLikeAWall() {
        val m = plain()
        val door = Building(BuildDef.DOOR, 10, 20, true)
        m.setBuilding(door)
        assertTrue(m.isRoofHolder(m.idx(10, 20)))
        m.removeBuilding(door)
        assertFalse("a door that is gone holds nothing", m.isRoofHolder(m.idx(10, 20)))
    }

    // ------------------------------------------------------------------ crush (Verse/RoofCollapserImmediate.cs:127-152)

    /** A colony, with a strip of roof on cleared ground and one colonist standing under it. */
    private fun stripGame(seed: Long): Triple<Game, Pawn, Building> {
        val g = Game(seed)
        g.startNewColony(Scenario.QUIET_ENCLAVE)
        val m = g.map
        val x0 = g.homeX; val y0 = g.homeY - 6
        for (y in y0 - 9..y0 + 9) for (x in x0 - 9..x0 + 12) {
            val i = m.idx(x, y)
            m.terrain[i] = Terrain.SOIL; m.plant[i] = null; m.building[i] = null; m.items.remove(i); m.natRoof[i] = false; m.roof[i] = false
        }
        val wall = Building(BuildDef.WOOD_WALL, x0, y0, true)
        m.setBuilding(wall)
        for (x in x0 + 1..x0 + 6) m.setRoof(m.idx(x, y0), true)
        val p = g.pawns.first { it.colonist }
        p.x = x0 + 3; p.y = y0
        m.rebuildRooms(0f)
        return Triple(g, p, wall)
    }

    @Test fun aColonistUnderAFallingRoofTakesACrushHit() {
        val (g, p, wall) = stripGame(4141L)
        val before = p.healthFraction()
        val injuries = p.injuries.size
        g.map.removeBuilding(wall)
        g.resolveRoofCollapses()
        assertTrue("the crush hit lowers health", p.healthFraction() < before)
        assertEquals("one injury is added", injuries + 1, p.injuries.size)
        assertTrue("the queue is drained", g.map.pendingRoofFalls.isEmpty())
    }

    @Test fun theCrushRollIsTheSameForTheSameSeed() {
        val (g1, p1, w1) = stripGame(777L)
        val (g2, p2, w2) = stripGame(777L)
        g1.map.removeBuilding(w1); g1.resolveRoofCollapses()
        g2.map.removeBuilding(w2); g2.resolveRoofCollapses()
        assertEquals(p1.healthFraction(), p2.healthFraction(), 0f)
        assertEquals(p1.injuries.map { it.severity }, p2.injuries.map { it.severity })
    }

    @Test fun theCrushIsACrushWoundAndTheRollIsFifteenToThirty() {
        // D: the range 15 to 30 is cited to Verse/RoofCollapserImmediate.cs:10 (docs/fidelity/roofs.md, constants table).
        assertEquals("the crush range minimum (D)", 15, ROOF_CRUSH_MIN)
        assertEquals("the crush range maximum (D)", 30, ROOF_CRUSH_MAX)
        val (g, p, wall) = stripGame(99L)
        g.map.removeBuilding(wall)
        g.resolveRoofCollapses()
        assertEquals(DamageKind.CRUSH, p.injuries.last().kind)
    }

    // ------------------------------------------------------------------ building (JobDriver_BuildRoof, WorkGiver_BuildRoof)

    @Test fun aDesignatedRoofIsBuiltByAColonistAndTheRoomBecomesIndoors() {
        val g = Game(2024L)
        g.startNewColony(Scenario.QUIET_ENCLAVE)
        g.mentalBreaksEnabled = false
        g.nextRaid = Long.MAX_VALUE; g.nextMisc = Long.MAX_VALUE; g.nextWanderer = Long.MAX_VALUE; g.nextTrader = Long.MAX_VALUE
        val m = g.map
        val x0 = g.homeX; val y0 = g.homeY - 6
        for (y in y0 - 9..y0 + 12) for (x in x0 - 9..x0 + 12) {
            val i = m.idx(x, y)
            m.terrain[i] = Terrain.SOIL; m.plant[i] = null; m.building[i] = null; m.items.remove(i); m.natRoof[i] = false; m.roof[i] = false
        }
        // A room of 4 by 4 inside, walled, with the roofs designated.
        m.walls(x0, y0, 6, 6)
        m.rebuildRooms(g.outdoorTemp())
        for (y in y0 + 1..y0 + 4) for (x in x0 + 1..x0 + 4) assertTrue("designated", g.designate(x, y, Desig.ROOF))
        assertFalse("a cell far from any holder cannot be roofed yet", m.canBuildRoofAt(m.idx(x0 + 9, y0 + 9)))
        // The colonist starts inside the walls, since a roof inside is reached only from inside.
        val colonist = g.colonists.first()
        colonist.x = x0 + 2; colonist.y = y0 + 2
        var steps = 0
        while (steps < 40000 && (y0 + 1..y0 + 4).any { y -> (x0 + 1..x0 + 4).any { x -> !m.roofed(m.idx(x, y)) } }) { g.step(); steps++ }
        assertTrue("every designated cell is roofed", (y0 + 1..y0 + 4).all { y -> (x0 + 1..x0 + 4).all { x -> m.roofed(m.idx(x, y)) } })
        assertTrue("the designations are cleared", (y0 + 1..y0 + 4).all { y -> (x0 + 1..x0 + 4).all { x -> m.desig[m.idx(x, y)].toInt() == Desig.NONE } })
        m.rebuildRooms(g.outdoorTemp())
        assertTrue("the room is indoors once roofed", m.roomIndoorAt(m.idx(x0 + 2, y0 + 2)))
    }

    @Test fun aRoofJobRoofsEveryHeldCellOfItsBlockInOnePass() {
        // A wall at (10, 20) holds the centre (11, 20) of a job. The block is x 10 to 12, y 19 to 21. The cell above the centre, (11, 21),
        // and the cell beside it, (12, 20), are held only through the centre, and (11, 19) is scanned before the centre. Stillmere scans
        // the block until no cell changes (C, docs/fidelity/roofs.md), so all six designated cells of the block are roofed by this job.
        // Vanilla's single pass in GenAdj order would leave (11, 19) and the others scanned before the centre for a later job: not adopted.
        val m = plain()
        m.setBuilding(Building(BuildDef.WOOD_WALL, 10, 20, true))
        for (x in 11..12) for (y in 19..21) m.desig[m.idx(x, y)] = Desig.ROOF.toByte()
        m.desig[m.idx(13, 20)] = Desig.ROOF.toByte() // designated, but outside this job's block
        m.finishRoofBlock(11, 20)
        assertTrue("the block centre is roofed by the job", m.roof[m.idx(11, 20)])
        assertTrue("the cell above the centre is roofed in the same job", m.roof[m.idx(11, 21)])
        assertTrue("the cell beside the centre is roofed in the same job", m.roof[m.idx(12, 20)])
        assertTrue("the cell below the centre is roofed in the same job", m.roof[m.idx(11, 19)])
        assertTrue("every designated cell of the block is roofed", (11..12).all { x -> (19..21).all { y -> m.roof[m.idx(x, y)] } })
        assertFalse("a designated cell outside the block is not reached by this job", m.roof[m.idx(13, 20)])
        assertEquals("that cell keeps its designation", Desig.ROOF, m.desig[m.idx(13, 20)].toInt())
        assertEquals("roofed cells are cleared of their designation", Desig.NONE, m.desig[m.idx(11, 20)].toInt())
        assertEquals("roofed cells are cleared of their designation", Desig.NONE, m.desig[m.idx(11, 21)].toInt())
    }

    @Test fun aTreeInsideTheBlockStaysUnroofedAndTheRestOfTheBlockRoofs() {
        // The same block with a tree at (12, 21): the tree cell is skipped and keeps its designation, and the other five roof.
        val m = plain()
        m.setBuilding(Building(BuildDef.WOOD_WALL, 10, 20, true))
        m.plant[m.idx(12, 21)] = Plant(PlantType.OAK, 12, 21, 1f)
        for (x in 11..12) for (y in 19..21) m.desig[m.idx(x, y)] = Desig.ROOF.toByte()
        m.finishRoofBlock(11, 20)
        assertFalse("a tree cell is not roofed", m.roof[m.idx(12, 21)])
        assertEquals("a tree cell keeps its designation", Desig.ROOF, m.desig[m.idx(12, 21)].toInt())
        assertTrue("the rest of the block is roofed", (11..12).all { x -> (19..21).all { y -> (x to y) == (12 to 21) || m.roof[m.idx(x, y)] } })
    }

    // ------------------------------------------------------------------ the designation the app makes (Game.designate, Desig.ROOF)

    @Test fun aRoofDesignationIsKeptOnOpenGroundAndRefusedOnRockAndOnARoofedCell() {
        // The app's roof tool calls Game.designate(x, y, Desig.ROOF) for each tapped or dragged cell, as the other tools do. Open ground
        // takes the mark, and the status text reads Desig.ROOF from it. Rock and an already roofed cell are refused, so they keep no mark.
        val g = Game(2025L)
        g.startNewColony(Scenario.QUIET_ENCLAVE)
        val m = g.map
        val x0 = g.homeX; val y0 = g.homeY - 6
        val open = m.idx(x0, y0); val rock = m.idx(x0 + 1, y0); val roofed = m.idx(x0 + 2, y0)
        m.terrain[open] = Terrain.SOIL; m.building[open] = null; m.natRoof[open] = false; m.roof[open] = false
        m.terrain[rock] = Terrain.ROCK; m.building[rock] = null; m.natRoof[rock] = false; m.roof[rock] = false
        m.terrain[roofed] = Terrain.SOIL; m.building[roofed] = null; m.natRoof[roofed] = false; m.roof[roofed] = true
        assertTrue("open ground takes a roof designation", g.designate(x0, y0, Desig.ROOF))
        assertEquals("the open cell is marked for a roof", Desig.ROOF, m.desig[open].toInt())
        assertFalse("rock cannot be roofed, so it is refused", g.designate(x0 + 1, y0, Desig.ROOF))
        assertEquals("the rock keeps no mark", Desig.NONE, m.desig[rock].toInt())
        assertFalse("a cell that is already roofed is refused", g.designate(x0 + 2, y0, Desig.ROOF))
        assertEquals("the roofed cell keeps no mark", Desig.NONE, m.desig[roofed].toInt())
        g.clearDesignation(x0, y0)
        assertEquals("cancelling clears the roof mark", Desig.NONE, m.desig[open].toInt())
    }

    // ------------------------------------------------------------------ taking a roof down (Desig.UNROOF)

    @Test fun onlyABuiltRoofCanBeMarkedForRemoval() {
        val g = Game(2025L)
        g.startNewColony(Scenario.QUIET_ENCLAVE)
        val m = g.map
        val x0 = g.homeX; val y0 = g.homeY - 6
        val built = m.idx(x0, y0); val natural = m.idx(x0 + 1, y0); val none = m.idx(x0 + 2, y0)
        for (i in intArrayOf(built, natural, none)) { m.terrain[i] = Terrain.SOIL; m.building[i] = null; m.roof[i] = false; m.natRoof[i] = false }
        m.roof[built] = true
        m.natRoof[natural] = true
        assertTrue("a built roof can be marked", g.designate(x0, y0, Desig.UNROOF))
        assertEquals(Desig.UNROOF, m.desig[built].toInt())
        assertFalse("natural roof cannot be taken down", g.designate(x0 + 1, y0, Desig.UNROOF))
        assertFalse("an unroofed cell cannot be marked", g.designate(x0 + 2, y0, Desig.UNROOF))
    }

    @Test fun aColonistTakesDownAMarkedRoofAndOnlyThatCell() {
        val g = Game(2024L)
        g.startNewColony(Scenario.QUIET_ENCLAVE)
        g.mentalBreaksEnabled = false
        g.nextRaid = Long.MAX_VALUE; g.nextMisc = Long.MAX_VALUE; g.nextWanderer = Long.MAX_VALUE; g.nextTrader = Long.MAX_VALUE
        val m = g.map
        val x0 = g.homeX; val y0 = g.homeY - 6
        for (y in y0 - 9..y0 + 12) for (x in x0 - 9..x0 + 12) {
            val i = m.idx(x, y)
            m.terrain[i] = Terrain.SOIL; m.plant[i] = null; m.building[i] = null; m.items.remove(i); m.natRoof[i] = false; m.roof[i] = false
        }
        m.walls(x0, y0, 6, 6)
        m.roofRect(x0 + 1, y0 + 1, 4, 4)
        m.rebuildRooms(g.outdoorTemp())
        val target = m.idx(x0 + 2, y0 + 2); val other = m.idx(x0 + 3, y0 + 2)
        assertTrue(g.designate(x0 + 2, y0 + 2, Desig.UNROOF))
        val colonist = g.colonists.first()
        colonist.x = x0 + 1; colonist.y = y0 + 1
        var steps = 0
        while (steps < 40000 && m.roof[target]) { g.step(); steps++ }
        assertFalse("the marked roof is gone", m.roof[target])
        assertEquals("the mark is cleared", Desig.NONE, m.desig[target].toInt())
        assertTrue("the next cell keeps its roof", m.roof[other])
    }

    // ------------------------------------------------------------------ save format (SaveGame.kt, version 38)

    @Test fun theRoofGridRoundTripsThroughASave() {
        val g = Game(31L)
        g.startNewColony(Scenario.QUIET_ENCLAVE)
        val m = g.map
        m.setRoof(m.idx(100, 100), true); m.setRoof(m.idx(101, 100), true)
        val back = SaveGame.read(SaveGame.write(g))
        assertEquals(40, SaveGame.VERSION)
        assertTrue(back.map.roof[back.map.idx(100, 100)] && back.map.roof[back.map.idx(101, 100)])
        assertEquals("the same count of roofs", m.roof.count { it }, back.map.roof.count { it })
    }

    @Test fun aSaveWithoutRoofsGetsRoofsOnItsIndoorRoomsOnLoad() {
        val g = Game(32L)
        g.startNewColony(Scenario.QUIET_ENCLAVE)
        val m = g.map
        for (y in 19..58) for (x in 19..73) { val i = m.idx(x, y); m.terrain[i] = Terrain.SOIL; m.natRoof[i] = false; m.plant[i] = null; m.building[i] = null }
        m.walls(20, 20, 5, 5) // nine cells, indoors under the old rule
        m.walls(40, 20, 32, 32) // 900 cells with no natural roof: outdoors under the old rule
        m.rebuildRooms(0f)
        val bytes = SaveGame.write(g, 37)
        val back = SaveGame.read(bytes)
        assertTrue("the small room is roofed on load", back.map.roofed(back.map.idx(22, 22)))
        assertTrue("the small room stays indoors", back.map.roomIndoorAt(back.map.idx(22, 22)))
        assertFalse("the large open room stays outdoors", back.map.roofed(back.map.idx(60, 40)))
        assertFalse(back.map.roomIndoorAt(back.map.idx(60, 40)))
    }
}
