package com.lianyu.ai.database

import com.lianyu.ai.database.dao.CompanionDao
import com.lianyu.ai.database.model.CompanionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCompanionSeederTest {
    @Test
    fun defaultTestCompanionHasStableTestPersona() {
        val companion = DefaultCompanionSeeder.createDefaultTestCompanion(now = 123L)

        assertEquals("小鱼", companion.name)
        assertEquals(123L, companion.createdAt)
        assertEquals(123L, companion.updatedAt)
        assertTrue(companion.personality.contains("体验角色"))
        assertTrue(companion.tags.orEmpty().contains(DefaultCompanionSeeder.defaultExperienceCompanionTag))
    }

    @Test
    fun insertsDefaultTestCompanionWhenItIsMissing() = runBlocking {
        val dao = FakeCompanionDao(existing = emptyList())

        val insertedId = DefaultCompanionSeeder.ensureDefaultTestCompanion(dao)

        assertEquals(42L, insertedId)
        assertEquals(1, dao.inserted.size)
        assertEquals("小鱼", dao.inserted.single().name)
    }

    @Test
    fun doesNotInsertDuplicateWhenDefaultTestCompanionAlreadyExists() = runBlocking {
        val existing = DefaultCompanionSeeder.createDefaultTestCompanion(now = 123L).copy(id = 7L)
        val dao = FakeCompanionDao(existing = listOf(existing))

        val insertedId = DefaultCompanionSeeder.ensureDefaultTestCompanion(dao)

        assertNull(insertedId)
        assertTrue(dao.inserted.isEmpty())
    }

    private class FakeCompanionDao(
        private val existing: List<CompanionEntity>
    ) : CompanionDao {
        val inserted = mutableListOf<CompanionEntity>()

        override fun getAllCompanions(): Flow<List<CompanionEntity>> = flowOf(existing + inserted)

        override suspend fun getCompanionById(id: Long): CompanionEntity? =
            (existing + inserted).firstOrNull { it.id == id }

        override fun getCompanionByIdFlow(id: Long): Flow<CompanionEntity?> =
            flowOf((existing + inserted).firstOrNull { it.id == id })

        override suspend fun getAllCompanionsSync(): List<CompanionEntity> = existing + inserted

        override suspend fun insertCompanion(companion: CompanionEntity): Long {
            inserted += companion.copy(id = 42L)
            return 42L
        }

        override suspend fun updateCompanion(companion: CompanionEntity): Int = 0

        override suspend fun deleteCompanion(companion: CompanionEntity): Int = 0

        override suspend fun updateTimestamp(id: Long, timestamp: Long): Int = 0

        override suspend fun increaseIntimacy(id: Long, amount: Int): Int = 0

        override suspend fun getIntimacy(id: Long): Int? = null
    }
}
