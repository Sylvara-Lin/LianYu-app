package com.lianyu.ai.database

import org.junit.Assert.assertFalse
import org.junit.Test

class AppDatabaseConfigurationTest {
    @Test
    fun databaseBuilderDoesNotMixExplicitMigrationsWithDestructiveFallback() {
        val source = java.io.File(
            "src/main/java/com/lianyu/ai/database/AppDatabase.kt"
        ).readText()

        assertFalse(
            "Room must not mix addMigrations() with fallbackToDestructiveMigrationFrom() for the same versions; it crashes when building the database.",
            source.contains("fallbackToDestructiveMigrationFrom(")
        )
    }
}
