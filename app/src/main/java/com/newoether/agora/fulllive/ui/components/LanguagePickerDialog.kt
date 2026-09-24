package com.newoether.agora.fulllive.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

data class SupportedLanguage(
    val code: String,
    val name: String,
    val flag: String
)

val DEFAULT_TRANSLATE_LANGUAGES = listOf(
    SupportedLanguage("en", "Anglais", "🇬🇧"),
    SupportedLanguage("es", "Espagnol", "🇪🇸"),
    SupportedLanguage("de", "Allemand", "🇩🇪"),
    SupportedLanguage("it", "Italien", "🇮🇹"),
    SupportedLanguage("pt", "Portugais", "🇵🇹"),
    SupportedLanguage("zh", "Chinois (Mandarin)", "🇨🇳"),
    SupportedLanguage("ja", "Japonais", "🇯🇵"),
    SupportedLanguage("ko", "Coréen", "🇰🇷"),
    SupportedLanguage("ar", "Arabe", "🇸🇦"),
    SupportedLanguage("ru", "Russe", "🇷🇺"),
    SupportedLanguage("hi", "Hindi", "🇮🇳"),
    SupportedLanguage("nl", "Néerlandais", "🇳🇱"),
    SupportedLanguage("sv", "Suédois", "🇸🇪"),
    SupportedLanguage("pl", "Polonais", "🇵🇱"),
    SupportedLanguage("tr", "Turc", "🇹🇷"),
    SupportedLanguage("el", "Grec", "🇬🇷"),
    SupportedLanguage("vi", "Vietnamien", "🇻🇳"),
    SupportedLanguage("id", "Indonésien", "🇮🇩"),
    SupportedLanguage("fr", "Français", "🇫🇷")
)

@Composable
fun LanguagePickerDialog(
    selectedCode: String,
    onDismiss: () -> Unit,
    onLanguageSelected: (SupportedLanguage) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredLangs = remember(searchQuery) {
        if (searchQuery.isBlank()) DEFAULT_TRANSLATE_LANGUAGES
        else DEFAULT_TRANSLATE_LANGUAGES.filter {
            it.name.contains(searchQuery, ignoreCase = true) || it.code.contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Langue cible de traduction",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Rechercher une langue...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredLangs) { lang ->
                        val isSelected = lang.code == selectedCode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onLanguageSelected(lang)
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = lang.flag, fontSize = 22.sp)
                                Spacer(modifier = Modifier.padding(6.dp))
                                Text(
                                    text = lang.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Fermer", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
