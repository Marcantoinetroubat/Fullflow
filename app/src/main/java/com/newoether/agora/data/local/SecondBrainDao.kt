package com.newoether.agora.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, lastModified DESC")
    fun observeAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY isPinned DESC, lastModified DESC")
    suspend fun getAllNotes(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE filePath = :filePath LIMIT 1")
    suspend fun getNote(filePath: String): NoteEntity?

    @Query("""
        SELECT * FROM notes
        WHERE title LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%'
        ORDER BY isPinned DESC, lastModified DESC
    """)
    suspend fun searchNotes(query: String): List<NoteEntity>

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE filePath = :filePath")
    suspend fun deleteNote(filePath: String): Int

    @Query("SELECT * FROM notes WHERE contentHash != :hash AND filePath = :filePath LIMIT 1")
    suspend fun noteNeedsReindex(filePath: String, hash: String): NoteEntity?
}

@Dao
interface ObsidianTaskDao {
    @Query("SELECT * FROM obsidian_tasks WHERE noteFilePath = :filePath ORDER BY lineIndex ASC")
    suspend fun getTasksForNote(filePath: String): List<ObsidianTaskEntity>

    @Query("SELECT * FROM obsidian_tasks ORDER BY dueDate IS NULL, dueDate ASC, priority DESC")
    fun observeAllTasks(): Flow<List<ObsidianTaskEntity>>

    @Query("SELECT * FROM obsidian_tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Long): ObsidianTaskEntity?

    @Query("SELECT * FROM obsidian_tasks WHERE isChecked = 0 ORDER BY dueDate IS NULL, dueDate ASC, priority DESC")
    suspend fun getOpenTasks(): List<ObsidianTaskEntity>

    @Query("SELECT COUNT(*) FROM obsidian_tasks WHERE isChecked = 0")
    fun observeOpenTaskCount(): Flow<Int>

    @Upsert
    suspend fun upsertTask(task: ObsidianTaskEntity)

    @Query("DELETE FROM obsidian_tasks WHERE noteFilePath = :filePath")
    suspend fun deleteTasksForNote(filePath: String)

    @Query("UPDATE obsidian_tasks SET isChecked = :checked WHERE id = :id")
    suspend fun setTaskChecked(id: Long, checked: Boolean)
}

@Dao
interface NoteEmbeddingDao {
    @Query("SELECT * FROM note_embeddings WHERE noteFilePath = :filePath ORDER BY chunkIndex ASC")
    suspend fun getEmbeddingsForNote(filePath: String): List<NoteEmbeddingEntity>

    @Query("SELECT * FROM note_embeddings")
    suspend fun getAllEmbeddings(): List<NoteEmbeddingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbeddings(embeddings: List<NoteEmbeddingEntity>)

    @Query("DELETE FROM note_embeddings WHERE noteFilePath = :filePath")
    suspend fun deleteEmbeddingsForNote(filePath: String)

    @Query("DELETE FROM note_embeddings")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM note_embeddings")
    suspend fun count(): Int
}

@Dao
interface VoiceMemoDao {
    @Query("SELECT * FROM voice_memos ORDER BY createdAt DESC")
    fun observeAllMemos(): Flow<List<VoiceMemoEntity>>

    @Query("SELECT * FROM voice_memos WHERE id = :id LIMIT 1")
    suspend fun getMemo(id: Long): VoiceMemoEntity?

    @Insert
    suspend fun insertMemo(memo: VoiceMemoEntity): Long

    @Delete
    suspend fun deleteMemo(memo: VoiceMemoEntity)
}
