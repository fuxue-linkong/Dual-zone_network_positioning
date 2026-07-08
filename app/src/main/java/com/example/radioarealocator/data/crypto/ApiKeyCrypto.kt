package com.example.radioarealocator.data.crypto

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * API Key AES-GCM 加密/解密工具。
 *
 * - 算法：AES/GCM/NoPadding（256 位密钥，128 位认证标签）
 * - IV：12 字节随机生成，附加在密文前
 * - 输出格式：Base64(IV + 密文 + 认证标签)
 *
 * 用途：保护存储在 BuildConfig 中的 API Key，防止反编译后明文泄露。
 * 明文 key 仅存在于 local.properties（不进 git），构建时加密后注入 BuildConfig，
 * 运行时通过 [decrypt] 还原。
 *
 * 注意：MASTER_KEY 硬编码在代码中，反编译仍可获取。
 * 进一步的安全方案是将 MASTER_KEY 迁移到 NDK/C++ 层。
 */
object ApiKeyCrypto {

    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH_BITS = 128

    /**
     * AES-256 主密钥（32 字节）。用于加密/解密 API Key。
     * 构建时（build.gradle.kts）和运行时（本文件）使用同一密钥。
     */
    private val MASTER_KEY: ByteArray = byteArrayOf(
        0x97.toByte(), 0x5F.toByte(), 0xAE.toByte(), 0x7F.toByte(),
        0xBB.toByte(), 0x1A.toByte(), 0xE7.toByte(), 0xC4.toByte(),
        0xB2.toByte(), 0x72.toByte(), 0x6D.toByte(), 0xFC.toByte(),
        0xB3.toByte(), 0xF5.toByte(), 0xF6.toByte(), 0xE2.toByte(),
        0xB3.toByte(), 0x9C.toByte(), 0x8C.toByte(), 0xCE.toByte(),
        0xDD.toByte(), 0x1C.toByte(), 0xF8.toByte(), 0x1F.toByte(),
        0xF9.toByte(), 0x12.toByte(), 0x14.toByte(), 0xE6.toByte(),
        0x8D.toByte(), 0xD5.toByte(), 0x72.toByte(), 0x60.toByte()
    )

    /**
     * 解密 BuildConfig 中的加密 API Key。
     *
     * @param encrypted Base64 编码的密文（IV + 密文 + 认证标签）
     * @return 原始 API Key 明文，解密失败返回空字符串
     */
    fun decrypt(encrypted: String): String {
        if (encrypted.isBlank()) return ""
        return try {
            val combined = Base64.decode(encrypted, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH) return ""

            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val cipherText = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(MASTER_KEY, "AES")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

            val plainBytes = cipher.doFinal(cipherText)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}
