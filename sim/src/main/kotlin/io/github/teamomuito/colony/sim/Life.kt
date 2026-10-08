package io.github.teamomuito.colony.sim

import kotlin.math.max
import kotlin.math.min

enum class LifeStage(val label: String) { BABY("Baby"), CHILD("Child"), TEEN("Teenager"), JUVENILE("Juvenile"), ADULT("Adult") }

const val DAYS_PER_YEAR = 60
const val GESTATION_DAYS = 30

/** Work a child may do: light chores only. Teens can do most things. Babies nothing. */
fun Pawn.ageIncapable(): Int {
    if (isAnimal || age >= 18) return 0
    if (age < 3) return -1
    val ok = if (age < 13) listOf(WorkType.HAUL, WorkType.CLEAN, WorkType.HANDLE, WorkType.PATIENT, WorkType.GROW, WorkType.PLANT_CUT)
    else WorkType.entries.filter { it != WorkType.DOCTOR && it != WorkType.WARDEN && it != WorkType.HUNT }
    var mask = 0
    for (w in WorkType.entries) if (w !in ok) mask = mask or (1 shl w.ordinal)
    return mask
}

fun Pawn.workBlocked(w: WorkType): Boolean = (incapable or ageIncapable()) and (1 shl w.ordinal) != 0

private val babyFoods = setOf(ItemType.MILK, ItemType.MEAL_SIMPLE, ItemType.MEAL_FINE, ItemType.PEMMICAN, ItemType.EGGS)

/** Once a day: birthdays, growth, pregnancy and animal breeding. */
fun Game.lifeDaily() {
    val d = day
    for (p in pawns.toList()) {
        if (!p.alive) continue
        if (p.isAnimal) { animalLifeDaily(p); continue }
        if (p.faction != Faction.PLAYER && !p.prisoner) continue
        if (d % DAYS_PER_YEAR == p.birthday % DAYS_PER_YEAR && d > 0) birthdayOf(p)
        if (!p.alive) continue
        if (p.pregnantUntil > 0L) pregnancyDaily(p)
    }
    conceptionRolls()
}

private fun Game.birthdayOf(p: Pawn) {
    p.age++
    val first = p.name.substringBefore(' ')
    when (p.age) {
        3 -> if (p.colonist) say("$first is no longer a baby.", 1)
        7, 10, 13 -> growthMoment(p)
        18 -> { if (p.colonist) say("$first has come of age.", 1) }
    }
    if (p.age >= 40 && !p.race.mech) agingConditions(p)
    if (p.age >= 80 && rng.chance(min(0.7f, (p.age - 79) * 0.09f))) die(p, "old age")
}

private fun Game.growthMoment(p: Pawn) {
    val first = p.name.substringBefore(' ')
    val s = rng.int(SkillType.entries.size)
    if (p.passion[s] < 2) { p.passion[s]++; if (p.colonist) say("$first discovered a passion for ${SkillType.entries[s].label.lowercase()}.", 1) }
    p.skill[s] = min(20, p.skill[s] + 2)
    if (p.age == 13) {
        val t = if (rng.chance(0.16f)) rng.pick(listOf(Trait.GAY, Trait.BISEXUAL, Trait.ASEXUAL)) else Trait.entries[rng.int(Trait.entries.size)]
        if (p.traits.none { it == t || conflicts(it, t) } && p.traits.size < 4) { p.traits.add(t); if (p.colonist) say("$first became ${t.label.lowercase()}.", 1) }
        for (w in WorkType.entries) {
            val sk = w.skill()
            p.priority[w.ordinal] = if (sk == null) (if (w == WorkType.FIREFIGHT || w == WorkType.PATIENT) 1 else 3) else if (p.passion[sk.ordinal] > 0) 2 else 3
        }
    }
}

private fun Game.agingConditions(p: Pawn) {
    fun roll(kind: HediffKind, base: Float, from: Int) {
        if (p.age < from || p.hediffs.any { it.kind == kind }) return
        if (rng.chance(min(0.55f, base + (p.age - from) * 0.02f))) {
            val h = addHediff(p, kind, 0.5f + rng.float() * 0.5f)
            h.tended = true
            p.healthDirty = true
            if (p.colonist) say("${p.name} developed ${kind.label.lowercase()}.", 2)
        }
    }
    roll(HediffKind.BAD_BACK, 0.12f, 40)
    roll(HediffKind.ARTHRITIS, 0.10f, 45)
    roll(HediffKind.CATARACT, 0.10f, 50)
    roll(HediffKind.HEARING_LOSS, 0.08f, 50)
    roll(HediffKind.DEMENTIA, 0.03f, 62)
}

// ---------------------------------------------------------------------- pregnancy

private fun Game.conceptionRolls() {
    for (m in colonists) {
        if (!m.female || m.age < 16 || m.age > 45 || m.pregnantUntil > 0L || m.downed) continue
        val partnerId = if (m.spouse >= 0) m.spouse else m.lover
        if (partnerId < 0) continue
        val dad = pawnById(partnerId) ?: continue
        if (!dad.alive || dad.female || !dad.colonist || dad.age < 16 || dad.downed) continue
        if (m.food < 0.2f || m.healthFraction() < 0.6f) continue
        if (rng.chance(0.06f)) {
            m.pregnantUntil = tick + GESTATION_DAYS.toLong() * TICKS_PER_DAY
            m.pregnantBy = dad.id
            m.healthDirty = true
            say("${m.name} is pregnant!", 1)
        }
    }
}

private fun Game.pregnancyDaily(m: Pawn) {
    if (m.healthFraction() < 0.35f || m.food <= 0.02f) {
        if (rng.chance(0.12f)) {
            m.pregnantUntil = 0L; m.pregnantBy = -1; m.healthDirty = true
            m.addThought("Lost the baby", -0.35f, tick, 10 * TICKS_PER_DAY)
            say("${m.name} lost her baby.", 3)
            return
        }
    }
    if (tick >= m.pregnantUntil) giveBirth(m)
}

fun Game.giveBirth(m: Pawn) {
    m.pregnantUntil = 0L
    m.healthDirty = true
    val dad = pawnById(m.pregnantBy)
    m.pregnantBy = -1
    val b = Pawn(nextPawnId++, "", Race.HUMAN, m.faction)
    b.female = rng.chance(0.5f)
    val first = rng.pick(Names.first.filter { (it in Names.female) == b.female })
    b.name = first + " " + (dad?.name ?: m.name).substringAfter(' ')
    b.age = 0
    b.birthday = day % DAYS_PER_YEAR
    b.mother = m.id; b.father = dad?.id ?: -1
    b.x = m.x; b.y = m.y; b.fromX = m.x; b.fromY = m.y
    b.backstory = "Colony child"
    b.food = 0.7f; b.rest = 1f; b.joy = 0.6f
    for (s in SkillType.entries) b.skill[s.ordinal] = 0
    for (w in WorkType.entries) b.priority[w.ordinal] = 0
    // Inherit a lean towards the parents' interests.
    for (parent in listOfNotNull(m, dad)) {
        val s = rng.int(SkillType.entries.size)
        if (parent.passion[s] > 0 && rng.chance(0.4f)) b.passion[s] = 1
    }
    b.opinion[m.id] = 90; m.opinion[b.id] = 90
    if (dad != null) { b.opinion[dad.id] = 90; dad.opinion[b.id] = 90 }
    pawns.add(b)
    recomputeHealth(b)
    m.addThought("Gave birth", 0.3f, tick, 8 * TICKS_PER_DAY)
    m.rest = min(m.rest, 0.4f)
    dad?.addThought("New baby", 0.18f, tick, 8 * TICKS_PER_DAY)
    for (o in colonists) if (o !== m && o !== dad) o.addThought("New baby in the colony", 0.06f, tick, 4 * TICKS_PER_DAY)
    // A hard labour can hurt if nobody has medicine ready.
    if (medicineCount() == 0 && rng.chance(0.08f)) { woundPart(m, 0, DamageKind.BRUISE, 8f); say("${m.name}'s labour was hard.", 2) }
    say("${m.name} gave birth to ${b.name}.", 1)
}

private fun Game.medicineCount() = map.countItems { it.cat == ItemCat.MEDICINE }

// ---------------------------------------------------------------------- babies

/** Babies lie where they are and need an adult to feed them. */
internal fun Game.babyTick(p: Pawn) {
    p.job = null
    p.clearPath()
    p.moveCd = 0
}

/** An idle adult finds a hungry baby and some milk or soft food. */
internal fun Game.findFeedBaby(p: Pawn): Job? {
    if (p.age < 13 || p.isAnimal) return null
    val baby = pawns.firstOrNull { it.alive && it.isBaby && it.faction == Faction.PLAYER && it.food < 0.55f && isFree(p, pawnKey(it.id, K_PATIENT)) } ?: return null
    val food = nearestItem(p, shared = true) { it.type in babyFoods && it.rot < 0.5f && it.corpseOf == null } ?: return null
    val j = Job(JobType.FEED_BABY, food.x, food.y)
    j.targetPawn = baby.id
    j.key = pawnKey(baby.id, K_PATIENT)
    j.dx = food.x; j.dy = food.y
    j.item = food.type
    reserve(p, j.key)
    return j
}

internal fun Game.driveFeedBaby(p: Pawn, j: Job) {
    val b = pawnById(j.targetPawn)
    if (b == null || !b.alive || b.food > 0.9f) { endJob(p); return }
    when (j.stage) {
        0 -> {
            val ii = map.idx(j.dx, j.dy)
            val s = map.items[ii]
            if (s == null || s.type !in babyFoods) { endJob(p); return }
            val r = goTo(p, j.dx, j.dy)
            if (r == -1) endJob(p) else if (r == 0) { p.carryType = s.type; p.carryQuality = s.quality; p.carryCount = map.take(ii, 1); j.stage = 1 }
        }
        1 -> {
            val r = goTo(p, b.x, b.y, adjacent = true)
            if (r == -1) endJob(p) else if (r == 0) {
                val t = p.carryType
                if (t != null && p.carryCount > 0) {
                    b.food = min(1f, b.food + t.nutrition * 1.2f + (if (t == ItemType.MILK) 0.35f else 0f))
                    p.carryCount = 0; p.carryType = null
                    p.gainXp(SkillType.ANIMALS, 20f)
                }
                endJob(p)
            }
        }
    }
}

// ---------------------------------------------------------------------- animals

private fun Game.animalLifeDaily(p: Pawn) {
    p.ageDays++
    if (p.faction != Faction.PLAYER) return
    if (p.female && p.stage == LifeStage.ADULT && p.pregnantUntil == 0L && p.food > 0.4f) {
        val sire = pawns.any { it.alive && it.race == p.race && !it.female && it.faction == Faction.PLAYER && it.stage == LifeStage.ADULT }
        val crowd = pawns.count { it.alive && it.faction == Faction.PLAYER && it.isAnimal }
        if (sire && crowd < 40 && rng.chance(0.07f)) {
            p.pregnantUntil = tick + p.race.gestationDays.toLong() * TICKS_PER_DAY
            p.healthDirty = true
        }
    } else if (p.pregnantUntil > 0L && tick >= p.pregnantUntil) {
        p.pregnantUntil = 0L
        val n = rng.range(1, p.race.litter)
        repeat(n) {
            val c = newAnimal(p.race, p.x, p.y, Faction.PLAYER)
            c.ageDays = 0; c.age = 0; c.tame = true; c.master = p.master; c.mother = p.id
            recomputeHealth(c)
        }
        if (map.inB(p.x, p.y)) say("${p.name} the ${p.race.label.lowercase()} had ${if (n == 1) "a baby" else "$n babies"}.", 1)
    }
}
