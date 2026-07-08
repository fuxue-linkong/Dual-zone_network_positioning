# 敏感信息保护方案：三碎片密钥 + AES-256-GCM

> 本文档描述 RadioAreaLocator 应用的客户端加密方案，该方案完全摆脱对 GitHub Secrets 的依赖，
> 在保障敏感信息（高德 API Key、SDK Key）安全的前提下，实现 CI/CD 零密钥配置构建。

---

## 目录

1. [方案概览](#1-方案概览)
2. [算法选择依据](#2-算法选择依据)
3. [架构设计](#3-架构设计)
4. [实现细节](#4-实现细节)
5. [安全考量](#5-安全考量)
6. [使用方法](#6-使用方法)
7. [文件清单](#7-文件清单)
8. [威胁模型与风险分析](#8-威胁模型与风险分析)

---

## 1. 方案概览

### 1.1 设计目标

| 目标 | 描述 |
|------|------|
| **零 Secrets 依赖** | CI/CD 构建无需配置任何 GitHub Secrets，密文直接入库 |
| **开发端加密** | 敏感信息在开发环境加密，密文（`secrets.dat`）安全嵌入 APK assets |
| **运行时解密** | 应用启动时从三个独立来源组装密钥，解密获取明文 |
| **防重打包** | 密钥与包名绑定，APK 被重打包后无法解密 |
| **防篡改** | AES-GCM 认证标签确保密文未被修改 |

### 1.2 与传统方案对比

| 维度 | GitHub Secrets 方案 | 本方案（三碎片加密） |
|------|-------------------|---------------------|
| CI 配置 | 需在仓库设置 Secrets | 无需任何配置 |
| 密钥存储 | Secrets 明文写入 local.properties | 密文 `secrets.dat` 入库 |
| 传输安全 | 依赖 GHA 加密通道 | 密文入库，无传输环节 |
| 反编译风险 | BuildConfig 含密钥 | 密钥分散，需组合三碎片 |
| 重打包防护 | 无 | 包名绑定，自动失效 |

---

## 2. 算法选择依据

### 2.1 对称加密：AES-256-GCM

**选择理由**：

- **AES-256**：NIST 标准对称加密算法，密钥空间 2^256，目前无已知实际攻击方法
- **GCM 模式**（Galois/Counter Mode）：
  - 同时提供**机密性**（加密）和**完整性**（认证标签）
  - 认证标签（128 bit）可检测密文篡改，防止 padding oracle 攻击
  - 无需额外 MAC 算法，单次操作完成加密+认证
  - 硬件加速（AES-NI），性能优异
- **对比 CBC + HMAC**：GCM 将加密和认证合为一步，减少代码复杂度和出错概率

**参数**：
- IV 长度：12 字节（96 bit）—— GCM 推荐值，每次加密随机生成
- 认证标签长度：128 bit（16 字节）—— 最大安全强度
- 密文 = IV 随机性下，相同明文每次加密结果不同

### 2.2 密钥组装：XOR 三碎片

**选择理由**：

- **XOR 运算**：简单且不可逆——拥有任意两个碎片无法推导第三个
- **三碎片分散**：
  - 单点泄露（反编译获取一个碎片）不影响整体安全
  - 需同时获取全部三个碎片才能还原主密钥
- **与 Shamir 秘密共享的对比**：
  - Shamir 需要运行时秘密分享计算，实现复杂
  - XOR 三碎片实现简洁，对移动端性能无影响
  - 对于 3-of-3 场景，XOR 已提供足够的安全属性

### 2.3 哈希派生：SHA-256

**选择理由**：

- **确定性**：相同输入始终产生相同输出，保证开发端与运行时密钥一致
- **抗碰撞**：SHA-256 无已知碰撞攻击
- **截断安全**：取前 32 字节（256 bit）作为密钥碎片，满足 AES-256 密钥长度要求

---

## 3. 架构设计

### 3.1 整体流程

```
┌─────────────────────────────────────────────────────────────┐
│                     开发环境（一次性）                        │
│                                                             │
│  local.properties ──→ encryptSecrets Task ──→ secrets.dat   │
│  (明文 API Key)         (AES-256-GCM)         (密文, 入库)    │
│                                                             │
│  密钥来源：ShardA ⊕ ShardB ⊕ ShardC = MasterKey             │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼ git commit
┌─────────────────────────────────────────────────────────────┐
│                    CI/CD (GitHub Actions)                    │
│                                                             │
│  checkout 仓库 ──→ secrets.dat 已在 assets/ ──→ assembleDebug │
│  无需任何 Secrets 配置                                       │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼ APK 安装
┌─────────────────────────────────────────────────────────────┐
│                    运行时（设备端）                           │
│                                                             │
│  Application.onCreate()                                      │
│    └→ SecretManager.init(context)                           │
│         ├─ ShardA ← BuildConfig.CRYPTO_SHARD_A (Base64)     │
│         ├─ ShardB ← 混淆字节数组 ⊕ OBF_KEY                   │
│         ├─ ShardC ← SHA-256(packageName + SALT)             │
│         ├─ MasterKey = ShardA ⊕ ShardB ⊕ ShardC            │
│         ├─ 读取 assets/secrets.dat                          │
│         └─ AES-GCM 解密 → 明文 JSON → 内存缓存               │
│                                                             │
│  业务模块调用 SecretManager.getSecret("amap.api.key")        │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 三碎片密钥架构

**主密钥 = ShardA ⊕ ShardB ⊕ ShardC（32 字节，XOR 组装）**

| 碎片 | 存储位置 | 生成方式 | 安全特征 |
|------|---------|---------|---------|
| **ShardA** | `BuildConfig.CRYPTO_SHARD_A` | 32 字节随机数，Base64 编码 | 编译期常量，随 APK 分发；R8 混淆后嵌入 BuildConfig 类 |
| **ShardB** | `SecretManager.kt` 中 `SHARD_B_OBF` | 混淆存储：原始值 ⊕ 0x5A×32 | 源码中不直接出现原始字节；R8 混淆后变量名不可读 |
| **ShardC** | 运行时计算 | `SHA-256(packageName + SALT)` 前 32 字节 | 绑定包名，重打包后 ShardC 改变 → 解密失败 |

**安全属性**：
- 反编译只能获取 ShardA（BuildConfig）和 ShardB（混淆数组），但无法获取 ShardC 的 SALT（R8 混淆后字符串常量被内联）
- 即使三个碎片全部被提取，ShardC 的包名绑定确保重打包后密钥失效
- XOR 组装保证：缺少任意一个碎片，主密钥完全不可推导

### 3.3 secrets.dat 文件格式

```
偏移      长度      字段           说明
0         4         magic          "SCL1"（Secrets Crypto Layer v1）
4         4         version        uint32 大端序，当前 = 1
8         12        GCM IV         随机初始化向量
20        N         ciphertext     AES-GCM 密文 + 16 字节认证标签
```

明文（解密后）为 JSON：
```json
{"amap.api.key":"9d99ce60cc6dea925db6c009d9d2a430","amap.sdk.key":"fc8f3b3d2096dd2720e06b467bc857b0"}
```

---

## 4. 实现细节

### 4.1 开发端加密（build.gradle.kts）

加密逻辑定义在 [app/build.gradle.kts](../app/build.gradle.kts) 顶部，包含：

1. **碎片常量定义**：
   - `CRYPTO_SHARD_A_BYTES`：32 字节随机常量
   - `SHARD_B_RAW`：32 字节随机常量（与 SecretManager.kt 中混淆值对应）
   - `SHARD_C_SALT`：派生盐值

2. **`assembleMasterKey()` 函数**：组装 ShardA ⊕ ShardB ⊕ ShardC

3. **`encryptSecretsData(plaintext)` 函数**：
   ```kotlin
   fun encryptSecretsData(plaintext: String): ByteArray {
       val masterKey = assembleMasterKey()
       val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
       val cipher = Cipher.getInstance("AES/GCM/NoPadding")
       cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(masterKey, "AES"), GCMParameterSpec(128, iv))
       val cipherText = cipher.doFinal(plaintext.toByteArray())
       // 拼装 [magic][version][iv][ciphertext] 二进制格式
       ...
   }
   ```

4. **`encryptSecrets` Gradle 任务**：
   - 读取 `local.properties` 中的 `amap.api.key` 和 `amap.sdk.key`
   - 构建 JSON 明文
   - 调用 `encryptSecretsData()` 加密
   - 写入 `app/src/main/assets/secrets.dat`

### 4.2 运行时解密（SecretManager.kt）

核心组件在 [SecretManager.kt](../app/src/main/java/com/example/radioarealocator/data/crypto/SecretManager.kt)：

1. **ShardB 去混淆**：
   ```kotlin
   // SHARD_B_OBF 存储的是 ShardB ⊕ 0x5A，运行时异或还原
   val shardB = xorBytes(SHARD_B_OBF, SHARD_B_KEY)
   ```

2. **ShardC 运行时派生**：
   ```kotlin
   val shardC = sha256Truncate(packageName + SHARD_C_SALT, 32)
   ```
   注意：运行时使用 `context.packageName` 而非常量，确保包名变更自动反映。

3. **密钥组装与解密**：
   ```kotlin
   val masterKey = xorBytes(xorBytes(shardA, shardB), shardC)
   // 校验 magic + version → 提取 IV + 密文 → AES-GCM 解密
   ```

4. **内存缓存**：解密后的密钥以 `Map<String, String>` 缓存在进程内存，进程结束即消失，不持久化到磁盘。

### 4.3 初始化时机

在 [RadioAreaLocatorApplication.kt](../app/src/main/java/com/example/radioarealocator/RadioAreaLocatorApplication.kt) 的 `onCreate()` 中：

```kotlin
override fun onCreate() {
    super.onCreate()
    SecretManager.init(this)           // 第一步：解密密钥
    MapsInitializer.updatePrivacyShow(this, true, true)  // 隐私合规
    MapsInitializer.updatePrivacyAgree(this, true)
    
    // 运行时注入地图 SDK Key（CI 构建时 manifest 为空，靠此覆盖）
    val sdkKey = SecretManager.getSecret("amap.sdk.key")
    if (sdkKey.isNotEmpty()) {
        MapsInitializer::class.java
            .getMethod("setApiKey", String::class.java)
            .invoke(null, sdkKey)
    }
}
```

### 4.4 业务模块使用

天气服务 [WeatherApiService.kt](../app/src/main/java/com/example/radioarealocator/data/weather/WeatherApiService.kt)：

```kotlin
private fun apiKey(): String {
    val key = SecretManager.getSecret("amap.api.key")
    if (key.isEmpty()) {
        throw ApiKeyMissingException("请运行 gradle encryptSecrets 生成 secrets.dat")
    }
    return key
}
```

---

## 5. 安全考量

### 5.1 密钥安全

| 威胁 | 防护措施 |
|------|---------|
| **反编译提取密钥** | 三碎片分散存储，单点泄露无法还原；R8 release 混淆进一步保护 |
| **动态调试 Hook** | 密钥组装逻辑分散在多个方法中，组装后立即用于解密，窗口期短 |
| **内存 dump** | 密钥仅存于进程内存，进程结束即消失；不写入 SharedPreferences/数据库 |
| **MITM 中间人** | 密文已嵌入 APK，无网络传输环节 |

### 5.2 密文完整性

| 威胁 | 防护措施 |
|------|---------|
| **密文篡改** | AES-GCM 128 bit 认证标签，篡改后解密抛出 `AEADBadTagException` |
| **格式伪造** | magic + version 校验，拒绝非 SCL1 格式文件 |
| **重放攻击** | 每次加密使用随机 IV，相同明文产生不同密文 |

### 5.3 防重打包

ShardC = SHA-256(packageName + SALT)。当攻击者重打包并修改 `applicationId` 时：
- 运行时 `context.packageName` 返回新包名
- ShardC 计算结果改变
- 主密钥不匹配 → GCM 认证失败 → 解密失败 → 应用无法运行

这是**防重打包的核心机制**：密钥与包名形成强绑定。

### 5.4 R8 混淆增强

Release 构建启用 R8 混淆 (`isMinifyEnabled = true`)：
- ShardB 的变量名 `SHARD_B_OBF`、`SHARD_B_KEY` 被缩短为不可读名称
- `assembleMasterKey` 方法名被混淆
- 字符串常量 `SHARD_C_SALT` 在字节码中被内联，难以通过字符串搜索定位
- 控制流被打乱，增加逆向分析难度

### 5.5 已知局限

本方案是**纵深防御层**，不提供与硬件级安全（TEE/SE）等同的保护：

1. **高级逆向**：经验丰富的攻击者长期分析仍可能提取全部三个碎片
2. **Root 设备**：在 Root 设备上可 Hook `SecretManager.getSecret()` 直接获取明文
3. **适用场景**：适用于 API Key 等中低敏感度数据的保护，不适合私钥等高价值密钥

**缓解建议**（如需更高安全级别）：
- 迁移至 Android Keystore 硬件级密钥保护
- 服务端代理 API 调用，密钥不随 APK 分发
- 使用 V2/V3 签名 + Play App Signing 防止重打包

---

## 6. 使用方法

### 6.1 首次配置（开发者）

1. 在项目根目录创建 `local.properties`，填入明文密钥：
   ```properties
   amap.api.key=你的高德API密钥
   amap.sdk.key=你的高德SDK密钥
   ```

2. 运行加密任务：
   ```bash
   gradle encryptSecrets
   ```
   输出：
   ```
   secrets.dat generated: .../app/src/main/assets/secrets.dat (137 bytes)
   ```

3. 将 `secrets.dat` 提交到 git：
   ```bash
   git add app/src/main/assets/secrets.dat
   git commit -m "chore: 加密更新 secrets.dat"
   ```

### 6.2 更新密钥

当 API Key 变更时：

1. 修改 `local.properties` 中的明文密钥
2. 重新运行 `gradle encryptSecrets`
3. 提交新的 `secrets.dat`

### 6.3 CI/CD 构建

**无需任何配置**。CI 检出代码后即可直接构建：

```bash
gradle assembleDebug     # Debug APK
gradle assembleRelease   # Release APK（需签名配置）
```

`secrets.dat` 已在仓库中，所有解密碎片已在代码中。

### 6.4 修改加密参数

如需更换碎片值（密钥轮换）：

1. 生成新的 32 字节随机数作为 ShardA，更新 `build.gradle.kts` 中的 `CRYPTO_SHARD_A_BYTES`
2. 生成新的 32 字节随机数作为 ShardB，同时更新 `build.gradle.kts` 的 `SHARD_B_RAW` 和 `SecretManager.kt` 的 `SHARD_B_OBF`（= ShardB ⊕ 0x5A×32）
3. 如需更换 ShardC 盐值，同时更新 `build.gradle.kts` 的 `SHARD_C_SALT` 和 `SecretManager.kt` 的 `SHARD_C_SALT`
4. 重新运行 `gradle encryptSecrets` 生成新密文
5. 提交所有变更

---

## 7. 文件清单

| 文件 | 作用 |
|------|------|
| [app/build.gradle.kts](../app/build.gradle.kts) | 碎片常量定义、加密函数、`encryptSecrets` 任务 |
| [app/src/main/java/.../crypto/SecretManager.kt](../app/src/main/java/com/example/radioarealocator/data/crypto/SecretManager.kt) | 运行时密钥组装与解密 |
| [app/src/main/assets/secrets.dat](../app/src/main/assets/secrets.dat) | 加密后的密文文件（二进制，入库） |
| [app/src/main/java/.../RadioAreaLocatorApplication.kt](../app/src/main/java/com/example/radioarealocator/RadioAreaLocatorApplication.kt) | 应用入口，初始化 SecretManager + SDK Key 注入 |
| [app/src/main/java/.../weather/WeatherApiService.kt](../app/src/main/java/com/example/radioarealocator/data/weather/WeatherApiService.kt) | 天气 API 调用，使用 `SecretManager.getSecret()` |
| [.github/workflows/ci.yml](../.github/workflows/ci.yml) | CI 工作流，已移除 Secrets 注入步骤 |
| [.github/workflows/release.yml](../.github/workflows/release.yml) | Release 工作流，已移除 Secrets 注入步骤 |

---

## 8. 威胁模型与风险分析

### 8.1 威胁模型

```
攻击者能力分级：

Level 1 - 普通用户
  └ 无法获取 APK 源码，仅能安装运行
  └ 防护：✅ 完全安全

Level 2 - 脚本小子
  └ 能反编译 APK（jadx/apktool）
  └ 能看到 BuildConfig 和混淆代码
  └ 防护：✅ 三碎片分散，无法轻易组装

Level 3 - 逆向工程师
  └ 能动态调试（Frida/Xposed）
  └ 能 Hook 运行时方法
  └ 防护：⚠️ 可在组装后 Hook 获取明文，但需专业技术

Level 4 - 高级攻击者
  └ 能完整逆向 + 动态分析
  └ 长期投入资源
  └ 防护：❌ 理论上可提取全部密钥
```

### 8.2 风险缓解措施

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| 碎片被逆向提取 | 中 | 高 | R8 混淆 + 三碎片分散 + 包名绑定 |
| 密钥在内存被 dump | 低 | 高 | 进程内存缓存，不持久化；窗口期短 |
| local.properties 泄露 | 低 | 中 | .gitignore 已排除，仅开发环境存在 |
| 密文被篡改 | 低 | 中 | AES-GCM 认证标签 |

### 8.3 安全标准符合性

本方案参照以下安全实践：

- **OWASP MASVS（Mobile Application Security Verification Standard）**：
  - MASVS-CRYPTO-1：使用标准加密算法（AES-256-GCM）
  - MASVS-STORAGE-1：敏感数据不持久化到磁盘
  - MASVS-RESILIENCE-1：反编译防护（碎片分散 + R8 混淆）

- **NIST SP 800-38D**（GCM 模式推荐）：
  - IV 长度 96 bit（12 字节）
  - 认证标签长度 128 bit

- **数据最小化原则**：
  - 仅加密必要的 API Key，不存储多余敏感信息
  - 明文仅在内存中存活，进程结束即清除

---

## 附录：碎片一致性验证

确保 `build.gradle.kts` 和 `SecretManager.kt` 中的碎片一致：

```kotlin
// 验证脚本（可在 Kotlin REPL 中运行）
// ShardB: build.gradle.kts 的 SHARD_B_RAW == SecretManager.kt 的 SHARD_B_OBF ⊕ SHARD_B_KEY

val SHARD_B_RAW = byteArrayOf(0x3F, 0xC8, 0x52, 0xA1, ...)  // build.gradle.kts
val SHARD_B_OBF = byteArrayOf(0x65, 0x92, 0x08, 0xFB, ...)  // SecretManager.kt
val SHARD_B_KEY = byteArrayOf(0x5A, 0x5A, 0x5A, 0x5A, ...)  // 0x5A × 32

// 验证：SHARD_B_RAW[i] == SHARD_B_OBF[i] ^ SHARD_B_KEY[i]
for (i in SHARD_B_RAW.indices) {
    assert(SHARD_B_RAW[i] == (SHARD_B_OBF[i] xor SHARD_B_KEY[i]).toByte())
    { "ShardB mismatch at index $i" }
}
println("ShardB consistency verified ✓")
```

> 如果修改了任一碎片的值，必须同步更新两处定义并重新运行 `gradle encryptSecrets` 生成新密文。
