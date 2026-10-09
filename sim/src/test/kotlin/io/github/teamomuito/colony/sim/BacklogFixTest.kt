package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression tests for the audit findings fixed in the backlog cycle. */
class BacklogFixTest {

    private fun calm(seed: Long): Game {
        val g = Game(seed)
        g.startNewColony(Scenario.LOST_TRIBE)
        g.mentalBreaksEnabled = false
        g.pawns.removeAll { it.isAnimal && it.faction == Faction.WILD }
        val never = Long.MAX_VALUE
        g.nextRaid = never; g.nextMisc = never; g.nextWanderer = never; g.nextTrader = never; g.nextPod = never; g.nextTempEvent = never
        return g
    }

    // ----------------------------------------------------------------------------------- caravans

    @Test fun aCaravanOfAnimalsAloneBreaksUpInsteadOfFreezing() {
        val g = calm(1301)
        val c = Caravan(g.nextCaravanId++, "Herd", g.world.homeTile)
        val horse = g.newAnimal(Race.MUFFALO, g.homeX, g.homeY, Faction.PLAYER)
        g.pawns.remove(horse)
        c.members.add(horse)
        g.caravans.add(c)
        g.caravansTick()
        assertFalse("nobody is left to lead the animals", g.caravans.contains(c))
    }

    @Test fun aDownedCaravanMemberGetsBackUpOnceHealed() {
        val g = calm(1302)
        val p = g.colonists[0]
        g.pawns.remove(p)
        val c = Caravan(g.nextCaravanId++, "Walkers", g.world.homeTile)
        c.members.add(p)
        g.caravans.add(c)
        p.downed = true
        p.cap[Cap.CONSCIOUSNESS.ordinal] = 1f
        p.cap[Cap.MOVING.ordinal] = 1f
        p.pain = 0f
        g.caravansTick()
        assertFalse("a healthy person stands up on the road", p.downed)
    }

    // ----------------------------------------------------------------------------------- recruitment

    @Test fun recruitingAPrisonerFreesTheirPrisonBed() {
        val g = calm(1303)
        val o = g.newHuman(g.homeX + 2, g.homeY, Faction.ENEMY)
        g.pawns.add(o)
        o.prisoner = true
        val bed = Building(BuildDef.BED, g.homeX + 4, g.homeY, true).also { it.prisonerBed = true; it.ownerId = o.id }
        g.map.setBuilding(bed)
        g.recruit(o)
        assertEquals("the bed is no longer held for them", -1, bed.ownerId)
        assertEquals(Faction.PLAYER, o.faction)
    }

    @Test fun aRecruitedVisitorDoesNotStillHaveLeavingOrders() {
        val g = calm(1304)
        val o = g.newHuman(g.homeX + 2, g.homeY, Faction.VISITOR)
        o.retreating = true
        o.prisoner = true
        g.recruit(o)
        assertFalse(o.retreating)
    }

    // ----------------------------------------------------------------------------------- needs and breaks

    @Test fun aStarvingPersonIsNotHeldInAMentalBreak() {
        val g = calm(1305)
        val p = g.colonists[0]
        p.breakUntil = g.tick + 5000
        p.food = 0.05f
        g.think(p)
        assertNotEquals("a starving person goes to eat", JobType.BREAK, p.job?.type)
    }

    @Test fun aCatatonicPersonStaysDownWhileTheEpisodeLasts() {
        val g = calm(1306)
        val p = g.colonists[0]
        p.downed = true
        p.breakKind = Break.CATATONIC
        p.breakUntil = g.tick + 5000
        p.cap[Cap.CONSCIOUSNESS.ordinal] = 1f
        p.cap[Cap.MOVING.ordinal] = 1f
        p.pain = 0f
        g.maybeStandUp(p)
        assertTrue("catatonic people do not get up", p.downed)
    }

    // ----------------------------------------------------------------------------------- sleep

    @Test fun aBedThatCannotBeReachedIsNotChosen() {
        val g = calm(1307)
        val p = g.colonists[0]
        val bed = Building(BuildDef.BED, g.homeX + 3, g.homeY + 3, true).also { it.ownerId = p.id }
        g.map.setBuilding(bed)
        p.bedId = g.map.idx(bed.x, bed.y)
        g.markUnreachable(p, key(p.bedId, K_BED))
        assertNotEquals("the claimed bed is skipped while it is out of reach", p.bedId, g.findBedFor(p))
    }

    // ----------------------------------------------------------------------------------- refugees

    @Test fun aRefugeeWaitsForRescueInsteadOfLeavingAtOnce() {
        val g = calm(1308)
        g.refugees()
        val r = g.pawns.last { it.refugee }
        assertTrue("the rescue window is open", r.escapeTick > g.tick)
        g.thinkVisitor(r)
        assertNotEquals("they do not start to leave", JobType.LEAVE, r.job?.type)
    }

    // ----------------------------------------------------------------------------------- floors

    @Test fun aFloorCanBeLaidUnderFurniture() {
        val g = calm(1309)
        val x = g.homeX + 6; val y = g.homeY + 6
        g.map.setBuilding(Building(BuildDef.BED, x, y, true))
        assertTrue("a floor goes under a built bed", g.canBuildAt(BuildDef.WOOD_FLOOR, x, y))
    }

    @Test fun aFloorCannotBeLaidUnderAWall() {
        val g = calm(1310)
        val x = g.homeX + 6; val y = g.homeY + 6
        g.map.setBuilding(Building(BuildDef.WOOD_WALL, x, y, true))
        assertFalse(g.canBuildAt(BuildDef.WOOD_FLOOR, x, y))
    }

    // ----------------------------------------------------------------------------------- sappers and relations

    @Test fun aSapperDigsThroughAWallWhenNoOneIsNear() {
        val g = calm(1313)
        g.pawns.removeAll { it.colonist }
        val wall = Building(BuildDef.WOOD_WALL, g.homeX + 8, g.homeY, true)
        g.map.setBuilding(wall)
        val s = g.newRaider(g.homeX + 9, g.homeY, ItemType.W_CLUB, 1)
        s.raidMode = 1
        g.pawns.add(s)
        repeat(400) { s.attackCd = 0; s.job = null; g.hostileAI(s) }
        assertTrue("the sapper chips at the wall (hp ${wall.hp})", wall.hp < 150f)
    }

    @Test fun factionRelationsChangeOverTime() {
        val g = calm(1314)
        val before = g.world.relation.map { it.toList() }
        repeat(400) { g.factionsDaily() }
        assertNotEquals("some pair of factions changes its relation", before, g.world.relation.map { it.toList() })
    }

    // ----------------------------------------------------------------------------------- raids and storytelling

    @Test fun aRaidDoesNotStartStraightAfterTheLastOne() {
        val g = calm(1311)
        g.raidLastEnded = g.tick
        assertFalse(g.eligibleNow(IncidentRegistry.def(Incident.RAID_ATTACK)))
    }

    @Test fun orrinSpacesIncidentsFurtherApartThanMarlowe() {
        val marlowe = calm(1312).also { it.storyteller = Storyteller.MARLOWE }
        val orrin = calm(1312).also { it.storyteller = Storyteller.ORRIN }
        val m = (0 until 50).map { marlowe.chaosInterval(IncidentChannel.RAID) }.average()
        val o = (0 until 50).map { orrin.chaosInterval(IncidentChannel.RAID) }.average()
        assertTrue("Orrin's average spacing is longer ($o vs $m)", o > m * 1.2)
    }
}
