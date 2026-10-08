package io.github.teamomuito.colony.sim

import kotlin.math.max
import kotlin.math.min

fun Game.opinionOf(a: Pawn, b: Pawn): Int = a.opinion[b.id] ?: 0

fun attractedTo(a: Pawn, b: Pawn): Boolean = when {
    Trait.ASEXUAL in a.traits -> false
    Trait.BISEXUAL in a.traits -> true
    Trait.GAY in a.traits -> a.female == b.female
    else -> a.female != b.female
}

fun Game.socialInteract(a: Pawn, b: Pawn) {
    if (!a.alive || !b.alive) return
    val soc = a.level(SkillType.SOCIAL) + (if (Trait.SOCIABLE in a.traits) 3 else 0)
    val op = opinionOf(b, a)
    val roll = rng.float()
    var kind = "chitchat"
    when {
        // Rude people pick fights, and unhappy people snap.
        (Trait.ABRASIVE in a.traits && roll < 0.35f) || (a.mood < 0.3f && roll < 0.35f) || (op < -20 && roll < 0.25f) -> kind = "insult"
        roll < 0.15f + soc * 0.01f && opinionOf(b, a) > 25 -> kind = "deep"
        roll < 0.25f && a.spouse < 0 && a.lover < 0 && b.spouse < 0 && b.lover < 0 && attractedTo(a, b) && attractedTo(b, a) && opinionOf(b, a) > 35 && opinionOf(a, b) > 35 && a.age >= 18 && b.age >= 18 -> kind = "romance"
        roll < 0.3f -> kind = "kind"
    }
    when (kind) {
        "insult" -> {
            b.addThought("Insulted by ${a.name.substringBefore(' ')}", -0.06f, tick, 2 * TICKS_PER_DAY)
            b.opinion[a.id] = op - 12
            a.opinion[b.id] = opinionOf(a, b) - 4
            if (b.mood < 0.25f && rng.chance(0.08f)) startFight(b, a)
        }
        "deep" -> {
            val v = (6 + soc / 2)
            a.opinion[b.id] = opinionOf(a, b) + v / 2; b.opinion[a.id] = op + v
            a.addThought("Deep talk", 0.05f, tick, TICKS_PER_DAY)
            b.addThought("Deep talk", 0.05f, tick, TICKS_PER_DAY)
        }
        "kind" -> {
            b.opinion[a.id] = op + 4 + (if (Trait.KIND in a.traits) 4 else 0)
            b.addThought("Kind words", 0.03f, tick, TICKS_PER_DAY)
        }
        "romance" -> {
            if (rng.chance(0.3f + (soc * 0.02f))) {
                a.lover = b.id; b.lover = a.id
                a.addThought("New relationship", 0.12f, tick, 3 * TICKS_PER_DAY)
                b.addThought("New relationship", 0.12f, tick, 3 * TICKS_PER_DAY)
                say("${a.name} and ${b.name} are now together.", 1)
            } else {
                b.addThought("Turned down romance", -0.03f, tick, TICKS_PER_DAY)
                a.addThought("Got rebuffed", -0.07f, tick, 2 * TICKS_PER_DAY)
            }
        }
        else -> {
            a.opinion[b.id] = opinionOf(a, b) + 3
            b.opinion[a.id] = op + 3
            a.addThought("Chitchat", 0.02f, tick, TICKS_PER_DAY)
            b.addThought("Chitchat", 0.02f, tick, TICKS_PER_DAY)
        }
    }
    // Relationships can sour, and kin look out for each other.
    if (a.lover == b.id && b.lover == a.id && (opinionOf(a, b) < 5 || opinionOf(b, a) < 5) && rng.chance(0.25f)) {
        a.lover = -1; b.lover = -1
        a.addThought("Broke up with ${b.name.substringBefore(' ')}", -0.15f, tick, 6 * TICKS_PER_DAY); b.addThought("Broke up with ${a.name.substringBefore(' ')}", -0.15f, tick, 6 * TICKS_PER_DAY)
        say("${a.name} and ${b.name} broke up.", 2)
    }
    if (a.mother == b.id || a.father == b.id || b.mother == a.id || b.father == a.id) {
        a.opinion[b.id] = max(opinionOf(a, b), 40); b.opinion[a.id] = max(opinionOf(b, a), 40)
    }
    // Lovers who are together long enough marry.
    if (a.lover == b.id && b.lover == a.id && opinionOf(a, b) > 60 && opinionOf(b, a) > 60 && rng.chance(0.02f)) {
        a.spouse = b.id; b.spouse = a.id; a.lover = -1; b.lover = -1
        a.addThought("Got married", 0.2f, tick, 6 * TICKS_PER_DAY); b.addThought("Got married", 0.2f, tick, 6 * TICKS_PER_DAY)
        say("${a.name} and ${b.name} got married!", 1)
    }
    a.lastSocial = tick
}

/** A brief scuffle between unhappy colonists. */
fun Game.startFight(a: Pawn, b: Pawn) {
    if (a.hostileFlag || a.downed) return
    a.hostileFlag = false
    a.addThought("Got into a fight", -0.1f, tick, TICKS_PER_DAY)
    b.addThought("Got into a fight", -0.1f, tick, TICKS_PER_DAY)
    a.breakKind = Break.INSULT
    a.breakUntil = tick + 1200
    endJob(a)
    say("${a.name} and ${b.name} are fighting!", 2)
    dealDamage(b, DamageKind.BRUISE, 4f, 0f, a)
    dealDamage(a, DamageKind.BRUISE, 4f, 0f, b)
}
