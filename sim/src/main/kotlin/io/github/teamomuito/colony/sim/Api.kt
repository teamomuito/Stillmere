package io.github.teamomuito.colony.sim

/** Player-facing actions that don't belong to the simulation loop. */

fun Game.assignBed(bed: Building, p: Pawn?) {
    if (!bed.def.sleeps) return
    // Free the old owner's link.
    val old = pawnById(bed.ownerId)
    if (old != null && old.bedId == map.idx(bed.x, bed.y)) old.bedId = -1
    bed.ownerId = p?.id ?: -1
    if (p != null) {
        if (p.bedId >= 0) map.building[p.bedId]?.let { if (it.ownerId == p.id) it.ownerId = -1 }
        p.bedId = map.idx(bed.x, bed.y)
    }
}

fun Game.dropWeapon(p: Pawn) {
    val w = p.weaponItem ?: return
    map.drop(w, 1, p.x, p.y, p.weaponQuality, forbid = true)
    p.weaponItem = null
}

fun Game.stripApparel(p: Pawn, w: Worn) {
    if (p.apparel.remove(w)) map.drop(w.type, 1, p.x, p.y, w.quality, forbid = true)
}

fun Game.releasePrisoner(p: Pawn) {
    if (!p.prisoner) return
    p.prisoner = false
    p.faction = Faction.VISITOR
    p.retreating = true
    p.escapeTick = tick
    p.homeTile = -1
    if (p.wfaction >= 0) world.factions.getOrNull(p.wfaction)?.let { adjustGoodwill(it, 12) }
    endJob(p)
    say("${p.name} was released.", 0)
}

fun Game.setPrisonerBed(bed: Building, v: Boolean) {
    if (!bed.def.sleeps) return
    bed.prisonerBed = v
    if (v) { bed.ownerId = -1; for (p in pawns) if (p.bedId == map.idx(bed.x, bed.y)) p.bedId = -1 }
}

fun Game.marksFor(kind: String, p: Pawn, v: Boolean) {
    when (kind) {
        "hunt" -> { p.huntMark = v; if (v) { p.tameMark = false; p.slaughterMark = false } }
        "tame" -> { p.tameMark = v; if (v) { p.huntMark = false } }
        "slaughter" -> { p.slaughterMark = v }
    }
}

fun Game.totalFoodNutrition(): Float {
    var n = 0f
    for (s in map.items.values) if (s.type.isFood && s.type.humanFood && s.type != ItemType.HAY && s.corpseOf == null && s.rot < 0.8f) n += s.type.nutrition * s.count
    return n
}

fun Game.bedCount(): Int = map.buildings().count { it != null && it.built && it.def.sleeps && !it.prisonerBed }

fun Game.categoryCount(cat: ItemCat): Int = map.countItems { it.cat == cat }

fun Game.skillLabel(level: Int): String = when {
    level <= 2 -> "Poor"; level <= 5 -> "Fair"; level <= 8 -> "Capable"; level <= 12 -> "Skilled"; level <= 16 -> "Expert"; else -> "Master"
}
