package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Independent answer to "can a walker starting at (sx, sy) reach the goal?", by breadth-first search over walkable cells. */
private fun oracle(m: GameMap, sx: Int, sy: Int, tx: Int, ty: Int, adjacent: Boolean): Boolean {
    val seen = BooleanArray(m.size)
    val queue = ArrayDeque<Int>()
    val start = m.idx(sx, sy)
    seen[start] = true; queue.add(start)
    while (queue.isNotEmpty()) {
        val c = queue.removeFirst()
        if (isGoalCell(m, m.xOf(c), m.yOf(c), tx, ty, adjacent)) return true
        for (d in 0 until 4) {
            val nx = m.xOf(c) + GameMap.DX4[d]; val ny = m.yOf(c) + GameMap.DY4[d]
            if (!m.inB(nx, ny)) continue
            val n = m.idx(nx, ny)
            if (!seen[n] && m.walkable(n)) { seen[n] = true; queue.add(n) }
        }
    }
    return false
}

/** A random map of walls and rock. Only walkable starts are asked about, so the questions are about walking. */
private fun randomMap(seed: Long, size: Int = 24, density: Double = 0.3): GameMap {
    val m = GameMap(size, size)
    val r = java.util.Random(seed)
    for (y in 0 until size) for (x in 0 until size) {
        if (r.nextDouble() < density) m.setBuilding(Building(BuildDef.WOOD_WALL, x, y, true))
    }
    return m
}

private fun pathIsValid(m: GameMap, start: Int, path: IntArray): Boolean {
    var prev = start
    for (c in path) {
        if (!m.walkable(c)) return false
        val dx = m.xOf(c) - m.xOf(prev); val dy = m.yOf(c) - m.yOf(prev)
        if (kotlin.math.abs(dx) > 1 || kotlin.math.abs(dy) > 1 || (dx == 0 && dy == 0)) return false
        if (dx != 0 && dy != 0 && (!m.walkable(m.idx(m.xOf(prev) + dx, m.yOf(prev))) || !m.walkable(m.idx(m.xOf(prev), m.yOf(prev) + dy)))) return false
        prev = c
    }
    return true
}

class RegionTest {
    @Test fun regionAnswersAgreeExactlyWithABreadthFirstOracleOnRandomMaps() {
        var asked = 0
        for (seed in 1L..12L) {
            val m = randomMap(seed)
            val f = Pathfinder(m)
            val r = java.util.Random(seed * 7)
            repeat(150) {
                val sx = r.nextInt(m.w); val sy = r.nextInt(m.h)
                if (!m.walkable(m.idx(sx, sy))) return@repeat
                val tx = r.nextInt(m.w); val ty = r.nextInt(m.h)
                val adjacent = r.nextBoolean()
                val truth = oracle(m, sx, sy, tx, ty, adjacent)
                assertEquals("seed $seed from ($sx,$sy) to ($tx,$ty) adjacent=$adjacent",
                    truth, f.regions.mayReach(m.idx(sx, sy), tx, ty, adjacent))
                // The search is complete now: it finds a path exactly when one exists, and every path is valid.
                val path = f.find(sx, sy, tx, ty, adjacent)
                assertEquals("search agrees with the oracle", truth, path != null)
                if (path != null && path.isNotEmpty()) assertTrue("the path is walkable step by step", pathIsValid(m, m.idx(sx, sy), path))
                asked++
            }
        }
        assertTrue("the test asked enough questions to mean something ($asked)", asked > 1000)
    }

    @Test fun aWallRemovedOrBuiltIsSeenAtOnce() {
        val m = GameMap(20, 20)
        val wall = Building(BuildDef.WOOD_WALL, 10, 10, false)   // not built yet: it blocks nothing
        for (y in 0 until 20) if (y != 10) m.setBuilding(Building(BuildDef.WOOD_WALL, 10, y, true))
        m.setBuilding(wall)
        val f = Pathfinder(m)
        assertNotNull("a gap in the wall: the unbuilt wall does not block", f.find(2, 10, 17, 10))
        // Construction finishes: the cell becomes blocked, and the cache must say so.
        wall.built = true
        m.markWalkChanged()
        assertNull("the finished wall closes the gap", f.find(2, 10, 17, 10))
        // The wall is removed again.
        m.removeBuilding(wall)
        assertNotNull("removing the wall reopens the gap", f.find(2, 10, 17, 10))
    }

    @Test fun anAutodoorBlocksOnlyWhileUnpowered() {
        val m = GameMap(12, 5)
        for (y in 0 until 5) if (y != 2) m.setBuilding(Building(BuildDef.WOOD_WALL, 6, y, true))
        val door = Building(BuildDef.AUTODOOR, 6, 2, true).also { it.powered = false }
        m.setBuilding(door)
        val f = Pathfinder(m)
        assertNull("an unpowered autodoor is shut", f.find(2, 2, 9, 2))
        door.powered = true
        m.markWalkChanged()   // what the power tick does for doors
        assertNotNull("a powered autodoor opens", f.find(2, 2, 9, 2))
    }

    @Test fun unreachableItemsAreNotChosenOverReachableOnes() {
        val g = Game(501)
        g.startNewColony(Scenario.LOST_TRIBE)
        g.mentalBreaksEnabled = false
        val p = g.colonists[0]
        val near = 4
        // An item right beside the colonist, walled in on all sides: the nearest item, but unreachable.
        val ix = p.x + 2; val iy = p.y
        for (dy in -1..1) for (dx in -1..1) if (dx != 0 || dy != 0) g.map.setBuilding(Building(BuildDef.WOOD_WALL, ix + dx, iy + dy, true))
        g.map.drop(ItemType.STEEL, 5, ix, iy)
        // A reachable item further away.
        g.map.drop(ItemType.STEEL, 5, p.x - near, p.y)
        val chosen = g.nearestItem(p) { it.type == ItemType.STEEL }
        assertNotNull(chosen)
        assertFalse("the walled-in item is not chosen", chosen!!.x == ix && chosen.y == iy)
        assertEquals(p.x - near, chosen.x)
    }

    @Test fun unreachableTargetsAreRefusedWithoutASearch() {
        val m = GameMap(20, 20)
        for (y in 0 until 20) m.setBuilding(Building(BuildDef.WOOD_WALL, 10, y, true))
        val f = Pathfinder(m)
        f.stats.reset()
        assertNull(f.find(2, 5, 17, 5))
        assertEquals("refused by the regions, not searched", 1L, f.stats.rejected)
        assertEquals("no search was run", 0L, f.stats.searches)
    }
}
