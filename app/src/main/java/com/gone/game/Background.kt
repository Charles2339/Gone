package com.gone.game

import android.graphics.*

class Background(private val screenW: Int, private val screenH: Int) {

    private val groundY = screenH - screenH * GameConstants.GROUND_HEIGHT_FRAC

    // Parallax layers: offset, speed multiplier, colour
    private data class Layer(var offset: Float, val speedMult: Float, val alpha: Int, val lineColor: Int)

    private val layers = listOf(
        Layer(0f, 0.1f, 30, Color.parseColor("#00E5FF")),
        Layer(0f, 0.25f, 50, Color.parseColor("#7C4DFF")),
        Layer(0f, 0.5f, 70, Color.parseColor("#00BFA5"))
    )

    // Starfield
    private val stars = Array(80) {
        floatArrayOf(
            (Math.random() * screenW).toFloat(),
            (Math.random() * groundY * 0.85f).toFloat(),
            (Math.random() * 2 + 1).toFloat()  // radius
        )
    }

    private val skyGradPaint = Paint()
    private val groundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0D1117")
    }
    private val groundLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        alpha = 25
        color = Color.parseColor("#00E5FF")
    }

    fun update(speed: Float, dt: Float) {
        val dx = speed * dt
        layers.forEach { it.offset = (it.offset + dx * it.speedMult) % screenW }

        // Slowly drift stars
        stars.forEach { s ->
            s[0] -= dx * 0.05f
            if (s[0] < 0) s[0] = screenW.toFloat()
        }
    }

    fun draw(canvas: Canvas) {
        // Sky gradient
        val gradient = LinearGradient(
            0f, 0f, 0f, groundY,
            intArrayOf(Color.parseColor("#020818"), Color.parseColor("#0D1B2A"), Color.parseColor("#0A2744")),
            null, Shader.TileMode.CLAMP
        )
        skyGradPaint.shader = gradient
        canvas.drawRect(0f, 0f, screenW.toFloat(), groundY, skyGradPaint)

        // Stars
        stars.forEach { s ->
            starPaint.alpha = (180 + Math.sin(s[0].toDouble()).toFloat() * 60).toInt().coerceIn(100, 255)
            canvas.drawCircle(s[0], s[1], s[2], starPaint)
        }

        // Parallax grid lines (distant city effect)
        layers.forEachIndexed { i, layer ->
            gridPaint.color = layer.lineColor
            gridPaint.alpha = layer.alpha
            val spacing = 120f - i * 20f
            var x = -layer.offset % spacing
            while (x < screenW) {
                canvas.drawLine(x, 0f, x, groundY, gridPaint)
                x += spacing
            }
        }

        // Ground
        canvas.drawRect(0f, groundY, screenW.toFloat(), screenH.toFloat(), groundPaint)

        // Ground line (neon)
        canvas.drawLine(0f, groundY, screenW.toFloat(), groundY, groundLinePaint)

        // Ground grid (perspective lines)
        val numLines = 12
        for (i in 0..numLines) {
            val t = i.toFloat() / numLines
            val x = t * screenW
            groundLinePaint.alpha = (255 * (1 - t * 0.8f)).toInt()
            groundLinePaint.strokeWidth = 1f
            canvas.drawLine(x, groundY, screenW / 2f, screenH.toFloat(), groundLinePaint)
        }
        groundLinePaint.alpha = 255
        groundLinePaint.strokeWidth = 3f
    }
}
