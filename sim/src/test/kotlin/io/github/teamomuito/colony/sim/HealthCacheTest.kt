package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The capacities and pain of a pawn are a cache of its injuries, hediffs and blood loss (Pawn.healthDirty). A read must give
 * what those inputs give now, and a game loaded from a save must read the same values as the live game (SaveRestoreTest).
 */
class HealthCacheTest {
    private fun colony(seed: Long): Game {
        val g = Game(seed)
        g.startNewColony(Scenario.QUIET_ENCLAVE)
        g.paintZone(g.homeX + 6, g.homeY - 6, g.homeX + 8, g.homeY - 4, ZoneKind.STOCKPILE)
        g.map.drop(ItemType.STEEL, 40, g.homeX - 6, g.homeY + 2)
        g.placeBlueprint(BuildDef.WOOD_WALL, g.homeX - 1, g.homeY + 4)
        return g
    }

    private fun Game.run(ticks: Int) { repeat(ticks) { step() } }

    /** The values a read gives, against a fresh computation from the same inputs. */
    private fun assertCacheIsFresh(p: Pawn, where: String) {
        val read = p.cap.toList()
        val readPain = p.pain
        p.healthDirty = true
        refreshHealthCache(p)
        assertEquals("capacities of ${p.name} $where", p.cap.toList(), read)
        assertEquals("pain of ${p.name} $where", p.pain, readPain, 0f)
    }

    @Test fun aReadIsFreshAndTheLoadedGameReadsTheSameAsTheLiveOne() {
        val g = colony(601)
        for (round in 0 until 12) {
            // Every tick is checked, since healing changes the inputs between health ticks.
            repeat(250) { g.step(); for (p in g.pawns) if (p.alive) assertCacheIsFresh(p, "at tick ${g.tick}") }
            // Damage, a hediff and toxic fallout, so the inputs change between the checks.
            if (round % 3 == 1) g.colonists.firstOrNull()?.let { g.explode(it.x, it.y, 1.5f, 3f) }
            if (round % 4 == 2) g.colonists.firstOrNull()?.let { g.addHediff(it, HediffKind.COLD_CORE, 0.1f) }
            if (round == 5) g.toxicFalloutUntil = g.tick + 400
            for (p in g.pawns) if (p.alive) assertCacheIsFresh(p, "at tick ${g.tick}")
            val loaded = SaveGame.read(SaveGame.write(g))
            for (p in g.pawns) if (p.alive) {
                val back = loaded.pawns.first { it.id == p.id }
                assertEquals("capacities of ${p.name} at tick ${g.tick}", p.cap.toList(), back.cap.toList())
                assertEquals("pain of ${p.name} at tick ${g.tick}", p.pain, back.pain, 0f)
            }
        }
    }

    @Test fun aBruiseThatHealsWithoutBleedingIsReadFreshAtEveryTick() {
        // A bruise does not bleed, so no blood-loss change marks the pawn: only the healing of its severity changes the inputs.
        val g = colony(603)
        g.run(100)
        val p = g.colonists.first()
        val leg = p.race.body.indexOfFirst { it.tag == PartTag.LEG }
        g.applyWound(p, leg, DamageKind.BRUISE, 12f, null)
        repeat(3000) { g.step(); if (p.alive) assertCacheIsFresh(p, "at tick ${g.tick}") }
    }

    @Test fun aHitIsReadAtOnceWithoutWaitingForAHealthTick() {
        val g = colony(602)
        g.run(100)
        val victim = g.colonists.first()
        val hpBefore = victim.healthFraction()
        // No health tick runs between the blast and the read below: the read must already reflect the wound.
        g.explode(victim.x, victim.y, 2f, 6f)
        assertTrue("the blast hurt the victim", victim.healthFraction() < hpBefore)
        val read = victim.cap.toList()
        victim.healthDirty = true
        refreshHealthCache(victim)
        assertEquals("a fresh computation agrees with the read", victim.cap.toList(), read)
    }
}
