package com.newoether.agora.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Universal brain item: a PDF, web page, or external note ingested into the
 * Second Brain for semantic search. Status tracks the async pipeline
 * (PENDING → PROCESSING → READY / FAILED).
 */
@Entity(tableName = "brain_items")
data class BrainItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "PDF" | "WEB"
    val sourceUrl: String = "",
    val title: String,
    val status: String = "PENDING", // PENDING | PROCESSING | READY | FAILED
    val pageCount: Int = 0,
    val contentHash: String = "",
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Chunk-level content + embedding for a [BrainItemEntity]. Preserves [pageNumber]
 * so citations can point back to the exact page in a PDF.
 */
@Entity(
    tableName = "brain_chunks",
    foreignKeys = [
        ForeignKey(
            entity = BrainItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("itemId")],
)
data class BrainChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val chunkIndex: Int,
    val pageNumber: Int = 0,
    val content: String,
    val embedding: ByteArray,
    val dimension: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BrainChunkEntity) return false
        return id == other.id
    }

    override fun hashCode() = id.hashCode()
}

/**
 * Persistent citation linking an assistant message to its sources (web + brain).
 * Survives across sessions so the user can audit which sources backed a claim.
 */
@Entity(tableName = "message_citations")
data class MessageCitationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: String,
    val label: String,
    val title: String,
    val url: String? = null,
    val brainItemId: Long? = null,
    val pageNumber: Int? = null,
    val snippet: String? = null,
)
