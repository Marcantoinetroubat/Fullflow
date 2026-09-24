package com.newoether.agora.ui.chat.message

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector

enum class FollowUpType(val label: String, val badge: String, val icon: ImageVector) {
    DEEP_DIVE("Approfondissement", "Approfondir", Icons.Default.Search),
    EXPANSION("Ouverture", "Cas pratique", Icons.Default.Explore),
    TRANSVERSAL("Transversalité", "Transversal", Icons.Default.Hub)
}

data class FollowUpSuggestion(
    val type: FollowUpType,
    val text: String,
)

object FollowUpSuggestionsGenerator {

    private val STOP_WORDS = setOf(
        "note", "important", "attention", "remarque", "exemple", "exemples",
        "étape", "étapes", "step", "summary", "résumé", "introduction", "conclusion",
        "astuce", "conseil", "warning", "info", "overview", "détails", "details",
        "code", "output", "résultat", "solution", "options", "option", "voici",
        "faire", "comme", "aussi", "plus", "très", "bien", "tout", "tous", "cette",
        "celui", "ceux", "notre", "votre", "leurs", "leur", "avoir", "être", "avec",
        "dans", "pour", "sans", "sous", "vers", "chez", "alors", "donc", "mais"
    )

    private val FRENCH_COMMON_WORDS = setOf(
        "pour", "dans", "cette", "avec", "vous", "nous", "leur", "tous", "tout",
        "mais", "donc", "alors", "voici", "voilà", "selon", "comme", "ainsi",
        "parce", "quand", "chaque", "autre", "autres", "entre", "après", "avant",
        // Articles, pronouns and auxiliaries — prevents sentence-initial capitalized
        // stopwords (e.g. « Les », « Des », « Une ») from polluting concept extraction.
        "les", "des", "une", "aux", "ces", "ses", "pas", "sur", "par", "est",
        "sont", "ont", "été", "elle", "elles", "ils", "dont", "toute", "toutes",
        "celle", "celles"
    )

    fun generate(
        assistantText: String,
        userPrompt: String? = null,
        memorySnippet: String? = null,
    ): List<FollowUpSuggestion> {
        val clean = assistantText.trim()
        val userClean = userPrompt?.trim().orEmpty()

        if (clean.length < 15 && userClean.length < 5) {
            return defaultSuggestions()
        }

        // 1. Extraire les concepts de la réponse et de la question utilisateur
        val promptConcepts = extractUserPromptConcepts(userClean)
        val assistantConcepts = extractAllSalientConcepts(clean)

        // Combiner en évitant les doublons : priorité aux concepts de la réponse, enrichis par ceux de la question
        val allConcepts = mutableListOf<String>()
        assistantConcepts.forEach { if (!allConcepts.contains(it)) allConcepts.add(it) }
        promptConcepts.forEach { if (!allConcepts.contains(it)) allConcepts.add(it) }

        val primaryConcept = allConcepts.getOrNull(0)
            ?: promptConcepts.firstOrNull()
            ?: extractFallbackSubject(clean, userClean)

        val secondaryConcept = allConcepts.getOrNull(1) ?: promptConcepts.getOrNull(1)
        val tertiaryConcept = allConcepts.getOrNull(2) ?: promptConcepts.getOrNull(2)

        // 2. Détecter le domaine prédominant
        val combined = (clean + " " + userClean).lowercase()
        val isCode = combined.contains("fun ") || combined.contains("class ") || combined.contains("val ") ||
                combined.contains("import ") || combined.contains("kotlin") || combined.contains("def ") ||
                clean.contains("```") || combined.contains("code") || combined.contains("api") ||
                combined.contains("fonction") || combined.contains("script")

        val isArchitecture = combined.contains("architecture") || combined.contains("pipeline") ||
                combined.contains("composant") || combined.contains("modèle") || combined.contains("système") ||
                combined.contains("flux") || combined.contains("infrastructure") || combined.contains("service") ||
                combined.contains("docker") || combined.contains("serveur")

        val isDataOrAI = combined.contains("données") || combined.contains("analyse") ||
                combined.contains("modèle ia") || combined.contains("algorithme") || combined.contains("prompt") ||
                combined.contains("token") || combined.contains("llm") || combined.contains("embeddings") ||
                combined.contains("machine learning") || combined.contains("dataset")

        val isWritingOrDoc = combined.contains("rédiger") || combined.contains("document") ||
                combined.contains("article") || combined.contains("synthèse") || combined.contains("plan d'action") ||
                combined.contains("rapport") || combined.contains("présentation")

        // 3. Analyser la recommandation finale ou indice de conclusion
        val concludingHint = extractConcludingHint(clean)

        // Variateur déterministe pour renouveler les formulations
        val seed = (clean.length xor clean.hashCode() xor userClean.hashCode()).let { if (it < 0) -it else it }

        // ── 1. APPROFONDISSEMENT (DEEP DIVE) ──
        val deepDiveText = when {
            primaryConcept != null && isCode -> {
                val variants = listOf(
                    "Comment gérer les cas limites et erreurs potentielles avec $primaryConcept ?",
                    "Peux-tu détailler le fonctionnement interne et les bonnes pratiques pour $primaryConcept ?",
                    "Comment optimiser la performance et la consommation mémoire de $primaryConcept ?"
                )
                variants[seed % variants.size]
            }
            primaryConcept != null && isArchitecture -> {
                val variants = listOf(
                    "Quels sont les compromis (trade-offs) et points de friction majeurs de $primaryConcept ?",
                    "Comment assurer la scalabilité et la résilience de $primaryConcept en production ?",
                    "Comment s'opère la synchronisation et la cohérence d'état dans $primaryConcept ?"
                )
                variants[seed % variants.size]
            }
            primaryConcept != null && isDataOrAI -> {
                val variants = listOf(
                    "Quels sont les biais méthodologiques et limites techniques concernant $primaryConcept ?",
                    "Comment évaluer précisément la qualité et les métriques de $primaryConcept ?",
                    "Peux-tu détailler la structure des données et paramètres de $primaryConcept ?"
                )
                variants[seed % variants.size]
            }
            primaryConcept != null -> {
                val variants = listOf(
                    "Peux-tu approfondir les mécanismes clés et sous-jacents de $primaryConcept ?",
                    "Quels sont les prérequis et pièges à anticiper concernant $primaryConcept ?",
                    "Quels sont les critères d'évaluation prioritaires pour $primaryConcept ?"
                )
                variants[seed % variants.size]
            }
            concludingHint != null -> {
                "Peux-tu détailler plus en profondeur les points soulevés sur $concludingHint ?"
            }
            isCode -> "Quels sont les cas limites et comment tester cette implémentation en conditions réelles ?"
            else -> "Quels sont les mécanismes fondamentaux et points d'attention clés à approfondir ?"
        }

        // ── 2. OUVERTURE / CAS PRATIQUE & FORMATS MULTIMODAUX (EXPANSION) ──
        val expansionSubject = secondaryConcept ?: primaryConcept
        val expansionText = when {
            expansionSubject != null && isCode -> {
                val variants = listOf(
                    "Donne-moi un exemple concret pas à pas d'intégration pour $expansionSubject.",
                    "Peux-tu modéliser l'architecture de $expansionSubject sous forme de diagramme ou mindmap Mermaid ?",
                    "Comment tester unitairement et valider ce cas d'usage avec $expansionSubject ?",
                    "Peux-tu écrire le script ou le code complet prêt pour la production ?"
                )
                variants[(seed / 3) % variants.size]
            }
            expansionSubject != null && isArchitecture -> {
                val variants = listOf(
                    "Comment déployer concrètement cette solution $expansionSubject sur un environnement réel ?",
                    "Peux-tu modéliser ce flux de $expansionSubject en mindmap ou diagramme d'architecture visuel ?",
                    "Quels sont les outils d'observabilité et de monitoring à mettre en place pour $expansionSubject ?",
                    "Propose un plan de migration progressif sans interruption de service pour $expansionSubject."
                )
                variants[(seed / 3) % variants.size]
            }
            expansionSubject != null && isWritingOrDoc -> {
                val variants = listOf(
                    "Peux-tu rédiger une première ébauche prête à l'emploi sur $expansionSubject ?",
                    "Structure une présentation en 3 slides percutantes pour valoriser $expansionSubject.",
                    "Comment synthétiser ce contenu en script audio / podcast ou mémo exécutif ?",
                    "Propose une variante adaptée à un public décisionnaire ou exécutif."
                )
                variants[(seed / 3) % variants.size]
            }
            expansionSubject != null && isDataOrAI -> {
                val variants = listOf(
                    "Génère un tableau de bord visuel avec les indicateurs clés et métriques pour $expansionSubject.",
                    "Comment enrichir et reformuler ce prompt avec la baguette magique pour un rendu optimal ?",
                    "Comment croiser ces analyses de $expansionSubject avec d'autres sources de données ?",
                    "Donne-moi un cas pratique concret d'application opérationnelle pour $expansionSubject."
                )
                variants[(seed / 3) % variants.size]
            }
            expansionSubject != null -> {
                val variants = listOf(
                    "Comment mettre en pratique concrètement $expansionSubject sur un cas réel ?",
                    "Peux-tu structurer cette approche en mindmap visuelle ou plan d'action hiérarchisé ?",
                    "Comment présenter cette démarche sous forme de slides ou de synthèse de cadrage ?",
                    "Donne-moi une feuille de route opérationnelle étape par étape pour $expansionSubject."
                )
                variants[(seed / 3) % variants.size]
            }
            concludingHint != null -> {
                "Comment mettre en œuvre concrètement la démarche suggérée sur $concludingHint ?"
            }
            else -> "Donne-moi un cas pratique concret pour appliquer ces recommandations immédiatement."
        }

        // ── 3. TRANSVERSALITÉ / SECOND CERVEAU & SYNERGIES (TRANSVERSAL) ──
        val memTarget = memorySnippet?.trim()?.takeIf { it.isNotBlank() }
        val transversalSubject = tertiaryConcept ?: secondaryConcept ?: primaryConcept
        val transversalText = when {
            memTarget != null && primaryConcept != null -> {
                val firstMemLine = memTarget.lines().firstOrNull { it.isNotBlank() }
                    ?.removePrefix("#")?.removePrefix("-")?.trim()?.take(35) ?: "vos projets"
                val variants = listOf(
                    "Comment articuler $primaryConcept avec vos objectifs en cours : '$firstMemLine' ?",
                    "Quelles synergies développer entre $primaryConcept et vos notes actives du Second Cerveau ?",
                    "Comment intégrer cette approche de $primaryConcept dans vos flux de travail prioritaires ?"
                )
                variants[(seed / 7) % variants.size]
            }
            transversalSubject != null && isCode -> {
                val variants = listOf(
                    "Quelles sont les alternatives modernes à $transversalSubject et leurs avantages respectifs ?",
                    "Quel est l'impact de ce choix technique sur la maintenabilité et l'architecture globale ?",
                    "Comment cette approche se compare-t-elle aux standards de l'industrie pour $transversalSubject ?"
                )
                variants[(seed / 7) % variants.size]
            }
            transversalSubject != null && isArchitecture -> {
                val variants = listOf(
                    "Comment interconnecter cette architecture $transversalSubject avec d'autres services ?",
                    "Quelles sont les solutions concurrentes ou alternatives viables pour $transversalSubject ?",
                    "Quelles synergies stratégiques peut-on dégager pour les futurs besoins d'évolution ?"
                )
                variants[(seed / 7) % variants.size]
            }
            transversalSubject != null && isDataOrAI -> {
                val variants = listOf(
                    "Comment croiser ces résultats avec d'autres sources de données ou modèles ?",
                    "Quels sont les impacts éthiques, de gouvernance et de confidentialité de $transversalSubject ?",
                    "Quelles sont les synergies possibles entre $transversalSubject et d'autres flux métiers ?"
                )
                variants[(seed / 7) % variants.size]
            }
            transversalSubject != null -> {
                val variants = listOf(
                    "Quelles synergies et passerelles peut-on créer entre $transversalSubject et d'autres domaines ?",
                    "Quelles alternatives stratégiques existent face à $transversalSubject et comment se positionnent-elles ?",
                    "Quelles perspectives d'avenir et innovations émergentes vont impacter $transversalSubject ?"
                )
                variants[(seed / 7) % variants.size]
            }
            else -> "Quelles alternatives, synergies et passerelles innovantes recommandes-tu d'explorer ?"
        }

        return listOf(
            FollowUpSuggestion(FollowUpType.DEEP_DIVE, deepDiveText),
            FollowUpSuggestion(FollowUpType.EXPANSION, expansionText),
            FollowUpSuggestion(FollowUpType.TRANSVERSAL, transversalText),
        )
    }

    /**
     * Extrait les sujets et entités clés depuis la question de l'utilisateur.
     */
    private fun extractUserPromptConcepts(prompt: String): List<String> {
        if (prompt.isBlank()) return emptyList()
        val results = mutableListOf<String>()

        // 1. Mots ou locutions entre guillemets ("terme" ou 'terme')
        val quoteRegex = Regex("[\"'«“]([^\"'»”\\n]{2,35})[\"'»”]")
        quoteRegex.findAll(prompt).forEach { match ->
            val c = cleanConcept(match.groupValues[1])
            if (c != null && !results.contains(c)) results.add(c)
        }

        // 2. Nettoyer les formules interrogatives habituelles pour isoler le cœur du sujet
        var cleanPrompt = prompt
            .replace(Regex("""(?i)^(peux-tu|pourrais-tu|pouvez-vous|merci de|stp|svp|s'il te plaît|s'il vous plaît|explique|explique-moi|comment|pourquoi|qu'est-ce que|quelle est|quel est|quels sont|quelles sont|donne-moi|aide-moi à|montre-moi|écris|créer|faire)\s+"""), "")
            .replace(Regex("""(?i)\s*\?$"""), "")
            .trim()

        // 3. Mots avec majuscule (hors premier mot si phrase courante) ou acronymes
        val entityRegex = Regex("""\b([A-Z][a-zA-Z0-9_\-\.]{2,30})\b""")
        entityRegex.findAll(cleanPrompt).forEach { match ->
            val word = match.groupValues[1].trim()
            if (!FRENCH_COMMON_WORDS.contains(word.lowercase()) && !STOP_WORDS.contains(word.lowercase())) {
                val c = cleanConcept(word)
                if (c != null && !results.contains(c)) results.add(c)
            }
        }

        // 4. Si la phrase nettoyée est concise (3 à 35 caractères), l'utiliser comme concept direct
        if (cleanPrompt.length in 4..35 && !FRENCH_COMMON_WORDS.contains(cleanPrompt.lowercase())) {
            val c = cleanConcept(cleanPrompt)
            if (c != null && !results.contains(c)) results.add(c)
        }

        return results
    }

    private fun extractAllSalientConcepts(text: String): List<String> {
        val results = mutableListOf<String>()

        // 1. Titres markdown (### Titre ou ## Titre)
        val headerRegex = Regex("""#{1,4}\s+([a-zA-ZÀ-ÿ0-9_\- '’]{3,45})""")
        headerRegex.findAll(text).forEach { match ->
            val candidate = cleanConcept(match.groupValues[1])
            if (candidate != null && !results.contains(candidate)) {
                results.add(candidate)
            }
        }

        // 2. Mots ou locutions en gras (**terme**)
        val boldRegex = Regex("""\*\*([a-zA-ZÀ-ÿ0-9_\- '’]{3,40})\*\*""")
        boldRegex.findAll(text).forEach { match ->
            val candidate = cleanConcept(match.groupValues[1])
            if (candidate != null && !results.contains(candidate)) {
                results.add(candidate)
            }
        }

        // 3. Éléments de liste structurés (1. **Concept** : ou - Concept :)
        val listRegex = Regex("""(?:^|\n)(?:\d+\.|\*|-)\s+(?:\*\*)?([A-ZÀ-Ýa-z0-9_\- '’]{3,35})(?:\*\*)?\s*[:–—]""")
        listRegex.findAll(text).forEach { match ->
            val candidate = cleanConcept(match.groupValues[1])
            if (candidate != null && !results.contains(candidate)) {
                results.add(candidate)
            }
        }

        // 4. Code en ligne pertinent (`nomDeFonction` ou `NomClasse`)
        val codeRegex = Regex("""`([a-zA-Z][a-zA-Z0-9_\.\-]{2,35})`""")
        codeRegex.findAll(text).forEach { match ->
            val code = match.groupValues[1].trim()
            if (!code.contains(" ") && code.length in 3..30 && !results.contains(code)) {
                results.add(code)
            }
        }

        // 5. Termes entre guillemets dans le texte
        val quoteRegex = Regex("[\"'«“]([a-zA-ZÀ-ÿ0-9_\\- '’]{3,35})[\"'»”]")
        quoteRegex.findAll(text).forEach { match ->
            val candidate = cleanConcept(match.groupValues[1])
            if (candidate != null && !results.contains(candidate)) {
                results.add(candidate)
            }
        }

        // 6. Termes techniques et acronymes en majuscules (ex: OAuth, Room, JWT, REST, GraphQL, Compose, Whisper)
        val techAcronymRegex = Regex("""\b([A-Z][A-Za-z0-9]{2,15}(?:[A-Z][a-z0-9]+)?)\b""")
        techAcronymRegex.findAll(text).forEach { match ->
            val term = match.groupValues[1].trim()
            if (term.length in 3..20 && !STOP_WORDS.contains(term.lowercase()) && !FRENCH_COMMON_WORDS.contains(term.lowercase()) && !results.contains(term)) {
                results.add(term)
            }
        }

        // 7. Expressions clés après des verbes / locutions pivots (ex: "le protocole X", "l'architecture Y", "concernant Z")
        val pivotRegex = Regex("""(?i)\b(?:l'architecture|le protocole|l'outil|la bibliothèque|le framework|l'algorithme|la méthode|concernant|avec le système)\s+([a-zA-ZÀ-ÿ0-9_\-]{3,30})\b""")
        pivotRegex.findAll(text).forEach { match ->
            val candidate = cleanConcept(match.groupValues[1])
            if (candidate != null && !results.contains(candidate)) {
                results.add(candidate)
            }
        }

        return results
    }

    private fun extractFallbackSubject(assistantText: String, userPrompt: String): String? {
        val candidate = extractUserPromptConcepts(userPrompt).firstOrNull()
        if (candidate != null) return candidate

        // Chercher dans les premières phrases de l'assistant les mots significatifs de plus de 5 lettres
        val firstSentence = assistantText.split(Regex("""[\.\n\?!]""")).firstOrNull { it.isNotBlank() } ?: ""
        val significantWords = firstSentence.split(Regex("""[\s,;:–—]+"""))
            .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
            .filter { it.length >= 5 && !FRENCH_COMMON_WORDS.contains(it.lowercase()) && !STOP_WORDS.contains(it.lowercase()) }

        return significantWords.firstOrNull { it.firstOrNull()?.isUpperCase() == true } ?: significantWords.firstOrNull()
    }

    private fun cleanConcept(raw: String): String? {
        var trimmed = raw.trim().removeSuffix(":").removeSuffix(".").removeSuffix(",").trim()
        // Strip leading articles/prepositions so suggestions read « côté » and not
        // « un côté », « architecture » and not « l'architecture ».
        trimmed = trimmed.replace(
            Regex("""^(?:le|la|les|un|une|des|du|de|au|aux|en|ce|cet|cette|mon|ma|mes|ton|ta|tes|son|sa|ses)\s+""", RegexOption.IGNORE_CASE),
            "",
        )
        trimmed = trimmed.replace(Regex("""^(?:l'|d')""", RegexOption.IGNORE_CASE), "")
        if (trimmed.length < 3 || trimmed.length > 45) return null
        if (STOP_WORDS.contains(trimmed.lowercase())) return null
        if (FRENCH_COMMON_WORDS.contains(trimmed.lowercase())) return null
        if (trimmed.all { it.isDigit() }) return null
        return trimmed
    }

    private fun extractConcludingHint(text: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val lastLine = lines.lastOrNull() ?: return null

        val hintRegex = Regex("""(?:recommandé de|conseillé de|possible de|alternative consiste à|attention à|pour aller plus loin|concernant)\s+([a-zA-ZÀ-ÿ0-9_\- '’]{4,35})""", RegexOption.IGNORE_CASE)
        val match = hintRegex.find(lastLine)
        return match?.groupValues?.getOrNull(1)?.trim()
    }

    private fun defaultSuggestions(): List<FollowUpSuggestion> {
        return listOf(
            FollowUpSuggestion(
                FollowUpType.DEEP_DIVE,
                "Peux-tu approfondir les points clés et mécanismes de cette réponse ?"
            ),
            FollowUpSuggestion(
                FollowUpType.EXPANSION,
                "Comment mettre en œuvre ces recommandations dans un cas pratique réel ?"
            ),
            FollowUpSuggestion(
                FollowUpType.TRANSVERSAL,
                "Quelles sont les alternatives, compromis et synergies à considérer ?"
            )
        )
    }
}
