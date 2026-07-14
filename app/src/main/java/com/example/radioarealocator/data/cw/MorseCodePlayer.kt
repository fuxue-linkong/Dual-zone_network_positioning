package com.example.radioarealocator.data.cw

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.sin

class MorseCodePlayer {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var isPaused = false

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

                audioTrack = AudioTrack.Builder()
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

                audioTrack?.play()

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
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isPlaying = false
                audioTrack?.release()
                audioTrack = null
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

        audioTrack?.write(samples, 0, samples.size)
    }

    fun pause() {
        isPaused = true
    }

    fun resume() {
        isPaused = false
    }

    fun stop() {
        isPlaying = false
        isPaused = false
        audioTrack?.release()
        audioTrack = null
    }

    fun isPlaying(): Boolean = isPlaying
    fun isPaused(): Boolean = isPaused
}