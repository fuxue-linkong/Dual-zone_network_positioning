#!/usr/bin/env bash
# 生成 25 年有效期的 release keystore，用于 GitHub Actions 持久化签名。
#
# 使用方法（在项目根目录执行）：
#   bash scripts/generate-release-keystore.sh
#
# 或在 Windows PowerShell 中执行：
#   bash scripts/generate-release-keystore.sh
#
# 脚本执行后，将输出的 base64 字符串和密码配置到 GitHub 仓库的 Secrets：
#   KEYSTORE_BASE64      — keystore 文件的 base64 编码
#   KEYSTORE_PASSWORD    — keystore 密码
#   KEY_ALIAS            — 密钥别名
#   KEY_PASSWORD         — 密钥密码
#
# 配置路径：GitHub 仓库 → Settings → Secrets and variables → Actions → New repository secret

set -euo pipefail

KEYSTORE_NAME="release.keystore"
KEY_ALIAS="radio-area-locator"
VALIDITY_DAYS=9125  # 25 年
OUTPUT_DIR="signing"

# 生成随机密码（也可手动指定）
KEYSTORE_PASSWORD=$(openssl rand -base64 18 | tr -d '/+=' | head -c 20)
KEY_PASSWORD=$(openssl rand -base64 18 | tr -d '/+=' | head -c 20)

mkdir -p "$OUTPUT_DIR"
KEYSTORE_PATH="$OUTPUT_DIR/$KEYSTORE_NAME"

echo "================================================"
echo "  生成 Release Keystore（有效期 ${VALIDITY_DAYS} 天 ≈ 25 年）"
echo "================================================"
echo ""

# 检查 keytool 是否可用
if ! command -v keytool >/dev/null 2>&1; then
    echo "错误：未找到 keytool。请确保已安装 JDK 并添加到 PATH。"
    exit 1
fi

# 如果已存在则备份
if [ -f "$KEYSTORE_PATH" ]; then
    BACKUP="${KEYSTORE_PATH}.bak.$(date +%Y%m%d%H%M%S)"
    cp "$KEYSTORE_PATH" "$BACKUP"
    echo "已备份现有 keystore 到 $BACKUP"
fi

# 生成 keystore
keytool -genkeypair \
    -keystore "$KEYSTORE_PATH" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity "$VALIDITY_DAYS" \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -dname "CN=RadioAreaLocator, OU=Dev, O=OpenSource, L=Beijing, ST=Beijing, C=CN"

echo ""
echo "================================================"
echo "  Keystore 生成成功"
echo "================================================"
echo ""
echo "文件路径: $KEYSTORE_PATH"
echo "密钥别名: $KEY_ALIAS"
echo ""

# 验证 keystore
echo "验证 keystore 信息："
keytool -list -v -keystore "$KEYSTORE_PATH" -alias "$KEY_ALIAS" -storepass "$KEYSTORE_PASSWORD" | \
    grep -E "Alias name|Creation date|Entry type|Valid from|Signature algorithm|Subject"

echo ""
echo "================================================"
echo "  GitHub Secrets 配置信息（请妥善保存）"
echo "================================================"
echo ""
echo "请将以下 4 个值配置到 GitHub 仓库的 Secrets："
echo "  仓库 → Settings → Secrets and variables → Actions → New repository secret"
echo ""
echo "┌─────────────────────────────────────────────────────────────┐"
echo "│ Secret 名称          │ 值                                   │"
echo "├─────────────────────────────────────────────────────────────┤"
echo -e "│ KEYSTORE_PASSWORD    │ $KEYSTORE_PASSWORD"
echo -e "│ KEY_ALIAS            │ $KEY_ALIAS"
echo -e "│ KEY_PASSWORD         │ $KEY_PASSWORD"
echo "└─────────────────────────────────────────────────────────────┘"
echo ""

# 输出 base64 编码
echo "KEYSTORE_BASE64 的值（单行，复制到 GitHub Secret）："
echo ""
base64 -w 0 "$KEYSTORE_PATH" 2>/dev/null || base64 -b 0 "$KEYSTORE_PATH"
echo ""
echo ""
echo "================================================"
echo "  配置完成后，删除本脚本输出的敏感信息"
echo "================================================"
echo "重要提醒："
echo "  1. 请安全保存以上密码信息，丢失后无法恢复"
echo "  2. keystore 文件 (signing/release.keystore) 已被 .gitignore 忽略"
echo "  3. 请将 keystore 文件备份到安全位置（如密码管理器）"
echo "  4. 配置完 GitHub Secrets 后，即可删除本地的密码输出记录"
