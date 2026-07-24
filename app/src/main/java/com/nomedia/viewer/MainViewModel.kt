package com.nomedia.viewer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
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
    val images: List<ImageFile> = emptyList(),
    val unviewed: List<ImageFile> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val columns: Int = 1,
    val bottomBarVisible: Boolean = true,
    val loading: Boolean = false,
    val message: String? = null,
    val storageConfig: StorageConfig = StorageConfig(),
    val fullscreenImage: ImageFile? = null
)

class MainViewModel(private val repo: AppRepository, private val storage: StorageManager) : ViewModel() {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()
    private var scanJob: Job? = null
    private var rootsSignature: String = ""
    private val sessionViewed = linkedSetOf<String>()

    init { reloadBasics(); scanAlbums(force = true) }

    fun reloadBasics() {
        _state.value = _state.value.copy(
            roots = repo.roots(), favorites = repo.favorites(), columns = repo.columns(), storageConfig = repo.storageConfig()
        )
    }

    fun setTab(tab: Int) {
        if (_state.value.currentTab == 0 && tab != 0) commitSessionViewed()
        _state.value = _state.value.copy(currentTab = tab, bottomBarVisible = true)
        if (tab == 1) scanAlbums(force = false)
    }

    fun addRoot(uri: Uri) {
        val root = repo.addRoot(uri)
        reloadBasics()
        rootsSignature = "" // force rescan
        _state.value = _state.value.copy(message = if (root == null) "无法解析该目录路径" else "已添加：${root.name}")
        scanAlbums(force = true)
    }
    fun removeRoot(uri: String) { repo.removeRoot(uri); reloadBasics(); rootsSignature = ""; scanAlbums(force = true) }
    fun setRootEnabled(uri: String, enabled: Boolean) { repo.setRootEnabled(uri, enabled); reloadBasics(); rootsSignature = ""; scanAlbums(force = true) }

    fun scanAlbums(force: Boolean = false) {
        val roots = repo.roots()
        val sig = roots.filter { it.enabled }.joinToString("|") { it.path }
        if (!force && sig == rootsSignature && _state.value.albums.isNotEmpty()) return
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null, roots = roots)
            if (roots.none { it.enabled }) {
                _state.value = _state.value.copy(loading = false, albums = emptyList(), message = if (roots.isEmpty()) "请先在设置中添加手机文件夹" else "请启用至少一个根目录")
                return@launch
            }
            val albums = repo.scanAlbums()
            rootsSignature = sig
            _state.value = _state.value.copy(loading = false, albums = albums, message = if (albums.isEmpty()) "没有扫描到含图片的子目录" else null)
        }
    }

    fun toggleAlbum(path: String) {
        val s = _state.value.selectedAlbumPaths.toMutableSet()
        if (path in s) s.remove(path) else s.add(path)
        _state.value = _state.value.copy(selectedAlbumPaths = s)
    }
    fun clearAlbumSelection() { _state.value = _state.value.copy(selectedAlbumPaths = emptySet()) }
    fun browseAlbum(path: String) = browsePaths(listOf(path), java.io.File(path).name.ifBlank { "图片" })
    fun browseSelectedAlbums() {
        val paths = _state.value.selectedAlbumPaths.toList()
        if (paths.isNotEmpty()) browsePaths(paths, "合并浏览 ${paths.size} 个文件夹")
        clearAlbumSelection()
    }

    fun browsePaths(paths: List<String>, title: String) = viewModelScope.launch {
        commitSessionViewed()
        sessionViewed.clear()
        _state.value = _state.value.copy(loading = true, message = null)
        val imgs = repo.scanImages(paths)
        val viewed = repo.viewed()
        _state.value = _state.value.copy(
            currentTab = 0, browsingTitle = title, images = imgs,
            unviewed = imgs.filter { it.path !in viewed }, favorites = repo.favorites(),
            loading = false, bottomBarVisible = true, message = if (imgs.isEmpty()) "该文件夹没有图片" else null
        )
    }

    // 当前活动浏览不立刻隐藏，只记录；退出当前文件夹时统一提交阅后即焚
    fun recordViewed(path: String) { sessionViewed.add(path) }
    private fun commitSessionViewed() {
        if (sessionViewed.isEmpty()) return
        sessionViewed.forEach { repo.markViewed(it) }
        sessionViewed.clear()
        val viewed = repo.viewed()
        _state.value = _state.value.copy(unviewed = _state.value.images.filter { it.path !in viewed })
    }
    fun resetViewed() { repo.resetViewed(); sessionViewed.clear(); _state.value = _state.value.copy(unviewed = _state.value.images, message = "已重置浏览记录") }

    fun toggleFavorite(path: String) {
        val added = repo.toggleFavorite(path)
        _state.value = _state.value.copy(favorites = repo.favorites())
        if (added) {
            val img = _state.value.images.find { it.path == path } ?: repo.favoriteImages().find { it.path == path }
            if (img != null) viewModelScope.launch { storage.saveFavoriteImage(img.path, img.name, repo.storageConfig()) }
        }
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
    override fun onCleared() { commitSessionViewed(); super.onCleared() }

    class Factory(private val repo: AppRepository, private val storage: StorageManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(repo, storage) as T
    }
}
