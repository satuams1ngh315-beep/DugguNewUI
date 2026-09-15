package com.duggustore.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duggustore.app.data.model.Campaign
import com.duggustore.app.data.model.Category
import com.duggustore.app.data.model.Coupon
import com.duggustore.app.data.model.Product
import com.duggustore.app.data.model.HomeSection
import com.duggustore.app.data.model.SponsoredSlot
import com.duggustore.app.data.repository.CampaignRepository
import com.duggustore.app.data.repository.CategoryRepository
import com.duggustore.app.data.repository.OfferRepository
import com.duggustore.app.data.repository.ProductRepository
import com.duggustore.app.data.repository.HomeLayoutRepository
import com.duggustore.app.data.repository.SponsoredSlotRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

data class HomeState(
    val isLoading: Boolean = false,
    val categories: List<Category> = emptyList(),
    /** The store's active coupons, shown on the home carousel. */
    val offers: List<Coupon> = emptyList(),
    /** Currently-running seasonal pushes — already date-windowed server-side. */
    val campaigns: List<Campaign> = emptyList(),
    /** Currently-live, admin-approved seller placements — already filtered server-side. */
    val sponsoredSlots: List<SponsoredSlot> = emptyList(),
    /** The admin-curated browse layout shown on the All tab. */
    val homeSections: List<HomeSection> = emptyList(),
    /**
     * The full active catalogue — kept only for Categories' scroll-spy view
     * (which genuinely needs every product across every category at once,
     * a different job from anything paginated) and as a fallback for
     * resolving a product by id from somewhere that isn't [feedProducts].
     * Home's own grid never renders this directly.
     */
    val products: List<Product> = emptyList(),
    /**
     * Every category, stocked or not — what a seller picks from when filing a
     * product. [categories] is the stocked-only list the storefront browses
     * by, and filing against that would deadlock: an empty category could
     * never receive its first product.
     */
    val allCategories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val searchQuery: String = "",
    val error: String? = null,
    /**
     * Whichever view is active — the default browse feed, a selected
     * category, or a search — pages through this same list, loaded and
     * filtered server-side rather than sliced out of [products] in memory.
     */
    val feedProducts: List<Product> = emptyList(),
    val hasMoreFeed: Boolean = true,
    val isLoadingMoreFeed: Boolean = false,
    /**
     * The category whose quick-shop sheet is open, if any — the sheet itself
     * is owned by the screen, but what to show in it is fetched here so the
     * first open of a category has something to wait on and every open after
     * that does not.
     */
    val quickShopCategoryId: String? = null,
    val quickShopProducts: List<Product> = emptyList(),
    val isQuickShopLoading: Boolean = false
)

// debounce() is still @FlowPreview on the coroutines version this app
// builds against, and it is load-bearing for search: it is what stops a
// network query going out on every keystroke.
@OptIn(kotlinx.coroutines.FlowPreview::class)
class HomeViewModel : ViewModel() {
    private val categoryRepo = CategoryRepository()
    private val productRepo = ProductRepository()
    private val offerRepo = OfferRepository()
    private val campaignRepo = CampaignRepository()
    private val sponsoredSlotRepo = SponsoredSlotRepository()
    private val homeLayoutRepo = HomeLayoutRepository()

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state

    /** Tracked here rather than in HomeState — advancing it isn't itself
     *  something the UI needs to recompose over. */
    private var feedPage = 0

    /**
     * Category id → the products the quick-shop sheet last saw for it.
     * Nothing here is a source of truth: it exists so re-opening a category
     * in the same session is instant, and it is deliberately never written
     * back to the feed's own state.
     */
    private val quickShopCache = mutableMapOf<String, List<Product>>()

    /**
     * Debounced separately from HomeState.searchQuery itself, so the text
     * field stays responsive to every keystroke while the network re-query
     * it drives waits until typing actually pauses.
     *
     * A MutableSharedFlow, not a MutableStateFlow. StateFlow conflates: it
     * drops a value equal to the one it already holds, and it drops values
     * a slow collector didn't keep up with. Behind a 350ms debounce that is
     * exactly the wrong behaviour — searching a term, clearing the field and
     * searching again would find the flow already holding that term and emit
     * nothing at all, so the second search simply never ran. A SharedFlow
     * with replay 1 delivers every submitted query, and still gives a
     * late-arriving collector the current one.
     */
    private val searchQueryChanges = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 8
    )

    init {
        loadData()
        viewModelScope.launch {
            searchQueryChanges
                // Collapses the keystrokes within a word into one query.
                // Deliberately no distinctUntilChanged: two searches for the
                // same text are two real requests to re-run it, and dropping
                // the second is what made a repeat search do nothing.
                .debounce(350)
                .collect { refreshFeed() }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            // Fired together rather than awaited one after another — four
            // sequential round trips left the screen sitting on its empty
            // state for roughly their combined latency, and then had the
            // carousel, categories and the whole product grid all pop in
            // at once, right below the search bar. Started concurrently,
            // the wait is only as long as the slowest of the four.
            // Stocked only: a category with nothing in it is a dead end for
            // a shopper, and the catalogue is seeded far wider than current
            // stock so sellers have somewhere precise to file things.
            val categoriesDeferred = async { categoryRepo.getStockedCategories() }
            val allCategoriesDeferred = async { categoryRepo.getAllCategories() }
            val productsDeferred = async { productRepo.getAllProducts() }
            val offersDeferred = async { offerRepo.getOffers() }
            val campaignsDeferred = async { campaignRepo.getRunningCampaigns() }
            val sponsoredSlotsDeferred = async { sponsoredSlotRepo.getLiveSlots() }
            val homeSectionsDeferred = async { homeLayoutRepo.getLayout() }
            val feedDeferred = async {
                productRepo.getProductsPage(
                    page = 0,
                    pageSize = FEED_PAGE_SIZE,
                    categoryId = _state.value.selectedCategoryId,
                    search = _state.value.searchQuery.takeIf { it.isNotBlank() }
                )
            }

            val categoriesResult = categoriesDeferred.await()
            val productsResult = productsDeferred.await()
            val allCategoriesResult = allCategoriesDeferred.await()
            // A store with no coupons/campaigns/sponsors is a normal state,
            // not an error worth showing; the carousel simply renders fewer cards.
            val offers = offersDeferred.await().getOrNull()
            val campaigns = campaignsDeferred.await().getOrNull()
            val sponsoredSlots = sponsoredSlotsDeferred.await().getOrNull()
            val homeSections = homeSectionsDeferred.await().getOrNull()
            val feedResult = feedDeferred.await()

            val categories = categoriesResult.getOrNull()
            val products = productsResult.getOrNull()?.filter { it.isActive }
            val allCategories = allCategoriesResult.getOrNull()
            val feed = feedResult.getOrNull()

            // Only the catalogue itself is fatal — a missing coupon rail is
            // fine, an empty home with no retry is not.
            val loadError = if (categoriesResult.isFailure && feedResult.isFailure) {
                feedResult.exceptionOrNull()?.message
                    ?: categoriesResult.exceptionOrNull()?.message
                    ?: "Couldn't load the store. Try again."
            } else {
                null
            }

            feedPage = 0
            _state.value = _state.value.copy(
                categories = categories ?: _state.value.categories,
                products = products ?: _state.value.products,
                allCategories = allCategories ?: _state.value.allCategories,
                offers = offers ?: _state.value.offers,
                campaigns = campaigns ?: _state.value.campaigns,
                sponsoredSlots = sponsoredSlots ?: _state.value.sponsoredSlots,
                homeSections = homeSections ?: _state.value.homeSections,
                feedProducts = feed ?: _state.value.feedProducts,
                hasMoreFeed = feed?.let { it.size == FEED_PAGE_SIZE } ?: _state.value.hasMoreFeed,
                isLoading = false,
                error = loadError
            )
        }
    }

    /**
     * Appends the next page of whichever view is currently active. A no-op
     * while a page is already in flight or the last one came back short of
     * a full page (nothing further to ask for) — the caller (a LazyColumn
     * item entering composition near the end of the list) can call this
     * freely without its own guard.
     */
    fun loadMoreFeed() {
        val current = _state.value
        if (current.isLoadingMoreFeed || !current.hasMoreFeed) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingMoreFeed = true)
            val nextPage = feedPage + 1
            productRepo.getProductsPage(
                page = nextPage,
                pageSize = FEED_PAGE_SIZE,
                categoryId = current.selectedCategoryId,
                search = current.searchQuery.takeIf { it.isNotBlank() }
            ).onSuccess { page ->
                feedPage = nextPage
                _state.value = _state.value.copy(
                    feedProducts = _state.value.feedProducts + page,
                    hasMoreFeed = page.size == FEED_PAGE_SIZE,
                    isLoadingMoreFeed = false
                )
            }.onFailure {
                _state.value = _state.value.copy(isLoadingMoreFeed = false)
            }
        }
    }

    fun selectCategory(categoryId: String?) {
        _state.value = _state.value.copy(selectedCategoryId = categoryId)
        // Not a rapid-fire event like typing, so no debounce needed —
        // refetch as soon as the tap lands.
        viewModelScope.launch { refreshFeed() }
    }

    /**
     * Opens a category's quick-shop sheet.
     *
     * The catalogue already in hand is painted first when it covers this
     * category, so a tap on a tile shows products on the same frame instead
     * of a spinner; the sheet's own page then replaces it with a list that
     * is paged exactly like the feed. A category visited before is served
     * from [quickShopCache] and never asked for twice in a session.
     */
    fun openQuickShop(categoryId: String) {
        val cached = quickShopCache[categoryId]
        val seed = _state.value.products.filter { it.categoryId == categoryId }

        _state.value = _state.value.copy(
            quickShopCategoryId = categoryId,
            quickShopProducts = cached ?: seed,
            isQuickShopLoading = cached == null
        )

        if (cached != null) return

        viewModelScope.launch {
            productRepo.getProductsPage(
                page = 0,
                pageSize = QUICK_SHOP_PAGE_SIZE,
                categoryId = categoryId,
                search = null
            ).onSuccess { page ->
                quickShopCache[categoryId] = page
                // A second tile tapped while this was in flight has already
                // moved the sheet on; its own products must not be
                // overwritten by the one the shopper left behind.
                if (_state.value.quickShopCategoryId == categoryId) {
                    _state.value = _state.value.copy(
                        quickShopProducts = page,
                        isQuickShopLoading = false
                    )
                }
            }.onFailure {
                if (_state.value.quickShopCategoryId == categoryId) {
                    // The seeded list, if there was one, stays on screen —
                    // a stale shelf beats an empty sheet.
                    _state.value = _state.value.copy(isQuickShopLoading = false)
                }
            }
        }
    }

    fun closeQuickShop() {
        _state.value = _state.value.copy(
            quickShopCategoryId = null,
            quickShopProducts = emptyList(),
            isQuickShopLoading = false
        )
    }

    fun search(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        // tryEmit rather than emit: this is called from the text field's
        // onValueChange, which isn't a coroutine. The buffer above absorbs
        // typing bursts, and the debounce means only the last of them
        // reaches the network anyway.
        searchQueryChanges.tryEmit(query)
    }

    /** Replaces the feed from page 0 for whatever filter (category/search) is now current. */
    private suspend fun refreshFeed() {
        val state = _state.value
        feedPage = 0
        _state.value = state.copy(isLoadingMoreFeed = false)
        productRepo.getProductsPage(
            page = 0,
            pageSize = FEED_PAGE_SIZE,
            categoryId = state.selectedCategoryId,
            search = state.searchQuery.takeIf { it.isNotBlank() }
        ).onSuccess { page ->
            _state.value = _state.value.copy(
                feedProducts = page,
                hasMoreFeed = page.size == FEED_PAGE_SIZE
            )
        }.onFailure {
            // A failed search used to leave the previous term's results
            // sitting there, which reads as the search having been ignored.
            // Clearing them lets the feed's own empty state explain that
            // this query returned nothing to show.
            _state.value = _state.value.copy(
                feedProducts = emptyList(),
                hasMoreFeed = false
            )
        }
    }

    private companion object {
        const val FEED_PAGE_SIZE = 20

        /**
         * The quick-shop sheet previews six and hands the rest to the
         * category view, so this only has to be comfortably deeper than
         * that — but it is fetched as a page rather than as a count, and
         * the sheet's price/offer chips read better across a wider slice
         * than a handful of rows.
         */
        const val QUICK_SHOP_PAGE_SIZE = 24
    }
}
