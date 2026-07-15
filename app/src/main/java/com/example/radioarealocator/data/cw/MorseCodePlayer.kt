package com.example.radioarealocator.data.cw

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.sin

class MorseCodePlayer {
    private val lock = Any()
    @Volatile private var isPlaying = false
    @Volatile private var isPaused = false
    private var audioTrack: AudioTrack? = null

    fun playMorseCode(
        morseCode: String,
        wpm: Int,
        frequency: Int,
        playMode: PlayMode,
        onComplete: () -> Unit
    ) {
        if (isPlaying) return

        isPlaying = true
        isPaused = false

        Thread {
            var completedNormally = false
            try {
                val dotDuration = 1200.0 / wpm // 毫秒
                val dashDuration = dotDuration * 3
                val symbolGap = dotDuration
                val charGap = dotDuration * 3
                val wordGap = dotDuration * 7

                val sampleRate = 44100
                val bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                synchronized(lock) {
                    audioTrack = track
                    track.play()
                }

                for (char in morseCode) {
                    if (!isPlaying) break

                    while (isPaused) {
                        Thread.sleep(100)
                    }

                    when (char) {
                        '.' -> playTone(dotDuration.toInt(), frequency, sampleRate)
                        '-' -> playTone(dashDuration.toInt(), frequency, sampleRate)
                        ' ' -> Thread.sleep(charGap.toLong())
                        '/' -> Thread.sleep(wordGap.toLong())
                    }

                    if (char != ' ' && char != '/') {
                        Thread.sleep(symbolGap.toLong())
                    }

                    if (playMode == PlayMode.INTERVAL) {
                        Thread.sleep(100)
                    }
                }

                onComplete()
                completedNormally = true
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isPlaying = false
                if (!completedNormally) {
                    try { onComplete() } catch (_: Exception) {}
                }
                synchronized(lock) {
                    audioTrack?.release()
                    audioTrack = null
                }
            }
        }.start()
    }

    private fun playTone(durationMs: Int, frequency: Int, sampleRate: Int) {
        val numSamples = durationMs * sampleRate / 1000
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            samples[i] = (sin(2.0 * Math.PI * frequency * t) * Short.MAX_VALUE).toInt().toShort()
        }

        val track = synchronized(lock) { audioTrack }
        track?.write(samples, 0, samples.size)
    }

    fun pause() {
        isPaused = true
        synchronized(lock) {
            audioTrack?.pause()
        }
    }

    fun resume() {
        isPaused = false
        synchronized(lock) {
            audioTrack?.play()
        }
    }

    fun stop() {
        isPlaying = false
        isPaused = false
        synchronized(lock) {
            audioTrack?.release()
            audioTrack = null
        }
    }

    fun isPlaying(): Boolean = isPlaying
    fun isPaused(): Boolean = isPaused
}
