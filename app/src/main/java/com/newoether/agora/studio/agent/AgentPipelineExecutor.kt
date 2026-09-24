package com.newoether.agora.studio.agent

import android.content.Context
import com.newoether.agora.AgoraApplication
import com.newoether.agora.api.ProviderConfig
import com.newoether.agora.api.StreamEvent
import com.newoether.agora.model.ChatMessage
import com.newoether.agora.model.Participant
import com.newoether.agora.studio.agent.db.AgentDao
import com.newoether.agora.studio.agent.db.AgentEntity
import com.newoether.agora.studio.agent.db.PipelineEntity
import com.newoether.agora.viewmodel.ProviderRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

data class PipelineProgress(
    val stepIndex: Int,
    val totalSteps: Int,
    val agentName: String,
    val agentSpecialty: String,
    val partialOutput: String,
)

data class PipelineResult(
    val question: String,
    val pipelineName: String,
    val agentContributions: List<Pair<String, String>>, // agentName to output text
    val finalSynthesis: String,
    val totalDurationMs: Long,
)

class AgentPipelineExecutor(
    private val context: Context,
) {
    private val app: AgoraApplication? = context.applicationContext as? AgoraApplication
    private val container = app?.requireContainer()
    private val agentDao: AgentDao? = container?.database?.agentDao()
    private val providerRegistry: ProviderRegistry? = container?.providerRegistry
    private val settingsRepo = container?.settingsRepository

    /**
     * Executes a pipeline of agents sequentially on the user's question.
     * Each agent receives the previous agent's output and enriches it.
     * After [pipeline.loopCount] rounds, the synthesizer produces the final answer.
     */
    suspend fun execute(
        question: String,
        pipeline: PipelineEntity,
        onProgress: (suspend (PipelineProgress) -> Unit)? = null,
    ): PipelineResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val agentIds = parseAgentIds(pipeline.agentIdsJson)
        val agents = agentIds.mapNotNull { id -> agentDao?.getAgentById(id) }
        val synthesizer = if (pipeline.synthesizerAgentId.isNotBlank()) {
            agentDao?.getAgentById(pipeline.synthesizerAgentId)
        } else {
            agentDao?.getAgentById("synthesizer")
        } ?: AgentDefaults.synthesizerAgent

        if (agents.isEmpty()) {
            return@withContext PipelineResult(
                question = question,
                pipelineName = pipeline.name,
                agentContributions = emptyList(),
                finalSynthesis = "Aucun agent actif dans ce pipeline.",
                totalDurationMs = 0,
            )
        }

        val contributions = mutableListOf<Pair<String, String>>()
        var currentInput = question
        var stepIndex = 0
        val totalSteps = pipeline.loopCount * agents.size + 1 // +1 for synthesizer

        // Run agents in sequence, looping if needed
        repeat(pipeline.loopCount) { round ->
            agents.forEach { agent ->
                stepIndex++
                val roundSuffix = if (pipeline.loopCount > 1) " (Tour ${round + 1}/${pipeline.loopCount})" else ""
                
                onProgress?.invoke(PipelineProgress(
                    stepIndex = stepIndex,
                    totalSteps = totalSteps,
                    agentName = agent.name,
                    agentSpecialty = "${agent.specialty}$roundSuffix",
                    partialOutput = currentInput.take(200),
                ))

                val output = runAgent(agent, currentInput)
                currentInput = output
                contributions.add(agent.name to output)
            }
        }

        // Final synthesis
        stepIndex++
        onProgress?.invoke(PipelineProgress(
            stepIndex = stepIndex,
            totalSteps = totalSteps,
            agentName = synthesizer.name,
            agentSpecialty = "Synthèse finale",
            partialOutput = currentInput.take(200),
        ))

        val synthesisPrompt = buildString {
            appendLine("Tu es ${synthesizer.name}, le synthétiseur final.")
            appendLine()
            appendLine("Tu reçois ci-dessous le travail de plusieurs experts qui ont enrichi la question.")
            appendLine("Ta mission : produire la synthèse finale, la plus claire et la plus actionnable possible.")
            appendLine()
            appendLine("# Question originale")
            appendLine(question)
            appendLine()
            appendLine("# Travail des experts")
            currentInput.take(4000).let { appendLine(it) }
            appendLine()
            appendLine("# Instructions")
            appendLine(synthesizer.systemPrompt)
            appendLine()
            appendLine("Produis la synthèse finale maintenant.")
        }

        val finalOutput = runAgent(synthesizer, synthesisPrompt)
        val durationMs = System.currentTimeMillis() - startTime

        PipelineResult(
            question = question,
            pipelineName = pipeline.name,
            agentContributions = contributions,
            finalSynthesis = finalOutput,
            totalDurationMs = durationMs,
        )
    }

    /**
     * Runs a single agent with its system prompt on the given input text.
     * Uses the model configured on the agent (or the app's selected model as fallback).
     */
    private suspend fun runAgent(agent: AgentEntity, inputText: String): String {
        if (providerRegistry == null || settingsRepo == null) return inputText

        val modelId = if (agent.modelId.isNotBlank()) agent.modelId else settingsRepo.selectedModel.value
        val providerName = providerRegistry.providerForModel(modelId)
        val provider = providerRegistry.getInstanceOrNull(providerName) ?: return inputText
        val apiKey = settingsRepo.resolveActiveKey(providerName) ?: ""
        val baseUrl = settingsRepo.providerBaseUrls.value[providerName]

        val messages = listOf(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                text = inputText,
                participant = Participant.USER,
            )
        )

        val config = ProviderConfig(
            apiKey = apiKey,
            modelId = modelId,
            systemPrompt = agent.systemPrompt,
            baseUrl = baseUrl,
            thinkingEnabled = false,
        )

        val outputBuilder = StringBuilder()
        provider.generateResponse(messages, config).collect { event ->
            if (event is StreamEvent.TextChunk) {
                outputBuilder.append(event.text)
            }
        }

        return outputBuilder.toString().ifBlank { inputText }
    }

    private fun parseAgentIds(json: String): List<String> {
        return try {
            val parsed = kotlinx.serialization.json.Json.parseToJsonElement(json).jsonArray
            parsed.map { element -> element.jsonPrimitive.content }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
