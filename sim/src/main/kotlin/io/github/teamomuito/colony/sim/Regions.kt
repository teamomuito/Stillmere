package io.github.teamomuito.colony.sim

/**
 * Regions are the connected groups of walkable cells. Moving diagonally needs both cells beside the step to be walkable,
 * so a route between two walkable cells exists exactly when they are joined by a path of orthogonal steps. Regions are
 * therefore plain 4-connected components.
 *
 * Regions are rebuilt when [GameMap.walkVersion] changes, and only when someone asks, so a map that changes every tick
 * costs one flood fill per question asked after a change, never one per tick.
 *
 * The answers are conservative. [mayReach] returns false only when no route can exist; when it cannot be sure it returns
 * true and the search decides.
 */
class Regions(private val m: GameMap) {
    private val id = IntArray(m.size)
    private val stack = IntArray(m.size)
    private var builtFor = -1
    /** Number of walkable regions in the current build. */
    var count = 0
        private set

    private fun ensureBuilt() {
        if (builtFor == m.walkVersion) return
        java.util.Arrays.fill(id, -1)
        count = 0
        for (s in 0 until m.size) {
            if (id[s] != -1 || !m.walkable(s)) continue
            var sp = 0
            stack[sp++] = s; id[s] = count
            while (sp > 0) {
                val c = stack[--sp]
                val x = m.xOf(c); val y = m.yOf(c)
                for (d in 0 until 4) {
                    val nx = x + GameMap.DX4[d]; val ny = y + GameMap.DY4[d]
                    if (!m.inB(nx, ny)) continue
                    val n = m.idx(nx, ny)
                    if (id[n] == -1 && m.walkable(n)) { id[n] = count; stack[sp++] = n }
                }
            }
            count++
        }
        builtFor = m.walkVersion
    }

    /** The region of a walkable cell, or -1 when the cell is not walkable. */
    fun regionOf(i: Int): Int { ensureBuilt(); return id[i] }

    /**
     * False only when a search from [startIdx] to the target (tx, ty) is certain to find nothing. A start on an
     * unwalkable cell is never refused, since the search is the authority on that.
     */
    fun mayReach(startIdx: Int, tx: Int, ty: Int, adjacent: Boolean): Boolean {
        ensureBuilt()
        val sr = id[startIdx]
        if (sr < 0) return true
        if (!adjacent) return id[m.idx(tx, ty)] == sr
        // Adjacent: some walkable cell beside the target must be in the start's region.
        val b = m.building[m.idx(tx, ty)]
        val multi = b != null && (b.fw > 1 || b.fh > 1)
        val x0 = if (multi) b!!.x - 1 else tx - 1
        val y0 = if (multi) b!!.y - 1 else ty - 1
        val x1 = if (multi) b!!.x + b.fw else tx + 1
        val y1 = if (multi) b!!.y + b.fh else ty + 1
        for (y in y0..y1) for (x in x0..x1) {
            if (!m.inB(x, y) || !isGoalCell(m, x, y, tx, ty, true)) continue
            if (id[m.idx(x, y)] == sr) return true
        }
        return false
    }

    /** Whether a job could plausibly use cell [target] for a pawn standing on [startIdx]: on foot, or beside it. */
    fun mayReachTarget(startIdx: Int, target: Int): Boolean {
        val tx = m.xOf(target); val ty = m.yOf(target)
        return mayReach(startIdx, tx, ty, adjacent = false) || mayReach(startIdx, tx, ty, adjacent = true)
    }
}
