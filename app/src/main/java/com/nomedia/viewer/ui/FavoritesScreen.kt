package com.nomedia.viewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nomedia.viewer.ImageFile

@Composable
fun FavoritesScreen(
    favorites: List<ImageFile>,
    isFavorite: (String) -> Boolean,
    onToggleFavorite: (String) -> Unit,
    onImageClick: (ImageFile) -> Unit
) {
    if (favorites.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = Color(0xFF666666)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "暂无收藏",
                    fontSize = 20.sp,
                    color = Color(0xFF888888),
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "在浏览时双击图片即可加入收藏",
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(
            items = favorites,
            key = { it.uri }
        ) { image ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onImageClick(image) },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Box {
                    // Full-size image (no crop - uses Fit for waterfall effect)
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(image.uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = image.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF111111)),
                        contentScale = ContentScale.Fit
                    )

                    // Favorite heart
                    if (isFavorite(image.uri)) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "Favorited",
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(18.dp),
                            tint = Color(0xFFFF6B6B)
                        )
                    }
                }
            }
        }
    }
}
