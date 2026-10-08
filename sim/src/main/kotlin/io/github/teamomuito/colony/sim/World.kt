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
    if (b != null) f = max(f, b.def.flam)
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
    val rain = weather == Weather.RAIN || weather == Weather.THUNDER
    val snow = weather == Weather.SNOW
    for ((i, f) in map.fires.entries.toList()) {
        f.age += 4
        val flam = cellFlammability(i)
        if (rain) f.intensity -= 0.01f
        if (snow) f.intensity -= 0.004f
        if (flam <= 0.01f) f.intensity -= 0.006f else f.intensity = min(1.2f, f.intensity + 0.002f * flam)
        if (f.age % 20 == 0) {
            val b = map.building[i]
            if (b != null && b.def.flam > 0f) {
                b.hp -= 4f * f.intensity * b.def.flam
                if (b.hp <= 0f) {
                    map.building[i] = null; map.roomDirty = true
                    say("A ${b.def.label.lowercase()} burned down.", 2)
                }
            }
            val pl = map.plant[i]
            if (pl != null && pl.type.flammable > 0f && rng.chance(0.25f * f.intensity)) { map.plant[i] = null }
            val st = map.items[i]
            if (st != null && st.type.flammable > 0f && st.corpseOf == null) {
                st.count -= max(1, (st.count * 0.15f * f.intensity).toInt())
                if (st.count <= 0) map.items.remove(i)
            }
            for (p in pawns) if (p.alive && p.x == i % map.w && p.y == i / map.w) dealDamage(p, DamageKind.BURN, 3.5f * f.intensity)
        }
        // Spread.
        if (f.age % 16 == 0) {
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
    blasts.add(Blast(x, y, radius, tick + 14))
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
            if (b.hp <= 0f) { map.building[i] = null; map.roomDirty = true }
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
        dealDamage(p, DamageKind.BLAST, damage * fall, 0f, source)
        dealDamage(p, DamageKind.BLAST, damage * 0.5f * fall, 0f, source)
        if (fire) dealDamage(p, DamageKind.BURN, 4f * fall, 0f, source)
    }
}

// ----------------------------------------------------------------------------------- power

fun Game.powerTick() {
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
        for (b in m.building) if (b != null && b.def.isPowered) b.powered = false
        return
    }
    // Union-find over components that share a device.
    val parent = IntArray(n) { it }
    fun find(a: Int): Int { var x = a; while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }; return x }
    val devices = ArrayList<Building>()
    val attach = ArrayList<IntArray>()
    for (b in m.building) {
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
    val dt = 250f / TICKS_PER_DAY
    for ((k, b) in devices.withIndex()) {
        val net = nets.getOrPut(find(attach[k][0])) { Net() }
        val d = b.def
        if (d == BuildDef.BATTERY) { net.cap += 600f; net.stored += b.charge; net.batteries.add(b); continue }
        if (d.producesPower) {
            var out = 0f
            when (d) {
                BuildDef.SOLAR_PANEL -> out = d.power * daylight() * (if (ps.flare) 0f else 1f)
                BuildDef.WIND_TURBINE -> out = d.power * windFactor
                BuildDef.WOOD_GENERATOR -> if (b.fuel > 0f) { out = d.power; b.fuel = max(0f, b.fuel - 250f / TICKS_PER_HOUR * 0.8f) }
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
    for (b in m.building) if (b != null && b.def.consumesPower && b !in onNet) b.powered = false
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
    // Toxic fallout hurts everybody outside and kills plants.
    if (toxicFalloutUntil > tick) {
        for (p in pawns) if (p.alive && !p.isAnimal && !m.roofed(m.idx(p.x, p.y))) {
            val h = addHediff(p, HediffKind.TOXIC_BUILDUP, 0f)
            h.severity = min(1f, h.severity + 0.0035f)
        }
        for (i in 0 until m.size) { val pl = m.plant[i]; if (pl != null && !m.roofed(i) && rng.chance(0.002f)) m.plant[i] = null }
    }
    // Filth settles in busy rooms slowly.
    if (tick % 2000L == 0L) for (p in pawns) if (p.alive && !p.isAnimal && rng.chance(0.3f)) {
        val i = m.idx(p.x, p.y)
        if (m.roofed(i) && m.filth[i] < 4) m.filth[i] = (m.filth[i] + 1).toByte()
    }
    // Windblown fire starts.
    windFactor = (windFactor + (rng.float() - 0.5f) * 0.12f).coerceIn(0.15f, 1.35f)
    // Hunger of tamed animals: hay and kibble are consumed in animalTick.
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
    weatherUntil = tick + rng.range(2500, 9000)
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
        shots.add(Shot(x.toFloat(), 0f, x.toFloat(), y.toFloat(), tick + 8, true, 2))
    }
}

private fun Game.lightTick() {
    val m = map
    val day = daylight()
    for (i in 0 until m.size) {
        m.light[i] = if (m.natRoof[i]) 0f else if (m.roomIndoorAt(i)) day * 0.85f else day
    }
    for (b in m.building) {
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

private fun Game.roomClimate(out: Float) {
    val m = map
    val heat = FloatArray(m.roomTemp.size)
    for (b in m.building) {
        if (b == null || !b.built) continue
        val d = b.def
        if (d.heat == 0f) continue
        val i = m.idx(b.x, b.y)
        val r = m.roomId[i]
        if (d.power != 0f) {
            if (!b.powered) continue
            if (r >= 0) {
                val t = m.roomTemp[r]
                if (d.heat > 0f && t < 21f) heat[r] += d.heat
                if (d.heat < 0f && t > 21f) heat[r] += d.heat
            }
            continue
        }
        var active = false
        when {
            d.fuelCap > 0f -> {
                if (b.fuel > 0f) {
                    val use = d == BuildDef.CAMPFIRE || d == BuildDef.TORCH_LAMP || b.inUse > 0 || d == BuildDef.WOOD_GENERATOR
                    if (use) { b.fuel = max(0f, b.fuel - 0.25f); active = true }
                    if (b.inUse > 0) b.inUse = max(0, b.inUse - 250)
                }
            }
            else -> active = true
        }
        if (active && r >= 0 && m.roomIndoor[r]) heat[r] += d.heat
        // Weather puts out outdoor fires.
        if (r >= 0 && !m.roomIndoor[r] && d == BuildDef.CAMPFIRE && (weather == Weather.RAIN || weather == Weather.THUNDER)) b.fuel = max(0f, b.fuel - 2f)
    }
    for (r in m.roomTemp.indices) {
        if (!m.roomIndoor[r]) { m.roomTemp[r] = out; continue }
        val sz = max(1, m.roomSize[r])
        m.roomTemp[r] += (out - m.roomTemp[r]) * 0.04f + heat[r] / sz * 1.2f
        m.roomTemp[r] = m.roomTemp[r].coerceIn(-60f, 80f)
    }
    // Underground rooms hold a mild constant.
    for (i in 0 until m.size) {
        val r = m.roomId[i]
        if (r >= 0 && m.roomIndoor[r] && m.natRoof[i] && m.roomSize[r] < 400 && heat[r] <= 0f) {
            val target = (out + 14f) / 2f
            m.roomTemp[r] += (target - m.roomTemp[r]) * 0.01f
        }
    }
}

private fun Game.plantsTick(out: Float) {
    val m = map
    val light = daylight()
    for (i in 0 until m.size) {
        val pl = m.plant[i] ?: continue
        val t = m.tempAt(i, out)
        val fert = m.terrain[i].fertility.let { f -> if (m.building[i]?.def == BuildDef.HYDROPONICS && m.building[i]?.powered == true) 2.0f else f }
        if (pl.type.crop || pl.type.isTree) {
            pl.age += 250
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
            val lit = light > 0.2f || m.light[i] > 0.5f
            if (t < pl.type.minTemp || t > pl.type.maxTemp || !lit) continue
            val days = if (pl.type.growDays <= 0f) 1f else pl.type.growDays
            var rate = 1f / (days * TICKS_PER_DAY * 0.55f) * 250f * max(0.2f, fert)
            if (weather == Weather.RAIN) rate *= 1.1f
            if (Trait.GREEN_THUMB in pawns.firstOrNull { it.colonist }?.traits.orEmpty()) rate *= 1f
            pl.growth = min(1f, pl.growth + rate)
        }
    }
    // Saplings: forests slowly spread.
    if (tick % 1000L == 0L) {
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

private fun Game.spoilTick(out: Float) {
    val m = map
    val dt = 250f
    var spoiled = 0
    val rem = ArrayList<Int>()
    for ((i, s) in m.items) {
        val days = if (s.corpseOf != null) 2.2f else s.type.spoilDays
        if (days <= 0f) continue
        val t = m.tempAt(i, out)
        val f = when {
            t <= 0f -> 0f
            t < 10f -> 0.2f + t / 10f * 0.4f
            t < 20f -> 0.6f + (t - 10f) / 10f * 0.4f
            else -> 1f + (t - 20f) / 20f
        }
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

private fun Game.roomStats() {
    val m = map
    val n = m.roomTemp.size
    if (n == 0) return
    val beauty = FloatArray(n)
    val filth = FloatArray(n)
    val wealth = FloatArray(n)
    val beds = IntArray(n); val ownedBeds = IntArray(n); val tables = IntArray(n); val chairs = IntArray(n); val joy = IntArray(n)
    val hosp = IntArray(n); val prison = IntArray(n); val bench = IntArray(n); val kitchen = IntArray(n)
    for (i in 0 until m.size) {
        val r = m.roomId[i]
        if (r < 0 || !m.roomIndoor[r]) continue
        var b = 0f
        val fl = m.floor[i]
        b += fl?.beauty ?: -0.2f
        val bd = m.building[i]
        if (bd != null && bd.built) {
            b += bd.def.beauty * bd.quality.mult
            wealth[r] += bd.def.totalCost
            when {
                bd.def.sleeps -> { beds[r]++; if (bd.ownerId >= 0) ownedBeds[r]++; if (bd.def.medical) hosp[r]++; if (bd.prisonerBed) prison[r]++ }
                bd.def == BuildDef.TABLE -> tables[r]++
                bd.def == BuildDef.CHAIR || bd.def == BuildDef.STOOL -> chairs[r]++
                bd.def.joy > 0f -> joy[r]++
                bd.def == BuildDef.STOVE_FUEL || bd.def == BuildDef.STOVE_ELEC -> kitchen[r]++
                bd.def.workbench -> bench[r]++
            }
        }
        val s = m.items[i]
        if (s != null) wealth[r] += s.type.value * s.count * 0.5f
        val pl = m.plant[i]
        if (pl != null && !pl.type.isTree) b += 0.4f
        b -= m.filth[i] * 0.9f
        b += if (m.terrain[i] == Terrain.ROCK) 0f else if (m.natRoof[i]) -0.1f else 0f
        beauty[r] += b
        filth[r] += m.filth[i]
    }
    for (r in 0 until n) {
        if (!m.roomIndoor[r]) continue
        val sz = max(1, m.roomSize[r])
        m.roomBeauty[r] = beauty[r] / sz
        m.roomClean[r] = -filth[r] / sz
        m.roomWealth[r] = wealth[r]
        val space = min(sz, 60)
        // Impressiveness: beauty, space, and wealth together.
        val score = m.roomBeauty[r] * 6f * min(1f, sz / 14f) + space * 0.18f + min(wealth[r] / 450f, 9f) + m.roomClean[r] * 2.5f
        m.roomImpress[r] = score
        m.roomRole[r] = when {
            prison[r] > 0 -> 5
            hosp[r] > 0 -> 4
            ownedBeds[r] + beds[r] >= 3 -> 2
            beds[r] > 0 -> 1
            tables[r] > 0 && chairs[r] > 0 -> 3
            joy[r] > 0 -> 6
            kitchen[r] > 0 -> 8
            bench[r] > 0 -> 7
            else -> 0
        }
    }
}

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

fun Game.turretsTick() {
    for (b in map.building) {
        if (b == null || !b.built) continue
        if (b.def != BuildDef.TURRET && b.def != BuildDef.MORTAR) continue
        if (b.cooldown > 0) { b.cooldown -= 10; continue }
        if (b.def == BuildDef.MORTAR) { mortarFire(b); continue }
        var best: Pawn? = null
        var bd = 28f * 28f
        for (h in pawns) {
            if (!h.hostile || !h.alive || h.downed) continue
            val d = ((h.x - b.x) * (h.x - b.x) + (h.y - b.y) * (h.y - b.y)).toFloat()
            if (d < bd && map.lineOfSight(b.x, b.y, h.x, h.y)) { best = h; bd = d }
        }
        if (best != null) {
            b.cooldown = 55
            repeat(3) {
                val hit = rng.chance(0.62f)
                shots.add(Shot(b.x.toFloat(), b.y.toFloat(), best.x.toFloat(), best.y.toFloat(), tick + 6 + it * 2, hit))
                if (hit) dealDamage(best, DamageKind.BULLET, 10f * (0.8f + rng.float() * 0.4f), 0f, null)
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
    b.shells--
    b.cooldown = 260
    val tx = (best.x + rng.range(-2, 2)).coerceIn(1, map.w - 2)
    val ty = (best.y + rng.range(-2, 2)).coerceIn(1, map.h - 2)
    shots.add(Shot(b.x.toFloat(), b.y.toFloat(), tx.toFloat(), ty.toFloat(), tick + 12, true, 1))
    explode(tx, ty, 2.4f, 34f, null, false)
}

fun Game.trapsTick() {
    for (b in map.building.toList()) {
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
            if (rng.chance(0.5f)) { map.building[map.idx(b.x, b.y)] = null }
            break
        }
    }
}
