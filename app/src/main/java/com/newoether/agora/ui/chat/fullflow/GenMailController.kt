package com.newoether.agora.ui.chat.fullflow

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide open/close state for the GenMail (Gmail assistant) dialog, so it can
 * be launched from anywhere — including the Settings workspace section — while the
 * dialog itself stays hosted in the chat layer. Same pattern as
 * [com.newoether.agora.workspace.drive.GoogleDriveWorkspaceController].
 */
object GenMailController {
    private val _showGenMail = MutableStateFlow(false)
    val showGenMail: StateFlow<Boolean> = _showGenMail.asStateFlow()

    fun openGenMail() {
        _showGenMail.value = true
    }

    fun closeGenMail() {
        _showGenMail.value = false
    }
}
