package io.github.teamomuito.colony.sim

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal const val K_ITEM = 0
internal const val K_BUILD = 1
internal const val K_PLANT = 2
internal const val K_DESIG = 3
internal const val K_DEST = 4
internal const val K_BED = 5
internal const val K_PATIENT = 6
internal const val K_STATION = 7

internal fun key(i: Int, kind: Int) = i * 8 + kind
internal fun pawnKey(id: Int, kind: Int) = 2_000_000 + id * 8 + kind

fun Game.endJob(p: Pawn) {
    releaseAll(p)
    val j = p.job
    if (j != null) for ((t, n, q) in j.held) map.drop(t, n, p.x, p.y, q)
    j?.stack?.let { placeStack(it, p.x, p.y); j.stack = null }
    if (p.carryCount > 0 && p.carryType != null) map.drop(p.carryType!!, p.carryCount, p.x, p.y, p.carryQuality)
    p.carryCount = 0; p.carryType = null
    if (p.carrying >= 0) { pawnById(p.carrying)?.let { it.carriedBy = -1 }; p.carrying = -1 }
    p.job = null
    p.clearPath()
    p.warmup = 0
}

internal fun Game.unreserve(p: Pawn, k: Int) {
    if (reservations[k] == p.id) reservations.remove(k)
    p.reserved.remove(k)
}

internal fun Game.markUnreachable(p: Pawn, k: Int) { unreachable[p.id * 10_000_000L + k] = tick + 900 }
internal fun Game.isBad(p: Pawn, k: Int): Boolean {
    val e = unreachable[p.id * 10_000_000L + k] ?: return false
    return e > tick
}

internal fun Game.nearestCell(p: Pawn, kind: Int, pred: (Int) -> Boolean): Int {
    var best = -1
    var bd = Int.MAX_VALUE
    for (i in 0 until map.size) {
        if (!pred(i)) continue
        val d = abs(map.xOf(i) - p.x) + abs(map.yOf(i) - p.y)
        if (d >= bd) continue
        val k = key(i, kind)
        if (!isFree(p, k) || isBad(p, k)) continue
        best = i; bd = d
    }
    return best
}

internal fun Game.nearestItem(p: Pawn, shared: Boolean = false, skip: Int = -1, pred: (ItemStack) -> Boolean): ItemStack? {
    var best: ItemStack? = null
    var bd = Int.MAX_VALUE
    for (s in map.items.values) {
        if (s.forbidden || s.corpseOf != null) continue
        if (!pred(s)) continue
        val i = map.idx(s.x, s.y)
        if (i == skip) continue
        val d = abs(s.x - p.x) + abs(s.y - p.y)
        if (d >= bd) continue
        val k = key(i, K_ITEM)
        if ((!shared && !isFree(p, k)) || isBad(p, k)) continue
        best = s; bd = d
    }
    return best
}

// ---------------------------------------------------------------- movement

internal fun Game.goalReached(p: Pawn, tx: Int, ty: Int, adjacent: Boolean): Boolean =
    if (adjacent) max(abs(p.x - tx), abs(p.y - ty)) <= 1 && (p.x != tx || p.y != ty) else p.x == tx && p.y == ty

/** @return 1 while moving, 0 on arrival, -1 if there is no way there. */
fun Game.goTo(p: Pawn, tx: Int, ty: Int, adjacent: Boolean = false, breach: Boolean = false): Int {
    if (p.moveCd > 0) { p.moveCd--; return 1 }
    if (goalReached(p, tx, ty, adjacent)) { p.clearPath(); return 0 }
    if (p.cap[Cap.MOVING.ordinal] <= 0.02f && !p.isAnimal) return -1
    val pk = map.idx(tx, ty) * 4 + (if (adjacent) 1 else 0) + (if (breach) 2 else 0)
    var path = p.path
    if (path == null || p.pathKey != pk || p.pathI >= path.size) {
        path = finder.find(p.x, p.y, tx, ty, adjacent, breach) ?: return -1
        if (path.isEmpty()) return 0
        p.path = path; p.pathI = 0; p.pathKey = pk
    }
    val next = path[p.pathI]
    if (!map.walkable(next)) {
        if (breach) {
            val b = map.building[next]
            if (b != null && b.built) { attackBuilding(p, b); return 1 }
        }
        p.clearPath()
        return 1
    }
    val prevIdx = map.idx(p.x, p.y)
    p.fromX = p.x; p.fromY = p.y
    p.x = map.xOf(next); p.y = map.yOf(next)
    p.pathI++
    p.moveTotal = max(3, p.moveSpeedTicks() * map.stepCost(next) / 10)
    p.moveCd = p.moveTotal
    // Tracking dirt indoors.
    if (!p.isAnimal && !map.roofed(prevIdx) && map.roofed(next) && rng.chance(0.06f) && map.filth[next] < 3) map.filth[next] = (map.filth[next] + 1).toByte()
    if (map.fires.containsKey(next) && !p.isAnimal) dealDamage(p, DamageKind.BURN, 2f)
    return 1
}

internal fun Game.attackBuilding(p: Pawn, b: Building) {
    if (p.attackCd > 0) return
    p.attackCd = 40
    val dmg = if (p.weapon.ranged) 6f else p.weapon.damage * 1.2f
    b.hp -= dmg
    if (b.hp <= 0f) {
        map.building[map.idx(b.x, b.y)] = null
        map.roomDirty = true
        say("A ${b.def.label.lowercase()} was destroyed!", 2)
    }
}

// ---------------------------------------------------------------- thinking

fun Game.threatNear(p: Pawn): Boolean {
    for (h in pawns) {
        if (!h.hostile || !h.alive || h.downed || h === p) continue
        if (h.faction == p.faction && !h.hostileFlag) continue
        val d = distance(p.x, p.y, h.x, h.y)
        if (d < 5f) return true
        if (d < 11f && map.lineOfSight(h.x, h.y, p.x, p.y)) return true
    }
    return false
}

fun Game.think(p: Pawn) {
    if (p.hostile && !p.colonist) { p.job = Job(if (p.retreating) JobType.LEAVE else JobType.RAID); return }
    if (p.hostileFlag) { p.job = Job(JobType.RAID); return }
    if (p.breakUntil > tick) { p.job = Job(JobType.BREAK); return }
    if (p.prisoner) { thinkPrisoner(p); return }
    if (p.faction == Faction.VISITOR) { thinkVisitor(p); return }
    if (p.drafted) return
    if (autoFightTarget(p) != null) { p.job = Job(JobType.ATTACK); return }
    if (threatNear(p)) { p.job = Job(JobType.FLEE); return }
    // Firefighting beats nearly everything.
    if (p.priority[WorkType.FIREFIGHT.ordinal] > 0 && p.priority[WorkType.FIREFIGHT.ordinal] <= 1) findFirefight(p)?.let { p.job = it; return }
    // Needs.
    val act = p.schedule[hour]
    if (p.food < 0.3f && startEat(p)) return
    if (p.needsMedical && p.priority[WorkType.PATIENT.ordinal] > 0 && patientShouldRest(p) && startRest(p)) return
    val sleepy = p.rest < 0.25f || (act == 3 && p.rest < 0.92f) || (Trait.NIGHT_OWL in p.traits && false)
    if (sleepy && startSleep(p)) return
    // Gear.
    if (tick % 5 == (p.id % 5).toLong()) findGear(p)?.let { p.job = it; return }
    if (act == 2 && p.joy < 0.95f) startJoy(p)?.let { p.job = it; return }
    val workHour = act == 1 || act == 0 || act == 3 && p.rest >= 0.92f
    if (workHour) {
        val order = WorkType.entries.filter { p.priority[it.ordinal] > 0 && p.incapable and (1 shl it.ordinal) == 0 }
            .sortedBy { p.priority[it.ordinal] * 100 + it.ordinal }
        for (w in order) {
            val j = tryWork(p, w) ?: continue
            p.job = j
            return
        }
    }
    if (p.food < 0.55f && startEat(p)) return
    if (p.joy < (if (act == 1) 0.2f else 0.55f)) startJoy(p)?.let { p.job = it; return }
    val j = Job(if (rng.chance(0.4f)) JobType.WANDER else JobType.IDLE)
    j.timer = rng.range(40, 90)
    p.job = j
}

private fun Game.patientShouldRest(p: Pawn): Boolean =
    p.pain > 0.12f || p.bloodLoss > 0.1f || p.hediffs.any { it.kind.category == 0 && it.severity > 0.25f } || p.healthFraction() < 0.6f || p.injuries.any { it.infection > 0f } || p.hediffs.any { it.kind == HediffKind.HYPOTHERMIA && it.severity > 0.35f }

internal fun Game.tryWork(p: Pawn, w: WorkType): Job? = when (w) {
    WorkType.FIREFIGHT -> findFirefight(p)
    WorkType.PATIENT -> null
    WorkType.DOCTOR -> findDoctor(p)
    WorkType.WARDEN -> findWarden(p)
    WorkType.HANDLE -> findHandle(p)
    WorkType.COOK -> findBill(p, WorkType.COOK) ?: findButcher(p)
    WorkType.HUNT -> findHunt(p)
    WorkType.CONSTRUCT -> findConstruct(p)
    WorkType.GROW -> findGrow(p)
    WorkType.MINE -> findMine(p)
    WorkType.PLANT_CUT -> findCut(p)
    WorkType.SMITH -> findBill(p, WorkType.SMITH)
    WorkType.TAILOR -> findBill(p, WorkType.TAILOR)
    WorkType.ART -> findArt(p)
    WorkType.CRAFT -> findBill(p, WorkType.CRAFT)
    WorkType.HAUL -> findHaul(p)
    WorkType.CLEAN -> findClean(p)
    WorkType.RESEARCH -> findResearch(p)
}

// ---------------------------------------------------------------- needs: eat / sleep / rest

private fun Game.foodScore(p: Pawn, t: ItemType, rot: Float): Int {
    if (!t.humanFood || !t.isFood || t == ItemType.HAY) return -1
    if (t == ItemType.KIBBLE && p.food > 0.12f) return -1
    if (t == ItemType.HUMAN_MEAT && Trait.CANNIBAL !in p.traits && p.food > 0.08f) return -1
    if (rot > 0.88f) return -1
    val raw = t.cat == ItemCat.FOOD_PLANT || t.cat == ItemCat.FOOD_MEAT && t != ItemType.MILK
    if (p.foodPolicy == 2 && t.cat != ItemCat.FOOD_MEAL && p.food > 0.1f) return -1
    if (p.foodPolicy == 1 && raw && p.food > 0.1f) return -1
    return when (t) {
        ItemType.MEAL_FINE -> 0
        ItemType.MEAL_SIMPLE -> 1
        ItemType.PEMMICAN -> 2
        ItemType.MILK -> 3
        ItemType.EGGS, ItemType.STRAWBERRIES -> 5
        ItemType.KIBBLE -> 9
        ItemType.HUMAN_MEAT -> 8
        else -> 6
    }
}

internal fun Game.startEat(p: Pawn): Boolean {
    var best: ItemStack? = null
    var bs = Int.MAX_VALUE
    for (s in map.items.values) {
        if (s.forbidden || s.corpseOf != null || s.count <= 0) continue
        val sc = foodScore(p, s.type, s.rot)
        if (sc < 0) continue
        val d = abs(s.x - p.x) + abs(s.y - p.y)
        val i = map.idx(s.x, s.y)
        if (isBad(p, key(i, K_ITEM))) continue
        val score = sc * 25 + d
        if (score < bs) { bs = score; best = s }
    }
    val s = best ?: return false
    val j = Job(JobType.EAT, s.x, s.y)
    j.key = key(map.idx(s.x, s.y), K_ITEM)
    j.item = s.type
    p.job = j
    return true
}

internal fun Game.startRest(p: Pawn): Boolean {
    val bed = findBedFor(p)
    val j = Job(JobType.REST)
    if (bed >= 0) { j.tx = map.xOf(bed); j.ty = map.yOf(bed); j.amount = 1 } else { j.stage = 1 }
    p.job = j
    return true
}

/** Finds a bed for this pawn, claiming it when unowned. Returns the cell index or -1. */
internal fun Game.findBedFor(p: Pawn, forceMedical: Boolean = false): Int {
    var bed = -1
    val needsMed = p.needsMedical && (p.pain > 0.15f || p.healthFraction() < 0.65f || p.hediffs.any { it.kind.category == 0 && it.severity > 0.3f })
    if (needsMed || forceMedical) {
        bed = nearestCell(p, K_BED) {
            val b = map.building[it]
            b != null && b.built && b.def.medical && (b.occupant == -1 || b.occupant == p.id || pawnById(b.occupant)?.alive != true) && (p.prisoner == b.prisonerBed || !b.prisonerBed)
        }
        if (bed >= 0) { map.building[bed]!!.occupant = p.id; return bed }
    }
    if (p.bedId >= 0) {
        val b = map.building[p.bedId]
        if (b != null && b.built && b.def.sleeps && b.ownerId == p.id) return p.bedId else p.bedId = -1
    }
    bed = nearestCell(p, K_BED) {
        val b = map.building[it]
        b != null && b.built && b.def.sleeps && !b.def.medical && b.prisonerBed == p.prisoner &&
            (b.ownerId == -1 || pawnById(b.ownerId)?.alive != true)
    }
    if (bed >= 0) { map.building[bed]!!.ownerId = p.id; p.bedId = bed; return bed }
    if (!p.prisoner) {
        bed = nearestCell(p, K_BED) { val b = map.building[it]; b != null && b.built && b.def.medical && !b.prisonerBed && (b.occupant == -1 || b.occupant == p.id) && b.ownerId == -1 }
        if (bed >= 0) { map.building[bed]!!.occupant = p.id; return bed }
    }
    return -1
}

internal fun Game.startSleep(p: Pawn): Boolean {
    val bed = findBedFor(p)
    val j = Job(JobType.SLEEP)
    if (bed >= 0) {
        j.tx = map.xOf(bed); j.ty = map.yOf(bed); j.amount = 1
    } else {
        j.stage = 1; j.amount = 0
    }
    p.job = j
    return true
}

// ---------------------------------------------------------------- joy

internal fun Game.startJoy(p: Pawn): Job? {
    // Drugs, when allowed.
    if (p.allowDrugs && p.joy < 0.6f && p.hediff(HediffKind.ALCOHOL_HIGH) == null) {
        val s = nearestItem(p) { it.type == ItemType.BEER || it.type == ItemType.JOINT || it.type == ItemType.PSYCHITE_TEA && rng.chance(0.3f) }
        if (s != null) {
            val j = Job(JobType.SMOKE, s.x, s.y); j.key = key(map.idx(s.x, s.y), K_ITEM); reserve(p, j.key); j.item = s.type
            return j
        }
    }
    val b = nearestCell(p, K_STATION) {
        val bd = map.building[it]
        bd != null && bd.built && bd.def.joy > 0f && !bd.forbidden && (!bd.def.consumesPower || bd.powered) &&
            (bd.def != BuildDef.HORSESHOES || weather != Weather.RAIN && weather != Weather.THUNDER && !map.roofed(it))
    }
    if (b >= 0) {
        reserve(p, key(b, K_STATION))
        val j = Job(JobType.JOY, map.xOf(b), map.yOf(b)); j.key = key(b, K_STATION)
        return j
    }
    // Chat with someone idle.
    val o = pawns.filter { it !== p && it.colonist && it.alive && !it.downed && it.job?.type in listOf(JobType.IDLE, JobType.WANDER, JobType.JOY) && distance(p.x, p.y, it.x, it.y) < 20f }
        .minByOrNull { distance(p.x, p.y, it.x, it.y) }
    if (o != null && rng.chance(0.6f)) {
        val j = Job(JobType.SOCIAL, o.x, o.y); j.targetPawn = o.id
        return j
    }
    return Job(JobType.JOY, p.x, p.y).also { it.stage = 9; it.timer = 900 }
}

// ---------------------------------------------------------------- gear

internal fun Game.apparelValue(it: ItemStack): Float {
    val a = it.type.apparel ?: return 0f
    return (a.armorSharp * 2f + a.insCold * 0.02f + 0.1f) * it.quality.mult * (0.4f + 0.6f * it.hp)
}

internal fun Game.findGear(p: Pawn): Job? {
    if (p.drafted || p.prisoner || p.isAnimal) return null
    // Weapon.
    val wantRanged = p.skill[SkillType.SHOOTING.ordinal] >= p.skill[SkillType.MELEE.ordinal] && Trait.BRAWLER !in p.traits || Trait.TRIGGER_HAPPY in p.traits
    fun weaponScore(t: ItemType?, q: Quality): Float {
        val w = t?.weapon ?: return 0f
        var sc = w.damage * w.burst / (w.cooldown / 50f) * q.mult
        sc *= if (w.ranged == wantRanged) 1.35f else 0.8f
        return sc
    }
    val cur = weaponScore(p.weaponItem, p.weaponQuality)
    val ws = nearestItem(p) { it.type.weapon != null && weaponScore(it.type, it.quality) > cur * 1.2f && (!it.type.weapon!!.ranged || Trait.BRAWLER !in p.traits) }
    if (ws != null && map.zoneKind(map.idx(ws.x, ws.y)) != ZoneKind.NONE) {
        reserve(p, key(map.idx(ws.x, ws.y), K_ITEM))
        val j = Job(JobType.EQUIP, ws.x, ws.y); j.key = key(map.idx(ws.x, ws.y), K_ITEM)
        return j
    }
    // Apparel: wear a better piece for any slot; swap tattered clothes.
    if (Trait.NUDIST in p.traits) return null
    val temp = outdoorTemp()
    var best: ItemStack? = null
    var bs = 0f
    for (s in map.items.values) {
        val a = s.type.apparel ?: continue
        if (s.forbidden || s.corpseOf != null) continue
        if (map.zoneKind(map.idx(s.x, s.y)) == ZoneKind.NONE) continue
        val i = map.idx(s.x, s.y)
        if (!isFree(p, key(i, K_ITEM)) || isBad(p, key(i, K_ITEM))) continue
        // Conflicting layers: shirts under outer, same slot replaces.
        val worn = p.apparel.filter { it.type.apparel?.slot == a.slot }
        val wornValue = worn.sumOf { ((it.type.apparel?.armorSharp ?: 0f) * 2f * it.quality.mult + (it.type.apparel?.insCold ?: 0f) * 0.02f * it.quality.mult + 0.1f).toDouble() }.toFloat()
        var v = apparelValue(s) - wornValue
        // Cold weather prefers warm gear and hot prefers light gear.
        if (temp < 5f) v += a.insCold * 0.01f
        if (temp > 28f) v -= a.insCold * 0.006f
        // Do not wear overlapping body areas in the same layer chain too much.
        if (a.slot == ApparelSlot.OUTER && p.apparel.any { it.type.apparel?.slot == ApparelSlot.OUTER && (it.type.apparel!!.cover and a.cover) != 0 }) {
            val o = p.apparel.first { it.type.apparel?.slot == ApparelSlot.OUTER && (it.type.apparel!!.cover and a.cover) != 0 }
            if (apparelValue(s) <= (o.type.apparel!!.armorSharp * 2f + 0.1f) * o.quality.mult * 1.15f) continue
        }
        if (v > bs + 0.04f) { bs = v; best = s }
    }
    val s = best ?: return null
    val i = map.idx(s.x, s.y)
    reserve(p, key(i, K_ITEM))
    val j = Job(JobType.WEAR, s.x, s.y); j.key = key(i, K_ITEM)
    return j
}

// ---------------------------------------------------------------- work finders

internal fun Game.findFirefight(p: Pawn): Job? {
    if (map.fires.isEmpty()) return null
    var best = -1
    var bd = Int.MAX_VALUE
    for (i in map.fires.keys) {
        val k = key(i, K_DESIG)
        if (!isFree(p, k) || isBad(p, k)) continue
        // Do not run into raging infernos far from the colony.
        val d = abs(i % map.w - p.x) + abs(i / map.w - p.y)
        if (d < bd && d < 45) { bd = d; best = i }
    }
    if (best < 0) return null
    reserve(p, key(best, K_DESIG))
    val j = Job(JobType.FIREFIGHT, map.xOf(best), map.yOf(best)); j.key = key(best, K_DESIG)
    return j
}

internal fun Game.findDoctor(p: Pawn): Job? {
    // Rescue downed friendlies who are not in a bed.
    var best: Pawn? = null
    var bd = Int.MAX_VALUE
    for (o in pawns) {
        if (o === p || !o.alive || !o.downed || o.carriedBy >= 0 || o.hostile && !o.prisoner) continue
        if (o.faction != Faction.PLAYER) continue
        if (o.isAnimal && !o.tame) continue
        val k = pawnKey(o.id, K_PATIENT)
        if (!isFree(p, k) || isBad(p, k)) continue
        val bedHere = map.building[map.idx(o.x, o.y)]?.def?.sleeps == true
        if (bedHere) continue
        if (o.breakKind == Break.CATATONIC && o.breakUntil > tick) continue
        val d = abs(o.x - p.x) + abs(o.y - p.y)
        if (d < bd) { best = o; bd = d }
    }
    if (best != null && !best.isAnimal) {
        val bed = findBedFor(best, true)
        if (bed >= 0) {
            val k = pawnKey(best.id, K_PATIENT)
            reserve(p, k)
            val j = Job(JobType.RESCUE, best.x, best.y)
            j.targetPawn = best.id; j.key = k; j.dx = map.xOf(bed); j.dy = map.yOf(bed)
            return j
        }
    }
    // Tend.
    var patient: Pawn? = null
    bd = Int.MAX_VALUE
    for (o in pawns) {
        if (!o.alive || !o.untended || o.carriedBy >= 0) continue
        if (o.hostile && !o.prisoner) continue
        if (o.faction != Faction.PLAYER && o.faction != Faction.VISITOR) continue
        if (o.isAnimal && !o.tame) continue
        if (o.careLevel == 0 && !o.isAnimal) continue
        val k = pawnKey(o.id, K_PATIENT)
        if (!isFree(p, k) || isBad(p, k)) continue
        val d = abs(o.x - p.x) + abs(o.y - p.y) - (if (o.downed) 12 else 0) - (if (o.bleeding > 0.0001f) 15 else 0)
        if (d < bd) { patient = o; bd = d }
    }
    val o = patient ?: return null
    // Pick medicine per care policy.
    var med: ItemStack? = null
    if (o.careLevel > 0 && !o.isAnimal) {
        med = nearestItem(p, shared = true) {
            it.type.cat == ItemCat.MEDICINE && it.type.potency > 0f && (o.careLevel >= 2 || it.type == ItemType.MEDS_HERBAL) &&
                map.zoneKind(map.idx(it.x, it.y)) != ZoneKind.NONE || (it.type.potency > 0f && it.type.cat == ItemCat.MEDICINE && o.careLevel >= 2)
        }
        // Prefer the best medicine for serious wounds.
        if (med != null && (o.bleeding < 0.0002f && o.injuries.none { it.infection > 0f }) && med.type == ItemType.MEDS_INDUSTRIAL) {
            val herb = nearestItem(p, shared = true) { it.type == ItemType.MEDS_HERBAL }
            if (herb != null) med = herb
        }
    }
    val k = pawnKey(o.id, K_PATIENT)
    reserve(p, k)
    val j = Job(JobType.TEND, o.x, o.y)
    j.targetPawn = o.id; j.key = k
    if (med != null) { j.dx = med.x; j.dy = med.y; j.item = med.type; j.stage = 10; reserve(p, key(map.idx(med.x, med.y), K_ITEM)) }
    return j
}

internal fun Game.findClean(p: Pawn): Job? {
    val i = nearestCell(p, K_DESIG) { map.filth[it] > 0 && map.roofed(it) && map.zoneKind(it) != ZoneKind.GROWING }
    if (i < 0) return null
    reserve(p, key(i, K_DESIG))
    val j = Job(JobType.CLEAN, map.xOf(i), map.yOf(i)); j.key = key(i, K_DESIG)
    return j
}

private fun Game.cropOk(): Boolean = outdoorTemp() > 5f

internal fun Game.findGrow(p: Pawn): Job? {
    val h = nearestCell(p, K_PLANT) {
        val pl = map.plant[it]
        pl != null && pl.type.crop && pl.mature && map.zoneKind(it) == ZoneKind.GROWING
    }
    if (h >= 0) {
        reserve(p, key(h, K_PLANT))
        val j = Job(JobType.HARVEST, map.xOf(h), map.yOf(h)); j.key = key(h, K_PLANT)
        return j
    }
    val s = nearestCell(p, K_PLANT) {
        val z = map.zoneAt(it)
        if (z == null || z.kind != ZoneKind.GROWING || !z.sow || map.plant[it] != null) return@nearestCell false
        val bd = map.building[it]
        if (bd != null && bd.def != BuildDef.HYDROPONICS) return@nearestCell false
        val fert = if (bd?.def == BuildDef.HYDROPONICS && bd.powered) 2f else map.terrain[it].fertility
        if (fert <= 0f) return@nearestCell false
        val t = map.tempAt(it, outdoorTemp())
        t > max(6f, z.crop.minTemp) && t < z.crop.maxTemp - 3f && !map.fires.containsKey(it)
    }
    if (s >= 0) {
        reserve(p, key(s, K_PLANT))
        val j = Job(JobType.SOW, map.xOf(s), map.yOf(s)); j.key = key(s, K_PLANT)
        return j
    }
    return null
}

internal fun Game.findMine(p: Pawn): Job? {
    val i = nearestCell(p, K_DESIG) { map.desig[it].toInt() == Desig.MINE && map.terrain[it] == Terrain.ROCK }
    if (i < 0) return null
    reserve(p, key(i, K_DESIG))
    val j = Job(JobType.MINE, map.xOf(i), map.yOf(i)); j.key = key(i, K_DESIG)
    return j
}

internal fun Game.findCut(p: Pawn): Job? {
    val i = nearestCell(p, K_DESIG) {
        val d = map.desig[it].toInt()
        (d == Desig.CUT || d == Desig.HARVEST) && map.plant[it] != null
    }
    if (i < 0) return null
    reserve(p, key(i, K_DESIG))
    val j = Job(JobType.CUT, map.xOf(i), map.yOf(i)); j.key = key(i, K_DESIG)
    return j
}

internal fun Game.findResearch(p: Pawn): Job? {
    val cur = researchCurrent ?: return null
    if (cur in researchDone) return null
    val highTech = cur.tier >= 3
    val bench = nearestCell(p, K_STATION) {
        val b = map.building[it]
        b != null && b.built && (b.def == BuildDef.HI_TECH_BENCH && b.powered || b.def == BuildDef.RESEARCH_BENCH && !highTech) && !b.forbidden
    }
    if (bench < 0) return null
    reserve(p, key(bench, K_STATION))
    val j = Job(JobType.RESEARCH, map.xOf(bench), map.yOf(bench)); j.key = key(bench, K_STATION)
    return j
}

internal fun Game.findArt(p: Pawn): Job? {
    val i = nearestCell(p, K_BUILD) {
        val b = map.building[it]
        b != null && !b.built && b.def.art && materialsAvailable(b)
    }
    if (i < 0) return null
    reserve(p, key(i, K_BUILD))
    val j = Job(JobType.BUILD, map.xOf(i), map.yOf(i)); j.key = key(i, K_BUILD)
    return j
}

private fun Game.materialsAvailable(b: Building): Boolean {
    for ((k, c) in b.def.cost.withIndex()) {
        val need = b.missing(k)
        if (need > 0 && map.countItems(c.first) < need) return false
    }
    return true
}

internal fun Game.findConstruct(p: Pawn): Job? {
    val claims = HashMap<ItemType, Int>()
    for (o in pawns) {
        val oj = o.job ?: continue
        if (o === p || o.dead || oj.type != JobType.BUILD || oj.stage >= 3) continue
        val ob = map.building[map.idx(oj.tx, oj.ty)] ?: continue
        for ((k, c) in ob.def.cost.withIndex()) claims[c.first] = (claims[c.first] ?: 0) + ob.missing(k)
    }
    val stock = ItemType.entries.associateWith { map.countItems(it) - (claims[it] ?: 0) }
    val i = nearestCell(p, K_BUILD) {
        val b = map.building[it]
        if (b == null || b.built || b.forbidden) return@nearestCell false
        if (b.def.research != null && b.def.research !in researchDone) return@nearestCell false
        if (b.def.art) return@nearestCell false
        for ((k, c) in b.def.cost.withIndex()) if (b.missing(k) > 0 && (stock[c.first] ?: 0) < b.missing(k)) return@nearestCell false
        true
    }
    if (i >= 0) {
        reserve(p, key(i, K_BUILD))
        val j = Job(JobType.BUILD, map.xOf(i), map.yOf(i)); j.key = key(i, K_BUILD)
        return j
    }
    val d = nearestCell(p, K_DESIG) { map.desig[it].toInt() == Desig.DECON && (map.building[it]?.built == true || map.floor[it] != null || map.conduit[it]) }
    if (d >= 0) {
        reserve(p, key(d, K_DESIG))
        val j = Job(JobType.DECONSTRUCT, map.xOf(d), map.yOf(d)); j.key = key(d, K_DESIG)
        return j
    }
    val r = nearestCell(p, K_DESIG) {
        val b = map.building[it]
        map.desig[it].toInt() == Desig.REPAIR && b != null && b.built && b.hp < b.def.hp
    }
    if (r >= 0) {
        reserve(p, key(r, K_DESIG))
        val j = Job(JobType.REPAIR, map.xOf(r), map.yOf(r)); j.key = key(r, K_DESIG)
        return j
    }
    return null
}

internal fun Game.zoneBestFor(p: Pawn, s: ItemStack, from: Int): Int {
    // Highest-priority stockpile that accepts the item and has room; nearest within a priority.
    val fromZone = map.zoneAt(from)
    var best = -1
    var bp = if (fromZone != null && fromZone.accepts(s.type, s.quality)) fromZone.priority else -1
    var bd = Int.MAX_VALUE
    for (i in 0 until map.size) {
        if (map.zoneId[i] == 0 || i == from) continue
        val z = map.zones[map.zoneId[i]] ?: continue
        if (z.kind != ZoneKind.STOCKPILE && z.kind != ZoneKind.DUMPING) continue
        if (!z.accepts(s.type, s.quality)) continue
        if (z.priority < bp) continue
        if (fromZone != null && z.priority == bp && fromZone === z) continue
        val bd0 = map.building[i]
        if (bd0 != null && (bd0.def.blocksMove || !bd0.built)) continue
        val ex = map.items[i]
        if (ex != null && !(ex.type == s.type && ex.count < s.type.stack && ex.quality == s.quality && !ex.forbidden)) continue
        if (!map.dropCell(i)) continue
        val k = key(i, K_DEST)
        if (!isFree(p, k) || isBad(p, k)) continue
        if (z.priority == bp && fromZone != null && z.priority == fromZone.priority && fromZone.accepts(s.type, s.quality)) continue
        val d = abs(map.xOf(i) - s.x) + abs(map.yOf(i) - s.y)
        if (z.priority > bp || (z.priority == bp && d < bd)) {
            best = i; bp = z.priority; bd = d
        }
    }
    return best
}

internal fun Game.findHaul(p: Pawn): Job? {
    // Fuel first: campfires, torches, smithies, stoves and generators.
    val fire = nearestCell(p, K_STATION) {
        val b = map.building[it]
        b != null && b.built && b.def.fuelCap > 0f && b.fuel < b.def.fuelCap * 0.4f && !b.forbidden
    }
    if (fire >= 0 && map.countItems(ItemType.WOOD) >= 4) {
        val s = nearestItem(p) { it.type == ItemType.WOOD }
        if (s != null) {
            reserve(p, key(fire, K_STATION)); reserve(p, key(map.idx(s.x, s.y), K_ITEM))
            val j = Job(JobType.REFUEL, s.x, s.y)
            j.dx = map.xOf(fire); j.dy = map.yOf(fire); j.key = key(fire, K_STATION)
            return j
        }
    }
    // Mortars need shells.
    val mortar = nearestCell(p, K_STATION) { val b = map.building[it]; b != null && b.built && b.def == BuildDef.MORTAR && b.shells < 5 }
    if (mortar >= 0) {
        val s = nearestItem(p) { it.type == ItemType.SHELL }
        if (s != null) {
            reserve(p, key(mortar, K_STATION)); reserve(p, key(map.idx(s.x, s.y), K_ITEM))
            val j = Job(JobType.REFUEL, s.x, s.y); j.aux = 1
            j.dx = map.xOf(mortar); j.dy = map.yOf(mortar); j.key = key(mortar, K_STATION)
            return j
        }
    }
    // Corpses to graves or dumps.
    val corpse = map.items.values.filter { it.corpseOf != null && !it.forbidden && isFree(p, key(map.idx(it.x, it.y), K_ITEM)) && !isBad(p, key(map.idx(it.x, it.y), K_ITEM)) }
        .minByOrNull { abs(it.x - p.x) + abs(it.y - p.y) }
    if (corpse != null) {
        val ci = map.idx(corpse.x, corpse.y)
        val human = corpse.corpseRace == Race.HUMAN
        if (human) {
            val grave = nearestCell(p, K_DEST) { val b = map.building[it]; b != null && b.built && b.def == BuildDef.GRAVE && b.occupant == -1 }
            if (grave >= 0) {
                reserve(p, key(ci, K_ITEM)); reserve(p, key(grave, K_DEST))
                val j = Job(JobType.BURY, corpse.x, corpse.y); j.dx = map.xOf(grave); j.dy = map.yOf(grave); j.key = key(ci, K_ITEM)
                return j
            }
        }
        val dump = zoneBestFor(p, corpse, ci)
        if (dump >= 0 && map.zoneKind(ci) != ZoneKind.DUMPING && map.zoneAt(dump)?.accepts(ItemType.CORPSE_HUMAN, Quality.NORMAL) == true) {
            reserve(p, key(ci, K_ITEM)); reserve(p, key(dump, K_DEST))
            val j = Job(JobType.HAUL, corpse.x, corpse.y); j.dx = map.xOf(dump); j.dy = map.yOf(dump); j.key = key(ci, K_ITEM); j.aux = 7
            return j
        }
    }
    // Loose items to the best stockpile, meals first. Check the nearest few.
    val candidates = map.items.values.filter { it.corpseOf == null && !it.forbidden }
        .sortedBy { abs(it.x - p.x) + abs(it.y - p.y) - (if (it.type.cat == ItemCat.FOOD_MEAL) 15 else 0) }
    var tries = 0
    for (s in candidates) {
        if (tries++ > 25) break
        val i = map.idx(s.x, s.y)
        val k = key(i, K_ITEM)
        if (!isFree(p, k) || isBad(p, k)) continue
        val dest = zoneBestFor(p, s, i)
        if (dest < 0) continue
        reserve(p, k); reserve(p, key(dest, K_DEST))
        val j = Job(JobType.HAUL, s.x, s.y)
        j.dx = map.xOf(dest); j.dy = map.yOf(dest); j.key = k
        return j
    }
    return null
}

// ---------------------------------------------------------------- bills

internal fun Game.billRunnable(b: Building, bill: Bill, p: Pawn): Boolean {
    if (bill.paused) return false
    val r = bill.recipe
    if (!r.available(researchDone)) return false
    if (p.level(r.workType.skill() ?: SkillType.CRAFTING) < max(bill.minSkill, r.minSkill)) return false
    when (bill.mode) {
        BillMode.DO_X -> if (bill.done >= bill.target) return false
        BillMode.UNTIL_HAVE -> {
            val have = map.items.values.filter { it.type == r.out && it.corpseOf == null && map.zoneKind(map.idx(it.x, it.y)) != ZoneKind.NONE }.sumOf { it.count }
            if (have >= bill.target) return false
        }
        BillMode.FOREVER -> {}
    }
    for (ing in r.inputs) {
        var have = 0
        for (s in map.items.values) {
            if (s.forbidden || s.corpseOf != null || !ing.accepts(s.type)) continue
            if (bill.allowedItems != null && s.type !in bill.allowedItems!!) continue
            if (s.rot > 0.8f && s.type.spoilDays > 0f) continue
            if (isBad(p, key(map.idx(s.x, s.y), K_ITEM))) continue
            if (!isFree(p, key(map.idx(s.x, s.y), K_ITEM))) continue
            have += s.count
            if (have >= ing.count) break
        }
        if (have < ing.count) return false
    }
    return true
}

internal fun Game.findBill(p: Pawn, wt: WorkType): Job? {
    var best: Job? = null
    var bd = Int.MAX_VALUE
    for (b in map.building) {
        if (b == null || !b.built || !b.def.workbench || b.bills.isEmpty() || b.forbidden) continue
        if (b.def.consumesPower && !b.powered) continue
        val i = map.idx(b.x, b.y)
        val k = key(i, K_STATION)
        if (!isFree(p, k) || isBad(p, k)) continue
        // Fuelled benches need fuel to be useful.
        if (b.def.fuelCap > 0f && b.fuel <= 0.5f && b.def != BuildDef.TORCH_LAMP) continue
        val d = abs(b.x - p.x) + abs(b.y - p.y)
        if (d >= bd) continue
        for ((bi, bill) in b.bills.withIndex()) {
            if (bill.recipe.workType != wt) continue
            if (b.def !in bill.recipe.benches) continue
            if (!billRunnable(b, bill, p)) continue
            val j = Job(JobType.BILL, b.x, b.y)
            j.billIndex = bi; j.bench = i; j.key = k
            best = j; bd = d
            break
        }
    }
    val j = best ?: return null
    reserve(p, j.key)
    return j
}

internal fun Game.findButcher(p: Pawn): Job? {
    val table = nearestCell(p, K_STATION) { val b = map.building[it]; b != null && b.built && b.def == BuildDef.BUTCHER_TABLE && !b.forbidden }
    if (table < 0) return null
    val corpse = map.items.values.filter {
        it.corpseOf != null && it.corpseRace != null && it.corpseRace != Race.HUMAN && !it.forbidden && it.rot < 0.7f &&
            isFree(p, key(map.idx(it.x, it.y), K_ITEM)) && !isBad(p, key(map.idx(it.x, it.y), K_ITEM)) && (it.corpseRace?.meat ?: 0) > 0
    }.minByOrNull { abs(it.x - p.x) + abs(it.y - p.y) } ?: return null
    reserve(p, key(map.idx(corpse.x, corpse.y), K_ITEM)); reserve(p, key(table, K_STATION))
    val j = Job(JobType.BUTCHER, corpse.x, corpse.y)
    j.dx = map.xOf(table); j.dy = map.yOf(table); j.key = key(map.idx(corpse.x, corpse.y), K_ITEM)
    return j
}

/** Puts a (corpse or other single) stack back on the map near a cell. */
fun Game.placeStack(s: ItemStack, x: Int, y: Int) {
    for (r in 0..8) for (dy in -r..r) for (dx in -r..r) {
        if (max(abs(dx), abs(dy)) != r) continue
        val nx = x + dx; val ny = y + dy
        if (!map.inB(nx, ny)) continue
        val i = map.idx(nx, ny)
        if (!map.dropCell(i) || map.items[i] != null) continue
        s.x = nx; s.y = ny
        map.items[i] = s
        return
    }
}

fun Game.rollQuality(skill: Int, art: Boolean = false): Quality {
    val x = skill + (rng.float() + rng.float() + rng.float() - 1.5f) * 6f + (if (art) 1f else 0f)
    return when {
        x < 1f -> Quality.AWFUL
        x < 4f -> Quality.POOR
        x < 9f -> Quality.NORMAL
        x < 13f -> Quality.GOOD
        x < 17f -> Quality.EXCELLENT
        x < 21f -> Quality.MASTERWORK
        else -> Quality.LEGENDARY
    }
}
