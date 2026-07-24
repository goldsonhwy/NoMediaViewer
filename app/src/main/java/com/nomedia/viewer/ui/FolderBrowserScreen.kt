package com.nomedia.viewer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nomedia.viewer.FolderGroup

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderBrowserScreen(
    folderGroups: List<FolderGroup>,
    selectedPaths: Set<String>,
    onFolderClick: (String) -> Unit,
    onFolderLongClick: (String) -> Unit,
    onMergeBrowse: () -> Unit,
    onClearSelection: () -> Unit
) {
    if (folderGroups.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(72.dp), tint = Color(0xFF555555))
                Spacer(Modifier.height(12.dp))
                Text("暂无文件夹", color = Color(0xFF777777), fontSize = 18.sp)
                Spacer(Modifier.height(4.dp))
                Text("请先在「设置」中添加手机文件夹", color = Color(0xFF555555), fontSize = 13.sp)
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(folderGroups, key = { it.path }) { group ->
                val isSelected = group.path in selectedPaths
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onFolderClick(group.path) },
                            onLongClick = { onFolderLongClick(group.path) }
                        ),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                    shape = RoundedCornerShape(10.dp),
                    elevation = CardDefaults.cardElevation(if (isSelected) 4.dp else 1.dp)
                ) {
                    Box {
                        // Thumbnail
                        if (group.thumbnailPath != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(group.thumbnailPath)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = group.name,
                                modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxWidth().height(120.dp).background(Color(0xFF222222), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF444444), modifier = Modifier.size(32.dp))
                            }
                        }

                        // Selection overlay
                        if (isSelected) {
                            Box(modifier = Modifier.matchParentSize().background(Color(0x88000000), RoundedCornerShape(10.dp)))
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = Color(0xFFE94560),
                                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp)
                            )
                        }

                        // Folder name + count at bottom
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                            color = Color(0xBB000000),
                            shape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                                Text(
                                    group.name,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${group.images.size}张",
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Merge browse FAB when items selected
        if (selectedPaths.isNotEmpty()) {
            FloatingActionButton(
                onClick = onMergeBrowse,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                containerColor = Color(0xFFE94560),
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Icon(Icons.Default.MergeType, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("合并浏览 (${selectedPaths.size})", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
