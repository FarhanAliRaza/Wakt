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
        // Hours wheel (0-23)
        LoopingWheelPicker(
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

        // Minutes wheel (0-59)
        LoopingWheelPicker(
            itemCount = 60,
            selectedItem = minutes,
            onItemSelected = onMinutesChange,
            itemHeight = itemHeight,
            modifier = Modifier.width(100.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LoopingWheelPicker(
    itemCount: Int,
    selectedItem: Int,
    onItemSelected: (Int) -> Unit,
    itemHeight: Dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }

    // Create repeated items for infinite scroll illusion (reduced for performance)
    val repeatCount = 100
    val totalItems = itemCount * repeatCount
    val middleSection = repeatCount / 2
    val initialIndex = (middleSection * itemCount) + selectedItem

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    // Scroll to correct position when selectedItem changes externally (e.g., loading saved data)
    LaunchedEffect(selectedItem) {
        val currentCenteredIndex = listState.firstVisibleItemIndex +
            if (listState.firstVisibleItemScrollOffset > itemHeightPx * 0.5f) 1 else 0
        val currentValue = currentCenteredIndex % itemCount

        // Only scroll if the current position doesn't match the selected item
        if (currentValue != selectedItem) {
            val targetIndex = (middleSection * itemCount) + selectedItem
            listState.scrollToItem(targetIndex)
        }
    }

    // Handle scroll end - notify selection change (only when scroll stops)
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val firstVisible = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            val centeredIndex = if (offset > itemHeightPx * 0.5f) firstVisible + 1 else firstVisible
            val actualValue = centeredIndex % itemCount
            if (actualValue != selectedItem) {
                onItemSelected(actualValue)
            }
        }
    }

    // Get colors outside of items to avoid repeated lookups
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant

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
                WheelPickerItem(
                    index = index,
                    itemCount = itemCount,
                    itemHeightPx = itemHeightPx,
                    itemHeight = itemHeight,
                    listState = listState,
                    primaryColor = primaryColor,
                    secondaryColor = secondaryColor
                )
            }
        }
    }
}

/**
 * Individual wheel picker item - uses graphicsLayer for alpha/scale to avoid recomposition during scroll
 */
@Composable
private fun WheelPickerItem(
    index: Int,
    itemCount: Int,
    itemHeightPx: Float,
    itemHeight: Dp,
    listState: LazyListState,
    primaryColor: androidx.compose.ui.graphics.Color,
    secondaryColor: androidx.compose.ui.graphics.Color
) {
    val actualValue = index % itemCount

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
        // Use fixed text style - color changes handled separately
        Text(
            text = String.format("%02d", actualValue),
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold,
            color = primaryColor
        )
    }
}
