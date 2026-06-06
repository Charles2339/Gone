package com.gone.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.max

enum class PlayerState { RUNNING, JUMPING, SLIDING, DEAD }

class Player(private val screenW: Int, private val screenH: Int) {

    val w = screenW * GameConstants.PLAYER_WIDTH_FRAC
    val h = screenH * GameConstants.PLAYER_HEIGHT_FRAC
    val groundY = screenH - screenH * GameConstants.GROUND_HEIGHT_FRAC
    val x = screenW * GameConstants.PLAYER_X_FRAC

    var y = groundY - h
    var velY = 0f
    var state = PlayerState.RUNNING

    // Double-jump
    private var canDoubleJump = false
    private var doubleJumpFlash = 0f

    // Slide
    private var slideTimer = 0L

    // Smooth animation — continuous phase in radians
    private var animPhase = 0f        // driven by dt every frame
    private var landSquash = 0f       // 0..1 squash on landing

    // ── Stroke sizes (scale with stickman size) ───────────────────────────────
    private val sw = (w * 0.28f).coerceAtLeast(3f)   // main stroke width

    // ── Paints ────────────────────────────────────────────────────────────────
    private fun bodyPaint(alpha: Int = 255) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        this.alpha = alpha
        strokeWidth = sw
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val headFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#001A20")
        style = Paint.Style.FILL
    }
    private val headStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.STROKE
        strokeWidth = sw
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4400E5FF")
        style = Paint.Style.FILL
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2200E5FF")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val doubleJumpRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = sw * 1.4f
        color = Color.parseColor("#FFD600")
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = sw * 0.8f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#FFD600")
    }
    private val tokenDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD600")
        style = Paint.Style.FILL
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    fun jump() {
        when {
            state == PlayerState.RUNNING -> {
                velY = GameConstants.JUMP_VELOCITY
                state = PlayerState.JUMPING
                canDoubleJump = true
            }
            state == PlayerState.JUMPING && canDoubleJump -> {
                velY = GameConstants.DOUBLE_JUMP_VELOCITY
                canDoubleJump = false
                doubleJumpFlash = 0.30f
            }
        }
    }

    fun slide() {
        if (state == PlayerState.RUNNING) {
            state = PlayerState.SLIDING
            slideTimer = GameConstants.SLIDE_DURATION_MS
        }
    }

    // ── Update ────────────────────────────────────────────────────────────────

    fun update(dt: Float) {
        if (state == PlayerState.DEAD) return

        if (state == PlayerState.SLIDING) {
            slideTimer -= (dt * 1000).toLong()
            if (slideTimer <= 0) state = PlayerState.RUNNING
        }

        // Gravity
        if (state == PlayerState.JUMPING || y < groundY - h - 1f) {
            velY += GameConstants.GRAVITY * dt
            y += velY * dt
            if (y >= groundY - h) {
                y = groundY - h
                landSquash = 1f         // trigger squash on land
                velY = 0f
                canDoubleJump = false
                state = if (slideTimer > 0) PlayerState.SLIDING else PlayerState.RUNNING
            }
        }

        // Advance run animation phase smoothly
        if (state == PlayerState.RUNNING || state == PlayerState.JUMPING) {
            val freq = if (state == PlayerState.RUNNING) GameConstants.RUN_ANIM_FREQ else 1.2
            animPhase = (animPhase + (freq * GameConstants.TWO_PI * dt).toFloat()) % GameConstants.TWO_PI
        }

        if (doubleJumpFlash > 0f) doubleJumpFlash -= dt
        if (landSquash > 0f) landSquash = (landSquash - dt * 6f).coerceAtLeast(0f)
    }

    // ── Hitbox ────────────────────────────────────────────────────────────────

    fun getHitbox(): RectF = if (state == PlayerState.SLIDING)
        RectF(x + sw, groundY - h * 0.52f, x + w - sw, groundY)
    else
        RectF(x + sw, y + sw, x + w - sw, y + h - sw)

    // ── Draw ──────────────────────────────────────────────────────────────────

    fun draw(canvas: Canvas) {
        if (state == PlayerState.DEAD) { drawDead(canvas); return }

        val cx = x + w / 2f

        // Double-jump burst effect
        if (doubleJumpFlash > 0f) drawDoubleJumpEffect(canvas, cx)

        when (state) {
            PlayerState.SLIDING -> drawSlide(canvas, cx)
            PlayerState.JUMPING -> drawAir(canvas, cx)
            else                -> drawRun(canvas, cx)
        }

        drawShadow(canvas, cx)

        // Double-jump available dot
        if (state == PlayerState.JUMPING && canDoubleJump) {
            val pulse = (sin(animPhase * 4f) * 0.3f + 1f).toFloat()
            tokenDotPaint.alpha = 220
            canvas.drawCircle(cx, y - w * 0.5f, w * 0.13f * pulse, tokenDotPaint)
        }
    }

    // ── Running pose — smooth 2-segment limbs via sine ────────────────────────

    private fun drawRun(canvas: Canvas, cx: Float) {
        val s = sin(animPhase)          // -1..1 main swing
        val k = sin(animPhase + PI / 3.5).toFloat()  // knee phase offset

        val headR  = w * 0.28f
        val headCy = y + headR * 1.0f

        // Torso anchor points
        val shoulderY = headCy + headR * 1.2f
        val hipY      = y + h * 0.56f

        // Torso lean forward slightly
        val leanX = w * 0.04f

        // ── Back limbs (dimmer, drawn first) ──────────────────────────────────
        val dimPaint  = bodyPaint(110)
        val dimGlow   = glowPaint.also { it.strokeWidth = sw * 2.2f; it.alpha = 30 }

        val legH   = h * 0.44f
        val legSpr = w * 0.30f
        val armH   = h * 0.30f
        val armSpr = w * 0.28f

        // Back leg
        val bThighEx = cx + leanX - s.toFloat() * legSpr
        val bThighEy = hipY + legH * 0.48f
        val bKneeBend = (-k * legH * 0.18f).toFloat().coerceAtMost(0f)   // bend up when back
        val bFootX  = bThighEx + s.toFloat() * legSpr * 0.35f
        canvas.drawLine(cx + leanX, hipY, bThighEx, bThighEy + bKneeBend, dimPaint)
        canvas.drawLine(bThighEx, bThighEy + bKneeBend, bFootX, groundY, dimPaint)

        // Back arm
        val bArmEx = cx + leanX + s.toFloat() * armSpr
        val bArmEy = shoulderY + armH * 0.50f
        val bForeX = bArmEx - s.toFloat() * armSpr * 0.40f
        val bForeY = bArmEy + armH * 0.50f
        canvas.drawLine(cx + leanX, shoulderY, bArmEx, bArmEy, dimPaint)
        canvas.drawLine(bArmEx, bArmEy, bForeX, bForeY, dimPaint)

        // ── Head ──────────────────────────────────────────────────────────────
        // Bob with stride
        val bobY = abs(sin(animPhase)).toFloat() * h * 0.025f
        canvas.drawCircle(cx + leanX, headCy + bobY, headR, headFill)
        canvas.drawCircle(cx + leanX, headCy + bobY, headR, headStroke)
        // Tiny eye glint
        val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00E5FF"); alpha = 180; style = Paint.Style.FILL }
        canvas.drawCircle(cx + leanX + headR * 0.30f, headCy + bobY - headR * 0.10f, headR * 0.18f, eyePaint)

        // ── Torso with glow ───────────────────────────────────────────────────
        glowPaint.strokeWidth = sw * 2.5f; glowPaint.alpha = 25
        canvas.drawLine(cx + leanX, shoulderY, cx + leanX, hipY, glowPaint)
        canvas.drawLine(cx + leanX, shoulderY, cx + leanX, hipY, bodyPaint())

        // ── Front leg ─────────────────────────────────────────────────────────
        val fThighEx = cx + leanX + s.toFloat() * legSpr
        val fThighEy = hipY + legH * 0.48f
        val fKneeLift = (s.toFloat() * legH * 0.22f).coerceAtLeast(0f)   // knee lifts forward
        val fFootX  = fThighEx - s.toFloat() * legSpr * 0.45f
        val fFootY  = groundY - max(0f, s.toFloat() * legH * 0.25f)      // foot flick up

        glowPaint.strokeWidth = sw * 2.2f; glowPaint.alpha = 28
        canvas.drawLine(cx + leanX, hipY, fThighEx, fThighEy - fKneeLift, glowPaint)
        canvas.drawLine(fThighEx, fThighEy - fKneeLift, fFootX, fFootY, glowPaint)
        canvas.drawLine(cx + leanX, hipY, fThighEx, fThighEy - fKneeLift, bodyPaint())
        canvas.drawLine(fThighEx, fThighEy - fKneeLift, fFootX, fFootY, bodyPaint())

        // ── Front arm ─────────────────────────────────────────────────────────
        val fArmEx = cx + leanX - s.toFloat() * armSpr     // opposite to leg
        val fArmEy = shoulderY + armH * 0.50f
        val fForeX = fArmEx + s.toFloat() * armSpr * 0.50f
        val fForeY = fArmEy + armH * 0.50f
        glowPaint.strokeWidth = sw * 2.2f; glowPaint.alpha = 28
        canvas.drawLine(cx + leanX, shoulderY, fArmEx, fArmEy, glowPaint)
        canvas.drawLine(fArmEx, fArmEy, fForeX, fForeY, glowPaint)
        canvas.drawLine(cx + leanX, shoulderY, fArmEx, fArmEy, bodyPaint())
        canvas.drawLine(fArmEx, fArmEy, fForeX, fForeY, bodyPaint())
    }

    // ── Air pose — tuck ───────────────────────────────────────────────────────

    private fun drawAir(canvas: Canvas, cx: Float) {
        val headR  = w * 0.28f
        val headCy = y + headR * 1.0f
        val shoulderY = headCy + headR * 1.2f
        val hipY   = y + h * 0.54f
        val leanX  = w * 0.06f

        // Rising or falling determines leg tuck vs. extension
        val isRising = velY < 0f
        val tuck = if (isRising) 0.80f else 0.40f  // how much legs are tucked

        // Head
        canvas.drawCircle(cx + leanX, headCy, headR, headFill)
        canvas.drawCircle(cx + leanX, headCy, headR, headStroke)

        // Torso
        canvas.drawLine(cx + leanX, shoulderY, cx + leanX, hipY, bodyPaint())

        // Arms — spread wide for balance
        canvas.drawLine(cx + leanX, shoulderY, cx + leanX - w * 0.50f, shoulderY + h * 0.05f, bodyPaint())
        canvas.drawLine(cx + leanX, shoulderY, cx + leanX + w * 0.45f, shoulderY + h * 0.05f, bodyPaint())
        // Forearms angle down
        canvas.drawLine(cx + leanX - w * 0.50f, shoulderY + h * 0.05f,
                        cx + leanX - w * 0.35f, shoulderY + h * 0.18f, bodyPaint())
        canvas.drawLine(cx + leanX + w * 0.45f, shoulderY + h * 0.05f,
                        cx + leanX + w * 0.32f, shoulderY + h * 0.18f, bodyPaint())

        // Legs tucked
        val legTuckY = hipY + h * 0.22f * tuck
        val legOutX  = w * 0.28f * tuck
        // Left
        canvas.drawLine(cx + leanX, hipY, cx + leanX - legOutX, legTuckY, bodyPaint())
        canvas.drawLine(cx + leanX - legOutX, legTuckY, cx + leanX - legOutX * 0.4f, hipY + h * 0.42f, bodyPaint())
        // Right
        canvas.drawLine(cx + leanX, hipY, cx + leanX + legOutX, legTuckY, bodyPaint())
        canvas.drawLine(cx + leanX + legOutX, legTuckY, cx + leanX + legOutX * 0.4f, hipY + h * 0.42f, bodyPaint())
    }

    // ── Slide pose ────────────────────────────────────────────────────────────

    private fun drawSlide(canvas: Canvas, cx: Float) {
        val bY    = groundY
        val headR = w * 0.26f
        val prog  = 1f - (slideTimer.toFloat() / GameConstants.SLIDE_DURATION_MS).coerceIn(0f, 1f)
        // Head position slides back
        val headX = cx - w * 0.20f - prog * w * 0.05f

        canvas.drawCircle(headX, bY - h * 0.42f, headR, headFill)
        canvas.drawCircle(headX, bY - h * 0.42f, headR, headStroke)

        // Stretched spine
        canvas.drawLine(headX + headR * 0.7f, bY - h * 0.42f,
                        cx + w * 0.40f, bY - h * 0.14f, bodyPaint())

        // Front leg sliding flat
        canvas.drawLine(cx + w * 0.10f, bY - h * 0.14f,
                        cx + w * 0.58f, bY - h * 0.04f, bodyPaint())
        // Back leg bent
        canvas.drawLine(cx + w * 0.10f, bY - h * 0.14f,
                        cx + w * 0.38f, bY, bodyPaint())

        // Trailing arm
        canvas.drawLine(cx - w * 0.05f, bY - h * 0.36f,
                        cx - w * 0.05f, bY - h * 0.08f, bodyPaint())
        // Leading arm (out front for balance)
        canvas.drawLine(cx + w * 0.20f, bY - h * 0.30f,
                        cx + w * 0.52f, bY - h * 0.20f, bodyPaint())
    }

    // ── Death pose ────────────────────────────────────────────────────────────

    private fun drawDead(canvas: Canvas) {
        val cx    = x + w / 2f
        val headR = w * 0.28f
        val bp    = bodyPaint(180)
        canvas.drawCircle(cx, groundY - headR * 1.1f, headR, headFill)
        canvas.drawCircle(cx, groundY - headR * 1.1f, headR, headStroke.also { it.alpha = 180 })
        canvas.drawLine(cx, groundY - headR * 2.1f, cx - w * 0.1f, groundY - h * 0.45f, bp)
        canvas.drawLine(cx - w * 0.1f, groundY - h * 0.45f, cx - w * 0.55f, groundY, bp)
        canvas.drawLine(cx - w * 0.1f, groundY - h * 0.45f, cx + w * 0.30f, groundY - h * 0.08f, bp)
        canvas.drawLine(cx, groundY - h * 0.65f, cx - w * 0.42f, groundY - h * 0.30f, bp)
        canvas.drawLine(cx, groundY - h * 0.65f, cx + w * 0.35f, groundY - h * 0.25f, bp)
    }

    // ── Shadow ────────────────────────────────────────────────────────────────

    private fun drawShadow(canvas: Canvas, cx: Float) {
        val airFrac = ((groundY - h - y) / (h * 2.2f)).coerceIn(0f, 1f)
        val sw2 = w * 0.42f * (1f - airFrac * 0.72f)
        shadowPaint.alpha = (100 - (airFrac * 85).toInt()).coerceIn(15, 100)
        canvas.drawOval(RectF(cx - sw2, groundY + 1f, cx + sw2, groundY + 7f), shadowPaint)
    }

    // ── Double-jump burst ─────────────────────────────────────────────────────

    private fun drawDoubleJumpEffect(canvas: Canvas, cx: Float) {
        val t     = doubleJumpFlash / 0.30f                        // 1→0
        val alpha = (t * 255).toInt().coerceIn(0, 255)
        val r     = w * (1.2f + (1f - t) * 2.8f)

        doubleJumpRingPaint.alpha = alpha
        canvas.drawCircle(cx, y + h * 0.5f, r, doubleJumpRingPaint)

        // Inner smaller ring
        doubleJumpRingPaint.alpha = (alpha * 0.5f).toInt()
        canvas.drawCircle(cx, y + h * 0.5f, r * 0.55f, doubleJumpRingPaint)

        // Trailing streaks
        val streakLen = w * (0.8f + (1f - t) * 3f)
        for (i in -1..1) {
            trailPaint.alpha = (alpha * 0.55f).toInt()
            canvas.drawLine(
                cx - w * 0.5f - streakLen, y + h * (0.4f + i * 0.12f),
                cx - w * 0.5f,             y + h * (0.4f + i * 0.12f),
                trailPaint
            )
        }
    }

    fun die()        { state = PlayerState.DEAD }
    fun isOnGround() = y >= groundY - h - 2f
}
