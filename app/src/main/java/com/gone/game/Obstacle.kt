package com.gone.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

enum class ObstacleType { LOW_BOX, TALL_BOX, WIDE_LOW, FLOATING }

class Obstacle(
    screenW: Int,
    screenH: Int,
    val type: ObstacleType = ObstacleType.entries.random()
) {
    val groundY = screenH - screenH * GameConstants.GROUND_HEIGHT_FRAC

    val rect: RectF
    val isFloating: Boolean

    init {
        val unit = screenH * 0.08f
        rect = when (type) {
            ObstacleType.LOW_BOX -> RectF(
                screenW.toFloat(), groundY - unit * 1.2f,
                screenW + unit * 1.2f, groundY
            )
            ObstacleType.TALL_BOX -> RectF(
                screenW.toFloat(), groundY - unit * 2.6f,
                screenW + unit * 1.0f, groundY
            )
            ObstacleType.WIDE_LOW -> RectF(
                screenW.toFloat(), groundY - unit * 0.7f,
                screenW + unit * 2.2f, groundY
            )
            ObstacleType.FLOATING -> RectF(
                screenW.toFloat(), groundY - unit * 2.8f,
                screenW + unit * 1.4f, groundY - unit * 1.6f
            )
        }
        isFloating = type == ObstacleType.FLOATING
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF3D00")
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6D00")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55FF3D00")
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 18f
        textAlign = Paint.Align.CENTER
    }

    fun move(dx: Float) {
        rect.offset(-dx, 0f)
    }

    fun isOffScreen() = rect.right < 0

    fun getHitbox() = RectF(rect.left + 3, rect.top + 3, rect.right - 3, rect.bottom - 3)

    fun draw(canvas: Canvas) {
        // Glow halo
        val glow = RectF(rect.left - 6, rect.top - 6, rect.right + 6, rect.bottom + 6)
        canvas.drawRoundRect(glow, 8f, 8f, glowPaint)

        // Fill
        canvas.drawRoundRect(rect, 6f, 6f, fillPaint)

        // Border
        canvas.drawRoundRect(rect, 6f, 6f, borderPaint)

        // Floating indicator
        if (isFloating) {
            canvas.drawText("▼", rect.centerX(), rect.bottom + 20f, labelPaint)
        }
    }
}
