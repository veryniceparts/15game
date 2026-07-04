package com.vnp.fifteen

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val movesText = findViewById<TextView>(R.id.movesText)
        val puzzleView = findViewById<PuzzleView>(R.id.puzzleView)
        val shuffleButton = findViewById<Button>(R.id.shuffleButton)

        puzzleView.onMove = { moves ->
            movesText.text = getString(R.string.moves_format, moves)
        }
        puzzleView.onWin = { moves ->
            Toast.makeText(this, getString(R.string.win_message, moves), Toast.LENGTH_LONG).show()
        }
        shuffleButton.setOnClickListener {
            puzzleView.shuffle()
        }

        movesText.text = getString(R.string.moves_format, 0)
    }
}
