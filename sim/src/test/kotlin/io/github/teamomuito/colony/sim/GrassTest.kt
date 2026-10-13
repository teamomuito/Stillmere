package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

/** Grass terrain and grass cover: fertility, growth, harvest, generation and saving (docs/fidelity/grass.md). */
class GrassTest {
    private fun resource(name: String): ByteArray =
        javaClass.getResourceAsStream("/legacy/$name")?.use { it.readBytes() } ?: error("missing test resource legacy/$name")

    /** A colony on a generated 250 by 250 temperate map, with the quiet settings the other tests use. */
    private fun colony(seed: Long): Game {
        val g = Game(seed, GameMap.generateFor(Game.MAP_SIZE, Game.MAP_SIZE, seed, Biome.TEMPERATE))
        g.startNewColony(Scenario.QUIET_ENCLAVE)
        g.mentalBreaksEnabled = false
        g.pawns.removeAll { it.isAnimal && it.faction == Faction.WILD }
        g.nextRaid = Long.MAX_VALUE; g.nextMisc = Long.MAX_VALUE; g.nextWanderer = Long.MAX_VALUE; g.nextTrader = Long.MAX_VALUE
        g.randomCaravanEvents = false
        return g
    }

    private fun GameMap.grassCells(): List<Int> = (0 until size).filter { terrain[it] == Terrain.GRASS }
    private fun GameMap.grassCovers(): List<Int> = (0 until size).filter { plant[it]?.type == PlantType.GRASS }

    // Enums and fixed values.

    @Test fun grassIsAppendedToBothEnumsAndTheOldOrdinalsStay() {
        assertEquals(0, Terrain.SOIL.ordinal)
        assertEquals(9, Terrain.MUD.ordinal)
        assertEquals(10, Terrain.GRASS.ordinal)
        assertEquals(Terrain.GRASS, Terrain.entries.last())
        assertEquals(20, PlantType.MEADOW_GRASS.ordinal)
        assertEquals(PlantType.GRASS, PlantType.entries.last())
    }

    @Test fun grassGroundIsPassableFertileAndCostsLikeSoil() {
        assertTrue(Terrain.GRASS.passable)
        assertEquals(1f, Terrain.GRASS.fertility, 0f)
        assertEquals(Terrain.SOIL.cost, Terrain.GRASS.cost)
    }

    @Test fun grassCoverIsAWildPlantThatRegrowsAndYieldsHay() {
        assertTrue(PlantType.GRASS.regrows)
        assertFalse(PlantType.GRASS.crop)
        assertFalse(PlantType.GRASS.isTree)
        assertEquals(ItemType.HAY, PlantType.GRASS.yieldType)
    }

    // Growth: the shared plant rules (RimWorld/Plant.cs:346, PlantUtility.cs:358, 361-381).

    @Test fun grassGrowsAtOneOverItsGrowDaysAtFullFactors() {
        val fert = fertilityGrowthFactor(Terrain.GRASS.fertility)
        assertEquals("fertility 1.0 gives factor 1.0", 1f, fert, 0f)
        val step = growthPerSlowTick(PlantType.GRASS, fert, 20f, 1f)
        assertEquals(Game.SLOW_TICK.toFloat() / (PlantType.GRASS.growDays * TICKS_PER_DAY), step, 1e-9f)
    }

    @Test fun grassStopsGrowingAtTheGrowthLimitsAndFallsAboveTheBand() {
        val fert = fertilityGrowthFactor(Terrain.GRASS.fertility)
        assertEquals(0f, growthPerSlowTick(PlantType.GRASS, fert, 0f, 1f), 0f)
        assertEquals(0f, growthPerSlowTick(PlantType.GRASS, fert, 58f, 1f), 0f)
        assertEquals(0.5f, temperatureGrowthFactor(50f, 6f, 42f), 1e-6f)
        assertEquals(0f, growthPerSlowTick(PlantType.GRASS, fert, 20f, 0f), 0f)
    }

    @Test fun poorGroundGrowsGrassSlowerThanFertileGround() {
        val fertile = growthPerSlowTick(PlantType.GRASS, fertilityGrowthFactor(1f), 20f, 1f)
        val sand = growthPerSlowTick(PlantType.GRASS, fertilityGrowthFactor(Terrain.SAND.fertility), 20f, 1f)
        assertTrue(sand < fertile)
        assertEquals(fertile * 0.5f, growthPerSlowTick(PlantType.GRASS, fertilityGrowthFactor(0f), 20f, 1f), 1e-9f)
    }

    // Harvest: 65 percent minimum and the yield formula (RimWorld/Plant.cs:115, 917-919, 934-947).

    @Test fun grassIsHarvestableAboveTwoThirdsOfItsGrowthAndTreesAndBerriesAreNot() {
        assertFalse(Plant(PlantType.GRASS, 0, 0, 0.65f).mature)
        assertTrue(Plant(PlantType.GRASS, 0, 0, 0.7f).mature)
        assertTrue(Plant(PlantType.GRASS, 0, 0, 1f).mature)
        assertFalse(Plant(PlantType.BERRY, 0, 0, 0.7f).mature)
        assertTrue(Plant(PlantType.BERRY, 0, 0, 1f).mature)
    }

    @Test fun grassYieldIsZeroAtTheMinimumAndHalfToFullAboveIt() {
        assertEquals(0f, harvestYieldExact(4, 0.65f), 0f)
        assertEquals(4f * (0.5f + 0.5f * (0.05f / 0.35f)), harvestYieldExact(4, 0.7f), 1e-5f)
        assertEquals(3f, harvestYieldExact(4, 0.825f), 1e-5f)
        assertEquals(4f, harvestYieldExact(4, 1f), 1e-6f)
    }

    /** Stillmere's own regrowth rule is growth 0.05 after a harvest (the regrowing-plant rule, C). */
    @Test fun aGrassCoverIsHarvestedForHayAndRegrows() {
        val g = colony(7L)
        val x = g.homeX + 7; val y = g.homeY
        val i = g.map.idx(x, y)
        g.map.terrain[i] = Terrain.GRASS
        g.map.plant[i] = Plant(PlantType.GRASS, x, y, 1f)
        assertTrue(g.designate(x, y, Desig.HARVEST))
        var steps = 0
        while (steps < 6000 && g.map.items.values.none { it.type == ItemType.HAY }) { g.step(); steps++ }
        assertTrue("a colonist harvests the grass", g.map.items.values.any { it.type == ItemType.HAY })
        val after = g.map.plant[i]
        assertNotNull("the cover regrows in place", after)
        assertEquals(PlantType.GRASS, after!!.type)
        assertTrue("it restarts near 5 percent growth, growth ${after.growth}", after.growth in 0.05f..0.1f)
    }

    @Test fun aGrassCoverCanBeSownOver() {
        val g = colony(7L)
        val x = g.homeX + 7; val y = g.homeY
        val i = g.map.idx(x, y)
        g.map.terrain[i] = Terrain.GRASS
        g.map.plant[i] = Plant(PlantType.GRASS, x, y, 1f)
        g.paintZone(x, y, x, y, ZoneKind.GROWING, PlantType.RICE)
        val colonist = g.colonists.first()
        val job = g.findGrow(colonist)
        assertNotNull("a sowing job is found on the grass", job)
        assertEquals(JobType.SOW, job!!.type)
        assertEquals(x, job.tx)
        assertEquals(y, job.ty)
    }

    // Generation.

    @Test fun aMapHasGrassGroundAndCoverOnTheGrassOnly() {
        for (seed in listOf(3L, 11L, 23L)) {
            val m = GameMap.generateFor(250, 250, seed, Biome.TEMPERATE)
            val grass = m.grassCells()
            val share = grass.size.toFloat() / m.size
            assertTrue("seed $seed: grass ground is a share of the map, got $share", share in 0.02f..0.5f)
            val covers = m.grassCovers()
            for (c in covers) assertEquals("a grass cover is on grass ground", Terrain.GRASS, m.terrain[c])
            val covered = covers.size.toFloat() / grass.size
            assertTrue("seed $seed: most grass ground has a cover, got $covered", covered > 0.5f)
            for (c in grass) assertTrue("grass ground is passable", m.terrain[c].passable)
        }
    }

    /** The desert and arid biomes share the code's desert flag, so neither gets grass ground or covers. */
    @Test fun noGrassOnADesertOrAridMap() {
        for (b in listOf(Biome.DESERT, Biome.ARID)) {
            val m = GameMap.generateFor(250, 250, 5L, b)
            assertEquals("$b grass ground", 0, m.grassCells().size)
            assertEquals("$b grass covers", 0, m.grassCovers().size)
        }
    }

    @Test fun theGrassLayoutIsTheSameForTheSameSeed() {
        val a = GameMap.generateFor(250, 250, 9L, Biome.TEMPERATE)
        val b = GameMap.generateFor(250, 250, 9L, Biome.TEMPERATE)
        assertEquals(a.grassCells(), b.grassCells())
        assertEquals(a.grassCovers(), b.grassCovers())
    }

    /**
     * Grass ground counts as soil for the start pad, so the pad is the same cell with the grass taken to be soil. The check is
     * made both ways on the same map.
     */
    @Test fun theStartPadIsTheSameWhetherGrassCountsAsSoilOrNot() {
        for (seed in listOf(3L, 11L)) {
            val m = GameMap.generateFor(250, 250, seed, Biome.TEMPERATE)
            val grass = m.grassCells()
            val withGrass = m.findOpenPad()
            for (c in grass) m.terrain[c] = Terrain.SOIL
            val asSoil = m.findOpenPad()
            for (c in grass) m.terrain[c] = Terrain.GRASS
            assertEquals("seed $seed", asSoil, withGrass)
        }
    }

    // Saves.

    /** A save that holds grass ground and covers at several growth stages round-trips cell for cell. */
    @Test fun aSaveWithGrassRoundTrips() {
        val g = colony(13L)
        val m = g.map
        var n = 0
        for (y in 20 until 60) for (x in 20 until 60) {
            val i = m.idx(x, y)
            if (m.terrain[i] != Terrain.SOIL && m.terrain[i] != Terrain.GRASS) continue
            m.terrain[i] = Terrain.GRASS
            if (m.plant[i] == null && n % 3 != 2) {
                val growth = when (n % 4) { 0 -> 0.1f; 1 -> 0.5f; 2 -> 0.8f; else -> 1f }
                m.plant[i] = Plant(PlantType.GRASS, x, y, growth).also { it.age = 1000 + n }
            }
            n++
        }
        val covers = m.grassCovers()
        assertTrue("the test map has grass covers", covers.size > 100)
        val bytes = SaveGame.write(g)
        assertEquals(SaveGame.VERSION, ByteBuffer.wrap(bytes).int)
        val back = SaveGame.read(bytes)
        assertEquals(m.w, back.map.w)
        assertEquals(m.h, back.map.h)
        for (i in 0 until m.size) {
            assertEquals("terrain of cell $i", m.terrain[i], back.map.terrain[i])
            val a = m.plant[i]; val b = back.map.plant[i]
            assertEquals("plant type of cell $i", a?.type, b?.type)
            if (a != null) {
                assertEquals("growth of cell $i", a.growth, b!!.growth, 0f)
                assertEquals("age of cell $i", a.age, b.age)
            }
        }
        assertEquals(covers, back.map.grassCovers())
        assertEquals(m.grassCells(), back.map.grassCells())
    }

    /** colony_v38.sav was written before grass existed. It loads, has none, and a new save is the current version (40). */
    @Test fun aVersion38SaveHasNoGrassAndLoadsAsTheCurrentVersion() {
        val fixture = resource("colony_v38.sav")
        assertEquals(38, ByteBuffer.wrap(fixture).int)
        val g = SaveGame.read(fixture)
        assertEquals(250, g.map.w)
        assertEquals(0, g.map.grassCells().size)
        assertEquals(0, g.map.grassCovers().size)
        val bytes = SaveGame.write(g)
        assertEquals(SaveGame.VERSION, ByteBuffer.wrap(bytes).int)
        val again = SaveGame.read(bytes)
        assertEquals(0, again.map.grassCells().size)
        assertEquals(g.colonists.size, again.colonists.size)
    }
}
