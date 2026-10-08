package io.github.teamomuito.colony.sim

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Compact binary save format. Jobs and reservations are not saved; pawns simply re-think on load. */
object SaveGame {
    private const val VERSION = 1

    fun write(g: Game): ByteArray {
        val bytes = ByteArrayOutputStream()
        val o = DataOutputStream(bytes)
        o.writeInt(VERSION)
        o.writeLong(g.seed)
        o.writeLong(g.tick)
        o.writeUTF(g.colonyName)
        o.writeInt(g.nextPawnId)
        o.writeInt(g.raidCounter)
        o.writeInt(g.raidsSurvived)
        o.writeLong(g.nextRaid); o.writeLong(g.nextWanderer); o.writeLong(g.nextPod); o.writeLong(g.nextTempEvent)
        o.writeBoolean(g.raidActive); o.writeInt(g.raidStartCount); o.writeLong(g.raidEnds)
        o.writeFloat(g.tempOffset); o.writeLong(g.tempEventUntil); o.writeUTF(g.tempEventName)
        o.writeBoolean(g.shipBuilt)
        o.writeInt(g.homeX); o.writeInt(g.homeY)
        o.writeInt(g.researchCurrent?.ordinal ?: -1)
        o.writeInt(g.researchDone.size)
        for (r in g.researchDone) o.writeInt(r.ordinal)
        o.writeInt(g.researchProgress.size)
        for ((r, v) in g.researchProgress) { o.writeInt(r.ordinal); o.writeFloat(v) }

        val m = g.map
        o.writeInt(m.w); o.writeInt(m.h)
        for (i in 0 until m.size) {
            o.writeByte(m.terrain[i].ordinal)
            o.writeBoolean(m.ore[i])
            o.writeByte(m.floor[i]?.ordinal ?: -1)
            o.writeByte(m.zone[i].toInt())
            o.writeByte(m.zoneCrop[i].toInt())
            o.writeByte(m.desig[i].toInt())
        }
        val builds = m.building.filterNotNull()
        o.writeInt(builds.size)
        for (b in builds) {
            o.writeByte(b.def.ordinal); o.writeShort(b.x); o.writeShort(b.y); o.writeBoolean(b.built)
            o.writeInt(b.delivered); o.writeFloat(b.progress); o.writeFloat(b.hp); o.writeInt(b.ownerId); o.writeFloat(b.fuel)
        }
        val plants = m.plant.filterNotNull()
        o.writeInt(plants.size)
        for (p in plants) { o.writeByte(p.type.ordinal); o.writeShort(p.x); o.writeShort(p.y); o.writeFloat(p.growth) }
        o.writeInt(m.items.size)
        for (s in m.items.values) { o.writeByte(s.type.ordinal); o.writeShort(s.x); o.writeShort(s.y); o.writeInt(s.count) }

        val pawns = g.pawns.filter { it.alive }
        o.writeInt(pawns.size)
        for (p in pawns) {
            o.writeInt(p.id); o.writeUTF(p.name); o.writeBoolean(p.colonist); o.writeBoolean(p.hostile)
            o.writeShort(p.x); o.writeShort(p.y)
            o.writeFloat(p.hp); o.writeFloat(p.food); o.writeFloat(p.rest); o.writeFloat(p.mood)
            o.writeBoolean(p.downed); o.writeBoolean(p.drafted); o.writeByte(p.weapon.ordinal); o.writeInt(p.age)
            o.writeInt(p.bedId); o.writeInt(p.raidId); o.writeBoolean(p.retreating); o.writeBoolean(p.wanderer)
            o.writeLong(p.breakUntil); o.writeInt(p.breakKind)
            for (s in p.skill) o.writeInt(s)
            for (s in p.xp) o.writeFloat(s)
            for (s in p.passion) o.writeInt(s)
            for (s in p.priority) o.writeInt(s)
            o.writeInt(p.traits.size); for (t in p.traits) o.writeInt(t.ordinal)
            o.writeInt(p.injuries.size)
            for (i in p.injuries) { o.writeFloat(i.severity); o.writeFloat(i.bleed); o.writeBoolean(i.tended) }
            o.writeInt(p.thoughts.size)
            for (t in p.thoughts) { o.writeUTF(t.label); o.writeFloat(t.mood); o.writeLong(t.expires) }
        }
        val recent = g.log.takeLast(40)
        o.writeInt(recent.size)
        for (l in recent) { o.writeLong(l.tick); o.writeUTF(l.text); o.writeInt(l.level) }
        o.flush()
        return bytes.toByteArray()
    }

    fun read(data: ByteArray): Game {
        val i = DataInputStream(ByteArrayInputStream(data))
        require(i.readInt() == VERSION) { "Unsupported save version" }
        val seed = i.readLong()
        val tick = i.readLong()
        val name = i.readUTF()
        val nextPawn = i.readInt()
        val raidCounter = i.readInt()
        val survived = i.readInt()
        val nr = i.readLong(); val nw = i.readLong(); val np = i.readLong(); val nt = i.readLong()
        val raidActive = i.readBoolean(); val raidStart = i.readInt(); val raidEnds = i.readLong()
        val tempOffset = i.readFloat(); val tempUntil = i.readLong(); val tempName = i.readUTF()
        val shipBuilt = i.readBoolean()
        val hx = i.readInt(); val hy = i.readInt()
        val cur = i.readInt()
        val done = List(i.readInt()) { Research.entries[i.readInt()] }
        val prog = List(i.readInt()) { Research.entries[i.readInt()] to i.readFloat() }

        val w = i.readInt(); val h = i.readInt()
        val map = GameMap(w, h)
        for (c in 0 until map.size) {
            map.terrain[c] = Terrain.entries[i.readByte().toInt()]
            map.ore[c] = i.readBoolean()
            val f = i.readByte().toInt()
            map.floor[c] = if (f >= 0) BuildDef.entries[f] else null
            map.zone[c] = i.readByte()
            map.zoneCrop[c] = i.readByte()
            map.desig[c] = i.readByte()
        }
        repeat(i.readInt()) {
            val def = BuildDef.entries[i.readByte().toInt()]
            val x = i.readShort().toInt(); val y = i.readShort().toInt()
            val b = Building(def, x, y, i.readBoolean())
            b.delivered = i.readInt(); b.progress = i.readFloat(); b.hp = i.readFloat(); b.ownerId = i.readInt(); b.fuel = i.readFloat()
            map.building[map.idx(x, y)] = b
        }
        repeat(i.readInt()) {
            val t = PlantType.entries[i.readByte().toInt()]
            val x = i.readShort().toInt(); val y = i.readShort().toInt()
            map.plant[map.idx(x, y)] = Plant(t, x, y, i.readFloat())
        }
        repeat(i.readInt()) {
            val t = ItemType.entries[i.readByte().toInt()]
            val x = i.readShort().toInt(); val y = i.readShort().toInt()
            map.items[map.idx(x, y)] = ItemStack(map.nextId(), t, i.readInt(), x, y)
        }

        val g = Game(seed, map)
        g.rng = Rng(seed + tick)
        g.tick = tick
        g.colonyName = name
        g.nextPawnId = nextPawn
        g.raidCounter = raidCounter
        g.raidsSurvived = survived
        g.nextRaid = nr; g.nextWanderer = nw; g.nextPod = np; g.nextTempEvent = nt
        g.raidActive = raidActive; g.raidStartCount = raidStart; g.raidEnds = raidEnds
        g.tempOffset = tempOffset; g.tempEventUntil = tempUntil; g.tempEventName = tempName
        g.shipBuilt = shipBuilt
        g.homeX = hx; g.homeY = hy
        g.researchCurrent = if (cur >= 0) Research.entries[cur] else null
        g.researchDone.addAll(done)
        for ((r, v) in prog) g.researchProgress[r] = v

        repeat(i.readInt()) {
            val id = i.readInt()
            val p = Pawn(id, i.readUTF(), i.readBoolean())
            p.hostile = i.readBoolean()
            p.x = i.readShort().toInt(); p.y = i.readShort().toInt(); p.fromX = p.x; p.fromY = p.y
            p.hp = i.readFloat(); p.food = i.readFloat(); p.rest = i.readFloat(); p.mood = i.readFloat()
            p.downed = i.readBoolean(); p.drafted = i.readBoolean(); p.weapon = Weapon.entries[i.readByte().toInt()]; p.age = i.readInt()
            p.bedId = i.readInt(); p.raidId = i.readInt(); p.retreating = i.readBoolean(); p.wanderer = i.readBoolean()
            p.breakUntil = i.readLong(); p.breakKind = i.readInt()
            for (s in p.skill.indices) p.skill[s] = i.readInt()
            for (s in p.xp.indices) p.xp[s] = i.readFloat()
            for (s in p.passion.indices) p.passion[s] = i.readInt()
            for (s in p.priority.indices) p.priority[s] = i.readInt()
            repeat(i.readInt()) { p.traits.add(Trait.entries[i.readInt()]) }
            repeat(i.readInt()) { p.injuries.add(Injury(i.readFloat(), i.readFloat(), i.readBoolean())) }
            repeat(i.readInt()) { p.thoughts.add(Thought(i.readUTF(), i.readFloat(), i.readLong())) }
            g.pawns.add(p)
        }
        g.log.clear()
        repeat(i.readInt()) { g.log.add(LogEntry(i.readLong(), i.readUTF(), i.readInt())) }
        map.rebuildRooms(g.outdoorTemp())
        return g
    }
}
