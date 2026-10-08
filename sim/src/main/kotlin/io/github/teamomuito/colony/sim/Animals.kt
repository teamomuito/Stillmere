package io.github.teamomuito.colony.sim

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

fun Game.populateWildlife() {
    val b = map.biome
    val herds = when (b) {
        Biome.TEMPERATE -> listOf(Race.DEER to 4, Race.MUFFALO to 3, Race.BOAR to 2, Race.HARE to 4, Race.TURKEY to 3, Race.WOLF to 4, Race.BEAR to 1, Race.RAT to 2)
        Biome.BOREAL -> listOf(Race.DEER to 4, Race.HARE to 4, Race.WOLF to 5, Race.BEAR to 2, Race.MUFFALO to 2)
        Biome.TUNDRA -> listOf(Race.HARE to 5, Race.WOLF to 4, Race.BEAR to 1, Race.MUFFALO to 3)
        Biome.DESERT -> listOf(Race.HARE to 3, Race.TURKEY to 3, Race.BOAR to 2, Race.RAT to 2)
        Biome.ARID -> listOf(Race.HARE to 4, Race.TURKEY to 3, Race.DEER to 3, Race.BOAR to 2, Race.WOLF to 2)
        Biome.TROPICAL -> listOf(Race.BOAR to 4, Race.TURKEY to 4, Race.DEER to 3, Race.HARE to 3, Race.RAT to 3)
    }
    for ((race, groups) in herds) {
        repeat(groups) {
            val size = if (race.herd) rng.range(2, 5) else 1
            var tries = 0
            var cx = 0; var cy = 0
            while (tries++ < 60) {
                cx = rng.range(3, map.w - 4); cy = rng.range(3, map.h - 4)
                val i = map.idx(cx, cy)
                if (map.walkable(i) && map.terrain[i] != Terrain.WATER_SHALLOW && distance(cx, cy, homeX, homeY) > 22f) break
            }
            repeat(size) {
                val x = (cx + rng.range(-3, 3)).coerceIn(1, map.w - 2); val y = (cy + rng.range(-3, 3)).coerceIn(1, map.h - 2)
                if (map.walkable(map.idx(x, y))) newAnimal(race, x, y)
            }
        }
    }
}

private fun Game.canGraze(p: Pawn): Boolean {
    val i = map.idx(p.x, p.y)
    return map.terrain[i].fertility > 0.1f && map.snow[i] < 0.4f && !map.roofed(i) && p.race.diet != Diet.CARNIVORE
}

private fun Game.anchorOf(p: Pawn): Pair<Int, Int> {
    if (p.faction == Faction.PLAYER) return homeX to homeY
    val leader = if (p.herdLeader >= 0) pawnById(p.herdLeader) else null
    if (leader != null && leader.alive && leader !== p) return leader.x to leader.y
    return p.x to p.y
}

internal fun Game.animalTick(p: Pawn) {
    // Eating and products.
    if (canGraze(p) && p.food < 0.95f && tick % 4 == 0L) p.food = min(1f, p.food + 0.0016f * (if (p.faction == Faction.PLAYER) 1f else 1.4f))
    if (p.faction == Faction.PLAYER) {
        val prod = p.race.product
        if (prod != null && p.stage == LifeStage.ADULT) {
            p.animalProductTimer++
            if (prod == ItemType.EGGS && p.animalProductTimer >= (TICKS_PER_DAY / p.race.productPerDay).toInt() && p.female) {
                p.animalProductTimer = 0
                map.drop(ItemType.EGGS, 1, p.x, p.y)
            }
        }
    }
    if (p.job == null) {
        val j = Job(JobType.WANDER); p.job = j
    }
    val j = p.job!!
    if (p.hostile) { animalHostileAI(p); return }
    // Run from danger.
    var danger: Pawn? = null
    var dd = 1e9f
    val scare = if (p.faction == Faction.PLAYER) 6f else if (p.race.predator || p.race.dangerous > 0.7f) 3f else 9f
    for (h in pawns) {
        if (!h.alive || h === p || h.downed) continue
        val threat = (h.hostile && h.faction == Faction.ENEMY) || (p.faction == Faction.WILD && !h.isAnimal && h.faction == Faction.PLAYER) || (p.faction == Faction.WILD && h.isAnimal && h.race.predator && h.faction == Faction.WILD && !p.race.predator)
        if (!threat) continue
        val d = distance(p.x, p.y, h.x, h.y)
        if (d < scare && d < dd) { danger = h; dd = d }
    }
    // Predators hunt when hungry.
    if (p.race.predator && p.faction == Faction.WILD && p.food < 0.5f && danger == null) {
        val prey = pawns.filter { it.alive && it !== p && it.isAnimal && !it.race.predator && !it.downed && it.race.size < p.race.size * 1.6f && distance(p.x, p.y, it.x, it.y) < 35f }
            .minByOrNull { distance(p.x, p.y, it.x, it.y) }
        if (prey != null) {
            if (distance(p.x, p.y, prey.x, prey.y) < 1.9f) {
                fire(p, prey)
                if (prey.dead || prey.downed && p.food < 0.9f) {
                    if (prey.dead) p.food = min(1f, p.food + 0.5f)
                }
            } else goTo(p, prey.x, prey.y, adjacent = true)
            return
        }
        // Hungry enough to go after people nearby.
        if (p.food < 0.08f && day >= 6) {
            val human = pawns.filter { it.alive && !it.isAnimal && it.faction == Faction.PLAYER && !it.downed && distance(p.x, p.y, it.x, it.y) < 20f }.minByOrNull { distance(p.x, p.y, it.x, it.y) }
            if (human != null) { p.manhunter = true; return }
        }
    }
    if (danger != null && !(p.race.dangerous > 0.7f && p.faction == Faction.WILD && !danger.hostile)) {
        // Flee directly away.
        val dx = Integer.signum(p.x - danger.x); val dy = Integer.signum(p.y - danger.y)
        val tx = (p.x + dx * 8 + rng.range(-3, 3)).coerceIn(1, map.w - 2); val ty = (p.y + dy * 8 + rng.range(-3, 3)).coerceIn(1, map.h - 2)
        if (map.walkable(map.idx(tx, ty))) { goTo(p, tx, ty); return }
    }
    // Tamed animals defend the colony.
    if (p.faction == Faction.PLAYER && p.race.dangerous >= 0.4f) {
        val foe = hostiles.filter { !it.downed && distance(p.x, p.y, it.x, it.y) < 13f }.minByOrNull { distance(p.x, p.y, it.x, it.y) }
        if (foe != null) {
            if (distance(p.x, p.y, foe.x, foe.y) < 1.9f) fire(p, foe) else goTo(p, foe.x, foe.y, adjacent = true)
            return
        }
    }
    // Idle wandering.
    if (j.timer-- <= 0 || goTo(p, j.tx, j.ty) != 1) {
        j.timer = rng.range(60, 260)
        val (ax, ay) = anchorOf(p)
        val rad = if (p.faction == Faction.PLAYER) 11 else 7
        val x = (ax + rng.range(-rad, rad)).coerceIn(1, map.w - 2)
        val y = (ay + rng.range(-rad, rad)).coerceIn(1, map.h - 2)
        val c = map.idx(x, y)
        if (map.walkable(c) && map.terrain[c] != Terrain.WATER_SHALLOW && (p.faction != Faction.PLAYER || distance(x, y, homeX, homeY) < 22f) && allowedFor(p, c)) { j.tx = x; j.ty = y } else { j.tx = p.x; j.ty = p.y }
    }
}

internal fun Game.animalHostileAI(p: Pawn) {
    var j = p.job
    if (j == null) { j = Job(JobType.RAID); p.job = j }
    j.timer++
    var t = pawnById(j.targetPawn)
    // Provoked animals go after whoever hurt them, then calm down.
    if (p.predatorTarget >= 0 && p.race.insect.not()) {
        val prov = pawnById(p.predatorTarget)
        if (prov == null || !prov.alive || prov.downed || j.timer > 2400) { p.manhunter = false; p.predatorTarget = -1; p.job = null; return }
        j.targetPawn = prov.id
        t = prov
    }
    if (t == null || !t.alive || t.downed && j.timer % 20 == 0 || j.timer % 80 == 0 && p.predatorTarget < 0) {
        t = pawns.filter { it.alive && it !== p && (it.faction == Faction.PLAYER || it.faction == Faction.VISITOR) && !it.downed }
            .minByOrNull { distance(p.x, p.y, it.x, it.y) }
        j.targetPawn = t?.id ?: -1
    }
    if (t == null) { p.manhunter = false; p.job = null; return }
    if (p.manhunter && tick % 3000L == 0L && rng.chance(0.2f) && distance(p.x, p.y, t.x, t.y) > 30f) { p.manhunter = false; p.job = null; return }
    if (distance(p.x, p.y, t.x, t.y) < 1.9f) fire(p, t)
    else goTo(p, t.x, t.y, adjacent = true, breach = false)
}
