package com.nomedia.viewer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.nomedia.viewer.ImageFile

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesScreen(
    favorites: List<ImageFile>,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    onImageClick: (ImageFile) -> Unit,
    onUnfavoriteSelected: (Set<String>) -> Unit,
    onDeleteSelected: (Set<String>) -> Unit
) {
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var savedIndex by rememberSaveable { mutableIntStateOf(0) }
    var savedOffset by rememberSaveable { mutableIntStateOf(0) }
    val state = rememberLazyGridState(initialFirstVisibleItemIndex = savedIndex, initialFirstVisibleItemScrollOffset = savedOffset)
    LaunchedEffect(state) { snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }.collect { (i, o) -> savedIndex = i; savedOffset = o } }
    if (favorites.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.FavoriteBorder, null, Modifier.size(72.dp), tint = Color(0xFF666666))
                Spacer(Modifier.height(12.dp)); Text("暂无收藏", color = Color(0xFF888888), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("浏览时单击图片加入收藏", color = Color(0xFF666666), fontSize = 13.sp)
            }
        }
        return
    }
    var lastZoomChange by remember { mutableLongStateOf(0L) }
    Column(Modifier.fillMaxSize()) {
        if (selection.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().background(Color(0xFF111111)).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("已选 ${selection.size}", color = Color.White, modifier = Modifier.weight(1f))
                Button(shape = RoundedCornerShape(8.dp), onClick = { selection = favorites.map { it.path }.toSet() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB000), contentColor = Color.Black)) { Text("全选") }
                Button(shape = RoundedCornerShape(8.dp), onClick = { onUnfavoriteSelected(selection); selection = emptySet() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202020), contentColor = Color(0xFFFFB000))) { Text("取消收藏") }
                Button(shape = RoundedCornerShape(8.dp), onClick = { onDeleteSelected(selection); selection = emptySet() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202020), contentColor = Color(0xFFFFB000))) { Text("删除") }
            }
        }
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
            columns = GridCells.Fixed(columns.coerceIn(1, 6)),
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
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
                },
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(
                favorites,
                key = { it.path },
                span = { img -> if (img.width > img.height && columns > 1) GridItemSpan(landscapeSpan(columns)) else GridItemSpan(1) }
            ) { img ->
                val selected = img.path in selection
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF111111))
                        .combinedClickable(
                            onClick = { if (selection.isNotEmpty()) selection = if (selected) selection - img.path else selection + img.path else onImageClick(img) },
                            onLongClick = { selection = if (selected) selection - img.path else selection + img.path }
                        )
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data("file://${img.path}").crossfade(false).allowHardware(true).precision(Precision.INEXACT).build(),
                        contentDescription = img.name,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)),
                        contentScale = ContentScale.FillWidth
                    )
                    if (selected) {
                        Box(Modifier.matchParentSize().background(Color(0x66FFB000)))
                        Text("✓", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                    }
                }
            }
        }
            val visible = state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
            val maxIndex = (favorites.size - visible).coerceAtLeast(1)
            val progress = state.firstVisibleItemIndex.toFloat() / maxIndex
            RightScrollProgressBar(progress, visibleFraction = (visible.toFloat() / favorites.size.coerceAtLeast(1)).coerceAtMost(1f))
        }
    }
}

private fun landscapeSpan(columns: Int): Int = when {
    columns <= 1 -> 1
    columns <= 3 -> columns
    columns <= 5 -> 2
    else -> 3
}.coerceAtMost(columns)
