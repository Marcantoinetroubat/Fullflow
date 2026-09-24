package com.newoether.agora.ui.chat

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.newoether.agora.model.ChatMessage
import com.newoether.agora.model.Participant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportFormat(val displayName: String, val extension: String, val mimeType: String) {
    TXT("Texte brut (.txt)", "txt", "text/plain"),
    MD("Markdown (.md)", "md", "text/markdown"),
    HTML("Page Web (.html)", "html", "text/html"),
    JSON("Données JSON (.json)", "json", "application/json")
}

object ConversationExporter {
    suspend fun export(
        context: Context,
        messages: List<ChatMessage>,
        format: ExportFormat,
        conversationTitle: String? = "Conversation"
    ) {
        val title = conversationTitle?.takeIf { it.isNotBlank() } ?: "Conversation"
        val formattedContent = when (format) {
            ExportFormat.TXT -> formatAsTxt(title, messages)
            ExportFormat.MD -> formatAsMd(title, messages)
            ExportFormat.HTML -> formatAsHtml(title, messages)
            ExportFormat.JSON -> formatAsJson(messages)
        }

        withContext(Dispatchers.IO) {
            val exportDirectory = File(context.cacheDir, "exports").apply { mkdirs() }
            val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9]"), "_").take(30)
            val file = File.createTempFile("agora_${sanitizedTitle}_", ".${format.extension}", exportDirectory)
            file.writeText(formattedContent, Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = format.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("Agora Export", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            withContext(Dispatchers.Main) {
                val chooser = Intent.createChooser(sendIntent, "Exporter la conversation")
                if (context !is android.app.Activity) {
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
        }
    }

    private fun formatAsTxt(title: String, messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        sb.append("=== ").append(title).append(" ===\n\n")
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        for (msg in messages) {
            val dateStr = sdf.format(Date(msg.timestamp))
            val sender = when (msg.participant) {
                Participant.USER -> "Utilisateur"
                Participant.MODEL -> "Agora"
                Participant.ERROR -> "Système"
            }
            sb.append("[").append(dateStr).append("] ").append(sender).append(":\n")
            sb.append(msg.text).append("\n")
            sb.append("----------------------------------------\n\n")
        }
        return sb.toString()
    }

    private fun formatAsMd(title: String, messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        sb.append("# ").append(title).append("\n\n")
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        for (msg in messages) {
            val dateStr = sdf.format(Date(msg.timestamp))
            val sender = when (msg.participant) {
                Participant.USER -> "**Utilisateur**"
                Participant.MODEL -> "**Agora**"
                Participant.ERROR -> "*Système (Erreur)*"
            }
            sb.append("### ").append(sender).append(" _(").append(dateStr).append(")_\n\n")
            sb.append(msg.text).append("\n\n")
            sb.append("---\n\n")
        }
        return sb.toString()
    }

    private fun formatAsHtml(title: String, messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        sb.append("""
            <!DOCTYPE html>
            <html lang="fr">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Export de Conversation</title>
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        background-color: #f3f4f6;
                        color: #1f2937;
                        padding: 24px;
                        line-height: 1.6;
                        margin: 0;
                    }
                    .container {
                        max-width: 800px;
                        margin: 0 auto;
                        background: #ffffff;
                        padding: 32px;
                        border-radius: 16px;
                        box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
                    }
                    h1 {
                        font-size: 1.8rem;
                        color: #111827;
                        border-bottom: 2px solid #e5e7eb;
                        padding-bottom: 12px;
                        margin-top: 0;
                        margin-bottom: 24px;
                    }
                    .message {
                        margin-bottom: 20px;
                        padding: 16px;
                        border-radius: 12px;
                    }
                    .user {
                        background-color: #eff6ff;
                        border-left: 4px solid #3b82f6;
                    }
                    .model {
                        background-color: #f9fafb;
                        border-left: 4px solid #10b981;
                    }
                    .error {
                        background-color: #fef2f2;
                        border-left: 4px solid #ef4444;
                    }
                    .header {
                        font-weight: bold;
                        font-size: 0.9rem;
                        color: #4b5563;
                        margin-bottom: 8px;
                        display: flex;
                        justify-content: space-between;
                    }
                    .content {
                        white-space: pre-wrap;
                        font-size: 1rem;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>Export : ${escapeHtml(title)}</h1>
        """.trimIndent())
        sb.append("\n")

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        for (msg in messages) {
            val dateStr = sdf.format(Date(msg.timestamp))
            val (sender, className) = when (msg.participant) {
                Participant.USER -> "Utilisateur" to "user"
                Participant.MODEL -> "Agora" to "model"
                Participant.ERROR -> "Système / Erreur" to "error"
            }
            sb.append("""
                    <div class="message $className">
                        <div class="header">
                            <span>$sender</span>
                            <span>$dateStr</span>
                        </div>
                        <div class="content">${escapeHtml(msg.text)}</div>
                    </div>
            """.trimIndent())
            sb.append("\n")
        }

        sb.append("""
                </div>
            </body>
            </html>
        """.trimIndent())
        return sb.toString()
    }

    private fun formatAsJson(messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        sb.append("[\n")
        for (i in messages.indices) {
            val msg = messages[i]
            sb.append("  {\n")
            sb.append("    \"id\": \"").append(escapeJson(msg.id)).append("\",\n")
            sb.append("    \"sender\": \"").append(msg.participant.name).append("\",\n")
            sb.append("    \"timestamp\": ").append(msg.timestamp).append(",\n")
            sb.append("    \"content\": \"").append(escapeJson(msg.text)).append("\"\n")
            sb.append("  }")
            if (i < messages.lastIndex) {
                sb.append(",")
            }
            sb.append("\n")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun escapeJson(text: String): String {
        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
