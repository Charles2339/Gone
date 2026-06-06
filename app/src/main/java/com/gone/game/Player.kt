package com.gone.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

enum class PlayerState { RUNNING, JUMPING, SLIDING, DEAD }

class Player(private val screenW: Int, private val screenH: Int) {

    // ── Core dimensions — everything derived from h ───────────────────────────
    val h   = screenH * GameConstants.PLAYER_HEIGHT_FRAC
    val cx  = screenW * GameConstants.PLAYER_X_FRAC    // horizontal centre (stable)

    // Hitbox: wide enough to reliably catch obstacle collisions
    private val hitW  = h * 0.50f                       // ~50% of height
    val x   get() = cx - hitW / 2f                     // left edge (GameView compat)
    val w   get() = hitW                                // width (GameView compat)

    val groundY = screenH - screenH * GameConstants.GROUND_HEIGHT_FRAC

    var y    = groundY - h
    var velY = 0f
    var state = PlayerState.RUNNING

    // Stickman anatomical proportions — all fractions of h
    private val headR    get() = h * 0.115f
    private val neckY    get() = y + h * 0.235f         // top of torso
    private val shoulderY get() = y + h * 0.27f
    private val hipY     get() = y + h * 0.545f
    private val thighLen = h * 0.25f
    private val shinLen  = h * 0.25f
    private val armLen1  = h * 0.18f
    private val armLen2  = h * 0.14f
    private val legSpread  = h * 0.21f   // max horizontal swing per leg
    private val armSpread  = h * 0.17f

    // Stroke: very thin lines = stickman
    private val sw = (h * 0.022f).coerceAtLeast(2.5f)

    // Physics / game state
    var canDoubleJump = false
    var onPlatform    = false
    var landSquash    = 0f

    private var doubleJumpFlash = 0f
    private var slideTimer      = 0L
    private var animPhase       = 0f

    // ── Paints ────────────────────────────────────────────────────────────────
    private val mainP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        strokeWidth = sw; style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private fun dimP() = Paint(mainP).also { it.alpha = 95 }

    private val headFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#001A20"); style = Paint.Style.FILL
    }
    private val headRim  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF"); style = Paint.Style.STROKE; strokeWidth = sw
    }
    private val glowP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1800E5FF"); style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val shadowP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4400E5FF"); style = Paint.Style.FILL
    }
    private val burstP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = sw * 1.6f; color = Color.parseColor("#FFD600")
    }
    private val trailP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = sw; strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#CCFFD600")
    }
    private val dotP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD600"); style = Paint.Style.FILL
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    fun jump() {
        when {
            state == PlayerState.RUNNING || onPlatform -> {
                velY = GameConstants.JUMP_VELOCITY
                state = PlayerState.JUMPING
                onPlatform = false; canDoubleJump = true
            }
            state == PlayerState.JUMPING && canDoubleJump -> {
                velY = GameConstants.DOUBLE_JUMP_VELOCITY
                canDoubleJump = false; doubleJumpFlash = 0.30f
            }
        }
    }

    fun slide() {
        if (state == PlayerState.RUNNING || onPlatform) {
            state = PlayerState.SLIDING; slideTimer = GameConstants.SLIDE_DURATION_MS
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
                    y = groundY - h; velY = 0f; landSquash = 1f
                    canDoubleJump = false
                    state = if (slideTimer > 0) PlayerState.SLIDING else PlayerState.RUNNING
                }
            }
        }

        val freq = if (state == PlayerState.JUMPING) 1.2 else GameConstants.RUN_ANIM_FREQ
        animPhase = (animPhase + (freq * GameConstants.TWO_PI * dt).toFloat()) % GameConstants.TWO_PI

        if (doubleJumpFlash > 0f) doubleJumpFlash -= dt
        if (landSquash > 0f)      landSquash = (landSquash - dt * 7f).coerceAtLeast(0f)
    }

    /** Called by GameView when player lands on an obstacle top. */
    fun landOn(obsTop: Float) {
        if (state == PlayerState.DEAD) return
        y = obsTop - h; velY = 0f
        if (!onPlatform) landSquash = 0.7f
        onPlatform = true; canDoubleJump = false
        if (state == PlayerState.JUMPING) state = PlayerState.RUNNING
    }

    // ── Hitbox ────────────────────────────────────────────────────────────────

    fun getHitbox(): RectF = if (state == PlayerState.SLIDING)
        // y + h = actual foot surface (groundY when on ground, obsTop when on platform)
        RectF(cx - hitW * 0.40f, y + h * 0.48f, cx + hitW * 0.40f, y + h - 2f)
    else
        RectF(cx - hitW * 0.40f, y + headR * 0.4f, cx + hitW * 0.40f, y + h - 2f)

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
        if (state == PlayerState.JUMPING && canDoubleJump) {
            val pulse = sin(animPhase * 5f) * 0.25f + 1f
            canvas.drawCircle(cx, y - h * 0.08f, h * 0.045f * pulse, dotP)
        }
    }

    // ── Running pose ──────────────────────────────────────────────────────────
    //
    // KEY FIX: feet always drawn to (y + h) — the player's actual bottom surface.
    // This is correct whether standing on groundY or on top of an obstacle.

    private fun drawRun(canvas: Canvas) {
        val s  = sin(animPhase).toFloat()   // -1 (left leg fwd) .. +1 (right leg fwd)
        val hy = hipY
        val footSurface = y + h             // THE FIX: feet target player's actual bottom

        // ── Back limbs (dim, drawn first so front limbs appear in front) ───────
        val dp = dimP()

        // Back leg: thigh goes in -s direction, foot kicks up behind when back
        val bKneeX   = cx - s * legSpread
        val bKneeY   = hy + thighLen * 0.75f
        val bFootX   = bKneeX - s * legSpread * 0.30f    // foot trails further back
        val bFootLift = (abs(s) * shinLen * 0.38f)        // foot lifts as leg goes back
        val bFootY   = (footSurface - bFootLift).coerceAtLeast(hy + thighLen * 0.5f)

        canvas.drawLine(cx, hy, bKneeX, bKneeY, dp)
        canvas.drawLine(bKneeX, bKneeY, bFootX, bFootY, dp)

        // Back arm (swings opposite to legs: +s leg → -s arm)
        val bElbX = cx + s * armSpread
        val bElbY = shoulderY + armLen1
        val bHndX = bElbX - s * armSpread * 0.45f
        val bHndY = bElbY + armLen2
        canvas.drawLine(cx, shoulderY, bElbX, bElbY, dp)
        canvas.drawLine(bElbX, bElbY, bHndX, bHndY, dp)

        // ── Head ──────────────────────────────────────────────────────────────
        val headCy = y + headR * 1.15f + abs(s) * h * 0.010f   // subtle bob
        canvas.drawCircle(cx, headCy, headR, headFill)
        canvas.drawCircle(cx, headCy, headR, headRim)
        val eyeP = Paint(headRim).also { it.style = Paint.Style.FILL; it.alpha = 160 }
        canvas.drawCircle(cx + headR * 0.32f, headCy - headR * 0.10f, headR * 0.16f, eyeP)

        // ── Torso ─────────────────────────────────────────────────────────────
        glowP.strokeWidth = sw * 2.8f; glowP.alpha = 28
        canvas.drawLine(cx, neckY, cx, hy, glowP)
        canvas.drawLine(cx, neckY, cx, hy, mainP)

        // ── Front leg: knee lifts when forward, foot stays near surface ───────
        val fKneeX   = cx + s * legSpread
        val kneeLift = (s * thighLen * 0.24f).coerceAtLeast(0f)   // lift only when forward
        val fKneeY   = hy + thighLen * 0.75f - kneeLift
        val fFootX   = fKneeX - s * legSpread * 0.20f             // foot slightly under knee
        val fFootY   = footSurface                                  // front foot always on surface

        glowP.strokeWidth = sw * 2.5f; glowP.alpha = 26
        canvas.drawLine(cx, hy, fKneeX, fKneeY, glowP)
        canvas.drawLine(fKneeX, fKneeY, fFootX, fFootY, glowP)
        canvas.drawLine(cx, hy, fKneeX, fKneeY, mainP)
        canvas.drawLine(fKneeX, fKneeY, fFootX, fFootY, mainP)

        // ── Front arm: swings opposite to front leg ───────────────────────────
        val fElbX = cx - s * armSpread
        val fElbY = shoulderY + armLen1
        val fHndX = fElbX + s * armSpread * 0.45f
        val fHndY = fElbY + armLen2

        glowP.strokeWidth = sw * 2.5f; glowP.alpha = 26
        canvas.drawLine(cx, shoulderY, fElbX, fElbY, glowP)
        canvas.drawLine(fElbX, fElbY, fHndX, fHndY, glowP)
        canvas.drawLine(cx, shoulderY, fElbX, fElbY, mainP)
        canvas.drawLine(fElbX, fElbY, fHndX, fHndY, mainP)
    }

    // ── Air / jump pose ───────────────────────────────────────────────────────

    private fun drawAir(canvas: Canvas) {
        val rising = velY < 0f
        val tuck   = if (rising) 0.80f else 0.45f

        val headCy = y + headR * 1.15f
        canvas.drawCircle(cx, headCy, headR, headFill)
        canvas.drawCircle(cx, headCy, headR, headRim)
        canvas.drawLine(cx, neckY, cx, hipY, mainP)

        // Arms spread wide
        val aw = h * 0.22f
        canvas.drawLine(cx, shoulderY, cx - aw, shoulderY + h * 0.07f, mainP)
        canvas.drawLine(cx - aw, shoulderY + h * 0.07f, cx - aw * 0.6f, shoulderY + h * 0.18f, mainP)
        canvas.drawLine(cx, shoulderY, cx + aw * 0.88f, shoulderY + h * 0.07f, mainP)
        canvas.drawLine(cx + aw * 0.88f, shoulderY + h * 0.07f, cx + aw * 0.5f, shoulderY + h * 0.18f, mainP)

        // Legs tucked or extended depending on rise/fall
        val lx = h * 0.20f * tuck
        val ly = h * 0.22f * tuck
        canvas.drawLine(cx, hipY, cx - lx, hipY + ly, mainP)
        canvas.drawLine(cx - lx, hipY + ly, cx - lx * 0.35f, hipY + h * 0.46f, mainP)
        canvas.drawLine(cx, hipY, cx + lx, hipY + ly, mainP)
        canvas.drawLine(cx + lx, hipY + ly, cx + lx * 0.35f, hipY + h * 0.46f, mainP)
    }

    // ── Slide pose ────────────────────────────────────────────────────────────

    private fun drawSlide(canvas: Canvas) {
        // Use groundY for slide (player is always on ground when sliding)
        val bY   = groundY
        val hdCy = bY - h * 0.40f
        val hdCx = cx - h * 0.05f
        canvas.drawCircle(hdCx, hdCy, headR, headFill)
        canvas.drawCircle(hdCx, hdCy, headR, headRim)
        canvas.drawLine(hdCx + headR * 0.6f, hdCy, cx + h * 0.28f, bY - h * 0.13f, mainP)
        canvas.drawLine(cx + h * 0.28f, bY - h * 0.13f, cx + h * 0.50f, bY - h * 0.04f, mainP)
        canvas.drawLine(cx + h * 0.28f, bY - h * 0.13f, cx + h * 0.30f, bY, mainP)
        canvas.drawLine(cx + h * 0.08f, bY - h * 0.30f, cx + h * 0.08f, bY - h * 0.06f, mainP)
        canvas.drawLine(cx + h * 0.18f, bY - h * 0.26f, cx + h * 0.48f, bY - h * 0.17f, mainP)
    }

    // ── Dead pose ─────────────────────────────────────────────────────────────

    private fun drawDead(canvas: Canvas) {
        val dp = Paint(mainP).also { it.alpha = 180 }
        val fy = groundY
        canvas.drawCircle(cx, fy - headR * 1.2f, headR, headFill)
        canvas.drawCircle(cx, fy - headR * 1.2f, headR, headRim.also { it.alpha = 180 })
        canvas.drawLine(cx, fy - headR * 2.3f, cx - h * 0.07f, fy - h * 0.48f, dp)
        canvas.drawLine(cx - h * 0.07f, fy - h * 0.48f, cx - h * 0.48f, fy, dp)
        canvas.drawLine(cx - h * 0.07f, fy - h * 0.48f, cx + h * 0.26f, fy - h * 0.08f, dp)
        canvas.drawLine(cx, fy - h * 0.64f, cx - h * 0.36f, fy - h * 0.30f, dp)
        canvas.drawLine(cx, fy - h * 0.64f, cx + h * 0.30f, fy - h * 0.24f, dp)
    }

    // ── Shadow ────────────────────────────────────────────────────────────────

    private fun drawShadow(canvas: Canvas) {
        val airFrac = ((groundY - h - y) / (h * 2f)).coerceIn(0f, 1f)
        val sr = h * 0.24f * (1f - airFrac * 0.75f)
        shadowP.alpha = (88 - (airFrac * 72).toInt()).coerceIn(10, 88)
        canvas.drawOval(RectF(cx - sr, groundY + 1f, cx + sr, groundY + 6f), shadowP)
    }

    // ── Double-jump burst ─────────────────────────────────────────────────────

    private fun drawBurst(canvas: Canvas) {
        val t = (doubleJumpFlash / 0.30f).coerceIn(0f, 1f)
        val alpha = (t * 255).toInt()
        val r = h * 0.28f + (1f - t) * h * 1.0f
        burstP.alpha = alpha
        canvas.drawCircle(cx, y + h * 0.50f, r, burstP)
        burstP.alpha = (alpha * 0.5f).toInt()
        canvas.drawCircle(cx, y + h * 0.50f, r * 0.55f, burstP)
        val len = h * 0.4f + (1f - t) * h * 1.2f
        for (i in -1..1) {
            trailP.alpha = (alpha * 0.5f).toInt()
            canvas.drawLine(cx - h * 0.30f - len, y + h * (0.38f + i * 0.10f),
                            cx - h * 0.30f,        y + h * (0.38f + i * 0.10f), trailP)
        }
    }

    fun die() { state = PlayerState.DEAD }
}
