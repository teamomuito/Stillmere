package io.github.teamomuito.colony.sim

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private const val K_ITEM = 0
private const val K_BUILD = 1
private const val K_PLANT = 2
private const val K_DESIG = 3
private const val K_DEST = 4
private const val K_BED = 5
private const val K_PATIENT = 6
private const val K_STATION = 7

private fun key(i: Int, kind: Int) = i * 8 + kind

fun Game.endJob(p: Pawn) {
    releaseAll(p)
    if (p.carryCount > 0 && p.carryType != null) map.drop(p.carryType!!, p.carryCount, p.x, p.y)
    p.carryCount = 0; p.carryType = null
    p.job = null
    p.clearPath()
}

private fun Game.unreserve(p: Pawn, k: Int) {
    if (reservations[k] == p.id) reservations.remove(k)
    p.reserved.remove(k)
}

private fun Game.markUnreachable(p: Pawn, k: Int) { unreachable[p.id * 10_000_000L + k] = tick + 900 }
private fun Game.isBad(p: Pawn, k: Int): Boolean {
    val e = unreachable[p.id * 10_000_000L + k] ?: return false
    return e > tick
}

private fun Game.nearestCell(p: Pawn, kind: Int, pred: (Int) -> Boolean): Int {
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

private fun Game.nearestItem(p: Pawn, types: List<ItemType>, minCount: Int = 1, skip: Int = -1, shared: Boolean = false): ItemStack? {
    var best: ItemStack? = null
    var bd = Int.MAX_VALUE
    for (s in map.items.values) {
        if (s.type !in types || s.count < minCount) continue
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

private fun Game.goalReached(p: Pawn, tx: Int, ty: Int, adjacent: Boolean): Boolean =
    if (adjacent) max(abs(p.x - tx), abs(p.y - ty)) <= 1 && (p.x != tx || p.y != ty) else p.x == tx && p.y == ty

/** @return 1 while moving, 0 on arrival, -1 if there is no way there. */
fun Game.goTo(p: Pawn, tx: Int, ty: Int, adjacent: Boolean = false, breach: Boolean = false): Int {
    if (p.moveCd > 0) { p.moveCd--; return 1 }
    if (goalReached(p, tx, ty, adjacent)) { p.clearPath(); return 0 }
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
    p.fromX = p.x; p.fromY = p.y
    p.x = map.xOf(next); p.y = map.yOf(next)
    p.pathI++
    p.moveTotal = max(3, p.moveSpeedTicks() * map.stepCost(next) / 10)
    p.moveCd = p.moveTotal
    return 1
}

private fun Game.attackBuilding(p: Pawn, b: Building) {
    if (p.attackCd > 0) return
    p.attackCd = 40
    b.hp -= if (p.weapon.ranged) 6f else p.weapon.damage
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
        val d = distance(p.x, p.y, h.x, h.y)
        if (d < 5f) return true
        if (d < 11f && map.lineOfSight(h.x, h.y, p.x, p.y)) return true
    }
    return false
}

fun Game.think(p: Pawn) {
    if (p.hostile) { p.job = Job(if (p.retreating) JobType.LEAVE else JobType.RAID); return }
    if (p.breakUntil > tick) { p.job = Job(JobType.BREAK); return }
    if (p.drafted) return
    if (autoFightTarget(p) != null) { p.job = Job(JobType.ATTACK); return }
    if (threatNear(p)) { p.job = Job(JobType.FLEE); return }
    if (p.food < 0.3f && startEat(p)) return
    if ((p.rest < 0.25f || (isSleepHour && p.rest < 0.9f)) && startSleep(p)) return
    val order = WorkType.entries.filter { p.priority[it.ordinal] > 0 }
        .sortedBy { p.priority[it.ordinal] * 10 + it.ordinal }
    for (w in order) {
        val j = tryWork(p, w) ?: continue
        p.job = j
        return
    }
    if (p.food < 0.55f && startEat(p)) return
    val j = Job(if (rng.chance(0.35f)) JobType.WANDER else JobType.IDLE)
    j.timer = rng.range(40, 90)
    p.job = j
}

private fun Game.startEat(p: Pawn): Boolean {
    val s = nearestItem(p, listOf(ItemType.MEAL), shared = true) ?: nearestItem(p, listOf(ItemType.RAW_FOOD), shared = true) ?: return false
    val j = Job(JobType.EAT, s.x, s.y)
    j.key = key(map.idx(s.x, s.y), K_ITEM)
    p.job = j
    return true
}

private fun Game.startSleep(p: Pawn): Boolean {
    var bed = -1
    if (p.bedId >= 0) {
        val b = map.building[p.bedId]
        if (b != null && b.built && b.def == BuildDef.BED && b.ownerId == p.id) bed = p.bedId else p.bedId = -1
    }
    if (bed < 0) {
        bed = nearestCell(p, K_BED) { val b = map.building[it]; b != null && b.built && b.def == BuildDef.BED && b.ownerId == -1 }
        if (bed >= 0) {
            map.building[bed]!!.ownerId = p.id
            p.bedId = bed
        }
    }
    val j = Job(JobType.SLEEP)
    if (bed >= 0) {
        j.tx = map.xOf(bed); j.ty = map.yOf(bed)
        j.amount = 1
    } else {
        j.stage = 1
        j.amount = 0
    }
    p.job = j
    return true
}

private fun Game.tryWork(p: Pawn, w: WorkType): Job? = when (w) {
    WorkType.DOCTOR -> findDoctor(p)
    WorkType.COOK -> findCook(p)
    WorkType.CONSTRUCT -> findConstruct(p)
    WorkType.GROW -> findGrow(p)
    WorkType.MINE -> findMine(p)
    WorkType.PLANT_CUT -> findCut(p)
    WorkType.HAUL -> findHaul(p)
    WorkType.RESEARCH -> findResearch(p)
}

private fun Game.findDoctor(p: Pawn): Job? {
    var best: Pawn? = null
    var bd = Int.MAX_VALUE
    for (o in pawns) {
        if (!o.alive || o.hostile || !o.untended) continue
        val k = 1_000_000 + o.id * 8 + K_PATIENT
        if (!isFree(p, k) || isBad(p, k)) continue
        val d = abs(o.x - p.x) + abs(o.y - p.y) - (if (o.downed) 10 else 0)
        if (d < bd) { best = o; bd = d }
    }
    val o = best ?: return null
    val k = 1_000_000 + o.id * 8 + K_PATIENT
    reserve(p, k)
    val j = Job(JobType.TEND, o.x, o.y)
    j.targetPawn = o.id; j.key = k
    return j
}

private fun Game.findCook(p: Pawn): Job? {
    val meals = map.countItems(ItemType.MEAL)
    if (meals >= colonists.size * 5) return null
    if (map.countItems(ItemType.RAW_FOOD) < 5) return null
    val stove = nearestCell(p, K_STATION) { val b = map.building[it]; b != null && b.built && b.def == BuildDef.STOVE }
    if (stove < 0) return null
    reserve(p, key(stove, K_STATION))
    val j = Job(JobType.COOK, map.xOf(stove), map.yOf(stove))
    j.key = key(stove, K_STATION)
    return j
}

private fun Game.findConstruct(p: Pawn): Job? {
    // Materials other builders are already fetching can't be promised twice.
    val claims = HashMap<ItemType, Int>()
    for (o in pawns) {
        val oj = o.job ?: continue
        if (o === p || o.dead || oj.type != JobType.BUILD || oj.stage >= 3) continue
        val ob = map.building[map.idx(oj.tx, oj.ty)] ?: continue
        claims[ob.def.item] = (claims[ob.def.item] ?: 0) + max(0, ob.def.count - ob.delivered)
    }
    val stock = ItemType.entries.associateWith { map.countItems(it) - (claims[it] ?: 0) }
    val i = nearestCell(p, K_BUILD) {
        val b = map.building[it]
        b != null && !b.built && (b.def.research == null || b.def.research in researchDone) &&
            (b.delivered >= b.def.count || (stock[b.def.item] ?: 0) >= b.def.count - b.delivered)
    }
    if (i >= 0) {
        reserve(p, key(i, K_BUILD))
        val j = Job(JobType.BUILD, map.xOf(i), map.yOf(i))
        j.key = key(i, K_BUILD)
        return j
    }
    val d = nearestCell(p, K_DESIG) { map.desig[it].toInt() == Desig.DECON && (map.building[it]?.built == true || map.floor[it] != null) }
    if (d >= 0) {
        reserve(p, key(d, K_DESIG))
        val j = Job(JobType.DECONSTRUCT, map.xOf(d), map.yOf(d))
        j.key = key(d, K_DESIG)
        return j
    }
    return null
}

private fun Game.cropOk(): Boolean = outdoorTemp() > 5f

private fun Game.findGrow(p: Pawn): Job? {
    val h = nearestCell(p, K_PLANT) {
        val pl = map.plant[it]
        pl != null && pl.type.crop && pl.mature && map.zone[it] == ZoneKind.GROWING.toByte()
    }
    if (h >= 0) {
        reserve(p, key(h, K_PLANT))
        val j = Job(JobType.HARVEST, map.xOf(h), map.yOf(h))
        j.key = key(h, K_PLANT)
        return j
    }
    if (!cropOk() || (season == Season.WINTER && tempOffset <= -10f)) return null
    val s = nearestCell(p, K_PLANT) {
        map.zone[it] == ZoneKind.GROWING.toByte() && map.plant[it] == null && map.building[it] == null &&
            map.terrain[it].fertility > 0f && map.tempAt(it, outdoorTemp()) > 6f
    }
    if (s >= 0) {
        reserve(p, key(s, K_PLANT))
        val j = Job(JobType.SOW, map.xOf(s), map.yOf(s))
        j.key = key(s, K_PLANT)
        return j
    }
    return null
}

private fun Game.findMine(p: Pawn): Job? {
    val i = nearestCell(p, K_DESIG) { map.desig[it].toInt() == Desig.MINE && map.terrain[it] == Terrain.ROCK }
    if (i < 0) return null
    reserve(p, key(i, K_DESIG))
    val j = Job(JobType.MINE, map.xOf(i), map.yOf(i))
    j.key = key(i, K_DESIG)
    return j
}

private fun Game.findCut(p: Pawn): Job? {
    val i = nearestCell(p, K_DESIG) { map.desig[it].toInt() == Desig.CUT && map.plant[it] != null }
    if (i < 0) return null
    reserve(p, key(i, K_DESIG))
    val j = Job(JobType.CUT, map.xOf(i), map.yOf(i))
    j.key = key(i, K_DESIG)
    return j
}

private fun Game.stockpileCellFor(p: Pawn, type: ItemType, from: Int): Int =
    nearestCell(p, K_DEST) {
        if (map.zone[it].toInt() != ZoneKind.STOCKPILE || it == from) return@nearestCell false
        val b = map.building[it]
        if (b != null && (b.def.blocksMove || !b.built)) return@nearestCell false
        val s = map.items[it]
        s == null || (s.type == type && s.count < type.stack)
    }

private fun Game.findHaul(p: Pawn): Job? {
    // Fuel campfires first.
    val fire = nearestCell(p, K_STATION) { val b = map.building[it]; b != null && b.built && b.def == BuildDef.CAMPFIRE && b.fuel < 8f }
    if (fire >= 0 && map.countItems(ItemType.WOOD) >= 6) {
        val s = nearestItem(p, listOf(ItemType.WOOD))
        if (s != null) {
            reserve(p, key(fire, K_STATION)); reserve(p, key(map.idx(s.x, s.y), K_ITEM))
            val j = Job(JobType.REFUEL, s.x, s.y)
            j.dx = map.xOf(fire); j.dy = map.yOf(fire); j.key = key(fire, K_STATION)
            return j
        }
    }
    // Loose items to the stockpile, meals first.
    var best: ItemStack? = null
    var bs = Int.MAX_VALUE
    for (s in map.items.values) {
        val i = map.idx(s.x, s.y)
        if (map.zone[i].toInt() == ZoneKind.STOCKPILE) continue
        val k = key(i, K_ITEM)
        if (!isFree(p, k) || isBad(p, k)) continue
        val score = abs(s.x - p.x) + abs(s.y - p.y) - (if (s.type == ItemType.MEAL) 15 else 0)
        if (score < bs) { best = s; bs = score }
    }
    val s = best ?: return null
    val i = map.idx(s.x, s.y)
    val dest = stockpileCellFor(p, s.type, i)
    if (dest < 0) return null
    reserve(p, key(i, K_ITEM)); reserve(p, key(dest, K_DEST))
    val j = Job(JobType.HAUL, s.x, s.y)
    j.dx = map.xOf(dest); j.dy = map.yOf(dest); j.key = key(i, K_ITEM)
    return j
}

private fun Game.findResearch(p: Pawn): Job? {
    val cur = researchCurrent ?: return null
    if (cur in researchDone) return null
    val bench = nearestCell(p, K_STATION) { val b = map.building[it]; b != null && b.built && b.def == BuildDef.RESEARCH_BENCH }
    if (bench < 0) return null
    reserve(p, key(bench, K_STATION))
    val j = Job(JobType.RESEARCH, map.xOf(bench), map.yOf(bench))
    j.key = key(bench, K_STATION)
    return j
}

// ---------------------------------------------------------------- driving

private fun Game.abort(p: Pawn, markBad: Boolean = false) {
    val j = p.job
    if (markBad && j != null && j.key >= 0) markUnreachable(p, j.key)
    endJob(p)
}

private fun Game.doWork(p: Pawn, j: Job, skill: SkillType, total: Float): Boolean {
    j.work += p.workSpeed(skill)
    p.gainXp(skill, 0.07f)
    return j.work >= total
}

private fun Game.pickUp(p: Pawn, i: Int, type: ItemType, n: Int): Int {
    val s = map.items[i] ?: return 0
    if (s.type != type) return 0
    val got = map.take(i, min(n, s.count))
    p.carryType = type
    p.carryCount += got
    return got
}

fun Game.driveJob(p: Pawn) {
    if (p.hostile) { hostileAI(p); return }
    if (p.drafted) { draftedAI(p); return }
    val j = p.job ?: return
    when (j.type) {
        JobType.IDLE -> { if (--j.timer <= 0) endJob(p) }
        JobType.WANDER -> {
            if (j.stage == 0) {
                val home = map.idx(p.x, p.y)
                for (t in 0 until 8) {
                    val x = (homeX + rng.range(-9, 9)).coerceIn(1, map.w - 2)
                    val y = (homeY + rng.range(-9, 9)).coerceIn(1, map.h - 2)
                    if (map.walkable(map.idx(x, y)) && map.terrain[map.idx(x, y)] != Terrain.WATER_SHALLOW) { j.tx = x; j.ty = y; j.stage = 1; break }
                }
                if (j.stage == 0 || home < 0) endJob(p)
            } else if (goTo(p, j.tx, j.ty) != 1) endJob(p)
        }
        JobType.BREAK -> {
            if (j.stage == 0 || goTo(p, j.tx, j.ty) != 1) {
                val x = (p.x + rng.range(-8, 8)).coerceIn(1, map.w - 2)
                val y = (p.y + rng.range(-8, 8)).coerceIn(1, map.h - 2)
                if (map.walkable(map.idx(x, y))) { j.tx = x; j.ty = y; j.stage = 1 }
            }
        }
        JobType.MOVE -> { if (goTo(p, j.tx, j.ty) != 1) { p.job = null } }
        JobType.FLEE -> driveFlee(p, j)
        JobType.ATTACK -> {
            val t = autoFightTarget(p)
            if (t == null) endJob(p) else fire(p, t)
        }
        JobType.EAT -> driveEat(p, j)
        JobType.SLEEP -> driveSleep(p, j)
        JobType.MINE -> driveMine(p, j)
        JobType.CUT, JobType.HARVEST -> driveCut(p, j)
        JobType.SOW -> driveSow(p, j)
        JobType.HAUL -> driveHaul(p, j)
        JobType.REFUEL -> driveRefuel(p, j)
        JobType.BUILD -> driveBuild(p, j)
        JobType.DECONSTRUCT -> driveDecon(p, j)
        JobType.COOK -> driveCook(p, j)
        JobType.RESEARCH -> driveResearch(p, j)
        JobType.TEND -> driveTend(p, j)
        else -> endJob(p)
    }
}

private fun Game.driveFlee(p: Pawn, j: Job) {
    if (j.timer % 40 == 0 || j.stage == 0) {
        var bx = p.x; var by = p.y; var bs = -1e9f
        val threats = hostiles.filter { !it.downed }
        for (t in 0 until 14) {
            val x = (p.x + rng.range(-14, 14)).coerceIn(1, map.w - 2)
            val y = (p.y + rng.range(-14, 14)).coerceIn(1, map.h - 2)
            if (!map.walkable(map.idx(x, y))) continue
            var md = 99f
            for (h in threats) md = min(md, distance(x, y, h.x, h.y))
            val score = md - 0.35f * distance(x, y, p.x, p.y)
            if (score > bs) { bs = score; bx = x; by = y }
        }
        j.tx = bx; j.ty = by; j.stage = 1
    }
    j.timer++
    goTo(p, j.tx, j.ty)
    if (!threatNear(p) && j.timer > 120) endJob(p)
    if (j.timer > 2400) endJob(p)
}

private fun Game.driveEat(p: Pawn, j: Job) {
    when (j.stage) {
        0 -> {
            val s = map.items[map.idx(j.tx, j.ty)]
            if (s == null || (s.type != ItemType.MEAL && s.type != ItemType.RAW_FOOD)) { abort(p); return }
            when (goTo(p, j.tx, j.ty)) {
                -1 -> abort(p, true)
                0 -> {
                    val i = map.idx(j.tx, j.ty)
                    val want = if (s.type == ItemType.MEAL) 1 else ceil((1f - p.food) / s.type.nutrition).toInt()
                    pickUp(p, i, s.type, max(1, want))
                    unreserve(p, j.key)
                    j.stage = 1
                }
            }
        }
        1 -> {
            val table = nearestCell(p, K_STATION) { val b = map.building[it]; b != null && b.built && b.def == BuildDef.TABLE && abs(map.xOf(it) - p.x) + abs(map.yOf(it) - p.y) < 30 }
            if (table < 0) { j.stage = 2; j.timer = 0; return }
            j.dx = map.xOf(table); j.dy = map.yOf(table)
            j.stage = 11
        }
        11 -> when (goTo(p, j.dx, j.dy, adjacent = true)) { 1 -> {}; else -> { j.stage = 2; j.timer = 0 } }
        2 -> {
            val t = p.carryType
            if (t == null || p.carryCount == 0) { endJob(p); return }
            j.timer++
            val total = if (t == ItemType.MEAL) 120 else 40 + p.carryCount * 3
            if (j.timer >= total) {
                p.food = min(1f, p.food + t.nutrition * p.carryCount)
                if (t == ItemType.MEAL) {
                    val atTable = (-1..1).any { dy -> (-1..1).any { dx ->
                        val x = p.x + dx; val y = p.y + dy
                        map.inB(x, y) && map.building[map.idx(x, y)]?.let { it.built && it.def == BuildDef.TABLE } == true
                    } }
                    if (atTable) p.addThought("Ate at a table", 0.03f, tick, TICKS_PER_DAY)
                    else p.addThought("Ate without a table", -0.03f, tick, TICKS_PER_DAY)
                } else p.addThought("Ate raw food", -0.05f, tick, (1.5f * TICKS_PER_DAY).toInt())
                p.carryCount = 0; p.carryType = null
                endJob(p)
            }
        }
    }
}

private fun Game.driveSleep(p: Pawn, j: Job) {
    if (threatNear(p)) { endJob(p); return }
    if (j.stage == 0) {
        val b = map.building[map.idx(j.tx, j.ty)]
        if (b == null || !b.built || b.def != BuildDef.BED) { p.bedId = -1; endJob(p); return }
        val r = goTo(p, j.tx, j.ty)
        if (r == -1) { endJob(p); return }
        if (r == 0) j.stage = 1
        return
    }
    val inBed = j.amount == 1
    p.rest = min(1f, p.rest + if (inBed) 0.00012f else 0.00008f)
    j.timer++
    val done = p.rest >= 0.98f || (!isSleepHour && p.rest > 0.8f)
    if (done) {
        val i = map.idx(p.x, p.y)
        if (!inBed) p.addThought("Slept on the ground", -0.04f, tick, TICKS_PER_DAY)
        if (!map.roomIndoorAt(i)) p.addThought("Slept outside", -0.07f, tick, TICKS_PER_DAY)
        endJob(p)
    }
}

private fun Game.driveMine(p: Pawn, j: Job) {
    val i = map.idx(j.tx, j.ty)
    if (map.terrain[i] != Terrain.ROCK || map.desig[i].toInt() != Desig.MINE) { endJob(p); return }
    if (j.stage == 0) {
        when (goTo(p, j.tx, j.ty, adjacent = true)) {
            -1 -> abort(p, true)
            0 -> j.stage = 1
        }
        return
    }
    val total = if (map.ore[i]) 1500f else 1000f
    if (doWork(p, j, SkillType.MINING, total)) {
        if (map.ore[i]) map.drop(ItemType.STEEL, rng.range(14, 22), p.x, p.y)
        else if (rng.chance(0.85f)) map.drop(ItemType.STONE, rng.range(6, 10), p.x, p.y)
        map.ore[i] = false
        map.terrain[i] = Terrain.GRAVEL
        map.desig[i] = 0
        map.roomDirty = true
        endJob(p)
    }
}

private fun Game.driveCut(p: Pawn, j: Job) {
    val i = map.idx(j.tx, j.ty)
    val pl = map.plant[i]
    if (pl == null || (j.type == JobType.CUT && map.desig[i].toInt() != Desig.CUT)) { endJob(p); return }
    if (j.stage == 0) {
        val r = goTo(p, j.tx, j.ty, adjacent = pl.type == PlantType.TREE)
        if (r == -1) abort(p, true) else if (r == 0) j.stage = 1
        return
    }
    if (doWork(p, j, SkillType.PLANTS, pl.type.harvestWork.toFloat())) {
        val yt = pl.type.yieldType
        if (yt != null) map.drop(yt, pl.type.yieldCount, p.x, p.y)
        map.plant[i] = null
        map.desig[i] = 0
        map.roomDirty = true
        endJob(p)
    }
}

private fun Game.driveSow(p: Pawn, j: Job) {
    val i = map.idx(j.tx, j.ty)
    if (map.plant[i] != null || map.zone[i] != ZoneKind.GROWING.toByte()) { endJob(p); return }
    if (j.stage == 0) {
        val r = goTo(p, j.tx, j.ty)
        if (r == -1) abort(p, true) else if (r == 0) j.stage = 1
        return
    }
    val crop = PlantType.entries[map.zoneCrop[i].toInt()]
    if (doWork(p, j, SkillType.PLANTS, crop.sowWork.toFloat())) {
        map.plant[i] = Plant(crop, j.tx, j.ty, 0.02f)
        endJob(p)
    }
}

private fun Game.driveHaul(p: Pawn, j: Job) {
    when (j.stage) {
        0 -> {
            val i = map.idx(j.tx, j.ty)
            val s = map.items[i]
            if (s == null) { endJob(p); return }
            val r = goTo(p, j.tx, j.ty)
            if (r == -1) abort(p, true)
            else if (r == 0) {
                pickUp(p, i, s.type, s.type.stack)
                unreserve(p, j.key)
                j.stage = 1
            }
        }
        1 -> {
            val r = goTo(p, j.dx, j.dy)
            if (r == -1) { endJob(p); return }
            if (r == 0) {
                val t = p.carryType
                if (t != null && p.carryCount > 0) {
                    val left = map.drop(t, p.carryCount, j.dx, j.dy)
                    p.carryCount = left
                    if (left == 0) p.carryType = null
                }
                endJob(p)
            }
        }
    }
}

private fun Game.driveRefuel(p: Pawn, j: Job) {
    when (j.stage) {
        0 -> {
            val i = map.idx(j.tx, j.ty)
            val s = map.items[i]
            if (s == null || s.type != ItemType.WOOD) { endJob(p); return }
            val r = goTo(p, j.tx, j.ty)
            if (r == -1) abort(p, true)
            else if (r == 0) { pickUp(p, i, ItemType.WOOD, 6); j.stage = 1 }
        }
        1 -> {
            val b = map.building[map.idx(j.dx, j.dy)]
            if (b == null || b.def != BuildDef.CAMPFIRE) { endJob(p); return }
            val r = goTo(p, j.dx, j.dy)
            if (r == -1) { endJob(p); return }
            if (r == 0) {
                b.fuel = min(24f, b.fuel + p.carryCount * 4f)
                p.carryCount = 0; p.carryType = null
                endJob(p)
            }
        }
    }
}

private fun Game.ejectPawns(i: Int) {
    for (o in pawns) {
        if (!o.alive || map.idx(o.x, o.y) != i) continue
        for (d in 0 until 8) {
            val nx = o.x + GameMap.DX8[d]; val ny = o.y + GameMap.DY8[d]
            if (map.inB(nx, ny) && map.walkable(map.idx(nx, ny))) { o.x = nx; o.y = ny; o.fromX = nx; o.fromY = ny; o.clearPath(); break }
        }
    }
}

private fun Game.driveBuild(p: Pawn, j: Job) {
    val i = map.idx(j.tx, j.ty)
    val b = map.building[i]
    if (b == null || b.built) { endJob(p); return }
    when (j.stage) {
        0 -> {
            if (b.delivered >= b.def.count) { j.stage = 3; return }
            val s = nearestItem(p, listOf(b.def.item))
            if (s == null) { abort(p, true); return }
            reserve(p, key(map.idx(s.x, s.y), K_ITEM))
            j.dx = s.x; j.dy = s.y
            j.stage = 1
        }
        1 -> {
            val ii = map.idx(j.dx, j.dy)
            val s = map.items[ii]
            if (s == null || s.type != b.def.item) { j.stage = 0; return }
            val r = goTo(p, j.dx, j.dy)
            if (r == -1) abort(p, true)
            else if (r == 0) {
                pickUp(p, ii, b.def.item, min(b.def.count - b.delivered, 75))
                unreserve(p, key(ii, K_ITEM))
                j.stage = 2
            }
        }
        2 -> {
            val r = goTo(p, j.tx, j.ty, adjacent = true)
            if (r == -1) abort(p, true)
            else if (r == 0) {
                b.delivered += p.carryCount
                p.carryCount = 0; p.carryType = null
                j.stage = if (b.delivered >= b.def.count) 3 else 0
            }
        }
        3 -> {
            val r = goTo(p, j.tx, j.ty, adjacent = true)
            if (r == -1) abort(p, true) else if (r == 0) j.stage = 4
        }
        4 -> {
            b.progress = j.work
            if (doWork(p, j, SkillType.CONSTRUCTION, b.def.work.toFloat())) {
                if (b.def.isFloor) {
                    map.floor[i] = b.def
                    map.building[i] = null
                } else {
                    b.built = true
                    b.hp = b.def.hp
                    if (b.def == BuildDef.CAMPFIRE) b.fuel = 0f
                    if (b.def == BuildDef.SHIP) { shipBuilt = true; say("The escape ship is ready! Open the Ship panel to launch.", 1) }
                    if (b.def.blocksMove || b.def.isWall || b.def.isDoor) ejectPawns(i)
                }
                map.roomDirty = true
                endJob(p)
            }
        }
    }
}

private fun Game.driveDecon(p: Pawn, j: Job) {
    val i = map.idx(j.tx, j.ty)
    val b = map.building[i]
    val fl = map.floor[i]
    if (map.desig[i].toInt() != Desig.DECON || (b == null && fl == null)) { endJob(p); return }
    if (j.stage == 0) {
        val r = goTo(p, j.tx, j.ty, adjacent = true)
        if (r == -1) abort(p, true) else if (r == 0) j.stage = 1
        return
    }
    val def = b?.def ?: fl!!
    if (doWork(p, j, SkillType.CONSTRUCTION, def.work * 0.5f)) {
        map.drop(def.item, max(1, def.count / 2), p.x, p.y)
        if (b != null) map.building[i] = null else map.floor[i] = null
        map.desig[i] = 0
        map.roomDirty = true
        endJob(p)
    }
}

private fun Game.driveCook(p: Pawn, j: Job) {
    val stove = map.building[map.idx(j.tx, j.ty)]
    if (stove == null || !stove.built) { endJob(p); return }
    when (j.stage) {
        0 -> {
            if (p.carryCount >= 5 || p.carryCount >= 10) { j.stage = 2; return }
            val s = nearestItem(p, listOf(ItemType.RAW_FOOD)) ?: run {
                if (p.carryCount >= 5) j.stage = 2 else endJob(p)
                return
            }
            reserve(p, key(map.idx(s.x, s.y), K_ITEM))
            j.dx = s.x; j.dy = s.y; j.stage = 1
        }
        1 -> {
            val ii = map.idx(j.dx, j.dy)
            val s = map.items[ii]
            if (s == null || s.type != ItemType.RAW_FOOD) { j.stage = 0; return }
            val r = goTo(p, j.dx, j.dy)
            if (r == -1) abort(p, true)
            else if (r == 0) {
                pickUp(p, ii, ItemType.RAW_FOOD, 10 - p.carryCount)
                unreserve(p, key(ii, K_ITEM))
                j.stage = 0
            }
        }
        2 -> {
            val r = goTo(p, j.tx, j.ty, adjacent = true)
            if (r == -1) abort(p, true) else if (r == 0) { j.stage = 3; j.work = 0f }
        }
        3 -> {
            if (p.carryCount < 5) { endJob(p); return }
            if (doWork(p, j, SkillType.COOKING, 400f)) {
                p.carryCount -= 5
                map.drop(ItemType.MEAL, 1, p.x, p.y)
                j.work = 0f
                if (map.countItems(ItemType.MEAL) >= colonists.size * 5) endJob(p)
            }
        }
    }
}

private fun Game.driveResearch(p: Pawn, j: Job) {
    val bench = map.building[map.idx(j.tx, j.ty)]
    val cur = researchCurrent
    if (bench == null || !bench.built || cur == null) { endJob(p); return }
    if (j.stage == 0) {
        val r = goTo(p, j.tx, j.ty, adjacent = true)
        if (r == -1) abort(p, true) else if (r == 0) j.stage = 1
        return
    }
    p.gainXp(SkillType.INTELLECTUAL, 0.07f)
    val prog = (researchProgress[cur] ?: 0f) + p.workSpeed(SkillType.INTELLECTUAL) * 0.25f
    researchProgress[cur] = prog
    if (prog >= cur.cost) {
        researchDone.add(cur)
        researchCurrent = null
        say("Research complete: ${cur.label}. ${cur.unlocks}.", 1)
        endJob(p)
        return
    }
    if (++j.timer > 900) endJob(p)
}

private fun Game.driveTend(p: Pawn, j: Job) {
    val o = pawnById(j.targetPawn)
    if (o == null || !o.alive || !o.untended) { endJob(p); return }
    j.tx = o.x; j.ty = o.y
    if (j.stage == 0) {
        val r = if (o === p) 0 else goTo(p, o.x, o.y, adjacent = true)
        if (r == -1) abort(p, true) else if (r == 0) j.stage = 1
        return
    }
    val self = o === p
    if (doWork(p, j, SkillType.MEDICINE, if (self) 360f else 200f)) {
        for (inj in o.injuries) if (!inj.tended) { inj.tended = true; inj.bleed *= 0.12f }
        if (o.colonist) say("${p.name} treated ${o.name}'s wounds.", 0)
        endJob(p)
    }
}

// ---------------------------------------------------------------- combat

fun Game.fire(p: Pawn, t: Pawn) {
    if (p.attackCd > 0) return
    val w = p.weapon
    p.attackCd = w.cooldown
    val d = distance(p.x, p.y, t.x, t.y)
    if (w.ranged) {
        val skill = p.level(SkillType.SHOOTING)
        val chance = (w.accuracy * (0.65f + 0.03f * skill) * (1.1f - d / (w.range * 1.4f))).coerceIn(0.1f, 0.95f)
        val hit = rng.chance(chance)
        shots.add(Shot(p.interpX(), p.interpY(), t.x.toFloat(), t.y.toFloat(), tick + 6, hit))
        if (hit) damage(t, w.damage * (0.85f + rng.float() * 0.3f))
        p.gainXp(SkillType.SHOOTING, 4f)
    } else {
        val skill = p.level(SkillType.MELEE)
        val hit = rng.chance((w.accuracy * (0.6f + 0.04f * skill)).coerceIn(0.1f, 0.95f))
        shots.add(Shot(p.x.toFloat(), p.y.toFloat(), t.x.toFloat(), t.y.toFloat(), tick + 4, hit))
        if (hit) damage(t, w.damage * (0.85f + rng.float() * 0.3f))
        p.gainXp(SkillType.MELEE, 4f)
    }
}

/** True when an undrafted colonist should drop what they are doing because of an enemy. */
fun Game.shouldReact(p: Pawn): Boolean {
    val t = p.job?.type ?: return false
    if (t == JobType.FLEE || t == JobType.ATTACK || t == JobType.BREAK || t == JobType.MOVE) return false
    return autoFightTarget(p) != null || threatNear(p)
}

/** Undrafted colonists defend themselves: shoot what is in range, or fight back when cornered. */
private fun Game.autoFightTarget(p: Pawn): Pawn? {
    var best: Pawn? = null
    var bd = 1e9f
    for (h in pawns) {
        if (!h.hostile || !h.alive || h.downed || h === p) continue
        val d = distance(p.x, p.y, h.x, h.y)
        val ok = d < 2.1f || (p.weapon.ranged && d <= p.weapon.range * 0.8f && map.lineOfSight(p.x, p.y, h.x, h.y))
        if (ok && d < bd) { best = h; bd = d }
    }
    return best
}

private fun Game.draftedAI(p: Pawn) {
    val j = p.job
    var target: Pawn? = null
    var bd = 1e9f
    for (h in pawns) {
        if (!h.hostile || !h.alive || h.downed || h === p) continue
        val d = distance(p.x, p.y, h.x, h.y)
        val reach = if (p.weapon.ranged) p.weapon.range else 1.5f
        if (d <= reach && d < bd && (d < 1.6f || map.lineOfSight(p.x, p.y, h.x, h.y))) { target = h; bd = d }
    }
    if (target != null) { fire(p, target); return }
    if (j?.type == JobType.MOVE) {
        if (goTo(p, j.tx, j.ty) != 1) p.job = null
        return
    }
    // Melee fighters charge enemies that get close.
    if (!p.weapon.ranged) {
        val h = hostiles.filter { !it.downed && distance(p.x, p.y, it.x, it.y) < 8f }.minByOrNull { distance(p.x, p.y, it.x, it.y) }
        if (h != null) goTo(p, h.x, h.y, adjacent = true)
    }
}

private fun Game.pickRaidTarget(p: Pawn): Pawn? {
    var best: Pawn? = null
    var bd = 1e9f
    for (c in pawns) {
        if (!c.alive || c === p || c.hostile) continue
        if (!c.colonist) continue
        var d = distance(p.x, p.y, c.x, c.y)
        if (c.downed) d += 40f
        if (d < bd) { best = c; bd = d }
    }
    return best
}

private fun Game.hostileAI(p: Pawn) {
    var j = p.job
    if (j == null) { think(p); j = p.job ?: return }
    if (p.colonist && p.breakKind == 1) {
        // Berserk colonist.
    } else if (p.retreating) {
        if (j.type != JobType.LEAVE) { p.job = Job(JobType.LEAVE); j = p.job!! }
        val ex = if (p.x < map.w - 1 - p.x) 0 else map.w - 1
        val ey = if (p.y < map.h - 1 - p.y) 0 else map.h - 1
        val toX = abs(p.x - ex) < abs(p.y - ey)
        val tx = if (toX) ex else p.x
        val ty = if (toX) p.y else ey
        if (max(abs(p.x - tx), abs(p.y - ty)) <= 1 || p.x <= 1 || p.y <= 1 || p.x >= map.w - 2 || p.y >= map.h - 2) {
            pawns.remove(p)
            return
        }
        if (goTo(p, tx.coerceIn(0, map.w - 1), ty.coerceIn(0, map.h - 1)) == -1) pawns.remove(p)
        return
    }
    j.timer++
    var t = pawnById(j.targetPawn)
    if (t == null || !t.alive || (t.downed && j.timer % 30 == 0) || j.timer % 120 == 0) {
        t = pickRaidTarget(p)
        j.targetPawn = t?.id ?: -1
    }
    if (t == null) return
    val d = distance(p.x, p.y, t.x, t.y)
    val w = p.weapon
    if (w.ranged) {
        val stand = w.range * 0.75f
        if (d <= stand && map.lineOfSight(p.x, p.y, t.x, t.y)) {
            if (p.moveCd > 0) p.moveCd-- else fire(p, t)
            return
        }
        goTo(p, t.x, t.y, adjacent = true, breach = true).let { if (it == -1) idleWander(p, j) }
    } else {
        if (d < 1.6f) { fire(p, t); return }
        goTo(p, t.x, t.y, adjacent = true, breach = true).let { if (it == -1) idleWander(p, j) }
    }
}

private fun Game.idleWander(p: Pawn, j: Job) {
    if (j.timer % 60 == 0) {
        val x = (p.x + rng.range(-5, 5)).coerceIn(1, map.w - 2)
        val y = (p.y + rng.range(-5, 5)).coerceIn(1, map.h - 2)
        if (map.walkable(map.idx(x, y))) goTo(p, x, y)
    }
}
