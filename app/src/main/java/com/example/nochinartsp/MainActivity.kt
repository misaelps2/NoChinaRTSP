package com.example.nochinartsp

import android.net.Uri
import android.os.Bundle
import android.view.SurfaceView
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import org.videolan.libvlc.IVLCVout
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

class MainActivity : ComponentActivity() {

    private var libVlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null

    private lateinit var surfaceView: SurfaceView
    private lateinit var rtspEditText: EditText
    private lateinit var statusText: TextView
    private lateinit var connectButton: Button
    private lateinit var loadingIndicator: ProgressBar

    private var pendingResumePlayback = false
    private var isStreamStarting = false

    private val prefs by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rtspEditText = findViewById(R.id.rtspEditText)
        surfaceView = findViewById(R.id.videoSurface)
        statusText = findViewById(R.id.statusText)
        connectButton = findViewById(R.id.openCameraButton)
        loadingIndicator = findViewById(R.id.loadingIndicator)

        val savedRtspUrl = prefs.getString(PREF_RTSP_URL, null)
        val defaultRtspUrl = getString(R.string.default_rtsp_url)
        val initialUrl = savedRtspUrl?.takeIf { it.isNotBlank() } ?: defaultRtspUrl
        rtspEditText.setText(initialUrl)

        connectButton.setOnClickListener {
            if (mediaPlayer?.isPlaying == true || isStreamStarting) {
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
        if (libVlc != null && mediaPlayer != null) return

        val options = arrayListOf(
            "--network-caching=250",
            "--rtsp-tcp"
        )

        libVlc = LibVLC(this, options)
        mediaPlayer = MediaPlayer(libVlc)

        val vlcOut: IVLCVout? = mediaPlayer?.vlcVout
        vlcOut?.setVideoView(surfaceView)
        vlcOut?.attachViews()

        mediaPlayer?.setEventListener { event ->
            runOnUiThread {
                when (event.type) {
                    MediaPlayer.Event.Opening,
                    MediaPlayer.Event.Buffering -> {
                        updateUiState(StreamState.CONNECTING)
                    }

                    MediaPlayer.Event.Playing -> {
                        pendingResumePlayback = true
                        isStreamStarting = false
                        updateUiState(StreamState.PLAYING)
                    }

                    MediaPlayer.Event.Stopped,
                    MediaPlayer.Event.EndReached -> {
                        isStreamStarting = false
                        updateUiState(StreamState.IDLE)
                    }

                    MediaPlayer.Event.EncounteredError -> {
                        pendingResumePlayback = false
                        isStreamStarting = false
                        updateUiState(StreamState.ERROR)
                        Toast.makeText(
                            this,
                            getString(R.string.stream_connection_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
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

        isStreamStarting = true
        updateUiState(StreamState.CONNECTING)

        prefs.edit().putString(PREF_RTSP_URL, rtspUrl).apply()

        val media = Media(libVlc, Uri.parse(rtspUrl)).apply {
            setHWDecoderEnabled(true, false)
            addOption(":network-caching=250")
            addOption(":rtsp-tcp")
        }

        mediaPlayer?.media = media
        media.release()
        mediaPlayer?.play()
        pendingResumePlayback = true
    }

    private fun stopRtspStream() {
        pendingResumePlayback = false
        isStreamStarting = false
        mediaPlayer?.stop()
        updateUiState(StreamState.IDLE)
    }

    override fun onResume() {
        super.onResume()
        if (pendingResumePlayback && mediaPlayer?.isPlaying == false) {
            mediaPlayer?.play()
        }
    }

    override fun onPause() {
        super.onPause()
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            updateUiState(StreamState.IDLE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.stop()
        mediaPlayer?.vlcVout?.detachViews()
        mediaPlayer?.release()
        libVlc?.release()
        mediaPlayer = null
        libVlc = null
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
