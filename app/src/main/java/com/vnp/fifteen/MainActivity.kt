package com.vnp.fifteen

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Shorter time budget for the on-demand "step toward optimal" button, so a click stays snappy. */
private const val STEP_TIME_BUDGET_MS = 2500L

class MainActivity : AppCompatActivity() {

    private lateinit var optimalText: TextView
    private lateinit var puzzleView: PuzzleView
    private lateinit var undoButton: Button
    private lateinit var stepButton: Button
    private var solverThread: Thread? = null
    private var solverGeneration = 0

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
            movesText.text = getString(R.string.moves_format, moves)
            undoButton.isEnabled = puzzleView.canUndo()
            stepButton.isEnabled = !puzzleView.isSolvedNow
        }
        puzzleView.onWin = { moves ->
            Toast.makeText(this, getString(R.string.win_message, moves), Toast.LENGTH_LONG).show()
        }
        puzzleView.onShuffle = { startSolving() }
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

    private fun startSolving() {
        solverThread?.interrupt()
        val generation = ++solverGeneration
        optimalText.text = getString(R.string.optimal_computing)

        val board = puzzleView.snapshotBoard()
        val thread = Thread {
            val result = PuzzleSolver.solve(board)
            runOnUiThread {
                if (generation == solverGeneration) {
                    optimalText.text = when (result) {
                        is PuzzleSolver.Result.Solved -> getString(R.string.optimal_format, result.moves)
                        is PuzzleSolver.Result.TimedOut -> getString(R.string.optimal_lower_bound_format, result.lowerBound)
                    }
                }
            }
        }
        solverThread = thread
        thread.start()
    }

    /** Recomputes the optimal solution for the current board and plays just its first move. */
    private fun stepTowardOptimal() {
        solverThread?.interrupt()
        val generation = ++solverGeneration
        stepButton.isEnabled = false
        undoButton.isEnabled = false
        puzzleView.isLocked = true
        optimalText.text = getString(R.string.optimal_computing)

        val board = puzzleView.snapshotBoard()
        val thread = Thread {
            val result = PuzzleSolver.solve(board, STEP_TIME_BUDGET_MS)
            val move = when (result) {
                is PuzzleSolver.Result.Solved -> result.path.firstOrNull()
                is PuzzleSolver.Result.TimedOut -> PuzzleSolver.bestNeighborMove(board)
            }
            runOnUiThread {
                if (generation == solverGeneration) {
                    optimalText.text = when (result) {
                        is PuzzleSolver.Result.Solved -> getString(R.string.optimal_format, result.moves)
                        is PuzzleSolver.Result.TimedOut -> getString(R.string.optimal_lower_bound_format, result.lowerBound)
                    }
                    move?.let { puzzleView.playMove(it) }
                    puzzleView.isLocked = false
                    stepButton.isEnabled = !puzzleView.isSolvedNow
                    undoButton.isEnabled = puzzleView.canUndo()
                }
            }
        }
        solverThread = thread
        thread.start()
    }
}
