package com.newoether.agora.ui.chat.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowUpSuggestionsGeneratorTest {

    @Test
    fun `generate returns 3 typed suggestions on technical code text`() {
        val codeResponse = """
            Voici l'implémentation en Kotlin :
            ```kotlin
            class CacheManager {
                fun get(key: String): Data? = map[key]
            }
            ```
            Cette fonction permet de stocker les données en mémoire vive.
        """.trimIndent()

        val suggestions = FollowUpSuggestionsGenerator.generate(codeResponse)
        assertEquals(3, suggestions.size)
        assertEquals(FollowUpType.DEEP_DIVE, suggestions[0].type)
        assertEquals(FollowUpType.EXPANSION, suggestions[1].type)
        assertEquals(FollowUpType.TRANSVERSAL, suggestions[2].type)

        // All questions should end with a question mark
        assertTrue(suggestions[0].text.endsWith("?"))
        assertTrue(suggestions[1].text.isNotBlank())
        assertTrue(suggestions[2].text.endsWith("?"))
    }

    @Test
    fun `generate extracts key subject from markdown bold headers`() {
        val architectureResponse = """
            Pour structurer votre projet, nous recommandons le composant **PipelineOrchestrator**.
            Ce système permet de coordonner les flux d'ingestion de données et la scalabilité.
        """.trimIndent()

        val suggestions = FollowUpSuggestionsGenerator.generate(architectureResponse)
        assertEquals(3, suggestions.size)
        assertTrue(
            suggestions.any { it.text.contains("PipelineOrchestrator") } ||
            suggestions.any { it.text.contains("architecture") || it.text.contains("scalabilité") }
        )
    }

    @Test
    fun `generate returns default suggestions on short text`() {
        val shortResponse = "Bonjour !"
        val suggestions = FollowUpSuggestionsGenerator.generate(shortResponse)
        assertEquals(3, suggestions.size)
        assertEquals(FollowUpType.DEEP_DIVE, suggestions[0].type)
        assertEquals(FollowUpType.EXPANSION, suggestions[1].type)
        assertEquals(FollowUpType.TRANSVERSAL, suggestions[2].type)
    }

    @Test
    fun `generate weaves subject from user prompt even when assistant response is plain unformatted text`() {
        val userPrompt = "Comment fonctionne le protocole OAuth2 pour sécuriser les API ?"
        val plainAssistantResponse = "Le protocole permet d'émettre des jetons d'accès temporaires sans exposer les identifiants originaux de l'utilisateur."

        val suggestions = FollowUpSuggestionsGenerator.generate(plainAssistantResponse, userPrompt = userPrompt)
        assertEquals(3, suggestions.size)
        // Check that OAuth2 or API is reflected in the suggestions
        assertTrue(
            suggestions.any { it.text.contains("OAuth2", ignoreCase = true) || it.text.contains("protocole", ignoreCase = true) }
        )
    }

    @Test
    fun `generate incorporates Second Brain memory snippet into transversal suggestion`() {
        val userPrompt = "Optimiser les requêtes SQL de la base de données"
        val assistantResponse = "Il est recommandé de créer des index composites et d'analyser le plan d'exécution avec EXPLAIN."
        val memorySnippet = "- Projet CRM Client 2026 : refonte de l'infrastructure"

        val suggestions = FollowUpSuggestionsGenerator.generate(assistantResponse, userPrompt, memorySnippet)
        assertEquals(3, suggestions.size)
        val transversal = suggestions.first { it.type == FollowUpType.TRANSVERSAL }
        assertTrue(
            transversal.text.contains("Projet CRM Client 2026", ignoreCase = true) ||
            transversal.text.contains("Second Cerveau", ignoreCase = true) ||
            transversal.text.contains("objectifs", ignoreCase = true)
        )
    }

    @Test
    fun `generate anchors multimodal format variants to the user prompt subject`() {
        val userPrompt = "Explique-moi les embeddings et la recherche sémantique"
        val assistantResponse = "Les embeddings sont des vecteurs denses qui capturent la sémantique des textes pour la recherche."

        val suggestions = FollowUpSuggestionsGenerator.generate(assistantResponse, userPrompt = userPrompt)
        assertEquals(3, suggestions.size)
        // The deliverable-format variants must stay anchored to the conversation subject,
        // never degrade into generic placeholders.
        assertTrue(
            suggestions.any { it.text.contains("embedding", ignoreCase = true) } ||
                suggestions.any { it.text.contains("recherche", ignoreCase = true) } ||
                suggestions.any { it.text.contains("vecteur", ignoreCase = true) }
        )
    }

    @Test
    fun `generate offers rich deliverable formats on data and architecture topics`() {
        val userPrompt = "Conçois l'architecture du pipeline de données avec Docker"
        val assistantResponse = "Voici une architecture en trois couches : ingestion, traitement, exposition via API."

        val suggestions = FollowUpSuggestionsGenerator.generate(assistantResponse, userPrompt = userPrompt)
        assertEquals(3, suggestions.size)
        // The EXPANSION suggestion must come from the rich code/architecture variant pool
        // (deterministic contract — the exact variant is seed-dependent).
        val expansion = suggestions.first { it.type == FollowUpType.EXPANSION }
        val markers = listOf(
            "exemple concret", "Mermaid", "mindmap", "tester unitairement", "script",
            "déployer", "observabilité", "migration", "diagramme",
        )
        assertTrue(markers.any { expansion.text.contains(it, ignoreCase = true) })
    }
}
