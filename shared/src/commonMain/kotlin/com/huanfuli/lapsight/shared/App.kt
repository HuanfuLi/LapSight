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
    var state by remember { mutableStateOf(GpsProbeState.idle()) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(state.isRunning) {
        while (state.isRunning) {
            delay(1_000)
            tick += 1
            state = GpsProbeSimulator.next(state, tick)
        }
    }

    ProbeDashboard(
        state = state,
        onStart = {
            tick = 0
            state = GpsProbeState.started()
        },
        onStop = { state = state.stopped() },
        onReset = {
            tick = 0
            state = GpsProbeState.idle()
        },
    )
}

@Composable
private fun ProbeDashboard(
    state: GpsProbeState,
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
                HeaderPanel(state, Modifier.weight(0.8f), compact = isCompactLandscape)
                MetricsPanel(state, Modifier.weight(1.2f), compact = isCompactLandscape)
                ControlPanel(state, onStart, onStop, onReset, Modifier.weight(0.8f), compact = isCompactLandscape)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(dashboardPadding),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HeaderPanel(state, Modifier.fillMaxWidth())
                MetricsPanel(state, Modifier.fillMaxWidth())
                Spacer(Modifier.weight(1f))
                ControlPanel(state, onStart, onStop, onReset, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun HeaderPanel(
    state: GpsProbeState,
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
            text = "GPS Probe",
            color = Color(0xFF9AA8B8),
            fontSize = if (compact) 14.sp else 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
        Text(
            text = "Closed-course timing aid. Phone GPS accuracy varies; verify before trusting lap data.",
            color = Color(0xFFCED7E2),
            fontSize = if (compact) 11.sp else 13.sp,
            lineHeight = if (compact) 15.sp else 17.sp,
        )
        Spacer(Modifier.height(if (compact) 12.dp else 16.dp))
        Text(
            text = state.fixStatus.label,
            color = state.fixStatus.color,
            fontSize = if (compact) 16.sp else 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun MetricsPanel(
    state: GpsProbeState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
    ) {
        MetricCard("Speed", state.speedKmhLabel, "km/h", emphasized = true, compact = compact)
        Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
            MetricCard("Accuracy", state.accuracyLabel, "m", Modifier.weight(1f), compact = compact)
            MetricCard("Rate", state.updateRateLabel, "Hz", Modifier.weight(1f), compact = compact)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
            MetricCard("Elapsed", state.elapsedLabel, "", Modifier.weight(1f), compact = compact)
            MetricCard("Samples", state.sampleCount.toString(), "", Modifier.weight(1f), compact = compact)
        }
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
                        emphasized && compact -> 42.sp
                        emphasized -> 54.sp
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
    state: GpsProbeState,
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
            onClick = if (state.isRunning) onStop else onStart,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isRunning) "Stop Probe" else "Start Probe")
        }
        Button(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.sampleCount > 0 || state.fixStatus != GpsFixStatus.Idle,
        ) {
            Text("Reset")
        }
        Text(
            text = "Phase 1 uses simulator-backed samples. Real Android/iOS GPS providers plug into this same state model next.",
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
        GpsFixStatus.Simulated -> "SIMULATED GPS"
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
