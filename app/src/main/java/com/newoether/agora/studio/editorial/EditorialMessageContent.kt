package com.newoether.agora.studio.editorial

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.newoether.agora.viewmodel.ChatViewModel
import com.newoether.agora.ui.ds.AgoraAlpha
import java.io.File
import kotlinx.coroutines.launch

data class EditorialSection(
    val title: String,
    val paragraphs: List<String>,
    var imagePath: String? = null
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorialMessageContent(
    text: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as? com.newoether.agora.AgoraApplication
    val container = remember(app) { app?.requireContainer() }
    val settingsRepo = remember(container) { container?.settingsRepository }

    val serifEnabled = settingsRepo?.editorialSerifEnabled?.collectAsState()?.value ?: true
    val imageFrequency = settingsRepo?.editorialImageFrequency?.collectAsState()?.value ?: 2

    // 1. Parse text into Editorial structure (Title, Subtitle, Intro, Sections, Conclusion)
    val parsedData = remember(text) { parseEditorialText(text) }
    
    // Store generated images index by section index
    val sectionImages = remember { mutableStateMapOf<Int, String>() }

    // Trigger image generation in background if frequency > 0 and model enabled
    LaunchedEffect(parsedData, imageFrequency) {
        if (imageFrequency > 0) {
            parsedData.sections.forEachIndexed { idx, section ->
                if ((idx + 1) % imageFrequency == 0 && !sectionImages.containsKey(idx)) {
                    scope.launch {
                        val path = EditorialImageInjector.generateImageForSection(
                            context = context,
                            sectionTitle = section.title,
                            sectionContent = section.paragraphs.firstOrNull() ?: ""
                        )
                        if (path != null) {
                            sectionImages[idx] = path
                        }
                    }
                }
            }
        }
    }

    val font = if (serifEnabled) FontFamily.Serif else FontFamily.SansSerif

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(14.dp)
    ) {
        // --- Badge Editorial ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Book,
                contentDescription = null,
                tint = Color(0xFFD4AF37),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "ARTICLE EDITORIAL",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD4AF37),
                letterSpacing = 1.5.sp
            )
        }

        // --- Article Title ---
        Text(
            text = parsedData.title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = font,
            color = Color.White,
            lineHeight = 28.sp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
        )

        // --- Subtitle (Accroche) ---
        if (parsedData.subtitle.isNotBlank()) {
            Text(
                text = parsedData.subtitle,
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
                fontFamily = font,
                color = Color.White.copy(alpha = 0.5f),
                lineHeight = 18.sp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )
        }

        // --- Sommaire (Table of Contents) ---
        if (parsedData.sections.size >= 2) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.02f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = AgoraAlpha.Subtle)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Dans cet article",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF),
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    parsedData.sections.forEachIndexed { index, sec ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFD4AF37))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = sec.title,
                                fontSize = 12.sp,
                                fontFamily = font,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // --- Introduction (First Paragraph with Dropcap Lettrine) ---
        val introParagraph = parsedData.introduction.ifBlank {
            parsedData.sections.firstOrNull()?.paragraphs?.firstOrNull() ?: ""
        }

        if (introParagraph.isNotBlank()) {
            val firstLetter = introParagraph.take(1)
            val remainingText = introParagraph.drop(1)

            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(bottom = 14.dp)
            ) {
                Text(
                    text = firstLetter,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD4AF37),
                    fontFamily = font,
                    modifier = Modifier.padding(end = 8.dp, top = 2.dp)
                )
                Text(
                    text = remainingText,
                    fontSize = 14.sp,
                    fontFamily = font,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Justify,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // --- Sections ---
        parsedData.sections.forEachIndexed { index, section ->
            // Skip the very first paragraph if we used it as the introduction dropcap above
            val displayParagraphs = if (parsedData.introduction.isBlank() && index == 0) {
                section.paragraphs.drop(1)
            } else {
                section.paragraphs
            }

            // Don't render section if there's nothing to show
            if (section.title.isNotBlank() || displayParagraphs.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Section title with colored bar decoration
                    if (section.title.isNotBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(Color(0xFF00E5FF))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = section.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = font,
                            color = Color(0xFFD4AF37),
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }

                    // Section Paragraphs
                    displayParagraphs.forEach { p ->
                        Text(
                            text = p,
                            fontSize = 14.sp,
                            fontFamily = font,
                            lineHeight = 21.sp,
                            textAlign = TextAlign.Justify,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        )
                    }

                    // Auto-generated Section Illustration Image
                    val imagePath = sectionImages[index]
                    AnimatedVisibility(
                        visible = imagePath != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        imagePath?.let { path ->
                            val file = remember(path) { File(path) }
                            if (file.exists()) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = AgoraAlpha.Subtle)),
                                    color = Color.Black,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .padding(vertical = 10.dp)
                                ) {
                                    AsyncImage(
                                        model = file,
                                        contentDescription = "Illustration pour : ${section.title}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Conclusion ---
        if (parsedData.conclusion.isNotBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color(0xFFD4AF37))
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "En conclusion",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = font,
                color = Color(0xFF00E5FF),
                modifier = Modifier.padding(bottom = 10.dp)
            )
            Text(
                text = parsedData.conclusion,
                fontSize = 14.sp,
                fontFamily = font,
                lineHeight = 21.sp,
                textAlign = TextAlign.Justify,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )
        }

        // --- Social Actions Bar ---
        Spacer(modifier = Modifier.height(8.dp))
        com.newoether.agora.ui.chat.message.EditorialActionsBar(
            articleText = text,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

data class EditorialArticle(
    val title: String,
    val subtitle: String,
    val introduction: String,
    val sections: List<EditorialSection>,
    val conclusion: String
)

/**
 * Parses markdown / plain text response into EditorialArticle parts.
 */
fun parseEditorialText(rawText: String): EditorialArticle {
    val lines = rawText.lines()
    var title = ""
    var subtitle = ""
    var introduction = ""
    val sections = mutableListOf<EditorialSection>()
    var conclusion = ""

    var currentSectionTitle = ""
    val currentSectionParagraphs = mutableListOf<String>()

    var inConclusion = false

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isBlank()) continue

        when {
            trimmed.startsWith("# ") -> {
                title = trimmed.removePrefix("# ").trim()
            }
            trimmed.startsWith("## ") -> {
                val secTitle = trimmed.removePrefix("## ").trim()
                if (secTitle.contains("conclusion", ignoreCase = true) || secTitle.contains("synthèse", ignoreCase = true)) {
                    inConclusion = true
                } else {
                    if (currentSectionTitle.isNotBlank() || currentSectionParagraphs.isNotEmpty()) {
                        sections.add(EditorialSection(currentSectionTitle, currentSectionParagraphs.toList()))
                        currentSectionParagraphs.clear()
                    }
                    currentSectionTitle = secTitle
                    inConclusion = false
                }
            }
            else -> {
                // If it is a paragraph
                if (title.isBlank()) {
                    title = trimmed.take(60) // Fallback title
                } else if (subtitle.isBlank() && !trimmed.startsWith("-") && !trimmed.startsWith("*")) {
                    subtitle = trimmed
                } else if (introduction.isBlank() && !trimmed.startsWith("-") && !trimmed.startsWith("*") && currentSectionTitle.isBlank()) {
                    introduction = trimmed
                } else if (inConclusion) {
                    conclusion = trimmed
                } else {
                    currentSectionParagraphs.add(trimmed)
                }
            }
        }
    }

    // Flush last section
    if (currentSectionTitle.isNotBlank() || currentSectionParagraphs.isNotEmpty()) {
        sections.add(EditorialSection(currentSectionTitle, currentSectionParagraphs.toList()))
    }

    return EditorialArticle(
        title = title.ifBlank { "Article" },
        subtitle = subtitle,
        introduction = introduction,
        sections = sections,
        conclusion = conclusion
    )
}
