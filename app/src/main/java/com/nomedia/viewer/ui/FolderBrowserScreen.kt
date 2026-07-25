package com.nomedia.viewer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.nomedia.viewer.FolderAlbum

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderBrowserScreen(
    albums: List<FolderAlbum>,
    viewed: Set<String>,
    loading: Boolean,
    message: String?,
    selectedPaths: Set<String> = emptySet(),
    selectionMode: Boolean = false,
    onAlbumClick: (String) -> Unit,
    onAlbumLongClick: (String) -> Unit
) {
    val gridState = rememberLazyGridState()
    Box(Modifier.fillMaxSize()) {
        when {
            loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = Color(0xFFFFB000)); Spacer(Modifier.height(10.dp)); Text("读取中…", color = Color.White) } }
            albums.isEmpty() -> EmptyFolder(message ?: "请先在设置中添加手机文件夹")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(albums, key = { it.path }) { album ->
                    val allRead = album.imagePaths.isNotEmpty() && album.imagePaths.all { it in viewed }
                    val selected = album.path in selectedPaths
                    Card(
                        modifier = Modifier.combinedClickable(
                            onClick = { if (selectionMode) onAlbumLongClick(album.path) else onAlbumClick(album.path) },
                            onLongClick = { onAlbumLongClick(album.path) }
                        ),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111))
                    ) {
                        Box(Modifier.fillMaxWidth().aspectRatio(9f / 16f)) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data("file://${album.coverPath}")
                                    .crossfade(false)
                                    .allowHardware(true)
                                    .precision(Precision.INEXACT)
                                    .size(360, 640)
                                    .build(),
                                contentDescription = album.name,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            if (allRead) GreenCorner(modifier = Modifier.align(Alignment.TopStart), topRight = false, size = 18.dp)
                            if (selected) {
                                Box(Modifier.matchParentSize().background(Color(0x66FFB000)))
                                Text("✓", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                            }
                            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = Color(0x88000000)) {
                                Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(album.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text("${album.count}", color = Color(0xFFEFEFEF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
        val progress = if (albums.size <= 1) 1f else gridState.firstVisibleItemIndex.toFloat() / (albums.size - 1).coerceAtLeast(1)
        RightScrollProgressBar(progress)
    }
}

@Composable
private fun EmptyFolder(text: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.FolderOff, null, Modifier.size(70.dp), tint = Color(0xFF555555))
            Spacer(Modifier.height(12.dp))
            Text("暂无文件夹", color = Color(0xFF888888), fontSize = 18.sp)
            Spacer(Modifier.height(4.dp))
            Text(text, color = Color(0xFF666666), fontSize = 13.sp)
        }
    }
}
