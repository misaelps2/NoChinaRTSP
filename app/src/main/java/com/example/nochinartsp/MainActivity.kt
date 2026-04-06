package com.example.nochinartsp

import android.net.Uri
import android.os.Bundle
import android.view.SurfaceView
import android.widget.Button
import android.widget.EditText
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

    private var pendingResumePlayback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rtspEditText = findViewById(R.id.rtspEditText)
        surfaceView = findViewById(R.id.videoSurface)
        val openCameraButton: Button = findViewById(R.id.openCameraButton)

        openCameraButton.setOnClickListener {
            startRtspStream()
        }
    }

    private fun ensurePlayerInitialized() {
        if (libVlc != null && mediaPlayer != null) return

        val options = arrayListOf(
            "--network-caching=150",
            "--rtsp-tcp"
        )

        libVlc = LibVLC(this, options)
        mediaPlayer = MediaPlayer(libVlc)

        val vlcOut: IVLCVout? = mediaPlayer?.vlcVout
        vlcOut?.setVideoView(surfaceView)
        vlcOut?.attachViews()

        mediaPlayer?.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.EncounteredError,
                MediaPlayer.Event.EndReached -> runOnUiThread {
                    Toast.makeText(
                        this,
                        getString(R.string.stream_connection_failed),
                        Toast.LENGTH_SHORT
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

        val media = Media(libVlc, Uri.parse(rtspUrl)).apply {
            setHWDecoderEnabled(true, false)
            addOption(":network-caching=150")
            addOption(":rtsp-tcp")
        }

        mediaPlayer?.media = media
        media.release()
        mediaPlayer?.play()
        pendingResumePlayback = true
    }

    override fun onResume() {
        super.onResume()
        if (pendingResumePlayback == true && mediaPlayer?.isPlaying == false) {
            mediaPlayer?.play()
        }
    }

    override fun onPause() {
        super.onPause()
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
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
}
