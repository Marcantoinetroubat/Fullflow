package com.newoether.agora.fulllive.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.newoether.agora.fulllive.audio.AudioEngine
import com.newoether.agora.fulllive.data.api.AIResponse
import com.newoether.agora.fulllive.data.api.AiClientCoordinator
import com.newoether.agora.fulllive.data.api.ToolsServices
import com.newoether.agora.fulllive.data.local.FullLiveDatabase
import com.newoether.agora.fulllive.data.local.SecurityVault
import com.newoether.agora.fulllive.data.local.entity.ConversationSessionEntity
import com.newoether.agora.fulllive.data.local.entity.MessageEntity
import com.newoether.agora.fulllive.data.local.entity.PersonaEntity
import com.newoether.agora.fulllive.data.local.entity.StatEntryEntity
import com.newoether.agora.fulllive.data.repository.FullLiveRepository
import com.newoether.agora.fulllive.ui.components.SupportedLanguage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

sealed interface CurrentScreen {
    object Home : CurrentScreen
    data class Conversation(val personaId: String) : CurrentScreen
    object Transcripteur : CurrentScreen
    object Traducteur : CurrentScreen
    data class HistoryDetail(val sessionId: String) : CurrentScreen
}

data class LiveUiState(
    val isConnected: Boolean = false,
    val isSpeaking: Boolean = false,
    val isListening: Boolean = false,
    val isMuted: Boolean = false,
    val isPaused: Boolean = false,
    val statusText: String = "Système en attente",
    val sessionTimerSeconds: Int = 0,
    val currentCostDollars: Double = 0.0,
    val userWaveAmplitude: Float = 0f,
    val orbVolume: Float = 0f,
    val orbBass: Float = 0f,
    val orbMid: Float = 0f,
    val orbHigh: Float = 0f,
    val liveAiText: String = "",
    val liveUserText: String = "",
    val activeSessionId: String? = null,
    val capturedImage: Bitmap? = null
)

class FullLiveViewModel(application: Application) : AndroidViewModel(application) {

    private val database = FullLiveDatabase.getDatabase(application)
    val securityVault = SecurityVault(application)
    private val repository = FullLiveRepository(database, securityVault)
    private val toolsServices = ToolsServices()
    val aiCoordinator = AiClientCoordinator(securityVault, toolsServices)
    private val audioEngine = AudioEngine(application)

    // --- Bridge to FullFlow's Second Brain ---
    // Set by FullLiveHost; provides active memory + note catalog from FullFlow
    var brainContextProvider: (suspend () -> String)? = null
    // Set by FullLiveHost; saves an insight as a note in FullFlow's vault
    var saveInsightToBrain: (suspend (String) -> Unit)? = null

    val personas: StateFlow<List<PersonaEntity>> = repository.personas
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessions: StateFlow<List<ConversationSessionEntity>> = repository.sessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stats: StateFlow<List<StatEntryEntity>> = repository.stats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentScreen = MutableStateFlow<CurrentScreen>(CurrentScreen.Home)
    val currentScreen: StateFlow<CurrentScreen> = _currentScreen.asStateFlow()

    private val _liveUiState = MutableStateFlow(LiveUiState())
    val liveUiState: StateFlow<LiveUiState> = _liveUiState.asStateFlow()

    private val _sessionMessages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val sessionMessages: StateFlow<List<MessageEntity>> = _sessionMessages.asStateFlow()

    private val _detectedInsights = MutableStateFlow<List<String>>(emptyList())
    val detectedInsights: StateFlow<List<String>> = _detectedInsights.asStateFlow()

    // Active session tracking
    private var activePersona: PersonaEntity? = null
    private var timerJob: Job? = null
    private var currentSessionStartTime = 0L
    private var currentSessionTokensIn = 0L
    private var currentSessionTokensOut = 0L

    init {
        viewModelScope.launch {
            repository.checkAndSeedDefaults()
        }

        // Hook up AudioEngine callbacks
        audioEngine.onUserAudioLevel = { level ->
            _liveUiState.value = _liveUiState.value.copy(userWaveAmplitude = level)
        }

        audioEngine.onAiAudioLevel = { vol, bass, mid, high ->
            _liveUiState.value = _liveUiState.value.copy(
                orbVolume = vol,
                orbBass = bass,
                orbMid = mid,
                orbHigh = high,
                isSpeaking = vol > 0.05f
            )
        }

        audioEngine.onUserStartedSpeaking = {
            // Full-Duplex Interruption: Stop AI speech if user starts talking
            if (_liveUiState.value.isSpeaking) {
                audioEngine.stopPlayback()
                _liveUiState.value = _liveUiState.value.copy(
                    isSpeaking = false,
                    statusText = "Vous parlez..."
                )
            }
        }
    }

    fun navigateTo(screen: CurrentScreen) {
        if (_liveUiState.value.isConnected) {
            stopSession(triggerInsights = false)
        }
        _currentScreen.value = screen
        if (screen is CurrentScreen.HistoryDetail) {
            loadHistoryMessages(screen.sessionId)
        }
    }

    fun navigateHome() {
        navigateTo(CurrentScreen.Home)
    }

    // --- Persona Operations ---
    fun savePersona(persona: PersonaEntity) {
        viewModelScope.launch {
            repository.savePersona(persona)
        }
    }

    fun deletePersona(id: String) {
        viewModelScope.launch {
            repository.deletePersona(id)
        }
    }

    fun duplicatePersona(persona: PersonaEntity) {
        viewModelScope.launch {
            val copy = persona.copy(
                id = "p_" + UUID.randomUUID().toString().take(8),
                name = "${persona.name} (copie)",
                isSystem = false
            )
            repository.savePersona(copy)
        }
    }

    // --- Live Conversation Management ---
    fun startConversation(persona: PersonaEntity) {
        activePersona = persona
        _currentScreen.value = CurrentScreen.Conversation(persona.id)
        val sessionId = "conv_" + UUID.randomUUID().toString().take(8)
        currentSessionStartTime = System.currentTimeMillis()
        currentSessionTokensIn = 0L
        currentSessionTokensOut = 0L
        _sessionMessages.value = emptyList()

        _liveUiState.value = LiveUiState(
            isConnected = true,
            statusText = "Prêt, vous pouvez parler",
            activeSessionId = sessionId
        )

        audioEngine.startRecording(scope = viewModelScope)
        startSessionTimer()

        // Greeting mode
        if (persona.greeting == "persona") {
            viewModelScope.launch {
                delay(400)
                sendAiTurn("Bonjour ! Salue-moi brièvement dans ton style et présente ce que nous pouvons faire ensemble aujourd'hui.")
            }
        }
    }

    fun sendUserSpeech(userSpeechText: String) {
        if (userSpeechText.isBlank() || !_liveUiState.value.isConnected) return
        val sessionId = _liveUiState.value.activeSessionId ?: return
        val persona = activePersona ?: return

        val userMessage = MessageEntity(
            sessionId = sessionId,
            sender = "user",
            text = userSpeechText.trim(),
            imageUri = null
        )
        _sessionMessages.value = _sessionMessages.value + userMessage
        _liveUiState.value = _liveUiState.value.copy(
            liveUserText = userSpeechText.trim(),
            statusText = "${persona.name} réfléchit..."
        )

        viewModelScope.launch {
            repository.saveMessage(userMessage)
            sendAiTurn(userSpeechText)
        }
    }

    private suspend fun sendAiTurn(userText: String) {
        val persona = activePersona ?: return
        val sessionId = _liveUiState.value.activeSessionId ?: return
        val history = _sessionMessages.value.map { it.sender to it.text }

        val systemPrompt = buildString {
            append(persona.prompt)
            append("\n\n# Outils natifs disponibles :\n- Date et heure avec get_current_datetime\n- Météo mondiale avec get_weather\n- Recherche web avec web_search\n")
            val userNotes = securityVault.getUserIdentityNotes()
            if (userNotes.isNotBlank()) {
                append("\n# Informations sur l'utilisateur :\n$userNotes\n")
            }
            if (persona.personaInfo.isNotBlank()) {
                append("\n# Informations spécifiques :\n${persona.personaInfo}\n")
            }
            // --- Second Cerveau FullFlow ---
            val brainContext = brainContextProvider?.invoke()
            if (!brainContext.isNullOrBlank()) {
                append("\n# Second Cerveau — Mémoire active et notes de l'utilisateur :\n")
                append(brainContext)
                append("\n")
            }
        }

        val response = aiCoordinator.executeConversationTurn(
            model = persona.model,
            systemInstruction = systemPrompt,
            conversationHistory = history,
            userMessage = userText,
            imageBitmap = _liveUiState.value.capturedImage
        )

        currentSessionTokensIn += response.inputTokens
        currentSessionTokensOut += response.outputTokens
        val newCost = (currentSessionTokensIn * 0.000003) + (currentSessionTokensOut * 0.000012)

        val aiMessage = MessageEntity(
            sessionId = sessionId,
            sender = "ai",
            text = response.text,
            imageUri = null
        )
        _sessionMessages.value = _sessionMessages.value + aiMessage
        repository.saveMessage(aiMessage)

        _liveUiState.value = _liveUiState.value.copy(
            statusText = "${persona.name} parle...",
            liveAiText = response.text,
            currentCostDollars = newCost,
            capturedImage = null
        )

        audioEngine.speakText(response.text, voiceName = persona.voice) {
            _liveUiState.value = _liveUiState.value.copy(
                statusText = "Connecté, parlez !",
                isSpeaking = false
            )
        }
    }

    fun attachCameraVisionImage(bitmap: Bitmap) {
        _liveUiState.value = _liveUiState.value.copy(
            capturedImage = bitmap,
            statusText = "Image capturée pour l'IA"
        )
    }

    fun toggleMute() {
        val newMuted = !_liveUiState.value.isMuted
        _liveUiState.value = _liveUiState.value.copy(
            isMuted = newMuted,
            statusText = if (newMuted) "Micro coupé" else "Micro actif"
        )
    }

    fun stopSession(triggerInsights: Boolean = true) {
        audioEngine.stopRecording()
        audioEngine.stopPlayback()
        stopSessionTimer()

        val sessionId = _liveUiState.value.activeSessionId
        val persona = activePersona

        if (sessionId != null && persona != null && _sessionMessages.value.isNotEmpty()) {
            val duration = _liveUiState.value.sessionTimerSeconds
            val cost = _liveUiState.value.currentCostDollars

            viewModelScope.launch {
                val fullTranscript = _sessionMessages.value.joinToString("\n") { "${it.sender}: ${it.text}" }
                val title = aiCoordinator.generateSessionTitle(fullTranscript)

                val sessionEntity = ConversationSessionEntity(
                    id = sessionId,
                    personaId = persona.id,
                    personaName = persona.name,
                    personaImage = persona.imageUri,
                    mode = "conversation",
                    model = persona.model.ifBlank { securityVault.getDefaultModel() },
                    costDollars = cost,
                    startDate = currentSessionStartTime,
                    endDate = System.currentTimeMillis(),
                    durationSeconds = duration,
                    inputTokens = currentSessionTokensIn,
                    outputTokens = currentSessionTokensOut,
                    summary = title
                )
                repository.saveSession(sessionEntity)

                repository.addStat(
                    StatEntryEntity(
                        personaId = persona.id,
                        personaName = persona.name,
                        model = sessionEntity.model,
                        date = System.currentTimeMillis(),
                        inputTokens = currentSessionTokensIn,
                        outputTokens = currentSessionTokensOut,
                        durationSeconds = duration,
                        costDollars = cost
                    )
                )

                if (triggerInsights) {
                    val existing = securityVault.getUserIdentityNotes()
                    val insights = aiCoordinator.extractUserInsights(fullTranscript, existing)
                    if (insights.isNotEmpty()) {
                        _detectedInsights.value = insights
                    }
                }
            }
        }

        _liveUiState.value = _liveUiState.value.copy(
            isConnected = false,
            isSpeaking = false,
            statusText = "Session terminée"
        )
    }

    // --- Mode Transcripteur Continu ---
    fun startTranscripteur() {
        _currentScreen.value = CurrentScreen.Transcripteur
        val sessionId = "trans_" + UUID.randomUUID().toString().take(8)
        currentSessionStartTime = System.currentTimeMillis()
        _sessionMessages.value = emptyList()

        _liveUiState.value = LiveUiState(
            isConnected = true,
            statusText = "Transcription en cours, parlez...",
            activeSessionId = sessionId
        )
        audioEngine.startRecording(scope = viewModelScope)
        startSessionTimer()
    }

    fun addTranscriptBlock(text: String) {
        val sessionId = _liveUiState.value.activeSessionId ?: return
        val msg = MessageEntity(
            sessionId = sessionId,
            sender = "transcript",
            text = text
        )
        _sessionMessages.value = _sessionMessages.value + msg
        viewModelScope.launch {
            repository.saveMessage(msg)
        }
    }

    fun toggleTranscripteurPause() {
        val paused = !_liveUiState.value.isPaused
        _liveUiState.value = _liveUiState.value.copy(
            isPaused = paused,
            statusText = if (paused) "Pause (reprise en appuyant sur lecture)" else "Transcription en cours..."
        )
        if (paused) {
            addTranscriptBlock("--- Pause ---")
        }
    }

    // --- Mode Traducteur (Interprète Live) ---
    fun startTraducteur(targetLang: SupportedLanguage) {
        _currentScreen.value = CurrentScreen.Traducteur
        val sessionId = "trad_" + UUID.randomUUID().toString().take(8)
        currentSessionStartTime = System.currentTimeMillis()
        _sessionMessages.value = emptyList()

        _liveUiState.value = LiveUiState(
            isConnected = true,
            statusText = "Interprète en direct (${targetLang.name}), parlez !",
            activeSessionId = sessionId
        )
        audioEngine.startRecording(scope = viewModelScope)
        startSessionTimer()
    }

    fun executeTranslationTurn(spokenText: String, targetLang: SupportedLanguage) {
        if (spokenText.isBlank()) return
        val sessionId = _liveUiState.value.activeSessionId ?: return

        val userMsg = MessageEntity(sessionId = sessionId, sender = "user", text = spokenText)
        _sessionMessages.value = _sessionMessages.value + userMsg

        viewModelScope.launch {
            repository.saveMessage(userMsg)
            _liveUiState.value = _liveUiState.value.copy(statusText = "Traduction en cours...")

            val systemInstruction = "Tu es un interprète en direct bidirectionnel d'excellence. Traduis fidèlement et immédiatement le propos dans la langue cible : ${targetLang.name} (${targetLang.code}). Réponds UNIQUEMENT par la traduction prononcée sans note ni commentaire."
            val response = aiCoordinator.executeConversationTurn(
                model = "gemini-3.5-flash",
                systemInstruction = systemInstruction,
                conversationHistory = emptyList(),
                userMessage = spokenText
            )

            val aiMsg = MessageEntity(sessionId = sessionId, sender = "ai", text = response.text)
            _sessionMessages.value = _sessionMessages.value + aiMsg
            repository.saveMessage(aiMsg)

            _liveUiState.value = _liveUiState.value.copy(
                statusText = "Traduction terminée",
                liveAiText = response.text
            )

            audioEngine.speakText(response.text)
        }
    }

    // --- History Replay ---
    private fun loadHistoryMessages(sessionId: String) {
        viewModelScope.launch {
            _sessionMessages.value = repository.getMessagesSync(sessionId)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_currentScreen.value is CurrentScreen.HistoryDetail) {
                navigateHome()
            }
        }
    }

    // --- Insights Handling ---
    fun saveSelectedInsights(insights: List<Pair<String, String>>) {
        viewModelScope.launch {
            val personaId = activePersona?.id
            insights.forEach { (fact, dest) ->
                if (dest == "global") {
                    val currentNotes = securityVault.getUserIdentityNotes()
                    val newNotes = if (currentNotes.isBlank()) "- $fact" else "$currentNotes\n- $fact"
                    securityVault.setUserIdentityNotes(newNotes)
                    repository.addInsight(fact, "global")
                    // Also save to FullFlow's Second Brain vault
                    saveInsightToBrain?.invoke(fact)
                } else {
                    repository.addInsight(fact, "persona", personaId)
                    saveInsightToBrain?.invoke(fact)
                }
            }
            _detectedInsights.value = emptyList()
        }
    }

    fun dismissInsights() {
        _detectedInsights.value = emptyList()
    }

    // --- Timer Routine ---
    private fun startSessionTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var elapsed = 0
            while (true) {
                delay(1000)
                if (!_liveUiState.value.isPaused) {
                    elapsed++
                    _liveUiState.value = _liveUiState.value.copy(sessionTimerSeconds = elapsed)
                }
            }
        }
    }

    private fun stopSessionTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
    }
}
