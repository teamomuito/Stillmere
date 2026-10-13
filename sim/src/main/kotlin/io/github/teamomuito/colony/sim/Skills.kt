package io.github.teamomuito.colony.sim

import kotlin.math.max
import kotlin.math.min

/**
 * A piecewise-linear curve: flat before its first point and after its last, straight lines between points.
 * Written here from the shape of the reference curves (docs/fidelity/skills-traits.md), not from the source.
 */
internal class PiecewiseCurve(vararg points: Pair<Float, Float>) {
    private val xs = FloatArray(points.size) { points[it].first }
    private val ys = FloatArray(points.size) { points[it].second }

    fun eval(x: Float): Float {
        if (x <= xs[0]) return ys[0]
        val last = xs.size - 1
        if (x >= xs[last]) return ys[last]
        for (i in 1..last) {
            if (x <= xs[i]) {
                val x0 = xs[i - 1]; val x1 = xs[i]; val y0 = ys[i - 1]; val y1 = ys[i]
                // Multiply before dividing so that whole-number levels land on exact values.
                return y0 + (y1 - y0) * (x - x0) / (x1 - x0)
            }
        }
        return ys[last]
    }
}

/**
 * Skill rules from the decompiled reference, section 3.6: learning, the daily cap, decay, the XP curve, the
 * generation of base levels and passions, and the XP awarded per tick and per attack. Pure functions over the sim RNG.
 */
object SkillRules {
    /** The decay interval (RimWorld/SkillRecord.cs:27). */
    const val INTERVAL_TICKS = 200
    const val MIN_LEVEL = 0
    const val MAX_LEVEL = 20

    /** XP at or below this level drops the skill one level (RimWorld/SkillRecord.cs:45). */
    const val MIN_XP = -1000f

    /** Non-direct XP per day above which later XP learns at the saturated factor (RimWorld/SkillRecord.cs:33, 37). */
    const val DAILY_FULL_RATE_XP = 4000f
    const val SATURATED_FACTOR = 0.2f

    /**
     * Medicine XP factor with no medicine (RimWorld/Toils_Tend.cs:69, the default used when there is no medicine). It is also the
     * floor of [medicineXpFactor], so no medicine and a medicine of potency 0 give the same factor.
     */
    const val MEDICINE_XP_FACTOR_DEFAULT = 0.5f
    /** Medicine potency times this is the medicine XP factor (Verse/ThingDef.cs:712, the medicine XP gain property). D. */
    const val MEDICINE_XP_PER_POTENCY = 0.7f
    /** Highest medicine XP factor, the upper bound of the clamp (Verse/ThingDef.cs:712). D. */
    const val MEDICINE_XP_FACTOR_MAX = 1f

    /** Ticks per real second, so a game tick is a sixtieth of a second (Verse/GenTicks.cs:67-70). */
    private const val TICKS_PER_SECOND = 60f

    /** Lowest first uniform draw in [gaussian]; Stillmere's guard (C), not in the reference. */
    const val GAUSSIAN_FLOOR = 1e-7f

    private val xpCurve = PiecewiseCurve(0f to 1000f, 9f to 10000f, 19f to 30000f)
    private val ageMaxFactor = PiecewiseCurve(0f to 0f, 10f to 0.7f, 35f to 1f, 60f to 1.6f)
    private val ageFactor = PiecewiseCurve(3f to 0.2f, 18f to 1f)
    private val levelAdjust = PiecewiseCurve(0f to 0f, 10f to 10f, 20f to 16f, 27f to 20f)

    /** Work types that use each skill, as a bit mask over WorkType ordinals. A skill with no work type is never disabled. */
    private val workMask = IntArray(SkillType.entries.size) { s ->
        WorkType.entries.filter { it.skill()?.ordinal == s }.fold(0) { m, w -> m or (1 shl w.ordinal) }
    }

    /** XP needed to rise from [level] to the next level. */
    fun xpToLevelUp(level: Int): Float = xpCurve.eval(level.toFloat())

    /** Learning factor from the passion: 0 none, 1 minor, 2 major. */
    fun passionFactor(passion: Int): Float = when (passion) {
        2 -> 1.5f
        1 -> 1.0f
        else -> 0.35f
    }

    /**
     * XP change at the decay interval for a skill at [level]. Zero below level 10. Each entry is the value
     * from the reference, read by level; higher levels lose more.
     */
    fun decayPerInterval(level: Int): Float = when (level) {
        10 -> -0.1f
        11 -> -0.2f
        12 -> -0.4f
        13 -> -0.6f
        14 -> -1.0f
        15 -> -1.8f
        16 -> -2.8f
        17 -> -4.0f
        18 -> -6.0f
        19 -> -8.0f
        20 -> -12.0f
        else -> 0f
    }

    /** A skill is disabled when it has work types and every one of them is incapable (Stillmere's mapping). */
    fun skillDisabled(s: SkillType, incapable: Int): Boolean {
        val mask = workMask[s.ordinal]
        return mask != 0 && (incapable and mask) == mask
    }

    /** Seconds of a cycle in game ticks (Verse/GenTicks.cs:67-70: ticks / 60). */
    fun cycleSeconds(ticks: Int): Float = ticks / TICKS_PER_SECOND

    /**
     * The medicine XP factor from the medicine's potency: potency x 0.7, clamped to 0.5 to 1 (Verse/ThingDef.cs:712, D). Sterile
     * dressing (potency 1.0) gives 0.7. Leaf poultice (potency 0.6) gives 0.42 and is raised to the floor, 0.5. Potency 0 (no
     * medicine) gives the floor, which is the reference's default (RimWorld/Toils_Tend.cs:69).
     */
    fun medicineXpFactor(potency: Float): Float =
        (potency * MEDICINE_XP_PER_POTENCY).coerceIn(MEDICINE_XP_FACTOR_DEFAULT, MEDICINE_XP_FACTOR_MAX)

    /**
     * XP for one medical tend: 500 for a human or 175 for an animal, times the medicine XP factor (RimWorld/Toils_Tend.cs:68-70).
     * [potency] is the medicine's potency, 0 with no medicine.
     */
    fun tendXp(animal: Boolean, potency: Float): Float = (if (animal) 175f else 500f) * medicineXpFactor(potency)

    /** A passion that a trait forces at generation. Which trait forces which skill is Stillmere's own pairing. */
    fun forcedPassion(t: Trait): SkillType? = if (t == Trait.STREET_SCRAPPER) SkillType.CLOSE_COMBAT else null

    /**
     * The shooting XP per second of a burst's cycle: 170 against a hostile pawn, 20 otherwise (Verse/Verb_Shoot.cs:14; the
     * learn is at :16, and the cycle is scaled by the full cycle time at :15).
     */
    fun shootingXpPerSecond(hostile: Boolean): Float = if (hostile) 170f else 20f

    /** The melee XP per second of the full cycle (RimWorld/Verb_MeleeAttack.cs:31). */
    const val MELEE_XP_PER_SECOND = 200f

    /**
     * Standard normal draw from two uniform draws (Verse/Rand.cs:137-142), using StrictMath for the log and sine. The first
     * draw is floored at 1e-7 so the log stays finite. That floor is Stillmere's own guard (C): the reference draw has none.
     */
    fun gaussian(rng: Rng): Float {
        val u1 = max(rng.float(), GAUSSIAN_FLOOR).toDouble()
        val u2 = rng.float().toDouble()
        return (StrictMath.sqrt(-2.0 * StrictMath.log(u1)) * StrictMath.sin(2.0 * StrictMath.PI * u2)).toFloat()
    }

    /** Vanilla's Rand.Range: a uniform value from min to max, and min itself when max is not above min. */
    private fun rangeF(rng: Rng, minIncl: Float, maxIncl: Float): Float {
        if (maxIncl <= minIncl) return minIncl
        return rng.float() * (maxIncl - minIncl) + minIncl
    }

    /**
     * Base level of every skill at generation. Each skill starts at a uniform 0 to 4 (the default flag). Each
     * backstory gain adds its amount times a uniform 1.0 to 1.4. The age factor and the final compression follow.
     * [gains] are the backstory skill gains in backstory order (childhood, then adult).
     */
    fun generateLevels(rng: Rng, age: Int, gains: List<Pair<SkillType, Int>>): IntArray {
        val levels = IntArray(SkillType.entries.size)
        for (s in SkillType.entries) {
            var v = rng.range(0, 4).toFloat()
            for ((gs, amount) in gains) if (gs == s) v += amount.toFloat() * rangeF(rng, 1f, 1.4f)
            v *= rangeF(rng, 1f, ageMaxFactor.eval(age.toFloat()))
            v *= ageFactor.eval(age.toFloat())
            v = levelAdjust.eval(v)
            levels[s.ordinal] = Math.rint(v.toDouble()).toInt().coerceIn(MIN_LEVEL, MAX_LEVEL)
        }
        return levels
    }

    /** Passion codes, as [SkillRules.generatePassions] returns them. */
    const val PASSION_MINOR = 1
    const val PASSION_MAJOR = 2

    /** Budget a major passion costs. A minor costs one unit. */
    private const val MAJOR_PASSION_COST = 1.5f

    /**
     * The passions the budget buys, majors first, then minors. The budget is 5 plus a Gaussian clamped to +/-4. While a unit is
     * left, a major is bought when at least [MAJOR_PASSION_COST] is left and a coin comes up (half the time), otherwise a minor.
     */
    private fun purchasePassions(rng: Rng): List<Int> {
        var purse = 5f + gaussian(rng).coerceIn(-4f, 4f)
        val majors = ArrayList<Int>()
        val minors = ArrayList<Int>()
        while (purse >= 1f) {
            if (purse >= MAJOR_PASSION_COST && rng.float() < 0.5f) {
                majors.add(PASSION_MAJOR)
                purse -= MAJOR_PASSION_COST
            } else {
                minors.add(PASSION_MINOR)
                purse -= 1f
            }
        }
        return majors + minors
    }

    /**
     * Passions for a new pawn. The budget is 5 plus a Gaussian clamped to +/-4. Each unit buys a minor passion, and a
     * major passion costs 1.5 when the budget allows, taken half the time. Forced passions come first (a major if one
     * is left, else a minor). Then the rest go to the highest-level skills. A passion never adds levels.
     * Returns the passion per skill: 0 none, 1 minor, 2 major.
     */
    fun generatePassions(
        rng: Rng, levels: IntArray, forced: List<SkillType>, disabled: (SkillType) -> Boolean, adult: Boolean,
    ): IntArray {
        val stock = ArrayDeque(purchasePassions(rng))
        val passion = IntArray(levels.size)
        // A forced passion takes the next one in stock, or a minor when the stock is empty. An unforced one takes only from stock.
        fun spend(s: SkillType, mustTake: Boolean) {
            when {
                stock.isNotEmpty() -> passion[s.ordinal] = stock.removeFirst()
                mustTake -> passion[s.ordinal] = PASSION_MINOR
            }
        }
        for (s in SkillType.entries) {
            if (disabled(s)) continue
            repeat(forced.count { it == s }) { spend(s, mustTake = true) }
        }
        // Under-13s gain passion steps at each growth birthday before 13. That loop is ungated by DLC (Verse/PawnGenerator.cs:1886-1904)
        // and is not modelled. newHuman makes adults only (19 to 55), so this early return is never taken from there.
        if (!adult) return passion
        for (s in SkillType.entries.sortedByDescending { levels[it.ordinal] }) {
            if (disabled(s)) continue
            spend(s, mustTake = false)
        }
        return passion
    }

    /**
     * The level and XP after [gained] is added to a skill at [level] holding [xp]. The rise is applied first, then the fall
     * (docs/fidelity/skills-traits.md, rows "XP needed per level" and "Level drop").
     */
    fun applyXp(level: Int, xp: Float, gained: Float): Pair<Int, Float> {
        val (risen, held) = climb(level, xp + gained)
        return fall(risen, held)
    }

    /** The most XP a skill at the cap holds: one short of the requirement for the next level. */
    private fun capXp(): Float = xpToLevelUp(MAX_LEVEL) - 1f

    /**
     * Raises the level while the XP covers the requirement, spending it. A skill at the cap has its XP held just under the next
     * requirement. Reaching the cap during the rise holds the XP in [0, capXp]; a skill that starts at the cap keeps any lower XP.
     */
    private fun climb(level: Int, xp: Float): Pair<Int, Float> {
        if (level >= MAX_LEVEL) return MAX_LEVEL to min(xp, capXp())
        var lvl = level
        var x = xp
        while (x >= xpToLevelUp(lvl)) {
            x -= xpToLevelUp(lvl)
            lvl++
            if (lvl == MAX_LEVEL) return MAX_LEVEL to x.coerceIn(0f, capXp())
        }
        return lvl to x
    }

    /**
     * Lowers the level while the XP is at or below [MIN_XP]. Each drop refills the XP by the requirement of the lower level. A
     * drop to level 0 holds the XP at 0.
     */
    private fun fall(level: Int, xp: Float): Pair<Int, Float> {
        var lvl = level
        var x = xp
        while (x <= MIN_XP) {
            lvl--
            if (lvl <= MIN_LEVEL) return MIN_LEVEL to 0f
            x += xpToLevelUp(lvl)
        }
        return lvl to x
    }
}
