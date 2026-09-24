package com.newoether.agora.mesh

import android.content.Context
import com.newoether.agora.mesh.service.MeshCoordinatorService
import com.newoether.agora.mesh.service.PiMeshBackgroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Singleton controller providing Compose-friendly access to the Pi-Mesh (mesh.v1) broker.
 */
object MeshController {
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    val broker = MeshBroker()

    private val _isStudioOpen = MutableStateFlow(false)
    val isStudioOpen: StateFlow<Boolean> = _isStudioOpen.asStateFlow()

    val workingAgentsCount: StateFlow<Int> = broker.agents.map { list ->
        list.count { it.state == MeshAgentState.WORKING }
    }.stateIn(scope, SharingStarted.Eagerly, 0)

    val idleAgentsCount: StateFlow<Int> = broker.agents.map { list ->
        list.count { it.state == MeshAgentState.IDLE }
    }.stateIn(scope, SharingStarted.Eagerly, 5)

    val stuckAgentsCount: StateFlow<Int> = broker.agents.map { list ->
        list.count { it.state == MeshAgentState.STUCK }
    }.stateIn(scope, SharingStarted.Eagerly, 0)

    fun openStudio() {
        _isStudioOpen.value = true
    }

    fun closeStudio() {
        _isStudioOpen.value = false
    }

    fun launchDebate(topic: String) {
        broker.launchDebate(topic)
        _isStudioOpen.value = true
    }

    fun launchChainedDrafting(subject: String) {
        broker.launchChainedDrafting(subject)
        _isStudioOpen.value = true
    }

    fun launchParallelSynthesis(topic: String) {
        broker.launchParallelSynthesis(topic)
        _isStudioOpen.value = true
    }

    val messageBus = MessageBus.defaultBus

    fun startBackgroundService(context: Context, asForeground: Boolean = false) {
        PiMeshBackgroundService.startService(context, asForeground)
        MeshCoordinatorService.startService(context)
    }

    fun stopBackgroundService(context: Context) {
        PiMeshBackgroundService.stopService(context)
        MeshCoordinatorService.stopService(context)
    }
}
