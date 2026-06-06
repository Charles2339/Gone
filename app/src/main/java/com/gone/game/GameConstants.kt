package com.gone.game

import kotlin.math.PI

object GameConstants {
    // Physics
    const val GRAVITY = 1800f
    const val JUMP_VELOCITY        = -725f   // -580 × 1.25
    const val DOUBLE_JUMP_VELOCITY = -775f   // -620 × 1.25
    const val SLIDE_DURATION_MS    = 600L

    // Speed progression (px/s)
    const val BASE_SPEED      = 500f
    const val MAX_SPEED       = 1400f
    const val SPEED_INCREMENT = 30f

    // Player geometry (fraction of screen)
    const val PLAYER_WIDTH_FRAC  = 0.055f
    const val PLAYER_HEIGHT_FRAC = 0.20f
    const val PLAYER_X_FRAC      = 0.15f

    // Ground band (fraction of screen height)
    const val GROUND_HEIGHT_FRAC = 0.18f

    // Obstacles
    const val MIN_OBSTACLE_GAP_MS = 1200L
    const val MAX_OBSTACLE_GAP_MS = 2400L

    // Score
    const val METRES_PER_PX = 1f / BASE_SPEED

    // Animation — cycles per second while running
    const val RUN_ANIM_FREQ = 3.0   // full stride per second (π-based)
    val TWO_PI = (2.0 * PI).toFloat()

    // FPS
    const val TARGET_FPS = 60
}
