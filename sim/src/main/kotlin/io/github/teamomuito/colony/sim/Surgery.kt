package io.github.teamomuito.colony.sim

import kotlin.math.max
import kotlin.math.min

enum class Implant(
    val label: String, val eff: Float, val tags: Set<PartTag>, val research: Research?, val cost: List<Pair<ItemType, Int>>,
    val bionic: Boolean = false,
) {
    PEG_LEG("Peg leg", 0.55f, setOf(PartTag.LEG, PartTag.FOOT), Research.BIONICS_BASIC, listOf(ItemType.WOOD to 20)),
    HOOK_HAND("Hook hand", 0.5f, setOf(PartTag.HAND, PartTag.ARM), Research.BIONICS_BASIC, listOf(ItemType.WOOD to 10, ItemType.STEEL to 8)),
    BIONIC_ARM("Bionic arm", 1.3f, setOf(PartTag.ARM, PartTag.HAND), Research.BIONICS, listOf(ItemType.PLASTEEL to 15, ItemType.GOLD to 8, ItemType.COMPONENT to 6), true),
    BIONIC_LEG("Bionic leg", 1.3f, setOf(PartTag.LEG, PartTag.FOOT), Research.BIONICS, listOf(ItemType.PLASTEEL to 15, ItemType.GOLD to 8, ItemType.COMPONENT to 6), true),
    BIONIC_EYE("Bionic eye", 1.25f, setOf(PartTag.EYE), Research.BIONICS, listOf(ItemType.PLASTEEL to 6, ItemType.GOLD to 8, ItemType.COMPONENT to 4), true),
    BIONIC_EAR("Bionic ear", 1.2f, setOf(PartTag.EAR), Research.BIONICS, listOf(ItemType.PLASTEEL to 4, ItemType.GOLD to 4, ItemType.COMPONENT to 3), true),
    BIONIC_HEART("Bionic heart", 1.3f, setOf(PartTag.HEART), Research.BIONICS, listOf(ItemType.PLASTEEL to 12, ItemType.GOLD to 12, ItemType.COMPONENT to 6), true),
}

enum class SurgeryKind { AMPUTATE, INSTALL }

class SurgeryOrder(val kind: SurgeryKind, val part: Int, val implant: Implant?) {
    fun label(p: Pawn) = when (kind) {
        SurgeryKind.AMPUTATE -> "Amputate ${p.race.body[part].label}"
        SurgeryKind.INSTALL -> "Install ${implant!!.label.lowercase()} (${p.race.body[part].label})"
    }
}

fun Game.availableSurgeries(p: Pawn): List<SurgeryOrder> {
    val out = ArrayList<SurgeryOrder>()
    if (p.race.isAnimal || p.race.mech) return out
    for ((i, d) in p.race.body.withIndex()) {
        val missing = p.partMissing(i)
        val has = p.implants[i]
        if (!missing && d.tag in setOf(PartTag.ARM, PartTag.HAND, PartTag.LEG, PartTag.FOOT, PartTag.EAR, PartTag.NOSE, PartTag.FINGER, PartTag.TOE) && has == null) out += SurgeryOrder(SurgeryKind.AMPUTATE, i, null)
        for (imp in Implant.entries) {
            if (d.tag !in imp.tags) continue
            if (imp.research != null && imp.research !in researchDone) continue
            if (has != null) continue
            if (!missing && !imp.bionic) continue
            if (!missing && p.partDamage(i) <= 0f && !imp.bionic) continue
            out += SurgeryOrder(SurgeryKind.INSTALL, i, imp)
        }
    }
    return out
}

fun Game.queueSurgery(p: Pawn, o: SurgeryOrder) {
    if (p.surgeries.any { it.part == o.part && it.kind == o.kind }) return
    p.surgeries.add(o)
}

internal fun Game.findSurgery(doc: Pawn): Job? {
    if (doc.level(SkillType.MEDICINE) < 3) return null
    for (o in pawns) {
        if (!o.alive || o.surgeries.isEmpty() || o.carriedBy >= 0) continue
        if (o.faction != Faction.PLAYER && !o.prisoner) continue
        val k = pawnKey(o.id, K_PATIENT)
        if (!isFree(doc, k) || isBad(doc, k)) continue
        val bedHere = map.building[map.idx(o.x, o.y)]
        if (bedHere == null || !bedHere.def.sleeps) continue
        val order = o.surgeries.first()
        // Materials.
        if (order.kind == SurgeryKind.INSTALL) {
            var ok = true
            for ((t, n) in order.implant!!.cost) if (map.countItems(t) < n) ok = false
            if (!ok) continue
        }
        reserve(doc, k)
        val j = Job(JobType.SURGERY, o.x, o.y)
        j.targetPawn = o.id; j.key = k
        return j
    }
    return null
}

internal fun Game.driveSurgery(p: Pawn, j: Job) {
    val o = pawnById(j.targetPawn)
    if (o == null || !o.alive || o.surgeries.isEmpty()) { endJob(p); return }
    val order = o.surgeries.first()
    when (j.stage) {
        0 -> {
            // Gather materials for implants.
            if (order.kind == SurgeryKind.AMPUTATE) { j.stage = 2; return }
            val cost = order.implant!!.cost
            if (j.ingIdx >= cost.size) { j.stage = 2; return }
            val (type, need) = cost[j.ingIdx]
            val s = nearestItem(p) { it.type == type }
            if (s == null) { abortSurg(p, j); return }
            reserve(p, key(map.idx(s.x, s.y), K_ITEM))
            j.dx = s.x; j.dy = s.y
            j.stage = 1
            j.amount = need - j.collected
        }
        1 -> {
            val ii = map.idx(j.dx, j.dy)
            val s = map.items[ii]
            val cost = order.implant!!.cost
            val (type, need) = cost[j.ingIdx]
            if (s == null || s.type != type) { j.stage = 0; return }
            val r = goTo(p, j.dx, j.dy)
            if (r == -1) abortSurg(p, j)
            else if (r == 0) {
                val n = min(need - j.collected, s.count)
                map.take(ii, n)
                j.held.add(Triple(type, n, s.quality))
                j.collected += n
                unreserve(p, key(ii, K_ITEM))
                if (j.collected >= need) { j.ingIdx++; j.collected = 0 }
                j.stage = 0
            }
        }
        2 -> {
            val r = goTo(p, o.x, o.y, adjacent = true)
            if (r == -1) abortSurg(p, j) else if (r == 0) { j.stage = 3; j.work = 0f }
        }
        3 -> {
            j.work += p.workSpeed(SkillType.MEDICINE) / TIME_SCALE
            p.gainXp(SkillType.MEDICINE, 0.08f / TIME_SCALE)
            if (j.work >= 700f) {
                val hospital = map.building[map.idx(o.x, o.y)]?.def?.medical == true
                val chance = (0.5f + 0.03f * p.level(SkillType.MEDICINE) + (if (hospital) 0.1f else 0f) + (if (order.kind == SurgeryKind.AMPUTATE) 0.3f else 0f)).coerceIn(0.3f, 0.98f)
                val h = addHediff(o, HediffKind.ANESTHESIA, 0.5f); h.duration = tk(3000)
                j.held.clear()
                if (rng.float() < chance) {
                    applySurgery(o, order)
                    say("${p.name} finished: ${order.label(o)} on ${o.name}.", 1)
                } else {
                    say("${p.name}'s surgery on ${o.name} failed!", 3)
                    for (k in 0 until 2) dealDamage(o, DamageKind.CUT, 6f, 0f, p, order.part.coerceAtMost(o.race.body.size - 1))
                }
                o.surgeries.remove(order)
                endJob(p)
            }
        }
    }
}

private fun Game.abortSurg(p: Pawn, j: Job) {
    markUnreachable(p, j.key)
    endJob(p)
}

fun Game.applySurgery(o: Pawn, order: SurgeryOrder) {
    val part = order.part
    when (order.kind) {
        SurgeryKind.AMPUTATE -> {
            o.injuries.removeAll { it.part == part }
            val m = Injury(part, DamageKind.CUT, o.partMax(part), bleed = o.partMax(part) * 4e-7f, missing = true, permanent = true)
            m.infectable = false
            m.tended = true; m.tendQuality = 0.6f
            o.injuries.add(m)
            o.implants.remove(part)
        }
        SurgeryKind.INSTALL -> {
            val imp = order.implant!!
            o.injuries.removeAll { it.part == part && (it.missing || !it.scar) }
            o.implants[part] = imp
        }
    }
    o.healthDirty = true
}
