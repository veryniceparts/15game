package com.vnp.fifteen

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import kotlin.random.Random

private const val SIZE = 4
private const val TILE_COUNT = SIZE * SIZE
private const val BLANK = 0
private const val SLIDE_DURATION_MS = 140L

class PuzzleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onMove: ((moves: Int) -> Unit)? = null
    var onWin: ((moves: Int) -> Unit)? = null
    var onShuffle: (() -> Unit)? = null

    /** Set while an automated replay (e.g. "show optimal solve") is driving the board, to ignore user taps. */
    var isLocked = false

    private val board = IntArray(TILE_COUNT) { (it + 1) % TILE_COUNT }
    private var moves = 0
    private var solved = true
    private var boardSize = 0f
    private var tileSize = 0f
    private val gap = 8f

    private var animator: ValueAnimator? = null
    private var slidingFromIndex = -1
    private var slidingToIndex = -1
    private var slideFraction = 0f

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
        animator?.cancel()
        slidingFromIndex = -1
        slidingToIndex = -1
        do {
            for (i in board.indices) board[i] = (i + 1) % TILE_COUNT
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
        onShuffle?.invoke()
    }

    /** Read-only copy of the current tile layout, for external analysis (e.g. an optimal-move solver). */
    fun snapshotBoard(): IntArray = board.copyOf()

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
        if (isLocked || event.action != MotionEvent.ACTION_UP || solved || slidingFromIndex != -1) return true
        val offsetX = (width - boardSize) / 2f
        val offsetY = (height - boardSize) / 2f
        val col = ((event.x - offsetX) / (tileSize + gap)).toInt()
        val row = ((event.y - offsetY) / (tileSize + gap)).toInt()
        if (row !in 0 until SIZE || col !in 0 until SIZE) return true

        attemptMove(row * SIZE + col)
        return true
    }

    /** Slides the tile at [tappedIndex] into the blank, the same as a user tap -- used to replay a solution. */
    fun playMove(tappedIndex: Int) {
        if (solved || slidingFromIndex != -1) return
        attemptMove(tappedIndex)
    }

    private fun attemptMove(tappedIndex: Int) {
        val blankIndex = board.indexOf(BLANK)
        if (tappedIndex in neighborIndices(blankIndex)) {
            startSlide(tappedIndex, blankIndex)
        }
    }

    private fun startSlide(fromIndex: Int, toIndex: Int) {
        slidingFromIndex = fromIndex
        slidingToIndex = toIndex
        slideFraction = 0f
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SLIDE_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                slideFraction = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    finishSlide(fromIndex, toIndex)
                }
            })
            start()
        }
    }

    private fun finishSlide(fromIndex: Int, toIndex: Int) {
        board[toIndex] = board[fromIndex]
        board[fromIndex] = BLANK
        slidingFromIndex = -1
        slidingToIndex = -1
        moves++
        onMove?.invoke(moves)
        invalidate()
        if (isSolved()) {
            solved = true
            onWin?.invoke(moves)
        }
    }

    private fun rectForIndex(index: Int, offsetX: Float, offsetY: Float): RectF {
        val row = index / SIZE
        val col = index % SIZE
        val left = offsetX + gap + col * (tileSize + gap)
        val top = offsetY + gap + row * (tileSize + gap)
        return RectF(left, top, left + tileSize, top + tileSize)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val offsetX = (width - boardSize) / 2f
        val offsetY = (height - boardSize) / 2f
        val corner = tileSize * 0.12f

        for (index in board.indices) {
            if (index == slidingFromIndex) {
                canvas.drawRoundRect(rectForIndex(index, offsetX, offsetY), corner, corner, blankPaint)
                continue
            }
            val rect = rectForIndex(index, offsetX, offsetY)
            val value = board[index]

            if (value == BLANK) {
                canvas.drawRoundRect(rect, corner, corner, blankPaint)
            } else {
                canvas.drawRoundRect(rect, corner, corner, tilePaint)
                drawTileText(canvas, rect, value)
            }
        }

        if (slidingFromIndex != -1) {
            val fromRect = rectForIndex(slidingFromIndex, offsetX, offsetY)
            val toRect = rectForIndex(slidingToIndex, offsetX, offsetY)
            val left = fromRect.left + (toRect.left - fromRect.left) * slideFraction
            val top = fromRect.top + (toRect.top - fromRect.top) * slideFraction
            val rect = RectF(left, top, left + tileSize, top + tileSize)
            canvas.drawRoundRect(rect, corner, corner, tilePaint)
            drawTileText(canvas, rect, board[slidingFromIndex])
        }
    }

    private fun drawTileText(canvas: Canvas, rect: RectF, value: Int) {
        val textY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(value.toString(), rect.centerX(), textY, textPaint)
    }
}
