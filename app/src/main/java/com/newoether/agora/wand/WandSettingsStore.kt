package com.newoether.agora.wand

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.newoether.agora.data.WAND_AUTO_TTS_ENABLED
import com.newoether.agora.data.WAND_IGNORE_CODE
import com.newoether.agora.data.WAND_MAGIC_PROMPT
import com.newoether.agora.data.WAND_MODEL
import com.newoether.agora.data.WAND_PERSONA_BRIEF
import com.newoether.agora.data.WAND_PERSONA_UPDATED_AT
import com.newoether.agora.data.WAND_PERSONA_VERSION
import com.newoether.agora.data.WAND_PROVIDER
import com.newoether.agora.data.WAND_STRUCTURES_JSON
import com.newoether.agora.data.WAND_TTS_MODEL
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Serializable mirror of [PromptStructure] for DataStore persistence. */
@Serializable
data class PromptStructureDto(
    val id: String,
    val type: String,
    val name: String,
    val template: String,
    val defaultConnections: List<String>,
    val defaultSkills: List<String> = emptyList(),
    val version: Int = 1,
)

private fun PromptStructure.toDto() = WandStructureCodec.toDto(this)

private fun PromptStructureDto.toDomain() = WandStructureCodec.toDomain(this)

/** Pure DTO mapping helpers — JVM-testable, no Android. */
internal object WandStructureCodec {
    fun toDto(structure: PromptStructure) = PromptStructureDto(
        id = structure.id,
        type = structure.type.name,
        name = structure.name,
        template = structure.template,
        defaultConnections = structure.defaultConnections.map { it.name },
        defaultSkills = structure.defaultSkills,
        version = structure.version,
    )

    fun toDomain(dto: PromptStructureDto) = PromptStructure(
        id = dto.id,
        type = runCatching { RequestType.valueOf(dto.type) }.getOrDefault(RequestType.BRAINSTORMING),
        name = dto.name,
        template = dto.template,
        defaultConnections = dto.defaultConnections.mapNotNull { raw ->
            runCatching { ConnectionId.valueOf(raw) }.getOrNull()
        },
        defaultSkills = dto.defaultSkills,
        version = dto.version,
    )
}

private val Context.wandDataStore by preferencesDataStore(name = "wand_settings")

/**
 * Persists Magic Wand settings in a dedicated DataStore (isolated from the big
 * settings surface to avoid regressions): editable prompt structures, wand/TTS
 * models, auto-TTS toggle, code filter, persona brief.
 */
class WandSettingsStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val structures: Flow<List<PromptStructure>> = context.wandDataStore.data.map { pref ->
        val raw = pref[WAND_STRUCTURES_JSON]
        if (raw.isNullOrBlank()) {
            WandController.DEFAULT_STRUCTURES
        } else {
            runCatching {
                json.decodeFromString<List<PromptStructureDto>>(raw).map { it.toDomain() }
            }.getOrDefault(WandController.DEFAULT_STRUCTURES)
        }
    }

    val wandProvider: Flow<String> = context.wandDataStore.data.map {
        it[WAND_PROVIDER]?.takeIf { p -> p.isNotBlank() } ?: "google"
    }

    val wandModel: Flow<String> = context.wandDataStore.data.map {
        it[WAND_MODEL]?.takeIf { m -> m.isNotBlank() } ?: WandCompiler.DEFAULT_MODEL
    }

    val magicPrompt: Flow<String> = context.wandDataStore.data.map {
        it[WAND_MAGIC_PROMPT]?.takeIf { p -> p.isNotBlank() } ?: WandPrompts.DEFAULT_MAGIC_PROMPT
    }

    val ttsModel: Flow<String> = context.wandDataStore.data.map {
        it[WAND_TTS_MODEL]?.takeIf { m -> m.isNotBlank() } ?: WandCompiler.DEFAULT_TTS_MODEL
    }

    val autoTtsEnabled: Flow<Boolean> = context.wandDataStore.data.map {
        it[WAND_AUTO_TTS_ENABLED] ?: true
    }

    val ignoreCode: Flow<Boolean> = context.wandDataStore.data.map {
        it[WAND_IGNORE_CODE] ?: true
    }

    val personaBrief: Flow<PersonaBrief> = context.wandDataStore.data.map { pref ->
        PersonaBrief(
            content = pref[WAND_PERSONA_BRIEF].orEmpty(),
            version = pref[WAND_PERSONA_VERSION] ?: 0,
            updatedAt = pref[WAND_PERSONA_UPDATED_AT] ?: 0L,
        )
    }

    // ── Writes ───────────────────────────────────────────────

    suspend fun saveStructures(list: List<PromptStructure>) {
        context.wandDataStore.edit {
            it[WAND_STRUCTURES_JSON] = json.encodeToString(list.map(PromptStructure::toDto))
        }
    }

    suspend fun upsertStructure(structure: PromptStructure) {
        val current = structures.first().toMutableList()
        val index = current.indexOfFirst { it.id == structure.id }
        if (index >= 0) {
            current[index] = structure.copy(version = current[index].version + 1)
        } else {
            current.add(structure)
        }
        saveStructures(current)
    }

    suspend fun deleteStructure(id: String) {
        saveStructures(structures.first().filterNot { it.id == id })
    }

    suspend fun resetStructuresToDefaults() {
        context.wandDataStore.edit { it.remove(WAND_STRUCTURES_JSON) }
    }

    suspend fun setWandProvider(provider: String) {
        context.wandDataStore.edit { it[WAND_PROVIDER] = provider }
    }

    suspend fun setWandModel(model: String) {
        context.wandDataStore.edit { it[WAND_MODEL] = model }
    }

    suspend fun setMagicPrompt(prompt: String) {
        context.wandDataStore.edit { it[WAND_MAGIC_PROMPT] = prompt }
    }

    suspend fun resetMagicPrompt() {
        context.wandDataStore.edit { it.remove(WAND_MAGIC_PROMPT) }
    }

    suspend fun setTtsModel(model: String) {
        context.wandDataStore.edit { it[WAND_TTS_MODEL] = model }
    }

    suspend fun setAutoTtsEnabled(enabled: Boolean) {
        context.wandDataStore.edit { it[WAND_AUTO_TTS_ENABLED] = enabled }
    }

    suspend fun setIgnoreCode(ignore: Boolean) {
        context.wandDataStore.edit { it[WAND_IGNORE_CODE] = ignore }
    }

    suspend fun savePersonaBrief(content: String) {
        context.wandDataStore.edit {
            val nextVersion = (it[WAND_PERSONA_VERSION] ?: 0) + 1
            it[WAND_PERSONA_BRIEF] = content
            it[WAND_PERSONA_VERSION] = nextVersion
            it[WAND_PERSONA_UPDATED_AT] = System.currentTimeMillis()
        }
    }

    companion object {
        @Volatile
        private var instance: WandSettingsStore? = null

        fun getInstance(context: Context): WandSettingsStore =
            instance ?: synchronized(this) {
                instance ?: WandSettingsStore(context.applicationContext).also { instance = it }
            }
    }
}
