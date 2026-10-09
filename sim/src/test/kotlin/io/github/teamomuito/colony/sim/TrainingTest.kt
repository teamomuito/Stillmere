package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun trainingGame(seed: Long): Game {
    val g = Game(seed)
    g.startNewColony(Scenario.LOST_TRIBE)
    g.mentalBreaksEnabled = false
    g.pawns.removeAll { it.isAnimal && it.faction == Faction.WILD }
    val never = Long.MAX_VALUE
    g.nextRaid = never; g.nextMisc = never; g.nextWanderer = never; g.nextTrader = never; g.nextPod = never; g.nextTempEvent = never
    return g
}

private fun Game.tameHare(x: Int, y: Int): Pawn {
    val a = newAnimal(Race.HARE, x, y, Faction.PLAYER)
    a.tame = true; a.master = colonists.first().id; a.food = 1f
    pawns.add(a)
    return a
}

class TrainingTest {

    @Test fun aHandlerTeachesATamedAnimalATrick() {
        val g = trainingGame(2401)
        val handler = g.colonists.first()
        val a = g.tameHare(handler.x, handler.y)
        handler.job = Job(JobType.TRAIN, a.x, a.y).also { it.targetPawn = a.id }
        repeat(6000) { g.step() }
        assertTrue("the animal learned a trick", a.trained >= 1)
    }

    @Test fun trainingStopsAtTheLimit() {
        val g = trainingGame(2402)
        val handler = g.colonists.first()
        val a = g.tameHare(handler.x, handler.y)
        a.trained = MAX_TRICKS
        handler.job = Job(JobType.TRAIN, a.x, a.y).also { it.targetPawn = a.id }
        repeat(600) { g.step() }
        assertEquals(MAX_TRICKS, a.trained)
        assertNotEquals(JobType.TRAIN, handler.job?.type)
    }

    @Test fun trainedAnimalsHitHarder() {
        val g = trainingGame(2403)
        val a = g.tameHare(0, 0)
        val before = a.weaponDamageMult()
        a.trained = MAX_TRICKS
        assertTrue(a.weaponDamageMult() > before)
    }

    @Test fun tricksSurviveASaveAndOlderSavesLoadWithNone() {
        val g = trainingGame(2404)
        val a = g.tameHare(0, 0)
        a.trained = 1
        val now = SaveGame.read(SaveGame.write(g)).pawns.first { it.id == a.id }.trained
        assertEquals(1, now)
        val then = SaveGame.read(SaveGame.write(g, version = 23)).pawns.first { it.id == a.id }.trained
        assertEquals(0, then)
    }
}
