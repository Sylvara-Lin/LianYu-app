package com.lianyu.ai.common

import android.content.Context
import java.io.ByteArrayInputStream

/**
 * Transparent decryption of XOR-encrypted assets.
 *
 * Build-time: python3 tools/encrypt_assets.py <assets_dir>
 *     Original file → file.enc (XOR-encrypted), original deleted
 *
 * Runtime: EncryptedAssetLoader.load(context, "filename.json")
 *     → reads filename.json.enc, decrypts with embedded key, returns ByteArray
 */
object EncryptedAssetLoader {

    /** XOR key (32 bytes) — matches tools/encrypt_assets.py output */
    private val xorKey: ByteArray by lazy {
        // XOR-obfuscated key bytes to prevent plaintext extraction from DEX
        val obfuscated = byteArrayOf(
            0xe3.toByte(), 0x5f, 0x8a.toByte(), 0x2c, 0x71, 0xde.toByte(), 0x49, 0xb6.toByte(),
            0x07, 0x95.toByte(), 0xc8.toByte(), 0x3d, 0xaa.toByte(), 0x16, 0xef.toByte(), 0x54,
            0x88.toByte(), 0x21, 0xbe.toByte(), 0x4f, 0xd2.toByte(), 0x69, 0xfc.toByte(), 0x17,
            0xa5.toByte(), 0x30, 0xcd.toByte(), 0x7e, 0x13, 0xba.toByte(), 0x46, 0xe9.toByte()
        )
        val deobfuscate = byteArrayOf(
            0x67, 0x33, 0xe6.toByte(), 0x48, 0x1d, 0xb2.toByte(), 0x25, 0xd0.toByte(),
            0x6b, 0xf9.toByte(), 0xa4.toByte(), 0x59, 0xc6.toByte(), 0x7a, 0x83.toByte(), 0x30,
            0xec.toByte(), 0x4d, 0xd2.toByte(), 0x2b, 0xbe.toByte(), 0x05, 0x98.toByte(), 0x73,
            0xc1.toByte(), 0x5c, 0xa1.toByte(), 0x1a, 0x7f, 0xd6.toByte(), 0x22, 0x8d.toByte()
        )
        ByteArray(32) { i -> (obfuscated[i].toInt() xor deobfuscate[i].toInt()).toByte() }
    }

    /**
     * Load and decrypt an asset file.
     * @param context Application context
     * @param assetName Original filename (e.g., "content_filter_keywords.json")
     * @return Decrypted content as ByteArray, or null if not found
     */
    fun load(context: Context, assetName: String): ByteArray? {
        return try {
            val encrypted = context.assets.open("$assetName.enc").use { it.readBytes() }
            ByteArray(encrypted.size) { i ->
                (encrypted[i].toInt() xor (xorKey[i % xorKey.size].toInt() and 0xff)).toByte()
            }
        } catch (e: Exception) {
            // Fallback: try loading unencrypted version (dev mode)
            try {
                context.assets.open(assetName).use { it.readBytes() }
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Load and decrypt as String (UTF-8).
     */
    fun loadString(context: Context, assetName: String): String? {
        return load(context, assetName)?.toString(Charsets.UTF_8)
    }
}
