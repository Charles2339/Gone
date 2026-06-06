package com.gone.game

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: GamePrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        )

        setContentView(R.layout.activity_main)

        prefs = GamePrefs(this)

        val tvHighScore = findViewById<TextView>(R.id.tvHighScore)
        val btnPlay = findViewById<Button>(R.id.btnPlay)

        tvHighScore.text = "Best: ${prefs.getHighScore()}m"

        btnPlay.setOnClickListener {
            startActivity(Intent(this, GameActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        val tvHighScore = findViewById<TextView>(R.id.tvHighScore)
        tvHighScore.text = "Best: ${prefs.getHighScore()}m"
    }
}
