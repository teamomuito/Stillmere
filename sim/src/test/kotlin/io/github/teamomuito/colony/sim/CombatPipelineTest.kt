package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A calm seeded colony with a clear strip of open ground, so combat tests see only what they place. */
private fun arena(seed: Long): Game {
    val g = Game(seed)
    g.startNewColony(Scenario.LOST_TRIBE)
    g.mentalBreaksEnabled = false
    g.pawns.removeAll { it.isAnimal && it.faction == Faction.WILD }
    g.nextRaid = Long.MAX_VALUE; g.nextMisc = Long.MAX_VALUE; g.nextWanderer = Long.MAX_VALUE; g.nextTrader = Long.MAX_VALUE
    for (y in g.homeY - 3..g.homeY + 3) for (x in g.homeX - 3..g.homeX + 40) {
        val i = g.map.idx(x, y)
        g.map.terrain[i] = Terrain.SOIL; g.map.plant[i] = null; g.map.building[i] = null; g.map.items.remove(i)
    }
    g.map.roomDirty = true
    return g
}

private fun Game.place(p: Pawn, x: Int, y: Int): Pawn {
    p.x = x; p.y = y; p.fromX = x; p.fromY = y; p.moveCd = 0
    if (!pawns.contains(p)) pawns.add(p)
    return p
}

private fun Game.enemy(x: Int, y: Int, seedWeapon: ItemType? = null): Pawn {
    val e = newHuman(x, y, Faction.ENEMY)
    e.weaponItem = seedWeapon
    return place(e, x, y)
}

/** Fires once, ignoring warmup and cooldown, so each call is one attempt. */
private fun Game.shoot(p: Pawn, t: Pawn) {
    p.attackCd = 0
    p.warmup = p.weapon.warmupTicks
    fire(p, t)
}

private fun Game.run(ticks: Int) { repeat(ticks) { step() } }

class CombatPipelineTest {
    @Test fun meleeReachesOnlyAdjacentTargets() {
        val g = arena(601)
        val me = g.place(g.colonists[0], g.homeX, g.homeY)
        me.weaponItem = ItemType.W_KNIFE
        val far = g.enemy(g.homeX + 3, g.homeY)
        repeat(10) { g.shoot(me, far) }
        assertTrue("three tiles away is out of melee reach", far.injuries.isEmpty())
        val near = g.enemy(g.homeX + 1, g.homeY)
        repeat(20) { g.shoot(me, near) }
        assertTrue("an adjacent target is hit", near.injuries.isNotEmpty())
    }

    @Test fun rangedWeaponsDoNotFireBeyondTheirRange() {
        val g = arena(602)
        val me = g.place(g.colonists[0], g.homeX, g.homeY)
        me.weaponItem = ItemType.W_RIFLE
        val far = g.enemy(g.homeX + 33, g.homeY)   // rifle range is 28
        val before = g.shots.size
        g.shoot(me, far)
        assertEquals("no shot is taken", before, g.shots.size)
    }

    @Test fun warmupDelaysTheFirstShotByItsFullLength() {
        val g = arena(603)
        val me = g.place(g.colonists[0], g.homeX, g.homeY)
        me.weaponItem = ItemType.W_RIFLE
        val target = g.enemy(g.homeX + 5, g.homeY)
        me.attackCd = 0; me.warmup = 0
        var calls = 0
        while (g.shots.isEmpty() && calls < 100) { g.fire(me, target); calls++ }
        assertEquals("the shot comes after the rifle's warmup", Weapon.RIFLE.warmupTicks + 1, calls)
    }

    @Test fun cooldownFollowsTheShot() {
        val g = arena(604)
        val me = g.place(g.colonists[0], g.homeX, g.homeY)
        me.weaponItem = ItemType.W_RIFLE
        val target = g.enemy(g.homeX + 5, g.homeY)
        g.shoot(me, target)
        assertTrue("the rifle cools down for its full cooldown", me.attackCd >= Weapon.RIFLE.cooldownTicks)
        val shotsAfterFirst = g.shots.size
        g.fire(me, target)
        assertEquals("no second shot while it cools down", shotsAfterFirst, g.shots.size)
    }

    @Test fun coverOnTheLineOfFireMakesShotsMissMore() {
        fun hitsWith(cover: Boolean): Int {
            val g = arena(605)
            val me = g.place(g.colonists[0], g.homeX, g.homeY)
            me.weaponItem = ItemType.W_RIFLE
            val target = g.enemy(g.homeX + 8, g.homeY)
            // Sandbags beside the target: cover counts only when it stands next to the one being shot at.
            if (cover) g.map.setBuilding(Building(BuildDef.SANDBAGS, g.homeX + 7, g.homeY, true))
            // The target dies partway through, so compare hit rates over the shots actually taken.
            repeat(200) { g.shoot(me, target) }
            return g.shots.count { it.hit } * 1000 / g.shots.size
        }
        val open = hitsWith(cover = false)
        val covered = hitsWith(cover = true)
        assertTrue("open: $open per mille, covered: $covered per mille", open > 300 && covered < open * 0.75)
    }

    @Test fun aWallOnTheLineStopsTheShotEntirely() {
        val g = arena(606)
        val me = g.place(g.colonists[0], g.homeX, g.homeY)
        me.weaponItem = ItemType.W_RIFLE
        val target = g.enemy(g.homeX + 6, g.homeY)
        g.map.setBuilding(Building(BuildDef.WOOD_WALL, g.homeX + 3, g.homeY, true))
        repeat(50) { g.shoot(me, target) }
        assertTrue("nothing reaches the target", target.injuries.isEmpty())
        assertTrue("no shot is recorded as a hit", g.shots.none { it.hit })
    }

    @Test fun thePersonInTheWayTakesTheShot() {
        val g = arena(607)
        val me = g.place(g.colonists[0], g.homeX, g.homeY)
        me.weaponItem = ItemType.W_RIFLE
        val bystander = g.place(g.colonists[1], g.homeX + 3, g.homeY)
        val target = g.enemy(g.homeX + 6, g.homeY)
        // Shoot while the bystander stands. Once they fall, the line is clear and the target may be hit.
        var k = 0
        while (bystander.alive && k < 60) { g.shoot(me, target); k++ }
        assertTrue("the pawn in the way is hit", bystander.injuries.isNotEmpty())
        assertTrue("the target is not hit through a pawn", target.injuries.isEmpty())
    }

    @Test fun aBetterShooterHitsMoreOften() {
        fun hitsAtSkill(level: Int): Int {
            val g = arena(608)
            val me = g.place(g.colonists[0], g.homeX, g.homeY)
            me.weaponItem = ItemType.W_RIFLE
            me.skill[SkillType.SHOOTING.ordinal] = level
            val target = g.enemy(g.homeX + 10, g.homeY)
            repeat(200) { g.shoot(me, target) }
            return g.shots.count { it.hit }
        }
        val novice = hitsAtSkill(0)
        val expert = hitsAtSkill(20)
        assertTrue("novice $novice, expert $expert", expert > novice)
    }

    @Test fun armorReducesDamageAndPenetrationReducesArmor() {
        fun severityOf(vest: Boolean, pen: Float): Float {
            val g = arena(609)
            val t = g.place(g.colonists[0], g.homeX, g.homeY)
            if (vest) t.apparel.add(Worn(ItemType.A_FLAK_VEST, Quality.NORMAL, ItemType.A_FLAK_VEST.apparel!!.hp))
            val torso = t.race.body.indexOfFirst { it.tag == PartTag.TORSO }
            g.dealDamage(t, DamageKind.BULLET, 20f, pen, null, partHint = torso)
            return t.injuries.sumOf { it.severity.toDouble() }.toFloat()
        }
        val bare = severityOf(vest = false, pen = 0f)
        val armored = severityOf(vest = true, pen = 0f)
        val piercing = severityOf(vest = true, pen = 0.5f)
        assertTrue("a flak vest reduces the damage ($bare -> $armored)", armored < bare)
        assertTrue("penetration gets past the vest ($armored -> $piercing)", piercing > armored)
    }

    @Test fun destroyingAVitalPartKillsTheVictim() {
        val g = arena(610)
        val t = g.place(g.colonists[0], g.homeX, g.homeY)
        val head = t.race.body.indexOfFirst { it.tag == PartTag.HEAD }
        g.dealDamage(t, DamageKind.BULLET, 500f, 0f, null, partHint = head)
        assertTrue("a destroyed head is fatal", t.dead)
    }

    @Test fun cutsBleedUntilTheyAreTended() {
        val g = arena(611)
        val t = g.place(g.colonists[0], g.homeX, g.homeY)
        val arm = t.race.body.indexOfFirst { it.tag == PartTag.ARM }
        g.dealDamage(t, DamageKind.CUT, 15f, 0f, null, partHint = arm)
        assertTrue("the wound bleeds", t.injuries.any { it.bleed > 0f })
        repeat(2000) { g.step() }
        assertTrue("blood is lost over time", t.bloodLoss > 0f || t.dead)
    }

    @Test fun anesthesiaWearsOffAfterItsDuration() {
        val g = arena(612)
        val t = g.place(g.colonists[0], g.homeX, g.homeY)
        g.addHediff(t, HediffKind.ANESTHESIA, 0.5f).duration = 3000
        g.run(200)
        assertTrue("still under anesthesia", t.hediff(HediffKind.ANESTHESIA) != null)
        g.run(3000)
        assertFalse("the anesthetic has worn off", t.hediff(HediffKind.ANESTHESIA) != null)
    }

    @Test fun explosionsHurtAlliesToo() {
        val g = arena(613)
        val ally = g.place(g.colonists[0], g.homeX, g.homeY)
        val blast = g.place(g.colonists[1], g.homeX + 1, g.homeY)
        g.explode(g.homeX + 2, g.homeY, 3f, 40f)
        assertTrue("the blast reaches the ally next to it", ally.injuries.isNotEmpty() || blast.injuries.isNotEmpty())
    }

    @Test fun shotgunPelletsEachLandOnTheirOwn() {
        val g = arena(615)
        val me = g.place(g.colonists[0], g.homeX, g.homeY)
        me.weaponItem = ItemType.W_SHOTGUN
        val target = g.enemy(g.homeX + 4, g.homeY)
        repeat(5) { g.shoot(me, target) }
        val landed = g.shots.count { it.hit }
        assertTrue("some pellets landed", landed > 0)
        assertTrue("each landed shot wounds more than once ($landed shots, ${target.injuries.size} wounds)", target.injuries.size > landed)
    }

    @Test fun aimingAtANewTargetLosesTheWarmupBuiltUpOnTheOld() {
        val g = arena(616)
        val me = g.place(g.colonists[0], g.homeX, g.homeY)
        me.weaponItem = ItemType.W_RIFLE
        val first = g.enemy(g.homeX + 5, g.homeY)
        val second = g.enemy(g.homeX + 5, g.homeY + 2)
        me.attackCd = 0; me.warmup = 0
        g.fire(me, first); g.fire(me, first); g.fire(me, first)
        assertEquals("the aim builds up on the first target", 3, me.warmup)
        g.fire(me, second)
        assertEquals("switching target starts the warmup again", 1, me.warmup)
    }

    @Test fun aDraftedPawnKeepsItsTargetWhileItCanBeHit() {
        val g = arena(617)
        val me = g.place(g.colonists[0], g.homeX, g.homeY)
        me.weaponItem = ItemType.W_RIFLE
        me.drafted = true
        val near = g.enemy(g.homeX + 3, g.homeY)
        val far = g.enemy(g.homeX + 9, g.homeY)
        me.fightTarget = far.id
        repeat(3) { g.draftedAI(me) }
        assertEquals("the pawn stays on the target it chose, though a nearer one is in reach", far.id, me.fightTarget)
        assertTrue("the nearer enemy is not shot at", near.injuries.isEmpty())
    }

    @Test fun aRaiderPicksTheStandingColonistOverADownedOne() {
        val g = arena(618)
        val raider = g.enemy(g.homeX + 20, g.homeY)
        raider.weaponItem = ItemType.W_RIFLE
        val downed = g.place(g.colonists[0], g.homeX + 18, g.homeY)
        downed.downed = true
        val standing = g.place(g.colonists[1], g.homeX + 24, g.homeY)
        raider.job = Job(JobType.RAID)
        g.hostileAI(raider)
        assertEquals("the downed colonist is passed over", standing.id, raider.job!!.targetPawn)
    }

    @Test fun aRaiderHoldsItsRangeAndDoesNotFireFromTooFar() {
        val g = arena(619)
        val raider = g.enemy(g.homeX + 30, g.homeY)
        raider.weaponItem = ItemType.W_RIFLE
        val colonist = g.place(g.colonists[0], g.homeX + 2, g.homeY)
        raider.job = Job(JobType.RAID); raider.job!!.targetPawn = colonist.id
        raider.warmup = Weapon.RIFLE.warmupTicks; raider.attackCd = 0
        val before = g.shots.size
        g.hostileAI(raider)
        assertEquals("from beyond its standoff range it closes in instead of shooting", before, g.shots.size)
    }

    @Test fun aRetreatingRaiderLeavesTheMap() {
        val g = arena(620)
        val raider = g.enemy(g.homeX + 10, g.homeY)
        raider.retreating = true
        g.hostileAI(raider)
        assertEquals("a retreating raider heads for the edge", JobType.LEAVE, raider.job?.type)
    }

    @Test fun theSameSeedGivesTheSameFight() {
        fun run(seed: Long): List<Int> {
            val g = arena(seed)
            val me = g.place(g.colonists[0], g.homeX, g.homeY)
            me.weaponItem = ItemType.W_RIFLE
            val target = g.enemy(g.homeX + 7, g.homeY)
            repeat(30) { g.shoot(me, target) }
            return g.shots.map { if (it.hit) 1 else 0 } + target.injuries.map { it.part }
        }
        assertEquals("a fight is reproducible from its seed", run(614), run(614))
    }
}
