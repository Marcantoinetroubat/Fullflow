package com.newoether.agora.fulllive.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.newoether.agora.fulllive.data.local.entity.ConversationSessionEntity
import com.newoether.agora.fulllive.data.local.entity.MessageEntity
import com.newoether.agora.fulllive.data.local.entity.PersonaEntity
import com.newoether.agora.fulllive.data.local.entity.StatEntryEntity
import com.newoether.agora.fulllive.data.local.entity.UserInsightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonaDao {
    @Query("SELECT * FROM personas ORDER BY sortOrder ASC, name ASC")
    fun getAllPersonas(): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM personas WHERE id = :id LIMIT 1")
    suspend fun getPersonaById(id: String): PersonaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(persona: PersonaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(personas: List<PersonaEntity>)

    @Query("DELETE FROM personas WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM personas")
    suspend fun count(): Int
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM conversation_sessions ORDER BY startDate DESC")
    fun getAllSessions(): Flow<List<ConversationSessionEntity>>

    @Query("SELECT * FROM conversation_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: String): ConversationSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(session: ConversationSessionEntity)

    @Query("DELETE FROM conversation_sessions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM conversation_sessions")
    suspend fun deleteAll()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC, id ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC, id ASC")
    suspend fun getMessagesForSessionSync(sessionId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}

@Dao
interface InsightDao {
    @Query("SELECT * FROM user_insights ORDER BY createdAt DESC")
    fun getAllInsights(): Flow<List<UserInsightEntity>>

    @Query("SELECT * FROM user_insights WHERE destination = 'global' ORDER BY createdAt DESC")
    fun getGlobalInsights(): Flow<List<UserInsightEntity>>

    @Query("SELECT * FROM user_insights WHERE destination = 'global' ORDER BY createdAt DESC")
    suspend fun getGlobalInsightsSync(): List<UserInsightEntity>

    @Query("SELECT * FROM user_insights WHERE destination = 'persona' AND personaId = :personaId ORDER BY createdAt DESC")
    fun getInsightsForPersona(personaId: String): Flow<List<UserInsightEntity>>

    @Query("SELECT * FROM user_insights WHERE destination = 'persona' AND personaId = :personaId ORDER BY createdAt DESC")
    suspend fun getInsightsForPersonaSync(personaId: String): List<UserInsightEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(insight: UserInsightEntity)

    @Query("DELETE FROM user_insights WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface StatDao {
    @Query("SELECT * FROM stat_entries ORDER BY date DESC")
    fun getAllStats(): Flow<List<StatEntryEntity>>

    @Query("SELECT * FROM stat_entries ORDER BY date DESC")
    suspend fun getAllStatsSync(): List<StatEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stat: StatEntryEntity)

    @Query("DELETE FROM stat_entries")
    suspend fun deleteAll()
}
