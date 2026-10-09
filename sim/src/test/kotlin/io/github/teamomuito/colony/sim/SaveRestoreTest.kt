package io.github.teamomuito.colony.sim

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun game(seed: Long, scenario: Scenario = Scenario.LOST_TRIBE): Game {
    val g = Game(seed)
    g.startNewColony(scenario)
    return g
}

private fun Game.run(ticks: Int) { repeat(ticks) { step() } }

/** A colony with real work: a stockpile, materials to haul, and walls to build. */
private fun workingColony(seed: Long): Game {
    val g = game(seed)
    g.paintZone(g.homeX + 6, g.homeY - 6, g.homeX + 8, g.homeY - 4, ZoneKind.STOCKPILE)
    g.map.drop(ItemType.STEEL, 40, g.homeX - 6, g.homeY + 2)
    g.map.drop(ItemType.WOOD, 40, g.homeX - 3, g.homeY + 4)
    g.placeBlueprint(BuildDef.WOOD_WALL, g.homeX - 1, g.homeY + 4)
    g.placeBlueprint(BuildDef.WOOD_WALL, g.homeX - 1, g.homeY + 6)
    return g
}

private fun Game.hasWorkInProgress() = pawns.any { it.alive && it.job != null && it.reserved.isNotEmpty() }

/** Every claim a pawn lists is in the claim table, held by that pawn, and no item is held by two pawns. */
private fun assertClaimsConsistent(g: Game) {
    for (p in g.pawns) for (k in p.reserved) {
        assertTrue("pawn ${p.name} lists a claim the table does not have", g.reservations[k]?.contains(p.id) == true)
    }
    for ((k, ids) in g.reservations) for (id in ids) {
        val holder = g.pawns.firstOrNull { it.id == id }
        assertTrue("claim $k is held by a missing pawn", holder != null && holder.reserved.contains(k))
    }
    val items = g.pawns.flatMap { it.reserved }.filter { kindOf(it) == K_ITEM }.groupingBy { it }.eachCount()
    assertTrue("an item is claimed twice", items.values.all { it <= 1 })
}

class SaveRestoreTest {
    @Test fun aLoadedGameContinuesExactlyLikeTheOriginal() {
        val g = workingColony(401)
        repeat(60) { if (!g.hasWorkInProgress()) g.run(50) }
        assertTrue("the fixture has work in progress", g.hasWorkInProgress())
        val loaded = SaveGame.read(SaveGame.write(g))
        g.run(1500)
        loaded.run(1500)
        assertArrayEquals("the same saved state after the same ticks", SaveGame.write(g), SaveGame.write(loaded))
    }

    @Test fun jobsTargetsProgressAndClaimsAreRestored() {
        val g = game(402)
        g.run(3000)
        val loaded = SaveGame.read(SaveGame.write(g))
        for (p in g.pawns.filter { it.alive }) {
            val lp = loaded.pawns.first { it.id == p.id }
            val j = p.job; val lj = lp.job
            assertEquals("${p.name} job", j?.type, lj?.type)
            if (j != null && lj != null) {
                assertEquals(j.tx, lj.tx); assertEquals(j.ty, lj.ty); assertEquals(j.stage, lj.stage)
                assertEquals(j.timer, lj.timer); assertEquals(j.key, lj.key); assertEquals(j.targetPawn, lj.targetPawn)
            }
            assertEquals("${p.name} claims", p.reserved.toSet(), lp.reserved.toSet())
        }
        assertClaimsConsistent(loaded)
        assertEquals(g.priority(), loaded.priority())
    }

    @Test fun invalidReferencesAreDroppedNotRestored() {
        val g = game(403)
        g.run(2000)
        // A colonist with a job that aims at a pawn that no longer exists.
        val broken = g.colonists[0]
        broken.job = Job(JobType.HAUL, 5, 5).also { it.targetPawn = 999_999; it.key = key(5, K_ITEM) }
        // Another colonist, without a job, carries a valid claim and a claim off the map.
        val p = g.colonists[1]
        p.job = null
        val offMap = key(g.map.size + 50, K_ITEM)
        val onMap = key(g.map.idx(g.homeX + 1, g.homeY + 1), K_PLANT)
        p.reserved.add(offMap); p.reserved.add(onMap)
        val loaded = SaveGame.read(SaveGame.write(g))
        assertNull("a job aiming at a pawn that does not exist is ended", loaded.pawns.first { it.id == broken.id }.job)
        val lp = loaded.pawns.first { it.id == p.id }
        assertFalse("a claim off the map is dropped", lp.reserved.contains(offMap))
        assertTrue("a valid claim is kept", lp.reserved.contains(onMap))
        assertClaimsConsistent(loaded)
    }

    @Test fun twoPawnsWhoSavedTheSameItemClaimKeepOnlyTheFirst() {
        val g = game(404)
        g.run(500)
        val shared = key(g.map.idx(g.homeX - 3, g.homeY), K_ITEM)
        val first = g.colonists[0]; val second = g.colonists[1]
        first.reserved.add(shared); second.reserved.add(shared)   // an inconsistent state that could only come from a bug
        second.job = Job(JobType.HAUL, g.homeX, g.homeY).also { it.key = shared }
        val loaded = SaveGame.read(SaveGame.write(g))
        val (winner, loser) = listOf(first, second).sortedBy { it.id }
        assertTrue("the pawn with the lower id keeps the claim", loaded.pawns.first { it.id == winner.id }.reserved.contains(shared))
        val lLoser = loaded.pawns.first { it.id == loser.id }
        assertFalse("the other pawn does not", lLoser.reserved.contains(shared))
        assertNull("and its job, which needed the claim, is ended", lLoser.job)
        assertClaimsConsistent(loaded)
    }

    @Test fun whatAJobIsCarryingComesBackWithIt() {
        val g = game(405)
        g.run(200)
        val p = g.colonists[0]
        val stack = ItemStack(777, ItemType.STEEL, 9, 3, 4).also { it.hp = 0.5f; it.quality = Quality.GOOD }
        p.job = Job(JobType.HAUL, 3, 4).also { it.stack = stack; it.held.add(Triple(ItemType.STEEL, 2, Quality.NORMAL)); it.heldRot = 0.25f }
        val loaded = SaveGame.read(SaveGame.write(g))
        val lj = loaded.pawns.first { it.id == p.id }.job!!
        val ls = lj.stack!!
        assertEquals(777, ls.id); assertEquals(ItemType.STEEL, ls.type); assertEquals(9, ls.count)
        assertEquals(Quality.GOOD, ls.quality); assertEquals(0.5f, ls.hp, 0f)
        assertEquals(listOf(Triple(ItemType.STEEL, 2, Quality.NORMAL)), lj.held)
        assertEquals(0.25f, lj.heldRot, 0f)
    }

    @Test fun theUnreachableMemoryAndPlayTimeSurvive() {
        val g = game(406)
        g.run(100)
        g.unreachable[12_345L] = g.tick + 900
        g.playMs = 4_200_000L
        val loaded = SaveGame.read(SaveGame.write(g))
        assertEquals(g.unreachable[12_345L], loaded.unreachable[12_345L])
        assertEquals(4_200_000L, loaded.playMs)
    }

    @Test fun aLoadedGameKeepsItsClaimTableConsistentWhileItRuns() {
        val g = game(407)
        g.run(2500)
        val loaded = SaveGame.read(SaveGame.write(g))
        repeat(20) { loaded.run(100); assertClaimsConsistent(loaded) }
    }

    @Test fun aGameSavedTwiceInARowStillResumesExactly() {
        val g = game(408)
        g.run(2000)
        val once = SaveGame.read(SaveGame.write(g))
        once.run(400); g.run(400)
        val twice = SaveGame.read(SaveGame.write(once))
        g.run(900); twice.run(900)
        assertArrayEquals(SaveGame.write(g), SaveGame.write(twice))
    }

    // ---------------------------------------------------------------- transient combat effects

    /*
     * Restoration rule for transient effects: a shot in flight or a blast on screen is presentation only. Its damage
     * is applied when the shot is fired or the explosion goes off, so the save already holds the outcome. Shots and
     * blasts are not saved and are not re-applied on load: a loaded game shows no leftover effect, and nothing hits or
     * burns twice. Nothing in the simulation reads them (only the renderer does), which the tests below prove.
     */

    @Test fun aShotInFlightAtSaveTimeIsNotSavedAndDoesNotChangeTheFuture() {
        val g = workingColony(409)
        g.run(300)
        val target = g.colonists[0]
        g.shots.add(Shot(target.x.toFloat(), target.y.toFloat(), target.x.toFloat() + 3f, target.y.toFloat(), g.tick + 20, true, 0))
        val loaded = SaveGame.read(SaveGame.write(g))
        assertTrue("the shot is not carried in the save", loaded.shots.isEmpty())
        assertEquals("nothing is hit a second time on load", target.healthFraction(), loaded.pawns.first { it.id == target.id }.healthFraction(), 0f)
        g.run(200); loaded.run(200)
        assertArrayEquals("the in-flight shot had no effect on the game", SaveGame.write(g), SaveGame.write(loaded))
    }

    @Test fun aBlastOnScreenAtSaveTimeKeepsItsDamageButIsNotReapplied() {
        val g = workingColony(410)
        g.run(300)
        val victim = g.colonists[0]
        val hpBefore = victim.healthFraction()
        g.explode(victim.x, victim.y, 2.5f, 4f)
        val hpAfterBlast = victim.healthFraction()
        assertTrue("the blast hurt the victim", hpAfterBlast < hpBefore)
        val loaded = SaveGame.read(SaveGame.write(g))
        assertTrue("the blast is not carried in the save", loaded.blasts.isEmpty())
        assertEquals("the damage is already in the save, once", hpAfterBlast, loaded.pawns.first { it.id == victim.id }.healthFraction(), 0f)
        g.run(100); loaded.run(100)
        assertArrayEquals(SaveGame.write(g), SaveGame.write(loaded))
    }

    // ---------------------------------------------------------------- hospital beds follow their patients

    @Test fun aHospitalBedIsHeldOnlyByAPatientWhoStillHasAJob() {
        val g = game(411)
        g.map.setBuilding(Building(BuildDef.HOSPITAL_BED, g.homeX - 4, g.homeY + 6, true))
        val patient = g.colonists[0]
        val bed = g.findBedFor(patient, forceMedical = true)
        assertTrue(bed >= 0)
        patient.job = Job(JobType.REST).also { it.stage = 1 }
        val other = g.colonists[1]
        other.job = null
        g.findBedFor(other, forceMedical = true)   // leaves the bed occupied by the patient, so this claims nothing
        val loaded = SaveGame.read(SaveGame.write(g))
        assertEquals("the patient with a job still holds the bed", patient.id, loaded.map.building[bed]!!.occupant)
    }
}

private fun Game.priority(): List<List<Int>> = pawns.map { it.priority.toList() }
