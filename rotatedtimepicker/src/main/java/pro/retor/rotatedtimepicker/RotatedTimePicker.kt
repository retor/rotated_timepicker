package pro.retor.rotatedtimepicker

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs

internal const val itemHeight = 65f
internal const val itemHalfHeight = itemHeight / 2f
internal const val countVisibleItems: Int = 5
internal const val listHeight = itemHeight * countVisibleItems

data class StartTime(
    val hour: Int,
    val minute: Int,
)

@Preview(backgroundColor = 0xffffffff, showBackground = true)
@Composable
private fun PreviewRotatedTimePicker() {
    var hour by remember { mutableStateOf("00") }
    var minute by remember { mutableStateOf("00") }
    Column {
        Text(
            text = "$hour:$minute",
            modifier =
            Modifier
                .padding(bottom = 10.dp)
                .align(Alignment.CenterHorizontally),
        )
        RotatedTimePicker(
            startTime = StartTime(7, 0),
            minTime = StartTime(8, 29),
            modifier = Modifier.align(Alignment.CenterHorizontally),
            lineColor = Color.Magenta,
            selectedTimeStrings = { h, m ->
                hour = h
                minute = m
            })
    }
}

@Composable
fun RotatedTimePicker(
    startTime: StartTime,
    minTime: StartTime = StartTime(0, 0),
    modifier: Modifier,
    lineColor: Color = Color.Red,
    selectedTime: (Int, Int) -> Unit = { _, _ -> },
    selectedTimeStrings: (String, String) -> Unit = { _, _ -> },
) {
    var hour by remember { mutableIntStateOf(startTime.hour) }
    var minute by remember { mutableIntStateOf(startTime.minute) }
    var hourString by remember { mutableStateOf(formatTime(startTime.hour)) }
    var minuteString by remember { mutableStateOf(formatTime(startTime.minute)) }
    var minMinutes by remember { mutableIntStateOf(minTime.minute) }

    Box(
        modifier
            .wrapContentSize()
            .padding(horizontal = 12.dp)
    ) {
        Row {
            TimeCell(maxOf(startTime.hour, minTime.hour), minTime.hour, IntRange(0, 23), lineColor) { i, s ->
                if (i >= minTime.hour && minMinutes != 0) minMinutes = 0
                hour = i
                hourString = s
                selectedTime.invoke(hour, minute)
                selectedTimeStrings.invoke(hourString, minuteString)
            }
            Text(
                ":",
                fontSize = 20.sp,
                modifier =
                Modifier
                    .padding(bottom = 3.dp)
                    .padding(horizontal = 12.dp)
                    .align(Alignment.CenterVertically),
            )
            TimeCell(maxOf(startTime.minute, minMinutes), minMinutes, IntRange(0, 59), lineColor) { i, s ->
                minute = i
                minuteString = s
                selectedTime.invoke(hour, minute)
                selectedTimeStrings.invoke(hourString, minuteString)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal inline fun TimeCell(
    initialValue: Int,
    minValue: Int,
    valuesRange: IntRange,
    lineColor: Color = Color.Red,
    crossinline onItemSelected: (Int, String) -> Unit,
) {
    val firstVisible = valuesRange.indexOf(initialValue)
    val composeContext = rememberCoroutineScope()
    val list =
        remember(valuesRange) {
            valuesRange.map { formatTime(it) }
        }
    val firstVisibleIndexToScroll = (firstVisible - 2) + ((Int.MAX_VALUE / 2) / list.size) * list.size
    val listState =
        rememberLazyListState(firstVisibleIndexToScroll)
    LaunchedEffect(initialValue) {
        listState.scrollToItem(firstVisibleIndexToScroll)
        onItemSelected(initialValue, list[firstVisible])
    }

    var lastSelectedItem by remember(valuesRange) { mutableStateOf("00") }
    var parentHalfHeight by remember { mutableFloatStateOf(listHeight / 2) }
    var parentHalfWeight by remember { mutableFloatStateOf(itemHalfHeight) }
    LazyColumn(
        Modifier
            .padding(horizontal = 25.dp)
            .height(with(LocalDensity.current) { listHeight.toDp() })
            .onGloballyPositioned {
                parentHalfHeight = (it.size.height / 2).toFloat()
                parentHalfWeight = (it.size.width).toFloat()
            }
            .drawBehind {
                drawSelectedLine(
                    lineColor,
                    -(parentHalfWeight),
                    parentHalfHeight - (itemHalfHeight),
                    parentHalfWeight * 2,
                    parentHalfHeight - (itemHalfHeight)
                )
                drawSelectedLine(
                    lineColor,
                    -(parentHalfWeight),
                    parentHalfHeight + (itemHalfHeight),
                    parentHalfWeight * 2,
                    parentHalfHeight + (itemHalfHeight)
                )
            },
        state = listState,
        flingBehavior =
        rememberSnapFlingBehavior(
            lazyListState = listState,
        ),
    ) {
        items(Int.MAX_VALUE, key = { it }) {
            val num = (it % list.size)
            val item = list[num]
            Box(
                modifier =
                Modifier
                    .fillParentMaxHeight(1f / countVisibleItems)
                    .onGloballyPositioned { coordinates ->
                        val y = coordinates.positionInParent().y
                        val isSelected = (y > parentHalfHeight - itemHalfHeight && y < parentHalfHeight + itemHalfHeight)
                        if (isSelected && lastSelectedItem != item) {
                            if (minValue == 0 || item.toInt() >= minValue) {
                                onItemSelected(item.toInt(), item)
                                lastSelectedItem = item
                            } else {
                                if (listState.isScrollInProgress.not())
                                    composeContext.launch {
                                        listState.scrollToItem(firstVisibleIndexToScroll)
                                    }
                            }
                        }
                    }
                    .graphicsLayer {
                        changeItemState(listState, it)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item,
                    fontSize = 20.sp,
                    color = if (item.toInt() < minValue) Color.Gray else Color.Black
                )
            }
        }
    }
}

internal fun formatTime(it: Int) = if (it < 10) "0$it" else "$it"

internal fun GraphicsLayerScope.changeItemState(listState: LazyListState, it: Int) {
    compositingStrategy = CompositingStrategy.Offscreen
    scaleX = calculateDistanceAndMaxDistance(listState, it, 0.5f)
    scaleY = calculateDistanceAndMaxDistance(listState, it, 0.5f)
    alpha = calculateDistanceAndMaxDistance(listState, it, 0.7f)
    rotationX = calculateRotation(listState, it)
}

internal fun DrawScope.drawSelectedLine(lineColor: Color, xS: Float, yS: Float, xE: Float, yE: Float) {
    drawLine(
        lineColor,
        Offset(xS, yS),
        Offset(xE, yE),
        strokeWidth = 5f,
    )
}

internal fun calculateDistanceAndMaxDistance(listState: LazyListState, index: Int, coff: Float): Float {
    val layoutInfo = listState.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo.map { it.index }
    if (!visibleItems.contains(index)) return 1f
    val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return 1f
    val center = layoutInfo.viewportSize.center.y
    val distance = abs((itemInfo.offset + itemInfo.size / 2) - center)
    val maxDistance = layoutInfo.viewportEndOffset / 2f
    return 1f - (distance / maxDistance) * coff
}

internal fun calculateRotation(listState: LazyListState, index: Int): Float {
    val layoutInfo = listState.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo.map { it.index }
    if (!visibleItems.contains(index)) return 0f
    val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return 0f
    val center = layoutInfo.viewportSize.center.y
    val distance = (itemInfo.offset + itemInfo.size / 2) - center
    return (distance / 3).toFloat()
}
