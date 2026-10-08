package io.github.teamomuito.colony.sim

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object Break {
    const val WANDER = 0; const val BERSERK = 1; const val BINGE_FOOD = 2; const val INSULT = 3; const val TANTRUM = 4
    const val FIRE = 5; const val CATATONIC = 6; const val RUN_WILD = 7; const val BINGE_DRUGS = 8
    fun label(k: Int) = when (k) {
        WANDER -> "sad wander"; BERSERK -> "berserk"; BINGE_FOOD -> "food binge"; INSULT -> "insulting spree"
        TANTRUM -> "tantrum"; FIRE -> "fire starting"; CATATONIC -> "catatonic"; RUN_WILD -> "running wild"; BINGE_DRUGS -> "drug binge"
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
    val minT = p.comfyMin()
    val maxT = p.comfyMax()
    val cold = minT - p.temp
    val hot = p.temp - maxT
    if (!p.isAnimal || p.faction == Faction.PLAYER || true) {
        val hypo = p.hediff(HediffKind.HYPOTHERMIA)
        if (cold > 0f && !(p.race.isAnimal && p.race.size > 1.4f && cold < 10f)) {
            val gain = ((cold - 3f) / 40f * 0.02f).coerceIn(0f, 0.05f) * (if (p.race.isAnimal) 0.5f else 1f)
            if (gain > 0f) {
                val h = hypo ?: addHediff(p, HediffKind.HYPOTHERMIA, 0f)
                h.severity = min(1f, h.severity + gain)
                p.healthDirty = true
                if (h.severity > 0.45f && p.temp < -2f && rng.chance(0.04f)) {
                    val tips = listOf(PartTag.HAND, PartTag.FOOT, PartTag.EAR, PartTag.NOSE)
                    val tag = rng.pick(tips)
                    val cand = partsWithTag(p, tag)
                    if (cand.isNotEmpty()) woundPart(p, rng.pick(cand), DamageKind.FROSTBITE, 4f + rng.float() * 4f)
                }
                if (h.severity >= 1f) die(p, "hypothermia")
            }
        } else if (hypo != null) {
            hypo.severity -= 0.025f
            p.healthDirty = true
            if (hypo.severity <= 0f) removeHediff(p, HediffKind.HYPOTHERMIA)
        }
        val heat = p.hediff(HediffKind.HEATSTROKE)
        if (hot > 0f) {
            val gain = ((hot - 4f) / 40f * 0.02f).coerceIn(0f, 0.05f)
            if (gain > 0f) {
                val h = heat ?: addHediff(p, HediffKind.HEATSTROKE, 0f)
                h.severity = min(1f, h.severity + gain)
                p.healthDirty = true
                if (h.severity >= 1f) die(p, "heatstroke")
            }
        } else if (heat != null) {
            heat.severity -= 0.03f
            p.healthDirty = true
            if (heat.severity <= 0f) removeHediff(p, HediffKind.HEATSTROKE)
        }
    }
    // Malnutrition recovers once fed.
    val mal = p.hediff(HediffKind.MALNUTRITION)
    if (mal != null && p.food > 0.15f) {
        mal.severity -= 0.03f
        p.healthDirty = true
        if (mal.severity <= 0f) removeHediff(p, HediffKind.MALNUTRITION)
    }
    // Toxic buildup clears slowly; failing filtration makes it worse.
    val tox = p.hediff(HediffKind.TOXIC_BUILDUP)
    if (p.cap[Cap.FILTRATION.ordinal] < 0.3f && !p.isAnimal) {
        val h = tox ?: addHediff(p, HediffKind.TOXIC_BUILDUP, 0f)
        h.severity = min(1f, h.severity + 0.01f)
        if (h.severity >= 1f) die(p, "organ failure")
    } else if (tox != null) {
        tox.severity -= 0.0012f
        if (tox.severity <= 0f) removeHediff(p, HediffKind.TOXIC_BUILDUP)
    }
    // Drug effects wear off; withdrawal.
    for (h in p.hediffs.toList()) {
        if (h.kind == HediffKind.ALCOHOL_ADDICTION || h.kind == HediffKind.SMOKELEAF_ADDICTION || h.kind == HediffKind.PSYCHITE_ADDICTION) {
            h.severity -= 0.45f / 96f
            if (h.severity <= 0f) {
                h.severity = 0f
                if (p.hediff(HediffKind.WITHDRAWAL) == null) addHediff(p, HediffKind.WITHDRAWAL, 0.4f)
            } else removeHediff(p, HediffKind.WITHDRAWAL)
        }
    }
    // Disease: occasionally someone gets sick from dirt and cold.
    if (p.colonist && tick % 5000L == 0L) maybeSick(p)
}

private fun Game.maybeSick(p: Pawn) {
    var risk = 0.0007f * difficulty.threat.coerceAtLeast(0.3f)
    if (p.hediff(HediffKind.HYPOTHERMIA) != null) risk *= 3f
    val i = map.idx(p.x, p.y)
    risk *= 1f + map.filth[i] * 0.3f
    if (p.hediffs.any { it.kind.category == 0 }) return
    if (rng.float() > risk * 10f) return
    val kind = rng.pick(listOf(HediffKind.FLU, HediffKind.FLU, HediffKind.GUT_WORMS, HediffKind.MUSCLE_PARASITES, HediffKind.MALARIA, HediffKind.PLAGUE, HediffKind.SLEEPING_SICKNESS).let { l ->
        if (day < 8) l.filter { it == HediffKind.FLU || it == HediffKind.GUT_WORMS } else l
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
    when {
        cold > 20f -> add("Freezing", -0.25f)
        cold > 9f -> add("Very cold", -0.14f)
        cold > 3f -> add("Cold", -0.05f)
        hot > 15f -> add("Scorching", -0.25f)
        hot > 7f -> add("Very hot", -0.14f)
        hot > 3f -> add("Hot", -0.05f)
    }
    if (p.hediffs.any { it.kind.category == 0 && it.severity > 0.1f }) add("Sick", -0.06f)
    val lost = p.injuries.count { it.missing && !p.implants.containsKey(it.part) && !p.race.body[it.part].inner }
    if (lost > 0) add("Missing body parts", -min(0.2f, 0.05f * lost))
    if (p.implants.isNotEmpty() && Trait.TRANSHUMANIST in p.traits) add("Bionic upgrades", 0.1f)
    // Clothing.
    if (p.apparel.isEmpty() && Trait.NUDIST !in p.traits) add("Naked", -0.1f)
    else if (p.apparel.isNotEmpty() && Trait.NUDIST in p.traits) add("Dressed against my beliefs", -0.1f)
    else if (p.apparel.isNotEmpty() && p.apparel.any { it.hp < (it.type.apparel?.hp ?: 100f) * 0.3f }) add("Tattered clothing", -0.04f)
    else if (p.apparel.any { it.quality.ordinal >= Quality.EXCELLENT.ordinal }) add("Fine clothing", 0.04f)
    // Environment.
    if (p.dark && p.job?.type != JobType.SLEEP) add("In the dark", -0.03f)
    if ((weather == Weather.RAIN || weather == Weather.THUNDER) && !map.roofed(i)) add("Soaking wet", -0.04f)
    val r = map.roomId[i]
    if (r >= 0 && map.roomIndoor[r] && !map.roomDirty) {
        if (map.roomBeauty[r] < -0.6f) add("Ugly surroundings", -0.04f)
        else if (map.roomBeauty[r] > 1.6f) add("Pretty surroundings", 0.06f)
        if (map.roomClean[r] < -0.9f) add("Filthy room", -0.07f)
        else if (map.roomClean[r] < -0.4f) add("Dirty room", -0.03f)
        if (map.roomSize[r] < 7 && map.roomRole[r] != 1) add("Cramped", -0.04f)
    } else if (!map.roofed(i)) {
        if (Trait.GREEN_THUMB in p.traits && map.plant[i] != null) add("Among the plants", 0.04f)
    }
    // Drugs.
    for (h in p.hediffs) when (h.kind) {
        HediffKind.ALCOHOL_HIGH -> add("Drunk", 0.06f)
        HediffKind.SMOKELEAF_HIGH -> add("Mellow", 0.08f)
        HediffKind.PSYCHITE_HIGH -> add("Psychite buzz", 0.12f)
        HediffKind.WITHDRAWAL -> add("Withdrawal", -0.14f)
        else -> {}
    }
    // Traits.
    if (Trait.SANGUINE in p.traits) add("Cheerful", 0.12f)
    if (Trait.DEPRESSIVE in p.traits) add("Gloomy", -0.12f)
    if (Trait.OPTIMIST in p.traits) add("Optimist", 0.1f)
    if (Trait.PESSIMIST in p.traits) add("Pessimist", -0.1f)
    // Corpses lying around.
    var seen = 0f
    for (s in map.items.values) {
        if (s.corpseOf == null) continue
        if (abs(s.x - p.x) + abs(s.y - p.y) > 9) continue
        if (Trait.PSYCHOPATH in p.traits || Trait.BLOODLUST in p.traits) { add("Enjoying the carnage", 0.04f); break }
        seen += if (s.rot > 0.4f) 0.06f else 0.03f
        if (s.corpseColonist) seen += 0.02f
    }
    if (seen > 0f) add("Saw a corpse", -min(0.14f, seen))
    // Relationships.
    if (p.spouse >= 0) {
        val sp = pawnById(p.spouse)
        if (sp != null && sp.alive) add("Married", 0.06f)
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
    if (p.breakUntil > 0 && tick >= p.breakUntil) {
        p.breakUntil = 0; p.breakKind = 0; p.hostileFlag = false
        if (p.downed && p.job == null) { /* catatonic recovery */ }
        endJob(p)
        p.addThought("Catharsis", 0.22f, tick, (2.5f * TICKS_PER_DAY).toInt())
        say("${p.name} has recovered from the mental break.", 0)
        return
    }
    if (p.breakUntil > 0 || p.downed || p.drafted && p.job?.type == JobType.ATTACK) return
    if (!mentalBreaksEnabled) return
    val mood = p.mood
    val chance = when {
        mood < 0.10f -> 0.03f
        mood < 0.20f -> 0.012f
        mood < 0.30f -> 0.0045f
        else -> 0f
    } * (if (Trait.PSYCHOPATH in p.traits) 0.8f else 1f)
    if (chance <= 0f || rng.float() > chance) return
    val sev = if (mood < 0.10f) 2 else if (mood < 0.20f) 1 else 0
    val options = ArrayList<Int>()
    options += Break.WANDER; options += Break.BINGE_FOOD; options += Break.INSULT
    if (sev >= 1) { options += Break.TANTRUM; options += Break.BERSERK; if (Trait.PYROMANIAC in p.traits) { options += Break.FIRE; options += Break.FIRE } }
    if (sev >= 2) { options += Break.CATATONIC; options += Break.RUN_WILD; options += Break.BERSERK }
    if (map.countItems(ItemType.BEER) + map.countItems(ItemType.JOINT) > 0) options += Break.BINGE_DRUGS
    startBreak(p, rng.pick(options))
}

fun Game.startBreak(p: Pawn, kind: Int) {
    p.breakKind = kind
    p.breakUntil = tick + rng.range(2000, 5000)
    p.drafted = false
    endJob(p)
    when (kind) {
        Break.BERSERK -> { p.hostileFlag = true; say("${p.name} has gone berserk!", 3) }
        Break.CATATONIC -> { p.breakUntil = tick + rng.range(5000, 9000); downPawn(p); say("${p.name} has gone catatonic.", 3) }
        Break.RUN_WILD -> say("${p.name} is running into the wilds!", 3)
        Break.TANTRUM -> say("${p.name} is throwing a tantrum!", 3)
        Break.FIRE -> say("${p.name} is setting fires!", 3)
        else -> say("${p.name} is having a mental break: ${Break.label(kind)}.", 2)
    }
    for (o in colonists) if (o !== p && Trait.PSYCHOPATH !in o.traits) o.addThought("Witnessed a mental break", -0.03f, tick, TICKS_PER_DAY)
}

fun Game.onPawnDied(p: Pawn, cause: String, source: Pawn?) {
    if (caravanDeath(p, cause)) return
    if (p.race.mech) {
        map.drop(ItemType.STEEL, rng.range(20, 60), p.x, p.y)
        map.drop(ItemType.COMPONENT, rng.range(1, 3), p.x, p.y)
        if (rng.chance(0.15f)) map.drop(ItemType.PLASTEEL, rng.range(5, 12), p.x, p.y)
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
    for (w in p.apparel) if (w.hp > 20f) map.drop(w.type, 1, cx, cy, w.quality)
    p.apparel.clear()
    for (b in map.buildings()) if (b != null && b.ownerId == p.id) b.ownerId = -1
    when {
        p.faction == Faction.PLAYER && !p.isAnimal && !p.prisoner -> {
            say("${p.name} has died ($cause).", 3)
            for (par in listOf(p.mother, p.father)) pawnById(par)?.let { if (it.alive) it.addThought("Child died: ${p.name.substringBefore(' ')}", -0.4f, tick, 12 * TICKS_PER_DAY) }
            graveyard.add("${p.name} - $cause (day ${day + 1})")
            if (p.age > 0) for (o in colonists) {
                if (Trait.PSYCHOPATH in o.traits) continue
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
    if (source != null && source.colonist && p.faction == Faction.ENEMY && Trait.BLOODLUST in source.traits) source.addThought("Killed someone", 0.1f, tick, TICKS_PER_DAY)
}
