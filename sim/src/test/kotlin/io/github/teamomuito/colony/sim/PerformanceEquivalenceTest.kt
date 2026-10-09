package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks that the rewritten searches (see PerfBenchTest for the measurements) give exactly the answers the original
 * formulations gave, and that saves are reproducible.
 */
class PerformanceEquivalenceTest {

    private fun colony(seed: Long): Game {
        val g = Game(seed)
        g.startNewColony(Scenario.LOST_TRIBE)
        g.mentalBreaksEnabled = false
        g.pawns.removeAll { it.isAnimal && it.faction == Faction.WILD }
        val never = Long.MAX_VALUE
        g.nextRaid = never; g.nextMisc = never; g.nextWanderer = never; g.nextTrader = never; g.nextPod = never; g.nextTempEvent = never
        return g
    }

    @Test fun haulCandidatesAreTheFirstTwentySixOfAStableSortByDistance() {
        val g = colony(1201)
        val r = java.util.Random(7)
        val types = ItemType.entries.filter { it.stack > 1 }
        repeat(400) {
            g.map.drop(types[r.nextInt(types.size)], 1 + r.nextInt(9), r.nextInt(g.map.w), r.nextInt(g.map.h))
        }
        // A few corpses and forbidden stacks, which the haul search must skip.
        for ((n, s) in g.map.items.values.take(20).withIndex()) { if (n % 2 == 0) s.corpseOf = "x" else s.forbidden = true }
        for (trial in 0 until 30) {
            val p = g.colonists[trial % g.colonists.size]
            p.x = r.nextInt(g.map.w); p.y = r.nextInt(g.map.h)
            val reference = g.map.items.values.filter { it.corpseOf == null && !it.forbidden }
                .sortedBy { kotlin.math.abs(it.x - p.x) + kotlin.math.abs(it.y - p.y) - (if (it.type.cat == ItemCat.FOOD_MEAL) 15 else 0) }
                .take(26)
            val fast = g.nearestHaulCandidates(p)
            assertEquals("trial $trial", reference.map { System.identityHashCode(it) }, fast.map { System.identityHashCode(it) })
            assertTrue(fast.zip(fast.drop(1)).all { (a, b) ->
                val ka = kotlin.math.abs(a.x - p.x) + kotlin.math.abs(a.y - p.y) - (if (a.type.cat == ItemCat.FOOD_MEAL) 15 else 0)
                val kb = kotlin.math.abs(b.x - p.x) + kotlin.math.abs(b.y - p.y) - (if (b.type.cat == ItemCat.FOOD_MEAL) 15 else 0)
                ka <= kb
            })
        }
    }

    @Test fun nearestPawnAndStackMatchFilterThenMin() {
        val g = colony(1202)
        val r = java.util.Random(11)
        for (trial in 0 until 50) {
            val p = g.colonists[trial % g.colonists.size]
            val limit = 3 + r.nextInt(25)
            val refPawn = g.pawns.filter { it !== p && it.alive && g.distance(p.x, p.y, it.x, it.y) < limit }
                .minByOrNull { g.distance(p.x, p.y, it.x, it.y) }
            assertEquals("pawn $trial", refPawn, g.nearestPawn(p) { it !== p && it.alive && g.distance(p.x, p.y, it.x, it.y) < limit })
            val refStack = g.map.items.values.filter { it.corpseOf == null && (it.x + it.y) % 3 == trial % 3 }
                .minByOrNull { kotlin.math.abs(it.x - p.x) + kotlin.math.abs(it.y - p.y) }
            assertEquals("stack $trial", refStack, g.nearestStack(p) { it.corpseOf == null && (it.x + it.y) % 3 == trial % 3 })
        }
    }

    @Test fun pruningExpiredMarksChangesNoAnswer() {
        val g = colony(1203)
        val p = g.colonists[0]
        for (k in 0 until 200) g.markUnreachable(p, k * 7)   // each mark lasts tk(900) ticks
        g.tick += tk(500)
        val before = (0 until 1400).map { g.isBad(p, it) }
        assertTrue("some marks are still active", before.any { it })
        g.pruneUnreachable()
        assertEquals("pruning active marks changes nothing", before, (0 until 1400).map { g.isBad(p, it) })
        g.tick += tk(600)  // every mark has now run out
        val expired = (0 until 1400).map { g.isBad(p, it) }
        assertTrue("nothing is bad once the marks have run out", expired.none { it })
        g.pruneUnreachable()
        assertEquals("pruning expired marks changes nothing", expired, (0 until 1400).map { g.isBad(p, it) })
        assertTrue("expired marks are forgotten", g.unreachable.isEmpty())
    }

    @Test fun activeMarksSurvivePruning() {
        val g = colony(1204)
        val p = g.colonists[0]
        g.markUnreachable(p, 42)
        g.pruneUnreachable()
        assertTrue(g.isBad(p, 42))
        g.tick += tk(1000)
        assertFalse(g.isBad(p, 42))
    }

    @Test fun theSameSeedGivesTheSameSaveBytes() {
        fun save(seed: Long): ByteArray {
            val g = colony(seed)
            g.research(Research.SMITHING)
            repeat(600) { g.step() }
            return SaveGame.write(g)
        }
        assertTrue("saves of one seed are byte-identical", save(1205).contentEquals(save(1205)))
    }

    private fun Game.research(r: Research) { researchDone.add(r); researchProgress[r] = 0.5f }
}
