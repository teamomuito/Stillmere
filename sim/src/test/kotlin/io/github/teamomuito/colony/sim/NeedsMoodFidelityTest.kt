package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EPS = 0.0001f

/**
 * Values from docs/fidelity/needs-mood.md. Each test names the source it checks. The reference's per-150-tick deltas are
 * checked by multiplying the per-tick rate back up, so that a per-day figure can be read straight off the test.
 */
class NeedsMoodFidelityTest {

    // ---- Mental-break thresholds (decompiled reference rows 222-223: 0.35, 0.20, 0.05 of the 0-1 mood)

    @Test fun breakThresholdsAreTheBaseGameValues() {
        assertEquals(0.35f, BreakRules.MINOR, 0f)
        assertEquals(0.20f, BreakRules.MAJOR, 0f)
        assertEquals(0.05f, BreakRules.EXTREME, 0f)
    }

    @Test fun severityBandsFollowTheThresholds() {
        assertEquals(-1, BreakRules.severity(0.36f))
        assertEquals("the minor threshold itself is not below it", -1, BreakRules.severity(0.35f))
        assertEquals(0, BreakRules.severity(0.34f))
        assertEquals("the major threshold itself is minor", 0, BreakRules.severity(0.20f))
        assertEquals(1, BreakRules.severity(0.19f))
        assertEquals("the extreme threshold itself is major", 1, BreakRules.severity(0.05f))
        assertEquals(2, BreakRules.severity(0.04f))
        assertEquals(2, BreakRules.severity(0f))
    }

    @Test fun thirtyPercentMoodIsInsideTheMinorBand() {
        // The old minor line was 30%, so 30% used to be safe. The base-game line is 35%.
        assertEquals(0, BreakRules.severity(0.30f))
    }

    // ---- Break timing (Verse.AI/MentalBreaker.cs:22-28, 296-306; the 150-tick checks as one rate per check)

    @Test fun meanTimesAreFourEightTenthsAndAHalfDay() {
        assertEquals(4f, BreakRules.meanDaysBetweenBreaks(0), 0f)
        assertEquals(0.8f, BreakRules.meanDaysBetweenBreaks(1), 0f)
        assertEquals(0.5f, BreakRules.meanDaysBetweenBreaks(2), 0f)
    }

    @Test fun breakCheckChanceIsTheMeanTimeRateOverTheCheckLength() {
        // A 625-tick update at a 4-day mean time: 625 / (4 x 60000).
        assertEquals(625f / (4f * 60000f), BreakRules.chance(0, 625), EPS)
    }

    @Test fun noBreakAboveTheMinorThreshold() {
        assertEquals(0f, BreakRules.chance(BreakRules.severity(0.5f), 625), 0f)
        assertEquals(0f, BreakRules.chance(BreakRules.severity(1f), 625), 0f)
    }

    @Test fun breakChanceRisesAsMoodFalls() {
        assertTrue(BreakRules.chance(0, 625) < BreakRules.chance(1, 625))
        assertTrue(BreakRules.chance(1, 625) < BreakRules.chance(2, 625))
    }

    @Test fun aBreakNeedsTwoThousandTicksBelowAndFifteenThousandAfterTheLast() {
        assertEquals(2000, BreakRules.MIN_TICKS_BELOW)
        assertEquals(15000, BreakRules.COOLDOWN_TICKS)
    }

    // ---- Hunger (decompiled rows 187-190: 1.6 per day; categories fed 1, hungry 0.5, urgently hungry 0.25, starving 0)

    @Test fun baseHumanUsesTheBaseGameRate() {
        assertEquals(1.6f, NeedRules.HUMAN_NUTRITION_PER_DAY, 0f)
        assertEquals(1.6f, NeedRules.humanFoodPerDay(gourmand = false, ascetic = false, pregnant = false, age = 25), EPS)
    }

    @Test fun traitsPregnancyAndChildhoodScaleTheRate() {
        // Multipliers are Stillmere's own (UNVERIFIED), so the test pins them to the code's documented values.
        assertEquals(1.6f * 1.3f, NeedRules.humanFoodPerDay(gourmand = true, ascetic = false, pregnant = false, age = 25), EPS)
        assertEquals(1.6f * 0.9f, NeedRules.humanFoodPerDay(gourmand = false, ascetic = true, pregnant = false, age = 25), EPS)
        assertEquals(1.6f * 1.25f, NeedRules.humanFoodPerDay(gourmand = false, ascetic = false, pregnant = true, age = 25), EPS)
        assertEquals(1.6f * 0.6f, NeedRules.humanFoodPerDay(gourmand = false, ascetic = false, pregnant = false, age = 8), EPS)
    }

    @Test fun aColonistNeedsAboutTwoSimpleMealsADay() {
        // Defs.kt gives a hearth stew 0.9 nutrition. At 1.6 per day that is about 1.8 meals, so about two a day.
        val mealsPerDay = NeedRules.HUMAN_NUTRITION_PER_DAY / ItemType.MEAL_HEARTH_STEW.nutrition
        assertTrue(mealsPerDay > 1.5f && mealsPerDay < 2f)
    }

    @Test fun hungerCategoriesSetTheShareOfFoodUse() {
        assertEquals(1f, NeedRules.hungerMultiplier(0.5f), 0f)
        assertEquals("the hungry line itself is fed", 1f, NeedRules.hungerMultiplier(0.24f), 0f)
        assertEquals(0.5f, NeedRules.hungerMultiplier(0.2f), 0f)
        assertEquals("the urgent line itself is hungry", 0.5f, NeedRules.hungerMultiplier(0.12f), 0f)
        assertEquals(0.25f, NeedRules.hungerMultiplier(0.11f), 0f)
        assertEquals(0f, NeedRules.hungerMultiplier(0f), 0f)
    }

    @Test fun urgentEatInterruptIsTheUrgentlyHungryLevel() {
        assertEquals(0.12f, URGENT_FOOD, 0f)
    }

    @Test fun malnutritionMovesAtTheReferenceRate() {
        // 0.453 per day is 0.0011325 per 150-tick interval (decompiled row 191).
        assertEquals(0.0011325f, NeedRules.MALNUTRITION_PER_DAY / 400f, EPS / 100f)
    }

    // ---- Rest (decompiled rows 194-199)

    @Test fun restFallIsZeroPointNineFiveADayAtRested() {
        // 1.5833333E-05 per tick, which is 0.002375 per 150 ticks (decompiled row 194).
        assertEquals(0.002375f, NeedRules.REST_FALL_PER_DAY / 400f, EPS / 100f)
    }

    @Test fun restFallBandsScaleTheRate() {
        assertEquals(1f, NeedRules.restFallMultiplier(0.5f), 0f)
        assertEquals("tired starts below 0.28", 0.7f, NeedRules.restFallMultiplier(0.27f), 0f)
        assertEquals(0.7f, NeedRules.restFallMultiplier(0.2f), 0f)
        assertEquals("very tired starts below 0.14", 0.3f, NeedRules.restFallMultiplier(0.13f), 0f)
        assertEquals("exhausted starts below 0.01", 0.6f, NeedRules.restFallMultiplier(0.009f), 0f)
        assertEquals(0.6f, NeedRules.restFallMultiplier(0f), 0f)
    }

    @Test fun collapseMeanTimeShortensWithTimeAtZero() {
        assertEquals(0.25f, NeedRules.collapseMtbDays(1001), 0f)
        assertEquals(0.125f, NeedRules.collapseMtbDays(15000), 0f)
        assertEquals(1f / 12f, NeedRules.collapseMtbDays(30000), 1e-6f)
        assertEquals(0.0625f, NeedRules.collapseMtbDays(45000), 0f)
    }

    // ---- Joy (decompiled rows 201-202: per 150 ticks, by band)

    @Test fun joyFallFollowsItsBand() {
        assertEquals("empty", 0.0015f, NeedRules.joyFallPerInterval(0.005f), 0f)
        assertEquals("very low", 0.0006f, NeedRules.joyFallPerInterval(0.1f), 0f)
        assertEquals("low", 0.00105f, NeedRules.joyFallPerInterval(0.2f), 0f)
        assertEquals("satisfied", 0.0015f, NeedRules.joyFallPerInterval(0.5f), 0f)
        assertEquals("extreme", 0.0015f, NeedRules.joyFallPerInterval(0.9f), 0f)
    }

    @Test fun joyFallPerDayAtEachBand() {
        assertEquals(0.24f, NeedRules.joyFallPerInterval(0.1f) * 400f, 1e-5f)
        assertEquals(0.42f, NeedRules.joyFallPerInterval(0.2f) * 400f, 1e-5f)
        assertEquals(0.6f, NeedRules.joyFallPerInterval(0.5f) * 400f, 1e-5f)
    }

    // ---- Comfort, beauty, outdoors and indoors (decompiled rows 213-216)

    @Test fun beautyLevelIsHalfPlusATenthOfRoomBeauty() {
        assertEquals(0.5f, NeedRules.beautyLevel(0f), EPS)
        assertEquals(0.65f, NeedRules.beautyLevel(1.5f), EPS)
        assertEquals(0.35f, NeedRules.beautyLevel(-1.5f), EPS)
        assertEquals(1f, NeedRules.beautyLevel(10f), 0f)
        assertEquals(0f, NeedRules.beautyLevel(-10f), 0f)
    }

    @Test fun comfortBandsFollowTheReferenceLines() {
        assertEquals(NeedRules.ComfortBand.BARE, NeedRules.comfortBand(0.05f))
        assertEquals(NeedRules.ComfortBand.MODEST, NeedRules.comfortBand(0.1f))
        assertEquals(NeedRules.ComfortBand.COSY, NeedRules.comfortBand(0.65f))
        assertEquals(NeedRules.ComfortBand.SNUG, NeedRules.comfortBand(0.75f))
        assertEquals(NeedRules.ComfortBand.PLUSH, NeedRules.comfortBand(0.85f))
        assertEquals(NeedRules.ComfortBand.OPULENT, NeedRules.comfortBand(0.95f))
    }

    @Test fun outdoorsNeedRisesOutsideAndFallsUnderARoof() {
        assertEquals("outside, no roof, per 150 ticks", 0.02f, NeedRules.outdoorsDelta(outdoor = true, roofed = false, inBed = false), EPS)
        assertEquals("outside under rock", -0.001f, NeedRules.outdoorsDelta(outdoor = true, roofed = true, inBed = false), EPS)
        assertEquals("indoors under a roof", -0.001125f, NeedRules.outdoorsDelta(outdoor = false, roofed = true, inBed = false), EPS)
    }

    @Test fun outdoorsFallIsAFifthInBed() {
        assertEquals(-0.001f * 0.2f, NeedRules.outdoorsDelta(outdoor = true, roofed = true, inBed = true), EPS)
        assertEquals("a rise is not reduced in bed", 0.02f, NeedRules.outdoorsDelta(outdoor = true, roofed = false, inBed = true), EPS)
    }

    @Test fun outdoorsFloorIsTwoTenthsOutsideAndNothingIndoors() {
        assertEquals(0.2f, NeedRules.outdoorsFloor(outdoor = true, roofed = true), 0f)
        assertEquals(0.2f, NeedRules.outdoorsFloor(outdoor = true, roofed = false), 0f)
        assertEquals(0f, NeedRules.outdoorsFloor(outdoor = false, roofed = true), 0f)
    }

    @Test fun indoorsNeedRisesUnderARoofAndFallsOutside() {
        assertEquals(0.005f, NeedRules.indoorsDelta(outdoor = false, roofed = true), EPS)
        assertEquals("outside under rock is not a fall", 0f, NeedRules.indoorsDelta(outdoor = true, roofed = true), 0f)
        assertEquals(-0.000625f, NeedRules.indoorsDelta(outdoor = true, roofed = false), EPS)
    }

    @Test fun aFallStopsAtItsFloorAndALevelBelowItStays() {
        assertEquals(0.2f, NeedRules.stepNeed(0.3f, -0.1f, 0.2f), EPS)
        assertEquals("already below the floor, it does not drop further", 0.1f, NeedRules.stepNeed(0.1f, -0.1f, 0.2f), EPS)
        assertEquals("a rise stops at 1", 1f, NeedRules.stepNeed(0.95f, 0.1f), 0f)
        assertEquals(0.49f, NeedRules.stepNeed(0.5f, -0.01f), EPS)
    }

    // ---- Cold and hot stages (decompiled row 209: stages at 10, 20 and 30 degrees from the comfort limit)

    @Test fun thermalStagesStepAtTenTwentyAndThirty() {
        assertEquals(0, NeedRules.thermalStage(0.5f))
        assertEquals(0, NeedRules.thermalStage(9.9f))
        assertEquals(1, NeedRules.thermalStage(10f))
        assertEquals(1, NeedRules.thermalStage(19.9f))
        assertEquals(2, NeedRules.thermalStage(20f))
        assertEquals(2, NeedRules.thermalStage(29.9f))
        assertEquals(3, NeedRules.thermalStage(30f))
    }

    // ---- Drugs (decompiled rows 217-220)

    @Test fun addictionChanceRisesWithToleranceAlongTheReferenceCurve() {
        assertEquals("no tolerance, no change", 1f, DrugRules.addictionMultiplier(0f), 0f)
        assertEquals(3f, DrugRules.addictionMultiplier(0.25f), EPS)
        assertEquals(5f, DrugRules.addictionMultiplier(0.5f), EPS)
        assertEquals(10f, DrugRules.addictionMultiplier(0.65f), EPS)
        assertEquals(15f, DrugRules.addictionMultiplier(0.8f), EPS)
        assertEquals("flat above 0.8", 15f, DrugRules.addictionMultiplier(1f), 0f)
    }

    @Test fun addictionFallIsHalfADayAndWithdrawalAtOnePercent() {
        assertEquals(0.5f, DrugRules.ADDICTION_FALL_PER_DAY, 0f)
        assertEquals(0.01f, DrugRules.WITHDRAWAL_LEVEL, 0f)
    }

    @Test fun beerAddictionIsAnAddictionKindThatFalls() {
        // Regression: the decay used to match by name suffix, which beer's kind does not have.
        assertTrue(HediffKind.BREW_DEPENDENCE in DrugRules.ADDICTIONS)
    }

    @Test fun dawnTabletsNeverAddict() {
        assertTrue(ItemType.DAWN_TABLET !in DrugRules.ADDICTIVE)
        assertTrue(ItemType.BEER in DrugRules.ADDICTIVE)
    }

    // ---- Game-level checks of the rules above

    @Test fun aPawnAtZeroRestCollapsesAfterTheWaitAndNotBefore() {
        val g = Game(4242L)
        g.startNewColony(Scenario.QUIET_ENCLAVE)
        val c = g.colonists.first()
        c.job = null; c.rest = 0f; c.ticksAtZeroRest = 0
        g.restCollapseTick(c)
        assertEquals("one slow update is inside the wait", 625, c.ticksAtZeroRest)
        assertTrue("no collapse inside the wait", c.job?.type != JobType.SLEEP)
        c.ticksAtZeroRest = 2000
        var fell = false
        repeat(500) {
            if (!fell) { c.rest = 0f; g.restCollapseTick(c); fell = c.job?.type == JobType.SLEEP }
        }
        assertTrue("a long time at zero rest ends in sleep", fell)
    }

    @Test fun restingClearsTheZeroRestCounter() {
        val g = Game(4242L)
        g.startNewColony(Scenario.QUIET_ENCLAVE)
        val c = g.colonists.first()
        c.job = null; c.rest = 0.5f; c.ticksAtZeroRest = 1900
        g.restCollapseTick(c)
        assertEquals(0, c.ticksAtZeroRest)
    }

    @Test fun beerAddictionFallsAndCravingStartsAtTheWithdrawalLevel() {
        val g = Game(4242L)
        g.startNewColony(Scenario.QUIET_ENCLAVE)
        val c = g.colonists.first()
        val h = g.addHediff(c, HediffKind.BREW_DEPENDENCE, 0.02f)
        g.comfortTick(c)
        assertTrue("it falls", h.severity < 0.02f)
        assertNull("not yet in withdrawal", c.hediff(HediffKind.CRAVING_ACHE))
        h.severity = 0.01f
        g.comfortTick(c)
        assertNotNull("withdrawal at 1 percent starts the craving", c.hediff(HediffKind.CRAVING_ACHE))
    }

    @Test fun toleranceWearsOffSlowly() {
        val g = Game(4242L)
        g.startNewColony(Scenario.QUIET_ENCLAVE)
        val c = g.colonists.first()
        c.drugTolerance[ItemType.BEER] = 0.1f
        repeat(200) { g.comfortTick(c) }
        val left = c.drugTolerance[ItemType.BEER]
        assertNotNull("a small tolerance is still there after 200 updates", left)
        assertTrue("and it has fallen", left!! < 0.1f && left > 0f)
    }

    // ---- Craving: the cravings of all addictions in withdrawal are summed before one keep-or-remove rule is applied

    /** The craving severity after one update, with two addictions added in the given order, or null when there is none. */
    private fun cravingAfterOneUpdate(first: Pair<HediffKind, Float>, second: Pair<HediffKind, Float>): Float? {
        val g = Game(4242L)
        g.startNewColony(Scenario.QUIET_ENCLAVE)
        val c = g.colonists.first()
        c.hediffs.removeAll { it.kind in DrugRules.ADDICTIONS }
        g.addHediff(c, first.first, first.second)
        g.addHediff(c, second.first, second.second)
        g.comfortTick(c)
        return c.hediff(HediffKind.CRAVING_ACHE)?.severity
    }

    @Test fun twoAddictionsInWithdrawalGiveTheSameCravingInEitherOrder() {
        val a = HediffKind.BREW_DEPENDENCE to 0.005f
        val b = HediffKind.LUCID_ADDICTION to 0.005f
        val ab = cravingAfterOneUpdate(a, b)
        val ba = cravingAfterOneUpdate(b, a)
        assertNotNull("two addictions in withdrawal give a craving", ab)
        assertEquals("the two cravings are summed", 0.8f, ab!!, EPS)
        assertEquals("the same whichever hediff comes first", ab, ba!!, EPS)
    }

    @Test fun anAddictionOutOfWithdrawalDoesNotRemoveAnotherCravingInEitherOrder() {
        val inWithdrawal = HediffKind.BREW_DEPENDENCE to 0.005f
        val notYet = HediffKind.LUCID_ADDICTION to 0.5f
        val first = cravingAfterOneUpdate(inWithdrawal, notYet)
        val second = cravingAfterOneUpdate(notYet, inWithdrawal)
        assertNotNull("the craving from the addiction in withdrawal stays", first)
        assertEquals("one craving", 0.4f, first!!, EPS)
        assertEquals("the same whichever hediff comes first", first, second!!, EPS)
    }

    @Test fun noCravingWhenNoAddictionIsInWithdrawalInEitherOrder() {
        val a = HediffKind.BREW_DEPENDENCE to 0.5f
        val b = HediffKind.LUCID_ADDICTION to 0.5f
        assertNull(cravingAfterOneUpdate(a, b))
        assertNull(cravingAfterOneUpdate(b, a))
    }

    // ---- In bed: the outdoors need and the rest gain use one test. A pawn given a bed is in bed. A collapsed or downed
    // pawn is not, even on a bed cell.

    /** A colonist on a roofed cell, so the outdoors need falls and the bed factor shows. */
    private fun roofedColonist(): Pair<Game, Pawn> {
        val g = Game(4242L)
        g.startNewColony(Scenario.QUIET_ENCLAVE)
        val c = g.colonists.first()
        g.map.natRoof[g.map.idx(c.x, c.y)] = true
        return g to c
    }

    /** The change in the outdoors need over one update, starting from 0.9. */
    private fun outdoorsChangeOverOneUpdate(g: Game, c: Pawn): Float {
        c.outdoorsNeed = 0.9f
        g.comfortTick(c)
        return c.outdoorsNeed - 0.9f
    }

    @Test fun aPawnInBedFallsOutdoorsAFifthAsFastAsACollapsedPawn() {
        val (g, c) = roofedColonist()
        g.map.setBuilding(Building(BuildDef.BED, c.x, c.y, true))
        c.job = Job(JobType.SLEEP, c.x, c.y).also { it.stage = 1; it.amount = 1 }
        assertTrue("given the bed and in it", g.isInBed(c))
        val inBed = outdoorsChangeOverOneUpdate(g, c)
        g.startSleepInPlace(c)
        assertFalse("collapsed on the same bed cell, not in bed", g.isInBed(c))
        val collapsed = outdoorsChangeOverOneUpdate(g, c)
        assertTrue("the roof makes the need fall", collapsed < 0f)
        assertEquals("the bed factor only for the pawn in bed", 0.2f * collapsed, inBed, 1e-6f)
    }

    @Test fun aDownedPawnOnABedCellGetsNoBedFactor() {
        val (g, c) = roofedColonist()
        val bed = Building(BuildDef.BED, c.x, c.y, true)
        c.downed = true
        c.job = null
        val noBed = outdoorsChangeOverOneUpdate(g, c)
        assertTrue("the roof makes the need fall", noBed < 0f)
        g.map.setBuilding(bed)
        assertFalse("downed with no job on a bed cell is not in bed", g.isInBed(c))
        assertEquals("the same fall as with no bed", noBed, outdoorsChangeOverOneUpdate(g, c), 1e-6f)
        g.startSleepInPlace(c)
        assertFalse("a downed pawn given a collapse job is not in bed either", g.isInBed(c))
        assertEquals("still the same fall", noBed, outdoorsChangeOverOneUpdate(g, c), 1e-6f)
    }

    @Test fun aCollapsedPawnRestsOnTheGroundRateEvenOnABed() {
        val (g, c) = roofedColonist()
        g.map.setBuilding(Building(BuildDef.BED, c.x, c.y, true))
        g.startSleepInPlace(c)
        c.rest = 0.5f
        g.driveJob(c)
        assertEquals("the ground rate, the same test as the outdoors need", 0.00007f / TIME_SCALE, c.rest - 0.5f, 1e-7f)
    }

    @Test fun aPawnGivenABedRestsAtTheBedRate() {
        val (g, c) = roofedColonist()
        g.map.setBuilding(Building(BuildDef.BED, c.x, c.y, true))
        c.job = Job(JobType.SLEEP, c.x, c.y).also { it.stage = 1; it.amount = 1 }
        c.rest = 0.5f
        g.driveJob(c)
        assertEquals("the bed rate at the bed's comfort", (0.00010f + BuildDef.BED.comfort * 0.00004f) / TIME_SCALE, c.rest - 0.5f, 1e-7f)
    }

    // ---- A REST job falls while the pawn walks to its bed (stage 0) and only gains once it lies down (stage 1).
    // The drain is taken in the need tick before the job driver runs, so one game step shows it at any distance.

    @Test fun aPawnWalkingToABedOnARestJobStillLosesRest() {
        val g = Game(4242L)
        g.startNewColony(Scenario.QUIET_ENCLAVE)
        val c = g.colonists.first()
        val bx = minOf(c.x + 3, g.map.w - 1)
        g.map.setBuilding(Building(BuildDef.BED, bx, c.y, true))
        c.job = Job(JobType.REST, bx, c.y).also { it.stage = 0; it.amount = 1 }
        c.rest = 0.5f
        g.step()
        assertEquals("the awake fall in the Rested band, with no gain on the way", -0.95f / TICKS_PER_DAY, c.rest - 0.5f, 1e-7f)
    }

    @Test fun aPawnWalkingToABedOnARestJobLosesRestOnEveryStepUntilItLiesDown() {
        val g = Game(4242L)
        g.startNewColony(Scenario.QUIET_ENCLAVE)
        val c = g.colonists.first()
        val bx = minOf(c.x + 3, g.map.w - 1)
        g.map.setBuilding(Building(BuildDef.BED, bx, c.y, true))
        c.job = Job(JobType.REST, bx, c.y).also { it.stage = 0; it.amount = 1 }
        c.rest = 0.5f
        val startX = c.x
        var steps = 0
        while (c.job?.stage == 0 && steps < 2000) {
            val before = c.rest
            g.step()
            steps++
            assertEquals("step $steps of the walk: the awake fall, no gain", -0.95f / TICKS_PER_DAY, c.rest - before, 1e-7f)
        }
        assertEquals("the pawn lay down on the bed (stage 1)", 1, c.job?.stage)
        assertTrue("it walked, so more than one step passed", steps > 1)
        assertTrue("it moved from where it started", c.x != startX)
        assertEquals("it is on the bed cell", bx, c.x)
        val lying = c.rest
        g.step()
        assertEquals("lying in the bed: the bed rate at the REST job's 0.6, and no fall",
            0.6f * (0.00010f + BuildDef.BED.comfort * 0.00004f) / TIME_SCALE, c.rest - lying, 1e-7f)
    }

    @Test fun aPawnLyingDownOnARestJobGainsRestAndDoesNotFall() {
        val g = Game(4242L)
        g.startNewColony(Scenario.QUIET_ENCLAVE)
        val c = g.colonists.first()
        c.job = Job(JobType.REST).also { it.stage = 1; it.amount = 0 }
        c.rest = 0.5f
        g.step()
        assertEquals("the ground rate at 0.6 for a REST job, and no fall", 0.6f * 0.00007f / TIME_SCALE, c.rest - 0.5f, 1e-7f)
    }
}
