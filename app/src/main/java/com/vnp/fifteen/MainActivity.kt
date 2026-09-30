package com.vnp.fifteen

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var optimalText: TextView
    private lateinit var puzzleView: PuzzleView
    private var solverThread: Thread? = null
    private var solverGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val movesText = findViewById<TextView>(R.id.movesText)
        optimalText = findViewById(R.id.optimalText)
        puzzleView = findViewById(R.id.puzzleView)
        val shuffleButton = findViewById<Button>(R.id.shuffleButton)

        puzzleView.onMove = { moves ->
            movesText.text = getString(R.string.moves_format, moves)
        }
        puzzleView.onWin = { moves ->
            Toast.makeText(this, getString(R.string.win_message, moves), Toast.LENGTH_LONG).show()
        }
        puzzleView.onShuffle = { startSolving() }
        shuffleButton.setOnClickListener {
            puzzleView.shuffle()
        }

        movesText.text = getString(R.string.moves_format, 0)
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
}
