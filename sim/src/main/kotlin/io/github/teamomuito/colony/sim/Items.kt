package io.github.teamomuito.colony.sim

import kotlin.math.min
import kotlin.math.roundToInt

/*
 * How goods are represented, and why there are several representations.
 *
 *  - [ItemType] is the definition: label, category, value, stack size, nutrition, weapon or apparel data. Every kind of
 *    item (resources, food, medicine, drugs, weapons, apparel, art, corpses, shells) is one entry here.
 *  - [ItemStack] is goods lying on the map: one pile per cell, with count, quality, hit points, rot, forbidden state and
 *    corpse details. Buildings and plants are separate objects (see [Building] and [Plant]); a building is not a stack.
 *  - [Worn] is apparel on a pawn, and [Pawn.weaponItem] with [Pawn.weaponQuality] is the weapon in hand. Both keep the
 *    same quality and condition as the stack they came from.
 *  - [Lot] and [Stock] hold goods that are off the map: caravan cargo, trader stock and settlement stock. A lot records
 *    the quality and condition as well as the kind, so moving goods does not change them.
 *
 * Keeping one class for all of these would mix map positions, pawn gear and trade bookkeeping. Instead each keeps only
 * what it needs, and [Lot] is the one shape they all convert to when they change hands.
 */

/**
 * One kind of goods in one state. [condition] is the percentage of full hit points: 100 is new, and worn apparel or
 * weathered goods are lower. Goods that differ in quality or condition are different lots.
 */
data class Lot(val type: ItemType, val quality: Quality = Quality.NORMAL, val condition: Int = 100)

/** Condition as a percentage, from a fraction of full hit points. */
internal fun conditionPercent(fraction: Float): Int = (fraction * 100f).roundToInt().coerceIn(1, 100)

fun ItemStack.lot(): Lot = Lot(type, quality, conditionPercent(hp))

fun Worn.lot(): Lot = Lot(type, quality, conditionPercent(hp / (type.apparel?.hp ?: 1f)))

/** How much quality scales the value of this lot. Only gear has quality that counts. */
fun Lot.valueFactor(): Float = if (type.isGear) quality.mult else 1f

/** The most a lot of [this] kind can be worth per unit, relative to its base value. */
fun ItemType.maxValueFactor(): Float = if (isGear) Quality.LEGENDARY.mult else 1f

/**
 * Goods held off the map, grouped by lot. Counts are kept per lot, so a caravan can carry good and poor rifles at once
 * and each keeps its own quality when it is sold, traded or unloaded.
 */
class Stock {
    private val lots = LinkedHashMap<Lot, Int>()

    fun isEmpty() = lots.isEmpty()

    /** Total of [t] in every quality and condition. */
    fun count(t: ItemType): Int {
        var n = 0
        for ((lot, k) in lots) if (lot.type == t) n += k
        return n
    }

    fun count(lot: Lot): Int = lots[lot] ?: 0

    fun add(lot: Lot, n: Int) {
        if (n > 0) lots[lot] = (lots[lot] ?: 0) + n
    }

    fun add(t: ItemType, n: Int) = add(Lot(t), n)

    /** Removes up to [n] of exactly [lot]. Returns how many were removed. */
    fun remove(lot: Lot, n: Int): Int {
        val have = lots[lot] ?: return 0
        val k = min(n, have)
        if (k <= 0) return 0
        if (have == k) lots.remove(lot) else lots[lot] = have - k
        return k
    }

    /** What [take] would remove for [n] of [t], without removing it. */
    fun peek(t: ItemType, n: Int): List<Pair<Lot, Int>> {
        val out = ArrayList<Pair<Lot, Int>>()
        var left = n
        for ((lot, have) in lots) {
            if (left <= 0) break
            if (lot.type != t) continue
            val k = min(left, have)
            out += lot to k
            left -= k
        }
        return out
    }

    /** Takes up to [n] of [t], from whichever lots hold it, in the order they were added. */
    fun take(t: ItemType, n: Int): List<Pair<Lot, Int>> = peek(t, n).also { for ((lot, k) in it) remove(lot, k) }

    /** Drops every lot whose kind matches [pred]. */
    fun removeKinds(pred: (ItemType) -> Boolean) { lots.keys.removeAll { pred(it.type) } }

    /** Every lot with its count, in the order they were added. */
    fun entries(): List<Pair<Lot, Int>> = lots.map { it.key to it.value }

    /** Totals per kind, ignoring quality and condition: for listings and for checks that do not care which copy. */
    fun totals(): Map<ItemType, Int> {
        val out = LinkedHashMap<ItemType, Int>()
        for ((lot, k) in lots) out[lot.type] = (out[lot.type] ?: 0) + k
        return out
    }

    fun clear() = lots.clear()

    fun addAll(other: Stock) { for ((lot, n) in other.entries()) add(lot, n) }

    fun copy(): Stock = Stock().also { c -> for ((lot, k) in lots) c.lots[lot] = k }

    override fun equals(other: Any?): Boolean = other is Stock && other.lots == lots
    override fun hashCode(): Int = lots.hashCode()
    override fun toString(): String = lots.entries.joinToString { "${it.key.type.name}/${it.key.quality}/${it.key.condition}% x${it.value}" }
}
