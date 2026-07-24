package com.nomedia.viewer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BrowseState(
    val images: List<ImageFile> = emptyList(),
    val unviewedImages: List<ImageFile> = emptyList(),
    val currentImageIndex: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
    val favorites: Set<String> = emptySet(),
    val viewedImages: Set<String> = emptySet(),
    val viewMode: ViewMode = ViewMode.SCROLL
)

enum class ViewMode {
    SCROLL, FAVORITES
}

class MainViewModel(private val repository: ImageRepository) : ViewModel() {

    private val _state = MutableStateFlow(BrowseState())
    val state: StateFlow<BrowseState> = _state.asStateFlow()

    init {
        loadImages()
    }

    fun loadImages() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val allImages = repository.scanNoMediaImages()
                val viewed = repository.getViewedImages()
                val favorites = repository.getFavorites()

                val unviewed = allImages.filter { it.path !in viewed }

                _state.value = _state.value.copy(
                    images = allImages,
                    unviewedImages = unviewed,
                    isLoading = false,
                    favorites = favorites,
                    currentImageIndex = 0
                )

                if (allImages.isEmpty()) {
                    _state.value = _state.value.copy(
                        error = "未找到 .nomedia 文件夹中的图片\n请确保应用有存储权限"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "扫描出错: ${e.message}"
                )
            }
        }
    }

    fun markAsViewed(path: String) {
        repository.markAsViewed(path)
        // Update unviewed list
        val viewed = repository.getViewedImages()
        _state.value = _state.value.copy(
            unviewedImages = _state.value.images.filter { it.path !in viewed },
            viewedImages = viewed
        )
    }

    fun toggleFavorite(path: String) {
        repository.toggleFavorite(path)
        _state.value = _state.value.copy(
            favorites = repository.getFavorites()
        )
    }

    fun isFavorite(path: String): Boolean {
        return repository.isFavorite(path)
    }

    fun resetHistory() {
        repository.resetHistory()
        val allImages = _state.value.images
        _state.value = _state.value.copy(
            unviewedImages = allImages,
            currentImageIndex = 0
        )
    }

    fun setViewMode(mode: ViewMode) {
        _state.value = _state.value.copy(viewMode = mode)
    }

    fun getFavoriteImages(): List<ImageFile> {
        return repository.getFavoriteImages()
    }

    class Factory(private val repository: ImageRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(repository) as T
        }
    }
}
