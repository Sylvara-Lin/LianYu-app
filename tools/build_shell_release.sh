#!/bin/bash
# ═══════════════════════════════════════════
# LianYu One-Piece Shell Release Build
# ═══════════════════════════════════════════
# Produces an APK where:
#   - classes.dex = FULL R8-minified DEX (all classes, obfuscated)
#   - shell_payload.bin = AES-256-CBC encrypted copy of ALL DEX
#
# Architecture:
#   R8 obfuscation protects the plaintext DEX from static analysis.
#   The encrypted payload provides an independent integrity baseline:
#   at runtime the shell decrypts it, verifies SHA-256, and can
#   optionally reinject classes to detect tampering.
#
# Pipeline:
#   1. Build full APK (R8 minification on)
#   2. Extract ALL DEX → AES-256-CBC encrypt → shell_payload.bin
#   3. Package: keep original classes.dex + inject payload + sign
# ═══════════════════════════════════════════

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

KEYSTORE="${PROJECT_DIR}/release.keystore"
STORE_PASS="${LIANYU_STORE_PASSWORD:-3498762309}"
KEY_ALIAS="${LIANYU_KEY_ALIAS:-your_alias}"
KEY_PASS="${LIANYU_KEY_PASSWORD:-3498762309}"
SDK_BT="/opt/android-sdk/build-tools/35.0.0"
APKSIGNER="${SDK_BT}/apksigner"
ZIPALIGN="${SDK_BT}/zipalign"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "========================================"
echo " LianYu One-Piece Shell Release Build"
echo "========================================"
echo ""

# ═══════════════════════════════════════════
# Step 1: Full Release Build
# ═══════════════════════════════════════════
echo -e "${YELLOW}[Step 1/4] Building full release APK...${NC}"
export LIANYU_STORE_PASSWORD="$STORE_PASS"
export LIANYU_KEY_PASSWORD="$KEY_PASS"
export LIANYU_KEY_ALIAS="$KEY_ALIAS"

./gradlew assembleRelease --no-daemon

FULL_APK="app/build/outputs/apk/release/app-release.apk"
test -f "$FULL_APK" || { echo -e "${RED}ERROR: Build failed${NC}"; exit 1; }

echo -e "  ${GREEN}✓${NC} Full APK built"
unzip -l "$FULL_APK" | grep 'classes.*\.dex$' | while read -r l; do echo "    $l"; done

# ═══════════════════════════════════════════
# Step 2: Encrypt ALL DEX as shell payload
# ═══════════════════════════════════════════
echo ""
echo -e "${YELLOW}[Step 2/3] Encrypting DEX into shell payload...${NC}"

PAYLOAD_OUT="app/build/shell_payload_assets"
rm -rf "$PAYLOAD_OUT"
mkdir -p "$PAYLOAD_OUT"

python3 tools/package_shell_payload.py "$FULL_APK" --output "$PAYLOAD_OUT" --dev

echo -e "  ${GREEN}✓${NC} Payload encrypted"

# ═══════════════════════════════════════════
# Step 3: Package final APK (original DEX + payload + sign)
# ═══════════════════════════════════════════
echo ""
echo -e "${YELLOW}[Step 3/3] Packaging final APK + signing...${NC}"

SHELL_APK="app/build/outputs/apk/release/app-release-shell.apk"

python3 -c "
import zipfile, os

src = '$FULL_APK'
dst = '$SHELL_APK'
payload_dir = '$PAYLOAD_OUT/lianyu_shell'

with zipfile.ZipFile(src, 'r') as zin:
    with zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            # Keep ONLY classes.dex (shell + transitive deps via R8 main-dex).
            # Secondary DEX files (classes2.dex, classes3.dex, ...) contain
            # business classes and framework overflow — they go into the
            # encrypted payload, loaded at runtime via InMemoryDexClassLoader.
            if item.filename.startswith('classes') and item.filename.endswith('.dex'):
                if item.filename == 'classes.dex':
                    zout.writestr(item, zin.read(item.filename))
                    print(f'  Kept {item.filename} ({item.file_size:,} bytes) — shell + deps')
                else:
                    print(f'  Removed {item.filename} ({item.file_size:,} bytes) → encrypted payload')
                continue
            # Strip old signatures
            if item.filename.startswith('META-INF/'):
                continue
            # Remove stale payload
            if item.filename.startswith('assets/lianyu_shell/') or item.filename.startswith('lianyu_shell/'):
                continue
            zout.writestr(item, zin.read(item.filename))

        # Inject encrypted payload (contains ALL classes for runtime loading)
        for fname in sorted(os.listdir(payload_dir)):
            fpath = os.path.join(payload_dir, fname)
            arcname = f'assets/lianyu_shell/{fname}'
            zout.write(fpath, arcname)
            print(f'  Added {arcname} ({os.path.getsize(fpath):,} bytes)')

sz = os.path.getsize(dst)
print(f'  APK size: {sz:,} bytes')

# Verify
with zipfile.ZipFile(dst, 'r') as z:
    dex = [n for n in z.namelist() if n.endswith('.dex')]
    pl = [n for n in z.namelist() if 'lianyu_shell' in n]
    print(f'  Final DEX files: {dex}')
    print(f'  Payload assets:  {pl}')
"

# Zipalign + Sign
ALIGNED="${SHELL_APK%.apk}_aligned.apk"
FINAL="app/build/outputs/apk/release/app-release-shell-signed.apk"

$ZIPALIGN -f -p 4 "$SHELL_APK" "$ALIGNED"
$APKSIGNER sign --ks "$KEYSTORE" --ks-pass "pass:$STORE_PASS" --ks-key-alias "$KEY_ALIAS" \
    --key-pass "pass:$KEY_PASS" --v1-signing-enabled false --v2-signing-enabled true \
    --v3-signing-enabled true --out "$FINAL" "$ALIGNED"

$APKSIGNER verify "$FINAL" && echo -e "  ${GREEN}✓${NC} Signature verified"

# ═══════════════════════════════════════════
# Done
# ═══════════════════════════════════════════
echo ""
echo "========================================"
echo " Build Complete!"
echo "========================================"
echo ""
unzip -l "$FINAL" | grep -E '(classes.*\.dex|lianyu_shell/)' | while read -r l; do echo "  $l"; done
echo ""
du -h "$FINAL" | awk '{print "  Output: " $2 " (" $1 ")"}'
echo ""
echo "Architecture:"
echo "  classes.dex (APK)    = shell bootstrap + transitive deps (plaintext, ~2MB)"
echo "  assets/lianyu_shell/ = AES-256-CBC encrypted ALL classes"
echo "  Runtime:             LianYuShellApplication loads from classes.dex"
echo "                       → decrypts payload → InMemoryDexClassLoader"
echo "                       → business classes available at runtime"
echo ""
