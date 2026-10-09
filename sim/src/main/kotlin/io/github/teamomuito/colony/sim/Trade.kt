package io.github.teamomuito.colony.sim

import kotlin.math.max
import kotlin.math.min

class TraderInfo(val pawnId: Int, val name: String, val arrival: Long, val leaveAt: Long) {
    val stock = Stock()
    var silver = 0
    var wantsKind = ItemCat.RESOURCE
}

fun Game.spawnTrader(from: WorldFaction? = null, orbitalAt: Pair<Int, Int>? = null) {
    if (traders.isNotEmpty()) return
    val fac = from ?: world.factions.filter { it.trades && standing(it) != Standing.HOSTILE }.let { if (it.isEmpty()) null else it[rng.int(it.size)] }
    val e = orbitalAt ?: edgeCell(rng.int(4)) ?: return
    val trader = newHuman(e.first, e.second, Faction.VISITOR)
    trader.name = "Trader " + trader.name.substringBefore(' ')
    trader.wfaction = fac?.id ?: -1
    trader.apparel.clear(); trader.apparel.add(Worn(ItemType.A_DUSTER, Quality.GOOD, 150f)); trader.weaponItem = ItemType.W_REVOLVER
    trader.escapeTick = tick + ((if (orbitalAt != null) 1.5f else 2.0f) * TICKS_PER_DAY).toInt()
    trader.homeTile = map.idx(homeX, homeY)
    val info = TraderInfo(trader.id, trader.name, tick, trader.escapeTick)
    info.silver = rng.range(800, 2600)
    // Stock: bulk goods and a few specialities.
    val goods = listOf(
        ItemType.STEEL to 250, ItemType.WOOD to 300, ItemType.CLOTH to 200, ItemType.LEATHER to 120, ItemType.COMPONENT to 20,
        ItemType.MEAL_SIMPLE to 40, ItemType.MEAL_FINE to 20, ItemType.MEAL_PACKAGED to 30, ItemType.RICE to 150, ItemType.CORN to 150, ItemType.POTATOES to 100,
        ItemType.MEDS_HERBAL to 20, ItemType.MEDS_INDUSTRIAL to 10, ItemType.BEER to 20, ItemType.JOINT to 15, ItemType.PSYCHITE_TEA to 10,
        ItemType.PLASTEEL to 60, ItemType.GOLD to 30, ItemType.KIBBLE to 100, ItemType.HAY to 100,
    )
    for ((t, n) in goods) if (rng.chance(if (orbitalAt != null) 0.8f else 0.55f)) info.stock.add(t, rng.range(n / 4, n) * (if (orbitalAt != null && (t == ItemType.COMPONENT || t == ItemType.PLASTEEL || t == ItemType.GOLD)) 2 else 1))
    if (fac != null && fac.kind == 0) info.stock.removeKinds { it in setOf(ItemType.COMPONENT, ItemType.PLASTEEL, ItemType.MEAL_PACKAGED, ItemType.MEDS_INDUSTRIAL) }
    val gear = ItemType.entries.filter { it.isGear && (fac == null || fac.kind != 0 || it.weapon?.ranged != true || it.weapon == Weapon.BOW || it.weapon == Weapon.GREATBOW) && (fac == null || fac.kind != 0 || (it.apparel != null && it.value < 80f) || it.weapon != null) }
    repeat(rng.range(2, 6)) { info.stock.add(rng.pick(gear), 1) }
    traders.add(info)
    say(if (orbitalAt != null) "An orbital trade ship lands at your beacon! ${trader.name} is waiting." else "A trade caravan ${if (fac != null) "from ${fac.name} " else ""}arrives! ${trader.name} is waiting in your colony.", 1)
    if (rng.chance(0.4f)) {
        val guard = newHuman(e.first, e.second, Faction.VISITOR)
        guard.weaponItem = ItemType.W_AUTOPISTOL; guard.escapeTick = trader.escapeTick; guard.homeTile = trader.homeTile
    }
}

fun Game.tradersHourly() {
    val it = traders.iterator()
    while (it.hasNext()) {
        val t = it.next()
        val p = pawnById(t.pawnId)
        if (p == null || !p.alive) { it.remove(); continue }
        if (tick > t.leaveAt) {
            p.retreating = true
            if (!pawns.contains(p)) it.remove()
            for (v in pawns) if (v.faction == Faction.VISITOR) v.retreating = true
            if (tick > t.leaveAt + 2000) { traders.remove(t); break }
        }
    }
    if (traders.isEmpty()) for (v in pawns.toList()) if (v.faction == Faction.VISITOR && v.alive && !v.retreating && tick > v.escapeTick) v.retreating = true
}

fun Game.sellPrice(t: ItemType, q: Quality = Quality.NORMAL): Float {
    val social = colonists.maxOfOrNull { it.level(SkillType.SOCIAL) } ?: 0
    return t.value * (if (t.isGear) q.mult else 1f) * (0.5f + social * 0.01f)
}

fun Game.buyPrice(t: ItemType): Float {
    val social = colonists.maxOfOrNull { it.level(SkillType.SOCIAL) } ?: 0
    return t.value * (1.35f - social * 0.01f)
}

fun Game.trader(): TraderInfo? = traders.firstOrNull { pawnById(it.pawnId)?.alive == true }

/**
 * Sell stacks lying in stockpiles within the colony (not forbidden); returns silver earned. Each stack keeps its quality
 * and condition in the trader's stock, and its price depends on the quality.
 */
fun Game.sellItem(t: TraderInfo, type: ItemType, count: Int): Int {
    if (count <= 0 || type == ItemType.SILVER) return 0
    var left = count
    var earned = 0f
    val stacks = sellableStacks().filter { it.value.type == type }
    for ((i, s) in stacks) {
        if (left <= 0) break
        val n = min(left, s.count)
        val price = sellPrice(type, s.quality)
        if (t.silver < price * n) { /* trader cannot afford the rest */ }
        val maxN = min(n, (t.silver / max(0.01f, price)).toInt())
        if (maxN <= 0) break
        val lot = s.lot()
        map.take(i, maxN)
        earned += price * maxN
        t.silver -= (price * maxN).toInt()
        t.stock.add(lot, maxN)
        left -= maxN
    }
    val e = earned.toInt()
    if (e > 0) {
        val p = pawnById(t.pawnId)
        map.drop(ItemType.SILVER, e, p?.x ?: homeX, p?.y ?: homeY)
        silverEarned += e
    }
    return e
}

/** Buys up to [count] of [type] from the trader. The goods arrive with the quality and condition they were stocked with. */
fun Game.buyItem(t: TraderInfo, type: ItemType, count: Int): Boolean {
    val picks = t.stock.peek(type, count)
    val n = picks.sumOf { it.second }
    if (n <= 0) return false
    val cost = picks.sumOf { (buyPrice(type) * it.first.valueFactor() * it.second).toDouble() }.toInt() + 1
    // Pay silver from stockpiles.
    var silver = 0
    for ((_, s) in map.items) if (s.type == ItemType.SILVER) silver += s.count
    if (silver < cost) return false
    var left = cost
    for (e in map.items.entries.filter { it.value.type == ItemType.SILVER }) {
        if (left <= 0) break
        left -= map.take(e.key, left)
    }
    val p = pawnById(t.pawnId)
    for ((lot, m) in picks) {
        t.stock.remove(lot, m)
        map.drop(lot, m, p?.x ?: homeX, p?.y ?: homeY)
    }
    t.silver += cost
    return true
}
