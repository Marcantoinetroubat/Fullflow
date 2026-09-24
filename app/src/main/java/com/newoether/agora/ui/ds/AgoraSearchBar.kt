package com.newoether.agora.ui.ds

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Barre de recherche canonique (champ simple : query + placeholder + loupe).
 * Hauteur 56dp, shape Pill, hint à contraste AA.
 *
 * Non migrés (écart fonctionnel documenté) : DrawerSearchBar (48dp, état
 * searching avec loader, bouton clear — à migrer avec la refonte du drawer),
 * GoogleDriveSearchBar et WebResearchHost field.
 */
@Composable
fun AgoraSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        placeholder = {
            Text(
                placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AgoraAlpha.Hint),
            )
        },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        singleLine = true,
        shape = AgoraRadii.Pill,
    )
}
