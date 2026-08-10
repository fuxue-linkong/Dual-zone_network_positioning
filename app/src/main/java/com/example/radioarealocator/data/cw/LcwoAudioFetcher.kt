package com.example.radioarealocator.data.cw

import com.example.radioarealocator.data.network.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.util.concurrent.TimeUnit

class LcwoAudioFetcher {

    private val client = HttpClientProvider.client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 从 lcwo.net 获取 CW 音频 MP3。
     *
     * @param text 要转换成莫尔斯电码的文本
     * @param wpm 字符速度 (WPM)
     * @param frequency 音调频率 (Hz)
     * @return MP3 音频的字节数组，失败时返回 null
     */
    suspend fun fetchAudio(text: String, wpm: Int, frequency: Int): ByteArray? {
        val safeText = text.trim().replace(" ", "+")
        if (safeText.isEmpty()) return null

        val safeWpm = wpm.coerceIn(5, 150)
        val safeFreq = frequency.coerceIn(250, 990)

        return withContext(Dispatchers.IO) {
            try {
                val url = LCWO_BASE_URL.toHttpUrl().newBuilder()
                    .addQueryParameter("s", safeWpm.toString())
                    .addQueryParameter("e", "0")
                    .addQueryParameter("f", safeFreq.toString())
                    .addQueryParameter("t", safeText)
                    .build()
                    .toString()

                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body.bytes()
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    companion object {
        private const val LCWO_BASE_URL = "https://cgi2.lcwo.net/cgi-bin/cw.mp3"
    }
}
