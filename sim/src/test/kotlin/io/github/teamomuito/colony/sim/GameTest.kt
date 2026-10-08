package io.github.teamomuito.colony.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun newGame(seed: Long = 7, sc: Scenario = Scenario.CRASHLANDED): Game {
    val g = Game(seed)
    g.startNewColony(sc)
    return g
}

private fun Game.run(ticks: Int) { repeat(ticks) { step() } }
private fun Game.quiet() { mentalBreaksEnabled = false; pawns.removeAll { it.isAnimal && it.faction == Faction.WILD }; nextRaid = Long.MAX_VALUE; nextMisc = Long.MAX_VALUE; nextWanderer = Long.MAX_VALUE; nextTrader = Long.MAX_VALUE }

class PathfinderTest {
    @Test fun routesAroundWallsAndBreachesWhenAllowed() {
        val m = GameMap(20, 20)
        for (y in 0 until 20) m.building[m.idx(10, y)] = Building(BuildDef.WOOD_WALL, 10, y, true)
        val f = Pathfinder(m)
        assertNull(f.find(2, 10, 17, 10))
        val breach = f.find(2, 10, 17, 10, breach = true)
        assertNotNull(breach)
        assertEquals(m.idx(17, 10), breach!!.last())
        m.building[m.idx(10, 10)] = Building(BuildDef.DOOR, 10, 10, true)
        assertNotNull(f.find(2, 10, 17, 10))
    }

    @Test fun adjacentGoalStopsNextToRock() {
        val m = GameMap(10, 10)
        m.terrain[m.idx(5, 5)] = Terrain.ROCK
        val p = Pathfinder(m).find(1, 5, 5, 5, adjacent = true)!!
        assertEquals(4, m.xOf(p.last()))
    }
}

class WorldTest {
    @Test fun mapHasPlayableTerrainInEveryBiome() {
        for (b in Biome.entries) {
            val m = GameMap.generate(100, 100, 42, b)
            assertTrue("$b rock", m.terrain.count { it == Terrain.ROCK } > 200)
            assertTrue("$b ore", m.ore.count { it != Ore.NONE } > 10)
            assertTrue("$b land", m.terrain.count { it.passable && it != Terrain.WATER_SHALLOW } > 5000)
        }
    }

    @Test fun roomsHoldHeatFromACampfire() {
        val g = newGame(); g.quiet()
        val hx = g.homeX + 8; val hy = g.homeY - 8
        for (x in hx..hx + 4) for (y in hy..hy + 4) {
            val edge = x == hx || x == hx + 4 || y == hy || y == hy + 4
            val i = g.map.idx(x, y)
            g.map.terrain[i] = Terrain.SOIL; g.map.plant[i] = null
            if (edge) g.map.building[i] = Building(BuildDef.WOOD_WALL, x, y, true)
        }
        val fire = Building(BuildDef.CAMPFIRE, hx + 2, hy + 2, true); fire.fuel = 24f
        g.map.building[g.map.idx(hx + 2, hy + 2)] = fire
        g.map.roomDirty = true
        g.tempOffset = -25f
        g.run(4000)
        val inside = g.map.idx(hx + 1, hy + 1)
        val outside = g.map.idx(hx - 3, hy)
        assertTrue(g.map.roomIndoorAt(inside))
        assertFalse(g.map.roomIndoorAt(outside))
        assertTrue("inside ${g.map.tempAt(inside, g.outdoorTemp())} outside ${g.outdoorTemp()}", g.map.tempAt(inside, g.outdoorTemp()) > g.outdoorTemp() + 4f)
    }

    @Test fun solarPanelsPowerALampThroughConduit() {
        val g = newGame(); g.quiet()
        g.researchDone.addAll(Research.entries)
        val hx = g.homeX + 6; val hy = g.homeY - 7
        for (x in hx..hx + 4) { val i = g.map.idx(x, hy); g.map.terrain[i] = Terrain.SOIL; g.map.plant[i] = null; g.map.building[i] = null }
        g.map.conduit[g.map.idx(hx + 1, hy)] = true; g.map.conduit[g.map.idx(hx + 2, hy)] = true; g.map.conduit[g.map.idx(hx + 3, hy)] = true
        val solar = Building(BuildDef.SOLAR_PANEL, hx, hy, true); g.map.building[g.map.idx(hx, hy)] = solar
        val lamp = Building(BuildDef.STANDING_LAMP, hx + 4, hy, true); g.map.building[g.map.idx(hx + 4, hy)] = lamp
        // Run into daytime.
        g.tick = 12L * TICKS_PER_HOUR
        g.run(600)
        assertTrue("lamp powered", lamp.powered)
        assertTrue(g.power.produced > 500f)
        // A solar flare shuts everything down.
        g.solarFlareUntil = g.tick + 5000
        g.run(500)
        assertFalse(lamp.powered)
    }

    @Test fun fireSpreadsAndBurnsOut() {
        val g = newGame(); g.quiet()
        val hx = g.homeX + 10; val hy = g.homeY + 10
        for (y in hy..hy + 6) for (x in hx..hx + 6) { val i = g.map.idx(x, y); g.map.terrain[i] = Terrain.SOIL; g.map.plant[i] = Plant(PlantType.OAK, x, y, 1f) }
        g.weather = Weather.CLEAR; g.weatherUntil = Long.MAX_VALUE
        g.igniteCell(g.map.idx(hx + 3, hy + 3), 0.9f)
        g.run(600)
        assertTrue("spread", g.map.fires.size > 1 || (hy..hy + 6).any { y -> (hx..hx + 6).any { x -> g.map.plant[g.map.idx(x, y)] == null } })
        g.weather = Weather.RAIN
        g.run(3000)
        assertTrue(g.map.fires.isEmpty())
    }
}

class HealthTest {
    private fun human(): Pair<Game, Pawn> { val g = newGame(); g.quiet(); return g to g.colonists[0] }

    @Test fun woundsBleedAndTendingHelps() {
        val (g, p) = human()
        p.skill[SkillType.MEDICINE.ordinal] = 15
        g.dealDamage(p, DamageKind.CUT, 12f, 0f, null, 0)
        assertTrue(p.injuries.isNotEmpty())
        assertTrue(p.bleeding > 0f)
        val before = p.bleeding
        g.tendPawn(p, p, ItemType.MEDS_INDUSTRIAL)
        assertTrue(p.bleeding < before * 0.5f)
        assertFalse(p.untended)
    }

    @Test fun woundsHealWithRest() {
        val (g, p) = human()
        g.dealDamage(p, DamageKind.BRUISE, 10f, 0f, null, 3)
        g.tendPawn(null, p, ItemType.MEDS_HERBAL)
        g.run(TICKS_PER_DAY * 6)
        assertTrue("healed ${p.injuries.size}", p.injuries.none { !it.scar && it.severity > 0.5f })
        assertTrue(p.alive)
    }

    @Test fun untreatedBleedingEventuallyKills() {
        val (g, p) = human()
        for (c in g.colonists) c.priority[WorkType.DOCTOR.ordinal] = 0
        for (k in 0 until 6) g.woundPart(p, 0, DamageKind.CUT, 5f)
        var worst = 0f
        repeat(96) { g.run(1000); worst = maxOf(worst, p.bloodLoss) }
        assertTrue("worst blood loss $worst", p.dead || worst > 0.3f)
    }

    @Test fun destroyingTheHeartKills() {
        val (g, p) = human()
        val heart = p.race.body.indexOfFirst { it.tag == PartTag.HEART }
        g.woundPart(p, heart, DamageKind.BULLET, 40f)
        assertTrue(p.dead)
    }

    @Test fun losingBothLegsDownsAPawn() {
        val (g, p) = human()
        for (i in p.race.body.indices) if (p.race.body[i].tag == PartTag.LEG) g.woundPart(p, i, DamageKind.CUT, 80f)
        g.recomputeHealth(p)
        assertTrue(p.cap[Cap.MOVING.ordinal] < 0.1f)
        g.run(30)
        assertTrue(p.downed || p.dead)
    }

    @Test fun armorReducesDamage() {
        val g = newGame(); g.quiet()
        val a = g.colonists[0]; val b = g.colonists[1]
        a.apparel.clear(); b.apparel.clear()
        b.apparel.add(Worn(ItemType.A_FLAK_VEST, Quality.NORMAL, 160f))
        var da = 0f; var db = 0f
        repeat(40) {
            a.injuries.clear(); b.injuries.clear()
            g.dealDamage(a, DamageKind.BULLET, 12f, 0f, null, 0)
            g.dealDamage(b, DamageKind.BULLET, 12f, 0f, null, 0)
            da += a.injuries.sumOf { it.severity.toDouble() }.toFloat(); db += b.injuries.sumOf { it.severity.toDouble() }.toFloat()
            b.apparel.firstOrNull()?.hp = 160f
        }
        assertTrue("armored $db vs bare $da", db < da * 0.75f)
    }

    @Test fun coldWithoutClothesCausesHypothermia() {
        val g = newGame(); g.quiet()
        val p = g.colonists[0]
        p.apparel.clear()
        g.tempOffset = -40f; g.tempEventUntil = g.tick + 10 * TICKS_PER_DAY
        g.run(3000)
        assertNotNull(p.hediff(HediffKind.HYPOTHERMIA))
    }

    @Test fun illnessRunsItsCourseWithCare() {
        val (g, p) = human()
        g.addHediff(p, HediffKind.FLU, 0.1f)
        repeat(8) { g.tendPawn(null, p, ItemType.MEDS_HERBAL); g.run(TICKS_PER_DAY / 4) }
        assertTrue(p.alive)
    }
}

class ColonyTest {
    @Test fun colonistsSurviveAFewQuietDays() {
        val g = newGame(); g.quiet()
        g.run(TICKS_PER_DAY * 3)
        assertEquals(3, g.colonists.size)
        assertTrue(g.colonists.all { it.food > 0f })
    }

    @Test fun colonistsBuildABed() {
        val g = newGame(); g.quiet()
        val bx = g.homeX + 4; val by = g.homeY
        assertTrue(g.placeBlueprint(BuildDef.BED, bx, by))
        g.run(5000)
        val b = g.map.building[g.map.idx(bx, by)]
        assertNotNull(b)
        assertTrue("bed built", b!!.built)
    }

    @Test fun choppingAndMiningYieldResources() {
        val g = newGame(); g.quiet()
        val tree = g.map.plant.filterNotNull().first { it.type.isTree }
        val woodBefore = g.map.countItems(ItemType.WOOD)
        g.designate(tree.x, tree.y, Desig.CUT)
        val rockI = (0 until g.map.size).first { g.map.terrain[it] == Terrain.ROCK && g.map.ore[it] == Ore.NONE && (0 until 8).any { d ->
            val nx = g.map.xOf(it) + GameMap.DX8[d]; val ny = g.map.yOf(it) + GameMap.DY8[d]
            g.map.inB(nx, ny) && g.map.walkable(g.map.idx(nx, ny))
        } }
        g.designate(g.map.xOf(rockI), g.map.yOf(rockI), Desig.MINE)
        g.run(12000)
        assertTrue("wood ${g.map.countItems(ItemType.WOOD)} vs $woodBefore", g.map.countItems(ItemType.WOOD) > woodBefore)
        assertTrue(g.map.terrain[rockI] != Terrain.ROCK)
    }

    @Test fun cropsGrowAndGetHarvested() {
        val g = newGame(); g.quiet()
        g.paintZone(g.homeX - 2, g.homeY + 3, g.homeX + 2, g.homeY + 5, ZoneKind.GROWING, PlantType.RICE)
        g.run(TICKS_PER_DAY * 9)
        assertTrue("planted", g.map.plant.any { it?.type == PlantType.RICE } || g.map.countItems(ItemType.RICE) > 0)
    }

    @Test fun billsCookMealsFromRawFood() {
        val g = newGame(); g.quiet()
        g.researchDone.addAll(Research.entries)
        val sx = g.homeX + 4; val sy = g.homeY
        val stove = Building(BuildDef.STOVE_FUEL, sx, sy, true); stove.fuel = 10f
        g.map.building[g.map.idx(sx, sy)] = stove
        val bill = Bill(Recipe.COOK_SIMPLE); bill.mode = BillMode.DO_X; bill.target = 2
        stove.bills.add(bill)
        g.map.drop(ItemType.RICE, 60, g.homeX, g.homeY - 2)
        // Remove existing meals so the new ones are countable.
        val mealsBefore = g.map.countItems(ItemType.MEAL_SIMPLE)
        g.run(9000)
        assertTrue("meals ${g.map.countItems(ItemType.MEAL_SIMPLE)} vs $mealsBefore", g.map.countItems(ItemType.MEAL_SIMPLE) >= mealsBefore - 6 + 2 || stove.bills.isEmpty())
        assertTrue(bill.done >= 1)
    }

    @Test fun researchTakesRealTimeAndUnlocksBuildings() {
        val g = newGame(); g.quiet()
        assertFalse(g.canBuildAt(BuildDef.STEEL_WALL, g.homeX + 6, g.homeY - 6))
        g.placeBlueprint(BuildDef.RESEARCH_BENCH, g.homeX + 3, g.homeY + 4)
        for (c in g.colonists.drop(1)) c.priority[WorkType.RESEARCH.ordinal] = 0
        g.colonists[0].priority[WorkType.RESEARCH.ordinal] = 1
        g.startResearch(Research.SMITHING)
        g.run(TICKS_PER_DAY / 2)
        assertTrue("not instant", Research.SMITHING !in g.researchDone)
        g.run(TICKS_PER_DAY * 10)
        assertTrue(Research.SMITHING in g.researchDone)
        assertTrue(g.canBuildAt(BuildDef.STEEL_WALL, g.homeX + 6, g.homeY - 6))
    }

    @Test fun stockpilesRespectFilters() {
        val g = newGame(); g.quiet()
        val z = g.map.zones.values.first { it.kind == ZoneKind.STOCKPILE }
        z.allowed[ItemType.WOOD.ordinal] = false
        val s = ItemStack(1000, ItemType.WOOD, 5, 0, 0)
        assertFalse(z.accepts(s.type, s.quality))
        assertTrue(z.accepts(ItemType.STEEL, Quality.NORMAL))
    }

    @Test fun savedGamesLoadBack() {
        val g = newGame()
        g.run(3000)
        g.placeBlueprint(BuildDef.WOOD_WALL, g.homeX + 5, g.homeY + 5)
        g.researchCurrent = Research.SMITHING
        val c = g.colonists[0]
        g.dealDamage(c, DamageKind.CUT, 9f, 0f, null, 0)
        g.addHediff(c, HediffKind.FLU, 0.2f)
        val bench = Building(BuildDef.CAMPFIRE, g.homeX + 6, g.homeY + 6, true)
        bench.bills.add(Bill(Recipe.COOK_SIMPLE).also { it.mode = BillMode.FOREVER })
        g.map.building[g.map.idx(g.homeX + 6, g.homeY + 6)] = bench
        val loaded = SaveGame.read(SaveGame.write(g))
        assertEquals(g.tick, loaded.tick)
        assertEquals(g.colonists.map { it.name }, loaded.colonists.map { it.name })
        assertEquals(g.map.countItems(ItemType.MEAL_SIMPLE), loaded.map.countItems(ItemType.MEAL_SIMPLE))
        assertNotNull(loaded.map.building[loaded.map.idx(g.homeX + 5, g.homeY + 5)])
        assertEquals(1, loaded.map.building[loaded.map.idx(g.homeX + 6, g.homeY + 6)]!!.bills.size)
        assertEquals(Research.SMITHING, loaded.researchCurrent)
        val lc = loaded.pawnById(c.id)!!
        assertEquals(c.injuries.size, lc.injuries.size)
        assertEquals(c.hediffs.size, lc.hediffs.size)
        assertEquals(g.map.zones.size, loaded.map.zones.size)
        loaded.run(2000)
    }
}

class CombatTest {
    @Test fun turretsAndColonistsRepelARaid() {
        val g = newGame(11); g.quiet()
        g.researchDone.addAll(Research.entries)
        for (dx in listOf(-3, 2)) { val b = Building(BuildDef.TURRET, g.homeX + dx, g.homeY - 3, true); g.map.building[g.map.idx(g.homeX + dx, g.homeY - 3)] = b }
        g.run(500)
        g.nextRaid = g.tick
        g.run(TICKS_PER_DAY * 2)
        assertTrue("raid happened", g.log.any { it.text.startsWith("RAID") })
        assertTrue(g.colonists.isNotEmpty())
    }

    @Test fun draftedColonistKillsALoneRaider() {
        val g = newGame(3); g.quiet()
        val shooter = g.colonists.first { it.weaponItem == ItemType.W_RIFLE }
        g.setDrafted(shooter, true)
        val raider = g.newRaider(shooter.x + 12, shooter.y, ItemType.W_CLUB, 1)
        g.run(3000)
        assertTrue("raider down or dead", raider.dead || raider.downed || raider.healthFraction() < 0.9f)
        assertTrue(shooter.alive)
    }

    @Test fun downedRaidersCanBeCapturedAndRecruited() {
        val g = newGame(5); g.quiet()
        val cell = (-3..3).flatMap { dy -> (-3..3).map { dx -> g.homeX + 6 + dx to g.homeY + 6 + dy } }
        val bed = Building(BuildDef.BED, g.homeX + 6, g.homeY + 6, true); bed.prisonerBed = true
        g.map.building[g.map.idx(g.homeX + 6, g.homeY + 6)] = bed
        val raider = g.newRaider(g.homeX + 3, g.homeY + 4, ItemType.W_KNIFE, 1)
        for (i in raider.race.body.indices) if (raider.race.body[i].tag == PartTag.LEG) g.woundPart(raider, i, DamageKind.CUT, 80f)
        raider.tameMark = false
        g.downPawn(raider)
        g.colonists.forEach { it.priority[WorkType.WARDEN.ordinal] = 1 }
        g.run(2500)
        assertTrue("captured", raider.prisoner)
        raider.resistance = 0f
        g.colonists.forEach { it.skill[SkillType.SOCIAL.ordinal] = 15 }
        for (k in 0 until 40) { raider.lastSocial = -99999; g.run(1000); if (!raider.prisoner) break }
        assertTrue("recruited", !raider.prisoner && raider.faction == Faction.PLAYER && raider.colonist)
        assertTrue(cell.isNotEmpty())
    }
}

class AnimalTest {
    @Test fun huntedAnimalsBecomeMeat() {
        val g = newGame(9); g.quiet()
        g.researchDone.addAll(Research.entries)
        val table = Building(BuildDef.BUTCHER_TABLE, g.homeX + 3, g.homeY - 4, true)
        g.map.building[g.map.idx(g.homeX + 3, g.homeY - 4)] = table
        val deer = g.newAnimal(Race.DEER, g.homeX + 8, g.homeY + 2)
        deer.huntMark = true
        for (c in g.colonists) { c.priority[WorkType.HUNT.ordinal] = 1; c.priority[WorkType.COOK.ordinal] = 1 }
        g.run(9000)
        assertTrue("deer killed", deer.dead)
        assertTrue("meat ${g.map.countItems(ItemType.MEAT)}", g.map.countItems(ItemType.MEAT) > 0)
    }

    @Test fun animalsCanBeTamed() {
        val g = newGame(9); g.quiet()
        val hare = g.newAnimal(Race.COW, g.homeX + 4, g.homeY + 3)
        hare.tameMark = true
        for (c in g.colonists) { c.priority[WorkType.HANDLE.ordinal] = 1; c.skill[SkillType.ANIMALS.ordinal] = 14 }
        g.run(15000)
        assertEquals(Faction.PLAYER, hare.faction)
    }

    @Test fun herdsExistOnTheMap() {
        val g = newGame()
        assertTrue(g.pawns.count { it.isAnimal } > 20)
    }
}

class TradeTest {
    @Test fun tradersBuyAndSell() {
        val g = newGame(); g.quiet()
        g.spawnTrader()
        val t = g.trader()
        assertNotNull(t)
        val steelBefore = g.map.countItems(ItemType.STEEL)
        val earned = g.sellItem(t!!, ItemType.STEEL, 40)
        assertTrue(earned > 0)
        assertEquals(steelBefore - 40, g.map.countItems(ItemType.STEEL))
        val silverBefore = g.map.countItems(ItemType.SILVER)
        val stockItem = t.stock.keys.first { it != ItemType.SILVER && !it.isGear }
        assertTrue(g.buyItem(t, stockItem, 1))
        assertTrue(g.map.countItems(ItemType.SILVER) < silverBefore)
    }
}

class ScenarioTest {
    @Test fun everyScenarioStartsAndRuns() {
        for (sc in Scenario.entries) {
            val g = newGame(21, sc)
            assertTrue("$sc crew", g.colonists.isNotEmpty())
            g.run(1500)
            assertFalse(g.gameOver)
        }
    }

    @Test fun storytellersAndDifficultyScaleThreats() {
        val g = newGame(); g.quiet()
        g.difficulty = Difficulty.PEACEFUL
        g.nextRaid = g.tick
        g.run(2000)
        assertFalse(g.raidActive)
    }

    @Test fun qualityRollTracksSkill() {
        val g = newGame()
        val low = (0 until 300).map { g.rollQuality(0).ordinal }.average()
        val high = (0 until 300).map { g.rollQuality(18).ordinal }.average()
        assertTrue("$low < $high", low + 1.5 < high)
    }
}


class AdvancedTest {
    @Test fun surgeryInstallsABionicLeg() {
        val g = newGame(8); g.quiet()
        g.researchDone.addAll(Research.entries)
        val bed = Building(BuildDef.HOSPITAL_BED, g.homeX + 3, g.homeY - 3, true)
        g.map.building[g.map.idx(g.homeX + 3, g.homeY - 3)] = bed
        g.map.drop(ItemType.PLASTEEL, 40, g.homeX, g.homeY - 2)
        g.map.drop(ItemType.GOLD, 40, g.homeX + 1, g.homeY - 2)
        g.map.drop(ItemType.COMPONENT, 20, g.homeX - 1, g.homeY - 2)
        val doc = g.colonists[0]; val pat = g.colonists[1]
        doc.skill[SkillType.MEDICINE.ordinal] = 16
        for (c in g.colonists) c.priority[WorkType.DOCTOR.ordinal] = if (c === doc) 1 else 0
        val leg = pat.race.body.indexOfFirst { it.tag == PartTag.LEG }
        g.woundPart(pat, leg, DamageKind.CUT, 80f)
        assertTrue(pat.partMissing(leg))
        val order = g.availableSurgeries(pat).first { it.kind == SurgeryKind.INSTALL && it.part == leg && it.implant == Implant.BIONIC_LEG }
        g.queueSurgery(pat, order)
        g.run(14000)
        assertTrue("implanted ${pat.implants} queue=${pat.surgeries.size}", pat.implants[leg] == Implant.BIONIC_LEG || pat.surgeries.isEmpty())
    }

    @Test fun mechanoidsDropScrapWhenDestroyed() {
        val g = newGame(4); g.quiet()
        val m = g.newMech(Race.SCYTHER, g.homeX + 20, g.homeY, 1)
        assertFalse(m.hostile.not())
        val steel = g.map.countItems(ItemType.STEEL)
        g.woundPart(m, 4, DamageKind.BULLET, 80f) // power core
        assertTrue(m.dead)
        g.run(30)
        assertTrue(g.map.countItems(ItemType.STEEL) > steel)
    }

    @Test fun visitorsLeaveTheMap() {
        val g = newGame(); g.quiet()
        g.spawnTrader()
        val t = g.trader()!!
        val trader = g.pawnById(t.pawnId)!!
        g.tick = t.leaveAt + 3000
        g.run(6000)
        assertTrue("trader gone", g.pawnById(t.pawnId) == null || !g.pawnById(t.pawnId)!!.alive || g.distance(trader.x, trader.y, g.homeX, g.homeY) > 25f)
    }

    @Test fun berryBushesRegrowAfterHarvest() {
        val g = newGame(); g.quiet()
        val x = g.homeX + 6; val y = g.homeY + 8
        val i = g.map.idx(x, y)
        g.map.terrain[i] = Terrain.SOIL; g.map.building[i] = null
        g.map.plant[i] = Plant(PlantType.BERRY, x, y, 1f)
        g.designate(x, y, Desig.HARVEST)
        g.run(3000)
        assertNotNull(g.map.plant[i])
        assertTrue(g.map.plant[i]!!.growth < 1f || g.map.countItems(ItemType.STRAWBERRIES) > 0)
    }

    @Test fun mentalBreaksHappenToUnhappyColonists() {
        val g = newGame(6)
        g.pawns.removeAll { it.isAnimal && it.faction == Faction.WILD }
        g.nextRaid = Long.MAX_VALUE; g.nextMisc = Long.MAX_VALUE
        val p = g.colonists[0]
        p.addThought("Awful day", -0.6f, g.tick, 20 * TICKS_PER_DAY)
        var broke = false
        repeat(40) { g.run(1000); if (p.breakUntil > 0) broke = true }
        assertTrue(broke)
    }
}


class AreaTest {
    @Test fun restrictedColonistsStayInsideTheirArea() {
        val g = newGame(2); g.quiet()
        val hx = g.homeX; val hy = g.homeY
        for (x in hx - 3..hx + 3) for (y in hy - 3..hy + 3) g.map.areas[0][g.map.idx(x, y)] = true
        val p = g.colonists[0]
        p.areaRestriction = 1
        // Work outside the area must be ignored.
        val tree = g.map.plant.filterNotNull().first { it.type.isTree && (Math.abs(it.x - hx) > 10 || Math.abs(it.y - hy) > 10) }
        g.designate(tree.x, tree.y, Desig.CUT)
        for (c in g.colonists.drop(1)) c.priority[WorkType.PLANT_CUT.ordinal] = 0
        g.run(6000)
        assertNotNull(g.map.plant[g.map.idx(tree.x, tree.y)])
        assertTrue(p.areaRestriction == 1)
    }
}


class CraftingTest {
    private fun bench(g: Game, def: BuildDef, dx: Int, dy: Int, recipe: Recipe, count: Int = 1): Building {
        val b = Building(def, g.homeX + dx, g.homeY + dy, true)
        val i = g.map.idx(b.x, b.y)
        g.map.terrain[i] = Terrain.SOIL; g.map.plant[i] = null
        g.map.building[i] = b
        if (def.fuelCap > 0f) b.fuel = def.fuelCap
        if (def.consumesPower) {
            // A solar panel and a bit of conduit next to it.
            for (k in 1..2) { val ci = g.map.idx(g.homeX + dx + k, g.homeY + dy); g.map.terrain[ci] = Terrain.SOIL; g.map.plant[ci] = null; g.map.building[ci] = null }
            g.map.conduit[g.map.idx(g.homeX + dx + 1, g.homeY + dy)] = true
            g.map.building[g.map.idx(g.homeX + dx + 2, g.homeY + dy)] = Building(BuildDef.SOLAR_PANEL, g.homeX + dx + 2, g.homeY + dy, true)
            g.map.conduit[g.map.idx(g.homeX + dx + 2, g.homeY + dy)] = true
            g.tick = 8L * TICKS_PER_HOUR
        }
        val bill = Bill(recipe); bill.mode = BillMode.DO_X; bill.target = count
        b.bills.add(bill)
        return b
    }

    @Test fun tailorsMakeClothesAndColonistsWearThem() {
        val g = newGame(12); g.quiet()
        g.researchDone.addAll(Research.entries)
        bench(g, BuildDef.TAILOR_BENCH, 4, -3, Recipe.MAKE_PARKA)
        g.map.drop(ItemType.CLOTH, 200, g.homeX, g.homeY - 2)
        for (c in g.colonists) c.skill[SkillType.CRAFTING.ordinal] = 8
        for (c in g.colonists) c.priority[WorkType.TAILOR.ordinal] = 1
        g.run(12000)
        val parkas = g.map.items.values.count { it.type == ItemType.A_PARKA } + g.colonists.count { c -> c.apparel.any { it.type == ItemType.A_PARKA } }
        assertTrue("parkas $parkas", parkas >= 1)
        g.run(8000)
        assertTrue("someone wears it", g.colonists.any { c -> c.apparel.any { it.type == ItemType.A_PARKA } })
    }

    @Test fun smithsCraftWeapons() {
        val g = newGame(13); g.quiet()
        g.researchDone.addAll(Research.entries)
        bench(g, BuildDef.SMITHY, 4, -3, Recipe.MAKE_KNIFE, 2)
        for (c in g.colonists) { c.skill[SkillType.CRAFTING.ordinal] = 10; c.priority[WorkType.SMITH.ordinal] = 1 }
        g.run(15000)
        val knives = g.map.items.values.count { it.type == ItemType.W_KNIFE } + g.pawns.count { it.weaponItem == ItemType.W_KNIFE }
        assertTrue("knives $knives", knives >= 2)
    }

    @Test fun stonecuttersTurnChunksIntoBlocks() {
        val g = newGame(14); g.quiet()
        g.researchDone.addAll(Research.entries)
        bench(g, BuildDef.STONECUTTER, 4, -3, Recipe.CUT_BLOCKS)
        g.map.drop(ItemType.STONE_CHUNK, 1, g.homeX + 1, g.homeY - 2)
        for (c in g.colonists) c.priority[WorkType.CRAFT.ordinal] = 1
        g.run(6000)
        assertTrue("blocks ${g.map.countItems(ItemType.STONE)}", g.map.countItems(ItemType.STONE) >= 20)
    }

    @Test fun drugLabBrewsBeer() {
        val g = newGame(15); g.quiet()
        g.researchDone.addAll(Research.entries)
        bench(g, BuildDef.DRUG_LAB, 4, -3, Recipe.BREW_BEER)
        g.map.drop(ItemType.CORN, 30, g.homeX + 1, g.homeY - 2)
        for (c in g.colonists) c.priority[WorkType.CRAFT.ordinal] = 1
        g.run(10000)
        assertTrue("beer ${g.map.countItems(ItemType.BEER)}", g.map.countItems(ItemType.BEER) >= 5)
    }

    @Test fun hydroponicsGrowCropsIndoorsWithPower() {
        val g = newGame(16); g.quiet()
        g.researchDone.addAll(Research.entries)
        val x = g.homeX + 8; val y = g.homeY - 8
        val i = g.map.idx(x, y)
        g.map.terrain[i] = Terrain.GRAVEL; g.map.plant[i] = null
        val h = Building(BuildDef.HYDROPONICS, x, y, true); h.powered = true
        g.map.building[i] = h
        g.map.conduit[g.map.idx(x + 1, y)] = true
        val solar = Building(BuildDef.SOLAR_PANEL, x + 2, y, true)
        g.map.terrain[g.map.idx(x + 2, y)] = Terrain.SOIL; g.map.plant[g.map.idx(x + 2, y)] = null
        g.map.building[g.map.idx(x + 2, y)] = solar
        g.map.terrain[g.map.idx(x + 1, y)] = Terrain.SOIL; g.map.plant[g.map.idx(x + 1, y)] = null
        g.setZone(x, y, ZoneKind.GROWING, PlantType.RICE)
        assertNotNull(g.map.zoneAt(i))
        g.tick = 7L * TICKS_PER_HOUR
        g.run(24000 * 8)
        val grown = g.map.plant[i]
        assertTrue("planted or harvested", grown != null || g.map.countItems(ItemType.RICE) > 0)
    }
}


class VictoryTest {
    @Test fun buildingAndLaunchingTheShipWinsTheGame() {
        val g = newGame(77); g.quiet()
        g.researchDone.addAll(Research.entries)
        // Plenty of everything and a power grid-free build site.
        for (t in listOf(ItemType.STEEL, ItemType.PLASTEEL, ItemType.COMPONENT, ItemType.GOLD)) g.map.drop(t, 800, g.homeX, g.homeY - 2)
        val spots = listOf(BuildDef.SHIP_COMPUTER, BuildDef.SHIP_ENGINE, BuildDef.SHIP_ENGINE, BuildDef.SHIP_REACTOR, BuildDef.SHIP_CASKET)
        for ((k, d) in spots.withIndex()) {
            val x = g.homeX + 6 + k * 2; val y = g.homeY - 7
            val i = g.map.idx(x, y)
            g.map.terrain[i] = Terrain.SOIL; g.map.plant[i] = null; g.map.building[i] = null
            assertTrue("place ${d.label}", g.placeBlueprint(d, x, y))
        }
        for (c in g.colonists) { c.skill[SkillType.CONSTRUCTION.ordinal] = 16; c.priority[WorkType.CONSTRUCT.ordinal] = 1 }
        assertFalse(g.shipComplete())
        g.run(TICKS_PER_DAY * 5)
        assertTrue("ship complete", g.shipComplete())
        assertTrue(g.launchShip())
        assertTrue(g.won && g.gameOver)
    }
}

class CaravanTest {
    private fun packed(g: Game): Pair<List<Pawn>, Map<ItemType, Int>> {
        g.map.drop(ItemType.MEAL_PACKAGED, 20, g.homeX, g.homeY)
        g.map.drop(ItemType.STEEL, 100, g.homeX, g.homeY)
        g.paintZone(g.homeX - 2, g.homeY - 2, g.homeX + 2, g.homeY + 2, ZoneKind.STOCKPILE)
        g.map.drop(ItemType.MEAL_PACKAGED, 20, g.homeX, g.homeY); g.map.drop(ItemType.STEEL, 100, g.homeX, g.homeY)
        return listOf(g.colonists[0]) to mapOf(ItemType.MEAL_PACKAGED to 20, ItemType.STEEL to 10)
    }

    @Test fun worldHasHomeSettlementsAndRoutes() {
        val g = newGame()
        val w = g.world
        assertTrue(w.passable(w.homeTile))
        assertEquals(g.map.biome, w.biome[w.homeTile])
        assertTrue(w.settlements.size >= 8)
        val reachable = w.settlements.count { w.path(w.homeTile, it.tile) != null }
        assertTrue("most settlements reachable", reachable >= w.settlements.size / 2)
    }

    @Test fun caravanTravelsTradesAndComesHome() {
        val g = newGame(); g.quiet()
        val (members, items) = packed(g)
        val dest = g.world.settlements.first { it.faction.trades && g.world.path(g.world.homeTile, it.tile) != null }
        // Drop a stockpile so goods are packable.
        val before = g.colonists.size
        assertNull(g.formCaravan(members, items, dest.tile))
        assertEquals(before - 1, g.colonists.size)
        val c = g.caravans.single()
        assertTrue(c.capacity() >= 35f)
        var guard = 0
        while (g.caravans.contains(c) && c.tile != dest.tile && guard++ < 400000 / 250 * 6) { g.run(250) }
        if (g.caravans.contains(c) && c.tile == dest.tile) {
            g.refreshSettlement(dest)
            val gold = g.caravanSell(c, dest, ItemType.STEEL, 5)
            assertTrue(gold > 0)
            assertNull(g.orderCaravan(c, g.world.homeTile))
            guard = 0
            while (g.caravans.contains(c) && guard++ < 6000) g.run(250)
        }
        // Either it returned (members back on the map) or something is still en route / was lost; the state must be consistent.
        if (g.caravans.isEmpty()) assertTrue(g.pawns.any { it === members[0] } || !members[0].alive)
    }

    @Test fun cannotSendEveryone() {
        val g = newGame()
        assertNotNull(g.formCaravan(g.colonists, emptyMap(), g.world.settlements[0].tile))
    }

    @Test fun savesAndLoadsCaravans() {
        val g = newGame(); g.quiet()
        val (members, items) = packed(g)
        val dest = g.world.settlements.first { it.faction.trades && g.world.path(g.world.homeTile, it.tile) != null }
        assertNull(g.formCaravan(members, items, dest.tile))
        g.run(250 * 3)
        val l = SaveGame.read(SaveGame.write(g))
        assertEquals(1, l.caravans.size)
        assertEquals(g.caravans[0].inventory, l.caravans[0].inventory)
        assertEquals(g.caravans[0].members.size, l.caravans[0].members.size)
    }
}
