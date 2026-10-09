package io.github.teamomuito.colony.sim

import kotlin.math.max
import kotlin.math.min

/**
 * The battle map for a caravan fight. It is an ordinary [Game] in encounter mode: the terrain and the enemies come from
 * the same generators as colony maps, and the caravan's people and animals are the same [Pawn] objects, now drafted so
 * the player can command them. Nothing is resolved here; the battle game runs until [battleTick] decides it.
 */
internal fun Game.createBattleGame(c: Caravan, plan: BattlePlan): Game {
    val biome = world.biome[c.tile]
    val bseed = seed * 7919 + tick + c.id
    val bg = Game(bseed, GameMap.generate(44, 44, bseed, biome))
    bg.encounter = true
    bg.battle = plan
    bg.parent = this
    bg.world = world
    bg.storyteller = storyteller
    bg.tick = tick
    bg.mentalBreaksEnabled = false
    bg.difficulty = Difficulty.PEACEFUL
    bg.nextRaid = Long.MAX_VALUE; bg.nextWanderer = Long.MAX_VALUE; bg.nextPod = Long.MAX_VALUE; bg.nextTrader = Long.MAX_VALUE
    bg.nextTempEvent = Long.MAX_VALUE; bg.nextMisc = Long.MAX_VALUE
    bg.weather = Weather.CLEAR; bg.weatherUntil = Long.MAX_VALUE
    bg.homeX = 22; bg.homeY = 22
    // Pawn ids continue the colony's counter so nothing collides when the survivors return.
    bg.nextPawnId = nextPawnId
    bg.researchDone.addAll(researchDone)

    // A battlefield with no sealed rock or deep water near the start lines.
    val m = bg.map
    for (y in 0 until m.h) for (x in 0 until m.w) {
        val i = m.idx(x, y)
        if (x < 9 || x > 34) { if (m.terrain[i] == Terrain.ROCK || m.terrain[i] == Terrain.WATER_DEEP) m.terrain[i] = Terrain.SOIL; m.natRoof[i] = false }
    }
    fun Game.placeNear(p: Pawn, x0: Int, y0: Int) {
        for (r in 0..12) for (dy in -r..r) for (dx in -r..r) {
            val x = x0 + dx; val y = y0 + dy
            if (!m.inB(x, y) || !m.walkable(m.idx(x, y)) || m.terrain[m.idx(x, y)] == Terrain.WATER_SHALLOW || pawnAt(x, y) != null) continue
            p.x = x; p.y = y; p.fromX = x; p.fromY = y; p.moveCd = 0; p.clearPath(); p.job = null
            return
        }
        p.x = x0; p.y = y0
    }

    // The caravan's people and animals. They keep their health, injuries, gear and mood.
    for (p in c.members.filter { it.alive }) {
        releaseAll(p)
        p.reserved.clear(); p.job = null; p.clearPath(); p.moveCd = 0
        p.carrying = -1; p.carriedBy = -1
        p.drafted = true; p.retreating = false
        bg.pawns.add(p); bg.battleMembers.add(p)
        bg.placeNear(p, 5, 22)
    }
    c.members.clear()

    // The enemy, from the west-or-east side away from the colony.
    var left = plan.points
    var count = 0
    val fx = if (plan.fortified) 31 else 38
    if (plan.fortified) {
        for (y in 15..29) { val i = m.idx(28, y); if (m.walkable(i)) m.setBuilding(Building(BuildDef.SANDBAGS, 28, y, true)) }
        for (y in 17..27 step 5) for (x in 30..31) { val i = m.idx(x, y); if (m.walkable(i)) m.setBuilding(Building(BuildDef.SANDBAGS, x, y, true)) }
    }
    if (plan.kind == 1) {
        val race = when (biome) {
            Biome.TUNDRA, Biome.BOREAL -> if (rng.chance(0.3f)) Race.BEAR else Race.WOLF
            Biome.DESERT, Biome.ARID -> if (rng.chance(0.5f)) Race.BOAR else Race.WOLF
            else -> rng.pick(listOf(Race.BOAR, Race.WOLF, Race.BEAR))
        }
        val n = (plan.points / 18f / race.dangerous.coerceAtLeast(0.5f)).toInt().coerceIn(2, 9)
        repeat(n) {
            val a = bg.newAnimal(race, fx, 22)
            a.manhunter = true
            bg.placeNear(a, fx + rng.range(0, 3), 22 + rng.range(-6, 6))
            count++
        }
    } else {
        val tier = raidWeaponTier(day)
        while ((left > 0f || count == 0) && count < 16) {
            val (w, cost) = tier[rng.int(tier.size)]
            val armor = ArrayList<ItemType>()
            if (day >= 18 && rng.chance(0.4f)) armor += ItemType.A_FLAK_VEST
            if (day >= 24 && rng.chance(0.3f)) armor += ItemType.A_HELMET
            val r = bg.newRaider(fx, 22, w, 1, armor)
            r.raidMode = 0
            r.wfaction = plan.enemyFaction
            bg.placeNear(r, fx + rng.range(0, 3), 22 + rng.range(-7, 7))
            left -= cost + armor.size * 10f
            count++
        }
    }
    return bg
}
