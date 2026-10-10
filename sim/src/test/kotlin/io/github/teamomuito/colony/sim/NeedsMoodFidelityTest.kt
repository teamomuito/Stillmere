package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EPS = 0.0001f

/** Values from docs/fidelity/needs-mood.md. Each test names the source it checks. */
class NeedsMoodFidelityTest {

    // ---- Mental-break thresholds (wiki mirrors, 2026-10-10: minor 35%, major 20%, extreme 5%)

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

    @Test fun noBreakAboveTheMinorThreshold() {
        assertEquals(0f, BreakRules.chance(BreakRules.severity(0.5f)), 0f)
        assertEquals(0f, BreakRules.chance(BreakRules.severity(1f)), 0f)
    }

    @Test fun breakChanceRisesAsMoodFalls() {
        assertTrue(BreakRules.chance(0) < BreakRules.chance(1))
        assertTrue(BreakRules.chance(1) < BreakRules.chance(2))
    }

    @Test fun thirtyPercentMoodIsInsideTheMinorBand() {
        // The old minor line was 30%, so 30% used to be safe. The base-game line is 35%.
        assertEquals(0, BreakRules.severity(0.30f))
    }

    // ---- Hunger (wiki mirrors: 1.6 nutrition per day at 100% hunger rate, food bar max 1.0)

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
        // Defs.kt gives a simple meal 0.9 nutrition. At 1.6 per day that is about 1.8 meals, so about two a day.
        val mealsPerDay = NeedRules.HUMAN_NUTRITION_PER_DAY / ItemType.MEAL_SIMPLE.nutrition
        assertTrue(mealsPerDay > 1.5f && mealsPerDay < 2f)
    }
}
