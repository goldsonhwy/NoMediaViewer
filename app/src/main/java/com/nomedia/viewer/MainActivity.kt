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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nomedia.viewer.ui.*
import com.nomedia.viewer.ui.theme.*

class MainActivity : ComponentActivity() {
    private val manageStorageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) Toast.makeText(this, "权限已获取", Toast.LENGTH_SHORT).show()
    }
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Toast.makeText(this, if (granted) "权限已获取" else "需要存储权限", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = AppRepository(applicationContext)
        val storage = StorageManager(applicationContext)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = AccentBlue, secondary = AccentGold, background = DarkBackground, surface = DarkSurface, onBackground = TextPrimary, onSurface = TextPrimary)) {
                val vm: MainViewModel = viewModel(factory = MainViewModel.Factory(repo, storage))
                val state by vm.state.collectAsState()
                LaunchedEffect(Unit) { checkPermissions() }

                state.fullscreenImage?.let { img ->
                    FullscreenViewer(img, vm.isFavorite(img.path), onToggleFavorite = { vm.toggleFavorite(img.path) }, onDismiss = { vm.closeFullscreen() })
                    return@MaterialTheme
                }

                val tabs = listOf(
                    TabItem("浏览", Icons.Default.PhotoLibrary, Icons.Outlined.Image),
                    TabItem("文件夹", Icons.Default.Folder, Icons.Outlined.Folder),
                    TabItem("收藏", Icons.Default.Favorite, Icons.Outlined.FavoriteBorder),
                    TabItem("设置", Icons.Default.Settings, Icons.Outlined.Settings)
                )
                Scaffold(
                    containerColor = DarkBackground,
                    bottomBar = {
                        AnimatedVisibility(visible = state.bottomBarVisible, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                            NavigationBar(containerColor = DarkSurface) {
                                tabs.forEachIndexed { idx, tab ->
                                    NavigationBarItem(
                                        selected = state.currentTab == idx,
                                        onClick = { vm.setTab(idx) },
                                        icon = { Icon(if (state.currentTab == idx) tab.selectedIcon else tab.unselectedIcon, null) },
                                        label = { Text(tab.label, fontSize = 11.sp) },
                                        colors = NavigationBarItemDefaults.colors(selectedIconColor = AccentGold, selectedTextColor = AccentGold, unselectedIconColor = TextSecondary, unselectedTextColor = TextSecondary, indicatorColor = AccentBlue)
                                    )
                                }
                            }
                        }
                    }
                ) { pad ->
                    Box(Modifier.padding(pad)) {
                        when (state.currentTab) {
                            0 -> BrowseScreen(
                                title = state.browsingTitle,
                                images = state.images,
                                unviewed = state.unviewed,
                                columns = state.columns,
                                isFavorite = { vm.isFavorite(it) },
                                onFavorite = { vm.toggleFavorite(it) },
                                onViewed = { vm.recordViewed(it) },
                                onBack = { vm.setTab(1) },
                                onScrollUp = { vm.setBottomVisible(false) },
                                onScrollDown = { vm.setBottomVisible(true) }
                            )
                            1 -> FolderBrowserScreen(
                                albums = state.albums,
                                selectedPaths = state.selectedAlbumPaths,
                                loading = state.loading,
                                message = state.message,
                                onAlbumClick = { vm.browseAlbum(it) },
                                onAlbumLongClick = { vm.toggleAlbum(it) },
                                onMergeBrowse = { vm.browseSelectedAlbums() }
                            )
                            2 -> FavoritesScreen(vm.favoriteImages(), onImageClick = { vm.openFullscreen(it) })
                            3 -> SettingsScreen(
                                roots = state.roots,
                                storageConfig = state.storageConfig,
                                columns = state.columns,
                                onColumns = { vm.setColumns(it) },
                                onAddRoot = { vm.addRoot(it) },
                                onRootEnabled = { uri, en -> vm.setRootEnabled(uri, en) },
                                onRemoveRoot = { vm.removeRoot(it) },
                                resolvePath = { vm.pathFromUri(it) },
                                onResetViewed = { vm.resetViewed() },
                                onSaveStorage = { vm.saveStorage(it) },
                                onTestWebDav = { url, user, pass, cb -> vm.testWebDav(url, user, pass, cb) }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) manageStorageLauncher.launch(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply { data = Uri.parse("package:$packageName") })
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}

data class TabItem(val label: String, val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector, val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector)
