package io.github.teamomuito.colony.sim

/**
 * The one place that decides who may work on what. A job claims the things it will touch (an item to pick up, a
 * bed, a plant, a patient, a stockpile cell) before it starts, and every job ends through [releaseAll].
 *
 * Keys name a thing: a cell as `index * KEY_KINDS + kind`, or a pawn as `PAWN_KEY_BASE + id * KEY_KINDS + kind`.
 * The kind is the low bits of the key, so the rules below can be read from the key alone.
 *
 * A pawn holds a key only while it also lists that key in [Pawn.reserved]. Anything that drops a pawn's list
 * without going through [releaseAll] leaves a stale entry, which [pruneReservations] removes.
 */
internal const val KEY_KINDS = 16

/** How many pawns may hold a stockpile drop-off cell at once: stacks of the same item merge there. */
private const val SHARED_DROP_CAPACITY = 16

/** How many pawns may hold a key at once. Only drop-off cells are shared; everything else is worked alone. */
internal fun capacityOf(key: Int): Int = if (kindOf(key) == K_DEST) SHARED_DROP_CAPACITY else 1

internal fun kindOf(key: Int): Int = key and (KEY_KINDS - 1)

/** Live pawns that currently hold [key]. */
internal fun Game.holdersOf(key: Int): List<Pawn> {
    val ids = reservations[key] ?: return emptyList()
    return pawns.filter { it.id in ids && it.alive && it.reserved.contains(key) }
}

/** Whether [p] could claim [key] now: it already holds it, or the key has room for one more holder. */
fun Game.isFree(p: Pawn, key: Int): Boolean {
    if (reservations[key] == null) return true
    if (p.reserved.contains(key) && reservations[key]?.contains(p.id) == true) return true
    return holdersOf(key).count { it.id != p.id } < capacityOf(key)
}

/** Claims [key] for [p]. Returns false, claiming nothing, when the key is full. */
fun Game.reserve(p: Pawn, key: Int): Boolean {
    if (!isFree(p, key)) return false
    reservations.getOrPut(key) { LinkedHashSet() }.add(p.id)
    if (!p.reserved.contains(key)) p.reserved.add(key)
    return true
}

/** Claims every key or none of them, so a job never starts holding half of what it needs. */
fun Game.reserveAll(p: Pawn, vararg keys: Int): Boolean {
    if (keys.any { !isFree(p, it) }) return false
    for (k in keys) reserve(p, k)
    return true
}

/** Gives up one key. */
internal fun Game.unreserve(p: Pawn, key: Int) {
    dropHolder(key, p.id)
    p.reserved.remove(key)
}

/** Gives up everything [p] holds. Every job end and every death or removal goes through here. */
fun Game.releaseAll(p: Pawn) {
    for (k in p.reserved) dropHolder(k, p.id)
    p.reserved.clear()
}

private fun Game.dropHolder(key: Int, id: Int) {
    val set = reservations[key] ?: return
    set.remove(id)
    if (set.isEmpty()) reservations.remove(key)
}

/**
 * Removes holders that are dead, no longer in the colony, or no longer list the key. Runs on the slow tick, so a
 * path that forgot to release cannot block a key for longer than a few hundred ticks.
 */
internal fun Game.pruneReservations() {
    if (reservations.isEmpty()) return
    val byId = HashMap<Int, Pawn>(pawns.size * 2)
    for (p in pawns) byId[p.id] = p
    val it = reservations.entries.iterator()
    while (it.hasNext()) {
        val (key, ids) = it.next()
        ids.removeAll { id -> byId[id]?.let { p -> p.alive && p.reserved.contains(key) } != true }
        if (ids.isEmpty()) it.remove()
    }
}
