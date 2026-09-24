package com.newoether.agora.ui.ds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Galerie « Design System / Components » : visualise tous les composants
 * canoniques et leurs états. À brancher sur Settings Developer ou route debug.
 * Validation visuelle mobile / tablette / desktop + sans régression fonctionnelle.
 */
@Composable
fun DesignSystemGallery(modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    var slider by remember { mutableFloatStateOf(0.5f) }
    var checked by remember { mutableStateOf(true) }
    var tab by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AgoraSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(AgoraSpacing.Lg),
    ) {
        Text("Design System — Agora", style = MaterialTheme.typography.headlineSmall)
        AgoraSectionLabel("Éditorial / Hero (M3 Expressive)")
        Text("Titre héro 33sp Bold", style = com.newoether.agora.ui.theme.EditorialType.heroTitle)
        Text("Sous-titre héro 16sp Medium", style = com.newoether.agora.ui.theme.EditorialType.heroSubtitle)
        Text("Section label accentué", style = com.newoether.agora.ui.theme.EditorialType.sectionLabel)
        Text("Libellé CTA", style = com.newoether.agora.ui.theme.EditorialType.ctaLabel)
        Text("Numérique premium", style = com.newoether.agora.ui.theme.EditorialType.premiumNumeric)

        AgoraSectionLabel("Boutons (48dp min, Pill)")

        AgoraPrimaryButton(text = "Primaire", onClick = {})
        AgoraPrimaryButton(text = "Disabled", onClick = {}, enabled = false)
        AgoraIconButton(onClick = {}, imageVector = Icons.Filled.ArrowBack, contentDescription = "Retour")
        AgoraBackButton(onClick = {}, contentDescription = "Retour")
        AgoraFab(text = "Action", icon = Icons.Filled.Add, onClick = {})

        AgoraSectionLabel("Card / Section")
        AgoraCard {
            Text(
                "Contenu card — tonal 1dp, border outline 0.12, radius Sm 12dp.",
                modifier = Modifier.padding(AgoraSpacing.CardPadding),
            )
        }

        AgoraSectionLabel("Champs / Slider / Switch / Tabs")
        AgoraTextField(value = text, onValueChange = { text = it }, label = "Label", placeholder = "Placeholder AA")
        AgoraSearchBar(query = text, onQueryChange = { text = it }, placeholder = "Rechercher")
        AgoraSliderRow(title = "Température", value = slider, onValueChange = { slider = it }, valueLabel = "0.5")
        AgoraSwitchRow(title = "Option", checked = checked, onCheckedChange = { checked = it }, description = "Role=Switch, 48dp")
        AgoraTabs(tabs = listOf("Un", "Deux", "Trois"), selectedIndex = tab, onSelect = { tab = it })

        AgoraSectionLabel("Feedback")
        AgoraLoader(label = "Chargement…")
        AgoraLinearLoader()
        AgoraSnackbarCard(message = "Message snackbar — surfaceContainerHigh, radius 12dp.")
        AgoraEmptyState(title = "Vide", description = "État vide canonique.", icon = Icons.Filled.Inbox, actionLabel = "Action", onAction = {})
        AgoraErrorState(title = "Erreur", description = "État erreur canonique + liveRegion.", retryLabel = "Réessayer", onRetry = {})

        AgoraSectionLabel("Dropdown / Skeleton")
        Text("AgoraDropdown + AgoraDropdownItem : shape Sm, surfaceContainer.")
        AgoraSkeleton(height = 20.dp, width = 200.dp)
        AgoraSkeletonTextRow()

        AgoraSectionLabel("Tokens")
        Text(
            "Spacing 2/4/8/12/16/20/24/32 · Radii 8/12/16/24/28+Pill · " +
                "Elev 0/1/2/4/6/8 · Alpha disabled 0.38/hint 0.6/divider 0.12/scrim 0.32 · " +
                "Durées 220/400ms · Ressorts Default/Snappy/Gentle/Container · " +
                "Dark Background 07090E · Surface 0F172A · Variant 1E293B · Outline 334155.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
