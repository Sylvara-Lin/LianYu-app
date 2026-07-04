package com.lianyu.ai.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lianyu.ai.common.SecureLog
import com.lianyu.ai.database.dao.ApiConfigDao
import com.lianyu.ai.database.dao.ChatGroupDao
import com.lianyu.ai.database.dao.ChatMessageDao
import com.lianyu.ai.database.dao.CompanionDao
import com.lianyu.ai.database.dao.GroupMessageDao
import com.lianyu.ai.database.dao.KeywordDao
import com.lianyu.ai.database.dao.MemoryDao
import com.lianyu.ai.database.dao.QuizQuestionDao
import com.lianyu.ai.database.dao.TokenUsageDao
import com.lianyu.ai.database.model.ApiConfig
import com.lianyu.ai.database.model.ApiProvider
import com.lianyu.ai.database.model.ChatGroup
import com.lianyu.ai.database.model.ChatMessage
import com.lianyu.ai.database.model.CompanionEntity
import com.lianyu.ai.database.model.FileFormat
import com.lianyu.ai.database.model.GroupMessage
import com.lianyu.ai.database.model.KeywordEntity
import com.lianyu.ai.database.model.MemoryCategory
import com.lianyu.ai.database.model.MemoryEntry
import com.lianyu.ai.database.model.MessageType
import com.lianyu.ai.database.model.QuizQuestionEntity
import com.lianyu.ai.database.model.TempMemory
import com.lianyu.ai.database.model.TokenUsage
import java.io.File

@Database(
    entities = [
        CompanionEntity::class,
        ChatMessage::class,
        ApiConfig::class,
        MemoryEntry::class,
        TempMemory::class,
        ChatGroup::class,
        GroupMessage::class,
        KeywordEntity::class,
        QuizQuestionEntity::class,
        TokenUsage::class
    ],
    version = 19,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun companionDao(): CompanionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun apiConfigDao(): ApiConfigDao
    abstract fun memoryDao(): MemoryDao
    abstract fun chatGroupDao(): ChatGroupDao
    abstract fun groupMessageDao(): GroupMessageDao
    abstract fun keywordDao(): KeywordDao
    abstract fun quizQuestionDao(): QuizQuestionDao
    abstract fun tokenUsageDao(): TokenUsageDao

    companion object {
        private const val DB_NAME = "lianyu_database"
        private val LOCK = Any()

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(LOCK) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun resetForTest() {
            synchronized(LOCK) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        fun shutdown() {
            synchronized(LOCK) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            // [M6 FIX] autoBackupIfNeeded 做文件 copyTo IO，不应在 getDatabase 首次调用路径
            // （可能在主线程）同步执行。这里只构建数据库，备份交给 Application.bgScope 异步触发。
            return openVerifiedDatabase(context, allowRecovery = true)
        }

        private fun openVerifiedDatabase(context: Context, allowRecovery: Boolean): AppDatabase {
            var candidate: AppDatabase? = null
            return try {
                candidate = createDatabase(context)
                verifyDatabaseCanOpen(candidate)
                candidate
            } catch (e: Throwable) {
                runCatching { candidate?.close() }
                if (!allowRecovery) throw e

                // 提取异常链中的所有错误信息
                val messages = extractExceptionMessages(e)

                // 严格区分两种完全不同的情况：
                //   A) Schema Identity Hash 不匹配 → Entity 定义变了，需要迁移
                //   B) WAL/Journal 文件损坏 → 数据完整性问题，应修复而非重建
                val isRealSchemaMismatch = messages.any { it.contains("identity hash") }
                val isCorruption = messages.any { it.contains("cannot verify the data integrity") }

                when {
                    isRealSchemaMismatch -> {
                        // 真正的 Schema 变化：先尝试从备份恢复，恢复失败才重建
                        SecureLog.w("AppDatabase", "Schema identity hash mismatch detected")
                        backupBeforeRecovery(context)
                        if (tryRecoverFromBackup(context)) {
                            SecureLog.i("AppDatabase", "Database restored from recovery backup")
                            createDatabase(context).also { verifyDatabaseCanOpen(it) }
                        } else {
                            SecureLog.w("AppDatabase", "No valid backup, recreating database (data loss unavoidable)")
                            deleteDatabaseFiles(context)
                            createDatabase(context).also {
                                verifyDatabaseCanOpen(it)
                                SecureLog.i("AppDatabase", "Database recreated after schema mismatch")
                            }
                        }
                    }
                    isCorruption && !isRealSchemaMismatch -> {
                        // WAL/Journal 损坏：先尝试修复，绝不直接删库
                        SecureLog.w("AppDatabase", "Database corruption detected, attempting repair...")
                        backupBeforeRecovery(context)

                        // 尝试1：删除损坏的 WAL/SHM 文件让 SQLite 回退到主 DB
                        if (tryRepairWalFiles(context)) {
                            SecureLog.i("AppDatabase", "WAL repair succeeded")
                            runCatching { createDatabase(context).also { verifyDatabaseCanOpen(it) } }.getOrElse {
                                // WAL 修复后仍打不开，尝试从备份恢复
                                recoverDatabase(context)
                                createDatabase(context).also {
                                    verifyDatabaseCanOpen(it)
                                    SecureLog.i("AppDatabase", "Database recovered from backup after failed WAL repair")
                                }
                            }
                        } else {
                            // 尝试2：从备份恢复
                            recoverDatabase(context)
                            createDatabase(context).also {
                                verifyDatabaseCanOpen(it)
                                SecureLog.i("AppDatabase", "Database recovered from backup")
                            }
                        }
                    }
                    else -> {
                        // 其他未知错误：备份后尝试恢复
                        SecureLog.e("AppDatabase", "Unexpected database error: ${e.message}", e)
                        backupBeforeRecovery(context)
                        recoverDatabase(context)
                        createDatabase(context).also {
                            verifyDatabaseCanOpen(it)
                            SecureLog.i("AppDatabase", "Database recovered after unknown error")
                        }
                    }
                }
            }
        }

        /**
         * 提取异常链中所有层级的错误信息用于分类判断
         */
        private fun extractExceptionMessages(e: Throwable): List<String> {
            val messages = mutableListOf<String>()
            var current: Throwable? = e
            while (current != null) {
                current.message?.let { messages.add(it) }
                current = current.cause
                if (current == e) break // 防止循环引用
            }
            return messages
        }

        /**
         * 尝试删除损坏的 WAL/SHM 文件，让 SQLite 回退到主数据库文件。
         * 这是覆盖安装后最常见的修复方式（Force Stop 导致 WAL 未 checkpoint）。
         */
        private fun tryRepairWalFiles(context: Context): Boolean {
            val dbFile = context.getDatabasePath(DB_NAME)
            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")

            // 主 DB 文件必须存在且非空
            if (!dbFile.exists() || dbFile.length() < 512) return false

            var repaired = false
            if (walFile.exists()) {
                repaired = walFile.delete()
                SecureLog.i("AppDatabase", "Deleted corrupted WAL file: $repaired")
            }
            if (shmFile.exists()) {
                val deleted = shmFile.delete()
                repaired = repaired || deleted
                SecureLog.i("AppDatabase", "Deleted corrupted SHM file: $deleted")
            }
            return repaired || dbFile.exists()
        }

        /**
         * 尝试从 recovery 目录恢复最新的有效备份
         */
        private fun tryRecoverFromBackup(context: Context): Boolean {
            val dbFile = context.getDatabasePath(DB_NAME)
            val recoveryDir = File(dbFile.parentFile, "recovery")
            if (!recoveryDir.exists()) return false

            val backups = recoveryDir.listFiles()
                ?.filter { it.name.endsWith(".db") || it.name.contains("corrupted") }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()

            for (backup in backups) {
                if (backup.length() > 1024) {
                    // 恢复 DB 主文件
                    backup.copyTo(dbFile, overwrite = true)
                    // 同时恢复关联的 WAL/SHM 文件（如果存在）
                    listOf("-wal", "-shm", "-journal").forEach { suffix ->
                        val backupAux = File(backup.parentFile, "${backup.name}$suffix")
                        val targetAux = File(dbFile.path + suffix)
                        if (backupAux.exists() && backupAux.length() > 0) {
                            runCatching { backupAux.copyTo(targetAux, overwrite = true) }
                        }
                    }
                    SecureLog.i("AppDatabase", "Restored from backup: ${backup.name}")
                    return true
                }
            }
            return false
        }

        private fun deleteDatabaseFiles(context: Context) {
            val dbFile = context.getDatabasePath(DB_NAME)
            dbFile.delete()
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            File(dbFile.path + "-journal").delete()
        }

        private fun backupBeforeRecovery(context: Context) {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) return

            val recoveryDir = File(dbFile.parentFile, "recovery")
            recoveryDir.mkdirs()
            val timestamp = System.currentTimeMillis().toString()

            listOf(
                dbFile,
                File(dbFile.path + "-wal"),
                File(dbFile.path + "-shm"),
                File(dbFile.path + "-journal")
            ).filter { it.exists() }.forEach { file ->
                val target = File(recoveryDir, "${file.name}.corrupted_$timestamp")
                runCatching { file.copyTo(target, overwrite = true) }
            }
        }

        private fun recoverDatabase(context: Context) {
            val dbFile = context.getDatabasePath(DB_NAME)
            val recoveryDir = File(dbFile.parentFile, "recovery")

            if (!recoveryDir.exists()) return

            val backups = recoveryDir.listFiles()
                ?.filter { it.name.endsWith(".db") || it.name.contains("corrupted") }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()

            if (backups.isNotEmpty()) {
                val latestBackup = backups.first()
                if (latestBackup.length() > 1024) {
                    latestBackup.copyTo(dbFile, overwrite = true)
                    SecureLog.i("AppDatabase", "Restored database from ${latestBackup.name}")
                    return
                }
            }

            dbFile.delete()
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            File(dbFile.path + "-journal").delete()
        }

        private fun createDatabase(context: Context): AppDatabase {
            // EncryptedFile-based DB encryption — disabled for troubleshooting
            // com.lianyu.ai.security.EncryptedDatabaseWrapper.prepareDatabase(context)
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                .addMigrations(*MIGRATIONS)
                // 移除 fallbackToDestructiveMigration()：它会在 Schema 不匹配时静默删除数据库
                // 现在由 openVerifiedDatabase() 统一处理异常，优先恢复而非重建
                .build()
        }

        private fun verifyDatabaseCanOpen(database: AppDatabase) {
            // 先尝试 checkpoint WAL 文件，修复覆盖安装后可能存在的脏写
            try {
                database.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
            } catch (_: Exception) { }
            database.openHelper.writableDatabase.query("PRAGMA user_version").close()
        }

        /**
         * Move unreadable legacy DB files out of the way before creating a new DB.
         * This handles old SQLCipher/native-incompatible databases without crashing
         * the first main-screen render. Files stay inside app-private storage.
         */
        private fun backupBrokenDatabase(context: Context) {
            val dbFile = context.applicationContext.getDatabasePath(DB_NAME)
            val candidates = listOf(
                dbFile,
                File(dbFile.path + "-wal"),
                File(dbFile.path + "-shm"),
                File(dbFile.path + "-journal")
            ).filter { it.exists() }

            if (candidates.isEmpty()) return

            val backupDir = File(dbFile.parentFile, "recovery")
            backupDir.mkdirs()
            val suffix = System.currentTimeMillis().toString()

            candidates.forEach { file ->
                val target = File(backupDir, "${file.name}.broken.$suffix")
                if (!file.renameTo(target)) {
                    runCatching { file.copyTo(target, overwrite = true) }
                    runCatching { file.delete() }
                }
            }
        }

        private fun addColumnIfMissing(
            db: SupportSQLiteDatabase,
            tableName: String,
            columnName: String,
            columnDefinition: String
        ) {
            db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == columnName) return
                }
            }
            db.execSQL("ALTER TABLE `$tableName` ADD COLUMN $columnName $columnDefinition")
        }

        private fun migrateLegacyTo6(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `companions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `avatarUrl` TEXT,
                    `age` INTEGER,
                    `personality` TEXT NOT NULL DEFAULT '',
                    `backstory` TEXT,
                    `speakingStyle` TEXT,
                    `tags` TEXT,
                    `rawPrompt` TEXT,
                    `systemPrompt` TEXT,
                    `intimacy` INTEGER NOT NULL DEFAULT 0,
                    `createdAt` INTEGER NOT NULL DEFAULT 0,
                    `updatedAt` INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            addColumnIfMissing(db, "companions", "avatarUrl", "TEXT")
            addColumnIfMissing(db, "companions", "age", "INTEGER")
            addColumnIfMissing(db, "companions", "personality", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "companions", "backstory", "TEXT")
            addColumnIfMissing(db, "companions", "speakingStyle", "TEXT")
            addColumnIfMissing(db, "companions", "tags", "TEXT")
            addColumnIfMissing(db, "companions", "rawPrompt", "TEXT")
            addColumnIfMissing(db, "companions", "systemPrompt", "TEXT")
            addColumnIfMissing(db, "companions", "intimacy", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "companions", "createdAt", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "companions", "updatedAt", "INTEGER NOT NULL DEFAULT 0")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `chat_messages` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `companionId` INTEGER NOT NULL,
                    `content` TEXT NOT NULL,
                    `isFromUser` INTEGER NOT NULL,
                    `timestamp` INTEGER NOT NULL DEFAULT 0,
                    `type` TEXT NOT NULL DEFAULT 'TEXT',
                    `searchContent` TEXT NOT NULL DEFAULT '',
                    `fileFormat` TEXT NOT NULL DEFAULT 'TEXT',
                    `linkString` TEXT NOT NULL DEFAULT '',
                    FOREIGN KEY(`companionId`) REFERENCES `companions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            addColumnIfMissing(db, "chat_messages", "type", "TEXT NOT NULL DEFAULT 'TEXT'")
            addColumnIfMissing(db, "chat_messages", "searchContent", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "chat_messages", "fileFormat", "TEXT NOT NULL DEFAULT 'TEXT'")
            addColumnIfMissing(db, "chat_messages", "linkString", "TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_companionId` ON `chat_messages` (`companionId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_companionId_timestamp` ON `chat_messages` (`companionId`, `timestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_companionId_fileFormat` ON `chat_messages` (`companionId`, `fileFormat`)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `api_configs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `provider` TEXT NOT NULL,
                    `name` TEXT NOT NULL DEFAULT '',
                    `apiKey` TEXT NOT NULL,
                    `baseUrl` TEXT NOT NULL,
                    `model` TEXT NOT NULL,
                    `temperature` REAL NOT NULL DEFAULT 0.7,
                    `maxTokens` INTEGER,
                    `isEnabled` INTEGER NOT NULL DEFAULT 1,
                    `connectionTested` INTEGER NOT NULL DEFAULT 0,
                    `connectionTestedAt` INTEGER NOT NULL DEFAULT 0,
                    `latencyMs` INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            addColumnIfMissing(db, "api_configs", "id", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "api_configs", "name", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "api_configs", "temperature", "REAL NOT NULL DEFAULT 0.7")
            addColumnIfMissing(db, "api_configs", "maxTokens", "INTEGER")
            addColumnIfMissing(db, "api_configs", "isEnabled", "INTEGER NOT NULL DEFAULT 1")
            addColumnIfMissing(db, "api_configs", "connectionTested", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "api_configs", "connectionTestedAt", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "api_configs", "latencyMs", "INTEGER NOT NULL DEFAULT 0")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `memory_entries` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `companionId` INTEGER NOT NULL,
                    `content` TEXT NOT NULL,
                    `category` TEXT NOT NULL DEFAULT 'FACT',
                    `importance` REAL NOT NULL DEFAULT 0.5,
                    `context` TEXT NOT NULL DEFAULT '',
                    `accessCount` INTEGER NOT NULL DEFAULT 1,
                    `timestamp` INTEGER NOT NULL DEFAULT 0,
                    `lastAccessed` INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            addColumnIfMissing(db, "memory_entries", "category", "TEXT NOT NULL DEFAULT 'FACT'")
            addColumnIfMissing(db, "memory_entries", "importance", "REAL NOT NULL DEFAULT 0.5")
            addColumnIfMissing(db, "memory_entries", "context", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "memory_entries", "accessCount", "INTEGER NOT NULL DEFAULT 1")
            addColumnIfMissing(db, "memory_entries", "lastAccessed", "INTEGER NOT NULL DEFAULT 0")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `temp_memory` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `companionId` INTEGER NOT NULL,
                    `userInput` TEXT NOT NULL,
                    `botResponse` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `chat_groups` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `avatarUrl` TEXT,
                    `companionIds` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL DEFAULT 0,
                    `updatedAt` INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `group_messages` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `groupId` INTEGER NOT NULL,
                    `companionId` INTEGER NOT NULL,
                    `content` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL DEFAULT 0,
                    `searchContent` TEXT NOT NULL DEFAULT '',
                    `fileFormat` TEXT NOT NULL DEFAULT 'TEXT',
                    `linkString` TEXT NOT NULL DEFAULT '',
                    FOREIGN KEY(`groupId`) REFERENCES `chat_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_messages_groupId` ON `group_messages` (`groupId`)")
            addColumnIfMissing(db, "group_messages", "searchContent", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "group_messages", "fileFormat", "TEXT NOT NULL DEFAULT 'TEXT'")
            addColumnIfMissing(db, "group_messages", "linkString", "TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_messages_groupId_timestamp` ON `group_messages` (`groupId`, `timestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_messages_groupId_fileFormat` ON `group_messages` (`groupId`, `fileFormat`)")
        }

        val MIGRATION_1_6 = object : Migration(1, 6) {
            override fun migrate(db: SupportSQLiteDatabase) = migrateLegacyTo6(db)
        }

        val MIGRATION_2_6 = object : Migration(2, 6) {
            override fun migrate(db: SupportSQLiteDatabase) = migrateLegacyTo6(db)
        }

        val MIGRATION_3_6 = object : Migration(3, 6) {
            override fun migrate(db: SupportSQLiteDatabase) = migrateLegacyTo6(db)
        }

        val MIGRATION_4_6 = object : Migration(4, 6) {
            override fun migrate(db: SupportSQLiteDatabase) = migrateLegacyTo6(db)
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) = migrateLegacyTo6(db)
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "api_configs", "connectionTested", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "api_configs", "connectionTestedAt", "INTEGER NOT NULL DEFAULT 0")
            }
        }

        private fun migrateApiConfigsTo9(db: SupportSQLiteDatabase) {
            addColumnIfMissing(db, "api_configs", "name", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "api_configs", "temperature", "REAL NOT NULL DEFAULT 0.7")
            addColumnIfMissing(db, "api_configs", "maxTokens", "INTEGER")
            addColumnIfMissing(db, "api_configs", "isEnabled", "INTEGER NOT NULL DEFAULT 1")
            addColumnIfMissing(db, "api_configs", "connectionTested", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "api_configs", "connectionTestedAt", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "api_configs", "latencyMs", "INTEGER NOT NULL DEFAULT 0")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `api_configs_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `provider` TEXT NOT NULL,
                    `name` TEXT NOT NULL DEFAULT '',
                    `apiKey` TEXT NOT NULL,
                    `baseUrl` TEXT NOT NULL,
                    `model` TEXT NOT NULL,
                    `temperature` REAL NOT NULL DEFAULT 0.7,
                    `maxTokens` INTEGER,
                    `isEnabled` INTEGER NOT NULL DEFAULT 1,
                    `connectionTested` INTEGER NOT NULL DEFAULT 0,
                    `connectionTestedAt` INTEGER NOT NULL DEFAULT 0,
                    `latencyMs` INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO `api_configs_new` (`id`, `provider`, `name`, `apiKey`, `baseUrl`, `model`, `temperature`, `maxTokens`, `isEnabled`, `connectionTested`, `connectionTestedAt`, `latencyMs`)
                SELECT `id`, `provider`, `name`, `apiKey`, `baseUrl`, `model`, `temperature`, `maxTokens`, `isEnabled`, `connectionTested`, `connectionTestedAt`, `latencyMs`
                FROM `api_configs`
            """.trimIndent())
            db.execSQL("DROP TABLE `api_configs`")
            db.execSQL("ALTER TABLE `api_configs_new` RENAME TO `api_configs`")
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) = migrateApiConfigsTo9(db)
        }

        private fun migrateChatStorageTo10(db: SupportSQLiteDatabase) {
            addColumnIfMissing(db, "chat_messages", "searchContent", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "chat_messages", "fileFormat", "TEXT NOT NULL DEFAULT 'TEXT'")
            addColumnIfMissing(db, "chat_messages", "linkString", "TEXT NOT NULL DEFAULT ''")
            db.execSQL("UPDATE `chat_messages` SET `searchContent` = `content` WHERE `searchContent` = ''")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_companionId_timestamp` ON `chat_messages` (`companionId`, `timestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_companionId_fileFormat` ON `chat_messages` (`companionId`, `fileFormat`)")

            addColumnIfMissing(db, "group_messages", "searchContent", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "group_messages", "fileFormat", "TEXT NOT NULL DEFAULT 'TEXT'")
            addColumnIfMissing(db, "group_messages", "linkString", "TEXT NOT NULL DEFAULT ''")
            db.execSQL("UPDATE `group_messages` SET `searchContent` = `content` WHERE `searchContent` = ''")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_messages_groupId_timestamp` ON `group_messages` (`groupId`, `timestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_messages_groupId_fileFormat` ON `group_messages` (`groupId`, `fileFormat`)")
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) = migrateChatStorageTo10(db)
        }

        private fun migrateSecurityTablesTo11(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `keywords` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `keyword` TEXT NOT NULL,
                    `pattern` TEXT,
                    `level` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `banDays` INTEGER NOT NULL,
                    `isEnabled` INTEGER NOT NULL DEFAULT 1,
                    `createdAt` INTEGER NOT NULL DEFAULT 0,
                    `checksum` TEXT NOT NULL DEFAULT ''
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_keywords_level` ON `keywords` (`level`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_keywords_type` ON `keywords` (`type`)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `quiz_questions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `question` TEXT NOT NULL,
                    `options` TEXT NOT NULL,
                    `correctIndex` INTEGER NOT NULL,
                    `category` TEXT NOT NULL,
                    `difficulty` TEXT NOT NULL DEFAULT 'MEDIUM',
                    `isEnabled` INTEGER NOT NULL DEFAULT 1,
                    `createdAt` INTEGER NOT NULL DEFAULT 0,
                    `checksum` TEXT NOT NULL DEFAULT ''
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_category` ON `quiz_questions` (`category`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_difficulty` ON `quiz_questions` (`difficulty`)")
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) = migrateSecurityTablesTo11(db)
        }

        private fun migrateTokenUsageTo12(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `token_usage` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `companionId` INTEGER NOT NULL,
                    `date` TEXT NOT NULL,
                    `inputTokens` INTEGER NOT NULL DEFAULT 0,
                    `outputTokens` INTEGER NOT NULL DEFAULT 0,
                    `totalTokens` INTEGER NOT NULL DEFAULT 0,
                    `requestCount` INTEGER NOT NULL DEFAULT 0,
                    `timestamp` INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_token_usage_companionId_date` ON `token_usage` (`companionId`, `date`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_token_usage_date` ON `token_usage` (`date`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_token_usage_companionId` ON `token_usage` (`companionId`)")
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) = migrateTokenUsageTo12(db)
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "chat_messages", "deviceId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "memory_entries", "deviceId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "temp_memory", "deviceId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "token_usage", "deviceId", "TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateChatStorageTo10(db)
                addColumnIfMissing(db, "token_usage", "deviceId", "TEXT NOT NULL DEFAULT ''")
                db.execSQL("DROP INDEX IF EXISTS `index_token_usage_companionId_date`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_token_usage_companionId_date_deviceId` ON `token_usage` (`companionId`, `date`, `deviceId`)")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "api_configs", "extraApiKeys", "TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 确保 deviceId 列存在（处理跳过 12→13 的旧 DB）
                addColumnIfMissing(db, "chat_messages", "deviceId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "memory_entries", "deviceId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "temp_memory", "deviceId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "token_usage", "deviceId", "TEXT NOT NULL DEFAULT ''")
                // 清理可能残留的多余索引
                db.execSQL("DROP INDEX IF EXISTS index_chat_messages_companionId_deviceId")
                db.execSQL("DROP INDEX IF EXISTS index_memory_entries_companionId_deviceId")
                db.execSQL("DROP INDEX IF EXISTS index_temp_memory_companionId_deviceId")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "api_configs", "skipCertVerify", "INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "api_configs", "formatHint", "TEXT NOT NULL DEFAULT 'openai'")
            }
        }

        val MIGRATIONS = arrayOf(
            MIGRATION_1_6,
            MIGRATION_2_6,
            MIGRATION_3_6,
            MIGRATION_4_6,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18,
            MIGRATION_18_19
        )

        private var lastBackupTime: Long = 0L
        private val BACKUP_INTERVAL_MS = 2 * 60 * 60 * 1000L  // 每2小时自动备份一次，缩短间隔减少覆盖安装时的数据丢失窗口

        fun backupDatabase(context: Context): Boolean {
            return try {
                val dbFile = context.getDatabasePath(DB_NAME)
                if (!dbFile.exists()) return false

                val backupDir = File(context.filesDir, "db_backup")
                backupDir.mkdirs()

                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                    .format(java.util.Date())

                val filesToBackup = listOf(
                    dbFile,
                    File(dbFile.path + "-wal"),
                    File(dbFile.path + "-shm"),
                    File(dbFile.path + "-journal")
                ).filter { it.exists() && it.length() > 0 }

                if (filesToBackup.isEmpty()) return false

                val backupSubDir = File(backupDir, "backup_$timestamp")
                backupSubDir.mkdirs()

                filesToBackup.forEach { file ->
                    val target = File(backupSubDir, file.name)
                    file.copyTo(target, overwrite = true)
                }

                lastBackupTime = System.currentTimeMillis()
                true
            } catch (e: Exception) {
                false
            }
        }

        fun restoreFromBackup(context: Context): Boolean {
            return try {
                val backupDir = File(context.filesDir, "db_backup")
                if (!backupDir.exists()) return false

                val backups = backupDir.listFiles()
                    ?.filter { it.isDirectory && it.name.startsWith("backup_") }
                    ?.sortedByDescending { it.name }
                    ?: emptyList()

                if (backups.isEmpty()) return false

                val latestBackup = backups.first()
                val dbFile = context.getDatabasePath(DB_NAME)

                INSTANCE?.close()
                INSTANCE = null

                latestBackup.listFiles()?.forEach { backupFile ->
                    val target = when {
                        backupFile.name == DB_NAME -> dbFile
                        else -> File(dbFile.parentFile, backupFile.name)
                    }
                    backupFile.copyTo(target, overwrite = true)
                }

                true
            } catch (e: Exception) {
                false
            }
        }

        fun getBackupInfo(context: Context): List<Map<String, Any>> {
            val backupDir = File(context.filesDir, "db_backup")
            if (!backupDir.exists()) return emptyList()

            return backupDir.listFiles()
                ?.filter { it.isDirectory && it.name.startsWith("backup_") }
                ?.sortedByDescending { it.name }
                ?.map { backup ->
                    val totalSize = backup.listFiles()?.sumOf { it.length() } ?: 0L
                    mapOf(
                        "name" to backup.name,
                        "timestamp" to backup.name.removePrefix("backup_"),
                        "size" to totalSize,
                        "fileCount" to (backup.listFiles()?.size ?: 0)
                    )
                } ?: emptyList()
        }

        fun autoBackupIfNeeded(context: Context) {
            val now = System.currentTimeMillis()
            if (now - lastBackupTime >= BACKUP_INTERVAL_MS) {
                backupDatabase(context)
            }
        }

        fun clearOldBackups(context: Context, keepCount: Int = 5): Int {
            return try {
                // [M5 FIX] 原用未设置的 System property "package.name" 拼接路径，
                // 结果是 /data/data//files/db_backup，永远找不到目录，旧备份永不清理。
                // 改用 context.filesDir 获取正确的应用私有目录。
                val backupDir = File(context.applicationContext.filesDir, "db_backup")
                if (!backupDir.exists()) return 0

                val backups = backupDir.listFiles()
                    ?.filter { it.isDirectory && it.name.startsWith("backup_") }
                    ?.sortedByDescending { it.name }
                    ?: emptyList()

                var deletedCount = 0
                backups.drop(keepCount).forEach { backup ->
                    if (backup.deleteRecursively()) deletedCount++
                }
                deletedCount
            } catch (e: Exception) {
                0
            }
        }
    }
}

class Converters {
    @TypeConverter
    fun fromApiProvider(value: ApiProvider): String = value.name

    @TypeConverter
    fun toApiProvider(value: String?): ApiProvider {
        if (value.isNullOrBlank()) return ApiProvider.OPENAI
        return runCatching { ApiProvider.valueOf(value.trim().uppercase()) }.getOrDefault(ApiProvider.OPENAI)
    }

    @TypeConverter
    fun fromMessageType(value: MessageType): String = value.name

    @TypeConverter
    fun toMessageType(value: String?): MessageType {
        if (value.isNullOrBlank()) return MessageType.TEXT
        return runCatching { MessageType.valueOf(value.trim().uppercase()) }.getOrDefault(MessageType.TEXT)
    }

    @TypeConverter
    fun fromFileFormat(value: FileFormat): String = value.name

    @TypeConverter
    fun toFileFormat(value: String?): FileFormat {
        if (value.isNullOrBlank()) return FileFormat.UNKNOWN
        return runCatching { FileFormat.valueOf(value.trim().uppercase()) }.getOrDefault(FileFormat.UNKNOWN)
    }

    @TypeConverter
    fun fromMemoryCategory(value: MemoryCategory): String = value.name

    @TypeConverter
    fun toMemoryCategory(value: String?): MemoryCategory {
        if (value.isNullOrBlank()) return MemoryCategory.FACT
        return runCatching { MemoryCategory.valueOf(value.trim().uppercase()) }.getOrDefault(MemoryCategory.FACT)
    }

}
