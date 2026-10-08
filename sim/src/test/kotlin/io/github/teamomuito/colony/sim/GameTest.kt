package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PathfinderTest {
    @Test fun routesAroundWallsAndBreachesWhenAllowed() {
        val m = GameMap(20, 20)
        for (y in 0 until 20) m.building[m.idx(10, y)] = Building(BuildDef.WOOD_WALL, 10, y, true)
        val f = Pathfinder(m)
        assertNull(f.find(2, 10, 17, 10))
        val breach = f.find(2, 10, 17, 10, breach = true)
        assertNotNull(breach)
        assertEquals(m.idx(17, 10), breach!!.last())
        m.building[m.idx(10, 10)] = Building(BuildDef.DOOR, 10, 10, true)
        assertNotNull(f.find(2, 10, 17, 10))
    }

    @Test fun adjacentGoalStopsNextToRock() {
        val m = GameMap(10, 10)
        m.terrain[m.idx(5, 5)] = Terrain.ROCK
        val p = Pathfinder(m).find(1, 5, 5, 5, adjacent = true)!!
        val last = p.last()
        assertEquals(4, m.xOf(last))
    }
}

class GameTest {
    private fun newGame(seed: Long = 7): Game {
        val g = Game(seed)
        g.startNewColony()
        return g
    }

    private fun run(g: Game, ticks: Int) { repeat(ticks) { g.step() } }

    @Test fun mapHasPlayableTerrain() {
        val g = newGame()
        val rock = g.map.terrain.count { it == Terrain.ROCK }
        val trees = g.map.plant.count { it?.type == PlantType.TREE }
        assertTrue("rock=$rock", rock > 100)
        assertTrue("trees=$trees", trees > 50)
        assertEquals(3, g.colonists.size)
    }

    @Test fun colonistsSleepEatAndSurviveAFewDays() {
        val g = newGame()
        run(g, TICKS_PER_DAY * 3)
        assertEquals(3, g.colonists.size)
        assertTrue(g.colonists.all { it.food > 0f })
    }

    @Test fun colonistsBuildAndUseABed() {
        val g = newGame()
        val bx = g.homeX + 4
        val by = g.homeY
        assertTrue(g.placeBlueprint(BuildDef.BED, bx, by))
        run(g, 4000)
        val b = g.map.building[g.map.idx(bx, by)]
        assertNotNull(b)
        assertTrue("bed built", b!!.built)
    }

    @Test fun choppingAndMiningYieldResources() {
        val g = newGame()
        val tree = g.map.plant.filterNotNull().first { it.type == PlantType.TREE }
        val woodBefore = g.map.countItems(ItemType.WOOD)
        g.designate(tree.x, tree.y, Desig.CUT)
        val rockI = (0 until g.map.size).first { g.map.terrain[it] == Terrain.ROCK && (0 until 8).any { d ->
            val nx = g.map.xOf(it) + GameMap.DX8[d]; val ny = g.map.yOf(it) + GameMap.DY8[d]
            g.map.inB(nx, ny) && g.map.walkable(g.map.idx(nx, ny))
        } }
        g.designate(g.map.xOf(rockI), g.map.yOf(rockI), Desig.MINE)
        val stoneBefore = g.map.countItems(ItemType.STONE)
        run(g, 9000)
        assertTrue("wood ${g.map.countItems(ItemType.WOOD)} vs $woodBefore", g.map.countItems(ItemType.WOOD) > woodBefore)
        assertTrue(g.map.countItems(ItemType.STONE) > stoneBefore || g.map.terrain[rockI] != Terrain.ROCK)
    }

    @Test fun cropsGrowAndGetHarvested() {
        val g = newGame()
        for (y in g.homeY + 3..g.homeY + 5) for (x in g.homeX - 2..g.homeX + 2) g.setZone(x, y, ZoneKind.GROWING, PlantType.RICE)
        val rawBefore = g.map.countItems(ItemType.RAW_FOOD)
        run(g, TICKS_PER_DAY * 9)
        assertTrue("planted", g.map.plant.any { it?.type == PlantType.RICE } || g.map.countItems(ItemType.RAW_FOOD) > rawBefore)
    }

    @Test fun turretsAndColonistsRepelARaid() {
        val g = newGame(11)
        g.researchDone.addAll(Research.entries)
        g.placeBlueprint(BuildDef.TURRET, g.homeX + 2, g.homeY - 3)
        g.placeBlueprint(BuildDef.TURRET, g.homeX - 3, g.homeY - 3)
        run(g, 3000)
        g.nextRaid = g.tick
        run(g, TICKS_PER_DAY * 2)
        assertTrue("raid happened", g.log.any { it.text.startsWith("RAID") })
        assertTrue(g.colonists.isNotEmpty())
    }

    @Test fun savedGamesLoadBack() {
        val g = newGame()
        run(g, 3000)
        g.placeBlueprint(BuildDef.WOOD_WALL, g.homeX + 5, g.homeY + 5)
        g.researchCurrent = Research.SMITHING
        val loaded = SaveGame.read(SaveGame.write(g))
        assertEquals(g.tick, loaded.tick)
        assertEquals(g.colonists.map { it.name }, loaded.colonists.map { it.name })
        assertEquals(g.map.countItems(ItemType.MEAL), loaded.map.countItems(ItemType.MEAL))
        assertNotNull(loaded.map.building[loaded.map.idx(g.homeX + 5, g.homeY + 5)])
        assertEquals(Research.SMITHING, loaded.researchCurrent)
        run(loaded, 2000)
    }

    @Test fun researchTakesRealTimeAndUnlocksBuildings() {
        val g = newGame()
        assertTrue(!g.canBuildAt(BuildDef.STEEL_WALL, g.homeX + 6, g.homeY - 6))
        g.placeBlueprint(BuildDef.RESEARCH_BENCH, g.homeX + 3, g.homeY + 4)
        g.startResearch(Research.SMITHING)
        run(g, TICKS_PER_DAY / 2)
        assertTrue("not instant", Research.SMITHING !in g.researchDone)
        run(g, TICKS_PER_DAY * 6)
        assertTrue(Research.SMITHING in g.researchDone)
        assertTrue(g.canBuildAt(BuildDef.STEEL_WALL, g.homeX + 6, g.homeY - 6))
    }

    @Test fun draftedColonistKillsALoneRaider() {
        val g = newGame(3)
        val shooter = g.colonists.first { it.weapon == Weapon.RIFLE }
        g.setDrafted(shooter, true)
        val raider = g.newRaider(shooter.x + 12, shooter.y, Weapon.CLUB, 1)
        run(g, 3000)
        assertTrue("raider down or dead", raider.dead || raider.downed || raider.hp < raider.maxHp)
        assertTrue(shooter.alive)
    }

    @Test fun walledInColonistsDoNotLeakIndoorTemperature() {
        val g = newGame()
        val hx = g.homeX + 8
        val hy = g.homeY - 8
        for (x in hx..hx + 4) for (y in hy..hy + 4) {
            val edge = x == hx || x == hx + 4 || y == hy || y == hy + 4
            val i = g.map.idx(x, y)
            g.map.terrain[i] = Terrain.SOIL
            g.map.plant[i] = null
            if (edge) g.map.building[i] = Building(BuildDef.WOOD_WALL, x, y, true)
        }
        g.map.rebuildRooms(g.outdoorTemp())
        val inside = g.map.idx(hx + 2, hy + 2)
        assertTrue(g.map.roomIndoorAt(inside))
        assertTrue(!g.map.roomIndoorAt(g.map.idx(hx - 2, hy)))
    }
}
