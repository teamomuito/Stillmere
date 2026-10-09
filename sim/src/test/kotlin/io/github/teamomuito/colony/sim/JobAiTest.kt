package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun newGame(seed: Long): Game {
    val g = Game(seed)
    g.startNewColony(Scenario.LOST_TRIBE)
    g.mentalBreaksEnabled = false
    g.pawns.removeAll { it.isAnimal && it.faction == Faction.WILD }
    g.nextRaid = Long.MAX_VALUE; g.nextMisc = Long.MAX_VALUE; g.nextWanderer = Long.MAX_VALUE; g.nextTrader = Long.MAX_VALUE
    return g
}

private fun Game.run(ticks: Int) { repeat(ticks) { step() } }

/** Runs until [cond] holds, for at most [limit] ticks. Returns whether it held. */
private fun Game.runUntil(limit: Int = 900, cond: () -> Boolean): Boolean {
    repeat(limit) { if (cond()) return true; step() }
    return cond()
}

/** Only hauling, for the given pawns. */
private fun Pawn.onlyHauling() { for (w in WorkType.entries) priority[w.ordinal] = if (w == WorkType.HAUL) 1 else 0 }

private fun Game.removeItems(pred: (ItemStack) -> Boolean) {
    for (s in map.items.values.filter(pred)) map.take(map.idx(s.x, s.y), s.count)
}

private fun Game.steelTotal(): Int =
    map.countItems(ItemType.STEEL) + colonists.sumOf { p ->
        (if (p.carryType == ItemType.STEEL) p.carryCount else 0) +
            (p.job?.held?.filter { it.first == ItemType.STEEL }?.sumOf { it.second } ?: 0)
    }

private fun Game.stockpileSteel(): Int =
    map.items.values.filter { it.type == ItemType.STEEL && it.x in homeX + 6..homeX + 8 && it.y in homeY - 6..homeY - 4 }.sumOf { it.count }

// ---------------------------------------------------------------- needs interrupt work

class JobAiTest {
    @Test fun urgentRestInterruptsWorkAndSendsThePawnToSleep() {
        val g = newGame(201)
        val c = g.colonists[0]
        c.job = Job(JobType.IDLE).also { it.timer = 500 }
        c.rest = 0.05f
        g.run(15)
        assertTrue("sleeping, not ${c.job?.type}", c.job?.type == JobType.SLEEP || c.job?.type == JobType.REST)
    }

    @Test fun urgentHungerInterruptsWorkAndSendsThePawnToEat() {
        val g = newGame(202)
        g.removeItems { it.type.isFood }
        val c = g.colonists[0]
        g.map.drop(ItemType.MEAL_SIMPLE, 5, c.x, c.y)
        c.job = Job(JobType.IDLE).also { it.timer = 500 }
        c.food = 0.05f
        g.run(15)
        assertEquals(JobType.EAT, c.job?.type)
    }

    @Test fun hungerWithNoFoodDoesNotThrashTheWorkJob() {
        val g = newGame(203)
        g.removeItems { it.type.isFood }
        val c = g.colonists[0]
        c.job = Job(JobType.IDLE).also { it.timer = 500 }
        c.food = 0.05f
        g.run(25)
        assertEquals(JobType.IDLE, c.job?.type)
    }

    // ---------------------------------------------------------------- beds

    @Test fun hospitalBedIsReleasedWhenThePatientLeavesIt() {
        val g = newGame(204)
        val bedX = g.homeX - 4; val bedY = g.homeY + 6
        g.map.setBuilding(Building(BuildDef.HOSPITAL_BED, bedX, bedY, true))
        val patient = g.colonists[0]
        val bed = g.findBedFor(patient, forceMedical = true)
        assertTrue(bed >= 0)
        assertEquals(patient.id, g.map.building[bed]!!.occupant)
        g.endJob(patient)
        assertEquals("bed freed when the patient's job ends", -1, g.map.building[bed]!!.occupant)
        // The next patient can use it.
        assertEquals(bed, g.findBedFor(g.colonists[1], forceMedical = true))
    }

    // ---------------------------------------------------------------- competing pawns

    @Test fun competingPawnsNeverReserveTheSameItemAndNoSteelIsLost() {
        val g = newGame(205)
        g.removeItems { it.type == ItemType.STEEL }
        g.paintZone(g.homeX + 6, g.homeY - 6, g.homeX + 8, g.homeY - 4, ZoneKind.STOCKPILE)
        g.map.drop(ItemType.STEEL, 30, g.homeX - 6, g.homeY + 2)
        g.map.drop(ItemType.STEEL, 30, g.homeX - 6, g.homeY + 4)
        val rivals = g.colonists.take(3)
        for (p in rivals) p.onlyHauling()
        val before = g.steelTotal()
        repeat(400) {
            g.step()
            // Items are worked alone; stockpile destinations are shared, so only item keys are checked here.
            val holders = g.colonists.flatMap { it.reserved }.filter { kindOf(it) == K_ITEM }.groupingBy { it }.eachCount()
            assertTrue("two pawns reserved one item", holders.values.all { it <= 1 })
            assertEquals("steel conserved", before, g.steelTotal())
        }
    }

    // ---------------------------------------------------------------- unreachable

    @Test fun unreachableItemIsRememberedInsteadOfRetriedEveryTick() {
        val g = newGame(206)
        g.removeItems { it.type == ItemType.STEEL }
        g.paintZone(g.homeX + 6, g.homeY - 6, g.homeX + 8, g.homeY - 4, ZoneKind.STOCKPILE)
        val ix = g.homeX + 2; val iy = g.homeY - 2
        for (dy in -1..1) for (dx in -1..1) if (dx != 0 || dy != 0)
            g.map.setBuilding(Building(BuildDef.WOOD_WALL, ix + dx, iy + dy, true))
        g.map.drop(ItemType.STEEL, 10, ix, iy)
        val c = g.colonists[0]
        c.onlyHauling()
        g.run(200)
        assertTrue("the enclosed item is marked unreachable", g.isBad(c, key(g.map.idx(ix, iy), K_ITEM)))
        assertFalse("no hauling toward the enclosed item", c.job?.let { it.type == JobType.HAUL && it.tx == ix && it.ty == iy } ?: false)
    }

    // ---------------------------------------------------------------- invalid targets

    @Test fun aHaulEndsWhenItsItemVanishesBeforePickup() {
        val g = newGame(207)
        g.removeItems { it.type == ItemType.STEEL }
        g.paintZone(g.homeX + 6, g.homeY - 6, g.homeX + 8, g.homeY - 4, ZoneKind.STOCKPILE)
        g.map.drop(ItemType.STEEL, 20, g.homeX - 6, g.homeY + 2)
        val c = g.colonists[0]
        c.onlyHauling()
        val hauling = g.runUntil { c.job?.type == JobType.HAUL && c.carryCount == 0 }
        assertTrue("pawn started a haul", hauling)
        val job = c.job!!
        g.removeItems { it.x == job.tx && it.y == job.ty }
        g.run(5)
        assertFalse("haul ended", c.job?.type == JobType.HAUL)
        assertTrue("no leftover reservations", c.reserved.isEmpty())
    }

    @Test fun aBuildEndsWhenItsBlueprintIsRemoved() {
        val g = newGame(208)
        g.map.drop(ItemType.WOOD, 40, g.homeX - 3, g.homeY + 4)
        val x = g.homeX - 1; val y = g.homeY + 4
        assertTrue(g.placeBlueprint(BuildDef.WOOD_WALL, x, y))
        val c = g.colonists[0]
        for (w in WorkType.entries) c.priority[w.ordinal] = if (w == WorkType.CONSTRUCT) 1 else 0
        assertTrue("pawn started building", g.runUntil { c.job?.type == JobType.BUILD })
        g.map.removeBuilding(g.map.building[g.map.idx(x, y)]!!)
        g.run(5)
        assertFalse(c.job?.type == JobType.BUILD)
        assertTrue(c.reserved.isEmpty())
    }

    // ---------------------------------------------------------------- interrupted jobs

    @Test fun anInterruptedHaulDropsItsLoadAndReleasesItsReservations() {
        val g = newGame(209)
        g.removeItems { it.type == ItemType.STEEL || it.type.isFood }
        g.paintZone(g.homeX + 6, g.homeY - 6, g.homeX + 8, g.homeY - 4, ZoneKind.STOCKPILE)
        g.map.drop(ItemType.STEEL, 20, g.homeX - 6, g.homeY + 2)
        val c = g.colonists[0]
        c.onlyHauling()
        assertTrue("pawn is carrying steel", g.runUntil { c.job?.type == JobType.HAUL && c.carryCount > 0 })
        val before = g.steelTotal()
        g.map.drop(ItemType.MEAL_SIMPLE, 5, c.x, c.y)
        c.food = 0.05f
        // Interrupted mid-haul: needs are checked every 10 ticks, so the eat job starts well before the walk ends.
        assertTrue("the pawn now eats within one interrupt cycle", g.runUntil(10) { c.job?.type == JobType.EAT })
        assertEquals("nothing was delivered to the stockpile", 0, g.stockpileSteel())
        assertEquals("the load is back on the ground", 0, c.carryCount)
        assertEquals("steel conserved", before, g.steelTotal())
        assertTrue("reservations released", c.reserved.isEmpty())
    }

    @Test fun aSleepingPawnIsNotInterruptedByWork() {
        val g = newGame(210)
        val c = g.colonists[0]
        c.job = Job(JobType.SLEEP).also { it.stage = 1; it.amount = 0 }
        c.rest = 0.05f
        g.run(15)
        assertEquals(JobType.SLEEP, c.job?.type)
        assertNotNull(c.job)
    }
}
