package com.nomedia.viewer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppState(
    val currentTab: Int = 1,
    val roots: List<RootFolder> = emptyList(),
    val albums: List<FolderAlbum> = emptyList(),
    val selectedAlbumPaths: Set<String> = emptySet(),
    val browsingTitle: String = "",
    val currentAlbumPath: String? = null,
    val images: List<ImageFile> = emptyList(),
    val viewed: Set<String> = emptySet(),
    val favorites: Set<String> = emptySet(),
    val columns: Int = 1,
    val bottomBarVisible: Boolean = true,
    val loading: Boolean = false,
    val message: String? = null,
    val transientNotice: String? = null,
    val storageConfig: StorageConfig = StorageConfig(),
    val fullscreenImage: ImageFile? = null
)

class MainViewModel(private val repo: AppRepository, private val storage: StorageManager) : ViewModel() {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()
    private var scanJob: Job? = null
    private var noticeJob: Job? = null
    private var rootsSignature: String = ""

    init { reloadBasics(); scanAlbums(force = true) }

    fun reloadBasics() {
        _state.value = _state.value.copy(
            roots = repo.roots(), favorites = repo.favorites(), viewed = repo.viewed(),
            columns = repo.columns(), storageConfig = repo.storageConfig()
        )
    }

    fun setTab(tab: Int) {
        _state.value = _state.value.copy(currentTab = tab, bottomBarVisible = true)
        if (tab == 1) scanAlbums(force = false)
    }

    fun addRoot(uri: Uri) { val r = repo.addRoot(uri); reloadBasics(); rootsSignature = ""; _state.value = _state.value.copy(message = if (r == null) "无法解析该目录路径" else "已添加：${r.name}"); scanAlbums(true) }
    fun removeRoot(uri: String) { repo.removeRoot(uri); reloadBasics(); rootsSignature = ""; scanAlbums(true) }
    fun setRootEnabled(uri: String, enabled: Boolean) { repo.setRootEnabled(uri, enabled); reloadBasics(); rootsSignature = ""; scanAlbums(true) }

    fun scanAlbums(force: Boolean = false) {
        val roots = repo.roots()
        val sig = roots.filter { it.enabled }.joinToString("|") { it.path }
        if (!force && sig == rootsSignature && _state.value.albums.isNotEmpty()) return
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null, roots = roots, viewed = repo.viewed())
            if (roots.none { it.enabled }) {
                _state.value = _state.value.copy(loading = false, albums = emptyList(), message = if (roots.isEmpty()) "请先在设置中添加手机文件夹" else "请启用至少一个根目录")
                return@launch
            }
            val albums = repo.scanAlbums()
            rootsSignature = sig
            _state.value = _state.value.copy(loading = false, albums = albums, viewed = repo.viewed(), message = if (albums.isEmpty()) "没有扫描到含图片的子目录" else null)
        }
    }

    fun toggleAlbum(path: String) { markAlbumRead(path) }
    fun browseAlbum(path: String) = browsePaths(listOf(path), java.io.File(path).name.ifBlank { "图片" }, path)
    fun browseSelectedAlbums() { /* v0.15: 合并浏览已取消 */ }

    fun markAlbumRead(path: String) {
        val album = _state.value.albums.find { it.path == path } ?: return
        album.imagePaths.forEach { repo.markViewed(it) }
        _state.value = _state.value.copy(viewed = repo.viewed(), transientNotice = "已标记已读：${album.name}")
        noticeJob?.cancel()
        noticeJob = viewModelScope.launch { delay(1400); _state.value = _state.value.copy(transientNotice = null) }
    }

    private fun browsePaths(paths: List<String>, title: String, albumPath: String?) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, message = null)
        val imgs = repo.scanImages(paths)
        _state.value = _state.value.copy(currentTab = 0, browsingTitle = title, currentAlbumPath = albumPath, images = imgs, viewed = repo.viewed(), favorites = repo.favorites(), loading = false, bottomBarVisible = true, message = if (imgs.isEmpty()) "该文件夹没有图片" else null)
    }

    fun markRead(path: String) { repo.markViewed(path); _state.value = _state.value.copy(viewed = repo.viewed()) }
    fun resetViewed() { repo.resetViewed(); _state.value = _state.value.copy(viewed = emptySet(), message = "已重置阅读标记") }

    fun goNextAlbumIfPossible() {
        val current = _state.value.currentAlbumPath ?: return
        val albums = _state.value.albums
        val idx = albums.indexOfFirst { it.path == current }
        if (idx >= 0 && idx < albums.lastIndex) {
            val next = albums[idx + 1]
            showNotice("已进入下一个文件夹：${next.name}")
            browseAlbum(next.path)
        }
    }

    private fun showNotice(text: String) {
        noticeJob?.cancel()
        _state.value = _state.value.copy(transientNotice = text)
        noticeJob = viewModelScope.launch { delay(2000); _state.value = _state.value.copy(transientNotice = null) }
    }

    fun toggleFavorite(path: String) {
        val added = repo.toggleFavorite(path); _state.value = _state.value.copy(favorites = repo.favorites())
        if (added) { val img = _state.value.images.find { it.path == path } ?: repo.favoriteImages().find { it.path == path }; if (img != null) viewModelScope.launch { storage.saveFavoriteImage(img.path, img.name, repo.storageConfig()) } }
    }
    fun favoriteImages(): List<ImageFile> = repo.favoriteImages()
    fun isFavorite(path: String): Boolean = path in repo.favorites()
    fun setColumns(c: Int) { repo.setColumns(c); _state.value = _state.value.copy(columns = repo.columns()) }
    fun setBottomVisible(v: Boolean) { if (_state.value.bottomBarVisible != v) _state.value = _state.value.copy(bottomBarVisible = v) }
    fun saveStorage(c: StorageConfig) { repo.saveStorage(c); _state.value = _state.value.copy(storageConfig = c, message = "已保存设置") }
    fun testWebDav(url: String, user: String, pass: String, cb: (String) -> Unit) = viewModelScope.launch { cb(storage.testWebDavAuto(url, user, pass)) }
    fun pathFromUri(uri: Uri): String? = repo.uriToPath(uri.toString())
    fun openFullscreen(img: ImageFile) { _state.value = _state.value.copy(fullscreenImage = img) }
    fun closeFullscreen() { _state.value = _state.value.copy(fullscreenImage = null) }

    class Factory(private val repo: AppRepository, private val storage: StorageManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(repo, storage) as T
    }
}
