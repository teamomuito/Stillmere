package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun openGame(seed: Long): Game {
    val g = Game(seed)
    g.startNewColony(Scenario.LOST_TRIBE)
    g.mentalBreaksEnabled = false
    g.pawns.removeAll { it.isAnimal && it.faction == Faction.WILD }
    val never = Long.MAX_VALUE
    g.nextRaid = never; g.nextMisc = never; g.nextWanderer = never; g.nextTrader = never; g.nextPod = never; g.nextTempEvent = never
    // Clear open ground around the colony so shots have a clear line.
    for (y in g.homeY - 4..g.homeY + 4) for (x in g.homeX - 4..g.homeX + 18) if (g.map.inB(x, y)) {
        val i = g.map.idx(x, y)
        g.map.terrain[i] = Terrain.SOIL; g.map.plant[i] = null; g.map.building[i] = null; g.map.items.remove(i)
    }
    g.map.roomDirty = true
    return g
}

class SuppressionTest {

    @Test fun aVolleyThatHitsNothingSuppressesTheTarget() {
        val g = openGame(2301)
        val shooter = g.colonists.first()
        shooter.weaponItem = ItemType.W_REVOLVER
        shooter.x = g.homeX; shooter.y = g.homeY
        val target = g.newHuman(g.homeX + 14, g.homeY, Faction.ENEMY)
        target.x = g.homeX + 14; target.y = g.homeY
        g.pawns.add(target)
        var suppressed = false
        repeat(300) {
            shooter.attackCd = 0
            g.fire(shooter, target)
            if (target.suppressedUntil > g.tick) suppressed = true
        }
        assertTrue("some volley misses and pins the target", suppressed)
    }

    @Test fun aSuppressedShooterIsLessAccurate() {
        val g = openGame(2302)
        val shooter = g.colonists.first()
        val target = g.newHuman(g.homeX + 5, g.homeY, Faction.ENEMY)
        target.x = g.homeX + 5; target.y = g.homeY
        g.pawns.add(target)
        val d = g.distance(shooter.x, shooter.y, target.x, target.y)
        val before = g.hitChance(shooter, target, Weapon.RIFLE, d, 0f)
        shooter.suppressedUntil = g.tick + TICKS_PER_HOUR
        val after = g.hitChance(shooter, target, Weapon.RIFLE, d, 0f)
        assertTrue("suppression lowers the chance to hit", after < before)
    }

    @Test fun suppressionEndsWithItsTime() {
        val g = openGame(2303)
        val shooter = g.colonists.first()
        val target = g.newHuman(g.homeX + 5, g.homeY, Faction.ENEMY)
        target.x = g.homeX + 5; target.y = g.homeY
        g.pawns.add(target)
        val d = g.distance(shooter.x, shooter.y, target.x, target.y)
        val calm = g.hitChance(shooter, target, Weapon.RIFLE, d, 0f)
        shooter.suppressedUntil = g.tick - 1
        assertEquals(calm, g.hitChance(shooter, target, Weapon.RIFLE, d, 0f), 0.0001f)
    }

    @Test fun suppressionSurvivesASave() {
        val g = openGame(2304)
        val p = g.colonists.first()
        p.suppressedUntil = g.tick + 500
        val loaded = SaveGame.read(SaveGame.write(g))
        assertEquals(p.suppressedUntil, loaded.pawns.first { it.id == p.id }.suppressedUntil)
    }

    @Test fun anOlderSaveLoadsWithNoSuppression() {
        val g = openGame(2305)
        val p = g.colonists.first()
        p.suppressedUntil = g.tick + 500
        val loaded = SaveGame.read(SaveGame.write(g, version = 22))
        assertEquals(0L, loaded.pawns.first { it.id == p.id }.suppressedUntil)
    }
}
