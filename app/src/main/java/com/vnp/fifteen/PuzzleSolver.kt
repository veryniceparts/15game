package com.vnp.fifteen

import kotlin.math.abs

/**
 * Finds (or bounds) the minimum number of moves to solve a 15-puzzle board
 * using IDA* with the Manhattan-distance + linear-conflict heuristic.
 *
 * Optimal 15-puzzle solving is hard without precomputed pattern databases:
 * some shuffled boards need 60-80 moves and can take longer than is
 * reasonable to search on a phone. [solve] runs under a time budget and
 * falls back to reporting the best lower bound found so far rather than
 * blocking indefinitely or guessing.
 */
object PuzzleSolver {

    sealed class Result {
        /** [path] holds, in order, the board index tapped at each step (the tile that slides into the blank). */
        data class Solved(val moves: Int, val path: IntArray) : Result()
        data class TimedOut(val lowerBound: Int) : Result()
    }

    /** A full sequence of moves from a board to solved, for driving playback/step controls. */
    sealed class PathResult {
        /** [path] is a proven-optimal solution. */
        data class Exact(val moves: Int, val path: IntArray) : PathResult()
        /** [path] reaches solved but isn't proven optimal; [lowerBound] is the best-known floor on the true optimum. */
        data class Approximate(val lowerBound: Int, val path: IntArray) : PathResult()
    }

    private const val SIZE = 4
    private const val TIME_BUDGET_MS = 6000L
    private const val GREEDY_TIME_BUDGET_MS = 5000L
    private const val MAX_DEPTH = 120
    private const val FOUND = -1
    private const val TIMEOUT = -2

    fun solve(board: IntArray, timeBudgetMs: Long = TIME_BUDGET_MS): Result {
        val start = System.currentTimeMillis()
        val working = board.copyOf()
        var bound = heuristic(working)
        val pathBuffer = IntArray(MAX_DEPTH)
        var solutionPath: IntArray? = null

        while (true) {
            if (Thread.interrupted() || System.currentTimeMillis() - start > timeBudgetMs) {
                return Result.TimedOut(bound)
            }
            val blank = working.indexOf(0)
            val outcome = search(working, 0, bound, blank, -1, start, timeBudgetMs, pathBuffer) { length ->
                solutionPath = pathBuffer.copyOfRange(0, length)
            }
            when (outcome) {
                FOUND -> return Result.Solved(solutionPath!!.size, solutionPath!!)
                TIMEOUT -> return Result.TimedOut(bound)
                else -> bound = outcome
            }
        }
    }

    /**
     * Finds a full path from [board] to solved: the proven-optimal one when it can be
     * found within [exactBudgetMs], otherwise a fast (weighted A*) best-effort path
     * within [greedyBudgetMs] so a step/undo UI always has a real sequence to follow
     * instead of re-deciding one move at a time.
     */
    fun findPath(
        board: IntArray,
        exactBudgetMs: Long = TIME_BUDGET_MS,
        greedyBudgetMs: Long = GREEDY_TIME_BUDGET_MS
    ): PathResult {
        return when (val result = solve(board, exactBudgetMs)) {
            is Result.Solved -> PathResult.Exact(result.moves, result.path)
            is Result.TimedOut -> {
                val path = findGreedyPath(board, greedyBudgetMs)
                    ?: bestNeighborMove(board)?.let { intArrayOf(it) }
                    ?: IntArray(0)
                PathResult.Approximate(result.lowerBound, path)
            }
        }
    }

    /**
     * Weighted A* (f = g + 2h) with a visited set, so it always terminates on a real
     * (if not necessarily optimal) solution quickly instead of oscillating the way a
     * single greedy step can. Returns null if it exhausts [timeBudgetMs] first.
     */
    private fun findGreedyPath(board: IntArray, timeBudgetMs: Long): IntArray? {
        val start = System.currentTimeMillis()
        if (isSolved(board)) return IntArray(0)

        class Node(val board: IntArray, val blank: Int, val move: Int, val parent: Node?, val g: Int)

        val startNode = Node(board.copyOf(), board.indexOf(0), -1, null, 0)
        val open = java.util.PriorityQueue<Node>(compareBy { it.g + 2 * heuristic(it.board) })
        open.add(startNode)
        val visited = HashSet<Long>()
        visited.add(packBoard(startNode.board))

        while (open.isNotEmpty()) {
            if (Thread.interrupted() || System.currentTimeMillis() - start > timeBudgetMs) return null
            val current = open.poll()
            if (isSolved(current.board)) {
                val moves = mutableListOf<Int>()
                var node: Node? = current
                while (node != null && node.move != -1) {
                    moves.add(0, node.move)
                    node = node.parent
                }
                return moves.toIntArray()
            }
            for (neighbor in neighborIndices(current.blank)) {
                val nextBoard = current.board.copyOf()
                swap(nextBoard, current.blank, neighbor)
                if (visited.add(packBoard(nextBoard))) {
                    open.add(Node(nextBoard, neighbor, neighbor, current, current.g + 1))
                }
            }
        }
        return null
    }

    private fun isSolved(board: IntArray): Boolean {
        for (i in board.indices) {
            val expected = if (i == board.size - 1) 0 else i + 1
            if (board[i] != expected) return false
        }
        return true
    }

    private fun packBoard(board: IntArray): Long {
        var key = 0L
        for (value in board) key = (key shl 4) or value.toLong()
        return key
    }

    /**
     * Best-effort single move that most reduces the heuristic distance to solved.
     * Used as a quick stand-in for the real next move when a fully optimal path
     * couldn't be proven within the time budget. Returns null if [board] has no
     * blank tile.
     */
    fun bestNeighborMove(board: IntArray): Int? {
        val blank = board.indexOf(0)
        if (blank == -1) return null
        val working = board.copyOf()
        var best: Int? = null
        var bestScore = Int.MAX_VALUE
        for (neighbor in neighborIndices(blank)) {
            swap(working, blank, neighbor)
            val score = heuristic(working)
            swap(working, blank, neighbor)
            if (score < bestScore) {
                bestScore = score
                best = neighbor
            }
        }
        return best
    }

    private fun neighborIndices(index: Int): List<Int> {
        val row = index / SIZE
        val col = index % SIZE
        val result = mutableListOf<Int>()
        if (row > 0) result.add(index - SIZE)
        if (row < SIZE - 1) result.add(index + SIZE)
        if (col > 0) result.add(index - 1)
        if (col < SIZE - 1) result.add(index + 1)
        return result
    }

    private fun search(
        board: IntArray,
        g: Int,
        bound: Int,
        blankPos: Int,
        cameFrom: Int,
        start: Long,
        timeBudgetMs: Long,
        pathBuffer: IntArray,
        onFound: (Int) -> Unit
    ): Int {
        val f = g + heuristic(board)
        if (f > bound) return f
        if (f == g) {
            onFound(g)
            return FOUND
        }
        if (Thread.interrupted() || System.currentTimeMillis() - start > timeBudgetMs) return TIMEOUT

        var min = Int.MAX_VALUE
        val row = blankPos / SIZE
        val col = blankPos % SIZE

        if (row > 0 && blankPos - SIZE != cameFrom) {
            val neighbor = blankPos - SIZE
            pathBuffer[g] = neighbor
            swap(board, blankPos, neighbor)
            val t = search(board, g + 1, bound, neighbor, blankPos, start, timeBudgetMs, pathBuffer, onFound)
            swap(board, blankPos, neighbor)
            if (t == FOUND || t == TIMEOUT) return t
            if (t < min) min = t
        }
        if (row < SIZE - 1 && blankPos + SIZE != cameFrom) {
            val neighbor = blankPos + SIZE
            pathBuffer[g] = neighbor
            swap(board, blankPos, neighbor)
            val t = search(board, g + 1, bound, neighbor, blankPos, start, timeBudgetMs, pathBuffer, onFound)
            swap(board, blankPos, neighbor)
            if (t == FOUND || t == TIMEOUT) return t
            if (t < min) min = t
        }
        if (col > 0 && blankPos - 1 != cameFrom) {
            val neighbor = blankPos - 1
            pathBuffer[g] = neighbor
            swap(board, blankPos, neighbor)
            val t = search(board, g + 1, bound, neighbor, blankPos, start, timeBudgetMs, pathBuffer, onFound)
            swap(board, blankPos, neighbor)
            if (t == FOUND || t == TIMEOUT) return t
            if (t < min) min = t
        }
        if (col < SIZE - 1 && blankPos + 1 != cameFrom) {
            val neighbor = blankPos + 1
            pathBuffer[g] = neighbor
            swap(board, blankPos, neighbor)
            val t = search(board, g + 1, bound, neighbor, blankPos, start, timeBudgetMs, pathBuffer, onFound)
            swap(board, blankPos, neighbor)
            if (t == FOUND || t == TIMEOUT) return t
            if (t < min) min = t
        }
        return min
    }

    private fun swap(board: IntArray, i: Int, j: Int) {
        val tmp = board[i]
        board[i] = board[j]
        board[j] = tmp
    }

    private fun heuristic(board: IntArray): Int = manhattanDistance(board) + linearConflicts(board)

    private fun manhattanDistance(board: IntArray): Int {
        var sum = 0
        for (i in board.indices) {
            val value = board[i]
            if (value == 0) continue
            val target = value - 1
            sum += abs(i / SIZE - target / SIZE) + abs(i % SIZE - target % SIZE)
        }
        return sum
    }

    /** Counts pairs of tiles already in their goal row/column but in the wrong relative order. */
    private fun linearConflicts(board: IntArray): Int {
        var conflicts = 0
        for (row in 0 until SIZE) {
            for (colA in 0 until SIZE) {
                val valueA = board[row * SIZE + colA]
                if (valueA == 0) continue
                val targetA = valueA - 1
                if (targetA / SIZE != row) continue
                for (colB in colA + 1 until SIZE) {
                    val valueB = board[row * SIZE + colB]
                    if (valueB == 0) continue
                    val targetB = valueB - 1
                    if (targetB / SIZE != row) continue
                    if (targetA % SIZE > targetB % SIZE) conflicts++
                }
            }
        }
        for (col in 0 until SIZE) {
            for (rowA in 0 until SIZE) {
                val valueA = board[rowA * SIZE + col]
                if (valueA == 0) continue
                val targetA = valueA - 1
                if (targetA % SIZE != col) continue
                for (rowB in rowA + 1 until SIZE) {
                    val valueB = board[rowB * SIZE + col]
                    if (valueB == 0) continue
                    val targetB = valueB - 1
                    if (targetB % SIZE != col) continue
                    if (targetA / SIZE > targetB / SIZE) conflicts++
                }
            }
        }
        return conflicts * 2
    }
}
