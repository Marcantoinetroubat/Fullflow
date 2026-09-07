package com.newoether.agora.workspace.drive

import androidx.compose.ui.graphics.Color
import com.newoether.agora.R
import java.text.SimpleDateFormat
import java.util.*

enum class GoogleWorkspaceType(
    val label: String,
    val brandColor: Color,
    val iconResId: Int?,
) {
    DOCS("Google Docs", Color(0xFF4285F4), R.drawable.ic_google_docs),
    SHEETS("Google Sheets", Color(0xFF0F9D58), R.drawable.ic_google_sheets),
    SLIDES("Google Slides", Color(0xFFF4B400), R.drawable.ic_google_slides),
    FOLDER("Dossier", Color(0xFF8AB4F8), null),
    PDF("Document PDF", Color(0xFFEA4335), null),
    IMAGE("Image", Color(0xFF34A853), null),
    OTHER("Fichier", Color(0xFF9AA0A6), null);

    companion object {
        fun fromMimeType(mimeType: String, fileName: String = ""): GoogleWorkspaceType {
            return when {
                mimeType == "application/vnd.google-apps.folder" -> FOLDER
                mimeType == "application/vnd.google-apps.document" || fileName.endsWith(".docx", true) || fileName.endsWith(".doc", true) -> DOCS
                mimeType == "application/vnd.google-apps.spreadsheet" || fileName.endsWith(".xlsx", true) || fileName.endsWith(".csv", true) -> SHEETS
                mimeType == "application/vnd.google-apps.presentation" || fileName.endsWith(".pptx", true) -> SLIDES
                mimeType == "application/pdf" || fileName.endsWith(".pdf", true) -> PDF
                mimeType.startsWith("image/") -> IMAGE
                else -> OTHER
            }
        }
    }
}

data class GoogleDriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val modifiedTime: Long = System.currentTimeMillis(),
    val size: Long = 0L,
    val webViewLink: String? = null,
    val thumbnailLink: String? = null,
    val parents: List<String> = emptyList(),
) {
    val isFolder: Boolean
        get() = mimeType == "application/vnd.google-apps.folder"

    val workspaceType: GoogleWorkspaceType
        get() = GoogleWorkspaceType.fromMimeType(mimeType, name)

    val formattedSize: String
        get() {
            if (isFolder) return "Dossier"
            if (size <= 0L) {
                return when (workspaceType) {
                    GoogleWorkspaceType.DOCS -> "Google Doc"
                    GoogleWorkspaceType.SHEETS -> "Google Sheet"
                    GoogleWorkspaceType.SLIDES -> "Google Slide"
                    else -> "—"
                }
            }
            val kb = size / 1024.0
            if (kb < 1024.0) return "%.1f Ko".format(Locale.ROOT, kb)
            val mb = kb / 1024.0
            if (mb < 1024.0) return "%.1f Mo".format(Locale.ROOT, mb)
            val gb = mb / 1024.0
            return "%.2f Go".format(Locale.ROOT, gb)
        }

    val formattedDate: String
        get() {
            val now = System.currentTimeMillis()
            val diff = now - modifiedTime
            return when {
                diff < 60_000L -> "À l'instant"
                diff < 3600_000L -> "Il y a ${(diff / 60_000L)} min"
                diff < 86400_000L -> "Il y a ${(diff / 3600_000L)} h"
                diff < 86400_000L * 2 -> "Hier"
                else -> {
                    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.FRENCH)
                    sdf.format(Date(modifiedTime))
                }
            }
        }
}

data class GoogleDriveAccountInfo(
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val storageLimit: Long = 15L * 1024 * 1024 * 1024,
    val storageUsage: Long = 4_800_000_000L,
) {
    val formattedUsage: String
        get() {
            val usedGb = storageUsage.toDouble() / (1024 * 1024 * 1024)
            val totalGb = storageLimit.toDouble() / (1024 * 1024 * 1024)
            return "%.1f Go / %.0f Go".format(Locale.ROOT, usedGb, totalGb)
        }

    val usageFraction: Float
        get() = if (storageLimit > 0) (storageUsage.toFloat() / storageLimit.toFloat()).coerceIn(0f, 1f) else 0f
}

sealed class DriveAuthState {
    object Disconnected : DriveAuthState()
    object Connecting : DriveAuthState()
    data class Connected(val account: GoogleDriveAccountInfo) : DriveAuthState()
    data class Error(val message: String) : DriveAuthState()
}
