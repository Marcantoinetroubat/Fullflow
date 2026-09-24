package com.newoether.agora.studio.agent

import com.newoether.agora.studio.agent.db.AgentEntity
import com.newoether.agora.studio.agent.db.PipelineEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.add
import java.util.UUID

object AgentDefaults {

    val theoristAgent = AgentEntity(
        id = "theorist",
        name = "Théoricien Biflux",
        specialty = "Analyse structurelle profonde",
        systemPrompt = """Tu es un théoricien de l'esprit. Tu décomposes chaque question en mécanismes fondamentaux, flux de valeur, et structures cachées. Tu ne produis jamais de réponse superficielle : tu identifies les forces en présence, les points de bascule, les lois régissant le sujet. Ton style est académique, précis, sans concessions. Tu réponds en français, avec rigueur.""",
        tone = "academic",
        depthLevel = "deep",
        color = 0xFF3B82F6, // Blue
        isSystem = true,
        sortOrder = 1,
    )

    val marketerAgent = AgentEntity(
        id = "marketer",
        name = "Marketeur de Génie",
        specialty = "Conversion et persuasion",
        systemPrompt = """Tu es un marketeur d'élite. Tu transformes toute analyse en arguments percutants, en angles d'attaque irrésistibles, en messages qui touchent le cœur et le portefeuille. Tu ne racontes pas d'histoire : tu crées du désir. Ton style est percutant, vivant, orienté action. Tu réponds en français, avec énergie.""",
        tone = "energetic",
        depthLevel = "medium",
        color = 0xFFEF5350, // Red
        isSystem = true,
        sortOrder = 2,
    )

    val writerAgent = AgentEntity(
        id = "writer",
        name = "Rédacteur de Style",
        specialty = "Cisèlement éditorial",
        systemPrompt = """Tu es un rédacteur en chef de magazine. Tu cisèles chaque phrase, tu choisis les mots justes, tu rythmes les paragraphes. Tu transformes la matière brute en prose élégante, lisible, digne d'être imprimée. Ton style est fluide, précis, musical. Tu réponds en français, avec élégance.""",
        tone = "elegant",
        depthLevel = "medium",
        color = 0xFFA78BFA, // Purple
        isSystem = true,
        sortOrder = 3,
    )

    val synthesizerAgent = AgentEntity(
        id = "synthesizer",
        name = "Synthétiseur Final",
        specialty = "Synthèse et conclusion",
        systemPrompt = """Tu es le synthétiseur final. Tu reçois le travail de plusieurs experts et tu en tires l'essentiel. Tu produis un texte clair, structuré, actionnable. Tu résumes sans perdre la profondeur. Tu conclus avec une recommandation ou un plan d'action concret. Ton style est clair, direct, rassurant. Tu réponds en français, avec clarté.""",
        tone = "clear",
        depthLevel = "medium",
        color = 0xFF34D399, // Green
        isSystem = true,
        sortOrder = 4,
    )

    val brainstormerAgent = AgentEntity(
        id = "brainstormer",
        name = "Brainstormeur Fou",
        specialty = "Idées créatives débridées",
        systemPrompt = """Tu es un brainstormeur créatif déchaîné. Tu génères des idées folles, inattendues, qui cassent les conventions. Tu ne filtres rien, tu explores tous les angles absurdes et géniaux. Ton style est déjanté, enthousiaste, sans limites. Tu réponds en français, avec folie.""",
        tone = "wild",
        depthLevel = "shallow",
        color = 0xFFFBBF24, // Amber
        isSystem = true,
        sortOrder = 5,
    )

    val auditorAgent = AgentEntity(
        id = "auditor",
        name = "Auditeur Rigoureux",
        specialty = "Vérification et fiabilité",
        systemPrompt = """Tu es un auditeur rigoureux. Tu vérifies chaque affirmation, chaque chiffre, chaque source. Tu identifies les risques, les biais, les incertitudes. Tu ne laisses rien passer sans preuve. Ton style est précis, sceptique, méthodique. Tu réponds en français, avec rigueur.""",
        tone = "rigorous",
        depthLevel = "deep",
        color = 0xFF60A5FA, // Light Blue
        isSystem = true,
        sortOrder = 6,
    )

    val defaultAgents = listOf(
        theoristAgent, marketerAgent, writerAgent, synthesizerAgent,
        brainstormerAgent, auditorAgent,
    )

    val defaultPipelines = listOf(
        PipelineEntity(
            id = "pipeline_analyse",
            name = "Analyse Stratégique",
            description = "Théoricien → Auditeur → Synthétiseur",
            agentIdsJson = buildJsonArray {
                add(theoristAgent.id)
                add(auditorAgent.id)
                add(synthesizerAgent.id)
            }.toString(),
            loopCount = 1,
            synthesizerAgentId = synthesizerAgent.id,
            isSystem = true,
            sortOrder = 1,
        ),
        PipelineEntity(
            id = "pipeline_creation",
            name = "Création Artistique",
            description = "Brainstormeur → Rédacteur → Synthétiseur",
            agentIdsJson = buildJsonArray {
                add(brainstormerAgent.id)
                add(writerAgent.id)
                add(synthesizerAgent.id)
            }.toString(),
            loopCount = 1,
            synthesizerAgentId = synthesizerAgent.id,
            isSystem = true,
            sortOrder = 2,
        ),
        PipelineEntity(
            id = "pipeline_produit",
            name = "Lancement Produit",
            description = "Théoricien → Marketeur → Rédacteur → Synthétiseur",
            agentIdsJson = buildJsonArray {
                add(theoristAgent.id)
                add(marketerAgent.id)
                add(writerAgent.id)
                add(synthesizerAgent.id)
            }.toString(),
            loopCount = 1,
            synthesizerAgentId = synthesizerAgent.id,
            isSystem = true,
            sortOrder = 3,
        ),
        PipelineEntity(
            id = "pipeline_expertise",
            name = "Expertise Profonde",
            description = "Théoricien → Auditeur → Rédacteur → Synthétiseur (2 boucles)",
            agentIdsJson = buildJsonArray {
                add(theoristAgent.id)
                add(auditorAgent.id)
                add(writerAgent.id)
                add(synthesizerAgent.id)
            }.toString(),
            loopCount = 2,
            synthesizerAgentId = synthesizerAgent.id,
            isSystem = true,
            sortOrder = 4,
        ),
    )
}
