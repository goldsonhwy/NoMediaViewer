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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nomedia.viewer.ui.*
import com.nomedia.viewer.ui.theme.*

class MainActivity : ComponentActivity() {

    private val requestManageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                Toast.makeText(this, "权限已获取 ✅", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) Toast.makeText(this, "权限已获取 ✅", Toast.LENGTH_SHORT).show()
        else Toast.makeText(this, "需要存储权限才能扫描图片", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = ImageRepository(applicationContext)
        val folderManager = FolderManager(applicationContext)
        val storageManager = StorageManager(applicationContext)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = AccentBlue, secondary = AccentGold,
                    background = DarkBackground, surface = DarkSurface,
                    onPrimary = TextPrimary, onBackground = TextPrimary, onSurface = TextPrimary
                )
            ) {
                val viewModel: MainViewModel = viewModel(
                    factory = MainViewModel.Factory(repository, folderManager, storageManager)
                )
                val state by viewModel.state.collectAsState()

                LaunchedEffect(Unit) { checkPermissions() }

                // Fullscreen overlay
                if (state.fullscreenImage != null) {
                    FullscreenViewer(
                        image = state.fullscreenImage!!,
                        isFavorite = viewModel.isFavorite(state.fullscreenImage!!.uri),
                        onToggleFavorite = { viewModel.toggleFavorite(state.fullscreenImage!!.uri) },
                        onDismiss = { viewModel.closeFullscreen() }
                    )
                    return@MaterialTheme
                }

                val tabs = listOf(
                    TabItem2("浏览", Icons.Default.PhotoLibrary, Icons.Outlined.Image),
                    TabItem2("文件夹", Icons.Default.Folder, Icons.Outlined.Folder),
                    TabItem2("收藏", Icons.Default.Favorite, Icons.Outlined.FavoriteBorder),
                    TabItem2("设置", Icons.Default.Settings, Icons.Outlined.Settings)
                )

                Scaffold(
                    containerColor = DarkBackground,
                    bottomBar = {
                        AnimatedVisibility(
                            visible = state.showBottomBar,
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut()
                        ) {
                            NavigationBar(
                                containerColor = DarkSurface,
                                contentColor = TextPrimary
                            ) {
                                tabs.forEachIndexed { index, tab ->
                                    NavigationBarItem(
                                        icon = {
                                            Icon(
                                                if (state.currentTab == index) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = null
                                            )
                                        },
                                        label = { Text(tab.label, fontSize = 11.sp) },
                                        selected = state.currentTab == index,
                                        onClick = { viewModel.setTab(index) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = AccentGold, selectedTextColor = AccentGold,
                                            unselectedIconColor = TextSecondary, unselectedTextColor = TextSecondary,
                                            indicatorColor = AccentBlue
                                        )
                                    )
                                }
                            }
                        }
                    }
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        when (state.currentTab) {
                            0 -> BrowseScreen(
                                images = if (state.unviewedImages.isNotEmpty()) state.unviewedImages else state.images,
                                unviewedCount = state.unviewedImages.size, totalCount = state.images.size,
                                columnCount = state.columnCount,
                                isFavorite = { viewModel.isFavorite(it) },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onMarkViewed = { viewModel.markAsViewed(it) },
                                onReset = { viewModel.resetHistory() },
                                onScanAgain = { viewModel.scanFolderGroups() },
                                onScrollUp = { viewModel.setShowBottomBar(false) },
                                onScrollDown = { viewModel.setShowBottomBar(true) },
                                onBack = { viewModel.setTab(1) }
                            )

                            1 -> FolderBrowserScreen(
                                folderGroups = state.folderGroups,
                                selectedPaths = state.selectedFolderPaths,
                                onFolderClick = { path -> viewModel.browseFolder(path) },
                                onFolderLongClick = { path -> viewModel.toggleFolderSelection(path) },
                                onMergeBrowse = {
                                    val paths = state.selectedFolderPaths.toList()
                                    viewModel.browseMergedFolders(paths)
                                    viewModel.clearFolderSelection()
                                },
                                onClearSelection = { viewModel.clearFolderSelection() }
                            )

                            2 -> FavoritesScreen(
                                favorites = viewModel.getFavoriteImages(),
                                isFavorite = { viewModel.isFavorite(it) },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onImageClick = { viewModel.openFullscreen(it) }
                            )

                            3 -> SettingsScreen(
                                storageConfig = viewModel.getStorageConfig(),
                                columnCount = state.columnCount,
                                onColumnCountChange = { viewModel.setColumnCount(it) },
                                onFolderAdded = { uri -> viewModel.addFolder(uri) },
                                onSave = { viewModel.saveStorageConfig(it) }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                requestManageStorageLauncher.launch(intent)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }
}

data class TabItem2(
    val label: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector
)
