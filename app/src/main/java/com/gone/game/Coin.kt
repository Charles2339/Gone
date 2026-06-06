package com.gone.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.sin

enum class CoinLane { GROUND, MID, HIGH }

class Coin(screenW: Int, screenH: Int, lane: CoinLane = CoinLane.entries.random()) {

    private val groundY = screenH - screenH * GameConstants.GROUND_HEIGHT_FRAC
    private val radius  = screenH * 0.030f

    var x = screenW.toFloat() + radius
    val y: Float = when (lane) {
        CoinLane.GROUND -> groundY - radius * 2.2f           // just above ground
        CoinLane.MID    -> groundY - screenH * 0.22f         // mid-air (single jump)
        CoinLane.HIGH   -> groundY - screenH * 0.36f         // double-jump height
    }

    private var collected = false
    private var collectAnim = 0f   // counts up after collection for pop effect
    private var pulse = 0f         // drives the idle glow pulse

    // ── Paints ────────────────────────────────────────────────────────────────
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFD600")
    }
    private val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = radius * 0.55f
        color = Color.parseColor("#55FFD600")
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = radius * 0.25f
        color = Color.parseColor("#FFF176")
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#020818")
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textSize = radius * 1.1f
    }
    private val collectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD600")
        style  = Paint.Style.FILL
    }

    // ── Update ────────────────────────────────────────────────────────────────

    fun move(dx: Float) { if (!collected) x -= dx }

    fun collect() { if (!collected) { collected = true; collectAnim = 0f } }

    fun update(dt: Float) {
        pulse = (pulse + dt * 3.5f) % (GameConstants.TWO_PI)
        if (collected) collectAnim = (collectAnim + dt * 5f).coerceAtMost(1f)
    }

    fun isCollected()  = collected && collectAnim >= 1f
    fun isOffScreen()  = !collected && x < -radius * 3

    fun getHitbox() = RectF(x - radius * 1.1f, y - radius * 1.1f, x + radius * 1.1f, y + radius * 1.1f)

    // ── Draw ──────────────────────────────────────────────────────────────────

    fun draw(canvas: Canvas) {
        if (collected) {
            drawCollect(canvas)
            return
        }

        val pulseFactor = (sin(pulse) * 0.12f + 1f)

        // Outer glow halo
        outerGlowPaint.alpha = (80 + sin(pulse).toFloat() * 40).toInt().coerceIn(40, 120)
        canvas.drawCircle(x, y, radius * pulseFactor * 1.5f, outerGlowPaint)

        // Gradient fill (bright top, darker bottom)
        fillPaint.shader = RadialGradient(
            x - radius * 0.3f, y - radius * 0.4f,
            radius * 1.1f,
            intArrayOf(Color.parseColor("#FFEE58"), Color.parseColor("#F9A825")),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, radius * pulseFactor, fillPaint)

        // Rim highlight
        canvas.drawCircle(x, y, radius * pulseFactor, rimPaint)

        // ¢ / star symbol
        canvas.drawText("✦", x, y + radius * 0.38f, textPaint)
    }

    private fun drawCollect(canvas: Canvas) {
        // Expanding fade-out ring
        val t = collectAnim
        collectPaint.alpha = ((1f - t) * 200).toInt().coerceIn(0, 200)
        val r = radius * (1f + t * 2.5f)
        collectPaint.style = Paint.Style.STROKE
        collectPaint.strokeWidth = radius * 0.4f * (1f - t)
        canvas.drawCircle(x, y - t * radius * 1.5f, r, collectPaint)

        // "+1" text floating upward
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFD600")
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textSize = radius * 1.4f
            alpha = ((1f - t) * 255).toInt().coerceIn(0, 255)
        }
        canvas.drawText("+1", x, y - radius * 2f - t * radius * 3f, tp)
    }
}
