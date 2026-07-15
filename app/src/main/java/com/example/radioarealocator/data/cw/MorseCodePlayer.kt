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
        synchronized(lock) {
            if (isPlaying) return
            isPlaying = true
            isPaused = false
        }

        val safeWpm = wpm.coerceIn(1, 100)
        val safeFrequency = frequency.coerceIn(100, 4000)

        Thread {
            var completedNormally = false
            try {
                val dotDuration = 1200.0 / safeWpm // 毫秒
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

                    while (isPaused && isPlaying) {
                        Thread.sleep(100)
                    }
                    if (!isPlaying) break

                    when (char) {
                        '.' -> playTone(dotDuration.toInt(), safeFrequency, sampleRate)
                        '-' -> playTone(dashDuration.toInt(), safeFrequency, sampleRate)
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
                onComplete()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isPlaying = false
                if (!completedNormally) {
                    try { onComplete() } catch (_: Exception) {}
                }
                // AudioTrack 仅由播放线程自身释放，避免与 stop() 竞态导致 use-after-release
                synchronized(lock) {
                    try { audioTrack?.release() } catch (_: Exception) {}
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
        if (!isPlaying) return
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
        // 不在此处 release：release 仅由播放线程在 finally 中执行，
        // 这里通过 pause + flush 让阻塞中的 write() 尽快返回
        synchronized(lock) {
            try {
                audioTrack?.pause()
                audioTrack?.flush()
            } catch (_: IllegalStateException) {
            }
        }
    }

    fun isPlaying(): Boolean = isPlaying
    fun isPaused(): Boolean = isPaused
}
