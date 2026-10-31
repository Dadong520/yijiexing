package cn.hbzjhs.driver.preview

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Resting positions of a map page's function panel: covering the map, half, or only a small handle. */
enum class MapPanelValue { Expanded, Half, Collapsed }

/**
 * Position of one draggable map panel. [value] is where the panel rests or is heading; [settled] is
 * the last position it actually reached. Only the panel's top edge in pixels moves while dragging or
 * animating; at rest the top edge follows the anchor of [value], so screen-size, keyboard or
 * font-size changes never leave the panel between positions.
 */
@Stable
class MapPanelState internal constructor(initial: MapPanelValue) {
    var value by mutableStateOf(initial)
        private set
    var settled by mutableStateOf(initial)
        private set
    // NaN while resting at the anchor of [value]; the live top edge while dragging or animating.
    private var moving by mutableFloatStateOf(Float.NaN)
    private var anchors: Map<MapPanelValue, Float> = emptyMap()
    private var velocityThreshold = 0f
    private var scope: CoroutineScope? = null
    private var job: Job? = null

    internal fun top(current: Map<MapPanelValue, Float>): Float =
        if (moving.isNaN()) current.getValue(value) else moving

    internal fun isBelow(y: Float): Boolean = !moving.isNaN() && moving > y

    /** True while a finger moved the panel and it has not been released to a resting position yet. */
    internal val dragging: Boolean get() = !moving.isNaN() && job?.isActive != true

    internal fun attach(newAnchors: Map<MapPanelValue, Float>, newScope: CoroutineScope, threshold: Float) {
        scope = newScope
        velocityThreshold = threshold
        if (newAnchors == anchors) return
        anchors = newAnchors
        // A running animation is re-aimed at the new anchor; a drag in progress stays inside the new range.
        if (job?.isActive == true) animateTo(value)
        else if (!moving.isNaN()) moving = moving.coerceIn(newAnchors.values.minOf { it }, newAnchors.values.maxOf { it })
    }

    /** Moves the panel by [delta] pixels (positive = down) and returns how much it actually moved. */
    internal fun dragBy(delta: Float): Float {
        if (anchors.isEmpty() || delta == 0f) return 0f
        val start = top(anchors)
        val end = (start + delta).coerceIn(anchors.values.minOf { it }, anchors.values.maxOf { it })
        if (end == start) return 0f
        job?.cancel()
        moving = end
        return end - start
    }

    /** Releases a drag: a quick fling goes one position further, otherwise the nearest position wins. */
    internal fun settle(velocity: Float) {
        if (anchors.isEmpty()) return
        val top = top(anchors)
        val sorted = anchors.entries.sortedBy { it.value }
        val target = when {
            velocity > velocityThreshold -> sorted.firstOrNull { it.value > top + 0.5f } ?: sorted.last()
            velocity < -velocityThreshold -> sorted.lastOrNull { it.value < top - 0.5f } ?: sorted.first()
            else -> sorted.minBy { abs(it.value - top) }
        }
        animateTo(target.key, velocity)
    }

    fun animateTo(target: MapPanelValue, velocity: Float = 0f) {
        job?.cancel()
        val end = anchors[target]
        val runner = scope
        if (end == null || runner == null) {
            value = target
            settled = target
            moving = Float.NaN
            return
        }
        val start = top(anchors)
        moving = start
        value = target
        job = runner.launch {
            animate(start, end, velocity, spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> moving = v }
            moving = Float.NaN
            settled = target
        }
    }

    companion object {
        val PanelSaver: Saver<MapPanelState, String> = Saver(
            save = { it.value.name },
            restore = { name -> MapPanelState(MapPanelValue.values().firstOrNull { it.name == name } ?: MapPanelValue.Half) },
        )
    }
}

/**
 * A map page whose functions live in a panel that can be dragged over the map (all functions,
 * scrollable), to half height, or down to a small handle that shows the whole map. Scrolling inside
 * [content] first moves the panel, then the content (and back), so the two never fight; touches on
 * the visible map always reach the map. [bottomBar] stays pinned under the content whenever the
 * panel is open; collapsing hides it only until the handle restores the panel, and a change of
 * [attentionKey] (for example a new order stage) brings a collapsed panel back to half height.
 */
@Composable
fun MapPanelLayout(
    saveKey: String,
    modifier: Modifier = Modifier,
    attentionKey: Any? = null,
    collapsedLabel: String = "显示功能面板",
    halfMapFraction: Float = 0.46f,
    minHalfContent: Dp = 160.dp,
    containerColor: Color = Color.White,
    map: @Composable (Modifier) -> Unit,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val panel = rememberSaveable(saveKey, saver = MapPanelState.PanelSaver) { MapPanelState(MapPanelValue.Half) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var handlePx by remember { mutableIntStateOf(0) }
    var barPx by remember { mutableIntStateOf(0) }
    var seenAttention by remember { mutableStateOf(attentionKey) }
    LaunchedEffect(attentionKey) {
        if (attentionKey != seenAttention) {
            seenAttention = attentionKey
            if (panel.value == MapPanelValue.Collapsed) panel.animateTo(MapPanelValue.Half)
        }
    }
    BackHandler(enabled = panel.value == MapPanelValue.Expanded) { panel.animateTo(MapPanelValue.Half) }
    val connection = remember(panel) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (source == NestedScrollSource.Drag && available.y < 0) Offset(0f, panel.dragBy(available.y)) else Offset.Zero

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (source == NestedScrollSource.Drag && available.y > 0) Offset(0f, panel.dragBy(available.y)) else Offset.Zero

            override suspend fun onPreFling(available: Velocity): Velocity =
                if (panel.dragging) { panel.settle(available.y); available } else Velocity.Zero

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (panel.dragging) panel.settle(0f)
                return Velocity.Zero
            }
        }
    }
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val full = constraints.maxHeight.toFloat()
        val handle = if (handlePx > 0) handlePx.toFloat() else with(density) { 60.dp.toPx() }
        val bar = if (bottomBar == null) 0f else barPx.toFloat()
        val collapsedTop = (full - handle).coerceAtLeast(0f)
        // Half height keeps the handle, the pinned actions and some content visible even on small
        // screens with large text; the map gives way first.
        val halfTop = minOf(full * halfMapFraction, full - handle - bar - with(density) { minHalfContent.toPx() })
            .coerceIn(0f, collapsedTop)
        val anchors = mapOf(MapPanelValue.Expanded to 0f, MapPanelValue.Half to halfTop, MapPanelValue.Collapsed to collapsedTop)
        val threshold = with(density) { 125.dp.toPx() }
        SideEffect { panel.attach(anchors, scope, threshold) }
        // The map is resized once when the panel crosses half height, not on every drag frame.
        val mapTall by remember(panel, halfTop) {
            derivedStateOf { panel.value == MapPanelValue.Collapsed || panel.isBelow(halfTop + 1f) }
        }
        // The map ends at the panel's top edge so its own controls and attribution stay uncovered; on
        // very short screens it keeps a minimum height (partly under the panel) so it is never zero-sized.
        val minMap = minOf(with(density) { 120.dp.toPx() }, collapsedTop)
        val mapHeight = with(density) { maxOf(if (mapTall) collapsedTop else halfTop, minMap).toDp() }
        map(Modifier.fillMaxWidth().height(mapHeight))
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .layout { measurable, c ->
                    val h = (full - panel.top(anchors)).roundToInt().coerceIn(0, c.maxHeight)
                    val placeable = measurable.measure(c.copy(minHeight = h, maxHeight = h))
                    layout(placeable.width, h) { placeable.place(0, 0) }
                },
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = containerColor,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, Line),
        ) {
            Column(Modifier.fillMaxSize()) {
                MapPanelHandle(
                    panel,
                    collapsedLabel,
                    Modifier
                        .onSizeChanged { if (it.height > 0) handlePx = it.height }
                        .draggable(
                            rememberDraggableState { delta -> panel.dragBy(delta) },
                            Orientation.Vertical,
                            onDragStopped = { velocity -> panel.settle(velocity) },
                        ),
                )
                // Resting collapsed, the clipped content is also hidden from screen readers.
                val hidden = panel.value == MapPanelValue.Collapsed && panel.settled == MapPanelValue.Collapsed
                Column(Modifier.weight(1f).fillMaxWidth().then(if (hidden) Modifier.clearAndSetSemantics {} else Modifier)) {
                    Box(Modifier.weight(1f).fillMaxWidth().nestedScroll(connection), content = content)
                    if (bottomBar != null)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(Alignment.Top, unbounded = true)
                                .onSizeChanged { if (it.height > 0) barPx = it.height },
                        ) { bottomBar() }
                }
            }
        }
    }
}

/** Drag handle with visible buttons and screen-reader actions for every panel position. */
@Composable
private fun MapPanelHandle(panel: MapPanelState, collapsedLabel: String, modifier: Modifier) {
    val value = panel.value
    val description = when (value) {
        MapPanelValue.Expanded -> "功能面板已展开，覆盖地图"
        MapPanelValue.Half -> "功能面板半展开，上方显示地图"
        MapPanelValue.Collapsed -> "功能面板已收起，显示全屏地图"
    }
    val next = when (value) {
        MapPanelValue.Collapsed -> MapPanelValue.Half
        MapPanelValue.Half -> MapPanelValue.Expanded
        MapPanelValue.Expanded -> MapPanelValue.Half
    }
    Column(
        modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (next == MapPanelValue.Expanded) "展开全部功能" else "半屏显示地图和功能", role = Role.Button) {
                panel.animateTo(next)
            }
            .semantics {
                stateDescription = description
                customActions = listOfNotNull(
                    CustomAccessibilityAction("展开全部功能") { panel.animateTo(MapPanelValue.Expanded); true }
                        .takeIf { value != MapPanelValue.Expanded },
                    CustomAccessibilityAction("半屏显示地图和功能") { panel.animateTo(MapPanelValue.Half); true }
                        .takeIf { value != MapPanelValue.Half },
                    CustomAccessibilityAction("收起面板，显示全屏地图") { panel.animateTo(MapPanelValue.Collapsed); true }
                        .takeIf { value != MapPanelValue.Collapsed },
                )
            }
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .padding(top = 8.dp)
                .size(width = 40.dp, height = 4.dp)
                .background(ControlOutline.copy(alpha = 0.5f), CircleShape),
        )
        // Every state shows the same single-line button row, so the handle height never jumps.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (value == MapPanelValue.Collapsed) {
                Spacer(Modifier.weight(1f))
                DriverTextButton(onClick = { panel.animateTo(MapPanelValue.Half) }) {
                    Text(collapsedLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = DriverType.Caption)
                }
                Spacer(Modifier.weight(1f))
            } else {
                DriverTextButton(onClick = { panel.animateTo(MapPanelValue.Collapsed) }) {
                    Text("看地图", maxLines = 1, fontSize = DriverType.Caption)
                }
                Spacer(Modifier.weight(1f))
                DriverTextButton(onClick = { panel.animateTo(if (value == MapPanelValue.Expanded) MapPanelValue.Half else MapPanelValue.Expanded) }) {
                    Text(if (value == MapPanelValue.Expanded) "收起一半" else "展开全部", maxLines = 1, fontSize = DriverType.Caption)
                }
            }
        }
    }
}
