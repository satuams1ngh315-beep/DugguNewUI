package com.duggustore.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duggustore.app.data.model.Favorite
import com.duggustore.app.data.model.Product
import com.duggustore.app.data.repository.FavoriteRepository
import com.duggustore.app.data.repository.ProductRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class FavoriteState(
    val isLoading: Boolean = false,
    val favorites: List<Favorite> = emptyList(),
    val favoriteProducts: List<Product> = emptyList(),
    val error: String? = null
)

class FavoriteViewModel : ViewModel() {
    private val favRepo = FavoriteRepository()
    private val productRepo = ProductRepository()

    private val _state = MutableStateFlow(FavoriteState())
    val state: StateFlow<FavoriteState> = _state

    fun loadFavorites(customerId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val favResult = favRepo.getFavorites(customerId)
            favResult.onSuccess { favorites ->
                _state.value = _state.value.copy(favorites = favorites)
                val products = favorites.map { fav ->
                    async { productRepo.getProduct(fav.productId).getOrNull() }
                }.awaitAll().filterNotNull()
                _state.value = _state.value.copy(favoriteProducts = products, isLoading = false)
            }
            favResult.onFailure {
                _state.value = _state.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    fun toggleFavorite(customerId: String, productId: String) {
        viewModelScope.launch {
            val isFav = _state.value.favorites.any { it.productId == productId }
            if (isFav) {
                favRepo.removeFromFavorites(customerId, productId)
            } else {
                favRepo.addToFavorites(customerId, productId)
            }
            loadFavorites(customerId)
        }
    }

    fun isFavorite(productId: String): Boolean {
        return _state.value.favorites.any { it.productId == productId }
    }

    /** Sign-out: favourites (and their heart states on the cards) are per-user. */
    fun clearSession() {
        _state.value = FavoriteState()
    }
}
