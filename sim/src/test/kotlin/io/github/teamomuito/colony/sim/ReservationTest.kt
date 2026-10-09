package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

class ReservationTest {
    @Test fun anExclusiveKeyHasOneHolderUntilItIsReleased() {
        val g = newGame(301)
        val a = g.colonists[0]; val b = g.colonists[1]
        for (kind in listOf(K_ITEM, K_BUILD, K_PLANT, K_DESIG, K_BED, K_STATION, K_GRAVE, K_PATIENT)) {
            val k = key(42, kind)
            assertTrue("kind $kind: first claim", g.reserve(a, k))
            assertFalse("kind $kind: second claim refused", g.reserve(b, k))
            assertFalse("kind $kind: not free for b", g.isFree(b, k))
            g.releaseAll(a)
            assertTrue("kind $kind: free after release", g.reserve(b, k))
            g.releaseAll(b)
        }
    }

    @Test fun stockpileDropCellsAreSharedButGravesAreNot() {
        val g = newGame(302)
        val drop = key(42, K_DEST)
        for (p in g.colonists.take(3)) assertTrue("drop cell has room for ${p.name}", g.reserve(p, drop))
        assertEquals(3, g.holdersOf(drop).size)

        val grave = key(43, K_GRAVE)
        assertTrue(g.reserve(g.colonists[0], grave))
        assertFalse("a grave takes one burial", g.reserve(g.colonists[1], grave))
    }

    @Test fun aMultiClaimIsAllOrNothing() {
        val g = newGame(303)
        val a = g.colonists[0]; val b = g.colonists[1]; val c = g.colonists[2]
        val k1 = key(10, K_ITEM); val k2 = key(11, K_ITEM)
        assertTrue(g.reserve(b, k2))
        assertFalse("refused because one key is held", g.reserveAll(a, k1, k2))
        assertTrue("nothing was half-claimed", a.reserved.isEmpty())
        assertTrue("the free key was not taken", g.isFree(c, k1))
        assertTrue(g.reserveAll(a, k1))
        assertEquals(listOf(k1), a.reserved.toList())
    }

    @Test fun releasingOneKeyKeepsTheOthers() {
        val g = newGame(304)
        val a = g.colonists[0]; val b = g.colonists[1]
        val k1 = key(20, K_ITEM); val k2 = key(21, K_ITEM)
        assertTrue(g.reserveAll(a, k1, k2))
        g.unreserve(a, k1)
        assertTrue("released key is free", g.isFree(b, k1))
        assertFalse("the other key is still held", g.reserve(b, k2))
        assertEquals(listOf(k2), a.reserved.toList())
    }

    @Test fun aDeadPawnsClaimsAreReleased() {
        val g = newGame(305)
        val a = g.colonists[0]; val b = g.colonists[1]
        val k = key(30, K_ITEM)
        assertTrue(g.reserveAll(a, k, key(31, K_BUILD)))
        g.die(a, "test")
        assertTrue("another pawn can take what the dead pawn held", g.reserve(b, k))
        assertTrue(g.reserve(b, key(31, K_BUILD)))
    }

    @Test fun aPawnThatLeavesTheColonyFreesItsClaimsOnPruning() {
        val g = newGame(306)
        val a = g.colonists[0]; val b = g.colonists[1]
        val k = key(32, K_ITEM)
        assertTrue(g.reserve(a, k))
        g.pawns.remove(a)
        assertTrue(g.reserve(b, k))
        g.pruneReservations()
        assertEquals(setOf(b.id), g.reservations[k])
    }

    @Test fun aListClearedWithoutAReleaseIsPrunedOnTheSlowTick() {
        val g = newGame(307)
        val a = g.colonists[0]; val b = g.colonists[1]
        val k = key(33, K_ITEM)
        assertTrue(g.reserve(a, k))
        a.reserved.clear()                       // a path that forgot to release
        assertTrue("a stale claim does not block", g.isFree(b, k))
        g.run(250)                               // one slow tick
        assertNull("the stale entry is gone", g.reservations[k])
    }

    @Test fun aDestroyedBlueprintFreesItsBuildKeyForAnotherPawn() {
        val g = newGame(308)
        g.map.drop(ItemType.WOOD, 40, g.homeX - 3, g.homeY + 4)
        val x = g.homeX - 1; val y = g.homeY + 4
        assertTrue(g.placeBlueprint(BuildDef.WOOD_WALL, x, y))
        val builder = g.colonists[0]
        for (p in g.colonists) for (w in WorkType.entries) p.priority[w.ordinal] = if (p === builder && w == WorkType.CONSTRUCT) 1 else 0
        var guard = 0
        while (builder.job?.type != JobType.BUILD && guard++ < 900) g.run(1)
        assertEquals(JobType.BUILD, builder.job?.type)
        val cell = g.map.idx(x, y)
        g.map.removeBuilding(g.map.building[cell]!!)
        g.run(5)
        assertTrue("the builder let go", builder.reserved.none { kindOf(it) == K_BUILD })
        assertTrue(g.reserve(g.colonists[1], key(cell, K_BUILD)))
    }

    @Test fun twoPawnsNeverGetTheSameHospitalBed() {
        val g = newGame(309)
        val bedX = g.homeX - 4; val bedY = g.homeY + 6
        g.map.setBuilding(Building(BuildDef.HOSPITAL_BED, bedX, bedY, true))
        val first = g.findBedFor(g.colonists[0], forceMedical = true)
        val second = g.findBedFor(g.colonists[1], forceMedical = true)
        assertTrue(first >= 0)
        assertTrue("the second patient is not given the occupied bed", second != first)
    }

    @Test fun claimsDoNotCrossIntoAnotherMap() {
        val home = newGame(310)
        val battle = newGame(311)
        val k = key(40, K_ITEM)
        assertTrue(home.reserve(home.colonists[0], k))
        assertTrue("the battle map is a separate claim table", battle.isFree(battle.colonists[0], k))
    }

    @Test fun aSavedAndLoadedGameStartsWithNoClaimsAndAFreedHospitalBed() {
        val g = newGame(312)
        val bx = g.homeX - 4; val by = g.homeY + 6
        g.map.setBuilding(Building(BuildDef.HOSPITAL_BED, bx, by, true))
        val patient = g.colonists[0]
        val bed = g.findBedFor(patient, forceMedical = true)
        assertEquals(patient.id, g.map.building[bed]!!.occupant)
        assertTrue(g.reserve(patient, key(40, K_ITEM)))

        val l = SaveGame.read(SaveGame.write(g))
        // The claim is part of the saved job state, so it comes back with its owner and no one else holds it.
        val loadedPatient = l.pawns.first { it.id == patient.id }
        assertEquals(setOf(key(40, K_ITEM)), loadedPatient.reserved.toSet())
        assertEquals(setOf(patient.id), l.reservations[key(40, K_ITEM)])
        // A patient with no job does not hold a hospital bed after a load.
        assertEquals("the bed is free after a load", -1, l.map.building[bed]!!.occupant)
    }
}
