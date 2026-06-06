package com.gone.game

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

enum class GameState { PLAYING, PAUSED, GAME_OVER }

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private var gameThread: GameThread? = null
    private val prefs = GamePrefs(context)

    @Volatile private var surfaceReady = false

    private var screenW = 0
    private var screenH = 0

    private var player: Player? = null
    private var background: Background? = null
    private val obstacles = mutableListOf<Obstacle>()
    private val coins     = mutableListOf<Coin>()
    private val particles = mutableListOf<Particle>()

    private var gameState  = GameState.PLAYING
    private var speed      = GameConstants.BASE_SPEED
    private var distancePx = 0f
    private var score      = 0
    private var highScore  = prefs.getHighScore()

    // Session coins (added to persistent bank on game over)
    private var sessionCoins = 0
    private var totalCoins   = prefs.getCoins()

    // Spawning
    private var nextObstacleMs = 1500L
    private var obstacleTimer  = 0L
    private var coinTimer      = 0L
    private var nextCoinMs     = 1800L

    // Touch
    private var touchStartY    = 0f
    private var touchStartTime = 0L

    // ── Paints ────────────────────────────────────────────────────────────────
    private val bgFallback  = Paint().apply { color = Color.parseColor("#020818") }
    private val overlayPaint = Paint().apply { color = Color.parseColor("#CC000000") }

    private val hudBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AA000000")
    }
    private val neonBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
    }
    private val speedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD600")
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val coinHudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD600")
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0BEC5")
        textAlign = Paint.Align.CENTER
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF3D00")
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val coinAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD600")
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    // ── Surface lifecycle ─────────────────────────────────────────────────────

    override fun surfaceCreated(holder: SurfaceHolder) {
        screenW = width; screenH = height
        initGame()
        surfaceReady = true
        startThread()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        screenW = width; screenH = height
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        stopThread()
    }

    // ── Thread helpers ────────────────────────────────────────────────────────

    private fun startThread() {
        gameThread?.running = false
        gameThread = GameThread(holder, this).also { it.start() }
    }

    private fun stopThread() {
        gameThread?.running = false
        var retry = true
        while (retry) {
            try { gameThread?.join(); retry = false }
            catch (e: InterruptedException) { /* keep trying */ }
        }
        gameThread = null
    }

    fun pause() {
        if (gameState == GameState.PLAYING) gameState = GameState.PAUSED
        stopThread()
    }

    fun resume() {
        if (surfaceReady) {
            if (gameState == GameState.PAUSED) gameState = GameState.PLAYING
            startThread()
        }
    }

    // ── Game logic ────────────────────────────────────────────────────────────

    private fun initGame() {
        player   = Player(screenW, screenH)
        background = Background(screenW, screenH)
        obstacles.clear(); coins.clear(); particles.clear()
        speed         = GameConstants.BASE_SPEED
        distancePx    = 0f; score = 0
        sessionCoins  = 0
        obstacleTimer = 0L; nextObstacleMs = 1500L
        coinTimer     = 0L; nextCoinMs     = 1800L
        gameState     = GameState.PLAYING
    }

    fun update(dt: Float) {
        if (!surfaceReady) return
        if (gameState == GameState.PLAYING) updateGame(dt)
    }

    private fun updateGame(dt: Float) {
        val p  = player     ?: return
        val bg = background ?: return

        // Speed ramp
        speed = (speed + GameConstants.SPEED_INCREMENT * dt).coerceAtMost(GameConstants.MAX_SPEED)
        distancePx += speed * dt
        score = (distancePx * GameConstants.METRES_PER_PX).toInt()

        p.update(dt)
        bg.update(speed, dt)

        val dx = speed * dt

        // ── Spawn obstacles ────────────────────────────────────────────────────
        obstacleTimer += (dt * 1000).toLong()
        if (obstacleTimer >= nextObstacleMs) {
            obstacleTimer = 0
            nextObstacleMs = Random.nextLong(
                max(400L, GameConstants.MIN_OBSTACLE_GAP_MS - (score / 10).toLong()),
                GameConstants.MAX_OBSTACLE_GAP_MS
            )
            obstacles.add(Obstacle(screenW, screenH))
        }

        // ── Spawn coins ────────────────────────────────────────────────────────
        coinTimer += (dt * 1000).toLong()
        if (coinTimer >= nextCoinMs) {
            coinTimer = 0
            nextCoinMs = Random.nextLong(900L, 2200L)
            // Spawn a cluster of 1–3 coins in the same lane
            val lane = CoinLane.entries.random()
            val count = Random.nextInt(1, 4)
            val spacing = screenW * 0.06f
            repeat(count) { i ->
                val c = Coin(screenW, screenH, lane)
                c.x += i * spacing
                coins.add(c)
            }
        }

        // ── Move & cull obstacles ──────────────────────────────────────────────
        val obsIter = obstacles.iterator()
        while (obsIter.hasNext()) {
            val obs = obsIter.next()
            obs.move(dx)
            if (obs.isOffScreen()) { obsIter.remove(); spawnScoreParticles(obs.rect.right + 60f, obs.rect.centerY()) }
        }

        // ── Move & collect coins ───────────────────────────────────────────────
        val playerBox = p.getHitbox()
        val coinIter  = coins.iterator()
        while (coinIter.hasNext()) {
            val coin = coinIter.next()
            coin.move(dx)
            coin.update(dt)
            if (!coin.isCollected() && RectF.intersects(playerBox, coin.getHitbox())) {
                coin.collect()
                sessionCoins++
                spawnCoinParticles(coin.x, coin.y)
            }
            if (coin.isCollected() || coin.isOffScreen()) coinIter.remove()
        }

        // ── Particles ─────────────────────────────────────────────────────────
        particles.forEach { it.update(dt) }
        particles.removeAll { it.isDead() }

        // ── Collision with obstacles ───────────────────────────────────────────
        for (obs in obstacles) {
            if (RectF.intersects(playerBox, obs.getHitbox())) {
                p.die()
                gameState = GameState.GAME_OVER
                highScore = score.coerceAtLeast(highScore)
                prefs.saveHighScore(score)
                prefs.addCoins(sessionCoins)
                totalCoins = prefs.getCoins()
                spawnDeathParticles(p.x + p.w / 2, p.y + p.h / 2)
                break
            }
        }
    }

    // ── Draw ──────────────────────────────────────────────────────────────────

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        val p  = player
        val bg = background
        if (p == null || bg == null) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgFallback)
            return
        }

        bg.draw(canvas)
        coins.forEach     { it.draw(canvas) }
        obstacles.forEach { it.draw(canvas) }
        p.draw(canvas)
        particles.forEach { it.draw(canvas) }

        drawHUD(canvas)

        when (gameState) {
            GameState.GAME_OVER -> drawGameOver(canvas)
            GameState.PAUSED    -> drawPaused(canvas)
            else -> {}
        }
    }

    // ── HUD ───────────────────────────────────────────────────────────────────

    private fun drawHUD(canvas: Canvas) {
        val pad      = 16f
        val textSize = screenH * 0.044f
        val rowH     = textSize + pad * 1.6f

        scorePaint.textSize  = textSize
        speedPaint.textSize  = textSize * 0.68f
        coinHudPaint.textSize = textSize * 0.82f

        // ── Score pill (top-left) ──────────────────────────────────────────────
        val scoreText = "${score}m"
        val scoreW = scorePaint.measureText(scoreText) + pad * 2.8f
        val scoreRect = RectF(pad, pad, pad + scoreW, pad + rowH)
        canvas.drawRoundRect(scoreRect, 14f, 14f, hudBgPaint)
        canvas.drawRoundRect(scoreRect, 14f, 14f, neonBorderPaint)
        canvas.drawText(scoreText, pad * 1.8f, pad + textSize * 0.98f, scorePaint)

        // ── Coin pill (below score) ────────────────────────────────────────────
        val coinText  = "✦ $sessionCoins"
        val coinW     = coinHudPaint.measureText(coinText) + pad * 2.8f
        val coinRect  = RectF(pad, pad + rowH + 6f, pad + coinW, pad + rowH * 2f + 6f)
        canvas.drawRoundRect(coinRect, 14f, 14f, hudBgPaint)
        canvas.drawRoundRect(coinRect, 14f, 14f, neonBorderPaint.also { it.color = Color.parseColor("#44FFD600") })
        canvas.drawText(coinText, pad * 1.8f, pad + rowH * 1.9f, coinHudPaint)
        neonBorderPaint.color = Color.parseColor("#00E5FF")   // restore

        // ── Speed (top-centre) ────────────────────────────────────────────────
        canvas.drawText("%.0f km/h".format(speed * 0.06f), screenW / 2f, pad + textSize * 0.75f, speedPaint)

        // ── Best (top-right) ──────────────────────────────────────────────────
        subtitlePaint.textSize  = textSize * 0.72f
        subtitlePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Best ${highScore}m", screenW - pad, pad + textSize * 0.90f, subtitlePaint)

        // ── Controls hint (first 5 m) ─────────────────────────────────────────
        if (score < 5) {
            subtitlePaint.textAlign = Paint.Align.CENTER
            subtitlePaint.textSize  = textSize * 0.62f
            subtitlePaint.alpha     = 180
            canvas.drawText("TAP = Jump  |  TAP AGAIN (air) = Double jump  |  SWIPE ↓ = Slide",
                screenW / 2f, screenH - pad * 2.5f, subtitlePaint)
            subtitlePaint.alpha = 255
        }
    }

    // ── Game-over screen ──────────────────────────────────────────────────────

    private fun drawGameOver(canvas: Canvas) {
        canvas.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), overlayPaint)
        val cx = screenW / 2f
        val cy = screenH / 2f

        titlePaint.textSize = screenH * 0.11f
        canvas.drawText("GONE", cx, cy - screenH * 0.18f, titlePaint)

        accentPaint.textSize = screenH * 0.065f
        canvas.drawText("${score}m", cx, cy - screenH * 0.06f, accentPaint)

        subtitlePaint.textSize  = screenH * 0.038f
        subtitlePaint.textAlign = Paint.Align.CENTER
        canvas.drawText("Best: ${highScore}m", cx, cy + screenH * 0.01f, subtitlePaint)

        // Coins collected this run
        coinAccentPaint.textSize = screenH * 0.048f
        canvas.drawText("✦ +$sessionCoins  (total: $totalCoins)", cx, cy + screenH * 0.09f, coinAccentPaint)

        // Divider
        val divPaint = Paint().apply { color = Color.parseColor("#33FFFFFF"); strokeWidth = 1f }
        canvas.drawLine(cx - screenW * 0.22f, cy + screenH * 0.14f, cx + screenW * 0.22f, cy + screenH * 0.14f, divPaint)

        subtitlePaint.textSize = screenH * 0.043f
        val blink = (200 + (System.currentTimeMillis() / 550 % 2) * 55).toInt()
        subtitlePaint.alpha = blink
        canvas.drawText("TAP TO RESTART", cx, cy + screenH * 0.22f, subtitlePaint)
        subtitlePaint.alpha = 255
    }

    // ── Paused screen ─────────────────────────────────────────────────────────

    private fun drawPaused(canvas: Canvas) {
        canvas.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), overlayPaint)
        titlePaint.textSize = screenH * 0.10f
        canvas.drawText("PAUSED", screenW / 2f, screenH / 2f, titlePaint)
        subtitlePaint.textSize  = screenH * 0.043f
        subtitlePaint.textAlign = Paint.Align.CENTER
        canvas.drawText("TAP TO RESUME", screenW / 2f, screenH / 2f + screenH * 0.11f, subtitlePaint)
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartY    = event.y
                touchStartTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_UP -> {
                val dy      = event.y - touchStartY
                val elapsed = System.currentTimeMillis() - touchStartTime
                when (gameState) {
                    GameState.PLAYING   -> if (dy > screenH * 0.08f && elapsed < 300) player?.slide() else player?.jump()
                    GameState.GAME_OVER -> initGame()
                    GameState.PAUSED    -> gameState = GameState.PLAYING
                }
            }
        }
        return true
    }

    // ── Particles ─────────────────────────────────────────────────────────────

    private fun spawnDeathParticles(x: Float, y: Float) {
        repeat(22) { particles.add(Particle(x, y, isScore = false)) }
    }

    private fun spawnScoreParticles(x: Float, y: Float) {
        repeat(4) { particles.add(Particle(x, y, isScore = true)) }
    }

    private fun spawnCoinParticles(x: Float, y: Float) {
        repeat(6) { particles.add(Particle(x, y, isScore = true)) }
    }
}
