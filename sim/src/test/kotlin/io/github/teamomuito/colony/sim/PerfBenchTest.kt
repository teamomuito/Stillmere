package io.github.teamomuito.colony.sim

import org.junit.Test

/**
 * Benchmarks for the simulation under load. They do not assert anything about speed; they print what they measure, so
 * they can be compared between builds. They are skipped unless COLONY_BENCH is set:
 *
 *   COLONY_BENCH=1 ./gradlew :sim:test --offline --tests '*PerfBenchTest*' -i
 */
/**
 * A sampling profiler for the benchmark: every millisecond it records the top of the watched thread's stack, and
 * reports the most frequent frames of the simulation (self) and the frames it was inside (inclusive).
 */
private class Sampler(private val target: Thread) : Thread("sampler") {
    private val self = HashMap<String, Int>()
    private val incl = HashMap<String, Int>()
    private var total = 0
    @Volatile private var running = true

    init { isDaemon = true }

    override fun run() {
        while (running) {
            val st = target.stackTrace
            if (st.isNotEmpty()) {
                total++
                val top = st.first { it.className.contains("colony.sim") || it.className.startsWith("java.util") || it.className.startsWith("kotlin") }
                self.merge("${top.className.substringAfterLast('.')}.${top.methodName}:${top.lineNumber}", 1, Int::plus)
                val seen = HashSet<String>()
                for (f in st) if (f.className.contains("colony.sim")) {
                    val k = "${f.className.substringAfterLast('.')}.${f.methodName}"
                    if (seen.add(k)) incl.merge(k, 1, Int::plus)
                }
            }
            sleep(1)
        }
    }

    fun stopAndReport(label: String) {
        running = false
        join()
        println("PROFILE $label samples=$total")
        println("PROFILE $label self (top frame, simulation or library):")
        for ((k, v) in self.entries.sortedByDescending { it.value }.take(15)) println("PROFILE   ${"%5.1f".format(100.0 * v / total)}%  $k")
        println("PROFILE $label inclusive (simulation methods on the stack):")
        for ((k, v) in incl.entries.sortedByDescending { it.value }.take(25)) println("PROFILE   ${"%5.1f".format(100.0 * v / total)}%  $k")
    }
}

class PerfBenchTest {
    private val enabled = System.getenv("COLONY_BENCH") != null

    /** A colony of [pawns] colonists, [animals] wild animals, [items] loose stacks, [fires] fires and some plants. */
    private fun loaded(seed: Long, pawns: Int, animals: Int, items: Int, fires: Int): Game {
        val g = Game(seed)
        g.startNewColony(Scenario.LOST_TRIBE)
        g.mentalBreaksEnabled = true
        val r = java.util.Random(seed)
        while (g.colonists.size < pawns) g.newHuman(g.homeX + r.nextInt(12) - 6, g.homeY + r.nextInt(12) - 6)
        val races = listOf(Race.DEER, Race.BOAR, Race.HARE, Race.WOLF, Race.TURKEY, Race.MUFFALO)
        repeat(animals) {
            val x = r.nextInt(g.map.w - 2) + 1; val y = r.nextInt(g.map.h - 2) + 1
            if (g.map.walkable(g.map.idx(x, y))) g.newAnimal(races[r.nextInt(races.size)], x, y)
        }
        val types = ItemType.entries.filter { it.stack > 1 && !it.isGear }
        repeat(items) {
            g.map.drop(types[r.nextInt(types.size)], 1 + r.nextInt(20), r.nextInt(g.map.w), r.nextInt(g.map.h))
        }
        // Fires are set directly: igniteCell is rightly refused on bare ground, and these are about the cost of burning.
        // Put fires where something burns: on cells that are fuel, so they can spread.
        var lit = 0
        for (i in 0 until g.map.size) if (lit < fires && g.cellFlammability(i) > 0.3f && r.nextInt(200) == 0) { g.map.fires[i] = Fire(0.8f); lit++ }
        for (i in 0 until g.map.size step 9) {
            if (g.map.walkable(i) && g.map.plant[i] == null && r.nextInt(3) == 0) g.map.plant[i] = Plant(PlantType.RICE, g.map.xOf(i), g.map.yOf(i), 1f)
        }
        return g
    }

    private fun ms(nanos: Long) = nanos / 1_000_000.0

    /**
     * A checksum of the game state that does not depend on the order hash-based collections happen to iterate in.
     * Equal state hashes before and after an optimisation mean the simulation behaved identically.
     */
    private fun stateHash(g: Game): Long {
        var h = 1125899906842597L
        fun mix(v: Long) { h = 31 * h + v }
        for (p in g.pawns.sortedBy { it.id }) {
            mix(p.id.toLong()); mix(p.x.toLong()); mix(p.y.toLong())
            mix((p.food * 1e6f).toLong()); mix((p.rest * 1e6f).toLong()); mix((p.joy * 1e6f).toLong())
            mix(p.injuries.size.toLong()); mix((p.job?.type?.ordinal ?: -1).toLong())
        }
        for (i in g.map.items.keys.sorted()) {
            val st = g.map.items.getValue(i)
            mix(i.toLong()); mix(st.type.ordinal.toLong()); mix(st.count.toLong())
        }
        for (f in g.map.fires.keys.sorted()) mix(f.toLong())
        mix(g.tick)
        return h
    }

    private fun bench(label: String, pawns: Int, animals: Int, items: Int, fires: Int, ticks: Int) {
        val t0 = System.nanoTime()
        val g = loaded(900L + pawns, pawns, animals, items, fires)
        val setup = System.nanoTime() - t0
        g.finder.stats.reset()
        val sampler = if (System.getenv("COLONY_PROFILE") != null) Sampler(Thread.currentThread()).also { it.start() } else null
        val mx = java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val tid = Thread.currentThread().id
        val alloc0 = mx.getThreadAllocatedBytes(tid)
        val start = System.nanoTime()
        var peakFires = 0
        repeat(ticks) { g.step(); if (g.map.fires.size > peakFires) peakFires = g.map.fires.size }
        val run = System.nanoTime() - start
        val allocPerTick = (mx.getThreadAllocatedBytes(tid) - alloc0) / 1024.0 / ticks
        sampler?.let { it.stopAndReport(label) }
        val searches = g.finder.stats.searches
        val expansions = g.finder.stats.expansions
        val failures = g.finder.stats.failures
        val rejected = g.finder.stats.rejected
        val bytes: ByteArray
        val saveStart = System.nanoTime()
        bytes = SaveGame.write(g)
        val save = System.nanoTime() - saveStart
        // A checksum of the whole game: equal checksums before and after an optimisation mean identical behaviour.
        val crc = java.util.zip.CRC32().also { it.update(bytes) }.value
        val state = stateHash(g)
        val loadStart = System.nanoTime()
        SaveGame.read(bytes)
        val load = System.nanoTime() - loadStart
        println(
            "BENCH $label pawns=$pawns animals=$animals items=$items fires=${g.map.fires.size} ticks=$ticks " +
                "setup=${"%.0f".format(ms(setup))}ms run=${"%.0f".format(ms(run))}ms " +
                "perTick=${"%.3f".format(ms(run) / ticks)}ms allocKB/tick=${"%.0f".format(allocPerTick)} " +
                "searches=$searches failed=$failures refused=$rejected expansions=$expansions peakFires=$peakFires " +
                "save=${"%.0f".format(ms(save))}ms load=${"%.0f".format(ms(load))}ms bytes=${bytes.size} crc=$crc state=$state"
        )
    }

    /** Bytes allocated per call of the functions that run every tick, on a loaded colony. */
    @Test fun attributeAllocations() {
        if (!enabled) return
        val g = loaded(977L, 100, 100, 4_000, 20)
        repeat(1_000) { g.step() }
        val mx = java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val tid = Thread.currentThread().id
        fun perCall(label: String, n: Int, body: () -> Unit) {
            repeat(50) { body() }   // warm up
            val a0 = mx.getThreadAllocatedBytes(tid)
            repeat(n) { body() }
            println("ALLOC $label ${"%.0f".format((mx.getThreadAllocatedBytes(tid) - a0) / n.toDouble())} bytes/call")
        }
        val idle = g.pawns.filter { it.alive && it.colonist }
        perCall("colonists getter", 2_000) { g.colonists }
        perCall("pawns.toList (step snapshot)", 2_000) { g.pawns.toList() }
        perCall("tamedAnimals getter", 2_000) { g.tamedAnimals }
        perCall("think (all colonists)", 20) { for (p in idle) { p.job = null; g.think(p) } }
        val animals = g.pawns.filter { it.isAnimal && it.alive }
        perCall("animalTick (all animals)", 20) { for (a in animals) g.animalTick(a) }
        perCall("findConstruct (all colonists)", 20) { for (p in idle) g.findConstruct(p) }
        perCall("findHaul (all colonists)", 20) { for (p in idle) g.findHaul(p) }
        for ((name, f) in listOf<Pair<String, (Pawn) -> Any?>>(
            "findFood" to { p -> g.findFood(p) }, "findGear" to { p -> g.findGear(p) }, "findFirefight" to { p -> g.findFirefight(p) },
            "findDoctor" to { p -> g.findDoctor(p) }, "findClean" to { p -> g.findClean(p) }, "findGrow" to { p -> g.findGrow(p) },
            "findMine" to { p -> g.findMine(p) }, "findCut" to { p -> g.findCut(p) }, "findArt" to { p -> g.findArt(p) },
            "findButcher" to { p -> g.findButcher(p) }, "findConstruct" to { p -> g.findConstruct(p) }, "findHaul" to { p -> g.findHaul(p) },
            "startJoy" to { p -> g.startJoy(p) })) {
            perCall("finder $name (all colonists)", 20) { for (p in idle) f(p) }
        }
        perCall("step", 200) { g.step() }
    }

    @Test fun benchTenPawns() { if (enabled) bench("small", 10, 10, 200, 0, 4_000) }
    @Test fun benchFiftyPawns() { if (enabled) bench("medium", 50, 40, 1_500, 5, 4_000) }
    @Test fun benchHundredPawns() { if (enabled) bench("large", 100, 100, 4_000, 20, 4_000) }
}
