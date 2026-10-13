package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A calm colony: only the timers a test sets can fire. Same setup as IncidentTest. */
private fun threatGame(seed: Long, story: Storyteller = Storyteller.MARLOWE): Game {
    val g = Game(seed)
    g.startNewColony(Scenario.QUIET_ENCLAVE)
    g.mentalBreaksEnabled = false
    g.storyteller = story
    g.pawns.removeAll { it.isAnimal && it.faction == Faction.WILD }
    val never = Long.MAX_VALUE
    g.nextRaid = never; g.nextMisc = never; g.nextWanderer = never; g.nextTrader = never; g.nextPod = never; g.nextTempEvent = never
    return g
}

private fun Game.atDay(d: Int) { tick = d.toLong() * TICKS_PER_DAY }

private const val TWO_DAYS = 2L * TICKS_PER_DAY

class ThreatsTest {

    // ----------------------------------------------------------------------------------- points curves (StorytellerUtility.cs)

    @Test fun wealthPointsFollowTheReferenceCurve() {
        assertEquals(0f, wealthPointsCurve.eval(0f), 0f)
        assertEquals("zero points below 14,000 wealth", 0f, wealthPointsCurve.eval(14000f), 0f)
        assertEquals(2400f, wealthPointsCurve.eval(400000f), 0.01f)
        assertEquals(3600f, wealthPointsCurve.eval(700000f), 0.01f)
        assertEquals(4200f, wealthPointsCurve.eval(1000000f), 0.01f)
        assertEquals("flat beyond the last point", 4200f, wealthPointsCurve.eval(5000000f), 0.01f)
        assertEquals("linear between points", 2400f * 86000f / 386000f, wealthPointsCurve.eval(100000f), 0.01f)
    }

    @Test fun colonistPointsFollowTheReferenceCurve() {
        assertEquals(15f, colonistPointsCurve.eval(0f), 0f)
        assertEquals(15f, colonistPointsCurve.eval(10000f), 0f)
        assertEquals(140f, colonistPointsCurve.eval(400000f), 0.01f)
        assertEquals(200f, colonistPointsCurve.eval(1000000f), 0.01f)
    }

    @Test fun theFloorCeilingRisesFromThirtyFiveAtDayTwelveToSeventyAtDayThirtyFive() {
        assertEquals(35f, pointsFloorCeilingCurve.eval(0f), 0f)
        assertEquals(35f, pointsFloorCeilingCurve.eval(12f), 0f)
        assertEquals(52.5f, pointsFloorCeilingCurve.eval(23.5f), 0.001f)
        assertEquals(70f, pointsFloorCeilingCurve.eval(35f), 0f)
        assertEquals(70f, pointsFloorCeilingCurve.eval(100f), 0f)
    }

    @Test fun thePointsAreHeldBetweenTheFloorAndTheCapOfTenThousand() {
        assertEquals(10000f, POINTS_CAP, 0f)
        assertEquals(40f, clampPoints(10f, 40f), 0f)
        assertEquals(500f, clampPoints(500f, 40f), 0f)
        assertEquals(POINTS_CAP, clampPoints(50000f, 40f), 0f)
    }

    // ----------------------------------------------------------------------------------- inputs

    @Test fun aColonistsPointsDependOnHealthInPartOnly() {
        assertEquals(15f, basePoints(0f, listOf(1f), emptyList()), 0.001f)
        assertEquals("a colonist at no health keeps 35 percent", 15f * 0.35f, basePoints(0f, listOf(0f), emptyList()), 0.001f)
    }

    @Test fun aTamedAnimalAddsAnEighthOfItsCombatPower() {
        // Combat power is Stillmere's stand-in, six times the danger (C, UNVERIFIED).
        assertEquals(0.08f * 6f * 1.5f, basePoints(0f, emptyList(), listOf(AnimalThreat(1.5f, 1f))), 0.0001f)
    }

    @Test fun aTamedAnimalIsBlendedByHealthLikeAColonist() {
        assertEquals("a healthy animal keeps all of its points", 0.08f * 6f * 1.5f, basePoints(0f, emptyList(), listOf(AnimalThreat(1.5f, 1f))), 0.0001f)
        assertEquals("an animal at no health keeps 35 percent", 0.08f * 6f * 1.5f * 0.35f, basePoints(0f, emptyList(), listOf(AnimalThreat(1.5f, 0f))), 0.0001f)
        assertEquals("half health keeps 67.5 percent", 0.08f * 6f * 1.5f * 0.675f, basePoints(0f, emptyList(), listOf(AnimalThreat(1.5f, 0.5f))), 0.0001f)
    }

    @Test fun colonistsAndAnimalsUseTheSameHealthBlend() {
        assertEquals("the blend is 35 percent at no health", 0.35f, healthBlend(0f), 0.0001f)
        assertEquals("and the whole value at full health", 1f, healthBlend(1f), 0.0001f)
        val colonist = basePoints(0f, listOf(0.5f), emptyList()) / colonistPointsCurve.eval(0f)
        val animal = basePoints(0f, emptyList(), listOf(AnimalThreat(1f, 0.5f))) / (ANIMAL_POINTS_PER_COMBAT_POWER * ANIMAL_COMBAT_POWER_PER_DANGER)
        assertEquals("a colonist and an animal at the same health keep the same share", colonist, animal, 0.0001f)
    }

    @Test fun buildingsCountAtHalfAndItemsAtFullInThreatWealth() {
        val g = threatGame(801)
        val before = g.threatWealth()
        val b = Building(BuildDef.BATTERY, g.homeX + 6, g.homeY + 6, true)
        g.map.setBuilding(b)
        assertEquals("a building counts at half", before + 0.5f * b.def.totalCost * b.quality.mult, g.threatWealth(), 0.01f)
        // drop() returns what it could not place, so the placed count is what is left out of the 40.
        var placed = 0
        for (dx in -8..8) for (dy in -8..8) if (placed == 0) placed = 40 - g.map.drop(ItemType.SILVER, 40, g.homeX + dx, g.homeY + dy)
        assertTrue("the test needs silver on the map", placed > 0)
        assertEquals(
            "items count at full value",
            before + 0.5f * b.def.totalCost * b.quality.mult + placed * ItemType.SILVER.value,
            g.threatWealth(), 0.05f,
        )
    }

    @Test fun manhuntersComeFromThreeQuartersOfThePoints() {
        assertEquals("120 points x 0.75 / 18 = 5", 5, manhuntCount(120f, 1f))
        assertEquals("a huge hunt is capped at ten", 10, manhuntCount(1000f, 1f))
        assertEquals("danger below half counts as half", 10, manhuntCount(120f, 0.2f))
        assertEquals("never fewer than two", 2, manhuntCount(10f, 1f))
        assertEquals(0.75f, MANHUNTER_POINTS_FACTOR, 0f)
    }

    @Test fun theManhunterCountIsRoundedNotTruncated() {
        // 110.4 x 0.75 / 18 = 4.6: rounds to 5 (truncation would give 4).
        assertEquals(5, manhuntCount(110.4f, 1f))
        // 100 x 0.75 / 18 = 4.17: rounds to 4.
        assertEquals(4, manhuntCount(100f, 1f))
    }

    @Test fun theManhunterBoundsAreTwoAndTen() {
        assertEquals(2, MANHUNTER_MIN_COUNT)
        assertEquals(10, MANHUNTER_MAX_COUNT)
        assertEquals(MANHUNTER_MIN_COUNT, manhuntCount(0f, 1f))
        assertEquals(MANHUNTER_MAX_COUNT, manhuntCount(100000f, 0.5f))
    }

    // ----------------------------------------------------------------------------------- the floor

    @Test fun theFloorIsThirtyFiveUntilDayTwelve() {
        val g = threatGame(802)
        g.atDay(5)
        assertEquals(35f, g.raidPointsFloor(), 0f)
    }

    @Test fun theFloorIsTheSameWithinAnHourAndDoesNotUseTheMainRng() {
        val g = threatGame(803)
        g.atDay(40)
        g.tick += 3L * TICKS_PER_HOUR
        val a = g.raidPointsFloor()
        val state = g.rng.state
        g.tick += 1000L   // still inside the same game hour (2,500 ticks)
        assertEquals("one value per hour", a, g.raidPointsFloor(), 0f)
        assertEquals("the main RNG does not move", state, g.rng.state)
        assertTrue("between 35 and the ceiling of 70: $a", a >= 35f && a <= 70f)
    }

    @Test fun theFloorChangesFromHourToHour() {
        val g = threatGame(804)
        g.atDay(40)
        val seen = HashSet<Float>()
        repeat(40) { seen += g.raidPointsFloor(); g.tick += TICKS_PER_HOUR }
        assertTrue("the floor moves from hour to hour: ${seen.size} values", seen.size > 30)
    }

    @Test fun theFloorIsTheSameForTheSameSeed() {
        val a = threatGame(805).also { it.atDay(30) }
        val b = threatGame(805).also { it.atDay(30) }
        assertEquals(a.raidPointsFloor(), b.raidPointsFloor(), 0f)
    }

    @Test fun threatPointsLieBetweenTheFloorAndTheCap() {
        val g = threatGame(806)
        g.atDay(20)
        val pts = g.threatPoints()
        assertTrue("$pts is at least the floor", pts >= g.raidPointsFloor())
        assertTrue("$pts is at most the cap", pts <= POINTS_CAP)
        assertEquals("threat points read the same twice", pts, g.threatPoints(), 0f)
    }

    // ----------------------------------------------------------------------------------- storyteller cycles

    @Test fun marloweAndOrrinHaveACycleAndJuniperDoesNot() {
        assertNotNull(Storyteller.MARLOWE.raidCycle())
        assertNotNull(Storyteller.ORRIN.raidCycle())
        assertNull(Storyteller.JUNIPER.raidCycle())
    }

    @Test fun raidsComeOnlyInsideTheOnWindowsAndFromTheStartDay() {
        for (story in listOf(Storyteller.MARLOWE, Storyteller.ORRIN)) {
            val c = story.raidCycle()!!
            for (seed in 1L..20L) {
                for (h in raidCycleHits(c, seed, 400L * CYCLE_STEPS_PER_DAY)) {
                    assertTrue("$story: no raid before the start day", h >= c.origin)
                    assertTrue("$story: $h is inside an on window", (h - c.origin) % c.period < c.onLen)
                }
            }
        }
    }

    @Test fun eachOnWindowHoldsTheRaidsItAllows() {
        for (story in listOf(Storyteller.MARLOWE, Storyteller.ORRIN)) {
            val c = story.raidCycle()!!
            for (seed in 1L..20L) {
                val limit = 300L * CYCLE_STEPS_PER_DAY
                val hits = raidCycleHits(c, seed, limit)
                var start = c.origin
                while (start <= limit) {
                    val n = hits.count { it >= start && it < start + c.onLen }
                    assertTrue("$story seed $seed: $n raids in a window at $start", n in c.minRaids..c.maxRaids)
                    start += c.period
                }
            }
        }
    }

    @Test fun aWindowThatCannotFitItsRaidsIsDroppedWhole() {
        // Four raids a day apart cannot fit in a one-day window, so every draw fails. Vanilla then clears the whole window
        // (IncidentCycleUtility.cs:64-69): none of its raids is kept, not even the one that fell inside it.
        val c = RaidCycle(minDays = 0, onDays = 1, offDays = 1, minSpacingDays = 1, minRaids = 4, maxRaids = 4)
        for (seed in 1L..20L) assertTrue("seed $seed keeps no raid", raidCycleHits(c, seed, 10L * CYCLE_STEPS_PER_DAY).isEmpty())
    }

    @Test fun hitsAreAtLeastTheMinimumSpacingApart() {
        for (story in listOf(Storyteller.MARLOWE, Storyteller.ORRIN)) {
            val c = story.raidCycle()!!
            for (seed in 1L..20L) {
                val hits = raidCycleHits(c, seed, 300L * CYCLE_STEPS_PER_DAY)
                for (k in 1 until hits.size) assertTrue("$story: ${hits[k] - hits[k - 1]} apart", hits[k] - hits[k - 1] >= c.spacing)
            }
        }
    }

    @Test fun theSameSeedGivesTheSameCycleAndANewSeedAMoreDifferentOne() {
        val c = Storyteller.MARLOWE.raidCycle()!!
        assertEquals(raidCycleHits(c, 77L, 200L * CYCLE_STEPS_PER_DAY), raidCycleHits(c, 77L, 200L * CYCLE_STEPS_PER_DAY))
        assertNotEquals(raidCycleHits(c, 77L, 200L * CYCLE_STEPS_PER_DAY), raidCycleHits(c, 78L, 200L * CYCLE_STEPS_PER_DAY))
    }

    @Test fun theLongRunRateIsTheMeanOfTheCycle() {
        // Marlowe: 3 or 4 raids (mean 3.5) per 16-day cycle, about one raid every 4.6 days, the rate of the old timer (mean 4.5 days).
        // Orrin: 2 to 4 (mean 3) per 20 days, one every 6.7 days, close to the old 6.75 days at pace 1.5.
        fun rate(story: Storyteller): Double {
            val c = story.raidCycle()!!
            val days = 730L - c.minDays
            var total = 0L
            for (seed in 1L..40L) total += raidCycleHits(c, seed, 730L * CYCLE_STEPS_PER_DAY).count { it < 730L * CYCLE_STEPS_PER_DAY }
            return total.toDouble() / (40.0 * days)
        }
        assertEquals("Marlowe rate", 3.5 / 16.0, rate(Storyteller.MARLOWE), 0.01)
        assertEquals("Orrin rate", 3.0 / 20.0, rate(Storyteller.ORRIN), 0.01)
    }

    @Test fun theNextCycleRaidIsAHitAtLeastTheSpacingAway() {
        val g = threatGame(807)
        g.atDay(10)
        val c = Storyteller.MARLOWE.raidCycle()!!
        val due = g.nextChannelTime(IncidentChannel.RAID)
        assertTrue("it is at least the spacing after now", due >= g.tick + TWO_DAYS)
        val hit = due / TICKS_PER_INTERVAL
        assertTrue("it is one of the cycle's hits", raidCycleHits(c, g.seed, hit).contains(hit))
    }

    @Test fun theNextHitIsTheFirstHitOfTheWholeWalk() {
        // nextRaidCycleTick starts its walk late. It must give the same hit as walking every cycle from day 8.
        for (story in listOf(Storyteller.MARLOWE, Storyteller.ORRIN)) {
            val c = story.raidCycle()!!
            for (seed in 1L..5L) {
                val g = threatGame(seed)
                for (day in listOf(8, 9, 20, 33, 100, 400)) {
                    val at = day.toLong() * TICKS_PER_DAY + 1234
                    val target = (at + TICKS_PER_INTERVAL - 1) / TICKS_PER_INTERVAL
                    val whole = raidCycleHits(c, seed, target + c.period).first { it >= target } * TICKS_PER_INTERVAL
                    assertEquals("$story seed $seed from day $day", whole, g.nextRaidCycleTick(c, at))
                }
            }
        }
    }

    @Test fun everyCycleHitIsRaidedAtTheFirstHourlyCheckAfterIt() {
        // The raid timer walks the cycle: the first raid at day 8, then every hit at least the spacing after the one before.
        // Raid duration is taken out (raidActive is cleared each hour), so only the schedule is tested.
        val g = threatGame(814)
        val c = Storyteller.MARLOWE.raidCycle()!!
        g.nextRaid = 8L * TICKS_PER_DAY
        val raidTicks = ArrayList<Long>()
        var seen = g.raidCounter
        while (g.tick < 730L * TICKS_PER_DAY) {
            g.tick += TICKS_PER_HOUR
            g.raidActive = false
            g.raidLastEnded = -1L
            g.runIncidentChannels()
            if (g.raidCounter != seen) { raidTicks += g.tick; seen = g.raidCounter }
        }
        val first = 8L * TICKS_PER_DAY
        assertEquals("the first raid is at day 8", first, raidTicks.first())
        val hits = raidCycleHits(c, g.seed, 730L * CYCLE_STEPS_PER_DAY)
            .map { it * TICKS_PER_INTERVAL }
            .filter { it >= first + c.spacingTicks && it < 700L * TICKS_PER_DAY }
            .map { (it + TICKS_PER_HOUR - 1) / TICKS_PER_HOUR * TICKS_PER_HOUR }
        val got = raidTicks.drop(1).filter { it < 700L * TICKS_PER_DAY }
        assertEquals("every hit from the spacing after day 8 is raided", hits, got)
    }

    @Test fun juniperKeepsItsChaosTimer() {
        val g = threatGame(808, Storyteller.JUNIPER)
        g.atDay(10)
        val wait = g.nextChannelTime(IncidentChannel.RAID) - g.tick
        // Juniper's chaos is 1: the raid spacing runs from 3 x 0.5 to 6 x 1.5 days (chaosInterval).
        assertTrue("the wait is in the chaos range: $wait", wait >= (3L * TICKS_PER_DAY) / 2 - 1 && wait <= 9L * TICKS_PER_DAY + 1)
    }

    @Test fun aDueRaidReschedulesOntoTheCycle() {
        val g = threatGame(809)
        g.atDay(10)
        g.nextRaid = g.tick
        g.runIncidentChannels()
        assertTrue("it moved on", g.nextRaid > g.tick + TWO_DAYS - 1)
        val c = Storyteller.MARLOWE.raidCycle()!!
        val hit = g.nextRaid / TICKS_PER_INTERVAL
        assertTrue("the new time is a cycle hit", raidCycleHits(c, g.seed, hit).contains(hit))
    }

    // ----------------------------------------------------------------------------------- recent incidents

    @Test fun aRecentIncidentLowersItsWeightAndTheListKeepsFive() {
        val g = threatGame(810)
        g.atDay(12)
        assertEquals(1f, g.recentWeight(Incident.METEORITE), 0f)
        g.executeIncident(IncidentRegistry.def(Incident.METEORITE))
        assertEquals(0.01f, g.recentWeight(Incident.METEORITE), 0f)
        g.executeIncident(IncidentRegistry.def(Incident.AURORA))
        assertEquals("the one before the last is 0.3", 0.3f, g.recentWeight(Incident.METEORITE), 0f)
        assertEquals(0.01f, g.recentWeight(Incident.AURORA), 0f)
        for (id in listOf(Incident.SKYHORN_VISIT, Incident.REFUGEES, Incident.PSYCHIC_WAVE, Incident.BLIGHT)) g.executeIncident(IncidentRegistry.def(id))
        assertEquals(listOf(Incident.BLIGHT, Incident.PSYCHIC_WAVE, Incident.REFUGEES, Incident.SKYHORN_VISIT, Incident.AURORA), g.lastRandomIncidents.toList())
        assertEquals("out of the list, back to full weight", 1f, g.recentWeight(Incident.METEORITE), 0f)
    }

    @Test fun aRepeatedIncidentMultipliesItsFactors() {
        val g = threatGame(811)
        g.lastRandomIncidents.addAll(listOf(Incident.BLIGHT, Incident.METEORITE, Incident.BLIGHT))
        assertEquals("0.01 times 0.5", 0.005f, g.recentWeight(Incident.BLIGHT), 1e-7f)
    }

    @Test fun aRecentIncidentIsFarLessLikelyToBePicked() {
        fun picks(recent: Incident?): Int {
            val g = threatGame(812)
            g.atDay(12)
            if (recent != null) g.lastRandomIncidents.add(recent)
            val ctx = g.incidentContext()
            var n = 0
            repeat(3000) { if (g.selectIncident(IncidentChannel.MISC, ctx, friendlyOnly = true)?.id == Incident.METEORITE) n++ }
            return n
        }
        val plain = picks(null)
        val recent = picks(Incident.METEORITE)
        assertTrue("plain $plain, recent $recent", plain > 500 && recent * 5 < plain)
    }

}
