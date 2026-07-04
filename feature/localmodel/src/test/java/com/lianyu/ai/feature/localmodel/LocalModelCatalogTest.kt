package com.lianyu.ai.feature.localmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelCatalogTest {
    @Test
    fun catalogContainsBothDownloadableModels() {
        assertEquals(listOf(LocalModel.Gemma4E2B, LocalModel.Gemma3_1B), LocalModelCatalog.all)
    }

    @Test
    fun catalogFallsBackToDefaultModelForUnknownId() {
        assertEquals(LocalModel.Gemma4E2B, LocalModelCatalog.findById("missing"))
    }

    @Test
    fun gemma4E2BUsesPinnedModelScopeDownload() {
        val model = LocalModel.Gemma4E2B

        assertEquals("gemma_4_e2b", model.id)
        assertEquals("Gemma 4 E2B", model.displayName)
        assertEquals("gemma-4-E2B-it.litertlm", model.fileName)
        assertTrue(model.downloadUrl.startsWith("https://modelscope.cn/models/"))
        assertTrue(model.downloadUrl.contains("/resolve/1bacc155f57965ae45832a9ccfe96cdfcb0e94e9/"))
        assertEquals("181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c", model.sha256)
        assertEquals(2_588_147_712L, model.expectedBytes)
    }

    @Test
    fun gemma3_1BUsesPinnedModelScopeDownload() {
        val model = LocalModel.Gemma3_1B

        assertEquals("gemma_3_1b", model.id)
        assertEquals("Gemma 3 1B Int4", model.displayName)
        assertEquals("gemma3-1b-it-int4.litertlm", model.fileName)
        assertTrue(model.downloadUrl.startsWith("https://modelscope.cn/models/"))
        assertTrue(model.downloadUrl.contains("/resolve/ea05e64beb281629d682a58fdf681d0c5116f93f/"))
        assertEquals("1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be", model.sha256)
        assertEquals(584_417_280L, model.expectedBytes)
    }
}
