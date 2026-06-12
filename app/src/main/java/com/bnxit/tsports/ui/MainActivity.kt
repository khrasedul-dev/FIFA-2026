package com.bnxit.tsports.ui

import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bnxit.tsports.databinding.ActivityMainBinding
import com.bnxit.tsports.player.ExoPlayerManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var exoPlayerManager: ExoPlayerManager

    private val streamUrl = "http://10.11.12.13/player.php?stream=1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hide the system UI for a full screen TV experience
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        exoPlayerManager = ExoPlayerManager(this)
        val player = exoPlayerManager.initializePlayer()
        binding.playerView.player = player
        
        // Make player view focusable for TV D-Pad interactions
        binding.playerView.isFocusable = true
        binding.playerView.isFocusableInTouchMode = true
        binding.playerView.requestFocus()

        // Handle aspect ratio toggle
        binding.btnAspectRatio.setOnClickListener {
            when (binding.playerView.resizeMode) {
                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> {
                    binding.playerView.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                }
                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL -> {
                    binding.playerView.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
                else -> {
                    binding.playerView.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            }
        }

        // Handle developer info toggle
        binding.btnDeveloperInfo.setOnClickListener {
            val panel = binding.developerInfoPanel
            if (panel.visibility == android.view.View.VISIBLE) {
                panel.visibility = android.view.View.GONE
            } else {
                panel.visibility = android.view.View.VISIBLE
            }
        }

        exoPlayerManager.playStream(streamUrl)
    }

    override fun onResume() {
        super.onResume()
        if (exoPlayerManager.player == null) {
            val player = exoPlayerManager.initializePlayer()
            binding.playerView.player = player
            exoPlayerManager.playStream(streamUrl)
        } else {
            exoPlayerManager.player?.playWhenReady = true
        }
    }

    override fun onPause() {
        super.onPause()
        exoPlayerManager.player?.playWhenReady = false
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayerManager.releasePlayer()
        binding.playerView.player = null
    }

    // Handle D-pad center to play/pause if controller doesn't automatically catch it
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (binding.developerInfoPanel.visibility == android.view.View.VISIBLE) {
                binding.developerInfoPanel.visibility = android.view.View.GONE
                return true
            }
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            val player = exoPlayerManager.player
            if (player != null) {
                if (player.isPlaying) {
                    player.pause()
                } else {
                    player.play()
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
