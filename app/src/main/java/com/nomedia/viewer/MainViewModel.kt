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
    val networkFolders: List<NetworkFolder> = emptyList(),
    val albums: List<FolderAlbum> = emptyList(),
    val selectedAlbumPaths: Set<String> = emptySet(),
    val browsingTitle: String = "",
    val currentAlbumPath: String? = null,
    val images: List<ImageFile> = emptyList(),
    val viewed: Set<String> = emptySet(),
    val favorites: Set<String> = emptySet(),
    val columns: Int = 1,
    val favoriteColumns: Int = 2,
    val scrollSpeed: Float = 1f,
    val bottomBarVisible: Boolean = true,
    val loading: Boolean = false,
    val message: String? = null,
    val transientNotice: String? = null,
    val storageConfig: StorageConfig = StorageConfig(),
    val fullscreenImage: ImageFile? = null,
    val fullscreenSource: List<ImageFile> = emptyList()
)

class MainViewModel(private val repo: AppRepository, private val storage: StorageManager, private val network: NetworkFolderManager) : ViewModel() {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()
    private var scanJob: Job? = null
    private var noticeJob: Job? = null
    private var rootsSignature: String = ""

    init { reloadBasics(); scanAlbums(force = true) }

    fun reloadBasics() {
        _state.value = _state.value.copy(
            roots = repo.roots(), networkFolders = repo.networkFolders(), favorites = repo.favorites(), viewed = repo.viewed(),
            columns = repo.columns(), favoriteColumns = repo.favoriteColumns(), scrollSpeed = repo.scrollSpeed(), storageConfig = repo.storageConfig()
        )
    }

    fun setTab(tab: Int) {
        _state.value = _state.value.copy(currentTab = tab, bottomBarVisible = true)
        if (tab == 1 || tab == 2) scanAlbums(force = false)
    }

    fun addRoot(uri: Uri) { val r = repo.addRoot(uri); reloadBasics(); rootsSignature = ""; _state.value = _state.value.copy(message = if (r == null) "无法解析该目录路径" else "已添加：${r.name}"); scanAlbums(true) }
    fun addNetworkFolder(type: NetworkFolderType, name: String, url: String, user: String, pass: String) { repo.addNetworkFolder(type, name, url, user, pass); reloadBasics(); rootsSignature = ""; _state.value = _state.value.copy(message = "已添加网络文件夹：${name.ifBlank { url }}"); scanAlbums(true) }
    fun addNetworkFolderValidated(type: NetworkFolderType, name: String, url: String, user: String, pass: String, cb: (String) -> Unit) = viewModelScope.launch {
        val pr = network.probe(type, url, user, pass)
        if (pr.ok) {
            val finalUrl = pr.normalizedUrl.ifBlank { url }
            repo.addNetworkFolder(type, name.ifBlank { finalUrl }, finalUrl, user, pass)
            reloadBasics(); rootsSignature = ""; scanAlbums(true)
            cb("✅ 已验证并添加：${name.ifBlank { finalUrl }}")
        } else cb(pr.message)
    }
    fun updateNetworkFolderValidated(id: String, type: NetworkFolderType, name: String, url: String, user: String, pass: String, enabled: Boolean, cb: (String) -> Unit) = viewModelScope.launch {
        val pr = network.probe(type, url, user, pass)
        if (pr.ok) {
            val finalUrl = pr.normalizedUrl.ifBlank { url }
            repo.updateNetworkFolder(id, type, name.ifBlank { finalUrl }, finalUrl, user, pass, enabled)
            reloadBasics(); rootsSignature = ""; scanAlbums(true)
            cb("✅ 已验证并保存")
        } else cb(pr.message)
    }
    fun removeNetworkFolder(id: String) { repo.removeNetworkFolder(id); reloadBasics(); rootsSignature = ""; scanAlbums(true) }
    fun setNetworkFolderEnabled(id: String, enabled: Boolean) { repo.setNetworkFolderEnabled(id, enabled); reloadBasics(); rootsSignature = ""; scanAlbums(true) }
    fun probeNetworkFolder(type: NetworkFolderType, url: String, user: String, pass: String, cb: (NetworkProbeResult) -> Unit) = viewModelScope.launch { cb(network.probe(type, url, user, pass)) }
    fun scanLan(cb: (List<String>) -> Unit) = viewModelScope.launch { cb(network.scanLanIps()) }
    fun removeRoot(uri: String) { repo.removeRoot(uri); reloadBasics(); rootsSignature = ""; scanAlbums(true) }
    fun setRootEnabled(uri: String, enabled: Boolean) { repo.setRootEnabled(uri, enabled); reloadBasics(); rootsSignature = ""; scanAlbums(true) }
    fun refreshWithNetwork() = viewModelScope.launch {
        val nodes = repo.networkFolders().filter { it.enabled }
        _state.value = _state.value.copy(loading = true, message = "正在检查网络节点…")
        var ok = 0
        nodes.forEach { n -> if (network.probe(n.type, n.url, n.user, n.pass).ok) ok++ }
        reloadBasics()
        _state.value = _state.value.copy(loading = false, message = "网络检查完成：$ok/${nodes.size} 可连接。需要读取图片时再进入对应目录。")
    }
    fun clearNetworkRecovery() { network.clearCache(); repo.clearNetworkFolders(); reloadBasics(); rootsSignature = ""; _state.value = _state.value.copy(message = "已重置网络节点和缓存") }

    fun scanAlbums(force: Boolean = false, includeNetwork: Boolean = false) {
        val roots = repo.roots()
        val sig = roots.filter { it.enabled }.joinToString("|") { it.path } + "||" + if (includeNetwork) repo.networkFolders().filter { it.enabled }.joinToString("|") { it.id + it.url } else "local-only"
        if (!force && sig == rootsSignature && _state.value.albums.isNotEmpty()) return
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val nets = repo.networkFolders()
            _state.value = _state.value.copy(loading = true, message = null, roots = roots, networkFolders = nets, viewed = repo.viewed())
            if (roots.none { it.enabled } && (includeNetwork.not() || nets.none { it.enabled })) {
                _state.value = _state.value.copy(loading = false, albums = emptyList(), message = if (roots.isEmpty() && (includeNetwork.not() || nets.isEmpty())) "请先在设置中添加手机文件夹；网络文件夹请在设置里手动刷新" else "请启用至少一个目录")
                return@launch
            }
            AppLogger.log("开始扫描 includeNetwork=$includeNetwork")
            val albums = repo.scanAlbums(if (includeNetwork) network else null)
            rootsSignature = sig
            _state.value = _state.value.copy(loading = false, albums = albums, viewed = repo.viewed(), message = if (albums.isEmpty()) "没有扫描到含图片的子目录" else null)
        }
    }

    fun toggleAlbum(path: String) { markAlbumRead(path) }
    fun browseAlbum(path: String) = browsePaths(listOf(path), java.io.File(path).name.ifBlank { "图片" }, path)
    fun browseSelectedAlbums() { /* v0.15: 合并浏览已取消 */ }
    fun browseMergedAlbums(paths: Set<String>) {
        if (paths.isEmpty()) return
        val names = _state.value.albums.filter { it.path in paths }.map { it.name }
        browsePaths(paths.toList(), if (names.size <= 2) names.joinToString(" + ") else "合并浏览 ${names.size} 个相册", null)
    }

    fun markAlbumRead(path: String) {
        val album = _state.value.albums.find { it.path == path } ?: return
        album.imagePaths.forEach { repo.markViewed(it) }
        repo.markAlbumViewed(path)
        _state.value = _state.value.copy(viewed = repo.viewed(), transientNotice = "已标记已读：${album.name}")
        noticeJob?.cancel()
        noticeJob = viewModelScope.launch { delay(1400); _state.value = _state.value.copy(transientNotice = null) }
    }

    fun unmarkAlbumRead(path: String) {
        val album = _state.value.albums.find { it.path == path } ?: return
        repo.unmarkViewed(album.imagePaths)
        _state.value = _state.value.copy(viewed = repo.viewed(), transientNotice = "已恢复未浏览：${album.name}")
        noticeJob?.cancel()
        noticeJob = viewModelScope.launch { delay(1400); _state.value = _state.value.copy(transientNotice = null) }
    }

    fun albumViewedAt(path: String): Long = repo.albumViewedAt(path)
    fun restoreAlbumsUnread(paths: Set<String>) {
        val imgs = _state.value.albums.filter { it.path in paths }.flatMap { it.imagePaths }
        repo.unmarkViewed(imgs)
        _state.value = _state.value.copy(viewed = repo.viewed(), transientNotice = "已恢复未浏览：${paths.size}个文件夹")
    }
    fun deleteAlbums(paths: Set<String>) {
        paths.forEach { repo.deleteAlbumFolder(it) }
        rootsSignature = ""
        _state.value = _state.value.copy(transientNotice = "已删除：${paths.size}个文件夹")
        scanAlbums(true)
    }

    private fun browsePaths(paths: List<String>, title: String, albumPath: String?) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, message = null)
        val imgs = repo.scanImages(paths)
        _state.value = _state.value.copy(currentTab = 0, browsingTitle = title, currentAlbumPath = albumPath, images = imgs, viewed = repo.viewed(), favorites = repo.favorites(), loading = false, bottomBarVisible = true, message = if (imgs.isEmpty()) "该文件夹没有图片" else null)
    }

    fun markRead(path: String) { repo.markViewed(path); _state.value = _state.value.copy(viewed = repo.viewed()) }
    fun resetViewed() { repo.resetViewed(); _state.value = _state.value.copy(viewed = emptySet(), message = "已重置阅读标记") }

    fun goNextAlbumIfPossible() = goRelativeAlbum(1)
    fun goPreviousAlbumIfPossible() = goRelativeAlbum(-1)

    private fun goRelativeAlbum(delta: Int) {
        val current = _state.value.currentAlbumPath ?: return
        val albums = _state.value.albums
        val idx = albums.indexOfFirst { it.path == current }
        val target = idx + delta
        if (idx >= 0 && target in albums.indices) {
            val next = albums[target]
            showNotice(if (delta > 0) "已进入下一个文件夹：${next.name}" else "已进入上一个文件夹：${next.name}")
            browseAlbum(next.path)
        }
    }

    private fun showNotice(text: String) {
        noticeJob?.cancel()
        _state.value = _state.value.copy(transientNotice = text)
        noticeJob = viewModelScope.launch { delay(2000); _state.value = _state.value.copy(transientNotice = null) }
    }

    fun toggleFavorite(path: String) {
        val added = repo.toggleFavorite(path)
        _state.value = _state.value.copy(favorites = repo.favorites())
        val cfg = repo.storageConfig()
        if (added) {
            val img = _state.value.images.find { it.path == path } ?: repo.favoriteImages().find { it.path == path }
            if (img != null) viewModelScope.launch {
                storage.saveFavoriteImage(img.path, cfg, repo.enabledRootPaths()).onSuccess { copied ->
                    repo.setFavoriteCopyPath(path, copied)
                }
            }
        } else {
            val copied = repo.favoriteCopyPath(path)
            if (copied.isNotBlank()) viewModelScope.launch {
                storage.deleteFavoriteCopy(copied, cfg)
                repo.clearFavoriteCopyPath(path)
            }
        }
    }
    fun favoriteImages(): List<ImageFile> = repo.favoriteImages()
    fun unFavoriteMany(paths: Set<String>) { paths.forEach { toggleFavorite(it) } }
    fun deleteFavoriteImages(paths: Set<String>) {
        paths.forEach { p ->
            if (p in repo.favorites()) toggleFavorite(p)
            runCatching { java.io.File(p).delete() }
        }
        _state.value = _state.value.copy(favorites = repo.favorites(), transientNotice = "已删除收藏图片：${paths.size}张")
    }
    fun isFavorite(path: String): Boolean = path in repo.favorites()
    fun setColumns(c: Int) { repo.setColumns(c); _state.value = _state.value.copy(columns = repo.columns()) }
    fun setFavoriteColumns(c: Int) { repo.setFavoriteColumns(c); _state.value = _state.value.copy(favoriteColumns = repo.favoriteColumns()) }
    fun setScrollSpeed(v: Float) { repo.setScrollSpeed(v); _state.value = _state.value.copy(scrollSpeed = repo.scrollSpeed()) }
    fun setBottomVisible(v: Boolean) { if (_state.value.bottomBarVisible != v) _state.value = _state.value.copy(bottomBarVisible = v) }
    fun saveStorage(c: StorageConfig) { repo.saveStorage(c); _state.value = _state.value.copy(storageConfig = c, message = "已保存设置") }
    fun testWebDav(url: String, user: String, pass: String, cb: (String) -> Unit) = viewModelScope.launch { cb(storage.testWebDavAuto(url, user, pass)) }
    fun pathFromUri(uri: Uri): String? = repo.uriToPath(uri.toString())
    fun openFullscreen(img: ImageFile) { _state.value = _state.value.copy(fullscreenImage = img, fullscreenSource = _state.value.images.ifEmpty { favoriteImages() }) }
    fun openFullscreenFrom(img: ImageFile, source: List<ImageFile>) { _state.value = _state.value.copy(fullscreenImage = img, fullscreenSource = source) }
    fun showNextFullscreen() = shiftFullscreen(1)
    fun showPreviousFullscreen() = shiftFullscreen(-1)
    private fun shiftFullscreen(delta: Int) {
        val current = _state.value.fullscreenImage ?: return
        val source = _state.value.fullscreenSource.ifEmpty { if (_state.value.images.any { it.path == current.path }) _state.value.images else favoriteImages() }
        val idx = source.indexOfFirst { it.path == current.path }
        val target = idx + delta
        if (idx >= 0 && target in source.indices) _state.value = _state.value.copy(fullscreenImage = source[target])
    }
    fun closeFullscreen() { _state.value = _state.value.copy(fullscreenImage = null) }

    class Factory(private val repo: AppRepository, private val storage: StorageManager, private val network: NetworkFolderManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(repo, storage, network) as T
    }
}
