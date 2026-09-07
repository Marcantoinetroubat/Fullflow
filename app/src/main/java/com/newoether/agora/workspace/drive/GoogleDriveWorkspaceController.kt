package com.newoether.agora.workspace.drive

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

object GoogleDriveWorkspaceController {
    private val _showDriveDashboard = MutableStateFlow(false)
    val showDriveDashboard = _showDriveDashboard.asStateFlow()

    private val _importedFileToChat = MutableSharedFlow<Pair<GoogleDriveFile, File?>>(extraBufferCapacity = 5)
    val importedFileToChat = _importedFileToChat.asSharedFlow()

    fun openDriveDashboard() {
        _showDriveDashboard.value = true
    }

    fun closeDriveDashboard() {
        _showDriveDashboard.value = false
    }

    suspend fun importFile(context: Context, file: GoogleDriveFile) {
        val repo = GoogleDriveRepository.getInstance(context)
        val downloaded = repo.downloadOrExportFile(file)
        _importedFileToChat.emit(file to downloaded)
    }

    fun handleRedirectUri(context: Context, uri: Uri): Boolean {
        val repo = GoogleDriveRepository.getInstance(context)
        return repo.handleOAuthRedirectUri(uri)
    }
}
