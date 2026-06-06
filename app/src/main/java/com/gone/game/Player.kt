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

    // Double jump tracking
    private var canDoubleJump = false
    private var doubleJumpFlash = 0f   // > 0 while the air-dash flash is active (seconds)

    private var slideTimer = 0L
    private var animFrame = 0
    private var animTick = 0

    // ── Paints ─────────────────────────────────────────────────────────────────
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
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
    // Flash ring shown on double-jump
    private val doubleJumpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.parseColor("#FFD600")
    }
    // Trail streak behind stickman on double-jump
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }

    // ── Actions ────────────────────────────────────────────────────────────────

    fun jump() {
        when {
            // First jump — must be on ground / running
            state == PlayerState.RUNNING -> {
                velY = GameConstants.JUMP_VELOCITY
                state = PlayerState.JUMPING
                canDoubleJump = true       // grant double-jump token
            }
            // Second jump — in the air with token available
            state == PlayerState.JUMPING && canDoubleJump -> {
                velY = GameConstants.DOUBLE_JUMP_VELOCITY
                canDoubleJump = false
                doubleJumpFlash = 0.25f    // show flash for 250 ms
            }
        }
    }

    fun slide() {
        if (state == PlayerState.RUNNING) {
            state = PlayerState.SLIDING
            slideTimer = GameConstants.SLIDE_DURATION_MS
        }
    }

    // ── Update ─────────────────────────────────────────────────────────────────

    fun update(dt: Float) {
        if (state == PlayerState.DEAD) return

        // Slide countdown
        if (state == PlayerState.SLIDING) {
            slideTimer -= (dt * 1000).toLong()
            if (slideTimer <= 0) state = PlayerState.RUNNING
        }

        // Gravity — applied whenever airborne
        if (state == PlayerState.JUMPING || y < groundY - h - 1f) {
            velY += GameConstants.GRAVITY * dt
            y += velY * dt

            if (y >= groundY - h) {
                y = groundY - h
                velY = 0f
                canDoubleJump = false
                state = PlayerState.RUNNING
            }
        }

        // Double-jump flash timer
        if (doubleJumpFlash > 0f) doubleJumpFlash -= dt

        // Running animation tick (8 ticks per frame, 4 frames)
        animTick++
        if (animTick >= 8) { animTick = 0; animFrame = (animFrame + 1) % 4 }
    }

    // ── Hitbox ─────────────────────────────────────────────────────────────────

    fun getHitbox(): RectF = if (state == PlayerState.SLIDING) {
        RectF(x + 4, groundY - h * 0.5f, x + w - 4, groundY)
    } else {
        RectF(x + 4, y + 4, x + w - 4, y + h - 4)
    }

    // ── Draw ───────────────────────────────────────────────────────────────────

    fun draw(canvas: Canvas) {
        if (state == PlayerState.DEAD) { drawDead(canvas); return }

        val cx = x + w / 2f

        // Double-jump burst ring + trailing streaks
        if (doubleJumpFlash > 0f) {
            val alpha = (doubleJumpFlash / 0.25f * 255).toInt().coerceIn(0, 255)
            val radius = w * (1.5f + (1f - doubleJumpFlash / 0.25f) * 1.5f)
            doubleJumpPaint.alpha = alpha
            canvas.drawCircle(cx, y + h / 2f, radius, doubleJumpPaint)

            // 3 horizontal streak lines behind the player
            for (i in 0..2) {
                val offset = i * h * 0.12f - h * 0.12f
                val streakLen = w * (1f + (1f - doubleJumpFlash / 0.25f) * 2f)
                trailPaint.alpha = (alpha * 0.6f).toInt()
                trailPaint.color = Color.parseColor("#FFD600")
                canvas.drawLine(cx - w * 0.5f - streakLen, y + h * 0.5f + offset,
                                cx - w * 0.5f, y + h * 0.5f + offset, trailPaint)
            }
        }

        when (state) {
            PlayerState.SLIDING -> drawSliding(canvas, cx)
            else -> drawRunning(canvas, cx)
        }

        // Ground shadow — shrinks when airborne
        val airFrac = ((groundY - h - y) / (h * 2f)).coerceIn(0f, 1f)
        val shadowW = w * 0.4f * (1f - airFrac * 0.7f)
        shadowPaint.alpha = (120 - (airFrac * 100).toInt()).coerceIn(20, 120)
        canvas.drawOval(
            RectF(cx - shadowW, groundY + 2f, cx + shadowW, groundY + 6f),
            shadowPaint
        )

        // Double-jump indicator dot above head when token is available mid-air
        if (state == PlayerState.JUMPING && canDoubleJump) {
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FFD600")
                alpha = 200
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, y - w * 0.3f, w * 0.12f, dotPaint)
        }
    }

    private fun drawRunning(canvas: Canvas, cx: Float) {
        val headR = w * 0.22f
        val headCy = y + headR * 1.1f
        val shoulderY = headCy + headR * 1.3f
        val hipY = y + h * 0.55f
        val footY = y + h

        // Tuck legs when in the air (jumping pose vs running pose)
        val isAir = state == PlayerState.JUMPING

        canvas.drawCircle(cx, headCy, headR, headPaint)
        canvas.drawLine(cx, shoulderY, cx, hipY, bodyPaint)

        if (isAir) {
            // Arms spread, legs tucked under
            canvas.drawLine(cx, shoulderY, cx - w * 0.5f, shoulderY + 6f, bodyPaint)
            canvas.drawLine(cx, shoulderY, cx + w * 0.5f, shoulderY + 6f, bodyPaint)
            canvas.drawLine(cx, hipY, cx - w * 0.3f, footY - 20f, bodyPaint)
            canvas.drawLine(cx, hipY, cx + w * 0.3f, footY - 20f, bodyPaint)
        } else {
            // Running swing
            val armSwing = if (animFrame < 2) 14f else -14f
            canvas.drawLine(cx, shoulderY, cx - w * 0.4f, shoulderY + armSwing + 10, bodyPaint)
            canvas.drawLine(cx, shoulderY, cx + w * 0.4f, shoulderY - armSwing + 10, bodyPaint)
            val legSwing = when (animFrame) { 0 -> 18f; 1 -> 6f; 2 -> -18f; else -> -6f }
            canvas.drawLine(cx, hipY, cx - w * 0.3f, footY - legSwing, bodyPaint)
            canvas.drawLine(cx, hipY, cx + w * 0.3f, footY + legSwing, bodyPaint)
        }
    }

    private fun drawSliding(canvas: Canvas, cx: Float) {
        val baseY = groundY
        val headR = w * 0.22f
        canvas.drawCircle(cx - w * 0.25f, baseY - h * 0.4f, headR, headPaint)
        canvas.drawLine(cx - w * 0.25f + headR, baseY - h * 0.4f, cx + w * 0.4f, baseY - h * 0.15f, bodyPaint)
        canvas.drawLine(cx + w * 0.1f, baseY - h * 0.15f, cx + w * 0.55f, baseY - h * 0.05f, bodyPaint)
        canvas.drawLine(cx + w * 0.1f, baseY - h * 0.15f, cx + w * 0.4f, baseY, bodyPaint)
        canvas.drawLine(cx - w * 0.1f, baseY - h * 0.35f, cx - w * 0.1f, baseY - h * 0.1f, bodyPaint)
    }

    private fun drawDead(canvas: Canvas) {
        val cx = x + w / 2f
        val headR = w * 0.22f
        canvas.drawCircle(cx, groundY - headR, headR, headPaint)
        canvas.drawLine(cx, groundY - headR * 2, cx, groundY - h * 0.5f, bodyPaint)
        canvas.drawLine(cx, groundY - h * 0.5f, cx - w * 0.5f, groundY, bodyPaint)
        canvas.drawLine(cx, groundY - h * 0.5f, cx + w * 0.3f, groundY - h * 0.1f, bodyPaint)
    }

    fun die() { state = PlayerState.DEAD }
    fun isOnGround() = y >= groundY - h - 2f
}
