package com.gone.game

import android.graphics.Canvas
import android.view.SurfaceHolder

class GameThread(
    private val surfaceHolder: SurfaceHolder,
    private val gameView: GameView
) : Thread() {

    var running = true
    private val targetMs = 1000L / GameConstants.TARGET_FPS

    override fun run() {
        var lastTime = System.nanoTime()

        while (running) {
            val now = System.nanoTime()
            val dt = ((now - lastTime) / 1_000_000_000f).coerceAtMost(0.05f)
            lastTime = now

            gameView.update(dt)

            var canvas: Canvas? = null
            try {
                canvas = surfaceHolder.lockCanvas()
                synchronized(surfaceHolder) {
                    if (canvas != null) gameView.draw(canvas)
                }
            } finally {
                if (canvas != null) surfaceHolder.unlockCanvasAndPost(canvas)
            }

            val elapsed = (System.nanoTime() - now) / 1_000_000
            val sleepMs = targetMs - elapsed
            if (sleepMs > 0) sleep(sleepMs)
        }
    }
}
