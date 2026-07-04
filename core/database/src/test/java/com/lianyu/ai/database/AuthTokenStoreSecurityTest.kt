package com.lianyu.ai.database

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AuthTokenStoreSecurityTest {
    private val projectRoot = generateSequence(File(".").canonicalFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun auth_token_store_migrates_legacy_plaintext_auth_prefs() {
        val source = File(
            projectRoot,
            "core/database/src/main/java/com/lianyu/ai/database/repository/AuthTokenStore.kt"
        ).readText()

        assertTrue(source.contains("LEGACY_PREFS_NAME"))
        assertTrue(source.contains("migrateLegacyPlaintextPrefs"))
        assertTrue(source.contains("appContext.getSharedPreferences(LEGACY_PREFS_NAME"))
        assertTrue(source.contains("legacyPrefs.edit().clear().apply()"))
    }
}
