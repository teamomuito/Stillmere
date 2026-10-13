package io.github.teamomuito.colony.sim

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Caves in rock, carved when a colony map is made (docs/fidelity/caves.md). A mass of rock of at least
 * [MIN_ROCKS_FOR_TUNNELS] cells, after its edges are trimmed, gets open tunnels, which start at the edge of the rock and run until
 * they leave it (the entrances), and closed tunnels, which stay inside it. The counts, widths, steps and branching are the base
 * game's values, cited in the document. The carver draws from a generator of its own and reads a value noise of its own, so the
 * map's other draws do not change. The output is deterministic from the seed and the rock.
 */
internal object Caves {
    /** A rock mass needs this many cells (after its edges are trimmed) before any tunnel is dug in it. */
    const val MIN_ROCKS_FOR_TUNNELS = 300
    /** Open tunnels per 10,000 rock cells, and the most of them in one mass. */
    const val OPEN_TUNNELS_PER_10K = 5.8f
    const val MAX_OPEN_TUNNELS = 3
    /** Closed tunnels per 10,000 rock cells, and the most of them in one mass. */
    const val CLOSED_TUNNELS_PER_10K = 2.5f
    const val MAX_CLOSED_TUNNELS = 1
    /** Degrees a tunnel's heading turns per step, times the noise (which runs from -1 to 1). */
    const val HEADING_CHANGE_DEGREES = 8.0
    /** Width lost per cell of tunnel, and the narrowest tunnel that is still dug. */
    const val WIDTH_LOSS_PER_CELL = 0.034f
    const val MIN_TUNNEL_WIDTH = 1.4f
    /** Chance, for each side, that a wide enough tunnel branches, and the number of cells before a branch may start. */
    const val BRANCH_CHANCE = 0.02f
    const val BRANCH_AFTER_CELLS = 10
    /** Width a branch loses (its uniform draw), and the turn of its heading, in degrees to either side. */
    const val BRANCH_WIDTH_LOSS_MIN = 0.2f
    const val BRANCH_WIDTH_LOSS_MAX = 0.4f
    const val BRANCH_TURN_MIN = 40.0
    const val BRANCH_TURN_MAX = 90.0
    const val BRANCH_HEADINGS = 6
    const val BRANCH_RAY_LIMIT = 50
    const val BRANCH_MIN_RAY = 18
    /** Rock width (the curve's x axis) to tunnel width (its y axis), in rock cells of the mass. Flat outside the points (C, see caves.md). */
    val WIDTH_CURVE = arrayOf(100f to 2f, 300f to 4f, 3000f to 5.5f)
    /** Rock groups are opened by this radius before tunnels are dug in them. */
    const val TRIM_RADIUS = 6
    /** The first cell of an open tunnel is at least this far from the map edge. */
    const val EDGE_CLEARANCE = 3
    /** Starts tried for an open tunnel, and the radius that measures the gap to a cave for it. */
    const val OPEN_TRIES = 10
    const val OPEN_GAP_RADIUS = 40f
    /** Starts tried for a closed tunnel, and the radius that measures its gap to open space. */
    const val CLOSED_TRIES = 7
    const val CLOSED_GAP_RADIUS = 30f
    /** Longest ray tried from a start, to choose the heading of an open tunnel. */
    const val RAY_LIMIT = 40
    /** A closed tunnel stops short of open space by this margin beyond its half width. */
    const val CLOSED_CLEARANCE = 1.5f
    /** A piece of rock left after trimming is dropped when it is below this share of the mass, or below the minimum. */
    const val SMALL_PIECE_SHARE = 0.05f
    /**
     * The heading noise: frequency per unit, and the scales of the travelled distance and of the start cell. Stillmere's noise is 2-D,
     * so the travelled term carries the start row and the second input is the start column (C, see caves.md).
     */
    const val HEADING_FREQUENCY = 0.00205
    const val HEADING_TIME_SCALE = 60.0
    const val HEADING_START_SCALE = 200.0
    /** Tie-break jitter for equal ray scores. It is far below the half-cell steps of the scores. */
    const val TIE_JITTER = 0.0001f

    private const val RNG_SALT = 0x6ca5e3b1L
    private const val NOISE_SALT = 0x2f71
    private const val DISC_REACH = 40

    private val DX4 = intArrayOf(1, -1, 0, 0)
    private val DY4 = intArrayOf(0, 0, 1, -1)
    /** Eight headings, 45 degrees apart from east, with y pointing south (down the grid). */
    private val DX8 = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
    private val DY8 = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)

    /** Cell offsets within [DISC_REACH], nearest first. Equal distances are ordered by row and column, so the order is fixed. */
    private val reachDx: IntArray
    private val reachDy: IntArray
    private val reachD2: IntArray

    init {
        val offsets = ArrayList<IntArray>()
        for (dy in -DISC_REACH..DISC_REACH) for (dx in -DISC_REACH..DISC_REACH) {
            val d2 = dx * dx + dy * dy
            if (d2 <= DISC_REACH * DISC_REACH) offsets.add(intArrayOf(dx, dy, d2))
        }
        offsets.sortWith(compareBy<IntArray>({ it[2] }, { it[1] }, { it[0] }))
        reachDx = IntArray(offsets.size) { offsets[it][0] }
        reachDy = IntArray(offsets.size) { offsets[it][1] }
        reachD2 = IntArray(offsets.size) { offsets[it][2] }
    }

    /** The number of offsets within [radius] (squared distance at most radius squared). */
    private fun reachCount(radius: Float): Int {
        val r2 = radius * radius
        var k = 0
        while (k < reachD2.size && reachD2[k] <= r2) k++
        return k
    }

    /**
     * The cells carved into [rock] on a [w] by [h] map. Only rock cells can be carved, and a carved cell becomes open floor for the
     * caller. The map's own random sequence is not used: all draws come from a generator seeded from [seed].
     */
    fun carve(w: Int, h: Int, rock: BooleanArray, seed: Long): BooleanArray =
        Carver(w, h, rock, Rng(seed xor RNG_SALT), Noise(seed.toInt() xor NOISE_SALT)).carveAll()

    private class Carver(
        private val w: Int,
        private val h: Int,
        private val rock: BooleanArray,
        private val rng: Rng,
        private val headingNoise: Noise,
    ) {
        private val n = w * h
        /** Cells carved so far. A carved cell was rock. */
        private val carved = BooleanArray(n)
        /** Rock cells already put in a mass. */
        private val seen = BooleanArray(n)
        /** The tunnels of one mass are dug in its cells: a cell is in the mass when its mark holds the current stamp. */
        private val groupMark = IntArray(n)
        private var groupStamp = 0
        /** Scratch for the morphology: a cell is in the set when its mark holds [setStamp]. */
        private val setMark = IntArray(n)
        private var setStamp = 0
        /** Scratch for breadth-first searches: a cell is visited when its mark holds [visitStamp]. */
        private val visitMark = IntArray(n)
        private var visitStamp = 0
        private val depth = IntArray(n)
        private val queue = IntArray(n)

        fun carveAll(): BooleanArray {
            for (i in 0 until n) {
                if (seen[i] || !rock[i]) continue
                val mass = floodRock(i)
                // Trimming and dropping pieces only shrink a mass, so a mass below the minimum can never get tunnels.
                if (mass.size < Caves.MIN_ROCKS_FOR_TUNNELS) continue
                val group = dropSmallPieces(opened(mass, Caves.TRIM_RADIUS))
                if (group.size < Caves.MIN_ROCKS_FOR_TUNNELS) continue
                groupStamp++
                for (c in group) groupMark[c] = groupStamp
                digOpenTunnels(group)
                digClosedTunnels(group)
            }
            return carved
        }

        /** Every rock cell connected to [start] through rock, by cardinal steps. */
        private fun floodRock(start: Int): List<Int> {
            val out = ArrayList<Int>()
            seen[start] = true
            out.add(start)
            var head = 0
            while (head < out.size) {
                val c = out[head++]
                val x = c % w
                val y = c / w
                for (k in 0 until 4) {
                    val nx = x + DX4[k]
                    val ny = y + DY4[k]
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val j = ny * w + nx
                    if (!seen[j] && rock[j]) {
                        seen[j] = true
                        out.add(j)
                    }
                }
            }
            return out
        }

        /** Morphological opening of [cells] by [r]: erode, then dilate. Cells stay inside the set, so this only trims. */
        private fun opened(cells: List<Int>, r: Int): List<Int> = dilate(erode(cells, r), r)

        /** Keeps the cells whose number of steps to the edge of the set, inside the set, is at least [r]. */
        private fun erode(cells: List<Int>, r: Int): List<Int> {
            setStamp++
            for (c in cells) setMark[c] = setStamp
            visitStamp++
            var tail = 0
            for (c in cells) {
                if (hasOutsideNeighbour(c)) {
                    visitMark[c] = visitStamp
                    depth[c] = 0
                    queue[tail++] = c
                }
            }
            var head = 0
            while (head < tail) {
                val c = queue[head++]
                val x = c % w
                val y = c / w
                for (k in 0 until 4) {
                    val nx = x + DX4[k]
                    val ny = y + DY4[k]
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val j = ny * w + nx
                    if (setMark[j] == setStamp && visitMark[j] != visitStamp) {
                        visitMark[j] = visitStamp
                        depth[j] = depth[c] + 1
                        queue[tail++] = j
                    }
                }
            }
            return cells.filter { visitMark[it] == visitStamp && depth[it] >= r }
        }

        /** Adds every cell within [r] cardinal steps of [seeds] (on the whole map). The seeds come first. */
        private fun dilate(seeds: List<Int>, r: Int): List<Int> {
            visitStamp++
            val out = ArrayList<Int>(seeds)
            var tail = 0
            for (c in seeds) {
                visitMark[c] = visitStamp
                depth[c] = 0
                queue[tail++] = c
            }
            var head = 0
            while (head < tail) {
                val c = queue[head++]
                if (depth[c] >= r) continue
                val x = c % w
                val y = c / w
                for (k in 0 until 4) {
                    val nx = x + DX4[k]
                    val ny = y + DY4[k]
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val j = ny * w + nx
                    if (visitMark[j] != visitStamp) {
                        visitMark[j] = visitStamp
                        depth[j] = depth[c] + 1
                        queue[tail++] = j
                        out.add(j)
                    }
                }
            }
            return out
        }

        /** Drops each connected piece of [cells] that is under the minimum or under [SMALL_PIECE_SHARE] of all of them. */
        private fun dropSmallPieces(cells: List<Int>): List<Int> {
            setStamp++
            for (c in cells) setMark[c] = setStamp
            visitStamp++
            val limit = Caves.SMALL_PIECE_SHARE * cells.size
            val kept = ArrayList<Int>()
            for (start in cells) {
                if (visitMark[start] == visitStamp) continue
                val piece = ArrayList<Int>()
                visitMark[start] = visitStamp
                piece.add(start)
                var head = 0
                while (head < piece.size) {
                    val c = piece[head++]
                    val x = c % w
                    val y = c / w
                    for (k in 0 until 4) {
                        val nx = x + DX4[k]
                        val ny = y + DY4[k]
                        if (nx !in 0 until w || ny !in 0 until h) continue
                        val j = ny * w + nx
                        if (setMark[j] == setStamp && visitMark[j] != visitStamp) {
                            visitMark[j] = visitStamp
                            piece.add(j)
                        }
                    }
                }
                if (piece.size >= Caves.MIN_ROCKS_FOR_TUNNELS && piece.size.toFloat() >= limit) kept.addAll(piece)
            }
            return kept
        }

        private fun hasOutsideNeighbour(c: Int): Boolean {
            val x = c % w
            val y = c / w
            for (k in 0 until 4) {
                val nx = x + DX4[k]
                val ny = y + DY4[k]
                if (nx !in 0 until w || ny !in 0 until h) return true
                if (setMark[ny * w + nx] != setStamp) return true
            }
            return false
        }

        private fun inGroup(x: Int, y: Int): Boolean = x in 0 until w && y in 0 until h && groupMark[y * w + x] == groupStamp

        private fun roundRandom(f: Float): Int {
            val whole = floor(f).toInt()
            return whole + if (rng.float() < f - whole) 1 else 0
        }

        private fun uniform(a: Float, b: Float): Float = a + (b - a) * rng.float()

        private fun uniformDegrees(a: Double, b: Double): Double = a + (b - a) * rng.float()

        /** Tunnel width for a mass of [n] rock cells, from the curve. */
        private fun widthFor(n: Int): Float {
            val pts = Caves.WIDTH_CURVE
            val x = n.toFloat()
            if (x <= pts[0].first) return pts[0].second
            if (x >= pts[pts.size - 1].first) return pts[pts.size - 1].second
            for (i in 1 until pts.size) {
                if (x <= pts[i].first) {
                    val (x0, y0) = pts[i - 1]
                    val (x1, y1) = pts[i]
                    return y0 + (y1 - y0) * ((x - x0) / (x1 - x0))
                }
            }
            return pts[pts.size - 1].second
        }

        private fun digOpenTunnels(group: List<Int>) {
            var count = roundRandom(group.size * uniform(0.9f, 1.1f) * Caves.OPEN_TUNNELS_PER_10K / 10000f)
            count = min(count, Caves.MAX_OPEN_TUNNELS)
            if (count > 0) count = rng.range(1, count)
            val rockWidth = widthFor(group.size)
            repeat(count) {
                var startX = -1
                var startY = -1
                var startGap = -1f
                var startRay = -1
                var startHeading = 0.0
                repeat(Caves.OPEN_TRIES) {
                    val cell = edgeCell(group)
                    val x = cell % w
                    val y = cell / w
                    val gap = gapToCarved(x, y, Caves.OPEN_GAP_RADIUS, openSpaceCounts = false)
                    val (heading, ray) = bestHeading(x, y)
                    if (startX < 0 || gap > startGap || (abs(gap - startGap) < 1e-6f && ray > startRay)) {
                        startX = x
                        startY = y
                        startGap = gap
                        startRay = ray
                        startHeading = heading
                    }
                }
                val width = uniform(rockWidth * 0.8f, rockWidth)
                dig(startX, startY, startHeading, width, closed = false, visited = HashSet())
            }
        }

        private fun digClosedTunnels(group: List<Int>) {
            var count = roundRandom(group.size * uniform(0.9f, 1.1f) * Caves.CLOSED_TUNNELS_PER_10K / 10000f)
            count = min(count, Caves.MAX_CLOSED_TUNNELS)
            if (count > 0) count = rng.range(0, count)
            val rockWidth = widthFor(group.size)
            repeat(count) {
                var best = -1
                var bestGap = -1f
                repeat(Caves.CLOSED_TRIES) {
                    val cell = group[rng.int(group.size)]
                    val gap = gapToCarved(cell % w, cell / w, Caves.CLOSED_GAP_RADIUS, openSpaceCounts = true)
                    if (best < 0 || gap > bestGap) {
                        best = cell
                        bestGap = gap
                    }
                }
                val width = uniform(rockWidth * 0.8f, rockWidth)
                val heading = uniformDegrees(0.0, 360.0)
                dig(best % w, best / w, heading, width, closed = true, visited = HashSet())
            }
        }

        /** A random cell of the mass that is not carved, at least [Caves.EDGE_CLEARANCE] from the map edge, next to a cell outside the mass. */
        private fun edgeCell(group: List<Int>): Int {
            val options = ArrayList<Int>()
            for (c in group) {
                val x = c % w
                val y = c / w
                val edge = min(min(x, y), min(w - 1 - x, h - 1 - y))
                if (edge < Caves.EDGE_CLEARANCE || carved[c]) continue
                for (k in 0 until 4) {
                    if (!inGroup(x + DX4[k], y + DY4[k])) {
                        options.add(c)
                        break
                    }
                }
            }
            if (options.isEmpty()) return group[rng.int(group.size)]
            return options[rng.int(options.size)]
        }

        /** Distance from (x, y) to the nearest carved cell, or to the nearest cell outside the mass when [openSpaceCounts]. */
        private fun gapToCarved(x: Int, y: Int, radius: Float, openSpaceCounts: Boolean): Float {
            val count = reachCount(radius)
            for (k in 0 until count) {
                val nx = x + reachDx[k]
                val ny = y + reachDy[k]
                val inside = nx in 0 until w && ny in 0 until h
                if ((openSpaceCounts && !inGroup(nx, ny)) || (inside && carved[ny * w + nx])) return sqrt(reachD2[k].toFloat())
            }
            return radius
        }

        /**
         * The heading of the longest open ray from (x, y), and that ray's length. Each of the eight headings scores its own ray
         * plus half of each neighbouring heading's ray. Ties are broken at random.
         */
        private fun bestHeading(x: Int, y: Int): Pair<Double, Int> {
            val rays = IntArray(8) { straightRay(x, y, DX8[it], DY8[it]) }
            var longest = 0
            for (r in rays) if (r > longest) longest = r
            var best = 0
            var bestScore = -1f
            for (k in 0 until 8) {
                val score = rays[k] + (rays[(k + 1) % 8] + rays[(k + 7) % 8]) / 2f + uniform(0f, Caves.TIE_JITTER)
                if (k == 0 || score > bestScore) {
                    best = k
                    bestScore = score
                }
            }
            return best * 45.0 to longest
        }

        /** Steps along a compass direction until a cell outside the mass, up to [Caves.RAY_LIMIT]. */
        private fun straightRay(x: Int, y: Int, dx: Int, dy: Int): Int {
            for (i in 0..Caves.RAY_LIMIT) {
                if (!inGroup(x + dx * i, y + dy * i)) return i
            }
            return Caves.RAY_LIMIT
        }

        /** Steps along an arbitrary heading (degrees) from the middle of (x, y) until a cell outside the mass, up to [limit]. */
        private fun headingRay(x: Int, y: Int, heading: Double, limit: Int): Int {
            val rad = heading * (StrictMath.PI / 180.0)
            val ux = StrictMath.cos(rad)
            val uy = StrictMath.sin(rad)
            for (i in 0..limit) {
                val cx = floor(x + 0.5 + ux * i).toInt()
                val cy = floor(y + 0.5 + uy * i).toInt()
                if (!inGroup(cx, cy)) return i
            }
            return limit
        }

        /** Digs from the start with the given heading. Open tunnels stop when they leave the mass (their entrances). */
        private fun dig(sx: Int, sy: Int, startHeading: Double, startWidth: Float, closed: Boolean, visited: HashSet<Int>) {
            var cx = sx
            var cy = sy
            var px = sx + 0.5
            var py = sy + 0.5
            var heading = startHeading
            var width = startWidth
            var travelled = 0.0
            var steps = 0
            var branchedRight = false
            var branchedLeft = false
            while (true) {
                if (closed && closedWouldBreakOut(cx, cy, width, visited)) return
                if (steps >= Caves.BRANCH_AFTER_CELLS && width > Caves.MIN_TUNNEL_WIDTH + Caves.BRANCH_WIDTH_LOSS_MAX) {
                    if (!branchedRight && rng.chance(Caves.BRANCH_CHANCE)) {
                        digBranch(cx, cy, heading, Caves.BRANCH_TURN_MIN, Caves.BRANCH_TURN_MAX, width - uniform(Caves.BRANCH_WIDTH_LOSS_MIN, Caves.BRANCH_WIDTH_LOSS_MAX), closed, visited)
                        branchedRight = true
                    }
                    if (!branchedLeft && rng.chance(Caves.BRANCH_CHANCE)) {
                        digBranch(cx, cy, heading, -Caves.BRANCH_TURN_MAX, -Caves.BRANCH_TURN_MIN, width - uniform(Caves.BRANCH_WIDTH_LOSS_MIN, Caves.BRANCH_WIDTH_LOSS_MAX), closed, visited)
                        branchedLeft = true
                    }
                }
                if (carveDisc(cx, cy, max(width, 1f), visited)) break
                // Walk in half-cell steps until the position leaves the current cell.
                val rad = heading * (StrictMath.PI / 180.0)
                val ux = StrictMath.cos(rad) * 0.5
                val uy = StrictMath.sin(rad) * 0.5
                while (floor(px).toInt() == cx && floor(py).toInt() == cy) {
                    px += ux
                    py += uy
                    travelled += 0.5
                }
                val nx = floor(px).toInt()
                val ny = floor(py).toInt()
                if (!inGroup(nx, ny)) break
                // The cell beside the old one, on the new row, is carved too when it is rock.
                if (cx in 0 until w && ny in 0 until h && rock[ny * w + cx]) {
                    val side = ny * w + cx
                    carved[side] = true
                    visited.add(side)
                }
                cx = nx
                cy = ny
                heading += turn(travelled, sx, sy) * Caves.HEADING_CHANGE_DEGREES
                width -= Caves.WIDTH_LOSS_PER_CELL
                if (width < Caves.MIN_TUNNEL_WIDTH) break
                steps++
            }
        }

        /** A side tunnel: the longest of a few headings turned from [heading], if its ray is long enough, is dug from here. */
        private fun digBranch(x: Int, y: Int, heading: Double, turnMin: Double, turnMax: Double, width: Float, closed: Boolean, visited: HashSet<Int>) {
            var bestRay = -1
            var bestHeading = heading
            repeat(Caves.BRANCH_HEADINGS) {
                val candidate = heading + uniformDegrees(turnMin, turnMax)
                val ray = headingRay(x, y, candidate, Caves.BRANCH_RAY_LIMIT)
                if (ray > bestRay) {
                    bestRay = ray
                    bestHeading = candidate
                }
            }
            if (bestRay >= Caves.BRANCH_MIN_RAY) dig(x, y, bestHeading, width, closed, visited)
        }

        /** The heading change for one step: a 2-D value noise in [-1, 1] (C), sampled from the travelled distance and the start cell. */
        private fun turn(travelled: Double, sx: Int, sy: Int): Double {
            val f = Caves.HEADING_FREQUENCY
            val v = headingNoise.at(
                (travelled * Caves.HEADING_TIME_SCALE * f + sy * Caves.HEADING_START_SCALE * f).toFloat(),
                (sx * Caves.HEADING_START_SCALE * f).toFloat(),
            )
            return v * 2.0 - 1.0
        }

        /** Carves the disc of [tunnelWidth] around (x, y) where it is rock. True when it meets another tunnel this dig did not make. */
        private fun carveDisc(x: Int, y: Int, tunnelWidth: Float, visited: HashSet<Int>): Boolean {
            val count = reachCount(tunnelWidth / 2f)
            var hit = false
            for (k in 0 until count) {
                val nx = x + reachDx[k]
                val ny = y + reachDy[k]
                if (nx !in 0 until w || ny !in 0 until h) continue
                val i = ny * w + nx
                if (!rock[i]) continue
                if (carved[i] && i !in visited) hit = true
                carved[i] = true
                visited.add(i)
            }
            return hit
        }

        /** True when a closed tunnel's next disc would touch open space or a cave it did not carve itself. */
        private fun closedWouldBreakOut(x: Int, y: Int, width: Float, visited: HashSet<Int>): Boolean {
            val count = reachCount(width / 2f + Caves.CLOSED_CLEARANCE)
            for (k in 0 until count) {
                val nx = x + reachDx[k]
                val ny = y + reachDy[k]
                val inside = nx in 0 until w && ny in 0 until h
                val i = if (inside) ny * w + nx else -1
                if (inside && i in visited) continue
                if (!inGroup(nx, ny) || (inside && carved[i])) return true
            }
            return false
        }
    }
}
