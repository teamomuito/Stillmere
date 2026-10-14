package io.github.teamomuito.colony.sim

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A square of [side] rock cells in the middle of a [w] by [h] map. The rest is open ground. */
private fun rockSquare(w: Int, h: Int, side: Int): BooleanArray {
    val rock = BooleanArray(w * h)
    val x0 = (w - side) / 2
    val y0 = (h - side) / 2
    for (y in y0 until y0 + side) for (x in x0 until x0 + side) rock[y * w + x] = true
    return rock
}

/** True when cell [i] has a cardinal neighbour that is off the map or is not rock. */
private fun touchesOutside(i: Int, w: Int, h: Int, rock: BooleanArray): Boolean {
    val x = i % w
    val y = i / w
    for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
        val nx = x + dx
        val ny = y + dy
        if (nx !in 0 until w || ny !in 0 until h || !rock[ny * w + nx]) return true
    }
    return false
}

private val CAVE_LOCAL = World.LocalTerrain(river = false, lake = false, hills = Hills.LARGE, caves = true)
private val NO_CAVE_LOCAL = World.LocalTerrain(river = false, lake = false, hills = Hills.LARGE, caves = false)

/** The cells that the caves carved on [withCaves]: gravel with its roof, where [without] (the same map with no caves) had rock. */
private fun carvedCells(withCaves: GameMap, without: GameMap): BooleanArray = BooleanArray(withCaves.size) { i ->
    withCaves.terrain[i] == Terrain.GRAVEL && withCaves.natRoof[i] && without.terrain[i] == Terrain.ROCK
}

private fun assertShare(what: String, hits: Int, total: Int, expected: Double, tolerance: Double) {
    val share = hits.toDouble() / total
    assertTrue("$what: share $share is within $tolerance of $expected ($hits of $total)", kotlin.math.abs(share - expected) <= tolerance)
}

class CavesTest {

    @Test fun aLargeRockMassIsTunnelledAndItsOwnTunnelsReachOutOfTheRock() {
        val w = 150
        val h = 150
        for (seed in 1L..8L) {
            val rock = rockSquare(w, h, 90)
            val carved = Caves.carve(w, h, rock, seed)
            var cells = 0
            var opening = 0
            for (i in carved.indices) {
                if (!carved[i]) continue
                cells++
                assertTrue("only rock is carved (seed $seed)", rock[i])
                if (touchesOutside(i, w, h, rock)) opening++
            }
            assertTrue("the mass is tunnelled (seed $seed)", cells > 0)
            // Open tunnels are always dug in a mass this size (at least one), and each starts on the edge of the rock.
            assertTrue("an open tunnel opens onto the ground (seed $seed)", opening > 0)
            assertTrue("most of the rock stays as wall (seed $seed)", cells < rock.count { it } / 2)
        }
    }

    @Test fun theSameSeedCarvesTheSameTunnelsAndDifferentSeedsDoNot() {
        val w = 150
        val h = 150
        val rock = rockSquare(w, h, 90)
        val a = Caves.carve(w, h, rock, 3L)
        assertTrue("same seed, same caves", a.contentEquals(Caves.carve(w, h, rock, 3L)))
        assertTrue("another seed carves other caves", !a.contentEquals(Caves.carve(w, h, rock, 4L)))
    }

    @Test fun aMassTooSmallForTunnelsIsNotTunnelled() {
        // A 15 by 15 mass is under the 300-cell minimum. A 19 by 19 mass has 361 cells, but trimming its edges leaves fewer than 300.
        for (seed in 1L..20L) for (side in listOf(15, 19)) {
            val rock = rockSquare(60, 60, side)
            assertEquals("a $side by $side mass gets no caves (seed $seed)", 0, Caves.carve(60, 60, rock, seed).count { it })
        }
    }

    @Test fun aMassThatStaysLargeAfterTrimmingCanGetTunnels() {
        // A 25 by 25 mass keeps about 540 cells after trimming, so an open tunnel comes up for some seeds (about one in three).
        val tunnelled = (1L..12L).count { seed ->
            Caves.carve(60, 60, rockSquare(60, 60, 25), seed).any { it }
        }
        assertTrue("some seeds tunnel a 25 by 25 mass (tunnelled $tunnelled of 12)", tunnelled > 0)
    }

    @Test fun aCaveMapHasGravelFloorUnderItsRoofAndNoOreInIt() {
        for (seed in 1L..3L) {
            val caves = GameMap.generate(250, 250, seed, Biome.TEMPERATE, CAVE_LOCAL)
            val plain = GameMap.generate(250, 250, seed, Biome.TEMPERATE, NO_CAVE_LOCAL)
            val carved = carvedCells(caves, plain)
            assertTrue("the map has caves (seed $seed)", carved.any { it })
            for (i in carved.indices) {
                if (!carved[i]) continue
                assertTrue("a cave cell is passable (seed $seed)", caves.terrain[i].passable)
                assertEquals("a cave cell has no ore (seed $seed)", Ore.NONE, caves.ore[i])
            }
        }
    }

    @Test fun aCaveMapOpensOntoTheSurfaceAndKeepsEverySideReachable() {
        for (seed in 1L..4L) {
            val caves = GameMap.generate(250, 250, seed, Biome.TEMPERATE, CAVE_LOCAL)
            val plain = GameMap.generate(250, 250, seed, Biome.TEMPERATE, NO_CAVE_LOCAL)
            val carved = carvedCells(caves, plain)
            val plainRock = BooleanArray(plain.size) { plain.terrain[it] == Terrain.ROCK }
            val openings = (0 until caves.size).count { i -> carved[i] && touchesOutside(i, 250, 250, plainRock) }
            assertTrue("a cave opens onto the surface (seed $seed)", openings > 0)
            val reach = caves.reachableFrom(caves.idx(caves.spawnX, caves.spawnY))
            for (side in 0 until 4) {
                assertTrue("side $side is reachable from the start (seed $seed)", caves.sideCells(side).any { reach[it] && caves.terrain[it] != Terrain.WATER_SHALLOW })
            }
        }
    }

    @Test fun caveMapsAreDeterministic() {
        val a = GameMap.generate(250, 250, 2L, Biome.TEMPERATE, CAVE_LOCAL)
        val b = GameMap.generate(250, 250, 2L, Biome.TEMPERATE, CAVE_LOCAL)
        assertArrayEquals("terrain", a.terrain, b.terrain)
        assertArrayEquals("ore", a.ore, b.ore)
        assertTrue("roofs", a.natRoof.contentEquals(b.natRoof))
    }

    @Test fun worldGivesCavesOnlyToHillTilesAndAtTheChanceOfEachKind() {
        var mountain = 0
        var mountainCaves = 0
        var large = 0
        var largeCaves = 0
        for (seed in 1L..40L) {
            val w = World.generate(seed)
            for (t in 0 until World.W * World.H) {
                if (w.caves[t]) {
                    assertTrue("caves only on land hills (seed $seed, tile $t)", !w.water[t] && (w.hills[t] == Hills.MOUNTAIN || w.hills[t] == Hills.LARGE))
                }
                when (w.hills[t]) {
                    Hills.MOUNTAIN -> { mountain++; if (w.caves[t]) mountainCaves++ }
                    Hills.LARGE -> { large++; if (w.caves[t]) largeCaves++ }
                    else -> {}
                }
            }
        }
        assertShare("mountain tiles", mountainCaves, mountain, 0.5, 0.05)
        assertShare("large hill tiles", largeCaves, large, 0.25, 0.04)
    }

    @Test fun aHomeTileCarriesItsCavesIntoItsLocalTerrain() {
        val w = World.generate(7L, Biome.TEMPERATE)
        assertEquals(w.caves[w.homeTile], w.localTerrain().caves)
    }

    @Test fun aColonyWithCavesSavesAndLoadsAsItWas() {
        val seed = (1L..300L).first { World.generate(it, Biome.TEMPERATE).localTerrain().caves }
        val g = Game(seed)
        g.startNewColony(Scenario.QUIET_ENCLAVE)
        val back = SaveGame.read(SaveGame.write(g))
        assertArrayEquals("terrain", g.map.terrain, back.map.terrain)
        assertArrayEquals("ore", g.map.ore, back.map.ore)
        assertTrue("roofs", g.map.natRoof.contentEquals(back.map.natRoof))
    }
}
