package com.example.wakt.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelTimePicker(
    hours: Int,
    minutes: Int,
    onHoursChange: (Int) -> Unit,
    onMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemHeight = 64.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(itemHeight * 3),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Hours wheel (0-23) - infinite scroll
        InfiniteWheelPicker(
            itemCount = 24,
            selectedItem = hours,
            onItemSelected = onHoursChange,
            itemHeight = itemHeight,
            modifier = Modifier.width(100.dp)
        )

        // Separator - centered with the picker
        Box(
            modifier = Modifier
                .height(itemHeight * 3)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = ":",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Minutes wheel (0-59) - infinite scroll
        InfiniteWheelPicker(
            itemCount = 60,
            selectedItem = minutes,
            onItemSelected = onMinutesChange,
            itemHeight = itemHeight,
            modifier = Modifier.width(100.dp)
        )
    }
}

/**
 * Infinite scrolling wheel picker.
 * Creates a virtually infinite list by repeating items and starting in the middle.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InfiniteWheelPicker(
    itemCount: Int,
    selectedItem: Int,
    onItemSelected: (Int) -> Unit,
    itemHeight: Dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }

    // Create a large virtual list by repeating items
    // Use 10000 repetitions to make it feel infinite
    val repetitions = 10000
    val totalItems = itemCount * repetitions
    val middleOffset = (repetitions / 2) * itemCount

    // Calculate initial index in the middle of the virtual list
    val initialIndex = middleOffset + selectedItem

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    // Track if we should skip the next scroll-to-item to avoid loops
    var isInternalScroll by remember { mutableStateOf(false) }

    // Scroll to correct position when selectedItem changes externally
    LaunchedEffect(selectedItem) {
        if (isInternalScroll) {
            isInternalScroll = false
            return@LaunchedEffect
        }

        val currentCenteredIndex = listState.firstVisibleItemIndex +
            if (listState.firstVisibleItemScrollOffset > itemHeightPx * 0.5f) 1 else 0
        val currentValue = currentCenteredIndex % itemCount

        if (currentValue != selectedItem) {
            // Calculate the closest position to scroll to
            val targetIndex = (currentCenteredIndex / itemCount) * itemCount + selectedItem
            listState.scrollToItem(targetIndex)
        }
    }

    // Handle scroll end - notify selection change
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val firstVisible = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            val centeredIndex = if (offset > itemHeightPx * 0.5f) firstVisible + 1 else firstVisible
            val actualValue = ((centeredIndex % itemCount) + itemCount) % itemCount

            if (actualValue != selectedItem) {
                isInternalScroll = true
                onItemSelected(actualValue)
            }

            // Re-center if we've scrolled too far from middle (to maintain "infinite" feel)
            val distanceFromMiddle = abs(centeredIndex - middleOffset)
            if (distanceFromMiddle > itemCount * 100) {
                val targetIndex = middleOffset + actualValue
                listState.scrollToItem(targetIndex)
            }
        }
    }

    // Cache colors
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier.height(itemHeight * 3),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = itemHeight),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
        ) {
            items(
                count = totalItems,
                key = { it }
            ) { index ->
                val actualValue = ((index % itemCount) + itemCount) % itemCount
                InfiniteWheelPickerItem(
                    value = actualValue,
                    index = index,
                    itemHeightPx = itemHeightPx,
                    itemHeight = itemHeight,
                    listState = listState,
                    primaryColor = primaryColor
                )
            }
        }
    }
}

/**
 * Individual wheel picker item - uses graphicsLayer for alpha/scale to avoid recomposition during scroll
 */
@Composable
private fun InfiniteWheelPickerItem(
    value: Int,
    index: Int,
    itemHeightPx: Float,
    itemHeight: Dp,
    listState: LazyListState,
    primaryColor: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier
            .height(itemHeight)
            .fillMaxWidth()
            .graphicsLayer {
                // Calculate distance from center in the draw phase - no recomposition!
                val firstVisible = listState.firstVisibleItemIndex
                val offset = listState.firstVisibleItemScrollOffset
                val centeredIndex = if (offset > itemHeightPx * 0.5f) firstVisible + 1 else firstVisible
                val distance = abs(index - centeredIndex)

                alpha = when (distance) {
                    0 -> 1f
                    1 -> 0.5f
                    else -> 0.2f
                }
                scaleX = when (distance) {
                    0 -> 1f
                    1 -> 0.85f
                    else -> 0.7f
                }
                scaleY = scaleX
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = String.format("%02d", value),
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold,
            color = primaryColor
        )
    }
}
