package io.github.teamomuito.colony.sim

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

// Combat constants and the pure rules that use them. Each value and its source is listed in docs/fidelity/combat.md.
// Everything here is deterministic: the only randomness is the seeded sim RNG, passed in as a number in [0, 1).

/** Distance breakpoints in cells for the touch, short, medium and long accuracy bands (Verse/ShootTuning.cs:5-11; Verse/VerbProperties.cs:652). */
internal const val BAND_TOUCH_CELLS = 3f
internal const val BAND_SHORT_CELLS = 12f
internal const val BAND_MEDIUM_CELLS = 25f
internal const val BAND_LONG_CELLS = 40f

/** Equipment accuracy factor clamp: the minimum is Verse/ShootTuning.cs:15, the clamp is Verse/VerbProperties.cs:653. */
internal const val EQUIPMENT_FACTOR_MIN = 0.01f
internal const val EQUIPMENT_FACTOR_MAX = 1f

/** Lowest shooter-and-distance aim factor (Verse/ShootTuning.cs:17; Verse/ShotReport.cs:64-66, 173). */
internal const val MIN_AIM_CHANCE = 0.0201f

/** Target size factor clamp, from the body size (Verse/ShotReport.cs:135). */
internal const val TARGET_SIZE_MIN = 0.5f
internal const val TARGET_SIZE_MAX = 2f

/** A prone target at 4.5 cells or more is hit at half the aim (Verse/ShotReport.cs:38). */
internal const val PRONE_DISTANCE_CELLS = 4.5f
internal const val PRONE_AIM_FACTOR = 0.5f

/** A prone target at 3.9 cells or less is executed: aim x 7.5 (Verse/ShotReport.cs:50). */
internal const val EXECUTION_DISTANCE_CELLS = 3.9f
internal const val EXECUTION_AIM_FACTOR = 7.5f

/** Melee reach in cells: adjacent, diagonals included (Verse/ShootTuning.cs:13). */
internal const val MELEE_REACH = 1.42f

/** Penetration default per point of damage (Verse/ProjectileProperties.cs:181; Verse/VerbProperties.cs:379). */
internal const val PENETRATION_PER_DAMAGE = 0.015f

/** Melee damage varies from 0.8 to 1.2 of the base (RimWorld/Verb_MeleeAttackDamage.cs:22). */
internal const val MELEE_DAMAGE_MIN = 0.8f
internal const val MELEE_DAMAGE_MAX = 1.2f

/** Ticks a melee target is staggered after a swing that leaves it alive (RimWorld/Verb_MeleeAttack.cs:98). */
internal const val STAGGER_TICKS = 95

/** Melee dodge: the attacker's chance is cut by the target's dodge chance, which rises with its close combat skill (C). */
internal const val DODGE_PER_SKILL = 0.04f
internal const val DODGE_MAX = 0.45f

/** Armour: a roll below this share of the rating deflects the hit (Verse/ArmorUtility.cs:11, 75-77). */
internal const val DEFLECT_SHARE = 0.5f

/** Armour: each covering apparel layer takes this share of the damage that reaches it (Verse/ArmorUtility.cs:70). */
internal const val APPAREL_DAMAGE_SHARE = 0.25f

/** Cover: a full-fill building (a wall) blocks this share of shots from the side (Verse/CoverUtility.cs:106). */
internal const val FULL_FILL_BLOCK = 0.75f

/** Cover: a diagonal cover's angle is scaled by this factor (Verse/CoverUtility.cs:61). */
internal const val DIAGONAL_ANGLE_FACTOR = 1.75f

/** Cover: the angle factors for the bands under 15, 27, 40, 52 and 65 degrees (Verse/CoverUtility.cs:63-88). */
internal val COVER_ANGLE_LIMITS = floatArrayOf(15f, 27f, 40f, 52f, 65f)
internal val COVER_ANGLE_FACTORS = floatArrayOf(1f, 0.8f, 0.6f, 0.4f, 0.2f)

/** Cover: a cover cell under 1.9 cells from the shooter keeps this factor (Verse/CoverUtility.cs:89-93). */
internal const val COVER_NEAR_CELLS = 1.9f
internal const val COVER_NEAR_FACTOR = 0.3333f

/** Cover: a cover cell under 2.9 cells from the shooter keeps this factor (Verse/CoverUtility.cs:94-97). */
internal const val COVER_MID_CELLS = 2.9f
internal const val COVER_MID_FACTOR = 0.66666f

/** Burning. Fire.cs and FireUtility.cs in the decompiled source use 150-tick checks. */
internal const val BURN_CHECK_TICKS = 150
internal const val FLAME_CATCH_TICKS = 60
internal const val CELL_CATCH_TICKS = 150
internal const val CELL_CATCH_MIN_SIZE = 0.4f
internal const val CELL_CATCH_SIZE_SHARE = 0.2f
internal const val FLAME_SIZE_MIN = 0.15f
internal const val FLAME_SIZE_MAX = 0.25f
internal const val BURN_DAMAGE_BASE = 0.0125f
internal const val BURN_DAMAGE_PER_SIZE = 0.0036f
internal const val BURN_DAMAGE_MIN = 0.0125f
internal const val BURN_DAMAGE_MAX = 0.05f

/** Burn size lost at each check, and twice that in rain (C; the vanilla dynamics of an attached fire are not read). */
internal const val BURN_DECAY = 0.15f
internal const val BURN_DECAY_RAIN = 0.3f

/** Flammability of a living pawn (UNVERIFIED: the vanilla value is an XML stat). Mechanoids do not burn (C). */
internal const val PAWN_FLAMMABILITY = 1f

/**
 * Accuracy of a ranged weapon at a band: 0 touch, 1 short, 2 medium, 3 long. Touch and short use the stated accuracy; the medium
 * and long bands are lower (C; the vanilla band values are XML).
 */
internal fun Weapon.accuracyAtBand(band: Int): Float = when (band) {
    0, 1 -> accuracy
    2 -> accuracy * 0.9f
    else -> accuracy * 0.7f
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** The equipment accuracy factor at a distance: blended linearly across the breakpoints, then clamped (VerbProperties.cs:650). */
internal fun equipmentFactor(w: Weapon, d: Float): Float {
    val v = when {
        d <= BAND_TOUCH_CELLS -> w.accuracyAtBand(0)
        d <= BAND_SHORT_CELLS -> lerp(w.accuracyAtBand(0), w.accuracyAtBand(1), (d - BAND_TOUCH_CELLS) / (BAND_SHORT_CELLS - BAND_TOUCH_CELLS))
        d <= BAND_MEDIUM_CELLS -> lerp(w.accuracyAtBand(1), w.accuracyAtBand(2), (d - BAND_SHORT_CELLS) / (BAND_MEDIUM_CELLS - BAND_SHORT_CELLS))
        d <= BAND_LONG_CELLS -> lerp(w.accuracyAtBand(2), w.accuracyAtBand(3), (d - BAND_MEDIUM_CELLS) / (BAND_LONG_CELLS - BAND_MEDIUM_CELLS))
        else -> w.accuracyAtBand(3)
    }
    return v.coerceIn(EQUIPMENT_FACTOR_MIN, EQUIPMENT_FACTOR_MAX)
}

/**
 * The shooter's accuracy `f`: the aim falls by a factor of f for every cell (Verse/ShotReport.cs:165-173). The base and the
 * slope per point of marksmanship are C, and the vanilla base is an XML stat (UNVERIFIED).
 */
internal fun shooterAccuracy(marksmanship: Int): Float = min(1f, 0.96f + 0.002f * marksmanship)

/** f raised to the distance in cells (Verse/ShotReport.cs:168), with StrictMath so every platform agrees. */
internal fun shooterFactor(f: Float, d: Float): Float = StrictMath.pow(f.toDouble(), d.toDouble()).toFloat()

/** Target size factor: the body size clamped to 0.5 to 2 (Verse/ShotReport.cs:135). */
internal fun targetSizeFactor(bodySize: Float): Float = bodySize.coerceIn(TARGET_SIZE_MIN, TARGET_SIZE_MAX)

/** Penetration of one projectile or swing: the hand-set value, or damage times 0.015 when the weapon sets none. */
internal fun penetrationOf(w: Weapon): Float {
    if (w.armorPen >= 0f) return w.armorPen
    val perHit = if (w.ranged) w.damage / w.pellets else w.damage
    return perHit * PENETRATION_PER_DAMAGE
}

/**
 * A melee hit under 1 is raised to 1 and becomes blunt (RimWorld/Verb_MeleeAttackDamage.cs:23, 26-37). The rule sits in the
 * branch for a pawn caster, and every Stillmere attacker is a Pawn, animals included (Verse/Verb.cs:125). Returns the damage and
 * the kind it is applied with.
 */
internal fun meleeFloor(damage: Float, kind: DamageKind): Pair<Float, DamageKind> =
    if (damage < 1f) 1f to DamageKind.BRUISE else damage to kind

/**
 * The armour roll for one layer (Verse/ArmorUtility.cs:58-88). [rating] is the layer's rating after penetration, and [u] is a
 * uniform roll in [0, 1). A roll under half the rating deflects the hit. A roll under the rating halves the damage and makes a
 * sharp hit blunt. With no rating there is no roll, and the damage passes unchanged.
 */
internal class ArmorStep(val damage: Float, val halved: Boolean, val deflected: Boolean)

internal fun armorStep(damage: Float, rating: Float, u: Float): ArmorStep {
    if (rating <= 0f) return ArmorStep(damage, halved = false, deflected = false)
    return when {
        u < rating * DEFLECT_SHARE -> ArmorStep(0f, halved = false, deflected = true)
        u < rating -> ArmorStep(damage / 2f, halved = true, deflected = false)
        else -> ArmorStep(damage, halved = false, deflected = false)
    }
}

/** The rating of one worn item against sharp or blunt damage: quality and hit points scale it (kept from Stillmere). */
internal fun Worn.armorRating(sharp: Boolean): Float {
    val a = type.apparel ?: return 0f
    val v = if (sharp) a.armorSharp else a.armorBlunt
    return v * quality.mult * (0.5f + 0.5f * (hp / a.hp).coerceIn(0f, 1f))
}

/** A cover building beside the target and the chance it stops a shot from this shooter. */
internal class CoverBlock(val building: Building, val block: Float)

/** Combined block chance of several covers: one minus the product of the chances that each lets the shot through. */
internal fun overallBlock(covers: List<CoverBlock>): Float {
    var pass = 1f
    for (c in covers) pass *= 1f - c.block
    return 1f - pass
}

/** Base block chance of a building as cover: a wall is full-fill, other buildings use their cover value (Verse/CoverUtility.cs:102-118). */
internal fun coverBase(d: BuildDef): Float = if (d.isWall) FULL_FILL_BLOCK else d.cover

/**
 * Block chance of one of the eight cells around the target (Verse/CoverUtility.cs:57-97). [dx], [dy] is the cover cell from
 * the target; [sx], [sy] is the shooter from the target; [nsx], [nsy] is the shooter from the cover cell. The angle between the
 * cover and the shooter, seen from the target, sets the angle factor. A diagonal cover has its angle scaled up. A cover close to
 * the shooter has its chance cut to 0.3333 (under 1.9 cells) or 0.66666 (under 2.9 cells).
 */
internal fun coverBlockChance(base: Float, dx: Int, dy: Int, sx: Int, sy: Int, nsx: Int, nsy: Int): Float {
    var angle = angleBetween(dx, dy, sx, sy)
    if (abs(dx) + abs(dy) == 2) angle *= DIAGONAL_ANGLE_FACTOR
    var angleFactor = 0f
    for (k in COVER_ANGLE_LIMITS.indices) {
        if (angle < COVER_ANGLE_LIMITS[k]) { angleFactor = COVER_ANGLE_FACTORS[k]; break }
    }
    if (angleFactor <= 0f) return 0f
    val len = sqrt((nsx * nsx + nsy * nsy).toFloat())
    val distFactor = when {
        len < COVER_NEAR_CELLS -> COVER_NEAR_FACTOR
        len < COVER_MID_CELLS -> COVER_MID_FACTOR
        else -> 1f
    }
    return base * angleFactor * distFactor
}

/** The angle in degrees between two grid vectors, from 0 to 180 (StrictMath for the trig). */
internal fun angleBetween(ax: Int, ay: Int, bx: Int, by: Int): Float {
    val a = StrictMath.toDegrees(StrictMath.atan2(ay.toDouble(), ax.toDouble()))
    val b = StrictMath.toDegrees(StrictMath.atan2(by.toDouble(), bx.toDouble()))
    var diff = abs(a - b) % 360.0
    if (diff > 180.0) diff = 360.0 - diff
    return diff.toFloat()
}

/** Chance that a pawn catches fire in one second from its flammability (RimWorld/FireUtility.cs:9-15): 0 at 0, 0.07 at 0.1, 1 at 0.3 and above. */
internal fun catchChancePerSecond(flammability: Float): Float = when {
    flammability <= 0f -> 0f
    flammability <= 0.1f -> 0.07f * flammability / 0.1f
    flammability <= 0.3f -> 0.07f + (1f - 0.07f) * (flammability - 0.1f) / 0.2f
    else -> 1f
}

/** Chance of catching fire within [ticks], from the per-second chance (FireUtility.cs:97-109). */
internal fun cumulativeCatchChance(flammability: Float, ticks: Int): Float {
    val perSecond = catchChancePerSecond(flammability)
    return 1f - StrictMath.pow((1f - perSecond).toDouble(), ticks / 60.0).toFloat()
}

/** Fire damage per burn check for a fire of [size]: clamp(0.0125 + 0.0036 x size, 0.0125, 0.05) x 150 (Fire.cs:348), not rounded. */
internal fun burnDamagePerCheck(size: Float): Float =
    (BURN_DAMAGE_BASE + BURN_DAMAGE_PER_SIZE * size).coerceIn(BURN_DAMAGE_MIN, BURN_DAMAGE_MAX) * BURN_CHECK_TICKS

internal fun Pawn.flammability(): Float = if (race.mech) 0f else PAWN_FLAMMABILITY
