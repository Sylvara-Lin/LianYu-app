#!/usr/bin/env python3
"""
patch_apk_digest.py — Compute and embed APK integrity hashes.

Reads the built APK, computes SHA-256 of DEX/SO/ARSC sections,
replaces placeholder values in native-bridge.cpp g_apk_digests_obs[96].

Usage:
  python tools/patch_apk_digest.py app-release.apk
  python tools/patch_apk_digest.py app-release.apk --sm3  # use SM3 later

Output: Updated native-bridge.cpp with real digest values.
"""

import hashlib, struct, sys, zipfile, os

CPP_PATH = "core/security/src/main/cpp/native-bridge.cpp"
ARRAY_NAME = "g_apk_digests_obs"

def compute_digests(apk_path):
    """Compute SHA-256 of DEX, SO, and ARSC from the APK."""
    digests = {}
    with zipfile.ZipFile(apk_path, 'r') as zf:
        for name in sorted(zf.namelist()):
            if name.endswith('.dex') or name.endswith('.so') or name.endswith('.arsc'):
                data = zf.read(name)
                h = hashlib.sha256(data).digest()
                digests[name] = h
    return digests

def xor_obfuscate(digest_hex, seed=0x5A):
    """Simple XOR obfuscation matching native-bridge.cpp deobfuscation."""
    result = bytearray(digest_hex)
    for i in range(len(result)):
        result[i] ^= seed ^ (i & 0xFF)
    return bytes(result)

def main():
    apk_path = sys.argv[1] if len(sys.argv) > 1 else "app/build/outputs/apk/release/app-release.apk"
    
    print(f"[DIGEST] Computing from: {apk_path}")
    digests = compute_digests(apk_path)
    
    # Concatenate all digests: DEX first, then SOs, then ARSC
    dex_hashes = b''
    so_hashes = b''
    arsc_hash = b''
    
    for name, h in sorted(digests.items()):
        if name.endswith('.dex'):
            dex_hashes += h
        elif name.endswith('.so'):
            so_hashes += h
        elif name.endswith('.arsc'):
            arsc_hash = h
    
    # Combine: DEX[32] + SO[32] + ARSC[32] = 96 bytes
    combined = dex_hashes[:32] + so_hashes[:32] + arsc_hash[:32]
    
    if len(combined) < 96:
        combined = combined.ljust(96, b'\x00')
    combined = combined[:96]
    
    print(f"  DEX SHA-256:  {dex_hashes[:32].hex()}")
    print(f"  SO  SHA-256:  {so_hashes[:32].hex()}")
    print(f"  ARSC SHA-256: {arsc_hash[:32].hex()}")
    
    # XOR obfuscate
    obs = xor_obfuscate(combined)
    
    # Generate C array
    c_array = 'static const uint8_t g_apk_digests_obs[96] = {\n'
    for i in range(0, 96, 12):
        line = ', '.join(f'0x{b:02x}' for b in obs[i:i+12])
        c_array += f'    {line},\n'
    c_array += '};'
    
    # Patch native-bridge.cpp
    if not os.path.exists(CPP_PATH):
        print(f"ERROR: {CPP_PATH} not found")
        return 1
    
    with open(CPP_PATH, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Find and replace g_apk_digests_obs array
    import re
    pattern = r'static const uint8_t g_apk_digests_obs\[96\] = \{.*?\};'
    new_content = re.sub(pattern, c_array, content, flags=re.DOTALL)
    
    if new_content == content:
        print("WARNING: Could not find g_apk_digests_obs array — no replacement made")
        return 1
    
    with open(CPP_PATH, 'w', encoding='utf-8') as f:
        f.write(new_content)
    
    print(f"\n[DONE] Patched {CPP_PATH} with real APK digests")
    print(f"  (Note: SHA-256 used; upgrade to SM3 before production)")
    return 0

if __name__ == '__main__':
    sys.exit(main())
