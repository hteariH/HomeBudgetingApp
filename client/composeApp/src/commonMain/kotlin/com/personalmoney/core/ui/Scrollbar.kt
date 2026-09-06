package com.personalmoney.core.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Attaches an interactive vertical scrollbar to a [ScrollState] container.
 *
 * Drawn in the viewport coordinate space (apply *before* [androidx.compose.foundation.verticalScroll]).
 * Automatically hides when the content fits entirely in the viewport.
 *
 * @param state The [ScrollState] driving the container.
 * @param width Thickness of the scrollbar thumb in its idle resting state.
 * @param hoverWidth Thickness of the scrollbar thumb when hovered or dragged.
 * @param paddingEnd Distance between the scrollbar and the container's right edge.
 * @param verticalPadding Distance from the top and bottom edges of the viewport.
 * @param persistent If true, thumb remains visible at subtle opacity even when idle.
 */
@Composable
fun Modifier.verticalScrollbar(
    state: ScrollState,
    width: Dp = 5.dp,
    hoverWidth: Dp = 7.dp,
    paddingEnd: Dp = 3.dp,
    verticalPadding: Dp = 4.dp,
    persistent: Boolean = true,
): Modifier {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isDark = MaterialTheme.colorScheme.surface.let {
        (it.red * 0.299f + it.green * 0.587f + it.blue * 0.114f) < 0.5f
    }
    val thumbBaseColor = if (isDark) Color.White else Color.Black

    var isHovered by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var isInteracting by remember { mutableStateOf(false) }

    LaunchedEffect(state.value, state.isScrollInProgress) {
        if (state.isScrollInProgress) {
            isInteracting = true
        } else {
            delay(1200)
            isInteracting = false
        }
    }

    val targetAlpha = when {
        isDragging || isHovered -> 0.75f
        isInteracting -> 0.55f
        persistent -> 0.35f
        else -> 0f
    }
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 200),
        label = "scrollbar_alpha"
    )

    val currentWidthDp by animateDpAsState(
        targetValue = if (isHovered || isDragging) hoverWidth else width,
        animationSpec = tween(durationMillis = 150),
        label = "scrollbar_width"
    )

    val widthPx = with(density) { currentWidthDp.toPx() }
    val paddingEndPx = with(density) { paddingEnd.toPx() }
    val verticalPaddingPx = with(density) { verticalPadding.toPx() }
    val minThumbHeightPx = with(density) { 36.dp.toPx() }
    val hitTargetWidthPx = with(density) { 24.dp.toPx() }

    var lastScrollableTrackPx by remember { mutableFloatStateOf(0f) }
    var lastThumbHeightPx by remember { mutableFloatStateOf(0f) }

    return this
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val boundsWidth = size.width
                val maxVal = state.maxValue
                if (down.position.x < boundsWidth - hitTargetWidthPx || maxVal <= 0) {
                    return@awaitEachGesture
                }

                var totalDragY = 0f
                var isDragActive = false

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (!isDragActive && abs(totalDragY) < 10f && lastScrollableTrackPx > 0f) {
                            val clickY = down.position.y
                            val targetThumbY = clickY - (lastThumbHeightPx / 2f) - verticalPaddingPx
                            val targetRatio = (targetThumbY / lastScrollableTrackPx).coerceIn(0f, 1f)
                            val targetValue = (targetRatio * state.maxValue).roundToInt()
                            coroutineScope.launch {
                                state.animateScrollTo(targetValue)
                            }
                        }
                        break
                    }

                    val deltaY = change.position.y - change.previousPosition.y
                    totalDragY += deltaY

                    if (abs(totalDragY) > 4f && !isDragActive) {
                        isDragActive = true
                        isDragging = true
                    }

                    if (isDragActive && deltaY != 0f && lastScrollableTrackPx > 0f) {
                        change.consume()
                        val contentDelta = deltaY * (state.maxValue.toFloat() / lastScrollableTrackPx)
                        coroutineScope.launch {
                            state.scrollBy(contentDelta)
                        }
                    }
                }
                isDragging = false
            }
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val pos = event.changes.firstOrNull()?.position
                    if (pos != null) {
                        val inHitZone = pos.x >= size.width - hitTargetWidthPx
                        if (event.type == PointerEventType.Move) {
                            isHovered = inHitZone
                        } else if (event.type == PointerEventType.Exit) {
                            isHovered = false
                        }
                    }
                }
            }
        }
        .drawWithContent {
            drawContent()

            val maxVal = state.maxValue
            if (maxVal <= 0 || alpha <= 0.01f) return@drawWithContent

            val viewportHeight = size.height
            val totalHeight = maxVal + viewportHeight
            val trackHeight = viewportHeight - (verticalPaddingPx * 2)
            if (trackHeight <= 0) return@drawWithContent

            val rawThumbHeight = (viewportHeight / totalHeight) * trackHeight
            val thumbHeight = rawThumbHeight.coerceIn(minThumbHeightPx, trackHeight)
            val scrollableTrack = trackHeight - thumbHeight
            lastThumbHeightPx = thumbHeight
            lastScrollableTrackPx = scrollableTrack

            if (scrollableTrack <= 0) return@drawWithContent

            val scrollRatio = when {
                state.value <= 0 -> 0f
                state.value >= maxVal -> 1f
                else -> (state.value.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
            }

            val thumbY = verticalPaddingPx + (scrollRatio * scrollableTrack)
            val thumbX = size.width - paddingEndPx - widthPx

            drawRoundRect(
                color = thumbBaseColor.copy(alpha = alpha),
                topLeft = Offset(thumbX, thumbY),
                size = Size(widthPx, thumbHeight),
                cornerRadius = CornerRadius(widthPx / 2, widthPx / 2)
            )
        }
}

/**
 * Attaches an interactive vertical scrollbar to a [LazyListState] container.
 *
 * Automatically hides when all items fit entirely in the viewport.
 *
 * @param state The [LazyListState] driving the container.
 * @param width Thickness of the scrollbar thumb in its idle resting state.
 * @param hoverWidth Thickness of the scrollbar thumb when hovered or dragged.
 * @param paddingEnd Distance between the scrollbar and the container's right edge.
 * @param verticalPadding Distance from the top and bottom edges of the viewport.
 * @param persistent If true, thumb remains visible at subtle opacity even when idle.
 */
@Composable
fun Modifier.verticalScrollbar(
    state: LazyListState,
    width: Dp = 5.dp,
    hoverWidth: Dp = 7.dp,
    paddingEnd: Dp = 3.dp,
    verticalPadding: Dp = 4.dp,
    persistent: Boolean = true,
): Modifier {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isDark = MaterialTheme.colorScheme.surface.let {
        (it.red * 0.299f + it.green * 0.587f + it.blue * 0.114f) < 0.5f
    }
    val thumbBaseColor = if (isDark) Color.White else Color.Black

    var isHovered by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var isInteracting by remember { mutableStateOf(false) }

    LaunchedEffect(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset, state.isScrollInProgress) {
        if (state.isScrollInProgress) {
            isInteracting = true
        } else {
            delay(1200)
            isInteracting = false
        }
    }

    val targetAlpha = when {
        isDragging || isHovered -> 0.75f
        isInteracting -> 0.55f
        persistent -> 0.35f
        else -> 0f
    }
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 200),
        label = "lazy_scrollbar_alpha"
    )

    val currentWidthDp by animateDpAsState(
        targetValue = if (isHovered || isDragging) hoverWidth else width,
        animationSpec = tween(durationMillis = 150),
        label = "lazy_scrollbar_width"
    )

    val widthPx = with(density) { currentWidthDp.toPx() }
    val paddingEndPx = with(density) { paddingEnd.toPx() }
    val verticalPaddingPx = with(density) { verticalPadding.toPx() }
    val minThumbHeightPx = with(density) { 36.dp.toPx() }
    val hitTargetWidthPx = with(density) { 24.dp.toPx() }

    var lastScrollableTrackPx by remember { mutableFloatStateOf(0f) }
    var lastThumbHeightPx by remember { mutableFloatStateOf(0f) }
    var lastEstimatedTotalHeightPx by remember { mutableFloatStateOf(0f) }
    var canScroll by remember { mutableStateOf(false) }

    return this
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val boundsWidth = size.width
                if (down.position.x < boundsWidth - hitTargetWidthPx || !canScroll) {
                    return@awaitEachGesture
                }

                var totalDragY = 0f
                var isDragActive = false

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (!isDragActive && abs(totalDragY) < 10f && lastScrollableTrackPx > 0f) {
                            val totalItems = state.layoutInfo.totalItemsCount
                            if (totalItems > 0) {
                                val clickY = down.position.y
                                val targetThumbY = clickY - (lastThumbHeightPx / 2f) - verticalPaddingPx
                                val targetRatio = (targetThumbY / lastScrollableTrackPx).coerceIn(0f, 1f)
                                val targetItemIndex = (targetRatio * (totalItems - 1)).roundToInt()
                                coroutineScope.launch {
                                    state.animateScrollToItem(targetItemIndex)
                                }
                            }
                        }
                        break
                    }

                    val deltaY = change.position.y - change.previousPosition.y
                    totalDragY += deltaY

                    if (abs(totalDragY) > 4f && !isDragActive) {
                        isDragActive = true
                        isDragging = true
                    }

                    if (isDragActive && deltaY != 0f && lastScrollableTrackPx > 0f) {
                        change.consume()
                        val maxScroll = (lastEstimatedTotalHeightPx - size.height).coerceAtLeast(1f)
                        val contentDelta = deltaY * (maxScroll / lastScrollableTrackPx)
                        coroutineScope.launch {
                            state.scrollBy(contentDelta)
                        }
                    }
                }
                isDragging = false
            }
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val pos = event.changes.firstOrNull()?.position
                    if (pos != null) {
                        val inHitZone = pos.x >= size.width - hitTargetWidthPx
                        if (event.type == PointerEventType.Move) {
                            isHovered = inHitZone
                        } else if (event.type == PointerEventType.Exit) {
                            isHovered = false
                        }
                    }
                }
            }
        }
        .drawWithContent {
            drawContent()

            val layoutInfo = state.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo
            if (totalItems == 0 || visibleItems.isEmpty() || alpha <= 0.01f) {
                canScroll = false
                return@drawWithContent
            }

            val viewportHeight = size.height
            if (visibleItems.size >= totalItems) {
                val first = visibleItems.first()
                val last = visibleItems.last()
                val contentHeight = (last.offset + last.size) - first.offset
                if (first.offset >= 0 && contentHeight <= viewportHeight) {
                    canScroll = false
                    return@drawWithContent
                }
            }

            canScroll = true
            val trackHeight = viewportHeight - (verticalPaddingPx * 2)
            if (trackHeight <= 0) return@drawWithContent

            val avgItemSize = visibleItems.sumOf { it.size.toDouble() }.toFloat() / visibleItems.size
            val estimatedTotalHeight = (avgItemSize * totalItems).coerceAtLeast(viewportHeight)
            lastEstimatedTotalHeightPx = estimatedTotalHeight

            val rawThumbHeight = (viewportHeight / estimatedTotalHeight) * trackHeight
            val thumbHeight = rawThumbHeight.coerceIn(minThumbHeightPx, trackHeight)
            val scrollableTrack = trackHeight - thumbHeight
            lastThumbHeightPx = thumbHeight
            lastScrollableTrackPx = scrollableTrack

            if (scrollableTrack <= 0) return@drawWithContent

            val firstItem = visibleItems.first()
            val lastItem = visibleItems.last()
            val isAtStart = firstItem.index == 0 && firstItem.offset >= 0
            val isAtEnd = lastItem.index == totalItems - 1 && (lastItem.offset + lastItem.size) <= viewportHeight

            val currentScroll = (firstItem.index * avgItemSize) - firstItem.offset
            val maxScroll = (estimatedTotalHeight - viewportHeight).coerceAtLeast(1f)

            val scrollRatio = when {
                isAtStart -> 0f
                isAtEnd -> 1f
                else -> (currentScroll / maxScroll).coerceIn(0f, 1f)
            }

            val thumbY = verticalPaddingPx + (scrollRatio * scrollableTrack)
            val thumbX = size.width - paddingEndPx - widthPx

            drawRoundRect(
                color = thumbBaseColor.copy(alpha = alpha),
                topLeft = Offset(thumbX, thumbY),
                size = Size(widthPx, thumbHeight),
                cornerRadius = CornerRadius(widthPx / 2, widthPx / 2)
            )
        }
}
