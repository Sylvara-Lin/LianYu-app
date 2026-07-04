package com.lianyu.ai

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseApkBlackboxAuditTest {
    private val projectRoot: File = generateSequence(File(".").canonicalFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun releaseVerifierBlocksHighSignalBusinessAndSecuritySymbols() {
        val verifier = File(projectRoot, "tools/verify_release_apk.py").readText()

        listOf(
            "BLACKBOX_DEX_PATTERNS",
            "ChatMessageCrypto",
            "ApiConfigSecretCodec",
            "RequestSecurityInterceptor",
            "M0",
            "A0",
            "blackbox-sensitive symbol found in release DEX"
        ).forEach { token ->
            assertTrue("release APK verifier must block $token", verifier.contains(token))
        }
    }
}
