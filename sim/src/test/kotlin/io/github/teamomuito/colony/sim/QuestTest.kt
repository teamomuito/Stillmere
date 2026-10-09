package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** A calm colony on a world map where only the quest code under test happens. */
private fun questGame(seed: Long): Game {
    val g = Game(seed)
    g.startNewColony(Scenario.LOST_TRIBE)
    g.mentalBreaksEnabled = false
    g.pawns.removeAll { it.isAnimal && it.faction == Faction.WILD }
    val never = Long.MAX_VALUE
    g.nextRaid = never; g.nextMisc = never; g.nextWanderer = never; g.nextTrader = never; g.nextPod = never; g.nextTempEvent = never
    return g
}

private fun Game.patron(): WorldFaction = world.factions.first { !it.permanentEnemy }.also { world.goodwill[it.id] = 30 }
private fun Game.foe(): WorldFaction = world.factions.first { it.permanentEnemy }

/** A camp on a free tile of the world, with an open offer from the patron. */
private fun Game.campQuest(reward: Int = 300, strength: Float = 20f): Pair<Quest, Site> {
    val tile = (0 until world.w * world.h).first { t ->
        t != world.homeTile && world.passable(t) && !world.river[t] && world.settlementAt(t) == null && world.siteAt(t) == null
    }
    val s = Site(world.nextSiteId++, tile, 0, foe().id, reward, tick + CAMP_QUEST_DAYS * TICKS_PER_DAY.toLong(), strength, "Raider camp")
    world.sites.add(s)
    return offerCampQuest(patron(), s) to s
}

/** A caravan of colonists who walk the world as one group. */
private fun Game.caravan(people: Int): Caravan {
    val c = Caravan(nextCaravanId++, "Test caravan", world.homeTile)
    for (p in colonists.take(people)) { pawns.remove(p); c.members.add(p); p.weaponItem = ItemType.W_RIFLE; p.drafted = false }
    caravans.add(c)
    return c
}

/** Fights the camp as the caravan would on arrival, and wins it. */
private fun Game.winFightAt(c: Caravan, site: Site) {
    c.tile = site.tile
    clearSite(c, site)
    val bg = beginBattle()
    bg.battle!!.outcome = BattleOutcome.VICTORY
    resolveBattle(bg)
}

class QuestTest {

    // ----------------------------------------------------------------------------------- offers and acceptance

    @Test fun anOfferIsOpenUntilAcceptedOrDeclined() {
        val g = questGame(801)
        val (q, _) = g.campQuest()
        assertEquals(QuestState.OFFERED, q.state)
        assertNull(g.acceptQuest(q.id))
        assertEquals(QuestState.ACCEPTED, q.state)
        assertNotNull("an accepted offer cannot be accepted again", g.acceptQuest(q.id))
        assertNotNull("nor declined", g.declineQuest(q.id))
    }

    @Test fun decliningRemovesTheCamp() {
        val g = questGame(802)
        val (q, s) = g.campQuest()
        assertNull(g.declineQuest(q.id))
        assertEquals(QuestState.DECLINED, q.state)
        assertNull("the camp is gone from the map", g.world.sites.firstOrNull { it.id == s.id })
    }

    // ----------------------------------------------------------------------------------- completion and rewards

    @Test fun clearingAnAcceptedCampPaysTheBountyAndRaisesGoodwill() {
        val g = questGame(803)
        val (q, s) = g.campQuest(reward = 300)
        g.acceptQuest(q.id)
        val c = g.caravan(2)
        val silverBefore = g.caravanSilver(c)
        val goodwill = g.world.goodwill[q.factionId]
        g.winFightAt(c, s)
        assertEquals(QuestState.COMPLETED, q.state)
        val paid = g.caravanSilver(c) - silverBefore
        assertTrue("the bounty is paid (plus battlefield silver): $paid", paid in 300..390)
        assertTrue("the patron is pleased", g.world.goodwill[q.factionId] > goodwill)
        assertNull(g.world.sites.firstOrNull { it.id == s.id })
    }

    @Test fun clearingAnUnacceptedCampPaysNothing() {
        val g = questGame(804)
        val (q, s) = g.campQuest(reward = 300)
        val c = g.caravan(2)
        val silverBefore = g.caravanSilver(c)
        val goodwill = g.world.goodwill[q.factionId]
        g.winFightAt(c, s)
        assertEquals(QuestState.EXPIRED, q.state)
        assertTrue("no bounty, only battlefield silver", g.caravanSilver(c) - silverBefore < 300)
        // Rivals of the camp's owner still approve of clearing it; that is not a bounty.
        assertTrue("no bounty goodwill", g.world.goodwill[q.factionId] - goodwill <= 6)
    }

    // ----------------------------------------------------------------------------------- expiry

    @Test fun anUnansweredOfferExpiresWithoutCost() {
        val g = questGame(805)
        val (q, s) = g.campQuest()
        val goodwill = g.world.goodwill[q.factionId]
        g.tick = q.deadline + 1
        g.questsDaily()
        assertEquals(QuestState.EXPIRED, q.state)
        assertEquals(goodwill, g.world.goodwill[q.factionId])
        assertNull(g.world.sites.firstOrNull { it.id == s.id })
    }

    @Test fun anAcceptedCampThatRunsOutOfTimeCostsGoodwill() {
        val g = questGame(806)
        val (q, s) = g.campQuest()
        g.acceptQuest(q.id)
        val goodwill = g.world.goodwill[q.factionId]
        g.tick = q.deadline + 1
        g.questsDaily()
        assertEquals(QuestState.FAILED, q.state)
        assertEquals(goodwill + CAMP_FAILED_GOODWILL, g.world.goodwill[q.factionId])
        assertNull(g.world.sites.firstOrNull { it.id == s.id })
    }

    @Test fun aQuestIsNotExpiredBeforeItsDeadline() {
        val g = questGame(807)
        val (q, _) = g.campQuest()
        g.tick = q.deadline
        g.questsDaily()
        assertEquals(QuestState.OFFERED, q.state)
    }

    @Test fun aResolvedQuestCannotBeResolvedAgain() {
        val g = questGame(808)
        val (q, s) = g.campQuest(reward = 300)
        g.acceptQuest(q.id)
        val c = g.caravan(2)
        g.winFightAt(c, s)
        val silver = g.caravanSilver(c)
        g.tick = q.deadline + 1
        g.questsDaily()
        assertEquals("a completed quest stays completed", QuestState.COMPLETED, q.state)
        assertEquals(silver, g.caravanSilver(c))
    }

    // ----------------------------------------------------------------------------------- rescue

    /** A caravan of one is caught by a camp, cannot be carried, and is left behind on the retreat. */
    private fun Game.leftBehindAt(): Pawn {
        val c = caravan(1)
        val victim = c.members.first()
        val (_, s) = campQuest()
        clearSite(c, s)
        val bg = beginBattle()
        val member = bg.battleMembers.first { it.id == victim.id }
        member.downed = true
        bg.requestRetreat()
        bg.battleTick()
        assertEquals(BattleOutcome.RETREAT, bg.battle!!.outcome)
        resolveBattle(bg)
        return member
    }

    @Test fun aColonistLeftBehindInARetreatBecomesACaptive() {
        val g = questGame(809)
        val captive = g.leftBehindAt()
        assertEquals(1, g.world.captives.size)
        assertSame(captive, g.world.captives.first().pawn)
        assertFalse("the captive is not in the colony", g.pawns.contains(captive))
        val rescue = g.world.quests.first { it.kind == QuestKind.RESCUE_CAPTIVES }
        assertEquals(QuestState.ACCEPTED, rescue.state)
        assertTrue("a camp was set up to hold them", g.world.sites.any { it.id == rescue.siteId })
    }

    @Test fun rescuingCaptivesReturnsThemWithTheirIdentity() {
        val g = questGame(810)
        val captive = g.leftBehindAt()
        val rescue = g.world.quests.first { it.kind == QuestKind.RESCUE_CAPTIVES }
        val camp = g.world.sites.first { it.id == rescue.siteId }
        val name = captive.name
        val id = captive.id
        val rescuer = g.caravan(1)
        g.winFightAt(rescuer, camp)
        assertEquals(QuestState.COMPLETED, rescue.state)
        assertTrue("no captives are left", g.world.captives.isEmpty())
        val back = rescuer.members.first { it.id == id }
        assertSame("the same pawn comes home", captive, back)
        assertEquals(name, back.name)
        assertNull("the camp is gone", g.world.sites.firstOrNull { it.id == camp.id })
    }

    @Test fun captivesAreLostWhenTheirRescueRunsOutOfTime() {
        val g = questGame(811)
        val captive = g.leftBehindAt()
        val rescue = g.world.quests.first { it.kind == QuestKind.RESCUE_CAPTIVES }
        g.tick = rescue.deadline + 1
        g.questsDaily()
        assertEquals(QuestState.FAILED, rescue.state)
        assertTrue(g.world.captives.isEmpty())
        assertTrue("the loss is written down", g.graveyard.any { it.startsWith(captive.name) })
    }

    @Test fun animalsLeftBehindWanderOffInsteadOfBeingCaptured() {
        val g = questGame(812)
        val c = g.caravan(0)     // nobody is free to carry a downed animal
        val horse = g.newAnimal(Race.MUFFALO, g.homeX, g.homeY, Faction.PLAYER)
        g.pawns.remove(horse)
        c.members.add(horse)
        val (_, s) = g.campQuest()
        g.clearSite(c, s)
        val bg = g.beginBattle()
        val member = bg.battleMembers.first { it.id == horse.id }
        member.downed = true          // nobody can carry it, so the retreat leaves it on the field
        bg.requestRetreat()
        var ticks = 0
        while (bg.battle!!.outcome == null && ticks++ < 60_000) bg.step()
        assertEquals(BattleOutcome.RETREAT, bg.battle!!.outcome)
        g.resolveBattle(bg)
        assertTrue("no one is held captive", g.world.captives.isEmpty())
        assertFalse("the animal is not in the caravan any more", c.members.contains(horse))
    }

    // ----------------------------------------------------------------------------------- map transitions and saves

    @Test fun theTemporaryMapIsDroppedWhenTheFightIsResolved() {
        val g = questGame(813)
        val (q, s) = g.campQuest()
        g.acceptQuest(q.id)
        val c = g.caravan(2)
        val members = c.members.toList()
        g.winFightAt(c, s)
        assertNull("no battle is pending", g.pendingBattle)
        assertFalse("the caravan is no longer in battle", c.inBattle)
        assertEquals("the same people come back", members.toSet(), c.members.toSet())
    }

    @Test fun aSaveTakenDuringAnActiveQuestAndFightLoadsCompletely() {
        val g = questGame(814)
        val (q, s) = g.campQuest(reward = 250)
        g.acceptQuest(q.id)
        val c = g.caravan(2)
        val captive = g.leftBehindAt()
        g.clearSite(c, s)   // a fight is pending when the game is saved
        val loaded = SaveGame.read(SaveGame.write(g))

        val lq = loaded.world.quests.first { it.id == q.id }
        assertEquals(QuestState.ACCEPTED, lq.state)
        assertEquals(q.deadline, lq.deadline)
        assertNotNull("the fight is still pending", loaded.pendingBattle)
        assertEquals(s.id, loaded.pendingBattle!!.site)
        val lc = loaded.world.captives.first()
        assertEquals(captive.id, lc.pawn.id)
        assertEquals(captive.name, lc.pawn.name)
        assertEquals(captive.injuries.size, lc.pawn.injuries.size)
    }

    @Test fun aLoadedQuestCanStillBeCompleted() {
        val g = questGame(815)
        val (q, s) = g.campQuest(reward = 250)
        g.acceptQuest(q.id)
        val loaded = SaveGame.read(SaveGame.write(g))
        val c = loaded.caravan(2)
        val silver = loaded.caravanSilver(c)
        loaded.winFightAt(c, loaded.world.sites.first { it.id == s.id })
        assertEquals(QuestState.COMPLETED, loaded.world.quests.first { it.id == q.id }.state)
        val paid = loaded.caravanSilver(c) - silver
        assertTrue("the bounty is paid after the load: $paid", paid in 250..340)
    }

    @Test fun oldQuestsAreForgottenSoSavesStaySmall() {
        val g = questGame(816)
        repeat(30) {
            val (q, _) = g.campQuest()
            g.declineQuest(q.id)
        }
        g.tick += 25L * TICKS_PER_DAY
        g.questsDaily()
        assertTrue("finished quests older than 20 days are dropped", g.world.quests.none { !it.open })
    }

    @Test fun offersKeepTheirIdsAcrossASave() {
        val g = questGame(817)
        val (q1, _) = g.campQuest()
        val loaded = SaveGame.read(SaveGame.write(g))
        val (q2, _) = loaded.campQuest()
        assertTrue("new offers never reuse an id", q2.id != q1.id)
    }
}
