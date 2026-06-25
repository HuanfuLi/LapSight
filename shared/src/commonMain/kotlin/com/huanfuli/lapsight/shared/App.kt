package com.huanfuli.lapsight.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanfuli.lapsight.shared.lap.DemoLapSession
import com.huanfuli.lapsight.shared.lap.LapDashState
import com.huanfuli.lapsight.shared.lap.SectorStatus
import com.huanfuli.lapsight.shared.lap.SectorSummary
import kotlinx.coroutines.delay

@Composable
@Preview
fun App() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF05070A),
            surface = Color(0xFF101722),
            primary = Color(0xFF62E3FF),
            secondary = Color(0xFFFFD166),
        )
    ) {
        LapSightApp()
    }
}

@Composable
fun LapSightApp() {
    // The demo session owns the lap engine; the UI only renders dash state and
    // advances the replay on a timer. Real GPS providers will later replace the
    // replay sample source without changing this UI.
    val session = remember { DemoLapSession() }
    var dash by remember { mutableStateOf(session.dashState) }

    LaunchedEffect(dash.isRunning) {
        while (dash.isRunning && !session.isFinished) {
            delay(700)
            dash = session.tick()
        }
    }

    LapDashboard(
        dash = dash,
        onStart = {
            session.start()
            dash = session.dashState
        },
        onStop = {
            session.stop()
            dash = session.dashState
        },
        onReset = {
            session.reset()
            dash = session.dashState
        },
    )
}

@Composable
private fun LapDashboard(
    dash: LapDashState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeContentPadding()
    ) {
        val isLandscape = maxWidth > maxHeight
        val isCompactLandscape = isLandscape && maxHeight < 520.dp
        val dashboardPadding = if (isCompactLandscape) 12.dp else 20.dp
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(dashboardPadding),
                horizontalArrangement = Arrangement.spacedBy(if (isCompactLandscape) 12.dp else 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderPanel(dash, Modifier.weight(0.9f), compact = isCompactLandscape)
                LapMetricsPanel(dash, Modifier.weight(1.3f), compact = isCompactLandscape)
                ControlPanel(dash, onStart, onStop, onReset, Modifier.weight(0.9f), compact = isCompactLandscape)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(dashboardPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HeaderPanel(dash, Modifier.fillMaxWidth())
                LapMetricsPanel(dash, Modifier.fillMaxWidth())
                Spacer(Modifier.weight(1f))
                ControlPanel(dash, onStart, onStop, onReset, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun HeaderPanel(
    dash: LapDashState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(modifier = modifier) {
        Text(
            text = "LapSight",
            color = MaterialTheme.colorScheme.primary,
            fontSize = if (compact) 28.sp else 34.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = "Lap Timing",
            color = Color(0xFF9AA8B8),
            fontSize = if (compact) 14.sp else 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
        Text(
            text = "Closed-course timing aid. Phone GPS accuracy varies; this is not pro-grade timing. Verify before trusting lap data.",
            color = Color(0xFFCED7E2),
            fontSize = if (compact) 11.sp else 13.sp,
            lineHeight = if (compact) 15.sp else 17.sp,
        )
        Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
        Text(
            text = dash.courseName.uppercase(),
            color = MaterialTheme.colorScheme.secondary,
            fontSize = if (compact) 13.sp else 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = dash.fixStatus.label,
            color = dash.fixStatus.color,
            fontSize = if (compact) 15.sp else 17.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LapMetricsPanel(
    dash: LapDashState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
    ) {
        MetricCard("Current Lap", dash.currentLapLabel, "", emphasized = true, compact = compact)
        Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
            MetricCard("Last", dash.lastLapLabel, "", Modifier.weight(1f), compact = compact)
            MetricCard("Best", dash.bestLapLabel, "", Modifier.weight(1f), compact = compact)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
            MetricCard("Laps", dash.lapCountLabel, "", Modifier.weight(1f), compact = compact)
            MetricCard("Speed", dash.speedKmhLabel, "km/h", Modifier.weight(1f), compact = compact)
            MetricCard("Accuracy", dash.accuracyLabel, "m", Modifier.weight(1f), compact = compact)
        }
        SectorReadout(dash, compact = compact)
    }
}

@Composable
private fun SectorReadout(
    dash: LapDashState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(if (compact) 12.dp else 16.dp)) {
            Text(
                text = "SECTORS",
                color = Color(0xFF7E8DA0),
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            if (dash.sectorSummaries.isEmpty()) {
                Text(
                    text = "No sectors on this course.",
                    color = Color(0xFF9AA8B8),
                    fontSize = if (compact) 12.sp else 14.sp,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)) {
                    dash.sectorSummaries.forEach { sector ->
                        SectorChip(sector, compact = compact)
                    }
                }
            }
            dash.latestSectorLabel?.let { label ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Latest: $label ${dash.latestSplitLabel}",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = if (compact) 12.sp else 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SectorChip(
    sector: SectorSummary,
    compact: Boolean = false,
) {
    Column {
        Text(
            text = sector.name.uppercase(),
            color = Color(0xFF9AA8B8),
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (sector.status == SectorStatus.Crossed) sector.splitLabel else "--:--.---",
            color = if (sector.status == SectorStatus.Crossed) Color.White else Color(0xFF5E6B7A),
            fontSize = if (compact) 14.sp else 18.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    compact: Boolean = false,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(if (compact) 12.dp else 16.dp)) {
            Text(
                text = label.uppercase(),
                color = Color(0xFF7E8DA0),
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    color = Color.White,
                    fontSize = when {
                        emphasized && compact -> 40.sp
                        emphasized -> 52.sp
                        compact -> 22.sp
                        else -> 28.sp
                    },
                    fontWeight = FontWeight.Black,
                )
                if (unit.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = unit,
                        color = Color(0xFF9AA8B8),
                        fontSize = when {
                            emphasized && compact -> 16.sp
                            emphasized -> 18.sp
                            compact -> 12.sp
                            else -> 13.sp
                        },
                        modifier = Modifier.padding(
                            bottom = when {
                                emphasized && compact -> 8.dp
                                emphasized -> 10.dp
                                compact -> 4.dp
                                else -> 5.dp
                            }
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlPanel(
    dash: LapDashState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
    ) {
        Button(
            onClick = if (dash.isRunning) onStop else onStart,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (dash.isRunning) "Stop Timing" else "Start Timing")
        }
        Button(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth(),
            enabled = dash.lapCount > 0 || dash.currentLapMillis != null || dash.isRunning,
        ) {
            Text("Reset")
        }
        Text(
            text = "Phase 2 runs the clean-room lap engine on deterministic replay samples. Real Android/iOS GPS providers plug into the same engine next.",
            color = Color(0xFF7E8DA0),
            fontSize = if (compact) 10.sp else 12.sp,
            lineHeight = if (compact) 14.sp else 16.sp,
        )
    }
}

private val GpsFixStatus.label: String
    get() = when (this) {
        GpsFixStatus.Idle -> "IDLE"
        GpsFixStatus.Acquiring -> "ACQUIRING"
        GpsFixStatus.Simulated -> "SIMULATED REPLAY"
        GpsFixStatus.Live -> "LIVE GPS"
        GpsFixStatus.Degraded -> "DEGRADED FIX"
        GpsFixStatus.Unavailable -> "UNAVAILABLE"
    }

private val GpsFixStatus.color: Color
    get() = when (this) {
        GpsFixStatus.Idle -> Color(0xFF9AA8B8)
        GpsFixStatus.Acquiring -> Color(0xFFFFD166)
        GpsFixStatus.Simulated -> Color(0xFF62E3FF)
        GpsFixStatus.Live -> Color(0xFF8CFF9B)
        GpsFixStatus.Degraded -> Color(0xFFFFB84D)
        GpsFixStatus.Unavailable -> Color(0xFFFF6B6B)
    }
