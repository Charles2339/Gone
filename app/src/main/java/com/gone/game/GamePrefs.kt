package com.gone.game

import android.content.Context

class GamePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("gone_prefs", Context.MODE_PRIVATE)

    fun getHighScore(): Int = prefs.getInt("high_score", 0)

    fun saveHighScore(score: Int) {
        if (score > getHighScore()) {
            prefs.edit().putInt("high_score", score).apply()
        }
    }
}
