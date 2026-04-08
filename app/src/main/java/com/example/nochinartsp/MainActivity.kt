package com.example.nochinartsp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class MainActivity : ComponentActivity() {

    private var player: ExoPlayer? = null

    private lateinit var playerView: PlayerView
    private lateinit var rtspEditText: EditText
    private lateinit var statusText: TextView
    private lateinit var connectButton: Button
    private lateinit var loadingIndicator: ProgressBar

    private var isPlaybackRequested = false

    private val prefs by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rtspEditText = findViewById(R.id.rtspEditText)
        playerView = findViewById(R.id.videoPlayerView)
        statusText = findViewById(R.id.statusText)
        connectButton = findViewById(R.id.openCameraButton)
        loadingIndicator = findViewById(R.id.loadingIndicator)

        val savedRtspUrl = prefs.getString(PREF_RTSP_URL, null)
        val initialUrl = savedRtspUrl?.takeIf { it.isNotBlank() }.orEmpty()
        rtspEditText.setText(initialUrl)

        connectButton.setOnClickListener {
            if (isPlaybackRequested) {
                stopRtspStream()
            } else {
                startRtspStream(autoConnect = false)
            }
        }

        if (initialUrl.isNotBlank()) {
            startRtspStream(autoConnect = true)
        } else {
            updateUiState(StreamState.IDLE)
        }
    }

    private fun ensurePlayerInitialized() {
        if (player != null) return

        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            playerView.player = exoPlayer
            exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
            exoPlayer.addListener(
                object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> updateUiState(StreamState.CONNECTING)
                            Player.STATE_READY -> {
                                if (exoPlayer.playWhenReady) {
                                    updateUiState(StreamState.PLAYING)
                                }
                            }

                            Player.STATE_ENDED,
                            Player.STATE_IDLE -> {
                                if (!isPlaybackRequested) {
                                    updateUiState(StreamState.IDLE)
                                }
                            }
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        isPlaybackRequested = false
                        updateUiState(StreamState.ERROR)
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.stream_connection_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }
    }

    private fun startRtspStream(autoConnect: Boolean) {
        val rtspUrl = rtspEditText.text.toString().trim()
        if (rtspUrl.isBlank()) {
            if (!autoConnect) {
                Toast.makeText(this, getString(R.string.enter_rtsp_url), Toast.LENGTH_SHORT).show()
            }
            updateUiState(StreamState.IDLE)
            return
        }

        ensurePlayerInitialized()

        prefs.edit().putString(PREF_RTSP_URL, rtspUrl).apply()
        isPlaybackRequested = true
        updateUiState(StreamState.CONNECTING)

        val mediaItem = MediaItem.Builder()
            .setUri(rtspUrl)
            .setMimeType("application/x-rtsp")
            .build()

        player?.apply {
            setMediaItem(mediaItem)
            playWhenReady = true
            prepare()
        }
    }

    private fun stopRtspStream() {
        isPlaybackRequested = false
        player?.apply {
            playWhenReady = false
            stop()
            clearMediaItems()
        }
        updateUiState(StreamState.IDLE)
    }

    override fun onStart() {
        super.onStart()
        if (isPlaybackRequested) {
            player?.playWhenReady = true
        }
    }

    override fun onStop() {
        super.onStop()
        player?.playWhenReady = false
    }

    override fun onDestroy() {
        super.onDestroy()
        playerView.player = null
        player?.release()
        player = null
    }

    private fun updateUiState(state: StreamState) {
        when (state) {
            StreamState.IDLE -> {
                loadingIndicator.visibility = android.view.View.GONE
                statusText.text = getString(R.string.status_idle)
                connectButton.text = getString(R.string.action_connect)
            }

            StreamState.CONNECTING -> {
                loadingIndicator.visibility = android.view.View.VISIBLE
                statusText.text = getString(R.string.status_connecting)
                connectButton.text = getString(R.string.action_disconnect)
            }

            StreamState.PLAYING -> {
                loadingIndicator.visibility = android.view.View.GONE
                statusText.text = getString(R.string.status_playing)
                connectButton.text = getString(R.string.action_disconnect)
            }

            StreamState.ERROR -> {
                loadingIndicator.visibility = android.view.View.GONE
                statusText.text = getString(R.string.status_error)
                connectButton.text = getString(R.string.action_connect)
            }
        }
    }

    private enum class StreamState {
        IDLE,
        CONNECTING,
        PLAYING,
        ERROR
    }

    companion object {
        private const val PREFS_NAME = "no_china_rtsp_prefs"
        private const val PREF_RTSP_URL = "pref_rtsp_url"
    }
}
