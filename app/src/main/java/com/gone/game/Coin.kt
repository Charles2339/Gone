package com.gone.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.sin

enum class CoinLane { GROUND, MID, HIGH }

class Coin(screenW: Int, screenH: Int, lane: CoinLane = CoinLane.entries.random()) {

    private val groundY = screenH - screenH * GameConstants.GROUND_HEIGHT_FRAC
    private val radius  = screenH * 0.028f

    var x = screenW.toFloat() + radius * 2f
    val y: Float = when (lane) {
        CoinLane.GROUND -> groundY - radius * 2.4f
        CoinLane.MID    -> groundY - screenH * 0.20f
        CoinLane.HIGH   -> groundY - screenH * 0.33f
    }

    private var collected   = false
    private var collectAnim = 0f
    private var pulse       = 0f

    // ── Paints ────────────────────────────────────────────────────────────────
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = radius * 0.60f
        color = Color.parseColor("#66FFD600")
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = radius * 0.22f
        color = Color.parseColor("#FFFFFF88")
    }
    private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7A5C00")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        style = Paint.Style.FILL
    }
    private val burstPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    // ── Update ────────────────────────────────────────────────────────────────

    fun move(dx: Float)  { if (!collected) x -= dx }

    fun collect() { if (!collected) { collected = true; collectAnim = 0f } }

    fun update(dt: Float) {
        pulse = (pulse + dt * 4.0f) % GameConstants.TWO_PI
        if (collected) collectAnim = (collectAnim + dt * 4.5f).coerceAtMost(1f)
    }

    fun isFullyDone()  = collected && collectAnim >= 1f
    fun isOffScreen()  = !collected && x < -radius * 4f

    /** Generous hitbox — 2.5× visual radius so fast-moving coins are never missed */
    fun getHitbox() = RectF(x - radius * 2.5f, y - radius * 2.5f,
                             x + radius * 2.5f, y + radius * 2.5f)

    // ── Draw ──────────────────────────────────────────────────────────────────

    fun draw(canvas: Canvas) {
        if (collected) { drawCollect(canvas); return }

        val p = sin(pulse).toFloat() * 0.12f + 1f   // 0.88..1.12 pulse scale

        // Outer glow halo
        glowPaint.alpha = (60 + sin(pulse).toFloat() * 35).toInt().coerceIn(25, 95)
        canvas.drawCircle(x, y, radius * p * 1.55f, glowPaint)

        // Gradient fill
        fillPaint.shader = RadialGradient(
            x - radius * 0.30f, y - radius * 0.38f, radius * p * 1.05f,
            intArrayOf(Color.parseColor("#FFEE58"), Color.parseColor("#FFA000")),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, radius * p, fillPaint)

        // Rim
        canvas.drawCircle(x, y, radius * p, rimPaint)

        // ✦ symbol
        symbolPaint.textSize = radius * 1.05f
        canvas.drawText("✦", x, y + radius * 0.36f, symbolPaint)
    }

    private fun drawCollect(canvas: Canvas) {
        val t     = collectAnim   // 0→1
        val alpha = ((1f - t) * 230).toInt().coerceIn(0, 230)

        // Expanding ring
        burstPaint.color       = Color.parseColor("#FFD600")
        burstPaint.strokeWidth = radius * 0.5f * (1f - t * 0.7f)
        burstPaint.alpha       = alpha
        canvas.drawCircle(x, y - t * radius, radius * (1f + t * 2.2f), burstPaint)

        // "+1" floating up
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color     = Color.parseColor("#FFD600")
            textAlign = Paint.Align.CENTER
            typeface  = Typeface.DEFAULT_BOLD
            textSize  = radius * 1.5f
            this.alpha = alpha
        }
        canvas.drawText("+1", x, y - radius * 2.2f - t * radius * 2.8f, tp)
    }
}
