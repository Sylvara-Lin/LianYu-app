#!/usr/bin/env python3
"""Package LianYu shell payload for one-piece shell loading.

The packager:
  1. Extracts classes*.dex from a signed release APK
  2. Builds a payload (DEX concatenation, then PKCS7-padded)
  3. Encrypts the payload through the configured backend
  4. Writes the encrypted payload + manifest into assets/lianyu_shell/

ENCRYPTION CONTRACT (must match CompositeVmpRuntime.decryptPayload):
  - Payload is PKCS7-padded to 16-byte alignment before encryption.
  - Runtime expects: [metadata: 16 bytes] [ciphertext: same length as padded plaintext]
  - Metadata (16 bytes) is extracted by runtime and passed to KmsProvider.decryptWithMetadata.
  - Ciphertext is the encrypted padded payload (KMS V2: WB-AES-CBC with BK blinding).
  - Runtime verifies plaintext + ciphertext SHA-256 against manifest.

MODES:
  --dev          Use a standalone AES-256-CBC encryptor (requires LIANYU_PAYLOAD_KEY env).
                 NOT for production — native KMS must be used for real releases.
  --verify-only  Check that an existing payload matches the DEX in an APK. No encryption.

Usage:
  # Production (requires native KMS bridge — see docs)
  LIANYU_PAYLOAD_KEY=$(cat key.bin | base64) \\
    python3 tools/package_shell_payload.py app-release.apk --output assets/lianyu_shell/

  # Dev/test with standalone AES
  LIANYU_PAYLOAD_KEY=$(openssl rand -base64 32) \\
    python3 tools/package_shell_payload.py app-release.apk --output assets/lianyu_shell/ --dev

  # Verify existing payload
  python3 tools/package_shell_payload.py app-release.apk --verify-only
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import struct
import sys
import zipfile
from pathlib import Path

METADATA_SIZE = 16
BLOCK_SIZE = 16
MANIFEST_FILENAME = "shell_payload_manifest.json"
PAYLOAD_FILENAME = "shell_payload.bin"
ASSET_DIR = "lianyu_shell"


def pkcs7_pad(data: bytes) -> bytes:
    pad_len = BLOCK_SIZE - (len(data) % BLOCK_SIZE)
    return data + bytes([pad_len] * pad_len)


def sha256_hex(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def extract_dex_from_apk(apk_path: Path) -> bytes:
    """Extract all classes*.dex entries from an APK, concatenated in order."""
    with zipfile.ZipFile(apk_path, "r") as z:
        dex_names = sorted(
            n for n in z.namelist() if n.startswith("classes") and n.endswith(".dex")
        )
        if not dex_names:
            raise SystemExit(f"ERROR: no classes*.dex found in {apk_path}")
        print(f"  Found {len(dex_names)} DEX files: {', '.join(dex_names)}")
        payload = b"".join(z.read(name) for name in dex_names)
        print(f"  Total DEX payload: {len(payload):,} bytes "
              f"(SHA-256: {sha256_hex(payload)[:16]}...)")
    return payload


def encrypt_dev(padded_payload: bytes, metadata: bytes) -> bytes:
    """Standalone AES-256-CBC encryptor for dev/test mode only.

    Uses SHA-256 of a known key as the AES key and metadata as IV.
    If LIANYU_PAYLOAD_KEY env var is set, uses that (base64-decoded, then SHA-256).
    Otherwise falls back to a hardcoded dev key matching KmsProvider.kt DEV_AES_KEY.
    """
    import base64
    key_b64 = os.environ.get("LIANYU_PAYLOAD_KEY")
    if key_b64:
        try:
            key_material = base64.b64decode(key_b64)
        except Exception:
            raise SystemExit("ERROR: LIANYU_PAYLOAD_KEY must be valid base64")
    else:
        key_material = b"LianYuOnePieceShellDevKey2025"

    aes_key = hashlib.sha256(key_material).digest()

    try:
        from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
    except ImportError:
        raise SystemExit(
            "ERROR: cryptography package required for --dev mode.\n"
            "  Install: pip install cryptography"
        )

    cipher = Cipher(algorithms.AES(aes_key), modes.CBC(metadata))
    encryptor = cipher.encryptor()
    ciphertext = encryptor.update(padded_payload) + encryptor.finalize()
    return ciphertext


def sign_manifest_content(manifest_json: str, dev_mode: bool) -> str:
    """Sign manifest content using AEAD (same key as payload encryption).

    The signature format matches TinkAeadProvider.signManifest():
      payload = "manifest:v1:" + SHA-256(manifest_content)
      signature = AES-GCM-encrypt(payload)  (AEAD-protected)

    In dev mode, uses LIANYU_PAYLOAD_KEY derived AES key.
    In production, the native KMS bridge handles this.
    """
    import base64 as b64

    manifest_hash = hashlib.sha256(manifest_json.encode()).digest()
    payload = b"manifest:v1:" + manifest_hash

    if dev_mode:
        key_b64 = os.environ.get("LIANYU_PAYLOAD_KEY")
        if key_b64:
            key_material = b64.b64decode(key_b64)
        else:
            key_material = b"LianYuOnePieceShellDevKey2025"
        aes_key = hashlib.sha256(key_material).digest()
        # Use a fixed nonce for the AEAD "signature" (deterministic for same key+content)
        nonce = hashlib.sha256(b"manifest_nonce" + key_material).digest()[:12]
        try:
            from cryptography.hazmat.primitives.ciphers.aead import AESGCM
            aesgcm = AESGCM(aes_key)
            signature = aesgcm.encrypt(nonce, payload, None)
        except ImportError:
            raise SystemExit("ERROR: cryptography package required for --dev mode")
    else:
        raise SystemExit(
            "ERROR: Production manifest signing requires native KMS bridge"
        )

    return b64.b64encode(signature).decode()


def build_payload(apk_path: Path, output_dir: Path, dev_mode: bool) -> None:
    """Build and write the encrypted shell payload."""
    dex_payload = extract_dex_from_apk(apk_path)

    # PKCS7 pad
    padded = pkcs7_pad(dex_payload)
    print(f"  Padded payload: {len(padded):,} bytes")

    # Generate metadata (salt + counter)
    metadata = os.urandom(8) + struct.pack(">Q", 1)
    assert len(metadata) == METADATA_SIZE

    # Encrypt
    print("  Encrypting payload...")
    if dev_mode:
        ciphertext = encrypt_dev(padded, metadata)
    else:
        raise SystemExit(
            "ERROR: Production payload encryption requires the native KMS bridge.\n"
            "  Use --dev for testing, or build the Java packager tool.\n"
            "  See: tools/package_shell_payload_native/ for the JNI-based packager."
        )

    print(f"  Ciphertext: {len(ciphertext):,} bytes")

    # Build final payload: metadata + ciphertext
    encrypted_payload = metadata + ciphertext

    # Compute hashes
    plaintext_sha256 = sha256_hex(padded)
    ciphertext_sha256 = sha256_hex(ciphertext)
    print(f"  Plaintext SHA-256:  {plaintext_sha256}")
    print(f"  Ciphertext SHA-256: {ciphertext_sha256}")

    # Build manifest content (without hmac)
    manifest = {
        "version": 1,
        "ciphertext_sha256": ciphertext_sha256,
        "plaintext_sha256": plaintext_sha256,
        "metadata_size": METADATA_SIZE,
        "payload_size": len(dex_payload),
        "padded_size": len(padded),
    }
    manifest_json = json.dumps(manifest, indent=2, sort_keys=True)

    # Sign manifest with AEAD (same key as payload, via HMAC-equivalent)
    hmac_b64 = sign_manifest_content(manifest_json, dev_mode)
    signed_manifest = {"hmac": hmac_b64, **manifest}
    signed_json = json.dumps(signed_manifest, indent=2, sort_keys=True)

    manifest_dir = output_dir / ASSET_DIR
    manifest_dir.mkdir(parents=True, exist_ok=True)

    manifest_path = manifest_dir / MANIFEST_FILENAME
    manifest_path.write_text(signed_json)
    print(f"  Manifest written (signed): {manifest_path}")

    # Write encrypted payload
    payload_path = manifest_dir / PAYLOAD_FILENAME
    payload_path.write_bytes(encrypted_payload)
    print(f"  Payload written: {payload_path} ({len(encrypted_payload):,} bytes)")


def verify_payload(apk_path: Path) -> None:
    """Verify that an existing payload matches the DEX in the APK."""
    dex_payload = extract_dex_from_apk(apk_path)
    padded = pkcs7_pad(dex_payload)

    asset_dir = Path("app/src/main/assets") / ASSET_DIR
    manifest_path = asset_dir / MANIFEST_FILENAME
    payload_path = asset_dir / PAYLOAD_FILENAME

    if not manifest_path.exists():
        raise SystemExit(f"ERROR: Manifest not found: {manifest_path}")
    if not payload_path.exists():
        raise SystemExit(f"ERROR: Payload not found: {payload_path}")

    manifest = json.loads(manifest_path.read_text())
    hmac_b64 = manifest.pop("hmac", None)
    if not hmac_b64:
        raise SystemExit("ERROR: Manifest missing hmac signature")

    # Verify manifest signature (dev mode only — production needs native)
    manifest_json = json.dumps(manifest, indent=2, sort_keys=True)
    expected_hmac = sign_manifest_content(manifest_json, dev_mode=True)
    if hmac_b64 != expected_hmac:
        raise SystemExit("ERROR: Manifest signature verification failed")

    encrypted = payload_path.read_bytes()

    # Verify structure
    if len(encrypted) <= METADATA_SIZE:
        raise SystemExit("ERROR: Payload too small (no metadata)")
    metadata = encrypted[:METADATA_SIZE]
    ciphertext = encrypted[METADATA_SIZE:]

    # Verify hashes
    actual_plaintext = sha256_hex(padded)
    actual_ciphertext = sha256_hex(ciphertext)

    expected_plaintext = manifest.get("plaintext_sha256", "")
    expected_ciphertext = manifest.get("ciphertext_sha256", "")

    errors = []
    if actual_ciphertext != expected_ciphertext:
        errors.append(
            f"Ciphertext SHA-256 mismatch:\n"
            f"  expected: {expected_ciphertext}\n"
            f"  actual:   {actual_ciphertext}"
        )
    if actual_plaintext != expected_plaintext:
        errors.append(
            f"Plaintext SHA-256 mismatch:\n"
            f"  expected: {expected_plaintext}\n"
            f"  actual:   {actual_plaintext}\n"
            f"  (DEX content changed — rebuild payload)"
        )

    if errors:
        for e in errors:
            print(f"FAIL: {e}")
        raise SystemExit(1)

    print(f"OK: Payload verified — hashes match")
    print(f"  Metadata: {len(metadata)} bytes")
    print(f"  Ciphertext: {len(ciphertext):,} bytes")
    print(f"  Plaintext (padded): {len(padded):,} bytes")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Package LianYu shell payload"
    )
    parser.add_argument("apk", type=Path, help="Signed release APK")
    parser.add_argument(
        "--output", type=Path, default=Path("app/src/main/assets"),
        help="Output directory for assets (default: app/src/main/assets)"
    )
    parser.add_argument(
        "--dev", action="store_true",
        help="Use standalone AES-256-CBC encryptor (NOT for production)"
    )
    parser.add_argument(
        "--verify-only", action="store_true",
        help="Verify existing payload matches APK DEX, no encryption"
    )
    args = parser.parse_args()

    if not args.apk.exists():
        raise SystemExit(f"ERROR: APK not found: {args.apk}")

    if args.verify_only:
        verify_payload(args.apk)
    else:
        build_payload(args.apk, args.output, args.dev)


if __name__ == "__main__":
    main()
