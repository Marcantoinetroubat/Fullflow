package com.newoether.agora.studio.reader.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReaderDao {
    @Query("SELECT * FROM reader_documents ORDER BY dateAdded DESC")
    fun getAllDocumentsFlow(): Flow<List<ReaderDocumentEntity>>

    @Query("SELECT * FROM reader_documents WHERE id = :id")
    suspend fun getDocumentById(id: Long): ReaderDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: ReaderDocumentEntity): Long

    @Query("UPDATE reader_documents SET lastReadPosition = :position, lastReadPercent = :percent WHERE id = :id")
    suspend fun updateProgress(id: Long, position: Int, percent: Float)

    @Delete
    suspend fun deleteDocument(doc: ReaderDocumentEntity)
}
