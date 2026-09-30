package com.vnp.fifteen

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Budget for the initial post-shuffle solve, which can afford to try harder for an exact answer. */
private const val INITIAL_EXACT_BUDGET_MS = 6000L
private const val INITIAL_GREEDY_BUDGET_MS = 5000L

/** Shorter budget for a recompute triggered by the player diverging from the last known path. */
private const val RECOMPUTE_EXACT_BUDGET_MS = 2500L
private const val RECOMPUTE_GREEDY_BUDGET_MS = 4000L

class MainActivity : AppCompatActivity() {

    private lateinit var optimalText: TextView
    private lateinit var puzzleView: PuzzleView
    private lateinit var undoButton: Button
    private lateinit var stepButton: Button
    private var solverThread: Thread? = null
    private var solverGeneration = 0
    private var isComputing = false
    private var currentMoves = 0

    /** Remaining moves of the last known path to solved, and the board it was computed for. Null when stale/unknown. */
    private var cachedPath: IntArray? = null
    private var cachedPathBoard: IntArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val movesText = findViewById<TextView>(R.id.movesText)
        optimalText = findViewById(R.id.optimalText)
        puzzleView = findViewById(R.id.puzzleView)
        val shuffleButton = findViewById<Button>(R.id.shuffleButton)
        undoButton = findViewById(R.id.undoButton)
        stepButton = findViewById(R.id.stepButton)

        puzzleView.onMove = { moves ->
            currentMoves = moves
            movesText.text = getString(R.string.moves_format, moves)
            undoButton.isEnabled = puzzleView.canUndo() && !isComputing
            stepButton.isEnabled = !puzzleView.isSolvedNow && !isComputing
            reconcileCacheWithBoard()
        }
        puzzleView.onWin = { moves ->
            Toast.makeText(this, getString(R.string.win_message, moves), Toast.LENGTH_LONG).show()
        }
        puzzleView.onShuffle = {
            cachedPath = null
            cachedPathBoard = null
            refreshOptimal(INITIAL_EXACT_BUDGET_MS, INITIAL_GREEDY_BUDGET_MS, lockUi = false, playFirstMoveWhenDone = false)
        }
        shuffleButton.setOnClickListener {
            puzzleView.shuffle()
        }
        undoButton.setOnClickListener {
            puzzleView.undo()
        }
        stepButton.setOnClickListener {
            stepTowardOptimal()
        }

        movesText.text = getString(R.string.moves_format, 0)
        undoButton.isEnabled = false
        // Covers the initial shuffle already done while the view was created.
        refreshOptimal(INITIAL_EXACT_BUDGET_MS, INITIAL_GREEDY_BUDGET_MS, lockUi = false, playFirstMoveWhenDone = false)
    }

    /**
     * After any committed move, keeps [cachedPath] in sync if it matches the move just made.
     * If the board diverged (a manual tap or undo that didn't match the cached next step), the
     * cached "Optimal: N" label is now stale -- possibly even less than the current move count
     * -- so quietly recompute it in the background without locking the board.
     */
    private fun reconcileCacheWithBoard() {
        val path = cachedPath
        val pathBoard = cachedPathBoard
        if (path != null && pathBoard != null && path.isNotEmpty()) {
            val expected = applyMove(pathBoard, path.first())
            val actual = puzzleView.snapshotBoard()
            if (expected.contentEquals(actual)) {
                cachedPath = path.copyOfRange(1, path.size)
                cachedPathBoard = actual
                return
            }
        }
        // No valid cache for the current board (diverged, or a prior refresh hasn't landed yet):
        // keep (re)triggering a background refresh so the label converges on the latest position
        // instead of showing a stale total left over from before this move.
        cachedPath = null
        cachedPathBoard = null
        refreshOptimal(RECOMPUTE_EXACT_BUDGET_MS, RECOMPUTE_GREEDY_BUDGET_MS, lockUi = false, playFirstMoveWhenDone = false)
    }

    private fun applyMove(board: IntArray, tappedIndex: Int): IntArray {
        val blank = board.indexOf(0)
        val result = board.copyOf()
        result[blank] = result[tappedIndex]
        result[tappedIndex] = 0
        return result
    }

    /** Follows the cached path one step at a time; only recalculates when the board has diverged from it. */
    private fun stepTowardOptimal() {
        val path = cachedPath
        val pathBoard = cachedPathBoard
        if (path != null && path.isNotEmpty() && pathBoard != null && pathBoard.contentEquals(puzzleView.snapshotBoard())) {
            puzzleView.playMove(path.first())
            return
        }
        refreshOptimal(RECOMPUTE_EXACT_BUDGET_MS, RECOMPUTE_GREEDY_BUDGET_MS, lockUi = true, playFirstMoveWhenDone = true)
    }

    /**
     * Recomputes a full path to solved from the current board and updates the "Optimal: N" label
     * as (moves already made) + (moves this path still needs) -- so it always reads as a live
     * best-possible total from here, never less than the move counter. [lockUi] blocks input while
     * computing (used for an explicit step request); a background divergence refresh leaves input
     * free. [playFirstMoveWhenDone] plays the path's first move once it's ready (the step button).
     */
    private fun refreshOptimal(exactBudgetMs: Long, greedyBudgetMs: Long, lockUi: Boolean, playFirstMoveWhenDone: Boolean) {
        solverThread?.interrupt()
        val generation = ++solverGeneration
        if (lockUi) {
            isComputing = true
            stepButton.isEnabled = false
            undoButton.isEnabled = false
            puzzleView.isLocked = true
        }
        optimalText.text = getString(R.string.optimal_computing)

        val board = puzzleView.snapshotBoard()
        val movesAtRequest = currentMoves
        val thread = Thread {
            val result = PuzzleSolver.findPath(board, exactBudgetMs, greedyBudgetMs)
            runOnUiThread {
                if (generation == solverGeneration) {
                    val path = when (result) {
                        is PuzzleSolver.PathResult.Exact -> {
                            optimalText.text = getString(R.string.optimal_format, movesAtRequest + result.moves)
                            result.path
                        }
                        is PuzzleSolver.PathResult.Approximate -> {
                            optimalText.text = getString(R.string.optimal_lower_bound_format, movesAtRequest + result.lowerBound)
                            result.path
                        }
                    }
                    if (board.contentEquals(puzzleView.snapshotBoard())) {
                        cachedPath = path
                        cachedPathBoard = board
                    }
                    if (lockUi) {
                        isComputing = false
                        puzzleView.isLocked = false
                    }
                    if (playFirstMoveWhenDone && path.isNotEmpty()) {
                        puzzleView.playMove(path.first())
                    } else {
                        stepButton.isEnabled = !puzzleView.isSolvedNow && !isComputing
                        undoButton.isEnabled = puzzleView.canUndo() && !isComputing
                    }
                }
            }
        }
        solverThread = thread
        thread.start()
    }
}
