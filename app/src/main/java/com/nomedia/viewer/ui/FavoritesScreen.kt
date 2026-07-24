package com.nomedia.viewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
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
import coil.size.Precision
import com.nomedia.viewer.ImageFile

@Composable
fun FavoritesScreen(
    favorites: List<ImageFile>,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    onImageClick: (ImageFile) -> Unit
) {
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
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceIn(1, 5)),
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(columns) {
                detectTransformGestures { _, _, zoom, _ ->
                    val now = System.currentTimeMillis()
                    if (now - lastZoomChange > 260) {
                        when {
                            zoom < 0.92f -> { onColumnsChange((columns + 1).coerceAtMost(5)); lastZoomChange = now }
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
            span = { img -> if (img.width > img.height && columns > 1) GridItemSpan(columns) else GridItemSpan(1) }
        ) { img ->
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data("file://${img.path}").crossfade(false).allowHardware(true).precision(Precision.INEXACT).build(),
                contentDescription = img.name,
                modifier = Modifier.fillMaxWidth().background(Color(0xFF111111)).clickable { onImageClick(img) },
                contentScale = ContentScale.FillWidth
            )
        }
    }
}
