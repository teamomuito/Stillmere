package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A calm colony where only the timers a test sets can fire. */
private fun quietGame(seed: Long, story: Storyteller = Storyteller.MARLOWE): Game {
    val g = Game(seed)
    g.startNewColony(Scenario.LOST_TRIBE)
    g.mentalBreaksEnabled = false
    g.storyteller = story
    g.pawns.removeAll { it.isAnimal && it.faction == Faction.WILD }
    val never = Long.MAX_VALUE
    g.nextRaid = never; g.nextMisc = never; g.nextWanderer = never; g.nextTrader = never; g.nextPod = never; g.nextTempEvent = never
    return g
}

private fun Game.atDay(d: Int) { tick = d.toLong() * TICKS_PER_DAY }

private fun Game.elig(id: Incident) = eligibleNow(IncidentRegistry.def(id))

private fun Game.runDays(d: Int) { repeat(d * TICKS_PER_DAY) { step() } }

class IncidentTest {

    // ----------------------------------------------------------------------------------- eligibility

    @Test fun aRaidIsIneligibleWhileOneIsActive() {
        val g = quietGame(701)
        g.raidActive = false
        assertTrue(g.elig(Incident.RAID_ATTACK))
        g.raidActive = true
        assertFalse(g.elig(Incident.RAID_ATTACK))
    }

    @Test fun threatsWaitAfterARaidButFriendlyIncidentsDoNot() {
        val g = quietGame(702)
        g.atDay(12)
        val wild = g.newAnimal(Race.DEER, g.homeX + 5, g.homeY)
        assertNotNull(wild)
        assertTrue("the test needs an animal that could join", g.incidentContext().wildTameable)
        g.raidLastEnded = g.tick
        assertFalse("a manhunter pack waits out the quiet after a raid", g.elig(Incident.MANHUNTER_PACK))
        assertTrue("a friendly animal may still join", g.elig(Incident.ANIMAL_JOINS))
        g.tick += RAID_QUIET_DAYS * TICKS_PER_DAY.toLong()
        assertTrue("the quiet is over after $RAID_QUIET_DAYS day", g.elig(Incident.MANHUNTER_PACK))
    }

    @Test fun anIncidentWaitsForItsCooldown() {
        val g = quietGame(703)
        g.atDay(10)
        assertTrue(g.elig(Incident.DISEASE_OUTBREAK))
        g.incidentLast[Incident.DISEASE_OUTBREAK] = g.tick
        assertFalse("no second outbreak straight away", g.elig(Incident.DISEASE_OUTBREAK))
        g.tick += 5L * TICKS_PER_DAY
        assertFalse("still inside the six-day cooldown", g.elig(Incident.DISEASE_OUTBREAK))
        g.tick += TICKS_PER_DAY.toLong()
        assertTrue("the cooldown has passed", g.elig(Incident.DISEASE_OUTBREAK))
    }

    @Test fun incidentsHaveAStartingDay() {
        val g = quietGame(704)
        g.atDay(10)
        assertFalse("manhunter packs wait until day 11", g.elig(Incident.MANHUNTER_PACK))
        g.atDay(11)
        assertTrue(g.elig(Incident.MANHUNTER_PACK))
    }

    @Test fun wildfiresNeedDryWeatherAndFuel() {
        val g = quietGame(705)
        g.atDay(DAYS_PER_SEASON)   // summer
        assertEquals(Season.SUMMER, g.season)
        assertTrue("the test needs fuel on the map", g.incidentContext().flammableCells > 40)
        assertTrue(g.elig(Incident.WILDFIRE))
        g.weather = Weather.RAIN
        assertFalse("rain puts a spark out before it starts", g.elig(Incident.WILDFIRE))
        g.weather = Weather.CLEAR
        g.atDay(3 * DAYS_PER_SEASON)   // winter, temperate
        assertFalse("a temperate winter is too wet and cold to burn", g.elig(Incident.WILDFIRE))
    }

    @Test fun heatAndColdAlternateByTheSeason() {
        val g = quietGame(706)
        g.atDay(DAYS_PER_SEASON)   // summer
        assertTrue(g.elig(Incident.HEAT_WAVE))
        assertFalse(g.elig(Incident.COLD_SNAP))
        g.atDay(3 * DAYS_PER_SEASON)   // winter
        assertFalse(g.elig(Incident.HEAT_WAVE))
        assertTrue(g.elig(Incident.COLD_SNAP))
    }

    @Test fun refugeesDoNotOverwriteARaidInProgress() {
        val g = quietGame(707)
        g.atDay(12)
        g.raidActive = true
        assertFalse(g.elig(Incident.REFUGEES))
    }

    @Test fun shortCircuitNeedsAChargedBattery() {
        val g = quietGame(708)
        g.atDay(10)
        assertFalse(g.elig(Incident.SHORT_CIRCUIT))
        val b = Building(BuildDef.BATTERY, g.homeX + 6, g.homeY + 6, true)
        g.map.setBuilding(b)
        b.charge = 200f
        assertTrue(g.elig(Incident.SHORT_CIRCUIT))
    }

    // ----------------------------------------------------------------------------------- selection

    @Test fun peacefulGamesOnlyEverGetFriendlyMiscIncidents() {
        val g = quietGame(709)
        g.difficulty = Difficulty.PEACEFUL
        g.atDay(10)
        val seen = HashSet<Incident>()
        repeat(300) {
            g.nextMisc = g.tick
            g.runIncidentChannels()
            seen += g.incidentLast.keys
        }
        assertTrue("some incident happened", seen.isNotEmpty())
        assertTrue("only friendly incidents: $seen", seen.all { IncidentRegistry.def(it).friendly })
        assertFalse("peaceful games have no raids", g.raidActive)
    }

    @Test fun selectionOnlyPicksEligibleIncidents() {
        val g = quietGame(710)
        g.atDay(12)
        val ctx = g.incidentContext()
        repeat(200) {
            val def = g.selectIncident(IncidentChannel.MISC, ctx)
            if (def != null) assertTrue("${def.id} was not eligible", g.eligibleNow(def, ctx))
        }
    }

    @Test fun friendlyOnlySelectionNeverPicksAThreat() {
        val g = quietGame(711)
        g.atDay(12)
        repeat(200) {
            val def = g.selectIncident(IncidentChannel.MISC, g.incidentContext(), friendlyOnly = true)
            if (def != null) assertTrue("${def.id} is not friendly", def.friendly)
        }
    }

    @Test fun theSameSeedGivesTheSameIncidents() {
        fun history(seed: Long): List<Incident?> {
            val g = quietGame(seed)
            g.atDay(15)
            return List(120) { g.rollIncident(IncidentChannel.MISC) }
        }
        assertEquals(history(712), history(712))
    }

    // ----------------------------------------------------------------------------------- scheduling

    @Test fun eachChannelsSpacingStaysWithinItsChaosRange() {
        for (story in Storyteller.entries) {
            val g = quietGame(713, story)
            val c = story.chaos
            for (ch in IncidentChannel.entries) {
                val lo = ch.minDays * TICKS_PER_DAY * (1f - 0.5f * c) - 1
                val hi = ch.maxDays * TICKS_PER_DAY * (1f + 0.5f * c) + 1
                repeat(100) {
                    val d = g.chaosInterval(ch)
                    assertTrue("$story $ch spacing $d outside [$lo, $hi]", d >= lo && d <= hi)
                    assertTrue("spacing is at least half a day", d >= TICKS_PER_DAY / 2)
                }
            }
        }
    }

    @Test fun aClosedChannelKeepsItsTimerUntilItOpens() {
        val g = quietGame(714)
        g.atDay(12)
        g.raidActive = true
        g.nextRaid = g.tick
        g.runIncidentChannels()
        assertEquals("a raid channel does not reschedule during a raid", g.tick, g.nextRaid)
        g.raidActive = false
        g.runIncidentChannels()
        assertTrue("it reschedules once it has fired", g.nextRaid > g.tick)
    }

    @Test fun theTempChannelWaitsForTheCurrentWeatherEventToEnd() {
        val g = quietGame(715)
        g.atDay(12)
        g.tempEventUntil = g.tick + TICKS_PER_DAY
        g.nextTempEvent = g.tick
        g.runIncidentChannels()
        assertEquals(g.tick, g.nextTempEvent)
    }

    @Test fun thePeacefulRaidChannelNeverFires() {
        val g = quietGame(716)
        g.difficulty = Difficulty.PEACEFUL
        g.atDay(20)
        g.nextRaid = g.tick
        g.runIncidentChannels()
        assertFalse(g.raidActive)
        assertEquals("the timer is left alone", g.tick, g.nextRaid)
    }

    @Test fun theHourlyLoopRunsTheChannels() {
        val g = quietGame(717)
        g.atDay(12)
        val before = g.tick
        g.nextWanderer = before
        g.runDays(1)
        assertTrue("the wanderer channel was scheduled again by the running game", g.nextWanderer > before)
    }

    // ----------------------------------------------------------------------------------- execution

    @Test fun executingAnIncidentRecordsItWhenItHappened() {
        val g = quietGame(718)
        g.atDay(12)
        g.executeIncident(IncidentRegistry.def(Incident.THRUMBO))
        assertEquals(g.tick, g.incidentLast[Incident.THRUMBO])
    }

    @Test fun blightDestroysNearbyCropsAndLeavesTheRest() {
        val g = quietGame(719)
        g.atDay(12)
        val crop = PlantType.entries.first { it.crop }
        val near = g.map.idx(g.homeX + 1, g.homeY + 1)
        val far = g.map.idx(g.homeX + 30, g.homeY + 30)
        g.map.plant[near] = Plant(crop, g.homeX + 1, g.homeY + 1, 1f)
        g.map.plant[far] = Plant(crop, g.homeX + 30, g.homeY + 30, 1f)
        g.executeIncident(IncidentRegistry.def(Incident.BLIGHT))
        assertEquals(null, g.map.plant[near])
        assertNotNull("a crop far away survives", g.map.plant[far])
    }

    @Test fun anOutbreakMakesColonistsSick() {
        val g = quietGame(720)
        g.atDay(12)
        g.executeIncident(IncidentRegistry.def(Incident.DISEASE_OUTBREAK))
        assertTrue("someone caught it", g.colonists.any { p -> p.hediffs.any { it.kind.category == 0 } })
    }

    @Test fun malariaOnlyBreaksOutInTheTropics() {
        val g = quietGame(721)
        g.atDay(20)
        assertEquals(Biome.TEMPERATE, g.map.biome)
        repeat(60) { g.outbreak() }
        assertFalse("no malaria in a temperate colony", g.colonists.any { p -> p.hediffs.any { it.kind == HediffKind.MALARIA } })
    }

    @Test fun shortCircuitDestroysTheBattery() {
        val g = quietGame(722)
        g.atDay(10)
        val b = Building(BuildDef.BATTERY, g.homeX + 6, g.homeY + 6, true)
        g.map.setBuilding(b)
        b.charge = 200f
        g.executeIncident(IncidentRegistry.def(Incident.SHORT_CIRCUIT))
        assertTrue("the battery is gone", g.map.buildings().none { it != null && it.def == BuildDef.BATTERY })
    }

    @Test fun aMeteoriteLeavesSteelBehind() {
        val g = quietGame(723)
        g.atDay(12)
        val before = g.map.countItems { it == ItemType.STEEL }
        g.executeIncident(IncidentRegistry.def(Incident.METEORITE))
        assertTrue(g.map.countItems { it == ItemType.STEEL } > before)
    }

    @Test fun aCargoPodAddsStockToTheMap() {
        val g = quietGame(724)
        g.atDay(12)
        val before = g.map.items.values.sumOf { it.count }
        g.executeIncident(IncidentRegistry.def(Incident.CARGO_POD))
        assertTrue(g.map.items.values.sumOf { it.count } > before)
    }

    @Test fun aWildfireStartsAFire() {
        val g = quietGame(725)
        g.atDay(DAYS_PER_SEASON)
        g.executeIncident(IncidentRegistry.def(Incident.WILDFIRE))
        assertTrue("a fire is burning", g.map.fires.isNotEmpty())
    }

    @Test fun aHeatWaveWarmsTheColony() {
        val g = quietGame(726)
        g.atDay(DAYS_PER_SEASON)
        g.executeIncident(IncidentRegistry.def(Incident.HEAT_WAVE))
        assertEquals(16f, g.tempOffset, 0.001f)
        assertTrue(g.tempEventUntil > g.tick)
    }

    @Test fun aRaidIncidentStartsARaid() {
        val g = quietGame(727)
        g.atDay(12)
        g.executeIncident(IncidentRegistry.def(Incident.RAID_ATTACK))
        assertTrue(g.raidActive)
        assertTrue(g.pawns.any { it.faction == Faction.ENEMY })
    }

    // ----------------------------------------------------------------------------------- history persistence

    @Test fun theIncidentHistorySurvivesASave() {
        val g = quietGame(728)
        g.atDay(12)
        g.incidentLast[Incident.BLIGHT] = g.tick - 3
        g.raidLastEnded = g.tick - 5
        val loaded = SaveGame.read(SaveGame.write(g))
        assertEquals(g.tick - 3, loaded.incidentLast[Incident.BLIGHT])
        assertEquals(g.tick - 5, loaded.raidLastEnded)
    }

    @Test fun anOldSaveWithoutHistoryStillLoads() {
        val g = quietGame(729)
        g.atDay(12)
        g.incidentLast[Incident.BLIGHT] = g.tick
        val loaded = SaveGame.read(SaveGame.write(g, version = 19))
        assertTrue("version 19 saves carry no history", loaded.incidentLast.isEmpty())
        assertEquals(-1L, loaded.raidLastEnded)
    }
}
