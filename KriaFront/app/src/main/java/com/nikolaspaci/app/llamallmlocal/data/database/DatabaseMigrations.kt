package com.nikolaspaci.app.llamallmlocal.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add new fields to conversations
            db.execSQL("ALTER TABLE conversations ADD COLUMN title TEXT")
            db.execSQL("ALTER TABLE conversations ADD COLUMN lastMessagePreview TEXT")
            db.execSQL("ALTER TABLE conversations ADD COLUMN lastUpdatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE conversations ADD COLUMN messageCount INTEGER NOT NULL DEFAULT 0")

            // Create indices for chat_messages
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_conversationId ON chat_messages(conversationId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_timestamp ON chat_messages(timestamp)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_conversationId_timestamp ON chat_messages(conversationId, timestamp)")

            // Update existing data
            db.execSQL("""
                UPDATE conversations
                SET lastUpdatedAt = timestamp,
                    messageCount = (
                        SELECT COUNT(*) FROM chat_messages
                        WHERE chat_messages.conversationId = conversations.id
                    )
            """)
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE model_parameters ADD COLUMN contextSize INTEGER NOT NULL DEFAULT 2048")
            db.execSQL("ALTER TABLE model_parameters ADD COLUMN maxTokens INTEGER NOT NULL DEFAULT 256")
            db.execSQL("ALTER TABLE model_parameters ADD COLUMN threadCount INTEGER NOT NULL DEFAULT 4")
            db.execSQL("ALTER TABLE model_parameters ADD COLUMN repeatPenalty REAL NOT NULL DEFAULT 1.1")
            db.execSQL("ALTER TABLE model_parameters ADD COLUMN useGpu INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Create models table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS models (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    filePath TEXT NOT NULL,
                    defaultParameterId INTEGER,
                    pendingParameterId INTEGER
                )
            """)
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_models_filePath ON models(filePath)")

            // 2. Populate models from existing model_parameters
            db.execSQL("""
                INSERT OR IGNORE INTO models (filePath, defaultParameterId)
                SELECT modelId, MIN(id)
                FROM model_parameters
                WHERE modelId != ''
                GROUP BY modelId
            """)

            // 3. Recreate model_parameters without modelId column
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS model_parameters_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    temperature REAL NOT NULL,
                    topK INTEGER NOT NULL,
                    topP REAL NOT NULL,
                    minP REAL NOT NULL,
                    contextSize INTEGER NOT NULL,
                    maxTokens INTEGER NOT NULL,
                    threadCount INTEGER NOT NULL,
                    repeatPenalty REAL NOT NULL,
                    useGpu INTEGER NOT NULL
                )
            """)
            db.execSQL("""
                INSERT INTO model_parameters_new (id, temperature, topK, topP, minP, contextSize, maxTokens, threadCount, repeatPenalty, useGpu)
                SELECT id, temperature, topK, topP, minP, contextSize, maxTokens, threadCount, repeatPenalty, useGpu
                FROM model_parameters
            """)
            db.execSQL("DROP TABLE model_parameters")
            db.execSQL("ALTER TABLE model_parameters_new RENAME TO model_parameters")
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE model_parameters ADD COLUMN systemPrompt TEXT NOT NULL DEFAULT ''")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS system_prompt_presets (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    prompt TEXT NOT NULL,
                    createdAt INTEGER NOT NULL DEFAULT 0
                )
            """)
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE model_parameters ADD COLUMN gpuLayers INTEGER NOT NULL DEFAULT 99")
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add enableThinking to model_parameters
            db.execSQL("ALTER TABLE model_parameters ADD COLUMN enableThinking INTEGER NOT NULL DEFAULT 1")
            // Add thinkingContent to chat_messages
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN thinkingContent TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add media fields to chat_messages
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN mediaPath TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN mediaType TEXT DEFAULT NULL")
        }
    }

    val ALL_MIGRATIONS = arrayOf(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
}
