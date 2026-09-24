package com.newoether.agora.wand

/**
 * Canon of the Magic Wand feature (cahier des charges « Voix & Baguette Magique »).
 * Pure Kotlin data model: no Android imports here so unit tests stay JVM-only.
 */

enum class RequestType(val label: String) {
    RECHERCHE_VEILLE("Recherche et veille"),
    SYNTHESE("Synthèse"),
    BRAINSTORMING("Brainstorming et réflexion stratégique"),
    ARBITRAGE("Arbitrage et décision"),
    CAHIER_DES_CHARGES("Cahier des charges"),
    AUDIT("Audit et diagnostic"),
    PLAN_ACTION("Plan d'action"),
    TRADUCTION_ACCESSIBLE("Traduction accessible"),
    REDACTION("Rédaction et communication"),
    ANALYSE_FINANCIERE("Analyse financière et chiffrage"),
    REFORMULATION("Reformulation et amplification"),
}

enum class ConnectionId(val label: String) {
    DRIVE("Drive"),
    DOCS("Docs"),
    GITHUB("GitHub"),
    YOUTUBE("YouTube"),
    GOOGLE_FINANCE("Google Finance"),
    NEWS("Actualités"),
    GMAIL("Gmail"),
    CALENDAR("Agenda"),
    WEB_SERPER("Recherche web"),
}

/** A prompt structure per request type. Editable by the user in settings (Lot B). */
data class PromptStructure(
    val id: String,
    val type: RequestType,
    val name: String,
    val template: String,
    val defaultConnections: List<ConnectionId>,
    val defaultSkills: List<String> = emptyList(),
    val version: Int = 1,
)

data class Skill(val id: String, val name: String, val instructions: String, val version: Int = 1)

data class PersonaBrief(val content: String, val version: Int, val updatedAt: Long)

data class WandRequest(
    val type: RequestType,
    val rawInput: String,
    val structureId: String,
    val connections: List<ConnectionId>,
    val skills: List<String>,
)

data class WandResult(
    val optimizedPrompt: String,
    val ambiguities: List<String>,
    val structureApplied: String,
    val connectionsDeclared: List<ConnectionId>,
    val skillsApplied: List<String>,
    /** Estimated compilation cost in USD, for the 0.01 $/compile guard. */
    val estimatedCostUsd: Double = 0.0,
)
