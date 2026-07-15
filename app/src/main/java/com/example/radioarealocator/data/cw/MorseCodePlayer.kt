package com.example.radioarealocator.data.cw

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.sin

class MorseCodePlayer {
    private val lock = Any()
    @Volatile private var isPlaying = false
    @Volatile private var isPaused = false
    private var audioTrack: AudioTrack? = null
    private val mainHandler = Handler(Looper.getMainLooper())

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
                val dotDuration = 1200.0 / wpm
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

                completedNormally = true
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isPlaying = false
                if (!completedNormally) {
                    try {
                        mainHandler.post { onComplete() }
                    } catch (_: Exception) {}
                } else {
                    mainHandler.post { onComplete() }
                }
                synchronized(lock) {
                    audioTrack?.release()
                    audioTrack = null
                }
            }
        }.start()
    }

    private fun playTone(durationMs: Int, frequency: Int, sampleRate: Int) {
        if (!isPlaying) return

        val numSamples = durationMs * sampleRate / 1000
        val samples = ShortArray(numSamples)
        val fadeInSamples = (sampleRate * 0.005).toInt().coerceAtMost(numSamples / 2)
        val fadeOutSamples = fadeInSamples

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            var amplitude = sin(2.0 * Math.PI * frequency * t)
            if (i < fadeInSamples) {
                amplitude *= i.toDouble() / fadeInSamples
            } else if (i >= numSamples - fadeOutSamples) {
                amplitude *= (numSamples - 1 - i).toDouble() / fadeOutSamples
            }
            samples[i] = (amplitude * Short.MAX_VALUE).toInt().toShort()
        }

        synchronized(lock) {
            if (!isPlaying) return
            audioTrack?.write(samples, 0, samples.size)
        }
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
