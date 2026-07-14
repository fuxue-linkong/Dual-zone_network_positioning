package com.example.radioarealocator.data.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 共享 OkHttpClient 单例。
 *
 * 优化点：
 * - 所有网络服务共享同一个连接池和线程池，减少资源浪费
 * - 统一超时配置，避免每个服务重复创建 Builder
 * - 连接池复用减少 TCP 握手开销
 */
object HttpClientProvider {

    /**
     * 共享的 OkHttpClient 实例。
     * 连接池：5 个空闲连接，存活 5 分钟。
     * 超时：连接 15s，读取 30s，写入 30s。
     */
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
        .build()
}
