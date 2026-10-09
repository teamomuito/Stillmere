package io.github.teamomuito.colony.sim

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class Caravan(val id: Int, var name: String, var tile: Int) {
    val members = ArrayList<Pawn>()
    /** Cargo, by lot, so quality and condition travel with the goods. */
    val inventory = Stock()
    val route = ArrayList<Int>()
    var destination = tile
    var progress = 0f
    var resting = false
    var goingHome = false
    var lastEvent = ""
    /** A fight is under way on a battle map: the people are over there, the cargo is still here. */
    var inBattle = false
    var forageNote = false
    val humans get() = members.filter { it.alive && !it.isAnimal }
    val alive get() = members.filter { it.alive }
}

// ---------------------------------------------------------------------- mass

fun ItemType.mass(): Float = when {
    this == ItemType.SILVER || this == ItemType.GOLD -> 0.008f
    this == ItemType.STONE_CHUNK -> 25f
    this == ItemType.COMPONENT -> 0.6f
    this == ItemType.STEEL || this == ItemType.PLASTEEL || this == ItemType.WOOD || this == ItemType.STONE -> 0.5f
    this == ItemType.PEMMICAN -> 0.04f
    cat == ItemCat.FOOD_MEAL -> 0.44f
    cat == ItemCat.FOOD_PLANT -> 0.03f
    cat == ItemCat.FOOD_MEAT -> 0.05f
    cat == ItemCat.FOOD_ANIMAL -> 0.02f
    cat == ItemCat.MEDICINE || cat == ItemCat.DRUG -> 0.2f
    weapon != null -> 3.5f
    apparel != null -> 2f
    cat == ItemCat.ART -> 12f
    this == ItemType.CORPSE_HUMAN -> 60f
    else -> 0.5f
}

/** How much a pawn can carry on the world map; animals only count when tame and big enough. */
fun Pawn.carryCapacity(): Float = when {
    !alive -> 0f
    race == Race.HUMAN -> 35f
    isAnimal && faction == Faction.PLAYER -> when (race) {
        Race.MUFFALO, Race.COW, Race.DROMEDARY -> 70f; Race.HORSE -> 80f; Race.DONKEY -> 55f; Race.ELEPHANT, Race.THRUMBO -> 140f; Race.RHINO -> 90f; Race.BISON -> 60f
        Race.DEER, Race.ELK, Race.CARIBOU -> 40f; Race.ALPACA, Race.GOAT, Race.PIG, Race.OSTRICH -> 30f; Race.HUSKY, Race.LABRADOR -> 25f; else -> 0f
    }
    else -> 0f
}

fun Caravan.capacity(): Float = members.filter { it.alive && !it.downed }.sumOf { it.carryCapacity().toDouble() }.toFloat()
fun Caravan.load(): Float = inventory.entries().sumOf { (it.first.type.mass() * it.second).toDouble() }.toFloat()
fun Caravan.speedFactor(): Float {
    val cap = capacity()
    if (cap <= 0f) return 0.6f
    val ratio = (load() / cap).coerceIn(0f, 1.2f)
    val slowest = alive.minOfOrNull { 1f / max(0.4f, it.race.moveTicks / 11f) } ?: 1f
    return (1f - 0.4f * ratio) * slowest.coerceIn(0.5f, 1f)
}

fun Caravan.ticksForNextTile(w: World): Float {
    val next = route.firstOrNull() ?: return 1f
    return World.TICKS_PER_COST * (w.cost(tile) + w.cost(next)) * 0.5f / max(0.1f, speedFactor())
}

fun Game.caravanDaysLeft(c: Caravan): Float {
    if (c.route.isEmpty()) return 0f
    val ticks = world.routeCost(c.tile, c.route) * World.TICKS_PER_COST / max(0.1f, c.speedFactor()) - c.progress
    return max(0f, ticks) / TICKS_PER_DAY
}

fun Game.routeDays(from: Int, to: Int, speed: Float = 0.85f): Float? {
    val p = world.path(from, to) ?: return null
    return world.routeCost(from, p) * World.TICKS_PER_COST / speed / TICKS_PER_DAY
}

fun Caravan.foodDays(): Float {
    var nut = 0f
    for ((lot, n) in inventory.entries()) if (lot.type.isFood && lot.type.humanFood) nut += lot.type.nutrition * n
    val humans = max(1, humans.size)
    return nut / (0.7f * humans)
}

// ---------------------------------------------------------------------- forming

/** Stockpiled goods the colony could pack. */
fun Game.packableItems(): Map<ItemType, Int> {
    val m = LinkedHashMap<ItemType, Int>()
    for ((i, s) in map.items) if (s.corpseOf == null && !s.forbidden && map.zoneKind(i) != ZoneKind.NONE) m[s.type] = (m[s.type] ?: 0) + s.count
    return m.entries.sortedBy { it.key.cat.ordinal }.associate { it.key to it.value }
}

fun Game.canJoinCaravan(p: Pawn): Boolean = p.alive && !p.downed && !p.prisoner && p.faction == Faction.PLAYER && p.carriedBy < 0 &&
    (p.colonist || (p.isAnimal && p.carryCapacity() > 0f))

fun Game.formCaravan(members: List<Pawn>, items: Map<ItemType, Int>, dest: Int, name: String = ""): String? {
    val humans = members.filter { it.colonist }
    if (humans.isEmpty()) return "A caravan needs at least one colonist."
    if (colonists.size - humans.size < 1) return "At least one colonist must stay behind."
    if (members.any { !canJoinCaravan(it) }) return "Someone can't travel (downed or busy)."
    val route = world.path(world.homeTile, dest) ?: return "No route to that tile."
    if (route.isEmpty()) return "Choose a destination."
    val cap = members.sumOf { it.carryCapacity().toDouble() }.toFloat()
    val mass = items.entries.sumOf { (it.key.mass() * it.value).toDouble() }.toFloat()
    if (mass > cap + 0.01f) return "Too heavy: ${"%.0f".format(mass)} kg of ${"%.0f".format(cap)} kg."
    // Make sure the goods actually exist.
    val have = packableItems()
    for ((t, n) in items) if (n > (have[t] ?: 0)) return "Not enough ${t.label.lowercase()}."

    val c = Caravan(nextCaravanId++, name.ifBlank { "Caravan ${nextCaravanId - 1}" }, world.homeTile)
    for ((t, n) in items) {
        if (n <= 0) continue
        var left = n
        for (e in map.items.entries.filter { it.value.type == t && it.value.corpseOf == null && !it.value.forbidden && map.zoneKind(it.key) != ZoneKind.NONE }) {
            if (left <= 0) break
            val lot = e.value.lot()
            val took = map.take(e.key, left)
            c.inventory.add(lot, took)
            left -= took
        }
    }
    for (p in members) {
        if (p.carrying >= 0) { pawnById(p.carrying)?.carriedBy = -1; p.carrying = -1 }
        p.drafted = false
        endJob(p); releaseAll(p)
        p.clearPath()
        pawns.remove(p)
        c.members.add(p)
    }
    c.route.addAll(route); c.destination = dest
    caravans.add(c)
    say("${c.name} set out for ${tileName(dest)}.", 1)
    return null
}

fun Game.tileName(t: Int): String {
    world.settlementAt(t)?.let { return it.name }
    if (t == world.homeTile) return colonyName
    return "${world.biome[t].label.lowercase()} (${world.x(t)},${world.y(t)})"
}

fun Game.orderCaravan(c: Caravan, dest: Int): String? {
    val route = world.path(c.tile, dest) ?: return "No route to that tile."
    if (route.isEmpty()) return "Already there."
    c.route.clear(); c.route.addAll(route); c.destination = dest; c.progress = 0f
    c.goingHome = dest == world.homeTile
    return null
}

fun Game.disbandCaravan(c: Caravan) {
    // Whatever is left stays out in the world; members come home only from the home tile.
    caravans.remove(c)
    say("${c.name} was abandoned.", 2)
}

/** Members walk back onto the map and the cargo is unloaded at the edge. */
fun Game.unloadCaravan(c: Caravan) {
    val e = edgeCell(rng.int(4)) ?: (homeX to homeY)
    for (p in c.members.filter { it.alive }) {
        p.x = e.first; p.y = e.second; p.fromX = p.x; p.fromY = p.y; p.moveCd = 0
        p.clearPath(); p.job = null; p.carriedBy = -1; p.carrying = -1
        pawns.add(p)
    }
    for ((lot, n) in c.inventory.entries()) map.drop(lot, n, e.first, e.second)
    caravans.remove(c)
    say("${c.name} arrived home with ${c.alive.size} travellers.", 1)
}

// ---------------------------------------------------------------------- simulation

internal fun Game.caravansTick() {
    for (c in caravans.toList()) {
        if (pendingBattle != null) return
        if (!c.inBattle) caravanStep(c, 250)
    }
}

private fun Game.caravanStep(c: Caravan, dt: Int) {
    val days = dt.toFloat() / TICKS_PER_DAY
    val here = c.tile
    // Needs: food, rest, health.
    for (p in c.members.toList()) {
        if (!p.alive) continue
        val rate = if (p.isAnimal) 0.55f * Math.pow(p.race.size.toDouble(), 0.5).toFloat() * 0.8f else 0.7f
        p.food = max(0f, p.food - rate * days)
        if (!p.isAnimal) p.rest = if (c.resting) min(1f, p.rest + 2.4f * days) else max(0f, p.rest - 0.95f * days)
        if (p.food < 0.35f) caravanEat(c, p)
        if (p.food <= 0f) {
            val h = addHediff(p, HediffKind.MALNUTRITION, 0f)
            h.severity = min(1f, h.severity + 0.28f * days)
            p.healthDirty = true
            if (h.severity >= 1f) die(p, "starvation")
        }
        if (p.alive && (p.injuries.isNotEmpty() || p.hediffs.isNotEmpty())) {
            caravanTend(c, p)
            healthTick(p, dt)
        }
    }
    c.members.removeAll { it.dead }
    if (c.members.none { it.alive }) { caravans.remove(c); say("${c.name} was lost.", 3); return }
    // Animals cannot lead a caravan. With no person left, the caravan breaks up rather than waiting forever.
    if (c.humans.isEmpty()) { caravans.remove(c); say("${c.name} has no one left to lead it. The animals scatter and the cargo is lost.", 3); return }
    // People who were downed in a fight get back up as they heal, here as they do on the colony map.
    for (h in c.humans) if (h.downed) maybeStandUp(h)
    if (c.humans.none { !it.downed }) {
        // Nobody can walk; wait for wounds to heal.
        c.resting = true
    }
    // Perishables.
    val coldMult = if (world.biome[here] == Biome.TUNDRA || world.biome[here] == Biome.BOREAL) 0.4f else 1f
    for ((lot, n) in c.inventory.entries()) {
        val t = lot.type
        if (t.spoilDays <= 0f || t.cat == ItemCat.FOOD_ANIMAL && t.spoilDays < 0f) continue
        val loss = n * days / t.spoilDays * coldMult
        var lost = loss.toInt()
        if (rng.float() < loss - lost) lost++
        if (lost > 0) c.inventory.remove(lot, lost)
    }

    // Rest when tired.
    val hum = c.humans
    if (!c.resting && hum.isNotEmpty() && hum.any { it.rest < 0.12f }) c.resting = true
    else if (c.resting && hum.isNotEmpty() && hum.all { it.rest > 0.8f || it.downed } && hum.any { !it.downed }) c.resting = false
    if (c.resting || c.route.isEmpty()) return

    c.progress += dt
    while (c.route.isNotEmpty() && c.progress >= c.ticksForNextTile(world)) {
        c.progress -= c.ticksForNextTile(world)
        c.tile = c.route.removeAt(0)
        if (!enterTile(c)) return
    }
    if (c.route.isEmpty()) { c.progress = 0f; arrive(c) }
}

private fun Game.caravanEat(c: Caravan, p: Pawn) {
    if (p.isAnimal) {
        val feed = listOf(ItemType.HAY, ItemType.KIBBLE).firstOrNull { c.inventory.count(it) > 0 && (p.race.diet != Diet.CARNIVORE || it == ItemType.KIBBLE) }
        if (feed != null) {
            c.inventory.take(feed, 1); p.food = min(1f, p.food + feed.nutrition * 10f + 0.2f)
        } else if (p.race.diet != Diet.CARNIVORE && world.forageChance(c.tile) > 0.25f) p.food = min(1f, p.food + 0.35f)
        else if (p.race.diet == Diet.CARNIVORE) {
            val meat = c.inventory.entries().firstOrNull { it.first.type.cat == ItemCat.FOOD_MEAT && it.second > 0 }
            if (meat != null) { c.inventory.take(meat.first.type, 1); p.food = min(1f, p.food + 0.4f) }
        }
        return
    }
    // Packaged and cooked food first, raw last.
    val order = listOf(ItemType.MEAL_PACKAGED, ItemType.PEMMICAN, ItemType.MEAL_FINE, ItemType.MEAL_SIMPLE)
    var pick: ItemType? = order.firstOrNull { c.inventory.count(it) > 0 }
    if (pick == null) pick = c.inventory.entries().firstOrNull { it.second > 0 && it.first.type.isFood && it.first.type.humanFood && it.first.type != ItemType.HUMAN_MEAT && it.first.type != ItemType.MILK }?.first?.type
    if (pick != null) {
        c.inventory.take(pick, 1)
        p.food = min(1f, p.food + pick.nutrition * (if (pick.cat == ItemCat.FOOD_MEAL || pick == ItemType.PEMMICAN) 1f else 0.9f))
        return
    }
    // Forage. Humans with Plants skill find more.
    val chance = world.forageChance(c.tile) * (0.7f + 0.04f * c.humans.maxOf { it.level(SkillType.PLANTS) })
    if (rng.chance(min(0.95f, chance))) {
        p.food = min(1f, p.food + 0.45f)
        if (!c.forageNote) { c.forageNote = true; say("${c.name} is out of food and foraging.", 2) }
        if (rng.chance(0.02f)) { addHediff(p, HediffKind.FOOD_POISONING, 0.3f); say("${p.name} got food poisoning from foraged food.", 2) }
    }
}

private fun Game.caravanTend(c: Caravan, patient: Pawn) {
    val needs = patient.injuries.any { !it.tended && !it.scar && !(it.missing && it.bleed <= 0f) } || patient.hediffs.any { it.kind.needsTend && !it.tended }
    if (!needs) return
    val doctor = c.humans.filter { !it.downed }.maxByOrNull { it.level(SkillType.MEDICINE) } ?: return
    val med = listOf(ItemType.MEDS_INDUSTRIAL, ItemType.MEDS_HERBAL, ItemType.HEALROOT).firstOrNull { c.inventory.count(it) > 0 }
    if (med != null) c.inventory.take(med, 1)
    tendPawn(doctor, patient, med)
}

/** Returns false if the caravan stops (an encounter blocked it, or it arrived). */
private fun Game.enterTile(c: Caravan): Boolean {
    val s = world.settlementAt(c.tile)
    if (s != null && c.route.isNotEmpty()) {
        // Passing through a settlement: stop only if it is the destination.
    }
    if (c.tile == c.destination) return true
    val hostileZone = world.settlements.any { it.faction.permanentEnemy && abs(world.x(it.tile) - world.x(c.tile)) + abs(world.y(it.tile) - world.y(c.tile)) <= 2 }
    val chance = 0.035f + (if (hostileZone) 0.05f else 0f)
    if (!rng.chance(chance)) return true
    val roll = rng.float()
    return when {
        roll < 0.42f -> { animalAmbush(c); false }
        roll < 0.72f -> { banditAmbush(c); false }
        roll < 0.86f -> { findCache(c); true }
        else -> { lostInTerrain(c); true }
    }
}

/** Threat points for a random ambush, scaled by how rich and well-defended the caravan is. */
private fun Game.ambushPoints(c: Caravan, base: Float): Float {
    val cargo = c.inventory.entries().sumOf { (it.first.type.value * it.second).toDouble() }.toFloat()
    var pts = base + c.humans.size * 13f + cargo / 80f + c.alive.count { it.isAnimal } * 3f
    pts *= difficulty.threat.coerceAtLeast(0.5f) * min(1f, 0.45f + day / 30f)
    return pts * (0.8f + rng.float() * 0.45f)
}

private fun Game.animalAmbush(c: Caravan) {
    startFight(c, "manhunting animals", 1, ambushPoints(c, 30f), false, BattleAftermath.AMBUSH)
}

private fun Game.banditAmbush(c: Caravan) {
    val f = world.factions.filter { it.kind == 2 }.let { it[rng.int(it.size)] }
    startFight(c, "${f.name} bandits", 0, ambushPoints(c, 38f), false, BattleAftermath.AMBUSH, enemyFaction = f.id)
}

private fun Game.findCache(c: Caravan) {
    val roll = rng.int(3)
    when (roll) {
        0 -> { val n = rng.range(30, 90); c.inventory.add(ItemType.STEEL, n); say("${c.name} found a ruined cache: $n steel.", 1) }
        1 -> { val n = rng.range(1, 3); c.inventory.add(ItemType.COMPONENT, n); say("${c.name} salvaged $n components from a wreck.", 1) }
        else -> { val n = rng.range(60, 200); c.inventory.add(ItemType.SILVER, n); say("${c.name} found $n silver in an abandoned camp.", 1) }
    }
}

private fun Game.lostInTerrain(c: Caravan) {
    c.progress = -World.TICKS_PER_COST * 0.5f
    say("${c.name} lost the trail and wasted half a day.", 1)
}

private fun Game.arrive(c: Caravan) {
    c.route.clear()
    if (c.tile == world.homeTile) { unloadCaravan(c); return }
    val site = world.siteAt(c.tile)
    if (site != null) { clearSite(c, site); return }
    val s = world.settlementAt(c.tile)
    if (s == null) { say("${c.name} reached ${tileName(c.tile)}.", 1); return }
    if (s.destroyedUntil > tick) { say("${c.name} found only ruins where ${s.name} stood.", 1); return }
    refreshSettlement(s)
    val f = s.faction
    if (hostileTo(f)) {
        startFight(c, "${s.name} guards", 0, defenderPoints(s) * 0.5f, false, BattleAftermath.SETTLEMENT_GUARDS, settlement = world.settlements.indexOf(s), enemyFaction = s.faction.id)
        return
    }
    say("${c.name} arrived at ${s.name} (${f.label}).", 1)
}

private fun Game.defenderPoints(s: Settlement): Float {
    val base = when (s.faction.kind) { 0 -> 75f; 1 -> 110f; else -> 100f }
    return (base + day * 2.2f) * difficulty.threat.coerceAtLeast(0.6f)
}

internal fun Game.clearSite(c: Caravan, site: Site) {
    say("${c.name} reached the ${site.name}.", 1)
    val rescue = openQuestAt(site.id)?.kind == QuestKind.RESCUE_CAPTIVES
    val aftermath = if (rescue) BattleAftermath.RESCUE_CAPTIVES else BattleAftermath.SITE
    startFight(c, "the ${site.name}", 0, site.strength, true, aftermath, site = site.id, enemyFaction = site.factionId)
}

/** Attack a settlement: its defenders fight behind sandbags on a battle map the player commands. */
fun Game.attackSettlement(c: Caravan, s: Settlement): String? {
    if (c.tile != s.tile || c.route.isNotEmpty() || c.inBattle) return "The caravan must be at ${s.name}."
    if (s.destroyedUntil > tick) return "It is already in ruins."
    if (c.humans.none { !it.downed }) return "Nobody can fight."
    refreshSettlement(s)
    val wasHostile = hostileTo(s.faction)
    if (!s.faction.permanentEnemy) adjustGoodwill(s.faction, -100)
    startFight(c, "${s.name}'s defenders", 0, defenderPoints(s), true, BattleAftermath.SETTLEMENT_ASSAULT,
        settlement = world.settlements.indexOf(s), wasHostile = wasHostile, enemyFaction = s.faction.id)
    return null
}

// ---------------------------------------------------------------------- settlements

fun Game.negotiator(c: Caravan): Int = c.humans.maxOfOrNull { it.level(SkillType.SOCIAL) } ?: 0
fun Game.caravanSellPrice(c: Caravan, t: ItemType) = t.value * (0.5f + negotiator(c) * 0.01f)
fun Game.caravanBuyPrice(c: Caravan, t: ItemType) = t.value * (1.35f - negotiator(c) * 0.01f)

fun Game.refreshSettlement(s: Settlement) {
    if (tick - s.stockTick < 12L * TICKS_PER_DAY) return
    s.stockTick = tick
    val r = Rng(seed * 131 + s.index * 977L + tick / (12L * TICKS_PER_DAY))
    s.stock.clear()
    s.silver = r.range(500, 2400)
    val goods: List<Pair<ItemType, Int>> = when (s.faction.kind) {
        0 -> listOf(ItemType.WOOD to 300, ItemType.LEATHER to 150, ItemType.WOOL to 120, ItemType.CLOTH to 150, ItemType.RICE to 200, ItemType.POTATOES to 150,
            ItemType.CORN to 200, ItemType.PEMMICAN to 120, ItemType.MEDS_HERBAL to 20, ItemType.HEALROOT to 40, ItemType.SMOKELEAF to 60, ItemType.HAY to 120,
            ItemType.W_CLUB to 3, ItemType.W_SPEAR to 3, ItemType.W_BOW to 3, ItemType.W_GREATBOW to 1, ItemType.A_TRIBAL to 3, ItemType.PSYCHOID to 30)
        else -> listOf(ItemType.STEEL to 400, ItemType.COMPONENT to 25, ItemType.PLASTEEL to 80, ItemType.GOLD to 25, ItemType.MEDS_INDUSTRIAL to 18,
            ItemType.MEAL_PACKAGED to 60, ItemType.MEAL_SIMPLE to 25, ItemType.KIBBLE to 100, ItemType.BEER to 20, ItemType.JOINT to 15, ItemType.CLOTH to 150,
            ItemType.W_REVOLVER to 2, ItemType.W_AUTOPISTOL to 2, ItemType.W_BOLT to 2, ItemType.W_RIFLE to 1, ItemType.W_SHOTGUN to 1, ItemType.A_FLAK_VEST to 2,
            ItemType.A_PARKA to 2, ItemType.A_DUSTER to 2, ItemType.A_HELMET to 2, ItemType.A_FLAK_PANTS to 2)
    }
    for ((t, n) in goods) if (r.chance(0.6f)) s.stock.add(t, max(1, r.range(n / 4, n)))
    // A request for a staple, with a juicy reward.
    val want = listOf(ItemType.STEEL to 80, ItemType.WOOD to 120, ItemType.MEAL_SIMPLE to 12, ItemType.MEDS_HERBAL to 6, ItemType.LEATHER to 60, ItemType.COMPONENT to 4, ItemType.CLOTH to 80)
    val (rt, rn) = want[r.int(want.size)]
    val n = max(2, (rn * (0.6f + r.float() * 0.8f)).toInt())
    s.request = SettlementRequest(rt, n, (rt.value * n * 1.6f + 40f).toInt(), tick + 8L * TICKS_PER_DAY)
}

fun Game.caravanSilver(c: Caravan) = c.inventory.count(ItemType.SILVER)

fun Game.caravanSell(c: Caravan, s: Settlement, t: ItemType, n: Int): Int {
    if (t == ItemType.SILVER || !s.faction.trades) return 0
    val price = caravanSellPrice(c, t)
    val k = min(min(n, c.inventory.count(t)), (s.silver / max(0.01f, price * t.maxValueFactor())).toInt())
    if (k <= 0) return 0
    var gain = 0
    for ((lot, m) in c.inventory.take(t, k)) {
        gain += (price * lot.valueFactor() * m).toInt()
        s.stock.add(lot, m)
    }
    c.inventory.add(ItemType.SILVER, gain)
    s.silver -= gain
    silverEarned += gain
    return gain
}

fun Game.caravanBuy(c: Caravan, s: Settlement, t: ItemType, n: Int): Boolean {
    if (!s.faction.trades) return false
    val picks = s.stock.peek(t, n)
    val k = picks.sumOf { it.second }
    if (k <= 0) return false
    val cost = picks.sumOf { (caravanBuyPrice(c, t) * it.first.valueFactor() * it.second).toDouble() }.toInt() + 1
    if (caravanSilver(c) < cost) return false
    if (c.load() + t.mass() * k > c.capacity() + 0.01f) return false
    c.inventory.remove(Lot(ItemType.SILVER), cost)
    for ((lot, m) in picks) { s.stock.remove(lot, m); c.inventory.add(lot, m) }
    s.silver += cost
    return true
}

fun Game.caravanBuyOk(c: Caravan, t: ItemType, n: Int): Boolean = c.load() + t.mass() * n <= c.capacity() + 0.01f

fun Game.fulfillRequest(c: Caravan, s: Settlement): Boolean {
    val r = s.request ?: return false
    if (tick > r.expires || c.inventory.count(r.type) < r.count) return false
    c.inventory.take(r.type, r.count)
    c.inventory.add(ItemType.SILVER, r.reward)
    adjustGoodwill(s.faction, 8)
    s.request = null
    say("${s.name} thanks you for the ${r.type.label.lowercase()}.", 1)
    return true
}

fun Game.giftGoods(c: Caravan, s: Settlement, t: ItemType, n: Int): Int {
    if (s.faction.permanentEnemy) return 0
    val k = min(n, c.inventory.count(t))
    if (k <= 0) return 0
    c.inventory.take(t, k)
    val gain = max(0, (t.value * k / 30f).toInt())
    adjustGoodwill(s.faction, gain)
    return gain
}


/** Called from the pawn death hook: a caravan member died far from home. */
internal fun Game.caravanDeath(p: Pawn, cause: String): Boolean {
    val c = caravans.firstOrNull { p in it.members } ?: return false
    if (p.faction == Faction.PLAYER && !p.isAnimal && !p.prisoner) {
        say("${p.name} died on the road ($cause).", 3)
        graveyard.add("${p.name} - $cause (day ${day + 1})")
        if (p.spouse >= 0) pawnById(p.spouse)?.spouse = -1
        if (p.lover >= 0) pawnById(p.lover)?.lover = -1
        for (o in colonists) if (Trait.PSYCHOPATH !in o.traits) o.addThought("Colonist died: ${p.name.substringBefore(' ')}", -0.1f, tick, 4 * TICKS_PER_DAY)
    } else say("${p.name} died on the road.", 1)
    // The dead keep their gear's quality and condition: it goes into the cargo as it was.
    p.weaponItem?.let { c.inventory.add(Lot(it, p.weaponQuality), 1) }
    p.weaponItem = null
    for (w in p.apparel) c.inventory.add(w.lot(), 1)
    p.apparel.clear()
    return true
}

class Settled(val game: Game, val archivedOld: ByteArray)

/**
 * Found a new colony where the caravan stands. The old colony is archived as a save (its other colonists stay in it);
 * the caller stores those bytes and may switch back later.
 */
fun Game.settle(c: Caravan, name: String): Settled? {
    val tile = c.tile
    if (c.route.isNotEmpty() || c.humans.none { !it.downed }) return null
    if (!world.passable(tile) || world.settlementAt(tile) != null || tile == world.homeTile || world.siteAt(tile) != null) return null
    caravans.remove(c)
    val archive = SaveGame.write(this)
    val biome = world.biome[tile]
    val lt = World.LocalTerrain(world.river[tile], world.adjacentWater(tile) && !world.river[tile], world.hills[tile])
    val g = Game(seed, GameMap.generate(map.w, map.h, seed * 17 + tick, biome, lt))
    g.worldBiome = worldBiome
    g.world = world
    world.homeTile = tile
    g.startSettlement(this, c.members.filter { it.alive }, c.inventory.copy(), name)
    return Settled(g, archive)
}
