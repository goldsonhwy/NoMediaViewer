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
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
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
                var showSplash by remember { mutableStateOf(false) }
                var showExitDialog by remember { mutableStateOf(false) }
                var readSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
                var unreadSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
                LaunchedEffect(Unit) {
                    checkPermissions()
                }

                BackHandler(enabled = state.fullscreenImage != null) {
                    vm.closeFullscreen()
                }
                BackHandler(enabled = state.fullscreenImage == null && state.currentTab == 0) {
                    vm.setTab(state.browseReturnTab)
                }
                BackHandler(enabled = state.fullscreenImage == null && state.currentTab != 0 && unreadSelection.isEmpty() && readSelection.isEmpty()) {
                    showExitDialog = true
                }
                BackHandler(enabled = state.fullscreenImage == null && state.currentTab == 1 && unreadSelection.isNotEmpty()) { unreadSelection = emptySet() }
                BackHandler(enabled = state.fullscreenImage == null && state.currentTab == 2 && readSelection.isNotEmpty()) { readSelection = emptySet() }

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
                                autoBrowseSpeed = state.autoBrowseSpeed,
                                autoBrowseRunning = state.autoBrowseRunning,
                                onToggleAutoBrowse = { vm.toggleAutoBrowse() },
                                onAutoBrowseSpeed = { vm.setAutoBrowseSpeed(it) },
                                isFavorite = { vm.isFavorite(it) },
                                isRead = { it in state.viewed },
                                onFavorite = { vm.toggleFavorite(it) },
                                onOpenFull = { vm.openFullscreen(it) },
                                onViewed = { vm.markRead(it) },
                                onSwipeLeft = { vm.goNextAlbumIfPossible() },
                                onSwipeRight = { vm.goPreviousAlbumIfPossible() },
                                onBack = { vm.setTab(1) },
                                onScrollUp = { vm.setBottomVisible(false) },
                                onScrollDown = { vm.setBottomVisible(true) },
                                onShareSelected = { shareImages(it) },
                                onDeleteSelected = { vm.deleteBrowseImages(it) },
                                onAutoReachedEnd = { vm.autoAdvanceToNextAlbum() }
                            )
                            1 -> {
                                val unreadAlbums = state.albums.filter { album -> if (album.imagePaths.isEmpty()) vm.albumViewedAt(album.path) == 0L else album.imagePaths.none { it in state.viewed } }
                                Column(Modifier.fillMaxSize()) {
                                    if (unreadSelection.isNotEmpty()) {
                                        Row(Modifier.fillMaxWidth().background(Color(0xFF111111)).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("已选 ${unreadSelection.size}", color = Color.White, modifier = Modifier.weight(1f))
                                            Button(shape = RoundedCornerShape(8.dp), onClick = { unreadSelection = unreadAlbums.map { it.path }.toSet() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB000), contentColor = Color.Black)) { Text("全选") }
                                            Button(shape = RoundedCornerShape(8.dp), onClick = { vm.browseMergedAlbums(unreadSelection); unreadSelection = emptySet() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202020), contentColor = Color(0xFFFFB000))) { Text("合并浏览") }
                                            Button(shape = RoundedCornerShape(8.dp), onClick = { unreadSelection.forEach { vm.markAlbumRead(it) }; unreadSelection = emptySet() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202020), contentColor = Color(0xFFFFB000))) { Text("已浏览") }
                                            Button(shape = RoundedCornerShape(8.dp), onClick = { vm.deleteAlbums(unreadSelection); unreadSelection = emptySet() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202020), contentColor = Color(0xFFFFB000))) { Text("删除") }
                                        }
                                    }
                                    FolderBrowserScreen(
                                        albums = unreadAlbums,
                                        viewed = state.viewed,
                                        loading = state.loading,
                                        message = state.message ?: "没有未浏览文件夹",
                                        selectedPaths = unreadSelection,
                                        selectionMode = unreadSelection.isNotEmpty(),
                                        onAlbumClick = { vm.browseAlbumFromList(1, it, unreadAlbums.map { a -> a.path }) },
                                        onAlbumLongClick = { path -> unreadSelection = if (path in unreadSelection) unreadSelection - path else unreadSelection + path }
                                    )
                                }
                            }
                            2 -> {
                                val readAlbums = state.albums
                                    .filter { album -> if (album.imagePaths.isEmpty()) vm.albumViewedAt(album.path) > 0L else album.imagePaths.any { it in state.viewed } }
                                    .sortedByDescending { vm.albumViewedAt(it.path) }
                                Column(Modifier.fillMaxSize()) {
                                    if (readSelection.isNotEmpty()) {
                                        Row(Modifier.fillMaxWidth().background(Color(0xFF111111)).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("已选 ${readSelection.size}", color = Color.White, modifier = Modifier.weight(1f))
                                            Button(shape = RoundedCornerShape(8.dp), onClick = { readSelection = readAlbums.map { it.path }.toSet() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB000), contentColor = Color.Black)) { Text("全选") }
                                            Button(shape = RoundedCornerShape(8.dp), onClick = { vm.browseMergedAlbums(readSelection); readSelection = emptySet() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202020), contentColor = Color(0xFFFFB000))) { Text("合并浏览") }
                                            Button(shape = RoundedCornerShape(8.dp), onClick = { vm.restoreAlbumsUnread(readSelection); readSelection = emptySet() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202020), contentColor = Color(0xFFFFB000))) { Text("未浏览") }
                                            Button(shape = RoundedCornerShape(8.dp), onClick = { vm.deleteAlbums(readSelection); readSelection = emptySet() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202020), contentColor = Color(0xFFFFB000))) { Text("删除") }
                                        }
                                    }
                                    FolderBrowserScreen(
                                        albums = readAlbums,
                                        viewed = state.viewed,
                                        loading = state.loading,
                                        message = state.message ?: "还没有已浏览文件夹",
                                        selectedPaths = readSelection,
                                        selectionMode = readSelection.isNotEmpty(),
                                        onAlbumClick = { vm.browseAlbumFromList(2, it, readAlbums.map { a -> a.path }) },
                                        onAlbumLongClick = { path -> readSelection = if (path in readSelection) readSelection - path else readSelection + path }
                                    )
                                }
                            }
                            3 -> {
                                val favs = vm.favoriteImages()
                                FavoritesScreen(
                                    favorites = favs,
                                    columns = state.favoriteColumns,
                                    onColumnsChange = { vm.setFavoriteColumns(it) },
                                    onImageClick = { vm.openFullscreenFrom(it, favs) },
                                    onUnfavoriteSelected = { vm.unFavoriteMany(it) },
                                    onDeleteSelected = { vm.deleteFavoriteImages(it) }
                                )
                            }
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
                if (showExitDialog) {
                    AlertDialog(
                        onDismissRequest = { showExitDialog = false },
                        title = { Text("退出 Yellow-gallery？") },
                        text = { Text("确定要退出软件吗？") },
                        confirmButton = { Button(shape = RoundedCornerShape(8.dp), onClick = { finish() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB000), contentColor = Color.Black)) { Text("退出") } },
                        dismissButton = { Button(shape = RoundedCornerShape(8.dp), onClick = { showExitDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202020), contentColor = Color(0xFFFFB000))) { Text("取消") } },
                        containerColor = Color(0xFF111111),
                        titleContentColor = Color.White,
                        textContentColor = Color.White
                    )
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
            }
        }
    }
    }

    private fun shareImages(paths: Set<String>) {
        val uris = ArrayList(paths.mapNotNull { p ->
            runCatching { FileProvider.getUriForFile(this, "${packageName}.fileprovider", java.io.File(p)) }.getOrNull()
        })
        if (uris.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "分享图片"))
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
