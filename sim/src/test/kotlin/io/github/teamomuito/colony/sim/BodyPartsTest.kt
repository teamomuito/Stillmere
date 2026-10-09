package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun bodyGame(seed: Long): Game {
    val g = Game(seed)
    g.startNewColony(Scenario.LOST_TRIBE)
    g.mentalBreaksEnabled = false
    g.pawns.removeAll { it.isAnimal && it.faction == Faction.WILD }
    return g
}

private fun Pawn.partIndexes(tag: PartTag): List<Int> = race.body.indices.filter { race.body[it].tag == tag }

class BodyPartsTest {

    @Test fun aHumanHasFingersAndToes() {
        assertEquals(2, Bodies.HUMAN.count { it.tag == PartTag.FINGER })
        assertEquals(2, Bodies.HUMAN.count { it.tag == PartTag.TOE })
    }

    @Test fun losingFingersWeakensGrip() {
        val g = bodyGame(2501)
        val p = g.colonists.first()
        g.recomputeHealth(p)
        val before = p.cap[Cap.MANIPULATION.ordinal]
        for (i in p.partIndexes(PartTag.FINGER)) g.woundPart(p, i, DamageKind.CUT, 1000f)
        g.recomputeHealth(p)
        val after = p.cap[Cap.MANIPULATION.ordinal]
        assertTrue("a hand without fingers grips worse", after < before)
        assertTrue("but it still holds on", after > 0f)
    }

    @Test fun losingToesSlowsWalking() {
        val g = bodyGame(2502)
        val p = g.colonists.first()
        g.recomputeHealth(p)
        val before = p.cap[Cap.MOVING.ordinal]
        for (i in p.partIndexes(PartTag.TOE)) g.woundPart(p, i, DamageKind.CUT, 1000f)
        g.recomputeHealth(p)
        assertTrue(p.cap[Cap.MOVING.ordinal] < before)
    }
}
