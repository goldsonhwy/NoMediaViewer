package com.nomedia.viewer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nomedia.viewer.ImageFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    var imageOffset by remember(image.path) { mutableStateOf(Offset.Zero) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var direction by remember { mutableIntStateOf(1) }
    var animateSwitch by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    fun goNextAnimated() { animateSwitch = true; direction = 1; scale = 1f; imageOffset = Offset.Zero; scope.launch { delay(16); onNext() } }
    fun goPrevAnimated() { animateSwitch = true; direction = -1; scale = 1f; imageOffset = Offset.Zero; scope.launch { delay(16); onPrevious() } }
    fun goNextDirect() { animateSwitch = false; scale = 1f; imageOffset = Offset.Zero; onNext() }
    fun goPrevDirect() { animateSwitch = false; scale = 1f; imageOffset = Offset.Zero; onPrevious() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(image.path) {
                awaitEachGesture {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            val a = pressed[0]
                            val b = pressed[1]
                            val currentCentroid = Offset((a.position.x + b.position.x) / 2f, (a.position.y + b.position.y) / 2f)
                            val previousCentroid = Offset((a.previousPosition.x + b.previousPosition.x) / 2f, (a.previousPosition.y + b.previousPosition.y) / 2f)
                            val pan = currentCentroid - previousCentroid
                            val currentDistance = (a.position - b.position).getDistance()
                            val previousDistance = (a.previousPosition - b.previousPosition).getDistance().coerceAtLeast(1f)
                            val zoom = currentDistance / previousDistance
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            imageOffset = if (scale <= 1.02f) Offset.Zero else imageOffset + pan
                            if (scale <= 1.02f) scale = 1f
                            pressed.forEach { it.consume() }
                        }
                    }
                }
            }
            .pointerInput(image.path) {
                detectDragGestures(
                    onDragStart = { drag = Offset.Zero },
                    onDrag = { change, amount -> drag += amount; change.consume() },
                    onDragEnd = {
                        if (abs(drag.x) > 120f && abs(drag.x) > abs(drag.y)) {
                            if (drag.x < 0f) goNextAnimated() else goPrevAnimated()
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
                            pos.x > size.width * 0.82f -> goNextDirect()
                            pos.x < size.width * 0.18f -> goPrevDirect()
                            else -> onToggleFavorite()
                        }
                    }
                )
            }
    ) {
        AnimatedContent(
            targetState = image.path,
            transitionSpec = {
                if (!animateSwitch) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else if (direction >= 0) {
                    (slideInHorizontally(animationSpec = tween(220)) { it } + fadeIn(tween(160))) togetherWith
                        (slideOutHorizontally(animationSpec = tween(220)) { -it } + fadeOut(tween(160)))
                } else {
                    (slideInHorizontally(animationSpec = tween(220)) { -it } + fadeIn(tween(160))) togetherWith
                        (slideOutHorizontally(animationSpec = tween(220)) { it } + fadeOut(tween(160)))
                }.using(SizeTransform(clip = false))
            },
            label = "fullscreen-slide"
        ) { path ->
            AsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data("file://$path")
                    .memoryCacheKey(path)
                    .diskCacheKey(path)
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = imageOffset.x, translationY = imageOffset.y),
                contentScale = ContentScale.Fit
            )
        }
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
                tint = if (isFavorite) Color(0xFFFF2B2B) else Color.White,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}
