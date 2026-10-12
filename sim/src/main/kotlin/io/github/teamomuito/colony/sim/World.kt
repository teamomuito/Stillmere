package io.github.teamomuito.colony.sim

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class PowerState {
    var produced = 0f
    var consumed = 0f
    var stored = 0f
    var capacity = 0f
    var nets = 0
    var flare = false
}

fun Game.cellFlammability(i: Int): Float {
    var f = 0f
    val b = map.building[i]
    if (b != null) f = max(f, b.flam)
    val pl = map.plant[i]
    if (pl != null) f = max(f, pl.type.flammable * (if (weather == Weather.RAIN) 0.4f else 1f))
    val it = map.items[i]
    if (it != null) f = max(f, it.type.flammable * 0.8f)
    val fl = map.floor[i]
    if (fl != null) f = max(f, fl.flam * 0.4f)
    if (map.terrain[i] == Terrain.MARSH) f *= 0.5f
    return f
}

fun Game.igniteCell(i: Int, intensity: Float = 0.3f) {
    if (!map.terrain[i].passable && map.terrain[i] != Terrain.ROCK) return
    if (map.terrain[i] == Terrain.WATER_SHALLOW || map.terrain[i] == Terrain.WATER_DEEP) return
    if (weather == Weather.RAIN || weather == Weather.THUNDER) return
    if (cellFlammability(i) <= 0.01f && intensity < 0.5f) return
    val f = map.fires[i]
    if (f != null) f.intensity = min(1.2f, f.intensity + intensity * 0.5f) else map.fires[i] = Fire(intensity)
}

fun Game.fireTick() {
    if (map.fires.isEmpty()) return
    // Quench vents burst when flames reach them.
    for (b in map.buildings()) {
        if (!b.built || b.def != BuildDef.QUENCH_VENT) continue
        if (map.fires.keys.any { val fx = it % map.w; val fy = it / map.w; Math.abs(fx - b.x) <= 3 && Math.abs(fy - b.y) <= 3 }) {
            for (dy in -6..6) for (dx in -6..6) { val x = b.x + dx; val y = b.y + dy; if (map.inB(x, y)) map.fires.remove(map.idx(x, y)) }
            map.removeBuilding(b)
            say("A quench vent smothers the flames.", 1)
            break
        }
    }
    if (map.fires.isEmpty()) return
    val rain = weather == Weather.RAIN || weather == Weather.THUNDER
    val snow = weather == Weather.SNOW
    for ((i, f) in map.fires.entries.toList()) {
        f.age += tk(4)
        val flam = cellFlammability(i)
        if (rain) f.intensity -= 0.01f
        if (snow) f.intensity -= 0.004f
        if (flam <= 0.01f) f.intensity -= 0.006f else f.intensity = min(1.2f, f.intensity + 0.002f * flam)
        if (f.age % tk(20) == 0) {
            val b = map.building[i]
            if (b != null && b.flam > 0f) {
                b.hp -= 4f * f.intensity * b.flam
                if (b.hp <= 0f) {
                    map.removeBuilding(b)
                    say("A ${b.displayName.lowercase()} burned down.", 2)
                }
            }
            val pl = map.plant[i]
            if (pl != null && pl.type.flammable > 0f && rng.chance(0.25f * f.intensity)) { map.plant[i] = null }
            val st = map.items[i]
            if (st != null && st.type.flammable > 0f && st.corpseOf == null) {
                st.count -= max(1, (st.count * 0.15f * f.intensity).toInt())
                if (st.count <= 0) map.items.remove(i)
            }
        }
        // A pawn on a large fire may catch fire, at the cumulative chance over 150 ticks. The fire does no direct damage to pawns
        // in the cell (RimWorld/Fire.cs:267-270; docs/fidelity/combat.md).
        if (f.age % BURN_CHECK_TICKS == 0 && f.intensity > CELL_CATCH_MIN_SIZE) {
            val fx = i % map.w; val fy = i / map.w
            for (p in pawns) {
                if (!p.alive || p.burnSize > 0f || p.x != fx || p.y != fy) continue
                if (rng.chance(cumulativeCatchChance(p.flammability(), CELL_CATCH_TICKS))) catchFire(p, f.intensity * CELL_CATCH_SIZE_SHARE)
            }
        }
        // Spread.
        if (f.age % tk(16) == 0) {
            for (d in 0 until 8) {
                val nx = i % map.w + GameMap.DX8[d]; val ny = i / map.w + GameMap.DY8[d]
                if (!map.inB(nx, ny)) continue
                val n = map.idx(nx, ny)
                if (map.fires.containsKey(n)) continue
                val nf = cellFlammability(n)
                val wind = 1f
                if (nf > 0f && rng.float() < 0.06f * f.intensity * nf * wind * (if (d >= 4) 0.5f else 1f)) igniteCell(n, 0.12f)
            }
        }
        if (f.intensity <= 0.02f) map.fires.remove(i)
    }
}

fun Game.explode(x: Int, y: Int, radius: Float, damage: Float, source: Pawn? = null, fire: Boolean = false) {
    blasts.add(Blast(x, y, radius, tick + tk(14)))
    val r = radius.toInt() + 1
    for (yy in y - r..y + r) for (xx in x - r..x + r) {
        if (!map.inB(xx, yy)) continue
        val d = distance(x, y, xx, yy)
        if (d > radius) continue
        val i = map.idx(xx, yy)
        val b = map.building[i]
        val fall = 1f - d / (radius + 0.5f)
        if (b != null && b.built) {
            b.hp -= damage * 3f * fall
            if (b.hp <= 0f) { map.removeBuilding(b) }
        }
        if (map.terrain[i] == Terrain.ROCK && damage > 30f && rng.chance(0.1f * fall)) { /* craters do not mine rock */ }
        if (fire) igniteCell(i, 0.4f * fall)
        val pl = map.plant[i]
        if (pl != null && rng.chance(0.4f * fall)) map.plant[i] = null
    }
    for (p in pawns.toList()) {
        if (!p.alive) continue
        val d = distance(x, y, p.x, p.y)
        if (d > radius) continue
        val fall = 1f - d / (radius + 0.5f)
        // Each thing is hit once per explosion, with the falloff for its cell (Verse/DamageWorker.cs:302, 316). An incendiary
        // blast is a single burn hit, so a flame hit can set the pawn alight.
        dealDamage(p, if (fire) DamageKind.BURN else DamageKind.BLAST, damage * fall, 0f, source)
    }
}

// ----------------------------------------------------------------------------------- power

fun Game.powerTick() {
    // Slide hatches open or close with the power, which changes what can be walked through.
    if (map.buildings().any { it.def == BuildDef.SHUTTER_DOOR }) map.markWalkChanged()
    val m = map
    val ps = power
    ps.produced = 0f; ps.consumed = 0f; ps.stored = 0f; ps.capacity = 0f
    ps.flare = solarFlareUntil > tick
    // Label conduit components with BFS.
    val comp = IntArray(m.size) { -1 }
    var n = 0
    val stack = IntArray(m.size)
    for (s in 0 until m.size) {
        if (!m.conduit[s] || comp[s] != -1) continue
        var sp = 0
        stack[sp++] = s; comp[s] = n
        while (sp > 0) {
            val c = stack[--sp]
            val cx = c % m.w; val cy = c / m.w
            for (d in 0 until 4) {
                val nx = cx + GameMap.DX4[d]; val ny = cy + GameMap.DY4[d]
                if (!m.inB(nx, ny)) continue
                val q = m.idx(nx, ny)
                if (m.conduit[q] && comp[q] == -1) { comp[q] = n; stack[sp++] = q }
            }
        }
        n++
    }
    ps.nets = n
    if (n == 0) {
        for (b in m.buildings()) if (b != null && b.def.isPowered) b.powered = false
        return
    }
    // Union-find over components that share a device.
    val parent = IntArray(n) { it }
    fun find(a: Int): Int { var x = a; while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }; return x }
    val devices = ArrayList<Building>()
    val attach = ArrayList<IntArray>()
    for (b in m.buildings()) {
        if (b == null || !b.built) continue
        val d = b.def
        if (!d.isPowered && d != BuildDef.BATTERY) continue
        val i = m.idx(b.x, b.y)
        val comps = ArrayList<Int>()
        if (comp[i] >= 0) comps.add(comp[i])
        for (k in 0 until 4) {
            val nx = b.x + GameMap.DX4[k]; val ny = b.y + GameMap.DY4[k]
            if (m.inB(nx, ny) && comp[m.idx(nx, ny)] >= 0) comps.add(comp[m.idx(nx, ny)])
        }
        if (comps.isEmpty()) { b.powered = false; continue }
        for (k in 1 until comps.size) parent[find(comps[k])] = find(comps[0])
        devices.add(b); attach.add(intArrayOf(comps[0]))
    }
    class Net { var prod = 0f; var cons = 0f; var cap = 0f; var stored = 0f; val batteries = ArrayList<Building>(); val consumers = ArrayList<Building>() }
    val nets = HashMap<Int, Net>()
    val dt = Game.SLOW_TICK.toFloat() / TICKS_PER_DAY
    for ((k, b) in devices.withIndex()) {
        val net = nets.getOrPut(find(attach[k][0])) { Net() }
        val d = b.def
        if (d == BuildDef.BATTERY) { net.cap += BATTERY_CAPACITY_WD; net.stored += b.charge; net.batteries.add(b); continue }
        if (d.producesPower) {
            var out = 0f
            when (d) {
                BuildDef.SOLAR_PANEL -> out = d.power * daylight() * m.unroofedShare(b)
                BuildDef.BREEZE_MILL -> out = d.power * min(windFactor, BREEZE_OUTPUT_WIND_CAP)
                BuildDef.WOOD_GENERATOR -> if (b.fuel > 0f) { out = d.power; b.fuel = max(0f, b.fuel - Game.SLOW_TICK.toFloat() / TICKS_PER_HOUR * 0.8f) }
                else -> {}
            }
            if (ps.flare) out *= 0f
            net.prod += out
        } else {
            net.consumers.add(b)
            net.cons += -d.power * (if (d.workbench && b.inUse <= 0) 0.1f else 1f)
        }
    }
    for (net in nets.values) {
        ps.produced += net.prod; ps.consumed += net.cons
        var balance = (net.prod - net.cons) * dt
        var energy = net.stored
        energy = (energy + balance).coerceIn(0f, net.cap)
        // Each battery loses its self-discharge every tick, never more than it holds (RimWorld/CompPowerBattery.cs:70).
        energy = max(0f, energy - net.batteries.size * BATTERY_SELF_DISCHARGE_W * dt)
        val ok = net.prod >= net.cons || (net.stored + balance >= 0f && net.stored > 0f)
        val per = if (net.batteries.isEmpty()) 0f else energy / net.batteries.size
        for (bt in net.batteries) bt.charge = per
        ps.stored += energy; ps.capacity += net.cap
        for (c in net.consumers) { c.powered = ok && !ps.flare }
        for (b in net.batteries) b.powered = true
    }
    // Anything not on a net is off.
    val onNet = HashSet<Building>()
    for (net in nets.values) { onNet.addAll(net.consumers); onNet.addAll(net.batteries) }
    for (b in m.buildings()) if (b != null && b.def.consumesPower && b !in onNet) b.powered = false
}

// ----------------------------------------------------------------------------------- slow world tick

fun Game.worldSlowTick() {
    val m = map
    val out = outdoorTemp()
    if (m.roomDirty) m.rebuildRooms(out)
    updateWeather()
    powerTick()
    lightTick()
    roomClimate(out)
    plantsTick(out)
    spoilTick(out)
    productionTick()
    roomStats()
    // Rain washes the outdoors clean; snow settles and melts.
    if (weather == Weather.RAIN || weather == Weather.THUNDER) {
        if (rng.chance(0.5f)) for (k in 0 until 40) {
            val i = rng.int(m.size)
            if (m.filth[i] > 0 && !m.roofed(i)) m.filth[i] = (m.filth[i] - 1).toByte()
        }
    }
    for (i in 0 until m.size) {
        if (m.roofed(i)) { m.snow[i] = 0f; continue }
        if (weather == Weather.SNOW && out < 1f) m.snow[i] = min(1f, m.snow[i] + 0.02f)
        else if (out > 0f && m.snow[i] > 0f) m.snow[i] = max(0f, m.snow[i] - 0.01f * (1f + out / 10f))
    }
    // A fume cloud hurts everybody outside and kills plants.
    if (toxicFalloutUntil > tick) {
        for (p in pawns) if (p.alive && !p.isAnimal && !m.roofed(m.idx(p.x, p.y))) {
            val h = addHediff(p, HediffKind.TOXIN_LOAD, 0f)
            h.severity = min(1f, h.severity + 0.0035f)
            p.healthDirty = true
        }
        for (i in 0 until m.size) { val pl = m.plant[i]; if (pl != null && !m.roofed(i) && rng.chance(0.002f)) m.plant[i] = null }
    }
    // Filth settles in busy rooms slowly.
    if (tick % tk(2000L) == 0L) for (p in pawns) if (p.alive && !p.isAnimal && rng.chance(0.3f)) {
        val i = m.idx(p.x, p.y)
        if (m.roofed(i) && m.filth[i] < 4) m.filth[i] = (m.filth[i] + 1).toByte()
    }
    // Windblown fire starts.
    windFactor = (windFactor + (rng.float() - 0.5f) * 0.12f).coerceIn(0.15f, 1.35f)
    // Hunger of tamed animals: fodder and feed pellets are consumed in animalTick.
}

private fun Game.updateWeather() {
    if (tick < weatherUntil) return
    val b = map.biome
    val s = season
    val cold = outdoorTemp() < 1f
    val r = rng.float()
    weather = when {
        r < b.rain * 0.55f -> if (cold) Weather.SNOW else Weather.RAIN
        r < b.rain * 0.7f && !cold && s != Season.WINTER -> Weather.THUNDER
        r < b.rain * 0.85f -> Weather.FOG
        r < b.rain + 0.15f -> Weather.CLOUDY
        else -> Weather.CLEAR
    }
    weatherUntil = tick + rng.range(tk(2500), tk(9000))
    if (weather == Weather.THUNDER && rng.chance(0.5f)) lightning()
}

fun Game.lightning() {
    repeat(rng.range(1, 3)) {
        val x = rng.range(3, map.w - 4); val y = rng.range(3, map.h - 4)
        val i = map.idx(x, y)
        val old = weather
        weather = Weather.CLEAR
        igniteCell(i, 0.5f)
        weather = old
        shots.add(Shot(x.toFloat(), 0f, x.toFloat(), y.toFloat(), tick + tk(8), true, 2))
    }
}

private fun Game.lightTick() {
    val m = map
    val day = daylight()
    for (i in 0 until m.size) {
        m.light[i] = if (m.natRoof[i]) 0f else if (m.roomIndoorAt(i)) day * 0.85f else day
    }
    for (b in m.buildings()) {
        if (b == null || !b.built || b.def.light <= 0f) continue
        val active = (b.def.fuelCap > 0f && b.fuel > 0f) || (b.def.power < 0f && b.powered) || (b.def.fuelCap <= 0f && b.def.power == 0f)
        if (!active) continue
        val r = b.def.light
        val ri = r.toInt() + 1
        for (yy in b.y - ri..b.y + ri) for (xx in b.x - ri..b.x + ri) {
            if (!m.inB(xx, yy)) continue
            val d = distance(b.x, b.y, xx, yy)
            if (d > r) continue
            val v = 1f - d / r
            val i = m.idx(xx, yy)
            if (v > m.light[i]) m.light[i] = v
        }
    }
    for (f in m.fires.keys) {
        val x = f % m.w; val y = f / m.w
        for (yy in y - 3..y + 3) for (xx in x - 3..x + 3) {
            if (!m.inB(xx, yy)) continue
            val i = m.idx(xx, yy)
            val v = 1f - distance(x, y, xx, yy) / 4f
            if (v > m.light[i]) m.light[i] = v
        }
    }
}

internal fun Game.roomClimate(out: Float) {
    val m = map
    val heat = FloatArray(m.roomTemp.size)
    for (b in m.buildings()) {
        if (b == null || !b.built) continue
        val d = b.def
        if (d.heat == 0f) continue
        val i = m.idx(b.x, b.y)
        val r = m.roomId[i]
        if (d.power != 0f) {
            if (!b.powered) continue
            if (r >= 0 && Thermal.thermostatAllows(d.heat, m.roomTemp[r])) heat[r] += d.heat
            continue
        }
        var active = false
        when {
            d.fuelCap > 0f -> {
                if (b.fuel > 0f) {
                    val use = d == BuildDef.CAMPFIRE || d == BuildDef.PITCH_POST || b.inUse > 0 || d == BuildDef.WOOD_GENERATOR
                    if (use) { b.fuel = max(0f, b.fuel - 0.25f); active = true }
                    if (b.inUse > 0) b.inUse = max(0, b.inUse - Game.SLOW_TICK.toInt())
                }
            }
            else -> active = true
        }
        if (active && r >= 0 && m.roomIndoor[r]) heat[r] += d.heat
        // Weather puts out outdoor fires.
        if (r >= 0 && !m.roomIndoor[r] && d == BuildDef.CAMPFIRE && (weather == Weather.RAIN || weather == Weather.THUNDER)) b.fuel = max(0f, b.fuel - 2f)
    }
    val next = Thermal.step(m.roomTemp, m.roomSize, m.roomIndoor, heat, m.linkA, m.linkB, m.linkC, out)
    next.copyInto(m.roomTemp)
}

/** Fertility factor: the reference's sensitivity blend (RimWorld/PlantUtility.cs:358). */
internal fun fertilityGrowthFactor(fertility: Float): Float =
    fertility * PLANT_FERTILITY_SENSITIVITY + (1f - PLANT_FERTILITY_SENSITIVITY)

/**
 * Temperature factor for a plant whose optimal band is [optimalLow] to [optimalHigh] degrees. Zero at the growth limits, a
 * straight ramp to full inside the band (RimWorld/PlantUtility.cs:370-381). The limits are the band's default distances.
 */
internal fun temperatureGrowthFactor(t: Float, optimalLow: Float, optimalHigh: Float): Float {
    val growthMin = optimalLow - GROWTH_BELOW_OPTIMAL_C
    val growthMax = optimalHigh + GROWTH_ABOVE_OPTIMAL_C
    return when {
        t <= growthMin || t >= growthMax -> 0f
        t < optimalLow -> (t - growthMin) / (optimalLow - growthMin)
        t <= optimalHigh -> 1f
        else -> (growthMax - t) / (growthMax - optimalHigh)
    }
}

/** Light factor: a ramp from [GROW_MIN_GLOW] to [GROW_OPTIMAL_GLOW] (RimWorld/PlantUtility.cs:361-367). */
internal fun lightGrowthFactor(glow: Float): Float = ((glow - GROW_MIN_GLOW) / (GROW_OPTIMAL_GLOW - GROW_MIN_GLOW)).coerceIn(0f, 1f)

/**
 * The glow a cell gives a plant: the daylight, or the cell's artificial light when that is brighter. [cellLight] is the cell's
 * light from [lightTick]: the daylight (less under a natural roof, or 85% of it indoors), with a lamp's or a fire's light on
 * top where it is brighter. Taking the larger of the two keeps the glow on the ramp of [lightGrowthFactor] instead of stepping
 * to full glow when the cell light passes half. Mapping the daylight to glow is Stillmere's (C); the glow ramp is
 * RimWorld/PlantUtility.cs:361-367.
 */
internal fun plantGlow(daylit: Float, cellLight: Float): Float = max(daylit, cellLight)

/** Whether a grower may sow this crop on a cell at [t] degrees: strictly inside the growth limits (RimWorld/PlantUtility.cs:21-33). */
internal fun growthSeasonOk(t: Float, type: PlantType): Boolean =
    t > type.minTemp - GROWTH_BELOW_OPTIMAL_C && t < type.maxTemp + GROWTH_ABOVE_OPTIMAL_C

/**
 * Growth added in one slow tick: the grow days set the full-rate time, and the three factors scale it (RimWorld/Plant.cs:346,
 * 289-302). [fertFactor] is the fertility factor from [fertilityGrowthFactor], or a grow tray's factor. At full factors this is
 * one grow-time per day, with no extra multiplier.
 */
internal fun growthPerSlowTick(type: PlantType, fertFactor: Float, t: Float, glow: Float): Float {
    val days = if (type.growDays <= 0f) 1f else type.growDays
    val factor = fertFactor * temperatureGrowthFactor(t, type.minTemp, type.maxTemp) * lightGrowthFactor(glow)
    return Game.SLOW_TICK.toFloat() / (days * TICKS_PER_DAY) * factor
}

/**
 * Harvest yield before random rounding (RimWorld/Plant.cs:932-947). A plant at or below the harvest minimum is not harvestable,
 * so it yields nothing (RimWorld/Plant.cs:115, and the zero returns at 917-919 and 934-936). Above it the yield rises from about
 * half at the minimum to full at full growth. Health is not modelled and the difficulty factor is 1.
 */
internal fun harvestYieldExact(count: Int, growth: Float): Float {
    if (growth <= HARVEST_MIN_GROWTH) return 0f
    val share = ((growth - HARVEST_MIN_GROWTH) / (1f - HARVEST_MIN_GROWTH)).coerceIn(0f, 1f)
    return count * (0.5f + 0.5f * share)
}

private fun Game.plantsTick(out: Float) {
    val m = map
    val daylit = daylight()
    for (i in 0 until m.size) {
        val pl = m.plant[i] ?: continue
        val t = m.tempAt(i, out)
        if (pl.type.crop || pl.type.isTree || pl.type.regrows) {
            pl.age += Game.SLOW_TICK.toInt()
            if (pl.growth >= 1f) {
                if (pl.type.crop && t < -3f && rng.chance(0.002f)) m.plant[i] = null
                continue
            }
            if (t < -2f && pl.type.crop) {
                if (rng.chance(0.15f)) {
                    m.plant[i] = null
                    if (m.zoneKind(i) == ZoneKind.GROWING) say("Frost killed a ${pl.type.label.lowercase()} plant.", 2)
                }
                continue
            }
            // The glow is the daylight, or a brighter lamp or fire on the cell (see plantGlow). A powered grow tray takes its own
            // factor in place of the terrain fertility. Growth is fertility x temperature x light over the grow days, with no
            // weather or trait term (RimWorld/Plant.cs:289-302, 346).
            val glow = plantGlow(daylit, m.light[i])
            val fertFactor = if (m.building[i]?.def == BuildDef.GROW_TRAY && m.building[i]?.powered == true) GROW_TRAY_GROWTH_FACTOR
                else fertilityGrowthFactor(m.terrain[i].fertility)
            val step = growthPerSlowTick(pl.type, fertFactor, t, glow)
            if (step <= 0f) continue
            pl.growth = min(1f, pl.growth + step)
        }
    }
    // Saplings: forests slowly spread.
    if (tick % tk(1000L) == 0L) {
        repeat(6) {
            val i = rng.int(m.size)
            if (m.plant[i] != null || m.building[i] != null || m.floor[i] != null || m.zoneId[i] != 0 || m.items[i] != null) return@repeat
            val t = m.terrain[i]
            if (t.fertility < 0.3f || m.roofed(i)) return@repeat
            val x = i % m.w; val y = i / m.w
            if (abs(x - homeX) < 6 && abs(y - homeY) < 6) return@repeat
            var parent: PlantType? = null
            for (dy in -3..3) for (dx in -3..3) {
                if (!m.inB(x + dx, y + dy)) continue
                val q = m.plant[m.idx(x + dx, y + dy)]
                if (q != null && q.type.isTree && q.growth >= 1f) parent = q.type
            }
            if (parent != null && rng.chance(0.25f * m.biome.treeDensity)) m.plant[i] = Plant(parent!!, x, y, 0.05f)
        }
    }
}

/** Manufactured goods left out in the open wear away; roofs, and especially rain, matter. */
private fun deteriorates(t: ItemType) = t.isGear || t == ItemType.CLOTH || t == ItemType.LEATHER || t == ItemType.WOOL || t == ItemType.WISPSILK ||
    t == ItemType.MEDS_LEAF_POULTICE || t == ItemType.MEDS_STERILE_DRESSING || t == ItemType.FEED_PELLETS || t.cat == ItemCat.ART

private fun Game.deteriorationTick() {
    val m = map
    val wet = weather == Weather.RAIN || weather == Weather.THUNDER || weather == Weather.SNOW
    val gone = ArrayList<Int>()
    for ((i, s) in m.items) {
        if (s.corpseOf != null || !deteriorates(s.type) || m.roofed(i) || m.building[i]?.def?.sleeps == true) continue
        s.hp -= Game.SLOW_TICK.toFloat() / (TICKS_PER_DAY * 25f) * (if (wet) 3f else 1f)
        if (s.hp <= 0f) {
            if (s.count <= 1 || s.type.isGear) gone.add(i) else { s.count = max(1, s.count - max(1, s.count / 6)); s.hp = 0.6f }
        }
    }
    for (i in gone) { val s = m.items.remove(i) ?: continue; if (s.type.isGear) say("A ${s.type.label.lowercase()} fell apart in the weather.", 0) }
}

/** Rot rate by temperature: none at or below 0 C, a straight ramp to full at 10 C (Verse/GenTemperature.cs:459-470). */
internal fun rotTemperatureFactor(t: Float): Float = when {
    t <= 0f -> 0f
    t >= 10f -> 1f
    else -> t / 10f
}

/** Share of a building's footprint that is not roofed. A roofed cell gives a sun array nothing (RimWorld/CompPowerPlantSolar.cs:19). */
internal fun GameMap.unroofedShare(b: Building): Float {
    var total = 0
    var open = 0
    for (yy in b.y until b.y + b.fh) for (xx in b.x until b.x + b.fw) {
        total++
        if (inB(xx, yy) && !roofed(idx(xx, yy))) open++
    }
    return if (total == 0) 1f else open / total.toFloat()
}

private fun Game.spoilTick(out: Float) {
    val m = map
    val dt = Game.SLOW_TICK.toFloat()
    deteriorationTick()
    var spoiled = 0
    val rem = ArrayList<Int>()
    for ((i, s) in m.items) {
        val days = if (s.corpseOf != null) CORPSE_ROT_DAYS else s.type.spoilDays
        if (days <= 0f) continue
        val f = rotTemperatureFactor(m.tempAt(i, out))
        if (f <= 0f) continue
        s.rot += dt / (days * TICKS_PER_DAY) * f
        if (s.rot >= 1f) { rem.add(i); spoiled += s.count }
    }
    for (i in rem) {
        val s = m.items.remove(i) ?: continue
        if (s.corpseOf != null) { m.filth[i] = min(4, m.filth[i] + 2).toByte() }
    }
    if (spoiled > 0 && rem.any { m.items[it] == null }) { /* silent: rot is routine */ }
}

private fun Game.roomStats() = map.computeRoomStats()

fun impressLabel(score: Float): String = when {
    score < 4f -> "awful"
    score < 8f -> "dull"
    score < 12f -> "mediocre"
    score < 17f -> "decent"
    score < 24f -> "slightly impressive"
    score < 34f -> "somewhat impressive"
    score < 50f -> "very impressive"
    score < 80f -> "extremely impressive"
    else -> "unbelievably impressive"
}

fun impressMood(score: Float): Float = when {
    score < 4f -> -0.1f
    score < 8f -> -0.04f
    score < 12f -> 0f
    score < 17f -> 0.03f
    score < 24f -> 0.06f
    score < 34f -> 0.09f
    score < 50f -> 0.12f
    score < 80f -> 0.16f
    else -> 0.2f
}

// ----------------------------------------------------------------------------------- traps and turrets

/** The weapon a turret building fires, from the weapon table. Null for buildings that do not shoot. */
internal fun turretWeapon(d: BuildDef): Weapon? = when (d) {
    BuildDef.TURRET -> Weapon.PIVOT_CANNON
    BuildDef.SENTRY_POD -> Weapon.SENTRY_POD_GUN
    BuildDef.LOBBING_TUBE -> Weapon.LOBBED_CHARGE
    else -> null
}

fun Game.turretsTick() {
    for (b in map.buildings()) {
        if (b == null || !b.built) continue
        val w = turretWeapon(b.def) ?: continue
        if (b.cooldown > 0) { b.cooldown -= tk(10); continue }
        if (b.def == BuildDef.LOBBING_TUBE) { mortarFire(b); continue }
        var best: Pawn? = null
        var bd = w.range * w.range
        for (h in pawns) {
            if (!h.hostile || !h.alive || h.downed) continue
            val d = ((h.x - b.x) * (h.x - b.x) + (h.y - b.y) * (h.y - b.y)).toFloat()
            if (d < bd && map.lineOfSight(b.x, b.y, h.x, h.y)) { best = h; bd = d }
        }
        if (best != null) {
            b.cooldown = w.cooldownTicks
            repeat(w.burst) {
                val hit = rng.chance(w.accuracy)
                shots.add(Shot(b.x.toFloat(), b.y.toFloat(), best.x.toFloat(), best.y.toFloat(), tick + tk(6) + it * tk(2), hit))
                if (hit) dealDamage(best, w.kind, w.damage, penetrationOf(w), null)
            }
        }
    }
}

private fun Game.mortarFire(b: Building) {
    if (b.shells <= 0) return
    var best: Pawn? = null
    var bd = 55f * 55f
    for (h in pawns) {
        if (!h.hostile || !h.alive) continue
        val d = ((h.x - b.x) * (h.x - b.x) + (h.y - b.y) * (h.y - b.y)).toFloat()
        if (d < bd && d > 64f) { best = h; bd = d }
    }
    if (best == null) return
    val w = Weapon.LOBBED_CHARGE
    b.shells--
    b.cooldown = w.cooldownTicks
    // A shell lands close to where it was aimed when it hits, and wide when it misses.
    val spread = if (rng.chance(w.accuracy)) 2 else 4
    val tx = (best.x + rng.range(-spread, spread)).coerceIn(1, map.w - 2)
    val ty = (best.y + rng.range(-spread, spread)).coerceIn(1, map.h - 2)
    shots.add(Shot(b.x.toFloat(), b.y.toFloat(), tx.toFloat(), ty.toFloat(), tick + tk(12), true, 1))
    explode(tx, ty, w.aoe, w.damage, null, false)
}

fun Game.trapsTick() {
    for (b in map.buildings()) {
        if (b == null || !b.built || !b.def.trap) continue
        for (h in pawns) {
            if (!h.alive || h.downed || h.x != b.x || h.y != b.y) continue
            if (h.faction == Faction.PLAYER && !h.isAnimal && rng.chance(0.85f)) continue // colonists know where traps are
            if (h.faction == Faction.PLAYER && h.isAnimal && rng.chance(0.5f)) continue
            if (b.def == BuildDef.TRAP_SPIKE) {
                dealDamage(h, DamageKind.STAB, 18f)
                dealDamage(h, DamageKind.STAB, 12f)
            } else {
                dealDamage(h, DamageKind.CRUSH, 34f)
            }
            say("A ${b.def.label.lowercase()} caught ${h.name}.", if (h.hostile) 1 else 2)
            if (rng.chance(0.5f)) { map.removeBuilding(b) }
            break
        }
    }
}

/**
 * Powered production that happens without a colonist: a subsoil auger brings up whatever ore lies under it (rough rocks
 * when there is none), and a pyre house burns the nearest corpse within reach.
 */
internal fun Game.productionTick() {
    for (b in map.buildings()) {
        if (b == null || !b.built || !b.powered) continue
        if (b.def == BuildDef.SUBSOIL_AUGER) {
            if (b.cooldown > 0) { b.cooldown -= Game.SLOW_TICK.toInt(); continue }
            b.cooldown = 2 * TICKS_PER_DAY
            val ore = map.ore[map.idx(b.x, b.y)]
            val item = ore.item
            if (ore != Ore.NONE && item != null) map.drop(item, max(1, rng.range(ore.yieldMin, ore.yieldMax) / 2), b.x, b.y)
            else map.drop(ItemType.STONE_CHUNK, rng.range(1, 2), b.x, b.y)
        } else if (b.def == BuildDef.PYRE_HOUSE) {
            val body = map.items.entries.firstOrNull { (i, s) ->
                s.corpseOf != null && distance(map.xOf(i), map.yOf(i), b.x, b.y) <= 5f
            } ?: continue
            map.items.remove(body.key)
            say("The pyre house burned the body of ${body.value.corpseOf}.", 0)
        }
    }
}
