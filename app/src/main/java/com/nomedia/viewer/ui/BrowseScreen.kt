package com.nomedia.viewer.ui

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.nomedia.viewer.ImageFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun BrowseScreen(
    title: String,
    images: List<ImageFile>,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    scrollSpeed: Float,
    autoBrowseSpeed: Float,
    autoBrowseRunning: Boolean,
    onToggleAutoBrowse: () -> Unit,
    onAutoBrowseSpeed: (Float) -> Unit,
    isFavorite: (String) -> Boolean,
    isRead: (String) -> Boolean,
    onFavorite: (String) -> Unit,
    onOpenFull: (ImageFile) -> Unit,
    onViewed: (String) -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onBack: () -> Unit,
    onScrollUp: () -> Unit,
    onScrollDown: () -> Unit,
    onShareSelected: (Set<String>) -> Unit,
    onDeleteSelected: (Set<String>) -> Unit,
    onAutoAppendNext: () -> Unit
) {
    if (images.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("请先从文件夹页选择一个相册", color = Color(0xFF888888)) }
        return
    }
    var lastZoomChange by remember { mutableLongStateOf(0L) }
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(columns) {
                detectTransformGestures { _, _, zoom, _ ->
                    val now = System.currentTimeMillis()
                    if (now - lastZoomChange > 260) {
                        when {
                            zoom < 0.92f -> { onColumnsChange((columns + 1).coerceAtMost(6)); lastZoomChange = now }
                            zoom > 1.08f -> { onColumnsChange((columns - 1).coerceAtLeast(1)); lastZoomChange = now }
                        }
                    }
                }
            }
    ) {
        if (columns > 1) MultiColumn(images, columns, scrollSpeed, autoBrowseSpeed, autoBrowseRunning, onToggleAutoBrowse, onAutoBrowseSpeed, onAutoAppendNext, selection, { selection = it }, isFavorite, isRead, onFavorite, onOpenFull, onViewed, onSwipeLeft, onSwipeRight, onScrollUp, onScrollDown)
        else OneColumn(images, scrollSpeed, autoBrowseSpeed, autoBrowseRunning, onToggleAutoBrowse, onAutoBrowseSpeed, onAutoAppendNext, selection, { selection = it }, isFavorite, isRead, onFavorite, onOpenFull, onViewed, onSwipeLeft, onSwipeRight, onScrollUp, onScrollDown)
        if (selection.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).background(Color(0xFF111111)).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("已选 ${selection.size}", color = Color.White, modifier = Modifier.weight(1f))
                Button(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), onClick = { selection = images.map { it.path }.toSet() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB000), contentColor = Color.Black)) { Text("全选") }
                Button(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), onClick = { onShareSelected(selection); selection = emptySet() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202020), contentColor = Color(0xFFFFB000))) { Text("分享") }
                Button(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), onClick = { onDeleteSelected(selection); selection = emptySet() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202020), contentColor = Color(0xFFFFB000))) { Text("删除") }
            }
        }
    }
}

@Composable
private fun OneColumn(
    images: List<ImageFile>,
    scrollSpeed: Float,
    autoBrowseSpeed: Float,
    autoBrowseRunning: Boolean,
    onToggleAutoBrowse: () -> Unit,
    onAutoBrowseSpeed: (Float) -> Unit,
    onAutoAppendNext: () -> Unit,
    selection: Set<String>,
    onSelection: (Set<String>) -> Unit,
    isFavorite: (String)->Boolean,
    isRead: (String)->Boolean,
    onFavorite:(String)->Unit,
    onOpenFull:(ImageFile)->Unit,
    onViewed:(String)->Unit,
    onSwipeLeft:()->Unit,
    onSwipeRight:()->Unit,
    onScrollUp:()->Unit,
    onScrollDown:()->Unit
) {
    var savedIndex by rememberSaveable { mutableIntStateOf(0) }
    var savedOffset by rememberSaveable { mutableIntStateOf(0) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = savedIndex, initialFirstVisibleItemScrollOffset = savedOffset)
    val scope = rememberCoroutineScope()
    val speedConnection = remember(scrollSpeed) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag && scrollSpeed > 1.01f && abs(available.y) > abs(available.x)) {
                    state.dispatchRawDelta(-available.y * (scrollSpeed - 1f))
                }
                return Offset.Zero
            }
        }
    }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var userHolding by remember { mutableStateOf(false) }
    var showAutoSlider by rememberSaveable { mutableStateOf(false) }
    var autoSliderTouched by rememberSaveable { mutableStateOf(false) }
    var autoEndTriggered by remember(images) { mutableStateOf(false) }
    LaunchedEffect(autoBrowseRunning, autoBrowseSpeed, userHolding) { while (autoBrowseRunning) { if (!userHolding) { val before = state.firstVisibleItemIndex; state.dispatchRawDelta(autoBrowseSpeed); val visible = state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1); if (!autoEndTriggered && before >= (images.size - visible).coerceAtLeast(0)) { autoEndTriggered = true; onAutoAppendNext() } }; delay(16) } }
    LaunchedEffect(autoSliderTouched, autoBrowseSpeed) { if (autoSliderTouched) { delay(2000); showAutoSlider = false; autoSliderTouched = false } }
    TrackScroll(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset, onScrollUp, onScrollDown)
    var lastTitle by rememberSaveable { mutableStateOf(titleToken(images)) }
    LaunchedEffect(images) {
        val now = titleToken(images)
        if (lastTitle.isNotBlank() && now != lastTitle) state.scrollToItem(0)
        lastTitle = now
    }
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }.collect { (i, o) -> savedIndex = i; savedOffset = o }
    }
    LaunchedEffect(state, images) {
        snapshotFlow { state.firstVisibleItemIndex }.distinctUntilChanged().collect { idx ->
            if (idx in images.indices) onViewed(images[idx].path)
            if (idx > 0 && idx - 1 in images.indices) onViewed(images[idx - 1].path)
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        userHolding = true
                        do { val event = awaitPointerEvent() } while (event.changes.any { it.pressed })
                        userHolding = false
                    }
                }
                .nestedScroll(speedConnection)
                .pointerInput(images, scrollSpeed) {
                detectDragGestures(
                    onDragStart = { drag = Offset.Zero; userHolding = true },
                    onDrag = { change, amount ->
                        drag += amount
                        if (abs(drag.x) > abs(drag.y) * 1.4f) change.consume()
                    },
                    onDragEnd = {
                        when {
                            drag.x < -150f && abs(drag.x) > abs(drag.y) * 1.5f -> onSwipeLeft()
                            drag.x > 150f && abs(drag.x) > abs(drag.y) * 1.5f -> onSwipeRight()
                        }
                        drag = Offset.Zero
                        userHolding = false
                    },
                    onDragCancel = { drag = Offset.Zero; userHolding = false }
                )
            },
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        itemsIndexed(images, key = { _, it -> it.path }) { _, img -> ImageTile(img, selection, onSelection, isFavorite, isRead, onFavorite, onOpenFull) }
    }
        val visible = state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
        val maxIndex = (images.size - visible).coerceAtLeast(1)
        val progress = state.firstVisibleItemIndex.toFloat() / maxIndex
        RightScrollProgressBar(progress, visibleFraction = (visible.toFloat() / images.size.coerceAtLeast(1)).coerceAtMost(1f))
        AutoBrowseControls(
            running = autoBrowseRunning,
            speed = autoBrowseSpeed,
            showSlider = showAutoSlider,
            onToggle = { onToggleAutoBrowse(); showAutoSlider = true; autoSliderTouched = false },
            onSpeed = { onAutoBrowseSpeed(it); autoSliderTouched = true; showAutoSlider = true }
        )
    }
}

@Composable
private fun MultiColumn(
    images: List<ImageFile>,
    columns: Int,
    scrollSpeed: Float,
    autoBrowseSpeed: Float,
    autoBrowseRunning: Boolean,
    onToggleAutoBrowse: () -> Unit,
    onAutoBrowseSpeed: (Float) -> Unit,
    onAutoAppendNext: () -> Unit,
    selection: Set<String>,
    onSelection: (Set<String>) -> Unit,
    isFavorite: (String)->Boolean,
    isRead: (String)->Boolean,
    onFavorite:(String)->Unit,
    onOpenFull:(ImageFile)->Unit,
    onViewed:(String)->Unit,
    onSwipeLeft:()->Unit,
    onSwipeRight:()->Unit,
    onScrollUp:()->Unit,
    onScrollDown:()->Unit
) {
    var savedIndex by rememberSaveable { mutableIntStateOf(0) }
    var savedOffset by rememberSaveable { mutableIntStateOf(0) }
    val state = rememberLazyGridState(initialFirstVisibleItemIndex = savedIndex, initialFirstVisibleItemScrollOffset = savedOffset)
    val scope = rememberCoroutineScope()
    val speedConnection = remember(scrollSpeed) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag && scrollSpeed > 1.01f && abs(available.y) > abs(available.x)) {
                    state.dispatchRawDelta(-available.y * (scrollSpeed - 1f))
                }
                return Offset.Zero
            }
        }
    }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var userHolding by remember { mutableStateOf(false) }
    var showAutoSlider by rememberSaveable { mutableStateOf(false) }
    var autoSliderTouched by rememberSaveable { mutableStateOf(false) }
    var autoEndTriggered by remember(images) { mutableStateOf(false) }
    LaunchedEffect(autoBrowseRunning, autoBrowseSpeed, userHolding) { while (autoBrowseRunning) { if (!userHolding) { val before = state.firstVisibleItemIndex; state.dispatchRawDelta(autoBrowseSpeed); val visible = state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1); if (!autoEndTriggered && before >= (images.size - visible).coerceAtLeast(0)) { autoEndTriggered = true; onAutoAppendNext() } }; delay(16) } }
    LaunchedEffect(autoSliderTouched, autoBrowseSpeed) { if (autoSliderTouched) { delay(2000); showAutoSlider = false; autoSliderTouched = false } }
    TrackScroll(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset, onScrollUp, onScrollDown)
    var lastTitle by rememberSaveable { mutableStateOf(titleToken(images)) }
    LaunchedEffect(images, columns) {
        val now = titleToken(images)
        if (lastTitle.isNotBlank() && now != lastTitle) state.scrollToItem(0)
        lastTitle = now
    }
    LaunchedEffect(state) { snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }.collect { (i, o) -> savedIndex = i; savedOffset = o } }
    LaunchedEffect(state, images) {
        snapshotFlow { state.firstVisibleItemIndex }.distinctUntilChanged().collect { first ->
            val start = (first / columns) * columns
            ((start - columns) until (start + columns)).forEach { idx -> if (idx in images.indices) onViewed(images[idx].path) }
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
                           .pointerInput(Unit) {
                               awaitEachGesture {
                                   awaitFirstDown(requireUnconsumed = false)
                                   userHolding = true
                                   do { val event = awaitPointerEvent() } while (event.changes.any { it.pressed })
                                   userHolding = false
                               }
                           }
                           .nestedScroll(speedConnection)
                           .pointerInput(images, columns, scrollSpeed) {
                detectDragGestures(
                    onDragStart = { drag = Offset.Zero; userHolding = true },
                    onDrag = { change, amount ->
                        drag += amount
                        if (abs(drag.x) > abs(drag.y) * 1.4f) change.consume()
                    },
                    onDragEnd = {
                        when {
                            drag.x < -150f && abs(drag.x) > abs(drag.y) * 1.5f -> onSwipeLeft()
                            drag.x > 150f && abs(drag.x) > abs(drag.y) * 1.5f -> onSwipeRight()
                        }
                        drag = Offset.Zero
                        userHolding = false
                    },
                    onDragCancel = { drag = Offset.Zero; userHolding = false }
                )
            },
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        itemsIndexed(
            images,
            key = { _, it -> it.path },
            span = { _, img -> if (img.width > img.height && columns > 1) GridItemSpan(landscapeSpan(columns)) else GridItemSpan(1) }
        ) { _, img -> ImageTile(img, selection, onSelection, isFavorite, isRead, onFavorite, onOpenFull) }
    }
        val visible = state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
        val maxIndex = (images.size - visible).coerceAtLeast(1)
        val progress = state.firstVisibleItemIndex.toFloat() / maxIndex
        RightScrollProgressBar(progress, visibleFraction = (visible.toFloat() / images.size.coerceAtLeast(1)).coerceAtMost(1f))
        AutoBrowseControls(
            running = autoBrowseRunning,
            speed = autoBrowseSpeed,
            showSlider = showAutoSlider,
            onToggle = { onToggleAutoBrowse(); showAutoSlider = true; autoSliderTouched = false },
            onSpeed = { onAutoBrowseSpeed(it); autoSliderTouched = true; showAutoSlider = true }
        )
    }
}

private fun landscapeSpan(columns: Int): Int = when {
    columns <= 1 -> 1
    columns <= 3 -> columns
    columns <= 5 -> 2
    else -> 3
}.coerceAtMost(columns)

private fun titleToken(images: List<ImageFile>): String = images.firstOrNull()?.path.orEmpty()

@Composable
private fun AutoBrowseControls(running: Boolean, speed: Float, showSlider: Boolean, onToggle: () -> Unit, onSpeed: (Float) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(34.dp),
            color = if (running) Color(0xFFFFB000) else Color(0xAA111111),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            onClick = onToggle
        ) { Box(contentAlignment = Alignment.Center) { Text(if (running) "Ⅱ" else "▶", color = if (running) Color.Black else Color(0xFFFFB000), fontSize = 14.sp) } }
        if (showSlider) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp).fillMaxWidth(0.72f).height(34.dp),
                color = Color(0xCC111111),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("自动", color = Color(0xFFFFB000), fontSize = 11.sp)
                    Slider(
                        value = speed,
                        onValueChange = onSpeed,
                        valueRange = 1f..20f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = Color(0xFFFFB000), activeTrackColor = Color(0xFFFFB000), inactiveTrackColor = Color(0xFF444444))
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageTile(img: ImageFile, selection: Set<String>, onSelection: (Set<String>) -> Unit, isFavorite: (String)->Boolean, isRead: (String)->Boolean, onFavorite:(String)->Unit, onOpenFull:(ImageFile)->Unit) {
    val ctx = LocalContext.current
    var flash by remember(img.path) { mutableStateOf(false) }
    val flashAlpha by animateFloatAsState(if (flash) 0.28f else 0f, animationSpec = tween(180), label = "favorite-flash")
    LaunchedEffect(flash) { if (flash) { delay(160); flash = false } }
    val selected = img.path in selection
    Box(Modifier.fillMaxWidth().background(Color(0xFF111111)).pointerInput(img.path, selection) { detectTapGestures(onTap = { if (selection.isNotEmpty()) onSelection(if (selected) selection - img.path else selection + img.path) else { val was = isFavorite(img.path); onFavorite(img.path); if (!was) flash = true } }, onLongPress = { onSelection(if (selected) selection - img.path else selection + img.path) }, onDoubleTap = { if (selection.isEmpty()) onOpenFull(img) }) }) {
        AsyncImage(
            model = ImageRequest.Builder(ctx).data("file://${img.path}").crossfade(false).allowHardware(true).precision(Precision.INEXACT).build(),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth
        )
        if (flashAlpha > 0f) Box(Modifier.matchParentSize().background(Color.White.copy(alpha = flashAlpha)))
        if (selected) {
            Box(Modifier.matchParentSize().background(Color(0x55FFB000)))
            Text("✓", color = Color.Black, fontSize = 22.sp, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        if (isFavorite(img.path)) Icon(Icons.Default.Favorite, null, Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp), tint = Color(0xFFFF2B2B))
    }
}

@Composable
private fun TrackScroll(index: Int, offset: Int, onUp: () -> Unit, onDown: () -> Unit) {
    var lastPacked by remember { mutableLongStateOf(index.toLong() * 1_000_000L + offset) }
    LaunchedEffect(index, offset) {
        val now = index.toLong() * 1_000_000L + offset
        val diff = now - lastPacked
        if (diff > 18) onUp() else if (diff < -18) onDown()
        lastPacked = now
    }
}
