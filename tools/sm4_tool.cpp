/*
 * sm4_tool — Standalone SM4-ECB encrypt/decrypt + CRC32
 * =========================================================
 * Uses the EXACT same SM4 primitives as dex-packer.cpp
 * (SBOX, FK, CK, T function, key expansion, CRC32 table).
 *
 * Build:  g++ -std=c++17 -O3 -o /tmp/sm4_tool tools/sm4_tool.cpp
 *
 * Usage:
 *   sm4_tool enc <in_file> <out_file> <key_hex>
 *     → SM4-ECB encrypt, prints CRC32(plaintext) to stderr
 *
 *   sm4_tool dec <in_file> <out_file> <key_hex>
 *     → SM4-ECB decrypt
 *
 *   sm4_tool genkey
 *     → Generate random 16-byte key, print hex to stdout
 */

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cstdint>
#include <ctime>

/* ============================================================
 * SM4 constants — MUST match dex-packer.cpp byte-for-byte
 * ============================================================ */

static const uint8_t SM4_SBOX[256] = {
    0xd6,0x90,0xe9,0xfe,0xcc,0xe1,0x3d,0xb7,0x16,0xb6,0x14,0xc2,0x28,0xfb,0x2c,0x05,
    0x2b,0x67,0x9a,0x76,0x2a,0xbe,0x04,0xc3,0xaa,0x44,0x13,0x26,0x49,0x86,0x06,0x99,
    0x9c,0x42,0x50,0xf4,0x91,0xef,0x98,0x7a,0x33,0x54,0x0b,0x43,0xed,0xcf,0xac,0x62,
    0xe4,0xb3,0x1c,0xa9,0xc9,0x08,0xe8,0x95,0x80,0xdf,0x94,0xfa,0x75,0x8f,0x3f,0xa6,
    0x47,0x07,0xa7,0xfc,0xf3,0x73,0x17,0xba,0x83,0x59,0x3c,0x19,0xe6,0x85,0x4f,0xa8,
    0x68,0x6b,0x81,0xb2,0x71,0x64,0xda,0x8b,0xf8,0xeb,0x0f,0x4b,0x70,0x56,0x9d,0x35,
    0x1e,0x24,0x0e,0x5e,0x63,0x58,0xd1,0xa2,0x25,0x22,0x7c,0x3b,0x01,0x21,0x78,0x87,
    0xd4,0x00,0x46,0x57,0x9f,0xd3,0x27,0x52,0x4c,0x36,0x02,0xe7,0xa0,0xc4,0xc8,0x9e,
    0xea,0xbf,0x8a,0xd2,0x40,0xc7,0x38,0xb5,0xa3,0xf7,0xf2,0xce,0xf9,0x61,0x15,0xa1,
    0xe0,0xae,0x5d,0xa4,0x9b,0x34,0x1a,0x55,0xad,0x93,0x32,0x30,0xf5,0x8c,0xb1,0xe3,
    0x1d,0xf6,0xe2,0x2e,0x82,0x66,0xca,0x60,0xc0,0x29,0x23,0xab,0x0d,0x53,0x4e,0x6f,
    0xd5,0xdb,0x37,0x45,0xde,0xfd,0x8e,0x2f,0x03,0xff,0x6a,0x72,0x6d,0x6c,0x5b,0x51,
    0x8d,0x1b,0xaf,0x92,0xbb,0xdd,0xbc,0x7f,0x11,0xd9,0x5c,0x41,0x1f,0x10,0x5a,0xd8,
    0x0a,0xc1,0x31,0x88,0xa5,0xcd,0x7b,0xbd,0x2d,0x74,0xd0,0x12,0xb8,0xe5,0xb4,0xb0,
    0x89,0x69,0x97,0x4a,0x0c,0x96,0x77,0x7e,0x65,0xb9,0xf1,0x09,0xc5,0x6e,0xc6,0x84,
    0x18,0xf0,0x7d,0xec,0x3a,0xdc,0x4d,0x20,0x79,0xee,0x5f,0x3e,0xd7,0xcb,0x39,0x48
};

static inline uint32_t sm4_rotl(uint32_t x, int n) {
    return (x << n) | (x >> (32 - n));
}

static uint32_t sm4_t(uint32_t x) {
    uint32_t t = 0;
    t  = SM4_SBOX[(x >> 24) & 0xFF]; t <<= 8;
    t |= SM4_SBOX[(x >> 16) & 0xFF]; t <<= 8;
    t |= SM4_SBOX[(x >> 8)  & 0xFF]; t <<= 8;
    t |= SM4_SBOX[ x        & 0xFF];
    return t ^ sm4_rotl(t, 13) ^ sm4_rotl(t, 23);
}

static void sm4_key_expand(const uint8_t key[16], uint32_t rk[32]) {
    const uint32_t FK[4] = {0xa3b1bac6, 0x56aa3350, 0x677d9197, 0xb27022dc};
    const uint32_t CK[32] = {
        0x00070e15,0x1c232a31,0x383f464d,0x545b6269,0x70777e85,0x8c939aa1,0xa8afb6bd,0xc4cbd2d9,
        0xe0e7eef5,0xfc030a11,0x181f262d,0x343b4249,0x50575e65,0x6c737a81,0x888f969d,0xa4abb2b9,
        0xc0c7ced5,0xdce3eaf1,0xf8ff060d,0x141b2229,0x30373e45,0x4c535a61,0x686f767d,0x848b9299,
        0xa0a7aeb5,0xbcc3cad1,0xd8dfe6ed,0xf4fb0209,0x10171e25,0x2c333a41,0x484f565d,0x646b7279
    };
    uint32_t mk[4], k[36];
    for (int i = 0; i < 4; i++)
        mk[i] = ((uint32_t)key[4*i] << 24) | ((uint32_t)key[4*i+1] << 16)
              | ((uint32_t)key[4*i+2] << 8)  |  key[4*i+3];
    for (int i = 0; i < 4; i++) k[i] = mk[i] ^ FK[i];
    for (int i = 0; i < 32; i++) {
        k[i+4] = k[i] ^ sm4_t(k[i+1] ^ k[i+2] ^ k[i+3] ^ CK[i]);
        rk[i] = k[i+4];
    }
}

/** SM4 encrypt one 16-byte block. rk is used forward (rk[0..31]). */
static void sm4_enc_one(const uint8_t in[16], const uint32_t rk[32], uint8_t out[16]) {
    uint32_t X[36];
    for (int i = 0; i < 4; i++)
        X[i] = ((uint32_t)in[4*i] << 24) | ((uint32_t)in[4*i+1] << 16)
             | ((uint32_t)in[4*i+2] << 8)  |  in[4*i+3];
    for (int i = 0; i < 32; i++)
        X[i+4] = X[i] ^ sm4_t(X[i+1] ^ X[i+2] ^ X[i+3] ^ rk[i]);
    for (int i = 0; i < 4; i++) {
        out[4*i]   = (X[35-i] >> 24) & 0xFF;
        out[4*i+1] = (X[35-i] >> 16) & 0xFF;
        out[4*i+2] = (X[35-i] >> 8)  & 0xFF;
        out[4*i+3] =  X[35-i]        & 0xFF;
    }
}

/** SM4 decrypt one 16-byte block. rk is used reversed (rk[31..0]).
 *  MUST match dex-packer.cpp sm4_dec_one exactly. */
static void sm4_dec_one(const uint8_t in[16], const uint32_t rk[32], uint8_t out[16]) {
    uint32_t X[36];
    for (int i = 0; i < 4; i++)
        X[i] = ((uint32_t)in[4*i] << 24) | ((uint32_t)in[4*i+1] << 16)
             | ((uint32_t)in[4*i+2] << 8)  |  in[4*i+3];
    for (int i = 0; i < 32; i++)
        X[i+4] = X[i] ^ sm4_t(X[i+1] ^ X[i+2] ^ X[i+3] ^ rk[31 - i]);
    for (int i = 0; i < 4; i++) {
        out[4*i]   = (X[35-i] >> 24) & 0xFF;
        out[4*i+1] = (X[35-i] >> 16) & 0xFF;
        out[4*i+2] = (X[35-i] >> 8)  & 0xFF;
        out[4*i+3] =  X[35-i]        & 0xFF;
    }
}

/* ============================================================
 * CRC32 — MUST match dex-packer.cpp crc32_u32 exactly
 * ============================================================ */

static uint32_t crc32_u32(uint32_t crc, const uint8_t* data, size_t len) {
    static const uint32_t table[256] = {
        0x00000000,0x77073096,0xEE0E612C,0x990951BA,0x076DC419,0x706AF48F,0xE963A535,0x9E6495A3,
        0x0EDB8832,0x79DCB8A4,0xE0D5E91E,0x97D2D988,0x09B64C2B,0x7EB17CBD,0xE7B82D07,0x90BF1D91,
        0x1DB71064,0x6AB020F2,0xF3B97148,0x84BE41DE,0x1ADAD47D,0x6DDDE4EB,0xF4D4B551,0x83D385C7,
        0x136C9856,0x646BA8C0,0xFD62F97A,0x8A65C9EC,0x14015C4F,0x63066CD9,0xFA0F3D63,0x8D080DF5,
        0x3B6E20C8,0x4C69105E,0xD56041E4,0xA2677172,0x3C03E4D1,0x4B04D447,0xD20D85FD,0xA50AB56B,
        0x35B5A8FA,0x42B2986C,0xDBBBC9D6,0xACBCF940,0x32D86CE3,0x45DF5C75,0xDCD60DCF,0xABD13D59,
        0x26D930AC,0x51DE003A,0xC8D75180,0xBFD06116,0x21B4F4B5,0x56B3C423,0xCFBA9599,0xB8BDA50F,
        0x2802B89E,0x5F058808,0xC60CD9B2,0xB10BE924,0x2F6F7C87,0x58684C11,0xC1611DAB,0xB6662D3D,
        0x76DC4190,0x01DB7106,0x98D220BC,0xEFD5102A,0x71B18589,0x06B6B51F,0x9FBFE4A5,0xE8B8D433,
        0x7807C9A2,0x0F00F934,0x9609A88E,0xE10E9818,0x7F6A0DBB,0x086D3D2D,0x91646C97,0xE6635C01,
        0x6B6B51F4,0x1C6C6162,0x856530D8,0xF262004E,0x6C0695ED,0x1B01A57B,0x8208F4C1,0xF50FC457,
        0x65B0D9C6,0x12B7E950,0x8BBEB8EA,0xFCB9887C,0x62DD1DDF,0x15DA2D49,0x8CD37CF3,0xFBD44C65,
        0x4DB26158,0x3AB551CE,0xA3BC0074,0xD4BB30E2,0x4ADFA541,0x3DD895D7,0xA4D1C46D,0xD3D6F4FB,
        0x4369E96A,0x346ED9FC,0xAD678846,0xDA60B8D0,0x44042D73,0x33031DE5,0xAA0A4C5F,0xDD0D7CC9,
        0x5005713C,0x270241AA,0xBE0B1010,0xC90C2086,0x5768B525,0x206F85B3,0xB966D409,0xCE61E49F,
        0x5EDEF90E,0x29D9C998,0xB0D09822,0xC7D7A8B4,0x59B33D17,0x2EB40D81,0xB7BD5C3B,0xC0BA6CAD,
        0xEDB88320,0x9ABFB3B6,0x03B6E20C,0x74B1D29A,0xEAD54739,0x9DD277AF,0x04DB2615,0x73DC1683,
        0xE3630B12,0x94643B84,0x0D6D6A3E,0x7A6A5AA8,0xE40ECF0B,0x9309FF9D,0x0A00AE27,0x7D079EB1,
        0xF00F9344,0x8708A3D2,0x1E01F268,0x6906C2FE,0xF762575D,0x806567CB,0x196C3671,0x6E6B06E7,
        0xFED41B76,0x89D32BE0,0x10DA7A5A,0x67DD4ACC,0xF9B9DF6F,0x8EBEEFF9,0x17B7BE43,0x60B08ED5,
        0xD6D6A3E8,0xA1D1937E,0x38D8C2C4,0x4FDFF252,0xD1BB67F1,0xA6BC5767,0x3FB506DD,0x48B2364B,
        0xD80D2BDA,0xAF0A1B4C,0x36034AF6,0x41047A60,0xDF60EFC3,0xA867DF55,0x316E8EEF,0x4669BE79,
        0xCB61B38C,0xBC66831A,0x256FD2A0,0x5268E236,0xCC0C7795,0xBB0B4703,0x220216B9,0x5505262F,
        0xC5BA3BBE,0xB2BD0B28,0x2BB45A92,0x5CB36A04,0xC2D7FFA7,0xB5D0CF31,0x2CD99E8B,0x5BDEAE1D,
        0x9B64C2B0,0xEC63F226,0x756AA39C,0x026D930A,0x9C0906A9,0xEB0E363F,0x72076785,0x05005713,
        0x95BF4A82,0xE2B87A14,0x7BB12BAE,0x0CB61B38,0x92D28E9B,0xE5D5BE0D,0x7CDCEFB7,0x0BDBDF21,
        0x86D3D2D4,0xF1D4E242,0x68DDB3F8,0x1FDA836E,0x81BE16CD,0xF6B9265B,0x6FB077E1,0x18B74777,
        0x88085AE6,0xFF0F6A70,0x66063BCA,0x11010B5C,0x8F659EFF,0xF862AE69,0x616BFFD3,0x166CCF45,
        0xA00AE278,0xD70DD2EE,0x4E048354,0x3903B3C2,0xA7672661,0xD06016F7,0x4969474D,0x3E6E77DB,
        0xAED16A4A,0xD9D65ADC,0x40DF0B66,0x37D83BF0,0xA9BCAE53,0xDEBB9EC5,0x47B2CF7F,0x30B5FFE9,
        0xBDBDF21C,0xCABAC28A,0x53B39330,0x24B4A3A6,0xBAD03605,0xCDD70693,0x54DE5729,0x23D967BF,
        0xB3667A2E,0xC4614AB8,0x5D681B02,0x2A6F2B94,0xB40BBE37,0xC30C8EA1,0x5A05DF1B,0x2D02EF8D
};;
    crc ^= 0xFFFFFFFF;
    for (size_t i = 0; i < len; i++)
        crc = table[(crc ^ data[i]) & 0xFF] ^ (crc >> 8);
    return crc ^ 0xFFFFFFFF;
}

/* ============================================================
 * File I/O helpers
 * ============================================================ */

static uint8_t* read_file(const char* path, size_t* out_len) {
    FILE* f = fopen(path, "rb");
    if (!f) { fprintf(stderr, "ERROR: cannot open %s\n", path); return nullptr; }
    fseek(f, 0, SEEK_END);
    long sz = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (sz <= 0) { fclose(f); return nullptr; }
    uint8_t* buf = (uint8_t*)malloc(sz);
    if (!buf) { fclose(f); return nullptr; }
    size_t n = fread(buf, 1, sz, f);
    fclose(f);
    *out_len = n;
    return buf;
}

static int write_file(const char* path, const uint8_t* data, size_t len) {
    FILE* f = fopen(path, "wb");
    if (!f) { fprintf(stderr, "ERROR: cannot write %s\n", path); return -1; }
    fwrite(data, 1, len, f);
    fclose(f);
    return 0;
}

static int parse_key(const char* hex, uint8_t key[16]) {
    if (strlen(hex) != 32) {
        fprintf(stderr, "ERROR: key must be 32 hex chars\n");
        return -1;
    }
    for (int i = 0; i < 16; i++) {
        unsigned int byte;
        if (sscanf(hex + 2*i, "%2x", &byte) != 1) {
            fprintf(stderr, "ERROR: invalid hex at position %d\n", 2*i);
            return -1;
        }
        key[i] = (uint8_t)byte;
    }
    return 0;
}

static void gen_random_key(uint8_t key[16]) {
    FILE* ur = fopen("/dev/urandom", "rb");
    if (!ur) {
        /* fallback: poor man's random */
        srand((unsigned)time(nullptr));
        for (int i = 0; i < 16; i++) key[i] = (uint8_t)(rand() & 0xFF);
        return;
    }
    fread(key, 1, 16, ur);
    fclose(ur);
}

/* ============================================================
 * Operations
 * ============================================================ */

static int do_encrypt(const char* in_path, const char* out_path, const uint8_t key[16]) {
    size_t plain_len;
    uint8_t* plain = read_file(in_path, &plain_len);
    if (!plain) return 1;

    /* Pad to 16-byte boundary (PKCS-style zero padding) */
    size_t pad = (16 - (plain_len % 16)) % 16;
    size_t padded_len = plain_len + pad;
    uint8_t* padded = (uint8_t*)malloc(padded_len);
    memcpy(padded, plain, plain_len);
    if (pad) memset(padded + plain_len, 0, pad);

    /* CRC32 of padded plaintext — must match runtime verification */
    uint32_t crc = crc32_u32(0, padded, padded_len);

    /* Key expansion */
    uint32_t rk[32];
    sm4_key_expand(key, rk);

    /* SM4-ECB encrypt */
    uint8_t* cipher = (uint8_t*)malloc(padded_len);
    for (size_t i = 0; i < padded_len; i += 16) {
        sm4_enc_one(padded + i, rk, cipher + i);
    }

    /* Write ciphertext */
    int rc = write_file(out_path, cipher, padded_len);

    /* Print info to stderr */
    fprintf(stderr, "plain_len=%zu pad=%zu padded=%zu CRC32=0x%08X\n",
            plain_len, pad, padded_len, crc);

    /* Print CRC32 to stdout for script consumption */
    printf("0x%08X\n", crc);

    free(plain);
    free(padded);
    free(cipher);
    /* Zero sensitive key material */
    memset(rk, 0, sizeof(rk));
    return rc;
}

static int do_decrypt(const char* in_path, const char* out_path, const uint8_t key[16]) {
    size_t cipher_len;
    uint8_t* cipher = read_file(in_path, &cipher_len);
    if (!cipher) return 1;

    if (cipher_len % 16 != 0) {
        fprintf(stderr, "ERROR: ciphertext length %zu not multiple of 16\n", cipher_len);
        free(cipher);
        return 1;
    }

    uint32_t rk[32];
    sm4_key_expand(key, rk);

    uint8_t* plain = (uint8_t*)malloc(cipher_len);
    for (size_t i = 0; i < cipher_len; i += 16) {
        sm4_dec_one(cipher + i, rk, plain + i);
    }

    int rc = write_file(out_path, plain, cipher_len);

    free(cipher);
    free(plain);
    memset(rk, 0, sizeof(rk));
    return rc;
}

/* ============================================================
 * Main
 * ============================================================ */

static void usage(const char* prog) {
    fprintf(stderr,
        "sm4_tool — SM4-ECB encrypt/decrypt (matches dex-packer.cpp)\n"
        "\n"
        "Usage:\n"
        "  %s enc <in> <out> <key_hex>   Encrypt file, print CRC32 to stdout\n"
        "  %s dec <in> <out> <key_hex>   Decrypt file\n"
        "  %s genkey                      Generate random 16-byte key (hex)\n"
        "  %s verify <original> <encrypted> <key_hex>\n"
        "                                 Encrypt→decrypt round-trip check\n"
        "\n"
        "CRC32 printed to stdout (for script capture).\n"
        "All other info to stderr.\n",
        prog, prog, prog, prog);
}

int main(int argc, char** argv) {
    if (argc < 2) { usage(argv[0]); return 1; }

    const char* cmd = argv[1];

    if (strcmp(cmd, "genkey") == 0) {
        uint8_t key[16];
        gen_random_key(key);
        for (int i = 0; i < 16; i++) printf("%02x", key[i]);
        printf("\n");
        return 0;
    }

    if (strcmp(cmd, "enc") == 0) {
        if (argc != 5) { usage(argv[0]); return 1; }
        uint8_t key[16];
        if (parse_key(argv[4], key) != 0) return 1;
        return do_encrypt(argv[2], argv[3], key);
    }

    if (strcmp(cmd, "dec") == 0) {
        if (argc != 5) { usage(argv[0]); return 1; }
        uint8_t key[16];
        if (parse_key(argv[4], key) != 0) return 1;
        return do_decrypt(argv[2], argv[3], key);
    }

    if (strcmp(cmd, "verify") == 0) {
        if (argc != 5) { usage(argv[0]); return 1; }
        uint8_t key[16];
        if (parse_key(argv[4], key) != 0) return 1;

        /* Read original plaintext */
        size_t orig_len;
        uint8_t* orig = read_file(argv[2], &orig_len);
        if (!orig) return 1;

        /* Pad to 16 */
        size_t pad = (16 - (orig_len % 16)) % 16;
        size_t padded_len = orig_len + pad;
        uint8_t* padded = (uint8_t*)malloc(padded_len);
        memcpy(padded, orig, orig_len);
        if (pad) memset(padded + orig_len, 0, pad);

        /* Encrypt with our SM4 */
        uint32_t rk[32];
        sm4_key_expand(key, rk);
        uint8_t* our_enc = (uint8_t*)malloc(padded_len);
        for (size_t i = 0; i < padded_len; i += 16)
            sm4_enc_one(padded + i, rk, our_enc + i);

        /* Decrypt back */
        uint8_t* dec = (uint8_t*)malloc(padded_len);
        for (size_t i = 0; i < padded_len; i += 16)
            sm4_dec_one(our_enc + i, rk, dec + i);

        /* Compare round-trip */
        int rt_ok = (memcmp(padded, dec, padded_len) == 0);
        printf("Round-trip: %s\n", rt_ok ? "PASS" : "FAIL");

        /* Read reference encrypted file and compare */
        size_t ref_len;
        uint8_t* ref_enc = read_file(argv[3], &ref_len);
        if (ref_enc) {
            int enc_match = (ref_len == padded_len) && (memcmp(our_enc, ref_enc, padded_len) == 0);
            printf("Enc-match:  %s (%zu vs %zu bytes)\n",
                   enc_match ? "PASS" : "FAIL", padded_len, ref_len);
            if (!enc_match) {
                /* Show first differing block */
                for (size_t i = 0; i < padded_len; i += 16) {
                    if (memcmp(our_enc + i, ref_enc + i, 16) != 0) {
                        printf("  First diff at block %zu (offset 0x%zx)\n", i/16, i);
                        break;
                    }
                }
            }
            free(ref_enc);
        }

        uint32_t crc = crc32_u32(0, padded, padded_len);
        printf("CRC32:      0x%08X\n", crc);

        free(orig);
        free(padded);
        free(our_enc);
        free(dec);
        memset(rk, 0, sizeof(rk));
        return rt_ok ? 0 : 1;
    }

    usage(argv[0]);
    return 1;
}
