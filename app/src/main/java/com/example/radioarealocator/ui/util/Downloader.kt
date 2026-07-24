package com.example.radioarealocator.ui.util

import com.example.radioarealocator.radioApp
import okhttp3.Request
import java.io.File

/** GitHub Releases API：获取最新 release 信息 */
private const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/fuxue-linkong/Dual-zone_network_positioning/releases/latest"

/**
 * 查询 GitHub 最新 release 信息。
 *
 * versionCode 从 release body 中提取（release.yml 写入格式：`Version: name (code)`），
 * 与 [com.example.radioarealocator.BuildConfig.VERSION_CODE] 比较判断是否有更新。
 *
 * @return 最新版本信息；网络失败或无 release 时返回默认空值
 */
fun checkNewVersion(): LatestVersionInfo {
    if (!isNetworkAvailable(radioApp)) return LatestVersionInfo()
    val defaultValue = LatestVersionInfo()
    runCatching {
        radioApp.okhttpClient.newCall(Request.Builder().url(LATEST_RELEASE_URL).build()).execute()
            .use { response ->
                if (!response.isSuccessful) return defaultValue
                val body = response.body.string()
                val json = org.json.JSONObject(body)
                val changelog = json.optString("body")
                val tagName = json.optString("tag_name")
                val versionName = tagName.removePrefix("v")

                // 从 body 提取 versionCode（格式：Version: 1.2.0-beta.1 (10)）
                val versionCodeRegex = Regex("\\((\\d+)\\)")
                val versionCode = versionCodeRegex.find(changelog)?.groupValues?.get(1)?.toIntOrNull() ?: 0

                // 查找 APK 资源
                val assets = json.optJSONArray("assets") ?: return defaultValue
                var downloadUrl = ""
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }

                return LatestVersionInfo(
                    versionCode = versionCode,
                    versionName = versionName,
                    downloadUrl = downloadUrl,
                    changelog = changelog,
                )
            }
    }
    return defaultValue
}

/**
 * 下载 APK 到指定文件，回调下载进度。
 *
 * @param url 下载地址（GitHub release asset 的 browser_download_url）
 * @param destFile 目标文件（通常位于 cacheDir，由 FileProvider 共享）
 * @param onProgress 进度回调，参数为 0-100 的整数；在 IO 线程触发
 * @return 下载成功返回 true，失败返回 false
 */
fun downloadApk(url: String, destFile: File, onProgress: (Int) -> Unit): Boolean {
    return runCatching {
        radioApp.okhttpClient.newCall(Request.Builder().url(url).build()).execute()
            .use { response ->
                if (!response.isSuccessful) return false
                val body = response.body ?: return false
                val totalBytes = body.contentLength()
                destFile.parentFile?.mkdirs()
                var lastReported = -1
                body.byteStream().use { input ->
                    destFile.outputStream().use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var readBytes = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            readBytes += read
                            if (totalBytes > 0) {
                                val progress = (readBytes * 100 / totalBytes).toInt().coerceIn(0, 100)
                                if (progress != lastReported) {
                                    lastReported = progress
                                    onProgress(progress)
                                }
                            }
                        }
                    }
                }
                true
            }
    }.getOrDefault(false)
}
