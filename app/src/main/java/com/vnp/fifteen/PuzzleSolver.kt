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
        data class Solved(val moves: Int) : Result()
        data class TimedOut(val lowerBound: Int) : Result()
    }

    private const val SIZE = 4
    private const val TIME_BUDGET_MS = 6000L
    private const val FOUND = -1
    private const val TIMEOUT = -2

    fun solve(board: IntArray): Result {
        val start = System.currentTimeMillis()
        val working = board.copyOf()
        var bound = heuristic(working)
        var solutionLength = -1

        while (true) {
            if (Thread.interrupted() || System.currentTimeMillis() - start > TIME_BUDGET_MS) {
                return Result.TimedOut(bound)
            }
            val blank = working.indexOf(0)
            val outcome = search(working, 0, bound, blank, -1, start) { length -> solutionLength = length }
            when (outcome) {
                FOUND -> return Result.Solved(solutionLength)
                TIMEOUT -> return Result.TimedOut(bound)
                else -> bound = outcome
            }
        }
    }

    private fun search(
        board: IntArray,
        g: Int,
        bound: Int,
        blankPos: Int,
        cameFrom: Int,
        start: Long,
        onFound: (Int) -> Unit
    ): Int {
        val f = g + heuristic(board)
        if (f > bound) return f
        if (f == g) {
            onFound(g)
            return FOUND
        }
        if (Thread.interrupted() || System.currentTimeMillis() - start > TIME_BUDGET_MS) return TIMEOUT

        var min = Int.MAX_VALUE
        val row = blankPos / SIZE
        val col = blankPos % SIZE

        if (row > 0 && blankPos - SIZE != cameFrom) {
            val neighbor = blankPos - SIZE
            swap(board, blankPos, neighbor)
            val t = search(board, g + 1, bound, neighbor, blankPos, start, onFound)
            swap(board, blankPos, neighbor)
            if (t == FOUND || t == TIMEOUT) return t
            if (t < min) min = t
        }
        if (row < SIZE - 1 && blankPos + SIZE != cameFrom) {
            val neighbor = blankPos + SIZE
            swap(board, blankPos, neighbor)
            val t = search(board, g + 1, bound, neighbor, blankPos, start, onFound)
            swap(board, blankPos, neighbor)
            if (t == FOUND || t == TIMEOUT) return t
            if (t < min) min = t
        }
        if (col > 0 && blankPos - 1 != cameFrom) {
            val neighbor = blankPos - 1
            swap(board, blankPos, neighbor)
            val t = search(board, g + 1, bound, neighbor, blankPos, start, onFound)
            swap(board, blankPos, neighbor)
            if (t == FOUND || t == TIMEOUT) return t
            if (t < min) min = t
        }
        if (col < SIZE - 1 && blankPos + 1 != cameFrom) {
            val neighbor = blankPos + 1
            swap(board, blankPos, neighbor)
            val t = search(board, g + 1, bound, neighbor, blankPos, start, onFound)
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
