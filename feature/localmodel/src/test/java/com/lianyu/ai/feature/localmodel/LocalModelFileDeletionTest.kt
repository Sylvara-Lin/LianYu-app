package com.lianyu.ai.feature.localmodel

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelFileDeletionTest {
    @Test
    fun missingFileIsAlreadyDeleted() {
        val dir = Files.createTempDirectory("local-model-delete").toFile()
        try {
            val file = File(dir, "missing.litertlm")

            val result = LocalModelFileDeletion.delete(file)

            assertTrue(result.deleted)
            assertNull(result.errorMessage)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun existingFileIsDeletedAndVerified() {
        val dir = Files.createTempDirectory("local-model-delete").toFile()
        try {
            val file = File(dir, "model.litertlm").apply {
                writeText("model")
            }

            val result = LocalModelFileDeletion.delete(file)

            assertTrue(result.deleted)
            assertFalse(file.exists())
            assertNull(result.errorMessage)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun failedDeleteReportsFailureWhenFileStillExists() {
        val dir = Files.createTempDirectory("local-model-delete").toFile()
        try {
            val file = File(dir, "model.litertlm").apply {
                writeText("model")
            }

            val result = LocalModelFileDeletion.delete(file) { false }

            assertFalse(result.deleted)
            assertTrue(file.exists())
            assertTrue(result.errorMessage?.contains("Could not delete") == true)
        } finally {
            dir.deleteRecursively()
        }
    }
}
