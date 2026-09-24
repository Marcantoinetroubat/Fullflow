package com.newoether.agora.studio.reader.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reader_documents")
data class ReaderDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val filePath: String,
    val type: String, // "pdf", "txt", "md"
    val sizeBytes: Long,
    val dateAdded: Long,
    val textLength: Int,
    val lastReadPosition: Int = 0,
    val lastReadPercent: Float = 0f
)
