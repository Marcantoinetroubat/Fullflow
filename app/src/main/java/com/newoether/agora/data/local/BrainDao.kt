package com.newoether.agora.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BrainItemDao {
    @Query("SELECT * FROM brain_items ORDER BY createdAt DESC")
    fun observeAllItems(): Flow<List<BrainItemEntity>>

    @Query("SELECT * FROM brain_items WHERE id = :id LIMIT 1")
    suspend fun getItem(id: Long): BrainItemEntity?

    @Query("SELECT * FROM brain_items WHERE status = 'READY' ORDER BY createdAt DESC")
    suspend fun getReadyItems(): List<BrainItemEntity>

    @Upsert
    suspend fun upsertItem(item: BrainItemEntity): Long

    @Query("UPDATE brain_items SET status = :status, errorMessage = :error WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, error: String? = null)

    @Query("DELETE FROM brain_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("SELECT COUNT(*) FROM brain_items WHERE status = 'READY'")
    suspend fun readyCount(): Int
}

@Dao
interface BrainChunkDao {
    @Query("SELECT * FROM brain_chunks WHERE itemId = :itemId ORDER BY chunkIndex ASC")
    suspend fun getChunksForItem(itemId: Long): List<BrainChunkEntity>

    @Query("SELECT * FROM brain_chunks")
    suspend fun getAllChunks(): List<BrainChunkEntity>

    @Query("SELECT * FROM brain_chunks WHERE content LIKE '%' || :query || '%' LIMIT 20")
    suspend fun searchContent(query: String): List<BrainChunkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<BrainChunkEntity>)

    @Query("DELETE FROM brain_chunks WHERE itemId = :itemId")
    suspend fun deleteChunksForItem(itemId: Long)

    @Query("SELECT COUNT(*) FROM brain_chunks")
    suspend fun count(): Int
}

@Dao
interface MessageCitationDao {
    @Query("SELECT * FROM message_citations WHERE messageId = :messageId ORDER BY id ASC")
    suspend fun getCitationsForMessage(messageId: String): List<MessageCitationEntity>

    @Insert
    suspend fun insertCitations(citations: List<MessageCitationEntity>)

    @Query("DELETE FROM message_citations WHERE messageId = :messageId")
    suspend fun deleteCitationsForMessage(messageId: String)
}
