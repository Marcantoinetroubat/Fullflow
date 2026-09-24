package com.newoether.agora.data.notes

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.newoether.agora.AgoraApplication
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VaultImporter {

    /**
     * Imports a single markdown file into the Second Brain notes vault.
     */
    suspend fun importSingleNote(context: Context, uri: Uri, repo: NoteRepository): Boolean = withContext(Dispatchers.IO) {
        try {
            val cr = context.contentResolver
            var fileName = "Note_${System.currentTimeMillis()}.md"

            // Get display name
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIdx != -1) {
                    val name = cursor.getString(nameIdx)
                    if (name.endsWith(".md", ignoreCase = true)) {
                        fileName = name
                    }
                }
            }

            cr.openInputStream(uri)?.use { stream ->
                val content = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                repo.saveNote(fileName, content)
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Persists permissions and recursively imports all `.md` files from a selected folder (Obsidian vault).
     */
    suspend fun importObsidianVault(
        context: Context,
        folderUri: Uri,
        repo: NoteRepository,
        onProgress: (current: Int, total: Int, fileName: String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Persist URI permissions
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(folderUri, flags)

            val rootDoc = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext false
            val allMarkdownDocs = mutableListOf<DocumentFile>()
            findMarkdownFilesRecursively(rootDoc, allMarkdownDocs)

            val total = allMarkdownDocs.size
            if (total == 0) return@withContext true

            allMarkdownDocs.forEachIndexed { index, doc ->
                val docName = doc.name ?: "note_${System.currentTimeMillis()}.md"
                withContext(Dispatchers.Main) {
                    onProgress(index + 1, total, docName)
                }

                context.contentResolver.openInputStream(doc.uri)?.use { stream ->
                    val content = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    
                    // We maintain the file hierarchy relative to the root doc!
                    val relativePath = getRelativePath(rootDoc, doc)
                    repo.saveNote(relativePath, content)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun findMarkdownFilesRecursively(file: DocumentFile, result: MutableList<DocumentFile>) {
        if (file.isDirectory) {
            file.listFiles().forEach { child ->
                findMarkdownFilesRecursively(child, result)
            }
        } else if (file.name?.endsWith(".md", ignoreCase = true) == true) {
            result.add(file)
        }
    }

    private fun getRelativePath(root: DocumentFile, file: DocumentFile): String {
        val pathSegments = mutableListOf<String>()
        var current: DocumentFile? = file
        while (current != null && current.uri != root.uri) {
            pathSegments.add(0, current.name ?: "")
            // DocumentFile tree does not have parent reference, so we fallback to display name hierarchy or standard name.
            // Under normal tree URI, we parse the document ID segments for relative path.
            break
        }
        
        // If empty or root, fallback to filename
        if (pathSegments.isEmpty()) return file.name ?: "note.md"
        
        // Try to decode document ID for hierarchy
        val docId = file.uri.lastPathSegment ?: ""
        val rootId = root.uri.lastPathSegment ?: ""
        if (docId.startsWith(rootId) && docId.length > rootId.length) {
            val relative = docId.substring(rootId.length).trimStart('/', ':')
            if (relative.endsWith(".md", ignoreCase = true)) {
                return relative
            }
        }
        
        return file.name ?: "note.md"
    }
}
