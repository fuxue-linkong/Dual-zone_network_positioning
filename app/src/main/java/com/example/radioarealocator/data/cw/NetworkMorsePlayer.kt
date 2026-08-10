package com.example.radioarealocator.data.cw

import android.content.Context
import android.media.MediaPlayer
import java.io.File

class NetworkMorsePlayer(private val context: Context) {
    @Volatile private var playbackActive = false
    @Volatile private var playbackPaused = false
    private var mediaPlayer: MediaPlayer? = null
    private var tempFile: File? = null

    fun playAudio(audioData: ByteArray, onComplete: () -> Unit) {
        if (playbackActive) return
        playbackActive = true
        playbackPaused = false

        Thread {
            try {
                tempFile = File.createTempFile("cw_", ".mp3", context.cacheDir)
                tempFile?.writeBytes(audioData)

                val mp = MediaPlayer().apply {
                    setDataSource(tempFile!!.absolutePath)
                    setOnCompletionListener {
                        playbackActive = false
                        cleanup()
                        onComplete()
                    }
                    setOnErrorListener { _, _, _ ->
                        playbackActive = false
                        cleanup()
                        try { onComplete() } catch (_: Exception) {}
                        true
                    }
                    prepare()
                    start()
                }
                mediaPlayer = mp
            } catch (e: Exception) {
                e.printStackTrace()
                playbackActive = false
                cleanup()
                try { onComplete() } catch (_: Exception) {}
            }
        }.start()
    }

    fun pause() {
        playbackPaused = true
        try { mediaPlayer?.pause() } catch (_: Exception) {}
    }

    fun resume() {
        playbackPaused = false
        try { mediaPlayer?.start() } catch (_: Exception) {}
    }

    fun stop() {
        playbackActive = false
        playbackPaused = false
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {}
        cleanup()
    }

    fun isPlaying(): Boolean = playbackActive
    fun isPaused(): Boolean = playbackPaused

    private fun cleanup() {
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        try {
            tempFile?.delete()
        } catch (_: Exception) {}
        tempFile = null
    }
}
