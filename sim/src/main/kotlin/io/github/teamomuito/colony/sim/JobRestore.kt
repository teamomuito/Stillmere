package io.github.teamomuito.colony.sim

/**
 * What a load puts back on top of the saved pawns: the reservations, and the jobs that depend on them.
 *
 * Reservations are rebuilt from each pawn's own list, in pawn order, so the claim table is always the one the pawns
 * agree on. A claim that names a missing pawn or a cell off the map is dropped. A claim that conflicts with an
 * earlier pawn's claim is dropped too, and that pawn's job is ended. Every job is then checked against the map and
 * the pawns that still exist; an invalid one is ended, which puts any load it carried back on the ground. Ending a
 * job frees everything it held, so no pawn is left waiting on a claim that cannot be honoured.
 */
internal fun Game.restoreJobState() {
    val byId = pawns.associateBy { it.id }
    for (p in pawns) {
        if (p.carriedBy >= 0 && byId[p.carriedBy] == null) p.carriedBy = -1
        if (p.carrying >= 0 && byId[p.carrying] == null) p.carrying = -1
    }

    reservations.clear()
    val conflicted = HashSet<Int>()
    for (p in pawns.sortedBy { it.id }) {
        val keys = ArrayList(p.reserved)
        p.reserved.clear()
        for (k in keys) {
            if (!validClaim(k, byId)) continue
            if (reserve(p, k)) continue
            conflicted += p.id
        }
    }

    // A hospital bed is held by the patient whose job holds it. A bed whose patient has no job any more is free.
    for (b in map.buildings()) if (b.def.medical && b.occupant >= 0 && byId[b.occupant]?.job == null) b.occupant = -1

    for (p in pawns) {
        val j = p.job
        val bad = j != null && !jobIsValid(j, byId)
        val path = p.path
        if (path != null && (p.pathI < 0 || p.pathI > path.size || path.any { !map.inB(map.xOf(it), map.yOf(it)) })) p.clearPath()
        if (bad || p.id in conflicted) endJob(p)
    }
}

/** Whether a claim key still names something: a live pawn's id, or a cell on the map. */
private fun Game.validClaim(k: Int, byId: Map<Int, Pawn>): Boolean {
    if (k < 0) return false
    if (k >= PAWN_KEY_BASE) {
        val id = (k - PAWN_KEY_BASE) / KEY_KINDS
        return byId[id]?.alive == true
    }
    return k / KEY_KINDS < map.size
}

private fun Game.jobIsValid(j: Job, byId: Map<Int, Pawn>): Boolean {
    if (j.targetPawn >= 0 && byId[j.targetPawn]?.alive != true) return false
    for ((x, y) in listOf(j.tx to j.ty, j.dx to j.dy)) {
        if (x >= 0 && (y < 0 || !map.inB(x, y))) return false
    }
    if (j.key in 0 until PAWN_KEY_BASE && j.key / KEY_KINDS >= map.size) return false
    if (j.key >= PAWN_KEY_BASE && byId[(j.key - PAWN_KEY_BASE) / KEY_KINDS] == null) return false
    return true
}
