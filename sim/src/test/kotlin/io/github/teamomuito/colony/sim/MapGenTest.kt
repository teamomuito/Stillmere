package io.github.teamomuito.colony.sim

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

private val SEEDS = 1L..12L

private fun assertSameMap(a: GameMap, b: GameMap, what: String) {
    assertEquals("$what biome", a.biome, b.biome)
    assertArrayEquals("$what terrain", a.terrain, b.terrain)
    assertArrayEquals("$what ore", a.ore, b.ore)
    assertArrayEquals("$what rock types", a.rockType, b.rockType)
    assertTrue("$what roofs", a.natRoof.contentEquals(b.natRoof))
    assertEquals("$what start x", a.spawnX, b.spawnX)
    assertEquals("$what start y", a.spawnY, b.spawnY)
    val plantsA = a.plant.map { p -> p?.let { "${it.type}@${it.x},${it.y}" } }
    val plantsB = b.plant.map { p -> p?.let { "${it.type}@${it.x},${it.y}" } }
    assertEquals("$what plants", plantsA, plantsB)
}

class MapGenTest {

    @Test fun sameSeedGivesTheSameMap() {
        for (seed in listOf(3L, 17L, 42L)) {
            for (b in Biome.entries) assertSameMap(GameMap.generateFor(100, 100, seed, b), GameMap.generateFor(100, 100, seed, b), "$b seed $seed")
            assertSameMap(GameMap.generateFor(100, 100, seed), GameMap.generateFor(100, 100, seed), "planet seed $seed")
        }
        val a = Game(9); a.startNewColony(Scenario.CRASHLANDED)
        val b = Game(9); b.startNewColony(Scenario.CRASHLANDED)
        assertSameMap(a.map, b.map, "game seed 9")
        assertEquals("same colony spot", a.homeX to a.homeY, b.homeX to b.homeY)
    }

    @Test fun startingAreaIsOpenAndEverySpawnPointIsReachableFromIt() {
        for (seed in SEEDS) for (b in Biome.entries) {
            val g = Game(seed, GameMap.generateFor(100, 100, seed, b))
            g.startNewColony(Scenario.CRASHLANDED)
            val m = g.map
            val where = "$b seed $seed"
            assertEquals("$where: the colony starts where the map chose", m.spawnX to m.spawnY, g.homeX to g.homeY)
            for (y in g.homeY - 3..g.homeY + 3) for (x in g.homeX - 3..g.homeX + 3) {
                val t = m.terrain[m.idx(x, y)]
                assertTrue("$where: open start at $x,$y", t.passable && t != Terrain.WATER_SHALLOW)
            }
            val pathfinder = Pathfinder(m)
            for (side in 0 until 4) {
                val cells = g.spawnCells(side)
                assertTrue("$where: side $side has an entry", cells.isNotEmpty())
                // Every entry is reachable over the terrain; a spread of them is checked with the game's own pathfinder.
                val reach = m.reachableFrom(m.idx(g.homeX, g.homeY))
                for ((x, y) in cells) assertTrue("$where: entry $x,$y is on the colony's ground", reach[m.idx(x, y)])
                for (k in 0 until 6) {
                    val (x, y) = cells[(k * cells.size) / 6]
                    assertTrue("$where: pathfinder reaches entry $x,$y on side $side", pathfinder.find(g.homeX, g.homeY, x, y) != null)
                }
                for (draw in 0 until 10) {
                    val e = g.edgeCell(side)
                    assertTrue("$where: a spawn is drawn on side $side", e != null && e in cells)
                }
            }
        }
    }

    @Test fun riversAndMountainsDoNotWallOffAnEdge() {
        // A river across the whole map, and a mountain range, are both cut through where they would block an edge.
        val cuts = listOf(
            World.LocalTerrain(river = true, lake = false, hills = Hills.FLAT, riverHorizontal = true),
            World.LocalTerrain(river = true, lake = false, hills = Hills.FLAT, riverHorizontal = false),
            World.LocalTerrain(river = false, lake = false, hills = Hills.MOUNTAIN),
        )
        for ((n, lt) in cuts.withIndex()) for (seed in SEEDS) {
            val m = GameMap.generate(100, 100, seed, Biome.TEMPERATE, lt)
            val g = Game(seed, m)
            g.startNewColony(Scenario.CRASHLANDED)
            val pathfinder = Pathfinder(m)
            for (side in 0 until 4) {
                val cells = g.spawnCells(side)
                assertTrue("terrain $n seed $seed: side $side has an entry", cells.isNotEmpty())
                val (x, y) = cells[cells.size / 2]
                assertTrue("terrain $n seed $seed: entry $x,$y on side $side reachable", pathfinder.find(g.homeX, g.homeY, x, y) != null)
            }
        }
    }

    @Test fun rockTypesOreAndVeinsStayInTheirRanges() {
        var rock = 0; var ore = 0; var oreIsolated = 0; var rockFractionSum = 0.0; var maps = 0
        // The veins planned per map: 8 steel of 7, 3 silver, 2 gold, 2 plasteel of 4, and 3 components of 3.
        val planned = 8 * 7 + (3 + 2 + 2) * 4 + 3 * 3
        val missing = HashMap<Ore, Int>()
        for (seed in SEEDS) for (b in Biome.entries) {
            val m = GameMap.generateFor(100, 100, seed, b)
            val mapRock = m.terrain.count { it == Terrain.ROCK }
            val mapOre = m.ore.count { it != Ore.NONE }
            maps++
            rockFractionSum += mapRock.toDouble() / m.size
            assertTrue("$b seed $seed: some rock", mapRock > 0)
            assertTrue("$b seed $seed: ore ($mapOre) is at most the planned $planned", mapOre <= planned)
            // A map with very little rock can have no room for a vein, so each ore is required on most maps, not every one.
            for (ore in Ore.entries.filter { it != Ore.NONE }) if (m.ore.none { it == ore }) missing[ore] = (missing[ore] ?: 0) + 1
            for (i in 0 until m.size) {
                if (m.ore[i] != Ore.NONE) {
                    assertEquals("$b seed $seed: ore sits in rock", Terrain.ROCK, m.terrain[i])
                    ore++
                    val x = m.xOf(i); val y = m.yOf(i)
                    if (GameMap.DX4.indices.none { k -> m.inB(x + GameMap.DX4[k], y + GameMap.DY4[k]) && m.ore[m.idx(x + GameMap.DX4[k], y + GameMap.DY4[k])] == m.ore[i] }) oreIsolated++
                }
                if (m.terrain[i] == Terrain.ROCK) rock++
            }
        }
        for (ore in Ore.entries.filter { it != Ore.NONE }) assertTrue("${ore.name} is on at least 90% of maps: ${missing[ore] ?: 0} without", (missing[ore] ?: 0) <= maps / 10)
        val oreShare = ore.toDouble() / rock
        assertTrue("ore is 3%..15% of rock overall: $oreShare", oreShare in 0.03..0.15)
        // Lumps are grown as one connected blob, so almost no ore is left on its own.
        assertTrue("ore lumps are connected: $oreIsolated of $ore isolated", oreIsolated <= ore / 100)
        val meanRockFraction = rockFractionSum / maps
        assertTrue("rock covers about a tenth of the map on average: $meanRockFraction", meanRockFraction in 0.08..0.16)

        // The rock noise depends on the seed alone, so the rock shares are taken over more seeds rather than more biomes.
        val rockTypes = IntArray(RockType.entries.size)
        var rockTiles = 0
        for (seed in 1L..40L) {
            val m = GameMap.generateFor(100, 100, seed, Biome.TEMPERATE)
            for (i in 0 until m.size) if (m.terrain[i] == Terrain.ROCK) { rockTypes[m.rockType[i].ordinal]++; rockTiles++ }
        }
        // Granite is the common rock, at about 40%; the other four share the rest at about 15% each.
        val granite = rockTypes[RockType.GRANITE.ordinal].toDouble() / rockTiles
        assertTrue("granite is about 40% of rock: $granite", granite in 0.28..0.45)
        for (t in RockType.entries.filter { it != RockType.GRANITE }) {
            val share = rockTypes[t.ordinal].toDouble() / rockTiles
            assertTrue("${t.name} is a real share of rock: $share", share in 0.10..0.25)
            assertTrue("granite is the most common rock, not ${t.name}", granite > share)
        }
    }

    @Test fun terrainTypesFollowTheBiome() {
        var iceMaps = 0; var tundraMaps = 0; var gravel = 0; var clustered = 0
        for (seed in SEEDS) for (b in Biome.entries) {
            val m = GameMap.generateFor(100, 100, seed, b)
            val ice = m.terrain.count { it == Terrain.ICE }
            if (b != Biome.TUNDRA) assertEquals("$b seed $seed has no ice", 0, ice)
            if (b == Biome.TUNDRA) {
                tundraMaps++
                if (ice > 0) iceMaps++
                assertFalse("tundra seed $seed: shallow water is frozen", m.terrain.any { it == Terrain.WATER_SHALLOW })
                for (i in 0 until m.size) {
                    if (m.terrain[i] != Terrain.GRAVEL) continue
                    gravel++
                    val x = m.xOf(i); val y = m.yOf(i)
                    val same = GameMap.DX4.indices.count { k -> m.inB(x + GameMap.DX4[k], y + GameMap.DY4[k]) && m.terrain[m.idx(x + GameMap.DX4[k], y + GameMap.DY4[k])] == Terrain.GRAVEL }
                    if (same >= 2) clustered++
                }
            }
        }
        assertTrue("tundra maps have ice: $iceMaps of $tundraMaps", iceMaps >= tundraMaps - 2)
        // Gravel comes in patches: a scattering of single tiles would leave most gravel with fewer than two gravel neighbours.
        assertTrue("tundra gravel is patchy: ${clustered.toDouble() / gravel}", clustered.toDouble() / gravel > 0.8)
    }

    @Test fun riverRunsAcrossTheAxisTheWorldRiverEntersOn() {
        // A river across the map covers most columns of it (or rows, when it runs north-south) and stays narrow across the other axis.
        // Rock can cover a part of the band, so the test asks for most, not every, column or row.
        val east = GameMap.generate(100, 100, 5, Biome.TEMPERATE, World.LocalTerrain(river = true, lake = false, hills = Hills.FLAT, riverHorizontal = true))
        val eastColumns = (0 until east.w).count { x -> (0 until east.h).any { y -> east.terrain[east.idx(x, y)] == Terrain.WATER_DEEP } }
        val eastRows = (0 until east.h).count { y -> (0 until east.w).any { x -> east.terrain[east.idx(x, y)] == Terrain.WATER_DEEP } }
        assertTrue("east-west river covers most columns: $eastColumns", eastColumns >= 80)
        assertTrue("east-west river is narrow north to south: $eastRows rows", eastRows <= 60)
        val north = GameMap.generate(100, 100, 5, Biome.TEMPERATE, World.LocalTerrain(river = true, lake = false, hills = Hills.FLAT, riverHorizontal = false))
        val northRows = (0 until north.h).count { y -> (0 until north.w).any { x -> north.terrain[north.idx(x, y)] == Terrain.WATER_DEEP } }
        val northColumns = (0 until north.w).count { x -> (0 until north.h).any { y -> north.terrain[north.idx(x, y)] == Terrain.WATER_DEEP } }
        assertTrue("north-south river covers most rows: $northRows", northRows >= 80)
        assertTrue("north-south river is narrow west to east: $northColumns columns", northColumns <= 60)
    }

    @Test fun lakeSitsOnTheSideTheWorldWaterIsOn() {
        var east = 0.0; var north = 0.0
        val seeds = 1L..4L
        for (seed in seeds) {
            val toEast = GameMap.generate(100, 100, seed, Biome.TEMPERATE, World.LocalTerrain(river = false, lake = true, hills = Hills.FLAT, lakeAngle = 0f))
            val toNorth = GameMap.generate(100, 100, seed, Biome.TEMPERATE, World.LocalTerrain(river = false, lake = true, hills = Hills.FLAT, lakeAngle = (-PI / 2).toFloat()))
            east += meanX(toEast) / seeds.count()
            north += meanY(toNorth) / seeds.count()
        }
        assertTrue("a lake to the east sits east of the middle: mean x $east", east > 60.0)
        assertTrue("a lake to the north sits north of the middle: mean y $north", north < 40.0)
    }

    private fun meanX(m: GameMap): Double {
        val water = (0 until m.size).filter { m.terrain[it] == Terrain.WATER_DEEP || m.terrain[it] == Terrain.WATER_SHALLOW }
        return water.sumOf { m.xOf(it).toDouble() } / water.size
    }

    private fun meanY(m: GameMap): Double {
        val water = (0 until m.size).filter { m.terrain[it] == Terrain.WATER_DEEP || m.terrain[it] == Terrain.WATER_SHALLOW }
        return water.sumOf { m.yOf(it).toDouble() } / water.size
    }

    @Test fun worldTileDecidesRiverAndLakeSide() {
        val w = World(World.W, World.H)
        // A river entering from the west and leaving to the east runs east-west.
        for (x in 19..21) w.river[w.tile(x, 20)] = true
        assertEquals(true, w.localTerrain(w.tile(20, 20)).riverHorizontal)
        assertTrue(w.localTerrain(w.tile(20, 20)).river)
        // A river entering from the north and leaving to the south runs north-south.
        for (y in 37..39) w.river[w.tile(40, y)] = true
        assertEquals(false, w.localTerrain(w.tile(40, 38)).riverHorizontal)
        // Water to the east puts the lake to the east; water to the north puts it to the north.
        w.water[w.tile(31, 10)] = true
        assertEquals(0f, w.localTerrain(w.tile(30, 10)).lakeAngle!!, 1e-6f)
        w.water[w.tile(30, 29)] = true
        assertEquals((-PI / 2).toFloat(), w.localTerrain(w.tile(30, 30)).lakeAngle!!, 1e-6f)
        // With nothing to say which way, the map is free to choose.
        assertNull(w.localTerrain(w.tile(60, 5)).riverHorizontal)
        assertNull(w.localTerrain(w.tile(60, 5)).lakeAngle)
    }

    @Test fun mapTakesItsBiomeFromTheWorldTile() {
        val biomes = HashSet<Biome>()
        for (seed in 1L..20L) {
            val planet = World.generate(seed)
            val map = GameMap.generateFor(100, 100, seed)
            biomes.add(map.biome)
            assertEquals("seed $seed: the map has its home tile's biome", planet.biome[planet.homeTile], map.biome)
            assertEquals("seed $seed: the game's planet has the same home", planet.homeTile, Game(seed, map).world.homeTile)
        }
        assertTrue("the planet gives several biomes: $biomes", biomes.size >= 3)
        for (b in Biome.entries) assertEquals("explicit $b is still honoured", b, GameMap.generateFor(100, 100, 6, b).biome)
    }
}
