// Directory: ui/review — package stays `shared.ui` (see ReviewScreen.kt note).
package com.huanfuli.lapsight.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huanfuli.lapsight.shared.lap.formatLapTime
import com.huanfuli.lapsight.shared.review.LapSectorTableMode
import com.huanfuli.lapsight.shared.review.LapSectorTableModel
import com.huanfuli.lapsight.shared.review.formatSectorTime
import com.huanfuli.lapsight.shared.ui.components.TimingText

/**
 * Lap × Sector timing table (SESS-02): one row per lap, sector columns that
 * sum to the lap time, per-column bests in motorsport purple, and BEST/OPT
 * summary rows (OPT = optimal lap from best sectors, V2 sessions only).
 * Lap rows select the lap that the telemetry replay and trace highlight.
 */
@Composable
internal fun LapSectorTableView(
    table: LapSectorTableModel,
    selectedLapNumber: Int?,
    onSelectLap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (table.rows.isEmpty()) return
    val spacing = LapSightTheme.spacing
    val bestColor = LapSightTheme.colors.traceBestLap
    val neutralColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    // Beyond 3 sector columns the table scrolls horizontally on fixed widths;
    // at 3 or fewer it stretches to the card width.
    val scrolling = table.columns.size > 3

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (scrolling) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
    ) {
        // Header row: LAP | S1 … Sn | TIME.
        TableRow(scrolling = scrolling, minHeight = HeaderRowHeight) {
            HeaderCell("LAP", Modifier.width(LapColumnWidth), TextAlign.Start)
            table.columns.forEach { column ->
                HeaderCell(column.label, sectorCellModifier(scrolling), TextAlign.End)
            }
            HeaderCell("TIME", timeCellModifier(scrolling), TextAlign.End)
        }

        table.rows.forEach { row ->
            val selected = row.lapNumber == selectedLapNumber
            TableRow(
                scrolling = scrolling,
                minHeight = LapRowHeight,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        } else {
                            Color.Transparent
                        },
                    )
                    .clickable { onSelectLap(row.lapNumber) },
            ) {
                Text(
                    text = row.lapNumber.toString(),
                    color = if (selected) MaterialTheme.colorScheme.primary else mutedColor,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(LapColumnWidth),
                )
                row.cells.forEachIndexed { index, cell ->
                    val isColumnBest = cell != null && cell == table.columnBests[index]
                    TimingText(
                        text = cell?.formatSectorTime() ?: "--",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isColumnBest) bestColor else neutralColor,
                        textAlign = TextAlign.End,
                        modifier = sectorCellModifier(scrolling),
                    )
                }
                val isBestLap = row.lapMillis == table.bestLapMillis
                TimingText(
                    text = row.lapMillis.formatLapTime(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isBestLap) bestColor else neutralColor,
                    textAlign = TextAlign.End,
                    modifier = timeCellModifier(scrolling),
                )
            }
        }

        if (table.columns.isNotEmpty()) {
            HorizontalDivider(color = LapSightTheme.colors.cardBorder)
            // BEST: fastest observed value per column, fastest lap at the end.
            TableRow(scrolling = scrolling, minHeight = SummaryRowHeight) {
                Text(
                    text = "BEST",
                    color = mutedColor,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(LapColumnWidth),
                )
                table.columnBests.forEach { best ->
                    TimingText(
                        text = best?.formatSectorTime() ?: "--",
                        style = MaterialTheme.typography.bodySmall,
                        color = neutralColor,
                        textAlign = TextAlign.End,
                        modifier = sectorCellModifier(scrolling),
                    )
                }
                TimingText(
                    text = table.bestLapMillis.formatLapTime(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = neutralColor,
                    textAlign = TextAlign.End,
                    modifier = timeCellModifier(scrolling),
                )
            }
            table.optimalLapMillis?.let { optimal ->
                // OPT: the optimal lap — all best sectors strung together.
                TableRow(scrolling = scrolling, minHeight = SummaryRowHeight) {
                    Text(
                        text = "OPT",
                        color = mutedColor,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(LapColumnWidth),
                    )
                    table.columns.forEach { _ ->
                        Text(text = "", modifier = sectorCellModifier(scrolling))
                    }
                    TimingText(
                        text = optimal.formatLapTime(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = bestColor,
                        textAlign = TextAlign.End,
                        modifier = timeCellModifier(scrolling),
                    )
                }
            }
        }
    }
    if (table.mode == LapSectorTableMode.LegacyCumulative) {
        Text(
            text = "Splits are cumulative from the lap start (recorded before sector intervals).",
            color = mutedColor,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TableRow(
    scrolling: Boolean,
    minHeight: Dp,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .then(if (scrolling) Modifier else Modifier.fillMaxWidth())
            .heightIn(min = minHeight)
            .padding(horizontal = LapSightTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier, textAlign: TextAlign) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall,
        textAlign = textAlign,
        maxLines = 1,
        modifier = modifier,
    )
}

private fun RowScope.sectorCellModifier(scrolling: Boolean): Modifier =
    if (scrolling) Modifier.width(SectorColumnWidth) else Modifier.weight(1f)

private fun RowScope.timeCellModifier(scrolling: Boolean): Modifier =
    if (scrolling) Modifier.width(TimeColumnWidth) else Modifier.weight(1.35f)

private val LapColumnWidth = 40.dp
private val SectorColumnWidth = 84.dp
private val TimeColumnWidth = 104.dp

// Lap rows are tap targets (>=48dp); header and BEST/OPT summary rows are not.
private val LapRowHeight = 48.dp
private val HeaderRowHeight = 28.dp
private val SummaryRowHeight = 36.dp
