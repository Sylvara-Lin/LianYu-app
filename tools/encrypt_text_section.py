#!/usr/bin/env python3
"""Stub: skip .text encryption for debug builds. Runtime skips decryption when magic=0."""
import sys

so_path = sys.argv[1] if len(sys.argv) > 1 else ""
cert_seed = sys.argv[2] if len(sys.argv) > 2 else "0x00000000"
print(f"   [stub] encrypt_text_section: skipping {so_path} (seed={cert_seed})")
# No-op: runtime will detect unencrypted .text and skip MAP_FIXED decryption