package com.nomedia.viewer.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nomedia.viewer.ImageFile
import kotlinx.coroutines.launch

@Composable
fun BrowseScreen(
    images: List<ImageFile>,
    unviewedCount: Int,
    totalCount: Int,
    isFavorite: (String) -> Boolean,
    onToggleFavorite: (String) -> Unit,
    onMarkViewed: (String) -> Unit,
    onReset: () -> Unit,
    onScanAgain: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showFavOverlay by mutableStateOf(false)
    var lastFavImage by mutableStateOf("")

    // Track visible items for marking as viewed
    val visibleItems = remember { mutableStateListOf<Int>() }
    LaunchedEffect(listState.firstVisibleItemIndex) {
        val layoutInfo = listState.layoutInfo
        val visible = layoutInfo.visibleItemsInfo.map { it.index }
        visibleItems.clear()
        visibleItems.addAll(visible)
    }

    if (images.isEmpty()) {
        // Empty state with scan button
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = Color(0xFF666666)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    if (totalCount > 0) "全部已看完 ✓" else "暂无图片",
                    fontSize = 20.sp,
                    color = if (totalCount > 0) Color(0xFF4CAF50) else Color(0xFF888888),
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (totalCount > 0) "点击重置可重新浏览所有图片" else "点击下方按钮重新扫描\n请确保已授予存储权限",
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onScanAgain,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460))
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (totalCount > 0) "重置浏览历史" else "重新扫描")
                }
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp), // No gap - seamless
            contentPadding = PaddingValues(0.dp)
        ) {
            itemsIndexed(
                items = images,
                key = { _, img -> img.path }
            ) { index, image ->
                var showHeart by remember { mutableStateOf(false) }

                // Mark as viewed when scrolled past
                LaunchedEffect(index) {
                    // Mark images as viewed when they're in the visible area
                    if (visibleItems.contains(index)) {
                        onMarkViewed(image.path)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(image.path) {
                            detectTapGestures(
                                onDoubleTap = {
                                    onToggleFavorite(image.path)
                                    showHeart = isFavorite(image.path)
                                    lastFavImage = image.path
                                }
                            )
                        }
                ) {
                    // The image - fills width, height auto
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data("file://${image.path}")
                            .crossfade(true)
                            .build(),
                        contentDescription = image.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF111111)),
                        contentScale = ContentScale.FillWidth
                    )

                    // Heart animation on double-tap
                    AnimatedVisibility(
                        visible = showHeart,
                        enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(200)),
                        exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(500)),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "Favorite",
                            modifier = Modifier.size(64.dp),
                            tint = Color(0xFFFF6B6B)
                        )
                    }

                    // File name overlay on top
                    Text(
                        text = image.name,
                        color = Color(0x88FFFFFF),
                        fontSize = 11.sp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .background(Color(0x66000000), shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )

                    // Favorite indicator
                    if (isFavorite(image.path)) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "Favorited",
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(20.dp),
                            tint = Color(0xFFFF6B6B)
                        )
                    }
                }
            }
        }

        // Top status bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            color = Color(0xCC1A1A2E),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "NoMedia Viewer",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    "$unviewedCount / $totalCount",
                    color = Color(0xFFAAAAAA),
                    fontSize = 13.sp
                )
            }
        }

        // Reset button at bottom right
        FloatingActionButton(
            onClick = onReset,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Color(0xFF0F3460),
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(6.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.VisibilityOff, contentDescription = "Reset", modifier = Modifier.size(20.dp))
                Text("重置", fontSize = 9.sp)
            }
        }

        // Image count badge
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            color = Color(0xCC1A1A2E)
        ) {
            Text(
                text = if (unviewedCount > 0) "剩余 $unviewedCount 张未看" else "全部已看完 ✓",
                color = if (unviewedCount > 0) Color(0xFFAAAAAA) else Color(0xFF4CAF50),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
