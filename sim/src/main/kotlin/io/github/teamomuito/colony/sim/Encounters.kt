package io.github.teamomuito.colony.sim

import kotlin.math.max
import kotlin.math.min

/**
 * A caravan battle: the travellers and their enemies are dropped onto a small throwaway map, run through the normal
 * simulation (pathing, cover, weapons, wounds, retreat) with no player input, and the survivors come back.
 * Returns true when the enemy was beaten.
 */
internal fun Game.runBattle(c: Caravan, kind: Int, points: Float, fortified: Boolean, label: String): Boolean {
    val biome = world.biome[c.tile]
    val bseed = seed * 7919 + tick + c.id
    val eg = Game(bseed, GameMap.generate(44, 44, bseed, biome))
    eg.encounter = true
    eg.tick = tick
    eg.mentalBreaksEnabled = false
    eg.difficulty = Difficulty.PEACEFUL
    eg.nextRaid = Long.MAX_VALUE; eg.nextWanderer = Long.MAX_VALUE; eg.nextPod = Long.MAX_VALUE; eg.nextTrader = Long.MAX_VALUE
    eg.nextTempEvent = Long.MAX_VALUE; eg.nextMisc = Long.MAX_VALUE
    eg.weather = Weather.CLEAR; eg.weatherUntil = Long.MAX_VALUE
    eg.homeX = 22; eg.homeY = 22
    eg.nextPawnId = 1_000_000

    // Clear a battlefield: no walls of rock or deep water near the start lines.
    val m = eg.map
    for (y in 0 until m.h) for (x in 0 until m.w) {
        val i = m.idx(x, y)
        if (x < 9 || x > 34) { if (m.terrain[i] == Terrain.ROCK || m.terrain[i] == Terrain.WATER_DEEP) m.terrain[i] = Terrain.SOIL; m.natRoof[i] = false }
    }

    fun place(p: Pawn, x0: Int, y0: Int) {
        for (r in 0..12) for (dy in -r..r) for (dx in -r..r) {
            val x = x0 + dx; val y = y0 + dy
            if (!m.inB(x, y) || !m.walkable(m.idx(x, y)) || m.terrain[m.idx(x, y)] == Terrain.WATER_SHALLOW || eg.pawnAt(x, y) != null) continue
            p.x = x; p.y = y; p.fromX = x; p.fromY = y; p.moveCd = 0; p.clearPath(); p.job = null
            return
        }
        p.x = x0; p.y = y0
    }

    val mine = c.members.filter { it.alive }
    val bookkeeping = HashMap<Pawn, Boolean>()
    for (p in mine) {
        bookkeeping[p] = p.ally
        p.ally = !p.isAnimal
        p.reserved.clear(); p.job = null; p.carrying = -1; p.carriedBy = -1
        eg.pawns.add(p)
        place(p, 5, 22)
    }

    // Enemies.
    var left = points
    var count = 0
    val rid = 1
    val fx = if (fortified) 31 else 38
    if (fortified) {
        for (y in 15..29) { val i = m.idx(28, y); if (m.walkable(i)) m.building[i] = Building(BuildDef.SANDBAGS, 28, y, true) }
        for (y in 17..27 step 5) for (x in 30..31) { val i = m.idx(x, y); if (m.walkable(i)) m.building[i] = Building(BuildDef.SANDBAGS, x, y, true) }
    }
    if (kind == 1) {
        val race = when (biome) {
            Biome.TUNDRA, Biome.BOREAL -> if (rng.chance(0.3f)) Race.BEAR else Race.WOLF
            Biome.DESERT, Biome.ARID -> if (rng.chance(0.5f)) Race.BOAR else Race.WOLF
            else -> rng.pick(listOf(Race.BOAR, Race.WOLF, Race.BEAR))
        }
        val n = (points / 18f / race.dangerous.coerceAtLeast(0.5f)).toInt().coerceIn(2, 9)
        repeat(n) { val a = eg.newAnimal(race, fx, 22); a.manhunter = true; place(a, fx + rng.range(0, 3), 22 + rng.range(-6, 6)); count++ }
    } else {
        val tier = raidWeaponTier(day)
        while ((left > 0f || count == 0) && count < 16) {
            val (w, cost) = tier[rng.int(tier.size)]
            val armor = ArrayList<ItemType>()
            if (day >= 18 && rng.chance(0.4f)) armor += ItemType.A_FLAK_VEST
            if (day >= 24 && rng.chance(0.3f)) armor += ItemType.A_HELMET
            val r = eg.newRaider(fx, 22, w, rid, armor)
            r.raidMode = 0
            place(r, fx + rng.range(0, 3), 22 + rng.range(-7, 7))
            left -= cost + armor.size * 10f
            count++
        }
    }

    // Fight.
    var won = false
    var t = 0
    while (t < 9000) {
        repeat(10) { eg.step() }
        t += 10
        val foes = eg.pawns.count { it.hostile && it.alive && !it.downed && !it.retreating }
        val friends = mine.count { it.alive && !it.downed && !it.isAnimal }
        if (foes == 0) { won = friends > 0; break }
        if (friends == 0) break
    }
    if (t >= 9000) won = false

    // Loot first, while the battlefield is intact.
    if (won) {
        for (s in m.items.values) {
            if (s.corpseOf != null) {
                val r = s.corpseRace
                if (r != null && r.isAnimal && !s.corpseColonist) c.inventory[ItemType.MEAT] = (c.inventory[ItemType.MEAT] ?: 0) + r.meat / 2
                continue
            }
            c.inventory[s.type] = (c.inventory[s.type] ?: 0) + s.count
        }
        for (p in eg.pawns) if (p.hostile && p.faction == Faction.ENEMY && !p.isAnimal) {
            p.weaponItem?.let { c.inventory[it] = (c.inventory[it] ?: 0) + 1 }
        }
        if (kind == 0) c.inventory[ItemType.SILVER] = caravanSilver(c) + rng.range(20, 90)
    }

    // Everyone goes home: dead are mourned, survivors are reset.
    for (p in mine) {
        eg.pawns.remove(p)
        p.ally = bookkeeping[p] ?: false
        p.job = null; p.reserved.clear(); p.clearPath(); p.moveCd = 0
        p.drafted = false; p.retreating = false
        if (p.dead) caravanDeath(p, "killed in battle")
    }
    c.members.removeAll { it.dead }
    return won
}
