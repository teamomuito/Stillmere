package io.github.teamomuito.colony.sim

import kotlin.math.max
import kotlin.math.min

/**
 * Need rules, kept out of [Game] so they can be tested on their own. Each constant is listed with its source in
 * `docs/fidelity/needs-mood.md`. The reference gives some deltas per 150-tick interval; [INTERVAL_TICKS] converts them
 * to the per-tick rates the sim uses.
 */
object NeedRules {
    /** Nutrition an adult human uses per day at the base hunger rate. The food bar runs from 0 to 1. */
    const val HUMAN_NUTRITION_PER_DAY = 1.6f

    /** The reference's need interval, in ticks. */
    const val INTERVAL_TICKS = 150f

    /**
     * Nutrition a human uses per day, after traits, pregnancy and age. The trait, pregnancy and child multipliers are
     * Stillmere's own (UNVERIFIED). Sleep is applied by the caller.
     */
    fun humanFoodPerDay(gourmand: Boolean, ascetic: Boolean, pregnant: Boolean, age: Int): Float =
        HUMAN_NUTRITION_PER_DAY * (if (gourmand) 1.3f else if (ascetic) 0.9f else 1f) * (if (pregnant) 1.25f else 1f) * (if (age < 13) 0.6f else 1f)

    /** Food below which a pawn is hungry: 0.8 times the eat-wanted level of 0.3. */
    const val HUNGRY_FOOD = 0.24f

    /** Food below which a pawn is urgently hungry: 0.4 times the eat-wanted level of 0.3. */
    const val URGENT_FOOD = 0.12f

    /** Share of the base food use at this food level. Fed 1, hungry 0.5, urgently hungry 0.25, starving 0. */
    fun hungerMultiplier(food: Float): Float = when {
        food <= 0f -> 0f
        food < URGENT_FOOD -> 0.25f
        food < HUNGRY_FOOD -> 0.5f
        else -> 1f
    }

    /** Rest lost per day while awake, in the Rested band. */
    const val REST_FALL_PER_DAY = 0.95f

    /** Share of the rest fall at this rest level. Rested 1, tired 0.7, very tired 0.3, exhausted 0.6. */
    fun restFallMultiplier(rest: Float): Float = when {
        rest < 0.01f -> 0.6f
        rest < 0.14f -> 0.3f
        rest < 0.28f -> 0.7f
        else -> 1f
    }

    /** Joy lost per 150 ticks at this joy level: empty, very low, low, and satisfied or above. */
    fun joyFallPerInterval(joy: Float): Float = when {
        joy < 0.01f -> 0.0015f
        joy < 0.15f -> 0.0006f
        joy < 0.3f -> 0.00105f
        else -> 0.0015f
    }

    /** Malnutrition gained while starving, and lost while fed, per day. Nominal: the per-pawn factor is not applied. */
    const val MALNUTRITION_PER_DAY = 0.453f

    /** Rest below this counts as zero. */
    const val ZERO_REST = 0.0001f

    /** Ticks at zero rest before the body may collapse into sleep. */
    const val COLLAPSE_AFTER_TICKS = 1000

    /** Mean days between collapses, by the ticks already spent at zero rest. */
    fun collapseMtbDays(zeroRestTicks: Int): Float = when {
        zeroRestTicks < 15000 -> 0.25f
        zeroRestTicks < 30000 -> 0.125f
        zeroRestTicks >= 45000 -> 1f / 16f
        else -> 1f / 12f
    }

    /**
     * Beauty need level for a room's average beauty per cell, from RimWorld/Need_Beauty.cs:95. The base level 0.5 is D: the code
     * default is at RimWorld/NeedDef.cs:45. The beauty need's XML override is not in the tree, so that part is
     * UNVERIFIED (docs/fidelity/needs-mood.md, row "Beauty need level").
     */
    fun beautyLevel(roomBeauty: Float): Float = (0.5f + 0.1f * roomBeauty).coerceIn(0f, 1f)

    /** A beauty level at or below this is ugly. */
    const val UGLY_BEAUTY = 0.35f

    /** A beauty level above this is pretty. */
    const val PRETTY_BEAUTY = 0.65f

    /**
     * Comfort need level bands. The edges (0.1, 0.6, 0.7, 0.8, 0.9) are from the reference's band rule (Need_Comfort.cs:41-61).
     * The names are Stillmere's own, from the comfort level's plain meaning, from bare to opulent.
     */
    enum class ComfortBand { BARE, MODEST, COSY, SNUG, PLUSH, OPULENT }

    fun comfortBand(level: Float): ComfortBand = when {
        level < 0.1f -> ComfortBand.BARE
        level < 0.6f -> ComfortBand.MODEST
        level < 0.7f -> ComfortBand.COSY
        level < 0.8f -> ComfortBand.SNUG
        level < 0.9f -> ComfortBand.PLUSH
        else -> ComfortBand.OPULENT
    }

    /**
     * Change in the outdoors need per 150 ticks. [outdoor] means the pawn stands outside the rooms; [roofed] means it has a
     * roof (Stillmere has one kind, which counts as thick). A fall is multiplied by 0.2 in bed.
     */
    fun outdoorsDelta(outdoor: Boolean, roofed: Boolean, inBed: Boolean): Float {
        val units = if (outdoor) (if (roofed) -0.4f else 8f) else (if (roofed) -0.45f else 5f)
        val d = units * 0.0025f
        return if (d < 0f && inBed) d * 0.2f else d
    }

    /** The lowest the outdoors need falls: 0.2 outside, 0 under a roof indoors. */
    fun outdoorsFloor(outdoor: Boolean, roofed: Boolean): Float = if (!outdoor && roofed) 0f else 0.2f

    /** Change in the indoors need per 150 ticks. Indoors under a roof it rises; outside it falls, with no roof it falls more slowly. */
    fun indoorsDelta(outdoor: Boolean, roofed: Boolean): Float {
        val units = if (!outdoor) (if (roofed) 2f else 0f) else (if (roofed) 0f else -0.25f)
        return units * 0.0025f
    }

    /**
     * Moves a need level by [delta]. A fall stops at [floor], and a level already below the floor stays where it is. A rise
     * stops at 1.
     */
    fun stepNeed(level: Float, delta: Float, floor: Float = 0f): Float =
        if (delta < 0f) min(level, max(level + delta, floor)) else min(level + delta, 1f)

    /** Where a comfort gap falls: 0 for under 10 degrees, 1 for 10 to 20, 2 for 20 to 30, 3 for 30 and up. */
    fun thermalStage(gap: Float): Int = when {
        gap < 10f -> 0
        gap < 20f -> 1
        gap < 30f -> 2
        else -> 3
    }
}

/**
 * Mood thresholds and timing for mental breaks. The major and extreme thresholds are fractions of the minor one, and the
 * mean times are the reference's. The minor threshold is Stillmere's own: the reference reads it from a stat that has no
 * base value in code (reference row 223 is ambiguous). See `docs/fidelity/needs-mood.md`.
 */
object BreakRules {
    /** Mood below which a minor break can happen. Stillmere's own value (UNVERIFIED against the stat's XML base). */
    const val MINOR = 0.35f

    /** Mood below which a major break can happen. */
    const val MAJOR = 0.20f

    /** Mood below which an extreme break can happen. */
    const val EXTREME = 0.05f

    /** Ticks a mood must stay below a level before a break of that level can happen. */
    const val MIN_TICKS_BELOW = 2000

    /** Awake ticks after a break before another can happen. */
    const val COOLDOWN_TICKS = 15000

    /** 2 for extreme, 1 for major, 0 for minor, or -1 when the mood is above every threshold. */
    fun severity(mood: Float): Int = when {
        mood < EXTREME -> 2
        mood < MAJOR -> 1
        mood < MINOR -> 0
        else -> -1
    }

    /** Mean days between breaks at a severity. Extreme 0.5, major 0.8, minor 4. Infinite for no break. */
    fun meanDaysBetweenBreaks(severity: Int): Float = when (severity) {
        2 -> 0.5f
        1 -> 0.8f
        0 -> 4f
        else -> Float.POSITIVE_INFINITY
    }

    /** Chance that a check covering [ticks] ticks fires a break of this severity. */
    fun chance(severity: Int, ticks: Int): Float {
        val meanDays = meanDaysBetweenBreaks(severity)
        if (meanDays.isInfinite()) return 0f
        return min(1f, ticks.toFloat() / (meanDays * TICKS_PER_DAY))
    }
}

/** Drug tolerance and addiction. The tolerance curve is the reference's; build-up and fall are Stillmere's own. */
object DrugRules {
    /** Tolerance gained per dose of an addictive drug, capped at 1. UNVERIFIED: the reference's build-up is XML. */
    const val TOLERANCE_PER_DOSE = 0.05f

    /** Tolerance lost per day. UNVERIFIED: the reference's decay is XML. */
    const val TOLERANCE_FALL_PER_DAY = 0.025f

    /** Addiction severity (the chemical need) lost per day. The default is from the reference; per-drug values are XML. */
    const val ADDICTION_FALL_PER_DAY = 0.5f

    /** Addiction severity at or below which the pawn is in withdrawal. */
    const val WITHDRAWAL_LEVEL = 0.01f

    /** Craving severity that each addiction in withdrawal adds. The cravings are summed, capped at 1. Stillmere's own (UNVERIFIED). */
    const val CRAVING_PER_ADDICTION = 0.4f

    /** The drugs that build tolerance and can addict. Dawn tablets do not. */
    val ADDICTIVE: Set<ItemType> = setOf(ItemType.BEER, ItemType.JOINT, ItemType.LUCID_DRAUGHT, ItemType.PALE_DUST, ItemType.WHITE_RUSH, ItemType.SURGE_TONIC)

    /** The addiction kinds, which fall each day. */
    val ADDICTIONS: Set<HediffKind> = setOf(
        HediffKind.BREW_DEPENDENCE, HediffKind.HUSHMOSS_ADDICTION, HediffKind.LUCID_ADDICTION,
        HediffKind.PALE_DUST_ADDICTION, HediffKind.WHITE_RUSH_ADDICTION, HediffKind.SURGE_TONIC_ADDICTION,
    )

    /** Chance that a first dose addicts, at zero tolerance. Stillmere's own values (UNVERIFIED). */
    fun addictionChance(drug: ItemType): Float = when (drug) {
        ItemType.LUCID_DRAUGHT -> 0.1f
        ItemType.BEER -> 0.03f
        ItemType.PALE_DUST -> 0.45f
        ItemType.WHITE_RUSH -> 0.3f
        ItemType.SURGE_TONIC -> 0.06f
        else -> 0.015f
    }

    /** Multiplier on the addiction chance at a tolerance: 1 at 0, 5 at 0.5, 15 at 0.8, linear between, flat above 0.8. */
    fun addictionMultiplier(tolerance: Float): Float = when {
        tolerance <= 0f -> 1f
        tolerance < 0.5f -> 1f + 8f * tolerance
        tolerance < 0.8f -> 5f + (tolerance - 0.5f) * (10f / 0.3f)
        else -> 15f
    }
}
