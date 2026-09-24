package com.newoether.agora.fulllive.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.newoether.agora.fulllive.data.local.SecurityVault
import com.newoether.agora.ui.ds.AgoraSpacing
import com.newoether.agora.fulllive.data.local.entity.StatEntryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigDialog(
    securityVault: SecurityVault,
    statsList: List<StatEntryEntity>,
    onDismiss: () -> Unit,
    onSaveConfig: (geminiKey: String, openAiKey: String, xaiKey: String, customOpenAiKey: String, customOpenAiBaseUrl: String, customOpenAiModelId: String, userNotes: String, budget: Float?, defaultModel: String, theme: String, layout: String) -> Unit,
    onExportBackupJson: () -> Unit,
    onImportBackupJson: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Mes infos, 1: Clés API, 2: Modèle, 3: Budget, 4: Affichage, 5: Stats, 6: Sauvegarde

    // State fields
    var userNotes by remember { mutableStateOf(securityVault.getUserIdentityNotes()) }
    var geminiKey by remember { mutableStateOf(securityVault.getGeminiKey()) }
    var openAiKey by remember { mutableStateOf(securityVault.getOpenAiKey()) }
    var xaiKey by remember { mutableStateOf(securityVault.getXaiKey()) }
    var customOpenAiKey by remember { mutableStateOf(securityVault.getCustomOpenAiKey()) }
    var customOpenAiBaseUrl by remember { mutableStateOf(securityVault.getCustomOpenAiBaseUrl()) }
    var customOpenAiModelId by remember { mutableStateOf(securityVault.getCustomOpenAiModelId()) }
    var budgetInput by remember { mutableStateOf(securityVault.getBudgetLimit()?.toString() ?: "") }
    var defaultModel by remember { mutableStateOf(securityVault.getDefaultModel()) }
    var themeState by remember { mutableStateOf(securityVault.getTheme()) }
    var layoutState by remember { mutableStateOf(securityVault.getConversationLayout()) }

    var modelMenuExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .height(600.dp)
                .padding(AgoraSpacing.Xs)
        ) {
            Column(modifier = Modifier.fillMaxHeight()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AgoraSpacing.Xl, vertical = AgoraSpacing.Lg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Configuration & Paramètres",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                // Scrollable or Multi Tab Row
                val tabs = listOf(
                    "Infos" to Icons.Default.Person,
                    "Clés API" to Icons.Default.Key,
                    "Modèle" to Icons.Default.Settings,
                    "Budget" to Icons.Default.Wallet,
                    "Affichage" to Icons.Default.Palette,
                    "Stats" to Icons.Default.BarChart
                )

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    tabs.forEachIndexed { index, (title, icon) ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, maxLines = 1, fontSize = 12.sp) },
                            icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(AgoraSpacing.Xl)) }
                        )
                    }
                }

                // Tab Content Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(AgoraSpacing.Lg)
                ) {
                    when (selectedTab) {
                        0 -> TabMyInfo(userNotes = userNotes, onNotesChanged = { userNotes = it })
                        1 -> TabApiKeys(
                            geminiKey = geminiKey, onGeminiChanged = { geminiKey = it },
                            openAiKey = openAiKey, onOpenAiChanged = { openAiKey = it },
                            xaiKey = xaiKey, onXaiChanged = { xaiKey = it },
                            customOpenAiKey = customOpenAiKey, onCustomOpenAiKeyChanged = { customOpenAiKey = it },
                            customOpenAiBaseUrl = customOpenAiBaseUrl, onCustomOpenAiBaseUrlChanged = { customOpenAiBaseUrl = it },
                            customOpenAiModelId = customOpenAiModelId, onCustomOpenAiModelIdChanged = { customOpenAiModelId = it }
                        )
                        2 -> TabModel(
                            defaultModel = defaultModel,
                            onModelSelected = { defaultModel = it },
                            expanded = modelMenuExpanded,
                            onExpandChanged = { modelMenuExpanded = it }
                        )
                        3 -> TabBudget(
                            budgetInput = budgetInput,
                            onBudgetChanged = { budgetInput = it },
                            stats = statsList
                        )
                        4 -> TabDisplay(
                            currentTheme = themeState,
                            onThemeChanged = { themeState = it },
                            currentLayout = layoutState,
                            onLayoutChanged = { layoutState = it }
                        )
                        5 -> TabStats(
                            stats = statsList,
                            onExportBackupJson = onExportBackupJson,
                            onImportBackupJson = onImportBackupJson
                        )
                    }
                }

                // Bottom Save Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AgoraSpacing.Lg, vertical = AgoraSpacing.Md),
                    horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Md)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Annuler", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Button(
                        onClick = {
                            val budgetFloat = budgetInput.toFloatOrNull()
                            onSaveConfig(
                                geminiKey.trim(),
                                openAiKey.trim(),
                                xaiKey.trim(),
                                customOpenAiKey.trim(),
                                customOpenAiBaseUrl.trim(),
                                customOpenAiModelId.trim(),
                                userNotes.trim(),
                                budgetFloat,
                                defaultModel,
                                themeState,
                                layoutState
                            )
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Enregistrer")
                    }
                }
            }
        }
    }
}

@Composable
private fun TabMyInfo(userNotes: String, onNotesChanged: (String) -> Unit) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = "Informations Utilisateur (Loi du Sceau)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(AgoraSpacing.Sm))
        Text(
            text = "Ces informations personnelles sont stockées avec un chiffrement matériel sur votre appareil et automatiquement transmises à vos personas pour contextualiser les échanges.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(AgoraSpacing.Lg))

        OutlinedTextField(
            value = userNotes,
            onValueChange = onNotesChanged,
            placeholder = {
                Text(
                    "- Je m'appelle Marc-Antoine\n" +
                    "- J'habite à Paris\n" +
                    "- Ingénieur en logiciel et passionné d'IA\n" +
                    "- Style direct, concis et rigoureux"
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            shape = RoundedCornerShape(14.dp)
        )
    }
}

@Composable
private fun TabApiKeys(
    geminiKey: String, onGeminiChanged: (String) -> Unit,
    openAiKey: String, onOpenAiChanged: (String) -> Unit,
    xaiKey: String, onXaiChanged: (String) -> Unit,
    customOpenAiKey: String, onCustomOpenAiKeyChanged: (String) -> Unit,
    customOpenAiBaseUrl: String, onCustomOpenAiBaseUrlChanged: (String) -> Unit,
    customOpenAiModelId: String, onCustomOpenAiModelIdChanged: (String) -> Unit
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = "Souveraineté des Clés d'API (Loi du Sceau)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(AgoraSpacing.Xs))
        Text(
            text = "Vos clés privées sont chiffrées via Android KeyStore (AES-256 GCM) sans aucun serveur tiers.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(AgoraSpacing.Lg))

        OutlinedTextField(
            value = geminiKey,
            onValueChange = onGeminiChanged,
            label = { Text("Clé Google Gemini (AIza...)") },
            placeholder = { Text("Clé API Google AI Studio") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(AgoraSpacing.Md))

        OutlinedTextField(
            value = openAiKey,
            onValueChange = onOpenAiChanged,
            label = { Text("Clé OpenAI (sk-...)") },
            placeholder = { Text("Clé API OpenAI") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(AgoraSpacing.Md))

        OutlinedTextField(
            value = xaiKey,
            onValueChange = onXaiChanged,
            label = { Text("Clé xAI Grok (xai-...)") },
            placeholder = { Text("Clé API xAI") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(AgoraSpacing.Lg))

        Text(
            text = "Option Custom OpenAI (OpenRouter, ZenMux, etc.)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(AgoraSpacing.Xxs))
        Text(
            text = "Laissez vide pour hériter des Providers globaux de l'application.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(AgoraSpacing.Md))

        OutlinedTextField(
            value = customOpenAiKey,
            onValueChange = onCustomOpenAiKeyChanged,
            label = { Text("Clé API Custom") },
            placeholder = { Text("Clé sk-...") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(AgoraSpacing.Md))

        OutlinedTextField(
            value = customOpenAiBaseUrl,
            onValueChange = onCustomOpenAiBaseUrlChanged,
            label = { Text("Base URL API Custom") },
            placeholder = { Text("https://openrouter.ai/api/v1") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(AgoraSpacing.Md))

        OutlinedTextField(
            value = customOpenAiModelId,
            onValueChange = onCustomOpenAiModelIdChanged,
            label = { Text("Identifiant Modèle Custom") },
            placeholder = { Text("google/gemini-2.5-flash") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabModel(
    defaultModel: String,
    onModelSelected: (String) -> Unit,
    expanded: Boolean,
    onExpandChanged: (Boolean) -> Unit
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = "Moteur Vocal et Textuel par Défaut",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(AgoraSpacing.Sm))
        Text(
            text = "Modèle utilisé pour les personas qui n'ont pas de modèle individuel configuré.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(AgoraSpacing.Lg))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = onExpandChanged
        ) {
            OutlinedTextField(
                value = MODEL_OPTIONS.find { it.first == defaultModel }?.second ?: defaultModel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Modèle par défaut") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandChanged(false) }
            ) {
                MODEL_OPTIONS.filter { it.first.isNotEmpty() }.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt.second) },
                        onClick = {
                            onModelSelected(opt.first)
                            onExpandChanged(false)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TabBudget(
    budgetInput: String,
    onBudgetChanged: (String) -> Unit,
    stats: List<StatEntryEntity>
) {
    val budgetLimit = budgetInput.toFloatOrNull()
    val monthSpend = stats.filter {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
        }
        it.date >= cal.timeInMillis
    }.sumOf { it.costDollars }.toFloat()

    val pct = if (budgetLimit != null && budgetLimit > 0) {
        (monthSpend / budgetLimit).coerceIn(0f, 1f)
    } else 0f

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = "Contrôle Budgétaire Autonome",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(AgoraSpacing.Sm))
        Text(
            text = "Suivi en temps réel des dépenses estimées par modèle et session, avec alerte et blocage automatique en cas de dépassement du plafond mensuel.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(AgoraSpacing.Lg))

        OutlinedTextField(
            value = budgetInput,
            onValueChange = onBudgetChanged,
            label = { Text("Plafond mensuel en USD ($)") },
            placeholder = { Text("Ex: 15.00") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(AgoraSpacing.Lg))

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(AgoraSpacing.Lg)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Dépenses ce mois :", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "$${String.format(Locale.US, "%.2f", monthSpend)}" +
                                if (budgetLimit != null && budgetLimit > 0) " / $${String.format(Locale.US, "%.2f", budgetLimit)}" else "",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(AgoraSpacing.Sm))
                LinearProgressIndicator(
                    progress = { pct },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AgoraSpacing.Sm)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (pct > 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun TabDisplay(
    currentTheme: String,
    onThemeChanged: (String) -> Unit,
    currentLayout: String,
    onLayoutChanged: (String) -> Unit
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = "Thème de l'application",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(AgoraSpacing.Sm))
        Row(horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Md)) {
            DisplayChoiceCard(
                title = "Sombre",
                isSelected = currentTheme == "dark",
                onClick = { onThemeChanged("dark") },
                modifier = Modifier.weight(1f)
            )
            DisplayChoiceCard(
                title = "Clair",
                isSelected = currentTheme == "light",
                onClick = { onThemeChanged("light") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(AgoraSpacing.Xl))

        Text(
            text = "Disposition de la conversation",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(AgoraSpacing.Sm))
        Column(verticalArrangement = Arrangement.spacedBy(AgoraSpacing.Sm)) {
            DisplayChoiceCard(
                title = "Vue classique (Orbe + Onde + Texte géant)",
                isSelected = currentLayout == "classic",
                onClick = { onLayoutChanged("classic") },
                modifier = Modifier.fillMaxWidth()
            )
            DisplayChoiceCard(
                title = "Transcription simple (Fil continu)",
                isSelected = currentLayout == "compact",
                onClick = { onLayoutChanged("compact") },
                modifier = Modifier.fillMaxWidth()
            )
            DisplayChoiceCard(
                title = "Bulles latérales (Style messagerie)",
                isSelected = currentLayout == "bubbles",
                onClick = { onLayoutChanged("bubbles") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DisplayChoiceCard(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(AgoraSpacing.Lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun TabStats(
    stats: List<StatEntryEntity>,
    onExportBackupJson: () -> Unit,
    onImportBackupJson: () -> Unit
) {
    val totalCost = stats.sumOf { it.costDollars }
    val totalSessions = stats.count { it.kind == "conversation" }
    val totalTokens = stats.sumOf { it.inputTokens + it.outputTokens }
    val totalSecs = stats.sumOf { it.durationSeconds }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = "Statistiques Analytiques",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(AgoraSpacing.Md))

        Row(horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Sm), modifier = Modifier.fillMaxWidth()) {
            StatMetricCard(title = "Sessions", value = "$totalSessions", modifier = Modifier.weight(1f))
            StatMetricCard(title = "Dépenses", value = "$${String.format(Locale.US, "%.2f", totalCost)}", modifier = Modifier.weight(1f))
            StatMetricCard(title = "Tokens", value = "${totalTokens / 1000}k", modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(AgoraSpacing.Xl))

        Text(
            text = "Sauvegarde & Restauration Intégrale",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(AgoraSpacing.Sm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.Md)
        ) {
            OutlinedButton(
                onClick = onExportBackupJson,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(AgoraSpacing.Xl))
                Spacer(modifier = Modifier.width(AgoraSpacing.Xs))
                Text("Exporter JSON")
            }

            OutlinedButton(
                onClick = onImportBackupJson,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(AgoraSpacing.Xl))
                Spacer(modifier = Modifier.width(AgoraSpacing.Xs))
                Text("Restaurer JSON")
            }
        }
    }
}

@Composable
private fun StatMetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(AgoraSpacing.Md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(AgoraSpacing.Xs))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
