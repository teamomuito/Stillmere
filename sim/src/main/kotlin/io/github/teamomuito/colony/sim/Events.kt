package io.github.teamomuito.colony.sim

import kotlin.math.max
import kotlin.math.min

private fun Game.threatPoints(): Float {
    val cols = colonists.size
    val animalsPower = tamedAnimals.sumOf { (it.race.dangerous * 6f).toDouble() }.toFloat()
    var pts = cols * 17f + map.wealth() / 95f + animalsPower
    pts *= difficulty.threat * storyteller.threat
    pts *= min(1f, 0.34f + day / 34f)
    return max(pts, 22f * difficulty.threat)
}

fun Game.edgeCell(side: Int): Pair<Int, Int>? {
    repeat(120) {
        val t = rng.int(map.w - 6) + 3
        val (x, y) = when (side) { 0 -> 1 to t; 1 -> map.w - 2 to t; 2 -> t to 1; else -> t to map.h - 2 }
        val i = map.idx(x, y)
        if (map.walkable(i) && map.terrain[i] != Terrain.WATER_SHALLOW) return x to y
    }
    return null
}

private fun Game.hint(bit: Int, text: String) {
    if (hintBits and (1 shl bit) != 0) return
    hintBits = hintBits or (1 shl bit)
    say("Tip: $text", 0)
}

/** Contextual advice for the first days, in the spirit of RimWorld's learning helper. */
fun Game.hintsTick() {
    if (day > 14) return
    val cols = colonists
    if (cols.isEmpty()) return
    if (day == 0 && hour >= 7) hint(0, "Tap a colonist to see their needs and health. Tap the ground to inspect it. Drag to look around, pinch to zoom.")
    if (day == 0 && hour >= 8 && map.desig.none { it.toInt() == Desig.CUT || it.toInt() == Desig.MINE }) hint(1, "Colonists only work on what you mark. Open Architect → Orders, pick Chop trees and drag over some trees.")
    if (day == 0 && hour >= 11 && bedCount() < cols.size) hint(2, "Build a bed for everyone (Architect → Furniture). Sleeping on the ground makes people miserable.")
    if (day <= 2 && hour >= 13 && map.zones.values.none { it.kind == ZoneKind.GROWING }) hint(3, "Plan your food: Architect → Zones, pick a crop and drag over soil. Cook the harvest at a campfire or stove with a bill.")
    if (day <= 3 && hour >= 15 && map.building.none { it != null && it.built && it.def.workbench && it.bills.isNotEmpty() } &&
        map.building.any { it != null && it.built && (it.def == BuildDef.CAMPFIRE || it.def == BuildDef.STOVE_FUEL) }) hint(4, "Workbenches need bills. Tap your campfire, press Open, then add a cooking bill.")
    if (map.building.any { it != null && it.built && it.def == BuildDef.RESEARCH_BENCH } && researchCurrent == null && researchDone.size < Research.entries.size) hint(5, "Your research bench is idle. Open the Research tab and start a project.")
    if (day >= 5 && day < 12) hint(6, "Raiders will come. Tap a colonist and press Draft, then tap an enemy to attack or the ground to move. Walls, doors and sandbags help.")
    if (day >= 2 && map.zones.values.none { it.kind == ZoneKind.STOCKPILE }) hint(7, "Items lie where they drop until you make a stockpile zone (Architect → Zones).")
    if (Research.ELECTRICITY in researchDone && power.nets == 0) hint(8, "Electricity is researched. Build a generator, conduits and a battery to power lamps, heaters and stoves.")
    if (day >= 3 && colonists.any { it.temp < it.comfyMin() - 6f }) hint(9, "Colonists are cold. Build a heated room (campfire or heater), and make parkas at a tailor bench.")
    if (day >= 4 && colonists.any { it.joy < 0.25f }) hint(10, "Colonists are bored. Build recreation (horseshoes, chess table) and keep Joy hours in the Schedule.")
}

fun Game.hourlyEvents() {
    hintsTick()
    // Raid wrap-up.
    if (raidActive) {
        val alive = pawns.count { it.faction == Faction.ENEMY && it.alive && it.raidId > 0 }
        val standing = pawns.count { it.faction == Faction.ENEMY && it.alive && it.raidId > 0 && !it.downed }
        val anyRetreat = pawns.any { it.faction == Faction.ENEMY && it.raidId > 0 && it.alive && it.retreating }
        if (alive == 0 || (standing == 0 && !anyRetreat)) {
            // Nobody left on their feet: the raid is over. Downed raiders will limp away once they recover.
            for (r in pawns) if (r.faction == Faction.ENEMY && r.raidId > 0 && r.alive) r.retreating = true
            raidActive = false; raidsSurvived++
            say("The raid has been beaten back.", 1)
        } else if (!anyRetreat && standing > 0 && (standing * 2 <= raidStartCount || tick > raidEnds)) {
            for (r in pawns) if (r.faction == Faction.ENEMY && r.raidId > 0 && r.alive && !r.downed) { r.retreating = true; endJob(r) }
            say("The raiders are retreating!", 1)
        }
    }
    if (tempEventUntil in 1..tick) {
        tempOffset = 0f; tempEventUntil = 0; say("The $tempEventName has ended.", 0)
    }
    if (solarFlareUntil in 1..tick) { solarFlareUntil = 0; say("The solar flare has ended; power is back.", 1) }
    if (eclipseUntil in 1..tick) { eclipseUntil = 0; say("The eclipse is over.", 0) }
    if (toxicFalloutUntil in 1..tick) { toxicFalloutUntil = 0; say("The toxic fallout has settled.", 1) }

    if (difficulty == Difficulty.PEACEFUL) {
        // No hostile events; still friendly ones.
    } else if (tick >= nextRaid && !raidActive) {
        launchRaid()
    }
    if (tick >= nextWanderer) {
        nextWanderer = tick + chaosInterval(4, 10)
        if (colonists.size < 14 && rng.chance(0.8f)) spawnWanderer()
    }
    if (tick >= nextPod) {
        nextPod = tick + chaosInterval(3, 8)
        spawnPod()
    }
    if (tick >= nextTrader) {
        nextTrader = tick + chaosInterval(6, 13)
        spawnTrader()
    }
    if (tick >= nextTempEvent && tempEventUntil == 0L) {
        nextTempEvent = tick + chaosInterval(8, 16)
        if (rng.chance(0.75f)) {
            val hotSeason = season == Season.SUMMER || map.biome == Biome.DESERT || map.biome == Biome.TROPICAL
            if (hotSeason) { tempOffset = 16f; tempEventName = "heat wave"; say("A heat wave is sweeping in!", 2) }
            else { tempOffset = -17f; tempEventName = "cold snap"; say("A cold snap has hit. Keep warm and protect your crops!", 2) }
            tempEventUntil = tick + rng.range(2 * TICKS_PER_DAY, 4 * TICKS_PER_DAY)
        }
    }
    if (tick >= nextMisc && difficulty != Difficulty.PEACEFUL && day >= 4) {
        nextMisc = tick + chaosInterval(2, 6)
        miscEvent()
    } else if (tick >= nextMisc) {
        nextMisc = tick + chaosInterval(3, 8)
        miscFriendly()
    }
    // Visitors leaving and arriving.
    tradersHourly()
    // Prisoners with a bad mood try to break out.
    for (p in prisoners) if (!p.escaping && p.mood < 0.28f && rng.chance(0.05f)) {
        p.escaping = true; endJob(p)
        say("${p.name} is trying to escape!", 3)
    }
    // Juniper throws in extra random trouble.
    if (storyteller == Storyteller.JUNIPER && rng.chance(0.03f) && difficulty != Difficulty.PEACEFUL) miscEvent()
}

private fun Game.chaosInterval(minDays: Int, maxDays: Int): Int {
    val lo = minDays * TICKS_PER_DAY
    val hi = maxDays * TICKS_PER_DAY
    val chaos = storyteller.chaos
    val base = rng.range(lo, hi)
    return (base * (1f - 0.5f * chaos + chaos * rng.float())).toInt().coerceAtLeast(TICKS_PER_DAY / 2)
}

private fun Game.miscEvent() {
    val weights = ArrayList<Pair<Int, Float>>()
    weights += 0 to (if (day > 10) 2f else 0f)   // manhunter pack
    weights += 1 to (if (day > 10) 2f else 0f) // infestation
    weights += 2 to 1.2f // disease outbreak
    weights += 3 to (if (power.nets > 0) 1.5f else 0f) // solar flare
    weights += 4 to 1f   // eclipse
    weights += 5 to (if (day > 10) 1f else 0f) // toxic fallout
    weights += 6 to (if (map.building.any { it != null && it.def == BuildDef.BATTERY && it.built }) 1.5f else 0f) // short circuit
    weights += 7 to 1f   // blight
    weights += 8 to 1.2f // animal joins
    weights += 9 to (if (day > 8) 0.5f else 0f)  // thrumbo
    weights += 10 to (if (prisoners.isNotEmpty()) 0.8f else 0f) // prison break handled hourly
    weights += 11 to 1.0f // heat/cold done elsewhere: lightning storm
    weights += 12 to (if (day > 9) 1.2f else 0f) // refugee
    val total = weights.sumOf { it.second.toDouble() }.toFloat()
    var r = rng.float() * total
    var pick = 0
    for ((k, w) in weights) { r -= w; if (r <= 0f) { pick = k; break } }
    when (pick) {
        0 -> manhunterPack()
        1 -> infestation()
        2 -> outbreak()
        3 -> { solarFlareUntil = tick + (0.9f * TICKS_PER_DAY).toInt(); say("A solar flare knocks out all electrical power!", 2) }
        4 -> { eclipseUntil = tick + (0.7f * TICKS_PER_DAY).toInt(); say("An eclipse blots out the sun.", 2) }
        5 -> { toxicFalloutUntil = tick + 3 * TICKS_PER_DAY; say("Toxic fallout! Stay indoors; crops will wither.", 3) }
        6 -> shortCircuit()
        7 -> blight()
        8 -> animalJoins()
        9 -> thrumboPasses()
        12 -> refugees()
        11 -> { weather = Weather.THUNDER; weatherUntil = tick + 5000; lightning(); say("A violent thunderstorm hits.", 2) }
        else -> {}
    }
}

private fun Game.miscFriendly() {
    if (rng.chance(0.5f)) animalJoins()
}

// ----------------------------------------------------------------------------------------- raids

internal fun raidWeaponTier(day: Int): List<Pair<ItemType?, Float>> = when {
    day < 9 -> listOf(ItemType.W_CLUB to 14f, ItemType.W_KNIFE to 16f, ItemType.W_SPEAR to 18f, ItemType.W_BOW to 20f, ItemType.W_REVOLVER to 26f)
    day < 22 -> listOf(ItemType.W_MACE to 24f, ItemType.W_BOW to 20f, ItemType.W_REVOLVER to 26f, ItemType.W_AUTOPISTOL to 28f, ItemType.W_BOLT to 32f, ItemType.W_SHOTGUN to 34f, ItemType.W_SPEAR to 18f)
    day < 40 -> listOf(ItemType.W_SMG to 34f, ItemType.W_SHOTGUN to 34f, ItemType.W_RIFLE to 42f, ItemType.W_BOLT to 32f, ItemType.W_LONGSWORD to 38f, ItemType.W_MACE to 24f)
    else -> listOf(ItemType.W_RIFLE to 42f, ItemType.W_LMG to 52f, ItemType.W_SNIPER to 54f, ItemType.W_SMG to 34f, ItemType.W_LONGSWORD to 38f, ItemType.W_SHOTGUN to 34f)
}

internal fun tribalTier(day: Int): List<Pair<ItemType?, Float>> = when {
    day < 15 -> listOf(ItemType.W_CLUB to 14f, ItemType.W_KNIFE to 16f, ItemType.W_SPEAR to 18f, ItemType.W_BOW to 20f)
    day < 30 -> listOf(ItemType.W_MACE to 24f, ItemType.W_SPEAR to 18f, ItemType.W_BOW to 20f, ItemType.W_GREATBOW to 30f, ItemType.W_LONGSWORD to 38f)
    else -> listOf(ItemType.W_MACE to 24f, ItemType.W_GREATBOW to 30f, ItemType.W_LONGSWORD to 38f, ItemType.W_BOLT to 32f)
}

private fun Game.pickRaidKind(): Int {
    // 0 assault, 1 sapper, 2 siege, 3 drop pods
    val hasWalls = map.building.count { it != null && it.built && it.def.isWall } > 12
    val opts = ArrayList<Int>()
    opts += 0; opts += 0; opts += 0
    if (day >= 14 && hasWalls) { opts += 1; opts += 1 }
    if (day >= 16) opts += 2
    if (day >= 12) opts += 3
    if (day >= 26) { opts += 4; opts += 4 }
    return opts[rng.int(opts.size)]
}

fun Game.launchRaid() {
    val points = threatPoints()
    val rf = pickRaiders()
    val kind = if (rf.kind == 0) 0 else pickRaidKind()
    val side = rng.int(4)
    val base = edgeCell(side) ?: return
    val raidId = ++raidCounter
    val tier = if (rf.kind == 0 && kind != 4) tribalTier(day) else raidWeaponTier(day)
    var left = points
    var count = 0
    val maxCount = 1 + day / 5 + colonists.size / 3
    val spawned = ArrayList<Pawn>()
    if (kind == 4) {
        val mechs = listOf(Race.SCYTHER to 34f, Race.LANCER to 36f, Race.CENTIPEDE to 90f)
        while ((left > 0f || count == 0) && count < 8) {
            val (r, c) = mechs[rng.int(if (left > 100f) 3 else 2)]
            val x = (base.first + rng.range(-3, 3)).coerceIn(1, map.w - 2)
            val y = (base.second + rng.range(-3, 3)).coerceIn(1, map.h - 2)
            if (!map.walkable(map.idx(x, y))) { left -= 1f; continue }
            spawned += newMech(r, x, y, raidId); left -= c; count++
        }
    }
    while (kind != 4 && (left > 0f || count == 0) && count < min(16, maxCount)) {
        val (w, c) = tier[rng.int(tier.size)]
        val armor = ArrayList<ItemType>()
        if (rf.kind != 0 && day >= 18 && rng.chance(0.5f)) armor += ItemType.A_FLAK_VEST
        if (rf.kind != 0 && day >= 24 && rng.chance(0.4f)) armor += ItemType.A_HELMET
        if (rf.kind != 0 && day >= 40 && rng.chance(0.3f)) { armor.clear(); armor += ItemType.A_ARMOR }
        var x = (base.first + rng.range(-3, 3)).coerceIn(1, map.w - 2)
        var y = (base.second + rng.range(-3, 3)).coerceIn(1, map.h - 2)
        if (kind == 3) {
            // Drop pods: land in the open near the colony.
            var tries = 0
            do {
                val ang = rng.float() * 6.283f
                val rad = rng.range(12, 22)
                x = (homeX + (Math.cos(ang.toDouble()) * rad).toInt()).coerceIn(2, map.w - 3)
                y = (homeY + (Math.sin(ang.toDouble()) * rad).toInt()).coerceIn(2, map.h - 3)
            } while (tries++ < 20 && (!map.walkable(map.idx(x, y)) || map.roofed(map.idx(x, y))))
        }
        if (!map.walkable(map.idx(x, y))) { left -= 1f; continue }
        val r = newRaider(x, y, w, raidId, armor)
        r.raidMode = kind
        r.wfaction = rf.id
        spawned += r
        left -= c + armor.size * 10f
        count++
    }
    raidActive = true
    raidStartCount = count
    raidStartedAt = tick
    raidEnds = tick + (if (kind == 2) 2.4f else 1.5f).times(TICKS_PER_DAY).toInt()
    if (kind == 2) {
        // Siege camp 30 tiles from the base along the approach direction.
        val dx = Integer.signum(base.first - homeX).let { if (it == 0) 0 else it }
        val dy = Integer.signum(base.second - homeY)
        var cx = (homeX + dx * 30).coerceIn(4, map.w - 5)
        var cy = (homeY + dy * 30).coerceIn(4, map.h - 5)
        if (dx == 0) cx = homeX; if (dy == 0) cy = homeY
        for (r in spawned) { r.campX = (cx + rng.range(-3, 3)).coerceIn(2, map.w - 3); r.campY = (cy + rng.range(-3, 3)).coerceIn(2, map.h - 3) }
    }
    nextRaid = tick + chaosInterval(3, 6)
    val dir = arrayOf("west", "east", "north", "south")[side]
    val label = when (kind) {
        1 -> "Sappers"; 2 -> "A siege force"; 3 -> "Raiders in drop pods"; 4 -> "Mechanoids"; else -> "Raiders"
    }
    val who = if (kind == 4) "" else " of ${rf.name}"
    say("RAID! $label$who ($count) ${if (kind == 3) "drop in near your colony" else "approach from the $dir"}.", 3)
}

private fun Game.spawnWanderer() {
    val e = edgeCell(rng.int(4)) ?: return
    val p = newHuman(e.first, e.second, Faction.PLAYER)
    p.wanderer = true
    p.weaponItem = if (rng.chance(0.4f)) ItemType.W_REVOLVER else ItemType.W_KNIFE
    p.food = 0.5f
    say("${p.name} (${p.backstory.lowercase()}) wanders in and joins your colony.", 1)
}

private fun Game.spawnPod() {
    val cols = colonists
    if (cols.isEmpty()) return
    val c = cols[rng.int(cols.size)]
    val x = (c.x + rng.range(-7, 7)).coerceIn(2, map.w - 3)
    val y = (c.y + rng.range(-7, 7)).coerceIn(2, map.h - 3)
    val (type, n) = when (rng.int(7)) {
        0 -> ItemType.STEEL to rng.range(40, 100)
        1 -> ItemType.MEAL_PACKAGED to rng.range(6, 14)
        2 -> ItemType.WOOD to rng.range(50, 110)
        3 -> ItemType.COMPONENT to rng.range(2, 6)
        4 -> ItemType.SILVER to rng.range(60, 220)
        5 -> ItemType.MEDS_HERBAL to rng.range(3, 8)
        else -> ItemType.CLOTH to rng.range(40, 90)
    }
    if (map.drop(type, n, x, y) < n) say("A cargo pod crashed nearby with ${type.label.lowercase()}.", 1)
}

private fun Game.manhunterPack() {
    val race = when (map.biome) {
        Biome.TUNDRA, Biome.BOREAL -> rng.pick(listOf(Race.WOLF, Race.WOLF, Race.BEAR))
        Biome.DESERT, Biome.ARID -> rng.pick(listOf(Race.BOAR, Race.WOLF))
        else -> rng.pick(if (day < 14) listOf(Race.BOAR, Race.WOLF, Race.DEER) else listOf(Race.BOAR, Race.WOLF, Race.BEAR, Race.MUFFALO))
    }
    val count = (threatPoints() / 18f / race.dangerous.coerceAtLeast(0.5f)).toInt().coerceIn(2, 10)
    val side = rng.int(4)
    val e = edgeCell(side) ?: return
    repeat(count) {
        val x = (e.first + rng.range(-3, 3)).coerceIn(1, map.w - 2); val y = (e.second + rng.range(-3, 3)).coerceIn(1, map.h - 2)
        if (map.walkable(map.idx(x, y))) { val a = newAnimal(race, x, y); a.manhunter = true }
    }
    say("A pack of $count ${race.label.lowercase()}s has gone manhunter and is heading your way!", 3)
}

private fun Game.infestation() {
    // Insects emerge from under a mountain close to the colony.
    var best = -1
    var bd = 1e9f
    for (i in 0 until map.size) {
        if (!map.natRoof[i] || !map.walkable(i) || map.terrain[i] == Terrain.ROCK) continue
        val d = distance(map.xOf(i), map.yOf(i), homeX, homeY)
        if (d in 8f..40f && d < bd && rng.chance(0.03f)) { best = i; bd = d }
    }
    if (best < 0) return
    val bug = if (day < 15) Race.MEGASCARAB else rng.pick(listOf(Race.MEGASCARAB, Race.SPELOPEDE, Race.MEGASPIDER))
    val n = (threatPoints() / 16f / bug.dangerous.coerceAtLeast(0.6f)).toInt().coerceIn(3, 12)
    repeat(n) {
        val x = (map.xOf(best) + rng.range(-2, 2)).coerceIn(1, map.w - 2); val y = (map.yOf(best) + rng.range(-2, 2)).coerceIn(1, map.h - 2)
        if (map.walkable(map.idx(x, y))) { val a = newAnimal(bug, x, y); a.manhunter = true }
    }
    say("An infestation! $n insects have burst out of the ground.", 3)
}

private fun Game.outbreak() {
    val cols = colonists
    if (cols.isEmpty()) return
    val kind = if (day < 12) HediffKind.FLU else rng.pick(listOf(HediffKind.FLU, HediffKind.FLU, HediffKind.PLAGUE, HediffKind.MALARIA))
    val victims = cols.shuffled(java.util.Random(rng.int(1_000_000).toLong())).take(max(1, cols.size / 3))
    for (v in victims) if (v.hediffs.none { it.kind.category == 0 }) addHediff(v, kind, 0.1f)
    say("A ${kind.label.lowercase()} outbreak is spreading through the colony!", 3)
}

private fun Game.shortCircuit() {
    val bats = map.building.filterNotNull().filter { it.def == BuildDef.BATTERY && it.built && it.charge > 150f }
    if (bats.isEmpty()) return
    val b = rng.pick(bats)
    say("A short circuit! A battery exploded.", 3)
    explode(b.x, b.y, 3.2f, 28f, null, true)
    map.building[map.idx(b.x, b.y)] = null
}

private fun Game.blight() {
    val crops = (0 until map.size).filter { map.plant[it]?.type?.crop == true }
    if (crops.isEmpty()) return
    val c = rng.pick(crops)
    val t = map.plant[c]!!.type
    var n = 0
    for (i in crops) if (map.plant[i]?.type == t && distance(map.xOf(i), map.yOf(i), map.xOf(c), map.yOf(c)) < 9f) { map.plant[i] = null; n++ }
    say("Blight destroyed $n ${t.label.lowercase()} plants.", 3)
}

private fun Game.animalJoins() {
    val cands = pawns.filter { it.alive && it.isAnimal && it.faction == Faction.WILD && !it.race.predator && it.race.dangerous < 0.5f && !it.manhunter && it.race.tameDifficulty < 1.2f }
    if (cands.isEmpty()) return
    val a = rng.pick(cands)
    val e = a
    a.faction = Faction.PLAYER; a.tame = true; a.name = rng.pick(Names.animal)
    a.x = (homeX + rng.range(-8, 8)).coerceIn(2, map.w - 3); a.y = (homeY + rng.range(-8, 8)).coerceIn(2, map.h - 3)
    if (!map.walkable(map.idx(a.x, a.y))) { a.x = homeX; a.y = homeY }
    a.fromX = a.x; a.fromY = a.y
    say("A ${e.race.label.lowercase()} has wandered in and decided to stay: ${a.name}.", 1)
}

private fun Game.thrumboPasses() {
    val e = edgeCell(rng.int(4)) ?: return
    newAnimal(Race.THRUMBO, e.first, e.second)
    say("A rare thrumbo has been spotted nearby!", 1)
}

private fun Game.refugees() {
    val side = rng.int(4)
    val e = edgeCell(side) ?: return
    val ref = newHuman(e.first, e.second, Faction.VISITOR)
    ref.refugee = true
    ref.homeTile = -1
    // Wounded and exhausted.
    val torso = ref.race.body.indexOfFirst { it.tag == PartTag.TORSO }
    woundPart(ref, torso, DamageKind.CUT, 14f)
    for (leg in partsWithTag(ref, PartTag.LEG).take(1)) woundPart(ref, leg, DamageKind.BULLET, 12f)
    downPawn(ref)
    ref.food = 0.3f
    val raidId = ++raidCounter
    val n = rng.range(2, 3 + day / 10)
    val tier = raidWeaponTier(day)
    repeat(n) {
        val (w, _) = tier[rng.int(tier.size)]
        val x = (e.first + rng.range(-2, 2)).coerceIn(1, map.w - 2); val y = (e.second + rng.range(-2, 2)).coerceIn(1, map.h - 2)
        if (map.walkable(map.idx(x, y))) newRaider(x, y, w, raidId)
    }
    raidActive = true; raidStartCount = n; raidStartedAt = tick; raidEnds = tick + TICKS_PER_DAY
    say("${ref.name} staggers in from the ${arrayOf("west", "east", "north", "south")[side]}, wounded and chased by raiders. Rescue them to gain a colonist.", 2)
}
