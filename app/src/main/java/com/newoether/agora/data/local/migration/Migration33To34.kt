package com.newoether.agora.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v33 → v34: Adds the Agent Pipeline engine — custom chat agents (specialists)
 * that can be chained into sequential pipelines with configurable loops,
 * plus a final synthesizer agent for editorial output.
 */
val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Custom chat agents (specialists for pipeline chaining).
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS agents (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                specialty TEXT NOT NULL,
                systemPrompt TEXT NOT NULL,
                modelId TEXT NOT NULL DEFAULT '',
                tone TEXT NOT NULL DEFAULT 'neutral',
                depthLevel TEXT NOT NULL DEFAULT 'medium',
                color INTEGER NOT NULL DEFAULT 4281909728,
                isActive INTEGER NOT NULL DEFAULT 1,
                isSystem INTEGER NOT NULL DEFAULT 0,
                sortOrder INTEGER NOT NULL DEFAULT 0
            )
        """)

        // Agent pipelines (ordered chains of agents with loop count).
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS pipelines (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                agentIdsJson TEXT NOT NULL DEFAULT '[]',
                loopCount INTEGER NOT NULL DEFAULT 1,
                synthesizerAgentId TEXT NOT NULL DEFAULT '',
                isActive INTEGER NOT NULL DEFAULT 1,
                isSystem INTEGER NOT NULL DEFAULT 0,
                sortOrder INTEGER NOT NULL DEFAULT 0
            )
        """)
    }
}
