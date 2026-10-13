package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

// Tests for the combat model in docs/fidelity/combat.md: the rules as pure functions, then the fight-level behaviour.

/** A calm seeded colony with a clear strip of open ground, as in CombatPipelineTest. */
private fun arena(seed: Long): Game {
    val g = Game(seed, GameMap.generateFor(Game.MAP_SIZE, Game.MAP_SIZE, seed, Biome.TEMPERATE))
    g.startNewColony(Scenario.QUIET_ENCLAVE)
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

private fun Game.enemy(x: Int, y: Int): Pawn = place(newHuman(x, y, Faction.ENEMY), x, y)

/** One attempt, ignoring warmup and cooldown. */
private fun Game.shoot(p: Pawn, t: Pawn) {
    p.attackCd = 0
    p.warmup = p.weapon.warmupTicks
    fire(p, t)
}

class CombatModelTest {

    // ----------------------------------------------------------------------------- pure rules

    @Test fun equipmentBandsBlendAcrossTheBreakpoints() {
        val w = Weapon.RIFLE
        assertEquals(w.accuracy, equipmentFactor(w, 3f), 1e-6f)
        assertEquals("touch and short use the stated accuracy", w.accuracy, equipmentFactor(w, 7.5f), 1e-6f)
        assertEquals(w.accuracy * 0.9f, equipmentFactor(w, 25f), 1e-6f)
        assertEquals("halfway from medium to long", w.accuracy * 0.8f, equipmentFactor(w, 32.5f), 1e-6f)
        assertEquals(w.accuracy * 0.7f, equipmentFactor(w, 40f), 1e-6f)
        assertEquals("past the last breakpoint the long band holds", w.accuracy * 0.7f, equipmentFactor(w, 60f), 1e-6f)
    }

    @Test fun equipmentFactorStaysWithinItsClampForEveryRangedWeapon() {
        for (w in Weapon.entries.filter { it.ranged }) for (d in 0..60) {
            val f = equipmentFactor(w, d.toFloat())
            assertTrue("${w.name} at $d cells: $f", f in 0.01f..1f)
        }
    }

    @Test fun theShooterFactorFallsWithDistanceAndRisesWithSkill() {
        assertEquals(StrictMath.pow(0.96, 10.0).toFloat(), shooterFactor(shooterAccuracy(0), 10f), 1e-5f)
        assertTrue(shooterAccuracy(20) > shooterAccuracy(0))
        assertTrue("the shooter factor never passes 1", shooterAccuracy(1000) <= 1f)
    }

    @Test fun penetrationDefaultsToFifteenThousandthsOfDamagePerProjectile() {
        assertEquals("a rifle has no hand-set penetration", 11f * 0.015f, penetrationOf(Weapon.RIFLE), 1e-6f)
        assertEquals("a shotgun pellet carries a pellet's share of the damage", 18f / 8f * 0.015f, penetrationOf(Weapon.SHOTGUN), 1e-6f)
        assertEquals("a club has no hand-set penetration", 11f * 0.015f, penetrationOf(Weapon.CLUB), 1e-6f)
        assertEquals("a hand-set value stays", 0.3f, penetrationOf(Weapon.SNIPER), 1e-6f)
    }

    @Test fun anArmourLayerDeflectsHalvesOrPassesTheDamage() {
        assertTrue("a roll under half the rating deflects", armorStep(20f, 0.5f, 0.2f).deflected)
        val halved = armorStep(20f, 0.5f, 0.3f)
        assertEquals("a roll between half the rating and the rating halves it", 10f, halved.damage, 1e-6f)
        assertTrue(halved.halved)
        val passed = armorStep(20f, 0.5f, 0.6f)
        assertEquals("a roll over the rating passes the damage", 20f, passed.damage, 1e-6f)
        assertFalse(passed.halved)
        assertEquals("no rating means no roll, so the damage passes", 20f, armorStep(20f, 0f, 0f).damage, 1e-6f)
    }

    @Test fun aMeleeHitUnderOneIsRaisedToOneAndBecomesBlunt() {
        assertEquals("a hit under 1 is raised to 1 and becomes blunt", 1f to DamageKind.BRUISE, meleeFloor(0.6f, DamageKind.CUT))
        assertEquals("a hit of 1 or more keeps its kind", 1.2f to DamageKind.CUT, meleeFloor(1.2f, DamageKind.CUT))
    }

    @Test fun aSandbagBehindTheTargetBlocksItsShareAndASideCoverDoesNot() {
        // Shooter straight along the x axis, sandbag between: angle 0 and distance factor 1.
        assertEquals(0.55f, coverBlockChance(0.55f, -1, 0, -10, 0, -9, 0), 1e-6f)
        // A diagonal cover 45 degrees off the line of fire becomes 78.75 degrees, past the last band: no cover.
        assertEquals(0f, coverBlockChance(0.55f, -1, -1, -10, 0, -9, 1), 1e-6f)
        // A cover cell within 1.9 cells of the shooter keeps 0.3333 of its chance (Verse/CoverUtility.cs:92).
        assertEquals(0.55f * 0.3333f, coverBlockChance(0.55f, -1, 0, -2, 0, -1, 0), 1e-6f)
    }

    @Test fun twoCoversCombineAsOneMinusTheProductOfTheGaps() {
        val covers = listOf(CoverBlock(Building(BuildDef.SANDBAGS, 0, 0, true), 0.5f), CoverBlock(Building(BuildDef.SANDBAGS, 0, 0, true), 0.5f))
        assertEquals(0.75f, overallBlock(covers), 1e-6f)
        assertEquals(0f, overallBlock(emptyList()), 0f)
    }

    @Test fun theFlammabilityCurveAndCumulativeChancesFollowTheReference() {
        assertEquals(0f, catchChancePerSecond(0f), 1e-6f)
        assertEquals(0.07f, catchChancePerSecond(0.1f), 1e-6f)
        assertEquals(1f, catchChancePerSecond(0.3f), 1e-6f)
        assertEquals(0.07f, cumulativeCatchChance(0.1f, 60), 1e-5f)
        assertEquals(1f - StrictMath.pow(0.93, 2.5).toFloat(), cumulativeCatchChance(0.1f, 150), 1e-5f)
    }

    @Test fun theBurnDamagePerCheckFollowsTheClampedFormula() {
        assertEquals(0.0125f * 150f, burnDamagePerCheck(0f), 1e-5f)
        assertEquals((0.0125f + 0.0036f * 0.2f) * 150f, burnDamagePerCheck(0.2f), 1e-5f)
        assertEquals("the top of the clamp is 0.05 per tick-unit", 0.05f * 150f, burnDamagePerCheck(50f), 1e-5f)
    }

    // ----------------------------------------------------------------------------- reach

    @Test fun rangedReachEndsExactlyAtTheWeaponsRange() {
        val g = arena(630)
        val me = g.place(g.colonists[0], g.homeX, g.homeY)
        me.weaponItem = ItemType.W_RIFLE            // range 28
        val inRange = g.enemy(g.homeX + 28, g.homeY)
        val before = g.shots.size
        g.shoot(me, inRange)
        assertTrue("a shot at exactly the range is taken", g.shots.size > before)
        val beyond = g.enemy(g.homeX + 29, g.homeY)
        val after = g.shots.size
        g.shoot(me, beyond)
        assertEquals("one cell past the range is not", after, g.shots.size)
    }

    @Test fun meleeReachIsAdjacentIncludingDiagonals() {
        val g = arena(631)
        val me = g.place(g.colonists[0], g.homeX, g.homeY)
        me.weaponItem = ItemType.W_POCKET_BLADE
        val twoAway = g.enemy(g.homeX + 2, g.homeY)
        val before = g.shots.size
        g.shoot(me, twoAway)
        assertEquals("two cells away is out of melee reach", before, g.shots.size)
        val diagonal = g.enemy(g.homeX + 1, g.homeY + 1)
        g.shoot(me, diagonal)
        assertTrue("a diagonal neighbour is in reach", g.shots.size > before)
    }

    // ----------------------------------------------------------------------------- melee dodge and stagger

    @Test fun aDownedTargetIsAlwaysHitInMeleeAndCannotDodge() {
        val g = arena(632)
        val me = g.place(g.colonists[0], g.homeX, g.homeY)
        me.weaponItem = ItemType.W_POCKET_BLADE
        val t = g.enemy(g.homeX + 1, g.homeY)
        t.downed = true
        t.skill[SkillType.CLOSE_COMBAT.ordinal] = 20
        assertEquals(0f, dodgeChance(t), 0f)
        repeat(50) { assertTrue("a downed target is hit with no roll", g.meleeSwingHits(me, t)) }
    }

    @Test fun dodgingLowersTheMeleeHitRateAndSkillRaisesIt() {
        fun hitsOutOf400(targetSkill: Int): Int {
            val g = arena(633)
            val me = g.place(g.colonists[0], g.homeX, g.homeY)
            me.weaponItem = ItemType.W_POCKET_BLADE
            val target = g.enemy(g.homeX + 1, g.homeY)
            target.skill[SkillType.CLOSE_COMBAT.ordinal] = targetSkill
            var hits = 0
            repeat(400) { if (g.meleeSwingHits(me, target)) hits++ }
            return hits
        }
        val green = hitsOutOf400(0)
        val veteran = hitsOutOf400(20)
        assertTrue("a veteran dodges more: $green hits from a novice, $veteran from a veteran", veteran < green)
    }

    @Test fun aMeleeSwingStaggersTheTargetForNinetyFiveTicks() {
        val g = arena(634)
        val me = g.place(g.colonists[0], g.homeX, g.homeY)
        me.weaponItem = ItemType.W_POCKET_BLADE
        val t = g.enemy(g.homeX + 1, g.homeY)
        g.shoot(me, t)
        assertEquals("hit or miss, the target is staggered", g.tick + STAGGER_TICKS, t.staggerUntil)
    }

    @Test fun aSwingAtAPawnThatIsOffTheMapDoesNotStaggerIt() {
        val g = arena(645)
        val me = g.place(g.colonists[0], g.homeX, g.homeY)
        me.weaponItem = ItemType.W_POCKET_BLADE
        val t = g.enemy(g.homeX + 1, g.homeY)
        g.pawns.remove(t)
        g.shoot(me, t)
        assertEquals("a pawn that is not on the map is not staggered (RimWorld/Verb_MeleeAttack.cs:96)", 0L, t.staggerUntil)
    }

    @Test fun aStaggeredPawnTakesNoActionUntilTheStaggerEnds() {
        val g = arena(635)
        val t = g.enemy(g.homeX + 5, g.homeY)
        t.staggerUntil = g.tick + STAGGER_TICKS
        val x = t.x; val y = t.y
        repeat(STAGGER_TICKS - 5) { g.step() }
        assertEquals("the pawn does not move while staggered", x, t.x)
        assertEquals("the pawn does not move while staggered", y, t.y)
    }

    // ----------------------------------------------------------------------------- armour and cover in a fight

    @Test fun theOuterLayerTakesItsShareBeforeTheInnerLayer() {
        val g = arena(641)
        val t = g.place(g.colonists[0], g.homeX, g.homeY)
        t.apparel.clear()
        val coat = Worn(ItemType.A_LONG_COAT, Quality.NORMAL, ItemType.A_LONG_COAT.apparel!!.hp)
        val tee = Worn(ItemType.A_TSHIRT, Quality.NORMAL, ItemType.A_TSHIRT.apparel!!.hp)
        t.apparel.add(tee); t.apparel.add(coat)
        val torso = t.race.body.indexOfFirst { it.tag == PartTag.TORSO }
        val coatBefore = coat.hp; val teeBefore = tee.hp
        g.dealDamage(t, DamageKind.CUT, 20f, 0f, null, partHint = torso)
        val scaled = 20f / max(0.6f, sqrt(t.race.hpScale.toDouble()).toFloat()).let { if (t.race.isAnimal) 1f else it }
        assertEquals("the coat takes a quarter of the damage that reaches it", 0.25f * scaled, coatBefore - coat.hp, 1e-4f)
        assertTrue("the tee is reached only after the coat, so it loses no more", teeBefore - tee.hp <= coatBefore - coat.hp + 1e-4f)
    }

    @Test fun aHalvedSharpHitIsStillRolledAgainstTheSharpRatingByTheNextLayer() {
        // Brigandine (sharp 0.75, blunt 0.35) is listed first, so it is the outer of the two outer layers (a stable sort).
        // Ribbed doublet (sharp 0.52, blunt 0.14) is the inner. If the outer halves the sharp hit, the inner still rolls at its
        // sharp rating (ArmorUtility.cs:21), so the hit is halved twice (a quarter) on about 0.375 x 0.26 of the trials. Read
        // with the blunt rating, the quarter would come on about 0.375 x 0.07 of them.
        val g = arena(656)
        val t = g.enemy(g.homeX, g.homeY)
        t.traits.clear()
        val torso = t.race.body.indexOfFirst { it.tag == PartTag.TORSO }
        val full = 20f / max(0.6f, sqrt(t.race.hpScale.toDouble()).toFloat()).let { if (t.race.isAnimal) 1f else it }
        var quarters = 0
        repeat(400) {
            t.apparel.clear(); t.injuries.clear()
            t.apparel.add(Worn(ItemType.A_ARMOR, Quality.NORMAL, ItemType.A_ARMOR.apparel!!.hp))
            t.apparel.add(Worn(ItemType.A_RIBBED_JACKET, Quality.NORMAL, ItemType.A_RIBBED_JACKET.apparel!!.hp))
            g.dealDamage(t, DamageKind.CUT, 20f, 0f, null, partHint = torso)
            val dealt = t.injuries.filter { it.part == torso }.sumOf { it.severity.toDouble() }.toFloat()
            if (abs(dealt - full / 4f) < 1e-3f) quarters++
        }
        assertTrue("a halved sharp hit meets the next layer's sharp rating: $quarters quarter hits of 400", quarters >= 20)
    }

    @Test fun anExplosionHitsEachPawnOnce() {
        val g = arena(642)
        val t = g.place(g.colonists[0], g.homeX, g.homeY)
        t.apparel.clear()
        val hits = ArrayList<String>()
        g.debugHook = { line -> if (line.contains(" dmg ${t.name} ")) hits.add(line) }
        g.explode(g.homeX + 1, g.homeY, 3f, 40f)
        assertEquals("one hit per pawn per explosion", 1, hits.size)
    }

    @Test fun aSandbagThatStopsShotsTakesTheirDamage() {
        val g = arena(640)
        val me = g.place(g.colonists[0], g.homeX, g.homeY)
        me.weaponItem = ItemType.W_RIFLE
        val target = g.enemy(g.homeX + 8, g.homeY)
        g.map.setBuilding(Building(BuildDef.SANDBAGS, g.homeX + 7, g.homeY, true))
        val sandbag = g.map.building[g.map.idx(g.homeX + 7, g.homeY)]!!
        val start = sandbag.hp
        repeat(20) { g.shoot(me, target) }
        assertTrue("the cover took the shots it stopped (${sandbag.hp} of $start hit points left)", sandbag.hp < start)
    }

    @Test fun aCoverShotApartByOnePelletStopsNoMoreShots() {
        for (seed in 646L..655L) {
            val g = arena(seed)
            val me = g.place(g.colonists[0], g.homeX, g.homeY)
            me.weaponItem = ItemType.W_SHOTGUN
            val target = g.enemy(g.homeX + 6, g.homeY)
            g.map.setBuilding(Building(BuildDef.SANDBAGS, g.homeX + 5, g.homeY, true))
            val sandbag = g.map.building[g.map.idx(g.homeX + 5, g.homeY)]!!
            sandbag.hp = 1f   // the first pellet it stops shot it apart
            repeat(4) { g.shoot(me, target) }
            val shotApart = g.log.count { it.text.contains("was shot apart") }
            assertTrue("seed $seed: a cover shot apart is announced once, not for every later pellet ($shotApart)", shotApart <= 1)
        }
    }

    // ----------------------------------------------------------------------------- burning

    @Test fun aFlameHitSetsAPawnAlightAndStopsItsJob() {
        val g = arena(636)
        val t = g.place(g.colonists[0], g.homeX, g.homeY)
        t.apparel.clear()
        t.job = Job(JobType.WANDER)
        g.dealDamage(t, DamageKind.BURN, 4f, 0f, null)
        assertTrue("a flame hit sets the pawn alight at a size of 0.15 to 0.25", t.burnSize >= FLAME_SIZE_MIN && t.burnSize <= FLAME_SIZE_MAX)
        assertNull("catching fire stops the job", t.job)
    }

    @Test fun aBurningPawnTakesItsCheckDamageAndTheFireWearsOff() {
        val g = arena(637)
        val t = g.place(g.colonists[0], g.homeX, g.homeY)
        t.apparel.clear()
        g.catchFire(t, 0.2f)
        val before = t.injuries.sumOf { it.severity.toDouble() }
        g.burnTick(t)
        assertTrue("a burn check wounds the pawn", t.injuries.sumOf { it.severity.toDouble() } > before)
        assertEquals("the burn size wears off by the decay", 0.2f - BURN_DECAY, t.burnSize, 1e-6f)
        g.burnTick(t)
        assertEquals("a second check burns it out", 0f, t.burnSize, 1e-6f)
    }

    @Test fun aLargeFireSetsThePawnOnItsCellAlight() {
        val g = arena(638)
        val t = g.place(g.colonists[0], g.homeX, g.homeY)
        t.apparel.clear()
        val i = g.map.idx(g.homeX, g.homeY)
        g.map.fires[i] = Fire(0.6f).also { it.age = 140 }   // the check runs when the age reaches 150
        g.fireTick()
        assertTrue("a fire of 0.6 sets the pawn alight at about 0.6 x 0.2 (${t.burnSize})", t.burnSize > 0.1f && t.burnSize < 0.13f)
    }

    @Test fun aBurningPawnKeepsItsBurnAndStaggerThroughASaveAndLoad() {
        val g = arena(639)
        val t = g.place(g.colonists[0], g.homeX, g.homeY)
        g.catchFire(t, 0.22f)
        t.staggerUntil = g.tick + STAGGER_TICKS
        val loaded = SaveGame.read(SaveGame.write(g))
        val back = loaded.pawns.first { it.id == t.id }
        assertEquals(0.22f, back.burnSize, 0f)
        assertEquals(t.staggerUntil, back.staggerUntil)
    }

    @Test fun theSameSeedGivesTheSameFightWithBurning() {
        fun run(seed: Long): List<Float> {
            val g = arena(seed)
            val me = g.place(g.colonists[0], g.homeX, g.homeY)
            me.weaponItem = ItemType.W_EMBER_THROWER
            val target = g.enemy(g.homeX + 6, g.homeY)
            repeat(30) { g.shoot(me, target) }
            return g.shots.map { if (it.hit) 1f else 0f } + target.burnSize + target.injuries.size.toFloat()
        }
        assertEquals("a fight with burning is reproducible from its seed", run(643), run(643))
    }
}
