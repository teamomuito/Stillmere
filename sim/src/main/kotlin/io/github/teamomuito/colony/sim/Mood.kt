package io.github.teamomuito.colony.sim

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object Break {
    const val WANDER = 0; const val BERSERK = 1; const val BINGE_FOOD = 2; const val INSULT = 3; const val TANTRUM = 4
    const val FIRE = 5; const val CATATONIC = 6; const val RUN_WILD = 7; const val BINGE_DRUGS = 8; const val HIDE = 9
    fun label(k: Int) = when (k) {
        WANDER -> "sad wander"; BERSERK -> "berserk"; BINGE_FOOD -> "food binge"; INSULT -> "insulting spree"
        TANTRUM -> "tantrum"; FIRE -> "fire starting"; CATATONIC -> "catatonic"; RUN_WILD -> "running wild"; BINGE_DRUGS -> "drug binge"
        HIDE -> "hiding"
        else -> "break"
    }
}

fun Game.comfortTick(p: Pawn) {
    val i = map.idx(p.x, p.y)
    val out = outdoorTemp()
    p.temp = map.tempAt(i, out)
    if (p.race.mech) return
    p.dark = !p.isAnimal && map.light[i] < 0.25f && !(p.job?.type == JobType.SLEEP)
    if (p.dead) return
    if (!p.isAnimal) placeNeedsTick(p)
    val minT = p.comfyMin()
    val maxT = p.comfyMax()
    val cold = minT - p.temp
    val hot = p.temp - maxT
    if (!p.isAnimal || p.faction == Faction.PLAYER || true) {
        val hypo = p.hediff(HediffKind.COLD_CORE)
        if (cold > 0f && !(p.race.isAnimal && p.race.size > 1.4f && cold < 10f)) {
            val gain = ((cold - 3f) / 40f * 0.02f).coerceIn(0f, 0.05f) * (if (p.race.isAnimal) 0.5f else 1f)
            if (gain > 0f) {
                val h = hypo ?: addHediff(p, HediffKind.COLD_CORE, 0f)
                h.severity = min(1f, h.severity + gain)
                p.healthDirty = true
                if (h.severity > 0.45f && p.temp < -2f && rng.chance(0.04f)) {
                    val tips = listOf(PartTag.HAND, PartTag.FOOT, PartTag.EAR, PartTag.NOSE)
                    val tag = rng.pick(tips)
                    val cand = partsWithTag(p, tag)
                    if (cand.isNotEmpty()) woundPart(p, rng.pick(cand), DamageKind.FROSTBITE, 4f + rng.float() * 4f)
                }
                if (h.severity >= 1f) die(p, "exposure")
            }
        } else if (hypo != null) {
            hypo.severity -= 0.025f
            p.healthDirty = true
            if (hypo.severity <= 0f) removeHediff(p, HediffKind.COLD_CORE)
        }
        val heat = p.hediff(HediffKind.SUNSTRUCK)
        if (hot > 0f) {
            val gain = ((hot - 4f) / 40f * 0.02f).coerceIn(0f, 0.05f)
            if (gain > 0f) {
                val h = heat ?: addHediff(p, HediffKind.SUNSTRUCK, 0f)
                h.severity = min(1f, h.severity + gain)
                p.healthDirty = true
                if (h.severity >= 1f) die(p, "overheating")
            }
        } else if (heat != null) {
            heat.severity -= 0.03f
            p.healthDirty = true
            if (heat.severity <= 0f) removeHediff(p, HediffKind.SUNSTRUCK)
        }
    }
    // Starved frame recovers at the malnutrition rate whenever the pawn is not starving.
    val mal = p.hediff(HediffKind.STARVED_FRAME)
    if (mal != null && p.food > 0f) {
        mal.severity -= NeedRules.MALNUTRITION_PER_DAY * Game.SLOW_TICK.toFloat() / TICKS_PER_DAY
        p.healthDirty = true
        if (mal.severity <= 0f) removeHediff(p, HediffKind.STARVED_FRAME)
    }
    // Toxin load clears slowly; failing filtration makes it worse.
    val tox = p.hediff(HediffKind.TOXIN_LOAD)
    if (p.cap[Cap.FILTRATION.ordinal] < 0.3f && !p.isAnimal) {
        val h = tox ?: addHediff(p, HediffKind.TOXIN_LOAD, 0f)
        h.severity = min(1f, h.severity + 0.01f)
        if (h.severity >= 1f) die(p, "organ failure")
    } else if (tox != null) {
        tox.severity -= 0.0012f
        if (tox.severity <= 0f) removeHediff(p, HediffKind.TOXIN_LOAD)
    }
    // Addiction severity is the chemical need: it falls each day. Each addiction in withdrawal adds a craving. The cravings
    // are summed over every addiction first and the keep-or-remove rule is applied once, so hediff order does not matter.
    val addictFall = DrugRules.ADDICTION_FALL_PER_DAY * Game.SLOW_TICK.toFloat() / TICKS_PER_DAY
    var craving = 0f
    for (h in p.hediffs.toList()) {
        if (h.kind !in DrugRules.ADDICTIONS) continue
        h.severity = max(0f, h.severity - addictFall)
        if (h.severity <= DrugRules.WITHDRAWAL_LEVEL) craving += DrugRules.CRAVING_PER_ADDICTION
    }
    if (craving > 0f) {
        val level = min(1f, craving)
        val ache = p.hediff(HediffKind.CRAVING_ACHE)
        if (ache == null) addHediff(p, HediffKind.CRAVING_ACHE, level)
        else if (ache.severity != level) { ache.severity = level; p.healthDirty = true }
    } else removeHediff(p, HediffKind.CRAVING_ACHE)
    // Drug tolerance wears off slowly.
    if (p.drugTolerance.isNotEmpty()) {
        val fall = DrugRules.TOLERANCE_FALL_PER_DAY * Game.SLOW_TICK.toFloat() / TICKS_PER_DAY
        val it = p.drugTolerance.entries.iterator()
        while (it.hasNext()) { val e = it.next(); val v = e.value - fall; if (v <= 0f) it.remove() else e.setValue(v) }
    }
    restCollapseTick(p)
    // Disease: occasionally someone gets sick from dirt and cold.
    if (p.colonist && tick % tk(5000L) == 0L) maybeSick(p)
}

private fun Game.maybeSick(p: Pawn) {
    var risk = 0.0007f * difficulty.threat.coerceAtLeast(0.3f)
    if (p.hediff(HediffKind.COLD_CORE) != null) risk *= 3f
    val i = map.idx(p.x, p.y)
    risk *= 1f + map.filth[i] * 0.3f
    if (p.hediffs.any { it.kind.category == 0 }) return
    if (rng.float() > risk * 10f) return
    val kind = rng.pick(listOf(HediffKind.CHEST_COUGH, HediffKind.CHEST_COUGH, HediffKind.GUT_FLUKES, HediffKind.TISSUE_LARVAE, HediffKind.MARSH_FEVER, HediffKind.BOIL_SICKNESS, HediffKind.HEAVY_DROWSE).let { l ->
        if (day < 8) l.filter { it == HediffKind.CHEST_COUGH || it == HediffKind.GUT_FLUKES } else l
    })
    addHediff(p, kind, 0.12f)
    say("${p.name} caught ${kind.label.lowercase()}.", 2)
}

fun Game.moodUpdate(p: Pawn) {
    p.situ.clear()
    fun add(label: String, v: Float) { p.situ.add(Thought(label, v, Long.MAX_VALUE)) }
    val i = map.idx(p.x, p.y)
    // Needs.
    when {
        p.food <= 0.03f -> add("Starving", -0.3f)
        p.food < 0.15f -> add("Very hungry", -0.18f)
        p.food < 0.3f -> add("Hungry", -0.08f)
    }
    when {
        p.rest < 0.1f -> add("Exhausted", -0.2f)
        p.rest < 0.3f -> add("Tired", -0.08f)
    }
    when {
        p.joy < 0.1f -> add("Bored", -0.12f)
        p.joy < 0.3f -> add("Restless", -0.04f)
        p.joy > 0.85f -> add("Entertained", 0.04f)
    }
    when {
        p.pain > 0.5f -> add("Intense pain", -0.28f)
        p.pain > 0.25f -> add("Serious pain", -0.15f)
        p.pain > 0.05f -> add("Pain", -0.05f)
    }
    val cold = p.comfyMin() - p.temp
    val hot = p.temp - p.comfyMax()
    if (cold > 0f) when (NeedRules.thermalStage(cold)) {
        0 -> add("Cold", -0.02f)
        1 -> add("Very cold", -0.14f)
        else -> add("Freezing", -0.25f)
    } else if (hot > 0f) when (NeedRules.thermalStage(hot)) {
        0 -> add("Hot", -0.02f)
        1 -> add("Very hot", -0.14f)
        else -> add("Scorching", -0.25f)
    }
    if (p.hediffs.any { it.kind.category == 0 && it.severity > 0.1f }) add("Sick", -0.06f)
    val lost = p.injuries.count { it.missing && !p.implants.containsKey(it.part) && !p.race.body[it.part].inner }
    if (lost > 0) add("Missing body parts", -min(0.2f, 0.05f * lost))
    if (p.implants.isNotEmpty() && Trait.MACHINE_MINDED in p.traits) add("Refit pride", 0.1f)
    // Clothing.
    if (p.apparel.isEmpty() && Trait.BARE_SKINNED !in p.traits) add("Naked", -0.1f)
    else if (p.apparel.isNotEmpty() && Trait.BARE_SKINNED in p.traits) add("Dressed against my beliefs", -0.1f)
    else if (p.apparel.isNotEmpty() && p.apparel.any { it.hp < (it.type.apparel?.hp ?: 100f) * 0.3f }) add("Tattered clothing", -0.04f)
    else if (p.apparel.any { it.quality.ordinal >= Quality.EXCELLENT.ordinal }) add("Fine clothing", 0.04f)
    // Environment.
    // Outdoors and indoors needs: time spent shut in, or outside for too long. Values are Stillmere's own (UNVERIFIED).
    val outNeed = p.outdoorsNeed
    when {
        outNeed <= 0.05f -> add("Sealed in", -0.15f)
        outNeed <= 0.2f -> add("Trapped inside", -0.10f)
        outNeed <= 0.4f -> add("Cooped up", -0.05f)
    }
    if (p.indoorsNeed <= 0.2f) add("Worn by the weather", -0.04f)
    if (p.dark && p.job?.type != JobType.SLEEP) add("In the dark", -0.03f)
    if ((weather == Weather.RAIN || weather == Weather.THUNDER) && !map.roofed(i)) add("Soaking wet", -0.04f)
    val r = map.roomId[i]
    if (r >= 0 && map.roomIndoor[r] && !map.roomDirty) {
        val beauty = NeedRules.beautyLevel(map.roomBeauty[r])
        if (beauty <= NeedRules.UGLY_BEAUTY) add("Drab rooms", -0.04f)
        else if (beauty > NeedRules.PRETTY_BEAUTY) add("Pretty surroundings", 0.06f)
        if (map.roomClean[r] < -0.9f) add("Filthy room", -0.07f)
        else if (map.roomClean[r] < -0.4f) add("Dirty room", -0.03f)
        if (map.roomSize[r] < 7 && map.roomRole[r] != 1) add("Cramped", -0.04f)
    } else if (!map.roofed(i)) {
        if (Trait.GARDEN_SENSE in p.traits && map.plant[i] != null) add("Among the plants", 0.04f)
    }
    // Drugs.
    for (h in p.hediffs) when (h.kind) {
        HediffKind.BREW_HAZE -> add("Brew haze", 0.06f)
        HediffKind.HUSHMOSS_HIGH -> add("Hushed calm", 0.08f)
        HediffKind.LUCID_HIGH -> add("Lucid high", 0.12f)
        HediffKind.CRAVING_ACHE -> add("Craving ache", -0.14f)
        else -> {}
    }
    // Traits.
    if (Trait.BUOYANT in p.traits) add("Cheerful", 0.12f)
    if (Trait.HEAVY_HEARTED in p.traits) add("Gloomy", -0.12f)
    if (Trait.SUNNY_OUTLOOK in p.traits) add("Sunny outlook", 0.1f)
    if (Trait.PLAIN_LIVING in p.traits) add("Content with little", 0.07f)
    if (Trait.ACQUISITIVE in p.traits) { val wl = map.wealth(); if (wl > 9000f) add("Rich colony", 0.1f) else if (wl < 3000f) add("Poor colony", -0.07f) }
    if (Trait.GREY_OUTLOOK in p.traits) add("Grey outlook", -0.1f)
    if (Trait.STRIKING_LOOKS in p.traits) add("Pleasant looks", 0.03f)
    if (Trait.PLAIN_LOOKS in p.traits) add("Looks bad", -0.03f)
    if (Trait.BROODING_MUSE in p.traits) add("Restless mind", -0.05f)
    // Company matters: a colonist who has not talked to anyone for two days feels it, gregarious ones more.
    if (p.colonist && tick > 2L * TICKS_PER_DAY && tick - p.lastSocial > 2L * TICKS_PER_DAY) {
        add("Lonely", if (Trait.EASY_COMPANY in p.traits) -0.1f else -0.05f)
    }
    // Corpses lying around.
    var seen = 0f
    for (s in map.items.values) {
        if (s.corpseOf == null) continue
        if (abs(s.x - p.x) + abs(s.y - p.y) > 9) continue
        if (Trait.UNMOVED in p.traits || Trait.BATTLE_HUNGRY in p.traits) { add("Enjoying the carnage", 0.04f); break }
        seen += if (s.rot > 0.4f) 0.06f else 0.03f
        if (s.corpseColonist) seen += 0.02f
    }
    if (seen > 0f) add("Saw a corpse", -min(0.14f, seen))
    // Rivals nearby grate on people; family nearby reassures.
    var rivals = 0; var kin = 0
    for (o in pawns) {
        if (o === p || !o.alive || o.isAnimal || o.faction != Faction.PLAYER) continue
        if (abs(o.x - p.x) + abs(o.y - p.y) > 12) continue
        val op = p.opinion[o.id] ?: 0
        if (op < -40) rivals++
        if (o.id == p.mother || o.id == p.father || o.mother == p.id || o.father == p.id) kin++
    }
    if (rivals > 0) add("Rival nearby", -min(0.12f, 0.05f * rivals))
    if (kin > 0 && p.age >= 3) add("Family close by", min(0.06f, 0.03f * kin))
    // Relationships.
    if (p.spouse >= 0) {
        val sp = pawnById(p.spouse)
        if (sp != null && sp.alive) {
            add("Married", 0.06f)
            if (distance(p.x, p.y, sp.x, sp.y) > 30f) add("Apart from spouse", -0.05f)
        }
    } else if (p.lover >= 0) {
        val lv = pawnById(p.lover)
        if (lv != null && lv.alive) add("In a relationship", 0.04f)
    }
    // Prisoners are not happy to be prisoners.
    if (p.prisoner) add("Imprisoned", -0.08f)
    // Combine.
    var m = 0.55f
    p.thoughts.removeAll { it.expires <= tick }
    for (t in p.thoughts) m += t.mood
    for (t in p.situ) m += t.mood
    m = m.coerceIn(0f, 1f)
    p.mood = (p.mood * 0.5f + m * 0.5f).coerceIn(0f, 1f)
    if (p.prisoner || p.faction == Faction.VISITOR || p.age < 13) return
    breaksTick(p)
}

private fun Game.breaksTick(p: Pawn) {
    val step = Game.SLOW_TICK.toInt()
    // Time below each threshold, and the awake time left before the next break.
    p.moodBelowExtremeTicks = if (p.mood < BreakRules.EXTREME) p.moodBelowExtremeTicks + step else 0
    p.moodBelowMajorTicks = if (p.mood < BreakRules.MAJOR) p.moodBelowMajorTicks + step else 0
    p.moodBelowMinorTicks = if (p.mood < BreakRules.MINOR) p.moodBelowMinorTicks + step else 0
    if (p.breakCooldown > 0 && p.job?.type != JobType.SLEEP) p.breakCooldown = max(0, p.breakCooldown - step)
    if (p.breakUntil > 0 && tick >= p.breakUntil) {
        p.breakUntil = 0; p.breakKind = 0; p.hostileFlag = false
        if (p.downed && p.job == null) { /* catatonic recovery */ }
        endJob(p)
        p.addThought("Catharsis", 0.22f, tick, (2.5f * TICKS_PER_DAY).toInt())
        p.breakCooldown = BreakRules.COOLDOWN_TICKS
        say("${p.name} has recovered from the mental break.", 0)
        return
    }
    if (p.breakUntil > 0 || p.downed || p.drafted && p.job?.type == JobType.ATTACK) return
    if (!mentalBreaksEnabled || p.breakCooldown > 0) return
    // The most severe level that has been below its threshold long enough is the only one that can break.
    val sev = when {
        p.moodBelowExtremeTicks > BreakRules.MIN_TICKS_BELOW -> 2
        p.moodBelowMajorTicks > BreakRules.MIN_TICKS_BELOW -> 1
        p.moodBelowMinorTicks > BreakRules.MIN_TICKS_BELOW -> 0
        else -> return
    }
    val chance = BreakRules.chance(sev, step) * (if (Trait.UNMOVED in p.traits) 0.8f else 1f) * (if (Trait.STEEL_NERVES in p.traits) 0.35f else 1f) * (if (Trait.SHORT_FUSE in p.traits) 1.8f else 1f)
    if (chance <= 0f || rng.float() > chance) return
    val options = ArrayList<Int>()
    options += Break.WANDER; options += Break.BINGE_FOOD; options += Break.INSULT; options += Break.HIDE
    if (sev >= 1) { options += Break.TANTRUM; options += Break.BERSERK; if (Trait.FLAME_DRAWN in p.traits) { options += Break.FIRE; options += Break.FIRE } }
    if (sev >= 2) { options += Break.CATATONIC; options += Break.RUN_WILD; options += Break.BERSERK }
    if (map.countItems(ItemType.BEER) + map.countItems(ItemType.JOINT) > 0) options += Break.BINGE_DRUGS
    startBreak(p, rng.pick(options))
}

/** Jobs in which a pawn sits or lies on the seat under it, so that seat's comfort applies. */
private val SEAT_JOBS = setOf(JobType.SLEEP, JobType.REST, JobType.JOY, JobType.SOCIAL, JobType.EAT, JobType.WAIT)

/** The comfort of the seat under the pawn while it sits or lies on one, and zero otherwise. */
private fun Game.seatComfort(p: Pawn): Float {
    val t = p.job?.type
    if (t != null && t !in SEAT_JOBS) return 0f
    val b = map.building[map.idx(p.x, p.y)] ?: return 0f
    return if (b.built) b.def.comfort else 0f
}

/** The outdoors and indoors needs move with the cell the pawn stands on. Comfort is read from its seat. */
private fun Game.placeNeedsTick(p: Pawn) {
    val i = map.idx(p.x, p.y)
    val outdoor = !map.roomIndoorAt(i)
    val roofed = map.roofed(i)
    val inBed = isInBed(p)
    val k = Game.SLOW_TICK.toFloat() / NeedRules.INTERVAL_TICKS
    p.outdoorsNeed = NeedRules.stepNeed(p.outdoorsNeed, NeedRules.outdoorsDelta(outdoor, roofed, inBed) * k, NeedRules.outdoorsFloor(outdoor, roofed))
    p.indoorsNeed = NeedRules.stepNeed(p.indoorsNeed, NeedRules.indoorsDelta(outdoor, roofed) * k)
    p.comfortNeed = seatComfort(p).coerceIn(0f, 1f)
}

/**
 * A colonist or prisoner that has been at zero rest long enough may fall asleep where it stands. Hostile pawns keep their raid
 * AI, and a drafted colonist is stood down (see [pawnTick]) before it can collapse.
 */
internal fun Game.restCollapseTick(p: Pawn) {
    if (p.isAnimal || p.race.mech || p.isBaby || p.dead || !(p.colonist || p.prisoner)) return
    p.ticksAtZeroRest = if (p.rest < NeedRules.ZERO_REST) p.ticksAtZeroRest + Game.SLOW_TICK.toInt() else 0
    if (p.ticksAtZeroRest <= NeedRules.COLLAPSE_AFTER_TICKS || p.downed || p.drafted || p.job?.type == JobType.SLEEP) return
    val chance = Game.SLOW_TICK.toFloat() / (NeedRules.collapseMtbDays(p.ticksAtZeroRest) * TICKS_PER_DAY)
    if (!rng.chance(chance)) return
    startSleepInPlace(p)
    say("${p.name} collapsed from exhaustion.", 2)
}

fun Game.startBreak(p: Pawn, kind: Int) {
    p.breakKind = kind
    p.breakUntil = tick + rng.range(tk(2000), tk(5000))
    p.drafted = false
    endJob(p)
    when (kind) {
        Break.BERSERK -> { p.hostileFlag = true; say("${p.name} has gone berserk!", 3) }
        Break.CATATONIC -> { p.breakUntil = tick + rng.range(tk(5000), tk(9000)); downPawn(p); say("${p.name} has gone catatonic.", 3) }
        Break.RUN_WILD -> say("${p.name} is running into the wilds!", 3)
        Break.TANTRUM -> say("${p.name} is throwing a tantrum!", 3)
        Break.FIRE -> say("${p.name} is setting fires!", 3)
        else -> say("${p.name} is having a mental break: ${Break.label(kind)}.", 2)
    }
    for (o in colonists) if (o !== p && Trait.UNMOVED !in o.traits) o.addThought("Witnessed a mental break", -0.03f, tick, TICKS_PER_DAY)
}

fun Game.onPawnDied(p: Pawn, cause: String, source: Pawn?) {
    if (!encounter && (p.race == Race.PUFFTAIL || p.race == Race.SWELLGRAZER) && map.inB(p.x, p.y)) {
        explode(p.x, p.y, if (p.race == Race.PUFFTAIL) 1.8f else 2.8f, if (p.race == Race.PUFFTAIL) 22f else 38f, null, true)
        say("A ${p.race.label.lowercase()} exploded!", 2)
    }
    // On a battle map, the colony's losses are recorded in the world the battle came from.
    if (encounter) parent?.recordBattleDeath(p, cause)
    if (caravanDeath(p, cause)) return
    if (p.race.mech) {
        map.drop(ItemType.STEEL, rng.range(20, 60), p.x, p.y)
        map.drop(ItemType.COMPONENT, rng.range(1, 3), p.x, p.y)
        if (rng.chance(0.15f)) map.drop(ItemType.HARDALLOY, rng.range(5, 12), p.x, p.y)
        say("A ${p.race.label.lowercase()} was destroyed.", 1)
        statsKilled++
        return
    }
    // Corpse.
    val i = map.idx(p.x, p.y)
    var cx = p.x; var cy = p.y
    if (!map.dropCell(i)) {
        loop@ for (r in 1..4) for (dy in -r..r) for (dx in -r..r) {
            val nx = p.x + dx; val ny = p.y + dy
            if (map.inB(nx, ny) && map.dropCell(map.idx(nx, ny)) && map.items[map.idx(nx, ny)] == null) { cx = nx; cy = ny; break@loop }
        }
    }
    val ci = map.idx(cx, cy)
    if (map.items[ci] == null) {
        val s = ItemStack(map.nextId(), ItemType.CORPSE_HUMAN, 1, cx, cy)
        s.corpseOf = p.name; s.corpseRace = p.race; s.corpseColonist = p.faction == Faction.PLAYER
        s.corpseAge = p.age
        map.items[ci] = s
    }
    // Gear drops.
    p.weaponItem?.let { if (p.faction != Faction.WILD) map.drop(it, 1, cx, cy, p.weaponQuality) }
    for (w in p.apparel) if (w.hp > 20f) map.drop(w.lot(), 1, cx, cy)
    p.apparel.clear()
    for (b in map.buildings()) if (b != null && b.ownerId == p.id) b.ownerId = -1
    when {
        p.faction == Faction.PLAYER && !p.isAnimal && !p.prisoner -> {
            say("${p.name} has died ($cause).", 3)
            for (par in listOf(p.mother, p.father)) pawnById(par)?.let { if (it.alive) it.addThought("Child died: ${p.name.substringBefore(' ')}", -0.4f, tick, 12 * TICKS_PER_DAY) }
            graveyard.add("${p.name} - $cause (day ${day + 1})")
            if (p.age > 0) for (o in colonists) {
                if (Trait.UNMOVED in o.traits) continue
                val closeness = if (o.spouse == p.id || o.lover == p.id) 0.3f else 0.1f
                o.addThought("Colonist died: ${p.name.substringBefore(' ')}", -closeness, tick, 4 * TICKS_PER_DAY)
            }
            if (p.spouse >= 0) pawnById(p.spouse)?.spouse = -1
            if (p.lover >= 0) pawnById(p.lover)?.lover = -1
        }
        p.faction == Faction.PLAYER && p.isAnimal -> {
            say("Your ${p.race.label.lowercase()} ${p.name} has died.", 2)
            for (o in colonists) if (o.master == p.id || p.master == o.id) o.addThought("Pet died", -0.12f, tick, 3 * TICKS_PER_DAY)
        }
        p.prisoner -> { say("Prisoner ${p.name} died.", 2); for (o in colonists) o.addThought("Prisoner died", -0.04f, tick, 2 * TICKS_PER_DAY) }
        p.faction == Faction.ENEMY -> { say("${p.name} was killed.", 1); statsKilled++ }
        else -> { if (!p.isAnimal) say("${p.name} died.", 0) }
    }
    if (source != null && source.isAnimal && source.race.predator && p.isAnimal && source.alive) source.food = min(1f, source.food + 0.65f)
    if (source != null && source.colonist && p.faction == Faction.ENEMY && Trait.BATTLE_HUNGRY in source.traits) source.addThought("Killed someone", 0.1f, tick, TICKS_PER_DAY)
}
