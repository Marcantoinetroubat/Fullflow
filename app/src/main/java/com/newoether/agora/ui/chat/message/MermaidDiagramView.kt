package com.newoether.agora.ui.chat.message

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import com.newoether.agora.ui.ds.AgoraAlpha

private const val MERMAID_ASSET_URL = "file:///android_asset/mermaid/mermaid_view.html"
private const val MIN_DIAGRAM_HEIGHT_DP = 120
private const val MAX_DIAGRAM_HEIGHT_DP = 440

/** Pure coercion of reported WebView CSS height (px) into a bounded diagram height (dp). JVM-testable. */
internal fun mermaidHeightDp(reportedHeightPx: Int): Int =
    reportedHeightPx.coerceIn(MIN_DIAGRAM_HEIGHT_DP, MAX_DIAGRAM_HEIGHT_DP)

/** Callbacks invoked from the Mermaid WebView Javascript bridge (background thread). */
private class MermaidBridge(
    private val onHeight: (Int) -> Unit,
    private val onRendered: () -> Unit,
    private val onError: (String) -> Unit,
) {
    @JavascriptInterface
    fun onHeight(heightCssPx: Int) = onHeight.invoke(heightCssPx)

    @JavascriptInterface
    fun onRendered() = onRendered.invoke()

    @JavascriptInterface
    fun onError(message: String) = onError.invoke(message)
}

/**
 * Renders a ```mermaid fenced block as an interactive diagram (mindmap, flowchart,
 * sequence, ganttâ€¦) via an offline WebView + bundled mermaid.js. Falls back to the
 * raw code view on syntax errors so a malformed block never breaks the message.
 */
@Composable
fun MermaidDiagramView(
    code: String,
    codeStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    var showCode by rememberSaveable { mutableStateOf(false) }
    var renderError by remember(code) { mutableStateOf<String?>(null) }
    var isRendering by remember(code) { mutableStateOf(true) }
    var reportedHeightPx by remember(code) { mutableIntStateOf(0) }
    var fullscreen by remember { mutableStateOf(false) }

    // Loading timeout: never spin forever if the asset fails to load silently.
    LaunchedEffect(code, isRendering) {
        if (isRendering) {
            kotlinx.coroutines.delay(8000)
            if (isRendering) {
                renderError = "DÃ©lai dÃ©passÃ© â€” le rendu Mermaid n'a pas abouti"
                isRendering = false
            }
        }
    }

    val darkTheme = isSystemInDarkTheme()
    val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = AgoraAlpha.Handle)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = AgoraAlpha.Disabled)

    val heightDp = remember(reportedHeightPx) {
        mermaidHeightDp(reportedHeightPx).dp
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
    ) {
        Column {
            // â”€â”€ Header : titre + bascule Diagramme/Code + plein Ã©cran â”€â”€
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.AccountTree,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "Diagramme Â· Mermaid",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
                Box(modifier = Modifier.weight(1f))
                IconButton(onClick = { showCode = !showCode }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = if (showCode) Icons.Default.AccountTree else Icons.Default.Code,
                        contentDescription = if (showCode) "Voir le diagramme" else "Voir le code",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = { fullscreen = true }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Agrandir le diagramme",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (showCode || renderError != null) {
                if (renderError != null && !showCode) {
                    Text(
                        text = "Diagramme non rendu â€” affichage du code source",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    )
                }
                Text(
                    text = code,
                    style = codeStyle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .horizontalScroll(rememberScrollState())
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth()) {
                    MermaidWebView(
                        code = code,
                        darkTheme = darkTheme,
                        onHeightReported = { reportedHeightPx = it },
                        onRendered = { isRendering = false; renderError = null },
                        onError = { message -> isRendering = false; renderError = message },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heightDp),
                    )
                    if (isRendering) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Rendu du diagrammeâ€¦",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (fullscreen) {
        MermaidFullscreenDialog(
            code = code,
            darkTheme = darkTheme,
            onDismiss = { fullscreen = false },
        )
    }
}

/** Shared WebView renderer, used inline (fixed reported height) and in fullscreen (zoomable). */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MermaidWebView(
    code: String,
    darkTheme: Boolean,
    onHeightReported: (Int) -> Unit,
    onRendered: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
    zoomEnabled: Boolean = false,
    onWebViewReady: (WebView) -> Unit = {},
) {
    // Latest callbacks, so the Javascript bridge always hits fresh Compose state.
    val currentOnHeight by androidx.compose.runtime.rememberUpdatedState(onHeightReported)
    val currentOnRendered by androidx.compose.runtime.rememberUpdatedState(onRendered)
    val currentOnError by androidx.compose.runtime.rememberUpdatedState(onError)
    val pageReady = remember { mutableStateOf(false) }
    // Guards against re-render loops: `update` fires on every parent recomposition,
    // so only re-render when the actual inputs changed.
    val lastRendered = remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    fun renderInto(target: WebView, source: String, dark: Boolean) {
        val quoted = org.json.JSONObject.quote(source)
        target.evaluateJavascript("renderAgoraMermaid($quoted, $dark)", null)
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                // enableSlowWholeDocumentDraw() is called once in AgoraApplication.onCreate().
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.setSupportZoom(zoomEnabled)
                settings.builtInZoomControls = zoomEnabled
                settings.displayZoomControls = false
                addJavascriptInterface(
                    MermaidBridge(
                        onHeight = { h -> post { currentOnHeight(h) } },
                        onRendered = { post { currentOnRendered() } },
                        onError = { msg -> post { currentOnError(msg) } },
                    ),
                    "AgoraMermaid",
                )
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        pageReady.value = true
                        lastRendered.value = code to darkTheme
                        renderInto(view, code, darkTheme)
                    }
                }
                loadUrl(MERMAID_ASSET_URL)
            }.also { onWebViewReady(it) }
        },
        update = { view ->
            val wanted = code to darkTheme
            if (pageReady.value && lastRendered.value != wanted) {
                lastRendered.value = wanted
                renderInto(view, code, darkTheme)
            }
        },
        onRelease = { it.destroy() },
    )
}

/** Fullscreen, zoomable diagram explorer with PNG share. */
@Composable
private fun MermaidFullscreenDialog(
    code: String,
    darkTheme: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    androidx.activity.compose.BackHandler { onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountTree,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Diagramme",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                    )
                    IconButton(
                        onClick = { shareDiagramAsPng(context, webViewRef.value) },
                        enabled = webViewRef.value != null,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Partager en PNG",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fermer",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                MermaidWebView(
                    code = code,
                    darkTheme = darkTheme,
                    onHeightReported = {},
                    onRendered = {},
                    onError = {},
                    zoomEnabled = true,
                    onWebViewReady = { webViewRef.value = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** Snapshots the diagram WebView into a PNG and opens the system share sheet. */
private fun shareDiagramAsPng(context: android.content.Context, webView: WebView?) {
    if (webView == null || webView.width <= 0 || webView.height <= 0) {
        Toast.makeText(context, "Diagramme pas encore prÃªt", Toast.LENGTH_SHORT).show()
        return
    }
    var bitmap: Bitmap? = null
    try {
        bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
        webView.draw(AndroidCanvas(bitmap))
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "diagram_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Partager le diagramme"))
    } catch (_: Exception) {
        Toast.makeText(context, "Export impossible", Toast.LENGTH_SHORT).show()
    } finally {
        bitmap?.recycle()
    }
}
