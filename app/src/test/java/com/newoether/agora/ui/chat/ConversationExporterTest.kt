package com.newoether.agora.ui.chat

import com.newoether.agora.model.ChatMessage
import com.newoether.agora.model.Participant
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationExporterTest {

    @Test
    fun `export formats retain multiple prior user and model messages`() {
        val messages = listOf(
            ChatMessage(
                id = "msg-1",
                text = "Première question de l'utilisateur sur l'architecture",
                participant = Participant.USER,
                timestamp = 1700000000000L
            ),
            ChatMessage(
                id = "msg-2",
                text = "Première réponse de l'assistant détaillée",
                participant = Participant.MODEL,
                timestamp = 1700000010000L
            ),
            ChatMessage(
                id = "msg-3",
                text = "Deuxième question de relance",
                participant = Participant.USER,
                timestamp = 1700000020000L
            ),
            ChatMessage(
                id = "msg-4",
                text = "Deuxième réponse finale de l'assistant",
                participant = Participant.MODEL,
                timestamp = 1700000030000L
            ),
        )

        // Reflection or invoking methods to verify formatting
        val formatAsTxtMethod = ConversationExporter::class.java.getDeclaredMethod(
            "formatAsTxt",
            String::class.java,
            List::class.java
        ).apply { isAccessible = true }

        val txt = formatAsTxtMethod.invoke(ConversationExporter, "Projet Alpha", messages) as String
        assertTrue(txt.contains("Première question de l'utilisateur sur l'architecture"))
        assertTrue(txt.contains("Première réponse de l'assistant détaillée"))
        assertTrue(txt.contains("Deuxième question de relance"))
        assertTrue(txt.contains("Deuxième réponse finale de l'assistant"))

        val formatAsMdMethod = ConversationExporter::class.java.getDeclaredMethod(
            "formatAsMd",
            String::class.java,
            List::class.java
        ).apply { isAccessible = true }

        val md = formatAsMdMethod.invoke(ConversationExporter, "Projet Alpha", messages) as String
        assertTrue(md.contains("Première question de l'utilisateur sur l'architecture"))
        assertTrue(md.contains("Première réponse de l'assistant détaillée"))
        assertTrue(md.contains("Deuxième question de relance"))
        assertTrue(md.contains("Deuxième réponse finale de l'assistant"))

        val formatAsHtmlMethod = ConversationExporter::class.java.getDeclaredMethod(
            "formatAsHtml",
            String::class.java,
            List::class.java
        ).apply { isAccessible = true }

        val html = formatAsHtmlMethod.invoke(ConversationExporter, "Projet Alpha", messages) as String
        assertTrue(html.contains("Première question de l&#39;utilisateur sur l&#39;architecture"))
        assertTrue(html.contains("Deuxième réponse finale de l&#39;assistant"))

        val formatAsJsonMethod = ConversationExporter::class.java.getDeclaredMethod(
            "formatAsJson",
            List::class.java
        ).apply { isAccessible = true }

        val json = formatAsJsonMethod.invoke(ConversationExporter, messages) as String
        assertTrue(json.contains("Première question de l'utilisateur sur l'architecture"))
        assertTrue(json.contains("Deuxième réponse finale de l'assistant"))
    }
}
