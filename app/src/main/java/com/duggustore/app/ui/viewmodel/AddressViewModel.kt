package com.duggustore.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duggustore.app.data.model.Address
import com.duggustore.app.data.repository.AddressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AddressState(
    val isLoading: Boolean = false,
    val addresses: List<Address> = emptyList(),
    val userId: String = "",
    val error: String? = null
) {
    val defaultAddress: Address? get() = addresses.firstOrNull { it.isDefault } ?: addresses.firstOrNull()
}

class AddressViewModel : ViewModel() {
    private val repository = AddressRepository()

    private val _state = MutableStateFlow(AddressState())
    val state: StateFlow<AddressState> = _state

    fun setUser(userId: String) {
        if (_state.value.userId != userId) {
            _state.value = _state.value.copy(userId = userId)
            loadAddresses()
        }
    }

    fun loadAddresses() {
        val userId = _state.value.userId
        if (userId.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            repository.getAddresses(userId)
                .onSuccess { _state.value = _state.value.copy(isLoading = false, addresses = it) }
                .onFailure { _state.value = _state.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun saveAddress(
        label: String,
        fullAddress: String,
        isDefault: Boolean,
        existingId: String = "",
        latitude: Double = 0.0,
        longitude: Double = 0.0
    ) {
        val userId = _state.value.userId
        if (userId.isEmpty() || fullAddress.isBlank()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val address = Address(
                id = existingId,
                userId = userId,
                label = label.ifBlank { "Home" },
                fullAddress = fullAddress.trim(),
                latitude = latitude,
                longitude = longitude,
                // The first address saved becomes the default, so checkout always has one.
                isDefault = isDefault || _state.value.addresses.isEmpty()
            )
            val result =
                if (existingId.isBlank()) repository.addAddress(address)
                else repository.updateAddress(address)

            result.onSuccess { loadAddresses() }
                .onFailure { _state.value = _state.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun deleteAddress(addressId: String) {
        viewModelScope.launch {
            repository.deleteAddress(addressId)
                .onSuccess { loadAddresses() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun setDefault(addressId: String) {
        val userId = _state.value.userId
        if (userId.isEmpty()) return
        viewModelScope.launch {
            repository.setDefault(userId, addressId)
                .onSuccess { loadAddresses() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /**
     * Sign-out: saved addresses are per-user. These ViewModels outlive the
     * session (activity scope), so without this the header's location strip
     * kept showing the previous user's default address to the next guest.
     */
    fun clearSession() {
        _state.value = AddressState()
    }
}
