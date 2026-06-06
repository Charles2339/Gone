package com.gone.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class Particle(
    private var x: Float,
    private var y: Float,
    private val isScore: Boolean
) {
    private val vx = Random.nextFloat() * 400f - 200f
    private val vy = Random.nextFloat() * -500f - 100f
    private var life = 1f
    private val decay = Random.nextFloat() * 1.5f + 0.8f
    private val size = Random.nextFloat() * 8f + 3f
    private val color = if (isScore) {
        Color.parseColor("#00E5FF")
    } else {
        listOf(
            Color.parseColor("#FF3D00"),
            Color.parseColor("#FFD600"),
            Color.parseColor("#FF6D00")
        ).random()
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    fun update(dt: Float) {
        x += vx * dt
        y += (vy + 200f) * dt  // slight gravity on particles
        life -= decay * dt
    }

    fun isDead() = life <= 0f

    fun draw(canvas: Canvas) {
        paint.color = color
        paint.alpha = (life * 255).toInt().coerceIn(0, 255)
        canvas.drawCircle(x, y, size * life, paint)
    }
}
