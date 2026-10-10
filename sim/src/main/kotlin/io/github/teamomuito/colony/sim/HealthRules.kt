package io.github.teamomuito.colony.sim

import kotlin.math.max
import kotlin.math.min

/**
 * The health formulas as pure functions of plain numbers, so they are deterministic and unit-testable.
 * docs/fidelity/health.md lists every constant here with its reference value and whether it is confirmed.
 */
object HealthRules {
    // ------------------------------------------------------------------ downed state
    /** A pawn below this consciousness is unconscious and downed. */
    const val CONSCIOUSNESS_MIN = 0.30f
    /** A pawn at or below this moving is incapacitated and downed. */
    const val MOVING_MIN = 0.15f
    const val PAIN_SHOCK_DEFAULT = 0.80f
    const val WIMP_SHOCK_OFFSET = -0.50f
    const val PAIN_SHOCK_FLOOR = 0.30f
    const val PAIN_SHOCK_CEIL = 0.95f

    fun painShockThreshold(wimp: Boolean): Float =
        (PAIN_SHOCK_DEFAULT + if (wimp) WIMP_SHOCK_OFFSET else 0f).coerceIn(PAIN_SHOCK_FLOOR, PAIN_SHOCK_CEIL)

    /** The one test for being downed; standing back up is its negation, so there is no hysteresis. */
    fun shouldBeDowned(consciousness: Float, moving: Float, pain: Float, shockThreshold: Float): Boolean =
        consciousness < CONSCIOUSNESS_MIN || moving <= MOVING_MIN || pain >= shockThreshold

    // ------------------------------------------------------------------ part health
    fun partMax(partHp: Float, healthScale: Float): Float = partHp * healthScale

    /** Efficiency of one part: its remaining health fraction, times the implant's efficiency if it has one. */
    fun partEfficiency(damage: Float, max: Float, implantEff: Float? = null): Float {
        val base = (1f - damage / max).coerceIn(0f, 1f)
        return if (implantEff != null) base * implantEff else base
    }

    // ------------------------------------------------------------------ capacities
    /** Mean of the efficiencies of the parts that share a role. A missing part counts as 0, so one of two lungs gone halves it. */
    fun mean(effs: List<Float>, whenNone: Float = 1f): Float = if (effs.isEmpty()) whenNone else effs.sum() / effs.size

    fun sight(eyes: Float) = eyes
    fun hearing(ears: Float) = ears
    fun talking(jaw: Float) = jaw
    fun eating(jaw: Float) = jaw
    fun breathing(lungs: Float) = lungs
    fun bloodPumping(hearts: Float) = hearts
    /** Metabolism is carried by the stomach. */
    fun metabolism(stomach: Float) = stomach
    /** Filtration is limited by whichever of the kidneys and the liver is worse. A body without either is unaffected. */
    fun bloodFiltration(kidneys: Float?, liver: Float?): Float = min(kidneys ?: 1f, liver ?: 1f)

    fun moving(legs: Float, feet: Float, toes: Float): Float = legs * (0.75f + 0.25f * feet) * (0.9f + 0.1f * toes)

    /** Fingers carry most of a hand's grip. A body with no hands is not limited. */
    fun manipulation(hasHands: Boolean, hands: Float, fingers: Float, arms: Float): Float =
        if (hasHands) hands * (0.6f + 0.4f * fingers) * 0.7f + arms * 0.3f else 1f

    const val PUMPING_CONSCIOUSNESS_WEIGHT = 0.2f
    const val BREATHING_CONSCIOUSNESS_WEIGHT = 0.2f
    const val FILTRATION_CONSCIOUSNESS_WEIGHT = 0.1f
    const val PAIN_CONSCIOUSNESS_WEIGHT = 0.55f
    const val BLOOD_LOSS_CONSCIOUSNESS_START = 0.3f
    const val BLOOD_LOSS_CONSCIOUSNESS_SLOPE = 1.4f

    /** A factor with weight w pulls the result down by w times its shortfall. */
    fun weighted(capacity: Float, weight: Float): Float = 1f - weight * (1f - capacity)

    fun consciousness(brain: Float, pain: Float, bloodLoss: Float, breathing: Float, pumping: Float, filtration: Float): Float {
        var c = brain
        c *= 1f - pain * PAIN_CONSCIOUSNESS_WEIGHT
        if (bloodLoss > BLOOD_LOSS_CONSCIOUSNESS_START) c *= max(0f, 1f - (bloodLoss - BLOOD_LOSS_CONSCIOUSNESS_START) * BLOOD_LOSS_CONSCIOUSNESS_SLOPE)
        c *= weighted(pumping, PUMPING_CONSCIOUSNESS_WEIGHT)
        c *= weighted(breathing, BREATHING_CONSCIOUSNESS_WEIGHT)
        c *= weighted(filtration, FILTRATION_CONSCIOUSNESS_WEIGHT)
        return c
    }

    // ------------------------------------------------------------------ pain
    const val PAIN_PER_SEVERITY = 0.014f

    /** Pain from one wound: damage type factor times severity, softened on bigger bodies. */
    fun woundPain(kindPain: Float, severity: Float, healthScale: Float): Float =
        severity * kindPain * PAIN_PER_SEVERITY / max(0.5f, Math.sqrt(healthScale.toDouble()).toFloat())

    // ------------------------------------------------------------------ bleeding
    /** A tended wound is bandaged. Better care leaves less of a trickle. */
    const val TENDED_BLEED_RESIDUAL = 0.06f
    const val BLEED_CLOT_TICKS_UNTENDED = 34000f
    const val BLEED_CLOT_TICKS_TENDED = 12000f

    fun bleedRate(raw: Float, tended: Boolean, tendQuality: Float): Float =
        if (tended) raw * (1f - tendQuality) * TENDED_BLEED_RESIDUAL else raw

    /** Clotting over [ldt] old ticks: the wound's own bleed rate falls with time, never below half per step. */
    fun clot(bleed: Float, tended: Boolean, ldt: Float): Float =
        bleed * (1f - ldt / (if (tended) BLEED_CLOT_TICKS_TENDED else BLEED_CLOT_TICKS_UNTENDED)).coerceAtLeast(0.5f)

    /** Blood regained per day once nothing is bleeding. */
    fun bloodRecoveryPerDay(wellFed: Boolean): Float = if (wellFed) 0.36f else 0.1f

    // ------------------------------------------------------------------ healing
    const val HEAL_UNTENDED_PER_DAY = 3.2f
    const val HEAL_TENDED_PER_DAY = 8f
    const val HEAL_TEND_BASE = 0.55f
    const val HEAL_BED_FACTOR = 1.35f
    const val HEAL_HOSPITAL_FACTOR = 1.15f
    const val HEAL_BRUISE_FACTOR = 1.5f
    const val HEAL_BURN_FACTOR = 0.7f
    const val HEAL_STARVING_FACTOR = 0.3f
    const val STARVING_FOOD = 0.05f
    const val SCAR_CHANCE = 0.18f
    /** An injury this small is gone, or leaves a scar. */
    const val HEALED_SEVERITY = 0.3f

    /** Health points a wound recovers in a day. */
    fun healPerDay(tended: Boolean, tendQuality: Float, kind: DamageKind, inBed: Boolean, hospital: Boolean, food: Float): Float {
        var heal = if (tended) HEAL_TENDED_PER_DAY * (HEAL_TEND_BASE + tendQuality) else HEAL_UNTENDED_PER_DAY
        if (inBed) heal *= HEAL_BED_FACTOR
        if (hospital) heal *= HEAL_HOSPITAL_FACTOR
        if (kind == DamageKind.BRUISE) heal *= HEAL_BRUISE_FACTOR
        if (kind == DamageKind.BURN) heal *= HEAL_BURN_FACTOR
        if (food < STARVING_FOOD) heal *= HEAL_STARVING_FACTOR
        return heal
    }

    // ------------------------------------------------------------------ infection
    const val INFECTION_START = 0.04f
    const val INFECTION_GROWTH_PER_DAY = 1.1f
    const val INFECTION_TEND_SUPPRESSION = 0.8f
    const val IMMUNE_PER_DAY = 0.9f
    const val IMMUNE_TEND_BONUS = 0.6f
    const val IMMUNE_REST_BONUS = 0.3f
    const val INFECTION_CHANCE_BASE = 0.7f * 2.2f
    const val INFECTION_OUTDOORS_FACTOR = 1.5f
    const val INFECTION_HOSPITAL_FACTOR = 0.3f
    const val INFECTION_TEND_FACTOR = 0.5f
    /** Only wounds at least this severe can become infected. */
    const val INFECTION_MIN_SEVERITY = 2f
    /** A wound tended at or above this quality is clean enough that it cannot become infected. */
    const val INFECTION_SAFE_TEND_QUALITY = 0.3f
    /** Tending takes this much off an infection per point of quality. */
    const val TEND_INFECTION_CURE = 0.35f

    fun infectionGrowth(tendQuality: Float, dt: Int): Float = INFECTION_GROWTH_PER_DAY / TICKS_PER_DAY * dt * (1f - INFECTION_TEND_SUPPRESSION * tendQuality)
    fun immuneGain(tendQuality: Float, resting: Boolean, dt: Int): Float =
        IMMUNE_PER_DAY / TICKS_PER_DAY * dt * (1f + IMMUNE_TEND_BONUS * tendQuality + (if (resting) IMMUNE_REST_BONUS else 0f))

    fun infectionChance(kindInfect: Float, dt: Int, indoors: Boolean, hospital: Boolean, tended: Boolean, tendQuality: Float): Float {
        var chance = kindInfect * INFECTION_CHANCE_BASE / TICKS_PER_DAY * dt
        if (!indoors) chance *= INFECTION_OUTDOORS_FACTOR
        if (hospital) chance *= INFECTION_HOSPITAL_FACTOR
        if (tended) chance *= (1f - tendQuality) * INFECTION_TEND_FACTOR
        return chance
    }

    // ------------------------------------------------------------------ tending
    /** Medical tend quality of a doctor by skill level, interpolated between these (level, value) points. */
    val TEND_SKILL_CURVE: List<Pair<Int, Float>> = listOf(0 to 0.20f, 6 to 0.80f, 10 to 1.10f, 18 to 1.50f, 20 to 1.55f)
    const val NO_MEDICINE_POTENCY = 0.3f
    const val NO_MEDICINE_MAX_QUALITY = 0.7f
    const val HOSPITAL_BED_TEND_OFFSET = 0.10f
    const val SELF_TEND_FACTOR = 0.7f
    const val TEND_RANDOM_SPREAD = 0.25f
    const val DEFAULT_DOCTOR_SKILL = 2

    fun tendSkillStat(level: Int): Float {
        val c = TEND_SKILL_CURVE
        val l = level.coerceIn(c.first().first, c.last().first)
        for (i in 1 until c.size) {
            val (x1, y1) = c[i]
            if (l <= x1) {
                val (x0, y0) = c[i - 1]
                return y0 + (y1 - y0) * (l - x0) / (x1 - x0).toFloat()
            }
        }
        return c.last().second
    }

    /** [roll] is a uniform 0..1 draw from the game's seeded generator. */
    fun tendQuality(
        skill: Int, manipulation: Float, potency: Float, maxQuality: Float, hospital: Boolean, self: Boolean, roll: Float,
    ): Float {
        var q = tendSkillStat(skill) * (0.6f + 0.4f * manipulation) * potency
        if (hospital) q += HOSPITAL_BED_TEND_OFFSET
        if (self) q *= SELF_TEND_FACTOR
        q *= 1f + (roll * 2f - 1f) * TEND_RANDOM_SPREAD
        return q.coerceIn(0f, maxQuality)
    }
}
