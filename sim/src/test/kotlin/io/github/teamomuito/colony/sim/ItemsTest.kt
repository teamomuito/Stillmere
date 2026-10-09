package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun itemGame(seed: Long): Game {
    val g = Game(seed)
    g.startNewColony(Scenario.LOST_TRIBE)
    g.mentalBreaksEnabled = false
    g.pawns.removeAll { it.isAnimal && it.faction == Faction.WILD }
    val never = Long.MAX_VALUE
    g.nextRaid = never; g.nextMisc = never; g.nextWanderer = never; g.nextTrader = never; g.nextPod = never; g.nextTempEvent = never
    return g
}

/** A stockpile zone on the colony's cell, so goods there count as sellable. */
private fun Game.stockpileAtHome(): Int {
    val z = map.newZone(ZoneKind.STOCKPILE)
    val i = map.idx(homeX, homeY)
    map.zoneId[i] = z.id
    return i
}

class ItemsTest {

    // ----------------------------------------------------------------------------------- Stock

    @Test fun stockKeepsEachQualityAndConditionAsItsOwnLot() {
        val s = Stock()
        s.add(Lot(ItemType.W_RIFLE, Quality.GOOD, 100), 1)
        s.add(Lot(ItemType.W_RIFLE, Quality.POOR, 40), 2)
        assertEquals(3, s.count(ItemType.W_RIFLE))
        assertEquals(1, s.count(Lot(ItemType.W_RIFLE, Quality.GOOD, 100)))
        assertEquals(2, s.count(Lot(ItemType.W_RIFLE, Quality.POOR, 40)))
        assertEquals(0, s.count(Lot(ItemType.W_RIFLE, Quality.NORMAL, 100)))
    }

    @Test fun takingGoodsRemovesThemFromTheRightLots() {
        val s = Stock()
        s.add(Lot(ItemType.A_FLAK_VEST, Quality.GOOD, 100), 1)
        s.add(Lot(ItemType.A_FLAK_VEST, Quality.POOR, 50), 2)
        val taken = s.take(ItemType.A_FLAK_VEST, 2)
        assertEquals(2, taken.sumOf { it.second })
        assertEquals("one good vest is left", 1, s.count(ItemType.A_FLAK_VEST))
        assertTrue(s.entries().isNotEmpty())
    }

    @Test fun peekDoesNotRemove() {
        val s = Stock()
        s.add(ItemType.STEEL, 10)
        assertEquals(4, s.peek(ItemType.STEEL, 4).sumOf { it.second })
        assertEquals(10, s.count(ItemType.STEEL))
    }

    @Test fun stockEqualityIgnoresOrderAndCopiesAreIndependent() {
        val a = Stock().apply { add(ItemType.WOOD, 5); add(ItemType.STEEL, 3) }
        val b = Stock().apply { add(ItemType.STEEL, 3); add(ItemType.WOOD, 5) }
        assertEquals(a, b)
        val c = a.copy()
        c.add(ItemType.WOOD, 1)
        assertNotEquals(a, c)
        assertEquals(5, a.count(ItemType.WOOD))
    }

    // ----------------------------------------------------------------------------------- moving goods

    @Test fun droppedGearKeepsItsConditionAndQuality() {
        val g = itemGame(901)
        val p = g.colonists[0]
        p.apparel.clear()
        p.apparel.add(Worn(ItemType.A_FLAK_VEST, Quality.GOOD, 80f))   // half of its 160 hit points
        g.die(p, "test")
        val vest = g.map.items.values.firstOrNull { it.type == ItemType.A_FLAK_VEST }
        assertNotNull(vest)
        assertEquals(Quality.GOOD, vest!!.quality)
        assertEquals("the vest is still half worn", 0.5f, vest.hp, 0.01f)
    }

    @Test fun caravanDeathKeepsTheDeadPersonsGear() {
        val g = itemGame(902)
        val p = g.colonists[0]
        g.pawns.remove(p)
        val c = Caravan(g.nextCaravanId++, "Test", g.world.homeTile)
        c.members.add(p)
        g.caravans.add(c)
        p.weaponItem = ItemType.W_RIFLE; p.weaponQuality = Quality.GOOD
        p.apparel.clear(); p.apparel.add(Worn(ItemType.A_FLAK_VEST, Quality.GOOD, 80f))
        g.caravanDeath(p, "test")
        assertEquals(1, c.inventory.count(Lot(ItemType.W_RIFLE, Quality.GOOD, 100)))
        assertEquals("the cargo keeps the vest's wear", 1, c.inventory.count(Lot(ItemType.A_FLAK_VEST, Quality.GOOD, 50)))
    }

    @Test fun unloadingACaravanKeepsQualityAndCondition() {
        val g = itemGame(903)
        val c = Caravan(g.nextCaravanId++, "Test", g.world.homeTile)
        g.caravans.add(c)
        c.inventory.add(Lot(ItemType.W_RIFLE, Quality.GOOD, 70), 2)
        g.unloadCaravan(c)
        val rifles = g.map.items.values.filter { it.type == ItemType.W_RIFLE }
        assertEquals(2, rifles.sumOf { it.count })
        assertTrue(rifles.all { it.quality == Quality.GOOD && kotlin.math.abs(it.hp - 0.7f) < 0.01f })
    }

    @Test fun aTraderSellsGearAtItsQualityAndBuyersGetItBack() {
        val g = itemGame(904)
        val cell = g.stockpileAtHome()
        val rifle = ItemStack(g.map.nextId(), ItemType.W_RIFLE, 1, g.homeX, g.homeY)
        rifle.quality = Quality.GOOD
        g.map.items[cell] = rifle
        val info = TraderInfo(-1, "Trader", g.tick, g.tick + 1000)
        info.silver = 2000
        g.sellItem(info, ItemType.W_RIFLE, 1)
        assertEquals("the trader holds the rifle at its quality", 1, info.stock.count(Lot(ItemType.W_RIFLE, Quality.GOOD, 100)))
        assertFalse("the rifle left the map", g.map.items.values.any { it.type == ItemType.W_RIFLE })

        g.map.drop(ItemType.SILVER, 1000, g.homeX, g.homeY)   // buying back costs more than the sale earned
        assertTrue(g.buyItem(info, ItemType.W_RIFLE, 1))
        assertTrue("the buyer gets a good rifle back", g.map.items.values.any { it.type == ItemType.W_RIFLE && it.quality == Quality.GOOD })
    }

    // ----------------------------------------------------------------------------------- saves

    @Test fun cargoLotsSurviveASave() {
        val g = itemGame(905)
        val c = Caravan(g.nextCaravanId++, "Test", g.world.homeTile)
        g.caravans.add(c)
        c.inventory.add(Lot(ItemType.A_FLAK_VEST, Quality.EXCELLENT, 62), 1)
        c.inventory.add(ItemType.STEEL, 40)
        val loaded = SaveGame.read(SaveGame.write(g))
        assertEquals(c.inventory, loaded.caravans.first { it.name == "Test" }.inventory)
    }

    @Test fun anOlderSaveLoadsItsCargoAsNormalGoods() {
        val g = itemGame(906)
        val c = Caravan(g.nextCaravanId++, "Test", g.world.homeTile)
        g.caravans.add(c)
        c.inventory.add(Lot(ItemType.W_RIFLE, Quality.GOOD, 70), 2)
        val loaded = SaveGame.read(SaveGame.write(g, version = 21))
        val lc = loaded.caravans.first { it.name == "Test" }
        assertEquals("the count survives", 2, lc.inventory.count(ItemType.W_RIFLE))
        assertEquals("version 21 kept only the kind, so it comes back as normal goods", 2, lc.inventory.count(Lot(ItemType.W_RIFLE)))
    }
}

