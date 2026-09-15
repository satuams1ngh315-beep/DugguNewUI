package com.duggustore.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duggustore.app.data.model.DeliveryPartner
import com.duggustore.app.data.model.DeliveryPartnerDocument
import com.duggustore.app.data.repository.DeliveryOnboardingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DeliveryOnboardingState(
    val hasLoaded: Boolean = false,
    val partner: DeliveryPartner? = null,
    val documents: List<DeliveryPartnerDocument> = emptyList(),
    val isSaving: Boolean = false,
    val isSubmitting: Boolean = false,
    val uploadingDocType: String? = null,
    val error: String? = null,
    val allPartners: List<DeliveryPartner> = emptyList(),
    val isLoadingAll: Boolean = false,
    /** An admin's view of an applicant's documents, keyed by partner id — separate from [documents] (the signed-in rider's own), since an admin browsing the queue must not clobber that. */
    val reviewDocuments: Map<String, List<DeliveryPartnerDocument>> = emptyMap(),
    /** Signed, directly-loadable URLs for review documents, keyed by document id. */
    val reviewDocumentUrls: Map<String, String> = emptyMap(),
    val loadingReviewDocsFor: String? = null,
    /** The partner id currently being approved/rejected — lets the queue show a spinner and block a second tap. */
    val reviewingId: String? = null,
    /** Separate from [error]: that one belongs to the signed-in rider's own form, this one to the admin's review action. */
    val reviewError: String? = null
)

class DeliveryOnboardingViewModel : ViewModel() {
    private val repository = DeliveryOnboardingRepository()

    private val _state = MutableStateFlow(DeliveryOnboardingState())
    val state: StateFlow<DeliveryOnboardingState> = _state

    fun load(userId: String) {
        viewModelScope.launch {
            val partnerResult = repository.getMyPartner(userId)
            val partner = partnerResult.getOrNull()
            val docs = if (partner != null) repository.getDocuments(userId).getOrElse { emptyList() } else emptyList()
            _state.value = _state.value.copy(hasLoaded = true, partner = partner, documents = docs)
        }
    }

    fun save(partner: DeliveryPartner, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            val result = repository.saveApplication(partner)
            result.onSuccess {
                load(partner.id)
                onDone()
            }
            result.onFailure { _state.value = _state.value.copy(error = it.message) }
            _state.value = _state.value.copy(isSaving = false)
        }
    }

    fun uploadDocument(partnerId: String, docType: String, bytes: ByteArray, contentType: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(uploadingDocType = docType, error = null)
            val result = repository.uploadDocument(partnerId, docType, bytes, contentType)
            result.onFailure { _state.value = _state.value.copy(error = it.message) }
            result.onSuccess {
                repository.getDocuments(partnerId).onSuccess { docs ->
                    _state.value = _state.value.copy(documents = docs)
                }
            }
            _state.value = _state.value.copy(uploadingDocType = null)
        }
    }

    fun submit(partnerId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, error = null)
            val result = repository.submitForReview(partnerId)
            result.onSuccess { load(partnerId) }
            result.onFailure { _state.value = _state.value.copy(error = it.message) }
            _state.value = _state.value.copy(isSubmitting = false)
        }
    }

    fun loadAllForReview() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingAll = true)
            repository.getAllPartners().onSuccess { list ->
                _state.value = _state.value.copy(allPartners = list)
            }
            _state.value = _state.value.copy(isLoadingAll = false)
        }
    }

    fun review(partnerId: String, approve: Boolean, rejectionReason: String = "") {
        viewModelScope.launch {
            _state.value = _state.value.copy(reviewingId = partnerId, reviewError = null)
            val result = repository.reviewPartner(partnerId, approve, rejectionReason)
            result.onSuccess { loadAllForReview() }
            result.onFailure { _state.value = _state.value.copy(reviewError = it.message ?: "Couldn't update this application") }
            _state.value = _state.value.copy(reviewingId = null)
        }
    }

    fun clearReviewError() {
        _state.value = _state.value.copy(reviewError = null)
    }

    /** Fetches one applicant's documents plus a signed URL for each, for the admin queue to display. */
    fun loadReviewDocuments(partnerId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingReviewDocsFor = partnerId)
            val docs = repository.getDocuments(partnerId).getOrElse { emptyList() }
            val urls = docs.mapNotNull { doc ->
                repository.documentUrl(doc.fileUrl).getOrNull()?.let { doc.id to it }
            }.toMap()
            _state.value = _state.value.copy(
                reviewDocuments = _state.value.reviewDocuments + (partnerId to docs),
                reviewDocumentUrls = _state.value.reviewDocumentUrls + urls,
                loadingReviewDocsFor = null
            )
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
    /**
     * Sign-out: resets hasLoaded too, so the next account re-checks the
     * table instead of being shown the previous user's KYC application.
     */
    fun clearSession() {
        _state.value = DeliveryOnboardingState()
    }

}
