package com.vnp.fifteen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.random.Random

private const val SIZE = 4
private const val TILE_COUNT = SIZE * SIZE
private const val BLANK = 0

class PuzzleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onMove: ((moves: Int) -> Unit)? = null
    var onWin: ((moves: Int) -> Unit)? = null

    private val board = IntArray(TILE_COUNT) { (it + 1) % TILE_COUNT }
    private var moves = 0
    private var solved = true
    private var boardSize = 0f
    private var tileSize = 0f
    private val gap = 8f

    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.tile)
    }
    private val blankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.blank)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.tile_text)
        textAlign = Paint.Align.CENTER
    }

    init {
        shuffle()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        boardSize = minOf(w, h).toFloat()
        tileSize = (boardSize - gap * (SIZE + 1)) / SIZE
        textPaint.textSize = tileSize * 0.42f
    }

    fun shuffle() {
        do {
            for (i in board.indices) board[i] = i
            var blankIndex = TILE_COUNT - 1
            repeat(400) {
                val neighbors = neighborIndices(blankIndex)
                val next = neighbors[Random.nextInt(neighbors.size)]
                board[blankIndex] = board[next]
                board[next] = BLANK
                blankIndex = next
            }
        } while (isSolved())
        moves = 0
        solved = false
        onMove?.invoke(moves)
        invalidate()
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

    private fun isSolved(): Boolean {
        for (i in 0 until TILE_COUNT - 1) {
            if (board[i] != i + 1) return false
        }
        return board[TILE_COUNT - 1] == BLANK
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP || solved) return true
        val offsetX = (width - boardSize) / 2f
        val offsetY = (height - boardSize) / 2f
        val col = ((event.x - offsetX) / (tileSize + gap)).toInt()
        val row = ((event.y - offsetY) / (tileSize + gap)).toInt()
        if (row !in 0 until SIZE || col !in 0 until SIZE) return true

        val index = row * SIZE + col
        val blankIndex = board.indexOf(BLANK)
        if (index in neighborIndices(blankIndex)) {
            board[blankIndex] = board[index]
            board[index] = BLANK
            moves++
            onMove?.invoke(moves)
            invalidate()
            if (isSolved()) {
                solved = true
                onWin?.invoke(moves)
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val offsetX = (width - boardSize) / 2f
        val offsetY = (height - boardSize) / 2f
        val corner = tileSize * 0.12f

        for (index in board.indices) {
            val row = index / SIZE
            val col = index % SIZE
            val left = offsetX + gap + col * (tileSize + gap)
            val top = offsetY + gap + row * (tileSize + gap)
            val rect = RectF(left, top, left + tileSize, top + tileSize)
            val value = board[index]

            if (value == BLANK) {
                canvas.drawRoundRect(rect, corner, corner, blankPaint)
            } else {
                canvas.drawRoundRect(rect, corner, corner, tilePaint)
                val textY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(value.toString(), rect.centerX(), textY, textPaint)
            }
        }
    }
}
