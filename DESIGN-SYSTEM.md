# Design System — FullFlow / Agora (consolidation v1)

Source unique de vérité : `app/src/main/java/com/newoether/agora/ui/ds/`.
Thème unique : `ui/theme/AgoraTheme` (MaterialKolor, 9 presets, AMOLED, dynamic-color).
`fulllive/ui/theme/` est un alias déprécié vers `AgoraTheme(OCEAN)` + `FullLiveBridge`.

## Tokens (`ui/ds/`)

| Fichier | Contenu |
|---|---|
| `AgoraSpacing.kt` | 2/4/8/12/16/20/24/32 + ScreenHorizontal, CardPadding, RowGap, ItemGap, SectionGap |
| `AgoraRadii.kt` | Xs 8, Sm 12, Md 16, Lg 24, Xl 28, Pill/Full=CircleShape ; Card=Sm, Dialog=Lg, Sheet=Md, Field=Sm. **Interdit `RoundedCornerShape(50)` sans unité.** |
| `AgoraElevation.kt` | 0/1/2/4/6/8 ; CardTonal 1, FabTonal 4, SnackbarShadow 6, SheetShadow 8 |
| `AgoraAlpha.kt` | Disabled 0.38, Hint/Secondary 0.6, Divider 0.12, Scrim 0.32, Handle 0.3, Subtle 0.08 |
| `AgoraDurations.kt` | Fast 220, Medium 400, Sheet snap 80ms / damping 0.9 / stiffness 350 |
| `AgoraBreakpoints.kt` | Compact <600dp, Medium <840dp, Drawer side-by-side ≥600dp, GridAdaptive 160dp |
| `FullLiveBridge.kt` | Cyan/Indigo/Amber/Purple/Rose/Emerald + helpers `fullLiveAccent/Secondary/Tertiary` vers `colorScheme` |

Typographie : `ui/theme/Type.kt` (`Typography` géométrique 1.2 + `ChatType` ~1.15, ancre body 15sp) — ne pas créer d'échelle parallèle.

## Composants canoniques

| Canonique | Remplace / supprime |
|---|---|
| `AgoraButton.kt` : `AgoraIconButton` (48dp min), `AgoraBackButton`, `AgoraPrimaryButton`, `AgoraFab` | `AnimatedActionFab`, `DocumentationFab`, `ShareSelectionFab`, FAB scroll `ChatApp:904`, IconButton 32/34/38/40dp |
| `AgoraCard.kt` : `AgoraCard` + `AgoraSectionLabel` | `SettingsGroup/Item`, `CardSurface`, duplicata `SettingsScreen:406`, ~30 `*Card` ad-hoc |
| `AgoraDialog.kt` : `AgoraDialog` | ~50 `fun *Dialog` (PromptEdit, Citation, ChatRename/Delete, Task pickers, Config, Persona, Mesh, Wand, VideoSlice…) |
| `AgoraBottomSheet.kt` : `AgoraBottomSheet` (design Smooth : handle 36×5, scrim 0.32, shape Md + M3 + `LocalAgoraMotionPolicy`) | `SmoothBottomSheet` custom + `MotionAwareModalBottomSheet` |
| `AgoraFields.kt` : `AgoraTextField`, `AgoraSliderRow`, `AgoraSwitchRow` (Role.Switch), `AgoraRadioRow`, `AgoraSettingsAddItem` | `GenParamSlider`×2, `ContextSliderItem`, `ThinkingControlPanel`, `LocalModelIdleRetentionSlider`, Switch/Radio directs, Outlined 16/12/10dp, BasicTextField customs |
| `AgoraTabs.kt` : `AgoraTabs` (48dp min) | `PillTabSwitcher`, 5× `TabRow` directs, `StudioTabsHeader` |
| `AgoraSearchBar.kt` : `AgoraSearchBar` (56dp, Pill) | `DrawerSearchBar` (44dp), `GoogleDriveSearchBar`, `WebResearchHost` field |
| `AgoraFeedback.kt` : `AgoraSnackbarCard`, `AgoraLoader/Linear`, `AgoraEmptyState`, `AgoraErrorState` | Snackbar inline, `CircularProgressIndicator` directs, empty/error inline (`TasksScreen:255`, `ResearchScreen:315`, `AttachmentThumbnail:101`) |
| `DesignSystemGallery.kt` | Nouvelle galerie « Design System / Components » (à brancher sur Settings Developer) |

## Incohérences corrigées (cette passe)

- `RoundedCornerShape(50)` (px, pas dp) → `CircleShape` / `AgoraRadii.Pill` : `AnimatedActionFab`, `ShareSelectionFab`, `ChatTopBar:613`, `FullScreenMediaViewer`×5, `PdfPageSelectDialog`×2, `TextFileViewer`×2, `VideoSliceDialog`×4, `CitationMessageContent`×2, `RatingForm`.
- `AnimatedActionFab` : `contentDescription=null` → `label` (TalkBack).
- `MainActivity` crash dialog : `IconButton 32dp + icon 16dp + alpha 0.6` → `48dp + 24dp + onSurfaceVariant` plein (contraste + touch target).
- `FullLiveTheme` → wrapper déprécié vers `AgoraTheme(OCEAN)` ; `fulllive Type/Color` → alias vers `ui/theme` + `FullLiveBridge`.
- `StudioTokens` : conservé comme alias visuel studios, à migrer vers `Agora*` (Pill 32→CircleShape, Result 22→Lg 24, Sheet 16→Md).

## Accessibilité détectée (reste à traiter)

- 288× `contentDescription=null` dont cliquables (`ChatTopBar` recherche 38dp, `SecondBrainHost` 32/34dp, `MeshStudioDialog`, `AttachmentAddMenu`) → labels + `Role`.
- Touch targets <48dp : 512× `size(16/18/20/24/32/36/40dp)` ; aucun `minimumInteractiveComponentSize`. Seules exceptions 48dp (`WandUi`, `Transcripteur`, `ConversationScreen`).
- Contrastes : `White.copy(0.2–0.6)` sur `0xFF0C1015/0xFF11171F` + micro 9/10/11sp (`SecondBrainHost:540`, `ResearchScreen:442`, `MeshStudioDialog:112`, `ChatTopBar` tokenSubtitle) → passer à `onSurface/onSurfaceVariant` + ≥12sp.
- Focus/clavier : `clickable` sans `Role/indication`, 1 seul `toggleable(Role.Switch)`, 0 `onPreviewKeyEvent/moveFocus/focusGroup`, `clearFocusOnTap` casse TalkBack.
- Sémantique : seuls îlots `CitationMessageContent` (`Role.Button`+description), `MessageBubbleAssets` (`heading()`), `MotionAwareProgressIndicators` (`progressBarRangeInfo`).

## Responsive détecté (reste à traiter)

- 0 `WindowSizeClass`, 0 `ListDetail/ SupportingPane / NavigationSuite` ; `BoxWithConstraints` détourné (pas de seuils) ; 0 gestion orientation/foldable.
- `fulllive/screens/*`, `MeshStudioDialog`, `ConfigDialog`, `GeminiVideoStudioComposer` sans `WindowInsets/imePadding` (vs OK `ChatApp`, `SettingsScaffold`, `WandUi`, `ResearchScreen`).
- Fixes : `GoogleWorkspaceDashboard:797` (180dp), `ResearchScreen:445` (130×54dp), `ChatTopBar:312` (`TITLE_CAPSULE_MAX_WIDTH` manuel), `ChatDrawerHost:188` seuils maison → `AgoraBreakpoints` + `weight` + `GridCells.Adaptive(160dp)`.

## Performance

- Aucune lib visuelle ajoutée. Réutilise `Material3`, `LocalAgoraMotionPolicy`, latest-wins renderer, `MotionAware*` (stationnaire si reduced-motion).
- Reste : dédupliquer `AnimatedBlobBackground`, gradients `ResearchScreen:80`, `StudioTokens.ScreenBackground`, `LatexRenderer`, thumbs Coil (`AttachmentThumbnail`, `MediaLoadPresentation`).

## Validation

- [x] `RoundedCornerShape(50)` résiduel = 0 (code).
- [x] `IconButton(.*size(32/34dp))` = 0 (passés à 48dp + icône 24dp : `SecondBrainHost` pin/edit/delete, `GeminiVideoStudioComponents` save/share, `FullFlowGenMailDialog` close, `SettingsSandboxPage` delete, `MainActivity` copy).
- [x] `DesignSystemGallery` branchée : `SettingsDeveloperPage` → item « Design System / Components » + `BackHandler`.
- [x] Dialogs migrés : disable developer (`AlertDialog`→`AgoraDialog` destructif), `PromptEditDialog` (`AlertDialog`+16dp→`AgoraDialog`+`AgoraTextField`).
- [x] `StudioTokens` : shapes → `AgoraRadii` (Pill/Dialog/Sheet), bordure → `Divider 0.12`, accents → `FullLiveBridge`.
- [x] Sliders normalisés (tokens + labels TalkBack) : `GenParamSlider` Float/Int (`SettingsGenerationPage`), `ContextSliderItem` (`SettingsContextPage`), radio `Role.RadioButton`.
- [x] Switch pilote : `SettingsDeveloperPage` double-toggle supprimé (`Switch onCheckedChange=null`, ligne `clickable` seule propriétaire).
- [x] Search normalisée : `DrawerSearchBar` 44→48dp, clear/loader 28/18→48/20dp, hint→`AgoraAlpha.Hint`, search icon labellisé, `surfaceColorAtElevation(8dp)`→token.
- [x] Tabs : `PillTabSwitcher` canonique segmented (tokens, 44→48dp, tween→`AgoraDurations.Fast`) vs `AgoraTabs` souligné M3 ; `SettingsAgentsPage` `TabRow`+13sp→`AgoraTabs`.
- [x] `ThinkingControlPanel` : alpha→`AgoraAlpha.Disabled`, espacements→tokens, icônes labellisées, chevron→`AgoraDurations.Medium`.
- [x] `OpenAiServiceTierControlPanel` : idem (2dp→`Xxs`, 4→`Xs`, 8→`Sm`, 16→`Lg`, 32→`Xxxl`, 0.38→`Disabled`, icônes labellisées, import `dp` supprimé).
- [x] `CustomEndpointProtocolSelector` + 7 usages `PillTabSwitcher` : héritent des tokens (aucune migration manuelle).
- [x] `GoogleDriveSearchBar` : shape→`AgoraRadii.Md`, bordure→`Divider`, `14→Lg`, `10→Sm`, hint `0.45→Hint`, clear 28→48dp (icône 16→20dp, teinte pleine).
- [x] `WebResearchHost` : retour 38→48dp (fond→`Subtle`, icône 18→24dp), `16/12→Lg/Md`, sous-titre 10→12sp + `Hint`, champ : loupe labellisée, shape→`AgoraRadii.Lg`, bordure→`Divider`, fonds→`Subtle`, placeholder/effacer→`Hint`, loader 18→20dp. Accent `0xFF4FC3F7` conservé (custom dark mono-fichier, à themer plus tard).
- [x] `ResearchScreen` : `SourceChipCard` (shape/bordure→tokens, badge 14→20dp, 9sp→11sp, titre alpha pleine + `weight`, url→`Hint`), `DetailedResultCard` (shape→`Sm`, fonds/bordures→`Subtle`, url 10→12sp + `Hint`, desc→`Hint`, accent→`FullLiveBridge.Cyan`), header section→`Hint`, carte erreur (`Pressed`/`Handle`, dismiss 24→48dp labellisé, icônes 16/14→20dp labellisées).
- [x] `SecondBrainHost` : ligne note `clickable(Role.Button, onClickLabel="Lire la note")`, tags 10→12sp, filePath 9→11sp + `0.3→0.6`.
- [x] `ResearchScreen` send : 40→48dp, accent→`FullLiveBridge.Cyan`, fond off→`Subtle`, teinte off `0.35→Hint`, icône 16→20dp.
- [x] `AttachmentThumbnail` : 7× `RoundedCornerShape(8dp)`→`AgoraRadii.Xs` (import supprimé), PDF `0.15→Pressed`, fichier `0.4→Hint`.
- [x] `ChatDrawerContent` vide : `24dp`→`Xxl` + `liveRegion(Polite)`.
- [x] `ChatTopBar` : recherche prev/next + dismiss + drawer/back + new/more 38/44→48dp labellisés, spacers 5→`Xs`, compteur 6→`Xs`, menu shape→`Sm`, Hub→`FullLiveBridge.Cyan`, titre `tween(200)→Fast`. Icônes `DropdownMenuItem` null conservées (texte présent, décoratives).
- [x] `ChatBottomBar` : expand/collapse 40→48dp + teinte pleine, `2dp→Level2`, `4dp→Xs`, pill `RoundedCornerShape(100)` px-bug→`CircleShape` + `10dp→Sm`/`8/4→Sm/Xs`.
- [x] `MeshHUDIndicator` : shape→`Md`, bordure `1dp→CardTonal`, alphas→tokens, `clickable(Role.Button, "Ouvrir le studio Mesh")`, spacers→tokens, ❌ 9→11sp (import `RoundedCornerShape` supprimé).
- [x] Sélecteurs de branches : 2× `RoundedCornerShape(100)` px-bug→`CircleShape`, boutons 24/28→36dp (convention `LocalMinimumInteractiveComponentSize` du fichier) labellisés, fond `0.5→Hint`, paddings→tokens (`AssistantMessageContent`, `UserMessageBubble`).
- [x] `MessageItem` pill compact : more 32→48dp (icône 18→20dp, label `R.string.more` déjà présent).
- [x] `ChatDrawerContent` : 3 boutons pleine largeur 42→48dp (Tasks/New/Settings, icônes décoratives null OK car texte présent).
- [x] `AttachmentAddMenu` : ancre + 32→48dp (icône 16→24dp), menu shape→`AgoraRadii.Md`, 6× spacer→`Md`. Icônes menu null OK (texte présent).
- [x] Contrastes textes : `SecondBrainHost` (fichier + brain url 9→11sp + `Hint`, prio 8→10sp, header→`Hint`), `WebResearchHost` (vide 10→12sp + `Hint`, url 10→12sp + `Hint`, paddings→tokens), `MeshStudioDialog` (badge shape/paddings→tokens), `SettingsAgentsPage` (specialty 10→12sp, preview 9→11sp + `Hint`, chaine 10→12sp, boucles 9→11sp + `Hint`, edit/delete 30→48dp labellises). Bordures/ripples/fonds non-texte conserves — fonctionnels.
- [x] Boutons 30dp restants : `MermaidDiagramView` x2 + `ChartBlockView` (30 vers 48dp, icones 16 vers 20dp, deja labellises), `SettingsAgentsPage` pipeline edit/delete (30 vers 48dp labellises). Seul `RoundedCornerShape(100.dp)` avec unite reste (legitime).
- [x] Fin des 9sp : Mesh (fingerprint + duree transit 9 vers 10sp), badges `Cle prete` image/video 9 vers 10sp, categorie style 9 vers 10sp, reader type/taille 9 vers 11sp + `Hint`, TTS subtitle 9 vers 11sp + `Hint`. Zero `fontSize = 9.sp`, zero `IconButton 30/32/34` (reader play 34 vers 48dp labellise + delete 0.25 vers `Hint` 48dp).
- [x] Nav `ChatApp` : labels 10 vers 12sp (M3), unselected 0.4 vers `Hint`, selected vers `FullLiveBridge.Cyan`. `AgentsPage` picker specialty 10 vers 12sp. `PersonasPage` : meta 10 vers 11sp, edit/duplicate/delete 28 vers 48dp (icones 14 vers 20dp), delete `AlertDialog` vers `AgoraDialog` destructif, chips 10 vers 11sp.
- [x] Badges 10sp corps : follow-up + citation + deliverable + home (10 vers 11sp), citation lh 10 vers 15sp, podcaster hint 10 vers 12sp + `Hint`, home send 30 vers 48dp (icones 16 vers 20dp). 10sp restants (~40) = badges Bold ancillaires sur fond teinte + monos metadata : tolere, a documenter.
- [x] Responsive tokens : `ChatDrawerHost.CHAT_APP_WIDTH_THRESHOLD` vers `AgoraBreakpoints.CompactMax` (960dp cote-a-cote inchange), `HomeScreen` grille `160dp` vers `GridAdaptiveMin`. `WindowSizeClass`/panes 2-colonnes restent un chantier feature (hors consolidation).
- [x] `SettingsScaffold` titre auto-fit : insets 24/16/12/8/4 vers tokens (mesure textuelle gardee, fonctionnelle). `roundToPx`/scroll mecaniques hors perimetre.
- [x] Tri `contentDescription=null` (~209 restants) : decoratifs (`leadingContent`/`DropdownMenuItem` avec texte, `toggleable`+`Switch(null)` dans `AppearancePage`) = OK TalkBack ; zero `IconButton` sans label (1 faux positif `editingPersona = null` retire).
- [x] Formes groupees deduplees : `AgoraRadii.stackedShape()` + `GroupJoint` 5dp documente (seul 5dp legitime) ; `TasksScreen`/`TaskEditorPage`/`SettingsScreen` migrés, locaux supprimes. Source unique verifiee.
- [x] Empty states groupees (`TasksScreen`, `TaskEditorPage`) : structure carte conservee (continuite stackedShape), icones/descriptions 0.4/0.6 vers `Hint`. `AgoraEmptyState` reserve aux vides plein-ecran.
- [x] `MainNavigationSnackbarHost` : tweens vers `Medium`, paddings vers tokens, shape/shadow vers `Sm`/`SnackbarShadow`, dismiss 28 vers 48dp (icone 18 vers 20dp, deja labellise). Logique debounce/timeout a11y gardee.
- [x] BottomSheet : `CitationSourcesBottomSheet` migree vers `AgoraBottomSheet` (header/divider/liste en slots Column, 24/12/8dp vers tokens, `activateThenDismiss`). `SmoothBottomSheet` reserve a `SegmentDetailSheet` (back-stack interne + `contentAtTop`, raison fonctionnelle documentee en KDoc) ; nouveaux usages interdits.
- [x] BottomSheet : variable picker `SystemPromptEditorPage` migre vers `AgoraBottomSheet` (paddings tokens). `MotionAwareModalBottomSheet` reserve aux hide() programmatiques (ImageActions, OverlayHost x2, Prompts, Skills) ; KDoc `AgoraBottomSheet` clarifie la repartition 3-voies.
- [x] Boutons 36dp : studio image x4 + Loop stop + queue remove vers 48dp (deja labellises) ; Wand close 36 vers 48dp ; `WandAccent` ponte vers `FullLiveBridge.Purple`. Seuls les switchers de branches restent a 36dp (convention compacte documentee).
- [x] `SettingsAddItem` : `clickable(Role.Button)`, disabled vers `Disabled`, paddings/icone vers tokens (icone 18 vers 20dp, 56dp min garde). `AgoraSettingsAddItem` (CTA isole) vs `SettingsAddItem` (ligne groupee) : roles distincts documentes.
- [x] 3e copie du pattern groupe (`SettingsModelsPage` Full/Top/Bottom/MidRounded) centralisee sur `AgoraRadii` ; `CardSurface`/`SectionLabel` 100% tokens (import `dp` supprime, top 36 vers `Xxxl`). `Flat*` gardes (prolongement header anime, fonctionnel).
- [x] `SettingsProviderDetailPage` : delete provider vers `AgoraDialog` destructif, `LocalModelIdleRetentionSlider` (5e copie) normalise tokens + label TalkBack. Tous les sliders partagent desormais les memes tokens.
- [x] `SettingsProviderDetailPage` : gguf-erreur + delete-model + delete-key vers `AgoraDialog` (4 `AlertDialog` formulaires complexes gardes : add/edit model, key, rename).
- [x] `AgoraTextField` etendu (`visualTransformation`, defaut None) ; dialog cle API migre (`AlertDialog`+2x16dp vers `AgoraDialog`+`AgoraTextField`, `8dp` vers `Sm`). Restent les 2 formulaires modeles numeriques (params `keyboardOptions`/`isError` a ajouter au canonique plus tard).
- [x] `SettingsProviderDetailPage` : 16x `RoundedCornerShape(16dp)` vers `Field`, 3x `5dp` vers `GroupJoint`, 3x menus `12dp` vers `Sm` (import `RoundedCornerShape` supprime). `tonalElevation 16dp` menus garde (fonctionnel).
- [ ] Captures 360/600/840dp + fontScale 1.3 + RTL (pas de `build.ps1`/SDK dans cette distribution).
- [ ] `build.ps1` (gate release : SDK 36, JDK 21, `verifyKotlinFileSize` 999 lignes) + tests `ColorSchemeTest`, `UserMessageTypographyTest`.
- [ ] Migration écran par écran : Chat → Settings → Studios/Wand/Mesh → fulllive screens (remplacer hex/dp/alpha bruts par tokens, `Agora*` par famille).

## Principe directeur

Une fonctionnalité = une représentation visuelle. Tout écart (ex. BottomSheet Smooth vs M3) doit avoir une raison fonctionnelle explicite, sinon il rejoint le canonique.
