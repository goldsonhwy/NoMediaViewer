package com.nomedia.viewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.nomedia.viewer.ImageFile
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun BrowseScreen(
    title: String,
    images: List<ImageFile>,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    isFavorite: (String) -> Boolean,
    isRead: (String) -> Boolean,
    onFavorite: (String) -> Unit,
    onOpenFull: (ImageFile) -> Unit,
    onViewed: (String) -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onBack: () -> Unit,
    onScrollUp: () -> Unit,
    onScrollDown: () -> Unit
) {
    if (images.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("请先从文件夹页选择一个相册", color = Color(0xFF888888)) }
        return
    }
    var dragX by remember { mutableFloatStateOf(0f) }
    var lastZoomChange by remember { mutableLongStateOf(0L) }
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
            .pointerInput(images) {
                detectDragGestures(
                    onDragStart = { dragX = 0f },
                    onDrag = { change, dragAmount -> dragX += dragAmount.x; change.consume() },
                    onDragEnd = {
                        when {
                            dragX < -120f -> onSwipeLeft()
                            dragX > 120f -> onSwipeRight()
                        }
                        dragX = 0f
                    },
                    onDragCancel = { dragX = 0f }
                )
            }
    ) {
        if (columns > 1) MultiColumn(images, columns, isFavorite, isRead, onFavorite, onOpenFull, onViewed, onScrollUp, onScrollDown)
        else OneColumn(images, isFavorite, isRead, onFavorite, onOpenFull, onViewed, onScrollUp, onScrollDown)
    }
}

@Composable
private fun OneColumn(images: List<ImageFile>, isFavorite: (String)->Boolean, isRead: (String)->Boolean, onFavorite:(String)->Unit, onOpenFull:(ImageFile)->Unit, onViewed:(String)->Unit, onScrollUp:()->Unit, onScrollDown:()->Unit) {
    val state = rememberLazyListState()
    TrackScroll(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset, onScrollUp, onScrollDown)
    LaunchedEffect(state, images) {
        snapshotFlow { state.firstVisibleItemIndex }.distinctUntilChanged().collect { idx ->
            if (idx in images.indices) onViewed(images[idx].path)
            if (idx > 0 && idx - 1 in images.indices) onViewed(images[idx - 1].path)
        }
    }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize().background(Color.Black), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        itemsIndexed(images, key = { _, it -> it.path }) { _, img -> ImageTile(img, isFavorite, isRead, onFavorite, onOpenFull) }
    }
}

@Composable
private fun MultiColumn(images: List<ImageFile>, columns: Int, isFavorite: (String)->Boolean, isRead: (String)->Boolean, onFavorite:(String)->Unit, onOpenFull:(ImageFile)->Unit, onViewed:(String)->Unit, onScrollUp:()->Unit, onScrollDown:()->Unit) {
    val state = rememberLazyGridState()
    TrackScroll(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset, onScrollUp, onScrollDown)
    LaunchedEffect(state, images) {
        snapshotFlow { state.firstVisibleItemIndex }.distinctUntilChanged().collect { first ->
            val start = (first / columns) * columns
            ((start - columns) until (start + columns)).forEach { idx ->
                if (idx in images.indices) onViewed(images[idx].path)
            }
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns), state = state, modifier = Modifier.fillMaxSize().background(Color.Black),
        horizontalArrangement = Arrangement.spacedBy(1.dp), verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        itemsIndexed(
            images,
            key = { _, it -> it.path },
            span = { _, img -> if (img.width > img.height && columns > 1) GridItemSpan(landscapeSpan(columns)) else GridItemSpan(1) }
        ) { _, img ->
            ImageTile(img, isFavorite, isRead, onFavorite, onOpenFull)
        }
    }
}

private fun landscapeSpan(columns: Int): Int = when {
    columns <= 1 -> 1
    columns <= 3 -> columns
    columns <= 5 -> 2
    else -> 3
}.coerceAtMost(columns)

@Composable
private fun ImageTile(img: ImageFile, isFavorite: (String)->Boolean, isRead: (String)->Boolean, onFavorite:(String)->Unit, onOpenFull:(ImageFile)->Unit) {
    val ctx = LocalContext.current
    Box(Modifier.fillMaxWidth().background(Color(0xFF111111)).pointerInput(img.path) { detectTapGestures(onTap = { onFavorite(img.path) }, onDoubleTap = { onOpenFull(img) }) }) {
        AsyncImage(
            model = ImageRequest.Builder(ctx).data("file://${img.path}").crossfade(false).allowHardware(true).precision(Precision.INEXACT).build(),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth
        )
        if (isRead(img.path)) GreenCorner(modifier = Modifier.align(Alignment.TopStart), topRight = false, size = 14.dp)
        if (isFavorite(img.path)) Icon(Icons.Default.Favorite, null, Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp), tint = Color(0xFFFFB000))
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
