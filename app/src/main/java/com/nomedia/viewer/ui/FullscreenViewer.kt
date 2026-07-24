package com.nomedia.viewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nomedia.viewer.ImageFile

@Composable
fun FullscreenViewer(image: ImageFile, isFavorite: Boolean, onToggleFavorite: () -> Unit, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data("file://${image.path}").crossfade(true).build(),
            contentDescription = image.name,
            modifier = Modifier.fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationY = offsetY)
                .pointerInput(Unit) { detectTapGestures(onTap = { onToggleFavorite() }, onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f }) }
                .pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale <= 1.05f) { offsetY += pan.y; if (offsetY > 260f) onDismiss() } else offsetY = 0f
                } },
            contentScale = ContentScale.Fit
        )
        Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth(), color = Color(0x99000000)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = Color.White) }
                Text(image.name, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                IconButton(onClick = onToggleFavorite) { Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (isFavorite) Color(0xFFFFB000) else Color.White) }
            }
        }
        Surface(Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp), color = Color(0x66000000), shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
            Text("单击=取消/加入收藏  双击=缩放  下拉=退出", color = Color(0xCCFFFFFF), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
        }
    }
}
