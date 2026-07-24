package com.nomedia.viewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nomedia.viewer.ImageFile
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun BrowseScreen(
    title: String,
    images: List<ImageFile>,
    unviewed: List<ImageFile>,
    columns: Int,
    isFavorite: (String) -> Boolean,
    onFavorite: (String) -> Unit,
    onViewed: (String) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    onScrollUp: () -> Unit,
    onScrollDown: () -> Unit
) {
    val display = if (unviewed.isNotEmpty()) unviewed else images
    if (images.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("请先从文件夹页选择一个相册", color = Color(0xFF888888)) }
        return
    }
    Box(Modifier.fillMaxSize()) {
        if (columns == 2) TwoColumn(display, isFavorite, onFavorite, onViewed, onScrollUp, onScrollDown)
        else OneColumn(display, isFavorite, onFavorite, onViewed, onScrollUp, onScrollDown)
        TopOverlay(title, unviewed.size, images.size, onBack)
        FloatingActionButton(
            onClick = onReset,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = Color(0xFF0F3460), contentColor = Color.White
        ) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.VisibilityOff, null, Modifier.size(20.dp)); Text("重置", fontSize = 9.sp) } }
    }
}

@Composable
private fun OneColumn(images: List<ImageFile>, isFavorite: (String)->Boolean, onFavorite:(String)->Unit, onViewed:(String)->Unit, onScrollUp:()->Unit, onScrollDown:()->Unit) {
    val state = rememberLazyListState()
    TrackIndex(state.firstVisibleItemIndex, onScrollUp, onScrollDown)
    LaunchedEffect(state, images) { snapshotFlow { state.firstVisibleItemIndex }.distinctUntilChanged().collect { if (it > 0 && it <= images.size) onViewed(images[it-1].path) } }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        itemsIndexed(images, key = { _, it -> it.path }) { _, img -> ImageTile(img, isFavorite, onFavorite, Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun TwoColumn(images: List<ImageFile>, isFavorite: (String)->Boolean, onFavorite:(String)->Unit, onViewed:(String)->Unit, onScrollUp:()->Unit, onScrollDown:()->Unit) {
    val state = rememberLazyGridState()
    TrackIndex(state.firstVisibleItemIndex, onScrollUp, onScrollDown)
    LaunchedEffect(state, images) { snapshotFlow { state.firstVisibleItemIndex }.distinctUntilChanged().collect { if (it > 0 && it <= images.size) onViewed(images[it-1].path) } }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2), state = state, modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(1.dp), verticalArrangement = Arrangement.spacedBy(1.dp)
    ) { itemsIndexed(images, key = { _, it -> it.path }) { _, img -> ImageTile(img, isFavorite, onFavorite, Modifier.fillMaxWidth()) } }
}

@Composable
private fun ImageTile(img: ImageFile, isFavorite: (String)->Boolean, onFavorite:(String)->Unit, modifier: Modifier) {
    val ctx = LocalContext.current
    var pulse by remember { mutableStateOf(false) }
    Box(modifier.pointerInput(img.path) { detectTapGestures(onDoubleTap = { onFavorite(img.path); pulse = true }) }) {
        AsyncImage(
            model = ImageRequest.Builder(ctx).data("file://${img.path}").crossfade(true).build(),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().background(Color(0xFF111111)),
            contentScale = ContentScale.FillWidth
        )
        if (isFavorite(img.path) || pulse) Icon(Icons.Default.Favorite, null, Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp), tint = Color(0xFFFF6B6B))
    }
}

@Composable
private fun BoxScope.TopOverlay(title: String, remain: Int, total: Int, onBack: () -> Unit) {
    Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth(), color = Color(0xCC1A1A2E)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
            Text(title.ifBlank { "涩图品鉴" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
            Text("$remain / $total", color = Color(0xFFBBBBBB), fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
        }
    }
}

@Composable
private fun TrackIndex(index: Int, onUp: () -> Unit, onDown: () -> Unit) {
    var last by remember { mutableIntStateOf(index) }
    LaunchedEffect(index) { if (index > last) onUp() else if (index < last) onDown(); last = index }
}
