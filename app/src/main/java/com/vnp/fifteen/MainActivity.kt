package com.vnp.fifteen

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

private const val REPLAY_STEP_DELAY_MS = 220L

class MainActivity : AppCompatActivity() {

    private lateinit var optimalText: TextView
    private lateinit var puzzleView: PuzzleView
    private lateinit var solveButton: Button
    private var solverThread: Thread? = null
    private var solverGeneration = 0

    private val replayHandler = Handler(Looper.getMainLooper())
    private var currentSolutionPath: IntArray? = null
    private var isAutoPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val movesText = findViewById<TextView>(R.id.movesText)
        optimalText = findViewById(R.id.optimalText)
        puzzleView = findViewById(R.id.puzzleView)
        val shuffleButton = findViewById<Button>(R.id.shuffleButton)
        solveButton = findViewById(R.id.solveButton)

        puzzleView.onMove = { moves ->
            movesText.text = getString(R.string.moves_format, moves)
            if (!isAutoPlaying) {
                clearSolution()
            }
        }
        puzzleView.onWin = { moves ->
            Toast.makeText(this, getString(R.string.win_message, moves), Toast.LENGTH_LONG).show()
        }
        puzzleView.onShuffle = { startSolving() }
        shuffleButton.setOnClickListener {
            puzzleView.shuffle()
        }
        solveButton.setOnClickListener {
            currentSolutionPath?.let { playOptimalSolution(it) }
        }

        movesText.text = getString(R.string.moves_format, 0)
        startSolving() // covers the initial shuffle already done while the view was created
    }

    private fun startSolving() {
        clearSolution()
        solverThread?.interrupt()
        val generation = ++solverGeneration
        optimalText.text = getString(R.string.optimal_computing)

        val board = puzzleView.snapshotBoard()
        val thread = Thread {
            val result = PuzzleSolver.solve(board)
            runOnUiThread {
                if (generation == solverGeneration) {
                    when (result) {
                        is PuzzleSolver.Result.Solved -> {
                            optimalText.text = getString(R.string.optimal_format, result.moves)
                            currentSolutionPath = result.path
                            solveButton.isEnabled = true
                        }
                        is PuzzleSolver.Result.TimedOut -> {
                            optimalText.text = getString(R.string.optimal_lower_bound_format, result.lowerBound)
                        }
                    }
                }
            }
        }
        solverThread = thread
        thread.start()
    }

    private fun clearSolution() {
        replayHandler.removeCallbacksAndMessages(null)
        isAutoPlaying = false
        puzzleView.isLocked = false
        currentSolutionPath = null
        solveButton.isEnabled = false
    }

    private fun playOptimalSolution(path: IntArray) {
        solveButton.isEnabled = false
        isAutoPlaying = true
        puzzleView.isLocked = true

        var step = 0
        lateinit var playNext: Runnable
        playNext = Runnable {
            if (step >= path.size) {
                isAutoPlaying = false
                puzzleView.isLocked = false
                return@Runnable
            }
            puzzleView.playMove(path[step])
            step++
            replayHandler.postDelayed(playNext, REPLAY_STEP_DELAY_MS)
        }
        replayHandler.post(playNext)
    }
}
