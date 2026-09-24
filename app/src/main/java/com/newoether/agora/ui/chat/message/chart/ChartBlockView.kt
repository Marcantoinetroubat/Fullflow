package com.newoether.agora.ui.chat.message.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import kotlin.math.abs
import com.newoether.agora.ui.ds.AgoraAlpha

/**
 * Renders a ```chart fenced block (JSON [ChartSpec]) as a native, animated dashboard
 * graph (bar / line / pie). Falls back to the raw code view when the JSON is invalid.
 */
@Composable
fun ChartBlockView(
    code: String,
    codeStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val spec = remember(code) { ChartSpecParser.parse(code) }
    var showCode by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = AgoraAlpha.Handle),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = AgoraAlpha.Disabled),
        ),
    ) {
        Column {
            // â”€â”€ Header : titre + bascule Graphique/Code â”€â”€
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = spec?.title?.takeIf { it.isNotBlank() } ?: "Tableau de bord",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 6.dp),
                )
                Box(modifier = Modifier.weight(1f))
                if (spec != null) {
                    IconButton(onClick = { showCode = !showCode }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = if (showCode) Icons.Default.BarChart else Icons.Default.Code,
                            contentDescription = if (showCode) "Voir le graphique" else "Voir le code",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            if (spec == null || showCode) {
                if (spec == null) {
                    Text(
                        text = "Graphique non rendu â€” JSON de spec invalide",
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
                ChartContent(
                    spec = spec,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun ChartContent(spec: ChartSpec, modifier: Modifier = Modifier) {
    val palette = chartPalette()
    when (spec.chartType) {
        ChartType.BAR -> CartesianChart(spec, isLine = false, palette = palette, modifier = modifier)
        ChartType.LINE -> CartesianChart(spec, isLine = true, palette = palette, modifier = modifier)
        ChartType.PIE -> PieChart(spec, palette = palette, modifier = modifier)
    }
}

@Composable
private fun chartPalette(): List<Color> {
    val cs = MaterialTheme.colorScheme
    return remember(cs.primary, cs.tertiary, cs.secondary, cs.inversePrimary, cs.primaryContainer, cs.tertiaryContainer) {
        listOf(cs.primary, cs.tertiary, cs.secondary, cs.inversePrimary, cs.primaryContainer, cs.tertiaryContainer)
    }
}

@Composable
private fun CartesianChart(
    spec: ChartSpec,
    isLine: Boolean,
    palette: List<Color>,
    modifier: Modifier = Modifier,
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(spec) {
        modelProducer.runTransaction {
            // Vico 2.x BuilderScope: one series() call per data series.
            if (isLine) {
                lineSeries { spec.series.forEach { s -> series(y = s.values) } }
            } else {
                columnSeries { spec.series.forEach { s -> series(y = s.values) } }
            }
        }
    }

    val bottomValueFormatter = remember(spec) {
        CartesianValueFormatter { _, value, _ ->
            spec.labels.getOrElse(value.toInt()) { "" }
        }
    }

    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                if (isLine) rememberLineCartesianLayer() else rememberColumnCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomValueFormatter),
            ),
            modelProducer = modelProducer,
            zoomState = rememberVicoZoomState(zoomEnabled = false),
            modifier = modifier.height(220.dp),
        )
    }

    if (spec.series.size > 1) {
        ChartLegend(
            items = spec.series.mapIndexed { index, s ->
                (s.name.ifBlank { "SÃ©rie ${index + 1}" }) to palette[index % palette.size]
            },
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun PieChart(
    spec: ChartSpec,
    palette: List<Color>,
    modifier: Modifier = Modifier,
) {
    val series = spec.series.firstOrNull() ?: return
    val values = remember(spec) { series.values.map { abs(it) } }
    val total = values.sum().takeIf { it > 0.0 } ?: return

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Canvas(modifier = Modifier.size(140.dp)) {
            var startAngle = -90f
            values.forEachIndexed { index, value ->
                val sweep = (value / total * 360.0).toFloat()
                drawArc(
                    color = palette[index % palette.size],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = Offset.Zero,
                    size = this.size,
                )
                startAngle += sweep
            }
        }
        ChartLegend(
            items = spec.labels.take(values.size).mapIndexed { index, label ->
                val pct = values[index] / total * 100.0
                "$label Â· ${"%.1f".format(pct)} %" to palette[index % palette.size]
            },
        )
    }
}

@Composable
private fun ChartLegend(items: List<Pair<String, Color>>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(8.dp)) { drawCircle(color = color) }
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}
