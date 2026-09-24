package com.newoether.agora.studio.agent.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agents")
data class AgentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val specialty: String,
    val systemPrompt: String,
    val modelId: String = "",
    val tone: String = "neutral",
    val depthLevel: String = "medium",
    val color: Long = 0xFF38BDF8,
    val isActive: Boolean = true,
    val isSystem: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(tableName = "pipelines")
data class PipelineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val agentIdsJson: String = "[]", // ordered list of agent IDs
    val loopCount: Int = 1,
    val synthesizerAgentId: String = "",
    val isActive: Boolean = true,
    val isSystem: Boolean = false,
    val sortOrder: Int = 0,
)
