package io.github.teamomuito.colony.sim

// Burning pawns. A pawn catches fire from a flame hit that is not deflected, or by standing in a large enough fire. A burning
// pawn stops its job, takes fire damage every 150 ticks, and its burn size wears off. Constants and sources are in Combat.kt and
// docs/fidelity/combat.md. The burn size is saved (version 36).

/** A flame hit that is not deflected may set the pawn alight, at the cumulative chance over 60 ticks (DamageWorker_Flame.cs:21-24). */
internal fun Game.flameIgnition(p: Pawn) {
    if (p.dead || p.burnSize > 0f) return
    if (!rng.chance(cumulativeCatchChance(p.flammability(), FLAME_CATCH_TICKS))) return
    catchFire(p, FLAME_SIZE_MIN + (FLAME_SIZE_MAX - FLAME_SIZE_MIN) * rng.float())
}

/**
 * Sets the pawn alight with a burn of [size]. The job stops, as the reference does when a fire attaches to a pawn
 * (RimWorld/FireUtility.cs:111-126). A pawn already alight keeps its fire.
 */
internal fun Game.catchFire(p: Pawn, size: Float) {
    if (p.dead || p.burnSize > 0f) return
    p.burnSize = size
    endJob(p)
    if (p.colonist) say("${p.name} caught fire!", 2)
}

/** One 150-tick burn check: fire damage on a random body part and on one random worn item, then the burn size wears off. */
internal fun Game.burnTick(p: Pawn) {
    if (p.dead || p.burnSize <= 0f) return
    val dmg = burnDamagePerCheck(p.burnSize)
    // Fire damage is not reduced by armour here (kept as a Stillmere choice; the vanilla armour category of fire is XML).
    applyWound(p, pickOuterPart(p), DamageKind.BURN, dmg, null)
    if (p.apparel.isNotEmpty()) p.apparel[rng.int(p.apparel.size)].hp -= dmg
    p.apparel.removeAll { it.hp <= 0f }
    p.healthDirty = true
    checkDeath(p, null)
    if (p.dead) return
    val decay = if (weather == Weather.RAIN || weather == Weather.THUNDER) BURN_DECAY_RAIN else BURN_DECAY
    p.burnSize = (p.burnSize - decay).coerceAtLeast(0f)
}
