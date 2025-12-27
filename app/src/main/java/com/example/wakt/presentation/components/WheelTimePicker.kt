package com.example.wakt.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

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

    // Create a large number of repeated items for infinite scroll illusion
    val repeatCount = 1000
    val totalItems = itemCount * repeatCount
    val middleSection = repeatCount / 2
    val initialIndex = (middleSection * itemCount) + selectedItem

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val coroutineScope = rememberCoroutineScope()

    // Calculate the visually centered index in real-time
    val centeredIndex by remember {
        derivedStateOf {
            val firstVisible = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            // If scrolled more than half the item height, the next item is centered
            if (offset > itemHeightPx * 0.5f) {
                firstVisible + 1
            } else {
                firstVisible
            }
        }
    }

    // Handle scroll end - snap and notify
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val actualValue = centeredIndex % itemCount

            if (actualValue != selectedItem) {
                onItemSelected(actualValue)
            }

            // Snap to the item
            coroutineScope.launch {
                listState.animateScrollToItem(centeredIndex)
            }
        }
    }

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
            items(totalItems) { index ->
                val actualValue = index % itemCount
                val distanceFromCenter = abs(index - centeredIndex)

                val alpha = when (distanceFromCenter) {
                    0 -> 1f
                    1 -> 0.4f
                    else -> 0.15f
                }

                val scale = when (distanceFromCenter) {
                    0 -> 1f
                    1 -> 0.8f
                    else -> 0.65f
                }

                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth()
                        .alpha(alpha),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = String.format("%02d", actualValue),
                        fontSize = (52 * scale).sp,
                        fontWeight = if (distanceFromCenter == 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (distanceFromCenter == 0)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
