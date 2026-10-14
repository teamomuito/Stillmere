package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Construction and power, plants and food, and research: the values and rules adopted from the reference (docs/fidelity/economy.md). */
class EconomyFidelityTest {

    /** A colony with every building and conduit removed, so a power step sees only what a test puts down. */
    private fun cleanGame(seed: Long = 11L): Game {
        val g = Game(seed)
        g.startNewColony(Scenario.QUIET_ENCLAVE)
        for (b in g.map.buildings().toList()) g.map.removeBuilding(b)
        g.map.conduit.fill(false)
        // No rooms: a roof in these tests is a natural roof set by hand, so indoor status never adds a roof.
        g.map.roomDirty = true
        return g
    }

    private fun Game.plainCell(dx: Int, dy: Int): Int {
        val i = map.idx(homeX + dx, homeY + dy)
        map.terrain[i] = Terrain.SOIL
        return i
    }

    // ------------------------------------------------------------------ construction and power

    @Test fun aBatteryHoldsOneThousandWattDays() {
        // 1,000 Wd: the code default (D, RimWorld/CompProperties_Battery.cs:5). The battery XML may override it (UNVERIFIED).
        assertEquals(1000f, BATTERY_CAPACITY_WD, 0f)
    }

    @Test fun aNetWithBatteriesLosesFiveWattsEachTickOnThePowerStep() {
        val g = cleanGame()
        val x = g.homeX + 8; val y = g.homeY + 8
        g.plainCell(8, 8)
        val bat = Building(BuildDef.BATTERY, x, y, true)
        bat.charge = 100f
        g.map.setBuilding(bat)
        g.map.conduit[g.map.idx(x - 1, y)] = true
        g.powerTick()
        // Five watts for each of the 625 ticks in a slow step, at 1/60000 watt-day per watt-tick (RimWorld/CompPowerBattery.cs:70).
        val drain = 5f * Game.SLOW_TICK.toFloat() / TICKS_PER_DAY
        assertEquals("one slow step of self-discharge", 100f - drain, bat.charge, 1e-4f)
    }

    @Test fun selfDischargeNeverTakesMoreThanTheBatteryHolds() {
        val g = cleanGame()
        val x = g.homeX + 8; val y = g.homeY + 8
        g.plainCell(8, 8)
        val bat = Building(BuildDef.BATTERY, x, y, true)
        bat.charge = 0.01f
        g.map.setBuilding(bat)
        g.map.conduit[g.map.idx(x - 1, y)] = true
        g.powerTick()
        assertEquals(0f, bat.charge, 0f)
    }

    @Test fun eachBatteryOnANetDrainsItsOwnFiveWatts() {
        val g = cleanGame()
        val x = g.homeX + 8; val y = g.homeY + 8
        g.plainCell(8, 8); g.plainCell(9, 8); g.plainCell(10, 8)
        val a = Building(BuildDef.BATTERY, x, y, true); a.charge = 200f
        val c = Building(BuildDef.BATTERY, x + 2, y, true); c.charge = 200f
        g.map.setBuilding(a); g.map.setBuilding(c)
        g.map.conduit[g.map.idx(x - 1, y)] = true
        g.map.conduit[g.map.idx(x + 1, y)] = true
        g.powerTick()
        val drain = 5f * Game.SLOW_TICK.toFloat() / TICKS_PER_DAY
        assertEquals("two batteries lose two drains", 400f - 2f * drain, a.charge + c.charge, 1e-3f)
    }

    @Test fun aBreezeMillUsesNoMoreThanOneAndAHalfWind() {
        val g = cleanGame()
        val x = g.homeX + 8; val y = g.homeY + 8
        g.plainCell(8, 8)
        val mill = Building(BuildDef.BREEZE_MILL, x, y, true)
        g.map.setBuilding(mill)
        g.map.conduit[g.map.idx(x - 1, y)] = true
        g.windFactor = 2f
        g.powerTick()
        assertEquals(BuildDef.BREEZE_MILL.power * 1.5f, g.power.produced, 1e-3f)
    }

    @Test fun theBreezeCapIsOneAndAHalf() {
        assertEquals(1.5f, BREEZE_OUTPUT_WIND_CAP, 0f)
    }

    @Test fun aSunArrayOutputIsScaledByItsUnroofedShare() {
        val g = cleanGame()
        g.tick = 12L * TICKS_PER_HOUR
        val x = g.homeX + 8; val y = g.homeY + 8
        for (dy in 0 until 3) for (dx in 0 until 3) g.plainCell(8 + dx, 8 + dy)
        val panel = Building(BuildDef.SOLAR_PANEL, x, y, true)
        g.map.setBuilding(panel)
        // Three of the nine cells roofed: two thirds of the footprint is open.
        for (k in 0 until 3) g.map.natRoof[g.map.idx(x + k, y)] = true
        assertEquals(6f / 9f, g.map.unroofedShare(panel), 1e-6f)
        g.map.conduit[g.map.idx(x - 1, y)] = true
        g.powerTick()
        assertEquals(BuildDef.SOLAR_PANEL.power * g.daylight() * 6f / 9f, g.power.produced, 1e-2f)
    }

    @Test fun aSunArrayUnderTheRoofMakesNothing() {
        val g = cleanGame()
        g.tick = 12L * TICKS_PER_HOUR
        val x = g.homeX + 8; val y = g.homeY + 8
        for (dy in 0 until 3) for (dx in 0 until 3) { g.plainCell(8 + dx, 8 + dy); g.map.natRoof[g.map.idx(x + dx, y + dy)] = true }
        val panel = Building(BuildDef.SOLAR_PANEL, x, y, true)
        g.map.setBuilding(panel)
        g.map.conduit[g.map.idx(x - 1, y)] = true
        g.powerTick()
        assertEquals(0f, g.power.produced, 0f)
    }

    // ------------------------------------------------------------------ plants and food

    @Test fun aCropGrowsOneGrowTimePerDayAtFullFactors() {
        // The rate per slow tick, times the slow ticks in a day, is 1 / grow days: no extra 0.55 (RimWorld/Plant.cs:346).
        val perDay = growthPerSlowTick(PlantType.RICE, fertilityGrowthFactor(1f), 20f, 1f) * (TICKS_PER_DAY / Game.SLOW_TICK.toFloat())
        assertEquals(1f / PlantType.RICE.growDays, perDay, 1e-6f)
    }

    @Test fun aPoweredGrowTrayKeepsItsOldGrowthFactorOfTwo() {
        // The tray's factor is applied directly, not through the fertility blend, so it stays at the value it had (a Stillmere rule, C).
        assertEquals(2f, GROW_TRAY_GROWTH_FACTOR, 0f)
        assertEquals(
            2f * growthPerSlowTick(PlantType.RICE, fertilityGrowthFactor(1f), 20f, 1f),
            growthPerSlowTick(PlantType.RICE, GROW_TRAY_GROWTH_FACTOR, 20f, 1f),
            1e-9f,
        )
    }

    @Test fun fertilityBlendsWithTheDefaultSensitivityOfHalf() {
        assertEquals(1f, fertilityGrowthFactor(1f), 1e-6f)
        assertEquals(1.2f, fertilityGrowthFactor(1.4f), 1e-6f)
        assertEquals(0.5f, fertilityGrowthFactor(0f), 1e-6f)
        assertEquals(0.575f, fertilityGrowthFactor(0.15f), 1e-6f)
    }

    @Test fun theTemperatureRampRunsFromZeroAtSixDegreesBelowTheBand() {
        // Rice: the band is 6 to 42 C. Growth is zero at 0 and 58 C, a ramp in between (RimWorld/PlantUtility.cs:370-381).
        assertEquals(0f, temperatureGrowthFactor(0f, 6f, 42f), 1e-6f)
        assertEquals(0.5f, temperatureGrowthFactor(3f, 6f, 42f), 1e-6f)
        assertEquals(1f, temperatureGrowthFactor(6f, 6f, 42f), 1e-6f)
        assertEquals(1f, temperatureGrowthFactor(42f, 6f, 42f), 1e-6f)
        assertEquals(0.5f, temperatureGrowthFactor(50f, 6f, 42f), 1e-6f)
        assertEquals(0f, temperatureGrowthFactor(58f, 6f, 42f), 1e-6f)
        assertEquals(0f, temperatureGrowthFactor(-1f, 6f, 42f), 1e-6f)
    }

    @Test fun theLightRampRunsFromZeroAtHalfGlowToFullAtTheOptimum() {
        assertEquals(0f, lightGrowthFactor(0.51f), 1e-6f)
        assertEquals(0.5f, lightGrowthFactor(0.755f), 1e-5f)
        assertEquals(1f, lightGrowthFactor(1f), 1e-6f)
        assertEquals(0f, lightGrowthFactor(0.2f), 0f)
    }

    @Test fun aCellsGlowIsTheBrighterOfDaylightAndItsArtificialLight() {
        // Daylight alone gives its own glow, so a dim day stays on the ramp (RimWorld/PlantUtility.cs:361-367). Only a lamp or a
        // fire above the daylight raises it. A cell lit to 0.55 by daylight is about a twelfth of full light, not full light.
        assertEquals(0.55f, plantGlow(0.55f, 0.55f), 0f)
        assertEquals(0.55f, plantGlow(0.55f, 0f), 0f)
        assertEquals(1f, plantGlow(0.2f, 1f), 0f)
        assertEquals(0.0816f, lightGrowthFactor(plantGlow(0.55f, 0.55f)), 1e-3f)
    }

    @Test fun aPlantUnderDimDaylightGrowsOnTheRampInTheWorldTick() {
        // Thunder at noon gives daylight 0.55, just above the 0.51 minimum glow. The world tick must grow the plant at glow 0.55
        // on the ramp. The old step gave full glow above half the daylight, about twelve times this growth.
        val g = cleanGame()
        g.weather = Weather.THUNDER
        g.weatherUntil = Long.MAX_VALUE
        g.eclipseUntil = 0L
        g.tick = 12L * TICKS_PER_HOUR
        val i = g.plainCell(3, 0)
        g.map.natRoof[i] = false
        g.map.plant[i] = Plant(PlantType.RICE, g.map.xOf(i), g.map.yOf(i), 0.1f)
        val before = g.map.plant[i]!!.growth
        g.worldSlowTick()
        val t = g.map.tempAt(i, g.outdoorTemp())
        val expected = growthPerSlowTick(PlantType.RICE, fertilityGrowthFactor(1f), t, 0.55f)
        assertTrue("the test cell must be inside the growth limits", expected > 0f)
        assertEquals(expected, g.map.plant[i]!!.growth - before, 1e-6f)
    }

    @Test fun aClearDayGivesAboutHalfAGrowTimeOfLightOnTheRamp() {
        // Sampled at the slow tick over a clear day, the light factor averages about 0.499: a ramp from 6:00 to 7:00 and from
        // 18:00 to 19:00, full light between. Rice (5.8 grow days) then takes about 11.6 game days (docs/fidelity/economy.md).
        val g = cleanGame()
        g.weather = Weather.CLEAR
        g.weatherUntil = Long.MAX_VALUE
        g.eclipseUntil = 0L
        var sum = 0f
        var samples = 0
        var t = 0L
        while (t < TICKS_PER_DAY) {
            g.tick = t
            sum += lightGrowthFactor(g.daylight())
            samples++
            t += Game.SLOW_TICK
        }
        val average = sum / samples
        assertEquals(0.499f, average, 0.003f)
        assertEquals(11.6f, PlantType.RICE.growDays / average, 0.1f)
    }

    @Test fun aCropSowsOnlyInsideItsGrowthLimits() {
        assertFalse(growthSeasonOk(0f, PlantType.RICE))
        assertTrue(growthSeasonOk(1f, PlantType.RICE))
        assertTrue(growthSeasonOk(57f, PlantType.RICE))
        assertFalse(growthSeasonOk(58f, PlantType.RICE))
    }

    @Test fun aCropCanBeHarvestedOnceItPassesSixtyFivePercent() {
        assertFalse(Plant(PlantType.RICE, 0, 0, 0.65f).mature)
        assertTrue(Plant(PlantType.RICE, 0, 0, 0.66f).mature)
        assertFalse("a tree still needs full growth", Plant(PlantType.OAK, 0, 0, 0.9f).mature)
        assertTrue(Plant(PlantType.OAK, 0, 0, 1f).mature)
    }

    @Test fun aPlantAtOrBelowTheHarvestMinimumYieldsNothing() {
        // Not harvestable at or below 65% growth (RimWorld/Plant.cs:115), so the cut gives no yield (RimWorld/Plant.cs:917-919, 934-936).
        // A 5% oak sapling gives no wood, not half its count.
        assertEquals(0f, harvestYieldExact(24, 0.05f), 0f)
        assertEquals(0f, harvestYieldExact(40, 0.3f), 0f)
        assertEquals(0f, harvestYieldExact(40, 0.65f), 0f)
    }

    @Test fun harvestYieldRisesFromAboutHalfJustAboveTheMinimumToFullAtFullGrowth() {
        assertEquals(40f, harvestYieldExact(40, 1f), 1e-4f)
        assertEquals(40f * (0.5f + 0.5f * 0.01f / 0.35f), harvestYieldExact(40, 0.66f), 1e-4f)
        assertEquals(30f, harvestYieldExact(40, 0.825f), 1e-4f)
    }

    @Test fun rotRateIsZeroAtFreezingAndFullFromTenDegrees() {
        assertEquals(0f, rotTemperatureFactor(-5f), 0f)
        assertEquals(0f, rotTemperatureFactor(0f), 0f)
        assertEquals(0.5f, rotTemperatureFactor(5f), 1e-6f)
        assertEquals(1f, rotTemperatureFactor(10f), 0f)
        assertEquals(1f, rotTemperatureFactor(30f), 0f)
    }

    @Test fun rawMeatSpoilsInTwoDaysAndCorpsesInTwoAndAHalf() {
        assertEquals(2f, ItemType.MEAT.spoilDays, 0f)
        assertEquals(2.5f, CORPSE_ROT_DAYS, 0f)
    }

    @Test fun rawMeatPoisonsOneInFiftyEaters() {
        assertEquals(0.02f, RAW_MEAT_POISON_CHANCE, 0f)
    }

    // ------------------------------------------------------------------ research

    @Test fun researchPointsFollowTheReferenceRateOnStillmeresScale() {
        assertEquals(0.00825f, RESEARCH_BASE_RATE, 0f)
        assertEquals(0.132f, researchPointsPerTick(1f, 1f, false), 1e-6f)
        assertEquals("a precision lab and keen mind", 0.132f * 1.6f * 1.35f, researchPointsPerTick(1f, 1.6f, true), 1e-5f)
    }

    @Test fun aProjectTakesItsBaseCostOverTheReferenceRate() {
        // Stillmere's costs are base cost times the unit scale, so the time is base / 0.00825 ticks at speed 1 (UNVERIFIED base).
        val ticks = Research.JOINERY_DETAIL.cost / researchPointsPerTick(1f, 1f, false)
        assertEquals(400f / 0.00825f, ticks, 0.5f)
    }

    @Test fun theResearchCostScaleIsUnchanged() {
        assertEquals(400f * RESEARCH_POINT_SCALE, Research.JOINERY_DETAIL.cost, 0f)
        assertEquals(16f, RESEARCH_POINT_SCALE, 0f)
    }

    @Test fun aResearchSessionLastsFourThousandTicks() {
        assertEquals(4000, RESEARCH_SESSION_TICKS)
    }
}
