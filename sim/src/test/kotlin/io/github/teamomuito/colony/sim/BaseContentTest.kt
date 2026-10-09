package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun contentGame(seed: Long): Game {
    val g = Game(seed)
    g.startNewColony(Scenario.LOST_TRIBE)
    g.mentalBreaksEnabled = false
    g.pawns.removeAll { it.isAnimal && it.faction == Faction.WILD }
    val never = Long.MAX_VALUE
    g.nextRaid = never; g.nextMisc = never; g.nextWanderer = never; g.nextTrader = never; g.nextPod = never; g.nextTempEvent = never
    // Clear a strip of ground so tests see only what they place.
    for (y in g.homeY - 3..g.homeY + 3) for (x in g.homeX - 3..g.homeX + 30) {
        val i = g.map.idx(x, y)
        g.map.terrain[i] = Terrain.SOIL; g.map.plant[i] = null; g.map.building[i] = null; g.map.items.remove(i)
    }
    g.map.roomDirty = true
    return g
}

/** A finished, powered building on the cleared strip. */
private fun Game.build(d: BuildDef, x: Int, y: Int, powered: Boolean = true): Building {
    val b = Building(d, x, y, true)
    b.powered = powered
    map.setBuilding(b)
    return b
}

private fun Game.hostileAt(x: Int, y: Int): Pawn {
    val e = newHuman(x, y, Faction.ENEMY)
    e.x = x; e.y = y
    if (e !in pawns) pawns.add(e)
    return e
}

class BaseContentTest {

    // ----------------------------------------------------------------------------------- weapons on buildings

    @Test fun aMiniTurretFiresItsOwnBurstAndCoolsDownByItsOwnTime() {
        val g = contentGame(1101)
        val t = g.build(BuildDef.MINI_TURRET, g.homeX, g.homeY)
        g.hostileAt(g.homeX + 8, g.homeY)
        val before = g.shots.size
        g.turretsTick()
        assertEquals("one burst of the mini-turret gun", Weapon.MINI_TURRET_GUN.burst, g.shots.size - before)
        assertEquals(Weapon.MINI_TURRET_GUN.cooldown, t.cooldown)
    }

    @Test fun aGunTurretUsesTheTurretGunTable() {
        val g = contentGame(1102)
        val t = g.build(BuildDef.TURRET, g.homeX, g.homeY)
        g.hostileAt(g.homeX + 8, g.homeY)
        g.turretsTick()
        assertEquals(Weapon.TURRET_GUN.cooldown, t.cooldown)
    }

    @Test fun aMortarShellsAndCoolsDownFromTheShellTable() {
        val g = contentGame(1103)
        val m = g.build(BuildDef.MORTAR, g.homeX, g.homeY)
        m.shells = 3
        g.hostileAt(g.homeX + 12, g.homeY)
        g.turretsTick()
        assertEquals("a shell was fired", 2, m.shells)
        assertEquals(Weapon.MORTAR_SHELL.cooldown, m.cooldown)
    }

    @Test fun turretsOnlyFireAtEnemiesInRange() {
        val g = contentGame(1104)
        val t = g.build(BuildDef.MINI_TURRET, g.homeX, g.homeY)
        g.hostileAt(g.homeX + Weapon.MINI_TURRET_GUN.range.toInt() + 5, g.homeY)
        val before = g.shots.size
        g.turretsTick()
        assertEquals(before, g.shots.size)
        assertEquals(0, t.cooldown)
    }

    // ----------------------------------------------------------------------------------- production

    @Test fun aPoweredDeepDrillBringsUpItsOre() {
        val g = contentGame(1105)
        val x = g.homeX + 4; val y = g.homeY + 2
        g.map.ore[g.map.idx(x, y)] = Ore.STEEL
        g.build(BuildDef.DEEP_DRILL, x, y)
        val before = g.map.countItems(ItemType.STEEL)
        g.productionTick()
        assertTrue("steel came up", g.map.countItems(ItemType.STEEL) > before)
    }

    @Test fun aDeepDrillWithNoOreBringsUpStoneChunks() {
        val g = contentGame(1106)
        val x = g.homeX + 4; val y = g.homeY + 2
        g.map.ore[g.map.idx(x, y)] = Ore.NONE
        g.build(BuildDef.DEEP_DRILL, x, y)
        g.productionTick()
        assertTrue(g.map.countItems(ItemType.STONE_CHUNK) > 0)
    }

    @Test fun anUnpoweredDeepDrillDoesNothing() {
        val g = contentGame(1107)
        val x = g.homeX + 4; val y = g.homeY + 2
        g.map.ore[g.map.idx(x, y)] = Ore.STEEL
        g.build(BuildDef.DEEP_DRILL, x, y, powered = false)
        g.productionTick()
        assertEquals(0, g.map.countItems(ItemType.STEEL))
    }

    @Test fun aCrematoriumBurnsTheNearestCorpseOnly() {
        val g = contentGame(1108)
        g.build(BuildDef.CREMATORIUM, g.homeX, g.homeY)
        val near = g.map.idx(g.homeX + 2, g.homeY)
        val far = g.map.idx(g.homeX + 20, g.homeY)
        g.map.items[near] = ItemStack(g.map.nextId(), ItemType.CORPSE_HUMAN, 1, g.homeX + 2, g.homeY).also { it.corpseOf = "Test" }
        g.map.items[far] = ItemStack(g.map.nextId(), ItemType.CORPSE_HUMAN, 1, g.homeX + 20, g.homeY).also { it.corpseOf = "Far" }
        g.productionTick()
        assertFalse("the near corpse is burned", g.map.items.containsKey(near))
        assertTrue("the far corpse is out of reach", g.map.items.containsKey(far))
    }

    // ----------------------------------------------------------------------------------- recipes and bills

    @Test fun sculpturesAreCarvedAtTheTableAndPlacedAsArt() {
        assertTrue(Recipe.CARVE_SMALL.benches.contains(BuildDef.SCULPTOR_TABLE))
        assertEquals(ItemType.SCULPTURE_SMALL, Recipe.CARVE_SMALL.out)
        assertEquals(ItemType.SCULPTURE_SMALL, BuildDef.SCULPTURE_SMALL.cost.first().first)
        assertEquals(ItemType.SCULPTURE_LARGE, BuildDef.SCULPTURE_LARGE.cost.first().first)
    }

    @Test fun beerCanBeBrewedAtTheBreweryAndTheFermentingBarrel() {
        assertTrue(Recipe.BREW_BEER.benches.containsAll(listOf(BuildDef.BREWERY, BuildDef.FERMENTING_BARREL)))
    }

    @Test fun nutrientPasteIsMadeAtTheDispenserFromAnyFood() {
        assertTrue(Recipe.MAKE_NUTRIENT.benches.contains(BuildDef.NUTRIENT_DISPENSER))
        assertEquals(ItemType.MEAL_NUTRIENT, Recipe.MAKE_NUTRIENT.out)
        assertTrue(Recipe.MAKE_NUTRIENT.inputs.any { it.accepts(ItemType.RICE) })
    }

    @Test fun berriesAreFoodAndTheBushYieldsThem() {
        assertTrue(ItemType.BERRIES.isFood)
        assertEquals(ItemType.BERRIES, PlantType.BERRY.yieldType)
        assertTrue(Recipe.COOK_SIMPLE.inputs.any { it.accepts(ItemType.BERRIES) })
    }

    @Test fun theNewBuildingsAreResearchedAndPlaceable() {
        assertEquals(Research.DEEP_DRILLING, BuildDef.DEEP_DRILL.research)
        assertEquals(Research.NUTRIENT_PASTE, BuildDef.NUTRIENT_DISPENSER.research)
        assertEquals(Research.SCULPTING, BuildDef.SCULPTOR_TABLE.research)
        assertEquals(Research.CREMATION, BuildDef.CREMATORIUM.research)
        assertEquals(Research.GUN_TURRETS, BuildDef.MINI_TURRET.research)
        assertTrue(BuildDef.entries.filter { it.category == "Production" }.all { !it.legacy })
    }

    // ----------------------------------------------------------------------------------- content that is not offered

    @Test fun unconfirmedContentIsNotOffered() {
        assertFalse(BaseContent.offered(Recipe.MAKE_LEVER))
        assertFalse(BaseContent.offered(Recipe.BREW_TEA))
        assertTrue("ordinary recipes are offered", BaseContent.offered(Recipe.MAKE_RIFLE))
    }

    @Test fun traderStockNeverContainsHiddenContent() {
        for (seed in 1L..12L) {
            val g = contentGame(seed)
            g.spawnTrader()
            val t = g.traders.first()
            for (hidden in BaseContent.hiddenItems) assertEquals("$hidden on seed $seed", 0, t.stock.count(hidden))
        }
    }

    @Test fun thereIsNoLongerAnUnusedAcidSpitWeapon() {
        assertNotNull(Weapon.valueOf("MINI_TURRET_GUN"))
        assertTrue(Weapon.entries.none { it.name == "SPIT" })
    }
}
