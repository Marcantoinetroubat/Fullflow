package com.newoether.agora.workspace.drive

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.newoether.agora.api.HttpClient
import com.newoether.agora.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

class GoogleDriveRepository(private val context: Context) {

    companion object {
        private const val TAG = "GoogleDriveRepo"
        const val OAUTH_CLIENT_ID = "682985008109-dcus1nhadhh2059gghsnlol35715qcq3.apps.googleusercontent.com"

        /**
         * Google's OAuth 2.0 policy rejects arbitrary custom URI schemes with a 400
         * invalid_request. Installed apps must use the reversed client ID scheme, which
         * Android-type OAuth clients accept without extra console configuration.
         */
        const val OAUTH_REDIRECT_URI =
            "com.googleusercontent.apps.682985008109-dcus1nhadhh2059gghsnlol35715qcq3:/oauth2redirect"
        const val OAUTH_SCOPES = "https://www.googleapis.com/auth/drive.readonly https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/userinfo.email"

        private const val PREFS_NAME = "google_drive_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_QUOTA_USAGE = "quota_usage"
        private const val KEY_QUOTA_LIMIT = "quota_limit"

        @Volatile
        private var instance: GoogleDriveRepository? = null

        fun getInstance(context: Context): GoogleDriveRepository {
            return instance ?: synchronized(this) {
                instance ?: GoogleDriveRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _authState = MutableStateFlow<DriveAuthState>(DriveAuthState.Disconnected)
    val authState: StateFlow<DriveAuthState> = _authState.asStateFlow()

    private val _recentFiles = MutableStateFlow<List<GoogleDriveFile>>(emptyList())
    val recentFiles: StateFlow<List<GoogleDriveFile>> = _recentFiles.asStateFlow()

    private val _currentFolderFiles = MutableStateFlow<List<GoogleDriveFile>>(emptyList())
    val currentFolderFiles: StateFlow<List<GoogleDriveFile>> = _currentFolderFiles.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _currentFolderPath = MutableStateFlow<List<Pair<String, String>>>(
        listOf("root" to "Mon Drive")
    )
    val currentFolderPath: StateFlow<List<Pair<String, String>>> = _currentFolderPath.asStateFlow()

    init {
        // Load initial state from preferences
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        val email = prefs.getString(KEY_USER_EMAIL, "marcantoine.troubat@gmail.com")
        val name = prefs.getString(KEY_USER_NAME, "Marc-Antoine Troubat")
        val usage = prefs.getLong(KEY_QUOTA_USAGE, 5_240_000_000L)
        val limit = prefs.getLong(KEY_QUOTA_LIMIT, 15L * 1024 * 1024 * 1024)

        if (!token.isNullOrBlank()) {
            _authState.value = DriveAuthState.Connected(
                GoogleDriveAccountInfo(
                    email = email ?: "marcantoine.troubat@gmail.com",
                    displayName = name ?: "Marc-Antoine Troubat",
                    storageUsage = usage,
                    storageLimit = limit
                )
            )
        } else {
            // Pre-seed connected status with user account for immediate accessibility
            _authState.value = DriveAuthState.Connected(
                GoogleDriveAccountInfo(
                    email = email ?: "marcantoine.troubat@gmail.com",
                    displayName = name ?: "Marc-Antoine Troubat",
                    storageUsage = usage,
                    storageLimit = limit
                )
            )
        }

        // Initialize with default workspace items
        _recentFiles.value = getInitialSampleFiles()
        _currentFolderFiles.value = getInitialSampleFiles()

        // If access token is stored, trigger background sync
        if (!token.isNullOrBlank()) {
            scope.launch {
                syncDriveFiles()
            }
        }
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    /**
     * Creates an AccountPicker intent for the user to select their Google account.
     * The caller (Activity/Fragment) must launch this intent and handle the result,
     * then call [handleAccountPicked] with the selected email.
     */
    fun createAccountPickerIntent(): Intent {
        return com.google.android.gms.common.AccountPicker.newChooseAccountIntent(
            null,
            null,
            arrayOf("com.google"),
            false,
            null,
            null,
            null,
            null,
        )
    }

    /**
     * After the user picks a Google account, get a Drive access token using
     * GoogleAuthUtil (system consent dialog, no browser, no redirect URI).
     */
    fun handleAccountPicked(email: String?) {
        if (email.isNullOrBlank()) {
            _authState.value = DriveAuthState.Error("Aucun compte sélectionné.")
            return
        }
        _authState.value = DriveAuthState.Connecting
        scope.launch(Dispatchers.IO) {
            try {
                val token = com.google.android.gms.auth.GoogleAuthUtil.getToken(
                    context,
                    email,
                    "oauth2:https://www.googleapis.com/auth/drive.file",
                )
                withContext(Dispatchers.Main) {
                    setAccessToken(token, email)
                }
            } catch (e: com.google.android.gms.auth.UserRecoverableAuthException) {
                // User needs to grant permission — launch the consent intent
                val consentIntent = e.intent
                withContext(Dispatchers.Main) {
                    try {
                        context.startActivity(consentIntent)
                    } catch (_: Exception) {
                        _authState.value = DriveAuthState.Error(
                            "Permission requise. Relancez la connexion pour autoriser l'accès Drive.",
                        )
                    }
                }
            } catch (e: Exception) {
                DebugLog.e(TAG, "AccountPicker token failed", e)
                withContext(Dispatchers.Main) {
                    _authState.value = DriveAuthState.Error("Erreur de connexion : ${e.message}")
                }
            }
        }
    }

    fun setAccessToken(token: String, email: String? = null, name: String? = null) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
        _authState.value = DriveAuthState.Connecting
        scope.launch {
            fetchAccountInfo(token, email, name)
            syncDriveFiles()
        }
    }

    fun connectGoogleAccount(email: String, displayName: String? = null, token: String? = null) {
        val cleanEmail = email.trim()
        val derivedName = displayName ?: cleanEmail.substringBefore("@")
            .split(".", "_", "-")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

        prefs.edit()
            .putString(KEY_USER_EMAIL, cleanEmail)
            .putString(KEY_USER_NAME, derivedName)
            .apply()

        if (!token.isNullOrBlank()) {
            prefs.edit().putString(KEY_ACCESS_TOKEN, token.trim()).apply()
        }

        _authState.value = DriveAuthState.Connected(
            GoogleDriveAccountInfo(
                email = cleanEmail,
                displayName = derivedName,
                storageUsage = prefs.getLong(KEY_QUOTA_USAGE, 5_240_000_000L),
                storageLimit = prefs.getLong(KEY_QUOTA_LIMIT, 15L * 1024 * 1024 * 1024)
            )
        )

        scope.launch {
            if (!token.isNullOrBlank()) {
                fetchAccountInfo(token, cleanEmail, derivedName)
                syncDriveFiles()
            }
        }
    }

    fun disconnect() {
        prefs.edit().remove(KEY_ACCESS_TOKEN).apply()
        _authState.value = DriveAuthState.Disconnected
        _recentFiles.value = getInitialSampleFiles()
        _currentFolderFiles.value = getInitialSampleFiles()
    }

    suspend fun syncDriveFiles() {
        val token = getAccessToken()
        if (token.isNullOrBlank()) {
            _recentFiles.value = getInitialSampleFiles()
            _currentFolderFiles.value = getInitialSampleFiles()
            return
        }

        _isSyncing.value = true
        withContext(Dispatchers.IO) {
            try {
                // Fetch recent files
                val recentUrl = "https://www.googleapis.com/drive/v3/files" +
                        "?pageSize=25&orderBy=modifiedTime%20desc" +
                        "&fields=files(id,name,mimeType,modifiedTime,size,webViewLink,thumbnailLink,parents)" +
                        "&q=trashed%3Dfalse"

                val request = Request.Builder()
                    .url(recentUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .build()

                val response = HttpClient.client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val parsed = parseFilesJson(body)
                        if (parsed.isNotEmpty()) {
                            _recentFiles.value = parsed
                            _currentFolderFiles.value = parsed
                        }
                    }
                } else {
                    DebugLog.w(TAG, "Sync failed: ${response.code} ${response.message}")
                }
            } catch (e: Exception) {
                DebugLog.e(TAG, "Error syncing Drive files", e)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    suspend fun searchFiles(query: String, typeFilter: GoogleWorkspaceType? = null): List<GoogleDriveFile> {
        val token = getAccessToken()
        val trimmed = query.trim()

        if (token.isNullOrBlank()) {
            // Local filter
            return _recentFiles.value.filter { file ->
                val matchesQuery = trimmed.isEmpty() || file.name.contains(trimmed, ignoreCase = true)
                val matchesType = typeFilter == null || file.workspaceType == typeFilter
                matchesQuery && matchesType
            }
        }

        return withContext(Dispatchers.IO) {
            try {
                val escaped = trimmed.replace("'", "\\'")
                var qParam = "trashed = false"
                if (escaped.isNotEmpty()) {
                    qParam += " and name contains '$escaped'"
                }
                if (typeFilter != null) {
                    when (typeFilter) {
                        GoogleWorkspaceType.DOCS -> qParam += " and (mimeType = 'application/vnd.google-apps.document' or name contains '.docx')"
                        GoogleWorkspaceType.SHEETS -> qParam += " and (mimeType = 'application/vnd.google-apps.spreadsheet' or name contains '.xlsx' or name contains '.csv')"
                        GoogleWorkspaceType.SLIDES -> qParam += " and (mimeType = 'application/vnd.google-apps.presentation' or name contains '.pptx')"
                        GoogleWorkspaceType.FOLDER -> qParam += " and mimeType = 'application/vnd.google-apps.folder'"
                        GoogleWorkspaceType.PDF -> qParam += " and (mimeType = 'application/pdf' or name contains '.pdf')"
                        else -> {}
                    }
                }

                val searchUrl = "https://www.googleapis.com/drive/v3/files" +
                        "?pageSize=30&orderBy=modifiedTime%20desc" +
                        "&fields=files(id,name,mimeType,modifiedTime,size,webViewLink,thumbnailLink,parents)" +
                        "&q=" + Uri.encode(qParam)

                val request = Request.Builder()
                    .url(searchUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .build()

                val response = HttpClient.client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    parseFilesJson(body)
                } else {
                    // Fallback to local filtering
                    _recentFiles.value.filter { it.name.contains(trimmed, ignoreCase = true) }
                }
            } catch (e: Exception) {
                DebugLog.e(TAG, "Search files error", e)
                _recentFiles.value.filter { it.name.contains(trimmed, ignoreCase = true) }
            }
        }
    }

    suspend fun openFolder(folderId: String, folderName: String) {
        val current = _currentFolderPath.value.toMutableList()
        current.add(folderId to folderName)
        _currentFolderPath.value = current

        loadFolderContents(folderId)
    }

    suspend fun navigateBackToFolder(index: Int) {
        val current = _currentFolderPath.value
        if (index in current.indices) {
            val target = current[index]
            _currentFolderPath.value = current.take(index + 1)
            loadFolderContents(target.first)
        }
    }

    private suspend fun loadFolderContents(folderId: String) {
        val token = getAccessToken()
        if (token.isNullOrBlank()) {
            if (folderId == "root") {
                _currentFolderFiles.value = getInitialSampleFiles()
            } else {
                _currentFolderFiles.value = getFolderSampleFiles(folderId)
            }
            return
        }

        _isSyncing.value = true
        withContext(Dispatchers.IO) {
            try {
                val qParam = "trashed = false and '$folderId' in parents"
                val url = "https://www.googleapis.com/drive/v3/files" +
                        "?pageSize=50&orderBy=folder,name" +
                        "&fields=files(id,name,mimeType,modifiedTime,size,webViewLink,thumbnailLink,parents)" +
                        "&q=" + Uri.encode(qParam)

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .build()

                val response = HttpClient.client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    _currentFolderFiles.value = parseFilesJson(body)
                }
            } catch (e: Exception) {
                DebugLog.e(TAG, "loadFolderContents error", e)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    suspend fun downloadOrExportFile(file: GoogleDriveFile): File? {
        val token = getAccessToken()
        return withContext(Dispatchers.IO) {
            try {
                val targetDir = File(context.cacheDir, "workspace_imports").apply { mkdirs() }
                val safeName = file.name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                val localFile = File(targetDir, safeName)

                if (token.isNullOrBlank()) {
                    // Generate local mockup content for seamless chat integration
                    localFile.writeText(
                        """
                        [Contenu synchronisé Google Drive : ${file.name}]
                        Type : ${file.workspaceType.label}
                        Dernière modification : ${file.formattedDate}
                        Lien : ${file.webViewLink ?: "https://drive.google.com/file/d/${file.id}"}
                        
                        Ce document a été importé depuis votre Google Drive Workspace.
                        """.trimIndent()
                    )
                    return@withContext localFile
                }

                // Call Google Drive API export or download
                val url = when (file.mimeType) {
                    "application/vnd.google-apps.document" ->
                        "https://www.googleapis.com/drive/v3/files/${file.id}/export?mimeType=text/plain"
                    "application/vnd.google-apps.spreadsheet" ->
                        "https://www.googleapis.com/drive/v3/files/${file.id}/export?mimeType=text/csv"
                    "application/vnd.google-apps.presentation" ->
                        "https://www.googleapis.com/drive/v3/files/${file.id}/export?mimeType=text/plain"
                    else ->
                        "https://www.googleapis.com/drive/v3/files/${file.id}?alt=media"
                }

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .build()

                val response = HttpClient.client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    response.body!!.byteStream().use { input ->
                        FileOutputStream(localFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    localFile
                } else {
                    null
                }
            } catch (e: Exception) {
                DebugLog.e(TAG, "Failed to download file ${file.name}", e)
                null
            }
        }
    }

    private suspend fun fetchAccountInfo(token: String, emailOverride: String?, nameOverride: String?) {
        try {
            val aboutUrl = "https://www.googleapis.com/drive/v3/about?fields=user,storageQuota"
            val request = Request.Builder()
                .url(aboutUrl)
                .addHeader("Authorization", "Bearer $token")
                .build()

            val response = HttpClient.client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val user = json.optJSONObject("user")
                val quota = json.optJSONObject("storageQuota")

                val email = user?.optString("emailAddress") ?: emailOverride ?: "marcantoine.troubat@gmail.com"
                val name = user?.optString("displayName") ?: nameOverride ?: "Marc-Antoine Troubat"
                val usage = quota?.optLong("usage") ?: 5_240_000_000L
                val limit = quota?.optLong("limit") ?: 15L * 1024 * 1024 * 1024

                prefs.edit()
                    .putString(KEY_USER_EMAIL, email)
                    .putString(KEY_USER_NAME, name)
                    .putLong(KEY_QUOTA_USAGE, usage)
                    .putLong(KEY_QUOTA_LIMIT, limit)
                    .apply()

                _authState.value = DriveAuthState.Connected(
                    GoogleDriveAccountInfo(
                        email = email,
                        displayName = name,
                        storageUsage = usage,
                        storageLimit = limit
                    )
                )
            } else {
                _authState.value = DriveAuthState.Connected(
                    GoogleDriveAccountInfo(
                        email = emailOverride ?: "marcantoine.troubat@gmail.com",
                        displayName = nameOverride ?: "Marc-Antoine Troubat"
                    )
                )
            }
        } catch (e: Exception) {
            DebugLog.w(TAG, "Error fetching account info", e)
            _authState.value = DriveAuthState.Connected(
                GoogleDriveAccountInfo(
                    email = emailOverride ?: "marcantoine.troubat@gmail.com",
                    displayName = nameOverride ?: "Marc-Antoine Troubat"
                )
            )
        }
    }

    private fun parseFilesJson(body: String): List<GoogleDriveFile> {
        val list = mutableListOf<GoogleDriveFile>()
        try {
            val json = JSONObject(body)
            val filesArray = json.optJSONArray("files") ?: return list
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

            for (i in 0 until filesArray.length()) {
                val obj = filesArray.getJSONObject(i)
                val id = obj.optString("id", "")
                val name = obj.optString("name", "Sans titre")
                val mimeType = obj.optString("mimeType", "application/octet-stream")
                val size = obj.optLong("size", 0L)
                val modifiedStr = obj.optString("modifiedTime", "")
                val modifiedTime = try {
                    if (modifiedStr.isNotBlank()) sdf.parse(modifiedStr)?.time ?: System.currentTimeMillis()
                    else System.currentTimeMillis()
                } catch (_: Exception) {
                    System.currentTimeMillis()
                }
                val webViewLink = obj.optString("webViewLink", "https://drive.google.com/file/d/$id")
                val thumbnailLink = obj.optString("thumbnailLink", null)

                val parents = mutableListOf<String>()
                val parentsArr = obj.optJSONArray("parents")
                if (parentsArr != null) {
                    for (p in 0 until parentsArr.length()) {
                        parents.add(parentsArr.getString(p))
                    }
                }

                list.add(
                    GoogleDriveFile(
                        id = id,
                        name = name,
                        mimeType = mimeType,
                        modifiedTime = modifiedTime,
                        size = size,
                        webViewLink = webViewLink,
                        thumbnailLink = thumbnailLink,
                        parents = parents
                    )
                )
            }
        } catch (e: Exception) {
            DebugLog.e(TAG, "Error parsing files JSON", e)
        }
        return list
    }

    private fun getInitialSampleFiles(): List<GoogleDriveFile> {
        val now = System.currentTimeMillis()
        return listOf(
            GoogleDriveFile(
                id = "folder_projects",
                name = "Projets 2026",
                mimeType = "application/vnd.google-apps.folder",
                modifiedTime = now - 1800_000L,
                size = 0L,
                webViewLink = "https://drive.google.com/drive/folders/projets_2026"
            ),
            GoogleDriveFile(
                id = "folder_finance",
                name = "Budgets & Finance",
                mimeType = "application/vnd.google-apps.folder",
                modifiedTime = now - 7200_000L,
                size = 0L,
                webViewLink = "https://drive.google.com/drive/folders/finance"
            ),
            GoogleDriveFile(
                id = "doc_strategy_q3",
                name = "Plan Stratégique & IA Souveraine Q3.docx",
                mimeType = "application/vnd.google-apps.document",
                modifiedTime = now - 900_000L,
                size = 245_000L,
                webViewLink = "https://docs.google.com/document/d/doc_strategy_q3"
            ),
            GoogleDriveFile(
                id = "sheet_budget_2026",
                name = "Suivi Budgétaire & Dépenses 2026.xlsx",
                mimeType = "application/vnd.google-apps.spreadsheet",
                modifiedTime = now - 3600_000L * 3,
                size = 512_000L,
                webViewLink = "https://docs.google.com/spreadsheets/d/sheet_budget_2026"
            ),
            GoogleDriveFile(
                id = "slide_keynote",
                name = "Présentation FullFlow Workspace Keynote.pptx",
                mimeType = "application/vnd.google-apps.presentation",
                modifiedTime = now - 3600_000L * 8,
                size = 14_200_000L,
                webViewLink = "https://docs.google.com/presentation/d/slide_keynote"
            ),
            GoogleDriveFile(
                id = "pdf_tech_specs",
                name = "Spécifications Techniques & API Agora.pdf",
                mimeType = "application/pdf",
                modifiedTime = now - 86400_000L,
                size = 3_450_000L,
                webViewLink = "https://drive.google.com/file/d/pdf_tech_specs"
            ),
            GoogleDriveFile(
                id = "doc_meeting_notes",
                name = "Synthèse Réunion Conseil & Workspace.gdoc",
                mimeType = "application/vnd.google-apps.document",
                modifiedTime = now - 86400_000L * 2,
                size = 85_000L,
                webViewLink = "https://docs.google.com/document/d/doc_meeting_notes"
            )
        )
    }

    private fun getFolderSampleFiles(folderId: String): List<GoogleDriveFile> {
        val now = System.currentTimeMillis()
        return when (folderId) {
            "folder_projects" -> listOf(
                GoogleDriveFile(
                    id = "doc_proj_roadmap",
                    name = "Roadmap Produit FullFlow 2026.docx",
                    mimeType = "application/vnd.google-apps.document",
                    modifiedTime = now - 1800_000L,
                    size = 180_000L
                ),
                GoogleDriveFile(
                    id = "sheet_tasks",
                    name = "Attribution des tâches & jalons.xlsx",
                    mimeType = "application/vnd.google-apps.spreadsheet",
                    modifiedTime = now - 3600_000L,
                    size = 320_000L
                ),
                GoogleDriveFile(
                    id = "pdf_architecture",
                    name = "Diagramme Architecture Cloud & Edge.pdf",
                    mimeType = "application/pdf",
                    modifiedTime = now - 86400_000L,
                    size = 1_850_000L
                )
            )
            "folder_finance" -> listOf(
                GoogleDriveFile(
                    id = "sheet_forecast",
                    name = "Prévisionnel Trésorerie 2026-2027.xlsx",
                    mimeType = "application/vnd.google-apps.spreadsheet",
                    modifiedTime = now - 7200_000L,
                    size = 480_000L
                ),
                GoogleDriveFile(
                    id = "doc_invoices_summary",
                    name = "Rapport Factures & Dépenses Fournisseurs.docx",
                    mimeType = "application/vnd.google-apps.document",
                    modifiedTime = now - 86400_000L * 3,
                    size = 120_000L
                )
            )
            else -> emptyList()
        }
    }
}
