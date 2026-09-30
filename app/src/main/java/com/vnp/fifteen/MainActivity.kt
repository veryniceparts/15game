package com.vnp.fifteen

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Time budget for an on-demand recompute (triggered by divergence), kept short so it stays responsive. */
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
            startSolving()
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
        startSolving() // covers the initial shuffle already done while the view was created
    }

    /** After any committed move, keeps [cachedPath] in sync if it matches, or drops it if the board diverged. */
    private fun reconcileCacheWithBoard() {
        val path = cachedPath
        val pathBoard = cachedPathBoard
        if (path == null || pathBoard == null || path.isEmpty()) return
        val expected = applyMove(pathBoard, path.first())
        val actual = puzzleView.snapshotBoard()
        if (expected.contentEquals(actual)) {
            cachedPath = path.copyOfRange(1, path.size)
            cachedPathBoard = actual
        } else {
            cachedPath = null
            cachedPathBoard = null
        }
    }

    private fun applyMove(board: IntArray, tappedIndex: Int): IntArray {
        val blank = board.indexOf(0)
        val result = board.copyOf()
        result[blank] = result[tappedIndex]
        result[tappedIndex] = 0
        return result
    }

    /** Background solve used only to populate the "Optimal: N" label and prime the path cache after a shuffle. */
    private fun startSolving() {
        solverThread?.interrupt()
        val generation = ++solverGeneration
        optimalText.text = getString(R.string.optimal_computing)

        val board = puzzleView.snapshotBoard()
        val movesAtRequest = currentMoves
        val thread = Thread {
            val result = PuzzleSolver.findPath(board)
            runOnUiThread {
                if (generation == solverGeneration) {
                    optimalText.text = when (result) {
                        is PuzzleSolver.PathResult.Exact ->
                            getString(R.string.optimal_format, movesAtRequest + result.moves)
                        is PuzzleSolver.PathResult.Approximate ->
                            getString(R.string.optimal_lower_bound_format, movesAtRequest + result.lowerBound)
                    }
                    if (cachedPath == null && board.contentEquals(puzzleView.snapshotBoard())) {
                        val path = when (result) {
                            is PuzzleSolver.PathResult.Exact -> result.path
                            is PuzzleSolver.PathResult.Approximate -> result.path
                        }
                        cachedPath = path
                        cachedPathBoard = board
                    }
                }
            }
        }
        solverThread = thread
        thread.start()
    }

    /** Follows the cached path one step at a time; only recalculates when the board has diverged from it. */
    private fun stepTowardOptimal() {
        val path = cachedPath
        val pathBoard = cachedPathBoard
        if (path != null && path.isNotEmpty() && pathBoard != null && pathBoard.contentEquals(puzzleView.snapshotBoard())) {
            puzzleView.playMove(path.first())
            return
        }
        recomputeAndStep()
    }

    private fun recomputeAndStep() {
        solverThread?.interrupt()
        val generation = ++solverGeneration
        isComputing = true
        stepButton.isEnabled = false
        undoButton.isEnabled = false
        puzzleView.isLocked = true
        optimalText.text = getString(R.string.optimal_computing)

        val board = puzzleView.snapshotBoard()
        val movesAtRequest = currentMoves
        val thread = Thread {
            val result = PuzzleSolver.findPath(board, RECOMPUTE_EXACT_BUDGET_MS, RECOMPUTE_GREEDY_BUDGET_MS)
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
                    cachedPath = path
                    cachedPathBoard = board
                    isComputing = false
                    puzzleView.isLocked = false
                    if (path.isNotEmpty()) {
                        puzzleView.playMove(path.first())
                    } else {
                        stepButton.isEnabled = !puzzleView.isSolvedNow
                        undoButton.isEnabled = puzzleView.canUndo()
                    }
                }
            }
        }
        solverThread = thread
        thread.start()
    }
}
