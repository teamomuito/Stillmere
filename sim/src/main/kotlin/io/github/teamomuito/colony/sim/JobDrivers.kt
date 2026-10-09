package io.github.teamomuito.colony.sim

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private fun Game.doWork(p: Pawn, j: Job, skill: SkillType?, total: Float): Boolean {
    // Work and skill gain are per tick, so they are divided by TIME_SCALE to keep their effect per game hour.
    j.work += p.workSpeed(skill) / TIME_SCALE
    if (skill != null) p.gainXp(skill, 0.07f / TIME_SCALE)
    return j.work >= total
}

private fun Game.abort(p: Pawn, markBad: Boolean = false) {
    val j = p.job
    if (markBad && j != null && j.key >= 0) markUnreachable(p, j.key)
    endJob(p)
}

private fun Game.pickUp(p: Pawn, i: Int, type: ItemType, n: Int): Int {
    val s = map.items[i] ?: return 0
    if (s.type != type) return 0
    if (p.carryCount > 0 && p.carryType != type) return 0
    val got = map.take(i, min(n, s.count))
    p.carryType = type
    p.carryQuality = s.quality
    p.carryCount += got
    return got
}

fun Game.driveJob(p: Pawn) {
    if (p.hostile && !p.colonist || p.hostileFlag) { hostileAI(p); return }
    if (p.ally) { allyAI(p); return }
    if (p.drafted) { draftedAI(p); return }
    val j = p.job ?: return
    when (j.type) {
        JobType.IDLE -> { if (--j.timer <= 0) endJob(p) }
        JobType.WANDER -> driveWander(p, j)
        JobType.BREAK -> driveBreak(p, j)
        JobType.MOVE -> { if (goTo(p, j.tx, j.ty) != 1) p.job = null }
        JobType.FLEE -> driveFlee(p, j)
        JobType.ATTACK -> {
            val t = autoFightTarget(p)
            if (t == null) endJob(p) else fire(p, t)
        }
        JobType.EAT -> driveEat(p, j)
        JobType.SLEEP, JobType.REST -> driveSleep(p, j)
        JobType.JOY -> driveJoy(p, j)
        JobType.SOCIAL -> driveSocial(p, j)
        JobType.SMOKE -> driveSmoke(p, j)
        JobType.MINE -> driveMine(p, j)
        JobType.CUT, JobType.HARVEST -> driveCut(p, j)
        JobType.SOW -> driveSow(p, j)
        JobType.HAUL -> driveHaul(p, j)
        JobType.BURY -> driveBury(p, j)
        JobType.REFUEL -> driveRefuel(p, j)
        JobType.BUILD -> driveBuild(p, j)
        JobType.DECONSTRUCT -> driveDecon(p, j)
        JobType.REPAIR -> driveRepair(p, j)
        JobType.BILL -> driveBill(p, j)
        JobType.RESEARCH -> driveResearch(p, j)
        JobType.TEND -> driveTend(p, j)
        JobType.RESCUE -> driveRescue(p, j)
        JobType.CAPTURE -> driveCapture(p, j)
        JobType.WARDEN -> driveWarden(p, j)
        JobType.FEED_PRISONER -> driveFeedPrisoner(p, j)
        JobType.FEED_ANIMAL -> driveFeedAnimal(p, j)
        JobType.FEED_BABY -> driveFeedBaby(p, j)
        JobType.SHEAR -> driveGather(p, j)
        JobType.HUNT -> driveHunt(p, j)
        JobType.TAME -> driveTame(p, j)
        JobType.TRAIN -> driveTrain(p, j)
        JobType.SLAUGHTER -> driveSlaughter(p, j)
        JobType.BUTCHER -> driveButcher(p, j)
        JobType.CLEAN -> driveClean(p, j)
        JobType.LEAVE -> leaveMap(p)
        JobType.SURGERY -> driveSurgery(p, j)
        JobType.FIREFIGHT -> driveFirefight(p, j)
        JobType.EQUIP -> driveEquip(p, j)
        JobType.WEAR -> driveWear(p, j)
        else -> endJob(p)
    }
}

// ---------------------------------------------------------------- movement-like

private fun Game.homeRoom(p: Pawn): Int = if (p.homeTile >= 0 && !map.roomDirty) map.roomId[p.homeTile] else -1

private fun Game.driveWander(p: Pawn, j: Job) {
    if (j.stage == 0) {
        for (t in 0 until 8) {
            val cx = if (p.prisoner && p.homeTile >= 0) map.xOf(p.homeTile) else homeX
            val cy = if (p.prisoner && p.homeTile >= 0) map.yOf(p.homeTile) else homeY
            val rad = if (p.prisoner) 4 else 9
            val x = (cx + rng.range(-rad, rad)).coerceIn(1, map.w - 2)
            val y = (cy + rng.range(-rad, rad)).coerceIn(1, map.h - 2)
            if (map.walkable(map.idx(x, y)) && map.terrain[map.idx(x, y)] != Terrain.WATER_SHALLOW) { j.tx = x; j.ty = y; j.stage = 1; break }
        }
        if (j.stage == 0) endJob(p)
    } else {
        val r = goTo(p, j.tx, j.ty)
        p.joy = min(1f, p.joy + 0.00003f / TIME_SCALE)
        if (r != 1) endJob(p)
    }
}

private fun Game.driveBreak(p: Pawn, j: Job) {
    when (p.breakKind) {
        Break.RUN_WILD -> {
            // Head for the nearest map edge and leave.
            val tx = if (p.x < map.w - p.x) 0 else map.w - 1
            if (goTo(p, tx, p.y.coerceIn(0, map.h - 1)) != 1 || p.x <= 1 || p.x >= map.w - 2) {
                if (p.x <= 2 || p.x >= map.w - 3) { say("${p.name} ran off into the wilds and is gone.", 3); p.dead = true; p.deathTick = tick; pawns.remove(p); releaseAll(p) }
            }
        }
        Break.TANTRUM -> {
            // Smash the nearest furniture or item.
            if (j.stage == 0 || j.timer++ % tk(120) == 0) {
                val t = nearestCell(p, K_DESIG) { val b = map.building[it]; b != null && b.built && !b.def.isWall && b.def.category != "Ship" && b.def != BuildDef.CONDUIT }
                if (t >= 0) { j.tx = map.xOf(t); j.ty = map.yOf(t); j.stage = 1 } else if (j.stage == 0) { j.stage = 9 }
            }
            if (j.stage == 1) {
                val b = map.building[map.idx(j.tx, j.ty)]
                if (b == null) { j.stage = 0; return }
                if (goTo(p, j.tx, j.ty, adjacent = true) == 0) {
                    b.hp -= 5f
                    if (b.hp <= 0f) { map.removeBuilding(b); j.stage = 0; say("${p.name} destroyed a ${b.def.label.lowercase()}.", 2) }
                }
            } else if (j.stage == 9) wanderStep(p, j)
        }
        Break.FIRE -> {
            if (j.stage == 0 || j.timer++ % tk(300) == 0) {
                val x = (p.x + rng.range(-6, 6)).coerceIn(1, map.w - 2); val y = (p.y + rng.range(-6, 6)).coerceIn(1, map.h - 2)
                j.tx = x; j.ty = y; j.stage = 1
            }
            if (goTo(p, j.tx, j.ty) != 1) { igniteCell(map.idx(p.x, p.y), 0.5f); j.stage = 0 }
        }
        Break.BINGE_FOOD -> {
            if (j.stage == 0 || j.timer++ % tk(200) == 0) {
                val s = nearestItem(p, shared = true) { it.type.cat == ItemCat.FOOD_MEAL || it.type.cat == ItemCat.FOOD_PLANT && it.type.humanFood }
                if (s != null) { j.tx = s.x; j.ty = s.y; j.stage = 1 } else { j.stage = 9 }
            }
            if (j.stage == 1) {
                val s = map.items[map.idx(j.tx, j.ty)]
                if (s == null || !s.type.isFood) { j.stage = 0; return }
                if (goTo(p, j.tx, j.ty) == 0) {
                    map.take(map.idx(j.tx, j.ty), 1); p.food = min(1f, p.food + s.type.nutrition)
                    j.stage = 0; j.timer = 0; j.amount++
                    if (j.amount >= 5 || p.food >= 0.99f && j.amount >= 3) p.breakUntil = tick  // full: the binge is over
                }
            } else wanderStep(p, j)
        }
        Break.BINGE_DRUGS -> {
            if (j.stage == 0 || j.timer++ % tk(200) == 0) {
                val s = nearestItem(p, shared = true) { it.type == ItemType.BEER || it.type == ItemType.JOINT }
                if (s != null) { j.tx = s.x; j.ty = s.y; j.stage = 1 } else j.stage = 9
            }
            if (j.stage == 1) {
                val s = map.items[map.idx(j.tx, j.ty)]
                if (s == null) { j.stage = 0; return }
                if (goTo(p, j.tx, j.ty) == 0) { map.take(map.idx(j.tx, j.ty), 1); applyDrug(p, s.type); j.stage = 0; j.timer = 0 }
            } else wanderStep(p, j)
        }
        Break.INSULT -> {
            if (j.stage == 0 || j.timer++ % tk(150) == 0) {
                val o = pawns.filter { it !== p && it.colonist && it.alive && !it.downed }.minByOrNull { distance(p.x, p.y, it.x, it.y) }
                if (o != null) { j.targetPawn = o.id; j.stage = 1 } else j.stage = 9
            }
            if (j.stage == 1) {
                val o = pawnById(j.targetPawn)
                if (o == null || !o.alive) { j.stage = 0; return }
                if (goTo(p, o.x, o.y, adjacent = true) == 0 && p.attackCd == 0) {
                    p.attackCd = tk(120)
                    o.addThought("Insulted by ${p.name.substringBefore(' ')}", -0.07f, tick, 2 * TICKS_PER_DAY)
                    o.opinion[p.id] = (o.opinion[p.id] ?: 0) - 25
                    j.stage = 0
                }
            } else wanderStep(p, j)
        }
        Break.CATATONIC -> {
            // Catatonic pawns are downed in place; nothing to do until the break ends.
        }
        Break.HIDE -> {
            // Retreat to the nearest free bed and stay there until the break passes.
            if (j.stage == 0 || j.timer++ % tk(200) == 0) {
                val t = nearestCell(p, K_BED) { val b = map.building[it]; b != null && b.built && b.def.sleeps && (b.occupant == -1 || b.occupant == p.id) }
                if (t >= 0) { j.tx = map.xOf(t); j.ty = map.yOf(t); j.stage = 1 } else j.stage = 9
            }
            if (j.stage == 1) goTo(p, j.tx, j.ty)
            else wanderStep(p, j)
        }
        else -> wanderStep(p, j)
    }
}

private fun Game.wanderStep(p: Pawn, j: Job) {
    if (j.dx < 0 || goTo(p, j.dx, j.dy) != 1) {
        val x = (p.x + rng.range(-8, 8)).coerceIn(1, map.w - 2); val y = (p.y + rng.range(-8, 8)).coerceIn(1, map.h - 2)
        if (map.walkable(map.idx(x, y))) { j.dx = x; j.dy = y } else j.dx = -1
    }
}

private fun Game.driveFlee(p: Pawn, j: Job) {
    if (j.timer % tk(40) == 0 || j.stage == 0) {
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
    if (!threatNear(p) && j.timer > tk(120)) endJob(p)
    if (j.timer > tk(2400)) endJob(p)
}

// ---------------------------------------------------------------- needs

private fun Game.driveEat(p: Pawn, j: Job) {
    when (j.stage) {
        0 -> {
            val s = map.items[map.idx(j.tx, j.ty)]
            if (s == null || !s.type.isFood) { abort(p); return }
            when (goTo(p, j.tx, j.ty)) {
                -1 -> abort(p, true)
                0 -> {
                    val i = map.idx(j.tx, j.ty)
                    val want = if (s.type.nutrition >= 0.5f) 1 else ceil((1f - p.food) / s.type.nutrition).toInt()
                    j.heldRot = s.rot
                    pickUp(p, i, s.type, max(1, want))
                    j.stage = 1
                }
            }
        }
        1 -> {
            val table = nearestCell(p, K_STATION) {
                val b = map.building[it]
                b != null && b.built && b.def == BuildDef.TABLE && abs(map.xOf(it) - p.x) + abs(map.yOf(it) - p.y) < 28
            }
            if (table < 0) { j.stage = 2; j.timer = 0; return }
            j.dx = map.xOf(table); j.dy = map.yOf(table)
            j.stage = 11
        }
        11 -> when (goTo(p, j.dx, j.dy, adjacent = true)) { 1 -> {}; else -> { j.stage = 2; j.timer = 0 } }
        2 -> {
            val t = p.carryType
            if (t == null || p.carryCount == 0) { endJob(p); return }
            j.timer++
            val total = if (t.nutrition >= 0.5f) tk(120) else tk(40) + p.carryCount * tk(3)
            if (j.timer >= total) {
                val amount = t.nutrition * p.carryCount
                p.food = min(1f, p.food + amount * p.cap[Cap.EATING.ordinal].coerceAtLeast(0.3f))
                val atTable = (-1..1).any { dy -> (-1..1).any { dx ->
                    val x = p.x + dx; val y = p.y + dy
                    map.inB(x, y) && map.building[map.idx(x, y)]?.let { it.built && it.def == BuildDef.TABLE } == true
                } }
                val chair = (-1..1).any { dy -> (-1..1).any { dx ->
                    val x = p.x + dx; val y = p.y + dy
                    map.inB(x, y) && map.building[map.idx(x, y)]?.let { it.built && (it.def == BuildDef.CHAIR || it.def == BuildDef.STOOL) } == true
                } }
                val dur = (0.8f * TICKS_PER_DAY).toInt()
                when (t) {
                    ItemType.MEAL_FINE -> p.addThought("Ate a fine meal", 0.05f, tick, dur)
                    ItemType.MEAL_SIMPLE, ItemType.PEMMICAN -> {}
                    ItemType.HUMAN_MEAT -> if (Trait.CANNIBAL in p.traits) p.addThought("Ate human flesh (loved it)", 0.12f, tick, dur) else p.addThought("Ate human flesh", -0.35f, tick, 3 * TICKS_PER_DAY)
                    ItemType.INSECT_MEAT -> p.addThought("Ate insect meat", -0.1f, tick, dur)
                    ItemType.KIBBLE -> p.addThought("Ate kibble", -0.1f, tick, dur)
                    ItemType.MILK -> {}
                    else -> p.addThought("Ate raw food", -0.07f, tick, (1.5f * TICKS_PER_DAY).toInt())
                }
                if (t.cat == ItemCat.FOOD_MEAL) {
                    if (atTable) p.addThought("Ate at a table", 0.03f, tick, dur)
                    else p.addThought("Ate without a table", -0.03f, tick, dur)
                    if (chair && atTable) p.addThought("Sat in a chair", 0.02f, tick, dur)
                }
                if (Trait.GOURMAND in p.traits) p.addThought("Gourmand's feast", 0.05f, tick, dur)
                if (Trait.CARNIVORE in p.traits && t.cat == ItemCat.FOOD_MEAT) p.addThought("Ate meat", 0.05f, tick, dur)
                // Rotten food can make people ill.
                if (j.heldRot > 0.65f && rng.chance(0.4f)) { addHediff(p, HediffKind.FOOD_POISONING, 0.25f); say("${p.name} got food poisoning.", 2) }
                else if (t.cat == ItemCat.FOOD_MEAT && rng.chance(0.01f)) addHediff(p, HediffKind.FOOD_POISONING, 0.2f)
                p.carryCount = 0; p.carryType = null
                endJob(p)
            }
        }
    }
}

private fun Game.driveSleep(p: Pawn, j: Job) {
    if (j.type == JobType.SLEEP && threatNear(p)) { endJob(p); return }
    if (j.stage == 0) {
        val b = map.building[map.idx(j.tx, j.ty)]
        if (b == null || !b.built || !b.def.sleeps) { if (b == null) p.bedId = -1; endJob(p); return }
        val r = goTo(p, j.tx, j.ty)
        if (r == -1) {
            // No way to the bed: remember that, and sleep where they are rather than trying the same bed every tick.
            markUnreachable(p, key(map.idx(j.tx, j.ty), K_BED))
            j.stage = 1; j.amount = 0; j.tx = p.x; j.ty = p.y
            return
        }
        if (r == 0) { j.stage = 1; if (b.def.medical) b.occupant = p.id }
        return
    }
    val inBed = j.amount == 1
    val bed = if (inBed) map.building[map.idx(p.x, p.y)] else null
    val comfort = bed?.def?.comfort ?: 0f
    // Rest gain is per tick: divided by TIME_SCALE so a bed restores the same amount per game hour.
    val gain = (if (inBed) 0.00010f + comfort * 0.00004f else 0.00007f) / TIME_SCALE
    j.timer++
    if (j.type == JobType.REST) {
        p.rest = min(1f, p.rest + gain * 0.6f)
        // Wake for food or when recovered.
        if (p.food < 0.15f) { endJob(p); return }
        val recovered = p.surgeries.isEmpty() && (!p.needsMedical || (p.pain < 0.03f && p.bloodLoss < 0.02f && p.healthFraction() > 0.93f && p.hediffs.none { it.kind.category == 0 }))
        if (recovered || j.timer > 3 * TICKS_PER_DAY) endJob(p)
        return
    }
    p.rest = min(1f, p.rest + gain)
    val done = p.rest >= 0.985f || (p.schedule[hour] != 3 && p.rest > 0.82f)
    if (done) {
        val i = map.idx(p.x, p.y)
        if (!inBed) p.addThought("Slept on the ground", -0.05f, tick, TICKS_PER_DAY)
        else if (comfort >= 0.65f) p.addThought("Slept in a comfy bed", 0.04f, tick, TICKS_PER_DAY)
        if (!map.roomIndoorAt(i)) p.addThought("Slept outside", -0.07f, tick, TICKS_PER_DAY)
        else if (!map.roomDirty) {
            val r = map.roomId[i]
            val imp = map.roomImpress[r]
            val label = impressLabel(imp)
            val v = impressMood(imp)
            if (inBed && v != 0f && !(p.prisoner)) p.addThought("Slept in a $label room", v, tick, TICKS_PER_DAY)
            if (map.roomRole[r] == 2) p.addThought("Slept in a barracks", -0.04f, tick, TICKS_PER_DAY)
            if (map.roomTemp[r] < p.comfyMin() - 5f) p.addThought("Slept in the cold", -0.05f, tick, TICKS_PER_DAY)
        }
        if (map.light[i] < 0.15f) p.addThought("Slept in the dark", -0.02f, tick, TICKS_PER_DAY)
        endJob(p)
    }
}

private fun Game.driveJoy(p: Pawn, j: Job) {
    if (j.stage == 9) {
        // Aimless walk around home.
        if (j.dx < 0 || goTo(p, j.dx, j.dy) != 1) {
            val x = (homeX + rng.range(-7, 7)).coerceIn(1, map.w - 2); val y = (homeY + rng.range(-7, 7)).coerceIn(1, map.h - 2)
            j.dx = if (map.walkable(map.idx(x, y))) x else -1; j.dy = y
        }
        p.joy = min(1f, p.joy + 0.00022f / TIME_SCALE)
        if (--j.timer <= 0 || p.joy > 0.9f) endJob(p)
        return
    }
    val b = map.building[map.idx(j.tx, j.ty)]
    if (b == null || !b.built || (b.def.consumesPower && !b.powered)) { endJob(p); return }
    if (j.stage == 0) {
        val r = goTo(p, j.tx, j.ty, adjacent = b.def.blocksMove)
        if (r == -1) abort(p, true) else if (r == 0) j.stage = 1
        return
    }
    val tol = if (p.lastJoyKind == b.def.name) 0.7f else 1f
    p.joy = min(1f, p.joy + b.def.joy * 0.6f / (LEGACY_TICKS_PER_HOUR * TIME_SCALE) * tol)
    if (j.timer++ > tk(3200) || p.joy >= 0.98f) {
        p.lastJoyKind = b.def.name
        if (b.def == BuildDef.HORSESHOES) p.addThought("Played horseshoes", 0.04f, tick, TICKS_PER_DAY / 2)
        endJob(p)
    }
}

private fun Game.driveSmoke(p: Pawn, j: Job) {
    val i = map.idx(j.tx, j.ty)
    val s = map.items[i]
    if (s == null || s.type != j.item) { endJob(p); return }
    if (j.stage == 0) {
        val r = goTo(p, j.tx, j.ty)
        if (r == -1) abort(p, true) else if (r == 0) { map.take(i, 1); j.stage = 1 }
        return
    }
    if (++j.timer > tk(160)) { applyDrug(p, j.item!!); p.joy = min(1f, p.joy + 0.3f); endJob(p) }
}

fun Game.applyDrug(p: Pawn, t: ItemType) {
    val (high, addict) = when (t) {
        ItemType.BEER -> HediffKind.ALCOHOL_HIGH to HediffKind.ALCOHOL_ADDICTION
        ItemType.JOINT -> HediffKind.SMOKELEAF_HIGH to HediffKind.SMOKELEAF_ADDICTION
        ItemType.PSYCHITE_TEA -> HediffKind.PSYCHITE_HIGH to HediffKind.PSYCHITE_ADDICTION
        ItemType.FLAKE -> HediffKind.FLAKE_HIGH to HediffKind.FLAKE_ADDICTION
        ItemType.YAYO -> HediffKind.YAYO_HIGH to HediffKind.YAYO_ADDICTION
        ItemType.GO_JUICE -> HediffKind.GOJUICE_HIGH to HediffKind.GOJUICE_ADDICTION
        ItemType.WAKE_UP -> { p.rest = min(1f, p.rest + 0.45f); addHediff(p, HediffKind.WAKEUP_HIGH, 0.5f).duration = tk(3000); p.addThought("Drug use", 0.04f, tick, TICKS_PER_DAY / 2); return }
        else -> return
    }
    val h = addHediff(p, high, 0.5f)
    h.duration = if (t == ItemType.BEER) tk(3500) else if (t == ItemType.GO_JUICE) tk(4000) else tk(2600)
    if (t == ItemType.FLAKE) p.addThought("Flake rush", 0.28f, tick, TICKS_PER_DAY)
    if (t == ItemType.YAYO) p.addThought("Yayo rush", 0.3f, tick, TICKS_PER_DAY)
    // Existing addiction is satisfied again.
    val ad = p.hediff(addict)
    if (ad != null) { ad.severity = 1f; removeHediff(p, HediffKind.WITHDRAWAL) }
    else if (rng.chance(when (t) { ItemType.PSYCHITE_TEA -> 0.1f; ItemType.BEER -> 0.03f; ItemType.FLAKE -> 0.45f; ItemType.YAYO -> 0.3f; ItemType.GO_JUICE -> 0.06f; else -> 0.015f })) {
        addHediff(p, addict, 1f)
        say("${p.name} is now addicted to ${t.label.lowercase()}.", 3)
    }
    p.addThought("Drug use", 0.06f, tick, TICKS_PER_DAY / 2)
}

private fun Game.driveSocial(p: Pawn, j: Job) {
    val o = pawnById(j.targetPawn)
    if (o == null || !o.alive || o.downed) { endJob(p); return }
    if (j.stage == 0) {
        val r = goTo(p, o.x, o.y, adjacent = true)
        if (r == -1) abort(p) else if (r == 0) { j.stage = 1; j.timer = 0 }
        return
    }
    if (++j.timer > tk(180)) {
        socialInteract(p, o)
        p.joy = min(1f, p.joy + 0.18f)
        o.joy = min(1f, o.joy + 0.1f)
        endJob(p)
    }
}

// ---------------------------------------------------------------- field work

private fun Game.driveMine(p: Pawn, j: Job) {
    val i = map.idx(j.tx, j.ty)
    if (map.terrain[i] != Terrain.ROCK || map.desig[i].toInt() != Desig.MINE) { endJob(p); return }
    if (j.stage == 0) {
        when (goTo(p, j.tx, j.ty, adjacent = true)) { -1 -> abort(p, true); 0 -> j.stage = 1 }
        return
    }
    val ore = map.ore[i]
    val total = if (ore != Ore.NONE) ore.work else 1000f
    if (doWork(p, j, SkillType.MINING, total)) {
        if (ore != Ore.NONE && ore.item != null) {
            map.drop(ore.item, rng.range(ore.yieldMin, ore.yieldMax), p.x, p.y)
        } else if (rng.chance(0.55f)) map.drop(ItemType.STONE_CHUNK, 1, p.x, p.y)
        map.ore[i] = Ore.NONE
        map.terrain[i] = Terrain.GRAVEL
        map.markWalkChanged()
        map.desig[i] = 0
        map.roomDirty = true
        // Cave-ins are not modelled; mined rock stays roofed (natRoof).
        endJob(p)
    }
}

private fun Game.driveCut(p: Pawn, j: Job) {
    val i = map.idx(j.tx, j.ty)
    val pl = map.plant[i]
    if (pl == null || (j.type == JobType.CUT && map.desig[i].toInt() == Desig.NONE)) { endJob(p); return }
    if (j.stage == 0) {
        val r = goTo(p, j.tx, j.ty, adjacent = pl.type.isTree)
        if (r == -1) abort(p, true) else if (r == 0) j.stage = 1
        return
    }
    if (doWork(p, j, SkillType.PLANTS, pl.type.harvestWork.toFloat() * (if (pl.growth < 1f) 0.5f else 1f))) {
        val yt = pl.type.yieldType
        if (yt != null) {
            var n = (pl.type.yieldCount * (0.5f + 0.5f * p.workSpeed(SkillType.PLANTS).coerceAtMost(1.6f)) * (if (pl.growth < 1f) pl.growth else 1f)).toInt()
            if (pl.type.crop) n = (pl.type.yieldCount * (0.75f + 0.25f * (p.level(SkillType.PLANTS) / 10f))).toInt()
            if (n > 0) map.drop(yt, n, p.x, p.y)
        }
        if (Trait.GREEN_THUMB in p.traits) p.addThought("Worked with plants", 0.04f, tick, TICKS_PER_DAY / 2)
        if (pl.type.regrows && map.desig[i].toInt() == Desig.HARVEST) { pl.growth = 0.05f; map.desig[i] = 0 }
        else { map.plant[i] = null; map.desig[i] = 0 }
        endJob(p)
    }
}

private fun Game.driveSow(p: Pawn, j: Job) {
    val i = map.idx(j.tx, j.ty)
    val z = map.zoneAt(i)
    if (map.plant[i] != null || z == null || z.kind != ZoneKind.GROWING) { endJob(p); return }
    if (j.stage == 0) {
        val r = goTo(p, j.tx, j.ty)
        if (r == -1) abort(p, true) else if (r == 0) j.stage = 1
        return
    }
    if (doWork(p, j, SkillType.PLANTS, z.crop.sowWork.toFloat())) {
        map.plant[i] = Plant(z.crop, j.tx, j.ty, 0.02f)
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
                if (s.corpseOf != null) {
                    map.items.remove(i); j.stack = s
                } else if (pickUp(p, i, s.type, s.type.stack) == 0) {
                    // The stack changed while we walked over: nothing to haul after all.
                    endJob(p); return
                }
                unreserve(p, j.key)
                j.stage = 1
            }
        }
        1 -> {
            val r = goTo(p, j.dx, j.dy)
            if (r == -1) { endJob(p); return }
            if (r == 0) {
                val st = j.stack
                if (st != null) {
                    val di = map.idx(j.dx, j.dy)
                    if (map.items[di] == null) { st.x = j.dx; st.y = j.dy; map.items[di] = st } else placeStack(st, j.dx, j.dy)
                    j.stack = null
                } else {
                    val t = p.carryType
                    if (t != null && p.carryCount > 0) {
                        val left = map.drop(t, p.carryCount, j.dx, j.dy, p.carryQuality)
                        p.carryCount = left
                        if (left == 0) p.carryType = null
                    }
                }
                endJob(p)
            }
        }
    }
}

private fun Game.driveBury(p: Pawn, j: Job) {
    when (j.stage) {
        0 -> {
            val i = map.idx(j.tx, j.ty)
            val s = map.items[i]
            if (s == null || s.corpseOf == null) { endJob(p); return }
            val r = goTo(p, j.tx, j.ty)
            if (r == -1) abort(p, true) else if (r == 0) { map.items.remove(i); j.stack = s; j.stage = 1 }
        }
        1 -> {
            val g = map.building[map.idx(j.dx, j.dy)]
            if (g == null || g.def != BuildDef.GRAVE || g.occupant != -1) { endJob(p); return }
            val r = goTo(p, j.dx, j.dy, adjacent = true)
            if (r == -1) { endJob(p); return }
            if (r == 0) { j.stage = 2; j.timer = 0 }
        }
        2 -> {
            if (++j.timer > tk(120)) {
                val g = map.building[map.idx(j.dx, j.dy)]
                val s = j.stack
                if (g != null && s != null) {
                    g.occupant = 1
                    j.stack = null
                    if (s.corpseColonist) {
                        say("${s.corpseOf} was laid to rest.", 0)
                        for (o in colonists) o.addThought("Colonist buried", 0.04f, tick, 2 * TICKS_PER_DAY)
                    }
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
            val want = if (j.aux == 1) ItemType.SHELL else ItemType.WOOD
            if (s == null || s.type != want) { endJob(p); return }
            val r = goTo(p, j.tx, j.ty)
            if (r == -1) abort(p, true)
            else if (r == 0) { pickUp(p, i, want, if (j.aux == 1) 5 else 6); j.stage = 1 }
        }
        1 -> {
            val b = map.building[map.idx(j.dx, j.dy)]
            if (b == null || !b.built) { endJob(p); return }
            val r = goTo(p, j.dx, j.dy, adjacent = b.def.blocksMove)
            if (r == -1) { endJob(p); return }
            if (r == 0) {
                if (j.aux == 1) b.shells = min(5, b.shells + p.carryCount)
                else b.fuel = min(b.def.fuelCap, b.fuel + p.carryCount * 4f)
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
    val skill = if (b.def.art) SkillType.ARTISTIC else SkillType.CONSTRUCTION
    when (j.stage) {
        0 -> {
            var k = -1
            for (c in b.cost.indices) if (b.missing(c) > 0) { k = c; break }
            if (k < 0) { j.stage = 3; return }
            // Checked when the job starts: if the stock is gone (used by another order, or never there), wait instead of walking.
            if (map.countItems(b.cost[k].first) < b.missing(k)) { endJob(p); return }
            val type = b.cost[k].first
            val s = nearestItem(p) { it.type == type }
            if (s == null) { abort(p, true); return }
            reserve(p, key(map.idx(s.x, s.y), K_ITEM))
            j.dx = s.x; j.dy = s.y; j.aux = k
            j.stage = 1
        }
        1 -> {
            val ii = map.idx(j.dx, j.dy)
            val s = map.items[ii]
            val type = b.cost[j.aux].first
            if (s == null || s.type != type) { j.stage = 0; return }
            val r = goTo(p, j.dx, j.dy)
            if (r == -1) abort(p, true)
            else if (r == 0) {
                pickUp(p, ii, type, min(b.missing(j.aux), type.stack))
                unreserve(p, key(ii, K_ITEM))
                j.stage = 2
            }
        }
        2 -> {
            val r = goTo(p, j.tx, j.ty, adjacent = true)
            if (r == -1) abort(p, true)
            else if (r == 0) {
                b.delivered[j.aux] += p.carryCount
                p.carryCount = 0; p.carryType = null
                var done = true
                for (c in b.cost.indices) if (b.missing(c) > 0) done = false
                j.stage = if (done) 3 else 0
            }
        }
        3 -> {
            val r = goTo(p, j.tx, j.ty, adjacent = true)
            if (r == -1) abort(p, true) else if (r == 0) j.stage = 4
        }
        4 -> {
            b.progress = j.work
            if (doWork(p, j, skill, b.def.work.toFloat())) {
                val q = if (b.def.category == "Furniture" || b.def.isFloor || b.def.workbench) rollQuality(p.level(skill) + (if ((Trait.CREATIVE in p.traits || Trait.TORTURED_ARTIST in p.traits) && b.def.art) 4 else 0), b.def.art) else Quality.NORMAL
                if (b.def.isFloor) {
                    map.floor[i] = b.def
                    map.floorQuality[i] = q
                    map.removeBuilding(b)
                } else if (b.def == BuildDef.CONDUIT) {
                    map.conduit[i] = true
                    map.removeBuilding(b)
                } else {
                    b.built = true
                    map.markWalkChanged()
                    b.hp = b.maxHp
                    b.quality = q
                    if (b.def.blocksMove || b.def.isWall || b.def.isDoor) ejectPawns(i)
                    if (b.def == BuildDef.GRAVE) b.occupant = -1
                    if (b.def.isShip) say("${b.def.label} constructed.", 1)
                    if (b.def == BuildDef.SHIP_CASKET || shipComplete()) { if (shipComplete()) say("The escape ship is ready! Open the Ship panel to launch.", 1) }
                }
                if (b.def.art && b.def.beauty >= 6f) p.addThought("Created art", 0.08f, tick, 2 * TICKS_PER_DAY)
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
    val cond = map.conduit[i] && b == null && fl == null
    if (map.desig[i].toInt() != Desig.DECON || (b == null && fl == null && !cond)) { endJob(p); return }
    if (j.stage == 0) {
        val r = goTo(p, j.tx, j.ty, adjacent = true)
        if (r == -1) abort(p, true) else if (r == 0) j.stage = 1
        return
    }
    val def = b?.def ?: fl ?: BuildDef.CONDUIT
    if (doWork(p, j, SkillType.CONSTRUCTION, def.work * 0.5f)) {
        for ((t, n) in b?.cost ?: def.cost) map.drop(t, max(1, n / 2), p.x, p.y)
        if (b != null) map.removeBuilding(b) else if (fl != null) map.floor[i] = null else map.conduit[i] = false
        map.desig[i] = 0
        map.roomDirty = true
        endJob(p)
    }
}

private fun Game.driveRepair(p: Pawn, j: Job) {
    val i = map.idx(j.tx, j.ty)
    val b = map.building[i]
    if (b == null || !b.built || b.hp >= b.maxHp || map.desig[i].toInt() != Desig.REPAIR) { map.desig[i] = 0; endJob(p); return }
    if (j.stage == 0) {
        val r = goTo(p, j.tx, j.ty, adjacent = true)
        if (r == -1) abort(p, true) else if (r == 0) j.stage = 1
        return
    }
    b.hp = min(b.maxHp, b.hp + 0.4f * p.workSpeed(SkillType.CONSTRUCTION) / TIME_SCALE)
    p.gainXp(SkillType.CONSTRUCTION, 0.04f / TIME_SCALE)
    if (b.hp >= b.maxHp) { map.desig[i] = 0; endJob(p) }
}

private fun Game.driveClean(p: Pawn, j: Job) {
    val i = map.idx(j.tx, j.ty)
    if (map.filth[i] <= 0) { endJob(p); return }
    if (j.stage == 0) {
        val r = goTo(p, j.tx, j.ty)
        if (r == -1) abort(p, true) else if (r == 0) j.stage = 1
        return
    }
    j.work += p.workSpeed(null) / TIME_SCALE
    if (j.work >= 90f) {
        j.work = 0f
        map.filth[i] = (map.filth[i] - 1).coerceAtLeast(0).toByte()
        if (map.filth[i] <= 0) endJob(p)
    }
}

private fun Game.driveFirefight(p: Pawn, j: Job) {
    val i = map.idx(j.tx, j.ty)
    val f = map.fires[i]
    if (f == null) { endJob(p); return }
    if (j.stage == 0) {
        val r = goTo(p, j.tx, j.ty, adjacent = true)
        if (r == -1) abort(p, true) else if (r == 0) j.stage = 1
        return
    }
    j.work += p.workSpeed(null) / TIME_SCALE
    if (j.work >= 40f) {
        j.work = 0f
        f.intensity -= 0.45f
        if (f.intensity <= 0.05f) { map.fires.remove(i); endJob(p) }
    }
}

// ---------------------------------------------------------------- equipment

private fun Game.driveEquip(p: Pawn, j: Job) {
    val i = map.idx(j.tx, j.ty)
    val s = map.items[i]
    if (s == null || s.type.weapon == null) { endJob(p); return }
    if (j.stage == 0) {
        val r = goTo(p, j.tx, j.ty)
        if (r == -1) abort(p, true) else if (r == 0) { j.stage = 1; j.timer = 0 }
        return
    }
    if (++j.timer > tk(60)) {
        val old = p.weaponItem
        val oq = p.weaponQuality
        map.take(i, 1)
        p.weaponItem = s.type; p.weaponQuality = s.quality
        if (old != null) map.drop(old, 1, p.x, p.y, oq)
        endJob(p)
    }
}

private fun Game.driveWear(p: Pawn, j: Job) {
    val i = map.idx(j.tx, j.ty)
    val s = map.items[i]
    val a = s?.type?.apparel
    if (s == null || a == null) { endJob(p); return }
    if (j.stage == 0) {
        val r = goTo(p, j.tx, j.ty)
        if (r == -1) abort(p, true) else if (r == 0) { j.stage = 1; j.timer = 0 }
        return
    }
    if (++j.timer > tk(100)) {
        map.take(i, 1)
        // Remove conflicting pieces: same slot, or outer layers overlapping the same body areas.
        val drop = p.apparel.filter { w ->
            val wa = w.type.apparel!!
            wa.slot == a.slot && (wa.cover and a.cover) != 0 || wa.slot == a.slot && a.slot != ApparelSlot.OUTER
        }
        for (w in drop) { p.apparel.remove(w); map.drop(w.type, 1, p.x, p.y, w.quality) }
        p.apparel.add(Worn(s.type, s.quality, a.hp * s.hp))
        endJob(p)
    }
}

// ---------------------------------------------------------------- crafting

private fun Game.driveBill(p: Pawn, j: Job) {
    val bench = map.building[j.bench]
    if (bench == null || !bench.built || (bench.def.consumesPower && !bench.powered)) { endJob(p); return }
    val bill = bench.bills.getOrNull(j.billIndex)
    if (bill == null) { endJob(p); return }
    val r = bill.recipe
    when (j.stage) {
        0 -> {
            if (j.ingIdx >= r.inputs.size) { j.stage = 2; return }
            val ing = r.inputs[j.ingIdx]
            val need = ing.count - j.collected
            val s = nearestItem(p) {
                ing.accepts(it.type) && (bill.allowedItems == null || it.type in bill.allowedItems!!) && it.rot < 0.8f
            }
            if (s == null) { abort(p, true); return }
            reserve(p, key(map.idx(s.x, s.y), K_ITEM))
            j.dx = s.x; j.dy = s.y
            j.aux = min(need, s.count)
            j.stage = 1
        }
        1 -> {
            val ii = map.idx(j.dx, j.dy)
            val s = map.items[ii]
            val ing = r.inputs[j.ingIdx]
            if (s == null || !ing.accepts(s.type)) { j.stage = 0; return }
            val rr = goTo(p, j.dx, j.dy)
            if (rr == -1) abort(p, true)
            else if (rr == 0) {
                val n = min(ing.count - j.collected, s.count)
                val q = s.quality
                val ty = s.type
                map.take(ii, n)
                j.held.add(Triple(ty, n, q))
                j.collected += n
                unreserve(p, key(ii, K_ITEM))
                if (j.collected >= ing.count) { j.ingIdx++; j.collected = 0 }
                j.stage = 0
            }
        }
        2 -> {
            val rr = goTo(p, j.tx, j.ty, adjacent = true)
            if (rr == -1) abort(p, true) else if (rr == 0) { j.stage = 3; j.work = 0f }
        }
        3 -> {
            bench.inUse = tk(700)
            val sk = r.workType.skill() ?: SkillType.CRAFTING
            if (doWork(p, j, sk, r.work.toFloat() * (if (bench.def.workbench) 1f else 1f))) {
                j.held.clear()
                val gear = r.out.isGear
                val q = if (gear) rollQuality(p.level(sk), false) else Quality.NORMAL
                map.drop(r.out, r.outCount, p.x, p.y, q)
                bill.done++
                if (bill.mode == BillMode.DO_X && bill.done >= bill.target) bench.bills.remove(bill)
                if (r.out == ItemType.MEAL_SIMPLE || r.out == ItemType.MEAL_FINE) p.gainXp(SkillType.COOKING, 40f)
                if (r.out == ItemType.A_RECON || r.out.isGear) p.gainXp(sk, 60f)
                endJob(p)
            }
        }
    }
}

// ---------------------------------------------------------------- research

private fun Game.driveResearch(p: Pawn, j: Job) {
    val bench = map.building[map.idx(j.tx, j.ty)]
    val cur = researchCurrent
    if (bench == null || !bench.built || cur == null) { endJob(p); return }
    if (bench.def == BuildDef.HI_TECH_BENCH && !bench.powered) { endJob(p); return }
    if (j.stage == 0) {
        val r = goTo(p, j.tx, j.ty, adjacent = true)
        if (r == -1) abort(p, true) else if (r == 0) j.stage = 1
        return
    }
    p.gainXp(SkillType.INTELLECTUAL, 0.07f / TIME_SCALE)
    val mult = if (bench.def == BuildDef.HI_TECH_BENCH) 1.6f else 1f
    val prog = (researchProgress[cur] ?: 0f) + p.workSpeed(SkillType.INTELLECTUAL) / TIME_SCALE * 0.25f * mult * (if (Trait.TOO_SMART in p.traits) 1.35f else 1f)
    researchProgress[cur] = prog
    if (prog >= cur.cost) {
        researchDone.add(cur)
        researchCurrent = null
        say("Research complete: ${cur.label}. ${cur.unlocks}.", 1)
        endJob(p)
        return
    }
    if (++j.timer > tk(900)) endJob(p)
}

// ---------------------------------------------------------------- medical

private fun Game.driveTend(p: Pawn, j: Job) {
    val o = pawnById(j.targetPawn)
    if (o == null || !o.alive || !o.untended) { endJob(p); return }
    when (j.stage) {
        10 -> {
            val ii = map.idx(j.dx, j.dy)
            val s = map.items[ii]
            if (s == null || s.type != j.item) { j.stage = 0; return }
            val r = goTo(p, j.dx, j.dy)
            if (r == -1) { j.stage = 0; j.item = null }
            else if (r == 0) { pickUp(p, ii, s.type, 1); unreserve(p, key(ii, K_ITEM)); j.stage = 0 }
        }
        0 -> {
            val r = if (o === p) 0 else goTo(p, o.x, o.y, adjacent = true)
            if (r == -1) abort(p, true) else if (r == 0) j.stage = 1
        }
        1 -> {
            val self = o === p
            val bedsite = o.carriedBy >= 0
            if (doWork(p, j, SkillType.MEDICINE, if (self) 360f else 200f)) {
                val med = if (p.carryCount > 0) p.carryType else null
                tendPawn(p, o, med)
                if (med != null) { p.carryCount = 0; p.carryType = null }
                if (o.colonist || o.prisoner) say("${p.name} treated ${o.name}'s wounds.", 0)
                if (bedsite) { }
                endJob(p)
            }
        }
    }
}

private fun Game.driveRescue(p: Pawn, j: Job) {
    val o = pawnById(j.targetPawn)
    if (o == null || !o.alive || !o.downed) { endJob(p); return }
    when (j.stage) {
        0 -> {
            val r = goTo(p, o.x, o.y, adjacent = true)
            if (r == -1) abort(p, true) else if (r == 0) { o.carriedBy = p.id; p.carrying = o.id; j.stage = 1 }
        }
        1 -> {
            val bed = map.building[map.idx(j.dx, j.dy)]
            if (bed == null || !bed.built) { endJob(p); return }
            val r = goTo(p, j.dx, j.dy)
            if (r == -1) { endJob(p); return }
            if (r == 0) {
                o.carriedBy = -1; p.carrying = -1
                o.x = j.dx; o.y = j.dy; o.fromX = o.x; o.fromY = o.y
                if (bed.def.medical) bed.occupant = o.id else bed.ownerId = if (bed.ownerId < 0) o.id else bed.ownerId
                o.bedId = map.idx(j.dx, j.dy)
                if (o.refugee) { o.refugee = false; recruit(o); say("${o.name} is grateful and joins the colony.", 1) }
                // Wake them into rest mode once they are able.
                val rj = Job(JobType.REST); rj.stage = 1; rj.amount = 1
                endJob(p)
                if (!o.downed) o.job = rj
            }
        }
    }
}

private fun Game.driveCapture(p: Pawn, j: Job) {
    val o = pawnById(j.targetPawn)
    if (o == null || !o.alive || !o.downed) { endJob(p); return }
    when (j.stage) {
        0 -> {
            val r = goTo(p, o.x, o.y, adjacent = true)
            if (r == -1) abort(p, true) else if (r == 0) { o.carriedBy = p.id; p.carrying = o.id; j.stage = 1 }
        }
        1 -> {
            val bed = map.building[map.idx(j.dx, j.dy)]
            if (bed == null || !bed.built) { endJob(p); return }
            val r = goTo(p, j.dx, j.dy)
            if (r == -1) { endJob(p); return }
            if (r == 0) {
                o.carriedBy = -1; p.carrying = -1
                o.x = j.dx; o.y = j.dy; o.fromX = o.x; o.fromY = o.y
                makePrisoner(o, map.idx(j.dx, j.dy))
                bed.ownerId = o.id; o.bedId = map.idx(j.dx, j.dy)
                endJob(p)
            }
        }
    }
}

fun Game.makePrisoner(o: Pawn, bedCell: Int) {
    o.prisoner = true
    o.faction = Faction.PLAYER
    o.hostileFlag = false
    o.retreating = false
    o.homeTile = bedCell
    o.resistance = 1.5f + rng.float() * 1.5f
    o.recruitProgress = 0f
    o.drafted = false
    say("${o.name} was taken prisoner.", 1)
    endJob(o)
}

private fun Game.driveWarden(p: Pawn, j: Job) {
    val o = pawnById(j.targetPawn)
    if (o == null || !o.alive || !o.prisoner) { endJob(p); return }
    if (j.stage == 0) {
        val r = goTo(p, o.x, o.y, adjacent = true)
        if (r == -1) abort(p, true) else if (r == 0) { j.stage = 1; j.timer = 0 }
        return
    }
    if (++j.timer > tk(240)) {
        val soc = p.level(SkillType.SOCIAL)
        p.gainXp(SkillType.SOCIAL, 120f)
        if (o.recruitMode == 0) {
            o.resistance = max(0f, o.resistance - (0.12f + soc * 0.012f))
            if (o.resistance <= 0.01f) {
                val chance = 0.2f + soc * 0.02f
                if (rng.float() < chance) {
                    recruit(o)
                }
            }
        } else {
            // Just talking.
            o.addThought("Chatted with a warden", 0.04f, tick, TICKS_PER_DAY)
        }
        j.stage = 2
        endJob(p)
    }
}

fun Game.recruit(o: Pawn) {
    o.prisoner = false
    o.faction = Faction.PLAYER
    o.homeTile = -1
    o.bedId = -1
    // The prison bed they were held in is free again, and a visitor's leaving orders no longer apply.
    for (b in map.buildings()) if (b.ownerId == o.id) b.ownerId = -1
    for (b in map.buildings()) if (b.occupant == o.id) b.occupant = -1
    o.retreating = false; o.escapeTick = 0; o.drafted = false
    o.mood = 0.45f
    // Fresh clothes and a clean slate.
    o.thoughts.clear()
    o.addThought("Recently joined", 0.1f, tick, 3 * TICKS_PER_DAY)
    for (w in WorkType.entries) o.priority[w.ordinal] = if (w.skill() == null) 3 else if (o.skill[w.skill()!!.ordinal] >= 6) 2 else 3
    o.priority[WorkType.HAUL.ordinal] = 4
    say("${o.name} has joined your colony!", 1)
}

private fun Game.driveFeedPrisoner(p: Pawn, j: Job) {
    val o = pawnById(j.targetPawn)
    if (o == null || !o.alive || !o.prisoner) { endJob(p); return }
    when (j.stage) {
        0 -> {
            val ii = map.idx(j.dx, j.dy)
            val s = map.items[ii]
            if (s == null || !s.type.isFood) { abort(p); return }
            val r = goTo(p, j.dx, j.dy)
            if (r == -1) abort(p, true) else if (r == 0) { pickUp(p, ii, s.type, if (s.type.nutrition >= 0.5f) 1 else 20); j.stage = 1 }
        }
        1 -> {
            val r = goTo(p, o.x, o.y, adjacent = true)
            if (r == -1) abort(p) else if (r == 0) {
                val t = p.carryType
                if (t != null && p.carryCount > 0) {
                    // Place it at their feet; they will eat it themselves.
                    map.drop(t, p.carryCount, o.x, o.y, p.carryQuality)
                    p.carryCount = 0; p.carryType = null
                }
                endJob(p)
            }
        }
    }
}

// ---------------------------------------------------------------- critters

private fun Game.driveFeedAnimal(p: Pawn, j: Job) {
    val a = pawnById(j.targetPawn)
    if (a == null || !a.alive || a.food > 0.5f) { endJob(p); return }
    when (j.stage) {
        0 -> {
            val ii = map.idx(j.dx, j.dy)
            val s = map.items[ii]
            if (s == null) { abort(p); return }
            val r = goTo(p, j.dx, j.dy)
            if (r == -1) abort(p, true) else if (r == 0) { pickUp(p, ii, s.type, 20); unreserve(p, j.key); j.stage = 1 }
        }
        1 -> {
            val r = goTo(p, a.x, a.y, adjacent = true)
            if (r == -1) abort(p) else if (r == 0) {
                val t = p.carryType
                if (t != null) a.food = min(1f, a.food + t.nutrition * p.carryCount * 1.2f)
                p.carryCount = 0; p.carryType = null
                p.gainXp(SkillType.ANIMALS, 60f)
                endJob(p)
            }
        }
    }
}

private fun Game.driveGather(p: Pawn, j: Job) {
    val a = pawnById(j.targetPawn)
    if (a == null || !a.alive || a.race.product == null) { endJob(p); return }
    if (j.stage == 0) {
        val r = goTo(p, a.x, a.y, adjacent = true)
        if (r == -1) abort(p) else if (r == 0) { j.stage = 1; j.timer = 0 }
        return
    }
    if (doWork(p, j, SkillType.ANIMALS, 280f)) {
        val n = (a.race.productPerDay * a.animalProductTimer / TICKS_PER_DAY.toFloat()).toInt()
        val prod = a.race.product!!
        if (n > 0) map.drop(prod, n, p.x, p.y)
        a.animalProductTimer = 0
        endJob(p)
    }
}

private fun Game.driveHunt(p: Pawn, j: Job) {
    val a = pawnById(j.targetPawn)
    if (a == null || !a.alive || !a.huntMark) { endJob(p); return }
    val w = p.weapon
    val d = distance(p.x, p.y, a.x, a.y)
    if (w.ranged) {
        if (d <= w.range * 0.8f && map.lineOfSight(p.x, p.y, a.x, a.y)) {
            if (p.moveCd > 0) p.moveCd-- else fire(p, a)
            return
        }
        if (goTo(p, a.x, a.y, adjacent = true) == -1) abort(p)
    } else {
        if (d < 1.6f) { fire(p, a); return }
        if (goTo(p, a.x, a.y, adjacent = true) == -1) abort(p)
    }
    if (++j.timer > tk(2600)) abort(p)
}

/** Teaches a tame animal one trick at a time. Each attempt takes a while and succeeds more often with a skilled handler. */
private fun Game.driveTrain(p: Pawn, j: Job) {
    val a = pawnById(j.targetPawn)
    if (a == null || !a.alive || !a.tame || a.trained >= MAX_TRICKS) { endJob(p); return }
    if (j.stage == 0) {
        val r = goTo(p, a.x, a.y, adjacent = true)
        if (r == -1) abort(p) else if (r == 0) { j.stage = 1; j.timer = 0 }
        return
    }
    if (++j.timer >= tk(300)) {
        j.timer = 0
        p.gainXp(SkillType.ANIMALS, 60f)
        val chance = (0.25f + p.level(SkillType.ANIMALS) * 0.05f) * (1.2f - a.race.wildness)
        if (rng.float() < chance.coerceIn(0.05f, 0.9f)) {
            a.trained++
            say("${p.name} taught ${a.name} a trick.", 0)
            endJob(p)
        }
    }
}

private fun Game.driveTame(p: Pawn, j: Job) {
    val a = pawnById(j.targetPawn)
    if (a == null || !a.alive || !a.tameMark) { endJob(p); return }
    if (j.stage == 0) {
        val r = goTo(p, a.x, a.y, adjacent = true)
        if (r == -1) abort(p) else if (r == 0) { j.stage = 1; j.timer = 0 }
        return
    }
    if (++j.timer >= tk(260)) {
        j.timer = 0
        val skill = p.level(SkillType.ANIMALS)
        val chance = (0.18f + skill * 0.035f - a.race.tameDifficulty * 0.1f).coerceIn(0.03f, 0.9f) * (0.5f + (1f - a.race.wildness) * 0.6f)
        p.gainXp(SkillType.ANIMALS, 150f)
        if (rng.float() < chance) {
            a.faction = Faction.PLAYER; a.tame = true; a.manhunter = false; a.tameMark = false
            a.name = rng.pick(Names.animal)
            a.master = p.id
            say("${p.name} tamed a ${a.race.label.lowercase()}: ${a.name}.", 1)
            endJob(p)
        } else if (rng.chance(0.12f * a.race.dangerous)) {
            a.manhunter = true
            dealDamage(p, a.race.weapon.kind, a.race.weapon.damage, 0f, a)
            say("The ${a.race.label.lowercase()} attacked ${p.name} during taming!", 3)
            endJob(p)
        }
    }
}

private fun Game.driveSlaughter(p: Pawn, j: Job) {
    val a = pawnById(j.targetPawn)
    if (a == null || !a.alive || !a.slaughterMark) { endJob(p); return }
    if (j.stage == 0) {
        val r = goTo(p, a.x, a.y, adjacent = true)
        if (r == -1) abort(p) else if (r == 0) { j.stage = 1; j.timer = 0 }
        return
    }
    if (++j.timer > tk(90)) {
        a.slaughterMark = false
        die(a, "slaughtered", p)
        endJob(p)
    }
}

private fun Game.driveButcher(p: Pawn, j: Job) {
    when (j.stage) {
        0 -> {
            val i = map.idx(j.tx, j.ty)
            val s = map.items[i]
            if (s == null || s.corpseOf == null) { endJob(p); return }
            val r = goTo(p, j.tx, j.ty)
            if (r == -1) abort(p, true) else if (r == 0) { map.items.remove(i); j.stack = s; j.stage = 1 }
        }
        1 -> {
            val t = map.building[map.idx(j.dx, j.dy)]
            if (t == null || !t.built) { endJob(p); return }
            val r = goTo(p, j.dx, j.dy, adjacent = true)
            if (r == -1) abort(p) else if (r == 0) { j.stage = 2; j.work = 0f }
        }
        2 -> {
            val s = j.stack ?: run { endJob(p); return }
            val race = s.corpseRace ?: Race.HARE
            if (doWork(p, j, SkillType.COOKING, 160f + race.meat * 1.2f)) {
                val eff = 0.55f + p.workSpeed(SkillType.COOKING).coerceAtMost(1.5f) * 0.3f
                val meat = (race.meat * eff * (1f - s.rot * 0.5f)).toInt()
                if (meat > 0) map.drop(if (race.insect) ItemType.INSECT_MEAT else ItemType.MEAT, meat, p.x, p.y)
                val leather = (race.leather * eff).toInt()
                if (leather > 0) map.drop(ItemType.LEATHER, leather, p.x, p.y)
                j.stack = null
                map.filth[map.idx(p.x, p.y)] = min(4, map.filth[map.idx(p.x, p.y)] + 1).toByte()
                endJob(p)
            }
        }
    }
}
