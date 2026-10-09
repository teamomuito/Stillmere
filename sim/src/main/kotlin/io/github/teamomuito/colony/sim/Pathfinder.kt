package io.github.teamomuito.colony.sim

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Counts of search work, so the cost of movement can be measured. */
class PathStats {
    /** A* searches actually run. */
    var searches = 0L
    /** Cells taken off the open list by searches. */
    var expansions = 0L
    /** Searches that ended without a path. */
    var failures = 0L
    /** Requests refused before searching, because the goal is in another region. */
    var rejected = 0L

    fun reset() { searches = 0; expansions = 0; failures = 0; rejected = 0 }
}

/**
 * Whether the cell (x, y) is a place a path can end, for a target at (tx, ty). Adjacent means next to the target (for a
 * building, next to any of its cells), so the target itself is never a goal in that case.
 */
internal fun isGoalCell(m: GameMap, x: Int, y: Int, tx: Int, ty: Int, adjacent: Boolean): Boolean {
    if (!adjacent) return x == tx && y == ty
    val b = m.building[m.idx(tx, ty)]
    if (b != null && (b.fw > 1 || b.fh > 1)) return x >= b.x - 1 && x <= b.x + b.fw && y >= b.y - 1 && y <= b.y + b.fh && !b.covers(x, y)
    return max(abs(x - tx), abs(y - ty)) <= 1 && (x != tx || y != ty)
}

/**
 * A* over the map, with a region check first: a goal that no walkable route can reach is refused without a search.
 * Returns cell indices from the first step to the goal, or null.
 */
class Pathfinder(private val m: GameMap) {
    val stats = PathStats()
    /** Which cells can reach which. Kept current by [GameMap.walkVersion]. */
    val regions = Regions(m)

    private val g = IntArray(m.size)
    private val from = IntArray(m.size)
    private val stamp = IntArray(m.size)
    private val closed = IntArray(m.size)
    private var run = 0
    private var heap = IntArray(m.size * 2 + 16)
    private var heapKey = IntArray(m.size * 2 + 16)

    private var hs = 0
    private fun push(node: Int, key: Int) {
        if (hs == heap.size) {
            heap = heap.copyOf(heap.size * 2)
            heapKey = heapKey.copyOf(heapKey.size * 2)
        }
        var i = hs++
        heap[i] = node; heapKey[i] = key
        while (i > 0) {
            val p = (i - 1) / 2
            if (heapKey[p] <= heapKey[i]) break
            swap(i, p); i = p
        }
    }

    private fun pop(): Int {
        val top = heap[0]
        hs--
        heap[0] = heap[hs]; heapKey[0] = heapKey[hs]
        var i = 0
        while (true) {
            val l = i * 2 + 1; val r = l + 1
            var s = i
            if (l < hs && heapKey[l] < heapKey[s]) s = l
            if (r < hs && heapKey[r] < heapKey[s]) s = r
            if (s == i) break
            swap(i, s); i = s
        }
        return top
    }

    private fun swap(a: Int, b: Int) {
        val n = heap[a]; heap[a] = heap[b]; heap[b] = n
        val k = heapKey[a]; heapKey[a] = heapKey[b]; heapKey[b] = k
    }

    /**
     * @param adjacent finish next to the target instead of on it
     * @param breach raiders may push through walls (at a high cost). Such searches ignore the region check, since
     *   the regions are of walkable cells only.
     */
    fun find(sx: Int, sy: Int, tx: Int, ty: Int, adjacent: Boolean = false, breach: Boolean = false): IntArray? {
        if (!m.inB(sx, sy) || !m.inB(tx, ty)) return null
        val start = m.idx(sx, sy)
        if (isGoalCell(m, sx, sy, tx, ty, adjacent)) return IntArray(0)
        if (!breach && !regions.mayReach(start, tx, ty, adjacent)) { stats.rejected++; return null }
        stats.searches++
        run++
        hs = 0
        stamp[start] = run; g[start] = 0; from[start] = -1
        push(start, h(sx, sy, tx, ty))
        var found = -1
        var expanded = 0
        while (hs > 0) {
            val c = pop()
            if (closed[c] == run) continue
            closed[c] = run
            val cx = m.xOf(c); val cy = m.yOf(c)
            if (isGoalCell(m, cx, cy, tx, ty, adjacent)) { found = c; break }
            expanded++
            for (d in 0 until 8) {
                val nx = cx + GameMap.DX8[d]; val ny = cy + GameMap.DY8[d]
                if (!m.inB(nx, ny)) continue
                val n = m.idx(nx, ny)
                if (closed[n] == run) continue
                var cost: Int
                if (!m.walkable(n)) {
                    if (breach && m.terrain[n].passable) cost = 120 else continue
                } else cost = m.stepCost(n)
                if (d >= 4) {
                    // No cutting corners around obstacles.
                    val a = m.idx(cx + GameMap.DX8[d], cy)
                    val b = m.idx(cx, cy + GameMap.DY8[d])
                    if (!m.walkable(a) || !m.walkable(b)) continue
                    cost = cost * 14 / 10
                }
                val ng = g[c] + cost
                if (stamp[n] != run || ng < g[n]) {
                    stamp[n] = run; g[n] = ng; from[n] = c
                    push(n, ng + h(nx, ny, tx, ty))
                }
            }
        }
        stats.expansions += expanded
        if (found < 0) { stats.failures++; return null }
        var len = 0
        var c = found
        while (c != start) { len++; c = from[c] }
        val out = IntArray(len)
        c = found
        var i = len - 1
        while (c != start) { out[i--] = c; c = from[c] }
        return out
    }

    private fun h(x: Int, y: Int, tx: Int, ty: Int): Int {
        val dx = abs(x - tx); val dy = abs(y - ty)
        return 10 * (dx + dy) - 6 * min(dx, dy)
    }
}
