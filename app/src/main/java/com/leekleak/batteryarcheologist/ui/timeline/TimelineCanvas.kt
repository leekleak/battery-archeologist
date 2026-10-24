package com.leekleak.batteryarcheologist.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leekleak.batteryarcheologist.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

private object TimelineCanvasDimens {
    val Gutter = 80.dp
    val RightPadding = 8.dp
    val AxisBottom = 28.dp
    val BatteryTop = 30.dp
    val BatteryBottom = 84.dp
    val RowsGap = 8.dp
    val RowHeight = 40.dp
    val BottomPadding = 6.dp
    val MinTickSpacing = 84.dp

    fun rowsTop(hasBattery: Boolean): Dp =
        if (hasBattery) BatteryBottom + RowsGap else AxisBottom + 2.dp

    fun height(rowCount: Int, hasBattery: Boolean): Dp =
        rowsTop(hasBattery) + RowHeight * rowCount.toFloat() + BottomPadding
}

private class TimelineMetrics(density: Density) {
    val gutter = with(density) { TimelineCanvasDimens.Gutter.toPx() }
    val rightPadding = with(density) { TimelineCanvasDimens.RightPadding.toPx() }
    val axisLabelTop = with(density) { 1.dp.toPx() }
    val axisBottom = with(density) { TimelineCanvasDimens.AxisBottom.toPx() }
    val batteryTop = with(density) { TimelineCanvasDimens.BatteryTop.toPx() }
    val batteryBottom = with(density) { TimelineCanvasDimens.BatteryBottom.toPx() }
    val rowsTopWithBattery = with(density) {
        (TimelineCanvasDimens.BatteryBottom + TimelineCanvasDimens.RowsGap).toPx()
    }
    val rowsTopWithoutBattery = with(density) {
        (TimelineCanvasDimens.AxisBottom + 2.dp).toPx()
    }
    val rowHeight = with(density) { TimelineCanvasDimens.RowHeight.toPx() }
    val labelX = with(density) { 4.dp.toPx() }
    val laneSpacing = with(density) { 5.dp.toPx() }
    val barHeight = with(density) { 4.dp.toPx() }
    val minBarWidth = with(density) { 2.dp.toPx() }
    val instantHalfHeight = with(density) { 3.5.dp.toPx() }
    val batteryStroke = with(density) { 2.dp.toPx() }
    val reportStroke = with(density) { 1.5.dp.toPx() }
    val reportDot = with(density) { 3.dp.toPx() }
    val cursorStroke = with(density) { 1.5.dp.toPx() }
    val cursorDot = with(density) { 4.dp.toPx() }
    val plotCorner = with(density) { 8.dp.toPx() }
    val minTickSpacing = with(density) { TimelineCanvasDimens.MinTickSpacing.toPx() }

    fun rowsTop(hasBattery: Boolean): Float =
        if (hasBattery) rowsTopWithBattery else rowsTopWithoutBattery
}

private data class TimelineTextStyles(
    val axis: TextStyle,
    val row: TextStyle,
)

private class RenderTick(val x: Float, val label: TextLayoutResult)

private class RenderBar(
    val startX: Float,
    val endX: Float,
    val lane: Int,
    val instant: Boolean,
    val alpha: Float,
)

private class RenderRow(
    val group: TimelineGroup,
    val bars: List<RenderBar>,
    val centerY: Float,
    val laneCount: Int,
)

private class BatteryPaths(
    val line: Path,
    val fill: Path,
)

private class TimelineRender(
    val viewport: TimelineViewport,
    val plotLeft: Float,
    val plotRight: Float,
    val plotWidth: Float,
    val axisBottom: Float,
    val batteryTop: Float,
    val batteryBottom: Float,
    val rowsTop: Float,
    val rowHeight: Float,
    val plotBottom: Float,
    val hasBattery: Boolean,
    val ticks: List<RenderTick>,
    val rows: List<RenderRow>,
    val rowLabels: List<TextLayoutResult>,
    val batteryLabel: TextLayoutResult,
    val battery: BatteryPaths?,
    val reportX: Float?,
) {
    fun xFor(timeMs: Long): Float =
        plotLeft + viewport.fraction(timeMs) * plotWidth
}

/**
 * Timeline canvas with a draggable inspection cursor.
 *
 * The chart uses a fixed full-report viewport; horizontal drags move the
 * cursor and vertical drags are left to the parent scroll container, so the
 * timeline can live in an ordinary vertically scrolling page.
 *
 * Every value that depends on the data or cursor is built once in a `remember`
 * block. The draw pass itself only draws precomputed values.
 */
@Composable
internal fun TimelineCanvas(
    data: TimelineData,
    cursorMs: Long?,
    onCursorMsChange: (Long) -> Unit,
    palette: TimelinePalette,
    modifier: Modifier = Modifier,
) {
    val viewport = data.initialViewport
    val hasBattery = data.batteryPoints.isNotEmpty()
    val canvasHeight = TimelineCanvasDimens.height(data.rows.size, hasBattery)

    val density = LocalDensity.current
    val metrics = remember(density) { TimelineMetrics(density) }
    val textMeasurer = rememberTextMeasurer()
    val typography = MaterialTheme.typography
    val textStyles = remember(typography, palette) {
        TimelineTextStyles(
            axis = typography.labelSmall.copy(
                fontSize = 10.sp,
                color = palette.axisText,
            ),
            row = typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = palette.rowText,
            ),
        )
    }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val latestOnCursorMsChange by rememberUpdatedState(onCursorMsChange)
    val latestSize by rememberUpdatedState(canvasSize)

    val rowLabels = ArrayList<String>(data.rows.size)
    for (row in data.rows) {
        rowLabels.add(stringResource(row.group.labelRes))
    }
    val batteryLabel = stringResource(R.string.timeline_group_battery)

    val render = remember(
        data,
        viewport,
        canvasSize,
        palette,
        textStyles,
        metrics,
        textMeasurer,
        rowLabels,
        batteryLabel,
    ) {
        buildTimelineRender(
            data = data,
            viewport = viewport,
            widthPx = canvasSize.width.toFloat(),
            metrics = metrics,
            styles = textStyles,
            textMeasurer = textMeasurer,
            rowLabels = rowLabels,
            batteryLabel = batteryLabel,
        )
    }

    val cursorX = remember(cursorMs, canvasSize, viewport, metrics) {
        cursorMs
            ?.takeIf { it in viewport.startMs..viewport.endMs }
            ?.let { timeMs ->
                val plotWidth = canvasSize.width - metrics.gutter - metrics.rightPadding
                if (plotWidth <= 1f) {
                    null
                } else {
                    metrics.gutter + viewport.fraction(timeMs) * plotWidth
                }
            }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(canvasHeight)
            .testTag("timeline_canvas")
            .onSizeChanged { canvasSize = it }
            .pointerInput(data, metrics) {
                fun timeAt(x: Float, size: IntSize): Long {
                    val plotWidth = size.width - metrics.gutter - metrics.rightPadding
                    if (plotWidth <= 1f) return viewport.startMs
                    val fraction = ((x - metrics.gutter) / plotWidth).coerceIn(0f, 1f)
                    return viewport.startMs +
                        (fraction * viewport.spanMs).roundToLong()
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val size = latestSize
                    latestOnCursorMsChange(timeAt(down.position.x, size))

                    // Only claim horizontal movement. Vertical drags are left
                    // unconsumed so the enclosing page can scroll normally.
                    var horizontal = false
                    val startX = down.position.x
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (!horizontal &&
                            abs(change.position.x - startX) > viewConfiguration.touchSlop
                        ) {
                            horizontal = true
                        }
                        if (horizontal) {
                            change.consume()
                            latestOnCursorMsChange(
                                timeAt(change.position.x, latestSize),
                            )
                        }
                    }
                }
            },
    ) {
        if (render != null) {
            drawTimeline(render, palette, metrics, cursorX)
        }
    }
}

private fun buildTimelineRender(
    data: TimelineData,
    viewport: TimelineViewport,
    widthPx: Float,
    metrics: TimelineMetrics,
    styles: TimelineTextStyles,
    textMeasurer: TextMeasurer,
    rowLabels: List<String>,
    batteryLabel: String,
): TimelineRender? {
    val plotLeft = metrics.gutter
    val plotRight = widthPx - metrics.rightPadding
    val plotWidth = plotRight - plotLeft
    if (plotWidth <= 1f) return null

    val hasBattery = data.batteryPoints.isNotEmpty()
    val rowsTop = metrics.rowsTop(hasBattery)
    val plotBottom = rowsTop + metrics.rowHeight * data.rows.size

    val tickCount = TimelineMath.tickCountForWidth(plotWidth, metrics.minTickSpacing)
    val tickAnchor = TimelineMath.localDayStart(viewport.startMs)
    val ticks = TimelineMath.tickTimes(
        viewport = viewport,
        targetTicks = tickCount,
        anchorMs = tickAnchor,
    ).map { timeMs ->
        RenderTick(
            x = plotLeft + viewport.fraction(timeMs) * plotWidth,
            label = textMeasurer.measure(
                text = TimelineMath.formatClockTime(timeMs, viewport.spanMs),
                style = styles.axis,
            ),
        )
    }

    val rowLabelLayouts = data.rows.mapIndexed { index, _ ->
        textMeasurer.measure(rowLabels[index], styles.row)
    }

    val visibleRows = data.visibleRows(viewport.startMs, viewport.endMs)
    val rows = visibleRows.mapIndexed { index, visible ->
        val drawableEntries = visible.entries.filterNot(::isInactiveInterval)
        val laneEntries = assignLanes(drawableEntries)
        var maxLane = 0
        val bars = ArrayList<RenderBar>(laneEntries.size)
        for (laneEntry in laneEntries) {
            if (laneEntry.lane > maxLane) maxLane = laneEntry.lane
            val entry = laneEntry.entry
            val startMs = maxOf(entry.startMs, viewport.startMs)
            val endMs = minOf(maxOf(entry.startMs, entry.endMs), viewport.endMs)
            if (endMs < viewport.startMs || startMs > viewport.endMs) continue
            bars.add(
                RenderBar(
                    startX = plotLeft + viewport.fraction(startMs) * plotWidth,
                    endX = plotLeft + viewport.fraction(endMs) * plotWidth,
                    lane = laneEntry.lane,
                    instant = entry.endMs <= entry.startMs,
                    alpha = valueShadeAlpha(entry.value),
                ),
            )
        }
        RenderRow(
            group = visible.row.group,
            bars = bars,
            centerY = rowsTop + metrics.rowHeight * (index + 0.5f),
            laneCount = maxLane + 1,
        )
    }

    val battery = if (hasBattery) {
        buildBatteryPaths(
            points = data.batteryPoints,
            viewport = viewport,
            plotLeft = plotLeft,
            plotRight = plotRight,
            plotWidth = plotWidth,
            batteryTop = metrics.batteryTop,
            batteryBottom = metrics.batteryBottom,
        )
    } else {
        null
    }

    val reportX = data.reportTakenMs
        ?.takeIf { it in viewport.startMs..viewport.endMs }
        ?.let { plotLeft + viewport.fraction(it) * plotWidth }

    return TimelineRender(
        viewport = viewport,
        plotLeft = plotLeft,
        plotRight = plotRight,
        plotWidth = plotWidth,
        axisBottom = metrics.axisBottom,
        batteryTop = metrics.batteryTop,
        batteryBottom = metrics.batteryBottom,
        rowsTop = rowsTop,
        rowHeight = metrics.rowHeight,
        plotBottom = plotBottom,
        hasBattery = hasBattery,
        ticks = ticks,
        rows = rows,
        rowLabels = rowLabelLayouts,
        batteryLabel = textMeasurer.measure(batteryLabel, styles.row),
        battery = battery,
        reportX = reportX,
    )
}

private fun buildBatteryPaths(
    points: List<BatteryPoint>,
    viewport: TimelineViewport,
    plotLeft: Float,
    plotRight: Float,
    plotWidth: Float,
    batteryTop: Float,
    batteryBottom: Float,
): BatteryPaths? {
    if (points.isEmpty()) return null

    fun levelY(level: Float): Float {
        val clamped = level.coerceIn(0f, 100f) / 100f
        return batteryBottom - clamped * (batteryBottom - batteryTop)
    }

    val startLevel = batteryLevelAt(points, viewport.startMs) ?: points.first().levelPercent
    val endLevel = batteryLevelAt(points, viewport.endMs)
        ?: points.firstOrNull { it.timeMs >= viewport.endMs }?.levelPercent
        ?: points.last().levelPercent
    val visible = batteryPointsInWindow(points, viewport.startMs, viewport.endMs)

    val line = Path()
    line.moveTo(plotLeft, levelY(startLevel))
    var lastLevel = startLevel
    for (point in visible) {
        if (point.timeMs <= viewport.startMs) continue
        if (point.timeMs >= viewport.endMs) break
        val x = plotLeft + viewport.fraction(point.timeMs) * plotWidth
        if (point.levelPercent != lastLevel) {
            line.lineTo(x, levelY(lastLevel))
            lastLevel = point.levelPercent
        }
        line.lineTo(x, levelY(lastLevel))
    }
    line.lineTo(plotRight, levelY(endLevel))

    val fill = Path().apply {
        addPath(line)
        lineTo(plotRight, batteryBottom)
        lineTo(plotLeft, batteryBottom)
        close()
    }
    return BatteryPaths(line, fill)
}

private fun DrawScope.drawTimeline(
    render: TimelineRender,
    palette: TimelinePalette,
    metrics: TimelineMetrics,
    cursorX: Float?,
) {
    // Rounded plot background, shared by the battery strip and metric rows.
    drawRoundRect(
        color = palette.plotBackground,
        topLeft = Offset(render.plotLeft, render.axisBottom),
        size = Size(
            width = render.plotWidth,
            height = (render.plotBottom - render.axisBottom).coerceAtLeast(1f),
        ),
        cornerRadius = CornerRadius(metrics.plotCorner),
    )

    // Alternating row stripes.
    render.rows.forEachIndexed { index, row ->
        if (index % 2 == 0) {
            drawRect(
                color = palette.plotStripe,
                topLeft = Offset(
                    render.plotLeft,
                    row.centerY - render.rowHeight / 2f,
                ),
                size = Size(render.plotWidth, render.rowHeight),
            )
        }
    }

    drawLine(
        color = palette.grid,
        start = Offset(render.plotLeft, render.axisBottom),
        end = Offset(render.plotRight, render.axisBottom),
        strokeWidth = 1f,
    )

    if (render.hasBattery) {
        drawLine(
            color = palette.grid,
            start = Offset(render.plotLeft, render.batteryTop),
            end = Offset(render.plotRight, render.batteryTop),
            strokeWidth = 1f,
        )
        drawLine(
            color = palette.grid,
            start = Offset(render.plotLeft, render.batteryBottom),
            end = Offset(render.plotRight, render.batteryBottom),
            strokeWidth = 1f,
        )
    }

    // Vertical grid behind the data.
    for (tick in render.ticks) {
        drawLine(
            color = palette.grid,
            start = Offset(tick.x, render.axisBottom),
            end = Offset(tick.x, render.plotBottom),
            strokeWidth = 1f,
        )
    }

    render.battery?.let { battery ->
        drawPath(path = battery.fill, color = palette.batteryFill)
        drawPath(
            path = battery.line,
            color = palette.battery,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = metrics.batteryStroke,
            ),
        )
    }

    // Metric rows.
    render.rows.forEachIndexed { index, row ->
        if (index > 0) {
            drawLine(
                color = palette.grid,
                start = Offset(
                    render.plotLeft,
                    row.centerY - render.rowHeight / 2f,
                ),
                end = Offset(
                    render.plotRight,
                    row.centerY - render.rowHeight / 2f,
                ),
                strokeWidth = 1f,
            )
        }

        val color = palette.seriesColor(row.group)
        val centerLane = (row.laneCount - 1) / 2f
        for (bar in row.bars) {
            val barColor = color.copy(alpha = bar.alpha)
            val laneY = row.centerY +
                (bar.lane - centerLane) * metrics.laneSpacing
            if (bar.instant) {
                drawLine(
                    color = barColor,
                    start = Offset(bar.startX, laneY - metrics.instantHalfHeight),
                    end = Offset(bar.startX, laneY + metrics.instantHalfHeight),
                    strokeWidth = metrics.minBarWidth,
                    cap = StrokeCap.Round,
                )
            } else {
                val width = max(bar.endX - bar.startX, metrics.minBarWidth)
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(bar.startX, laneY - metrics.barHeight / 2f),
                    size = Size(width, metrics.barHeight),
                    cornerRadius = CornerRadius(metrics.barHeight / 2f),
                )
            }
        }
    }

    // Row labels in the left gutter.
    for (index in render.rowLabels.indices) {
        val label = render.rowLabels[index]
        val row = render.rows.getOrNull(index) ?: continue
        drawText(
            textLayoutResult = label,
            topLeft = Offset(
                metrics.labelX,
                row.centerY - label.size.height / 2f,
            ),
        )
    }

    if (render.hasBattery) {
        drawText(
            textLayoutResult = render.batteryLabel,
            topLeft = Offset(metrics.labelX, render.batteryTop + 2.dp.toPx()),
        )
    }

    // Axis labels.
    for (tick in render.ticks) {
        val labelWidth = tick.label.size.width.toFloat()
        val maxLeft = (render.plotRight - labelWidth).coerceAtLeast(render.plotLeft)
        val left = (tick.x - labelWidth / 2f).coerceIn(render.plotLeft, maxLeft)
        drawText(
            textLayoutResult = tick.label,
            topLeft = Offset(left, metrics.axisLabelTop),
        )
    }

    // Report-taken marker is drawn over the data.
    render.reportX?.let { x ->
        drawLine(
            color = palette.report.copy(alpha = 0.85f),
            start = Offset(x, render.axisBottom),
            end = Offset(x, render.plotBottom),
            strokeWidth = metrics.reportStroke,
        )
        drawCircle(
            color = palette.report,
            radius = metrics.reportDot,
            center = Offset(x, render.axisBottom),
        )
    }

    // The inspection cursor is the strongest line so it stays visible over
    // every series color.
    cursorX?.let { x ->
        drawLine(
            color = palette.cursor.copy(alpha = 0.9f),
            start = Offset(x, render.axisBottom),
            end = Offset(x, render.plotBottom),
            strokeWidth = metrics.cursorStroke,
        )
        drawCircle(
            color = palette.cursor,
            radius = metrics.cursorDot,
            center = Offset(x, render.axisBottom),
        )
    }
}
