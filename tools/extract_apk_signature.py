#!/usr/bin/env python3
"""
APK Signature Hash Extractor — 自动提取 APK 签名证书哈希

这个脚本用于：
1. 从 APK 中提取签名证书的 SHA-256 哈希
2. 生成 native-bridge.cpp 中 d_se() 函数需要的 expected[32] 数组
3. 自动替换 native-bridge.cpp 中的占位符哈希

Usage:
    # 提取签名并生成替换代码
    python3 tools/extract_apk_signature.py <path/to/release.apk>

    # 自动替换 native-bridge.cpp 中的哈希
    python3 tools/extract_apk_signature.py <path/to/release.apk> --patch

    # 从 keystore 直接提取（不需要 APK）
    python3 tools/extract_apk_signature.py --keystore <keystore.jks> --alias <alias>
"""

import sys
import os
import subprocess
import re
import zipfile
import hashlib


def extract_cert_from_apk(apk_path: str) -> bytes:
    """从 APK 中提取第一个签名证书文件（.RSA 或 .DSA）。"""
    with zipfile.ZipFile(apk_path, 'r') as z:
        cert_files = [f for f in z.namelist()
                      if f.startswith('META-INF/') and (f.endswith('.RSA') or f.endswith('.DSA') or f.endswith('.EC'))]
        if not cert_files:
            raise ValueError("No certificate found in APK. Make sure APK is signed.")
        return z.read(cert_files[0])


def extract_cert_hash_from_keystore(keystore_path: str, alias: str, password: str = "") -> str:
    """从 keystore 中提取证书的 SHA-256 哈希。"""
    cmd = [
        'keytool', '-exportcert', '-alias', alias,
        '-keystore', keystore_path, '-rfc'
    ]
    if password:
        cmd.extend(['-storepass', password])

    try:
        result = subprocess.run(cmd, capture_output=True, text=True)
        if result.returncode != 0:
            print(f"ERROR: keytool failed: {result.stderr}")
            sys.exit(1)

        # Parse PEM certificate
        cert_pem = result.stdout
        # Extract base64 content
        lines = [l for l in cert_pem.split('\n') if l and not l.startswith('---')]
        cert_der = ''.join(lines)
        import base64
        cert_bytes = base64.b64decode(cert_der)

        # Compute SHA-256
        return hashlib.sha256(cert_bytes).hexdigest()
    except FileNotFoundError:
        print("ERROR: keytool not found. Make sure JDK is installed and in PATH.")
        sys.exit(1)


def extract_cert_hash_from_apk(apk_path: str) -> str:
    """从 APK 中提取签名证书的 SHA-256 哈希。"""
    cert_data = extract_cert_from_apk(apk_path)

    # 使用 keytool 解析 PKCS7 签名块
    # 先写入临时文件
    import tempfile
    with tempfile.NamedTemporaryFile(suffix='.p7b', delete=False) as f:
        f.write(cert_data)
        temp_path = f.name

    try:
        # 使用 openssl 提取证书
        cmd = [
            'openssl', 'pkcs7', '-in', temp_path,
            '-inform', 'DER', '-print_certs', '-outform', 'DER'
        ]
        result = subprocess.run(cmd, capture_output=True)

        if result.returncode == 0 and result.stdout:
            cert_der = result.stdout
        else:
            # Fallback: try to parse as raw certificate
            cert_der = cert_data

        # 计算 SHA-256
        return hashlib.sha256(cert_der).hexdigest()
    except FileNotFoundError:
        print("WARNING: openssl not found, trying alternative method...")
        # Fallback: 直接用 cert_data 的哈希（可能不完全准确）
        return hashlib.sha256(cert_data).hexdigest()
    finally:
        os.unlink(temp_path)


def generate_d_se_array(hash_hex: str) -> str:
    """生成 native-bridge.cpp 中 d_se() 函数的 expected[32] 数组代码。"""
    hash_bytes = bytes.fromhex(hash_hex)
    if len(hash_bytes) != 32:
        raise ValueError(f"Hash must be 32 bytes, got {len(hash_bytes)}")

    # XOR obfuscate with the same key as native-bridge.cpp
    obfuscated = []
    for i, b in enumerate(hash_bytes):
        key = 0x5A ^ ((i * 0x7E) & 0xFF)  # 使用与 d_se 相同的 obfuscation
        obfuscated.append(b ^ key)

    lines = ["/* AUTO-GENERATED: APK Signature SHA-256 (XOR-obfuscated) */"]
    lines.append("static void d_se(unsigned char* out) {")
    lines.append("    static const unsigned char E[32] = {")

    for i in range(0, 32, 8):
        row = obfuscated[i:i+8]
        hex_vals = ', '.join(f'0x{b:02X}' for b in row)
        lines.append(f"        {hex_vals},")

    lines.append("    };")
    lines.append("    static const unsigned char K[32] = {")

    for i in range(0, 32, 8):
        keys = []
        for j in range(i, min(i+8, 32)):
            key = 0x5A ^ ((j * 0x7E) & 0xFF)
            keys.append(key)
        hex_vals = ', '.join(f'0x{b:02X}' for b in keys)
        lines.append(f"        {hex_vals},")

    lines.append("    };")
    lines.append("")
    lines.append("    for (int i = 0; i < 32; i++) {")
    lines.append("        out[i] = E[i] ^ K[i];")
    lines.append("    }")
    lines.append("}")

    return '\n'.join(lines)


def patch_native_bridge(cpp_path: str, new_d_se: str) -> bool:
    """替换 native-bridge.cpp 中的 d_se 函数。"""
    with open(cpp_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # 找到 d_se 函数的位置（从 static void d_se 到下一个 static 函数之前）
    pattern = r'static void d_se\(unsigned char\* out\)\s*\{[\s\S]*?\n\}'

    if not re.search(pattern, content):
        print("WARNING: Could not find d_se() function. Manual replacement needed.")
        return False

    new_content = re.sub(pattern, new_d_se.strip(), content)

    with open(cpp_path, 'w', encoding='utf-8') as f:
        f.write(new_content)

    return True


def main():
    import argparse
    parser = argparse.ArgumentParser(description='Extract APK signature hash')
    parser.add_argument('apk', nargs='?', help='Path to signed APK')
    parser.add_argument('--keystore', help='Path to keystore file')
    parser.add_argument('--alias', help='Key alias in keystore')
    parser.add_argument('--password', default='', help='Keystore password')
    parser.add_argument('--patch', action='store_true',
                        help='Auto-patch native-bridge.cpp')
    parser.add_argument('--cpp', default='core/security/src/main/cpp/native-bridge.cpp',
                        help='Path to native-bridge.cpp')
    args = parser.parse_args()

    hash_hex = None

    if args.keystore:
        if not args.alias:
            print("ERROR: --alias required when using --keystore")
            sys.exit(1)
        print(f"[*] Extracting certificate from keystore: {args.keystore}")
        hash_hex = extract_cert_hash_from_keystore(args.keystore, args.alias, args.password)
    elif args.apk:
        print(f"[*] Extracting certificate from APK: {args.apk}")
        hash_hex = extract_cert_hash_from_apk(args.apk)
    else:
        print("ERROR: Provide either an APK file or --keystore")
        sys.exit(1)

    print(f"[+] Certificate SHA-256: {hash_hex}")

    # 生成 d_se 函数代码
    d_se_code = generate_d_se_array(hash_hex)

    print("\n" + "="*60)
    print("Generated d_se() function:")
    print("="*60)
    print(d_se_code)

    if args.patch:
        print(f"\n[*] Patching {args.cpp}...")
        if patch_native_bridge(args.cpp, d_se_code):
            print(f"[+] Successfully patched {args.cpp}")
        else:
            print("[!] Auto-patch failed. Please manually replace d_se() function.")
            print("\nReplace this function in native-bridge.cpp:")
            print("-" * 40)
            print(d_se_code)
    else:
        print("\n[*] To auto-patch, run with --patch")
        print("[*] Or manually replace the d_se() function in native-bridge.cpp")


if __name__ == '__main__':
    main()
