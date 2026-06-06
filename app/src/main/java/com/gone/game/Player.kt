package com.gone.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.sin

enum class PlayerState { RUNNING, JUMPING, SLIDING, DEAD }

class Player(private val screenW: Int, private val screenH: Int) {

    // ── Dimensions (everything derived from h, NOT w) ─────────────────────────
    val h  = screenH * GameConstants.PLAYER_HEIGHT_FRAC
    val w  = screenW * GameConstants.PLAYER_WIDTH_FRAC   // used only for hitbox x
    val groundY = screenH - screenH * GameConstants.GROUND_HEIGHT_FRAC
    val x  = screenW * GameConstants.PLAYER_X_FRAC       // left edge of hitbox

    var y  = groundY - h
    var velY = 0f
    var state = PlayerState.RUNNING

    // Stickman proportions — all relative to h
    private val headR    get() = h * 0.115f
    private val neckY    get() = y + h * 0.24f    // where neck meets torso
    private val shoulderY get() = y + h * 0.27f
    private val hipY     get() = y + h * 0.54f
    private val sw       = (h * 0.022f).coerceAtLeast(2.5f)  // thin stick lines
    private val cx       get() = x + w / 2f       // horizontal centre

    // Physics state
    var canDoubleJump = false
    var onPlatform    = false
    var landSquash    = 0f

    // Double-jump visual
    private var doubleJumpFlash = 0f

    // Slide
    private var slideTimer = 0L

    // Smooth animation
    private var animPhase = 0f

    // ── Paints ────────────────────────────────────────────────────────────────
    private val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        strokeWidth = sw
        style  = Paint.Style.STROKE
        strokeCap  = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private fun dimPaint() = Paint(mainPaint).also { it.alpha = 100 }
    private val headFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#001A20"); style = Paint.Style.FILL
    }
    private val headRim  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF"); style = Paint.Style.STROKE; strokeWidth = sw
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1800E5FF"); style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4400E5FF"); style = Paint.Style.FILL
    }
    private val burstPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = sw * 1.6f; color = Color.parseColor("#FFD600")
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = sw; strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#CCFFD600")
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD600"); style = Paint.Style.FILL
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    fun jump() {
        when {
            state == PlayerState.RUNNING || onPlatform -> {
                velY = GameConstants.JUMP_VELOCITY
                state = PlayerState.JUMPING
                onPlatform = false
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
        if (state == PlayerState.RUNNING || onPlatform) {
            state = PlayerState.SLIDING
            slideTimer = GameConstants.SLIDE_DURATION_MS
        }
    }

    // ── Update ────────────────────────────────────────────────────────────────

    fun update(dt: Float) {
        if (state == PlayerState.DEAD) return

        if (state == PlayerState.SLIDING) {
            slideTimer -= (dt * 1000).toLong()
            if (slideTimer <= 0) { state = PlayerState.RUNNING; slideTimer = 0 }
        }

        if (!onPlatform) {
            if (state == PlayerState.JUMPING || y < groundY - h - 1f) {
                velY += GameConstants.GRAVITY * dt
                y   += velY * dt
                if (y >= groundY - h) {
                    y = groundY - h
                    velY = 0f
                    landSquash = 1f
                    canDoubleJump = false
                    state = if (slideTimer > 0) PlayerState.SLIDING else PlayerState.RUNNING
                }
            }
        }

        // Run anim: fast while on ground, slow gentle drift in air
        val freq = if (state == PlayerState.JUMPING) 1.0 else GameConstants.RUN_ANIM_FREQ
        animPhase = (animPhase + (freq * GameConstants.TWO_PI * dt).toFloat()) % GameConstants.TWO_PI

        if (doubleJumpFlash > 0f) doubleJumpFlash -= dt
        if (landSquash > 0f)      landSquash = (landSquash - dt * 7f).coerceAtLeast(0f)
    }

    // Called by GameView when player's feet land on an obstacle top
    fun landOn(obsTop: Float) {
        if (state == PlayerState.DEAD) return
        y     = obsTop - h
        velY  = 0f
        if (!onPlatform) landSquash = 0.8f   // one-shot squash on first contact
        onPlatform    = true
        canDoubleJump = false
        if (state == PlayerState.JUMPING) state = PlayerState.RUNNING
    }

    // ── Hitbox ────────────────────────────────────────────────────────────────

    fun getHitbox(): RectF = if (state == PlayerState.SLIDING)
        RectF(x + sw, groundY - h * 0.50f, x + w - sw, groundY)
    else
        RectF(x + sw * 0.5f, y + headR * 0.3f, x + w - sw * 0.5f, y + h - sw)

    // ── Draw ──────────────────────────────────────────────────────────────────

    fun draw(canvas: Canvas) {
        if (state == PlayerState.DEAD) { drawDead(canvas); return }
        if (doubleJumpFlash > 0f) drawBurst(canvas)
        when (state) {
            PlayerState.SLIDING -> drawSlide(canvas)
            PlayerState.JUMPING -> drawAir(canvas)
            else                -> drawRun(canvas)
        }
        drawShadow(canvas)
        // Double-jump token dot
        if (state == PlayerState.JUMPING && canDoubleJump) {
            val pulse = sin(animPhase * 5f) * 0.25f + 1f
            canvas.drawCircle(cx, y - h * 0.10f, h * 0.045f * pulse, dotPaint)
        }
    }

    // ── Running — smooth 2-segment limbs, all proportions from h ─────────────

    private fun drawRun(canvas: Canvas) {
        val s = sin(animPhase).toFloat()            // -1..1 leg swing
        val kOff = sin(animPhase + PI.toFloat() / 3.5f).toFloat() // knee phase

        // Leg geometry
        val thighLen   = h * 0.26f
        val shinLen    = h * 0.26f
        val legSwing   = h * 0.20f   // max horizontal spread

        // Hip
        val hx = cx; val hy = hipY

        // ── Back limbs (dim, drawn first) ─────────────────────────────────────
        val dp = dimPaint()

        // Back leg
        val bKneeX = hx - s * legSwing
        val bKneeLift = (-kOff * thighLen * 0.14f).coerceAtMost(0f)
        val bKneeY  = hy + thighLen + bKneeLift
        val bFootX  = bKneeX + s * legSwing * 0.4f
        val bFootY  = groundY
        canvas.drawLine(hx, hy, bKneeX, bKneeY, dp)
        canvas.drawLine(bKneeX, bKneeY, bFootX, bFootY, dp)

        // Back arm (arms swing opposite to legs)
        val armSwing = h * 0.18f
        val armLen1  = h * 0.18f
        val armLen2  = h * 0.14f
        val bElbX = cx + s * armSwing
        val bElbY = shoulderY + armLen1
        val bHandX = bElbX - s * armSwing * 0.5f
        val bHandY = bElbY + armLen2
        canvas.drawLine(cx, shoulderY, bElbX, bElbY, dp)
        canvas.drawLine(bElbX, bElbY, bHandX, bHandY, dp)

        // ── Head (drawn between back/front layers) ────────────────────────────
        val headCx = cx
        val headCy = y + headR * 1.15f + abs(s) * h * 0.012f   // subtle bob
        canvas.drawCircle(headCx, headCy, headR, headFill)
        canvas.drawCircle(headCx, headCy, headR, headRim)
        // Eye
        val eyePaint = Paint(headRim).also { it.style = Paint.Style.FILL; it.alpha = 160 }
        canvas.drawCircle(headCx + headR * 0.30f, headCy - headR * 0.12f, headR * 0.16f, eyePaint)

        // ── Torso ─────────────────────────────────────────────────────────────
        glowPaint.strokeWidth = sw * 2.8f; glowPaint.alpha = 30
        canvas.drawLine(cx, neckY, cx, hipY, glowPaint)
        canvas.drawLine(cx, neckY, cx, hipY, mainPaint)

        // ── Front leg ─────────────────────────────────────────────────────────
        val fKneeX  = hx + s * legSwing
        val fKneeLift = (s * thighLen * 0.22f).coerceAtLeast(0f)
        val fKneeY  = hy + thighLen - fKneeLift
        val fFootX  = fKneeX - s * legSwing * 0.45f
        val fFootY  = (groundY - s * shinLen * 0.18f).coerceAtLeast(groundY - shinLen * 0.25f)

        glowPaint.strokeWidth = sw * 2.5f; glowPaint.alpha = 28
        canvas.drawLine(hx, hy, fKneeX, fKneeY, glowPaint)
        canvas.drawLine(fKneeX, fKneeY, fFootX, fFootY, glowPaint)
        canvas.drawLine(hx, hy, fKneeX, fKneeY, mainPaint)
        canvas.drawLine(fKneeX, fKneeY, fFootX, fFootY, mainPaint)

        // ── Front arm ─────────────────────────────────────────────────────────
        val fElbX  = cx - s * armSwing
        val fElbY  = shoulderY + armLen1
        val fHandX = fElbX + s * armSwing * 0.5f
        val fHandY = fElbY + armLen2

        glowPaint.strokeWidth = sw * 2.5f; glowPaint.alpha = 28
        canvas.drawLine(cx, shoulderY, fElbX, fElbY, glowPaint)
        canvas.drawLine(fElbX, fElbY, fHandX, fHandY, glowPaint)
        canvas.drawLine(cx, shoulderY, fElbX, fElbY, mainPaint)
        canvas.drawLine(fElbX, fElbY, fHandX, fHandY, mainPaint)
    }

    // ── Air pose ──────────────────────────────────────────────────────────────

    private fun drawAir(canvas: Canvas) {
        val rising = velY < 0f
        val tuck   = if (rising) 0.85f else 0.50f

        val headCy = y + headR * 1.15f
        canvas.drawCircle(cx, headCy, headR, headFill)
        canvas.drawCircle(cx, headCy, headR, headRim)

        // Torso
        canvas.drawLine(cx, neckY, cx, hipY, mainPaint)

        // Arms spread wide
        val aw = h * 0.24f
        canvas.drawLine(cx, shoulderY, cx - aw, shoulderY + h * 0.06f, mainPaint)
        canvas.drawLine(cx - aw, shoulderY + h * 0.06f, cx - aw * 0.6f, shoulderY + h * 0.18f, mainPaint)
        canvas.drawLine(cx, shoulderY, cx + aw * 0.9f, shoulderY + h * 0.06f, mainPaint)
        canvas.drawLine(cx + aw * 0.9f, shoulderY + h * 0.06f, cx + aw * 0.5f, shoulderY + h * 0.18f, mainPaint)

        // Legs tucked
        val lx = h * 0.22f * tuck
        val ldy = h * 0.22f * tuck
        // Left
        canvas.drawLine(cx, hipY, cx - lx, hipY + ldy, mainPaint)
        canvas.drawLine(cx - lx, hipY + ldy, cx - lx * 0.35f, hipY + h * 0.46f, mainPaint)
        // Right
        canvas.drawLine(cx, hipY, cx + lx, hipY + ldy, mainPaint)
        canvas.drawLine(cx + lx, hipY + ldy, cx + lx * 0.35f, hipY + h * 0.46f, mainPaint)
    }

    // ── Slide pose ────────────────────────────────────────────────────────────

    private fun drawSlide(canvas: Canvas) {
        val bY    = groundY
        val hdCy  = bY - h * 0.42f
        val hdCx  = cx - h * 0.06f
        canvas.drawCircle(hdCx, hdCy, headR, headFill)
        canvas.drawCircle(hdCx, hdCy, headR, headRim)
        // Spine stretched horizontal
        val spineEndX = cx + h * 0.30f
        val spineEndY = bY - h * 0.14f
        canvas.drawLine(hdCx + headR * 0.6f, hdCy, spineEndX, spineEndY, mainPaint)
        // Front leg
        canvas.drawLine(spineEndX, spineEndY, cx + h * 0.52f, bY - h * 0.04f, mainPaint)
        // Back leg
        canvas.drawLine(spineEndX, spineEndY, cx + h * 0.28f, bY, mainPaint)
        // Trailing arm
        canvas.drawLine(cx + h * 0.08f, bY - h * 0.28f, cx + h * 0.08f, bY - h * 0.06f, mainPaint)
        // Leading arm forward
        canvas.drawLine(cx + h * 0.22f, bY - h * 0.26f, cx + h * 0.50f, bY - h * 0.18f, mainPaint)
    }

    // ── Dead pose ─────────────────────────────────────────────────────────────

    private fun drawDead(canvas: Canvas) {
        val dp = Paint(mainPaint).also { it.alpha = 180 }
        val fy = groundY
        canvas.drawCircle(cx, fy - headR * 1.2f, headR, headFill)
        canvas.drawCircle(cx, fy - headR * 1.2f, headR, headRim.also { it.alpha = 180 })
        canvas.drawLine(cx, fy - headR * 2.2f, cx - h * 0.08f, fy - h * 0.48f, dp)
        canvas.drawLine(cx - h * 0.08f, fy - h * 0.48f, cx - h * 0.50f, fy, dp)
        canvas.drawLine(cx - h * 0.08f, fy - h * 0.48f, cx + h * 0.28f, fy - h * 0.08f, dp)
        canvas.drawLine(cx, fy - h * 0.66f, cx - h * 0.38f, fy - h * 0.30f, dp)
        canvas.drawLine(cx, fy - h * 0.66f, cx + h * 0.32f, fy - h * 0.24f, dp)
    }

    // ── Shadow ────────────────────────────────────────────────────────────────

    private fun drawShadow(canvas: Canvas) {
        val airFrac = ((groundY - h - y) / (h * 2f)).coerceIn(0f, 1f)
        val sw2 = h * 0.24f * (1f - airFrac * 0.75f)
        shadowPaint.alpha = (90 - (airFrac * 75).toInt()).coerceIn(12, 90)
        canvas.drawOval(RectF(cx - sw2, groundY + 1f, cx + sw2, groundY + 6f), shadowPaint)
    }

    // ── Double-jump burst ─────────────────────────────────────────────────────

    private fun drawBurst(canvas: Canvas) {
        val t     = (doubleJumpFlash / 0.30f).coerceIn(0f, 1f)
        val alpha = (t * 255).toInt()
        val r     = h * 0.30f + (1f - t) * h * 1.0f

        burstPaint.alpha = alpha
        canvas.drawCircle(cx, y + h * 0.50f, r, burstPaint)
        burstPaint.alpha = (alpha * 0.5f).toInt()
        canvas.drawCircle(cx, y + h * 0.50f, r * 0.55f, burstPaint)

        val len = h * 0.4f + (1f - t) * h * 1.2f
        for (i in -1..1) {
            trailPaint.alpha = (alpha * 0.5f).toInt()
            canvas.drawLine(cx - h * 0.25f - len, y + h * (0.4f + i * 0.10f),
                            cx - h * 0.25f,        y + h * (0.4f + i * 0.10f), trailPaint)
        }
    }

    fun die() { state = PlayerState.DEAD }
}
