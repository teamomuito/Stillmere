package io.github.teamomuito.colony.sim

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A colony map, made as [GameMap.generateFor] makes it for [Game.MAP_SIZE], with or without its ruins. */
private fun colony(seed: Long, ruins: Boolean = true, biome: Biome = Biome.TEMPERATE): GameMap {
    val world = World.generate(seed, biome)
    return GameMap.generate(Game.MAP_SIZE, Game.MAP_SIZE, seed, world.biome[world.homeTile], world.localTerrain(), ruins = ruins)
}

private fun wallsOf(m: GameMap): List<String> =
    m.buildings().map { "${it.def}@${it.x},${it.y}:${it.material}:${it.hp}:${it.built}" }.sorted()

private fun itemsOf(m: GameMap): List<String> =
    m.items.entries.sortedBy { it.key }.map { "${it.key}:${it.value.type}x${it.value.count}:${it.value.quality}" }

private fun floorsOf(m: GameMap): List<String> =
    (0 until m.size).filter { m.floor[it] != null }.map { "$it:${m.floor[it]}" }

/** Every cell that a ruin's walls or floors stand on, from the plans. */
private fun footprintOf(plans: List<RuinPlan>, m: GameMap): BooleanArray {
    val out = BooleanArray(m.size)
    for (p in plans) for (y in p.y0..p.y1) for (x in p.x0..p.x1) out[m.idx(x, y)] = true
    return out
}

/** Every side of the map can be reached from the start over walkable cells, with the pathfinder's rule for corners. */
private fun everySideReachable(m: GameMap): Boolean {
    val start = m.idx(m.spawnX, m.spawnY)
    if (!m.walkable(start)) return false
    val seen = BooleanArray(m.size)
    val queue = ArrayDeque<Int>()
    seen[start] = true
    queue.add(start)
    while (queue.isNotEmpty()) {
        val c = queue.removeFirst()
        val cx = m.xOf(c)
        val cy = m.yOf(c)
        for (d in 0 until 8) {
            val nx = cx + GameMap.DX8[d]
            val ny = cy + GameMap.DY8[d]
            if (!m.inB(nx, ny)) continue
            val n = m.idx(nx, ny)
            if (seen[n] || !m.walkable(n)) continue
            if (d >= 4 && (!m.walkable(m.idx(nx, cy)) || !m.walkable(m.idx(cx, ny)))) continue
            seen[n] = true
            queue.add(n)
        }
    }
    return (0 until 4).all { side -> m.sideCells(side).any { seen[it] && m.terrain[it] != Terrain.WATER_SHALLOW } }
}

class RuinsTest {

    @Test fun theSameSeedGivesTheSameRuins() {
        for (seed in 1L..4L) {
            val a = colony(seed)
            val b = colony(seed)
            assertEquals("walls for seed $seed", wallsOf(a), wallsOf(b))
            assertEquals("floors for seed $seed", floorsOf(a), floorsOf(b))
            assertEquals("items for seed $seed", itemsOf(a), itemsOf(b))
        }
    }

    @Test fun aColonyMapIsBuiltWithItsRuinsAsItsGeneratorMakesThem() {
        // The map from GameMap.generateFor has the ruins, and the same ruins come from placing them on the map without any.
        for (seed in 1L..3L) {
            val withRuins = GameMap.generateFor(Game.MAP_SIZE, Game.MAP_SIZE, seed, Biome.TEMPERATE)
            val plain = colony(seed, ruins = false)
            val plans = Ruins.place(plain, seed).plans
            assertTrue("seed $seed has ruins", plans.isNotEmpty())
            assertEquals("seed $seed: the walls are the placed walls", wallsOf(plain), wallsOf(withRuins))
            assertEquals("seed $seed: the floors are the placed floors", floorsOf(plain), floorsOf(withRuins))
        }
    }

    @Test fun aMapWithRuinsIsTheSameMapWithoutThemExceptInTheirFootprints() {
        for (seed in 1L..6L) {
            val with = colony(seed, ruins = true)
            val without = colony(seed, ruins = false)
            val where = "seed $seed"
            assertArrayEquals("$where terrain", without.terrain, with.terrain)
            assertArrayEquals("$where ore", without.ore, with.ore)
            assertArrayEquals("$where rock types", without.rockType, with.rockType)
            assertTrue("$where roofs", without.natRoof.contentEquals(with.natRoof))
            assertEquals("$where start", without.spawnX to without.spawnY, with.spawnX to with.spawnY)
            val plans = Ruins.place(without, seed).plans
            val inside = footprintOf(plans, with)
            for (i in 0 until with.size) {
                if (inside[i]) {
                    assertEquals("$where: no plant inside a ruin at $i", null, with.plant[i]?.type)
                } else {
                    assertEquals("$where: plant at $i outside the ruins", without.plant[i]?.let { "${it.type}@${it.x},${it.y}" },
                        with.plant[i]?.let { "${it.type}@${it.x},${it.y}" })
                }
            }
        }
    }

    @Test fun ruinWallsAreStoneOrSteelWithinTheirHitPoints() {
        var walls = 0
        var floors = 0
        for (seed in 1L..6L) {
            val m = colony(seed)
            for (b in m.buildings()) {
                if (b.def != BuildDef.WOOD_WALL) continue
                walls++
                assertTrue("a ruin wall is stone or steel (seed $seed): ${b.material}", b.material == ItemType.STONE || b.material == ItemType.STEEL)
                assertTrue("a wall is built (seed $seed)", b.built)
                assertTrue("hit points of ${b.x},${b.y} (seed $seed) are from 1 to the maximum: ${b.hp} of ${b.maxHp}", b.hp >= 1f && b.hp <= b.maxHp)
            }
            floors += (0 until m.size).count { m.floor[it] == BuildDef.STONE_FLOOR }
        }
        assertTrue("some ruin walls are made", walls > 0)
        assertTrue("some ruin floors are laid", floors > 0)
    }

    @Test fun everySideStaysReachableFromTheStartAndTheStartStaysClear() {
        for (seed in 1L..8L) {
            val m = colony(seed)
            assertTrue("every side reachable (seed $seed)", everySideReachable(m))
            for (y in m.spawnY - Ruins.START_CLEAR..m.spawnY + Ruins.START_CLEAR) for (x in m.spawnX - Ruins.START_CLEAR..m.spawnX + Ruins.START_CLEAR) {
                if (!m.inB(x, y)) continue
                val i = m.idx(x, y)
                assertTrue("nothing built near the start (seed $seed) at $x,$y", m.building[i] == null && m.floor[i] == null)
            }
        }
    }

    @Test fun ruinCentresAreTenApartAndFootprintsKeepTwoCellsApart() {
        var pairs = 0
        for (seed in 1L..6L) {
            val plans = Ruins.place(colony(seed, ruins = false), seed).plans
            for (later in plans.indices) for (earlier in 0 until later) {
                val a = plans[earlier]
                val b = plans[later]
                val dx = a.centreX - b.centreX
                val dy = a.centreY - b.centreY
                assertTrue("centres of seed $seed are 10 apart: $dx,$dy", dx * dx + dy * dy > Ruins.MIN_SPACING * Ruins.MIN_SPACING)
                val apart = a.x1 + Ruins.USED_PADDING < b.x0 || b.x1 + Ruins.USED_PADDING < a.x0 ||
                    a.y1 + Ruins.USED_PADDING < b.y0 || b.y1 + Ruins.USED_PADDING < a.y0
                assertTrue("footprints of seed $seed keep 2 cells apart", apart)
                pairs++
            }
        }
        assertTrue("some pairs of ruins were checked", pairs > 0)
    }

    @Test fun theDamagePassStopsAtTheCap() {
        var plansSeen = 0
        for (seed in 1L..10L) {
            for (p in Ruins.place(colony(seed, ruins = false), seed).plans) {
                plansSeen++
                val cap = p.buildables * Ruins.MAX_DESTROYED
                assertTrue("destroyed ${p.destroyed} of ${p.buildables} stops at the cap (seed $seed)", p.destroyed.toFloat() <= cap + 1f)
                assertTrue("destroyed walls are destroyed things", p.destroyedWalls.size <= p.destroyed)
            }
        }
        assertTrue("some ruins were damaged", plansSeen > 0)
    }

    @Test fun theSizeCurveGivesSidesFromSixToThirtyWithHalfAtTenOrLess() {
        val rng = Rng(77L)
        val n = 4000
        var atMostTen = 0
        var atLeastTwenty = 0
        for (k in 0 until n) {
            val s = Ruins.drawSize(rng)
            assertTrue("side $s is from 6 to 30", s in 6..30)
            if (s <= 10) atMostTen++
            if (s >= 20) atLeastTwenty++
        }
        val shortShare = atMostTen.toDouble() / n
        val longShare = atLeastTwenty.toDouble() / n
        assertTrue("about half of the sides are 10 or less (was $shortShare)", shortShare > 0.45 && shortShare < 0.6)
        assertTrue("about one side in eight is 20 or more (was $longShare)", longShare > 0.08 && longShare < 0.18)
    }

    @Test fun theCountPer10kCellsFollowsTheScatterer() {
        assertEquals(6, Ruins.ruinCount(250, 250, 1.0f))
        assertEquals(3, Ruins.ruinCount(250, 250, 0.5f))
        assertEquals(1, Ruins.ruinCount(100, 100, 1.0f))
    }

    @Test fun lootIsFromTheTableOnFreeCellsAndRubbleIsRoughRock() {
        val tableTypes = Ruins.LOOT_TABLE.associateBy { it.type }
        var rubble = 0
        var loot = 0
        for (seed in 1L..4L) {
            val m = colony(seed)
            val plans = Ruins.place(colony(seed, ruins = false), seed).plans
            val inside = footprintOf(plans, m)
            for ((i, s) in m.items) {
                if (s.corpseOf != null) continue
                assertTrue("item at $i (seed $seed) is in a ruin", inside[i])
                assertTrue("item at $i (seed $seed) is on passable ground", m.terrain[i].passable)
                assertTrue("no building under item at $i (seed $seed)", m.building[i] == null)
                if (s.type == ItemType.STONE_CHUNK) rubble++
                else {
                    val entry = tableTypes[s.type] ?: error("item ${s.type} is not in the loot table (seed $seed)")
                    loot++
                    assertTrue("count ${s.count} of ${s.type} is in its range (seed $seed)", s.count in entry.min..entry.max)
                }
            }
            // Each ruin gets 3 to 10 stacks of loot. A ruin of 36 cells or more always has the free cells for the minimum.
            for (p in plans) {
                val stacks = (p.y0..p.y1).sumOf { y -> (p.x0..p.x1).count { x -> m.items[m.idx(x, y)]?.let { tableTypes.containsKey(it.type) } == true } }
                assertTrue("loot stacks $stacks at most ${Ruins.LOOT_MAX} (seed $seed)", stacks <= Ruins.LOOT_MAX)
                if ((p.x1 - p.x0 + 1) * (p.y1 - p.y0 + 1) >= 36) {
                    assertTrue("loot stacks $stacks at least ${Ruins.LOOT_MIN} (seed $seed)", stacks >= Ruins.LOOT_MIN)
                }
            }
        }
        assertTrue("some loot was placed", loot > 0)
        assertTrue("some rubble was placed", rubble > 0)
    }

    @Test fun aBattleMapHasNoRuins() {
        for (seed in 1L..4L) {
            val m = GameMap.generate(44, 44, seed, Biome.TEMPERATE, veins = GameMap.FIXED_ORE_VEINS)
            assertTrue("no ruin walls on a battle map (seed $seed)", m.buildings().none { it.def == BuildDef.WOOD_WALL })
            assertFalse("no ruin floors on a battle map (seed $seed)", (0 until m.size).any { m.floor[it] == BuildDef.STONE_FLOOR })
        }
    }

    @Test fun aColonyWithRuinsSavesAndLoadsAsItWas() {
        val seed = (1L..50L).first { GameMap.generateFor(Game.MAP_SIZE, Game.MAP_SIZE, it, Biome.TEMPERATE).buildings().any { b -> b.def == BuildDef.WOOD_WALL } }
        val g = Game(seed)
        g.startNewColony(Scenario.QUIET_ENCLAVE)
        val back = SaveGame.read(SaveGame.write(g))
        assertEquals("walls", wallsOf(g.map), wallsOf(back.map))
        assertEquals("floors", floorsOf(g.map), floorsOf(back.map))
        assertEquals("items", itemsOf(g.map), itemsOf(back.map))
        assertArrayEquals("terrain", g.map.terrain, back.map.terrain)
    }
}
