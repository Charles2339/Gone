package com.gone.game

import kotlin.math.PI

object GameConstants {
    // Physics
    const val GRAVITY              = 1800f
    const val JUMP_VELOCITY        = -725f
    const val DOUBLE_JUMP_VELOCITY = -775f
    const val SLIDE_DURATION_MS    = 600L

    // Speed (px/s)
    const val BASE_SPEED      = 500f
    const val MAX_SPEED       = 1400f
    const val SPEED_INCREMENT = 30f

    // Player geometry — ONLY height fraction matters; everything scales from h
    const val PLAYER_HEIGHT_FRAC = 0.13f   // was 0.20f — proper stickman size
    const val PLAYER_WIDTH_FRAC  = 0.040f  // hitbox width (visual lines are centred on this)
    const val PLAYER_X_FRAC      = 0.15f

    // Ground
    const val GROUND_HEIGHT_FRAC = 0.18f

    // Obstacles
    const val MIN_OBSTACLE_GAP_MS = 1200L
    const val MAX_OBSTACLE_GAP_MS = 2400L

    // Score
    const val METRES_PER_PX = 1f / BASE_SPEED

    // Animation
    const val RUN_ANIM_FREQ = 3.2          // strides per second
    val TWO_PI = (2.0 * PI).toFloat()

    // FPS
    const val TARGET_FPS = 60
}
