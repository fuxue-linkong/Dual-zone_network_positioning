# 双区网络定位

使用AI构建Android 应用，自动获取手机定位并计算：

- **CQ 分区**（CQ Zone）
- **ITU 分区**（ITU Zone）
- **梅登兰德定位**（Maidenhead Locator）

同时自动将每次查询结果保存到本地历史记录。
如有bug请议题中提出，或者发我的QQ（3252647738@qq.com）/outlook邮箱（fuxuelingkong@outlook.com）
## 功能

1. 一键获取当前 GPS 坐标。
2. 自动计算 CQ / ITU 分区和 6 位 Maidenhead 网格。
3. 使用 Room 数据库持久化历史记录，支持清空。

### 应用截图

![应用截图](IMG_20260620_010952.jpg)

## 技术栈

- Kotlin 1.9.22
- Jetpack Compose
- Room + KSP
- Google Play Services Location
- Material Design 3

## 许可证

[LICENSE](LICENSE)
