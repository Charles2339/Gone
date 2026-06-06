package com.gone.game

object GameConstants {
    // Physics
    const val GRAVITY = 1800f          // px/s²
    const val JUMP_VELOCITY = -900f    // px/s  (negative = up)
    const val SLIDE_DURATION_MS = 600L

    // Speed progression (px/s)
    const val BASE_SPEED = 500f
    const val MAX_SPEED = 1400f
    const val SPEED_INCREMENT = 30f    // added per second

    // Player geometry (fraction of screen height)
    const val PLAYER_WIDTH_FRAC = 0.055f
    const val PLAYER_HEIGHT_FRAC = 0.18f
    const val PLAYER_X_FRAC = 0.15f   // fixed x position

    // Ground
    const val GROUND_HEIGHT_FRAC = 0.18f

    // Obstacles
    const val MIN_OBSTACLE_GAP_MS = 1200L
    const val MAX_OBSTACLE_GAP_MS = 2400L

    // Score (1 metre = BASE_SPEED px travelled)
    const val METRES_PER_PX = 1f / BASE_SPEED

    // FPS target
    const val TARGET_FPS = 60
}
