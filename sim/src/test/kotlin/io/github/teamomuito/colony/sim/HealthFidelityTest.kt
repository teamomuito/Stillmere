package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EPS = 0.005f

private fun healthGame(seed: Long): Game {
    val g = Game(seed)
    g.startNewColony(Scenario.LOST_TRIBE)
    g.mentalBreaksEnabled = false
    g.pawns.removeAll { it.isAnimal && it.faction == Faction.WILD }
    g.nextRaid = Long.MAX_VALUE; g.nextMisc = Long.MAX_VALUE; g.nextWanderer = Long.MAX_VALUE; g.nextTrader = Long.MAX_VALUE
    return g
}

private fun Pawn.parts(tag: PartTag): List<Int> = race.body.indices.filter { race.body[it].tag == tag }

/** Destroy a part outright. */
private fun Game.destroy(p: Pawn, part: Int) { woundPart(p, part, DamageKind.CUT, 10_000f); recomputeHealth(p) }

/** Damage a part to the given fraction of its health, without destroying it. */
private fun Game.hurt(p: Pawn, part: Int, fraction: Float) { woundPart(p, part, DamageKind.BRUISE, p.partMax(part) * fraction); recomputeHealth(p) }

private fun Pawn.capacity(c: Cap) = cap[c.ordinal]

// ====================================================================== body model

class BodyModelTest {
    @Test fun humanPartHealthMatchesTheDocumentedTable() {
        // Pins the values in docs/fidelity/health.md, "Human body parts". Only some rows are SECONDARY-sourced; the rest are UNVERIFIED.
        val expected = mapOf(
            "torso" to 40f, "neck" to 25f, "head" to 25f, "left arm" to 30f, "left hand" to 20f, "left leg" to 30f, "left foot" to 25f,
            "heart" to 15f, "left lung" to 15f, "left kidney" to 15f, "liver" to 20f, "stomach" to 20f,
            "brain" to 10f, "left eye" to 10f, "left ear" to 12f, "nose" to 10f, "jaw" to 20f,
        )
        for ((label, hp) in expected) assertEquals(label, hp, Bodies.HUMAN.first { it.label == label }.hp, 0f)
    }

    @Test fun organsSitInsideTheirParents() {
        val body = Bodies.HUMAN
        fun parentOf(label: String) = body[body.first { it.label == label }.parent].label
        for (o in listOf("heart", "left lung", "right lung", "stomach", "liver", "left kidney", "right kidney")) assertEquals(o, "torso", parentOf(o))
        for (o in listOf("brain", "left eye", "right eye", "left ear", "right ear", "nose", "jaw")) assertEquals(o, "head", parentOf(o))
        assertTrue("limbs are outer parts", body.filter { it.tag in setOf(PartTag.ARM, PartTag.LEG, PartTag.HAND, PartTag.FOOT) }.none { it.inner })
    }

    @Test fun onlyTheVitalPartsAreVital() {
        val vital = Bodies.HUMAN.filter { it.vital }.map { it.tag }.toSet()
        assertEquals(setOf(PartTag.HEART, PartTag.BRAIN, PartTag.TORSO, PartTag.NECK, PartTag.HEAD), vital)
    }

    @Test fun partHealthScalesWithTheBodyNotAFlatTotal() {
        val g = healthGame(5101)
        val human = g.colonists.first()
        val torso = human.race.body.indexOfFirst { it.tag == PartTag.TORSO }
        assertEquals(40f, human.partMax(torso), 0f)
        assertEquals(40f * 2.8f, HealthRules.partMax(40f, Race.MUFFALO.hpScale), 1e-4f)
    }

    @Test fun hitsLandOnOuterPartsInProportionToCoverage() {
        val g = healthGame(5102)
        val p = g.colonists.first()
        p.apparel.clear()
        val hits = IntArray(p.race.body.size)
        repeat(3000) {
            p.injuries.clear()
            g.dealDamage(p, DamageKind.BRUISE, 2f)
            for (inj in p.injuries) hits[inj.part]++
        }
        val body = p.race.body
        assertTrue("a blunt hit never goes straight to an organ", body.indices.filter { body[it].inner }.all { hits[it] == 0 })
        val outer = body.indices.filter { !body[it].inner }
        val coverage = outer.sumOf { body[it].coverage.toDouble() }.toFloat()
        val total = hits.sum().toFloat()
        for (i in outer) assertEquals(body[i].label, body[i].coverage / coverage, hits[i] / total, 0.03f)
    }

    @Test fun sharpHitsCanReachOrgansThroughTheirParent() {
        val g = healthGame(5103)
        val p = g.colonists.first()
        p.apparel.clear()
        val torso = p.race.body.indexOfFirst { it.tag == PartTag.TORSO }
        var organHits = 0
        repeat(300) {
            p.injuries.clear()
            g.dealDamage(p, DamageKind.STAB, 20f, 0f, null, torso)
            if (p.injuries.any { p.race.body[it.part].parent == torso }) organHits++
        }
        assertTrue("some stabs reach an organ: $organHits", organHits > 50)
        assertTrue("but not all: $organHits", organHits < 300)
    }

    @Test fun destroyingAParentTakesItsOrgansWithIt() {
        val g = healthGame(5104)
        val p = g.colonists.first()
        val head = p.race.body.indexOfFirst { it.tag == PartTag.HEAD }
        val brain = p.race.body.indexOfFirst { it.tag == PartTag.BRAIN }
        g.woundPart(p, head, DamageKind.CUT, 10_000f)
        assertTrue(p.partMissing(brain))
        assertTrue("losing the head is fatal", p.dead)
    }
}

// ====================================================================== capacities: one test per formula

class CapacityFormulaTest {
    @Test fun consciousnessCombinesBrainPainBloodAndOrgans() {
        assertEquals(1f, HealthRules.consciousness(1f, 0f, 0f, 1f, 1f, 1f), 0f)
        assertEquals("brain scales it directly", 0.5f, HealthRules.consciousness(0.5f, 0f, 0f, 1f, 1f, 1f), EPS)
        assertEquals("pain takes 55% of itself", 1f - 0.4f * 0.55f, HealthRules.consciousness(1f, 0.4f, 0f, 1f, 1f, 1f), EPS)
        assertEquals("blood loss bites after 30%", 1f - 0.2f * 1.4f, HealthRules.consciousness(1f, 0f, 0.5f, 1f, 1f, 1f), EPS)
        assertEquals("no effect from blood loss below 30%", 1f, HealthRules.consciousness(1f, 0f, 0.3f, 1f, 1f, 1f), 0f)
        assertEquals("pumping has weight 0.2", 0.9f, HealthRules.consciousness(1f, 0f, 0f, 1f, 0.5f, 1f), EPS)
        assertEquals("breathing has weight 0.2", 0.9f, HealthRules.consciousness(1f, 0f, 0f, 0.5f, 1f, 1f), EPS)
        assertEquals("filtration has weight 0.1", 0.9f, HealthRules.consciousness(1f, 0f, 0f, 1f, 1f, 0f), EPS)
        assertEquals("factors multiply", 0.5f * 0.9f * 0.9f, HealthRules.consciousness(0.5f, 0f, 0f, 0.5f, 0.5f, 1f), EPS)
    }

    @Test fun consciousnessOnAPawnFollowsTheOrgans() {
        val g = healthGame(5201)
        val p = g.colonists.first()
        g.recomputeHealth(p)
        assertEquals(1f, p.capacity(Cap.CONSCIOUSNESS), 0f)
        g.destroy(p, p.parts(PartTag.LUNG).first())
        assertEquals(0.9f, p.capacity(Cap.CONSCIOUSNESS), EPS)
    }

    @Test fun movingIsLegsWithFeetAndToesAsSmallerShares() {
        assertEquals(1f, HealthRules.moving(1f, 1f, 1f), 0f)
        assertEquals("one leg of two", 0.5f, HealthRules.moving(0.5f, 1f, 1f), EPS)
        assertEquals("no feet costs a quarter", 0.75f, HealthRules.moving(1f, 0f, 1f), EPS)
        assertEquals("no toes costs a tenth", 0.9f, HealthRules.moving(1f, 1f, 0f), EPS)
        val g = healthGame(5202)
        val p = g.colonists.first()
        g.destroy(p, p.parts(PartTag.LEG).first())
        assertEquals(0.5f, p.capacity(Cap.MOVING), EPS)
    }

    @Test fun manipulationIsHandsFingersAndArms() {
        assertEquals("a body without hands is not limited", 1f, HealthRules.manipulation(false, 0f, 0f, 0f), 0f)
        assertEquals(1f, HealthRules.manipulation(true, 1f, 1f, 1f), EPS)
        assertEquals("half the hands", 0.5f * 0.7f + 0.3f, HealthRules.manipulation(true, 0.5f, 1f, 1f), EPS)
        assertEquals("no fingers leaves 60% of the grip", 0.6f * 0.7f + 0.3f, HealthRules.manipulation(true, 1f, 0f, 1f), EPS)
        assertEquals("no arms leaves 70%", 0.7f, HealthRules.manipulation(true, 1f, 1f, 0f), EPS)
        val g = healthGame(5203)
        val p = g.colonists.first()
        g.destroy(p, p.parts(PartTag.HAND).first())
        assertEquals(0.5f * 0.7f + 0.3f, p.capacity(Cap.MANIPULATION), EPS)
    }

    @Test fun sightIsTheMeanOfTheEyes() {
        assertEquals(0.5f, HealthRules.sight(HealthRules.mean(listOf(1f, 0f))), 0f)
        val g = healthGame(5204)
        val p = g.colonists.first()
        g.destroy(p, p.parts(PartTag.EYE).first())
        assertEquals("one eye gone halves sight", 0.5f, p.capacity(Cap.SIGHT), EPS)
        assertTrue(p.alive)
    }

    @Test fun hearingIsTheMeanOfTheEars() {
        assertEquals(0.5f, HealthRules.hearing(HealthRules.mean(listOf(1f, 0f))), 0f)
        val g = healthGame(5205)
        val p = g.colonists.first()
        g.hurt(p, p.parts(PartTag.EAR).first(), 0.5f)
        assertEquals(0.75f, p.capacity(Cap.HEARING), EPS)
    }

    @Test fun eatingIsTheJawAloneAndTheStomachBelongsToMetabolism() {
        assertEquals(0.5f, HealthRules.eating(0.5f), 0f)
        val g = healthGame(5206)
        val p = g.colonists.first()
        g.destroy(p, p.parts(PartTag.STOMACH).first())
        assertEquals("a lost stomach does not slow chewing", 1f, p.capacity(Cap.EATING), 0f)
        g.hurt(p, p.parts(PartTag.JAW).first(), 0.5f)
        assertEquals(0.5f, p.capacity(Cap.EATING), EPS)
    }

    @Test fun breathingIsTheMeanOfTheLungsAndLosingBothIsFatal() {
        assertEquals(0.5f, HealthRules.breathing(HealthRules.mean(listOf(1f, 0f))), 0f)
        val g = healthGame(5207)
        val p = g.colonists.first()
        val (a, b) = p.parts(PartTag.LUNG)
        g.destroy(p, a)
        assertEquals("one lung gone halves breathing", 0.5f, p.capacity(Cap.BREATHING), EPS)
        assertTrue(p.alive)
        g.destroy(p, b)
        assertTrue("both lungs gone is fatal", p.dead)
    }

    @Test fun bloodPumpingIsTheHeartAndLosingItIsFatal() {
        assertEquals(0.5f, HealthRules.bloodPumping(0.5f), 0f)
        val g = healthGame(5208)
        val p = g.colonists.first()
        val heart = p.parts(PartTag.HEART).first()
        g.hurt(p, heart, 0.5f)
        assertEquals(0.5f, p.capacity(Cap.PUMPING), EPS)
        g.destroy(p, heart)
        assertTrue(p.dead)
    }

    @Test fun bloodFiltrationIsKidneysAndLiverWhicheverIsWorse() {
        assertEquals(1f, HealthRules.bloodFiltration(null, null), 0f)
        assertEquals(0.5f, HealthRules.bloodFiltration(0.5f, 1f), 0f)
        assertEquals(0.25f, HealthRules.bloodFiltration(0.5f, 0.25f), 0f)
        val g = healthGame(5209)
        val p = g.colonists.first()
        val (k1, k2) = p.parts(PartTag.KIDNEY)
        g.destroy(p, k1)
        assertEquals("one kidney gone halves filtration", 0.5f, p.capacity(Cap.FILTRATION), EPS)
        assertTrue(p.alive)
        g.destroy(p, k2)
        assertTrue("both kidneys gone is fatal", p.dead)
        assertTrue(g.graveyard.last(), g.graveyard.last().contains("organ failure"))
    }

    @Test fun losingTheLiverIsFatal() {
        val g = healthGame(5210)
        val p = g.colonists.first()
        g.destroy(p, p.parts(PartTag.LIVER).first())
        assertTrue(p.dead)
    }

    @Test fun metabolismIsTheStomach() {
        assertEquals(0.5f, HealthRules.metabolism(0.5f), 0f)
        val g = healthGame(5211)
        val p = g.colonists.first()
        g.recomputeHealth(p)
        assertEquals(1f, p.capacity(Cap.METABOLISM), 0f)
        g.hurt(p, p.parts(PartTag.STOMACH).first(), 0.5f)
        assertEquals(0.5f, p.capacity(Cap.METABOLISM), EPS)
        g.destroy(p, p.parts(PartTag.STOMACH).first())
        assertEquals(0f, p.capacity(Cap.METABOLISM), 0f)
        assertTrue("a lost stomach is not fatal in itself", p.alive)
    }

    @Test fun talkingIsTheJaw() {
        assertEquals(0.4f, HealthRules.talking(0.4f), 0f)
    }
}

// ====================================================================== pain, downed, bleeding, death

class InjuryConditionTest {
    @Test fun painRisesWithSeverityAndShrinksOnBiggerBodies() {
        val small = HealthRules.woundPain(DamageKind.CUT.pain, 5f, 1f)
        assertEquals(2 * small, HealthRules.woundPain(DamageKind.CUT.pain, 10f, 1f), 1e-5f)
        assertTrue(HealthRules.woundPain(DamageKind.CUT.pain, 5f, 4f) < small)
        assertTrue("a burn hurts more than a bruise", HealthRules.woundPain(DamageKind.BURN.pain, 5f, 1f) > HealthRules.woundPain(DamageKind.BRUISE.pain, 5f, 1f))
    }

    @Test fun painOnAPawnComesFromTheirWoundsAndNotFromScarsOrMissingParts() {
        val g = healthGame(5301)
        val p = g.colonists.first()
        g.recomputeHealth(p)
        assertEquals(0f, p.pain, 0f)
        p.injuries.add(Injury(0, DamageKind.CUT, 10f))
        g.recomputeHealth(p)
        assertEquals(HealthRules.woundPain(DamageKind.CUT.pain, 10f, 1f), p.pain, 1e-5f)
        p.injuries.clear()
        p.injuries.add(Injury(0, DamageKind.CUT, 10f, scar = true))
        g.recomputeHealth(p)
        assertEquals(0f, p.pain, 0f)
    }

    @Test fun painShockThresholdIs80PercentAndAWimpGoesDownAt30() {
        assertEquals(0.80f, HealthRules.painShockThreshold(false), 0f)
        assertEquals(0.30f, HealthRules.painShockThreshold(true), EPS)
    }

    @Test fun downedWhenUnconsciousIncapacitatedOrInShock() {
        val shock = 0.8f
        assertFalse(HealthRules.shouldBeDowned(1f, 1f, 0f, shock))
        assertTrue("consciousness below 30%", HealthRules.shouldBeDowned(0.29f, 1f, 0f, shock))
        assertFalse("30% exactly is still awake", HealthRules.shouldBeDowned(0.30f, 1f, 0f, shock))
        assertTrue("moving at 15% or less", HealthRules.shouldBeDowned(1f, 0.15f, 0f, shock))
        assertFalse(HealthRules.shouldBeDowned(1f, 0.16f, 0f, shock))
        assertTrue("pain at the shock threshold", HealthRules.shouldBeDowned(1f, 1f, 0.8f, shock))
        assertFalse(HealthRules.shouldBeDowned(1f, 1f, 0.79f, shock))
    }

    @Test fun aPawnGetsBackUpAsSoonAsNothingKeepsThemDown() {
        val g = healthGame(5302)
        val p = g.colonists.first()
        g.recomputeHealth(p)
        p.downed = true
        g.maybeStandUp(p)
        assertFalse(p.downed)
        p.downed = true
        p.pain = 0.85f
        g.maybeStandUp(p)
        assertTrue("still in shock", p.downed)
    }

    @Test fun bleedingIsStoppedByTendingAndFadesByClotting() {
        assertEquals(1e-5f, HealthRules.bleedRate(1e-5f, false, 0f), 0f)
        assertEquals("a perfect tend leaves nothing", 0f, HealthRules.bleedRate(1e-5f, true, 1f), 0f)
        assertEquals("a poor tend leaves a trickle", 1e-5f * 0.5f * 0.06f, HealthRules.bleedRate(1e-5f, true, 0.5f), 1e-9f)
        val untended = HealthRules.clot(1e-5f, false, 1000f)
        val tended = HealthRules.clot(1e-5f, true, 1000f)
        assertTrue(untended < 1e-5f && tended < untended)
        assertEquals("never more than half per step", 0.5e-5f, HealthRules.clot(1e-5f, true, 100_000f), 1e-9f)
    }

    @Test fun lostBloodComesBackSlowerWhenHungry() {
        assertTrue(HealthRules.bloodRecoveryPerDay(true) > HealthRules.bloodRecoveryPerDay(false))
    }

    @Test fun woundsMakeBlood_OneHundredPercentBloodLossKills() {
        val g = healthGame(5303)
        val p = g.colonists.first()
        g.woundPart(p, 0, DamageKind.CUT, 20f)
        assertTrue(p.bleeding > 0f)
        p.bloodLoss = 0.99f
        g.checkDeath(p, null)
        assertTrue(p.alive)
        p.bloodLoss = 1f
        g.checkDeath(p, null)
        assertTrue(p.dead)
        assertTrue(g.graveyard.last(), g.graveyard.last().contains("blood loss"))
    }

    @Test fun infectionKillsAt100Percent() {
        val g = healthGame(5304)
        val p = g.colonists.first()
        p.injuries.add(Injury(0, DamageKind.CUT, 5f, infection = 0.99f))
        g.checkDeath(p, null)
        assertTrue(p.alive)
        p.injuries.first().infection = 1f
        g.checkDeath(p, null)
        assertTrue(p.dead)
    }

    @Test fun anAwakeColonistWithABigWoundIsNotDead() {
        val g = healthGame(5305)
        val p = g.colonists.first()
        g.woundPart(p, 0, DamageKind.BRUISE, 30f)
        assertTrue(p.alive)
        assertFalse(p.downed)
    }
}

// ====================================================================== infection

class InfectionRuleTest {
    @Test fun infectionStartsRarerWithCareAndInTheHospital() {
        val open = HealthRules.infectionChance(0.2f, 10, indoors = false, hospital = false, tended = false, tendQuality = 0f)
        val indoors = HealthRules.infectionChance(0.2f, 10, indoors = true, hospital = false, tended = false, tendQuality = 0f)
        val hospital = HealthRules.infectionChance(0.2f, 10, indoors = true, hospital = true, tended = false, tendQuality = 0f)
        val tended = HealthRules.infectionChance(0.2f, 10, indoors = true, hospital = false, tended = true, tendQuality = 0.8f)
        assertEquals("outdoors is 1.5 times indoors", 1.5f, open / indoors, EPS)
        assertEquals("a hospital cuts it to 30%", 0.3f, hospital / indoors, EPS)
        assertEquals("tending at 0.8 leaves 10%", 0.1f, tended / indoors, EPS)
        assertEquals("a clean kind never infects", 0f, HealthRules.infectionChance(0f, 10, false, false, false, 0f), 0f)
    }

    @Test fun infectionGrowthFightsImmunityAndGoodCareTipsTheBalance() {
        val dt = TICKS_PER_DAY
        assertEquals(1.1f, HealthRules.infectionGrowth(0f, dt), EPS)
        assertEquals(0.9f, HealthRules.immuneGain(0f, false, dt), EPS)
        assertTrue("untreated, the infection outgrows the immune response", HealthRules.infectionGrowth(0f, dt) > HealthRules.immuneGain(0f, false, dt))
        assertTrue("with excellent care and rest the immune response wins", HealthRules.infectionGrowth(1f, dt) < HealthRules.immuneGain(1f, true, dt))
    }

    @Test fun tendingAnInfectedWoundKnocksItBack() {
        val g = healthGame(5401)
        val p = g.colonists.first()
        g.hediffsClear(p)
        p.injuries.add(Injury(0, DamageKind.CUT, 6f, infection = 0.6f))
        g.tendPawn(null, p, ItemType.MEDS_INDUSTRIAL)
        val inj = p.injuries.first()
        assertTrue(inj.infection < 0.6f)
        assertEquals(0.6f - HealthRules.TEND_INFECTION_CURE * inj.tendQuality, inj.infection, 1e-5f)
    }

    private fun Game.hediffsClear(p: Pawn) { p.hediffs.clear(); p.injuries.clear() }
}

// ====================================================================== healing rules: one test per rule

class HealingRuleTest {
    private val day = TICKS_PER_DAY

    private fun heal(
        tended: Boolean = false, q: Float = 0f, kind: DamageKind = DamageKind.CUT, bed: Boolean = false, hospital: Boolean = false, food: Float = 0.8f,
    ) = HealthRules.healPerDay(tended, q, kind, bed, hospital, food)

    @Test fun untendedWoundsHealAtTheBaseRate() = assertEquals(3.2f, heal(), 0f)

    @Test fun tendedWoundsHealFasterWithBetterTending() {
        assertEquals(8f * 0.55f, heal(tended = true, q = 0f), EPS)
        assertEquals(8f * 1.55f, heal(tended = true, q = 1f), EPS)
        assertTrue(heal(tended = true, q = 0.5f) > heal())
    }

    @Test fun restingInABedSpeedsHealing() = assertEquals(1.35f, heal(bed = true) / heal(), EPS)

    @Test fun aHospitalBedSpeedsHealingFurther() = assertEquals(1.15f, heal(hospital = true) / heal(), EPS)

    @Test fun bruisesHealFasterAndBurnsSlower() {
        assertEquals(1.5f, heal(kind = DamageKind.BRUISE) / heal(), EPS)
        assertEquals(0.7f, heal(kind = DamageKind.BURN) / heal(), EPS)
    }

    @Test fun hungerSlowsHealing() = assertEquals(0.3f, heal(food = 0.01f) / heal(), EPS)

    @Test fun smallWoundsAreGoneBelowTheHealedSeverity() = assertEquals(0.3f, HealthRules.HEALED_SEVERITY, 0f)

    @Test fun bedRestAndHospitalStackOnAPawn() {
        val g = healthGame(5501)
        val a = g.colonists[0]; val b = g.colonists[1]
        for (p in listOf(a, b)) { p.injuries.clear(); p.injuries.add(Injury(0, DamageKind.BRUISE, 8f)); p.food = 0.8f; g.recomputeHealth(p) }
        // Same wound, but b sleeps in a hospital bed.
        val bed = Building(BuildDef.HOSPITAL_BED, g.homeX + 3, g.homeY + 3, true)
        g.map.setBuilding(bed)
        b.bedId = g.map.idx(bed.x, bed.y)
        b.job = Job(JobType.SLEEP, b.x, b.y)
        g.healthTick(a, TICKS_PER_DAY / 4)
        g.healthTick(b, TICKS_PER_DAY / 4)
        val ratio = (8f - b.injuries.first().severity) / (8f - a.injuries.first().severity)
        assertEquals(1.35f * 1.15f, ratio, 0.02f)
    }

    @Test fun sharpWoundsSometimesLeaveAScarThatDoesNotWeakenThePart() {
        val g = healthGame(5502)
        val p = g.colonists.first()
        p.injuries.clear()
        repeat(400) { p.injuries.add(Injury(0, DamageKind.CUT, 0.2f)) }
        g.healthTick(p, 10)
        val scars = p.injuries.count { it.scar }
        assertTrue("about 18% scar, got $scars of 400", scars in 40..110)
        assertTrue("the rest simply heal", p.injuries.all { it.scar })
        assertEquals("a scar is permanent and free", 0f, p.partDamage(0), 0f)
        assertTrue(p.injuries.all { it.permanent })
    }

    @Test fun bruisesNeverScar() {
        val g = healthGame(5503)
        val p = g.colonists.first()
        p.injuries.clear()
        repeat(200) { p.injuries.add(Injury(0, DamageKind.BRUISE, 0.2f)) }
        g.healthTick(p, 10)
        assertTrue(p.injuries.isEmpty())
    }
}

// ====================================================================== medicine and tending

class TendingRuleTest {
    @Test fun doctorSkillFollowsTheDocumentedCurve() {
        val ref = mapOf(0 to 0.20f, 6 to 0.80f, 10 to 1.10f, 18 to 1.50f, 20 to 1.55f)
        for ((lvl, q) in ref) assertEquals("level $lvl", q, HealthRules.tendSkillStat(lvl), EPS)
        assertEquals("between anchors it interpolates", 0.5f, HealthRules.tendSkillStat(3), EPS)
        assertEquals(0.2f, HealthRules.tendSkillStat(-4), 0f)
        assertEquals(1.55f, HealthRules.tendSkillStat(30), 0f)
        for (l in 0 until 20) assertTrue(HealthRules.tendSkillStat(l + 1) > HealthRules.tendSkillStat(l))
    }

    private fun q(skill: Int = 6, manip: Float = 1f, potency: Float = 1f, max: Float = 1f, hospital: Boolean = false, self: Boolean = false, roll: Float = 0.5f) =
        HealthRules.tendQuality(skill, manip, potency, max, hospital, self, roll)

    @Test fun tendQualityIsSkillTimesPotency() {
        assertEquals(0.8f, q(), EPS)
        assertEquals(0.8f * 0.6f, q(potency = 0.6f), EPS)
    }

    @Test fun aHospitalBedAddsAFlatBonus() = assertEquals(0.1f, q(hospital = true) - q(), EPS)

    @Test fun tendingYourselfIsWorse() = assertEquals(0.7f, q(self = true) / q(), EPS)

    @Test fun luckSwingsQualityByAQuarterEitherWay() {
        assertEquals(0.75f, q(roll = 0f) / q(), EPS)
        assertEquals(1.25f, q(roll = 1f) / q(), EPS)
    }

    @Test fun clumsyHandsTendWorse() = assertTrue(q(manip = 0.3f) < q())

    @Test fun medicineCapsHowGoodATendCanBe() {
        assertEquals(0.7f, q(skill = 20, potency = 0.6f, max = 0.7f), 0f)
        assertEquals(1f, q(skill = 20, max = 1f), 0f)
        assertEquals(0f, q(skill = 0, potency = 0f), 0f)
    }

    @Test fun medicineItemsCarryTheirPotencyAndCap() {
        assertEquals(1.0f, ItemType.MEDS_INDUSTRIAL.potency, 0f)
        assertEquals(1.0f, ItemType.MEDS_INDUSTRIAL.maxTendQuality, 0f)
        assertEquals(0.6f, ItemType.MEDS_HERBAL.potency, 0f)
        assertEquals(0.7f, ItemType.MEDS_HERBAL.maxTendQuality, 0f)
    }

    @Test fun herbalIsWorseThanIndustrialAndBothBeatBareHands() {
        val g = healthGame(5601)
        val doc = g.colonists[1]
        doc.skill[SkillType.MEDICINE.ordinal] = 8
        g.recomputeHealth(doc)
        fun avg(med: ItemType?) = (0 until 200).map { g.tendQuality(doc, med, false) }.average()
        val none = avg(null); val herbal = avg(ItemType.MEDS_HERBAL); val industrial = avg(ItemType.MEDS_INDUSTRIAL)
        assertTrue("none $none < herbal $herbal", none < herbal)
        assertTrue("herbal $herbal < industrial $industrial", herbal < industrial)
    }

    @Test fun tendQualityIsDeterministicForAFixedSeed() {
        fun run(): List<Float> {
            val g = healthGame(5602)
            val doc = g.colonists[1]
            return (0 until 20).map { g.tendQuality(doc, ItemType.MEDS_INDUSTRIAL, it % 2 == 0) }
        }
        assertEquals(run(), run())
    }

    @Test fun aBetterDoctorTendsBetterOnAPatient() {
        val results = listOf(2, 14).map { skill ->
            val g = healthGame(5603)
            val doc = g.colonists[1]; val patient = g.colonists[0]
            doc.skill[SkillType.MEDICINE.ordinal] = skill
            g.recomputeHealth(doc)
            g.woundPart(patient, 0, DamageKind.CUT, 8f)
            g.tendPawn(doc, patient, ItemType.MEDS_INDUSTRIAL)
            patient.injuries.first { !it.scar }.tendQuality
        }
        assertTrue("${results[0]} < ${results[1]}", results[0] < results[1])
    }

    @Test fun selfTendingUsesTheSelfPenalty() {
        val own = healthGame(5604)
        val p = own.colonists[0]
        p.skill[SkillType.MEDICINE.ordinal] = 4
        own.recomputeHealth(p)
        val self = own.tendQuality(p, ItemType.MEDS_INDUSTRIAL, false, self = true)
        val other = healthGame(5604)
        val q2 = other.colonists[0]
        q2.skill[SkillType.MEDICINE.ordinal] = 4
        other.recomputeHealth(q2)
        val away = other.tendQuality(q2, ItemType.MEDS_INDUSTRIAL, false, self = false)
        assertEquals(0.7f, self / away, EPS)
    }

    @Test fun tendingMarksEveryWoundAndIllnessTended() {
        val g = healthGame(5605)
        val p = g.colonists[0]
        g.woundPart(p, 0, DamageKind.CUT, 6f)
        g.woundPart(p, p.race.body.indexOfFirst { it.tag == PartTag.LEG }, DamageKind.CUT, 6f)
        g.addHediff(p, HediffKind.FLU, 0.3f)
        assertTrue(p.untended)
        g.tendPawn(g.colonists[1], p, ItemType.MEDS_HERBAL)
        assertFalse(p.untended)
        assertTrue(p.injuries.filter { !it.scar }.all { it.tended && it.tendQuality > 0f })
        assertTrue(p.hediff(HediffKind.FLU)!!.tended)
    }

    @Test fun aHigherQualityTendReplacesALowerOneButNotViceVersa() {
        val g = healthGame(5606)
        val p = g.colonists[0]
        g.woundPart(p, 0, DamageKind.CUT, 6f)
        val inj = p.injuries.first()
        inj.tended = true; inj.tendQuality = 0.99f
        g.tendPawn(null, p, ItemType.MEDS_HERBAL)
        assertEquals(0.99f, inj.tendQuality, 0f)
    }
}

// ====================================================================== surgery, prosthetics, scars, lost limbs

class SurgeryRuleTest {
    @Test fun amputatingALimbRemovesItAndLeavesATendedStump() {
        val g = healthGame(5701)
        val p = g.colonists.first()
        val arm = p.parts(PartTag.ARM).first()
        g.applySurgery(p, SurgeryOrder(SurgeryKind.AMPUTATE, arm, null))
        assertTrue(p.partMissing(arm))
        assertEquals(0f, p.partEff(arm), 0f)
        val stump = p.injuries.first { it.part == arm && it.missing }
        assertTrue(stump.tended && stump.permanent)
        g.recomputeHealth(p)
        assertTrue("one arm fewer weakens manipulation", p.capacity(Cap.MANIPULATION) < 1f)
    }

    @Test fun aPegLegRestoresPartOfWhatAmputationTook() {
        val g = healthGame(5702)
        val p = g.colonists.first()
        val leg = p.parts(PartTag.LEG).first()
        g.applySurgery(p, SurgeryOrder(SurgeryKind.AMPUTATE, leg, null))
        g.recomputeHealth(p)
        val without = p.capacity(Cap.MOVING)
        g.applySurgery(p, SurgeryOrder(SurgeryKind.INSTALL, leg, Implant.PEG_LEG))
        g.recomputeHealth(p)
        assertFalse(p.partMissing(leg))
        assertEquals(Implant.PEG_LEG.eff, p.partEff(leg), 0f)
        assertTrue(p.capacity(Cap.MOVING) > without)
        assertTrue("a peg leg is worse than the leg it replaced", p.capacity(Cap.MOVING) < 1f)
    }

    @Test fun bionicPartsBeatNaturalOnes() {
        val g = healthGame(5703)
        val p = g.colonists.first()
        val eye = p.parts(PartTag.EYE).first()
        g.applySurgery(p, SurgeryOrder(SurgeryKind.INSTALL, eye, Implant.BIONIC_EYE))
        g.recomputeHealth(p)
        assertTrue(p.partEff(eye) > 1f)
        assertTrue(p.capacity(Cap.SIGHT) > 1f - 1e-4f)
    }

    @Test fun surgeryOffersOnlyBaseGameOperationsAndNeedsResearchForImplants() {
        val g = healthGame(5704)
        val p = g.colonists.first()
        val offers = g.availableSurgeries(p)
        assertTrue("amputations are always offered", offers.any { it.kind == SurgeryKind.AMPUTATE })
        assertTrue("no implants without research", offers.none { it.kind == SurgeryKind.INSTALL })
        val leg = p.parts(PartTag.LEG).first()
        g.applySurgery(p, SurgeryOrder(SurgeryKind.AMPUTATE, leg, null))
        assertTrue("peg leg is gated behind prosthetics research", g.availableSurgeries(p).none { it.implant == Implant.PEG_LEG })
        g.researchDone.add(Research.BIONICS_BASIC)
        assertTrue(g.availableSurgeries(p).any { it.kind == SurgeryKind.INSTALL && it.implant == Implant.PEG_LEG && it.part == leg })
    }

    @Test fun noOperationIsOfferedForAnimalsOrMachines() {
        val g = healthGame(5705)
        val p = g.colonists.first()
        assertNotNull(p)
        val dog = Pawn(9999, "Dog", Race.HUSKY, Faction.PLAYER)
        assertTrue(g.availableSurgeries(dog).isEmpty())
    }
}

// ====================================================================== the whole arc, with a fixed seed

class InjuryToRecoveryTest {
    private class Outcome(
        val downedAfterInjury: Boolean, val standUpTick: Int, val finalBlood: Float, val finalWounds: Int,
        val finalMoving: Float, val alive: Boolean, val tendQuality: Float, val downedTwice: Boolean,
    )

    private fun scenario(seed: Long): Outcome {
        val g = healthGame(seed)
        val p = g.colonists[0]
        val doc = g.colonists[1]
        doc.skill[SkillType.MEDICINE.ordinal] = 8
        g.recomputeHealth(doc)
        p.food = 0.9f
        // Two crushed legs and a cut to the torso: the patient cannot walk and is bleeding.
        for (leg in p.parts(PartTag.LEG)) g.woundPart(p, leg, DamageKind.BRUISE, p.partMax(leg) * 0.88f)
        g.woundPart(p, 0, DamageKind.CUT, 6f)
        g.recomputeHealth(p)
        assertFalse("fresh wounds do not down anyone until the body reacts", p.downed)
        g.healthTick(p, 10)
        val downed = p.downed
        assertTrue("moving is ${p.capacity(Cap.MOVING)}", p.capacity(Cap.MOVING) <= HealthRules.MOVING_MIN)
        val bleedingBefore = p.bleeding
        g.tendPawn(doc, p, ItemType.MEDS_INDUSTRIAL)
        val q = p.injuries.first { !it.scar && it.kind == DamageKind.CUT }.tendQuality
        assertTrue("tending slows the bleeding", p.bleeding < bleedingBefore)
        var standUp = -1
        var downedAgain = false
        var t = 0
        while (t < 15 * TICKS_PER_DAY) {
            g.healthTick(p, 10)
            if (p.dead) break
            if (p.downed) { g.maybeStandUp(p); if (!p.downed && standUp < 0) standUp = t }
            else if (standUp >= 0 && p.pain >= HealthRules.PAIN_SHOCK_DEFAULT) downedAgain = true
            t += 10
        }
        return Outcome(downed, standUp, p.bloodLoss, p.injuries.count { !it.scar && !it.missing }, p.capacity(Cap.MOVING), p.alive, q, downedAgain)
    }

    @Test fun anInjuredPawnIsDownedThenTendedThenRecovers() {
        val o = scenario(5801)
        assertTrue("downed by the injury", o.downedAfterInjury)
        assertTrue("alive at the end", o.alive)
        assertTrue("stood back up on their own legs", o.standUpTick >= 0)
        assertFalse("did not collapse again", o.downedTwice)
        assertEquals("every wound healed", 0, o.finalWounds)
        assertEquals("blood is back", 0f, o.finalBlood, 0.01f)
        assertTrue("walks normally again: ${o.finalMoving}", o.finalMoving > 0.95f)
    }

    @Test fun theSameSeedGivesTheSameRecovery() {
        val a = scenario(5802)
        val b = scenario(5802)
        assertEquals(a.standUpTick, b.standUpTick)
        assertEquals(a.tendQuality, b.tendQuality, 0f)
        assertEquals(a.finalBlood, b.finalBlood, 0f)
        assertEquals(a.finalMoving, b.finalMoving, 0f)
    }

    @Test fun withoutAnyTendingAnUntreatedPawnIsWorseOff() {
        val g = healthGame(5803)
        val p = g.colonists[0]
        for (leg in p.parts(PartTag.LEG)) g.woundPart(p, leg, DamageKind.BRUISE, p.partMax(leg) * 0.88f)
        g.woundPart(p, 0, DamageKind.CUT, 12f)
        g.recomputeHealth(p)
        val bleeding = p.bleeding
        g.healthTick(p, 10)
        assertTrue(p.downed)
        assertTrue(bleeding > 0f && p.untended)
    }
}
