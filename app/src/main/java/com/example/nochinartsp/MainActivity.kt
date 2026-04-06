package com.example.nochinartsp

import android.net.Uri
import android.os.Bundle
import android.view.SurfaceView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.videolan.libvlc.IVLCVout
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

class MainActivity : AppCompatActivity() {

    private var libVlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null

    private lateinit var surfaceView: SurfaceView
    private lateinit var rtspEditText: EditText
    private lateinit var statusTextView: TextView

    private var lastRtspUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rtspEditText = findViewById(R.id.rtspEditText)
        surfaceView = findViewById(R.id.videoSurface)
        statusTextView = findViewById(R.id.statusTextView)
        val openCameraButton: Button = findViewById(R.id.openCameraButton)

        openCameraButton.setOnClickListener {
            startRtspStream()
        }
    }

    private fun ensurePlayerInitialized() {
        if (libVlc != null && mediaPlayer != null) return

        libVlc = LibVLC(
            this,
            arrayListOf(
                "--network-caching=300",
                "--rtsp-tcp"
            )
        )
        mediaPlayer = MediaPlayer(libVlc)

        val videoOut: IVLCVout = mediaPlayer?.vlcVout ?: return
        videoOut.setVideoView(surfaceView)
        videoOut.attachViews()

        mediaPlayer?.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening -> updateStatus(getString(R.string.status_connecting))
                MediaPlayer.Event.Playing -> updateStatus(getString(R.string.status_playing))
                MediaPlayer.Event.Buffering -> updateStatus(getString(R.string.status_buffering))
                MediaPlayer.Event.Paused -> updateStatus(getString(R.string.status_paused))
                MediaPlayer.Event.Stopped -> updateStatus(getString(R.string.status_stopped))
                MediaPlayer.Event.EndReached,
                MediaPlayer.Event.EncounteredError -> runOnUiThread {
                    updateStatus(getString(R.string.status_error))
                    Toast.makeText(
                        this,
                        getString(R.string.stream_connection_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun startRtspStream() {
        val rtspUrl = rtspEditText.text.toString().trim()
        if (rtspUrl.isBlank()) {
            Toast.makeText(this, getString(R.string.enter_rtsp_url), Toast.LENGTH_SHORT).show()
            return
        }

        ensurePlayerInitialized()

        val currentLibVlc = libVlc ?: run {
            Toast.makeText(this, getString(R.string.stream_connection_failed), Toast.LENGTH_SHORT).show()
            return
        }

        val media = Media(currentLibVlc, Uri.parse(rtspUrl)).apply {
            setHWDecoderEnabled(true, true)
            addOption(":network-caching=300")
            addOption(":rtsp-tcp")
            addOption(":clock-jitter=0")
            addOption(":clock-synchro=0")
        }

        mediaPlayer?.stop()
        mediaPlayer?.media = media
        media.release()

        mediaPlayer?.play()
        lastRtspUrl = rtspUrl
        updateStatus(getString(R.string.status_connecting))
    }

    private fun updateStatus(text: String) {
        runOnUiThread {
            statusTextView.text = text
        }
    }

    override fun onResume() {
        super.onResume()
        if (!lastRtspUrl.isNullOrBlank() && mediaPlayer?.isPlaying == false) {
            mediaPlayer?.play()
        }
    }

    override fun onPause() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
        }
        super.onPause()
    }

    override fun onDestroy() {
        mediaPlayer?.stop()
        mediaPlayer?.vlcVout?.detachViews()
        mediaPlayer?.release()
        libVlc?.release()
        mediaPlayer = null
        libVlc = null
        super.onDestroy()
    }
}
