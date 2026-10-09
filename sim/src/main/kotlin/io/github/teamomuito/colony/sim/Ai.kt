package io.github.teamomuito.colony.sim

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Undrafted colonists defend themselves: shoot what is in range, or fight back when cornered. */
internal fun Game.autoFightTarget(p: Pawn): Pawn? {
    if (p.prisoner || p.hostile) return null
    var best: Pawn? = null
    var bd = 1e9f
    for (h in pawns) {
        if (!h.hostile || !h.alive || h.downed || h === p) continue
        if (h.faction == p.faction && !h.hostileFlag) continue
        val d = distance(p.x, p.y, h.x, h.y)
        val ok = d < 2.1f || (p.weapon.ranged && d <= p.weapon.range * 0.8f && map.lineOfSight(p.x, p.y, h.x, h.y) && p.cap[Cap.MANIPULATION.ordinal] > 0.3f)
        if (ok && d < bd) { best = h; bd = d }
    }
    return best
}

fun Game.shouldReact(p: Pawn): Boolean {
    val t = p.job?.type ?: return false
    if (t == JobType.FLEE || t == JobType.ATTACK || t == JobType.BREAK || t == JobType.MOVE) return false
    return autoFightTarget(p) != null || threatNear(p)
}

/** What a ranged shot meets on the way to its target. */
internal class ShotPath(val blocked: Boolean, val interceptor: Pawn?, val cover: Float)

/**
 * Walks the cells between shooter and target. A building that blocks sight stops the shot. The first pawn on the line
 * takes it, friend or foe, as in the game it imitates. Cover is the best cover anywhere on the line.
 */
internal fun Game.shotPath(p: Pawn, t: Pawn): ShotPath {
    var cover = 0f
    for (c in map.cellsBetween(p.x, p.y, t.x, t.y)) {
        val b = map.building[c]
        if (b != null && b.built && b.def.blocksSight) return ShotPath(blocked = true, interceptor = null, cover = 0f)
        if (b != null && b.built) cover = max(cover, b.def.cover)
        val x = map.xOf(c); val y = map.yOf(c)
        val occupant = pawnAt(x, y)
        if (occupant != null && occupant !== p) return ShotPath(blocked = false, interceptor = occupant, cover = cover)
    }
    return ShotPath(blocked = false, interceptor = null, cover = cover)
}

private fun Game.hitChance(p: Pawn, t: Pawn, w: Weapon, d: Float, cover: Float): Float {
    val skill = p.level(if (w.ranged) SkillType.SHOOTING else SkillType.MELEE)
    var base = w.accuracy * (0.62f + 0.03f * skill) * p.weaponDamageMult().coerceIn(0.8f, 1.15f)
    if (w.ranged) {
        base *= (1.15f - d / (w.range * 1.35f)).coerceIn(0.2f, 1.1f)
        base *= p.cap[Cap.SIGHT.ordinal].coerceAtLeast(0.2f)
        base *= (1f - cover).coerceAtLeast(0.2f)
        if (Trait.CAREFUL_SHOOTER in p.traits) base *= 1.12f
        if (weather == Weather.FOG) base *= 0.85f
        if (Trait.TRIGGER_HAPPY in p.traits) base *= 0.92f
        base *= (0.55f + 0.45f * t.race.size.coerceIn(0.5f, 1.4f)) // small animals are harder to hit
    } else {
        if (Trait.BRAWLER in p.traits) base *= 1.12f
        base *= (1f - 0.04f * t.level(SkillType.MELEE)).coerceAtLeast(0.55f)
    }
    base *= p.cap[Cap.MANIPULATION.ordinal].coerceIn(0.35f, 1f).let { if (p.isAnimal) 1f else it }
    return base.coerceIn(0.05f, 0.95f)
}

/**
 * Records the pawn's target. Switching from one target to another loses the aim built up on the first, so warmup
 * starts again. Choosing a first target keeps whatever warmup there is.
 */
internal fun Game.aim(p: Pawn, t: Pawn) {
    if (p.fightTarget != -1 && p.fightTarget != t.id) p.warmup = 0
    p.fightTarget = t.id
}

fun Game.fire(p: Pawn, t: Pawn) {
    if (p.attackCd > 0) return
    if (t.dead) return
    val w = p.weapon
    val d = distance(p.x, p.y, t.x, t.y)
    if (w.ranged) {
        if (d > w.range + 1) return
        aim(p, t)
        if (p.warmup < w.warmup) { p.warmup++; return }
        p.warmup = 0
        val path = shotPath(p, t)
        // The shot meets whatever is first on its line; a wall on the line stops it outright.
        val target = path.interceptor ?: t
        val td = distance(p.x, p.y, target.x, target.y)
        for (k in 0 until w.burst) {
            var anyHit = false
            for (pellet in 0 until w.pellets) {
                if (path.blocked || !rng.chance(hitChance(p, target, w, td, path.cover))) continue
                anyHit = true
                // Each pellet is armored separately, which is why shotguns do little against armor.
                val dmg = w.damage / w.pellets * (0.85f + rng.float() * 0.3f) * p.weaponDamageMult()
                if (w.aoe > 0f) explode(target.x, target.y, w.aoe, dmg, p, w == Weapon.MOLOTOV)
                else dealDamage(target, w.kind, dmg, w.armorPen, p)
            }
            shots.add(Shot(p.interpX(), p.interpY(), t.x.toFloat(), t.y.toFloat(), tick + 5 + k * 2, anyHit, if (w == Weapon.BOW || w == Weapon.GREATBOW) 3 else 0))
        }
        p.attackCd = max(8, (w.cooldown * (if (Trait.TRIGGER_HAPPY in p.traits) 0.85f else if (Trait.CAREFUL_SHOOTER in p.traits) 1.25f else 1f) / p.cap[Cap.MANIPULATION.ordinal].coerceIn(0.4f, 1f).let { if (p.isAnimal) 1f else it }).toInt())
        p.gainXp(SkillType.SHOOTING, 4f * w.burst)
    } else {
        if (d > 2.1f) return
        val hit = rng.chance(hitChance(p, t, w, d, cover = 0f))
        shots.add(Shot(p.x.toFloat(), p.y.toFloat(), t.x.toFloat(), t.y.toFloat(), tick + 4, hit, 4))
        if (hit) {
            var dmg = w.damage * (0.85f + rng.float() * 0.3f) * p.weaponDamageMult()
            if (p.isAnimal) dmg *= (0.7f + 0.3f * p.race.size)
            dealDamage(t, w.kind, dmg, w.armorPen, p)
        }
        p.attackCd = max(10, (w.cooldown / p.cap[Cap.MANIPULATION.ordinal].coerceIn(0.4f, 1f).let { if (p.isAnimal) 1f else it }).toInt())
        p.gainXp(SkillType.MELEE, 4f)
    }
}

internal fun Game.draftedAI(p: Pawn) {
    // Retreating pawns walk to the edge and leave, whatever else they could be doing.
    if (p.retreating) { leaveMap(p); return }
    val j = p.job
    // Explicit attack orders.
    if (j?.type == JobType.ATTACK && j.aux == 1) {
        val t = pawnById(j.targetPawn)
        if (t == null || !t.alive || t.downed) { p.job = null } else {
            val w = p.weapon
            val d = distance(p.x, p.y, t.x, t.y)
            if (w.ranged) {
                if (d <= w.range * 0.9f && map.lineOfSight(p.x, p.y, t.x, t.y)) { if (p.moveCd > 0) p.moveCd-- else fire(p, t) }
                else if (goTo(p, t.x, t.y, adjacent = true) == -1) p.job = null
            } else {
                if (d < 1.9f) fire(p, t) else if (goTo(p, t.x, t.y, adjacent = true) == -1) p.job = null
            }
            return
        }
    }
    // Keep the current target while it can still be hit; only then look for the nearest one.
    val reach = if (p.weapon.ranged) p.weapon.range else 1.9f
    fun inReach(h: Pawn): Boolean {
        if (!h.hostile || !h.alive || h.downed || h === p) return false
        if (h.faction == p.faction && !h.hostileFlag) return false
        val d = distance(p.x, p.y, h.x, h.y)
        return d <= reach && (d < 1.9f || map.lineOfSight(p.x, p.y, h.x, h.y))
    }
    var target: Pawn? = pawnById(p.fightTarget)?.takeIf { inReach(it) }
    if (target == null) {
        var bd = 1e9f
        for (h in pawns) {
            if (!inReach(h)) continue
            val d = distance(p.x, p.y, h.x, h.y)
            if (d < bd) { target = h; bd = d }
        }
    }
    if (target != null) {
        if (p.moveCd > 0) p.moveCd-- else fire(p, target)
        return
    }
    if (j?.type == JobType.MOVE) {
        if (goTo(p, j.tx, j.ty) != 1) p.job = null
        return
    }
    if (!p.weapon.ranged) {
        val h = hostiles.filter { !it.downed && distance(p.x, p.y, it.x, it.y) < 8f }.minByOrNull { distance(p.x, p.y, it.x, it.y) }
        if (h != null) goTo(p, h.x, h.y, adjacent = true)
    }
}

private fun Game.pickRaidTarget(p: Pawn): Pawn? {
    var best: Pawn? = null
    var bd = 1e9f
    for (c in pawns) {
        if (!c.alive || c === p) continue
        val enemy = if (p.faction == Faction.ENEMY || p.manhunter) c.faction == Faction.PLAYER && !c.prisoner || c.faction == Faction.VISITOR && c.refugee
        else (c.faction == Faction.PLAYER || c.faction == Faction.VISITOR) && c !== p
        if (!enemy) continue
        if (p.manhunter && c.isAnimal && c.race.size > p.race.size * 1.5f && c.faction == Faction.WILD) continue
        var d = distance(p.x, p.y, c.x, c.y)
        if (c.downed) d += 40f
        if (c.isAnimal) d += 6f
        if (d < bd) { best = c; bd = d }
    }
    if (p.hostileFlag && p.colonist) {
        // Berserk colonists lash out at their friends.
        best = pawns.filter { it !== p && it.alive && it.faction == Faction.PLAYER && !it.hostileFlag }.minByOrNull { distance(p.x, p.y, it.x, it.y) }
    }
    return best
}

private fun Game.pickTurretTarget(p: Pawn): Building? {
    var best: Building? = null
    var bd = p.weapon.range * p.weapon.range
    if (!p.weapon.ranged) return null
    for (b in map.buildings()) {
        if (b == null || !b.built || turretWeapon(b.def) == null) continue
        val d = ((b.x - p.x) * (b.x - p.x) + (b.y - p.y) * (b.y - p.y)).toFloat()
        if (d < bd && map.lineOfSight(p.x, p.y, b.x, b.y)) { best = b; bd = d }
    }
    return best
}

/** Dormant mechanoids lie still until someone gets close; the whole cluster wakes together. */
internal fun Game.dormantTick(p: Pawn) {
    if (tick % 20 != (p.id % 20).toLong()) return
    if (pawns.any { it.alive && it.faction == Faction.PLAYER && !it.isAnimal && distance(p.x, p.y, it.x, it.y) < 11f }) {
        for (o in pawns) if (o.dormant && o.raidId == p.raidId) o.dormant = false
        say("The mechanoid cluster has awoken!", 3)
    }
}

internal fun Game.hostileAI(p: Pawn) {
    var j = p.job
    if (j == null) { think(p); j = p.job ?: return }
    if (p.isAnimal) { animalHostileAI(p); return }
    if (p.colonist && p.breakKind == Break.BERSERK) {
        // fall through to the aggression code below
    } else if (p.retreating) {
        if (j.type != JobType.LEAVE) { p.job = Job(JobType.LEAVE); j = p.job!! }
        leaveMap(p)
        return
    }
    // Wounded raiders run away.
    if (!p.colonist && !p.retreating && p.healthFraction() < 0.45f && rng.chance(0.002f)) { p.retreating = true; endJob(p); return }
    j.timer++
    // Siege: wait at the camp and lob mortar shells until the clock runs out.
    if (p.raidMode == 2 && p.campX >= 0 && day * TICKS_PER_DAY + 0 < tick) {
        val waitUntil = raidStartedAt + 9000
        if (tick < waitUntil) {
            val d = distance(p.x, p.y, p.campX, p.campY)
            if (d > 4f) goTo(p, p.campX, p.campY)
            else if (j.timer % 700 == 0) siegeShell(p)
            return
        }
    }
    var t = pawnById(j.targetPawn)
    if (t == null || !t.alive || (t.downed && j.timer % 30 == 0) || j.timer % 100 == 0) {
        t = pickRaidTarget(p)
        j.targetPawn = t?.id ?: -1
    }
    val turret = pickTurretTarget(p)
    val w = p.weapon
    if (turret != null && (t == null || distance(p.x, p.y, turret.x, turret.y) + 3f < distance(p.x, p.y, t.x, t.y))) {
        if (p.attackCd == 0 && p.warmup >= w.warmup) {
            p.attackCd = w.cooldown
            turret.hp -= w.damage * w.burst * 0.6f
            shots.add(Shot(p.x.toFloat(), p.y.toFloat(), turret.x.toFloat(), turret.y.toFloat(), tick + 5, true))
            if (turret.hp <= 0f) { map.removeBuilding(turret); map.roomDirty = true; say("A ${turret.def.label.lowercase()} was destroyed!", 3) }
        } else p.warmup++
        return
    }
    if (t == null) { if (j.timer % 90 == 0) wanderAround(p); return }
    val d = distance(p.x, p.y, t.x, t.y)
    if (w.ranged) {
        val stand = w.range * 0.75f
        if (d <= stand && map.lineOfSight(p.x, p.y, t.x, t.y)) {
            if (p.moveCd > 0) p.moveCd-- else fire(p, t)
            return
        }
        if (goTo(p, t.x, t.y, adjacent = true, breach = true) == -1) wanderAround(p)
    } else {
        if (d < 1.9f) { fire(p, t); return }
        if (goTo(p, t.x, t.y, adjacent = true, breach = true) == -1) wanderAround(p)
    }
}


private fun Game.wanderAround(p: Pawn) {
    val x = (p.x + rng.range(-5, 5)).coerceIn(1, map.w - 2)
    val y = (p.y + rng.range(-5, 5)).coerceIn(1, map.h - 2)
    if (map.walkable(map.idx(x, y))) goTo(p, x, y)
}

private fun Game.siegeShell(p: Pawn) {
    val target = colonists.randomOrNull(rng) ?: return
    val tx = (target.x + rng.range(-4, 4)).coerceIn(1, map.w - 2)
    val ty = (target.y + rng.range(-4, 4)).coerceIn(1, map.h - 2)
    shots.add(Shot(p.x.toFloat(), p.y.toFloat(), tx.toFloat(), ty.toFloat(), tick + 14, true, 1))
    explode(tx, ty, 2.3f, 30f, p, false)
}

private fun <T> List<T>.randomOrNull(rng: Rng): T? = if (isEmpty()) null else this[rng.int(size)]

internal fun Game.leaveMap(p: Pawn) {
    val ex = if (p.x < map.w - 1 - p.x) 0 else map.w - 1
    val ey = if (p.y < map.h - 1 - p.y) 0 else map.h - 1
    val toX = abs(p.x - ex) < abs(p.y - ey)
    val tx = if (toX) ex else p.x
    val ty = if (toX) p.y else ey
    if (max(abs(p.x - tx), abs(p.y - ty)) <= 1 || p.x <= 1 || p.y <= 1 || p.x >= map.w - 2 || p.y >= map.h - 2) {
        pawns.remove(p); releaseAll(p)
        return
    }
    if (goTo(p, tx.coerceIn(0, map.w - 1), ty.coerceIn(0, map.h - 1)) == -1) { pawns.remove(p); releaseAll(p) }
}

internal fun Game.enemyDownedTick(p: Pawn) {
    // Downed raiders are left behind by their friends. Nothing to do; health handles recovery.
}

// ---------------------------------------------------------------- prisoners and visitors

internal fun Game.thinkPrisoner(p: Pawn) {
    if (p.escaping) { p.job = Job(JobType.LEAVE); return }
    if (p.food < 0.3f) {
        val room = if (p.homeTile >= 0 && !map.roomDirty) map.roomId[p.homeTile] else -1
        val s = nearestItem(p, shared = true) {
            it.type.isFood && it.type.humanFood && it.type != ItemType.HAY && it.rot < 0.8f &&
                (room >= 0 && map.roomId[map.idx(it.x, it.y)] == room || room < 0 && abs(it.x - p.x) + abs(it.y - p.y) < 5)
        }
        if (s != null) {
            val j = Job(JobType.EAT, s.x, s.y); j.key = key(map.idx(s.x, s.y), K_ITEM); j.item = s.type
            p.job = j; return
        }
    }
    if ((p.rest < 0.3f || p.schedule[hour] == 3 && p.rest < 0.92f) && p.homeTile >= 0) {
        val b = map.building[p.homeTile]
        if (b != null && b.built && b.def.sleeps) {
            val j = Job(JobType.SLEEP, map.xOf(p.homeTile), map.yOf(p.homeTile)); j.amount = 1
            p.job = j; return
        }
    }
    if (p.needsMedical && p.homeTile >= 0 && (p.pain > 0.15f || p.healthFraction() < 0.7f)) {
        val j = Job(JobType.REST, map.xOf(p.homeTile), map.yOf(p.homeTile)); j.amount = 1
        p.job = j; return
    }
    val j = Job(if (rng.chance(0.4f)) JobType.WANDER else JobType.IDLE)
    j.timer = rng.range(60, 140)
    p.job = j
}

internal fun Game.thinkVisitor(p: Pawn) {
    if (p.retreating || tick > p.escapeTick) { p.job = Job(JobType.LEAVE); return }
    val j = Job(if (rng.chance(0.5f)) JobType.WANDER else JobType.IDLE)
    j.timer = rng.range(80, 200)
    p.job = j
}

// ---------------------------------------------------------------- warden / handler / hunter finders

private fun Game.prisonBedFor(p: Pawn): Int = nearestCell(p, K_BED) {
    val b = map.building[it]
    b != null && b.built && b.def.sleeps && b.prisonerBed && (b.ownerId == -1 || pawnById(b.ownerId)?.alive != true)
}

internal fun Game.findWarden(p: Pawn): Job? {
    // Capture downed raiders.
    val captive = pawns.filter { it.alive && it.downed && it.faction == Faction.ENEMY && !it.isAnimal && it.carriedBy < 0 && isFree(p, pawnKey(it.id, K_PATIENT)) && !isBad(p, pawnKey(it.id, K_PATIENT)) }
        .minByOrNull { abs(it.x - p.x) + abs(it.y - p.y) }
    if (captive != null) {
        val bed = prisonBedFor(p)
        if (bed >= 0 && reserveAll(p, pawnKey(captive.id, K_PATIENT), key(bed, K_BED))) {
            val j = Job(JobType.CAPTURE, captive.x, captive.y)
            j.targetPawn = captive.id; j.dx = map.xOf(bed); j.dy = map.yOf(bed); j.key = pawnKey(captive.id, K_PATIENT)
            return j
        }
    }
    for (o in pawns) {
        if (!o.alive || !o.prisoner) continue
        val k = pawnKey(o.id, K_PATIENT)
        if (!isFree(p, k) || isBad(p, k)) continue
        // Feeding.
        if (o.food < 0.35f) {
            val room = if (o.homeTile >= 0 && !map.roomDirty) map.roomId[o.homeTile] else -1
            val hasFood = map.items.values.any { it.type.isFood && it.type.humanFood && room >= 0 && map.roomId[map.idx(it.x, it.y)] == room }
            if (!hasFood) {
                val food = nearestItem(p) { it.type.cat == ItemCat.FOOD_MEAL }
                if (food != null && reserveAll(p, k, key(map.idx(food.x, food.y), K_ITEM))) {
                    val j = Job(JobType.FEED_PRISONER, food.x, food.y)
                    j.targetPawn = o.id; j.dx = food.x; j.dy = food.y; j.key = k
                    return j
                }
            }
        }
        // Recruiting talks.
        if (tick - o.lastSocial > 6000 && o.recruitMode == 0 && o.food > 0.2f) {
            o.lastSocial = tick
            reserve(p, k)
            val j = Job(JobType.WARDEN, o.x, o.y)
            j.targetPawn = o.id; j.key = k
            return j
        }
    }
    return null
}

internal fun Game.findHandle(p: Pawn): Job? {
    var best: Pawn? = null
    var bd = Int.MAX_VALUE
    var kind = 0
    for (a in pawns) {
        if (!a.alive || !a.isAnimal || a.downed) continue
        val k = pawnKey(a.id, K_PATIENT)
        if (!isFree(p, k) || isBad(p, k)) continue
        val d = abs(a.x - p.x) + abs(a.y - p.y)
        if (a.tameMark && a.faction == Faction.WILD && d < bd) { best = a; bd = d; kind = 1 }
    }
    if (best != null) {
        reserve(p, pawnKey(best.id, K_PATIENT))
        val j = Job(JobType.TAME, best.x, best.y); j.targetPawn = best.id; j.key = pawnKey(best.id, K_PATIENT)
        return j
    }
    for (a in pawns) {
        if (!a.alive || !a.isAnimal || a.faction != Faction.PLAYER) continue
        val k = pawnKey(a.id, K_PATIENT)
        if (!isFree(p, k) || isBad(p, k)) continue
        if (a.slaughterMark) {
            reserve(p, k)
            val j = Job(JobType.SLAUGHTER, a.x, a.y); j.targetPawn = a.id; j.key = k
            return j
        }
        // Feed the hungry.
        if (a.food < 0.3f) {
            val carn = a.race.diet == Diet.CARNIVORE
            val s = nearestItem(p) { (it.type == ItemType.KIBBLE || (!carn && it.type == ItemType.HAY) || (carn && it.type == ItemType.MEAT) || it.type == ItemType.HAY && a.race.diet != Diet.CARNIVORE) }
            if (s != null && reserveAll(p, k, key(map.idx(s.x, s.y), K_ITEM))) {
                val j = Job(JobType.FEED_ANIMAL, s.x, s.y); j.targetPawn = a.id; j.dx = s.x; j.dy = s.y; j.key = k
                return j
            }
        }
        // Gather wool and milk.
        val prod = a.race.product
        if (prod != null && (prod == ItemType.WOOL || prod == ItemType.MILK) && a.animalProductTimer > TICKS_PER_DAY * 0.7f && a.tame && a.stage == LifeStage.ADULT) {
            reserve(p, k)
            val j = Job(JobType.SHEAR, a.x, a.y); j.targetPawn = a.id; j.key = k
            return j
        }
    }
    return null
}

internal fun Game.findHunt(p: Pawn): Job? {
    if (p.weaponItem == null && Trait.BRAWLER !in p.traits) return null
    var best: Pawn? = null
    var bd = Int.MAX_VALUE
    for (a in pawns) {
        if (!a.alive || !a.isAnimal || !a.huntMark || a.faction == Faction.PLAYER) continue
        if (a.race.dangerous > 1.2f && !p.weapon.ranged) continue
        val k = pawnKey(a.id, K_PATIENT)
        if (!isFree(p, k) || isBad(p, k)) continue
        val d = abs(a.x - p.x) + abs(a.y - p.y)
        if (d < bd && d < 70) { best = a; bd = d }
    }
    val a = best ?: return null
    reserve(p, pawnKey(a.id, K_PATIENT))
    val j = Job(JobType.HUNT, a.x, a.y); j.targetPawn = a.id; j.key = pawnKey(a.id, K_PATIENT)
    return j
}

/** Soldiers sent by an allied faction: charge the nearest enemy, otherwise wait near the colony, then go home. */
internal fun Game.allyAI(p: Pawn) {
    if (p.retreating) { leaveMap(p); return }
    val h = hostiles.filter { !it.downed }.minByOrNull { distance(p.x, p.y, it.x, it.y) }
    if (h == null) {
        if (distance(p.x, p.y, homeX, homeY) > 7f) goTo(p, homeX, homeY, adjacent = true)
        return
    }
    val w = p.weapon
    val d = distance(p.x, p.y, h.x, h.y)
    val inReach = if (w.ranged) d <= w.range * 0.9f && map.lineOfSight(p.x, p.y, h.x, h.y) else d < 1.9f
    if (inReach) { if (p.moveCd > 0) p.moveCd-- else fire(p, h) } else goTo(p, h.x, h.y, adjacent = true)
}
