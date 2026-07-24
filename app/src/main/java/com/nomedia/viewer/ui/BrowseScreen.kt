package com.nomedia.viewer.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nomedia.viewer.ImageFile
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun BrowseScreen(
    images: List<ImageFile>,
    unviewedCount: Int, totalCount: Int,
    columnCount: Int,
    isFavorite: (String) -> Boolean,
    onToggleFavorite: (String) -> Unit,
    onMarkViewed: (String) -> Unit,
    onReset: () -> Unit,
    onScanAgain: () -> Unit,
    onScrollUp: () -> Unit,
    onScrollDown: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    when {
        images.isEmpty() -> EmptyBrowseScreen(hasTotal = totalCount > 0, onScanAgain = onScanAgain)
        columnCount == 2 -> TwoColumnBrowseScreen(
            images = images, unviewedCount = unviewedCount, totalCount = totalCount,
            isFavorite = isFavorite, onToggleFavorite = onToggleFavorite,
            onMarkViewed = onMarkViewed, onReset = onReset, onScanAgain = onScanAgain,
            onScrollUp = onScrollUp, onScrollDown = onScrollDown, onBack = onBack
        )
        else -> SingleColumnBrowseScreen(
            images = images, unviewedCount = unviewedCount, totalCount = totalCount,
            isFavorite = isFavorite, onToggleFavorite = onToggleFavorite,
            onMarkViewed = onMarkViewed, onReset = onReset, onScanAgain = onScanAgain,
            onScrollUp = onScrollUp, onScrollDown = onScrollDown, onBack = onBack
        )
    }
}

@Composable
private fun EmptyBrowseScreen(hasTotal: Boolean, onScanAgain: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(80.dp), tint = Color(0xFF666666))
            Spacer(Modifier.height(16.dp))
            Text(if (hasTotal) "全部已看完 ✓" else "暂无图片", fontSize = 20.sp, color = if (hasTotal) Color(0xFF4CAF50) else Color(0xFF888888), fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(if (hasTotal) "点击重置可重新浏览所有图片" else "请先在「文件夹」页面勾选目录", fontSize = 14.sp, color = Color(0xFF666666), textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onScanAgain, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460))) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (hasTotal) "重置浏览历史" else "重新扫描")
            }
        }
    }
}

@Composable
private fun SingleColumnBrowseScreen(
    images: List<ImageFile>, unviewedCount: Int, totalCount: Int,
    isFavorite: (String) -> Boolean, onToggleFavorite: (String) -> Unit,
    onMarkViewed: (String) -> Unit, onReset: () -> Unit, onScanAgain: () -> Unit,
    onScrollUp: () -> Unit, onScrollDown: () -> Unit, onBack: (() -> Unit)? = null
) {
    val listState = rememberLazyListState()
    TrackScrollDirection(firstVisible = listState.firstVisibleItemIndex, onScrollUp = onScrollUp, onScrollDown = onScrollDown)
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect { firstVisible ->
            if (firstVisible > 0 && firstVisible <= images.size) onMarkViewed(images[firstVisible - 1].uri)
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp), contentPadding = PaddingValues(0.dp)) {
            itemsIndexed(images, key = { _, img -> img.uri }) { _, image ->
                ImageItemRow(image = image, isFavorite = isFavorite, onToggleFavorite = onToggleFavorite)
            }
        }
        BrowseOverlays(unviewedCount = unviewedCount, totalCount = totalCount, onReset = onReset, onBack = onBack)
    }
}

@Composable
private fun TwoColumnBrowseScreen(
    images: List<ImageFile>, unviewedCount: Int, totalCount: Int,
    isFavorite: (String) -> Boolean, onToggleFavorite: (String) -> Unit,
    onMarkViewed: (String) -> Unit, onReset: () -> Unit, onScanAgain: () -> Unit,
    onScrollUp: () -> Unit, onScrollDown: () -> Unit, onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val gridState = rememberLazyGridState()
    TrackScrollDirection(firstVisible = gridState.firstVisibleItemIndex, onScrollUp = onScrollUp, onScrollDown = onScrollDown)
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex }.distinctUntilChanged().collect { firstVisible ->
            if (firstVisible > 0 && firstVisible <= images.size) onMarkViewed(images[firstVisible - 1].uri)
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(1.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            gridItemsIndexed(images, key = { _, img -> img.uri }) { _, image ->
                AsyncImage(
                    model = ImageRequest.Builder(context).data(image.uri).crossfade(true).build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF111111))
                        .then(Modifier.pointerInput(image.uri) {
                            detectTapGestures(onDoubleTap = { onToggleFavorite(image.uri) })
                        }),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
        BrowseOverlays(unviewedCount = unviewedCount, totalCount = totalCount, onReset = onReset, onBack = onBack)
    }
}

@Composable
private fun ImageItemRow(image: ImageFile, isFavorite: (String) -> Boolean, onToggleFavorite: (String) -> Unit) {
    val context = LocalContext.current
    var showHeart by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth().pointerInput(image.uri) {
        detectTapGestures(onDoubleTap = {
            onToggleFavorite(image.uri)
            showHeart = isFavorite(image.uri)
        })
    }) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(image.uri).crossfade(true).build(),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().background(Color(0xFF111111)),
            contentScale = ContentScale.FillWidth
        )
        if (showHeart) {
            Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.align(Alignment.Center).size(64.dp), tint = Color(0xFFFF6B6B))
        }
        if (isFavorite(image.uri)) {
            Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp), tint = Color(0xFFFF6B6B))
        }
    }
}

@Composable
private fun TrackScrollDirection(firstVisible: Int, onScrollUp: () -> Unit, onScrollDown: () -> Unit) {
    var lastIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(firstVisible) {
        if (firstVisible > lastIndex) onScrollUp() else if (firstVisible < lastIndex) onScrollDown()
        lastIndex = firstVisible
    }
}

@Composable
private fun BoxScope.BrowseOverlays(unviewedCount: Int, totalCount: Int, onReset: () -> Unit, onBack: (() -> Unit)? = null) {
    // Top bar
    Surface(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter), color = Color(0xCC1A1A2E), shadowElevation = 4.dp) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Text("涩图品鉴", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text("$unviewedCount / $totalCount", color = Color(0xFFAAAAAA), fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp))
        }
    }
    // Reset FAB
    FloatingActionButton(onClick = onReset, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp), containerColor = Color(0xFF0F3460), contentColor = Color.White, elevation = FloatingActionButtonDefaults.elevation(6.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(20.dp))
            Text("重置", fontSize = 9.sp)
        }
    }
    // Count badge
    Surface(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp), shape = RoundedCornerShape(20.dp), color = Color(0xCC1A1A2E)) {
        Text(
            text = if (unviewedCount > 0) "剩余 $unviewedCount 张" else "全部已看完 ✓",
            color = if (unviewedCount > 0) Color(0xFFAAAAAA) else Color(0xFF4CAF50),
            fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
