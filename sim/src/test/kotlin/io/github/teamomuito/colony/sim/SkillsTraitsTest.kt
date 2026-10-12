package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Values from docs/fidelity/skills-traits.md, section 3.6 of the decompiled reference. */
class SkillsTraitsTest {
    private fun human(): Pawn = Pawn(1, "Test Pawn", Race.HUMAN, Faction.PLAYER)

    // ---- XP curve and level rules

    @Test fun theXpCurveFollowsTheReferencePoints() {
        assertEquals(1000f, SkillRules.xpToLevelUp(0), 0f)
        assertEquals(6000f, SkillRules.xpToLevelUp(5), 0f)
        assertEquals(10000f, SkillRules.xpToLevelUp(9), 0f)
        assertEquals(12000f, SkillRules.xpToLevelUp(10), 0f)
        assertEquals(28000f, SkillRules.xpToLevelUp(18), 0f)
        assertEquals(30000f, SkillRules.xpToLevelUp(19), 0f)
        assertEquals(30000f, SkillRules.xpToLevelUp(20), 0f)
    }

    @Test fun aLevelUpCarriesTheSurplusAndUsesTheNewRequirement() {
        // 4500 XP from level 0: 1000 to level 1, 2000 to level 2, and 1500 left, short of 3000.
        assertEquals(2 to 1500f, SkillRules.applyXp(0, 0f, 4500f))
    }

    @Test fun aSkillAtTwentyHoldsItsXpJustBelowTheNextLevel() {
        assertEquals(20 to 29999f, SkillRules.applyXp(20, 0f, 1_000_000f))
    }

    @Test fun xpBelowMinusAThousandDropsTheLevelAndRefillsFromTheLowerRequirement() {
        // -1000.05 falls to level 9, which needs 10000, so the XP becomes 8999.95.
        val (lvl, x) = SkillRules.applyXp(10, -999.95f, -0.1f)
        assertEquals(9, lvl)
        assertEquals(8999.95f, x, 0.01f)
    }

    @Test fun levelZeroClampsXpToZeroWhenItDrops() {
        assertEquals(0 to 0f, SkillRules.applyXp(1, -999f, -5f))
    }

    // ---- Learning factors and the daily cap

    @Test fun thePassionFactorsAreTheReferenceValues() {
        assertEquals(0.35f, SkillRules.passionFactor(0), 0f)
        assertEquals(1.0f, SkillRules.passionFactor(1), 0f)
        assertEquals(1.5f, SkillRules.passionFactor(2), 0f)
    }

    @Test fun learningFollowsThePassion() {
        val none = human(); none.gainXp(SkillType.ARTISTRY, 100f)
        assertEquals(35f, none.xp[SkillType.ARTISTRY.ordinal], 1e-3f)
        val minor = human(); minor.passion[SkillType.ARTISTRY.ordinal] = 1; minor.gainXp(SkillType.ARTISTRY, 100f)
        assertEquals(100f, minor.xp[SkillType.ARTISTRY.ordinal], 1e-3f)
        val major = human(); major.passion[SkillType.ARTISTRY.ordinal] = 2; major.gainXp(SkillType.ARTISTRY, 100f)
        assertEquals(150f, major.xp[SkillType.ARTISTRY.ordinal], 1e-3f)
    }

    @Test fun theTraitFactorsApplyAfterThePassion() {
        val eager = human(); eager.traits.add(Trait.EAGER_PUPIL); eager.gainXp(SkillType.ARTISTRY, 100f)
        assertEquals(49f, eager.xp[SkillType.ARTISTRY.ordinal], 1e-3f)
        val slow = human(); slow.traits.add(Trait.SLOW_STUDY); slow.gainXp(SkillType.ARTISTRY, 100f)
        assertEquals(21f, slow.xp[SkillType.ARTISTRY.ordinal], 1e-3f)
    }

    @Test fun aSkillLearnsAtOneFifthAfterFourThousandXpInADay() {
        val p = human(); p.passion[SkillType.ARTISTRY.ordinal] = 2
        p.gainXp(SkillType.ARTISTRY, 3000f)
        assertEquals(4500f, p.xpToday[SkillType.ARTISTRY.ordinal], 1e-3f)
        assertEquals(2, p.skill[SkillType.ARTISTRY.ordinal])
        assertEquals(1500f, p.xp[SkillType.ARTISTRY.ordinal], 1e-3f)
        // Today's total is over 4000, so this 100 learns at 0.2 of the major rate: 1.5 x 0.2 x 100 = 30.
        p.gainXp(SkillType.ARTISTRY, 100f)
        assertEquals(1530f, p.xp[SkillType.ARTISTRY.ordinal], 1e-3f)
        assertEquals(4530f, p.xpToday[SkillType.ARTISTRY.ordinal], 1e-3f)
    }

    @Test fun theDailyCountIsPerSkill() {
        val p = human(); p.passion[SkillType.ARTISTRY.ordinal] = 2
        p.gainXp(SkillType.ARTISTRY, 3000f)
        p.gainXp(SkillType.GALLEY, 100f)
        assertEquals(35f, p.xp[SkillType.GALLEY.ordinal], 1e-3f)
    }

    @Test fun animalsAndMechanoidsLearnNothing() {
        val animal = Pawn(2, "Boar", Race.entries.first { it.isAnimal }, Faction.WILD)
        animal.gainXp(SkillType.MARKSMANSHIP, 100f)
        assertEquals(0f, animal.xp[SkillType.MARKSMANSHIP.ordinal], 0f)
        val mech = Pawn(3, "Mech", Race.entries.first { it.mech }, Faction.PLAYER)
        assertFalse(mech.hasSkills)
        mech.gainXp(SkillType.MARKSMANSHIP, 100f)
        assertEquals(0f, mech.xp[SkillType.MARKSMANSHIP.ordinal], 0f)
    }

    @Test fun aSkillWhoseWorkIsAllIncapableLearnsNothing() {
        val p = human(); p.incapable = 1 shl WorkType.ART.ordinal
        p.gainXp(SkillType.ARTISTRY, 100f)
        assertEquals(0f, p.xp[SkillType.ARTISTRY.ordinal], 0f)
        assertTrue(SkillRules.skillDisabled(SkillType.ARTISTRY, p.incapable))
        assertFalse(SkillRules.skillDisabled(SkillType.CLOSE_COMBAT, p.incapable))
    }

    @Test fun aLevelTwentyPawnHoldsItsXpAtTheCap() {
        val p = human(); p.skill[SkillType.ARTISTRY.ordinal] = 20; p.passion[SkillType.ARTISTRY.ordinal] = 2
        p.gainXp(SkillType.ARTISTRY, 1_000_000f)
        assertEquals(20, p.skill[SkillType.ARTISTRY.ordinal])
        assertEquals(29999f, p.xp[SkillType.ARTISTRY.ordinal], 1e-3f)
    }

    // ---- Decay

    @Test fun decayStartsAtLevelTenAndGrowsWithTheLevel() {
        assertEquals(0f, SkillRules.decayPerInterval(9), 0f)
        assertEquals(-0.1f, SkillRules.decayPerInterval(10), 0f)
        assertEquals(-0.4f, SkillRules.decayPerInterval(12), 0f)
        assertEquals(-1.0f, SkillRules.decayPerInterval(14), 0f)
        assertEquals(-4.0f, SkillRules.decayPerInterval(17), 0f)
        assertEquals(-12f, SkillRules.decayPerInterval(20), 0f)
    }

    @Test fun aSkillAtLevelTenLosesXpEachInterval() {
        val p = human(); p.skill[SkillType.ARTISTRY.ordinal] = 10; p.xp[SkillType.ARTISTRY.ordinal] = 500f
        p.skillIntervalTick()
        assertEquals(499.9f, p.xp[SkillType.ARTISTRY.ordinal], 1e-3f)
        assertEquals(10, p.skill[SkillType.ARTISTRY.ordinal])
    }

    @Test fun decayDoesNotNeedUse() {
        // The reference has no use check: an idle level-12 skill loses XP every interval.
        val p = human(); p.skill[SkillType.CLOSE_COMBAT.ordinal] = 12; p.xp[SkillType.CLOSE_COMBAT.ordinal] = 0f
        p.skillIntervalTick()
        assertEquals(-0.4f, p.xp[SkillType.CLOSE_COMBAT.ordinal], 1e-3f)
    }

    @Test fun aSkillBelowTenDoesNotDecay() {
        val p = human(); p.skill[SkillType.ARTISTRY.ordinal] = 9; p.xp[SkillType.ARTISTRY.ordinal] = 300f
        p.skillIntervalTick()
        assertEquals(300f, p.xp[SkillType.ARTISTRY.ordinal], 0f)
    }

    @Test fun aDisabledSkillDoesNotDecay() {
        val p = human(); p.incapable = 1 shl WorkType.ART.ordinal
        p.skill[SkillType.ARTISTRY.ordinal] = 12; p.xp[SkillType.ARTISTRY.ordinal] = 300f
        p.skillIntervalTick()
        assertEquals(300f, p.xp[SkillType.ARTISTRY.ordinal], 0f)
    }

    @Test fun decayCountsAgainstTodaysXp() {
        val p = human(); p.skill[SkillType.ARTISTRY.ordinal] = 10
        p.skillIntervalTick()
        assertEquals(-0.1f, p.xpToday[SkillType.ARTISTRY.ordinal], 1e-6f)
    }

    // ---- Generation

    @Test fun aBaseLevelWithoutBackstoriesIsZeroToFourAtThirtyYears() {
        for (seed in 1L..100L) {
            val levels = SkillRules.generateLevels(Rng(seed), 30, emptyList())
            assertTrue("seed $seed: ${levels.toList()}", levels.all { it in 0..4 })
        }
    }

    @Test fun aBackstoryGainAddsItsAmountWithAFactorFromOneToOnePointFour() {
        for (seed in 1L..100L) {
            val levels = SkillRules.generateLevels(Rng(seed), 30, listOf(SkillType.CLOSE_COMBAT to 10))
            // Base 0 to 4 plus 10 x (1.0 to 1.4): 10 to 18, which the final curve brings to 10 to 15.
            assertTrue("seed $seed: ${levels.toList()}", levels[SkillType.CLOSE_COMBAT.ordinal] in 10..15)
        }
    }

    @Test fun aLargeBackstoryGainIsCompressedAtTheTop() {
        for (seed in 1L..100L) {
            val levels = SkillRules.generateLevels(Rng(seed), 30, listOf(SkillType.CLOSE_COMBAT to 20))
            // 20 to 28 before compression: 20 maps to 16, and 27 or more maps to 20.
            assertTrue("seed $seed: ${levels[SkillType.CLOSE_COMBAT.ordinal]}", levels[SkillType.CLOSE_COMBAT.ordinal] in 16..20)
        }
    }

    @Test fun thePassionBudgetMakesOneToNinePassionsWithoutForcedOnes() {
        for (seed in 1L..300L) {
            val levels = IntArray(SkillType.entries.size) { 3 }
            val passion = SkillRules.generatePassions(Rng(seed), levels, emptyList(), { false }, adult = true)
            val n = passion.count { it > 0 }
            assertTrue("seed $seed had $n", n in 1..9)
            assertTrue(passion.all { it in 0..2 })
        }
    }

    @Test fun theFirstPassionGoesToTheHighestLevelSkill() {
        for (seed in 1L..100L) {
            val levels = IntArray(SkillType.entries.size) { 2 }
            levels[SkillType.HEALING.ordinal] = 14
            val passion = SkillRules.generatePassions(Rng(seed), levels, emptyList(), { false }, adult = true)
            assertTrue("seed $seed", passion[SkillType.HEALING.ordinal] >= 1)
        }
    }

    @Test fun aForcedPassionIsAlwaysSetAndAPassionNeverAddsLevels() {
        for (seed in 1L..200L) {
            val levels = IntArray(SkillType.entries.size) { 2 }
            val passion = SkillRules.generatePassions(Rng(seed), levels, listOf(SkillType.CLOSE_COMBAT), { false }, adult = true)
            assertTrue("seed $seed", passion[SkillType.CLOSE_COMBAT.ordinal] >= 1)
            assertTrue(levels.all { it == 2 })
        }
    }

    @Test fun aDisabledSkillGetsNoPassion() {
        for (seed in 1L..200L) {
            val levels = IntArray(SkillType.entries.size) { 5 }
            val passion = SkillRules.generatePassions(Rng(seed), levels, listOf(SkillType.ARCHITECTURE), { it == SkillType.ARCHITECTURE }, adult = true)
            assertEquals("seed $seed", 0, passion[SkillType.ARCHITECTURE.ordinal])
        }
    }

    @Test fun forcedTraitsAreStreetScrapperOnly() {
        assertEquals(SkillType.CLOSE_COMBAT, SkillRules.forcedPassion(Trait.STREET_SCRAPPER))
        assertEquals(null, SkillRules.forcedPassion(Trait.EAGER_PUPIL))
    }

    @Test fun newPawnsHaveNoConflictingTraitsAndOneToThreeDrawnTraits() {
        val g = Game(77L)
        repeat(200) {
            val p = g.newHuman(0, 0)
            for (a in p.traits) for (b in p.traits) if (a !== b) assertFalse("${a.name} with ${b.name}", g.conflicts(a, b))
            // Up to three drawn traits, plus the romantic one that is added on its own.
            assertTrue("traits ${p.traits.size}", p.traits.size in 1..4)
        }
    }

    @Test fun backstoryTraitsCountTowardTheTargetSoNoDrawIsAdded() {
        // Three traits that clash with none of each other fill the target of 1 to 3 on their own (PawnGenerator.cs:1495-1510).
        val fixed = listOf(Trait.BRISK_HANDS, Trait.THICK_HIDE, Trait.SUNNY_OUTLOOK)
        val g = Game(5L)
        repeat(50) {
            val p = human()
            g.drawTraits(p, fixed)
            assertEquals(fixed, p.traits.toList())
        }
    }

    @Test fun aBackstoryTraitThatClashesIsStillAdded() {
        // Forced traits are not checked for clashes in vanilla (PawnGenerator.cs:1461-1492; TraitSet.cs:258-270).
        val clashing = listOf(Trait.BRISK_HANDS, Trait.IDLE_STREAK)
        val g = Game(5L)
        assertTrue("the pair clashes", g.conflicts(Trait.BRISK_HANDS, Trait.IDLE_STREAK))
        repeat(20) {
            val p = human()
            g.drawTraits(p, clashing)
            assertTrue("both backstory traits are kept", p.traits.containsAll(clashing))
        }
    }

    @Test fun aRepeatedBackstoryTraitIsAddedOnce() {
        // A repeat is skipped (PawnGenerator.cs:1471, 1487: HasTrait; TraitSet.cs:260: HasTrait refuses the second copy).
        val g = Game(7L)
        val p = human()
        g.drawTraits(p, listOf(Trait.BRISK_HANDS, Trait.BRISK_HANDS))
        assertEquals(1, p.traits.count { it == Trait.BRISK_HANDS })
    }

    @Test fun aBackstoryTraitLeavesTheRestOfTheTargetToRandomDraws() {
        val g = Game(6L)
        repeat(50) {
            val p = human()
            g.drawTraits(p, listOf(Trait.BRISK_HANDS))
            assertEquals(Trait.BRISK_HANDS, p.traits.first())
            assertTrue("traits ${p.traits.size}", p.traits.size in 1..3)
            for (a in p.traits) for (b in p.traits) if (a !== b) assertFalse(g.conflicts(a, b))
        }
    }

    @Test fun newPawnsAreTheSameForTheSameSeed() {
        val a = Game(91L).newHuman(0, 0)
        val b = Game(91L).newHuman(0, 0)
        assertEquals(a.skill.toList(), b.skill.toList())
        assertEquals(a.passion.toList(), b.passion.toList())
        assertEquals(a.traits.toList(), b.traits.toList())
    }

    @Test fun aStreetScrapperAlwaysHasACloseCombatPassion() {
        for (seed in 1L..300L) {
            val g = Game(seed)
            val p = g.newHuman(0, 0)
            if (Trait.STREET_SCRAPPER in p.traits) assertTrue("seed $seed", p.passion[SkillType.CLOSE_COMBAT.ordinal] >= 1)
        }
    }

    // ---- Work, attacks and medicine

    @Test fun medicineXpIsHalfOfTheReferenceFigure() {
        assertEquals(250f, SkillRules.tendXp(false), 0f)
        assertEquals(87.5f, SkillRules.tendXp(true), 0f)
    }

    @Test fun cycleSecondsAreTicksOverSixty() {
        assertEquals(2.5f, SkillRules.cycleSeconds(150), 1e-6f)
    }

    private fun arena(seed: Long): Game {
        val g = Game(seed, GameMap.generateFor(100, 100, seed, Biome.TEMPERATE))
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

    private fun Game.placed(p: Pawn, x: Int, y: Int): Pawn {
        p.x = x; p.y = y; p.fromX = x; p.fromY = y; p.moveCd = 0
        if (!pawns.contains(p)) pawns.add(p)
        return p
    }

    private fun enemyAt(g: Game, x: Int, y: Int): Pawn = g.placed(g.newHuman(x, y, Faction.ENEMY), x, y)

    /** One attack attempt: warm-up and cooldown are ready, so the call is one burst or one swing. */
    private fun Game.attemptOnce(p: Pawn, t: Pawn) {
        p.attackCd = 0
        p.warmup = p.weapon.warmupTicks
        fire(p, t)
    }

    private fun shooter(g: Game, weapon: ItemType): Pawn {
        val me = g.placed(g.colonists[0], g.homeX, g.homeY)
        me.weaponItem = weapon
        me.passion.fill(0); me.skill.fill(0); me.xp.fill(0f); me.xpToday.fill(0f); me.traits.clear(); me.incapable = 0
        return me
    }

    @Test fun aShotLearnsMarksmanshipAtOneHundredSeventyPerSecondOfCycleAgainstAHostile() {
        val g = arena(701)
        val me = shooter(g, ItemType.W_RIFLE)
        val target = enemyAt(g, g.homeX + 6, g.homeY)
        g.attemptOnce(me, target)
        val w = me.weapon
        // The cycle is warm-up plus the cooldown the shot set, plus the spacing between a burst's shots.
        val cycle = w.warmupTicks + me.attackCd + (w.burst - 1) * tk(2)
        assertEquals(170f * SkillRules.cycleSeconds(cycle) * 0.35f, me.xp[SkillType.MARKSMANSHIP.ordinal], 0.01f)
    }

    @Test fun aDownedTargetGivesNoShootingXp() {
        val g = arena(702)
        val me = shooter(g, ItemType.W_RIFLE)
        val target = enemyAt(g, g.homeX + 6, g.homeY)
        target.downed = true
        g.attemptOnce(me, target)
        assertEquals(0f, me.xp[SkillType.MARKSMANSHIP.ordinal], 0f)
    }

    @Test fun aMeleeSwingLearnsCloseCombatForItsFullCycleInSeconds() {
        val g = arena(703)
        val me = shooter(g, ItemType.W_POCKET_BLADE)
        val target = enemyAt(g, g.homeX + 1, g.homeY)
        g.attemptOnce(me, target)
        // The full cycle is warm-up plus the cooldown the swing set (Verse/VerbProperties.cs:579-581). Melee weapons carry no
        // warm-up in the data, so here it is the cooldown alone.
        val w = me.weapon
        val cycle = w.warmupTicks + me.attackCd + (w.burst - 1) * tk(2)
        assertEquals(200f * SkillRules.cycleSeconds(cycle) * 0.35f, me.xp[SkillType.CLOSE_COMBAT.ordinal], 0.01f)
    }

    @Test fun aDownedMeleeTargetGivesNoCloseCombatXp() {
        val g = arena(704)
        val me = shooter(g, ItemType.W_POCKET_BLADE)
        val target = enemyAt(g, g.homeX + 1, g.homeY)
        target.downed = true
        g.attemptOnce(me, target)
        assertEquals(0f, me.xp[SkillType.CLOSE_COMBAT.ordinal], 0f)
    }

    @Test fun theDailyCountStartsAgainAtMidnight() {
        val g = arena(705)
        val p = g.colonists[0]
        p.xpToday[SkillType.HEALING.ordinal] = 5000f
        g.tick = TICKS_PER_DAY - 1L
        g.step()
        assertEquals(0f, p.xpToday[SkillType.HEALING.ordinal], 0f)
    }

    @Test fun decayRunsOnItsPhaseOfTheDecayInterval() {
        val g = arena(706)
        val p = g.colonists[0]
        g.pawns.retainAll { it === p || !it.hasSkills }
        p.job = null
        p.skill.fill(0); p.xp.fill(0f); p.xpToday.fill(0f)
        p.skill[SkillType.ARTISTRY.ordinal] = 12; p.xp[SkillType.ARTISTRY.ordinal] = 500f
        // The next step's tick is 20000 - id, and (tick + id) is then a multiple of 200.
        g.tick = 20000L - p.id - 1L
        g.step()
        assertEquals(500f - 0.4f, p.xp[SkillType.ARTISTRY.ordinal], 0.01f)
    }
}
