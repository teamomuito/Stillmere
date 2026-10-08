package io.github.teamomuito.colony.sim

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Binary save format. Jobs and reservations are not saved; pawns simply re-think after loading. */
object SaveGame {
    private const val VERSION = 11

    private fun DataOutputStream.opt(s: String?) { writeBoolean(s != null); if (s != null) writeUTF(s) }
    private fun DataInputStream.opt(): String? = if (readBoolean()) readUTF() else null

    fun write(g: Game): ByteArray {
        val bytes = ByteArrayOutputStream()
        val o = DataOutputStream(bytes)
        o.writeInt(VERSION)
        o.writeLong(g.seed); o.writeLong(g.tick); o.writeUTF(g.colonyName)
        o.writeInt(g.scenario.ordinal); o.writeInt(g.storyteller.ordinal); o.writeInt(g.difficulty.ordinal)
        o.writeInt(g.nextPawnId); o.writeInt(g.raidCounter); o.writeInt(g.raidsSurvived)
        o.writeLong(g.nextRaid); o.writeLong(g.nextWanderer); o.writeLong(g.nextPod); o.writeLong(g.nextTempEvent); o.writeLong(g.nextMisc); o.writeLong(g.nextTrader)
        o.writeBoolean(g.raidActive); o.writeInt(g.raidStartCount); o.writeLong(g.raidEnds); o.writeLong(raidStartedAt)
        o.writeInt(g.weather.ordinal); o.writeLong(g.weatherUntil)
        o.writeFloat(g.tempOffset); o.writeLong(g.tempEventUntil); o.writeUTF(g.tempEventName)
        o.writeLong(g.solarFlareUntil); o.writeLong(g.toxicFalloutUntil); o.writeLong(g.eclipseUntil)
        o.writeInt(g.homeX); o.writeInt(g.homeY); o.writeFloat(g.windFactor)
        o.writeInt(g.statsKilled); o.writeInt(g.silverEarned); o.writeInt(g.hintBits)
        o.writeInt(g.graveyard.size); for (s in g.graveyard) o.writeUTF(s)
        o.writeInt(g.researchCurrent?.ordinal ?: -1)
        o.writeInt(g.researchDone.size); for (r in g.researchDone) o.writeInt(r.ordinal)
        o.writeInt(g.researchProgress.size); for ((r, v) in g.researchProgress) { o.writeInt(r.ordinal); o.writeFloat(v) }
        o.writeInt(g.traders.size)
        for (t in g.traders) {
            o.writeInt(t.pawnId); o.writeUTF(t.name); o.writeLong(t.arrival); o.writeLong(t.leaveAt); o.writeInt(t.silver)
            o.writeInt(t.stock.size); for ((k, v) in t.stock) { o.writeInt(k.ordinal); o.writeInt(v) }
        }

        val m = g.map
        o.writeInt(m.w); o.writeInt(m.h); o.writeInt(m.biome.ordinal)
        for (i in 0 until m.size) {
            o.writeByte(m.terrain[i].ordinal); o.writeByte(m.ore[i].ordinal); o.writeByte(m.rockType[i].ordinal)
            o.writeByte(m.floor[i]?.ordinal ?: -1); o.writeByte(m.floorQuality[i].ordinal)
            o.writeInt(m.zoneId[i]); o.writeByte(m.desig[i].toInt()); o.writeByte(m.filth[i].toInt())
            o.writeBoolean(m.natRoof[i]); o.writeBoolean(m.conduit[i]); o.writeByte((m.snow[i] * 100).toInt())
        }
        for (a in 0 until 3) { o.writeUTF(m.areaNames[a]); for (c in 0 until m.size) o.writeBoolean(m.areas[a][c]) }
        o.writeInt(m.peekNextZone())
        o.writeInt(m.zones.size)
        for (z in m.zones.values) {
            o.writeInt(z.id); o.writeInt(z.kind); o.writeUTF(z.name); o.writeInt(z.priority); o.writeInt(z.crop.ordinal)
            o.writeBoolean(z.sow); o.writeInt(z.minQuality.ordinal); o.writeInt(z.cells)
            for (b in z.allowed) o.writeBoolean(b)
        }
        val builds = m.building.filterNotNull()
        o.writeInt(builds.size)
        for (b in builds) {
            o.writeInt(b.def.ordinal); o.writeShort(b.x); o.writeShort(b.y); o.writeBoolean(b.built)
            for (d in b.delivered) o.writeInt(d)
            o.writeFloat(b.progress); o.writeFloat(b.hp); o.writeInt(b.ownerId); o.writeFloat(b.fuel)
            o.writeByte(b.quality.ordinal); o.writeBoolean(b.forbidden); o.writeFloat(b.charge); o.writeInt(b.shells)
            o.writeBoolean(b.prisonerBed); o.writeInt(b.occupant)
            o.writeInt(b.bills.size)
            for (bill in b.bills) {
                o.writeInt(bill.recipe.ordinal); o.writeInt(bill.mode.ordinal); o.writeInt(bill.target); o.writeInt(bill.done)
                o.writeBoolean(bill.paused); o.writeInt(bill.minSkill)
                val al = bill.allowedItems
                o.writeBoolean(al != null)
                if (al != null) { o.writeInt(al.size); for (t in al) o.writeInt(t.ordinal) }
            }
        }
        val plants = m.plant.filterNotNull()
        o.writeInt(plants.size)
        for (p in plants) { o.writeInt(p.type.ordinal); o.writeShort(p.x); o.writeShort(p.y); o.writeFloat(p.growth); o.writeInt(p.age) }
        o.writeInt(m.items.size)
        for (s in m.items.values) {
            o.writeInt(s.type.ordinal); o.writeShort(s.x); o.writeShort(s.y); o.writeInt(s.count)
            o.writeByte(s.quality.ordinal); o.writeFloat(s.rot); o.writeFloat(s.hp); o.writeBoolean(s.forbidden)
            o.opt(s.corpseOf); o.writeInt(s.corpseRace?.ordinal ?: -1); o.writeBoolean(s.corpseColonist); o.writeInt(s.corpseAge)
        }
        o.writeInt(m.fires.size)
        for ((i, f) in m.fires) { o.writeInt(i); o.writeFloat(f.intensity) }
        o.writeInt(g.worldBiome.ordinal); o.writeInt(g.world.homeTile)

        val pawns = g.pawns.filter { it.alive }
        o.writeInt(pawns.size)
        for (p in pawns) writePawn(o, p, g.tick)
        // World state
        for (g2 in g.world.goodwill) o.writeInt(g2)
        o.writeInt(g.world.settlements.size)
        for (st in g.world.settlements) {
            o.writeInt(st.silver); o.writeLong(st.stockTick); o.writeLong(st.destroyedUntil)
            o.writeInt(st.stock.size); for ((k, v) in st.stock) { o.writeInt(k.ordinal); o.writeInt(v) }
            val r = st.request
            o.writeBoolean(r != null)
            if (r != null) { o.writeInt(r.type.ordinal); o.writeInt(r.count); o.writeInt(r.reward); o.writeLong(r.expires) }
        }
        o.writeInt(g.world.nextSiteId)
        o.writeInt(g.world.sites.size)
        for (s2 in g.world.sites) { o.writeInt(s2.id); o.writeInt(s2.tile); o.writeInt(s2.kind); o.writeInt(s2.factionId); o.writeInt(s2.reward); o.writeLong(s2.expires); o.writeFloat(s2.strength); o.writeUTF(s2.name) }
        o.writeInt(g.nextCaravanId)
        o.writeInt(g.caravans.size)
        for (c in g.caravans) {
            o.writeInt(c.id); o.writeUTF(c.name); o.writeInt(c.tile); o.writeInt(c.destination); o.writeFloat(c.progress)
            o.writeBoolean(c.resting); o.writeBoolean(c.goingHome); o.writeUTF(c.lastEvent); o.writeBoolean(c.forageNote)
            o.writeInt(c.route.size); for (t in c.route) o.writeInt(t)
            o.writeInt(c.inventory.size); for ((k, v) in c.inventory) { o.writeInt(k.ordinal); o.writeInt(v) }
            val ms = c.members.filter { it.alive }
            o.writeInt(ms.size); for (p in ms) writePawn(o, p, g.tick)
        }
        val recent = g.log.takeLast(60)
        o.writeInt(recent.size)
        for (l in recent) { o.writeLong(l.tick); o.writeUTF(l.text); o.writeInt(l.level) }
        o.flush()
        return bytes.toByteArray()
    }

    private fun writePawn(o: DataOutputStream, p: Pawn, now: Long) {
        o.writeInt(p.id); o.writeUTF(p.name); o.writeInt(p.race.ordinal); o.writeInt(p.faction.ordinal)
        o.writeShort(p.x); o.writeShort(p.y)
        o.writeBoolean(p.downed); o.writeBoolean(p.drafted); o.writeBoolean(p.prisoner); o.writeBoolean(p.hostileFlag)
        o.writeInt(p.age); o.writeBoolean(p.female); o.writeFloat(p.mood)
        o.writeLong(p.breakUntil); o.writeInt(p.breakKind)
        o.writeFloat(p.food); o.writeFloat(p.rest); o.writeFloat(p.joy); o.writeFloat(p.bloodLoss)
        o.writeInt(p.careLevel); o.writeInt(p.foodPolicy); o.writeBoolean(p.allowDrugs)
        for (s in p.skill) o.writeInt(s)
        for (s in p.xp) o.writeFloat(s)
        for (s in p.passion) o.writeInt(s)
        for (s in p.priority) o.writeInt(s)
        o.writeInt(p.traits.size); for (t in p.traits) o.writeInt(t.ordinal)
        o.writeUTF(p.backstory); o.writeInt(p.incapable)
        o.writeInt(p.weaponItem?.ordinal ?: -1); o.writeInt(p.weaponQuality.ordinal)
        o.writeInt(p.apparel.size); for (w in p.apparel) { o.writeInt(w.type.ordinal); o.writeInt(w.quality.ordinal); o.writeFloat(w.hp) }
        o.writeInt(p.injuries.size)
        for (i in p.injuries) {
            o.writeInt(i.part); o.writeInt(i.kind.ordinal); o.writeFloat(i.severity); o.writeFloat(i.bleed)
            o.writeBoolean(i.tended); o.writeFloat(i.tendQuality); o.writeFloat(i.infection); o.writeBoolean(i.infectable)
            o.writeBoolean(i.permanent); o.writeBoolean(i.missing); o.writeBoolean(i.scar); o.writeUTF(i.implant); o.writeInt(i.age); o.writeFloat(i.immune)
        }
        o.writeInt(p.hediffs.size)
        for (h in p.hediffs) { o.writeInt(h.kind.ordinal); o.writeFloat(h.severity); o.writeFloat(h.immunity); o.writeBoolean(h.tended); o.writeFloat(h.tendQuality); o.writeInt(h.age); o.writeInt(h.part); o.writeInt(h.duration) }
        val th = p.thoughts.filter { it.expires > now }
        o.writeInt(th.size)
        for (t in th) { o.writeUTF(t.label); o.writeFloat(t.mood); o.writeLong(t.expires - now) }
        o.writeInt(p.opinion.size); for ((k, v) in p.opinion) { o.writeInt(k); o.writeInt(v) }
        o.writeInt(p.spouse); o.writeInt(p.lover)
        for (s in p.schedule) o.writeInt(s)
        o.writeInt(p.bedId); o.writeInt(p.homeTile)
        o.writeBoolean(p.tame); o.writeInt(p.master); o.writeInt(p.animalProductTimer); o.writeBoolean(p.manhunter)
        o.writeBoolean(p.huntMark); o.writeBoolean(p.tameMark); o.writeBoolean(p.slaughterMark)
        o.writeFloat(p.resistance); o.writeBoolean(p.escaping); o.writeInt(p.recruitMode)
        o.writeInt(p.raidId); o.writeInt(p.raidMode); o.writeInt(p.campX); o.writeInt(p.campY); o.writeBoolean(p.retreating)
        o.writeLong(p.escapeTick); o.writeBoolean(p.wanderer); o.writeLong(p.lastSocial); o.writeInt(p.wfaction); o.writeBoolean(p.ally); o.writeInt(p.birthday); o.writeInt(p.ageDays); o.writeInt(p.mother); o.writeInt(p.father); o.writeLong(p.pregnantUntil); o.writeInt(p.pregnantBy)
        o.writeInt(p.herdLeader)
        o.writeBoolean(p.refugee); o.writeInt(p.areaRestriction)
        o.writeInt(p.implants.size); for ((k, v) in p.implants) { o.writeInt(k); o.writeInt(v.ordinal) }
    }

    fun read(data: ByteArray): Game {
        val i = DataInputStream(ByteArrayInputStream(data))
        require(i.readInt() == VERSION) { "Unsupported save version" }
        val seed = i.readLong(); val tick = i.readLong(); val name = i.readUTF()
        val scenario = Scenario.entries[i.readInt()]; val story = Storyteller.entries[i.readInt()]; val diff = Difficulty.entries[i.readInt()]
        val nextPawn = i.readInt(); val raidCounter = i.readInt(); val survived = i.readInt()
        val nr = i.readLong(); val nw = i.readLong(); val np = i.readLong(); val nt = i.readLong(); val nm = i.readLong(); val ntr = i.readLong()
        val raidActive = i.readBoolean(); val raidStart = i.readInt(); val raidEnds = i.readLong(); val rsa = i.readLong()
        val weather = Weather.entries[i.readInt()]; val weatherUntil = i.readLong()
        val tempOffset = i.readFloat(); val tempUntil = i.readLong(); val tempName = i.readUTF()
        val flare = i.readLong(); val toxic = i.readLong(); val eclipse = i.readLong()
        val hx = i.readInt(); val hy = i.readInt(); val wind = i.readFloat()
        val killed = i.readInt(); val earned = i.readInt(); val hints = i.readInt()
        val grave = List(i.readInt()) { i.readUTF() }
        val cur = i.readInt()
        val done = List(i.readInt()) { Research.entries[i.readInt()] }
        val prog = List(i.readInt()) { Research.entries[i.readInt()] to i.readFloat() }
        val traderList = List(i.readInt()) {
            val t = TraderInfo(i.readInt(), i.readUTF(), i.readLong(), i.readLong())
            t.silver = i.readInt()
            repeat(i.readInt()) { t.stock[ItemType.entries[i.readInt()]] = i.readInt() }
            t
        }

        val w = i.readInt(); val h = i.readInt()
        val map = GameMap(w, h)
        map.biome = Biome.entries[i.readInt()]
        for (c in 0 until map.size) {
            map.terrain[c] = Terrain.entries[i.readByte().toInt()]
            map.ore[c] = Ore.entries[i.readByte().toInt()]
            map.rockType[c] = RockType.entries[i.readByte().toInt()]
            val f = i.readByte().toInt()
            map.floor[c] = if (f >= 0) BuildDef.entries[f] else null
            map.floorQuality[c] = Quality.entries[i.readByte().toInt()]
            map.zoneId[c] = i.readInt(); map.desig[c] = i.readByte(); map.filth[c] = i.readByte()
            map.natRoof[c] = i.readBoolean(); map.conduit[c] = i.readBoolean(); map.snow[c] = i.readByte() / 100f
        }
        for (a in 0 until 3) { map.areaNames[a] = i.readUTF(); for (c in 0 until map.size) map.areas[a][c] = i.readBoolean() }
        map.setNextZone(i.readInt())
        repeat(i.readInt()) {
            val z = Zone(i.readInt(), i.readInt())
            z.name = i.readUTF(); z.priority = i.readInt(); z.crop = PlantType.entries[i.readInt()]
            z.sow = i.readBoolean(); z.minQuality = Quality.entries[i.readInt()]; z.cells = i.readInt()
            for (k in z.allowed.indices) z.allowed[k] = i.readBoolean()
            map.zones[z.id] = z
        }
        repeat(i.readInt()) {
            val def = BuildDef.entries[i.readInt()]
            val x = i.readShort().toInt(); val y = i.readShort().toInt()
            val b = Building(def, x, y, i.readBoolean())
            for (k in b.delivered.indices) b.delivered[k] = i.readInt()
            b.progress = i.readFloat(); b.hp = i.readFloat(); b.ownerId = i.readInt(); b.fuel = i.readFloat()
            b.quality = Quality.entries[i.readByte().toInt()]; b.forbidden = i.readBoolean(); b.charge = i.readFloat(); b.shells = i.readInt()
            b.prisonerBed = i.readBoolean(); b.occupant = i.readInt()
            repeat(i.readInt()) {
                val bill = Bill(Recipe.entries[i.readInt()])
                bill.mode = BillMode.entries[i.readInt()]; bill.target = i.readInt(); bill.done = i.readInt()
                bill.paused = i.readBoolean(); bill.minSkill = i.readInt()
                if (i.readBoolean()) { val set = HashSet<ItemType>(); repeat(i.readInt()) { set.add(ItemType.entries[i.readInt()]) }; bill.allowedItems = set }
                b.bills.add(bill)
            }
            map.building[map.idx(x, y)] = b
        }
        repeat(i.readInt()) {
            val t = PlantType.entries[i.readInt()]
            val x = i.readShort().toInt(); val y = i.readShort().toInt()
            val pl = Plant(t, x, y, i.readFloat()); pl.age = i.readInt()
            map.plant[map.idx(x, y)] = pl
        }
        repeat(i.readInt()) {
            val t = ItemType.entries[i.readInt()]
            val x = i.readShort().toInt(); val y = i.readShort().toInt()
            val s = ItemStack(map.nextId(), t, i.readInt(), x, y)
            s.quality = Quality.entries[i.readByte().toInt()]; s.rot = i.readFloat(); s.hp = i.readFloat(); s.forbidden = i.readBoolean()
            s.corpseOf = i.opt(); val cr = i.readInt(); s.corpseRace = if (cr >= 0) Race.entries[cr] else null
            s.corpseColonist = i.readBoolean(); s.corpseAge = i.readInt()
            map.items[map.idx(x, y)] = s
        }
        repeat(i.readInt()) { val c = i.readInt(); map.fires[c] = Fire(i.readFloat()) }

        val wBiome = Biome.entries[i.readInt()]; val wHome = i.readInt()
        val g = Game(seed, map)
        g.rng = Rng(seed + tick)
        g.tick = tick; g.colonyName = name
        g.scenario = scenario; g.storyteller = story; g.difficulty = diff
        g.nextPawnId = nextPawn; g.raidCounter = raidCounter; g.raidsSurvived = survived
        g.nextRaid = nr; g.nextWanderer = nw; g.nextPod = np; g.nextTempEvent = nt; g.nextMisc = nm; g.nextTrader = ntr
        g.raidActive = raidActive; g.raidStartCount = raidStart; g.raidEnds = raidEnds; raidStartedAt = rsa
        g.weather = weather; g.weatherUntil = weatherUntil
        g.tempOffset = tempOffset; g.tempEventUntil = tempUntil; g.tempEventName = tempName
        g.solarFlareUntil = flare; g.toxicFalloutUntil = toxic; g.eclipseUntil = eclipse
        g.homeX = hx; g.homeY = hy; g.windFactor = wind
        g.statsKilled = killed; g.silverEarned = earned; g.hintBits = hints
        g.graveyard.addAll(grave)
        g.researchCurrent = if (cur >= 0) Research.entries[cur] else null
        g.researchDone.addAll(done)
        for ((r, v) in prog) g.researchProgress[r] = v
        g.traders.addAll(traderList)

        repeat(i.readInt()) { g.pawns.add(readPawn(i, tick)) }
        run {
            g.worldBiome = wBiome
            g.world = World.generate(seed, wBiome)
            g.world.homeTile = wHome
            for (k in g.world.goodwill.indices) g.world.goodwill[k] = i.readInt()
            repeat(i.readInt()) { idx ->
                val st = g.world.settlements.getOrNull(idx)
                val silver = i.readInt(); val stockTick = i.readLong(); val destroyed = i.readLong()
                val stock = List(i.readInt()) { ItemType.entries[i.readInt()] to i.readInt() }
                val req = if (i.readBoolean()) SettlementRequest(ItemType.entries[i.readInt()], i.readInt(), i.readInt(), i.readLong()) else null
                if (st != null) { st.silver = silver; st.stockTick = stockTick; st.destroyedUntil = destroyed; st.stock.putAll(stock); st.request = req }
            }
            g.world.nextSiteId = i.readInt()
            repeat(i.readInt()) { g.world.sites.add(Site(i.readInt(), i.readInt(), i.readInt(), i.readInt(), i.readInt(), i.readLong(), i.readFloat(), i.readUTF())) }
            g.nextCaravanId = i.readInt()
            repeat(i.readInt()) {
                val c = Caravan(i.readInt(), i.readUTF(), i.readInt())
                c.destination = i.readInt(); c.progress = i.readFloat(); c.resting = i.readBoolean(); c.goingHome = i.readBoolean()
                c.lastEvent = i.readUTF(); c.forageNote = i.readBoolean()
                repeat(i.readInt()) { c.route.add(i.readInt()) }
                repeat(i.readInt()) { c.inventory[ItemType.entries[i.readInt()]] = i.readInt() }
                repeat(i.readInt()) { c.members.add(readPawn(i, tick)) }
                g.caravans.add(c)
            }
        }
        g.log.clear()
        repeat(i.readInt()) { g.log.add(LogEntry(i.readLong(), i.readUTF(), i.readInt())) }
        map.rebuildRooms(g.outdoorTemp())
        for (p in g.pawns) g.recomputeHealth(p)
        return g
    }

    private fun readPawn(i: DataInputStream, now: Long): Pawn {
        val id = i.readInt(); val name = i.readUTF()
        val p = Pawn(id, name, Race.entries[i.readInt()], Faction.entries[i.readInt()])
        p.x = i.readShort().toInt(); p.y = i.readShort().toInt(); p.fromX = p.x; p.fromY = p.y
        p.downed = i.readBoolean(); p.drafted = i.readBoolean(); p.prisoner = i.readBoolean(); p.hostileFlag = i.readBoolean()
        p.age = i.readInt(); p.female = i.readBoolean(); p.mood = i.readFloat()
        p.breakUntil = i.readLong(); p.breakKind = i.readInt()
        p.food = i.readFloat(); p.rest = i.readFloat(); p.joy = i.readFloat(); p.bloodLoss = i.readFloat()
        p.careLevel = i.readInt(); p.foodPolicy = i.readInt(); p.allowDrugs = i.readBoolean()
        for (s in p.skill.indices) p.skill[s] = i.readInt()
        for (s in p.xp.indices) p.xp[s] = i.readFloat()
        for (s in p.passion.indices) p.passion[s] = i.readInt()
        for (s in p.priority.indices) p.priority[s] = i.readInt()
        repeat(i.readInt()) { p.traits.add(Trait.entries[i.readInt()]) }
        p.backstory = i.readUTF(); p.incapable = i.readInt()
        val wi = i.readInt(); p.weaponItem = if (wi >= 0) ItemType.entries[wi] else null; p.weaponQuality = Quality.entries[i.readInt()]
        repeat(i.readInt()) { p.apparel.add(Worn(ItemType.entries[i.readInt()], Quality.entries[i.readInt()], i.readFloat())) }
        repeat(i.readInt()) {
            val inj = Injury(i.readInt(), DamageKind.entries[i.readInt()], i.readFloat(), i.readFloat())
            inj.tended = i.readBoolean(); inj.tendQuality = i.readFloat(); inj.infection = i.readFloat(); inj.infectable = i.readBoolean()
            inj.permanent = i.readBoolean(); inj.missing = i.readBoolean(); inj.scar = i.readBoolean(); inj.implant = i.readUTF(); inj.age = i.readInt(); inj.immune = i.readFloat()
            p.injuries.add(inj)
        }
        repeat(i.readInt()) {
            val h = Hediff(HediffKind.entries[i.readInt()], i.readFloat(), i.readFloat())
            h.tended = i.readBoolean(); h.tendQuality = i.readFloat(); h.age = i.readInt(); h.part = i.readInt(); h.duration = i.readInt()
            p.hediffs.add(h)
        }
        repeat(i.readInt()) { p.thoughts.add(Thought(i.readUTF(), i.readFloat(), i.readLong() + now)) }
        repeat(i.readInt()) { p.opinion[i.readInt()] = i.readInt() }
        p.spouse = i.readInt(); p.lover = i.readInt()
        for (s in p.schedule.indices) p.schedule[s] = i.readInt()
        p.bedId = i.readInt(); p.homeTile = i.readInt()
        p.tame = i.readBoolean(); p.master = i.readInt(); p.animalProductTimer = i.readInt(); p.manhunter = i.readBoolean()
        p.huntMark = i.readBoolean(); p.tameMark = i.readBoolean(); p.slaughterMark = i.readBoolean()
        p.resistance = i.readFloat(); p.escaping = i.readBoolean(); p.recruitMode = i.readInt()
        p.raidId = i.readInt(); p.raidMode = i.readInt(); p.campX = i.readInt(); p.campY = i.readInt(); p.retreating = i.readBoolean()
        p.escapeTick = i.readLong(); p.wanderer = i.readBoolean(); p.lastSocial = i.readLong(); p.wfaction = i.readInt(); p.ally = i.readBoolean(); p.birthday = i.readInt(); p.ageDays = i.readInt(); p.mother = i.readInt(); p.father = i.readInt(); p.pregnantUntil = i.readLong(); p.pregnantBy = i.readInt()
        p.herdLeader = i.readInt()
        p.refugee = i.readBoolean(); p.areaRestriction = i.readInt()
        repeat(i.readInt()) { p.implants[i.readInt()] = Implant.entries[i.readInt()] }
        return p
    }
}
