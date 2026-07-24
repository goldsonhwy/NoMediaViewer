package com.nomedia.viewer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nomedia.viewer.ui.BrowseScreen
import com.nomedia.viewer.ui.FavoritesScreen
import com.nomedia.viewer.ui.theme.*

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(this, "权限已获取 ✅", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "需要存储权限才能扫描 .nomedia 图片", Toast.LENGTH_LONG).show()
        }
    }

    private val requestManageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Permission screen dismissed - check if granted and rescan
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                Toast.makeText(this, "权限已获取 ✅", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = ImageRepository(applicationContext)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = AccentBlue,
                    secondary = AccentGold,
                    background = DarkBackground,
                    surface = DarkSurface,
                    onPrimary = TextPrimary,
                    onBackground = TextPrimary,
                    onSurface = TextPrimary
                )
            ) {
                val viewModel: MainViewModel = viewModel(
                    factory = MainViewModel.Factory(repository)
                )
                val state by viewModel.state.collectAsState()
                var selectedTab by remember { mutableStateOf(0) }

                // Check permissions on first composition and request if needed
                LaunchedEffect(Unit) {
                    requestPermissions(viewModel)
                }

                Scaffold(
                    containerColor = DarkBackground,
                    bottomBar = {
                        NavigationBar(
                            containerColor = DarkSurface,
                            contentColor = TextPrimary
                        ) {
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                                label = { Text("浏览") },
                                selected = selectedTab == 0,
                                onClick = {
                                    selectedTab = 0
                                    viewModel.setViewMode(ViewMode.SCROLL)
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AccentGold,
                                    selectedTextColor = AccentGold,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                    indicatorColor = AccentBlue
                                )
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Outlined.FavoriteBorder, contentDescription = null) },
                                label = { Text("收藏") },
                                selected = selectedTab == 1,
                                onClick = {
                                    selectedTab = 1
                                    viewModel.setViewMode(ViewMode.FAVORITES)
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = FavoriteRed,
                                    selectedTextColor = FavoriteRed,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                    indicatorColor = AccentBlue
                                )
                            )
                        }
                    }
                ) { paddingValues ->
                    when {
                        state.isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(paddingValues),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = AccentGold,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text("正在扫描 .nomedia 文件夹...", color = TextSecondary)
                                }
                            }
                        }

                        state.error != null && state.images.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(paddingValues),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = AccentGold
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        state.error!!,
                                        color = TextSecondary,
                                        textAlign = TextAlign.Center,
                                        fontSize = 15.sp
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "请先授予存储权限，然后重新扫描",
                                        color = TextSecondary,
                                        textAlign = TextAlign.Center,
                                        fontSize = 13.sp
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            requestPermissions(viewModel)
                                            viewModel.loadImages()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AccentBlue
                                        )
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("授予权限并重新扫描")
                                    }
                                }
                            }
                        }

                        selectedTab == 0 -> {
                            val unviewedImages = state.unviewedImages
                            BrowseScreen(
                                images = if (unviewedImages.isNotEmpty()) unviewedImages else state.images,
                                unviewedCount = unviewedImages.size,
                                totalCount = state.images.size,
                                isFavorite = { viewModel.isFavorite(it) },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onMarkViewed = { viewModel.markAsViewed(it) },
                                onReset = { viewModel.resetHistory() },
                                onScanAgain = { viewModel.loadImages() }
                            )
                        }

                        selectedTab == 1 -> {
                            FavoritesScreen(
                                favorites = viewModel.getFavoriteImages(),
                                isFavorite = { viewModel.isFavorite(it) },
                                onToggleFavorite = { viewModel.toggleFavorite(it) }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestPermissions(viewModel: MainViewModel? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+): Need MANAGE_EXTERNAL_STORAGE
            // Note: API 33+ is also API 30+, so this covers all modern Android
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                requestManageStorageLauncher.launch(intent)
            } else {
                // Permission already granted, trigger scan
                viewModel?.loadImages()
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ specific media permissions (never reached in current logic,
            // kept for completeness)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                viewModel?.loadImages()
            }
        } else {
            // Android 10 and below
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                viewModel?.loadImages()
            }
        }
    }
}
