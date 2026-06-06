package com.gone.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

enum class PlayerState { RUNNING, JUMPING, SLIDING, DEAD }

class Player(screenW: Int, screenH: Int) {

    val w = screenW * GameConstants.PLAYER_WIDTH_FRAC
    val h = screenH * GameConstants.PLAYER_HEIGHT_FRAC
    val groundY = screenH - screenH * GameConstants.GROUND_HEIGHT_FRAC
    val x = screenW * GameConstants.PLAYER_X_FRAC

    var y = groundY - h
    var velY = 0f
    var state = PlayerState.RUNNING

    private var slideTimer = 0L
    private var animFrame = 0
    private var animTick = 0

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")   // cyan stickman
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3000E5FF")
        style = Paint.Style.FILL
    }

    fun jump() {
        if (state == PlayerState.RUNNING) {
            velY = GameConstants.JUMP_VELOCITY
            state = PlayerState.JUMPING
        }
    }

    fun slide() {
        if (state == PlayerState.RUNNING) {
            state = PlayerState.SLIDING
            slideTimer = GameConstants.SLIDE_DURATION_MS
        }
    }

    fun update(dt: Float) {
        if (state == PlayerState.DEAD) return

        // Slide countdown
        if (state == PlayerState.SLIDING) {
            slideTimer -= (dt * 1000).toLong()
            if (slideTimer <= 0) state = PlayerState.RUNNING
        }

        // Gravity
        if (state == PlayerState.JUMPING || y < groundY - h) {
            velY += GameConstants.GRAVITY * dt
            y += velY * dt

            if (y >= groundY - h) {
                y = groundY - h
                velY = 0f
                state = PlayerState.RUNNING
            }
        }

        // Animation tick
        animTick++
        if (animTick >= 8) { animTick = 0; animFrame = (animFrame + 1) % 4 }
    }

    fun getHitbox(): RectF {
        return if (state == PlayerState.SLIDING) {
            // Crouched hitbox
            RectF(x + 4, groundY - h * 0.5f, x + w - 4, groundY)
        } else {
            RectF(x + 4, y + 4, x + w - 4, y + h - 4)
        }
    }

    fun draw(canvas: Canvas) {
        if (state == PlayerState.DEAD) {
            drawDead(canvas)
            return
        }

        val isSliding = state == PlayerState.SLIDING
        val cx = x + w / 2f

        if (isSliding) {
            drawSliding(canvas, cx)
        } else {
            drawRunning(canvas, cx)
        }

        // Shadow
        val shadowY = groundY + 2
        canvas.drawOval(
            RectF(cx - w * 0.4f, shadowY, cx + w * 0.4f, shadowY + 6),
            shadowPaint
        )
    }

    private fun drawRunning(canvas: Canvas, cx: Float) {
        val headR = w * 0.22f
        val headCy = y + headR * 1.1f
        val shoulderY = headCy + headR * 1.3f
        val hipY = y + h * 0.55f
        val footY = y + h

        // Head
        canvas.drawCircle(cx, headCy, headR, headPaint)

        // Torso
        canvas.drawLine(cx, shoulderY, cx, hipY, bodyPaint)

        // Arms — swinging
        val armSwing = if (animFrame < 2) 14f else -14f
        canvas.drawLine(cx, shoulderY, cx - w * 0.4f, shoulderY + armSwing + 10, bodyPaint)
        canvas.drawLine(cx, shoulderY, cx + w * 0.4f, shoulderY - armSwing + 10, bodyPaint)

        // Legs — running cycle
        val legSwing = when (animFrame) { 0 -> 18f; 1 -> 6f; 2 -> -18f; else -> -6f }
        canvas.drawLine(cx, hipY, cx - w * 0.3f, footY - legSwing, bodyPaint)
        canvas.drawLine(cx, hipY, cx + w * 0.3f, footY + legSwing, bodyPaint)
    }

    private fun drawSliding(canvas: Canvas, cx: Float) {
        val baseY = groundY
        val headR = w * 0.22f

        // Head far back
        canvas.drawCircle(cx - w * 0.25f, baseY - h * 0.4f, headR, headPaint)
        // Stretched body
        canvas.drawLine(cx - w * 0.25f + headR, baseY - h * 0.4f, cx + w * 0.4f, baseY - h * 0.15f, bodyPaint)
        // Legs flat
        canvas.drawLine(cx + w * 0.1f, baseY - h * 0.15f, cx + w * 0.55f, baseY - h * 0.05f, bodyPaint)
        canvas.drawLine(cx + w * 0.1f, baseY - h * 0.15f, cx + w * 0.4f, baseY, bodyPaint)
        // Arms
        canvas.drawLine(cx - w * 0.1f, baseY - h * 0.35f, cx - w * 0.1f, baseY - h * 0.1f, bodyPaint)
    }

    private fun drawDead(canvas: Canvas) {
        val cx = x + w / 2f
        val headR = w * 0.22f
        // Collapsed stickman
        canvas.drawCircle(cx, groundY - headR, headR, headPaint)
        canvas.drawLine(cx, groundY - headR * 2, cx, groundY - h * 0.5f, bodyPaint)
        canvas.drawLine(cx, groundY - h * 0.5f, cx - w * 0.5f, groundY, bodyPaint)
        canvas.drawLine(cx, groundY - h * 0.5f, cx + w * 0.3f, groundY - h * 0.1f, bodyPaint)
    }

    fun die() { state = PlayerState.DEAD }
    fun isOnGround() = y >= groundY - h - 2f
}
