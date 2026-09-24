package com.newoether.agora.fulllive.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val model: String = "",
    val voice: String = "Puck",
    val reactivity: String = "balanced",
    val creativity: String = "balanced",
    val greeting: String = "persona",
    val personaInfo: String = "",
    val prompt: String,
    val imageUri: String? = null,
    val gender: String? = null,
    val isSystem: Boolean = false,
    val sortOrder: Int = 0
)

@Entity(tableName = "conversation_sessions")
data class ConversationSessionEntity(
    @PrimaryKey val id: String,
    val personaId: String,
    val personaName: String,
    val personaImage: String? = null,
    val mode: String = "conversation", // "conversation", "transcription", "translation"
    val model: String,
    val costDollars: Double = 0.0,
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long = System.currentTimeMillis(),
    val durationSeconds: Int = 0,
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val summary: String = "",
    val targetLang: String? = null
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: String,
    val sender: String, // "user", "ai", "transcript"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val imageUri: String? = null
)

@Entity(tableName = "user_insights")
data class UserInsightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fact: String,
    val destination: String = "global", // "global" or "persona"
    val personaId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "stat_entries")
data class StatEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val personaId: String,
    val personaName: String,
    val model: String,
    val date: Long = System.currentTimeMillis(),
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val durationSeconds: Int = 0,
    val costDollars: Double = 0.0,
    val kind: String = "conversation" // "conversation" or "image"
)
