package com.duggustore.app.ui.screens.customer

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.duggustore.app.data.local.AppPrefs
import com.duggustore.app.data.model.Address
import com.duggustore.app.data.model.Campaign
import com.duggustore.app.data.model.Category
import com.duggustore.app.data.model.Coupon
import com.duggustore.app.data.model.HomeSection
import com.duggustore.app.data.model.Product
import com.duggustore.app.data.model.SponsoredSlot
import androidx.compose.ui.res.stringResource
import com.duggustore.app.R
import com.duggustore.app.platform.LocationState
import com.duggustore.app.platform.rememberDeviceLocation
import com.duggustore.app.platform.rememberVoiceSearchController
import com.duggustore.app.ui.components.*
import com.duggustore.app.ui.components.buildDiscountBanners
import com.duggustore.app.ui.theme.*
import kotlinx.coroutines.delay
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.rememberPagerState

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    categories: List<Category>,
    selectedCategoryId: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    // A category tapped on this page opens its own quick-shop sheet rather
    // than re-filtering the feed underneath it; [onCategorySelected] is what
    // that sheet's "view all" falls back to, so the old browse path is still
    // one tap away.
    onQuickShopOpen: (String) -> Unit = {},
    quickShopCategoryId: String? = null,
    quickShopProducts: List<Product> = emptyList(),
    isQuickShopLoading: Boolean = false,
    onQuickShopDismiss: () -> Unit = {},
    onAddToCart: (Product) -> Unit,
    onProductClick: (Product) -> Unit = {},
    deliveryAddress: String = "Set your delivery address",
    cartQuantities: Map<String, Int> = emptyMap(),
    favoriteIds: Set<String> = emptySet(),
    onIncrease: (Product) -> Unit = {},
    onDecrease: (Product) -> Unit = {},
    onToggleFavorite: (Product) -> Unit = {},
    onAddressClick: () -> Unit = {},
    notificationCount: Int = 0,
    onNotificationsClick: () -> Unit = {},
    savedAddresses: List<Address> = emptyList(),
    onSelectAddress: (Address) -> Unit = {},
    onSaveDetectedAddress: (String, Double, Double) -> Unit = { _, _, _ -> },
    offers: List<Coupon> = emptyList(),
    onOfferClick: (Coupon) -> Unit = {},
    isLoading: Boolean = false,
    error: String? = null,
    onRefresh: () -> Unit = {},
    onRetry: () -> Unit = {},
    // Whichever view is active — the default browse feed, a category, or a
    // search — pages through this same list; there's no separate in-memory
    // "filtered" list any more, since filtering now happens server-side.
    feedProducts: List<Product> = emptyList(),
    hasMoreFeed: Boolean = false,
    isLoadingMoreFeed: Boolean = false,
    onLoadMoreFeed: () -> Unit = {},
    // The full catalogue, separate from feedProducts above — used only to
    // let the offer carousel feature a matching product on its own card.
    allProducts: List<Product> = emptyList(),
    // Feed the wallet-reminder and referral-invite banners on the same rail
    // as the coupon cards — 0 / blank simply omits that banner.
    walletBalance: Int = 0,
    referralCode: String = "",
    onWalletBannerClick: () -> Unit = {},
    // Already date-windowed (campaigns) / approved-and-live (sponsored
    // slots) server-side — nothing here needs to check dates again.
    campaigns: List<Campaign> = emptyList(),
    sponsoredSlots: List<SponsoredSlot> = emptyList(),
    // The admin-curated browse layout for the All tab. Empty until an admin
    // has built one, which just leaves the page as the plain product feed it
    // was before.
    homeSections: List<HomeSection> = emptyList(),
    // Incremented when Home is re-tapped on the bottom bar.
    scrollToTopTick: Int = 0
) {
    // Both sheets are owned here so the header can stay a plain row of
    // controls and the sheets sit above the whole screen.
    var showLocationSheet by remember { mutableStateOf(false) }
    val detected = rememberDeviceLocation()
    val voice = rememberVoiceSearchController { onSearchQueryChange(it) }
    var showImageSearch by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    // The search row's measured bounds, so the history dropdown can hang
    // directly under it at exactly its width.
    var searchRowSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val dropdownGap = with(density) { 6.dp.roundToPx() }
    val pullRefreshState = rememberPullRefreshState(refreshing = isLoading, onRefresh = onRefresh)

    // Search and tabs stay pinned. The address strip is placement-only:
    // scroll down the feed shows it, scroll up hides it. Changing header
    // height used to remeasure the LazyColumn (and recompose every visible
    // card) at the start of every fling.
    val listState = rememberLazyListState()
    // Measured rather than guessed. This used to be a hand-tuned constant,
    // which meant every change to the strip's padding or font had to be
    // mirrored in that number by hand — and when the constant overshot the
    // strip's real height, a full collapse carried half the search bar off
    // the top of the screen with it.
    var addressStripPx by remember { mutableIntStateOf(0) }
    // What's deliberately left behind, so the search bar settles with a
    // little breathing room above it rather than jammed against the status
    // bar once the address has gone.
    val collapsedTopGapPx = with(density) { AddressBarCollapsedTopGap.roundToPx() }
    val collapseRangePx = (addressStripPx - collapsedTopGapPx).coerceAtLeast(0).toFloat()
    val collapseScope = rememberCoroutineScope()
    val addressCollapse = remember(collapseRangePx, collapseScope) {
        AddressBarCollapseState(collapseRangePx, collapseScope)
    }

    LaunchedEffect(scrollToTopTick) {
        if (scrollToTopTick <= 0) return@LaunchedEffect
        addressCollapse.reset()
        listState.animateScrollToItem(0)
    }

    // The dropdown floats over the feed rather than pushing it, so it would
    // otherwise hang there while the page scrolled underneath it.
    //
    // Read through snapshotFlow rather than as a LaunchedEffect key: a key
    // reads the state during composition, which recomposed this whole screen
    // — feed rows, section blocks and all — twice for every fling.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling -> if (scrolling) focusManager.clearFocus() }
    }

    // Paired off once per feed change instead of on every recomposition. The
    // chunking allocated a list per row each time this screen recomposed, and
    // it recomposes for search text, focus, and the header measuring itself.
    val feedRows = remember(feedProducts) { feedProducts.chunked(2) }

    // Home is the bottom of the back stack, so with no search active the
    // system back button already does the right thing (exits to the
    // launcher). With a search active it did that too — the whole app
    // closed instead of just backing out of the search, which is what
    // actually looks like a crash from the outside.
    BackHandler(enabled = searchQuery.isNotBlank()) {
        onSearchQueryChange("")
    }

    val context = LocalContext.current
    var recentSearches by remember { mutableStateOf(AppPrefs.recentSearches(context)) }
    // Saved once typing pauses rather than on every keystroke, so the list
    // doesn't fill up with "k", "ku", "kur" for a single search.
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) return@LaunchedEffect
        delay(1000)
        AppPrefs.addRecentSearch(context, searchQuery)
        recentSearches = AppPrefs.recentSearches(context)
    }

    // The coupon cards plus whatever else earns a slot on the same rail —
    // a spotlight for whatever was added to the catalogue most recently,
    // a reminder of wallet money the customer already has, an invite to
    // refer a friend. Each is a normal outcome to omit, not a fallback:
    // no new product, no balance, or no code yet just means one fewer card.
    // Optimized: split into smaller remember blocks to reduce recomposition scope
    val discountBanners = remember(offers, allProducts) {
        buildDiscountBanners(offers, allProducts, onOfferClick)
    }

    val campaignBanners = remember(campaigns, categories) {
        campaigns.map { campaign ->
            PromoBanner(
                id = "campaign:${campaign.id}",
                tint = runCatching { Color(android.graphics.Color.parseColor(campaign.tintHex)) }.getOrDefault(Orange),
                eyebrowIcon = Icons.Default.Campaign,
                eyebrow = "Limited time",
                headline = campaign.label,
                subtitle = categories.firstOrNull { it.id == campaign.categoryId }?.name
                    ?: "Handpicked for you",
                chipLabel = campaign.ctaLabel,
                onClick = { onCategorySelected(campaign.categoryId) }
            )
        }
    }

    val sponsoredBanners = remember(sponsoredSlots) {
        sponsoredSlots.mapNotNull { slot ->
            val product = slot.product ?: return@mapNotNull null
            PromoBanner(
                id = "sponsored:${slot.id}",
                tint = TextSecondary,
                eyebrowIcon = Icons.Default.Campaign,
                eyebrow = "",
                headline = product.name,
                subtitle = slot.headline.ifBlank { product.description },
                cornerTag = "SPONSORED",
                featuredProduct = product,
                onClick = { onProductClick(product) }
            )
        }
    }

    val newArrivalBanner = remember(allProducts) {
        allProducts.filter { it.isActive }.maxByOrNull { it.createdAt }?.let { product ->
            PromoBanner(
                id = "new-arrival:${product.id}",
                tint = Violet,
                eyebrowIcon = Icons.Default.FiberNew,
                eyebrow = "",
                headline = "Just landed",
                subtitle = product.name,
                cornerTag = "NEW",
                featuredProduct = product,
                onClick = { onProductClick(product) }
            )
        }
    }

    val walletBanner = remember(walletBalance) {
        if (walletBalance > 0) {
            PromoBanner(
                id = "wallet",
                tint = Teal,
                eyebrowIcon = Icons.Default.AccountBalanceWallet,
                eyebrow = "In your wallet",
                headline = "₹$walletBalance cashback",
                subtitle = "Waiting to be used on your next order",
                chipLabel = "USE NOW",
                onClick = onWalletBannerClick
            )
        } else null
    }

    val referralBanner = remember(referralCode) {
        if (referralCode.isNotBlank()) {
            PromoBanner(
                id = "referral",
                tint = Color(0xFF5AA9E6),
                eyebrowIcon = Icons.Default.Share,
                eyebrow = "Invite a friend",
                headline = "Give ₹50, get ₹50",
                subtitle = "Both of you get wallet credit on their first order",
                chipLabel = "SHARE MY CODE",
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Use my Duggu Store referral code $referralCode and we both get ₹50 wallet credit!"
                        )
                    }
                    context.startActivity(Intent.createChooser(intent, "Share referral code"))
                }
            )
        } else null
    }

    val promoBanners = remember(discountBanners, campaignBanners, sponsoredBanners, newArrivalBanner, walletBanner, referralBanner) {
        buildList {
            addAll(discountBanners)
            addAll(campaignBanners)
            addAll(sponsoredBanners)
            newArrivalBanner?.let { add(it) }
            walletBanner?.let { add(it) }
            referralBanner?.let { add(it) }
        }
    }

    // Held here rather than inside OfferCarousel: the rail is an item in the
    // feed below, so it is disposed the moment it scrolls off the top and
    // any state remembered inside it goes with it. Keeping the page out
    // here means coming back to the top of home returns you to the card you
    // were on instead of snapping back to the first one.
    val bannerPagerState = rememberPagerState(pageCount = { promoBanners.size })

    // The rail's artwork, warmed into Coil's memory cache and held there for
    // as long as home is on screen. Without this the bitmaps are only
    // reachable through the cache's own LRU, and a long scroll through the
    // feed's product photos is enough to evict them — so the banners were
    // being fetched and decoded again every time the rail came back into
    // view, which is the reload that was visible as a flash of empty card.
    val bannerImageUrls = remember(promoBanners) {
        promoBanners.mapNotNull { it.featuredProduct?.cutoutImage() }
    }
    // Keys the results so they survive recomposition; the list is small
    // (one image per promo card), so holding them all is cheap.
    val heldBannerImages = remember { mutableStateMapOf<String, Any>() }
    LaunchedEffect(bannerImageUrls) {
        heldBannerImages.keys.retainAll(bannerImageUrls.toSet())
        bannerImageUrls.forEach { url ->
            if (heldBannerImages.containsKey(url)) return@forEach
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(384)
                .memoryCacheKey(url)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
            // A strong reference to the decoded drawable. This is what
            // actually keeps it alive: the memory cache can drop its own
            // copy under pressure, but Coil will not re-decode an image it
            // is still handed back for the same key.
            val result = context.imageLoader.execute(request).drawable
            if (result != null) heldBannerImages[url] = result
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // The whole header used to be an oversized column shifted up by
            // the collapse amount, which moved the search bar and tabs off
            // the top of the screen along with the address. Only the address
            // strip collapses now (see the layout modifier on it below), so
            // everything under it simply slides up into the space it leaves
            // and stays fully on screen.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .nestedScroll(addressCollapse)
            ) {

            // Search stays on screen; the address strip above it is what
            // the nested-scroll connection slides off. Flat white so the
            // whole top of the page reads as one considered band, with a
            // hairline at the bottom of the header stack.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(1f)
                    .background(SurfaceWhite)
            ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
            Column(
                // 20dp, not the usual 16 — this is what lines the search
                // bar's edges up with the offer cards below it, whose
                // width comes from the pager's own 20dp contentPadding.
                // The tab row below sits outside it, so its baseline can
                // run the full width of the screen. The white header is
                // edge-to-edge, so the content clears the status bar here.
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
            ) {
                val locationState = detected.state
                // The strip that slides away: the gap below the status bar
                // plus the address row itself. Its measured height is what
                // sets the collapse distance, so the two can never drift
                // apart.
                //
                // 10dp of gap. This was briefly 30 while the whole header
                // still moved as one piece and the badge was being clipped
                // from above; now that only this strip collapses, nothing is
                // pressing on the badge and 30 just read as a band of empty
                // space under the status bar.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Clips to the reduced height reported below, so the
                        // part of the row that has scrolled up out of its own
                        // box doesn't paint over the status bar.
                        .clipToBounds()
                        // Measured at full height, but reported to the parent
                        // as shorter by however far it has collapsed, and
                        // drawn shifted up to match. That keeps the collapse
                        // contained: the row shrinks in place and the search
                        // bar below rises into the gap, rather than the whole
                        // header sliding off the top of the screen.
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val shift = addressCollapse.collapsePx
                                .roundToInt()
                                .coerceIn(0, placeable.height)
                            layout(placeable.width, placeable.height - shift) {
                                placeable.place(0, -shift)
                            }
                        }
                        // Inside the collapse, so it reports the row's full
                        // natural height rather than the shrinking one — the
                        // other way round it would feed its own output back
                        // in and settle at zero.
                        .onSizeChanged { addressStripPx = it.height }
                ) {
                Spacer(Modifier.height(10.dp))
                // The header shows what the order will actually use. A saved
                // address the customer picked wins over the GPS street: the
                // detected address used to take precedence, so choosing
                // "Home" in the sheet changed nothing on screen — and the
                // header then disagreed with checkout's delivery address.
                // Detected is only a stand-in until a real address exists.
                // Mirrors AddressState.defaultAddress: the flagged default,
                // else the first saved one.
                val selectedDelivery = savedAddresses.firstOrNull { it.isDefault }
                    ?: savedAddresses.firstOrNull()
                LocationBar(
                    city = if (locationState is LocationState.Locating) {
                        stringResource(R.string.location_finding)
                    } else {
                        stringResource(R.string.home_deliver_to)
                    },
                    address = when {
                        selectedDelivery != null -> selectedDelivery.fullAddress
                        locationState is LocationState.Found -> locationState.address
                        locationState is LocationState.Locating ->
                            stringResource(R.string.location_wait)
                        locationState is LocationState.Unavailable ->
                            stringResource(locationState.messageRes)
                        else -> deliveryAddress
                    },
                    onClick = { showLocationSheet = true },
                    trailing = { StoreWordmarkBadge() },
                    // Alpha is sampled in the layer so a collapse frame
                    // invalidates draw on this row only, not composition.
                    modifier = Modifier.graphicsLayer {
                        val range = collapseRangePx
                        alpha = if (range <= 0f) 1f
                        else (1f - addressCollapse.collapsePx / range).coerceIn(0f, 1f)
                    }
                )
                }
                Spacer(Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { searchRowSize = it }
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StoreSearchBar(
                        query = searchQuery,
                        // Short hint: with a mic and a scan button in the bar
                        // and the picker and bell beside it, the long one
                        // would ellipsize mid-word in what's left.
                        placeholder = stringResource(R.string.home_search_hint_short),
                        onQueryChange = onSearchQueryChange,
                        // Null when the device has no speech recogniser, which
                        // leaves the mic out rather than showing a dead button.
                        onMicClick = voice?.let { { it.open() } },
                        onPhotoSearchClick = { showImageSearch = true },
                        onFocusChange = { searchFocused = it },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    LanguagePicker()
                    Box(contentAlignment = Alignment.TopEnd) {
                        Box(
                            // No filled disc behind it — the grey circle read
                            // as a stuck press state next to the flat controls
                            // either side of it. Still clipped round, so the
                            // ripple on tap is a circle rather than a box.
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .dugguClickable { onNotificationsClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.NotificationsNone,
                                stringResource(R.string.home_notifications),
                                tint = TextSecondary,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        if (notificationCount > 0) {
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 17.dp, minHeight = 17.dp)
                                    .clip(CircleShape)
                                    .background(MyntraPink),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (notificationCount > 9) "9+" else "$notificationCount",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }

                // A real dropdown rather than another row in the header: a
                // Popup floats over the tabs and the feed, so opening the
                // history doesn't shove the whole page down and let go of it
                // again. Not focusable, or it would take focus off the field
                // that opened it and close itself on the spot.
                if (searchFocused && searchQuery.isBlank() && recentSearches.isNotEmpty() &&
                    searchRowSize.height > 0
                ) {
                    Popup(
                        alignment = Alignment.TopStart,
                        offset = IntOffset(0, searchRowSize.height + dropdownGap),
                        properties = PopupProperties(focusable = false),
                        onDismissRequest = { focusManager.clearFocus() }
                    ) {
                        SearchHistoryPanel(
                            modifier = Modifier.width(
                                with(density) { searchRowSize.width.toDp() }
                            ),
                            terms = recentSearches,
                            onTermClick = onSearchQueryChange,
                            onRemoveTerm = { term ->
                                AppPrefs.removeRecentSearch(context, term)
                                recentSearches = AppPrefs.recentSearches(context)
                            },
                            onClearAll = {
                                AppPrefs.clearRecentSearches(context)
                                recentSearches = emptyList()
                            }
                        )
                    }
                }
                }
            }

            // Pinned with the search field rather than scrolling with the
            // feed: the tabs filter what is in that feed, so losing them
            // the moment you scroll into it is the wrong way round.
            if (categories.isNotEmpty() && searchQuery.isBlank()) {
                CategoryTabStrip(
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    onCategorySelected = onCategorySelected,
                    onCategoryOpen = { onQuickShopOpen(it.id) }
                )
            } else {
                Spacer(Modifier.height(8.dp))
            }
            }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pullRefresh(pullRefreshState)
            ) {
            if (error != null && !isLoading && categories.isEmpty() && feedProducts.isEmpty()) {
                ErrorRetryBlock(message = error, onRetry = onRetry)
            } else if (isLoading && categories.isEmpty() && feedProducts.isEmpty()) {
                // Fills the same weight(1f) area the list below would, so
                // there is no jump in the page's overall height once real
                // content replaces it. Shaped like the grid it's standing in
                // for, rather than a bare spinner, so the first frame already
                // reads as "a product grid is coming" instead of a blank
                // page with a wait icon in the middle of it.
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ProductGridSkeleton()
                }
            } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {

            // Search takes over the whole page — the carousel is a browsing
            // aid, and showing it above a set of search results made it look
            // like the results were mixed in with it instead of being their
            // own list.
            val isSearching = searchQuery.isNotBlank()

            // contentType on every entry below: this feed mixes a banner, N
            // section blocks and N product rows in one LazyColumn, and
            // without it Compose can't tell which of its pooled slots a
            // scrolled-in item can reuse — it has to measure and lay out
            // each one as if it were a shape never seen before. Labelling
            // the shape lets it reuse the right pool, which is what turns a
            // fresh-composition cost into a cheap rebind on fast scrolling.
            if (promoBanners.isNotEmpty() && !isSearching) {
                item(contentType = "banner") {
                    Spacer(Modifier.height(14.dp))
                    OfferCarousel(
                        banners = promoBanners,
                        feedListState = listState,
                        // Owned by the screen, not by the row, so scrolling
                        // the rail out of the feed no longer resets it to
                        // the first card.
                        hoistedPagerState = bannerPagerState
                    )
                }
            }

            // The browse layout belongs to the All tab only: picking a
            // category is a request for that category's products, and the
            // sections are how you get there in the first place.
            val browsing = !isSearching && selectedCategoryId == null
            if (browsing) {
                items(homeSections, key = { it.id }, contentType = { "section" }) { section ->
                    Spacer(Modifier.height(26.dp))
                    HomeSectionBlock(
                        section = section,
                        onCategoryClick = onQuickShopOpen
                    )
                }
            }

            item(contentType = "header") {
                Spacer(Modifier.height(24.dp))
                RowHeader(
                    title = when {
                        searchQuery.isNotBlank() -> stringResource(R.string.home_results)
                        selectedCategoryId != null ->
                            categories.firstOrNull { it.id == selectedCategoryId }?.name
                                ?: stringResource(R.string.home_products)
                        else -> stringResource(R.string.home_popular_deals)
                    },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(12.dp))
            }

            if (feedProducts.isEmpty()) {
                item(contentType = "empty") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.SearchOff, null, tint = TextLight, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.home_empty_title),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (searchQuery.isBlank())
                                stringResource(R.string.home_empty_browse)
                            else
                                stringResource(R.string.home_empty_search),
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                // Two per row, built manually so the whole page stays one scrolling
                // LazyColumn rather than nesting a grid inside it. Keyed on the
                // pair's own product ids — without a key, Compose can only
                // diff this list by position, so an insert/removal/reorder
                // anywhere in a 50-100 product feed reuses every row after it
                // for the wrong pair instead of recomposing just the one that
                // actually changed.
                // Keyed on the first product's id in each pair — it
                // identifies the pair uniquely (a product sits in
                // exactly one pair) and costs nothing, where joining
                // both ids built a fresh 70-odd character string
                // every time a key was asked for.
                // Compose reads the key during composition and
                // invalidates only when a product enters/leaves the
                // feed, not on every scrolled pixel.
                items(feedRows, key = { pair -> pair.first().id }, contentType = { "product_row" }) { pair ->
                    HomeProductRow(
                        pair = pair,
                        cartQuantities = cartQuantities,
                        favoriteIds = favoriteIds,
                        onAddToCart = onAddToCart,
                        onIncrease = onIncrease,
                        onDecrease = onDecrease,
                        onToggleFavorite = onToggleFavorite,
                        onProductClick = onProductClick
                    )
                }

                // A plain item at the tail of the feed rather than a scroll
                // listener — LazyColumn only composes what's near the
                // viewport, so this only enters composition (and fires) once
                // the user has actually scrolled close to the end of what's
                // loaded so far.
                if (hasMoreFeed) {
                    item(contentType = "loader") {
                        LaunchedEffect(feedProducts.size) { onLoadMoreFeed() }
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoadingMoreFeed) {
                                CircularProgressIndicator(color = Teal, modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                }
            }
            }
            }

            PullRefreshIndicator(
                refreshing = isLoading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                contentColor = Teal
            )
            }
            }
        }

        LocationSheet(
            visible = showLocationSheet,
            locationState = detected.state,
            addresses = savedAddresses,
            onDetectLocation = detected.refresh,
            onUseDetected = { address, lat, lng ->
                onSaveDetectedAddress(address, lat, lng)
                showLocationSheet = false
            },
            onSelectAddress = { address ->
                onSelectAddress(address)
                showLocationSheet = false
            },
            onAddNewAddress = {
                showLocationSheet = false
                onAddressClick()
            },
            onDismiss = { showLocationSheet = false }
        )

        voice?.let { VoiceSearchSheet(controller = it) }

        if (showImageSearch) {
            ImageSearchScreen(
                onResult = { code ->
                    showImageSearch = false
                    onSearchQueryChange(code)
                },
                onDismiss = { showImageSearch = false }
            )
        }

        // Resolved from the id the view model is holding rather than passed
        // down as a whole Category: the sheet is open exactly as long as that
        // id is set, so the two can never disagree about what is showing.
        val quickShopCategory = quickShopCategoryId?.let { id ->
            categories.firstOrNull { it.id == id }
        }
        if (quickShopCategory != null) {
            QuickShopSheet(
                category = quickShopCategory,
                products = quickShopProducts,
                isLoading = isQuickShopLoading,
                cartQuantities = cartQuantities,
                favoriteIds = favoriteIds,
                onAddToCart = onAddToCart,
                onIncrease = onIncrease,
                onDecrease = onDecrease,
                onToggleFavorite = onToggleFavorite,
                onProductClick = onProductClick,
                onViewAll = {
                    onCategorySelected(quickShopCategory.id)
                    onQuickShopDismiss()
                },
                onDismiss = onQuickShopDismiss
            )
        }
    }
}

/** Location strip + the 6.dp gap under it — the only header that collapses. */
/**
 * Left on screen above the search bar once the address strip has fully
 * collapsed, so the search field stops short of the status bar instead of
 * sliding under it.
 */
private val AddressBarCollapsedTopGap = 8.dp

/**
 * Nested-scroll collapse that never remasures the feed.
 *
 * [collapsePx] is written from the scroll connection and read only inside
 * [Modifier.offset] / [Modifier.graphicsLayer] lambdas, so a fling re-places
 * (or redraws) the column — it does not recompose HomeScreen or the cards.
 */
private class AddressBarCollapseState(
    private val rangePx: Float,
    /** Drives the drag's snapTo; the animation itself runs on the scroll's own scope. */
    private val scope: CoroutineScope
) : NestedScrollConnection {
    /**
     * Animatable rather than a plain float so the strip can be dragged
     * one-to-one with the finger *and* animated on release from the same
     * value, without the two fighting over it.
     */
    private val collapse = Animatable(0f)
    val collapsePx: Float get() = collapse.value

    suspend fun reset() { collapse.snapTo(0f) }

    /**
     * Called when the gesture ends. A finger lifted mid-collapse used to
     * leave the strip frozen half-open — the address clipped, the search bar
     * sitting at some in-between height. This runs it to whichever end it is
     * closest to, so it always comes to rest either fully shown or fully
     * hidden.
     */
    suspend fun settle() {
        if (rangePx <= 0f) return
        val target = if (collapse.value > rangePx / 2f) rangePx else 0f
        if (collapse.value == target) return
        collapse.animateTo(
            targetValue = target,
            // Springs rather than a fixed duration: the strip is following a
            // flick, so it should carry the gesture's own momentum into the
            // settle instead of restarting at zero speed. No bounce — this is
            // a header finding its place, not a thing being thrown.
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    // Both directions are taken before the list scrolls, so the strip leads
    // the gesture and the feed only starts moving once the strip has
    // finished — the standard "enter always" collapsing-header feel.
    //
    // delta < 0 (swiping the content up, reading further down the feed)
    // slides the address strip away until the search bar sits at the top.
    // delta > 0 (swiping back down) brings it straight back, from wherever
    // in the feed you happen to be, rather than making you scroll all the
    // way to item 0 first — a downward flick from halfway down the page
    // used to leave the address hidden, which read as the bar being stuck.
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (rangePx <= 0f) return Offset.Zero
        val delta = available.y
        val current = collapse.value
        val next = (current - delta).coerceIn(0f, rangePx)
        val used = current - next
        if (used == 0f) return Offset.Zero
        // snapTo, not animateTo: while the finger is down the strip should
        // track it exactly. The animation only happens once it lifts.
        scope.launch { collapse.snapTo(next) }
        // Reported in the gesture's own sign (negative going up, positive
        // going down): this much of the swipe went into the strip, so the
        // feed must not scroll by it as well.
        return Offset(0f, used)
    }

    /**
     * A fling is handed over after the finger has already gone, so this is
     * where the settle belongs — and it runs before the list consumes the
     * velocity, so the strip finishes its own movement rather than waiting
     * for the feed to stop.
     */
    override suspend fun onPreFling(available: Velocity): Velocity {
        settle()
        return Velocity.Zero
    }
}

/**
 * One two-column product row. Extracted so LazyColumn can reuse/skip it
 * independently of the pinned header and the banner above it.
 */
@Composable
private fun HomeProductRow(
    pair: List<Product>,
    cartQuantities: Map<String, Int>,
    favoriteIds: Set<String>,
    onAddToCart: (Product) -> Unit,
    onIncrease: (Product) -> Unit,
    onDecrease: (Product) -> Unit,
    onToggleFavorite: (Product) -> Unit,
    onProductClick: (Product) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        pair.forEach { product ->
            StoreProductCard(
                product = product,
                quantityInCart = cartQuantities[product.id] ?: 0,
                isFavorite = favoriteIds.contains(product.id),
                onAdd = { onAddToCart(product) },
                onIncrease = { onIncrease(product) },
                onDecrease = { onDecrease(product) },
                onToggleFavorite = { onToggleFavorite(product) },
                onClick = { onProductClick(product) },
                modifier = Modifier.weight(1f)
            )
        }
        if (pair.size == 1) Spacer(Modifier.weight(1f))
    }
}

/**
 * The category strip under the search bar, as tabs.
 *
 * This was a rail of photo tiles on rounded cards. Tabs say the same thing
 * in less room and in the shape a shopper already reads as "pick one of
 * these": one line of names, the current one marked in pink with a rule
 * under it, and everything else quiet beside it. The photos still do their
 * job — they are the header of the quick-shop sheet each tab opens.
 *
 * Pinned with the search field above the feed rather than scrolling away
 * with it, because these tabs are how the page is navigated: losing them
 * the moment you scroll into the products is the wrong way round.
 */
@Composable
private fun CategoryTabStrip(
    categories: List<Category>,
    selectedCategoryId: String?,
    onCategorySelected: (String?) -> Unit,
    onCategoryOpen: (Category) -> Unit
) {
    val listState = rememberLazyListState()

    // A category can also be chosen somewhere other than this strip — a tile
    // in a browse section, or "View all" from inside the quick-shop sheet —
    // and then the tab that is now active may be scrolled off the end. The
    // offset of 1 accounts for "All" being the first item.
    LaunchedEffect(selectedCategoryId) {
        val index = categories.indexOfFirst { it.id == selectedCategoryId }
        if (index >= 0) listState.animateScrollToItem(index + 1)
    }

    Column(modifier = Modifier.fillMaxWidth().background(SurfaceWhite)) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 6.dp)
        ) {
            item(key = "tab_all") {
                CategoryTab(
                    label = stringResource(R.string.home_tab_all),
                    selected = selectedCategoryId == null,
                    onClick = { onCategorySelected(null) }
                )
            }
            items(categories, key = { it.id }) { category ->
                CategoryTab(
                    label = category.name,
                    selected = selectedCategoryId == category.id,
                    onClick = { onCategoryOpen(category) }
                )
            }
        }
        // The strip's own edge, so the feed scrolls under a line and not
        // into the tabs.
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(DividerGray))
    }
}

/**
 * One tab: its name, and when it is the active one, a pink rule sitting on
 * the strip's bottom edge — the same pink as the bag badge and the add-to-bag
 * button, so "this is the one you are on" reads in one colour across the app.
 */
@Composable
private fun CategoryTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(46.dp)
            .dugguClickableFlat(onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp),
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MyntraPink else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 10.dp)
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MyntraPink)
            )
        }
    }
}
