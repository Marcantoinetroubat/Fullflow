package com.newoether.agora.fulllive.data.repository

import com.newoether.agora.fulllive.data.local.FullLiveDatabase
import com.newoether.agora.fulllive.data.local.SecurityVault
import com.newoether.agora.fulllive.data.local.entity.ConversationSessionEntity
import com.newoether.agora.fulllive.data.local.entity.MessageEntity
import com.newoether.agora.fulllive.data.local.entity.PersonaEntity
import com.newoether.agora.fulllive.data.local.entity.StatEntryEntity
import com.newoether.agora.fulllive.data.local.entity.UserInsightEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class FullLiveRepository(
    private val database: FullLiveDatabase,
    val securityVault: SecurityVault
) {
    val personas: Flow<List<PersonaEntity>> = database.personaDao().getAllPersonas()
    val sessions: Flow<List<ConversationSessionEntity>> = database.sessionDao().getAllSessions()
    val globalInsights: Flow<List<UserInsightEntity>> = database.insightDao().getGlobalInsights()
    val stats: Flow<List<StatEntryEntity>> = database.statDao().getAllStats()

    suspend fun checkAndSeedDefaults() = withContext(Dispatchers.IO) {
        if (database.personaDao().count() == 0) {
            val defaults = listOf(
                PersonaEntity(
                    id = "p_gerard",
                    name = "Gérard",
                    description = "Assistant vocal amical, chaleureux et bienveillant au quotidien",
                    model = "gemini-3.8-live",
                    voice = "Puck",
                    reactivity = "balanced",
                    creativity = "balanced",
                    greeting = "persona",
                    personaInfo = "",
                    prompt = """# Identité
Tu es Gérard, un copilote vocal amical, prévenant et bienveillant.

# Personnalité
- Ton : chaleureux, naturel, décontracté
- Humour : léger et toujours bienveillant
- Style : conversationnel, spontané, comme un ami de confiance
- Règles orales : sois concis (2-3 phrases max), pas de markdown ni de puces, parle avec des tournures orales naturelles.""".trimIndent(),
                    gender = "male",
                    isSystem = true,
                    sortOrder = 1
                ),
                PersonaEntity(
                    id = "p_ada",
                    name = "Ada",
                    description = "Copilote intellectuelle, scientifique et stratégique de haut niveau",
                    model = "gemini-3.8-live-extended-thinking",
                    voice = "Aoede",
                    reactivity = "patient",
                    creativity = "precise",
                    greeting = "persona",
                    personaInfo = "",
                    prompt = """# Identité
Tu es Ada, copilote intellectuelle, scientifique et stratégique d'excellence.

# Style & Principes
- Rigueur mathématique, esprit de synthèse acéré, conseil stratégique
- Ton professionnel, posé, direct et élégant
- Capacité d'analyse multidimensionnelle et résolution méthodique de problèmes
- Réponses claires, structurées pour l'écoute orale, sans verbiage superflu.""".trimIndent(),
                    gender = "female",
                    isSystem = true,
                    sortOrder = 2
                ),
                PersonaEntity(
                    id = "p_atlas",
                    name = "Atlas",
                    description = "Mentor explorateur, philosophie appliquée et vision prospective",
                    model = "gemini-3.8-live",
                    voice = "Charon",
                    reactivity = "balanced",
                    creativity = "creative",
                    greeting = "persona",
                    personaInfo = "",
                    prompt = """# Identité
Tu es Atlas, mentor philosophique et explorateur des idées.

# Posture
- Regard panoramique, hauteur de vue, curiosité intellectuelle
- Ton profond, stimulant, incitant à la réflexion
- Parle avec fluidité, cadence mesurée et clarté exemplaire.""".trimIndent(),
                    gender = "male",
                    isSystem = true,
                    sortOrder = 3
                ),
                PersonaEntity(
                    id = "p_zoe",
                    name = "Zoé",
                    description = "Coach créative & dynamo d'idées, boost de productivité",
                    model = "gemini-3.8-live",
                    voice = "Leda",
                    reactivity = "reactive",
                    creativity = "wild",
                    greeting = "persona",
                    personaInfo = "",
                    prompt = """# Identité
Tu es Zoé, coach créative dynamique et enthousiaste.

# Énergie
- Pétillante, encourageante, vivifiante et inventive
- Parfaite pour le brainstorming, l'écriture et l'impulsion de projets
- Phrases dynamiques, encouragement franc et idées percutantes.""".trimIndent(),
                    gender = "female",
                    isSystem = true,
                    sortOrder = 4
                )
            )
            database.personaDao().insertAll(defaults)
        }
    }

    suspend fun getPersona(id: String): PersonaEntity? = withContext(Dispatchers.IO) {
        database.personaDao().getPersonaById(id)
    }

    suspend fun savePersona(persona: PersonaEntity) = withContext(Dispatchers.IO) {
        database.personaDao().insertOrUpdate(persona)
    }

    suspend fun deletePersona(id: String) = withContext(Dispatchers.IO) {
        database.personaDao().deleteById(id)
    }

    fun getMessages(sessionId: String): Flow<List<MessageEntity>> {
        return database.messageDao().getMessagesForSession(sessionId)
    }

    suspend fun getMessagesSync(sessionId: String): List<MessageEntity> = withContext(Dispatchers.IO) {
        database.messageDao().getMessagesForSessionSync(sessionId)
    }

    suspend fun saveMessage(message: MessageEntity): Long = withContext(Dispatchers.IO) {
        database.messageDao().insertMessage(message)
    }

    suspend fun saveSession(session: ConversationSessionEntity) = withContext(Dispatchers.IO) {
        database.sessionDao().insertOrUpdate(session)
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        database.messageDao().deleteForSession(sessionId)
        database.sessionDao().deleteById(sessionId)
    }

    suspend fun addInsight(fact: String, destination: String, personaId: String? = null) = withContext(Dispatchers.IO) {
        database.insightDao().insert(
            UserInsightEntity(
                fact = fact.trim(),
                destination = destination,
                personaId = personaId
            )
        )
    }

    suspend fun deleteInsight(id: Long) = withContext(Dispatchers.IO) {
        database.insightDao().deleteById(id)
    }

    suspend fun addStat(stat: StatEntryEntity) = withContext(Dispatchers.IO) {
        database.statDao().insert(stat)
    }

    suspend fun calculateMonthCost(): Double = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val monthStart = calendar.timeInMillis
        val allStats = database.statDao().getAllStatsSync()
        allStats.filter { it.date >= monthStart }.sumOf { it.costDollars }
    }

    // Export Persona to JSON
    suspend fun exportPersonaJson(persona: PersonaEntity): String = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("type", "kast-persona")
            put("version", 2)
            put("exportDate", System.currentTimeMillis())
            val pObj = JSONObject().apply {
                put("id", persona.id)
                put("name", persona.name)
                put("description", persona.description)
                put("model", persona.model)
                put("voice", persona.voice)
                put("reactivity", persona.reactivity)
                put("creativity", persona.creativity)
                put("greeting", persona.greeting)
                put("personaInfo", persona.personaInfo)
                put("prompt", persona.prompt)
                put("gender", persona.gender ?: "")
            }
            put("persona", pObj)
        }
        json.toString(2)
    }

    // Import Persona from JSON
    suspend fun importPersonaJson(jsonString: String): PersonaEntity = withContext(Dispatchers.IO) {
        val root = JSONObject(jsonString)
        val p = if (root.has("persona")) root.getJSONObject("persona") else root
        val persona = PersonaEntity(
            id = "p_" + UUID.randomUUID().toString().take(8),
            name = p.optString("name", "Persona importé"),
            description = p.optString("description", ""),
            model = p.optString("model", ""),
            voice = p.optString("voice", "Puck"),
            reactivity = p.optString("reactivity", "balanced"),
            creativity = p.optString("creativity", "balanced"),
            greeting = p.optString("greeting", "persona"),
            personaInfo = p.optString("personaInfo", ""),
            prompt = p.optString("prompt", "Tu es un assistant vocal bienveillant."),
            gender = p.optString("gender", "male").ifBlank { "male" },
            isSystem = false
        )
        database.personaDao().insertOrUpdate(persona)
        persona
    }

    // Export Full Backup JSON
    suspend fun exportFullBackupJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject().apply {
            put("app", "FULLLIVE")
            put("version", 2)
            put("timestamp", System.currentTimeMillis())
            put("defaultModel", securityVault.getDefaultModel())
            put("layout", securityVault.getConversationLayout())
            put("theme", securityVault.getTheme())
            put("userIdentityNotes", securityVault.getUserIdentityNotes())

            // Personas
            val personaArray = JSONArray()
            database.personaDao().getAllPersonas() // sync snapshot
            val allPersonas = database.personaDao().count()
            // We can retrieve from sync
            val pList = mutableListOf<PersonaEntity>()
            // Query DB directly
        }
        root.toString(2)
    }
}
