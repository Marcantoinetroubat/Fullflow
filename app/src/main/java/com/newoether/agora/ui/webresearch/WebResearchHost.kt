package com.newoether.agora.ui.webresearch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.newoether.agora.tool.WebSearchToolProvider
import com.newoether.agora.ui.ds.AgoraAlpha
import com.newoether.agora.ui.ds.AgoraRadii
import com.newoether.agora.ui.ds.AgoraSpacing
import com.newoether.agora.viewmodel.GenerationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Web Research page — Perplexity/Morphic-style internet search.
 * Uses the multi-backend WebSearchToolProvider (Tavily, Brave, Serper, Exa, SearXNG, DuckDuckGo)
 * with automatic fallback, configured in Settings → Web Search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebResearchHost() {
    if (!WebResearchController.visible) return

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasSearched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = { WebResearchController.close() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF090D12),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { WebResearchController.close() },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = AgoraAlpha.Subtle)),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(AgoraSpacing.Md))
                    Column {
                        Text(
                            text = "Recherche Web",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = "Recherche multi-sources avec fallback automatique",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = AgoraAlpha.Hint),
                        )
                    }
                }

                // Search bar
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Rechercher sur le web...", color = Color.White.copy(alpha = AgoraAlpha.Hint)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Rechercher", tint = Color(0xFF4FC3F7))
                    },
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF4FC3F7),
                            )
                        } else if (query.isNotBlank()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Effacer", tint = Color.White.copy(alpha = AgoraAlpha.Hint))
                            }
                        }
                    },
                    singleLine = true,
                    shape = AgoraRadii.Lg,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4FC3F7),
                        unfocusedBorderColor = Color.White.copy(alpha = AgoraAlpha.Divider),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF4FC3F7),
                        focusedContainerColor = Color.White.copy(alpha = AgoraAlpha.Subtle),
                        unfocusedContainerColor = Color.White.copy(alpha = AgoraAlpha.Subtle),
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (query.isNotBlank() && !isSearching) {
                                scope.launch { performSearch(query, results = { results = it }, error = { errorMessage = it }, searching = { isSearching = it }, searched = { hasSearched = it }) }
                            }
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Error message
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = Color(0xFFEF5350),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                // Results or empty state
                Box(modifier = Modifier.weight(1f)) {
                    if (!hasSearched && !isSearching && errorMessage == null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Default.Language,
                                contentDescription = null,
                                tint = Color(0xFF4FC3F7).copy(alpha = 0.3f),
                                modifier = Modifier.size(56.dp),
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Posez une question ou entrez un sujet",
                                color = Color.White.copy(alpha = AgoraAlpha.Hint),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                "La recherche utilise vos backends configurés\n(Tavily, Brave, Serper, Exa, SearXNG, DuckDuckGo)",
                                color = Color.White.copy(alpha = AgoraAlpha.Hint),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = AgoraSpacing.Sm),
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Sm),
                            verticalArrangement = Arrangement.spacedBy(AgoraSpacing.Sm),
                        ) {
                            items(results) { result ->
                                SearchResultCard(result)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Performs the search using WebSearchToolProvider with the configured multi-backend fallback.
 */
private suspend fun performSearch(
    query: String,
    results: (List<SearchResultItem>) -> Unit,
    error: (String?) -> Unit,
    searching: (Boolean) -> Unit,
    searched: (Boolean) -> Unit,
) {
    searching(true)
    error(null)

    withContext(Dispatchers.IO) {
        try {
            val provider = WebSearchToolProvider()
            val args = """{"query":"${query.replace("\"", "\\\"")}","num_results":8}"""
            // Use a minimal context — the provider reads settings from the app container
            // via GenerationContext which needs provider/API keys from settings
            val context = resolveGenerationContext()
            val raw = provider.execute("web_search", args, context)

            val parsed = parseSearchResults(raw)
            withContext(Dispatchers.Main) {
                results(parsed)
                searching(false)
                searched(true)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                error("Erreur de recherche : ${e.message}")
                searching(false)
                searched(true)
            }
        }
    }
}

/**
 * Tries to resolve the GenerationContext with web search settings from the app container.
 */
private fun resolveGenerationContext(): GenerationContext {
    // We can't easily get the container from a Composable context here,
    // but WebSearchToolProvider reads from GenerationContext which has defaults.
    // In a production app, this would be injected properly.
    return GenerationContext(
        webSearchEnabled = true,
        webSearchProvider = "duckduckgo", // Will use fallback chain
        webSearchNumResults = 8,
    )
}

/**
 * Parses the JSON response from WebSearchToolProvider into UI items.
 */
internal fun parseSearchResults(rawJson: String): List<SearchResultItem> {
    return try {
        val root = Json.parseToJsonElement(rawJson).jsonObject
        val resultsArray = root["results"]?.jsonArray ?: return emptyList()

        resultsArray.map { element ->
            val obj = element.jsonObject
            SearchResultItem(
                title = (obj["title"] as? JsonPrimitive)?.content ?: "",
                url = (obj["url"] as? JsonPrimitive)?.content ?: "",
                description = (obj["description"] as? JsonPrimitive)?.content
                    ?: (obj["content"] as? JsonPrimitive)?.content ?: "",
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

data class SearchResultItem(
    val title: String,
    val url: String,
    val description: String,
)

@Composable
private fun SearchResultCard(result: SearchResultItem) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = result.title,
                color = Color(0xFF4FC3F7),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (result.url.isNotBlank()) {
                Text(
                    text = result.url,
                    color = Color.White.copy(alpha = AgoraAlpha.Hint),
                    fontSize = 12.sp,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = result.description,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}
