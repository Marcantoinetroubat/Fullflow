package com.newoether.agora.studio.image.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StudioImageDao {

    @Query("SELECT * FROM studio_images ORDER BY timestamp DESC")
    fun getAllImages(): Flow<List<StudioImageEntity>>

    @Query("SELECT * FROM studio_images WHERE id = :id LIMIT 1")
    suspend fun getImageById(id: String): StudioImageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: StudioImageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImages(images: List<StudioImageEntity>)

    @Query("DELETE FROM studio_images WHERE id = :id")
    suspend fun deleteImageById(id: String)

    @Query("DELETE FROM studio_images WHERE id IN (:ids)")
    suspend fun deleteImagesByIds(ids: List<String>)

    @Query("SELECT COUNT(*) FROM studio_images")
    fun getImageCount(): Flow<Int>

    @Query("DELETE FROM studio_images")
    suspend fun clearAllImages()
}

@Dao
interface SavedPromptDao {

    @Query("SELECT * FROM saved_prompts ORDER BY timestamp DESC")
    fun getAllSavedPrompts(): Flow<List<SavedPromptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrompt(prompt: SavedPromptEntity): Long

    @Query("DELETE FROM saved_prompts WHERE id = :id")
    suspend fun deletePromptById(id: Long)

    @Query("SELECT COUNT(*) FROM saved_prompts")
    suspend fun getPromptCount(): Int
}
