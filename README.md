# Radio Area Locator

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

## 技术栈

- Kotlin 1.9.22
- Jetpack Compose
- Room + KSP
- Google Play Services Location
- Material Design 3

## 关于分区精度

应用内置了一套常见国家/地区的经纬度包围盒，可在大多数陆地人口密集区获得较准确的 CQ / ITU 分区。对于海上或未覆盖的偏远地区，会使用基于经度的经验公式作为兜底估算。

如果你需要更高精度（例如精确到边境线），可以在 `ZoneResolver.kt` 中扩展区域数据，或替换为从 GeoJSON 多边形数据进行的点包含判断。

## GitHub Actions Release 配置

仓库已包含 `.github/workflows/release.yml`。要给 APK 签名并自动发布，需要在仓库 **Settings → Secrets and variables → Actions** 中添加以下 secrets：

| Secret | 说明 |
|--------|------|
| `KEYSTORE_BASE64` | 签名密钥库文件的 Base64 编码 |
| `KEYSTORE_PASSWORD` | 密钥库密码 |
| `KEY_ALIAS` | 密钥别名 |
| `KEY_PASSWORD` | 密钥密码 |

生成 Base64 密钥库：

```bash
base64 -i your-release.keystore
```

推送以 `v` 开头的 tag 即可触发 Release 工作流：

```bash
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions 会自动编译 `app-release.apk`、使用上述密钥签名，并发布到 GitHub Releases。

## 许可证

[LICENSE](LICENSE)
