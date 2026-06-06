package com.gone.game

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.max
import kotlin.random.Random

enum class GameState { MENU, PLAYING, PAUSED, GAME_OVER }

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private var gameThread: GameThread? = null
    private val prefs = GamePrefs(context)

    private var screenW = 0
    private var screenH = 0

    private lateinit var player: Player
    private lateinit var background: Background
    private val obstacles = mutableListOf<Obstacle>()

    private var gameState = GameState.PLAYING
    private var speed = GameConstants.BASE_SPEED
    private var distancePx = 0f
    private var score = 0
    private var highScore = prefs.getHighScore()

    // Obstacle spawning
    private var nextObstacleMs = 1500L
    private var obstacleTimer = 0L

    // Particles
    private val particles = mutableListOf<Particle>()

    // Touch zones
    private var touchStartY = 0f
    private var touchStartTime = 0L

    // HUD Paints
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
    }
    private val hudBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AA000000")
    }
    private val overlayPaint = Paint().apply {
        color = Color.parseColor("#CC000000")
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0BEC5")
        textAlign = Paint.Align.CENTER
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF3D00")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val speedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD600")
        typeface = Typeface.DEFAULT_BOLD
    }
    private val neonLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        screenW = width
        screenH = height
        initGame()
        gameThread = GameThread(holder, this).also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        screenW = width
        screenH = height
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        gameThread?.running = false
        gameThread?.join()
    }

    private fun initGame() {
        player = Player(screenW, screenH)
        background = Background(screenW, screenH)
        obstacles.clear()
        particles.clear()
        speed = GameConstants.BASE_SPEED
        distancePx = 0f
        score = 0
        obstacleTimer = 0L
        nextObstacleMs = 1500L
        gameState = GameState.PLAYING
    }

    fun update(dt: Float) {
        when (gameState) {
            GameState.PLAYING -> updateGame(dt)
            else -> {}
        }
    }

    private fun updateGame(dt: Float) {
        // Speed ramp
        speed = (speed + GameConstants.SPEED_INCREMENT * dt).coerceAtMost(GameConstants.MAX_SPEED)

        // Distance & score
        distancePx += speed * dt
        score = (distancePx * GameConstants.METRES_PER_PX).toInt()

        // Player
        player.update(dt)

        // Background
        background.update(speed, dt)

        // Spawn obstacles
        obstacleTimer += (dt * 1000).toLong()
        if (obstacleTimer >= nextObstacleMs) {
            obstacleTimer = 0
            nextObstacleMs = Random.nextLong(
                max(400L, GameConstants.MIN_OBSTACLE_GAP_MS - (score / 10).toLong()),
                GameConstants.MAX_OBSTACLE_GAP_MS
            )
            obstacles.add(Obstacle(screenW, screenH))
        }

        // Move & cull obstacles
        val dx = speed * dt
        val iter = obstacles.iterator()
        while (iter.hasNext()) {
            val obs = iter.next()
            obs.move(dx)
            if (obs.isOffScreen()) {
                iter.remove()
                spawnScoreParticles(obs.rect.right + 60f, obs.rect.centerY())
            }
        }

        // Particles
        particles.forEach { it.update(dt) }
        particles.removeAll { it.isDead() }

        // Collision
        val playerBox = player.getHitbox()
        for (obs in obstacles) {
            if (RectF.intersects(playerBox, obs.getHitbox())) {
                player.die()
                gameState = GameState.GAME_OVER
                highScore = score.coerceAtLeast(highScore)
                prefs.saveHighScore(score)
                spawnDeathParticles(player.x + player.w / 2, player.y + player.h / 2)
                break
            }
        }
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        if (screenW == 0) return

        background.draw(canvas)

        obstacles.forEach { it.draw(canvas) }
        player.draw(canvas)
        particles.forEach { it.draw(canvas) }

        drawHUD(canvas)

        when (gameState) {
            GameState.GAME_OVER -> drawGameOver(canvas)
            GameState.PAUSED -> drawPaused(canvas)
            else -> {}
        }
    }

    private fun drawHUD(canvas: Canvas) {
        val pad = 16f
        val textSize = screenH * 0.045f
        scorePaint.textSize = textSize
        speedPaint.textSize = textSize * 0.7f

        // Score box
        val scoreText = "${score}m"
        val boxW = scorePaint.measureText(scoreText) + pad * 3
        canvas.drawRoundRect(RectF(pad, pad, pad + boxW, pad + textSize + pad * 1.5f), 12f, 12f, hudBgPaint)
        canvas.drawRoundRect(RectF(pad, pad, pad + boxW, pad + textSize + pad * 1.5f), 12f, 12f, neonLinePaint)
        canvas.drawText(scoreText, pad * 2, pad + textSize, scorePaint)

        // High score
        val hsText = "Best: ${highScore}m"
        val hsX = screenW - pad - scorePaint.measureText(hsText) - pad
        canvas.drawText(hsText, hsX, pad + textSize, subtitlePaint.also { it.textAlign = Paint.Align.LEFT; it.textSize = textSize * 0.75f })

        // Speed indicator
        val speedText = "%.0f km/h".format(speed * 0.06f)
        speedPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(speedText, screenW / 2f, pad + textSize * 0.8f, speedPaint)

        // Controls hint (early game)
        if (score < 5) {
            subtitlePaint.textSize = textSize * 0.65f
            subtitlePaint.textAlign = Paint.Align.CENTER
            subtitlePaint.alpha = 180
            canvas.drawText("TAP = Jump  |  SWIPE DOWN = Slide", screenW / 2f, screenH - pad * 3, subtitlePaint)
            subtitlePaint.alpha = 255
        }
    }

    private fun drawGameOver(canvas: Canvas) {
        canvas.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), overlayPaint)

        val cx = screenW / 2f
        val cy = screenH / 2f

        titlePaint.textSize = screenH * 0.12f
        canvas.drawText("GONE", cx, cy - screenH * 0.12f, titlePaint)

        accentPaint.textSize = screenH * 0.07f
        canvas.drawText("${score}m", cx, cy + screenH * 0.01f, accentPaint)

        subtitlePaint.textSize = screenH * 0.04f
        canvas.drawText("Best: ${highScore}m", cx, cy + screenH * 0.07f, subtitlePaint)

        // Tap to restart
        subtitlePaint.textSize = screenH * 0.045f
        subtitlePaint.alpha = (200 + (System.currentTimeMillis() / 600 % 2) * 55).toInt()
        canvas.drawText("TAP TO RESTART", cx, cy + screenH * 0.18f, subtitlePaint)
        subtitlePaint.alpha = 255
    }

    private fun drawPaused(canvas: Canvas) {
        canvas.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), overlayPaint)
        titlePaint.textSize = screenH * 0.1f
        canvas.drawText("PAUSED", screenW / 2f, screenH / 2f, titlePaint)
        subtitlePaint.textSize = screenH * 0.045f
        canvas.drawText("TAP TO RESUME", screenW / 2f, screenH / 2f + screenH * 0.1f, subtitlePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartY = event.y
                touchStartTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_UP -> {
                val dy = event.y - touchStartY
                val elapsed = System.currentTimeMillis() - touchStartTime

                when (gameState) {
                    GameState.PLAYING -> {
                        if (dy > screenH * 0.08f && elapsed < 300) {
                            player.slide()
                        } else {
                            player.jump()
                        }
                    }
                    GameState.GAME_OVER -> initGame()
                    GameState.PAUSED -> gameState = GameState.PLAYING
                    else -> {}
                }
            }
        }
        return true
    }

    private fun spawnDeathParticles(x: Float, y: Float) {
        repeat(20) {
            particles.add(Particle(x, y, isScore = false))
        }
    }

    private fun spawnScoreParticles(x: Float, y: Float) {
        repeat(4) {
            particles.add(Particle(x, y, isScore = true))
        }
    }

    fun pause() {
        if (gameState == GameState.PLAYING) gameState = GameState.PAUSED
        gameThread?.running = false
    }

    fun resume() {
        if (gameState == GameState.PAUSED) gameState = GameState.PLAYING
        if (gameThread?.isAlive == false) {
            gameThread = GameThread(holder, this).also { it.start() }
        } else if (gameThread == null) {
            gameThread = GameThread(holder, this).also { it.start() }
        }
    }
}
