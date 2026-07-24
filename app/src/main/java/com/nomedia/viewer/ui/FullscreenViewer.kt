package com.nomedia.viewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nomedia.viewer.ImageFile
import kotlin.math.abs

@Composable
fun FullscreenViewer(
    image: ImageFile,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDismiss: () -> Unit
) {
    var scale by remember(image.path) { mutableFloatStateOf(1f) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data("file://${image.path}").crossfade(false).build(),
            contentDescription = image.name,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .pointerInput(image.path) {
                    detectDragGestures(
                        onDragStart = { drag = Offset.Zero },
                        onDrag = { change, amount -> drag += amount; change.consume() },
                        onDragEnd = {
                            if (abs(drag.x) > 120f && abs(drag.x) > abs(drag.y)) {
                                if (drag.x < 0f) onNext() else onPrevious()
                            }
                            drag = Offset.Zero
                        },
                        onDragCancel = { drag = Offset.Zero }
                    )
                }
                .pointerInput(image.path) {
                    detectTapGestures(
                        onTap = { pos ->
                            when {
                                pos.x > size.width * 0.82f -> onNext()
                                pos.x < size.width * 0.18f -> onPrevious()
                                else -> onToggleFavorite()
                            }
                        },
                        onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f }
                    )
                },
            contentScale = ContentScale.Fit
        )
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 18.dp)
                .size(54.dp)
        ) {
            Icon(
                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = if (isFavorite) Color(0xFFFFB000) else Color.White,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}
