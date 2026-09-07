package com.newoether.agora.workspace.drive

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newoether.agora.R
import com.newoether.agora.ui.theme.OutfitFamily
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleWorkspaceDashboard(
    repository: GoogleDriveRepository,
    onImportFileToChat: (GoogleDriveFile) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val authState by repository.authState.collectAsState()
    val recentFiles by repository.recentFiles.collectAsState()
    val currentFolderFiles by repository.currentFolderFiles.collectAsState()
    val currentFolderPath by repository.currentFolderPath.collectAsState()
    val isSyncing by repository.isSyncing.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<GoogleWorkspaceType?>(null) }
    var searchResults by remember { mutableStateOf<List<GoogleDriveFile>?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var importedFeedbackFile by remember { mutableStateOf<String?>(null) }

    // Search trigger
    LaunchedEffect(searchQuery, selectedFilter) {
        if (searchQuery.isNotBlank() || selectedFilter != null) {
            isSearching = true
            val results = repository.searchFiles(searchQuery, selectedFilter)
            searchResults = results
            isSearching = false
        } else {
            searchResults = null
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C1015))
                    .statusBarsPadding()
            ) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_google_drive),
                        contentDescription = "Google Drive",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.workspace_google_drive),
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Espace Google Workspace",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    // Sync button
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                repository.syncDriveFiles()
                            }
                        },
                        enabled = !isSyncing
                    ) {
                        val rotation = remember { Animatable(0f) }
                        LaunchedEffect(isSyncing) {
                            if (isSyncing) {
                                rotation.animateTo(
                                    targetValue = 360f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1000, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart
                                    )
                                )
                            } else {
                                rotation.snapTo(0f)
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = stringResource(R.string.workspace_oauth_sync),
                            tint = if (isSyncing) Color(0xFF4285F4) else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    if (onClose != null) {
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fermer",
                                tint = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                // Account / OAuth status pill
                GoogleDriveAccountBanner(
                    authState = authState,
                    onConnectClick = { repository.startOAuthBrowserFlow(context) },
                    onManualTokenClick = { showTokenDialog = true },
                    onDisconnectClick = { repository.disconnect() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Search Bar Component
                GoogleDriveSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClearQuery = {
                        searchQuery = ""
                        focusManager.clearFocus()
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Category Filter Chips
                GoogleWorkspaceFilterChips(
                    selectedFilter = selectedFilter,
                    onFilterSelect = { selectedFilter = if (selectedFilter == it) null else it }
                )

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            }
        },
        containerColor = Color(0xFF07090C),
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                // If search active: display search results
                if (searchResults != null) {
                    item {
                        Text(
                            text = "Résultats de recherche (${searchResults!!.size})",
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                    if (searchResults!!.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.workspace_no_files_found),
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        items(searchResults!!, key = { it.id }) { file ->
                            GoogleDriveFileRow(
                                file = file,
                                onImportClick = {
                                    onImportFileToChat(file)
                                    importedFeedbackFile = file.name
                                },
                                onOpenClick = {
                                    file.webViewLink?.let { link ->
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                                    }
                                }
                            )
                        }
                    }
                } else {
                    // ── SECTION 1: FICHIERS RÉCENTS GOOGLE DRIVE ──
                    item {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.History,
                                        contentDescription = null,
                                        tint = Color(0xFF4285F4),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.workspace_recent_files),
                                        fontFamily = OutfitFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = "${recentFiles.size} fichiers",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Horizontal recent files cards
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(recentFiles, key = { "recent_${it.id}" }) { file ->
                                    GoogleDriveRecentFileCard(
                                        file = file,
                                        onImportClick = {
                                            onImportFileToChat(file)
                                            importedFeedbackFile = file.name
                                        },
                                        onOpenClick = {
                                            file.webViewLink?.let { link ->
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // ── SECTION 2: EXPLORATEUR DE FICHIERS GOOGLE DRIVE ──
                    item {
                        Column(modifier = Modifier.padding(top = 24.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.FolderOpen,
                                        contentDescription = null,
                                        tint = Color(0xFFF4B400),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.workspace_file_explorer),
                                        fontFamily = OutfitFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Breadcrumbs
                            GoogleDriveBreadcrumbs(
                                path = currentFolderPath,
                                onCrumbClick = { index ->
                                    coroutineScope.launch {
                                        repository.navigateBackToFolder(index)
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    // Explorer items list
                    items(currentFolderFiles, key = { "explorer_${it.id}" }) { file ->
                        GoogleDriveFileRow(
                            file = file,
                            onImportClick = {
                                if (file.isFolder) {
                                    coroutineScope.launch {
                                        repository.openFolder(file.id, file.name)
                                    }
                                } else {
                                    onImportFileToChat(file)
                                    importedFeedbackFile = file.name
                                }
                            },
                            onOpenClick = {
                                if (file.isFolder) {
                                    coroutineScope.launch {
                                        repository.openFolder(file.id, file.name)
                                    }
                                } else {
                                    file.webViewLink?.let { link ->
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Toast feedback when file imported
            AnimatedVisibility(
                visible = importedFeedbackFile != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF1E293B),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF34A853).copy(alpha = 0.5f)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF34A853),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Importé : ${importedFeedbackFile ?: ""}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontFamily = OutfitFamily
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        TextButton(
                            onClick = { importedFeedbackFile = null },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("OK", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Manual token entry dialog
    if (showTokenDialog) {
        var tokenInput by remember { mutableStateOf("") }
        AlertDialog(
            containerColor = Color(0xFF121820),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.8f),
            onDismissRequest = { showTokenDialog = false },
            title = {
                Text(
                    text = "Connexion OAuth Google Drive",
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Collez un jeton d'accès Google Drive (OAuth 2.0) pour synchroniser votre compte Google Workspace :",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        placeholder = { Text("ya29.a0A...", color = Color.White.copy(alpha = 0.3f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF4285F4),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tokenInput.isNotBlank()) {
                            repository.setAccessToken(tokenInput.trim())
                            showTokenDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4))
                ) {
                    Text("Connecter")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTokenDialog = false }) {
                    Text("Annuler", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }
}

@Composable
fun GoogleDriveAccountBanner(
    authState: DriveAuthState,
    onConnectClick: () -> Unit,
    onManualTokenClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF141A22),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        when (authState) {
            is DriveAuthState.Connected -> {
                val account = authState.account
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF34A853))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = account.email,
                                fontFamily = OutfitFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                        }

                        Row {
                            TextButton(
                                onClick = onManualTokenClick,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Jeton", fontSize = 11.sp, color = Color(0xFF38BDF8))
                            }
                            TextButton(
                                onClick = onDisconnectClick,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Déconnecter", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Storage progress
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Stockage Drive",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Text(
                            text = account.formattedUsage,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { account.usageFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF4285F4),
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                }
            }

            is DriveAuthState.Connecting -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF4285F4)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.workspace_oauth_connecting),
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }

            else -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Connexion Google Drive",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Accédez à vos Docs, Sheets et Slides",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    Row {
                        Button(
                            onClick = onConnectClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Connexion OAuth", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GoogleDriveSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(pillShape)
            .background(Color(0xFF121820))
            .border(1.dp, Color.White.copy(alpha = 0.15f), pillShape)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Rechercher",
                tint = Color(0xFF4285F4),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = OutfitFamily,
                    fontSize = 14.sp,
                    color = Color.White
                ),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.workspace_search_hint),
                            fontFamily = OutfitFamily,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.45f)
                        )
                    }
                    innerTextField()
                }
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClearQuery,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Effacer",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GoogleWorkspaceFilterChips(
    selectedFilter: GoogleWorkspaceType?,
    onFilterSelect: (GoogleWorkspaceType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filters = listOf(
        GoogleWorkspaceType.DOCS to "Docs",
        GoogleWorkspaceType.SHEETS to "Sheets",
        GoogleWorkspaceType.SLIDES to "Slides",
        GoogleWorkspaceType.FOLDER to "Dossiers",
        GoogleWorkspaceType.PDF to "PDF"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEach { (type, label) ->
            val isSelected = selectedFilter == type
            val chipBg = if (isSelected) type.brandColor.copy(alpha = 0.25f) else Color(0xFF141A22)
            val borderColor = if (isSelected) type.brandColor else Color.White.copy(alpha = 0.12f)
            val textColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)

            Surface(
                onClick = { onFilterSelect(type) },
                shape = RoundedCornerShape(12.dp),
                color = chipBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (type.iconResId != null) {
                        Icon(
                            painter = painterResource(id = type.iconResId),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
fun GoogleDriveRecentFileCard(
    file: GoogleDriveFile,
    onImportClick: () -> Unit,
    onOpenClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = file.workspaceType
    Surface(
        modifier = modifier
            .width(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .clickable(onClick = onOpenClick),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF11171F)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top Row: Type Icon + Relative Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (type.iconResId != null) {
                    Icon(
                        painter = painterResource(id = type.iconResId),
                        contentDescription = type.label,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = if (file.isFolder) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                        contentDescription = type.label,
                        tint = type.brandColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = file.formattedDate,
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // File Name
            Text(
                text = file.name,
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(38.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Size / Type subtitle
            Text(
                text = file.formattedSize,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.55f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Import Button
            Button(
                onClick = onImportClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = type.brandColor.copy(alpha = 0.18f),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddComment,
                    contentDescription = null,
                    tint = type.brandColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Importer",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun GoogleDriveBreadcrumbs(
    path: List<Pair<String, String>>,
    onCrumbClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        path.forEachIndexed { index, (_, name) ->
            val isLast = index == path.lastIndex
            Text(
                text = name,
                fontFamily = OutfitFamily,
                fontSize = 12.sp,
                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                color = if (isLast) Color.White else Color(0xFF4285F4),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = !isLast) { onCrumbClick(index) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            if (!isLast) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun GoogleDriveFileRow(
    file: GoogleDriveFile,
    onImportClick: () -> Unit,
    onOpenClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = file.workspaceType
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        if (type.iconResId != null) {
            Icon(
                painter = painterResource(id = type.iconResId),
                contentDescription = type.label,
                tint = Color.Unspecified,
                modifier = Modifier.size(28.dp)
            )
        } else {
            Icon(
                imageVector = if (file.isFolder) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = type.label,
                tint = type.brandColor,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = file.formattedSize,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
                Text(
                    text = " • ",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.4f)
                )
                Text(
                    text = file.formattedDate,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }

        // Actions
        IconButton(
            onClick = onImportClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (file.isFolder) Icons.Default.ChevronRight else Icons.Default.AddComment,
                contentDescription = if (file.isFolder) "Ouvrir dossier" else "Importer dans le chat",
                tint = if (file.isFolder) Color.White.copy(alpha = 0.6f) else Color(0xFF4285F4),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
