package com.newoether.agora.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v32 → v33: Adds the brain ingestion layer — universal brain items (PDF/WEB),
 * their chunk-level embeddings, and persistent message citations linking
 * assistant messages to their sources (web + brain) for post-hoc audit.
 */
val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Brain items (PDF or web page ingested into the Second Brain).
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS brain_items (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL,
                sourceUrl TEXT NOT NULL,
                title TEXT NOT NULL,
                status TEXT NOT NULL,
                pageCount INTEGER NOT NULL,
                contentHash TEXT NOT NULL,
                errorMessage TEXT,
                createdAt INTEGER NOT NULL
            )
        """)

        // Chunk-level content + embeddings for brain items.
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS brain_chunks (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                itemId INTEGER NOT NULL,
                chunkIndex INTEGER NOT NULL,
                pageNumber INTEGER NOT NULL,
                content TEXT NOT NULL,
                embedding BLOB NOT NULL,
                dimension INTEGER NOT NULL,
                FOREIGN KEY(itemId) REFERENCES brain_items(id) ON DELETE CASCADE
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_brain_chunks_itemId ON brain_chunks(itemId)")

        // Persistent citations linking messages to sources.
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS message_citations (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                messageId TEXT NOT NULL,
                label TEXT NOT NULL,
                title TEXT NOT NULL,
                url TEXT,
                brainItemId INTEGER,
                pageNumber INTEGER,
                snippet TEXT
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_message_citations_messageId ON message_citations(messageId)")
    }
}
