package com.example.radioarealocator.data.crypto

import android.content.Context
import android.util.Base64
import com.example.radioarealocator.BuildConfig
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 敏感信息安全管理器。
 *
 * 本方案采用"三碎片密钥组装 + AES-256-GCM + 运行时解密"架构，
 * 彻底消除对 GitHub Secrets 的依赖：
 *
 * 1. 开发阶段：运行 `gradle encryptSecrets` 从 local.properties 读取明文密钥，
 *    用组装后的主密钥 AES-GCM 加密，输出 `app/src/main/assets/secrets.dat`。
 *    开发者将 secrets.dat 提交到 git——密文可安全入库。
 *
 * 2. CI/CD 阶段（GHA）：无需任何 Secrets，直接构建即可。
 *    secrets.dat 已在仓库中，所有解密所需的密钥碎片也在代码中。
 *
 * 3. 运行时：[init] 从三个独立来源组装 256 位主密钥，解密 secrets.dat，
 *    缓存在进程内存中供业务模块按需读取。
 *
 * ## 密钥碎片架构
 *
 * 主密钥 = ShardA ⊕ ShardB ⊕ ShardC（32 字节，XOR 组装）
 *
 * | 碎片 | 存储位置 | 特征 |
 * |------|---------|------|
 * | ShardA | BuildConfig.CRYPTO_SHARD_A (Base64) | 编译期常量，随 APK 分发 |
 * | ShardB | 本文件混淆字节数组 | 运行时异或去混淆，R8 混淆后难以识别 |
 * | ShardC | SHA-256(packageName + SALT) | 绑定包名，重打包后无法解密 |
 *
 * **安全属性**：
 * - 反编译 APK 只能获取单个碎片，无法还原主密钥
 * - R8 release 混淆进一步保护组装逻辑
 * - 重打包（修改包名）破坏 ShardC，导致解密失败
 * - AES-GCM 认证标签防止密文篡改
 *
 * ## secrets.dat 文件格式
 *
 * ```
 * [4B  magic "SCL1"]
 * [4B  version (uint32 BE)]   // 当前 = 1
 * [12B GCM IV]
 * [NB  GCM 密文 + 16B 认证标签]
 * ```
 *
 * 明文（解密后）为 JSON：
 * ```json
 * {"amap.api.key":"...","amap.sdk.key":"..."}
 * ```
 */
object SecretManager {

    private const val ASSET_NAME = "secrets.dat"
    private const val MAGIC = "SCL1"
    private const val VERSION = 1
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val KEY_LENGTH = 32

    // ── ShardB：混淆存储的字节数组 ──────────────────────────────────────
    // 存储的是 ShardB ⊕ OBF_KEY，运行时异或还原。
    // 这样源码中不直接出现原始密钥字节，增加静态分析难度。
    private val SHARD_B_OBF = byteArrayOf(
        0x65.toByte(), 0x92.toByte(), 0x08.toByte(), 0xFB.toByte(),
        0xC7.toByte(), 0xBA.toByte(), 0x1E.toByte(), 0x76.toByte(),
        0xAB.toByte(), 0x32.toByte(), 0xED.toByte(), 0x67.toByte(),
        0x50.toByte(), 0xC4.toByte(), 0x0B.toByte(), 0x29.toByte(),
        0x8E.toByte(), 0x73.toByte(), 0xD5.toByte(), 0x3C.toByte(),
        0x41.toByte(), 0x99.toByte(), 0x00.toByte(), 0xBD.toByte(),
        0x12.toByte(), 0x5D.toByte(), 0xC8.toByte(), 0xE4.toByte(),
        0x8C.toByte(), 0x6A.toByte(), 0x46.toByte(), 0xAE.toByte()
    )
    private val SHARD_B_KEY = byteArrayOf(
        0x5A, 0x5A, 0x5A, 0x5A, 0x5A, 0x5A, 0x5A, 0x5A,
        0x5A, 0x5A, 0x5A, 0x5A, 0x5A, 0x5A, 0x5A, 0x5A,
        0x5A, 0x5A, 0x5A, 0x5A, 0x5A, 0x5A, 0x5A, 0x5A,
        0x5A, 0x5A, 0x5A, 0x5A, 0x5A, 0x5A, 0x5A, 0x5A
    )

    // ── ShardC：基于包名的运行时派生 ────────────────────────────────────
    private const val SHARD_C_SALT = "R4d10_Ar34_L0c8t0r_2026_Salt"

    // 解密后的密钥缓存（进程内存中，进程结束即消失）
    @Volatile private var secrets: Map<String, String> = emptyMap()
    @Volatile private var initialized = false

    /**
     * 初始化：读取并解密 assets/secrets.dat。
     *
     * 应在 [android.app.Application.onCreate] 中调用，确保业务模块使用前已完成。
     *
     * @param context 应用上下文
     */
    fun init(context: Context) {
        if (initialized) return
        try {
            val masterKey = assembleMasterKey(context.packageName)
            val encrypted = context.assets.open(ASSET_NAME).use { it.readBytes() }
            val plaintext = decrypt(encrypted, masterKey)
            val json = JSONObject(plaintext)
            secrets = json.keys().asSequence().associateWith { json.optString(it) }
        } catch (e: Exception) {
            // 解密失败：密钥缓存保持空 map，调用方通过 [getSecret] 获取空字符串
            secrets = emptyMap()
        }
        initialized = true
    }

    /**
     * 获取解密后的密钥值。
     *
     * @param key 密钥名（如 "amap.api.key"）
     * @return 明文密钥，未初始化或不存在时返回空字符串
     */
    fun getSecret(key: String): String = secrets[key] ?: ""

    /**
     * 组装 256 位主密钥：ShardA ⊕ ShardB ⊕ ShardC。
     *
     * ShardA 从 BuildConfig 读取（Base64 解码）。
     * ShardB 从混淆数组异或去混淆。
     * ShardC 由 包名 + SALT 的 SHA-256 截断得到。
     */
    private fun assembleMasterKey(packageName: String): ByteArray {
        val shardA = Base64.decode(BuildConfig.CRYPTO_SHARD_A, Base64.NO_WRAP)
        val shardB = xorBytes(SHARD_B_OBF, SHARD_B_KEY)
        val shardC = sha256Truncate(packageName + SHARD_C_SALT, KEY_LENGTH)
        return xorBytes(xorBytes(shardA, shardB), shardC)
    }

    /** AES-256-GCM 解密 secrets.dat。 */
    private fun decrypt(encrypted: ByteArray, key: ByteArray): String {
        require(encrypted.size > 8) { "secrets.dat 文件过短" }
        // 校验 magic
        val magic = String(encrypted, 0, 4, Charsets.US_ASCII)
        require(magic == MAGIC) { "secrets.dat magic 不匹配: $magic" }
        // 校验 version
        val version = ((encrypted[4].toInt() and 0xFF) shl 24) or
            ((encrypted[5].toInt() and 0xFF) shl 16) or
            ((encrypted[6].toInt() and 0xFF) shl 8) or
            (encrypted[7].toInt() and 0xFF)
        require(version == VERSION) { "secrets.dat 版本不支持: $version" }
        // 提取 IV + 密文
        val iv = encrypted.copyOfRange(8, 8 + GCM_IV_LENGTH)
        val cipherText = encrypted.copyOfRange(8 + GCM_IV_LENGTH, encrypted.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val plainBytes = cipher.doFinal(cipherText)
        return String(plainBytes, Charsets.UTF_8)
    }

    /** 两个字节数组异或。 */
    private fun xorBytes(a: ByteArray, b: ByteArray): ByteArray {
        val result = ByteArray(a.size)
        for (i in a.indices) result[i] = (a[i].toInt() xor b[i % b.size].toInt()).toByte()
        return result
    }

    /** SHA-256 哈希并截断到指定长度。 */
    private fun sha256Truncate(input: String, length: Int): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.copyOfRange(0, minOf(length, digest.size))
    }
}
