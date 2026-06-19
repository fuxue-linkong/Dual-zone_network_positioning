# 双区网络定位

一款基于 Jetpack Compose 的 Android 应用，自动获取手机定位并计算：

- **CQ 分区**（CQ Zone）
- **ITU 分区**（ITU Zone）
- **梅登兰德定位**（Maidenhead Locator）

同时自动将每次查询结果保存到本地历史记录。

## 功能

1. 一键获取当前 GPS 坐标。
2. 自动计算 CQ / ITU 分区和 6 位 Maidenhead 网格。
3. 使用 Room 数据库持久化历史记录，支持清空。
4. 支持 GitHub Actions 自动构建并发布签名 APK 到 Releases。

### 应用截图

![应用截图](IMG_20260620_010952.jpg)

## 技术栈

- Kotlin 1.9.22
- Jetpack Compose
- Room + KSP
- Google Play Services Location
- Material Design 3

## 关于分区精度

应用内置了一套常见国家/地区的经纬度包围盒，可在大多数陆地人口密集区获得较准确的 CQ / ITU 分区。对于海上或未覆盖的偏远地区，会使用基于经度的经验公式作为兜底估算。

如果你需要更高精度（例如精确到边境线），可以在 `ZoneResolver.kt` 中扩展区域数据，或替换为从 GeoJSON 多边形数据进行的点包含判断。

## 许可证

[LICENSE](LICENSE)
