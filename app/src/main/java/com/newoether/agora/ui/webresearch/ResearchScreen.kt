package com.newoether.agora.ui.webresearch

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newoether.agora.model.ChatMessage
import com.newoether.agora.ui.ds.AgoraAlpha
import com.newoether.agora.ui.ds.AgoraElevation
import com.newoether.agora.ui.ds.AgoraRadii
import com.newoether.agora.ui.ds.AgoraSpacing
import com.newoether.agora.ui.ds.FullLiveBridge
import com.newoether.agora.model.Participant
import com.newoether.agora.api.ProviderConfig
import com.newoether.agora.api.StreamEvent
import com.newoether.agora.tool.WebSearchToolProvider
import com.newoether.agora.viewmodel.ChatViewModel
import com.newoether.agora.viewmodel.GenerationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResearchScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }
    var synthesizedAnswer by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isSynthesizing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasSearched by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val suggestions = listOf(
        "Dernières avancées en Intelligence Artificielle 🤖",
        "Nouveautés de Kotlin 2.2 et Jetpack Compose 🚀",
        "Actualités technologiques du jour 📰",
        "Fonctionnement du Web3 et de la Blockchain 🌐"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0C0F14),
                        Color(0xFF080A0D),
                        Color(0xFF040507)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Screen Title / Brand Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AgoraSpacing.Xl, vertical = AgoraSpacing.Lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(AgoraSpacing.Xxl)
                )
                Spacer(modifier = Modifier.width(AgoraSpacing.Md))
                Column {
                    Text(
                        text = "Recherche Web",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Synthese multi-sources en temps réel",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            // Central Content Area
            Box(modifier = Modifier.weight(1f)) {
                if (!hasSearched && !isSearching) {
                    // Empty landing state with suggestion chips
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = AgoraSpacing.Xxl)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8).copy(alpha = 0.15f),
                            modifier = Modifier.size(82.dp)
                        )
                        Spacer(modifier = Modifier.height(AgoraSpacing.Lg))
                        Text(
                            text = "Posez n'importe quelle question",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "FullFlow interroge vos moteurs (Tavily, Serper, Exa, SearXNG) et rédige une synthèse intelligente.",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = AgoraSpacing.Xs, bottom = AgoraSpacing.Xxl)
                        )

                        // Suggestion cards
                        suggestions.forEach { suggestion ->
                            Surface(
                                shape = RoundedCornerShape(28.dp)__,
                                color = Color.White.copy(alpha = AgoraAlpha.Subtle),
                                border = BorderStroke(1.dp__, Color.White.copy(alpha = AgoraAlpha.Subtle)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                                    .clickable {
                                        query = suggestion
                                        focusManager.clearFocus()
                                        scope.launch {
                                            executeSearchAndSynthesis(
                                                query = suggestion,
                                                viewModel = viewModel,
                                                onSearching = { isSearching = it },
                                                onSynthesizing = { isSynthesizing = it },
                                                onResults = { searchResults = it },
                                                onAnswer = { synthesizedAnswer = it },
                                                onError = { errorMessage = it },
                                                onSearched = { hasSearched = it }
                                            )
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Lg),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8).copy(alpha = AgoraAlpha.Hint),
                                        modifier = Modifier.size(AgoraSpacing.Lg)
                                    )
                                    Spacer(modifier = Modifier.width(AgoraSpacing.Md))
                                    Text(
                                        text = suggestion,
                                        fontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Results View: Sources + Answer
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Md)
                    ) {
                        // Sources horizontal list
                        if (searchResults.isNotEmpty()) {
                            item {
                                Text(
                                    text = "SOURCES RELEVANTES",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8),
                                    modifier = Modifier.padding(bottom = AgoraSpacing.Sm, start = AgoraSpacing.Xs)
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Sm),
                                    modifier = Modifier.padding(bottom = AgoraSpacing.Lg)
                                ) {
                                    itemsIndexed(searchResults) { index, item ->
                                        SourceChipCard(index + 1, item)
                                    }
                                }
                            }
                        }

                        // Synthesized Answer Section
                        item {
                            Surface(
                                shape = RoundedCornerShape(28.dp)__,
                                color = Color.White.copy(alpha = AgoraAlpha.Subtle),
                                border = BorderStroke(1.dp__, Color.White.copy(alpha = AgoraAlpha.Subtle)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = AgoraSpacing.Lg)
                            ) {
                                Column(modifier = Modifier.padding(AgoraSpacing.Lg)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(bottom = AgoraSpacing.Md)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Psychology,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(AgoraSpacing.Xl)
                                        )
                                        Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                                        Text(
                                            text = "Synthèse intelligente",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        if (isSynthesizing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(AgoraSpacing.Lg),
                                                strokeWidth = 2.dp__,
                                                color = Color(0xFF38BDF8)
                                            )
                                        }
                                    }

                                    if (synthesizedAnswer.isBlank() && isSearching) {
                                        Text(
                                            text = "Recherche en cours sur le web...",
                                            fontSize = 13.sp,
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                    } else if (synthesizedAnswer.isBlank() && isSynthesizing) {
                                        Text(
                                            text = "Rédaction de la synthèse thématique...",
                                            fontSize = 13.sp,
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                    } else {
                                        Text(
                                            text = synthesizedAnswer,
                                            fontSize = 13.sp,
                                            color = Color.White.copy(alpha = 0.9f),
                                            lineHeight = 20.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Search result detail list (full fallback list)
                        if (searchResults.isNotEmpty()) {
                            item {
                                Text(
                                    text = "RÉSULTATS DÉTAILLÉS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = AgoraAlpha.Hint),
                                    modifier = Modifier.padding(start = AgoraSpacing.Xs, top = AgoraSpacing.Sm, bottom = AgoraSpacing.Sm)
                                )
                            }
                            items(searchResults) { result ->
                                DetailedResultCard(result)
                            }
                        }
                    }
                }
            }

            // Error display
            if (errorMessage != null) {
                Surface(
                    color = Color(0xFFEF5350).copy(alpha = AgoraAlpha.Pressed),
                    border = BorderStroke(AgoraElevation.CardTonal, Color(0xFFEF5350).copy(alpha = AgoraAlpha.Handle)),
                    shape = AgoraRadii.Sm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Xs)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = AgoraSpacing.Md, vertical = AgoraSpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Erreur",
                            tint = Color(0xFFEF5350),
                            modifier = Modifier.size(AgoraSpacing.Xl)
                        )
                        Spacer(modifier = Modifier.width(AgoraSpacing.Sm))
                        Text(
                            text = errorMessage!!,
                            color = Color(0xFFEF5350),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { errorMessage = null },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fermer le message d'erreur",
                                tint = Color(0xFFEF5350),
                                modifier = Modifier.size(AgoraSpacing.Xl)
                            )
                        }
                    }
                }
            }

            // Bottom Input Bar
            Surface(
                color = Color.White.copy(alpha = 0.02f),
                border = BorderStroke(1.dp__, Color.White.copy(alpha = AgoraAlpha.Subtle)),
                shape = RoundedCornerShape(28.dp)__,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Md)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = AgoraSpacing.Sm, vertical = AgoraSpacing.Xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Poser une question...", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF38BDF8)
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (query.isNotBlank() && !isSearching && !isSynthesizing) {
                                    focusManager.clearFocus()
                                    scope.launch {
                                        executeSearchAndSynthesis(
                                            query = query,
                                            viewModel = viewModel,
                                            onSearching = { isSearching = it },
                                            onSynthesizing = { isSynthesizing = it },
                                            onResults = { searchResults = it },
                                            onAnswer = { synthesizedAnswer = it },
                                            onError = { errorMessage = it },
                                            onSearched = { hasSearched = it }
                                        )
                                    }
                                }
                            }
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            if (query.isNotBlank() && !isSearching && !isSynthesizing) {
                                focusManager.clearFocus()
                                scope.launch {
                                    executeSearchAndSynthesis(
                                        query = query,
                                        viewModel = viewModel,
                                        onSearching = { isSearching = it },
                                        onSynthesizing = { isSynthesizing = it },
                                        onResults = { searchResults = it },
                                        onAnswer = { synthesizedAnswer = it },
                                        onError = { errorMessage = it },
                                        onSearched = { hasSearched = it }
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (query.isNotBlank()) FullLiveBridge.Cyan else Color.White.copy(alpha = AgoraAlpha.Subtle))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Rechercher",
                            tint = if (query.isNotBlank()) Color.Black else Color.White.copy(alpha = AgoraAlpha.Hint),
                            modifier = Modifier.size(AgoraSpacing.Xl)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceChipCard(index: Int, item: SearchResultItem) {
    Surface(
        shape = AgoraRadii.Sm,
        color = Color.White.copy(alpha = AgoraAlpha.Subtle),
        border = BorderStroke(AgoraElevation.CardTonal, Color.White.copy(alpha = AgoraAlpha.Subtle)),
        modifier = Modifier
            .width(130.dp)
            .height(54.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = AgoraSpacing.Sm, vertical = AgoraSpacing.Xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(AgoraSpacing.Xl)
                        .clip(CircleShape)
                        .background(FullLiveBridge.Cyan.copy(alpha = 0.15f))
                ) {
                    Text(
                        text = index.toString(),
                        color = FullLiveBridge.Cyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(AgoraSpacing.Xs))
                Text(
                    text = item.title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(AgoraSpacing.Xxs))
            Text(
                text = item.url,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = AgoraAlpha.Hint),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DetailedResultCard(result: SearchResultItem) {
    Surface(
        shape = AgoraRadii.Sm,
        color = Color.White.copy(alpha = AgoraAlpha.Subtle),
        border = BorderStroke(AgoraElevation.CardTonal, Color.White.copy(alpha = AgoraAlpha.Subtle)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AgoraSpacing.Xs)
    ) {
        Column(modifier = Modifier.padding(AgoraSpacing.Lg)) {
            Text(
                text = result.title,
                color = FullLiveBridge.Cyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (result.url.isNotBlank()) {
                Text(
                    text = result.url,
                    color = Color.White.copy(alpha = AgoraAlpha.Hint),
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(AgoraSpacing.Xs))
            Text(
                text = result.description,
                color = Color.White.copy(alpha = AgoraAlpha.Hint),
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

private suspend fun executeSearchAndSynthesis(
    query: String,
    viewModel: ChatViewModel,
    onSearching: (Boolean) -> Unit,
    onSynthesizing: (Boolean) -> Unit,
    onResults: (List<SearchResultItem>) -> Unit,
    onAnswer: (String) -> Unit,
    onError: (String?) -> Unit,
    onSearched: (Boolean) -> Unit
) {
    onSearching(true)
    onError(null)
    onAnswer("")
    onSearched(true)

    withContext(Dispatchers.IO) {
        try {
            // Step 1: Execute Web Search via WebSearchToolProvider
            val provider = WebSearchToolProvider()
            val args = """{"query":"${query.replace("\"", "\\\"")}","num_results":6}"""
            val context = GenerationContext(
                webSearchEnabled = true,
                webSearchProvider = "duckduckgo", // uses default chain configured in AppContainer settings
                webSearchNumResults = 6
            )
            val raw = provider.execute("web_search", args, context)
            val parsed = parseSearchResults(raw)

            if (parsed.isEmpty()) {
                withContext(Dispatchers.Main) {
                    onResults(emptyList())
                    onAnswer("Aucun résultat de recherche pertinent n'a pu être extrait. Veuillez reformuler votre question.")
                    onSearching(false)
                }
                return@withContext
            }

            withContext(Dispatchers.Main) {
                onResults(parsed)
                onSearching(false)
                onSynthesizing(true)
            }

            // Step 2: Synthesis with configured active model
            val modelId = viewModel.settings.selectedModel.value
            val providerName = viewModel.getProviderForModel(modelId)
            val llmProvider = viewModel.getProviderInstanceOrNull(providerName)

            if (llmProvider == null) {
                withContext(Dispatchers.Main) {
                    onAnswer("Résultats web chargés ! Renseignez votre clé API dans les Paramètres pour obtenir la synthèse rédigée par l'IA.")
                    onSynthesizing(false)
                }
                return@withContext
            }

            val apiKey = viewModel.settings.resolveActiveKey(providerName) ?: ""
            if (apiKey.isBlank()) {
                withContext(Dispatchers.Main) {
                    onAnswer("Résultats web chargés ! Renseignez votre clé d'API '$providerName' dans les Paramètres de l'application pour rédiger la synthèse.")
                    onSynthesizing(false)
                }
                return@withContext
            }

            val customUrls = viewModel.settings.providerBaseUrls.value
            val baseUrl = customUrls[providerName]
            val sourcesText = parsed.mapIndexed { idx, item ->
                "Source [${idx + 1}] (${item.title}): ${item.description}"
            }.joinToString("\n\n")

            val prompt = """
                Voici les résultats de recherche web récents pour la question : "$query".
                Rédige une réponse complète, claire, structurée et captivante à la question.
                Cite tes sources en utilisant le format [1], [2], etc. correspondant à chaque source listée.
                Sois fluide, naturel, sans introduction superflue. Rédige en Français de façon rigoureuse.

                # Sources :
                $sourcesText
            """.trimIndent()

            val messages = listOf(
                ChatMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    text = prompt,
                    participant = Participant.USER
                )
            )

            val providerConfig = ProviderConfig(
                apiKey = apiKey,
                modelId = modelId,
                baseUrl = baseUrl,
                systemPrompt = "Tu es un moteur de recherche intelligent style Perplexity. Réponds directement avec rigueur, précision et clarté.",
                thinkingEnabled = false
            )

            val answerBuilder = StringBuilder()
            llmProvider.generateResponse(messages, providerConfig).collect { event ->
                if (event is StreamEvent.TextChunk) {
                    answerBuilder.append(event.text)
                    withContext(Dispatchers.Main) {
                        onAnswer(answerBuilder.toString())
                    }
                } else if (event is StreamEvent.Error) {
                    withContext(Dispatchers.Main) {
                        onError("Erreur IA: ${event.error.userMessage()}")
                    }
                }
            }

            withContext(Dispatchers.Main) {
                onSynthesizing(false)
            }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError("Erreur : ${e.localizedMessage ?: "Inconnue"}")
                onSearching(false)
                onSynthesizing(false)
            }
        }
    }
}
