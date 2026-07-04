package com.lianyu.ai.database

import com.lianyu.ai.database.dao.ApiConfigDao
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ApiConfigDaoQueryTest {
    private val projectRoot = generateSequence(File(".").canonicalFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun activeConfigPrefersNewestEnabledConfiguredApi() {
        val daoSource = File(
            projectRoot,
            "core/database/src/main/java/com/lianyu/ai/database/dao/ApiConfigDao.kt"
        ).readText()

        assertTrue(
            "聊天发消息必须选择最新启用且 apiKey 非空的配置，避免命中旧配置/空配置后收不到模型回复",
            daoSource.contains(
                "SELECT * FROM api_configs WHERE apiKey IS NOT NULL AND apiKey != '' AND isEnabled = 1 ORDER BY id DESC LIMIT 1"
            )
        )
    }
}
