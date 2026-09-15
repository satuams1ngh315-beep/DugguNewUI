package com.duggustore.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duggustore.app.data.model.Campaign
import com.duggustore.app.data.model.Category
import com.duggustore.app.data.model.Coupon
import com.duggustore.app.data.model.DbHealth
import com.duggustore.app.data.model.TableStat
import com.duggustore.app.data.model.SponsoredSlot
import com.duggustore.app.data.model.UserProfile
import com.duggustore.app.data.model.Order
import com.duggustore.app.data.model.Product
import com.duggustore.app.data.repository.AuthRepository
import com.duggustore.app.data.model.HomeSection
import com.duggustore.app.data.repository.CampaignRepository
import com.duggustore.app.data.repository.HomeLayoutRepository
import com.duggustore.app.data.repository.CategoryRepository
import com.duggustore.app.data.repository.DbHealthRepository
import com.duggustore.app.data.repository.OfferRepository
import com.duggustore.app.data.repository.OrderRepository
import com.duggustore.app.data.repository.ProductRepository
import com.duggustore.app.data.repository.SponsoredSlotRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AdminState(
    val isLoading: Boolean = false,
    val users: List<UserProfile> = emptyList(),
    val orders: List<Order> = emptyList(),
    val products: List<Product> = emptyList(),
    val categories: List<Category> = emptyList(),
    val coupons: List<Coupon> = emptyList(),
    val campaigns: List<Campaign> = emptyList(),
    /** Every section including hidden ones — the customer only ever sees the active ones. */
    val homeSections: List<HomeSection> = emptyList(),
    /** Section id to its category ids, in display order. */
    val sectionCategories: Map<String, List<String>> = emptyMap(),
    val sponsoredSlots: List<SponsoredSlot> = emptyList(),
    val totalUsers: Int = 0,
    val totalOrders: Int = 0,
    val totalRevenue: Double = 0.0,
    val totalDeliveries: Int = 0,
    val isSavingCatalog: Boolean = false,
    val catalogError: String? = null,
    val reviewingSlotId: String? = null,
    val slotReviewError: String? = null,
    /** Database tab — loaded on demand rather than with the rest of the dashboard. */
    val tableStats: List<TableStat> = emptyList(),
    val dbHealth: DbHealth? = null,
    val isLoadingDb: Boolean = false,
    val dbError: String? = null,
    val error: String? = null
)

class AdminViewModel : ViewModel() {
    private val authRepo = AuthRepository()
    private val orderRepo = OrderRepository()
    private val productRepo = ProductRepository()
    private val categoryRepo = CategoryRepository()
    private val offerRepo = OfferRepository()
    private val campaignRepo = CampaignRepository()
    private val homeLayoutRepo = HomeLayoutRepository()
    private val sponsoredSlotRepo = SponsoredSlotRepository()
    private val dbHealthRepo = DbHealthRepository()

    private val _state = MutableStateFlow(AdminState())
    val state: StateFlow<AdminState> = _state

    fun loadDashboard() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                val usersDeferred = async { authRepo.getAllUsers() }
                val ordersDeferred = async { orderRepo.getAllOrders() }
                val productsDeferred = async { productRepo.getAllProducts() }
                val categoriesDeferred = async { categoryRepo.getAllCategories() }
                val couponsDeferred = async { offerRepo.getAllCoupons() }
                val campaignsDeferred = async { campaignRepo.getAllCampaigns() }
                val homeSectionsDeferred = async { homeLayoutRepo.getAllSections() }
                val sectionCategoriesDeferred = async { homeLayoutRepo.getAllSectionCategories() }
                val slotsDeferred = async { sponsoredSlotRepo.getAllSlots() }

                val users = usersDeferred.await().getOrThrow()
                val orders = ordersDeferred.await().getOrThrow()
                val products = productsDeferred.await().getOrThrow()
                val categories = categoriesDeferred.await().getOrThrow()
                val coupons = couponsDeferred.await().getOrThrow()
                val campaigns = campaignsDeferred.await().getOrThrow()
                // A store that has not been given a browse layout yet is a
                // normal state, not a load failure — hence getOrNull.
                val homeSections = homeSectionsDeferred.await().getOrNull().orEmpty()
                val sectionCategories = sectionCategoriesDeferred.await().getOrNull().orEmpty()
                val slots = slotsDeferred.await().getOrThrow()

                _state.value = _state.value.copy(
                    isLoading = false,
                    users = users,
                    totalUsers = users.size,
                    orders = orders,
                    totalOrders = orders.size,
                    totalRevenue = orders.filter { it.status == "delivered" }.sumOf { it.totalAmount },
                    totalDeliveries = orders.count { it.status == "delivered" },
                    products = products,
                    categories = categories,
                    coupons = coupons,
                    campaigns = campaigns,
                    homeSections = homeSections,
                    sectionCategories = sectionCategories,
                    sponsoredSlots = slots
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to load dashboard")
            }
        }
    }

    /**
     * Loads the Database tab. Kept out of [loadDashboard] because the table
     * counts scan every table — fine when an admin asks for them, wasteful on
     * every dashboard open.
     */
    fun loadDbHealth() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingDb = true, dbError = null)
            val health = dbHealthRepo.getHealth()
            val stats = dbHealthRepo.getTableStats()

            val failure = health.exceptionOrNull() ?: stats.exceptionOrNull()
            _state.value = _state.value.copy(
                dbHealth = health.getOrNull() ?: _state.value.dbHealth,
                tableStats = stats.getOrNull() ?: _state.value.tableStats,
                isLoadingDb = false,
                dbError = failure?.let {
                    // The RPCs are the one part of this screen that needs a
                    // migration applied, so name that rather than a bare error.
                    "Couldn't read database stats: ${it.message}. " +
                        "If this says the function is missing, run supabase_admin_stats.sql."
                }
            )
        }
    }

    fun clearDbError() {
        _state.value = _state.value.copy(dbError = null)
    }

    fun updateUserRole(userId: String, role: String) {
        viewModelScope.launch {
            val result = authRepo.updateUserRole(userId, role)
            result.onSuccess { loadDashboard() }
            result.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }

    fun toggleProductActive(product: Product) {
        viewModelScope.launch {
            val result = productRepo.updateProduct(product.copy(isActive = !product.isActive))
            result.onSuccess {
                _state.value = _state.value.copy(
                    products = _state.value.products.map { if (it.id == product.id) it.copy(isActive = !product.isActive) else it }
                )
            }
            result.onFailure { _state.value = _state.value.copy(catalogError = it.message) }
        }
    }

    fun saveCategory(category: Category, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSavingCatalog = true, catalogError = null)
            val result = if (category.id.isBlank()) {
                categoryRepo.createCategory(category)
            } else {
                categoryRepo.updateCategory(category)
            }
            result.onSuccess {
                categoryRepo.getAllCategories().onSuccess { categories ->
                    _state.value = _state.value.copy(categories = categories)
                }
                onDone()
            }
            result.onFailure { _state.value = _state.value.copy(catalogError = it.message) }
            _state.value = _state.value.copy(isSavingCatalog = false)
        }
    }

    fun toggleCategoryActive(category: Category) {
        viewModelScope.launch {
            val result = categoryRepo.updateCategory(category.copy(isActive = !category.isActive))
            result.onSuccess {
                _state.value = _state.value.copy(
                    categories = _state.value.categories.map { if (it.id == category.id) it.copy(isActive = !category.isActive) else it }
                )
            }
            result.onFailure { _state.value = _state.value.copy(catalogError = it.message) }
        }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch {
            val result = categoryRepo.deleteCategory(id)
            result.onSuccess {
                _state.value = _state.value.copy(categories = _state.value.categories.filter { it.id != id })
            }
            result.onFailure { _state.value = _state.value.copy(catalogError = it.message) }
        }
    }

    fun saveCoupon(coupon: Coupon, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSavingCatalog = true, catalogError = null)
            val result = if (coupon.id.isBlank()) {
                offerRepo.createCoupon(coupon)
            } else {
                offerRepo.updateCoupon(coupon)
            }
            result.onSuccess {
                offerRepo.getAllCoupons().onSuccess { coupons ->
                    _state.value = _state.value.copy(coupons = coupons)
                }
                onDone()
            }
            result.onFailure { _state.value = _state.value.copy(catalogError = it.message) }
            _state.value = _state.value.copy(isSavingCatalog = false)
        }
    }

    fun toggleCouponActive(coupon: Coupon) {
        viewModelScope.launch {
            val result = offerRepo.updateCoupon(coupon.copy(isActive = !coupon.isActive))
            result.onSuccess {
                _state.value = _state.value.copy(
                    coupons = _state.value.coupons.map { if (it.id == coupon.id) it.copy(isActive = !coupon.isActive) else it }
                )
            }
            result.onFailure { _state.value = _state.value.copy(catalogError = it.message) }
        }
    }

    fun deleteCoupon(id: String) {
        viewModelScope.launch {
            val result = offerRepo.deleteCoupon(id)
            result.onSuccess {
                _state.value = _state.value.copy(coupons = _state.value.coupons.filter { it.id != id })
            }
            result.onFailure { _state.value = _state.value.copy(catalogError = it.message) }
        }
    }

    fun clearCatalogError() {
        _state.value = _state.value.copy(catalogError = null)
    }

    /** [durationDays] runs the campaign from right now, whether it's a new one or a renewal of an existing one. */
    fun saveCampaign(campaign: Campaign, durationDays: Int, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSavingCatalog = true, catalogError = null)
            val result = if (campaign.id.isBlank()) {
                campaignRepo.createCampaign(campaign, durationDays)
            } else {
                campaignRepo.updateCampaign(campaign, durationDays)
            }
            result.onSuccess {
                campaignRepo.getAllCampaigns().onSuccess { campaigns ->
                    _state.value = _state.value.copy(campaigns = campaigns)
                }
                onDone()
            }
            result.onFailure { _state.value = _state.value.copy(catalogError = it.message) }
            _state.value = _state.value.copy(isSavingCatalog = false)
        }
    }

    fun toggleCampaignActive(campaign: Campaign) {
        viewModelScope.launch {
            val result = campaignRepo.setActive(campaign, !campaign.isActive)
            result.onSuccess {
                _state.value = _state.value.copy(
                    campaigns = _state.value.campaigns.map {
                        if (it.id == campaign.id) it.copy(isActive = !campaign.isActive) else it
                    }
                )
            }
            result.onFailure { _state.value = _state.value.copy(catalogError = it.message) }
        }
    }

    fun deleteCampaign(id: String) {
        viewModelScope.launch {
            val result = campaignRepo.deleteCampaign(id)
            result.onSuccess {
                _state.value = _state.value.copy(campaigns = _state.value.campaigns.filter { it.id != id })
            }
            result.onFailure { _state.value = _state.value.copy(catalogError = it.message) }
        }
    }

    /**
     * Saves a section and its category list together. The categories are
     * written as a replacement rather than a diff, so the order the admin
     * left them in is the order the customer gets.
     */
    fun saveHomeSection(
        section: HomeSection,
        categoryIds: List<String>,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSavingCatalog = true, catalogError = null)
            val result = if (section.id.isBlank()) {
                homeLayoutRepo.createSection(section.title, section.layout, section.sortOrder)
            } else {
                homeLayoutRepo.updateSection(
                    section.id,
                    section.title,
                    section.layout,
                    section.sortOrder
                )
            }
            result.onSuccess {
                // A new section has no id until it exists, so its categories
                // are attached on the reload that follows rather than here.
                val sections = homeLayoutRepo.getAllSections().getOrNull().orEmpty()
                val saved = if (section.id.isBlank()) {
                    sections.lastOrNull { it.title == section.title }
                } else {
                    sections.firstOrNull { it.id == section.id }
                }
                saved?.let { homeLayoutRepo.setSectionCategories(it.id, categoryIds) }
                refreshHomeLayout()
                onDone()
            }
            result.onFailure { _state.value = _state.value.copy(catalogError = it.message) }
            _state.value = _state.value.copy(isSavingCatalog = false)
        }
    }

    fun toggleHomeSectionActive(section: HomeSection) {
        viewModelScope.launch {
            val result = homeLayoutRepo.setSectionActive(section.id, !section.isActive)
            result.onSuccess {
                _state.value = _state.value.copy(
                    homeSections = _state.value.homeSections.map {
                        if (it.id == section.id) it.copy(isActive = !section.isActive) else it
                    }
                )
            }
            result.onFailure { _state.value = _state.value.copy(catalogError = it.message) }
        }
    }

    fun deleteHomeSection(id: String) {
        viewModelScope.launch {
            val result = homeLayoutRepo.deleteSection(id)
            result.onSuccess {
                _state.value = _state.value.copy(
                    homeSections = _state.value.homeSections.filter { it.id != id },
                    sectionCategories = _state.value.sectionCategories - id
                )
            }
            result.onFailure { _state.value = _state.value.copy(catalogError = it.message) }
        }
    }

    private suspend fun refreshHomeLayout() {
        val sections = homeLayoutRepo.getAllSections().getOrNull()
        val categories = homeLayoutRepo.getAllSectionCategories().getOrNull()
        _state.value = _state.value.copy(
            homeSections = sections ?: _state.value.homeSections,
            sectionCategories = categories ?: _state.value.sectionCategories
        )
    }

    fun reviewSponsoredSlot(slotId: String, approve: Boolean, reason: String = "") {
        viewModelScope.launch {
            _state.value = _state.value.copy(reviewingSlotId = slotId, slotReviewError = null)
            val result = sponsoredSlotRepo.reviewSlot(slotId, approve, reason)
            result.onSuccess {
                sponsoredSlotRepo.getAllSlots().onSuccess { slots ->
                    _state.value = _state.value.copy(sponsoredSlots = slots)
                }
            }
            result.onFailure { _state.value = _state.value.copy(slotReviewError = it.message) }
            _state.value = _state.value.copy(reviewingSlotId = null)
        }
    }

    fun clearSlotReviewError() {
        _state.value = _state.value.copy(slotReviewError = null)
    }
    /**
     * Sign-out: the admin dashboard (users, orders, coupons, slots) is
     * scoped to the admin session and must not outlive it.
     */
    fun clearSession() {
        _state.value = AdminState()
    }

}
