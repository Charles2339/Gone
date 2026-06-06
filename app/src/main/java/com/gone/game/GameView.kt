package com.gone.game

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.max
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

    private var sessionCoins = 0
    private var totalCoins   = prefs.getCoins()

    // Spawn timers
    private var obstacleTimer  = 0L
    private var nextObstacleMs = 1500L
    private var coinTimer      = 0L
    private var nextCoinMs     = 1800L

    // Touch
    private var touchStartY    = 0f
    private var touchStartTime = 0L

    // ── Paints ────────────────────────────────────────────────────────────────
    private val bgFallback  = Paint().apply { color = Color.parseColor("#020818") }
    private val overlayPaint = Paint().apply { color = Color.parseColor("#CC000000") }
    private val hudBgPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#AA000000") }
    private val neonBorder  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF"); strokeWidth = 2f; style = Paint.Style.STROKE
    }
    private val scorePaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD
    }
    private val speedPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD600"); typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val coinHudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD600"); typeface = Typeface.DEFAULT_BOLD
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0BEC5"); textAlign = Paint.Align.CENTER
    }
    private val titlePaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF"); typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF3D00"); typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val coinAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD600"); typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    init { holder.addCallback(this); isFocusable = true }

    // ── Surface lifecycle ─────────────────────────────────────────────────────

    override fun surfaceCreated(holder: SurfaceHolder) {
        screenW = width; screenH = height
        initGame(); surfaceReady = true; startThread()
    }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) { screenW = w; screenH = h }
    override fun surfaceDestroyed(holder: SurfaceHolder) { surfaceReady = false; stopThread() }

    private fun startThread() {
        gameThread?.running = false
        gameThread = GameThread(holder, this).also { it.start() }
    }
    private fun stopThread() {
        gameThread?.running = false
        var retry = true
        while (retry) { try { gameThread?.join(); retry = false } catch (e: InterruptedException) {} }
        gameThread = null
    }

    fun pause()  { if (gameState == GameState.PLAYING) gameState = GameState.PAUSED; stopThread() }
    fun resume() { if (surfaceReady) { if (gameState == GameState.PAUSED) gameState = GameState.PLAYING; startThread() } }

    // ── Game init ─────────────────────────────────────────────────────────────

    private fun initGame() {
        player     = Player(screenW, screenH)
        background = Background(screenW, screenH)
        obstacles.clear(); coins.clear(); particles.clear()
        speed = GameConstants.BASE_SPEED
        distancePx = 0f; score = 0; sessionCoins = 0
        obstacleTimer = 0L; nextObstacleMs = 1500L
        coinTimer = 0L;     nextCoinMs     = 1800L
        gameState = GameState.PLAYING
    }

    // ── Update ────────────────────────────────────────────────────────────────

    fun update(dt: Float) {
        if (!surfaceReady) return
        if (gameState == GameState.PLAYING) updateGame(dt)
    }

    private fun updateGame(dt: Float) {
        val p  = player     ?: return
        val bg = background ?: return

        speed = (speed + GameConstants.SPEED_INCREMENT * dt).coerceAtMost(GameConstants.MAX_SPEED)
        distancePx += speed * dt
        score = (distancePx * GameConstants.METRES_PER_PX).toInt()

        // Reset platform flag before update; GameView re-sets it after obstacle check
        p.onPlatform = false
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
            coinTimer = 0; nextCoinMs = Random.nextLong(900L, 2400L)
            val lane  = CoinLane.entries.random()
            val count = Random.nextInt(1, 4)
            repeat(count) { i ->
                val c = Coin(screenW, screenH, lane)
                c.x += i * screenW * 0.065f
                coins.add(c)
            }
        }

        // ── Move & cull obstacles ──────────────────────────────────────────────
        val obsIter = obstacles.iterator()
        while (obsIter.hasNext()) {
            val obs = obsIter.next()
            obs.move(dx)
            if (obs.isOffScreen()) {
                obsIter.remove()
                spawnScoreParticles(obs.rect.right + 60f, obs.rect.centerY())
            }
        }

        // ── Coin collision — check BEFORE move so fast coins can't tunnel ──────
        val playerBox = p.getHitbox()
        val coinIter  = coins.iterator()
        while (coinIter.hasNext()) {
            val coin = coinIter.next()
            // Check with pre-move position
            val preHit = RectF.intersects(playerBox, coin.getHitbox())
            coin.move(dx)
            coin.update(dt)
            // Also check post-move position (catches coins that passed through)
            val postHit = RectF.intersects(playerBox, coin.getHitbox())
            if ((preHit || postHit) && !coin.isFullyDone()) {
                coin.collect()
                sessionCoins++
                spawnCoinParticles(coin.x, coin.y)
            }
            if (coin.isFullyDone() || coin.isOffScreen()) coinIter.remove()
        }

        // ── Particles ─────────────────────────────────────────────────────────
        particles.forEach { it.update(dt) }
        particles.removeAll { it.isDead() }

        // ── Platform + collision logic ─────────────────────────────────────────
        // Phase 1: check if player is landing on / standing on any obstacle top
        val feetY       = p.y + p.h
        val landMargin  = p.h * 0.28f + speed * dt   // generous for fast landing
        var landedThisFrame = false

        for (obs in obstacles) {
            val oh = obs.getHitbox()
            // Must overlap horizontally (use slightly inset player x so edges don't count)
            if (p.x + p.w * 0.85f <= oh.left || p.x + p.w * 0.15f >= oh.right) continue
            val obsTop = oh.top
            // Player falling (velY>=0) and feet near obstacle top = land on it
            if (p.velY >= 0f && feetY >= obsTop - landMargin && feetY <= obsTop + landMargin) {
                p.landOn(obsTop)
                landedThisFrame = true
                break
            }
        }

        // Phase 2: fatal collision — only if player is NOT standing on a platform top
        if (!landedThisFrame) {
            val freshBox = p.getHitbox()   // re-fetch after potential landOn snap
            for (obs in obstacles) {
                if (!RectF.intersects(freshBox, obs.getHitbox())) continue
                val oh     = obs.getHitbox()
                val fresh_feetY = freshBox.bottom
                // If feet are clearly above obstacle top → was a landing, not a side hit
                if (fresh_feetY <= oh.top + p.h * 0.22f && p.velY >= 0f) continue
                // Otherwise it's a genuine side/front collision
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
        val pad  = 16f
        val ts   = screenH * 0.042f
        val rowH = ts + pad * 1.7f

        scorePaint.textSize   = ts
        coinHudPaint.textSize = ts * 0.80f
        speedPaint.textSize   = ts * 0.66f

        // Score pill
        val scoreText = "${score}m"
        val scoreW = scorePaint.measureText(scoreText) + pad * 3f
        val sr = RectF(pad, pad, pad + scoreW, pad + rowH)
        canvas.drawRoundRect(sr, 14f, 14f, hudBgPaint)
        canvas.drawRoundRect(sr, 14f, 14f, neonBorder)
        canvas.drawText(scoreText, pad * 1.9f, pad + ts * 0.97f, scorePaint)

        // Coin pill
        val coinText = "✦ $sessionCoins"
        val coinW    = coinHudPaint.measureText(coinText) + pad * 3f
        val cr = RectF(pad, pad + rowH + 6f, pad + coinW, pad + rowH * 1.95f + 6f)
        canvas.drawRoundRect(cr, 14f, 14f, hudBgPaint)
        val goldBorder = Paint(neonBorder).also { it.color = Color.parseColor("#55FFD600") }
        canvas.drawRoundRect(cr, 14f, 14f, goldBorder)
        canvas.drawText(coinText, pad * 1.9f, pad + rowH * 1.85f, coinHudPaint)

        // Speed (centre top)
        canvas.drawText("%.0f km/h".format(speed * 0.06f), screenW / 2f, pad + ts * 0.75f, speedPaint)

        // Best (top right)
        subtitlePaint.textSize  = ts * 0.70f
        subtitlePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Best ${highScore}m", screenW - pad, pad + ts * 0.88f, subtitlePaint)

        // Controls hint
        if (score < 5) {
            subtitlePaint.textAlign = Paint.Align.CENTER
            subtitlePaint.textSize  = ts * 0.60f
            subtitlePaint.alpha     = 190
            canvas.drawText("TAP = Jump  ·  Double-tap air = Double jump  ·  Swipe ↓ = Slide",
                screenW / 2f, screenH - pad * 2.5f, subtitlePaint)
            subtitlePaint.alpha = 255
        }
    }

    // ── Overlays ──────────────────────────────────────────────────────────────

    private fun drawGameOver(canvas: Canvas) {
        canvas.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), overlayPaint)
        val cx = screenW / 2f; val cy = screenH / 2f

        titlePaint.textSize  = screenH * 0.11f
        canvas.drawText("GONE", cx, cy - screenH * 0.18f, titlePaint)

        accentPaint.textSize = screenH * 0.065f
        canvas.drawText("${score}m", cx, cy - screenH * 0.06f, accentPaint)

        subtitlePaint.textSize  = screenH * 0.037f
        subtitlePaint.textAlign = Paint.Align.CENTER
        canvas.drawText("Best: ${highScore}m", cx, cy + screenH * 0.01f, subtitlePaint)

        coinAccentPaint.textSize = screenH * 0.046f
        canvas.drawText("✦ +$sessionCoins   (total: $totalCoins)", cx, cy + screenH * 0.09f, coinAccentPaint)

        val divPaint = Paint().apply { color = Color.parseColor("#33FFFFFF"); strokeWidth = 1f }
        canvas.drawLine(cx - screenW * 0.22f, cy + screenH * 0.14f,
                        cx + screenW * 0.22f, cy + screenH * 0.14f, divPaint)

        subtitlePaint.textSize = screenH * 0.043f
        subtitlePaint.alpha    = (200 + (System.currentTimeMillis() / 550 % 2) * 55).toInt()
        canvas.drawText("TAP TO RESTART", cx, cy + screenH * 0.22f, subtitlePaint)
        subtitlePaint.alpha = 255
    }

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
            MotionEvent.ACTION_DOWN -> { touchStartY = event.y; touchStartTime = System.currentTimeMillis() }
            MotionEvent.ACTION_UP   -> {
                val dy = event.y - touchStartY
                val el = System.currentTimeMillis() - touchStartTime
                when (gameState) {
                    GameState.PLAYING   -> if (dy > screenH * 0.08f && el < 300) player?.slide() else player?.jump()
                    GameState.GAME_OVER -> initGame()
                    GameState.PAUSED    -> gameState = GameState.PLAYING
                }
            }
        }
        return true
    }

    // ── Particles ─────────────────────────────────────────────────────────────

    private fun spawnDeathParticles(x: Float, y: Float)  { repeat(22) { particles.add(Particle(x, y, false)) } }
    private fun spawnScoreParticles(x: Float, y: Float)  { repeat(4)  { particles.add(Particle(x, y, true))  } }
    private fun spawnCoinParticles(x: Float, y: Float)   { repeat(6)  { particles.add(Particle(x, y, true))  } }
}
