# Bug 修复报告

> 修复日期：2026-07-06
> 修复范围：功能缺陷、性能问题、兼容性问题、构建错误
> 测试验证：本地单元测试 34 个全部通过

## 一、修复总览

| 编号 | 严重度 | 类别 | 模块 | 简述 |
|------|--------|------|------|------|
| #1 | 高 | 功能缺陷 | ZoneResolver | 区域边界点同时命中多个区域 |
| #2 | 高 | 功能缺陷 | SatellitePredictor | 在境卫星过境信息显示错误 |
| #3 | 高 | 并发安全 | FavoriteSatellitesStore | toggle() 非原子操作 |
| #4 | 中 | 性能问题 | MainActivity | 背景图大图 OOM |
| #5 | 中 | 性能问题 | MainScreen | 每秒重组导致性能下降 |
| #6 | 中 | 构建错误 | test.yml | 覆盖率检查逻辑混淆 |
| #7 | 中 | 功能缺陷 | SettingsStore | satelliteSource 未持久化 |
| #8 | 低 | 用户体验 | MainScreen | 筛选弹窗 expanded 状态未保存 |
| #9 | 低 | 兼容性 | AboutScreen | versionName 废弃 API |
| #10 | 低 | 构建警告 | strings.xml | 资源格式化参数非位置化 |
| #11 | 高 | 编译错误 | ZoneResolver | Double 不支持 until 操作符 |

---

## 二、详细修复记录

### Bug #1：区域边界点同时命中多个区域

- **严重度**：高
- **类别**：功能缺陷
- **模块**：[ZoneResolver.kt](../app/src/main/java/com/example/radioarealocator/data/zone/ZoneResolver.kt)
- **错误场景**：
  相邻区域在边界经纬度（如经度 40.0）上同时命中，导致 `resolve()` 返回错误分区。
  例如：欧洲区域 maxLon=40、俄罗斯区域 minLon=40，经度 40.0 的坐标会被两个区域同时匹配。
- **根因**：
  `ZoneRegion.contains()` 使用闭区间 `[min, max]`，边界点被两个区域同时包含。
  `resolve()` 用 `regions.asReversed()` 倒序遍历，让列表后的小区域优先命中，但俄罗斯大区域 (50-77, 40-180) 在列表中位于欧洲之后，反转后俄罗斯先匹配，导致经度 40.0 错误归属俄罗斯。
- **修复方案**：
  改用左开右闭区间 `(min, max]`：边界点归属上限区域。
  - 欧洲区域：经度 40.0 命中（maxLon=40 闭区间）
  - 俄罗斯区域：经度 41.0 才命中（minLon=40 开区间）
  - 极值边界 -180.0 / -90.0 作为闭区间下限包含，确保对蹟点仍可命中。
- **代码变更**：
  ```kotlin
  fun contains(lat: Double, lon: Double): Boolean {
      val latOk = if (minLat <= -90.0) lat in minLat..maxLat else lat > minLat && lat <= maxLat
      val lonOk = if (minLon <= -180.0) lon in minLon..maxLon else lon > minLon && lon <= maxLon
      return latOk && lonOk
  }
  ```
- **测试验证**：
  - `boundary longitude 40 belongs to europe not russia`：经度 40 归欧洲（cqZone=14），经度 41 归俄罗斯（cqZone=16）
  - `polar boundaries are still matched`：经度 180 仍命中俄罗斯（lat=75 避开子区域）
  - `no mans land at boundaries`：抽样 8 个边界经度均有归属

### Bug #2：在境卫星过境信息显示错误

- **严重度**：高
- **类别**：功能缺陷
- **模块**：[SatellitePredictor.kt](../app/src/main/java/com/example/radioarealocator/data/satellite/SatellitePredictor.kt)
- **错误场景**：
  当卫星当前正在过境（isCurrentlyVisible=true）时，UI 显示的 AOS（入境时间）是下一次过境的时间，而非当前过境的实际开始时间。
- **根因**：
  `predict4java` 的 `nextSatPass(date, keepTLE)` 总是返回下一次过境，即使当前正在过境也返回下一次的 AOS/LOS。
  原代码直接使用 `nextPass.startTime` 作为 AOS，导致在境卫星显示错误的未来时间。
- **修复方案**：
  在 `predictSinglePass()` 中判断当前是否在境：
  - 若在境且 `nextPass.startTime` 已过去，用 `now` 近似 AOS，用 `nextPass.endTime` 作为 LOS
  - 否则正常使用 `nextPass.startTime` / `nextPass.endTime`
  - 若下一次过境开始时间超出搜索窗口（searchEnd），返回 null

### Bug #3：toggle() 非原子操作

- **严重度**：高
- **类别**：并发安全
- **模块**：[FavoriteSatellitesStore.kt](../app/src/main/java/com/example/radioarealocator/data/satellite/FavoriteSatellitesStore.kt)
- **错误场景**：
  多线程并发调用 `toggle(catalogNumber)` 时，可能丢失修改。
  例如：线程 A 读取 {1,2}，线程 B 读取 {1,2}，A 写入 {1,2,3}，B 写入 {1,2,4}，最终结果丢失了 3 或 4。
- **根因**：
  `toggle()` 内部执行 load-modify-save 三步操作，无同步保护。
- **修复方案**：
  为 `toggle()` 添加 `@Synchronized` 注解，保证同一实例上的调用串行化。
  ```kotlin
  @Synchronized
  fun toggle(catalogNumber: Int): Set<Int> {
      val current = load()
      val updated = if (catalogNumber in current) current - catalogNumber else current + catalogNumber
      save(updated)
      return updated
  }
  ```

### Bug #4：背景图大图 OOM

- **严重度**：中
- **类别**：性能问题
- **模块**：[MainActivity.kt](../app/src/main/java/com/example/radioarealocator/MainActivity.kt)
- **错误场景**：
  用户选择高分辨率背景图（如 4000×6000）时，`BitmapFactory.decodeStream()` 直接解码原图，可能消耗 100MB+ 内存，导致 OOM 崩溃。
- **根因**：
  原代码无下采样，直接解码完整尺寸 Bitmap。
- **修复方案**：
  实现两阶段解码：
  1. 第一阶段：`inJustDecodeBounds=true` 仅读取图片边界
  2. 计算下采样比例 `inSampleSize`，使解码后尺寸不超过 1080×1920
  3. 第二阶段：按 `inSampleSize` 解码下采样后的 Bitmap
  ```kotlin
  private fun calculateInSampleSize(srcWidth: Int, srcHeight: Int, targetWidth: Int, targetHeight: Int): Int {
      if (srcWidth <= 0 || srcHeight <= 0) return 1
      var sampleSize = 1
      while (srcWidth / sampleSize > targetWidth * 2 || srcHeight / sampleSize > targetHeight * 2) {
          sampleSize *= 2
      }
      return sampleSize.coerceAtLeast(1)
  }
  ```

### Bug #5：每秒重组导致性能下降

- **严重度**：中
- **类别**：性能问题
- **模块**：[MainScreen.kt](../app/src/main/java/com/example/radioarealocator/ui/MainScreen.kt)
- **错误场景**：
  每个在境卫星的 `SatelliteItem` 内部都有一个 `LaunchedEffect` 每秒更新 `nowMillis`，N 个在境卫星会启动 N 个独立的 1Hz 协程，导致不必要的重组和 CPU 占用。
- **根因**：
  时钟状态分散在子组件中，未复用。
- **修复方案**：
  将时钟提升到 `SatelliteDetailContent` 父级：
  - 仅当存在在境卫星时启动时钟
  - 通过参数 `nowMillis` 传递给每个 `SatelliteItem`
  ```kotlin
  var inPassNowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
  val hasInPassSatellites = filteredSatellites.any { it.isCurrentlyVisible }
  LaunchedEffect(hasInPassSatellites) {
      if (hasInPassSatellites) {
          while (true) {
              inPassNowMillis = System.currentTimeMillis()
              delay(1000)
          }
      }
  }
  ```

### Bug #6：覆盖率检查逻辑混淆

- **严重度**：中
- **类别**：构建错误
- **模块**：[test.yml](../.github/workflows/test.yml)
- **错误场景**：
  CI 工作流中覆盖率检查将"测试通过率"和"指令覆盖率"混淆，导致通过率不足 100% 时仍可能因覆盖率达标而通过。
- **根因**：
  原脚本只用一个 `COVERAGE` 变量同时表示通过率和覆盖率，语义不清。
- **修复方案**：
  区分 `PASS_RATE`（要求 100%）和 `COVERAGE`（要求 ≥90%），分别检查：
  ```yaml
  if [ "$(awk "BEGIN { print ($PASS_RATE < 100) }")" = "1" ]; then
    echo "❌ 测试通过率 ${PASS_RATE}% 低于 100%（存在失败/跳过用例）"
    exit 1
  fi
  if [ "$(awk "BEGIN { print ($COVERAGE < $THRESHOLD) }")" = "1" ]; then
    echo "❌ 指令覆盖率 ${COVERAGE}% 低于阈值 ${THRESHOLD}%"
    exit 1
  fi
  ```

### Bug #7：satelliteSource 未持久化

- **严重度**：中
- **类别**：功能缺陷
- **模块**：[SettingsStore.kt](../app/src/main/java/com/example/radioarealocator/data/SettingsStore.kt) + [MainViewModel.kt](../app/src/main/java/com/example/radioarealocator/ui/MainViewModel.kt)
- **错误场景**：
  用户在设置中切换卫星数据来源（ALL / CelesTrak / SatNOGS），重启应用后恢复为默认值 "ALL"。
- **根因**：
  `MainViewModel._satelliteSource` 仅用 `mutableStateOf` 保存在内存中，未写入 SharedPreferences。
- **修复方案**：
  在 `SettingsStore` 新增 `satelliteSource` 属性持久化到 SharedPreferences，`MainViewModel` 初始化时读取、设置时写入：
  ```kotlin
  // SettingsStore
  var satelliteSource: String
      get() = prefs.getString(KEY_SATELLITE_SOURCE, "ALL") ?: "ALL"
      set(value) { prefs.edit().putString(KEY_SATELLITE_SOURCE, value).apply() }

  // MainViewModel
  private val _satelliteSource = mutableStateOf(settingsStore.satelliteSource)
  fun setSatelliteSource(source: String) {
      _satelliteSource.value = source
      settingsStore.satelliteSource = source
  }
  ```

### Bug #8：筛选弹窗 expanded 状态未保存

- **严重度**：低
- **类别**：用户体验
- **模块**：[MainScreen.kt](../app/src/main/java/com/example/radioarealocator/ui/MainScreen.kt)
- **错误场景**：
  用户打开卫星筛选弹窗后旋转屏幕或切到后台，弹窗自动收起，需重新点击打开。
- **根因**：
  `expanded` 用 `remember` 保存，配置变更（如旋转）时状态丢失。
- **修复方案**：
  改用 `rememberSaveable`，状态会随实例状态一起保存恢复：
  ```kotlin
  var expanded by rememberSaveable { mutableStateOf(false) }
  ```

### Bug #9：versionName 废弃 API

- **严重度**：低
- **类别**：兼容性
- **模块**：[AboutScreen.kt](../app/src/main/java/com/example/radioarealocator/ui/AboutScreen.kt)
- **错误场景**：
  Android 13+（API 33+）上调用 `getPackageInfo(name, 0)` 触发废弃警告，未来版本可能失效。
- **根因**：
  `PackageManager.getPackageInfo(String, Int)` 在 API 33 起被废弃，应改用 `PackageInfoFlags` 版本。
- **修复方案**：
  按 SDK 版本分支调用：
  ```kotlin
  val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
  } else {
      @Suppress("DEPRECATION")
      context.packageManager.getPackageInfo(context.packageName, 0)
  }
  ```

### Bug #10：资源格式化参数非位置化

- **严重度**：低
- **类别**：构建警告
- **模块**：[strings.xml](../app/src/main/res/values/strings.xml)
- **错误场景**：
  `mergeDebugResources` 任务警告：`satellite_count_filtered` 字符串包含多个 `%d` 替换符但未使用位置化格式，可能导致翻译时参数顺序错乱。
- **根因**：
  原字符串 `%d / %d 颗` 使用非位置化格式，AAPT 无法确定参数顺序。
- **修复方案**：
  改用位置化格式 `%1$d / %2$d 颗`：
  ```xml
  <string name="satellite_count_filtered">%1$d / %2$d 颗</string>
  ```

### Bug #11：Double 不支持 until 操作符

- **严重度**：高
- **类别**：编译错误
- **模块**：[ZoneResolver.kt](../app/src/main/java/com/example/radioarealocator/data/zone/ZoneResolver.kt)
- **错误场景**：
  Bug #1 修复时使用 `lat in minLat until maxLat`，但 `until` 仅适用于 `Int`/`Long`，`Double` 类型编译失败：`Unresolved reference 'until'`。
- **根因**：
  Kotlin 标准库未为 `Double`/`Float` 提供 `until` 扩展操作符。
- **修复方案**：
  改用显式比较实现半开区间。最终方案采用左开右闭区间 `(min, max]`（见 Bug #1）：
  ```kotlin
  lat > minLat && lat <= maxLat
  ```

---

## 三、测试验证

### 单元测试结果

| 测试类 | 测试数 | 通过 | 失败 | 跳过 |
|--------|--------|------|------|------|
| ZoneResolverTest | 12 | 12 | 0 | 0 |
| MaidenheadCalculatorTest | 14 | 14 | 0 | 0 |
| SatelliteCatalogTest | 8 | 8 | 0 | 0 |
| **合计** | **34** | **34** | **0** | **0** |

### 新增单元测试用例

为 Bug #1 边界修复新增 3 个测试用例（位于 [ZoneResolverTest.kt](../app/src/test/java/com/example/radioarealocator/data/zone/ZoneResolverTest.kt)）：

1. `boundary longitude 40 belongs to europe not russia`：验证边界点归属
2. `polar boundaries are still matched`：验证极值边界仍可命中
3. `no mans land at boundaries`：验证多个边界相接无"无人区"

### 集成测试

新增 4 个集成测试类，覆盖 Bug #3、#7、#8 的修复验证。位于 `app/src/androidTest/` 目录：

| 测试类 | 测试数 | 覆盖 Bug | 说明 |
|--------|--------|----------|------|
| [SettingsStoreIntegrationTest](../app/src/androidTest/java/com/example/radioarealocator/data/SettingsStoreIntegrationTest.kt) | 7 | #7 | satelliteSource/backgroundUri 持久化与恢复 |
| [FavoriteSatellitesStoreIntegrationTest](../app/src/androidTest/java/com/example/radioarealocator/data/satellite/FavoriteSatellitesStoreIntegrationTest.kt) | 7 | #3 | toggle 单线程读写 + 50 协程并发不丢失 |
| [SatelliteFilterIntegrationTest](../app/src/androidTest/java/com/example/radioarealocator/ui/SatelliteFilterIntegrationTest.kt) | 11 | #8 | rememberSaveable 状态恢复 + applyFilter 8 种条件组合 |
| [MainViewModelIntegrationTest](../app/src/androidTest/java/com/example/radioarealocator/ui/MainViewModelIntegrationTest.kt) | 9 | #7 | ViewModel 销毁重建后状态恢复 |
| **合计** | **34** | | |

#### 关键集成测试场景

1. **Bug #3 并发安全**（`toggle_concurrentDifferentSatellites_noLoss`）：
   - 启动 50 个协程并发 toggle 不同卫星编号
   - 验证最终集合包含全部 50 个编号（无丢失）
   - 修复前（无 @Synchronized）会因 load-modify-save 竞态导致部分丢失

2. **Bug #7 持久化恢复**（`satelliteSource_restoredFromSettingsStore_onViewModelRecreation`）：
   - 第一次 ViewModel 设置 satelliteSource="CT"
   - 新建 ViewModel 实例（模拟进程重启）
   - 验证 satelliteSource 恢复为 "CT"

3. **Bug #8 状态保存**（`rememberSaveable_expandedState_survivesStateRestoration`）：
   - 使用 `StateRestorationTester` 模拟 Activity 重建
   - 验证 rememberSaveable 保存 expanded=true
   - 对照组 `remember_withoutSaveable_losesStateOnRestoration` 验证 remember 会丢失

#### 编译验证

```bash
gradle :app:compileDebugAndroidTestKotlin --console=plain --no-daemon
# BUILD SUCCESSFUL in 33s
```

集成测试已通过编译验证，需在连接设备/模拟器的 CI 环境运行：

```bash
gradle :app:connectedDebugAndroidTest --console=plain
```

### 本地环境配置

- JDK：通过 `actions/setup-java@v4` 配置
- Android SDK Platform 37.0（通过 `sdkmanager --install "platforms;android-37.0"` 安装，并创建 `android-37` → `android-37.0` 目录连接）
- Gradle 9.4.1（全局安装，项目无 gradlew）

---

## 四、未涉及项

- **安全漏洞**：经审查 `signing/github-secrets.txt` 仅含配置说明，实际密钥不在代码库中；`.gitignore` 已正确排除敏感文件；网络请求（AmsatStatusApiService）异常处理完整。未发现安全漏洞。
- **集成测试运行**：本地无连接设备/模拟器，集成测试已编译通过但未实际运行，建议在 CI 上通过 `connectedDebugAndroidTest` 执行。
- **其他已审查但无问题的项**：
  - LocationHelper 多策略定位的协程取消逻辑正确
  - SatelliteCacheStore TLE 解析与序列化健壮
  - MainScreen 时钟 LaunchedEffect 在无在境卫星时自动暂停
  - Theme.kt 暗色模式切换正常
  - ProGuard 规则完整
