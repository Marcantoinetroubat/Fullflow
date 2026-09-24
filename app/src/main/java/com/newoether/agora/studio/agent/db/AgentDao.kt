package com.newoether.agora.studio.agent.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentDao {
    @Query("SELECT * FROM agents WHERE isActive = 1 ORDER BY sortOrder ASC, name ASC")
    fun observeActiveAgents(): Flow<List<AgentEntity>>

    @Query("SELECT * FROM agents ORDER BY sortOrder ASC, name ASC")
    fun observeAllAgents(): Flow<List<AgentEntity>>

    @Query("SELECT * FROM agents WHERE id = :id LIMIT 1")
    suspend fun getAgentById(id: String): AgentEntity?

    @Query("SELECT * FROM agents WHERE isActive = 1 ORDER BY sortOrder ASC, name ASC")
    suspend fun getActiveAgents(): List<AgentEntity>

    @Upsert
    suspend fun upsertAgent(agent: AgentEntity)

    @Delete
    suspend fun deleteAgent(agent: AgentEntity)

    @Query("UPDATE agents SET isActive = :active WHERE id = :id")
    suspend fun setAgentActive(id: String, active: Boolean)

    // ── Pipelines ───────────────────────────────────────────────

    @Query("SELECT * FROM pipelines WHERE isActive = 1 ORDER BY sortOrder ASC, name ASC")
    fun observeActivePipelines(): Flow<List<PipelineEntity>>

    @Query("SELECT * FROM pipelines ORDER BY sortOrder ASC, name ASC")
    fun observeAllPipelines(): Flow<List<PipelineEntity>>

    @Query("SELECT * FROM pipelines WHERE id = :id LIMIT 1")
    suspend fun getPipelineById(id: String): PipelineEntity?

    @Query("SELECT * FROM pipelines WHERE isActive = 1 ORDER BY sortOrder ASC, name ASC")
    suspend fun getActivePipelines(): List<PipelineEntity>

    @Upsert
    suspend fun upsertPipeline(pipeline: PipelineEntity)

    @Delete
    suspend fun deletePipeline(pipeline: PipelineEntity)

    @Query("UPDATE pipelines SET isActive = :active WHERE id = :id")
    suspend fun setPipelineActive(id: String, active: Boolean)
}
