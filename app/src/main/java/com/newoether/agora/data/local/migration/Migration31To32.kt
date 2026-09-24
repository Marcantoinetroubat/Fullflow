package com.newoether.agora.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v31 → v32: Adds the Second Brain (PKM) tables — Obsidian-style structured notes,
 * tasks embedded in Markdown, note-level embeddings for semantic search, and voice memos.
 *
 * Coexists with the existing flat-file [com.newoether.agora.data.MemoryManager]; the
 * Room-backed tables are a richer index/search layer on top of the same .md content.
 */
val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Structured notes (Obsidian-style vault items).
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS notes (
                filePath TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                tags TEXT NOT NULL,
                contentHash TEXT NOT NULL,
                isPinned INTEGER NOT NULL,
                lastModified INTEGER NOT NULL
            )
        """)

        // Obsidian Tasks parsed from Markdown (- [ ], - [x], - [/]).
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS obsidian_tasks (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                noteFilePath TEXT NOT NULL,
                lineIndex INTEGER NOT NULL,
                text TEXT NOT NULL,
                isChecked INTEGER NOT NULL,
                priority TEXT,
                dueDate TEXT,
                FOREIGN KEY(noteFilePath) REFERENCES notes(filePath) ON DELETE CASCADE
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_obsidian_tasks_noteFilePath ON obsidian_tasks(noteFilePath)")

        // Note chunk embeddings for semantic RAG over the vault.
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS note_embeddings (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                noteFilePath TEXT NOT NULL,
                chunkIndex INTEGER NOT NULL,
                chunkText TEXT NOT NULL,
                embedding BLOB NOT NULL,
                dimension INTEGER NOT NULL,
                FOREIGN KEY(noteFilePath) REFERENCES notes(filePath) ON DELETE CASCADE
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_note_embeddings_noteFilePath ON note_embeddings(noteFilePath)")

        // Voice memos captured via SpeechRecognizer, transcribed to notes.
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS voice_memos (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                filePath TEXT NOT NULL,
                durationMs INTEGER NOT NULL,
                transcription TEXT,
                createdAt INTEGER NOT NULL
            )
        """)
    }
}
