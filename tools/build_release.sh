#!/bin/bash
# ================================================================
# LianYu Release Build Script — 完整加固构建流程
# ================================================================
# Steps:
#   1. 生成白盒AES表
#   2. 构建 Release APK
#   3. 提取 APK 签名证书哈希 → 更新 native-bridge.cpp
#   4. 生成 APK 完整性校验哈希（DEX/SO/ARSC）
#   5. 重新编译 Native 并重新打包
#   6. 打包加密 Shell Payload (业务 DEX → shell_payload.bin)
#   7. 剥离 classes.dex 中的业务类 (仅保留壳代码)
#   8. 去掉 @Metadata 注解，DEX 4路分片
#   9. 重新签名
#
# Usage:
#   ./tools/build_release.sh
#   ./tools/build_release.sh --keystore ../release.keystore --alias lianyu
# ================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

echo "========================================"
echo "LianYu Release Build — One-Piece Shell"
echo "========================================"

# Parse arguments
KEYSTORE=""
ALIAS=""
PASSWORD=""
while [[ $# -gt 0 ]]; do
    case $1 in
        --keystore) KEYSTORE="$2"; shift 2 ;;
        --alias) ALIAS="$2"; shift 2 ;;
        --password) PASSWORD="$2"; shift 2 ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

# ── Step 1: White-Box AES tables ──
echo ""
echo "[Step 1/9] Generating White-Box AES tables..."
if [ -f ".lianyu_wb_key" ]; then
    echo "[*] Using existing key from .lianyu_wb_key"
    python3 tools/setup_wb_key.py
else
    echo "[*] Generating new random key..."
    python3 tools/setup_wb_key.py --generate
fi

# ── Step 2: Initial Build ──
echo ""
echo "[Step 2/9] Building Release APK (initial)..."
./gradlew assembleRelease

APK_PATH="app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK_PATH" ]; then
    echo "ERROR: APK not found at $APK_PATH"
    exit 1
fi

# ── Step 3: Signature hash → native-bridge.cpp ──
echo ""
echo "[Step 3/9] Extracting APK signature hash..."
if [ -n "$KEYSTORE" ] && [ -n "$ALIAS" ]; then
    python3 tools/extract_apk_signature.py \
        --keystore "$KEYSTORE" --alias "$ALIAS" --password "$PASSWORD" --patch
else
    python3 tools/extract_apk_signature.py "$APK_PATH" --patch || true
fi

# ── Step 4: Generate integrity hashes ──
echo ""
echo "[Step 4/9] Generating APK integrity hashes..."
SO_PATH="app/build/intermediates/merged_native_libs/release/out/lib/arm64-v8a/liblianyu_security.so"
if [ -f "$SO_PATH" ]; then
    python3 tools/generate_apk_hashes.py "$APK_PATH" "$SO_PATH"
else
    echo "[!] SO not found, generating DEX-only hashes..."
    python3 tools/generate_apk_hashes.py "$APK_PATH"
fi

# ── Step 5: Rebuild with integrity hashes ──
echo ""
echo "[Step 5/9] Rebuilding with integrity hashes..."
./gradlew assembleRelease

# ── Step 6: Package encrypted shell payload ──
echo ""
echo "[Step 6/9] Packaging encrypted shell payload..."
if [ -z "$LIANYU_PAYLOAD_KEY" ]; then
    echo "[!] LIANYU_PAYLOAD_KEY not set — generating ephemeral dev key"
    export LIANYU_PAYLOAD_KEY=$(openssl rand -base64 32)
fi
python3 tools/package_shell_payload.py "$APK_PATH" \
    --output app/src/main/assets --dev

# ── Step 7: Strip business classes from classes.dex ──
echo ""
echo "[Step 7/9] Stripping business classes from classes.dex..."
STRIPPED_APK="app/build/outputs/apk/release/app-release-stripped.apk"
python3 tools/strip_classes_dex.py "$APK_PATH" --output "$STRIPPED_APK"

# ── Step 8: Strip @Metadata + 4-way DEX split ──
echo ""
echo "[Step 8/9] Stripping @Metadata annotations + DEX split..."
python3 tools/strip_metadata.py "$STRIPPED_APK"
python3 tools/split_dex.py \
    --apk "$STRIPPED_APK" --splits 4 \
    --keystore "${KEYSTORE:-../release.keystore}" \
    --storepass "${LIANYU_STORE_PASSWORD:-3498762309}" \
    --keyalias "${LIANYU_KEY_ALIAS:-your_alias}" \
    --keypass "${LIANYU_KEY_PASSWORD:-3498762309}"

# ── Step 9: Re-sign final APK ──
echo ""
echo "[Step 9/9] Re-signing final APK..."
cp "$STRIPPED_APK" "$APK_PATH"
if command -v apksigner &> /dev/null && [ -f "../release.keystore" ]; then
    apksigner sign \
        --ks ../release.keystore \
        --ks-pass pass:"${LIANYU_STORE_PASSWORD:-3498762309}" \
        --ks-key-alias "${LIANYU_KEY_ALIAS:-your_alias}" \
        --key-pass pass:"${LIANYU_KEY_PASSWORD:-3498762309}" \
        --v1-signing-enabled false \
        --v2-signing-enabled true \
        --v3-signing-enabled true \
        --out "$APK_PATH" "$STRIPPED_APK"
    echo "[+] Signed with apksigner"
else
    echo "[!] Manual signing required: apksigner sign --ks release.keystore $STRIPPED_APK"
fi

# ── Verify ──
echo ""
echo "[*] Verifying final APK..."
if [ -f "$APK_PATH" ]; then
    echo "[+] Final APK: $APK_PATH"
    ls -lh "$APK_PATH"
    apksigner verify "$APK_PATH" 2>/dev/null && echo "[+] Signature verified" || echo "[!] Verify skipped"
    echo ""
    echo "[*] APK contents:"
    unzip -l "$APK_PATH" | grep -E "(lib.*\.so|classes.*\.dex|resources\.arsc|shell_payload)" || true
fi

echo ""
echo "========================================"
echo "Build Complete!"
echo "========================================"
echo "Output: $APK_PATH"
echo ""
echo "  classes.dex:  ONLY shell/security code (all business DEX encrypted)"
echo "  shell_payload.bin:  encrypted business DEX"
echo "  Keep .lianyu_wb_key SECRET"
echo "========================================"
