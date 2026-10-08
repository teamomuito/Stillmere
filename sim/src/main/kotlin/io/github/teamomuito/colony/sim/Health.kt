package io.github.teamomuito.colony.sim

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

fun Pawn.partMax(i: Int): Float = race.body[i].hp * race.hpScale

fun Pawn.partMissing(i: Int): Boolean {
    if (implants.containsKey(i)) return false
    for (inj in injuries) if (inj.part == i && inj.missing) return true
    val par = race.body[i].parent
    return par >= 0 && partMissing(par)
}

fun Pawn.partDamage(i: Int): Float {
    var d = 0f
    for (inj in injuries) if (inj.part == i && !inj.missing && !inj.scar) d += inj.severity
    return d
}

fun Pawn.partEff(i: Int): Float {
    if (partMissing(i)) return 0f
    val imp = implants[i]
    val base = (1f - partDamage(i) / partMax(i)).coerceIn(0f, 1f)
    return if (imp != null) base * imp.eff else base
}

private fun Pawn.avgEff(tag: PartTag, default: Float = 1f): Float {
    var n = 0
    var s = 0f
    for ((i, d) in race.body.withIndex()) if (d.tag == tag) { n++; s += partEff(i) }
    return if (n == 0) default else s / n
}

private fun Pawn.hasTag(tag: PartTag) = race.body.any { it.tag == tag }

fun Game.recomputeHealth(p: Pawn) {
    p.healthDirty = false
    var pain = 0f
    for (inj in p.injuries) {
        if (inj.scar || inj.missing || p.race.mech) continue
        pain += inj.severity * inj.kind.pain * 0.014f / max(0.5f, p.race.hpScale.let { Math.sqrt(it.toDouble()).toFloat() })
    }
    for (h in p.hediffs) pain += h.kind.pain * h.severity
    if (Trait.WIMP in p.traits) pain *= 1.4f
    if (Trait.TOUGH in p.traits) pain *= 0.7f
    // Painkilling drugs.
    if (p.hediffs.any { it.kind == HediffKind.ALCOHOL_HIGH }) pain *= 0.8f
    p.pain = min(1f, pain)

    val legs = p.avgEff(PartTag.LEG)
    val feet = p.avgEff(PartTag.FOOT)
    var moving = legs * (0.75f + 0.25f * feet)
    val hands = p.avgEff(PartTag.HAND)
    val arms = p.avgEff(PartTag.ARM)
    var manip = if (p.hasTag(PartTag.HAND)) hands * 0.7f + arms * 0.3f else 1f
    var sight = if (p.hasTag(PartTag.EYE)) p.avgEff(PartTag.EYE) else 1f
    var hearing = if (p.hasTag(PartTag.EAR)) p.avgEff(PartTag.EAR) else 1f
    val jaw = if (p.hasTag(PartTag.JAW)) p.avgEff(PartTag.JAW) else 1f
    val breathing = if (p.hasTag(PartTag.LUNG)) min(1f, p.avgEff(PartTag.LUNG) * 1.4f) else 1f
    val pumping = if (p.hasTag(PartTag.HEART)) p.avgEff(PartTag.HEART) else 1f
    var filtration = 1f
    if (p.hasTag(PartTag.LIVER)) filtration = min(filtration, p.avgEff(PartTag.LIVER) * 1.2f)
    if (p.hasTag(PartTag.KIDNEY)) filtration = min(filtration, min(1f, p.avgEff(PartTag.KIDNEY) * 1.6f))
    val brain = if (p.hasTag(PartTag.BRAIN)) p.avgEff(PartTag.BRAIN) else 1f

    var cons = brain
    cons *= (1f - p.pain * 0.55f)
    if (p.bloodLoss > 0.3f) cons *= max(0f, 1f - (p.bloodLoss - 0.3f) * 1.4f)
    cons *= min(1f, breathing + 0.2f)
    cons *= min(1f, pumping + 0.25f)
    var eating = jaw * min(1f, 0.4f + p.avgEff(PartTag.STOMACH))
    for (h in p.hediffs) {
        val s = min(1f, h.severity)
        cons *= (1f - h.kind.cons * s)
        moving *= (1f - h.kind.move * s)
        manip *= (1f - h.kind.manip * s)
        eating *= (1f - h.kind.eat * s)
        sight *= (1f - h.kind.sight * s)
        hearing *= (1f - h.kind.hearing * s)
        if (h.kind == HediffKind.ANESTHESIA) cons = 0f
    }
    if (p.pregnantUntil > 0L && p.race == Race.HUMAN) moving *= 0.88f
    // Pain and sickness slow people down a bit.
    moving *= (1f - p.pain * 0.2f)
    manip *= (1f - p.pain * 0.2f)

    p.cap[Cap.CONSCIOUSNESS.ordinal] = cons.coerceIn(0f, 1f)
    p.cap[Cap.MOVING.ordinal] = moving.coerceIn(0f, 1.2f).let { if (legs <= 0.01f) min(it, 0.0f) else it }
    p.cap[Cap.MANIPULATION.ordinal] = manip.coerceIn(0f, 1f)
    p.cap[Cap.SIGHT.ordinal] = sight
    p.cap[Cap.HEARING.ordinal] = hearing
    p.cap[Cap.TALKING.ordinal] = jaw
    p.cap[Cap.EATING.ordinal] = eating.coerceIn(0f, 1f)
    p.cap[Cap.BREATHING.ordinal] = breathing
    p.cap[Cap.PUMPING.ordinal] = pumping
    p.cap[Cap.FILTRATION.ordinal] = filtration.coerceIn(0f, 1f)
}

private fun Game.pickOuterPart(p: Pawn): Int {
    val body = p.race.body
    var total = 0f
    for ((i, d) in body.withIndex()) if (!d.inner && !p.partMissing(i)) total += d.coverage
    if (total <= 0f) return 0
    var r = rng.float() * total
    for ((i, d) in body.withIndex()) {
        if (d.inner || p.partMissing(i)) continue
        r -= d.coverage
        if (r <= 0f) return i
    }
    return 0
}

private fun Game.pickChild(p: Pawn, parent: Int): Int {
    val body = p.race.body
    val kids = body.indices.filter { body[it].parent == parent && !p.partMissing(it) }
    if (kids.isEmpty()) return -1
    return rng.pick(kids)
}

/** Resolve a hit: pick a body part, apply armor, create a wound and handle destruction. */
fun Game.dealDamage(
    target: Pawn, kind: DamageKind, amount: Float, armorPen: Float = 0f, source: Pawn? = null,
    partHint: Int = -1,
) {
    if (target.dead) return
    debugHook?.invoke("t=$tick dmg ${target.name} ${kind.name} ${amount.toInt()} from ${source?.name ?: "?"} (${source?.race?.label})")
    var part = if (partHint >= 0) partHint else pickOuterPart(target)
    val def = target.race.body[part]
    var dmg = amount / max(0.6f, Math.sqrt(target.race.hpScale.toDouble()).toFloat()).let { if (target.race.isAnimal) 1f else it }
    // Armor.
    val cover = if (def.cover != 0) def.cover else if (def.inner) target.race.body[def.parent].cover else 0
    val sharp = kind.sharp || kind == DamageKind.BLAST
    val armor = max(0f, target.armorFor(cover, sharp) - armorPen)
    if (armor > 0f) {
        val roll = rng.float()
        if (roll < armor * 0.5f && dmg * (1f - armor) < 2.5f) {
            // Deflected.
            for (w in target.apparel) w.hp -= dmg * 0.08f
            return
        }
        dmg *= (1f - armor)
        for (w in target.apparel) if ((w.type.apparel?.cover ?: 0) and cover != 0) w.hp -= dmg * 0.1f
        target.apparel.removeAll { it.hp <= 0f }
    }
    if (dmg < 0.3f) return
    if (Trait.TOUGH in target.traits) dmg *= 0.75f
    applyWound(target, part, kind, dmg, source)
    // Penetration into organs.
    if (!def.inner && (kind.sharp || kind == DamageKind.BLAST) && dmg >= def.hp * target.race.hpScale * 0.35f && rng.chance(0.55f)) {
        val child = pickChild(target, part)
        if (child >= 0) applyWound(target, child, kind, dmg * 0.55f, source)
    }
    target.healthDirty = true
    checkDeath(target, source)
}

internal fun Game.applyWound(target: Pawn, part: Int, kind: DamageKind, dmg: Float, source: Pawn?) {
    val def = target.race.body[part]
    if (target.partMissing(part)) return
    val maxHp = target.partMax(part)
    val cur = target.partDamage(part)
    val applied = min(dmg, max(0f, maxHp - cur) + dmg * 0.0f)
    val inj = Injury(part, kind, if (cur + dmg >= maxHp) max(0.5f, maxHp - cur) else dmg)
    inj.bleed = if (target.race.mech) 0f else kind.bleed * inj.severity * 1.35e-6f * (if (def.inner) 1.7f else 1f)
    if (kind == DamageKind.BRUISE || kind == DamageKind.CRUSH) inj.bleed *= 0.1f
    inj.infectable = kind.infect > 0f
    target.injuries.add(inj)
    if (cur + dmg >= maxHp) destroyPart(target, part, kind, source)
    // Blood spatter.
    val i = map.idx(target.x, target.y)
    if (kind.bleed > 0.3f && dmg > 2f && map.filth[i] < 4 && rng.chance(0.5f)) map.filth[i] = (map.filth[i] + 1).toByte()
    if (applied > 0f && target.faction == Faction.WILD && target.race.isAnimal && !target.hostile) {
        // Wild animals react by running or fighting.
        if (target.race.dangerous > 0.3f && source != null && !source.isAnimal) { target.manhunter = true; target.predatorTarget = source.id }
    }
}

private fun Game.destroyPart(p: Pawn, part: Int, kind: DamageKind, source: Pawn?) {
    val body = p.race.body
    val def = body[part]
    // Replace the wounds with a "missing" record; nested parts go with it.
    p.injuries.removeAll { it.part == part || (body[it.part].parent == part) }
    p.implants.remove(part)
    val fatal = def.vital
    val m = Injury(part, kind, def.hp * p.race.hpScale, bleed = if (p.race.mech) 0f else def.hp * p.race.hpScale * kind.bleed * 1.5e-6f * (if (def.inner) 1.2f else 1f), missing = true, permanent = true)
    m.infectable = false
    p.injuries.add(m)
    if (!def.inner && def.tag != PartTag.TORSO && (p.colonist || p.prisoner)) say("${p.name}'s ${def.label} was destroyed!", 3)
    p.healthDirty = true
}


fun Game.addHediff(p: Pawn, kind: HediffKind, severity: Float, part: Int = -1): Hediff {
    val ex = p.hediffs.firstOrNull { it.kind == kind && it.part == part }
    if (ex != null) { ex.severity = min(1f, ex.severity + severity); return ex }
    val h = Hediff(kind, severity)
    h.part = part
    p.hediffs.add(h)
    p.healthDirty = true
    return h
}

fun Game.removeHediff(p: Pawn, kind: HediffKind) {
    if (p.hediffs.removeAll { it.kind == kind }) p.healthDirty = true
}

fun Pawn.hediff(kind: HediffKind): Hediff? = hediffs.firstOrNull { it.kind == kind }

internal fun Game.checkDeath(p: Pawn, source: Pawn?) {
    if (p.dead) return
    val body = p.race.body
    var cause: String? = null
    for ((i, d) in body.withIndex()) if (d.vital && !d.inner || d.tag == PartTag.BRAIN || d.tag == PartTag.HEART) {
        if (p.partMissing(i)) { cause = "${d.label} destroyed"; break }
        if (d.tag == PartTag.BRAIN && p.partEff(i) <= 0.001f) { cause = "brain destroyed"; break }
        if (d.tag == PartTag.HEART && p.partEff(i) <= 0.001f) { cause = "heart failure"; break }
    }
    val lungs = body.indices.filter { body[it].tag == PartTag.LUNG }
    if (cause == null && lungs.isNotEmpty() && lungs.all { p.partEff(it) <= 0.001f }) cause = "suffocation"
    if (cause == null && p.bloodLoss >= 1f) cause = "blood loss"
    if (cause == null && p.injuries.any { it.infection >= 1f }) cause = "infection"
    if (cause == null) for (h in p.hediffs) if (h.kind.lethal && h.severity >= 1f) { cause = h.kind.label.lowercase(); break }
    if (cause != null) die(p, cause, source)
}

// ---------------------------------------------------------------------------- tick

fun Game.healthTick(p: Pawn, dt: Int) {
    if (p.dead) return
    if (p.injuries.isEmpty() && p.hediffs.isEmpty() && p.bloodLoss <= 0f) {
        if (p.healthDirty) recomputeHealth(p)
        return
    }
    val resting = p.job?.type == JobType.SLEEP || p.job?.type == JobType.REST
    var bleed = 0f
    val inBed = resting && p.bedId >= 0
    val hospital = p.bedId >= 0 && map.building[p.bedId]?.def?.medical == true
    val it = p.injuries.iterator()
    while (it.hasNext()) {
        val inj = it.next()
        inj.age += dt
        if (inj.scar || inj.missing && inj.bleed <= 0f) {
            if (inj.missing) inj.bleed = 0f
            continue
        }
        // Bleeding.
        // A tended wound is bandaged and stops bleeding; poor care leaves a trickle.
        val b = if (inj.tended) inj.bleed * (1f - inj.tendQuality) * 0.06f else inj.bleed
        bleed += b
        if (inj.missing) {
            // Stumps stop bleeding over time.
            inj.bleed = max(0f, inj.bleed - 2e-9f * dt)
            continue
        }
        // Tending wears off.
        if (inj.tended && inj.age % 30000 < dt && (inj.infection > 0f)) inj.tended = false
        // Healing.
        var heal = (if (inj.tended) 8f * (0.55f + inj.tendQuality) else 3.2f) / TICKS_PER_DAY * dt
        if (inBed) heal *= 1.35f
        if (hospital) heal *= 1.15f
        if (inj.kind == DamageKind.BRUISE) heal *= 1.5f
        if (inj.kind == DamageKind.BURN) heal *= 0.7f
        if (p.food < 0.05f) heal *= 0.3f
        inj.severity -= heal
        if (inj.severity <= 0.3f) {
            if (inj.kind.sharp && rng.chance(0.18f)) {
                inj.scar = true; inj.severity = 0f; inj.bleed = 0f; inj.permanent = true; inj.infection = 0f
            } else it.remove()
            p.healthDirty = true
            continue
        }
        inj.bleed *= (1f - dt / (if (inj.tended) 12000f else 34000f)).coerceAtLeast(0.5f)
        if (inj.bleed < 2e-8f) inj.bleed = 0f
        // Infection: an untreated infection grows faster than the body can fight it; good care tips the balance.
        if (inj.infection > 0f) {
            val q = if (inj.tended) inj.tendQuality else 0f
            val grow = 1.1f / TICKS_PER_DAY * dt * (1f - 0.8f * q)
            inj.immune += 0.9f / TICKS_PER_DAY * dt * (1f + 0.6f * q + (if (resting) 0.3f else 0f))
            inj.infection += grow
            if (inj.immune > inj.infection * 1.1f) inj.infection -= (inj.immune - inj.infection) * 0.9f / TICKS_PER_DAY * dt * 2f
            if (inj.infection >= 1f) { checkDeath(p, null); if (p.dead) return }
            if (inj.infection <= 0f) { inj.infection = 0f; inj.immune = 0f }
        } else if (inj.infectable && (!inj.tended || inj.tendQuality < 0.3f) && inj.severity > 2f) {
            var chance = inj.kind.infect * 0.7f / TICKS_PER_DAY * dt * 2.2f
            if (!map.roomIndoorAt(map.idx(p.x, p.y))) chance *= 1.5f
            if (hospital) chance *= 0.3f
            if (inj.tended) chance *= (1f - inj.tendQuality) * 0.5f
            if (rng.float() < chance) {
                inj.infection = 0.04f
                if (p.colonist) say("${p.name}'s ${p.race.body[inj.part].label} wound is infected.", 2)
            }
        }
    }
    // Blood.
    if (bleed > 0f) {
        p.bloodLoss = min(1.1f, p.bloodLoss + bleed * dt)
        if (p.bloodLoss >= 1f) checkDeath(p, null)
        // Blood on the floor.
        if (rng.chance(0.04f * dt / 10f)) {
            val i = map.idx(p.x, p.y)
            if (map.filth[i] < 4) map.filth[i] = (map.filth[i] + 1).toByte()
        }
        p.healthDirty = true
    } else if (p.bloodLoss > 0f) {
        p.bloodLoss = max(0f, p.bloodLoss - (if (p.food > 0.2f) 0.36f else 0.1f) / TICKS_PER_DAY * dt)
        p.healthDirty = true
    }
    if (p.dead) return
    // Hediffs.
    val hi = p.hediffs.iterator()
    while (hi.hasNext()) {
        val h = hi.next()
        h.age += dt
        when (h.kind.category) {
            0 -> {
                if (h.tended && h.age % 15000 < dt) h.tended = false
                val per = dt / TICKS_PER_DAY.toFloat()
                var imm = h.kind.immunityPerDay * per
                if (h.tended) imm *= 1f + 0.5f * h.tendQuality
                if (resting) imm *= 1.25f
                h.immunity += imm
                var prog = h.kind.progressPerDay * per
                if (h.tended) prog *= (1f - 0.65f * h.tendQuality)
                if (h.immunity >= 1f) { hi.remove(); p.healthDirty = true; if (p.colonist) say("${p.name} recovered from ${h.kind.label.lowercase()}.", 1); continue }
                // Severity tracks the gap between the disease and immunity.
                if (h.immunity < h.severity * 0.9f || h.severity < 0.2f) h.severity += prog
                else h.severity = max(0.05f, h.severity - prog * 0.5f)
                if (!h.kind.lethal) h.severity = min(h.severity, 0.95f)
                if (h.kind.lethal && h.severity >= 1f) { checkDeath(p, null); if (p.dead) return }
                p.healthDirty = true
            }
            1 -> { /* environmental hediffs are driven from slowTick */ }
            2 -> {
                h.duration -= dt
                if (h.duration <= 0) { hi.remove(); p.healthDirty = true }
            }
            3 -> { /* tolerance and addiction handled by drug logic */ }
            5 -> { /* chronic: stays for life */ }
        }
    }
    if (p.healthDirty) recomputeHealth(p)
    // Dropping into shock or unconsciousness.
    if (!p.downed && (p.cap[Cap.CONSCIOUSNESS.ordinal] < 0.3f || p.cap[Cap.MOVING.ordinal] < 0.12f) || p.pain >= 0.85f && !p.downed) {
        if (!p.dead) downPawn(p)
    }
}

fun Game.downPawn(p: Pawn) {
    if (p.downed || p.dead) return
    p.downed = true
    p.drafted = false
    endJob(p)
    if (p.carrying >= 0) { pawnById(p.carrying)?.carriedBy = -1; p.carrying = -1 }
    if (p.colonist) say("${p.name} is down!", 3)
    else if (p.faction == Faction.ENEMY) say("${p.name} is down.", 0)
}

fun Game.maybeStandUp(p: Pawn) {
    if (!p.downed || p.dead) return
    if (p.cap[Cap.CONSCIOUSNESS.ordinal] >= 0.4f && (p.cap[Cap.MOVING.ordinal] >= 0.16f) && p.pain < 0.75f && p.carriedBy < 0) {
        p.downed = false
        if (p.colonist) say("${p.name} got back up.", 0)
    }
}

// ---------------------------------------------------------------------------- tending

fun Game.tendQuality(doctor: Pawn?, med: ItemType?, hospital: Boolean): Float {
    val skill = doctor?.level(SkillType.MEDICINE) ?: 2
    val base = 0.18f + 0.032f * skill
    val medFactor = when {
        med == null -> 0.45f
        else -> 0.55f + med.potency * 0.45f
    }
    var q = base * medFactor * (0.85f + rng.float() * 0.3f)
    if (hospital) q *= 1.1f
    if (doctor != null) q *= (0.6f + 0.4f * doctor.cap[Cap.MANIPULATION.ordinal])
    return q.coerceIn(0.05f, 1f)
}

/** Tends every wound and illness that needs attention in one go. */
fun Game.tendPawn(doctor: Pawn?, patient: Pawn, med: ItemType?) {
    val hospital = patient.bedId >= 0 && map.building[patient.bedId]?.def?.medical == true
    val q = tendQuality(doctor, med, hospital)
    var any = false
    for (inj in patient.injuries) {
        if (inj.scar || inj.missing && inj.bleed <= 0f) continue
        if (!inj.tended || q > inj.tendQuality) {
            inj.tended = true; inj.tendQuality = q; any = true
            if (inj.infection > 0f) inj.infection = max(0f, inj.infection - 0.35f * q)
            inj.age = 0
        }
    }
    for (h in patient.hediffs) if (h.kind.needsTend) { h.tended = true; h.tendQuality = q; h.age = 0; any = true }
    if (any) patient.healthDirty = true
    doctor?.gainXp(SkillType.MEDICINE, 220f)
}

fun Game.die(p: Pawn, cause: String, source: Pawn? = null) {
    if (p.dead) return
    p.dead = true
    p.deathTick = tick
    if (p.carrying >= 0) { pawnById(p.carrying)?.carriedBy = -1; p.carrying = -1 }
    if (p.carriedBy >= 0) { pawnById(p.carriedBy)?.carrying = -1; p.carriedBy = -1 }
    endJob(p)
    releaseAll(p)
    onPawnDied(p, cause, source)
}

/** Public helper to put a wound on a specific part (frostbite, burns, and so on). */
fun Game.woundPart(p: Pawn, part: Int, kind: DamageKind, dmg: Float, source: Pawn? = null) {
    if (p.dead || part < 0 || part >= p.race.body.size) return
    applyWound(p, part, kind, dmg, source)
    p.healthDirty = true
    checkDeath(p, source)
}

fun Game.partsWithTag(p: Pawn, tag: PartTag): List<Int> = p.race.body.indices.filter { p.race.body[it].tag == tag && !p.partMissing(it) }
