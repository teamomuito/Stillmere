package io.github.teamomuito.colony.sim

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A* over the map. Returns cell indices from the first step to the goal, or null. */
class Pathfinder(private val m: GameMap) {
    private val g = IntArray(m.size)
    private val from = IntArray(m.size)
    private val stamp = IntArray(m.size)
    private var run = 0
    private val heap = IntArray(m.size * 3 + 16)
    private val heapKey = IntArray(m.size * 3 + 16)

    private var hs = 0
    private fun push(node: Int, key: Int) {
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
     * @param breach raiders may push through walls (at a high cost)
     */
    fun find(sx: Int, sy: Int, tx: Int, ty: Int, adjacent: Boolean = false, breach: Boolean = false): IntArray? {
        if (!m.inB(sx, sy) || !m.inB(tx, ty)) return null
        val start = m.idx(sx, sy)
        val goal = m.idx(tx, ty)
        if (isGoal(sx, sy, tx, ty, adjacent)) return IntArray(0)
        run++
        hs = 0
        stamp[start] = run; g[start] = 0; from[start] = -1
        push(start, h(sx, sy, tx, ty))
        var found = -1
        var expanded = 0
        while (hs > 0) {
            val c = pop()
            val cx = m.xOf(c); val cy = m.yOf(c)
            if (isGoal(cx, cy, tx, ty, adjacent)) { found = c; break }
            if (++expanded > 6000) break
            for (d in 0 until 8) {
                val nx = cx + GameMap.DX8[d]; val ny = cy + GameMap.DY8[d]
                if (!m.inB(nx, ny)) continue
                val n = m.idx(nx, ny)
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
        if (found < 0) return null
        var len = 0
        var c = found
        while (c != start) { len++; c = from[c] }
        val out = IntArray(len)
        c = found
        var i = len - 1
        while (c != start) { out[i--] = c; c = from[c] }
        return out
    }

    private fun isGoal(x: Int, y: Int, tx: Int, ty: Int, adjacent: Boolean): Boolean =
        if (adjacent) max(abs(x - tx), abs(y - ty)) <= 1 && (x != tx || y != ty) else x == tx && y == ty

    private fun h(x: Int, y: Int, tx: Int, ty: Int): Int {
        val dx = abs(x - tx); val dy = abs(y - ty)
        return 10 * (dx + dy) - 6 * min(dx, dy)
    }
}
