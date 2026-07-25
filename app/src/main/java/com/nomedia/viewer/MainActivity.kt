package com.nomedia.viewer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
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
        AppLogger.init(applicationContext)
        val repo = AppRepository(applicationContext)
        val storage = StorageManager(applicationContext)
        val network = NetworkFolderManager(applicationContext)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = AccentBlue, secondary = AccentGold, background = DarkBackground, surface = DarkSurface, onBackground = TextPrimary, onSurface = TextPrimary)) {
                val vm: MainViewModel = viewModel(factory = MainViewModel.Factory(repo, storage, network))
                val state by vm.state.collectAsState()
                var showSplash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    checkPermissions()
                    delay(520)
                    showSplash = false
                }

                BackHandler(enabled = state.fullscreenImage != null) {
                    vm.closeFullscreen()
                }
                BackHandler(enabled = state.fullscreenImage == null && state.currentTab == 0) {
                    vm.setTab(1)
                }

                state.fullscreenImage?.let { img ->
                    FullscreenViewer(
                        img,
                        vm.isFavorite(img.path),
                        onToggleFavorite = { vm.toggleFavorite(img.path) },
                        onNext = { vm.showNextFullscreen() },
                        onPrevious = { vm.showPreviousFullscreen() },
                        onDismiss = { vm.closeFullscreen() }
                    )
                    return@MaterialTheme
                }

                val tabs = listOf(
                    TabItem("浏览", Icons.Default.PhotoLibrary, Icons.Outlined.Image),
                    TabItem("未浏览", Icons.Default.Folder, Icons.Outlined.Folder),
                    TabItem("已浏览", Icons.Default.Folder, Icons.Outlined.Folder),
                    TabItem("收藏", Icons.Default.Favorite, Icons.Outlined.FavoriteBorder),
                    TabItem("设置", Icons.Default.Settings, Icons.Outlined.Settings)
                )
                Box {
                    Scaffold(
                    containerColor = DarkBackground,
                    bottomBar = {
                        AnimatedVisibility(
                            visible = state.bottomBarVisible,
                            enter = slideInVertically(animationSpec = tween(90)) { it } + fadeIn(animationSpec = tween(70)),
                            exit = slideOutVertically(animationSpec = tween(70)) { it } + fadeOut(animationSpec = tween(50))
                        ) {
                            Surface(color = Color.Black, tonalElevation = 0.dp) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    tabs.forEachIndexed { idx, tab ->
                                        val selected = state.currentTab == idx
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clickable { vm.setTab(idx) },
                                            color = if (selected) Color(0xFFFFB000) else Color(0xFF111111),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    tab.label,
                                                    color = if (selected) Color.Black else Color(0xFFFFB000),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
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
                                columns = state.columns,
                                onColumnsChange = { vm.setColumns(it) },
                                scrollSpeed = state.scrollSpeed,
                                isFavorite = { vm.isFavorite(it) },
                                isRead = { it in state.viewed },
                                onFavorite = { vm.toggleFavorite(it) },
                                onOpenFull = { vm.openFullscreen(it) },
                                onViewed = { vm.markRead(it) },
                                onSwipeLeft = { vm.goNextAlbumIfPossible() },
                                onSwipeRight = { vm.goPreviousAlbumIfPossible() },
                                onBack = { vm.setTab(1) },
                                onScrollUp = { vm.setBottomVisible(false) },
                                onScrollDown = { vm.setBottomVisible(true) }
                            )
                            1 -> {
                                val unreadAlbums = state.albums.filter { album -> album.imagePaths.none { it in state.viewed } }
                                FolderBrowserScreen(
                                    albums = unreadAlbums,
                                    viewed = state.viewed,
                                    loading = state.loading,
                                    message = state.message ?: "没有未浏览文件夹",
                                    onAlbumClick = { vm.browseAlbum(it) },
                                    onAlbumLongClick = { vm.markAlbumRead(it) }
                                )
                            }
                            2 -> {
                                val readAlbums = state.albums.filter { album -> album.imagePaths.any { it in state.viewed } }
                                FolderBrowserScreen(
                                    albums = readAlbums,
                                    viewed = state.viewed,
                                    loading = state.loading,
                                    message = state.message ?: "还没有已浏览文件夹",
                                    onAlbumClick = { vm.browseAlbum(it) },
                                    onAlbumLongClick = { vm.unmarkAlbumRead(it) }
                                )
                            }
                            3 -> FavoritesScreen(
                                favorites = vm.favoriteImages(),
                                columns = state.favoriteColumns,
                                onColumnsChange = { vm.setFavoriteColumns(it) },
                                onImageClick = { vm.openFullscreen(it) }
                            )
                            4 -> SettingsScreen(
                                roots = state.roots,
                                networkFolders = state.networkFolders,
                                storageConfig = state.storageConfig,
                                columns = state.columns,
                                favoriteColumns = state.favoriteColumns,
                                scrollSpeed = state.scrollSpeed,
                                onColumns = { vm.setColumns(it) },
                                onFavoriteColumns = { vm.setFavoriteColumns(it) },
                                onScrollSpeed = { vm.setScrollSpeed(it) },
                                onAddRoot = { vm.addRoot(it) },
                                onAddNetworkFolder = { type, name, url, user, pass, cb -> vm.addNetworkFolderValidated(type, name, url, user, pass, cb) },
                                onUpdateNetworkFolder = { id, type, name, url, user, pass, enabled, cb -> vm.updateNetworkFolderValidated(id, type, name, url, user, pass, enabled, cb) },
                                onRemoveNetworkFolder = { vm.removeNetworkFolder(it) },
                                onExportLogs = { exportLogs() },
                                onRefreshNetwork = { vm.refreshWithNetwork() },
                                onClearNetworkRecovery = { vm.clearNetworkRecovery() },
                                onNetworkEnabled = { id, en -> vm.setNetworkFolderEnabled(id, en) },
                                onProbeNetwork = { type, url, user, pass, cb -> vm.probeNetworkFolder(type, url, user, pass, cb) },
                                onScanLan = { cb -> vm.scanLan(cb) },
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
                state.transientNotice?.let { notice ->
                    Box(Modifier.matchParentSize(), contentAlignment = Alignment.TopCenter) {
                        Surface(
                            modifier = Modifier.padding(top = 22.dp),
                            color = Color(0xCC111111),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
                        ) {
                            Text(notice, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                }
                AnimatedVisibility(
                    visible = showSplash,
                    exit = fadeOut(animationSpec = tween(160))
                ) {
                    Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                        Surface(Modifier.matchParentSize(), color = DarkBackground) {}
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFFFB000), modifier = Modifier.padding(bottom = 14.dp))
                            Text("Yellow-gallery", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.padding(5.dp))
                            Text("正在进入…", color = Color(0xFFBDBDBD), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
    }

    private fun exportLogs() {
        val file = AppLogger.exportFile(applicationContext)
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Yellow-gallery 日志文件")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "导出日志"))
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
