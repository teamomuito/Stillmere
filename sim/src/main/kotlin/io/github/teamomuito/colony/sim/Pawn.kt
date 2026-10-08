package io.github.teamomuito.colony.sim

import kotlin.math.max
import kotlin.math.min

enum class JobType(val label: String) {
    IDLE("Idle"), WANDER("Wandering"), EAT("Eating"), SLEEP("Sleeping"), MINE("Mining"), CUT("Cutting plants"),
    SOW("Sowing"), HARVEST("Harvesting"), HAUL("Hauling"), BUILD("Building"), DECONSTRUCT("Deconstructing"),
    COOK("Cooking"), RESEARCH("Researching"), TEND("Doctoring"), REFUEL("Refuelling"), MOVE("Moving"),
    FLEE("Fleeing"), ATTACK("Fighting"), BREAK("Mental break"), RAID("Raiding"), LEAVE("Leaving"),
}

class Job(val type: JobType, var tx: Int = -1, var ty: Int = -1) {
    var stage = 0
    var timer = 0
    var work = 0f
    var targetPawn = -1
    var key = -1
    var amount = 0
    var dx = -1
    var dy = -1
}

class Injury(var severity: Float, var bleed: Float, var tended: Boolean = false)

class Thought(val label: String, val mood: Float, var expires: Long)

class Pawn(val id: Int, var name: String, val colonist: Boolean) {
    var x = 0
    var y = 0
    var fromX = 0
    var fromY = 0
    var moveCd = 0
    var moveTotal = 1

    var hp = 100f
    var maxHp = 100f
    var food = 0.85f
    var rest = 0.9f
    var mood = 0.55f
    var downed = false
    var dead = false
    var deathTick = 0L
    var drafted = false
    var weapon = Weapon.FISTS
    var hostile = !colonist
    var age = 25
    var attackCd = 0
    var breakUntil = 0L
    var breakKind = 0
    var temp = 20f
    var wanderer = false
    var pathKey = -1

    val skill = IntArray(SkillType.entries.size)
    val xp = FloatArray(SkillType.entries.size)
    val passion = IntArray(SkillType.entries.size)
    val priority = IntArray(WorkType.entries.size) { 3 }
    val traits = ArrayList<Trait>()
    val injuries = ArrayList<Injury>()
    val thoughts = ArrayList<Thought>()

    var job: Job? = null
    var path: IntArray? = null
    var pathI = 0
    var carryType: ItemType? = null
    var carryCount = 0
    var bedId = -1 // cell index of owned bed, -1 for none
    val reserved = ArrayList<Int>()

    // Raider bookkeeping
    var raidId = 0
    var retreating = false

    val alive get() = !dead
    val bleeding get() = injuries.sumOf { it.bleed.toDouble() }.toFloat()
    val untended get() = injuries.any { !it.tended && it.bleed > 0.0005f }
    val injured get() = injuries.isNotEmpty() || hp < maxHp - 1f
    val moving get() = path != null && pathI < (path?.size ?: 0)

    fun level(s: SkillType) = skill[s.ordinal]

    fun workSpeed(s: SkillType): Float {
        var f = 0.4f + 0.075f * skill[s.ordinal]
        if (Trait.HARD_WORKER in traits) f *= 1.25f
        if (Trait.LAZY in traits) f *= 0.75f
        if (rest < 0.2f) f *= 0.8f
        if (hp < maxHp * 0.6f) f *= 0.8f
        return f
    }

    fun moveSpeedTicks(): Int {
        var t = 11
        if (Trait.NIMBLE in traits) t -= 2
        if (hp < maxHp * 0.5f) t += 4
        if (carryCount > 0) t += 1
        return t
    }

    fun gainXp(s: SkillType, amount: Float) {
        val i = s.ordinal
        val mult = when (passion[i]) { 2 -> 2.0f; 1 -> 1.4f; else -> 1.0f }
        xp[i] += amount * mult
        val need = 2500f + skill[i] * 400f
        if (xp[i] >= need && skill[i] < 20) { xp[i] -= need; skill[i]++ }
    }

    fun addThought(label: String, mood: Float, now: Long, duration: Int) {
        thoughts.removeAll { it.label == label }
        thoughts.add(Thought(label, mood, now + duration))
    }

    fun clearPath() { path = null; pathI = 0 }

    fun interpX(): Float = if (moveCd > 0 && moveTotal > 0) x + (fromX - x) * (moveCd.toFloat() / moveTotal) else x.toFloat()
    fun interpY(): Float = if (moveCd > 0 && moveTotal > 0) y + (fromY - y) * (moveCd.toFloat() / moveTotal) else y.toFloat()

    fun moodWithThoughts(now: Long): Float {
        var v = 0.6f
        for (t in thoughts) if (t.expires > now) v += t.mood
        if (Trait.OPTIMIST in traits) v += 0.1f
        if (Trait.PESSIMIST in traits) v -= 0.1f
        return min(1f, max(0f, v))
    }
}

object Names {
    val first = listOf(
        "Ada", "Bram", "Cleo", "Dax", "Elin", "Finn", "Gwen", "Hale", "Iris", "Jory", "Kai", "Lena", "Milo", "Nora", "Orin",
        "Pia", "Quinn", "Rhea", "Sol", "Tess", "Uri", "Vera", "Wren", "Xan", "Yara", "Zed", "Odette", "Jasper", "Mira", "Tobias",
    )
    val last = listOf(
        "Voss", "Marek", "Okafor", "Lindqvist", "Tanaka", "Reyes", "Hollis", "Brandt", "Moreau", "Kowal", "Idris", "Calder",
    )
    val raider = listOf("Skull", "Rat", "Ash", "Fang", "Hook", "Crow", "Slag", "Burr", "Gash", "Knuckle", "Rust", "Ox", "Jag", "Wolf")
}
