package com.newoether.agora.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Obsidian-style structured note stored in Room (complements the flat-file
 * [com.newoether.agora.data.MemoryManager] which stays for backward compatibility).
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val filePath: String,
    val title: String,
    val tags: String = "",
    val contentHash: String = "",
    val isPinned: Boolean = false,
    val lastModified: Long = System.currentTimeMillis(),
)

/**
 * Obsidian Tasks embedded in note Markdown (``- [ ]``, ``- [x]``, ``- [/]``).
 * Parsed bidirectionally: toggling the checkbox rewrites the Markdown line.
 */
@Entity(
    tableName = "obsidian_tasks",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["filePath"],
            childColumns = ["noteFilePath"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteFilePath")],
)
data class ObsidianTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteFilePath: String,
    val lineIndex: Int,
    val text: String,
    val isChecked: Boolean = false,
    val priority: String? = null,
    val dueDate: String? = null,
)

/**
 * Chunk-level embedding for a note, powering semantic search over the vault.
 * Uses FullFlow's real embedding models (llama.cpp / OpenAI) — not the hashing
 * approximation from the original second-cerveau project.
 */
@Entity(
    tableName = "note_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["filePath"],
            childColumns = ["noteFilePath"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteFilePath")],
)
data class NoteEmbeddingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteFilePath: String,
    val chunkIndex: Int,
    val chunkText: String,
    val embedding: ByteArray,
    val dimension: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NoteEmbeddingEntity) return false
        return id == other.id && noteFilePath == other.noteFilePath && chunkIndex == other.chunkIndex
    }

    override fun hashCode(): Int {
        var result = noteFilePath.hashCode()
        result = 31 * result + chunkIndex
        result = 31 * result + id.hashCode()
        return result
    }
}

/**
 * Voice memo captured via Android SpeechRecognizer, transcribed to a Markdown note.
 */
@Entity(tableName = "voice_memos")
data class VoiceMemoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val durationMs: Long = 0L,
    val transcription: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
